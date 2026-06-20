package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * CTA principal du main menu — "Appuyez pour entrer dans Reborn".
 *
 * <p>Composition (gauche → droite) :
 * <ol>
 *   <li>Drop shadow doux sous le pill (lift + détache du fond MCEF clair).</li>
 *   <li>Outer glow blanc translucide qui respire (slow breathing).</li>
 *   <li>Pill : surface foncée + border blanche subtile + highlight 1px top
 *       (specular "glass").</li>
 *   <li>Keycap ESPACE : look raised (top highlight + bottom shadow inset)
 *       avec border accent au hover.</li>
 *   <li>Texte prompt avec luminosité respirante.</li>
 * </ol>
 *
 * <p>Objectif esthétique : feel premium / minimalist, type Lunar / Feather
 * UI — beaucoup de subtilités plutôt qu'une grosse animation.
 */
public class PressSpacePrompt extends ButtonWidget {

    private static final String KEY_LABEL = "ESPACE";
    private static final String PROMPT_TEXT = "Appuyez pour entrer dans Reborn";
    /** Cache des Text stylés — évite une alloc par frame (3×/render). */
    private static final Text KEY_TEXT    = RebornFont.bold(KEY_LABEL);
    private static final Text PROMPT_TEXT_STYLED = RebornFont.body(PROMPT_TEXT);

    private static final int PADDING_X = 16;
    private static final int PADDING_Y = 10;
    private static final int KEYCAP_PADDING_X = 10;
    private static final int KEYCAP_PADDING_Y = 5;
    private static final int GAP_KEYCAP_TEXT = 14;
    private static final int PILL_RADIUS = 10;
    private static final int KEYCAP_RADIUS = 4;

    /** Couches de glow extérieur (anneau de lumière diffus). 3 couches
     *  suffisent visuellement, moitié moins de fill calls que 5. */
    private static final int GLOW_LAYERS = 3;

    // ─── Palette dédiée prompt ──────────────────────────────────
    // Fond pill : noir bleuté très opaque pour bien ressortir sur les
    // backgrounds MCEF clairs (sunset, jour, plages). On garde un soupçon
    // de bleu pour rester dans la palette Zenkai sans tirer vers le froid.
    private static final int PILL_BG_TOP      = 0xF0151823;
    private static final int PILL_BG_BOTTOM   = 0xF00A0C12;
    private static final int PILL_BG_HOVER_TOP    = 0xF61F2330;
    private static final int PILL_BG_HOVER_BOTTOM = 0xF60E1119;

    // Border pill : blanche translucide, plus marquée au hover.
    private static final int PILL_BORDER_IDLE  = 0x33FFFFFF;
    private static final int PILL_BORDER_HOVER = 0x66FFFFFF;

    // Highlight specular en haut du pill (effet "verre").
    private static final int PILL_HIGHLIGHT    = 0x26FFFFFF;

    // Keycap : raised look avec gradient + edges contrastés.
    private static final int KEY_BG_TOP        = 0xFF2A2F3D;
    private static final int KEY_BG_BOTTOM     = 0xFF0F1219;
    private static final int KEY_HIGHLIGHT_TOP = 0x80FFFFFF;
    private static final int KEY_SHADOW_BOT    = 0x66000000;
    private static final int KEY_BORDER_IDLE   = 0x99FFFFFF;
    private static final int KEY_BORDER_HOVER  = Colors.ACCENT_HOVER;

    public PressSpacePrompt(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, Text.literal(PROMPT_TEXT),
              onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    public static int computeWidth(TextRenderer tr, float responsiveScale) {
        int keyW = tr.getWidth(KEY_TEXT) + 2 * KEYCAP_PADDING_X;
        int textW = tr.getWidth(PROMPT_TEXT_STYLED);
        // Buffer +40px de sécurité : tr.getWidth() peut sous-estimer la
        // largeur des TTF customs Inter (advance imprécise sur certains glyphs).
        int total = PADDING_X * 2 + keyW + GAP_KEYCAP_TEXT + textW + 40;
        return Math.round(total * responsiveScale);
    }

    public static int computeHeight(TextRenderer tr, float responsiveScale) {
        int base = tr.fontHeight + 2 * KEYCAP_PADDING_Y + 2 * PADDING_Y;
        return Math.round(base * responsiveScale);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered();

        // Breath cycle ~3s. 0..1 sinusoidal.
        float t = (System.currentTimeMillis() % 3000L) / 3000f;
        float breath = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0);

        // ── (1) Drop shadow sous le pill ───────────────────────
        // 3 couches descendantes pour effet diffus naturel.
        for (int i = 1; i <= 3; i++) {
            int alpha = 60 - i * 15;
            int shadow = (alpha << 24);
            DrawHelpers.roundedRect(context,
                x0 + i, y0 + h - 2 + i, w - 2 * i, 4,
                Math.max(0, PILL_RADIUS - i), shadow);
        }

        // ── (2) Outer glow blanc qui respire ───────────────────
        // Halo subtil — l'amplitude grandit au hover.
        float glowStrength = hovered ? 0.85f : 0.45f;
        for (int i = GLOW_LAYERS; i >= 1; i--) {
            float layerStrength = (1f - (float) i / (GLOW_LAYERS + 1));
            // 0.15f par couche : compense la réduction 5→3 layers (intégrale
            // cumulée alpha quasi identique au précédent design).
            float alphaF = 0.15f * layerStrength * glowStrength * (0.55f + 0.45f * breath);
            int alpha = Math.round(alphaF * 255);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | 0xFFFFFF;
            DrawHelpers.roundedRect(context,
                x0 - i, y0 - i, w + 2 * i, h + 2 * i,
                PILL_RADIUS + i, color);
        }

        // ── (3) Pill BG avec faux gradient vertical ────────────
        // On dessine 2 rounded rects empilés : top color sur la moitié
        // supérieure (radius normal), bottom color sur la moitié inférieure
        // (radius bottom only, mais notre helper round 4 corners → on
        // dessine la version full puis on overlay le top half).
        int bgTop    = hovered ? PILL_BG_HOVER_TOP : PILL_BG_TOP;
        int bgBottom = hovered ? PILL_BG_HOVER_BOTTOM : PILL_BG_BOTTOM;
        int border   = hovered ? PILL_BORDER_HOVER : PILL_BORDER_IDLE;

        // Base : bottom color sur tout le pill (border inclu).
        DrawHelpers.roundedOutlinedRect(context, x0, y0, w, h, PILL_RADIUS, bgBottom, border);
        // Top half : top color overlay, en respectant un rect plus petit
        // qui laisse intacts les bottom corners (donc on l'écrase d'1px
        // de chaque côté pour ne pas déborder sur la border).
        int topHalfH = h / 2;
        DrawHelpers.roundedRect(context,
            x0 + 1, y0 + 1, w - 2, topHalfH - 1,
            PILL_RADIUS - 1, bgTop);

        // ── (3 bis) Highlight specular 1px en haut du pill ─────
        // Bande horizontale fine dans la partie supérieure pour effet
        // "verre dépoli" — donne du relief.
        context.fill(x0 + PILL_RADIUS, y0 + 1, x0 + w - PILL_RADIUS, y0 + 2, PILL_HIGHLIGHT);

        // ── (4) Keycap "ESPACE" — raised look ──────────────────
        int keyTextW = tr.getWidth(KEY_TEXT);
        int keyTextH = tr.fontHeight;
        int keyW = keyTextW + 2 * KEYCAP_PADDING_X;
        int keyH = keyTextH + 2 * KEYCAP_PADDING_Y;
        int keyX = x0 + PADDING_X;
        int keyY = y0 + (h - keyH) / 2;
        int keyBorder = hovered ? KEY_BORDER_HOVER : KEY_BORDER_IDLE;

        // Base + bord.
        DrawHelpers.roundedOutlinedRect(context, keyX, keyY, keyW, keyH, KEYCAP_RADIUS,
            KEY_BG_BOTTOM, keyBorder);
        // Top half lighter color.
        DrawHelpers.roundedRect(context,
            keyX + 1, keyY + 1, keyW - 2, keyH / 2,
            Math.max(0, KEYCAP_RADIUS - 1), KEY_BG_TOP);
        // Top highlight 1px (specular).
        context.fill(keyX + KEYCAP_RADIUS, keyY + 1,
            keyX + keyW - KEYCAP_RADIUS, keyY + 2, KEY_HIGHLIGHT_TOP);
        // Bottom shadow 1px (inset bas).
        context.fill(keyX + KEYCAP_RADIUS, keyY + keyH - 2,
            keyX + keyW - KEYCAP_RADIUS, keyY + keyH - 1, KEY_SHADOW_BOT);

        // Texte ESPACE — pure blanc + shadow pour lisibilité.
        context.drawText(tr, KEY_TEXT,
            keyX + KEYCAP_PADDING_X, keyY + KEYCAP_PADDING_Y, Colors.WHITE_PURE, true);

        // ── (5) Texte du prompt avec luminosité respirante ─────
        int promptX = keyX + keyW + GAP_KEYCAP_TEXT;
        int promptY = y0 + (h - tr.fontHeight) / 2;
        int alpha = hovered ? 255 : Math.round(215 + 40 * breath);
        int promptColor = (alpha << 24) | 0xFFFFFF;
        context.drawText(tr, PROMPT_TEXT_STYLED,
            promptX, promptY, promptColor, true);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            "Appuyez sur Espace pour entrer dans Reborn");
    }
}
