package fr.reborn.hud.skin;

import java.util.Locale;

/**
 * Spécification d'apparence d'un personnage Reborn (Phase 2, éditeur KORVEX).
 *
 * <p>Modèle <b>style + couleur</b> : chaque facette (cheveux, yeux, pilosité,
 * tenue) a un <b>style</b> (index de forme, cyclé par {@code ‹ N/M ›}) et une
 * <b>couleur libre</b> (ARGB, réglée par un color-picker HSV). La peau n'a qu'une
 * couleur. {@link RebornSkins} transforme la spec en texture 64×64 ; comme tous
 * les clients ont le mod, chacun compose la même image → tout le monde voit le
 * même perso. La spec voyage au serveur (commande {@code create}) qui la stocke
 * et la rediffuse dans le roster {@code reborn:character}.
 *
 * <p><b>Sans assets</b> : la composition est procédurale (aplats + variations de
 * forme selon le style). Les vrais PNG remplaceront {@link RebornSkins#compose}
 * sans toucher à cette spec ni à la synchro. {@link #useOwnSkin} conserve le skin
 * Minecraft du joueur (aucune composition).
 */
public final class SkinSpec {

    /** Nombre de teintes de peau livrées (PNG {@code character/skin/<genre>/N.png}). */
    public static final int SKIN_TONES = 10;

    // ── Noms de styles (procédural = variation de forme) ──────────────
    public static final String[] HAIR_STYLES = { "Chauve", "Court", "Hérissé", "Mi-long", "Long" };
    /** Rangées de frange sur le front, par style de cheveux. */
    public static final int[] HAIR_FRINGE = { 0, 1, 2, 2, 3 };

    public static final String[] EYE_STYLES = { "Normaux", "Fendus", "Ronds", "Perçants" };
    public static final String[] FACIAL_STYLES = { "Aucune", "Moustache", "Bouc", "Barbe" };
    public static final String[] OUTFIT_STYLES = { "Torse nu", "Débardeur", "Veste", "Manteau", "Kimono" };

    // ── Couleurs par défaut (ARGB) ────────────────────────────────────
    /** Teinte de repli si le PNG de peau est illisible (composition dégradée). */
    public static final int DEFAULT_SKIN = 0xFFD9A57D;
    public static final int DEFAULT_HAIR = 0xFF3A2416;
    public static final int DEFAULT_EYE = 0xFF5A3A1E;
    public static final int DEFAULT_FACIAL = 0xFF3A2416;
    public static final int DEFAULT_OUTFIT = 0xFFD9772E;

    // ── État ──────────────────────────────────────────────────────────
    public boolean useOwnSkin = false;

    /** Genre → jeu de PNG de peau ({@code male/} ou {@code female/}). */
    public boolean female = false;
    /**
     * Carrure : {@code true} = modèle Alex (bras 3px), {@code false} = classique
     * (Steve, 4px). Indépendante du genre côté Homme ; imposée à {@code true} pour
     * Femme. La texture est la même — seul le modèle du {@code PlayerSkin} change.
     */
    public boolean slim = false;
    /** Index de teinte de peau (0 = clair … {@link #SKIN_TONES}-1 = foncé). */
    public int skinStyle = 0;

    public int hairStyle = 1;
    public int hairColor = DEFAULT_HAIR;

    public int eyeStyle = 0;
    public int eyeColor = DEFAULT_EYE;

    public int facialStyle = 0;
    public int facialColor = DEFAULT_FACIAL;

    public int outfitStyle = 2;
    public int outfitColor = DEFAULT_OUTFIT;

    /** Fait tourner un index dans [0, len) avec bouclage (dir = +1 / -1). */
    public static int cycle(int idx, int len, int dir) {
        return ((idx + dir) % len + len) % len;
    }

    // ── Color picker HSV libre ────────────────────────────────────────
    /** HSV (h∈[0,1), s,v∈[0,1]) → ARGB opaque. */
    public static int hsvToArgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        s = clamp01(s); v = clamp01(v);
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s), q = v * (1f - f * s), t = v * (1f - (1f - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** ARGB → HSV {h,s,v} ∈ [0,1]. */
    public static float[] argbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float s = max == 0f ? 0f : d / max;
        return new float[] { h, s, max };
    }

    private static float clamp01(float x) { return x < 0f ? 0f : x > 1f ? 1f : x; }

    /**
     * Queue sérialisée ajoutée à la commande {@code create} (une valeur par ligne)
     * pour que ShinobiCore stocke + rediffuse l'apparence. Couleurs en hex RRGGBB.
     * Ordre : useOwnSkin, female, slim, skinStyle, hairStyle, hair, eyeStyle, eye,
     * facialStyle, facial, outfitStyle, outfit.
     *
     * <p>La peau n'a plus de couleur libre : elle est choisie par {@code skinStyle}
     * (index de teinte livrée) dans le jeu de PNG du genre {@code female}.
     */
    public String serialize() {
        return (useOwnSkin ? "1" : "0")
            + "\n" + (female ? "1" : "0")
            + "\n" + (slim ? "1" : "0")
            + "\n" + skinStyle
            + "\n" + hairStyle + "\n" + hex(hairColor)
            + "\n" + eyeStyle + "\n" + hex(eyeColor)
            + "\n" + facialStyle + "\n" + hex(facialColor)
            + "\n" + outfitStyle + "\n" + hex(outfitColor);
    }

    private static String hex(int argb) {
        return String.format(Locale.US, "%06X", argb & 0xFFFFFF);
    }
}
