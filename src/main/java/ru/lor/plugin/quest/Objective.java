package ru.lor.plugin.quest;

/** One line of a chapter: what to do and how many times. */
public record Objective(ObjectiveType type, String itemId, int amount, String text) {

    public boolean matches(ObjectiveType type, String extra) {
        if (this.type != type) {
            return false;
        }
        return itemId == null || itemId.equalsIgnoreCase(extra);
    }
}
