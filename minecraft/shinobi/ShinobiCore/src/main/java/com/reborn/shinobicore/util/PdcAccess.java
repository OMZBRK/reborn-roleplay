package com.reborn.shinobicore.util;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Type-aware {@link PersistentDataContainer} accessors that hide the
 * key construction + cast boilerplate. Used by every custom item
 * (JutsuItem, BackpackItem, MedicineItem, GrappleItem, ZiplineItem,
 * TrailMapItem, ParcheminItem, LearnShelfItem) and several entity
 * markers (DummyManager, BackpackEntityManager, MedicArmoirManager).
 *
 * <p>Replace this:
 * <pre>{@code
 *   meta.getPersistentDataContainer().set(
 *       new NamespacedKey(plugin, "my_key"),
 *       PersistentDataType.STRING, "hello");
 *   ...
 *   String v = meta.getPersistentDataContainer().get(
 *       new NamespacedKey(plugin, "my_key"),
 *       PersistentDataType.STRING);
 *   if (v == null) return;
 * }</pre>
 * <p>...with:
 * <pre>{@code
 *   PdcAccess.setString(meta, plugin, "my_key", "hello");
 *   ...
 *   String v = PdcAccess.getString(meta, plugin, "my_key");
 *   if (v == null) return;
 * }</pre>
 *
 * <p>All methods accept either an {@link ItemMeta}, an
 * {@link ItemStack} (which is unwrapped to its meta), or an
 * {@link Entity} (which exposes its own PDC).
 */
public final class PdcAccess {

    private PdcAccess() {}

    /* ------------------------------------------------------------ key */

    /** Build a {@link NamespacedKey} scoped to the plugin. */
    public static NamespacedKey key(ShinobiCore plugin, String name) {
        return new NamespacedKey(plugin, name);
    }

    /* ------------------------------------------------------ getters */

    public static String getString(ItemMeta meta, ShinobiCore plugin, String name) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(key(plugin, name), PersistentDataType.STRING);
    }

    public static String getString(ItemStack item, ShinobiCore plugin, String name) {
        if (item == null || !item.hasItemMeta()) return null;
        return getString(item.getItemMeta(), plugin, name);
    }

    public static String getString(Entity e, ShinobiCore plugin, String name) {
        if (e == null) return null;
        return e.getPersistentDataContainer()
                .get(key(plugin, name), PersistentDataType.STRING);
    }

    public static Integer getInt(ItemMeta meta, ShinobiCore plugin, String name) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(key(plugin, name), PersistentDataType.INTEGER);
    }

    public static Long getLong(ItemMeta meta, ShinobiCore plugin, String name) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(key(plugin, name), PersistentDataType.LONG);
    }

    public static UUID getUuid(ItemMeta meta, ShinobiCore plugin, String name) {
        String raw = getString(meta, plugin, name);
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }

    /* ------------------------------------------------------ setters */

    public static void setString(ItemMeta meta, ShinobiCore plugin,
                                 String name, String value) {
        if (meta == null || value == null) return;
        meta.getPersistentDataContainer().set(
                key(plugin, name), PersistentDataType.STRING, value);
    }

    public static void setInt(ItemMeta meta, ShinobiCore plugin,
                              String name, int value) {
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                key(plugin, name), PersistentDataType.INTEGER, value);
    }

    public static void setLong(ItemMeta meta, ShinobiCore plugin,
                               String name, long value) {
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                key(plugin, name), PersistentDataType.LONG, value);
    }

    public static void setUuid(ItemMeta meta, ShinobiCore plugin,
                               String name, UUID value) {
        if (value == null) return;
        setString(meta, plugin, name, value.toString());
    }

    /* ------------------------------------------------------ presence */

    public static boolean has(ItemMeta meta, ShinobiCore plugin, String name) {
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(key(plugin, name), PersistentDataType.STRING)
            || pdc.has(key(plugin, name), PersistentDataType.INTEGER)
            || pdc.has(key(plugin, name), PersistentDataType.LONG);
    }

    public static boolean hasString(ItemStack item, ShinobiCore plugin, String name) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(plugin, name), PersistentDataType.STRING);
    }
}
