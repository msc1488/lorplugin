package ru.lor.plugin.quest;

import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Chapter progression, objective tracking and rewards. */
public final class QuestManager {

    private final LorPlugin plugin;
    private final Map<Integer, Chapter> chapters = new TreeMap<>();
    private final Set<UUID> welcomed = new HashSet<>();

    public QuestManager(LorPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        chapters.clear();
        File file = new File(plugin.getDataFolder(), "chapters.yml");
        if (!file.exists()) {
            plugin.saveResource("chapters.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("chapters");
        if (root == null) {
            plugin.getLogger().warning("chapters.yml не содержит секции chapters!");
            return;
        }
        for (String key : root.getKeys(false)) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Некорректный номер главы: " + key);
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            List<Objective> objectives = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("objectives")) {
                String rawType = map.get("type") == null ? "" : String.valueOf(map.get("type"));
                ObjectiveType type = ObjectiveType.parse(rawType);
                if (type == null) {
                    plugin.getLogger().warning("Глава " + id + ": неизвестный тип цели " + rawType);
                    continue;
                }
                String item = map.get("item") == null ? null : String.valueOf(map.get("item"));
                int amount = 1;
                if (map.get("amount") != null) {
                    try {
                        amount = Integer.parseInt(String.valueOf(map.get("amount")));
                    } catch (NumberFormatException exception) {
                        plugin.getLogger().warning("Глава " + id + ": некорректное количество " + map.get("amount"));
                    }
                }
                String text = map.get("text") == null ? rawType : String.valueOf(map.get("text"));
                objectives.add(new Objective(type, item, Math.max(1, amount), text));
            }
            int rewardEcho = section.getInt("rewards.echo", 0);
            List<String> rewardItems = section.getStringList("rewards.items");
            Chapter chapter = new Chapter(id,
                    section.getString("title", "<dark_aqua>Глава " + id),
                    section.getStringList("lore"),
                    objectives,
                    rewardEcho,
                    rewardItems,
                    section.getString("broadcast", ""));
            chapters.put(id, chapter);
        }
        plugin.getLogger().info("Загружено глав: " + chapters.size());
    }

    public Map<Integer, Chapter> chapters() {
        return chapters;
    }

    public Chapter chapter(int id) {
        return chapters.get(id);
    }

    public Chapter current(Profile profile) {
        return chapters.get(profile.chapter());
    }

    public List<ObjectiveStatus> statuses(Profile profile) {
        Chapter chapter = current(profile);
        if (chapter == null) {
            return new ArrayList<>();
        }
        return statuses(profile, chapter);
    }

    public List<ObjectiveStatus> statuses(Profile profile, Chapter chapter) {
        List<ObjectiveStatus> list = new ArrayList<>();
        if (chapter == null) {
            return list;
        }
        int index = 0;
        for (Objective objective : chapter.objectives()) {
            list.add(new ObjectiveStatus(index, objective,
                    Math.min(objective.amount(), profile.progress(chapter.id() + ":" + index))));
            index++;
        }
        return list;
    }

    public ObjectiveStatus firstIncomplete(Profile profile) {
        for (ObjectiveStatus status : statuses(profile)) {
            if (!status.done()) {
                return status;
            }
        }
        return null;
    }

    public void progress(Player player, ObjectiveType type, String extra, int delta) {
        if (player == null || delta <= 0) {
            return;
        }
        Profile profile = plugin.profiles().get(player);
        Chapter chapter = current(profile);
        if (chapter == null) {
            return;
        }
        boolean changed = false;
        int index = 0;
        for (Objective objective : chapter.objectives()) {
            String key = chapter.id() + ":" + index;
            index++;
            if (!objective.matches(type, extra)) {
                continue;
            }
            int current = profile.progress(key);
            if (current >= objective.amount()) {
                continue;
            }
            int next = Math.min(objective.amount(), current + delta);
            profile.progress(key, next);
            changed = true;
            if (next >= objective.amount()) {
                plugin.messages().send(player, "chapter.objective-done", "text", objective.text());
                player.playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.9f, 1.5f);
            }
        }
        if (changed) {
            checkComplete(player, profile, chapter);
        }
    }

    private void checkComplete(Player player, Profile profile, Chapter chapter) {
        int index = 0;
        for (Objective objective : chapter.objectives()) {
            if (profile.progress(chapter.id() + ":" + index) < objective.amount()) {
                return;
            }
            index++;
        }
        complete(player, profile, chapter);
    }

    private void complete(Player player, Profile profile, Chapter chapter) {
        if (profile.hasCompleted(chapter.id())) {
            return;
        }
        profile.complete(chapter.id());
        int nextId = chapter.id() + 1;
        Chapter next = chapters.get(nextId);
        profile.chapter(next != null ? nextId : chapter.id());
        long reward = chapter.rewardEcho() + plugin.config().echoPerChapter();
        profile.addEcho(reward);
        for (String item : chapter.rewardItems()) {
            plugin.items().give(player, item, 1);
            profile.unlockRelic(item);
        }
        String plainTitle = Text.plain(chapter.title());
        plugin.messages().title(player, "chapter.complete-title", "chapter.complete-subtitle",
                "title", plainTitle, "roman", roman(chapter.id()));
        plugin.messages().send(player, "chapter.complete", "title", plainTitle, "echo", reward);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        String template = chapter.broadcast();
        if (template != null && !template.isBlank()) {
            plugin.getServer().broadcast(plugin.messages().prefixedTemplate(template,
                    "player", player.getName(), "title", plainTitle, "chapter", roman(chapter.id())));
        } else {
            plugin.messages().broadcast("chapter.broadcast", "player", player.getName(), "title", plainTitle);
        }

        if (next != null) {
            start(player, next);
        } else {
            messagesFinal(player, profile);
        }
        plugin.profiles().saveAsync();
    }

    private void messagesFinal(Player player, Profile profile) {
        profile.addEcho(1000);
        plugin.messages().title(player, "chapter.final-title", "chapter.final-subtitle");
        for (String line : plugin.messages().stringList("chapter.final-lines")) {
            player.sendMessage(Text.mm(line));
        }
        plugin.messages().broadcast("chapter.final-broadcast", "player", player.getName());
    }

    public void start(Player player, Chapter chapter) {
        plugin.messages().title(player, "chapter.started-title", "chapter.started-subtitle",
                "roman", roman(chapter.id()), "title", Text.plain(chapter.title()));
        plugin.messages().sendRaw(player, "chapter.started", "title", Text.plain(chapter.title()));
        for (String line : chapter.lore()) {
            player.sendMessage(Text.mm("<dark_gray>» <gray>" + line));
        }
        plugin.messages().sendRaw(player, "chapter.objectives-header");
        for (Objective objective : chapter.objectives()) {
            plugin.messages().sendRaw(player, "chapter.objective-line",
                    "text", objective.text(), "amount", objective.amount());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.6f, 0.7f);
    }

    public void welcome(Player player) {
        if (!welcomed.add(player.getUniqueId())) {
            return;
        }
        Profile profile = plugin.profiles().get(player);
        Chapter chapter = current(profile);
        if (chapter == null) {
            return;
        }
        ObjectiveStatus status = firstIncomplete(profile);
        if (status != null) {
            plugin.messages().actionBar(player, "hud.objective",
                    "text", status.objective().text(),
                    "current", status.current(),
                    "amount", status.objective().amount());
        }
        if (profile.progress().isEmpty() && profile.completed().isEmpty()) {
            plugin.messages().send(player, "quest.new-player");
        }
    }

    public void onBreakSculk(Player player) {
        progress(player, ObjectiveType.BREAK_SCULK, null, 1);
    }

    public void onKillMob(Player player, String mobId) {
        progress(player, ObjectiveType.KILL_SCULK, null, 1);
        progress(player, ObjectiveType.KILL_MOB, mobId, 1);
    }

    public void onCollect(Player player, String itemId, int amount) {
        progress(player, ObjectiveType.COLLECT_ITEM, itemId, amount);
    }

    public void onCraft(Player player, String itemId) {
        progress(player, ObjectiveType.CRAFT_ITEM, itemId, 1);
    }

    public void onDeepDark(Player player) {
        progress(player, ObjectiveType.VISIT_DEEP_DARK, null, 1);
    }

    public void onRitual(Player player) {
        progress(player, ObjectiveType.ACTIVATE_RITUAL, null, 1);
    }

    public void onTide(Player player) {
        progress(player, ObjectiveType.SURVIVE_TIDE, null, 1);
    }

    public void onBoss(Player player) {
        progress(player, ObjectiveType.KILL_BOSS, null, 1);
    }

    public void onCleanse(Player player) {
        progress(player, ObjectiveType.CLEANSE, null, 1);
    }

    /** Admin helper: instantly finish a chapter (used by /lor admin complete). */
    public boolean forceComplete(Player player, int chapterId) {
        Chapter chapter = chapters.get(chapterId);
        if (chapter == null) {
            return false;
        }
        Profile profile = plugin.profiles().get(player);
        profile.chapter(chapterId);
        int index = 0;
        for (Objective objective : chapter.objectives()) {
            profile.progress(chapterId + ":" + index, objective.amount());
            index++;
        }
        complete(player, profile, chapter);
        return true;
    }

    public boolean setChapter(Player player, int chapterId) {
        Chapter chapter = chapters.get(chapterId);
        if (chapter == null) {
            return false;
        }
        Profile profile = plugin.profiles().get(player);
        profile.chapter(chapterId);
        start(player, chapter);
        return true;
    }

    public static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    /** Snapshot for GUIs and the HUD. */
    public record ObjectiveStatus(int index, Objective objective, int current) {
        public boolean done() {
            return current >= objective.amount();
        }

        public Component bar() {
            return Text.mm((done() ? "<green>" : "<dark_aqua>") + current + "<gray>/<white>" + objective.amount());
        }
    }
}
