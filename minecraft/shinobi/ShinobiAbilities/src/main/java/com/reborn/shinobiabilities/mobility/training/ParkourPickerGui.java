package com.reborn.shinobiabilities.mobility.training;

import com.reborn.shinobiabilities.mobility.MobilityActionSlot;
import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The temporary parameter pickers opened from the parkour anchor-bar: a small
 * chest GUI per parameter (press key, success zone, loops, bar speed) plus a
 * multi-select reward chest. Clicks are routed here by {@code ParkourListener}
 * via the {@link Holder}; each option item carries its value in a PDC tag.
 */
public final class ParkourPickerGui {

    public enum Type { KEY, ZONE, LOOPS, SPEED, REWARD }

    public static final String VALUE_KEY = "parkour_pick_value";

    /** Marks an inventory as one of our pickers + carries the edit context. */
    public static final class Holder implements InventoryHolder {
        private final ParkourEditorSession session;
        private final Type type;
        private Inventory inv;
        Holder(ParkourEditorSession session, Type type) {
            this.session = session;
            this.type = type;
        }
        public ParkourEditorSession session() { return session; }
        public Type type() { return type; }
        void setInv(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }

    private ParkourPickerGui() { }

    /* ---------------------------------------------------------------- open */

    public static void open(Player p, ParkourEditorSession s, Type type) {
        ParkourAnchor a = s.selectedAnchor();
        if (a == null) { p.closeInventory(); return; }
        Holder holder = new Holder(s, type);
        int size = (type == Type.LOOPS) ? 18 : 9;
        Inventory inv = Bukkit.createInventory(holder, size, title(type, s));
        holder.setInv(inv);
        populate(inv, type, a, s);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.4f);
    }

    private static Component title(Type t, ParkourEditorSession s) {
        return switch (t) {
            case KEY -> Component.text("Touche — ancre " + s.selectedNumber(), NamedTextColor.DARK_AQUA);
            case ZONE -> Component.text("Zone — ancre " + s.selectedNumber(), NamedTextColor.DARK_AQUA);
            case LOOPS -> Component.text("Boucles — ancre " + s.selectedNumber(), NamedTextColor.DARK_AQUA);
            case SPEED -> Component.text("Vitesse — ancre " + s.selectedNumber(), NamedTextColor.DARK_AQUA);
            case REWARD -> Component.text("Récompenses du parcours", NamedTextColor.DARK_PURPLE);
        };
    }

    private static void populate(Inventory inv, Type type, ParkourAnchor a, ParkourEditorSession s) {
        switch (type) {
            case KEY -> {
                inv.setItem(2, opt(Material.LEATHER_BOOTS, "Accroupi", "SNEAK",
                        a.key() == ParkourAnchor.Key.SNEAK));
                inv.setItem(4, opt(Material.FEATHER, "Saut (Espace)", "SPACE",
                        a.key() == ParkourAnchor.Key.SPACE));
                inv.setItem(6, opt(Material.WOODEN_SWORD, "Clic gauche", "LEFT_CLICK",
                        a.key() == ParkourAnchor.Key.LEFT_CLICK));
            }
            case ZONE -> {
                inv.setItem(1, opt(Material.ENDER_PEARL, "Aléatoire", "RANDOM",
                        a.zone() == ParkourAnchor.Zone.RANDOM));
                inv.setItem(3, opt(Material.LIME_DYE, "Gauche", "LEFT",
                        a.zone() == ParkourAnchor.Zone.LEFT));
                inv.setItem(5, opt(Material.YELLOW_DYE, "Milieu", "MIDDLE",
                        a.zone() == ParkourAnchor.Zone.MIDDLE));
                inv.setItem(7, opt(Material.ORANGE_DYE, "Droite", "RIGHT",
                        a.zone() == ParkourAnchor.Zone.RIGHT));
            }
            case LOOPS -> {
                for (int i = 1; i <= 9; i++) {
                    inv.setItem(i - 1, opt(Material.CLOCK, i + " boucle(s)",
                            String.valueOf(i), a.loops() == i));
                }
            }
            case SPEED -> {
                int[] vals = {12, 20, 30, 45, 60};
                String[] names = {"Très rapide", "Rapide", "Moyen", "Lent", "Très lent"};
                for (int i = 0; i < vals.length; i++) {
                    inv.setItem(2 + i, opt(Material.SUGAR, names[i] + " (" + vals[i] + "t)",
                            String.valueOf(vals[i]), a.loopTicks() == vals[i]));
                }
            }
            case REWARD -> {
                MobilityActionSlot[] v = MobilityActionSlot.values();
                for (int i = 0; i < v.length; i++) {
                    boolean on = s.working().rewards().contains(v[i]);
                    inv.setItem(i + 1, opt(v[i].icon(), v[i].displayName() + (on ? " ✔" : ""),
                            v[i].name(), on));
                }
                inv.setItem(8, opt(Material.LIME_DYE, "Terminer", "done", false));
            }
        }
    }

    private static ItemStack opt(Material m, String name, String value, boolean selected) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.title(name, selected ? NamedTextColor.GREEN : NamedTextColor.WHITE));
            List<Component> lore = new ArrayList<>();
            lore.add(Texts.lore(selected ? "Actuel — clique pour confirmer" : "Clique pour choisir"));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            Keys.setString(meta, VALUE_KEY, value);
            it.setItemMeta(meta);
        }
        return it;
    }

    /* -------------------------------------------------------------- handle */

    public static void handle(Player p, Holder h, ItemStack clicked) {
        String val = Keys.getString(clicked, VALUE_KEY);
        if (val == null) return;
        ParkourEditorSession s = h.session();
        ParkourAnchor a = s.selectedAnchor();
        if (a == null) { p.closeInventory(); return; }
        switch (h.type()) {
            case KEY -> { a.setKey(ParkourAnchor.Key.from(val)); done(p, s); }
            case ZONE -> { a.setZone(ParkourAnchor.Zone.from(val)); done(p, s); }
            case LOOPS -> { a.setLoops(parseInt(val, a.loops())); done(p, s); }
            case SPEED -> { a.setLoopTicks(parseInt(val, a.loopTicks())); done(p, s); }
            case REWARD -> {
                if (val.equals("done")) { done(p, s); return; }
                MobilityActionSlot slot = MobilityActionSlot.from(val);
                if (slot != null && !s.working().rewards().add(slot)) {
                    s.working().rewards().remove(slot);
                }
                Inventory inv = h.getInventory();   // refresh the toggles in place
                if (inv != null) populate(inv, Type.REWARD, a, s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
            }
        }
    }

    private static void done(Player p, ParkourEditorSession s) {
        p.closeInventory();
        s.refresh(p);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
