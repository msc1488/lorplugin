package ru.lor.plugin.listeners;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.mechanics.CustomMob;

/** Combat hooks: custom mob drops, blade bonus, corruption on hit. */
public final class EntityListener implements Listener {

    private final LorPlugin plugin;

    public EntityListener(LorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (plugin.boss().isBoss(entity)) {
            event.getDrops().clear();
            plugin.boss().onDeath(killer);
            return;
        }
        if (plugin.mobs().identify(entity) != null) {
            event.getDrops().clear();
            plugin.mobs().onDeath(entity, killer);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = resolve(event.getDamager());
        if (event.getEntity() instanceof Player victim && damager instanceof Mob mob) {
            CustomMob type = plugin.mobs().identify(mob);
            if (type != null) {
                applyCorruption(victim, type);
            }
        }
        if (damager instanceof Player player && event.getEntity() instanceof LivingEntity victim) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (plugin.items().is(hand, "sculk_blade")) {
                boolean sculkCreature = plugin.mobs().identify(victim) != null
                        || plugin.boss().isBoss(victim);
                if (sculkCreature) {
                    event.setDamage(event.getDamage() + 4.0);
                    victim.getWorld().spawnParticle(Particle.SCULK_SOUL,
                            victim.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.01);
                    victim.getWorld().playSound(victim.getLocation(),
                            Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.6f, 1.6f);
                }
            }
        }
    }

    private Entity resolve(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private void applyCorruption(Player player, CustomMob type) {
        Profile profile = plugin.profiles().get(player);
        switch (type) {
            case CRAWLER -> {
                profile.addCorruption(6.0);
                plugin.messages().actionBar(player, "mobs.hit.crawler");
            }
            case SENTINEL -> {
                profile.addCorruption(3.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false, false));
                plugin.messages().actionBar(player, "mobs.hit.sentinel");
            }
            case DEVOURER -> {
                long drained = Math.min(5L, profile.echo());
                if (drained > 0) {
                    profile.addEcho(-drained);
                    plugin.messages().actionBar(player, "mobs.hit.devourer", "echo", drained);
                }
            }
            case HERALD -> {
                profile.addCorruption(4.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, true, false, false));
                plugin.messages().actionBar(player, "mobs.hit.herald");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        plugin.mobs().tryNaturalReplacement(event);
    }
}
