package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.MainMenuFlow;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Orchestrateur du rendu passif du main menu Reborn — layout v5 façon
 * Paladium Reforged, en deux étages ({@link MainMenuFlow}).
 *
 * <p>Composants passifs (les éléments cliquables — entrées de menu,
 * contrôles OST — restent des ClickableWidget gérés par le screen) :
 * <ul>
 *   <li>BG plein écran : Dynamic Animated Player 3D via MCEF browser.</li>
 *   <li>Voile de lisibilité sobre (dégradés haut/bas, aucun accent).</li>
 *   <li>SPLASH : {@link SplashOverlay} (logo centré + prompt clignotant).</li>
 *   <li>MENU :
 *     <ul>
 *       <li>Top-LEFT : petite marque Reborn (placeholder « nuage » ; hover
 *           → révèle le lecteur OST juste en dessous).</li>
 *       <li>Top-RIGHT : version (REBORN + tag ROLEPLAY).</li>
 *       <li>Bottom-RIGHT : crédits.</li>
 *       <li>Le menu vertical gauche + les contrôles OST sont des widgets.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see fr.reborn.hud.mixin.menu.TitleScreenMixin
 */
public final class MainMenuRenderer {

    // ─────────────── Marque top-left (placeholder nuage Akatsuki) ──
    public static final int LOGO_X = 16;
    public static final int LOGO_Y = 12;
    public static final int LOGO_SIZE = 30;

    // ─────────────── Panneau OST (révélé au hover, sous la marque) ──
    public static int ostPanelX() {
        return LOGO_X;
    }
    public static int ostPanelY() {
        return LOGO_Y + LOGO_SIZE + 8;
    }

    /**
     * Zone de hover qui révèle l'OST : la marque (étendue jusqu'au
     * panneau pour éviter un dead-zone) OU le panneau OST lui-même.
     */
    public static boolean isOverOstReveal(int mouseX, int mouseY) {
        int panelBottom = ostPanelY() + OSTPlayerV2.CARD_H;
        boolean overMark = mouseX >= LOGO_X && mouseX < LOGO_X + LOGO_SIZE
            && mouseY >= LOGO_Y && mouseY < ostPanelY();
        boolean overPanel = mouseX >= ostPanelX() && mouseX < ostPanelX() + OSTPlayerV2.CARD_W
            && mouseY >= ostPanelY() && mouseY < panelBottom;
        return overMark || overPanel;
    }

    // ─────────────── Menu vertical gauche ──────────────────────────
    /** X du bord gauche des entrées de menu. */
    public static final int MENU_X = 34;
    /** Échelle du label des entrées normales (ArcadePix). */
    public static final float MENU_ENTRY_SCALE = 2.0f;
    /** Hauteur d'une entrée normale (zone cliquable). */
    public static final int MENU_ENTRY_H = 22;
    /** Échelle de l'entrée primaire (JOUER) — plus grosse que les autres. */
    public static final float MENU_ENTRY_SCALE_PRIMARY = 2.8f;
    /** Hauteur de l'entrée primaire. */
    public static final int MENU_ENTRY_H_PRIMARY = 32;
    /** Espacement vertical entre entrées. */
    public static final int MENU_ENTRY_GAP = 6;

    /** Y de la première entrée (JOUER primaire) — bloc centré autour de 52%.
     *  {@code entryCount} = total (1 primaire + N normales). */
    public static int menuStartY(int screenH, int entryCount) {
        int block = MENU_ENTRY_H_PRIMARY
            + (entryCount - 1) * MENU_ENTRY_H
            + (entryCount - 1) * MENU_ENTRY_GAP;
        return Math.round(screenH * 0.52f) - block / 2;
    }

    /**
     * Multiplicateur responsive — réduit l'UI sur petites fenêtres.
     * Conservé pour {@link fr.reborn.hud.menu.connect.ConnectingRenderer}.
     */
    public static float responsiveScale(int screenW) {
        if (screenW <= 700) return 0.55f;
        if (screenW <= 900) return 0.70f;
        if (screenW <= 1100) return 0.85f;
        return 1.0f;
    }

    private MainMenuRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH,
                              int mouseX, int mouseY, boolean ostRevealed) {
        // 1. Dynamic Animated Player en BG (MCEF browser plein écran).
        DynamicPlayerBackground.render(ctx, screenW, screenH);

        // 2. Voile de lisibilité sobre : fins dégradés haut/bas.
        renderLegibilityScrim(ctx, screenW, screenH);

        if (MainMenuFlow.isSplash()) {
            SplashOverlay.render(ctx, screenW, screenH);
            return;
        }

        // ── MENU ────────────────────────────────────────────────
        renderTopLeftMark(ctx, ostRevealed);
        renderVersionTopRight(ctx, screenW);
        renderCreditsBottomRight(ctx, screenW, screenH);

        // Fond du panneau OST (les contrôles sont des widgets rendus
        // par-dessus par le screen).
        if (ostRevealed) {
            OSTPlayerV2.renderBackground(ctx, ostPanelX(), ostPanelY());
        }
    }

    /**
     * Voile de lisibilité minimal : deux dégradés doux (haut + bas) pour
     * garantir le contraste du texte sur un BG MCEF potentiellement clair.
     */
    private static void renderLegibilityScrim(DrawContext ctx, int screenW, int screenH) {
        int band = Math.max(64, screenH / 5);
        verticalFade(ctx, 0, 0, screenW, band, 0x66000000, 0x00000000);
        verticalFade(ctx, 0, screenH - band, screenW, band, 0x00000000, 0x88000000);
    }

    /**
     * Petite marque Reborn top-left — PLACEHOLDER. À remplacer par la
     * texture nuage Akatsuki dessinée sous Aseprite (déposer un PNG dans
     * {@code assets/reborn/textures/gui/title/cloud.png} et swapper ce
     * bloc par un {@code ctx.drawTexture(...)}).
     *
     * <p>Pour l'instant : blob crimson arrondi + « R » display. Bordure qui
     * s'éclaire au survol pour indiquer que c'est interactif (révèle l'OST).
     */
    private static void renderTopLeftMark(DrawContext ctx, boolean hovered) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int x = LOGO_X;
        int y = LOGO_Y;
        int s = LOGO_SIZE;

        int bg = hovered ? Colors.ACCENT_SOFT : Colors.SURFACE;
        int border = hovered ? Colors.ACCENT_HOVER : Colors.BORDER_STRONG;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, s, s, 8, bg, border);

        // « R » ArcadePix centré (placeholder du futur nuage Akatsuki).
        Text r = RebornFont.arcade("R");
        float scale = 2.0f;
        int rw = Math.round(tr.getWidth(r) * scale);
        int rh = Math.round(tr.fontHeight * scale);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + (s - rw) / 2f, y + (s - rh) / 2f, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, r, 0, 0, hovered ? Colors.WHITE_PURE : Colors.FOREGROUND, false);
        ctx.getMatrices().pop();
    }

    /** Version en haut-droite : « REBORN <ver> » + tag « ROLEPLAY ». */
    private static void renderVersionTopRight(DrawContext ctx, int screenW) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        // ArcadePix, majuscules, plus petit, même couleur pour les 2 lignes.
        Text line1 = RebornFont.arcade("REBORN " + RebornVersion.MOD_VERSION.toUpperCase());
        Text line2 = RebornFont.arcade("ROLEPLAY");
        float scale = 0.8f;
        int color = 0xFFD1D5DB;
        drawRightAligned(ctx, tr, line1, scale, screenW - 14, 12, color);
        drawRightAligned(ctx, tr, line2, scale, screenW - 14, 20, color);
    }

    /**
     * Crédits en bas-droite du menu — mêmes lignes que le splash (ArcadePix),
     * en plus petit, alignées à droite.
     */
    private static void renderCreditsBottomRight(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        float scale = 0.6f;
        Text l1 = RebornFont.arcade(RebornVersion.SPLASH_CREDIT_1);
        Text l2 = RebornFont.arcade(RebornVersion.SPLASH_CREDIT_2);
        drawRightAligned(ctx, tr, l2, scale, screenW - 12, screenH - 10, 0xFFB6BAC2);
        drawRightAligned(ctx, tr, l1, scale, screenW - 12, screenH - 17, 0xFFD1D5DB);
    }

    /** Dessine {@code text} (scaled) aligné à droite sur {@code rightX}, baseline {@code y}. */
    private static void drawRightAligned(DrawContext ctx, TextRenderer tr, Text text,
                                         float scale, int rightX, int y, int color) {
        int w = Math.round(tr.getWidth(text) * scale);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(rightX - w, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, text, 0, 0, color, true);
        ctx.getMatrices().pop();
    }

    private static void verticalFade(DrawContext ctx, int x, int y, int w, int h, int colorTop, int colorBot) {
        if (h <= 0 || w <= 0) return;
        for (int i = 0; i < h; i++) {
            float t = (float) i / h;
            int color = Colors.lerp(colorTop, colorBot, t);
            ctx.fill(x, y + i, x + w, y + i + 1, color);
        }
    }
}
