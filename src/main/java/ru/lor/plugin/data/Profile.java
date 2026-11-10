package ru.lor.plugin.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent state of one player. */
public final class Profile {

    private final UUID uuid;
    private String name = "";
    private long echo;
    private double corruption;
    private int chapter = 1;
    private final Set<Integer> completed = new HashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Set<String> relics = new HashSet<>();
    private int mobKills;
    private int bossKills;
    private int blocksBroken;
    private int tidesSurvived;
    private long lastSeen = System.currentTimeMillis();

    // runtime-only values
    private long lastWhisper;
    private long lastCorruptionDamage;

    public Profile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public long echo() {
        return echo;
    }

    public void echo(long value) {
        this.echo = Math.max(0L, value);
    }

    public long addEcho(long amount) {
        echo = Math.max(0L, echo + amount);
        return echo;
    }

    public boolean spendEcho(long amount) {
        if (amount <= 0 || echo < amount) {
            return false;
        }
        echo -= amount;
        return true;
    }

    public double corruption() {
        return corruption;
    }

    public void corruption(double value) {
        this.corruption = Math.max(0.0, Math.min(100.0, value));
    }

    public void addCorruption(double amount) {
        corruption(corruption + amount);
    }

    public int chapter() {
        return chapter;
    }

    public void chapter(int value) {
        this.chapter = Math.max(1, value);
    }

    public Set<Integer> completed() {
        return Collections.unmodifiableSet(completed);
    }

    public boolean hasCompleted(int chapterId) {
        return completed.contains(chapterId);
    }

    public void complete(int chapterId) {
        completed.add(chapterId);
    }

    public Map<String, Integer> progress() {
        return progress;
    }

    public int progress(String key) {
        return progress.getOrDefault(key, 0);
    }

    public void progress(String key, int value) {
        progress.put(key, Math.max(0, value));
    }

    public Set<String> relics() {
        return relics;
    }

    public boolean hasRelic(String id) {
        return relics.contains(id);
    }

    public void unlockRelic(String id) {
        relics.add(id);
    }

    public int mobKills() {
        return mobKills;
    }

    public void mobKills(int value) {
        this.mobKills = Math.max(0, value);
    }

    public void addMobKill() {
        mobKills++;
    }

    public int bossKills() {
        return bossKills;
    }

    public void bossKills(int value) {
        this.bossKills = Math.max(0, value);
    }

    public void addBossKill() {
        bossKills++;
    }

    public int blocksBroken() {
        return blocksBroken;
    }

    public void blocksBroken(int value) {
        this.blocksBroken = Math.max(0, value);
    }

    public void addBlockBroken() {
        blocksBroken++;
    }

    public int tidesSurvived() {
        return tidesSurvived;
    }

    public void tidesSurvived(int value) {
        this.tidesSurvived = Math.max(0, value);
    }

    public void addTideSurvived() {
        tidesSurvived++;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long value) {
        this.lastSeen = value;
    }

    public long lastWhisper() {
        return lastWhisper;
    }

    public void lastWhisper(long value) {
        this.lastWhisper = value;
    }

    public long lastCorruptionDamage() {
        return lastCorruptionDamage;
    }

    public void lastCorruptionDamage(long value) {
        this.lastCorruptionDamage = value;
    }

    public void resetStory() {
        completed.clear();
        progress.clear();
        chapter = 1;
    }

    public void resetAll() {
        resetStory();
        relics.clear();
        mobKills = 0;
        bossKills = 0;
        blocksBroken = 0;
        tidesSurvived = 0;
        echo = 0L;
        corruption = 0.0;
    }
}
