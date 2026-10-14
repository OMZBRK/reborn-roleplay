package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.inventory.RpWeights;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les objets de la nourriture : <b>panier d'ingrédients</b>, <b>plat</b>, <b>ration sèche</b>, <b>bol vide</b>. De vrais
 * objets (ils se donnent, se volent, se rangent) : PAPER habillé par un modèle du pack Nexo ({@code reborn:<id>},
 * {@code minecraft/server-config/nexo/pack/assets/reborn/}), données en PDC (espace {@code reborn}) :
 * {@code nourriture} (panier | plat | ration | bol_vide), {@code recette}, {@code chaud} (fin de la chaleur, ms epoch),
 * {@code entame} (0..1, repas commencé puis rangé).
 */
public final class NourritureItems {

    static final NamespacedKey K_GENRE = new NamespacedKey("reborn", "nourriture");
    static final NamespacedKey K_RECETTE = new NamespacedKey("reborn", "recette");
    static final NamespacedKey K_CHAUD = new NamespacedKey("reborn", "chaud");
    static final NamespacedKey K_ENTAME = new NamespacedKey("reborn", "entame");

    public static final String PANIER = "panier", PLAT = "plat", RATION = "ration", BOL_VIDE = "bol_vide";

    private static final TextColor NOM = TextColor.color(0xE8C27A);

    private NourritureItems() {}

    public static ItemStack panier(NourritureConfig.Plat recette, int n) {
        ItemStack it = base("panier_ingredients", "Ingrédients : " + recette.nom().toLowerCase(Locale.FRANCE), 16, 1.0, n);
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(gris("Panier du marché, pour " + recette.nom() + "."),
                gris("Au fourneau d'un restaurant : deux plats.")));
        PersistentDataContainer d = m.getPersistentDataContainer();
        d.set(K_GENRE, PersistentDataType.STRING, PANIER);
        d.set(K_RECETTE, PersistentDataType.STRING, recette.id());
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack plat(NourritureConfig.Plat p, long chaudJusqua, int n, List<String> effets) {
        ItemStack it = base("plat_" + p.id(), p.nom(), 16, 0.5, n);
        ItemMeta m = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(gris("Satiété +" + (int) Math.round(p.satiete()) + " % · " + (int) Math.round(p.dureeS()) + " s à manger"));
        for (String e : effets) lore.add(Component.text(e, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(gris("Se mange chaud : froid, effets réduits de moitié."));
        m.lore(lore);
        PersistentDataContainer d = m.getPersistentDataContainer();
        d.set(K_GENRE, PersistentDataType.STRING, PLAT);
        d.set(K_RECETTE, PersistentDataType.STRING, p.id());
        d.set(K_CHAUD, PersistentDataType.LONG, chaudJusqua);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack ration(NourritureConfig.Plat r, int n) {
        ItemStack it = base("ration_seche", r.nom(), 16, 0.3, n);
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(gris("De quoi tenir : +" + (int) Math.round(r.satiete()) + " %, sans effet."),
                gris("Jamais au-dessus de " + (int) Math.round(r.plafond()) + " %.")));
        m.getPersistentDataContainer().set(K_GENRE, PersistentDataType.STRING, RATION);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack bolVide() {
        ItemStack it = base("bol_vide", "Bol vide", 16, 0.2, 1);
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(gris("À rendre au cuisinier.")));
        m.getPersistentDataContainer().set(K_GENRE, PersistentDataType.STRING, BOL_VIDE);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack base(String model, String nom, int pile, double poids, int n) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, n));
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("reborn", model));
        m.setMaxStackSize(pile);
        m.displayName(Component.text(nom, NOM).decoration(TextDecoration.ITALIC, false));
        m.getPersistentDataContainer().set(RpWeights.WEIGHT_KEY, PersistentDataType.DOUBLE, poids);
        it.setItemMeta(m);
        return it;
    }

    private static Component gris(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    /* ================================================================ lecture */

    public static String genre(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(K_GENRE, PersistentDataType.STRING);
    }

    public static String recette(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(K_RECETTE, PersistentDataType.STRING);
    }

    /** Fin de la chaleur (ms epoch), 0 si inconnue. */
    public static long chaud(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Long v = it.getItemMeta().getPersistentDataContainer().get(K_CHAUD, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    public static double entame(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Double v = it.getItemMeta().getPersistentDataContainer().get(K_ENTAME, PersistentDataType.DOUBLE);
        return v == null ? 0 : Math.max(0, Math.min(0.99, v));
    }

    /** Copie d'un plat avec la part déjà mangée (0 = efface) ; une pile entamée ne fait qu'un plat. */
    public static ItemStack avecEntame(ItemStack it, double part) {
        ItemStack c = it.clone();
        ItemMeta m = c.getItemMeta();
        if (part <= 0.001) m.getPersistentDataContainer().remove(K_ENTAME);
        else m.getPersistentDataContainer().set(K_ENTAME, PersistentDataType.DOUBLE, part);
        c.setItemMeta(m);
        return c;
    }
}
