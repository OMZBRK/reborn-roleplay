package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import fr.reborn.integrity.ui.RebornVersion;
import fr.reborn.integrity.ui.ServerInfoState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Card serveur "Reborn · En ligne · 23 / 200 · 28 ms" du footer central
 * du main menu. Affichage statique (pas de hover ni click) — informationnel.
 *
 * <p>Ligne 1 (Inter Bold) : dot vert/rouge + "Reborn · En ligne" +
 *  séparateur · + "X / Y joueurs" + séparateur · + "P ms"
 * <p>Ligne 2 (Inter Medium muté) : "Patch 1.21.1 · Fabric 0.16.5"
 *
 * <p>Le ping refresh est piloté par {@link ServerInfoState#maybeRefresh()}
 * — appelé une fois par frame depuis ici.
 */
public final class ServerInfoMini {

    private static final int DOT_SIZE = 8;
    private static final int LINE_HEIGHT = 14;
    private static final float TEXT_SCALE_LINE1 = 1.0f;
    private static final float TEXT_SCALE_LINE2 = 0.9f;
    private static final int SEPARATOR_X_PADDING = 6;

    private ServerInfoMini() {}

    /**
     * Dessine la card centrée horizontalement sur {@code centerX},
     * top-aligned sur {@code topY}.
     */
    public static void render(DrawContext ctx, int centerX, int topY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        ServerInfoState state = ServerInfoState.INSTANCE;
        state.maybeRefresh();

        boolean online = state.isOnline();

        // ─── Ligne 1 ───
        String line1Status = online ? "Reborn · En ligne" : "Serveur hors ligne";
        Text line1Status_t = RebornFont.bold(line1Status);
        int line1StatusW = tr.getWidth(line1Status_t);

        String line1Players = online ? state.getPlayers() + " / " + state.getMaxPlayers() + " joueurs" : "";
        Text line1Players_t = RebornFont.body(line1Players);
        int line1PlayersW = online ? tr.getWidth(line1Players_t) : 0;

        // Largeur totale ligne 1 = dot + gap + statut + (sep + players) si online.
        int line1Width = DOT_SIZE + 6 + line1StatusW;
        if (online) {
            line1Width += SEPARATOR_X_PADDING + 4 + SEPARATOR_X_PADDING + line1PlayersW;
        }

        int line1X = centerX - line1Width / 2;
        int line1Y = topY;

        // Dot status — vivant : double halo pulsant + cœur stable.
        int dotColor = online ? Colors.SUCCESS : Colors.DANGER;
        int dotCx = line1X + DOT_SIZE / 2;
        int dotCy = line1Y + tr.fontHeight / 2 + 1;

        if (online) {
            float t = (System.currentTimeMillis() % 4_000L) / 4_000f;
            // Halo extérieur — grandit + fade (0 → 1 → 0).
            float wave1 = (float) Math.sin(t * Math.PI * 2.0) * 0.5f + 0.5f;
            int wave1Radius = DOT_SIZE / 2 + 2 + Math.round(wave1 * 4);
            int wave1Alpha = Math.round((1f - wave1) * 110);
            int wave1Color = (wave1Alpha << 24) | (dotColor & 0x00FFFFFF);
            DrawHelpers.disc(ctx, dotCx, dotCy, wave1Radius, wave1Color);

            // Halo intermédiaire — décalé de PI pour effet double-pulse.
            float wave2 = (float) Math.sin(t * Math.PI * 2.0 + Math.PI) * 0.5f + 0.5f;
            int wave2Radius = DOT_SIZE / 2 + 1 + Math.round(wave2 * 3);
            int wave2Alpha = Math.round((1f - wave2) * 80);
            int wave2Color = (wave2Alpha << 24) | (dotColor & 0x00FFFFFF);
            DrawHelpers.disc(ctx, dotCx, dotCy, wave2Radius, wave2Color);
        } else {
            // Hors-ligne : pas d'animation, juste un halo fixe sobre.
            DrawHelpers.disc(ctx, dotCx, dotCy, DOT_SIZE / 2 + 2,
                Colors.withAlpha(dotColor, 0.25f));
        }
        // Cœur central plein — toujours visible, indépendant du pulse.
        DrawHelpers.disc(ctx, dotCx, dotCy, DOT_SIZE / 2, dotColor);
        // Highlight blanc en haut-gauche pour effet 3D / vivant.
        if (online) {
            DrawHelpers.disc(ctx, dotCx - 1, dotCy - 1, 1, 0xCCFFFFFF);
        }

        // Texte status.
        int cursor = line1X + DOT_SIZE + 6;
        ctx.drawText(tr, line1Status_t, cursor, line1Y, Colors.WHITE_PURE, false);
        cursor += line1StatusW;

        if (online) {
            // Séparateur ·
            ctx.drawText(tr, RebornFont.body("·"),
                cursor + SEPARATOR_X_PADDING, line1Y, Colors.FOREGROUND_MUTED, false);
            cursor += SEPARATOR_X_PADDING * 2 + 4;
            // Players.
            ctx.drawText(tr, line1Players_t, cursor, line1Y, Colors.FOREGROUND_SUBTLE, false);
        }

        // ─── Ligne 2 — version mod + MC ───
        Text line2 = RebornFont.body(RebornVersion.shortVersion());
        int line2W = Math.round(tr.getWidth(line2) * TEXT_SCALE_LINE2);
        int line2X = centerX - line2W / 2;
        int line2Y = topY + LINE_HEIGHT;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(line2X, line2Y, 0);
        ctx.getMatrices().scale(TEXT_SCALE_LINE2, TEXT_SCALE_LINE2, 1f);
        ctx.drawText(tr, line2, 0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
    }

    /** Hauteur totale en pixels pour le layouting du parent. */
    public static int height() {
        return LINE_HEIGHT * 2;
    }
}
