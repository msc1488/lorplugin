package ru.lor.plugin.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.lor.plugin.LorPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** MiniMessage-based messages loaded from messages.yml. */
public final class Messages {

    private final LorPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration file;
    private YamlConfiguration defaults;
    private String prefix = "";

    public Messages(LorPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        this.defaults = loadDefaults();
        this.file = YamlConfiguration.loadConfiguration(target);

        int merged = mergeMissingKeys();
        if (merged > 0) {
            try {
                file.save(target);
                plugin.getLogger().info("messages.yml: добавлено новых ключей — " + merged + ".");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Не удалось сохранить messages.yml, новые ключи будут браться из ресурсов плагина.", e);
            }
        }

        this.prefix = file.getString("prefix", "");
    }

    /** Loads the messages bundled inside the jar (UTF-8, never null unless the resource is missing). */
    private YamlConfiguration loadDefaults() {
        InputStream stream = plugin.getResource("messages.yml");
        if (stream == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось прочитать встроенный messages.yml.", e);
            return null;
        }
    }

    /** Copies every leaf key from the bundled resource that the server-side file does not have yet. */
    private int mergeMissingKeys() {
        if (defaults == null) {
            return 0;
        }
        int merged = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || file.contains(key)) {
                continue;
            }
            file.set(key, defaults.get(key));
            merged++;
        }
        return merged;
    }

    private String defaultValue(String key) {
        return defaults == null ? null : defaults.getString(key);
    }

    private List<String> defaultList(String key) {
        return defaults == null ? List.of() : defaults.getStringList(key);
    }

    public FileConfiguration file() {
        return file;
    }

    public ConfigurationSection section(String path) {
        return file.getConfigurationSection(path);
    }

    public boolean has(String key) {
        return file.isString(key) || file.isList(key);
    }

    private String apply(String input, Object... placeholders) {
        String out = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]).replace("<", "").replace(">", "");
            out = out.replace("{" + name + "}", value);
        }
        return out;
    }

    public String rawString(String key, Object... placeholders) {
        String value = file.getString(key, "<red>[missing message: " + key + "]");
        return apply(value, placeholders);
    }

    public Component raw(String key, Object... placeholders) {
        return mm.deserialize(rawString(key, placeholders));
    }

    public Component prefixed(String key, Object... placeholders) {
        return mm.deserialize(prefix + rawString(key, placeholders));
    }

    public Component prefixedTemplate(String template, Object... placeholders) {
        return mm.deserialize(prefix + apply(template, placeholders));
    }

    public List<Component> list(String key, Object... placeholders) {
        List<Component> result = new ArrayList<>();
        for (String line : file.getStringList(key)) {
            result.add(mm.deserialize(apply(line, placeholders)));
        }
        return result;
    }

    public List<String> stringList(String key, Object... placeholders) {
        List<String> result = new ArrayList<>();
        for (String line : file.getStringList(key)) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }

    public void sendRaw(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(raw(key, placeholders));
    }

    public void broadcast(String key, Object... placeholders) {
        plugin.getServer().broadcast(prefixed(key, placeholders));
    }

    public void title(Player player, String titleKey, String subtitleKey, Object... placeholders) {
        Component title = raw(titleKey, placeholders);
        Component subtitle = raw(subtitleKey, placeholders);
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800))));
    }

    public void actionBar(Player player, String key, Object... placeholders) {
        player.sendActionBar(raw(key, placeholders));
    }
}
