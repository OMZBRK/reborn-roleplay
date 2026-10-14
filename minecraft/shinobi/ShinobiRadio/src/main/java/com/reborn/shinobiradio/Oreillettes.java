package com.reborn.shinobiradio;

import com.reborn.shinobicore.inventory.RareteItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * L'oreillette (docs/gameplay/RADIO.md §3) : un objet Nexo ({@code reborn:oreillette}) porté dans l'emplacement
 * cosmétique Boucle d'oreille. <b>Chaque oreillette porte ses données</b> (PDC {@code shinobiradio:*}) : son numéro
 * unique, son escouade, son propriétaire d'origine (personnage, compte, nom RP) et son état (coupée ou non). C'est
 * l'oreillette qui donne l'accès à l'escouade : qui la porte entend et parle sur <b>son</b> escouade.
 */
final class Oreillettes {

    /** Données lues sur un objet oreillette ({@code numero} -1 = jamais liée). */
    record Info(int numero, UUID escouade, UUID proprio, UUID proprioCompte, String proprioNom, boolean coupee) {
        boolean liee() { return escouade != null; }
    }

    private final NamespacedKey kNumero, kEscouade, kProprio, kProprioCompte, kProprioNom, kCoupee;
    private final RadioConfig cfg;

    Oreillettes(Plugin plugin, RadioConfig cfg) {
        this.cfg = cfg;
        kNumero = new NamespacedKey(plugin, "numero");
        kEscouade = new NamespacedKey(plugin, "escouade");
        kProprio = new NamespacedKey(plugin, "proprio");
        kProprioCompte = new NamespacedKey(plugin, "proprio_compte");
        kProprioNom = new NamespacedKey(plugin, "proprio_nom");
        kCoupee = new NamespacedKey(plugin, "coupee");
    }

    /** Est-ce une oreillette (id Nexo ou modèle d'objet listé dans {@code oreillette.ids}) ? */
    boolean est(ItemStack s) {
        if (s == null || s.getType().isAir()) return false;
        String id = RareteItems.itemId(s);
        return id != null && cfg.idsOreillette.contains(id);
    }

    /** Données de l'oreillette, ou null si ce n'est pas une oreillette. */
    Info lire(ItemStack s) {
        if (!est(s)) return null;
        ItemMeta m = s.getItemMeta();
        if (m == null) return new Info(-1, null, null, null, "", false);
        PersistentDataContainer d = m.getPersistentDataContainer();
        Integer n = d.get(kNumero, PersistentDataType.INTEGER);
        Byte c = d.get(kCoupee, PersistentDataType.BYTE);
        String nom = d.get(kProprioNom, PersistentDataType.STRING);
        return new Info(n == null ? -1 : n, uuid(d.get(kEscouade, PersistentDataType.STRING)),
                uuid(d.get(kProprio, PersistentDataType.STRING)), uuid(d.get(kProprioCompte, PersistentDataType.STRING)),
                nom == null ? "" : nom, c != null && c != 0);
    }

    /** Copie liée à l'escouade {@code esc}, au propriétaire donné, avec le numéro {@code numero}. */
    ItemStack lier(ItemStack s, int numero, UUID esc, UUID proprio, UUID compte, String nom) {
        ItemStack r = s.clone();
        r.setAmount(1);
        ItemMeta m = r.getItemMeta();
        PersistentDataContainer d = m.getPersistentDataContainer();
        d.set(kNumero, PersistentDataType.INTEGER, numero);
        if (esc != null) d.set(kEscouade, PersistentDataType.STRING, esc.toString()); else d.remove(kEscouade);
        d.set(kProprio, PersistentDataType.STRING, proprio.toString());
        if (compte != null) d.set(kProprioCompte, PersistentDataType.STRING, compte.toString());
        d.set(kProprioNom, PersistentDataType.STRING, nom == null ? "" : nom);
        r.setItemMeta(m);
        return lore(r);
    }

    /** Copie sans escouade (le numéro et le propriétaire restent). */
    ItemStack delier(ItemStack s) {
        ItemStack r = s.clone();
        ItemMeta m = r.getItemMeta();
        m.getPersistentDataContainer().remove(kEscouade);
        r.setItemMeta(m);
        return lore(r);
    }

    /** Copie coupée (fil arraché) ou réparée. */
    ItemStack couper(ItemStack s, boolean coupee) {
        ItemStack r = s.clone();
        ItemMeta m = r.getItemMeta();
        if (coupee) m.getPersistentDataContainer().set(kCoupee, PersistentDataType.BYTE, (byte) 1);
        else m.getPersistentDataContainer().remove(kCoupee);
        r.setItemMeta(m);
        return lore(r);
    }

    /** Lore discrète : le numéro, et « coupée » le cas échéant (jamais le nom de l'escouade). */
    private ItemStack lore(ItemStack s) {
        Info i = lire(s);
        if (i == null) return s;
        ItemMeta m = s.getItemMeta();
        List<Component> l = new ArrayList<>();
        l.add(Component.text("Oreillette radio", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (i.numero() >= 0) l.add(Component.text("N° " + i.numero(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        if (i.coupee()) l.add(Component.text("Coupée — à réparer chez un artisan", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.lore(l);
        s.setItemMeta(m);
        return s;
    }

    /** Une oreillette neuve : l'objet Nexo {@code oreillette.id-nexo}, sinon PAPER + item_model reborn:oreillette. */
    ItemStack neuve() {
        ItemStack s = null;
        try {
            Class<?> k = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = k.getMethod("itemFromId", String.class).invoke(null, cfg.idNexo);
            if (builder != null) {
                Object item = builder.getClass().getMethod("build").invoke(builder);
                if (item instanceof ItemStack is) s = is;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        if (s == null) {
            s = new ItemStack(Material.PAPER);
            ItemMeta m = s.getItemMeta();
            m.setItemModel(new NamespacedKey("reborn", "oreillette"));
            m.itemName(Component.text("Oreillette", NamedTextColor.WHITE));
            s.setItemMeta(m);
        }
        return lore(s);
    }

    private static UUID uuid(String s) {
        try { return s == null || s.isBlank() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
