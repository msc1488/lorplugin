package ru.lor.plugin.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.lor.plugin.LorPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only description of the plugin's crafting recipes.
 * Kept in sync with {@link RecipeManager} manually; {@link #all()} respects
 * the recipes.<id> config toggles.
 */
public final class RecipeBook {

    public record Recipe(String id, List<String> grid, List<String> shapeless) {
        public boolean shaped() {
            return grid != null && !grid.isEmpty();
        }
    }

    private final LorPlugin plugin;

    public RecipeBook(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Recipe> all() {
        List<Recipe> list = new ArrayList<>();
        if (enabled("resonator")) {
            list.add(new Recipe("resonator", List.of(
                    "AMETHYST_SHARD", "echo_shard", "AMETHYST_SHARD",
                    "echo_shard", "COMPASS", "echo_shard",
                    "AMETHYST_SHARD", "echo_shard", "AMETHYST_SHARD"), List.of()));
        }
        if (enabled("cleansing_essence")) {
            list.add(new Recipe("cleansing_essence", List.of(), List.of(
                    "HONEY_BOTTLE", "GLOW_BERRIES", "echo_shard")));
        }
        if (enabled("sculk_blade")) {
            list.add(new Recipe("sculk_blade", List.of(
                    ".", "echo_shard", ".",
                    ".", "echo_shard", ".",
                    ".", "NETHERITE_SWORD", "."), List.of()));
        }
        if (enabled("silence_amulet")) {
            list.add(new Recipe("silence_amulet", List.of(
                    ".", "GOLD_INGOT", ".",
                    "GOLD_INGOT", "echo_shard", "GOLD_INGOT",
                    ".", "GOLD_INGOT", "."), List.of()));
        }
        if (enabled("listener_tome")) {
            list.add(new Recipe("listener_tome", List.of(), List.of(
                    "BOOK", "echo_shard")));
        }
        return list;
    }

    public ItemStack stack(String token) {
        if (token == null || token.isBlank() || token.equals(".")) {
            return null;
        }
        if (CustomItems.IDS.contains(token)) {
            return plugin.items().create(token, 1);
        }
        Material material = Material.matchMaterial(token);
        return material == null ? null : new ItemStack(material);
    }

    private boolean enabled(String id) {
        return plugin.getConfig().getBoolean("recipes." + id, true);
    }
}
