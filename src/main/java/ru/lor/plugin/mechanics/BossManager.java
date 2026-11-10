package ru.lor.plugin.mechanics;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.util.Keys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Multi-phase "Herald of the Abyss" encounter. */
public final class BossManager {

    private final LorPlugin plugin;
    private Fight fight;

    public BossManager(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return fight != null && fight.boss != null && fight.boss.isValid() && !fight.boss.isDead();
    }

    public boolean start(Player summoner, Location center) {
        if (isActive()) {
            plugin.messages().send(summoner, "boss.already-active");
            return false;
        }
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        Location spawn = center.clone().add(0, 1, 0);
        Warden boss = (Warden) world.spawnEntity(spawn, EntityType.WARDEN);
        boss.setAI(false);
        boss.setPersistent(false);
        boss.setRemoveWhenFarAway(false);
        boss.customName(plugin.messages().raw("boss.name"));
        boss.setCustomNameVisible(true);
        boss.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);

        double health = plugin.config().bossHealth();
        AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        boss.setHealth(Math.min(health, boss.getMaxHealth()));
        set(boss, Attribute.MOVEMENT_SPEED, 0.28);
        set(boss, Attribute.ATTACK_DAMAGE, plugin.config().bossDamage());
        set(boss, Attribute.KNOCKBACK_RESISTANCE, 1.0);
        set(boss, Attribute.FOLLOW_RANGE, 64.0);

        BossBar bar = BossBar.bossBar(plugin.messages().raw("boss.name"), 1.0f,
                BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        Fight state = new Fight(boss, bar);
        this.fight = state;

        for (Player player : onlineNear(world, spawn, 64.0)) {
            state.viewers.add(player.getUniqueId());
            state.participants.add(player.getUniqueId());
            player.showBossBar(bar);
            plugin.messages().title(player, "boss.spawn-title", "boss.spawn-subtitle");
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.7f);
        }
        world.playSound(spawn, Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.6f);
        world.spawnParticle(Particle.SONIC_BOOM, spawn, 30, 1.5, 1.5, 1.5, 0.05);
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
        plugin.getLogger().info("Босс призван игроком " + summoner.getName());
        return true;
    }

    private void set(Warden boss, Attribute attribute, double value) {
        AttributeInstance instance = boss.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void tick() {
        Fight state = fight;
        if (state == null) {
            return;
        }
        Warden boss = state.boss;
        if (!boss.isValid() || boss.isDead()) {
            stop(true);
            return;
        }
        Player nearest = nearest(boss.getLocation(), 64.0);
        if (nearest == null) {
            state.emptyTicks += 5;
            if (state.emptyTicks >= plugin.config().bossDespawnSeconds() * 20) {
                plugin.messages().broadcast("boss.timeout");
                stop(true);
            }
            return;
        }
        state.emptyTicks = 0;

        for (Player player : onlineNear(boss.getWorld(), boss.getLocation(), 64.0)) {
            if (state.viewers.add(player.getUniqueId())) {
                player.showBossBar(state.bar);
            }
            state.participants.add(player.getUniqueId());
        }

        double ratio = Math.max(0.0, Math.min(1.0, boss.getHealth() / boss.getMaxHealth()));
        state.bar.progress((float) ratio);
        int phase = ratio > 0.66 ? 1 : ratio > 0.33 ? 2 : 3;
        if (phase != state.phase) {
            state.phase = phase;
            onPhase(state, phase);
        }

        boss.lookAt(nearest);
        World world = boss.getWorld();
        world.spawnParticle(Particle.SCULK_SOUL, boss.getLocation().add(0, 1.5, 0),
                6, 0.6, 1.0, 0.6, 0.01);
        if (state.tick % 40 == 0) {
            world.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.8f);
        }
        state.tick += 5;

        state.melee -= 5;
        state.sonic -= 5;
        state.pulse -= 5;
        state.summon -= 5;
        state.shockwave -= 5;

        if (state.melee <= 0 && boss.getLocation().distanceSquared(nearest.getLocation()) < 20.0) {
            state.melee = 30;
            nearest.damage(plugin.config().bossDamage(), boss);
            Vector push = nearest.getLocation().toVector()
                    .subtract(boss.getLocation().toVector()).normalize().multiply(0.9).setY(0.35);
            nearest.setVelocity(push);
            nearest.playSound(nearest.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.5f, 0.8f);
        }
        if (state.sonic <= 0) {
            state.sonic = phase == 3 ? 60 : 100;
            sonicBoom(state, nearest);
        }
        if (phase >= 2 && state.pulse <= 0) {
            state.pulse = 160;
            darknessPulse(state);
        }
        if (phase >= 2 && state.summon <= 0) {
            state.summon = phase == 3 ? 160 : 240;
            summonAdds(state, nearest);
        }
        if (phase == 3 && state.shockwave <= 0) {
            state.shockwave = 140;
            shockwave(state);
        }
        if (state.tick >= 200) {
            state.tick = 0;
        }
    }

    private void onPhase(Fight state, int phase) {
        World world = state.boss.getWorld();
        world.playSound(state.boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.5f, phase == 3 ? 0.5f : 0.7f);
        for (Player player : onlineNear(world, state.boss.getLocation(), 64.0)) {
            plugin.messages().send(player, "boss.phase", "phase", phase);
            plugin.messages().title(player, "boss.phase-title", "boss.phase-subtitle", "phase", phase);
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 1.5f, 0.7f);
        }
        if (phase == 3) {
            set(state.boss, Attribute.MOVEMENT_SPEED, 0.34);
            plugin.messages().broadcast("boss.enrage");
        }
    }

    private void sonicBoom(Fight state, Player target) {
        Location from = state.boss.getEyeLocation();
        Location to = target.getEyeLocation();
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 0.5) {
            return;
        }
        direction.normalize();
        World world = state.boss.getWorld();
        for (double step = 0; step < length; step += 0.5) {
            Location point = from.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
        }
        world.playSound(from, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.7f);
        if (target.getLocation().distanceSquared(from) <= 32 * 32) {
            target.damage(plugin.config().bossSonicDamage(), state.boss);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, false, false));
            plugin.messages().actionBar(target, "boss.hit");
        }
    }

    private void darknessPulse(Fight state) {
        World world = state.boss.getWorld();
        world.playSound(state.boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.5f);
        world.spawnParticle(Particle.SCULK_SOUL, state.boss.getLocation().add(0, 1, 0),
                80, 8.0, 1.5, 8.0, 0.02);
        for (Player player : onlineNear(world, state.boss.getLocation(), 28.0)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, true, false, false));
            plugin.messages().actionBar(player, "boss.pulse");
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_LISTENING, 1.4f, 0.6f);
        }
    }

    private void summonAdds(Fight state, Player target) {
        int amount = state.phase == 3 ? 3 : 2;
        for (int i = 0; i < amount; i++) {
            plugin.mobs().spawn(plugin.mobs().findSpawnLocation(target), CustomMob.random(java.util.concurrent.ThreadLocalRandom.current()), target, false);
        }
        state.boss.getWorld().playSound(state.boss.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2.0f, 0.6f);
    }

    private void shockwave(Fight state) {
        World world = state.boss.getWorld();
        world.playSound(state.boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.4f);
        world.spawnParticle(Particle.SONIC_BOOM, state.boss.getLocation().add(0, 1, 0), 40, 2.0, 0.5, 2.0, 0.05);
        for (Player player : onlineNear(world, state.boss.getLocation(), 9.0)) {
            Vector push = player.getLocation().toVector()
                    .subtract(state.boss.getLocation().toVector()).normalize().multiply(1.6).setY(0.6);
            player.setVelocity(push);
            player.damage(plugin.config().bossDamage() + 1.0, state.boss);
            plugin.messages().actionBar(player, "boss.shockwave");
        }
    }

    /** Called from EntityDeathEvent when the warden dies. */
    public void onDeath(Player killer) {
        Fight state = fight;
        if (state == null) {
            return;
        }
        fight = null;
        if (state.task != null) {
            state.task.cancel();
        }
        Location location = state.boss.getLocation();
        for (UUID uuid : state.viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(state.bar);
            }
        }
        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SCULK_SOUL, location.add(0, 2, 0), 150, 2.0, 2.0, 2.0, 0.05);
            world.playSound(location, Sound.ENTITY_WARDEN_DEATH, 2.5f, 0.6f);
        }
        String killerName = killer == null ? "неизвестно" : killer.getName();
        plugin.messages().broadcast("boss.defeated", "player", killerName);
        for (UUID uuid : state.participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            Profile profile = plugin.profiles().get(player);
            profile.addEcho(plugin.config().echoPerBoss());
            profile.addBossKill();
            profile.unlockRelic("heart_of_choir");
            plugin.items().give(player, "heart_of_choir", 1);
            plugin.quests().onBoss(player);
            plugin.messages().send(player, "boss.reward", "echo", plugin.config().echoPerBoss());
            plugin.messages().title(player, "boss.victory-title", "boss.victory-subtitle");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        }
        plugin.profiles().saveAsync();
    }

    public boolean isBoss(org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.BYTE);
    }

    public void stop(boolean silent) {
        Fight state = fight;
        if (state == null) {
            return;
        }
        fight = null;
        if (state.task != null) {
            state.task.cancel();
        }
        for (UUID uuid : state.viewers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(state.bar);
            }
        }
        if (state.boss.isValid()) {
            state.boss.remove();
        }
        if (!silent) {
            plugin.messages().broadcast("boss.cancelled");
        }
    }

    private Player nearest(Location location, double range) {
        if (location.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDistance = range * range;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private List<Player> onlineNear(World world, Location location, double range) {
        List<Player> result = new ArrayList<>();
        if (world == null) {
            return result;
        }
        double squared = range * range;
        for (Player player : world.getPlayers()) {
            if (player.getWorld().equals(world) && player.getLocation().distanceSquared(location) <= squared) {
                result.add(player);
            }
        }
        return result;
    }

    private static final class Fight {
        private final Warden boss;
        private final BossBar bar;
        private final Set<UUID> viewers = new HashSet<>();
        private final Set<UUID> participants = new HashSet<>();
        private BukkitTask task;
        private int phase = 1;
        private int tick;
        private int emptyTicks;
        private int melee = 30;
        private int sonic = 80;
        private int pulse = 200;
        private int summon = 240;
        private int shockwave = 160;

        private Fight(Warden boss, BossBar bar) {
            this.boss = boss;
            this.bar = bar;
        }
    }
}
