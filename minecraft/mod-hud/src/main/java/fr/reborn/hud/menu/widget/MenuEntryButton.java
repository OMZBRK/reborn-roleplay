package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationPart;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Entrée du menu vertical gauche — style Paladium Reforged.
 *
 * <p>Label en police Minecraft vanilla GRAS (look pixel, proche Paladium),
 * aligné à gauche. Au survol (ou
 * focus clavier) : le label passe en accent crimson et une flèche
 * curseur {@code ►} apparaît à sa gauche (glisse légèrement vers le
 * label). En idle : ivoire atténué.
 *
 * <p>Deux extras optionnels :
 * <ul>
 *   <li>{@link #withHoverInfo(Supplier)} — petit texte affiché à droite
 *       du label au survol (ex : « 42 en ligne » sur JOUER).</li>
 *   <li>{@link #placeholder()} — style estompé pour les entrées pas
 *       encore fonctionnelles (NEWS / BOUTIQUE).</li>
 * </ul>
 */
public class MenuEntryButton extends Button {

    /** Espace réservé à gauche pour la flèche curseur. */
    private static final int ARROW_SLOT = 16;
    /** Taille de la flèche (largeur = hauteur du chevron). */
    private static final int ARROW_SIZE = 8;

    private final String rawLabel;
    /** Police Minecraft vanilla en gras (pixel) — proche du look Paladium. */
    private final Component label;
    private final float scale;

    private boolean placeholder = false;
    private Supplier<String> hoverInfo = null;

    /** Progression d'animation du hover (0..1) pour le slide de flèche. */
    private float hoverAnim = 0f;
    private long lastFrameMs = 0L;

    public MenuEntryButton(int x, int y, int width, int height,
                           String text, float scale, PressAction onPress) {
        super(x, y, width, height, Component.literal(text), onPress,
              Button.DEFAULT_NARRATION_SUPPLIER);
        this.rawLabel = text;
        this.label = RebornFont.arcade(text);
        this.scale = scale;
    }

    /** Style estompé pour une entrée non fonctionnelle (placeholder). */
    public MenuEntryButton placeholder() {
        this.placeholder = true;
        return this;
    }

    /** Texte affiché à droite du label au survol (ex : joueurs en ligne). */
    public MenuEntryButton withHoverInfo(Supplier<String> info) {
        this.hoverInfo = info;
        return this;
    }

    /** Largeur du label rendu (scaled) — utile au parent pour le layout. */
    public static int labelWidth(Font tr, String text, float scale) {
        return ARROW_SLOT + Math.round(tr.width(RebornFont.arcade(text)) * scale);
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.textRenderer;

        boolean active = isHovered() || isFocused();

        // Anim hover (slide flèche). dt-based pour être fps-indépendant.
        long now = System.currentTimeMillis();
        float dt = lastFrameMs == 0L ? 0f : (now - lastFrameMs) / 1000f;
        lastFrameMs = now;
        float target = active ? 1f : 0f;
        float step = Math.min(1f, dt * 12f);
        hoverAnim += (target - hoverAnim) * step;

        int x0 = getX();
        int y0 = getY();
        int h = getHeight();

        int idle = placeholder ? Colors.FOREGROUND_MUTED : Colors.FOREGROUND_SUBTLE;
        int hot = placeholder ? Colors.FOREGROUND_SUBTLE : Colors.ACCENT_HOVER;
        int color = Colors.lerp(idle, hot, hoverAnim);

        // ── Flèche curseur ► (fade + slide) ────────────────────
        if (hoverAnim > 0.02f) {
            int arrowAlpha = Math.round(hoverAnim * 255);
            int arrowColor = (arrowAlpha << 24) | (hot & 0x00FFFFFF);
            int slide = Math.round((1f - hoverAnim) * -6f); // vient de la gauche
            int ax = x0 + 2 + slide;
            int ay = y0 + (h - ARROW_SIZE) / 2;
            drawRightArrow(ctx, ax, ay, ARROW_SIZE, arrowColor);
        }

        // ── Label display, aligné à gauche, centré verticalement ─
        int labelX = x0 + ARROW_SLOT;
        int scaledH = Math.round(tr.lineHeight * scale);
        int labelY = y0 + (h - scaledH) / 2;

        ctx.pose().pushMatrix();
        ctx.pose().translate(labelX, labelY);
        ctx.pose().scale(scale, scale);
        ctx.text(tr, label, 1, 1, 0x66000000, false);
        ctx.text(tr, label, 0, 0, color, false);
        ctx.pose().popMatrix();

        // ── Info survol (ex : joueurs en ligne) à droite du label ─
        if (active && hoverInfo != null) {
            String info = hoverInfo.get();
            if (info != null && !info.isEmpty()) {
                int scaledLabelW = Math.round(tr.width(label) * scale);
                int infoX = labelX + scaledLabelW + 10;
                int infoY = y0 + (h - tr.lineHeight) / 2;
                ctx.text(tr, RebornFont.arcade(info), infoX, infoY,
                    Colors.FOREGROUND_SUBTLE, true);
            }
        }
    }

    /** Petit chevron plein pointant à droite (►), dessiné en lignes. */
    private static void drawRightArrow(GuiGraphicsExtractor ctx, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            // Largeur du triangle décroît du centre vers les pointes.
            int half = Math.min(i, size - 1 - i);
            int rowW = half + 1;
            ctx.fill(x, y + i, x + rowW, y + i + 1, color);
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(NarrationPart.TITLE, rawLabel);
    }
}
