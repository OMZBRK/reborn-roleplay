package fr.reborn.hud.menu;

/**
 * Palette Reborn — design tokens v3 (Akatsuki direction).
 *
 * <p>Toutes les couleurs sont au format ARGB int (0xAARRGGBB) compatible
 * avec {@code GuiGraphicsExtractor.fill}. Les variantes "soft" / "glow" intègrent
 * déjà leur alpha — pas besoin de manipuler la transparence dans le caller.
 *
 * <p>Synchronise avec {@code apps/launcher/src/styles/globals.css} et
 * {@code docs/REBORN_ASEPRITE_PALETTE.md}.
 *
 * <p>Référence visuelle :
 * <ul>
 *   <li>accent #A0182B (Akatsuki crimson) — couleur Reborn signature</li>
 *   <li>gold #D9A95E — sub-actions, contrastes secondaires</li>
 *   <li>foreground #F5E9D0 (ivoire chaud, jamais blanc pur sur fond sombre)</li>
 *   <li>danger #EF4444 — UNIQUEMENT pour quitter / kick / état critique</li>
 * </ul>
 */
public final class Colors {

    private Colors() {}

    // ─────────────────── Surfaces (bordeaux warm) ─────
    public static final int BACKGROUND        = 0xFF0A0608;
    public static final int SURFACE           = 0xFF15090B;
    public static final int SURFACE_ELEVATED  = 0xFF1F0E11;
    public static final int SURFACE_OVERLAY   = 0xFF2A1418;
    public static final int BORDER            = 0xFF2D181C;
    public static final int BORDER_STRONG     = 0xFF4A2127;
    public static final int MUTED             = 0xFF7A6E5C;

    /** Backdrop semi-opaque utilisé pour les modals + ESC menu. */
    public static final int BACKDROP_85 = 0xD9050304;
    public static final int BACKDROP_60 = 0x99050304;

    // ─────────────────── Foreground (ivoire chaud) ────
    public static final int FOREGROUND          = 0xFFF5E9D0;
    public static final int FOREGROUND_SUBTLE   = 0xFFC2B59A;
    public static final int FOREGROUND_MUTED    = 0xFF7A6E5C;
    public static final int WHITE_PURE          = 0xFFFFFFFF;

    // ─────────────────── Accent (Akatsuki crimson) ────
    public static final int ACCENT              = 0xFFA0182B;
    public static final int ACCENT_HOVER        = 0xFFC01E35;
    public static final int ACCENT_PRESSED      = 0xFF7A1322;
    public static final int ACCENT_SOFT         = 0xFF3A0A12;
    /** Glow externe pour outline + boutons primaires. Alpha 35%. */
    public static final int ACCENT_GLOW         = 0x59A0182B;
    /** Glow fort, alpha 55%. */
    public static final int ACCENT_GLOW_STRONG  = 0x8CC01E35;
    /** Couleur du LogoSigil halo (alpha 60%). */
    public static final int ACCENT_HALO         = 0x99A0182B;

    // ─────────────────── Gold (accent secondaire) ────
    /** Or chaud pour sub-actions / hover / hairline lights. */
    public static final int GOLD                = 0xFFD9A95E;
    public static final int GOLD_SOFT           = 0xFF3A2C14;
    /** Glow gold (alpha 40%). */
    public static final int GOLD_GLOW           = 0x66D9A95E;

    // ─────────────────── Semantic ────────────────────
    public static final int SUCCESS       = 0xFF4ADE80;
    public static final int WARNING       = 0xFFF59E0B;
    public static final int DANGER        = 0xFFEF4444;
    public static final int DANGER_SOFT   = 0xFF3A1414;
    /** Halo danger (slash mark du LogoSigil, alpha 85%). */
    public static final int DANGER_GLOW   = 0xD9EF4444;

    // ─────────────────── Roles ───────────────────────
    public static final int ROLE_WHITELISTED = 0xFF8B5CF6;
    public static final int ROLE_STAFF       = 0xFFD9A95E;
    public static final int ROLE_ADMIN       = 0xFFA0182B;
    public static final int ROLE_FOUNDER     = 0xFFF5E9D0;

    // ─────────────────── Sakura (rose) ────────────────
    /** Couleur de base d'une pétale sakura (hsl(340, 78%, 78%)). */
    public static final int PETAL_BASE   = 0xFFF1B0CC;
    /** Stroke plus foncée. */
    public static final int PETAL_STROKE = 0xFFD17BA1;

    // ─────────────────── Utilities ────────────────────
    public static final int TRANSPARENT = 0x00000000;

    /**
     * Applique un alpha (0.0..1.0) à une couleur ARGB existante. L'alpha
     * actuel est ignoré et remplacé.
     */
    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Interpole linéairement entre deux couleurs ARGB. Utilisé pour les
     * animations de hover / fade-in.
     */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        int rA = Math.round(aA + (bA - aA) * t);
        int rR = Math.round(aR + (bR - aR) * t);
        int rG = Math.round(aG + (bG - aG) * t);
        int rB = Math.round(aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }
}
