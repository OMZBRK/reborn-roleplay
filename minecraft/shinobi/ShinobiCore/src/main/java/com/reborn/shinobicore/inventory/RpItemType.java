package com.reborn.shinobicore.inventory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Définition d'un objet RP (registre serveur, source de vérité). Le sac ne
 * stocke que des {@code (typeId, count)} ; toutes les métadonnées (nom, poids,
 * rareté, glyphe, emplacement cosmétique) vivent ici et sont envoyées au client
 * en JSON. Les identifiants sont alignés sur la démo client (mod-hud).
 */
public final class RpItemType {

    public final String id;
    public final String name;
    public final String desc;
    public final RpItemCategory category;
    public final RpRarity rarity;
    public final double weight;
    public final String glyph;
    public final CosmeticSlot cosmeticSlot; // null si non cosmétique
    public final BagTier bagTier;           // non-null => c'est un objet-sac équipable
    public final int maxStack;

    private RpItemType(String id, String name, String desc, RpItemCategory category, RpRarity rarity,
                       double weight, String glyph, CosmeticSlot cosmeticSlot, BagTier bagTier, int maxStack) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.rarity = rarity;
        this.weight = weight;
        this.glyph = glyph;
        this.cosmeticSlot = cosmeticSlot;
        this.bagTier = bagTier;
        this.maxStack = Math.max(1, maxStack);
    }

    public boolean isCosmetic() {
        return cosmeticSlot != null;
    }

    public boolean isBag() {
        return bagTier != null;
    }

    public boolean isConsumable() {
        return category == RpItemCategory.CONSOMMABLES;
    }

    /** Objet JSON pour une pile de cet objet (schéma attendu par le client). */
    public String toJson(int count) {
        return "{"
            + "\"id\":\"" + esc(id) + "\","
            + "\"name\":\"" + esc(name) + "\","
            + "\"desc\":\"" + esc(desc) + "\","
            + "\"category\":\"" + category.name() + "\","
            + "\"rarity\":\"" + rarity.name() + "\","
            + "\"weight\":" + weight + ","
            + "\"count\":" + count + ","
            + "\"glyph\":\"" + esc(glyph) + "\","
            + "\"cosmetic\":\"" + (cosmeticSlot != null ? cosmeticSlot.name() : "") + "\","
            + "\"bag\":\"" + (bagTier != null ? bagTier.name() : "") + "\""
            + "}";
    }

    /** Échappement JSON minimal (guillemets, antislash, retours ligne). */
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    // ─────────── Registre ───────────
    private static final Map<String, RpItemType> REGISTRY = new LinkedHashMap<>();

    private static RpItemType reg(String id, String name, String desc, RpItemCategory cat, RpRarity rar,
                                  double weight, String glyph, CosmeticSlot cosmetic, int maxStack) {
        RpItemType t = new RpItemType(id, name, desc, cat, rar, weight, glyph, cosmetic, null, maxStack);
        REGISTRY.put(id, t);
        return t;
    }

    /** Enregistre un objet-sac (tier). L'id doit correspondre à {@link BagTier#itemId}. */
    private static RpItemType regBag(BagTier tier, String name, String desc, RpRarity rar, double weight) {
        RpItemType t = new RpItemType(tier.itemId, name, desc, RpItemCategory.SACS, rar, weight,
            tier.glyph, null, tier, 1);
        REGISTRY.put(tier.itemId, t);
        return t;
    }

    public static RpItemType get(String id) {
        return REGISTRY.get(id);
    }

    static {
        reg("kunai", "Kunai", "Couteau de lancer standard.\nÉquilibré, fiable.",
            RpItemCategory.ARMES, RpRarity.COMMUN, 0.20, "刃", null, 16);
        reg("shuriken", "Shuriken", "Étoile de jet.\nParfaite en volée.",
            RpItemCategory.ARMES, RpRarity.COMMUN, 0.05, "星", null, 40);
        reg("tanto", "Tantō", "Lame courte d'assaut.\nDégâts élevés au corps à corps.",
            RpItemCategory.ARMES, RpRarity.RARE, 1.50, "刀", null, 1);
        reg("tag_explosif", "Parchemin explosif", "Note piégée.\nDétone au contact du chakra.",
            RpItemCategory.PARCHEMINS, RpRarity.RARE, 0.10, "札", null, 20);
        reg("parchemin_katon", "Parchemin — Katon", "Contient une technique de Feu.\nÀ étudier à la bibliothèque.",
            RpItemCategory.PARCHEMINS, RpRarity.EPIQUE, 0.30, "火", null, 1);
        reg("pilule_soldat", "Pilule du soldat", "Restaure le chakra un court instant.\nEffet secondaire : fatigue.",
            RpItemCategory.CONSOMMABLES, RpRarity.RARE, 0.05, "丸", null, 10);
        reg("ration", "Ration de combat", "Nourriture de mission.\nCoupe la faim longtemps.",
            RpItemCategory.CONSOMMABLES, RpRarity.COMMUN, 0.40, "食", null, 16);
        reg("onguent", "Onguent médical", "Soin de premiers secours.\nReferme les plaies légères.",
            RpItemCategory.CONSOMMABLES, RpRarity.COMMUN, 0.20, "薬", null, 10);
        reg("fil_ninja", "Fil de fer ninja", "Matériau de piège.\nInvisible et coupant.",
            RpItemCategory.MATERIAUX, RpRarity.COMMUN, 0.30, "糸", null, 8);
        reg("corde", "Corde", "Grimpe et immobilisation.",
            RpItemCategory.MATERIAUX, RpRarity.COMMUN, 0.80, "縄", null, 4);
        reg("masque_anbu", "Masque ANBU", "Cache le visage.\nCosmétique équipable.",
            RpItemCategory.COSMETIQUES, RpRarity.EPIQUE, 0.20, "面", CosmeticSlot.MASQUE, 1);
        reg("manteau_akatsuki", "Manteau d'Akatsuki", "Long manteau à nuages rouges.\nCosmétique porté sur le dos.",
            RpItemCategory.COSMETIQUES, RpRarity.LEGENDAIRE, 2.00, "衣", CosmeticSlot.MANTEAU, 1);
        reg("bandeau_konoha", "Bandeau de Konoha", "Le protège-front du village.\nSymbole du rang de ninja.",
            RpItemCategory.COSMETIQUES, RpRarity.RARE, 0.10, "額", CosmeticSlot.BANDEAU, 1);
        reg("rouleau_invocation", "Rouleau d'invocation", "Objet de quête.\nScelle un contrat animal.",
            RpItemCategory.QUETE, RpRarity.LEGENDAIRE, 0.50, "召", null, 1);

        // ─── Sacs (débloquent l'espace ; équipés dans l'emplacement Sac) ───
        regBag(BagTier.SACOCHE, "Sacoche",
            "Petite sacoche de hanche.\n+" + BagTier.SACOCHE.extraSlots + " cases, +"
                + (int) BagTier.SACOCHE.extraWeight + " kg.",
            RpRarity.COMMUN, 0.60);
        regBag(BagTier.BANDOULIERE, "Bandoulière",
            "Sac en bandoulière.\n+" + BagTier.BANDOULIERE.extraSlots + " cases, +"
                + (int) BagTier.BANDOULIERE.extraWeight + " kg.",
            RpRarity.RARE, 1.20);
        regBag(BagTier.SAC, "Sac ninja",
            "Sac à dos de mission.\n+" + BagTier.SAC.extraSlots + " cases, +"
                + (int) BagTier.SAC.extraWeight + " kg.",
            RpRarity.RARE, 2.00);
        regBag(BagTier.SAC_LOURD, "Sac lourd",
            "Barda de longue mission.\n+" + BagTier.SAC_LOURD.extraSlots + " cases, +"
                + (int) BagTier.SAC_LOURD.extraWeight + " kg.\nRalentit celui qui le porte.",
            RpRarity.EPIQUE, 3.50);
    }
}
