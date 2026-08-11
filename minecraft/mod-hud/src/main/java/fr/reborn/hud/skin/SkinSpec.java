package fr.reborn.hud.skin;

import java.util.Locale;

/**
 * Spécification d'apparence d'un personnage Reborn (créateur KORVEX, Phase 2).
 *
 * <p>Modèle <b>style + couleur</b> : chaque facette (cheveux, yeux, pilosité,
 * tenue) référence un <b>asset du catalogue</b> par son {@code id} (nom nommé,
 * ex. {@code Cheveux_Sasuke}) + une ou plusieurs <b>couleurs</b>. La peau est un
 * index de teinte livrée. {@link RebornSkins} transforme la spec en texture
 * 64×64 ; comme tous les clients ont le mod + le même catalogue bundlé, chacun
 * compose la même image → tout le monde voit le même perso. La spec voyage au
 * serveur (commande {@code create}) qui la stocke <b>opaque</b> et la rediffuse.
 *
 * <p>Migration : les anciens index numériques (<i>hairStyle</i>…) sont remplacés
 * par des <b>id d'asset</b> ({@code hairId}…), résolus via {@link CharacterCatalog}.
 * {@link #useOwnSkin} conserve le skin Minecraft du joueur (aucune composition).
 */
public final class SkinSpec {

    /** Nombre de teintes de peau livrées (PNG {@code character/skin/<genre>/N.png}). */
    public static final int SKIN_TONES = 10;

    /** Nombre de zones recolorables d'une tenue (canaux R/G/B/A du masque). */
    public static final int OUTFIT_ZONES = 4;

    // ── Couleurs par défaut (ARGB) ────────────────────────────────────
    /** Teinte de repli si le PNG de peau est illisible (composition dégradée). */
    public static final int DEFAULT_SKIN = 0xFFD9A57D;
    public static final int DEFAULT_HAIR = 0xFF3A2416;
    public static final int DEFAULT_EYE = 0xFF5A3A1E;
    public static final int DEFAULT_FACIAL = 0xFF3A2416;
    public static final int DEFAULT_OUTFIT = 0xFFFFFFFF;

    // ── Id d'asset par défaut (voir catalog.json) ─────────────────────
    public static final String DEFAULT_HAIR_ID = "Cheveux_Base";
    public static final String DEFAULT_EYE_ID = "Yeux_Style1";

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

    /** Id d'asset cheveux ({@code ""} = chauve). */
    public String hairId = DEFAULT_HAIR_ID;
    public int hairColor = DEFAULT_HAIR;

    /** Id d'asset yeux. */
    public String eyeId = DEFAULT_EYE_ID;
    /** Iris œil GAUCHE (ARGB). */
    public int eyeColor = DEFAULT_EYE;
    /** Iris œil DROIT (ARGB) — permet l'hétérochromie (yeux de couleurs différentes). */
    public int eyeColorRight = DEFAULT_EYE;

    /** Id d'asset pilosité ({@code ""} = imberbe). */
    public String facialId = "";
    public int facialColor = DEFAULT_FACIAL;

    /** Id d'asset tenue ({@code ""} = torse nu). */
    public String outfitId = "";
    /**
     * Couleurs des zones recolorables de la tenue (canaux R/G/B/A du masque). Non
     * utilisées tant qu'aucun {@code <id>_Mask.png} + {@code zones} n'existent pour
     * l'asset (la tenue s'affiche alors telle que peinte).
     */
    public final int[] outfitZone = { DEFAULT_OUTFIT, DEFAULT_OUTFIT, DEFAULT_OUTFIT, DEFAULT_OUTFIT };

    /** Fait tourner un index dans [0, len) avec bouclage (dir = +1 / -1). */
    public static int cycle(int idx, int len, int dir) {
        if (len <= 0) return 0;
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
     * pour que ShinobiCore stocke + rediffuse l'apparence (blob opaque). Couleurs en
     * hex RRGGBB. Ordre (v2) : useOwnSkin, female, slim, skinStyle, hairId, hairColor,
     * eyeId, eyeColor, eyeColorRight, facialId, facialColor, outfitId, oz0..oz3.
     */
    public String serialize() {
        return (useOwnSkin ? "1" : "0")
            + "\n" + (female ? "1" : "0")
            + "\n" + (slim ? "1" : "0")
            + "\n" + skinStyle
            + "\n" + nz(hairId) + "\n" + hex(hairColor)
            + "\n" + nz(eyeId) + "\n" + hex(eyeColor) + "\n" + hex(eyeColorRight)
            + "\n" + nz(facialId) + "\n" + hex(facialColor)
            + "\n" + nz(outfitId)
            + "\n" + hex(outfitZone[0]) + "\n" + hex(outfitZone[1])
            + "\n" + hex(outfitZone[2]) + "\n" + hex(outfitZone[3]);
    }

    private static String hex(int argb) {
        return String.format(Locale.US, "%06X", argb & 0xFFFFFF);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /**
     * Reconstruit une spec depuis la queue {@link #serialize()} (rediffusée par
     * ShinobiCore dans le roster). Robuste : un blob vide/partiel retombe sur les
     * valeurs par défaut pour les champs manquants.
     */
    public static SkinSpec deserialize(String blob) {
        SkinSpec s = new SkinSpec();
        if (blob == null || blob.isBlank()) return s;
        String[] p = blob.split("\n", -1);
        try {
            int i = 0;
            s.useOwnSkin    = "1".equals(p[i++].trim());
            s.female        = "1".equals(p[i++].trim());
            s.slim          = "1".equals(p[i++].trim());
            s.skinStyle     = pi(p[i++]);
            s.hairId        = p[i++].trim();
            s.hairColor     = pc(p[i++]);
            s.eyeId         = p[i++].trim();
            s.eyeColor      = pc(p[i++]);
            s.eyeColorRight = pc(p[i++]);
            s.facialId      = p[i++].trim();
            s.facialColor   = pc(p[i++]);
            s.outfitId      = p[i++].trim();
            for (int z = 0; z < OUTFIT_ZONES && i < p.length; z++) s.outfitZone[z] = pc(p[i++]);
        } catch (RuntimeException ignored) {
            // blob tronqué → on garde ce qui a pu être lu + défauts sur le reste.
        }
        return s;
    }

    private static int pi(String s) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return 0; }
    }

    /** Hex RRGGBB → ARGB opaque (repli gris si illisible). */
    private static int pc(String hex) {
        try { return 0xFF000000 | (Integer.parseInt(hex.trim(), 16) & 0xFFFFFF); }
        catch (RuntimeException e) { return 0xFF888888; }
    }
}
