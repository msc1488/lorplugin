package ru.lor.plugin.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.config.Messages;
import ru.lor.plugin.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Factory and registry for all custom items. */
public final class CustomItems {

    public static final List<String> IDS = List.of(
            "echo_shard",
            "resonator",
            "cleansing_essence",
            "sculk_blade",
            "abyss_key",
            "heart_of_choir",
            "silence_amulet",
            "listener_tome"
    );

    private final LorPlugin plugin;

    public CustomItems(LorPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    public ItemStack create(String id, int amount) {
        Material material = material(id);
        if (material == null) {
            return null;
        }
        int capped = Math.max(1, Math.min(amount, material.getMaxStackSize()));
        ItemStack stack = new ItemStack(material, capped);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(messages().raw("items." + id + ".name")
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (Component line : messages().list("items." + id + ".lore")) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);

        switch (id) {
            case "echo_shard", "abyss_key", "heart_of_choir", "silence_amulet" ->
                    meta.setEnchantmentGlintOverride(true);
            case "sculk_blade" -> {
                meta.addEnchant(Enchantment.SHARPNESS, 4, true);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            }
            case "resonator" -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            case "listener_tome" -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            default -> {
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public String identify(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public boolean is(ItemStack stack, String id) {
        return id.equals(identify(stack));
    }

    public boolean isAny(ItemStack stack) {
        return identify(stack) != null;
    }

    public void give(Player player, String id, int amount) {
        ItemStack stack = create(id, amount);
        if (stack == null) {
            return;
        }
        player.getInventory().addItem(stack).forEach((slot, leftover) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public boolean has(Player player, String id) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (is(stack, id)) {
                return true;
            }
        }
        return false;
    }

    public int count(Player player, String id) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (is(stack, id)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public boolean take(Player player, String id, int amount) {
        if (count(player, id) < amount) {
            return false;
        }
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length && left > 0; index++) {
            ItemStack stack = contents[index];
            if (!is(stack, id)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                player.getInventory().setItem(index, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        return true;
    }

    private Material material(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "echo_shard" -> Material.ECHO_SHARD;
            case "resonator" -> Material.RECOVERY_COMPASS;
            case "cleansing_essence" -> Material.HONEY_BOTTLE;
            case "sculk_blade" -> Material.NETHERITE_SWORD;
            case "abyss_key" -> Material.TRIAL_KEY;
            case "heart_of_choir" -> Material.HEART_OF_THE_SEA;
            case "silence_amulet" -> Material.AMETHYST_SHARD;
            case "listener_tome" -> Material.BOOK;
            default -> null;
        };
    }
}
