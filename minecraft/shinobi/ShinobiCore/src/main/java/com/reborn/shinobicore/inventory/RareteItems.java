package com.reborn.shinobicore.inventory;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Identité d'un objet pour l'inventaire : <b>rareté</b>, identifiant (Nexo / modèle), objet <b>unique</b> (jamais
 * détruit au sol), <b>arme</b> (emplacement étui).
 *
 * <p>Résolution de la rareté (décision du 2026-10-13), dans l'ordre :
 * <ol>
 *   <li>clé persistante {@code reborn:rarete} posée sur l'objet (par un plugin, ou à l'apparition au sol) ;</li>
 *   <li>table {@code rarete.par-id} de la config (id Nexo / id de modèle) ;</li>
 *   <li>rang du parchemin (modèle {@code reborn:parchemin_<rang>} : D → Commun … S → Légendaire) ;</li>
 *   <li>composant vanilla {@code rarity} (commun, peu commun, rare, épique) ;</li>
 *   <li>Commun.</li>
 * </ol>
 */
public final class RareteItems {

    /** Clé persistante de la rareté (texte : nom de {@link RpRarity}). Lue aussi par le mod (aura au sol). */
    public static final NamespacedKey CLE = new NamespacedKey("reborn", "rarete");
    /** Objet unique (relique, objet de quête) : jamais détruit au sol. */
    public static final NamespacedKey UNIQUE = new NamespacedKey("reborn", "unique");
    /** Objet de quête : jamais détruit au sol. */
    public static final NamespacedKey QUETE = new NamespacedKey("reborn", "quete");
    /** Identifiant posé par Nexo sur ses objets. */
    private static final NamespacedKey NEXO_ID = new NamespacedKey("nexo", "id");
    /** Identifiant de rouleau posé par ShinobiAbilities ({@code ParcheminItems.PDC_SCROLL}). */
    private static final NamespacedKey PARCHEMIN_ID = new NamespacedKey("shinobiabilities", "parchemin_id");

    private RareteItems() {}

    /** Rareté résolue de l'objet ({@link RpRarity#COMMUN} si vide). */
    public static RpRarity of(ItemStack s) {
        if (s == null || s.getType().isAir()) return RpRarity.COMMUN;
        if (s.hasItemMeta()) {
            String v = s.getItemMeta().getPersistentDataContainer().get(CLE, PersistentDataType.STRING);
            RpRarity r = RpRarity.parse(v);
            if (r != null) return r;
        }
        return derivee(s);
    }

    /** Rareté sans tenir compte de la clé posée (table, rang, composant vanilla). */
    public static RpRarity derivee(ItemStack s) {
        if (s == null || s.getType().isAir()) return RpRarity.COMMUN;
        String id = itemId(s);
        if (id != null) {
            RpRarity t = InventaireConfig.get().rareteParId.get(id);
            if (t != null) return t;
            if (id.startsWith("parchemin_") && id.length() == "parchemin_".length() + 1) {
                RpRarity r = RpRarity.fromRang(id.charAt(id.length() - 1));
                if (r != null) return r;
            }
        }
        ItemRarity vr = null;
        try {
            if (s.hasItemMeta() && s.getItemMeta().hasRarity()) vr = s.getItemMeta().getRarity();
            else vr = s.getType().asItemType() != null ? s.getType().asItemType().getItemRarity() : null;
        } catch (Throwable ignored) { }
        if (vr != null) {
            return switch (vr) {
                case UNCOMMON -> RpRarity.PEU_COMMUN;
                case RARE -> RpRarity.RARE;
                case EPIC -> RpRarity.EPIQUE;
                default -> RpRarity.COMMUN;
            };
        }
        return RpRarity.COMMUN;
    }

    /** Pose la clé de rareté résolue sur l'objet (copie modifiée en place). */
    public static void poser(ItemStack s) {
        if (s == null || s.getType().isAir()) return;
        RpRarity r = of(s);
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.getPersistentDataContainer().set(CLE, PersistentDataType.STRING, r.name());
        s.setItemMeta(m);
    }

    /**
     * Retire la clé de rareté si elle ne fait que répéter la rareté dérivée (posée à l'apparition au sol) : les piles
     * ramassées refusionnent avec les mêmes objets jamais tombés.
     */
    public static ItemStack normaliser(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return s;
        ItemMeta m = s.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        String v = pdc.get(CLE, PersistentDataType.STRING);
        if (v == null) return s;
        if (RpRarity.parse(v) == derivee(s)) {
            pdc.remove(CLE);
            s.setItemMeta(m);
        }
        return s;
    }

    /**
     * Identifiant court de l'objet, en minuscules : id Nexo ({@code nexo:id}), sinon clé du modèle d'objet
     * ({@code reborn:parchemin_b} → {@code parchemin_b}, {@code nexo:kunai} → {@code kunai}), sinon {@code null}.
     */
    public static String itemId(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return null;
        ItemMeta m = s.getItemMeta();
        try {
            String n = m.getPersistentDataContainer().get(NEXO_ID, PersistentDataType.STRING);
            if (n != null && !n.isBlank()) return n.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) { }
        try {
            if (m.hasItemModel() && m.getItemModel() != null) {
                String k = m.getItemModel().getKey().toLowerCase(Locale.ROOT);
                int slash = k.lastIndexOf('/');
                return slash >= 0 ? k.substring(slash + 1) : k;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Objet jamais détruit au sol : clé {@code reborn:unique} / {@code reborn:quete}, rouleau, ou id listé. */
    public static boolean unique(ItemStack s) {
        if (s == null || s.getType().isAir()) return false;
        InventaireConfig cfg = InventaireConfig.get();
        if (s.hasItemMeta()) {
            PersistentDataContainer pdc = s.getItemMeta().getPersistentDataContainer();
            if (pdc.has(UNIQUE) || pdc.has(QUETE)) return true;
            if (cfg.parcheminsEternels && pdc.has(PARCHEMIN_ID)) return true;
        }
        String id = itemId(s);
        return id != null && cfg.eternelsIds.contains(id);
    }

    /** Peut aller dans l'emplacement Arme (étui) : id listé dans {@code inventaire.arme.ids}, ou épée / hache / … */
    public static boolean arme(ItemStack s) {
        if (s == null || s.getType().isAir()) return false;
        InventaireConfig cfg = InventaireConfig.get();
        String id = itemId(s);
        if (id != null && cfg.armeIds.contains(id)) return true;
        if (!cfg.armeVanilla) return false;
        String n = s.getType().name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("TRIDENT") || n.equals("MACE");
    }

    /** Nom lisible de l'objet (nom affiché, nom Nexo, sinon matériau en minuscules). */
    public static String nom(ItemStack s) {
        if (s == null) return "objet";
        if (s.hasItemMeta()) {
            ItemMeta m = s.getItemMeta();
            try {
                var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
                if (m.hasDisplayName()) return plain.serialize(m.displayName());
                if (m.hasItemName()) return plain.serialize(m.itemName());
            } catch (Throwable ignored) { }
        }
        String n = s.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return n.isEmpty() ? "objet" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
