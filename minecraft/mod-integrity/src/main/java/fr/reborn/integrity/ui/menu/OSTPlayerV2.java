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
 * OST Player V2 — card horizontale du footer-gauche du main menu.
 *
 * <p>Layout (largeur ~360, hauteur ~80) :
 * <pre>
 *   ┌─cover─┐ ┌─meta────────────────┐ ┌─controls─────┐
 *   │  ▣   │ │ Hollow Moonlight    │ │ ⏮  ▶  ⏭  ♪ │
 *   │ 64×64 │ │ Reborn OST · K.A    │ └──────────────┘
 *   └───────┘ └─────────────────────┘
 *   ──────── progress bar ───────────
 * </pre>
 *
 * <p>Cover : disque avec halo, tourne quand isPlaying. Cible PR future
 * pour mettre une vraie cover art par piste — pour l'instant disque
 * uni accent.
 *
 * <p>Les 4 boutons (prev / play|pause / next / playlist) sont des
 * {@link IconButton} créés par {@link #buildControls(int, int)} pour
 * être ajoutés en addDrawableChild du Screen. Le mixin les positionne
 * relatif au coin de la card.
 */
public final class OSTPlayerV2 {

    public static final int CARD_W = 280;
    public static final int CARD_H = 72;
    private static final int PADDING = 10;
    private static final int COVER_SIZE = 48;
    private static final int CONTROL_SIZE = 22;
    private static final int CONTROL_SPACING = 2;

    private OSTPlayerV2() {}

    /**
     * Rend le fond + cover + meta + progress. À appeler AVANT
     * super.render() pour que les IconButton viennent au-dessus.
     */
    public static void renderBackground(DrawContext ctx, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;
        OSTPlayer ost = OSTPlayer.INSTANCE;

        // ─── Card background ───
        DrawHelpers.roundedOutlinedRect(ctx, x, y, CARD_W, CARD_H, 10,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);

        // ─── Cover disc ───
        int coverCx = x + PADDING + COVER_SIZE / 2;
        int coverCy = y + PADDING + COVER_SIZE / 2;
        // Halo bleu subtle.
        DrawHelpers.disc(ctx, coverCx, coverCy, COVER_SIZE / 2 + 4,
            Colors.withAlpha(Colors.ACCENT, 0.25f));
        // Disc principal.
        DrawHelpers.disc(ctx, coverCx, coverCy, COVER_SIZE / 2, Colors.ACCENT_SOFT);
        DrawHelpers.ring(ctx, coverCx, coverCy, COVER_SIZE / 2, 2, Colors.ACCENT);

        // Petit symbole "♪" au centre — note de musique stylisée.
        // En primitives : un cercle + une queue verticale.
        int noteX = coverCx - 4;
        int noteY = coverCy - 8;
        DrawHelpers.disc(ctx, noteX, noteY + 8, 3, Colors.WHITE_PURE);
        ctx.fill(noteX + 2, noteY - 2, noteX + 3, noteY + 8, Colors.WHITE_PURE);
        ctx.fill(noteX + 3, noteY - 4, noteX + 8, noteY - 2, Colors.WHITE_PURE);

        // ─── Meta — titre + sous-titre seulement, layout vertical compact ───
        int metaX = x + PADDING + COVER_SIZE + 12;
        int metaY = y + PADDING + 8;

        String trackName = ost.getCurrentTrackName();
        ctx.drawText(tr, RebornFont.bold(trackName), metaX, metaY, Colors.WHITE_PURE, false);

        String artist = "Reborn OST · Original Score";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX, metaY + 12, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body(artist), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        // ─── Progress bar en bas ───
        int progX = x + PADDING;
        int progY = y + CARD_H - PADDING + 2;
        int progW = CARD_W - 2 * PADDING;
        // Track.
        ctx.fill(progX, progY, progX + progW, progY + 2, Colors.BORDER_STRONG);
        // Fill basé sur elapsed time (approximation 180s par piste).
        if (ost.isPlaying()) {
            float progress = Math.min(1f, (ost.getElapsedMs() / 1000f) / 180f);
            int fill = Math.round(progW * progress);
            ctx.fill(progX, progY, progX + fill, progY + 2, Colors.ACCENT);
        }
    }

    /**
     * Crée les 4 IconButton de contrôle. À appeler depuis le mixin
     * dans {@code init()}, après quoi on les addDrawableChild.
     * Les positions sont absolues (relatives à l'écran), calculées depuis
     * le coin top-left de la card (x, y).
     */
    public static IconButton[] buildControls(int cardX, int cardY) {
        int ctrlBaseX = cardX + CARD_W - PADDING - (CONTROL_SIZE * 4 + CONTROL_SPACING * 3);
        int ctrlY = cardY + PADDING + (COVER_SIZE - CONTROL_SIZE) / 2;

        OSTPlayer ost = OSTPlayer.INSTANCE;

        IconButton prev = new IconButton(
            ctrlBaseX, ctrlY, CONTROL_SIZE,
            IconPack::skipPrev, "Précédent", false,
            b -> ost.prev()
        );

        IconButton playPause = new IconButton(
            ctrlBaseX + (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            (ctx, x, y, size, color) -> {
                if (OSTPlayer.INSTANCE.isPlaying()) {
                    IconPack.pause(ctx, x, y, size, color);
                } else {
                    IconPack.play(ctx, x, y, size, color);
                }
            },
            "Lecture / Pause", false,
            b -> ost.togglePlayPause()
        );

        IconButton next = new IconButton(
            ctrlBaseX + 2 * (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            IconPack::skipNext, "Suivant", false,
            b -> ost.next()
        );

        IconButton menu = new IconButton(
            ctrlBaseX + 3 * (CONTROL_SIZE + CONTROL_SPACING), ctrlY, CONTROL_SIZE,
            IconPack::menu, "Playlist", false,
            b -> { /* TODO PR #2.1 : ouvrir OSTPlaylistOverlay */ }
        );

        return new IconButton[]{prev, playPause, next, menu};
    }
}
