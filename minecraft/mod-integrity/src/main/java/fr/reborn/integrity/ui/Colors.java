package fr.reborn.integrity.ui;

/**
 * Palette Reborn — extraite du design system v2 ({@code styles.css}
 * du dossier reborn-design-prep/minecraft-main-menu/).
 *
 * <p>Toutes les couleurs sont au format ARGB int (0xAARRGGBB) compatible
 * avec {@code DrawContext.fill}. Les variantes "soft" / "glow" intègrent
 * déjà leur alpha — pas besoin de manipuler la transparence dans le caller.
 *
 * <p>Référence visuelle :
 * <ul>
 *   <li>accent #3b5bdb (Zenkai blue) — couleur Reborn signature</li>
 *   <li>danger #ef4444 — UNIQUEMENT pour quitter / kick / état critique</li>
 *   <li>Aucun rouge en accent principal (consigne design)</li>
 * </ul>
 */
public final class Colors {

    private Colors() {}

    // ─────────────────── Surfaces ───────────────────
    public static final int BACKGROUND        = 0xFF07080B;
    public static final int SURFACE           = 0xFF0E1014;
    public static final int SURFACE_ELEVATED  = 0xFF15181F;
    public static final int SURFACE_OVERLAY   = 0xFF1A1E27;
    public static final int BORDER            = 0xFF1D212A;
    public static final int BORDER_STRONG     = 0xFF2A2F3A;
    public static final int MUTED             = 0xFF6B7280;

    /** Backdrop semi-opaque utilisé pour les modals + ESC menu. */
    public static final int BACKDROP_85 = 0xD9050608;
    public static final int BACKDROP_60 = 0x99050608;

    // ─────────────────── Foreground ──────────────────
    public static final int FOREGROUND          = 0xFFE5E7EB;
    public static final int FOREGROUND_SUBTLE   = 0xFF9CA3AF;
    public static final int FOREGROUND_MUTED    = 0xFF6B7280;
    public static final int WHITE_PURE          = 0xFFFFFFFF;

    // ─────────────────── Accent (Zenkai blue) ────────
    public static final int ACCENT              = 0xFF3B5BDB;
    public static final int ACCENT_HOVER        = 0xFF4C6CE6;
    public static final int ACCENT_PRESSED      = 0xFF2F4CC4;
    public static final int ACCENT_SOFT         = 0xFF1A2550;
    /** Glow externe pour outline + boutons primaires. Alpha 35%. */
    public static final int ACCENT_GLOW         = 0x593B5BDB;
    /** Glow fort, alpha 55%. */
    public static final int ACCENT_GLOW_STRONG  = 0x8C3B5BDB;
    /** Couleur du LogoSigil halo (alpha 60% du shared.jsx). */
    public static final int ACCENT_HALO         = 0x993B5BDB;

    // ─────────────────── Semantic ────────────────────
    public static final int SUCCESS       = 0xFF16A34A;
    public static final int WARNING       = 0xFFF59E0B;
    public static final int DANGER        = 0xFFEF4444;
    public static final int DANGER_SOFT   = 0xFF3A1414;
    /** Halo danger (slash mark du LogoSigil, alpha 85%). */
    public static final int DANGER_GLOW   = 0xD9EF4444;

    // ─────────────────── Roles ───────────────────────
    public static final int ROLE_WHITELISTED = 0xFF3B5BDB;
    public static final int ROLE_STAFF       = 0xFF8B5CF6;
    public static final int ROLE_ADMIN       = 0xFFEF4444;
    public static final int ROLE_FOUNDER     = 0xFFF59E0B;

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
