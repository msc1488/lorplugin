package ru.lor.plugin.util;

import org.bukkit.NamespacedKey;

/** All persistent-data keys used by the plugin. */
public final class Keys {

    public static final NamespacedKey ITEM = new NamespacedKey("lor", "item");
    public static final NamespacedKey MOB = new NamespacedKey("lor", "mob");
    public static final NamespacedKey BOSS = new NamespacedKey("lor", "boss");
    public static final NamespacedKey TIDE = new NamespacedKey("lor", "tide");

    private Keys() {
    }
}
