package com.reborn.shinobicore.medic;

import org.bukkit.Material;

/**
 * Catalog of French medicines used by the {@code /soigner} flow + the
 * Medic Armoir.
 *
 * <p>Each constant carries:
 * <ul>
 *   <li>a Bukkit {@link Material} for the in-world icon (kept thematic
 *       — {@code HONEYCOMB} for a balm, {@code BONE_MEAL} for plaster,
 *       {@code SUGAR} for pills, etc. — so a glance at the armoir reads
 *       like a real first-aid kit);</li>
 *   <li>a French display name (used both as the item's display name
 *       and as the encyclopedia entry title);</li>
 *   <li>a one-line short tagline;</li>
 *   <li>two lines of usage instructions for the encyclopedia.</li>
 * </ul>
 */
public enum Medicine {

    ARNICA_GEL(
            Material.HONEYCOMB,
            "Gel d'Arnica",
            "Anti-hématome topique.",
            new String[] {
                    "À appliquer en couche fine sur l'hématome.",
                    "Masser doucement jusqu'à pénétration."
            }),
    BIAFINE(
            Material.HONEY_BOTTLE,
            "Biafine",
            "Émulsion pour brûlures.",
            new String[] {
                    "Étaler une couche épaisse sur la brûlure.",
                    "Renouveler toutes les 4 heures."
            }),
    PLATRE(
            Material.BONE_MEAL,
            "Plâtre",
            "Immobilisation d'une fracture.",
            new String[] {
                    "Mouler autour du membre cassé.",
                    "Laisser sécher 24 heures avant de bouger."
            }),
    ANTALGIQUE(
            Material.SUGAR,
            "Antalgique (Paracétamol)",
            "Soulage la douleur.",
            new String[] {
                    "Avaler avec un verre d'eau.",
                    "Pas plus de quatre prises par jour."
            }),
    AMOXICILLINE(
            Material.BROWN_MUSHROOM,
            "Amoxicilline",
            "Antibiotique large spectre.",
            new String[] {
                    "Avaler la gélule entière.",
                    "Cure complète obligatoire (5 à 7 jours)."
            }),
    BETADINE(
            Material.BROWN_DYE,
            "Bétadine",
            "Antiseptique iodé.",
            new String[] {
                    "Verser sur la plaie ou la zone infectée.",
                    "Laisser agir une minute avant pansement."
            }),
    COMPRESSE_STERILE(
            Material.PAPER,
            "Compresse stérile",
            "Pansement primaire.",
            new String[] {
                    "Déposer sur la plaie après désinfection.",
                    "À renouveler dès qu'elle est souillée."
            }),
    BANDE(
            Material.STRING,
            "Bande de gaze",
            "Maintien du pansement.",
            new String[] {
                    "Enrouler par-dessus la compresse.",
                    "Serrer modérément pour garder la circulation."
            }),
    /** Special consumable — not a recipe medicine. Right-clicking
     *  with this in hand restores 100 chakra, grants Speed II for
     *  3 minutes, and spawns 5 Faible Hématome on the Buste droit
     *  (the body remembers the burst). Handled by
     *  {@code PiluleConsumeListener}, never by {@code /soigner}. */
    PILULE_SOLDAT(
            Material.SUGAR,
            "Pilule du Soldat",
            "Restitue le chakra mais épuise le corps.",
            new String[] {
                    "Avale d'un trait.",
                    "Effets : +100 chakra, Vitesse pendant 3 minutes.",
                    "Coût : 5 hématomes au buste droit."
            });

    private final Material material;
    private final String   displayName;
    private final String   tagline;
    private final String[] usage;

    Medicine(Material m, String displayName, String tagline, String[] usage) {
        this.material    = m;
        this.displayName = displayName;
        this.tagline     = tagline;
        this.usage       = usage;
    }

    public Material material()    { return material; }
    public String   displayName() { return displayName; }
    public String   tagline()     { return tagline; }
    public String[] usage()       { return usage; }
}
