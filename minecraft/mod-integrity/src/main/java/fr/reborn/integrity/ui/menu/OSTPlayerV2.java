package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.IconPack;
import fr.reborn.integrity.ui.OSTPlayer;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * OST Player V2 — version compacte référence {@code renduostplayer.png} :
 * cover note grise + titre + sous-titre + 3 controls (play/volume/menu).
 *
 * <p>Layout (280×64) :
 * <pre>
 *   ┌─cover─┐ ┌─meta────────────────┐ ┌─ctrl───────┐
 *   │  ♫    │ │ Track 01            │ │ ▶  🔊  ☰  │
 *   │  44px │ │ Reborn OST · Score  │ └────────────┘
 *   └───────┘ └─────────────────────┘
 * </pre>
 *
 * <p>Pas de progress bar ni de Prev/Next — minimaliste façon Spotify
 * compact widget. Le user clique Menu pour ouvrir la playlist (PR #2.1).
 */
public final class OSTPlayerV2 {

    public static final int CARD_W = 280;
    public static final int CARD_H = 64;
    private static final int PADDING = 10;
    private static final int COVER_SIZE = 44;
    private static final int CONTROL_SIZE = 18;
    private static final int CONTROL_SPACING = 4;

    private OSTPlayerV2() {}

    public static void renderBackground(DrawContext ctx, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;
        OSTPlayer ost = OSTPlayer.INSTANCE;

        // ─── Card BG rounded ───
        DrawHelpers.roundedOutlinedRect(ctx, x, y, CARD_W, CARD_H, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // ─── Cover : disque gris très foncé avec note musique ───
        int coverCx = x + PADDING + COVER_SIZE / 2;
        int coverCy = y + PADDING + COVER_SIZE / 2;
        DrawHelpers.disc(ctx, coverCx, coverCy, COVER_SIZE / 2, Colors.SURFACE_ELEVATED);
        DrawHelpers.ring(ctx, coverCx, coverCy, COVER_SIZE / 2, 1, Colors.BORDER_STRONG);

        // Note musique stylisée (♫) — petit disque + queue verticale.
        int noteHeadX = coverCx - 3;
        int noteHeadY = coverCy + 5;
        DrawHelpers.disc(ctx, noteHeadX, noteHeadY, 3, Colors.FOREGROUND_SUBTLE);
        ctx.fill(noteHeadX + 2, noteHeadY - 9, noteHeadX + 3, noteHeadY + 1, Colors.FOREGROUND_SUBTLE);
        ctx.fill(noteHeadX + 3, noteHeadY - 11, noteHeadX + 7, noteHeadY - 9, Colors.FOREGROUND_SUBTLE);

        // ─── Meta — titre + sous-titre ───
        int metaX = x + PADDING + COVER_SIZE + 10;
        int metaY = y + PADDING + 6;

        String trackName = ost.getCurrentTrackName();
        ctx.drawText(tr, RebornFont.bold(trackName), metaX, metaY, Colors.WHITE_PURE, false);

        String artist = "Reborn OST · Original Score";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX, metaY + 12, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body(artist), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
    }

    /**
     * Crée les 3 IconButton de contrôle (play/pause, volume, menu).
     * Le mixin les addDrawableChild + les re-render après le BG card.
     */
    public static IconButton[] buildControls(int cardX, int cardY) {
        int ctrlBaseX = cardX + CARD_W - PADDING - (CONTROL_SIZE * 3 + CONTROL_SPACING * 2);
        int ctrlY = cardY + (CARD_H - CONTROL_SIZE) / 2;

        OSTPlayer ost = OSTPlayer.INSTANCE;

        IconButton playPause = new IconButton(
            ctrlBaseX, ctrlY, CONTROL_SIZE,
            (ctx, x, y, size, color) -> {
                if (OSTPlayer.INSTANCE.isPlaying()) {
                    IconPack.pause(ctx, x, y, size, color);
                } else {
                    IconPack.play(ctx, x, y, size, color);
                }
            },
            "Lecture / Pause", false,
            b -> ost.togglePlayPause()
        ).ghost();

        IconButton volume = new IconButton(
            ctrlBaseX + (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            IconPack::volume, "Volume", false,
            b -> { /* TODO PR #2.1 : volume popup au hover */ }
        ).ghost();

        IconButton menu = new IconButton(
            ctrlBaseX + 2 * (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            IconPack::menu, "Playlist", false,
            b -> ost.next() // TODO PR #2.1 : ouvrir OSTPlaylistOverlay — fallback next pour l'instant
        ).ghost();

        return new IconButton[]{playPause, volume, menu};
    }
}
