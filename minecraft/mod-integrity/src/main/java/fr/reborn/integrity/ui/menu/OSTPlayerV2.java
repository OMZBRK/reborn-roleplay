package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.IconPack;
import fr.reborn.integrity.ui.IconTextures;
import fr.reborn.integrity.ui.OSTPlayer;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * OST Player V2 compact — coin bas-gauche du main menu, version finale.
 *
 * <p>Layout (220×52) :
 * <pre>
 *   ┌─────────────────────────────────────────────┐
 *   │ ⊙  Track 01           ▶  🔊  ☰              │
 *   │    Reborn OST...                            │
 *   │ ────── progress ───                         │
 *   └─────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Pas de Prev/Next — juste Play/Volume/Menu. Progress bar discrète
 * en bas de la card (1px).
 */
public final class OSTPlayerV2 {

    public static final int CARD_W = 220;
    public static final int CARD_H = 52;
    private static final int PADDING = 8;
    private static final int COVER_SIZE = 32;
    private static final int CONTROL_SIZE = 14;
    private static final int CONTROL_SPACING = 2;

    /** Position absolue (centre X, top Y) du bouton volume — utilisée pour
     *  positionner l'OSTVolumePopup au-dessus. */
    public static int[] volumeButtonAnchor(int cardX, int cardY) {
        int ctrlBaseX = cardX + CARD_W - PADDING - (CONTROL_SIZE * 3 + CONTROL_SPACING * 2);
        int btnVolX = ctrlBaseX + (CONTROL_SIZE + CONTROL_SPACING);
        int btnY = cardY + PADDING + 2;
        return new int[] {btnVolX + CONTROL_SIZE / 2, btnY};
    }

    private OSTPlayerV2() {}

    public static void renderBackground(DrawContext ctx, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;
        OSTPlayer ost = OSTPlayer.INSTANCE;

        // Auto-skip à la fin d'une piste — appelé à chaque frame depuis
        // ici, le main menu render se fait quasiment à 60 fps donc la
        // détection de fin (via SoundManager.isPlaying false + grace 2s)
        // se déclenche dans la frame suivante.
        ost.tickAutoAdvance();

        // ─── Card BG ───
        DrawHelpers.roundedOutlinedRect(ctx, x, y, CARD_W, CARD_H, 8,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // ─── Cover : mini disque gris foncé + note ───
        int coverCx = x + PADDING + COVER_SIZE / 2;
        int coverCy = y + PADDING + COVER_SIZE / 2;
        DrawHelpers.disc(ctx, coverCx, coverCy, COVER_SIZE / 2, Colors.SURFACE_ELEVATED);
        DrawHelpers.ring(ctx, coverCx, coverCy, COVER_SIZE / 2, 1, Colors.BORDER_STRONG);

        // Note musique miniature.
        int noteHeadX = coverCx - 2;
        int noteHeadY = coverCy + 3;
        DrawHelpers.disc(ctx, noteHeadX, noteHeadY, 2, Colors.FOREGROUND_SUBTLE);
        ctx.fill(noteHeadX + 1, noteHeadY - 6, noteHeadX + 2, noteHeadY, Colors.FOREGROUND_SUBTLE);
        ctx.fill(noteHeadX + 2, noteHeadY - 7, noteHeadX + 5, noteHeadY - 6, Colors.FOREGROUND_SUBTLE);

        // ─── Meta — titre + sous-titre ───
        int metaX = x + PADDING + COVER_SIZE + 8;
        int metaY = y + PADDING + 2;

        String trackName = ost.getCurrentTrackName();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX, metaY, 0);
        ctx.getMatrices().scale(0.9f, 0.9f, 1f);
        ctx.drawText(tr, RebornFont.bold(trackName), 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        String artist = "Reborn OST · Original Score";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX, metaY + 11, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawText(tr, RebornFont.body(artist), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        // ─── Progress bar (1px) en bas de la card ───
        int progY = y + CARD_H - 4;
        int progX0 = x + PADDING;
        int progX1 = x + CARD_W - PADDING;
        ctx.fill(progX0, progY, progX1, progY + 1, Colors.BORDER_STRONG);
        if (ost.isPlaying()) {
            // Progress basé sur la durée apprise (exacte à la 2e écoute,
            // estimation à 180s par défaut pour la 1ère).
            int fill = Math.round((progX1 - progX0) * ost.getProgress());
            ctx.fill(progX0, progY, progX0 + fill, progY + 1, Colors.ACCENT);
        }
    }

    /**
     * Crée les 3 IconButton de contrôle (play/pause, volume, menu).
     */
    public static IconButton[] buildControls(int cardX, int cardY) {
        int ctrlBaseX = cardX + CARD_W - PADDING - (CONTROL_SIZE * 3 + CONTROL_SPACING * 2);
        int ctrlY = cardY + PADDING + 2;

        OSTPlayer ost = OSTPlayer.INSTANCE;

        IconButton playPause = new IconButton(
            ctrlBaseX, ctrlY, CONTROL_SIZE,
            (ctx, x, y, size, color) -> {
                if (OSTPlayer.INSTANCE.isPlaying()) {
                    IconTextures.or(IconTextures.PAUSE, IconPack::pause).draw(ctx, x, y, size, color);
                } else {
                    IconTextures.or(IconTextures.PLAY, IconPack::play).draw(ctx, x, y, size, color);
                }
            },
            "Lecture / Pause", false,
            b -> ost.togglePlayPause()
        ).ghost();

        IconButton volume = new IconButton(
            ctrlBaseX + (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            IconTextures.or(IconTextures.VOLUME, IconPack::volume),
            "Volume", false,
            b -> ost.toggleVolumePopup()
        ).ghost();

        IconButton menu = new IconButton(
            ctrlBaseX + 2 * (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            IconTextures.or(IconTextures.MENU, IconPack::menu),
            "Playlist", false,
            b -> ost.togglePlaylist()
        ).ghost();

        return new IconButton[]{playPause, volume, menu};
    }
}
