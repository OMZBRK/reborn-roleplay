package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * CTA principal du main menu — "Appuyez pour entrer dans Reborn".
 *
 * <p>Version epuree — plus d'ombre portee ni de halo qui respire.
 * Composition (gauche → droite) :
 * <ol>
 *   <li>Pill : surface foncée + border blanche subtile + highlight 1px top
 *       (specular "glass").</li>
 *   <li>Keycap ESPACE : look raised (top highlight + bottom shadow inset)
 *       avec border accent au hover.</li>
 *   <li>Texte prompt avec une luminosité qui respire très légèrement.</li>
 * </ol>
 *
 * <p>Objectif esthétique : sobre et net. Le fond 3D MCEF porte la richesse
 * visuelle ; le CTA reste calme et lisible.
 */
public class PressSpacePrompt extends Button {

    private static final String KEY_LABEL = "ESPACE";
    private static final String PROMPT_TEXT = "Appuyez pour entrer dans Reborn";
    /** Cache des Component stylés — évite une alloc par frame (3×/render). */
    private static final Component KEY_TEXT    = RebornFont.bold(KEY_LABEL);
    private static final Component PROMPT_TEXT_STYLED = RebornFont.body(PROMPT_TEXT);

    private static final int PADDING_X = 16;
    private static final int PADDING_Y = 10;
    private static final int KEYCAP_PADDING_X = 10;
    private static final int KEYCAP_PADDING_Y = 5;
    private static final int GAP_KEYCAP_TEXT = 14;
    private static final int PILL_RADIUS = 10;
    private static final int KEYCAP_RADIUS = 4;

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
        super(x, y, width, height, Component.literal(PROMPT_TEXT),
              onPress, Button.DEFAULT_NARRATION_SUPPLIER);
    }

    public static int computeWidth(Font tr, float responsiveScale) {
        int keyW = tr.width(KEY_TEXT) + 2 * KEYCAP_PADDING_X;
        int textW = tr.width(PROMPT_TEXT_STYLED);
        // Buffer +40px de sécurité : tr.width() peut sous-estimer la
        // largeur des TTF customs Inter (advance imprécise sur certains glyphs).
        int total = PADDING_X * 2 + keyW + GAP_KEYCAP_TEXT + textW + 40;
        return Math.round(total * responsiveScale);
    }

    public static int computeHeight(Font tr, float responsiveScale) {
        int base = tr.lineHeight + 2 * KEYCAP_PADDING_Y + 2 * PADDING_Y;
        return Math.round(base * responsiveScale);
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.textRenderer;

        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered();

        // Respiration tres douce, uniquement sur la luminosite du texte.
        // Plus d'ombre portee ni de halo qui respire : on reste sobre.
        float t = (System.currentTimeMillis() % 3600L) / 3600f;
        float breath = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0);

        // ── (1) Pill BG avec faux gradient vertical ────────────
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

        // ── (1 bis) Highlight specular 1px en haut du pill ─────
        // Bande horizontale fine dans la partie supérieure pour effet
        // "verre dépoli" — donne du relief.
        context.fill(x0 + PILL_RADIUS, y0 + 1, x0 + w - PILL_RADIUS, y0 + 2, PILL_HIGHLIGHT);

        // ── (2) Keycap "ESPACE" — raised look ──────────────────
        int keyTextW = tr.width(KEY_TEXT);
        int keyTextH = tr.lineHeight;
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
        context.text(tr, KEY_TEXT,
            keyX + KEYCAP_PADDING_X, keyY + KEYCAP_PADDING_Y, Colors.WHITE_PURE, true);

        // ── (3) Texte du prompt avec luminosité respirante ─────
        int promptX = keyX + keyW + GAP_KEYCAP_TEXT;
        int promptY = y0 + (h - tr.lineHeight) / 2;
        int alpha = hovered ? 255 : Math.round(215 + 40 * breath);
        int promptColor = (alpha << 24) | 0xFFFFFF;
        context.text(tr, PROMPT_TEXT_STYLED,
            promptX, promptY, promptColor, true);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(net.minecraft.client.gui.narration.NarrationPart.TITLE,
            "Appuyez sur Espace pour entrer dans Reborn");
    }
}
