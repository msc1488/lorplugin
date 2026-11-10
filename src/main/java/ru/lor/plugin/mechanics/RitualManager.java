package ru.lor.plugin.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;

import java.util.ArrayList;
import java.util.List;

/** The 5x5 sculk ritual that summons the Herald. */
public final class RitualManager {

    private final LorPlugin plugin;
    private boolean running;
    private long lastRitual;

    public RitualManager(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    public void sendInfo(Player player) {
        for (String line : plugin.messages().stringList("ritual.scheme")) {
            player.sendMessage(ru.lor.plugin.util.Text.mm(line));
        }
    }

    /** Returns true when the interaction was handled. */
    public boolean tryActivate(Player player, Block clicked) {
        if (clicked.getType() != Material.SCULK_CATALYST) {
            return false;
        }
        if (running) {
            plugin.messages().send(player, "ritual.in-progress");
            return true;
        }
        if (plugin.boss().isActive()) {
            plugin.messages().send(player, "ritual.boss-alive");
            return true;
        }
        Profile profile = plugin.profiles().get(player);
        int required = plugin.config().ritualRequiredChapter();
        if (profile.chapter() < required) {
            plugin.messages().send(player, "ritual.too-early", "chapter", required);
            return true;
        }
        long cooldown = plugin.config().ritualCooldownSeconds() * 1000L;
        long left = lastRitual + cooldown - System.currentTimeMillis();
        if (left > 0) {
            plugin.messages().send(player, "ritual.cooldown", "seconds", (left + 999) / 1000);
            return true;
        }
        Validation validation = validate(clicked);
        if (!validation.valid()) {
            plugin.messages().send(player, "ritual.invalid");
            for (String line : validation.missing()) {
                plugin.messages().sendRaw(player, "ritual.missing-line", "line", line);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 1.0f, 0.6f);
            return true;
        }
        if (plugin.config().ritualRequireKey() && !plugin.items().take(player, "abyss_key", 1)) {
            plugin.messages().send(player, "ritual.no-key");
            return true;
        }
        running = true;
        lastRitual = System.currentTimeMillis();
        plugin.messages().send(player, "ritual.start");
        animate(player, clicked.getLocation());
        return true;
    }

    public Validation validate(Block center) {
        List<String> missing = new ArrayList<>();
        if (center.getType() != Material.SCULK_CATALYST) {
            missing.add(plugin.messages().rawString("ritual.missing-catalyst"));
        }
        if (!center.getRelative(BlockFace.UP).isPassable()) {
            missing.add(plugin.messages().rawString("ritual.missing-space"));
        }
        int ring = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                if (center.getRelative(x, 0, z).getType() == Material.SCULK) {
                    ring++;
                }
            }
        }
        if (ring < 8) {
            missing.add(plugin.messages().rawString("ritual.missing-ring", "count", 8 - ring));
        }
        int corners = 0;
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                if (isShrieker(center.getRelative(x, 0, z))) {
                    corners++;
                }
            }
        }
        if (corners < 4) {
            missing.add(plugin.messages().rawString("ritual.missing-corners", "count", 4 - corners));
        }
        int sides = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block block = center.getRelative(face.getModX() * 2, 0, face.getModZ() * 2);
            if (block.getType() == Material.SCULK) {
                sides++;
            }
        }
        if (sides < 4) {
            missing.add(plugin.messages().rawString("ritual.missing-sides", "count", 4 - sides));
        }
        return new Validation(missing.isEmpty(), missing);
    }

    private boolean isShrieker(Block block) {
        return block.getType() == Material.SCULK_SHRIEKER || block.getType() == Material.SCULK_CATALYST;
    }

    private void animate(Player player, Location center) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick += 10;
                if (tick >= 60) {
                    cancel();
                    running = false;
                    center.getWorld().strikeLightningEffect(center);
                    if (plugin.boss().start(player, center)) {
                        plugin.quests().onRitual(player);
                    }
                    return;
                }
                double radius = 0.4 + tick * 0.06;
                for (int i = 0; i < 12; i++) {
                    double angle = (Math.PI * 2 / 12) * i + tick * 0.1;
                    Location point = center.clone().add(Math.cos(angle) * radius, 0.2 + tick * 0.03,
                            Math.sin(angle) * radius);
                    center.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, point, 1, 0, 0, 0, 0);
                    center.getWorld().spawnParticle(Particle.SCULK_SOUL, point, 1, 0, 0, 0, 0.005);
                }
                center.getWorld().playSound(center, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.6f,
                        0.6f + tick * 0.008f);
                center.getWorld().playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.2f, 0.7f);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public record Validation(boolean valid, List<String> missing) {
    }
}
