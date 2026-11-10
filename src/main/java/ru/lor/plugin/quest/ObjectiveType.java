package ru.lor.plugin.quest;

import java.util.Locale;

/** Types of lore objectives understood by chapters.yml. */
public enum ObjectiveType {
    BREAK_SCULK,
    KILL_SCULK,
    KILL_MOB,
    COLLECT_ITEM,
    CRAFT_ITEM,
    VISIT_DEEP_DARK,
    ACTIVATE_RITUAL,
    SURVIVE_TIDE,
    KILL_BOSS,
    CLEANSE;

    public static ObjectiveType parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
