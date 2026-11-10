package ru.lor.plugin.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Periodic "Sculk Tide" world event. */
public final class TideManager {

    private static final Set<Material> REPLACEABLE = EnumSet.of(
            Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.CALCITE,
            Material.GRAVEL, Material.DIRT, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.COBBLED_DEEPSLATE
    );

    private final LorPlugin plugin;
    private final Random random = new Random();
    private final Set<UUID> participants = new HashSet<>();
    private boolean active;
    private long nextRun;
    private long startedAt;
    private BukkitTask cycleTask;
    private BukkitTask endTask;

    public TideManager(LorPlugin plugin) {
        this.plugin = plugin;
        this.nextRun = System.currentTimeMillis() + plugin.config().tideIntervalMinutes() * 60_000L;
    }

    public boolean isActive() {
        return active;
    }

    public long nextRunMillis() {
        return nextRun;
    }

    public int secondsLeft() {
        if (!active) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
        return (int) Math.max(0, plugin.config().tideDurationSeconds() - elapsed);
    }

    public void tickMinute() {
        if (!plugin.config().tideEnabled() || active) {
            return;
        }
        if (System.currentTimeMillis() >= nextRun) {
            start(false);
        }
    }

    public boolean start(boolean force) {
        if (active) {
            return false;
        }
        List<Player> eligible = eligiblePlayers();
        if (eligible.isEmpty()) {
            nextRun = System.currentTimeMillis() + 5L * 60L * 1000L;
            return false;
        }
        active = true;
        startedAt = System.currentTimeMillis();
        participants.clear();
        for (Player player : eligible) {
            participants.add(player.getUniqueId());
        }
        plugin.getServer().broadcast(plugin.messages().prefixed("tide.start-broadcast"));
        for (Player player : eligible) {
            plugin.messages().title(player, "tide.start-title", "tide.start-subtitle");
            plugin.messages().send(player, "tide.start");
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.5f);
        }
        int duration = plugin.config().tideDurationSeconds();
        cycleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, 200L, 200L);
        endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> end(true), duration * 20L);
        plugin.getLogger().info("Скалк-прилив начался (игроков: " + eligible.size() + ")");
        return true;
    }

    private void cycle() {
        if (!active) {
            return;
        }
        int left = secondsLeft();
        for (UUID uuid : new ArrayList<>(participants)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !plugin.config().isWorldEnabled(player.getWorld())) {
                continue;
            }
            Profile profile = plugin.profiles().get(player);
            profile.addCorruption(plugin.config().tideCorruption());
            plugin.messages().actionBar(player, "tide.actionbar", "seconds", left);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.5f);
            if (plugin.mobs().trackedCount() < plugin.config().tideMobCap()) {
                plugin.mobs().spawnAround(player, plugin.config().tideMobsPerCycle());
            }
            if (plugin.config().tideSpread()) {
                spread(player);
            }
        }
    }

    private void spread(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = base.getBlockX() + random.nextInt(21) - 10;
            int z = base.getBlockZ() + random.nextInt(21) - 10;
            int y = base.getBlockY() + random.nextInt(9) - 4;
            if (y > plugin.config().tideSpreadMaxY()) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (!REPLACEABLE.contains(block.getType())) {
                continue;
            }
            if (!block.getRelative(BlockFace.UP).isPassable()) {
                continue;
            }
            block.setType(random.nextDouble() < 0.2 ? Material.SCULK : Material.SCULK_VEIN, false);
        }
    }

    public void end(boolean reward) {
        if (!active) {
            return;
        }
        active = false;
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        plugin.mobs().despawnAll(true);
        plugin.getServer().broadcast(plugin.messages().prefixed("tide.end-broadcast"));
        if (reward) {
            for (UUID uuid : participants) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                Profile profile = plugin.profiles().get(player);
                profile.addEcho(plugin.config().tideRewardEcho());
                profile.addTideSurvived();
                plugin.quests().onTide(player);
                plugin.messages().send(player, "tide.reward", "echo", plugin.config().tideRewardEcho());
                plugin.messages().title(player, "tide.end-title", "tide.end-subtitle");
            }
            plugin.profiles().saveAsync();
        }
        participants.clear();
        nextRun = System.currentTimeMillis() + plugin.config().tideIntervalMinutes() * 60_000L;
        plugin.getLogger().info("Скалк-прилив завершён");
    }

    public void stopByAdmin() {
        if (!active) {
            return;
        }
        end(false);
    }

    /** Silent shutdown: no broadcasts, no rewards. */
    public void shutdown() {
        active = false;
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (plugin.mobs() != null) {
            plugin.mobs().despawnAll(true);
        }
        participants.clear();
    }

    public List<Player> eligiblePlayers() {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.config().isWorldEnabled(player.getWorld())) {
                continue;
            }
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            result.add(player);
        }
        return result;
    }
}
