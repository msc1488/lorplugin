package ru.lor.plugin.quest;

import java.util.List;

/** A lore chapter loaded from chapters.yml. */
public final class Chapter {

    private final int id;
    private final String title;
    private final List<String> lore;
    private final List<Objective> objectives;
    private final int rewardEcho;
    private final List<String> rewardItems;
    private final String broadcast;

    public Chapter(int id, String title, List<String> lore, List<Objective> objectives,
                   int rewardEcho, List<String> rewardItems, String broadcast) {
        this.id = id;
        this.title = title;
        this.lore = lore;
        this.objectives = objectives;
        this.rewardEcho = rewardEcho;
        this.rewardItems = rewardItems;
        this.broadcast = broadcast;
    }

    public int id() {
        return id;
    }

    public String title() {
        return title;
    }

    public List<String> lore() {
        return lore;
    }

    public List<Objective> objectives() {
        return objectives;
    }

    public int rewardEcho() {
        return rewardEcho;
    }

    public List<String> rewardItems() {
        return rewardItems;
    }

    public String broadcast() {
        return broadcast;
    }
}
