package fr.reborn.hud.ui.style;

/**
 * Palette de couleurs Reborn, extraite des maquettes HTML
 * ({@code reborn-hud-editor.html}, {@code reborn-chat.html}).
 *
 * <p>Format : {@code 0xAARRGGBB} pour DrawContext.fill().
 * Les constantes "SOFT" / "GLOW" sont des versions à alpha réduit du
 * même teint.
 */
public final class RebornColors {

    private RebornColors() {}

    // ─────────── Backgrounds ───────────
    public static final int BG_DEEP             = 0xFF07080B;
    public static final int BG_PANEL            = 0xEB0E1014; // 92%
    public static final int BG_PANEL_ELEVATED   = 0xF2151820; // 95%
    public static final int BG_INPUT            = 0xE61A1E27; // 90%
    public static final int SURFACE_OVERLAY     = 0xFF1A1E27;

    // ─────────── Borders ───────────
    public static final int BORDER              = 0x14FFFFFF; // 8%
    public static final int BORDER_STRONG       = 0x24FFFFFF; // 14%

    // ─────────── Accent (Reborn blue) ───────────
    public static final int ACCENT              = 0xFF3B5BDB;
    public static final int ACCENT_HOVER        = 0xFF4C6CE6;
    public static final int ACCENT_SOFT         = 0x2D3B5BDB; // 18%
    public static final int ACCENT_GLOW         = 0x663B5BDB; // 40%

    // ─────────── Foreground ───────────
    public static final int FOREGROUND          = 0xFFE5E7EB;
    public static final int FOREGROUND_SUBTLE   = 0xFF9CA3AF;
    public static final int FOREGROUND_MUTED    = 0xFF6B7280;

    // ─────────── States ───────────
    public static final int DANGER              = 0xFFEF4444;
    public static final int DANGER_SOFT         = 0x24EF4444; // 14%
    public static final int SUCCESS             = 0xFF16A34A;
    public static final int WARNING             = 0xFFF59E0B;
    public static final int WARNING_SOFT        = 0x26F59E0B; // 15%

    // ─────────── RP / Channels ───────────
    public static final int RP_COLOR            = 0xFFC084FC;
    public static final int RP_SOFT             = 0x1FC084FC; // 12%
    public static final int GROUP_COLOR         = 0xFF10B981;

    /** Override l'alpha d'une couleur ARGB. */
    public static int withAlpha(int argb, int alphaByte) {
        return ((alphaByte & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    /** Linear blend RGB de deux couleurs : t=0 → a, t=1 → b. Alpha de a. */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        int r = Math.round(aR + (bR - aR) * t);
        int g = Math.round(aG + (bG - aG) * t);
        int bl = Math.round(aB + (bB - aB) * t);
        return (aA << 24) | (r << 16) | (g << 8) | bl;
    }
}
