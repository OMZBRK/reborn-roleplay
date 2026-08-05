package fr.reborn.hud.menu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.ClickableWidget;
import net.minecraft.network.chat.Component;

/**
 * Widget OST player en coin haut droite du title screen — style moderne
 * minimaliste : fond sombre semi-transparent + 3 boutons play/prev/next
 * dessines en formes geometriques (triangles + rectangles via
 * {@code context.fill}) pour eviter de dependre d'une icon font.
 *
 * <p>Layout (largeur 240, hauteur 56) :
 * <pre>
 *   ┌─────────────────────────────────────┐
 *   │  ♪  Track 01                        │  ← titre (ligne 1)
 *   │  ◀  ▶/⏸  ▶│        🔉 ▬▬▬●▬▬▬▬▬│  ← controls + vol (ligne 2)
 *   │  ●─────────────────────             │  ← progress bar (ligne 3)
 *   └─────────────────────────────────────┘
 * </pre>
 *
 * Toutes les icones (play/pause/prev/next/volume) sont dessinees en
 * shapes via {@code GuiGraphicsExtractor.fill}, pas via font caracters.
 */
public class OSTPlayerWidget extends ClickableWidget {

    private static final int FG = 0xFFFFFAF0; // blanc chaud
    private static final int FG_DIM = 0x66FFFAF0; // semi-transparent
    private static final int BG = 0xCC0A0A0A; // noir 80% opaque
    private static final int ACCENT = 0xFFC9A66B; // beige doré

    // Geometrie interne du widget — coords relatives au top-left.
    private static final int PADDING = 8;
    private static final int LINE1_Y = 6;
    private static final int LINE2_Y = 24;
    private static final int LINE3_Y = 44;
    private static final int CONTROL_SIZE = 10;
    private static final int CONTROL_PREV_X = 8;
    private static final int CONTROL_PLAY_X = 28;
    private static final int CONTROL_NEXT_X = 48;
    private static final int VOLUME_ICON_X = 78;
    private static final int VOLUME_SLIDER_X = 100;
    private static final int VOLUME_SLIDER_W = 120;

    private final Font textRenderer;
    /** True si le user est en train de drag le slider de volume. */
    private boolean draggingVolume = false;

    public OSTPlayerWidget(int x, int y, Font textRenderer) {
        super(x, y, 240, 56, Component.literal("OST Player"));
        this.textRenderer = textRenderer;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        OSTPlayer ost = OSTPlayer.INSTANCE;
        ost.tickAutoAdvance();

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;

        // Fond + ligne d'accent en haut.
        context.fill(x0, y0, x1, y1, BG);
        context.fill(x0, y0, x1, y0 + 1, ACCENT);

        // Ligne 1 : pictogramme note + nom de la piste.
        context.text(textRenderer, "♪ " + ost.getCurrentTrackName(),
            x0 + PADDING, y0 + LINE1_Y, FG, false);

        // Ligne 2 : 3 boutons controles (formes geometriques).
        int cy = y0 + LINE2_Y;
        drawPrevIcon(context, x0 + CONTROL_PREV_X, cy);
        if (ost.isPlaying()) {
            drawPauseIcon(context, x0 + CONTROL_PLAY_X, cy);
        } else {
            drawPlayIcon(context, x0 + CONTROL_PLAY_X, cy);
        }
        drawNextIcon(context, x0 + CONTROL_NEXT_X, cy);

        // Icone volume (haut-parleur simplifie).
        drawSpeakerIcon(context, x0 + VOLUME_ICON_X, cy);

        // Slider volume.
        int sliderY = cy + CONTROL_SIZE / 2 - 1;
        context.fill(x0 + VOLUME_SLIDER_X, sliderY, x0 + VOLUME_SLIDER_X + VOLUME_SLIDER_W, sliderY + 2, FG_DIM);
        int volFill = (int) (VOLUME_SLIDER_W * ost.getVolume());
        context.fill(x0 + VOLUME_SLIDER_X, sliderY, x0 + VOLUME_SLIDER_X + volFill, sliderY + 2, FG);
        // Knob du slider.
        int knobX = x0 + VOLUME_SLIDER_X + volFill;
        context.fill(knobX - 2, sliderY - 2, knobX + 2, sliderY + 4, FG);

        // Ligne 3 : barre de progression.
        int progY = y0 + LINE3_Y;
        int progX0 = x0 + PADDING;
        int progX1 = x1 - PADDING;
        int progW = progX1 - progX0;
        context.fill(progX0, progY, progX1, progY + 2, FG_DIM);
        if (ost.isPlaying()) {
            // Estimation : la majorite des pistes font 2-4 min. On utilise
            // 180s comme baseline visuelle ; quand la piste finit reellement
            // (detection via SoundManager) on saute a la suivante et le
            // bar reset.
            float progress = Math.min(1.0f, (ost.getElapsedMs() / 1000f) / 180f);
            int progFill = (int) (progW * progress);
            context.fill(progX0, progY, progX0 + progFill, progY + 2, ACCENT);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Icon drawing — toutes les icones sont dessinees en rectangles via
    // context.fill pour ne pas dependre d'une icon font.
    // ──────────────────────────────────────────────────────────────────

    /** ▶ Triangle pointant a droite. */
    private void drawPlayIcon(GuiGraphicsExtractor context, int x, int y) {
        // Triangle equilateral via 5 lignes horizontales.
        context.fill(x,     y,     x + 2, y + 10, FG);
        context.fill(x + 2, y + 1, x + 4, y + 9, FG);
        context.fill(x + 4, y + 2, x + 6, y + 8, FG);
        context.fill(x + 6, y + 3, x + 8, y + 7, FG);
        context.fill(x + 8, y + 4, x + 10, y + 6, FG);
    }

    /** ⏸ Deux rectangles verticaux. */
    private void drawPauseIcon(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x,     y, x + 3,  y + 10, FG);
        context.fill(x + 6, y, x + 9, y + 10, FG);
    }

    /** ⏮ Triangle pointant a gauche + barre verticale. */
    private void drawPrevIcon(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + 2, y + 10, FG);
        // Triangle inverse
        context.fill(x + 2, y + 4, x + 4,  y + 6, FG);
        context.fill(x + 4, y + 3, x + 6,  y + 7, FG);
        context.fill(x + 6, y + 2, x + 8,  y + 8, FG);
        context.fill(x + 8, y + 1, x + 10, y + 9, FG);
    }

    /** ⏭ Triangle a droite + barre verticale. */
    private void drawNextIcon(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x,     y + 1, x + 2, y + 9, FG);
        context.fill(x + 2, y + 2, x + 4, y + 8, FG);
        context.fill(x + 4, y + 3, x + 6, y + 7, FG);
        context.fill(x + 6, y + 4, x + 8, y + 6, FG);
        context.fill(x + 8, y, x + 10, y + 10, FG);
    }

    /** 🔉 Haut-parleur simplifie (carre + triangle). */
    private void drawSpeakerIcon(GuiGraphicsExtractor context, int x, int y) {
        // Corps carre.
        context.fill(x, y + 3, x + 3, y + 7, FG);
        // Cone.
        context.fill(x + 3, y + 2, x + 5, y + 8, FG);
        context.fill(x + 5, y + 1, x + 7, y + 9, FG);
        context.fill(x + 7, y,     x + 9, y + 10, FG);
    }

    // ──────────────────────────────────────────────────────────────────
    // Click + drag handling
    // ──────────────────────────────────────────────────────────────────

    @Override
    public void onClick(double mouseX, double mouseY) {
        OSTPlayer ost = OSTPlayer.INSTANCE;
        int relX = (int) (mouseX - getX());
        int relY = (int) (mouseY - getY());

        // Zone des controls : autour de LINE2_Y +- 5px.
        if (relY >= LINE2_Y - 2 && relY <= LINE2_Y + CONTROL_SIZE + 2) {
            // Prev
            if (relX >= CONTROL_PREV_X - 2 && relX <= CONTROL_PREV_X + CONTROL_SIZE + 2) {
                ost.prev();
                return;
            }
            // Play / Pause
            if (relX >= CONTROL_PLAY_X - 2 && relX <= CONTROL_PLAY_X + CONTROL_SIZE + 2) {
                ost.togglePlayPause();
                return;
            }
            // Next
            if (relX >= CONTROL_NEXT_X - 2 && relX <= CONTROL_NEXT_X + CONTROL_SIZE + 2) {
                ost.next();
                return;
            }
            // Volume slider — calcule la position relative et set.
            if (relX >= VOLUME_SLIDER_X && relX <= VOLUME_SLIDER_X + VOLUME_SLIDER_W) {
                float v = (relX - VOLUME_SLIDER_X) / (float) VOLUME_SLIDER_W;
                ost.setVolume(v);
                draggingVolume = true;
                return;
            }
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (draggingVolume) {
            int relX = (int) (mouseX - getX());
            float v = Math.max(0, Math.min(1, (relX - VOLUME_SLIDER_X) / (float) VOLUME_SLIDER_W));
            OSTPlayer.INSTANCE.setVolume(v);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        draggingVolume = false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(net.minecraft.client.gui.narration.NarrationPart.TITLE,
            "Lecteur OST : " + OSTPlayer.INSTANCE.getCurrentTrackName());
    }
}
