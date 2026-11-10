package ru.lor.plugin.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.util.Keys;

import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Spawns, drives and rewards the custom sculk mobs. */
public final class MobManager {

    private final LorPlugin plugin;
    private final Random random = new Random();
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();

    public MobManager(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public CustomMob identify(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        return CustomMob.byId(id);
    }

    public boolean isTideMob(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.TIDE, PersistentDataType.BYTE);
    }

    public int trackedCount() {
        return tracked.size();
    }

    public Entity spawn(Location location, CustomMob type, Player target, boolean tide) {
        if (location.getWorld() == null || tracked.size() >= plugin.config().maxTrackedMobs()) {
            return null;
        }
        Entity entity = location.getWorld().spawnEntity(location, type.entityType());
        if (!(entity instanceof Mob mob)) {
            entity.remove();
            return null;
        }
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(true);
        mob.setCanPickupItems(false);
        mob.customName(plugin.messages().raw("mobs." + type.id() + ".name"));
        mob.setCustomNameVisible(true);
        mob.getPersistentDataContainer().set(Keys.MOB, PersistentDataType.STRING, type.id());
        if (tide) {
            mob.getPersistentDataContainer().set(Keys.TIDE, PersistentDataType.BYTE, (byte) 1);
        }
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(type.health());
            mob.setHealth(type.health());
        }
        set(mob, Attribute.MOVEMENT_SPEED, type.speed());
        set(mob, Attribute.ATTACK_DAMAGE, type.damage());
        set(mob, Attribute.FOLLOW_RANGE, 32.0);
        set(mob, Attribute.KNOCKBACK_RESISTANCE, 0.35);
        equip(mob, type);
        if (entity instanceof Vex vex) {
            vex.setLimitedLifetime(true);
            vex.setLimitedLifetimeTicks(20 * 60 * 10);
        }
        if (target != null) {
            mob.setTarget(target);
        }
        tracked.add(mob.getUniqueId());
        location.getWorld().spawnParticle(Particle.SCULK_SOUL, location.clone().add(0, 0.6, 0),
                12, 0.3, 0.4, 0.3, 0.02);
        location.getWorld().playSound(location, Sound.BLOCK_SCULK_SPREAD, 1.0f, 0.8f);
        return mob;
    }

    private void set(Mob mob, Attribute attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void equip(Mob mob, CustomMob type) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) {
            return;
        }
        switch (type) {
            case SENTINEL -> {
                equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            }
            case HERALD -> equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            default -> {
            }
        }
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
    }

    /** Called once per second. */
    public void tick() {
        Iterator<UUID> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                iterator.remove();
                continue;
            }
            CustomMob type = identify(mob);
            if (type == null) {
                iterator.remove();
                continue;
            }
            LivingEntity target = validTarget(mob.getTarget(), mob);
            if (target == null) {
                target = nearestPlayer(mob, 26.0);
                if (target != null) {
                    mob.setTarget(target);
                }
            }
            if (target != null) {
                mob.lookAt(target);
            }
            long ticks = mob.getTicksLived();
            if (target != null && ticks % 20 == 0) {
                switch (type) {
                    case SENTINEL -> {
                        if (ticks % 60 == 0
                                && mob.getLocation().distanceSquared(target.getLocation()) < 256.0) {
                            beam(mob, target);
                        }
                    }
                    case HERALD -> {
                        if (ticks % 200 == 0 && mob.getLocation().distanceSquared(target.getLocation()) > 25.0) {
                            blink(mob, target);
                        }
                    }
                    case DEVOURER -> mob.addPotionEffect(
                            new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, false));
                    default -> {
                    }
                }
            }
        }
    }

    private LivingEntity validTarget(LivingEntity target, Mob mob) {
        if (target == null || !target.isValid() || target.isDead()) {
            return null;
        }
        if (target instanceof Player player) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || !player.getWorld().equals(mob.getWorld())) {
                return null;
            }
        }
        return target;
    }

    private LivingEntity nearestPlayer(Mob mob, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(mob.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void beam(Mob sender, LivingEntity target) {
        Location from = sender.getEyeLocation();
        Location to = target.getEyeLocation();
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length <= 0.1) {
            return;
        }
        direction.normalize();
        World world = sender.getWorld();
        for (double step = 0; step < length; step += 0.7) {
            Location point = from.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
        }
        world.playSound(from, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 1.4f);
        target.damage(4.0, sender);
        target.setVelocity(direction.clone().multiply(0.35).setY(0.15));
    }

    private void blink(Mob mob, LivingEntity target) {
        Location destination = target.getLocation().clone().add(
                random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
        Block block = destination.getBlock();
        if (!block.isPassable() || !block.getRelative(BlockFace.UP).isPassable()) {
            destination = target.getLocation();
        }
        mob.getWorld().spawnParticle(Particle.SCULK_SOUL, mob.getLocation().add(0, 1, 0),
                18, 0.3, 0.5, 0.3, 0.02);
        mob.teleport(destination);
        mob.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
    }

    /** Called from EntityDeathEvent. Returns true if the entity was one of ours. */
    public boolean onDeath(LivingEntity entity, Player killer) {
        CustomMob type = identify(entity);
        if (type == null) {
            return false;
        }
        tracked.remove(entity.getUniqueId());
        entity.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, entity.getLocation().add(0, 1, 0),
                20, 0.4, 0.6, 0.4, 0.01);
        if (killer == null) {
            return true;
        }
        Profile profile = plugin.profiles().get(killer);
        long echo = plugin.config().echoPerMob();
        boolean overloaded = profile.corruption() >= plugin.config().corruptionMax();
        if (overloaded) {
            echo *= 2;
        }
        profile.addEcho(echo);
        profile.addMobKill();
        plugin.quests().onKillMob(killer, type.id());

        plugin.messages().actionBar(killer, "mobs.echo-reward", "echo", echo,
                "mob", plugin.messages().rawString("mobs." + type.id() + ".short"));
        killer.playSound(killer.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.7f, 1.2f);

        if (random.nextDouble() < 0.45) {
            entity.getWorld().dropItemNaturally(entity.getLocation(),
                    plugin.items().create("echo_shard", 1));
        }
        if (type == CustomMob.SENTINEL && random.nextDouble() < 0.20) {
            entity.getWorld().dropItemNaturally(entity.getLocation(),
                    plugin.items().create("cleansing_essence", 1));
        }
        return true;
    }

    public Player nearestPlayer(Location location, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : location.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    public Location findSpawnLocation(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = base.getBlockX() + random.nextInt(17) - 8;
            int z = base.getBlockZ() + random.nextInt(17) - 8;
            int y = base.getBlockY() + random.nextInt(7) - 3;
            Block block = world.getBlockAt(x, y, z);
            if (block.isPassable() && block.getRelative(BlockFace.UP).isPassable()
                    && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                return block.getLocation().add(0.5, 0, 0.5);
            }
        }
        return base;
    }

    public void spawnAround(Player player, int amount) {
        for (int i = 0; i < amount; i++) {
            CustomMob type = CustomMob.random(random);
            spawn(findSpawnLocation(player), type, player, true);
        }
    }

    public void spawnAround(Player player, CustomMob type, int amount) {
        for (int i = 0; i < amount; i++) {
            spawn(findSpawnLocation(player), type, player, true);
        }
    }

    public boolean tryNaturalReplacement(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return false;
        }
        if (tracked.size() >= plugin.config().maxTrackedMobs()) {
            return false;
        }
        Location location = event.getLocation();
        if (!plugin.config().isWorldEnabled(location.getWorld())) {
            return false;
        }
        if (location.getBlock().getBiome() != Biome.DEEP_DARK) {
            return false;
        }
        if (random.nextDouble() > plugin.config().mobNaturalSpawnChance()) {
            return false;
        }
        EntityType type = event.getEntityType();
        if (type != EntityType.ZOMBIE && type != EntityType.HUSK && type != EntityType.SPIDER
                && type != EntityType.SKELETON && type != EntityType.CREEPER) {
            return false;
        }
        event.setCancelled(true);
        Location spawnAt = location.clone();
        Bukkit.getScheduler().runTask(plugin, () -> spawn(spawnAt, CustomMob.random(random), null, false));
        return true;
    }

    public void despawnAll(boolean tideOnly) {
        java.util.Iterator<UUID> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) {
                iterator.remove();
                continue;
            }
            if (tideOnly && !isTideMob(entity)) {
                continue;
            }
            entity.getWorld().spawnParticle(Particle.SCULK_SOUL, entity.getLocation().add(0, 0.8, 0),
                    10, 0.3, 0.5, 0.3, 0.01);
            entity.remove();
            iterator.remove();
        }
    }

    public String describe(Entity entity) {
        CustomMob type = identify(entity);
        return type == null ? "unknown" : type.id().toLowerCase(Locale.ROOT);
    }
}
