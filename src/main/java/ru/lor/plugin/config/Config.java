package ru.lor.plugin.config;

import org.bukkit.World;
import ru.lor.plugin.LorPlugin;

import java.util.List;

/**
 * Typed access to config.yml. Values are read on demand so /lor reload works
 * without rebuilding the manager graph.
 */
public final class Config {

    private final LorPlugin plugin;

    public Config(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private int i(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    private double d(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    private boolean b(String path, boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }

    private long l(String path, long def) {
        return plugin.getConfig().getLong(path, def);
    }

    public boolean isWorldEnabled(World world) {
        if (world == null) {
            return false;
        }
        List<String> worlds = plugin.getConfig().getStringList("settings.worlds");
        if (worlds.isEmpty()) {
            return true;
        }
        for (String name : worlds) {
            if (name.equalsIgnoreCase(world.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean debug() {
        return b("settings.debug", false);
    }

    public boolean hud() {
        return b("settings.hud", true);
    }

    public int saveIntervalSeconds() {
        return Math.max(30, i("settings.save-interval-seconds", 300));
    }

    public int echoPerSculkBlock() {
        return i("echo.per-sculk-block", 2);
    }

    public int echoPerMob() {
        return i("echo.per-mob", 20);
    }

    public int echoPerBoss() {
        return i("echo.per-boss", 500);
    }

    public int echoPerChapter() {
        return i("echo.per-chapter", 100);
    }

    public int echoPerTide() {
        return i("echo.per-tide", 150);
    }

    public boolean paymentsEnabled() {
        return b("payments.enabled", true);
    }

    public int paymentMin() {
        return Math.max(1, i("payments.min-amount", 1));
    }

    /** 0 — без верхнего ограничения. */
    public int paymentMax() {
        return Math.max(0, i("payments.max-amount", 0));
    }

    /** Комиссия сервера в процентах, 0–99. */
    public double paymentTaxPercent() {
        return Math.max(0.0, Math.min(99.0, d("payments.tax-percent", 0.0)));
    }

    public int paymentCooldownSeconds() {
        return Math.max(0, i("payments.cooldown-seconds", 3));
    }

    public boolean paymentLogsEnabled() {
        return b("payments.logs.enabled", true);
    }

    public int paymentLogsKeep() {
        return Math.max(20, i("payments.logs.keep", 500));
    }

    public boolean auctionEnabled() {
        return b("auction.enabled", true);
    }

    public int auctionMaxListings() {
        return Math.max(1, i("auction.max-listings", 5));
    }

    public long auctionMinPrice() {
        return Math.max(1L, l("auction.min-price", 1L));
    }

    public long auctionMaxPrice() {
        return Math.max(1L, l("auction.max-price", 1_000_000L));
    }

    /** Комиссия аукциона в процентах, 0–99. */
    public double auctionTaxPercent() {
        return Math.max(0.0, Math.min(99.0, d("auction.tax-percent", 5.0)));
    }

    public List<String> auctionBlockedMaterials() {
        return plugin.getConfig().getStringList("auction.blocked-materials");
    }

    public boolean corruptionEnabled() {
        return b("corruption.enabled", true);
    }

    public int corruptionRadius() {
        return Math.max(1, i("corruption.radius", 6));
    }

    public double corruptionPerBlock() {
        return d("corruption.per-block", 0.12);
    }

    public int corruptionMaxBlocks() {
        return Math.max(1, i("corruption.max-blocks", 40));
    }

    public double corruptionDeepDark() {
        return d("corruption.deep-dark", 0.8);
    }

    public double corruptionDecay() {
        return d("corruption.decay", 0.35);
    }

    public double corruptionMax() {
        return Math.max(10.0, d("corruption.max", 100.0));
    }

    public double cleanseAmount() {
        return d("corruption.cleanse-amount", 40.0);
    }

    public double corruptionDamage() {
        return d("corruption.damage-at-max", 1.0);
    }

    public double amuletMultiplier() {
        return d("corruption.amulet-multiplier", 0.5);
    }

    public double mobNaturalSpawnChance() {
        return d("mobs.natural-spawn-chance", 0.05);
    }

    public boolean despawnOnDisable() {
        return b("mobs.despawn-on-disable", true);
    }

    public int maxTrackedMobs() {
        return Math.max(10, i("mobs.max-tracked", 60));
    }

    public double bossHealth() {
        return d("boss.health", 320.0);
    }

    public double bossDamage() {
        return d("boss.damage", 7.0);
    }

    public double bossSonicDamage() {
        return d("boss.sonic-damage", 10.0);
    }

    public double bossDespawnRadius() {
        return d("boss.despawn-radius", 64.0);
    }

    public int bossDespawnSeconds() {
        return Math.max(10, i("boss.despawn-seconds", 45));
    }

    public int ritualRequiredChapter() {
        return Math.max(1, i("ritual.required-chapter", 5));
    }

    public boolean ritualRequireKey() {
        return b("ritual.require-key", true);
    }

    public int ritualCooldownSeconds() {
        return Math.max(0, i("ritual.cooldown-seconds", 300));
    }

    public boolean tideEnabled() {
        return b("tide.enabled", true);
    }

    public int tideIntervalMinutes() {
        return Math.max(5, i("tide.interval-minutes", 45));
    }

    public int tideDurationSeconds() {
        return Math.max(60, i("tide.duration-seconds", 300));
    }

    public double tideCorruption() {
        return d("tide.corruption-per-cycle", 1.5);
    }

    public double tideCorruptionMultiplier() {
        return d("tide.corruption-multiplier", 3.0);
    }

    public int tideMobsPerCycle() {
        return Math.max(0, i("tide.mobs-per-cycle", 2));
    }

    public int tideMobCap() {
        return Math.max(1, i("tide.mob-cap", 30));
    }

    public boolean tideSpread() {
        return b("tide.spread-blocks", true);
    }

    public int tideSpreadMaxY() {
        return i("tide.spread-max-y", 0);
    }

    public int tideRewardEcho() {
        return i("tide.reward-echo", 150);
    }

    public boolean shrinesEnabled() {
        return b("worldgen.shrines.enabled", true);
    }

    public double shrineChance() {
        return d("worldgen.shrines.chance", 0.03);
    }

    public int shrineMinY() {
        return i("worldgen.shrines.min-y", -50);
    }

    public int shrineMaxY() {
        return i("worldgen.shrines.max-y", -20);
    }
}
