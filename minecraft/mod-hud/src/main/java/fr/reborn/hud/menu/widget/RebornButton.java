package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Bouton Reborn modal — rectangle arrondi avec border 1px, label centré
 * en Inter Bold. Anime au survol :
 * <ul>
 *   <li>Fade vers la couleur hover (lerp 250ms)</li>
 *   <li>Halo lumineux extérieur qui pulse</li>
 *   <li>Léger scale-up du label (1.0 → 1.06)</li>
 * </ul>
 *
 * <p>Deux variantes prédéfinies :
 * <ul>
 *   <li>{@code RebornButton.ghost(...)} : fond transparent, border subtil,
 *       hover = surface elevated + border accent. Pour actions secondaires
 *       (Annuler, Retour).</li>
 *   <li>{@code RebornButton.danger(...)} : fond rouge, hover plus lumineux
 *       + halo rouge. Pour actions destructives (Quitter, Supprimer).</li>
 * </ul>
 */
public class RebornButton extends ButtonWidget {

    public enum Style { GHOST, DANGER, ACCENT }

    private final Style style;
    /** État de hover lissé 0..1 — anime smooth in/out au lieu de jump. */
    private float hoverProgress = 0f;
    private long lastFrameMs = System.currentTimeMillis();

    private RebornButton(int x, int y, int w, int h, Text label, Style style, PressAction onPress) {
        super(x, y, w, h, label, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        this.style = style;
    }

    public static RebornButton ghost(int x, int y, int w, int h, String label, PressAction onPress) {
        return new RebornButton(x, y, w, h, RebornFont.bold(label), Style.GHOST, onPress);
    }

    public static RebornButton danger(int x, int y, int w, int h, String label, PressAction onPress) {
        return new RebornButton(x, y, w, h, RebornFont.bold(label), Style.DANGER, onPress);
    }

    public static RebornButton accent(int x, int y, int w, int h, String label, PressAction onPress) {
        return new RebornButton(x, y, w, h, RebornFont.bold(label), Style.ACCENT, onPress);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        // Smooth hover transition (~140ms).
        long now = System.currentTimeMillis();
        float dt = (now - lastFrameMs) / 140f;
        lastFrameMs = now;
        float target = isHovered() ? 1f : 0f;
        hoverProgress += (target - hoverProgress) * Math.min(1f, dt);
        hoverProgress = Math.max(0f, Math.min(1f, hoverProgress));

        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();

        int idleBg, hoverBg, idleBorder, hoverBorder, idleText, hoverText, glowColor;
        switch (style) {
            case DANGER -> {
                idleBg     = 0xFFB91C1C;          // rouge profond
                hoverBg    = 0xFFDC2626;          // rouge vif
                idleBorder = 0xFFEF4444;
                hoverBorder = 0xFFFCA5A5;
                idleText   = Colors.WHITE_PURE;
                hoverText  = Colors.WHITE_PURE;
                glowColor  = 0xFFEF4444;
            }
            case ACCENT -> {
                idleBg     = Colors.ACCENT;
                hoverBg    = Colors.ACCENT_HOVER;
                idleBorder = Colors.ACCENT_HOVER;
                hoverBorder = Colors.WHITE_PURE;
                idleText   = Colors.WHITE_PURE;
                hoverText  = Colors.WHITE_PURE;
                glowColor  = Colors.ACCENT;
            }
            default -> { // GHOST
                idleBg     = 0;
                hoverBg    = Colors.SURFACE_ELEVATED;
                idleBorder = Colors.BORDER_STRONG;
                hoverBorder = Colors.ACCENT;
                idleText   = Colors.FOREGROUND;
                hoverText  = Colors.WHITE_PURE;
                glowColor  = Colors.ACCENT;
            }
        }

        // Halo extérieur — proportionnel au hoverProgress + pulse continu.
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 380.0);
        float haloIntensity = hoverProgress * pulse;
        if (haloIntensity > 0.01f) {
            int haloLayers = 5;
            for (int i = haloLayers; i >= 1; i--) {
                float layerT = 1f - i / (float) (haloLayers + 1);
                int alpha = Math.round(haloIntensity * layerT * 110);
                int c = (alpha << 24) | (glowColor & 0x00FFFFFF);
                DrawHelpers.roundedRect(ctx, x0 - i, y0 - i, w + 2 * i, h + 2 * i, 6 + i, c);
            }
        }

        // Fond + border lerp idle → hover.
        int bg = lerp(idleBg, hoverBg, hoverProgress);
        int border = lerp(idleBorder, hoverBorder, hoverProgress);
        DrawHelpers.roundedOutlinedRect(ctx, x0, y0, w, h, 6, bg, border);

        // Label centré avec léger scale-up au hover.
        float labelScale = 1f + 0.06f * hoverProgress;
        int textColor = lerp(idleText, hoverText, hoverProgress);
        int textW = Math.round(tr.getWidth(getMessage()) * labelScale);
        int textH = Math.round(tr.fontHeight * labelScale);
        int textX = x0 + (w - textW) / 2;
        int textY = y0 + (h - textH) / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(textX, textY, 0);
        ctx.getMatrices().scale(labelScale, labelScale, 1f);
        ctx.drawText(tr, getMessage(), 0, 0, textColor, false);
        ctx.getMatrices().pop();
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
             | (Math.round(ar + (br - ar) * t) << 16)
             | (Math.round(ag + (bg - ag) * t) <<  8)
             |  Math.round(ab + (bb - ab) * t);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            getMessage().getString());
    }
}
