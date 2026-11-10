package ru.lor.plugin.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.lor.plugin.LorPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Loads and saves player profiles in data/players.yml. */
public final class ProfileStore {

    private final LorPlugin plugin;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final File file;
    private volatile boolean saving;
    private volatile boolean pending;

    public ProfileStore(LorPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/players.yml");
    }

    public void loadAll() {
        profiles.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Пропущен некорректный UUID в players.yml: " + key);
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Profile profile = new Profile(uuid);
            profile.name(section.getString("name", ""));
            profile.echo(section.getLong("echo", 0L));
            profile.corruption(section.getDouble("corruption", 0.0));
            profile.chapter(section.getInt("chapter", 1));
            for (Integer id : section.getIntegerList("completed")) {
                profile.complete(id);
            }
            ConfigurationSection progress = section.getConfigurationSection("progress");
            if (progress != null) {
                for (String progressKey : progress.getKeys(false)) {
                    profile.progress(progressKey, progress.getInt(progressKey, 0));
                }
            }
            for (String relic : section.getStringList("relics")) {
                profile.unlockRelic(relic);
            }
            profile.mobKills(section.getInt("stats.mobs", 0));
            profile.bossKills(section.getInt("stats.bosses", 0));
            profile.blocksBroken(section.getInt("stats.blocks", 0));
            profile.tidesSurvived(section.getInt("stats.tides", 0));
            profile.lastSeen(section.getLong("last-seen", System.currentTimeMillis()));
            profiles.put(uuid, profile);
        }
        plugin.getLogger().info("Загружено профилей: " + profiles.size());
    }

    public Profile get(Player player) {
        Profile profile = profiles.computeIfAbsent(player.getUniqueId(), Profile::new);
        profile.name(player.getName());
        return profile;
    }

    public Profile get(UUID uuid) {
        return profiles.computeIfAbsent(uuid, Profile::new);
    }

    public Profile find(String name) {
        for (Profile profile : profiles.values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? null : get(online);
    }

    public Collection<Profile> all() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public java.util.List<Profile> top(int limit) {
        java.util.List<Profile> list = new ArrayList<>(profiles.values());
        list.sort((a, b) -> Long.compare(b.echo(), a.echo()));
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection players = out.createSection("players");
        for (Profile profile : profiles.values()) {
            ConfigurationSection section = players.createSection(profile.uuid().toString());
            section.set("name", profile.name());
            section.set("echo", profile.echo());
            section.set("corruption", Math.round(profile.corruption() * 100.0) / 100.0);
            section.set("chapter", profile.chapter());
            section.set("completed", new ArrayList<>(profile.completed()));
            section.set("progress", new LinkedHashMap<>(profile.progress()));
            section.set("relics", new ArrayList<>(profile.relics()));
            section.set("stats.mobs", profile.mobKills());
            section.set("stats.bosses", profile.bossKills());
            section.set("stats.blocks", profile.blocksBroken());
            section.set("stats.tides", profile.tidesSurvived());
            section.set("last-seen", profile.lastSeen());
        }
        return out;
    }

    public void saveAsync() {
        if (saving) {
            pending = true;
            return;
        }
        saving = true;
        YamlConfiguration snapshot = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write(snapshot);
            } finally {
                saving = false;
                if (pending) {
                    pending = false;
                    if (plugin.isEnabled()) {
                        Bukkit.getScheduler().runTask(plugin, this::saveAsync);
                    }
                }
            }
        });
    }

    public void saveNow() {
        write(snapshot());
    }

    private synchronized void write(YamlConfiguration snapshot) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку data/");
            }
            snapshot.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить players.yml: " + exception.getMessage());
        }
    }
}
