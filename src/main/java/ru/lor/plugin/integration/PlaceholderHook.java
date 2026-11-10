package ru.lor.plugin.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.quest.Chapter;
import ru.lor.plugin.quest.QuestManager;
import ru.lor.plugin.util.Text;

import java.util.Locale;

/** Optional PlaceholderAPI bridge: %lor_echo%, %lor_corruption%, ... */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final LorPlugin plugin;

    public PlaceholderHook(LorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lor";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LorPlugin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Profile profile = plugin.profiles().get(player.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "echo" -> String.valueOf(profile.echo());
            case "corruption" -> String.valueOf(Math.round(profile.corruption()));
            case "corruption_raw" -> String.format(Locale.ROOT, "%.1f", profile.corruption());
            case "chapter" -> String.valueOf(profile.chapter());
            case "chapter_roman" -> QuestManager.roman(profile.chapter());
            case "chapter_title" -> {
                Chapter chapter = plugin.quests().chapter(profile.chapter());
                yield chapter == null ? "" : Text.plain(chapter.title());
            }
            case "objective" -> {
                QuestManager.ObjectiveStatus status = plugin.quests().firstIncomplete(profile);
                yield status == null ? "" : status.objective().text();
            }
            case "objective_current" -> {
                QuestManager.ObjectiveStatus status = plugin.quests().firstIncomplete(profile);
                yield status == null ? "" : String.valueOf(status.current());
            }
            case "objective_amount" -> {
                QuestManager.ObjectiveStatus status = plugin.quests().firstIncomplete(profile);
                yield status == null ? "" : String.valueOf(status.objective().amount());
            }
            case "relics" -> String.valueOf(profile.relics().size());
            case "mobs" -> String.valueOf(profile.mobKills());
            case "bosses" -> String.valueOf(profile.bossKills());
            case "blocks" -> String.valueOf(profile.blocksBroken());
            case "tides" -> String.valueOf(profile.tidesSurvived());
            case "top" -> {
                int place = 1;
                for (Profile other : plugin.profiles().top(Integer.MAX_VALUE)) {
                    if (other.uuid().equals(profile.uuid())) {
                        yield String.valueOf(place);
                    }
                    place++;
                }
                yield "?";
            }
            case "tide_active" -> plugin.tide().isActive() ? "да" : "нет";
            case "tide_seconds" -> String.valueOf(plugin.tide().secondsLeft());
            default -> null;
        };
    }
}
