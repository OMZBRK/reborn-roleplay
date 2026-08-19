package com.reborn.shinobiabilities.mobility.training;

import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Temporary-hotbar editor for a training parkour, in two levels.
 *
 * <p><b>Main bar</b> (default): Ajouter · Annuler la dernière · Sélectionner
 * Anchor · Valider la création. Every placed anchor is shown to the editor only
 * as a glowing carpet (client-side block + glow particles, refreshed by the
 * manager's editor task).
 *
 * <p><b>Anchor bar</b> (after Sélectionner near an anchor): Touche à presser ·
 * Zone de réussite · Boucle avant échec · Vitesse de la barre · Récompense ·
 * Retour. Each of these opens a {@link ParkourPickerGui} chest to pick the
 * value. Edits a working {@link Parkour} committed only on Valider.
 */
public final class ParkourEditorSession {

    public static final String ACTION_KEY = "parkour_editor_action";
    // main bar
    public static final String ADD = "add", UNDO = "undo", SELECT = "select", DONE = "done";
    // anchor bar
    public static final String P_KEY = "p_key", P_ZONE = "p_zone", P_LOOPS = "p_loops",
            P_SPEED = "p_speed", P_REWARD = "p_reward", BACK = "back";

    private enum Mode { MAIN, ANCHOR }

    private final ParkourManager manager;
    private final UUID playerId;
    private final Parkour working;

    private Mode mode = Mode.MAIN;
    private int selectedIndex = -1;
    private final List<Location> carpets = new ArrayList<>();   // shown fake blocks
    private ItemStack[] stash;
    private int heldSlot;
    private boolean restored;

    public ParkourEditorSession(ParkourManager manager, UUID playerId, Parkour working) {
        this.manager = manager;
        this.playerId = playerId;
        this.working = working;
    }

    public Parkour working() { return working; }
    public int selectedNumber() { return selectedIndex + 1; }
    public ParkourAnchor selectedAnchor() {
        return (selectedIndex >= 0 && selectedIndex < working.anchors().size())
                ? working.anchors().get(selectedIndex) : null;
    }

    /* -------------------------------------------------------------- enter */

    public void enter(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] snap = inv.getContents();
        for (int i = 0; i < snap.length; i++) {
            ItemStack it = snap[i];
            if (it != null && Keys.getString(it, ACTION_KEY) != null) snap[i] = null;
        }
        stash = snap;
        heldSlot = inv.getHeldItemSlot();
        inv.clear();
        giveTools(p);
        inv.setHeldItemSlot(0);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.4f, 1.3f);
        p.sendMessage(Component.text("Éditeur du parcours « " + working.name() + " »",
                NamedTextColor.AQUA));
        p.sendMessage(Component.text("Ajoute des ancres, puis « Sélectionner Anchor » près "
                + "d'une ancre pour régler ses paramètres.", NamedTextColor.GRAY));
        previewTick(p);
    }

    public void refresh(Player p) {
        if (p == null) return;
        p.getInventory().clear();
        giveTools(p);
    }

    private void giveTools(Player p) {
        PlayerInventory inv = p.getInventory();
        if (mode == Mode.MAIN) {
            int n = working.anchors().size();
            inv.setItem(0, tool(Material.EMERALD, "Ajouter", NamedTextColor.GREEN, ADD,
                    "Ancres placées : " + n, "Clic droit sur la position voulue."));
            inv.setItem(1, tool(Material.REDSTONE, "Annuler la dernière", NamedTextColor.RED, UNDO,
                    "Retire la dernière ancre."));
            inv.setItem(2, tool(Material.SPYGLASS, "Sélectionner Anchor", NamedTextColor.AQUA, SELECT,
                    "Approche-toi d'une ancre (3 blocs)",
                    "puis clic droit pour la régler."));
            inv.setItem(8, tool(Material.LIME_DYE, "Valider la création", NamedTextColor.GREEN, DONE,
                    "Clic gauche : enregistrer (2 ancres min).",
                    "Clic droit : annuler sans enregistrer."));
        } else {
            ParkourAnchor a = selectedAnchor();
            int num = selectedNumber();
            inv.setItem(0, tool(Material.FEATHER, "Touche à presser", NamedTextColor.YELLOW, P_KEY,
                    "Ancre " + num + " : " + (a == null ? "?" : a.key().pretty()),
                    "Clic : ouvrir le choix."));
            inv.setItem(1, tool(Material.TARGET, "Zone de réussite", NamedTextColor.YELLOW, P_ZONE,
                    "Ancre " + num + " : " + (a == null ? "?" : a.zone().pretty()),
                    "Clic : ouvrir le choix."));
            inv.setItem(2, tool(Material.CLOCK, "Boucle avant échec", NamedTextColor.YELLOW, P_LOOPS,
                    "Ancre " + num + " : " + (a == null ? "?" : a.loops() + " boucle(s)"),
                    "Clic : ouvrir le choix."));
            inv.setItem(3, tool(Material.SUGAR, "Vitesse de la barre", NamedTextColor.YELLOW, P_SPEED,
                    "Ancre " + num + " : " + (a == null ? "?" : a.loopTicks() + " ticks/balayage"),
                    "Clic : ouvrir le choix."));
            inv.setItem(4, tool(Material.NETHER_STAR, "Récompense", NamedTextColor.LIGHT_PURPLE, P_REWARD,
                    "Débloquées : " + rewardsSummary(),
                    "Clic : ouvrir le choix."));
            inv.setItem(8, tool(Material.ARROW, "Retour", NamedTextColor.GRAY, BACK,
                    "Revenir à la création."));
        }
    }

    private String rewardsSummary() {
        if (working.rewards().isEmpty()) return "(aucune)";
        List<String> names = new ArrayList<>();
        for (var s : working.rewards()) names.add(s.displayName());
        return String.join(", ", names);
    }

    private ItemStack tool(Material m, String name, NamedTextColor color,
                           String action, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.title(name, color));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>(lore.length);
                for (String s : lore) l.add(Texts.lore(s));
                meta.lore(l);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            Keys.setString(meta, ACTION_KEY, action);
            it.setItemMeta(meta);
        }
        return it;
    }

    /* -------------------------------------------------------------- handle */

    public void handle(String action, boolean left, Player p) {
        switch (action) {
            case ADD -> {
                if (!p.getWorld().getName().equals(working.world())) {
                    p.sendMessage(Component.text("Le parcours est dans un autre monde.",
                            NamedTextColor.RED));
                    return;
                }
                Location l = p.getLocation();
                working.anchors().add(new ParkourAnchor(l.getX(), l.getY(), l.getZ()));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
                p.sendMessage(Component.text("Ancre " + working.anchors().size() + " ajoutée.",
                        NamedTextColor.GREEN));
                refresh(p);
                previewTick(p);
            }
            case UNDO -> {
                List<ParkourAnchor> a = working.anchors();
                if (a.isEmpty()) {
                    p.sendMessage(Component.text("Aucune ancre à retirer.", NamedTextColor.GRAY));
                    return;
                }
                a.remove(a.size() - 1);
                if (selectedIndex >= a.size()) { selectedIndex = -1; mode = Mode.MAIN; }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                p.sendMessage(Component.text("Dernière ancre retirée (" + a.size() + ").",
                        NamedTextColor.YELLOW));
                refresh(p);
                previewTick(p);
            }
            case SELECT -> doSelect(p);
            case DONE -> { if (left) finish(p); else cancel(p); }
            case P_KEY -> ParkourPickerGui.open(p, this, ParkourPickerGui.Type.KEY);
            case P_ZONE -> ParkourPickerGui.open(p, this, ParkourPickerGui.Type.ZONE);
            case P_LOOPS -> ParkourPickerGui.open(p, this, ParkourPickerGui.Type.LOOPS);
            case P_SPEED -> ParkourPickerGui.open(p, this, ParkourPickerGui.Type.SPEED);
            case P_REWARD -> ParkourPickerGui.open(p, this, ParkourPickerGui.Type.REWARD);
            case BACK -> {
                mode = Mode.MAIN;
                selectedIndex = -1;
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
                refresh(p);
                previewTick(p);
            }
            default -> { }
        }
    }

    private void doSelect(Player p) {
        if (working.anchors().isEmpty()) {
            p.sendMessage(Component.text("Aucune ancre à sélectionner.", NamedTextColor.GRAY));
            return;
        }
        Location pl = p.getLocation();
        int best = -1;
        double bestSq = 9.0;   // 3-block radius
        for (int i = 0; i < working.anchors().size(); i++) {
            ParkourAnchor a = working.anchors().get(i);
            double dx = a.x() - pl.getX(), dy = a.y() - pl.getY(), dz = a.z() - pl.getZ();
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) { bestSq = sq; best = i; }
        }
        if (best < 0) {
            p.sendMessage(Component.text("Approche-toi d'une ancre (3 blocs).", NamedTextColor.YELLOW));
            return;
        }
        selectedIndex = best;
        mode = Mode.ANCHOR;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
        p.sendMessage(Component.text("Ancre " + (best + 1) + " sélectionnée.", NamedTextColor.AQUA));
        refresh(p);
        previewTick(p);
    }

    private void finish(Player p) {
        if (!working.runnable()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            p.sendMessage(Component.text("Il faut au moins 2 ancres ("
                    + working.anchors().size() + " placée(s)).", NamedTextColor.RED));
            return;
        }
        restorePreview(p);
        manager.commit(working);
        restore(p);
        manager.removeEditor(playerId);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.sendMessage(Component.text("Parcours « " + working.name() + " » enregistré — "
                + working.anchors().size() + " ancres · récompenses : " + rewardsSummary(),
                NamedTextColor.GREEN));
    }

    private void cancel(Player p) {
        restorePreview(p);
        restore(p);
        manager.removeEditor(playerId);
        p.sendMessage(Component.text("Édition annulée (non enregistré).", NamedTextColor.GRAY));
    }

    /** Disconnect / forced exit — restore preview + inventory, drop the session. */
    public void abort() {
        Player p = Bukkit.getPlayer(playerId);
        restorePreview(p);
        restore(p);
        manager.removeEditor(playerId);
    }

    private void restore(Player p) {
        if (restored) return;
        restored = true;
        if (p == null) return;
        PlayerInventory inv = p.getInventory();
        if (stash != null) inv.setContents(stash);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, heldSlot)));
    }

    /* ------------------------------------------------------------- preview */

    /** Refresh the editor-only carpets + glow particles. Called by the manager's
     *  editor task (~½s) and immediately after any anchor change. */
    public void previewTick(Player p) {
        if (p == null) return;
        for (Location loc : carpets) p.sendBlockChange(loc, loc.getBlock().getBlockData());
        carpets.clear();
        World w = p.getWorld();
        if (!w.getName().equals(working.world())) return;
        for (int i = 0; i < working.anchors().size(); i++) {
            ParkourAnchor a = working.anchors().get(i);
            Location bloc = new Location(w, Math.floor(a.x()), Math.floor(a.y()), Math.floor(a.z()));
            boolean sel = (i == selectedIndex);
            p.sendBlockChange(bloc,
                    (sel ? Material.LIME_CARPET : Material.LIGHT_BLUE_CARPET).createBlockData());
            carpets.add(bloc);
            Location glow = bloc.clone().add(0.5, 0.12, 0.5);
            p.spawnParticle(sel ? Particle.END_ROD : Particle.GLOW, glow,
                    sel ? 6 : 3, 0.15, 0.05, 0.15, sel ? 0.01 : 0.0);
        }
    }

    private void restorePreview(Player p) {
        if (p == null) { carpets.clear(); return; }
        for (Location loc : carpets) p.sendBlockChange(loc, loc.getBlock().getBlockData());
        carpets.clear();
    }
}
