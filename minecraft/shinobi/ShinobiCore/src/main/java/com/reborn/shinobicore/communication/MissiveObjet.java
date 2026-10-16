package com.reborn.shinobicore.communication;

import com.reborn.shinobicore.communication.CommData.Missive;
import com.reborn.shinobicore.inventory.RpWeights;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * La missive, objet (CLOCHE_ET_MISSIVES.md §3.5) : {@code PAPER} habillé par l'item_model
 * {@code reborn:missive_scellee} ou {@code reborn:missive_descellee} (provisoires du pack Nexo, l'utilisateur modélise
 * les vrais). L'objet ne porte que l'identifiant de la copie ({@code reborn:missive}) : le texte reste sur le serveur
 * ({@code communication/missives.yml}). Info-bulle : « Pour : … », l'état, la signature si elle est signée ; jamais
 * l'auteur d'une missive anonyme.
 */
public final class MissiveObjet {

    public static final NamespacedKey ID = new NamespacedKey("reborn", "missive");
    public static final String SCELLEE = "missive_scellee", DESCELLEE = "missive_descellee";
    private static final TextColor CREME = TextColor.color(0xE8DCC0), OR = TextColor.color(0xF2D08A),
            AMBRE = TextColor.color(0xE2A04A);

    private MissiveObjet() {}

    public static String id(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }

    public static boolean est(ItemStack s) { return id(s) != null; }

    static Component sans(Component c) { return c.decoration(TextDecoration.ITALIC, false); }

    /** « Kenta Inuzuka et 1 autre ». */
    static String pour(Missive m) {
        String n = m.pour().nom();
        int autres = m.dests.size() - 1;
        return autres <= 0 ? n : n + " et " + autres + (autres == 1 ? " autre" : " autres");
    }

    public static ItemStack creer(Missive m) {
        ItemStack s = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = s.getItemMeta();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, m.id);
        meta.getPersistentDataContainer().set(RpWeights.WEIGHT_KEY, PersistentDataType.DOUBLE, 0.0);
        meta.setMaxStackSize(1);
        s.setItemMeta(meta);
        return maj(s, m);
    }

    /** Remet le modèle, le nom et les lignes selon l'état (scellée / cachet brisé). */
    public static ItemStack maj(ItemStack s, Missive m) {
        ItemStack c = s.clone();
        ItemMeta meta = c.getItemMeta();
        meta.setItemModel(new NamespacedKey("reborn", m.scellee ? SCELLEE : DESCELLEE));
        meta.itemName(Component.text(m.scellee ? "Missive scellée" : "Missive"));
        List<Component> lore = new ArrayList<>();
        lore.add(sans(Component.text("Pour : " + pour(m), CREME)));
        lore.add(sans(Component.text(m.scellee ? "Scellée à la cire" : "Cachet brisé", m.scellee ? OR : AMBRE)));
        if (m.signee && !m.auteurNom.isBlank()) lore.add(sans(Component.text("Signée " + m.auteurNom, NamedTextColor.GRAY)));
        lore.add(sans(Component.text(m.scellee ? "Clic droit : briser le cachet et lire" : "Clic droit : lire", NamedTextColor.GRAY)));
        meta.lore(lore);
        c.setItemMeta(meta);
        return c;
    }
}
