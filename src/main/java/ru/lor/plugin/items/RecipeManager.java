package ru.lor.plugin.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import ru.lor.plugin.LorPlugin;

import java.util.ArrayList;
import java.util.List;

/** Registers crafting recipes for the custom items. */
public final class RecipeManager {

    private final LorPlugin plugin;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeManager(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        unregisterAll();
        if (enabled("resonator")) {
            ItemStack result = plugin.items().create("resonator", 1);
            ShapedRecipe recipe = new ShapedRecipe(key("resonator"), result);
            recipe.shape("ABA", "BCB", "ABA");
            recipe.setIngredient('A', Material.AMETHYST_SHARD);
            recipe.setIngredient('B', new RecipeChoice.ExactChoice(plugin.items().create("echo_shard", 1)));
            recipe.setIngredient('C', Material.COMPASS);
            add(recipe);
        }
        if (enabled("cleansing_essence")) {
            ShapelessRecipe recipe = new ShapelessRecipe(key("cleansing_essence"),
                    plugin.items().create("cleansing_essence", 1));
            recipe.addIngredient(Material.HONEY_BOTTLE);
            recipe.addIngredient(Material.GLOW_BERRIES);
            recipe.addIngredient(new RecipeChoice.ExactChoice(plugin.items().create("echo_shard", 1)));
            add(recipe);
        }
        if (enabled("sculk_blade")) {
            ShapedRecipe recipe = new ShapedRecipe(key("sculk_blade"),
                    plugin.items().create("sculk_blade", 1));
            recipe.shape(" B ", " B ", " S ");
            recipe.setIngredient('B', new RecipeChoice.ExactChoice(plugin.items().create("echo_shard", 1)));
            recipe.setIngredient('S', Material.NETHERITE_SWORD);
            add(recipe);
        }
        if (enabled("silence_amulet")) {
            ShapedRecipe recipe = new ShapedRecipe(key("silence_amulet"),
                    plugin.items().create("silence_amulet", 1));
            recipe.shape(" A ", "ABA", " A ");
            recipe.setIngredient('A', Material.GOLD_INGOT);
            recipe.setIngredient('B', new RecipeChoice.ExactChoice(plugin.items().create("echo_shard", 1)));
            add(recipe);
        }
        if (enabled("listener_tome")) {
            ShapelessRecipe recipe = new ShapelessRecipe(key("listener_tome"),
                    plugin.items().create("listener_tome", 1));
            recipe.addIngredient(Material.BOOK);
            recipe.addIngredient(new RecipeChoice.ExactChoice(plugin.items().create("echo_shard", 1)));
            add(recipe);
        }
        plugin.getLogger().info("Зарегистрировано рецептов: " + registered.size());
    }

    private void add(org.bukkit.inventory.Recipe recipe) {
        if (Bukkit.addRecipe(recipe)) {
            registered.add(((org.bukkit.Keyed) recipe).getKey());
        }
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }

    private boolean enabled(String id) {
        return plugin.getConfig().getBoolean("recipes." + id, true);
    }

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id);
    }
}
