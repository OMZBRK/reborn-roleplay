package fr.reborn.hud.skin;

import java.util.Locale;

/**
 * Spécification d'apparence d'un personnage Reborn (Phase 2 création de perso).
 *
 * <p>Décrit un skin composé par <b>identifiants cosmétiques</b> (teinte de peau,
 * coiffure, couleurs, tenue) plutôt que par une texture brute. {@link RebornSkins}
 * transforme cette spec en une texture 64×64 ; comme tous les clients ont le mod,
 * chacun compose la même image à partir des mêmes IDs → tout le monde voit le même
 * personnage. La spec voyage jusqu'au serveur (commande {@code create}) qui la
 * stocke et la rediffuse dans le roster {@code reborn:character}.
 *
 * <p><b>Phase actuelle (sans assets)</b> : la composition est <i>procédurale</i>
 * (aplats de couleur sur les zones du skin). Les vrais PNG (visages, coiffures,
 * tenues de clan) remplaceront le rendu dans {@link RebornSkins#compose} sans
 * changer ni cette spec ni la synchro.
 *
 * <p>{@link #useOwnSkin} laisse le joueur conserver son propre skin Minecraft :
 * dans ce cas aucune texture n'est composée et l'override est retiré.
 */
public final class SkinSpec {

    // ── Options « Type de skin » ──────────────────────────────────────
    public static final String[] SKIN_TYPES = { "RP composé", "Mon skin" };

    // ── Palette teinte de peau (rampe sombre → clair, ARGB) ───────────
    public static final int SKIN_DARK = 0xFF3B2A1E;
    public static final int SKIN_LIGHT = 0xFFFFE0BD;
    /** Arrêts discrets de teinte pour le cycleur (garde {@link #skinTone} continu). */
    public static final float[] SKIN_STOPS = { 0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f };
    public static final String[] SKIN_STOP_NAMES = {
        "Ébène", "Foncée", "Hâlée", "Mate", "Claire", "Pâle"
    };

    // ── Coiffures (procédural : hauteur de frange + couverture) ───────
    public static final String[] HAIR_STYLES = { "Rasé", "Court", "Hérissé", "Mi-long", "Long" };
    /** Nombre de rangées de frange sur le visage, par style. */
    public static final int[] HAIR_FRINGE = { 0, 1, 2, 2, 3 };

    public static final String[] HAIR_COLOR_NAMES = {
        "Noir", "Brun", "Châtain", "Blond", "Roux", "Blanc", "Bleu", "Rose", "Vert", "Violet"
    };
    public static final int[] HAIR_COLORS = {
        0xFF1A1A1E, 0xFF3A2416, 0xFF6B4A2B, 0xFFE8C878, 0xFFA83F1E,
        0xFFEDEDED, 0xFF2F6DB5, 0xFFE87FB0, 0xFF3E8E4F, 0xFF7A4FB0
    };

    public static final String[] EYE_COLOR_NAMES = {
        "Noir", "Brun", "Bleu", "Vert", "Rouge", "Ambre", "Violet", "Gris"
    };
    public static final int[] EYE_COLORS = {
        0xFF141414, 0xFF5A3A1E, 0xFF3A78C8, 0xFF3E9E5A,
        0xFFB52B2B, 0xFFD79A2B, 0xFF8A4FB0, 0xFF9AA0A6
    };

    public static final String[] OUTFIT_NAMES = {
        "Civile", "Genin", "Jōnin", "ANBU", "Kiri", "Akatsuki", "Blanche"
    };
    public static final int[] OUTFIT_COLORS = {
        0xFF6B6F76, 0xFFD9772E, 0xFF3E5E3A, 0xFF1E2024, 0xFF2F5E7A, 0xFF17161C, 0xFFDDDDDD
    };

    // ── État ──────────────────────────────────────────────────────────
    public boolean useOwnSkin = false;
    public float skinTone = 0.6f;   // 0..1 (continu ; partagé avec le slider Identité)
    public int hairStyle = 1;       // index HAIR_STYLES
    public int hairColor = 0;       // index HAIR_COLORS
    public int eyeColor = 1;        // index EYE_COLORS
    public int outfit = 1;          // index OUTFIT_COLORS

    /** Fait tourner un index dans [0, len) avec bouclage (dir = +1 / -1). */
    public static int cycle(int idx, int len, int dir) {
        return ((idx + dir) % len + len) % len;
    }

    /** Clé stable (change à chaque modif) — utile pour du cache éventuel. */
    public String key() {
        return String.format(Locale.US, "%b:%.2f:%d:%d:%d:%d",
            useOwnSkin, skinTone, hairStyle, hairColor, eyeColor, outfit);
    }

    /**
     * Queue sérialisée ajoutée à la commande {@code create} (une valeur par ligne),
     * pour que ShinobiCore stocke + rediffuse l'apparence.
     * Ordre : useOwnSkin, skinTone, hairStyle, hairColor, eyeColor, outfit.
     */
    public String serialize() {
        return (useOwnSkin ? "1" : "0")
            + "\n" + String.format(Locale.US, "%.2f", skinTone)
            + "\n" + hairStyle
            + "\n" + hairColor
            + "\n" + eyeColor
            + "\n" + outfit;
    }
}
