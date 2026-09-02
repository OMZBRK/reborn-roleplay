package com.reborn.shinobiabilities.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * PDC helpers scoped to the <b>ShinobiAbilities</b> namespace (core's
 * {@code PdcAccess} is typed to ShinobiCore and would stamp keys under
 * the wrong namespace). Keys are cached — never re-allocate
 * {@code new NamespacedKey} per call.
 */
public final class Keys {

    private static Plugin plugin;
    private static final Map<String, NamespacedKey> CACHE = new HashMap<>();

    private Keys() {}

    /** Called once from the main class onEnable. */
    public static void init(Plugin owner) {
        plugin = owner;
        CACHE.clear();
    }

    public static NamespacedKey key(String name) {
        return CACHE.computeIfAbsent(name, n -> new NamespacedKey(plugin, n));
    }

    /* ------------------------------------------------------ item PDC */

    public static void setString(ItemMeta meta, String name, String value) {
        if (meta == null || value == null) return;
        meta.getPersistentDataContainer().set(key(name), PersistentDataType.STRING, value);
    }

    public static String getString(ItemStack item, String name) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(key(name), PersistentDataType.STRING);
    }

    public static boolean has(ItemStack item, String name) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(name), PersistentDataType.STRING);
    }
}
