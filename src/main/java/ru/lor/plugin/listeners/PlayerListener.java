package ru.lor.plugin.listeners;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;

/** Player-side interactions with lore items and quest tracking. */
public final class PlayerListener implements Listener {

    private final LorPlugin plugin;

    public PlayerListener(LorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Profile profile = plugin.profiles().get(player);
        profile.name(player.getName());
        profile.lastSeen(System.currentTimeMillis());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.quests().welcome(player), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Profile profile = plugin.profiles().get(player);
        profile.lastSeen(System.currentTimeMillis());
        plugin.cooldowns().clear(player.getUniqueId());
        plugin.profiles().saveAsync();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack stack = event.getItem();
        String id = plugin.items().identify(stack);
        if (id == null) {
            return;
        }
        Player player = event.getPlayer();
        switch (id) {
            case "resonator" -> {
                event.setCancelled(true);
                if (event.getClickedBlock() != null
                        && event.getClickedBlock().getType() == Material.SCULK_CATALYST) {
                    plugin.rituals().tryActivate(player, event.getClickedBlock());
                } else {
                    resonatorPulse(player);
                }
            }
            case "listener_tome" -> {
                event.setCancelled(true);
                plugin.gui().openCodex(player, 0);
            }
            case "heart_of_choir" -> {
                event.setCancelled(true);
                plugin.messages().send(player, "items.heart_of_choir.whisper");
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_LISTENING, 1.0f, 0.6f);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL,
                        player.getLocation().add(0, 1.2, 0), 20, 0.4, 0.6, 0.4, 0.01);
            }
            case "abyss_key" -> {
                event.setCancelled(true);
                plugin.messages().send(player, "items.abyss_key.hint");
            }
            case "silence_amulet" -> {
                event.setCancelled(true);
                plugin.messages().send(player, "items.silence_amulet.hint");
            }
            default -> {
            }
        }
    }

    private void resonatorPulse(Player player) {
        if (!plugin.cooldowns().ready(player.getUniqueId(), "resonator")) {
            plugin.messages().send(player, "items.resonator.cooldown",
                    "seconds", plugin.cooldowns().remainingSeconds(player.getUniqueId(), "resonator"));
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.7f, 0.8f);
            return;
        }
        plugin.cooldowns().set(player.getUniqueId(), "resonator", 30_000L);
        int sculk = 0;
        int radius = 12;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (ru.lor.plugin.mechanics.CorruptionManager.isSculk(player.getWorld()
                            .getBlockAt(player.getLocation().getBlockX() + x,
                                    player.getLocation().getBlockY() + y,
                                    player.getLocation().getBlockZ() + z).getType())) {
                        sculk++;
                    }
                }
            }
        }
        int mobs = 0;
        java.util.Collection<Entity> nearby = player.getWorld().getNearbyEntities(player.getLocation(), 24, 16, 24);
        for (Entity entity : nearby) {
            if (plugin.mobs().identify(entity) != null || plugin.boss().isBoss(entity)) {
                entity.setGlowing(true);
                mobs++;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (entity.isValid()) {
                        entity.setGlowing(false);
                    }
                }, 200L);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.4f, 1.6f);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0),
                60, 1.2, 0.8, 1.2, 0.02);
        plugin.messages().send(player, "items.resonator.pulse", "sculk", sculk, "mobs", mobs);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled() || !plugin.items().is(event.getItem(), "cleansing_essence")) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        boolean mainHand = plugin.items().is(player.getInventory().getItemInMainHand(), "cleansing_essence");
        ItemStack hand = mainHand ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (!plugin.items().is(hand, "cleansing_essence")) {
            return;
        }
        if (hand.getAmount() <= 1) {
            if (mainHand) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
            } else {
                player.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
            }
        } else {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.2f);
        plugin.corruption().cleanse(player, plugin.config().cleanseAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        String id = plugin.items().identify(stack);
        if (id == null) {
            return;
        }
        plugin.quests().onCollect(player, id, stack.getAmount());
        plugin.messages().actionBar(player, "items.pickup", "item",
                plugin.messages().rawString("items." + id + ".short"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getInventory().getResult();
        String id = plugin.items().identify(result);
        if (id == null) {
            return;
        }
        plugin.quests().onCraft(player, id);
        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 1.4f);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.corruption().onDeath(player);
        plugin.messages().send(player, "corruption.death");
    }
}
