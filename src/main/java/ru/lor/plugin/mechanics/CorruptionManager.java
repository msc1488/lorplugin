package ru.lor.plugin.mechanics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.util.Text;

import java.util.List;
import java.util.Random;

/** Sculk corruption: passive gain near sculk, tiers, cleansing and the action bar HUD. */
public final class CorruptionManager {

    private final LorPlugin plugin;
    private final Random random = new Random();

    public CorruptionManager(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Profile profile = plugin.profiles().get(player);
            if (!plugin.config().isWorldEnabled(player.getWorld())
                    || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) {
                hud(player, profile);
                continue;
            }
            if (!plugin.config().corruptionEnabled()) {
                hud(player, profile);
                continue;
            }
            int sculk = countSculk(player.getLocation());
            boolean deepDark = player.getLocation().getBlock().getBiome() == Biome.DEEP_DARK;
            double gain;
            if (sculk > 0 || deepDark) {
                gain = plugin.config().corruptionPerBlock()
                        * Math.min(sculk, plugin.config().corruptionMaxBlocks());
                if (deepDark) {
                    gain += plugin.config().corruptionDeepDark();
                }
                if (plugin.tide().isActive()) {
                    gain *= plugin.config().tideCorruptionMultiplier();
                }
                if (hasAmulet(player)) {
                    gain *= plugin.config().amuletMultiplier();
                }
            } else {
                gain = -plugin.config().corruptionDecay();
            }
            profile.addCorruption(gain);
            applyEffects(player, profile);
            hud(player, profile);
        }
    }

    public void applyEffects(Player player, Profile profile) {
        double value = profile.corruption();
        World world = player.getWorld();
        boolean amulet = hasAmulet(player);

        if (value >= 25.0) {
            if (random.nextDouble() < 0.10) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, true, false, false));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.25f, 1.6f);
            }
            if (random.nextDouble() < 0.25) {
                world.spawnParticle(org.bukkit.Particle.SCULK_SOUL, player.getLocation().add(0, 1.0, 0),
                        2, 0.3, 0.6, 0.3, 0.005);
            }
        }
        if (value >= 50.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 0, true, false, false));
            if (!amulet) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, true, false, false));
            }
        }
        if (value >= 75.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 70, 0, true, false, false));
            if (random.nextDouble() < 0.35) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, true, false, false));
            }
            whisper(player, profile);
        }
        if (value >= plugin.config().corruptionMax()) {
            if (!amulet) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false, false));
            }
            long now = System.currentTimeMillis();
            if (now - profile.lastCorruptionDamage() > 6000L && player.getHealth() > 2.0) {
                profile.lastCorruptionDamage(now);
                player.damage(plugin.config().corruptionDamage());
                player.sendActionBar(plugin.messages().raw("corruption.max-warning"));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.6f);
            }
        }
        if (amulet) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private void whisper(Player player, Profile profile) {
        long now = System.currentTimeMillis();
        if (now - profile.lastWhisper() < 45000L || random.nextDouble() > 0.6) {
            return;
        }
        List<String> whispers = plugin.messages().stringList("whispers");
        if (whispers.isEmpty()) {
            return;
        }
        profile.lastWhisper(now);
        player.sendMessage(Text.mm("<dark_gray><italic>" + whispers.get(random.nextInt(whispers.size()))));
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_LISTENING, 0.5f, 0.7f);
    }

    public void cleanse(Player player, double amount) {
        Profile profile = plugin.profiles().get(player);
        double before = profile.corruption();
        profile.addCorruption(-Math.abs(amount));
        double removed = before - profile.corruption();
        if (removed <= 0.0) {
            plugin.messages().send(player, "corruption.already-clean");
            return;
        }
        plugin.messages().send(player, "corruption.cleansed", "amount", Math.round(removed));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                player.getLocation().add(0, 1.0, 0), 25, 0.5, 0.8, 0.5, 0.02);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        plugin.quests().onCleanse(player);
    }

    public void onDeath(Player player) {
        Profile profile = plugin.profiles().get(player);
        profile.addCorruption(-15.0);
    }

    public boolean hasAmulet(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (plugin.items().is(stack, "silence_amulet")) {
                return true;
            }
        }
        return false;
    }

    public int countSculk(Location location) {
        if (location.getWorld() == null) {
            return 0;
        }
        World world = location.getWorld();
        int radius = plugin.config().corruptionRadius();
        int max = plugin.config().corruptionMaxBlocks();
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material material = world.getBlockAt(bx + x, by + y, bz + z).getType();
                    if (isSculk(material) && ++count >= max) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    public static boolean isSculk(Material material) {
        return material == Material.SCULK
                || material == Material.SCULK_VEIN
                || material == Material.SCULK_SENSOR
                || material == Material.CALIBRATED_SCULK_SENSOR
                || material == Material.SCULK_SHRIEKER
                || material == Material.SCULK_CATALYST;
    }

    private void hud(Player player, Profile profile) {
        if (!plugin.config().hud() || !plugin.isHudEnabled(player)) {
            return;
        }
        int percent = (int) Math.round(profile.corruption());
        NamedTextColor color = color(percent);
        int filled = Math.max(0, Math.min(10, (int) Math.round(percent / 10.0)));
        Component bar = Component.text("|".repeat(filled), color)
                .append(Component.text("|".repeat(10 - filled), NamedTextColor.DARK_GRAY));
        Component line = plugin.messages().raw("hud.echo-label")
                .append(Component.text(" " + profile.echo(), NamedTextColor.AQUA))
                .append(Component.text("   "))
                .append(plugin.messages().raw("hud.sculk-label"))
                .append(Component.text(" "))
                .append(bar)
                .append(Component.text(" " + percent + "%", color));
        player.sendActionBar(line);
    }

    private NamedTextColor color(int percent) {
        if (percent >= 100) {
            return NamedTextColor.DARK_RED;
        }
        if (percent >= 75) {
            return NamedTextColor.RED;
        }
        if (percent >= 50) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        if (percent >= 25) {
            return NamedTextColor.DARK_PURPLE;
        }
        return NamedTextColor.DARK_AQUA;
    }
}
