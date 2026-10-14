package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /voirsac} (RedLine {@code /invsee}) : coffre <b>en lecture</b> côté serveur — ligne 1 la barre d'action, lignes
 * 2-4 le stockage du sac porté, ligne 5 l'équipement (sac, cosmétiques, étui), ligne 6 une fiche. Un <b>Admin</b> peut
 * <b>prendre</b> un objet de la barre ou du sac d'un clic (il arrive dans sa propre sacoche) ; tout est journalisé
 * (ouverture : {@code STAFF VOIR_SAC} ; prise : {@code OBJET PRISE_STAFF}). L'ouverture est aussi signalée au chat staff.
 */
public final class VoirSac implements Listener {

    /** Inventaire du coffre ; {@code cases} = n° de case du coffre → référence (« H3 » barre, « B5 » sac). */
    static final class Vue implements InventoryHolder {
        final UUID cible;
        final Map<Integer, String> cases = new HashMap<>();
        Inventory inv;

        Vue(UUID cible) { this.cible = cible; }

        @Override public Inventory getInventory() { return inv; }
    }

    private final ShinobiCore plugin;

    public VoirSac(ShinobiCore plugin) { this.plugin = plugin; }

    public void ouvrir(Player staff, Player cible) {
        Vue v = new Vue(cible.getUniqueId());
        ShinobiCharacter c = plugin.characters().getActive(cible.getUniqueId());
        String titre = "Sacoche · " + (c != null ? PlayerIds.prefix(c) + c.name() : cible.getName());
        v.inv = Bukkit.createInventory(v, 54, Component.text(titre.length() > 40 ? titre.substring(0, 40) : titre));
        remplir(v, cible, c, StaffGrades.of(staff) >= StaffGrades.ADMIN);
        staff.openInventory(v.inv);
        Journal.entree(Categorie.STAFF, "VOIR_SAC").acteur(staff).cible(cible).lieu(cible.getLocation()).ecrire();
        if (plugin.staffPanel() != null) plugin.staffPanel().log(staff, "staff", "Sacoche consultée", cible.getName());
    }

    private void remplir(Vue v, Player cible, ShinobiCharacter c, boolean prise) {
        v.inv.clear();
        v.cases.clear();
        for (int i = 0; i < 9; i++) {
            ItemStack s = cible.getInventory().getItem(i);
            if (s != null && !s.getType().isAir()) { v.inv.setItem(i, s.clone()); v.cases.put(i, "H" + i); }
        }
        InventoryManager im = plugin.rpInventory();
        if (im != null) {
            List<ItemStack> sac = im.bagStorage(cible);
            for (int i = 0; i < sac.size() && i < 27; i++) {
                ItemStack s = sac.get(i);
                if (s != null) { v.inv.setItem(9 + i, s); v.cases.put(9 + i, "B" + i); }
            }
            int slot = 36;
            for (Map.Entry<String, ItemStack> e : im.staffApercu(cible).entrySet()) {
                if (slot > 44) break;
                v.inv.setItem(slot++, etiquette(e.getValue(), e.getKey().replace("EQ:", "")));
            }
        }
        ItemStack fiche = new ItemStack(Material.PAPER);
        ItemMeta m = fiche.getItemMeta();
        m.displayName(Component.text(c != null ? PlayerIds.prefix(c) + c.name() : cible.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(gris("Compte : " + cible.getName()));
        if (c != null && plugin.monnaie() != null) {
            var cp = plugin.monnaie().banque().compte(c);
            lore.add(gris("Bourse : " + com.reborn.shinobicore.monnaie.Bourse.fmt(plugin.monnaie().bourses().montant(cible))
                    + " ryo · compte : " + com.reborn.shinobicore.monnaie.Bourse.fmt(cp.solde()) + " ryo"));
        } else if (c != null) lore.add(gris("Ryo : " + c.ryo()));
        if (im != null) lore.add(gris(String.format(java.util.Locale.FRANCE, "Poids : %.1f / %.1f", im.weight(cible), im.maxWeight(cible))));
        lore.add(gris(prise ? "Clic : prendre l'objet (journalisé)" : "Lecture seule"));
        m.lore(lore);
        fiche.setItemMeta(m);
        v.inv.setItem(49, fiche);
    }

    private static Component gris(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack etiquette(ItemStack s, String emplacement) {
        ItemStack c = s.clone();
        ItemMeta m = c.getItemMeta();
        if (m != null) {
            List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
            lore.add(0, Component.text("Équipé : " + emplacement, NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            c.setItemMeta(m);
        }
        return c;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getView().getTopInventory().getHolder(false) instanceof Vue v)) return;
        ev.setCancelled(true);
        if (!(ev.getWhoClicked() instanceof Player staff)) return;
        if (ev.getClickedInventory() != ev.getView().getTopInventory()) return;
        String ref = v.cases.get(ev.getSlot());
        if (ref == null) return;
        if (StaffGrades.of(staff) < StaffGrades.ADMIN) {
            staff.sendActionBar(Component.text("Lecture seule (prendre : Admin).", NamedTextColor.GRAY));
            return;
        }
        Player cible = Bukkit.getPlayer(v.cible);
        if (cible == null) { staff.closeInventory(); return; }
        ItemStack pris = null;
        if (ref.startsWith("H")) {
            int i = Integer.parseInt(ref.substring(1));
            ItemStack s = cible.getInventory().getItem(i);
            if (s != null && !s.getType().isAir()) {
                pris = s.clone();
                cible.getInventory().setItem(i, null);
                cible.updateInventory();
                if (plugin.rpInventory() != null) plugin.rpInventory().push(cible, false);
            }
        } else if (ref.startsWith("B") && plugin.rpInventory() != null) {
            pris = plugin.rpInventory().takeFromBag(cible, Integer.parseInt(ref.substring(1)));
        }
        if (pris == null) return;
        InventoryManager.donner(staff, pris);
        Journal.entree(Categorie.OBJET, "PRISE_STAFF").acteur(staff).cible(cible)
                .detail(pris.getAmount() + "× " + RareteItems.nom(pris) + " [" + RareteItems.of(pris).name() + "] · case " + ref)
                .lieu(cible.getLocation()).ecrire();
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().log(staff, "staff", "Objet pris", cible.getName() + " · " + pris.getAmount() + "× " + RareteItems.nom(pris));
        }
        ShinobiCharacter c = plugin.characters().getActive(cible.getUniqueId());
        remplir(v, cible, c, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent ev) {
        if (ev.getView().getTopInventory().getHolder(false) instanceof Vue) ev.setCancelled(true);
    }
}
