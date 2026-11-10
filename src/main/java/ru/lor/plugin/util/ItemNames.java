package ru.lor.plugin.util;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.lor.plugin.LorPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Русские названия блоков и предметов для поиска на аукционе.
 * Словарь лежит в ресурсе items-ru.yml (ключ — имя Material в нижнем регистре).
 */
public final class ItemNames {

    private final Map<String, String> russian = new HashMap<>();

    public ItemNames(LorPlugin plugin) {
        InputStream stream = plugin.getResource("items-ru.yml");
        if (stream == null) {
            plugin.getLogger().warning("Не найден ресурс items-ru.yml — поиск на русском недоступен.");
        } else {
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                for (String key : yaml.getKeys(false)) {
                    String value = yaml.getString(key);
                    if (value != null && !value.isBlank()) {
                        russian.put(key, value);
                    }
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Не удалось прочитать items-ru.yml: " + exception.getMessage());
            }
        }
    }

    /** Русское название материала или null, если его нет в словаре. */
    public String russian(Material material) {
        if (material == null) {
            return null;
        }
        return russian.get(material.name().toLowerCase(Locale.ROOT));
    }

    /** Приводит строку к виду, в котором сравниваются запрос и названия: нижний регистр, ё → е. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /**
     * Совпадение названия с уже нормализованным запросом.
     * Кроме прямого вхождения учитывает русские окончания: «эхо» найдёт «Осколок эха».
     */
    public static boolean matches(String name, String needle) {
        String normalized = normalize(name);
        if (needle.isEmpty() || normalized.contains(needle)) {
            return true;
        }
        String stem = stem(needle);
        return stem != null && normalized.contains(stem);
    }

    /** Отрезает русское окончание-гласную, чтобы искать по основе слова. */
    private static String stem(String needle) {
        if (needle.length() < 3) {
            return null;
        }
        char last = needle.charAt(needle.length() - 1);
        if ("аеёиоуыэюяйь".indexOf(last) < 0) {
            return null;
        }
        return needle.substring(0, needle.length() - 1);
    }
}
