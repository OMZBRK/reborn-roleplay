package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Temporary-hotbar editor for a trail. On {@link #enter} the player's real
 * inventory is stashed and a toolbar is laid out: walk to a spot, right-click
 * <b>Ajouter un point</b> to capture it, <b>Annuler</b> the last, <b>Aperçu</b>
 * to see the path, then <b>Terminer</b> to save (or <b>Annuler l'édition</b> to
 * drop it). The real inventory is always restored on exit / disconnect.
 *
 * <p>This replaces the old {@code /trail add|finish|cancel} command chain.
 * Clicks are routed here by {@link TrailEditorListener} via the PDC tag, not
 * the slot.
 */
public final class TrailEditorSession {

    public static final String ACTION_KEY = "trail_editor_action";
    public static final String ADD     = "add";
    public static final String UNDO    = "undo";
    public static final String PREVIEW = "preview";
    public static final String FINISH  = "finish";
    public static final String CANCEL  = "cancel";

    private final TrailManager trails;
    private final UUID playerId;
    private final String name;

    private ItemStack[] stash;
    private int heldSlot;
    private boolean restored;
    /** Glowing carpet anchor markers shown while editing (removed on exit). */
    private final List<org.bukkit.entity.BlockDisplay> markers = new ArrayList<>();

    public TrailEditorSession(TrailManager trails, UUID playerId, String name) {
        this.trails = trails;
        this.playerId = playerId;
        this.name = name;
    }

    /* -------------------------------------------------------------- enter */

    public void enter(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] snapshot = inv.getContents();
        // Never stash our own tools (re-entry safety).
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack it = snapshot[i];
            if (it != null && Keys.getString(it, ACTION_KEY) != null) snapshot[i] = null;
        }
        stash = snapshot;
        heldSlot = inv.getHeldItemSlot();
        inv.clear();
        giveTools(p);
        inv.setHeldItemSlot(0);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.4f, 1.3f);
        p.sendMessage(Component.text("Édition de la piste « " + name
                + " » — place-toi sur chaque point et clique « Ajouter ».",
                NamedTextColor.AQUA));
        refreshMarkers(p);
    }

    public void refresh(Player p) {
        if (p == null) return;
        p.getInventory().clear();
        giveTools(p);
        p.getInventory().setHeldItemSlot(0);
    }

    private void giveTools(Player p) {
        int n = Math.max(0, trails.editPointCount(p));
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, tool(Material.EMERALD, "Ajouter un point", NamedTextColor.GREEN, ADD,
                "Points placés : " + n, "Clic droit sur le point voulu."));
        inv.setItem(1, tool(Material.REDSTONE, "Annuler le dernier", NamedTextColor.RED, UNDO,
                "Retire le dernier point capturé."));
        inv.setItem(2, tool(Material.ENDER_EYE, "Aperçu du tracé", NamedTextColor.AQUA, PREVIEW,
                "Affiche les points + le chemin."));
        inv.setItem(7, tool(Material.LIME_DYE, "Terminer & sauvegarder", NamedTextColor.GREEN, FINISH,
                "Enregistre la piste (2 points min)."));
        inv.setItem(8, tool(Material.BARRIER, "Annuler l'édition", NamedTextColor.RED, CANCEL,
                "Abandonne sans sauvegarder."));
    }

    private ItemStack tool(Material m, String name, NamedTextColor color,
                           String action, String... loreLines) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.title(name, color));
            if (loreLines.length > 0) {
                List<Component> lore = new ArrayList<>(loreLines.length);
                for (String s : loreLines) lore.add(Texts.lore(s));
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            Keys.setString(meta, ACTION_KEY, action);
            it.setItemMeta(meta);
        }
        return it;
    }

    /* -------------------------------------------------------------- handle */

    public void handle(String action, Player p) {
        switch (action) {
            case ADD -> {
                int n = trails.editAdd(p);
                if (n == -2) {
                    p.sendMessage(Component.text("La piste est dans un autre monde.", NamedTextColor.RED));
                } else if (n >= 0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
                    p.sendMessage(Component.text("Point " + n + " ajouté.", NamedTextColor.GREEN));
                    refresh(p);
                    refreshMarkers(p);
                }
            }
            case UNDO -> {
                int n = trails.editUndo(p);
                if (n == 0) {
                    p.sendMessage(Component.text("Aucun point à retirer.", NamedTextColor.GRAY));
                } else if (n > 0) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                    p.sendMessage(Component.text("Dernier point retiré (" + n + " restant(s)).",
                            NamedTextColor.YELLOW));
                    refresh(p);
                    refreshMarkers(p);
                }
            }
            case PREVIEW -> previewTo(p);
            case FINISH  -> finish(p);
            case CANCEL  -> cancel(p);
            default -> { }
        }
    }

    private void finish(Player p) {
        int n = trails.editPointCount(p);
        if (n < 2) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            p.sendMessage(Component.text("Il faut au moins 2 points (" + Math.max(0, n)
                    + " placé(s)).", NamedTextColor.RED));
            return;
        }
        boolean ok = trails.editFinish(p);
        restore(p);
        trails.removeEditor(playerId);
        p.sendMessage(ok
                ? Component.text("Piste « " + name + " » enregistrée (" + n + " points).",
                        NamedTextColor.GREEN)
                : Component.text("Échec d'enregistrement de la piste.", NamedTextColor.RED));
    }

    private void cancel(Player p) {
        trails.editCancel(p);
        restore(p);
        trails.removeEditor(playerId);
        p.sendMessage(Component.text("Édition annulée.", NamedTextColor.GRAY));
    }

    /** Disconnect / forced exit — restore the inventory, drop the session. */
    public void abort() {
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) trails.editCancel(p);
        restore(p);
        trails.removeEditor(playerId);
    }

    private void restore(Player p) {
        if (restored) return;
        restored = true;
        clearMarkers();
        if (p == null) return;
        PlayerInventory inv = p.getInventory();
        if (stash != null) inv.setContents(stash);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
    }

    /* ------------------------------------------------------------- preview */

    private void previewTo(Player p) {
        List<Vector> pts = trails.editPoints(p);
        if (pts.isEmpty()) {
            p.sendMessage(Component.text("Aucun point à prévisualiser.", NamedTextColor.GRAY));
            return;
        }
        for (Vector v : pts) {
            p.spawnParticle(Particle.HAPPY_VILLAGER,
                    new Location(p.getWorld(), v.getX(), v.getY() + 0.3, v.getZ()),
                    6, 0.15, 0.15, 0.15, 0);
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            Vector a = pts.get(i);
            Vector b = pts.get(i + 1);
            Vector dir = b.clone().subtract(a);
            double len = dir.length();
            if (len < 1.0e-3) continue;
            dir.multiply(1.0 / len);
            for (double d = 0; d < len; d += 0.5) {
                p.spawnParticle(Particle.END_ROD,
                        new Location(p.getWorld(),
                                a.getX() + dir.getX() * d,
                                a.getY() + 0.3 + dir.getY() * d,
                                a.getZ() + dir.getZ() * d),
                        1, 0, 0, 0, 0);
            }
        }
        p.sendMessage(Component.text("Aperçu : " + pts.size() + " point(s).", NamedTextColor.AQUA));
    }

    /* ----------------------------------------------------- glowing markers */

    /** (Re)draw a glowing lime carpet at each captured anchor for the editor.
     *  These are rendered BlockDisplay entities (no real blocks placed) and are
     *  cleared when editing finishes / is cancelled. */
    private void refreshMarkers(Player p) {
        clearMarkers();
        if (p == null) return;
        org.bukkit.World w = p.getWorld();
        org.bukkit.block.data.BlockData carpet = Material.LIME_CARPET.createBlockData();
        for (Vector v : trails.editPoints(p)) {
            Location loc = new Location(w, Math.floor(v.getX()), Math.floor(v.getY()), Math.floor(v.getZ()));
            org.bukkit.entity.BlockDisplay d = w.spawn(loc, org.bukkit.entity.BlockDisplay.class, bd -> {
                bd.setBlock(carpet);
                bd.setGlowing(true);
                bd.setGlowColorOverride(org.bukkit.Color.LIME);
                bd.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                bd.setPersistent(false);
            });
            markers.add(d);
        }
    }

    private void clearMarkers() {
        for (org.bukkit.entity.BlockDisplay d : markers) {
            if (d != null && d.isValid()) d.remove();
        }
        markers.clear();
    }
}
