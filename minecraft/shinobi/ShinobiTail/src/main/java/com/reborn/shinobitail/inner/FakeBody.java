package com.reborn.shinobitail.inner;

import com.reborn.shinobitail.ShinobiTail;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The body left behind while the host's mind is in the Inner World: an
 * armor stand wearing the player's skin head + a copy of their visible
 * equipment, name tag included, so the scene still "contains" them.
 *
 * <p>Stands are PDC-tagged; orphans (crash / unloaded chunk) are wiped
 * on chunk load and at plugin boot. Plugin-only by design — if the
 * server later adds a packet-NPC library, only this class changes.
 */
public final class FakeBody {

    private static final String KEY = "fake_body";

    private FakeBody() { }

    public static NamespacedKey key(ShinobiTail plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    /** Spawns the stand-in at the player's exact position. */
    public static ArmorStand spawn(ShinobiTail plugin, Player player, String displayName) {
        Location loc = player.getLocation().clone();
        ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setBasePlate(false);
            s.setArms(true);
            s.setGravity(true);
            s.setPersistent(true);
            s.setInvulnerable(plugin.getConfig()
                    .getBoolean("fake-body.invulnerable", true));
            s.setRotation(loc.getYaw(), 0);
            s.customName(Component.text(displayName, NamedTextColor.GRAY));
            s.setCustomNameVisible(true);
            s.getPersistentDataContainer().set(key(plugin),
                    PersistentDataType.BYTE, (byte) 1);

            // Skin head — the armor stand wears the player's face.
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(player);
                head.setItemMeta(skull);
            }
            EntityEquipment eq = s.getEquipment();
            if (eq != null) {
                eq.setHelmet(head);
                if (plugin.getConfig().getBoolean("fake-body.copy-equipment", true)) {
                    EntityEquipment peq = player.getEquipment();
                    if (peq != null) {
                        eq.setChestplate(copy(peq.getChestplate()));
                        eq.setLeggings(copy(peq.getLeggings()));
                        eq.setBoots(copy(peq.getBoots()));
                        eq.setItemInMainHand(copy(peq.getItemInMainHand()));
                        eq.setItemInOffHand(copy(peq.getItemInOffHand()));
                    }
                }
            }
        });
        return stand;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public static boolean isFakeBody(ShinobiTail plugin, Entity entity) {
        return entity instanceof ArmorStand
                && entity.getPersistentDataContainer()
                        .has(key(plugin), PersistentDataType.BYTE);
    }

    /** Removes every tagged stand in the chunk (orphan cleanup). */
    public static void cleanupChunk(ShinobiTail plugin, Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (isFakeBody(plugin, e)) e.remove();
        }
    }
}
