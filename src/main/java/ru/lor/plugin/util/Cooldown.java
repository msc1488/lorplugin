package ru.lor.plugin.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory cooldowns, keyed by player and feature name. */
public final class Cooldown {

    private final Map<UUID, Map<String, Long>> data = new HashMap<>();

    public boolean ready(UUID player, String key) {
        return remaining(player, key) <= 0L;
    }

    public long remaining(UUID player, String key) {
        Map<String, Long> map = data.get(player);
        if (map == null) {
            return 0L;
        }
        Long until = map.get(key);
        if (until == null) {
            return 0L;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0L) {
            map.remove(key);
            return 0L;
        }
        return left;
    }

    public long remainingSeconds(UUID player, String key) {
        return (remaining(player, key) + 999L) / 1000L;
    }

    public void set(UUID player, String key, long millis) {
        data.computeIfAbsent(player, ignored -> new HashMap<>()).put(key, System.currentTimeMillis() + millis);
    }

    public void clear(UUID player) {
        data.remove(player);
    }
}
