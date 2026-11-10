package ru.lor.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies our menus and keeps the inventory reference required by the API. */
public final class MenuHolder implements InventoryHolder {

    public enum Type {
        MAIN, QUESTS, CHAPTER, CODEX, RELICS, STATS, TOP, RECIPES, RECIPE,
        AUCTION, AUCTION_MY, AUCTION_CONFIRM
    }

    private final Type type;
    private final int page;
    private final String query;
    private final String id;
    private Inventory inventory;

    public MenuHolder(Type type, int page) {
        this(type, page, null, null);
    }

    public MenuHolder(Type type, int page, String query, String id) {
        this.type = type;
        this.page = page;
        this.query = query;
        this.id = id;
    }

    public Type type() {
        return type;
    }

    public int page() {
        return page;
    }

    /** Поисковый запрос аукциона, если есть. */
    public String query() {
        return query;
    }

    /** ID лота для меню подтверждения. */
    public String id() {
        return id;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
