package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import fr.reborn.integrity.ui.ServerInfoState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Card serveur "Reborn · En ligne · X / Y joueurs" du footer central du
 * main menu. Affichage statique (pas de hover ni click) — informationnel.
 *
 * <p>Une seule ligne (Inter Bold) : dot vert/rouge pulsant + "Reborn ·
 *  En ligne" + séparateur · + "X / Y joueurs". La version Minecraft +
 *  Fabric vit dans {@link CreditsCorner} (coin bas-droite) pour éviter
 *  la duplication visuelle.
 *
 * <p>Le ping refresh est piloté par {@link ServerInfoState#maybeRefresh()}
 * — appelé une fois par frame depuis ici.
 */
public final class ServerInfoMini {

    private static final int DOT_SIZE = 8;
    private static final int LINE_HEIGHT = 14;
    private static final int SEPARATOR_X_PADDING = 6;

    /** Cache des Text constants — évite 3 allocs Text par frame. */
    private static final Text STATUS_ONLINE_TEXT  = RebornFont.bold("Reborn · En ligne");
    private static final Text STATUS_OFFLINE_TEXT = RebornFont.bold("Serveur hors ligne");
    private static final Text SEPARATOR_TEXT      = RebornFont.body("·");

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
        Text line1Status_t = online ? STATUS_ONLINE_TEXT : STATUS_OFFLINE_TEXT;
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
            // Halo unique pulsant doux — un seul ring qui respire, plus
            // discret que le double-pulse précédent qui faisait trop "alerte".
            float t = (System.currentTimeMillis() % 3_000L) / 3_000f;
            float wave = (float) Math.sin(t * Math.PI * 2.0) * 0.5f + 0.5f;
            int radius = DOT_SIZE / 2 + 2 + Math.round(wave * 2);
            int alpha = Math.round((1f - wave) * 90);
            int color = (alpha << 24) | (dotColor & 0x00FFFFFF);
            DrawHelpers.disc(ctx, dotCx, dotCy, radius, color);
        } else {
            // Hors-ligne : pas d'animation, juste un halo fixe sobre.
            DrawHelpers.disc(ctx, dotCx, dotCy, DOT_SIZE / 2 + 2,
                Colors.withAlpha(dotColor, 0.25f));
        }
        DrawHelpers.disc(ctx, dotCx, dotCy, DOT_SIZE / 2, dotColor);
        if (online) {
            DrawHelpers.disc(ctx, dotCx - 1, dotCy - 1, 1, 0xCCFFFFFF);
        }

        // Texte status — drop shadow=true pour lisibilite garantie sur
        // les fonds clairs (sunset HDRI, ciel jour, etc.) generes par
        // le browser MCEF du DynamicPlayerBackground.
        int cursor = line1X + DOT_SIZE + 6;
        ctx.drawText(tr, line1Status_t, cursor, line1Y, Colors.WHITE_PURE, true);
        cursor += line1StatusW;

        if (online) {
            ctx.drawText(tr, SEPARATOR_TEXT,
                cursor + SEPARATOR_X_PADDING, line1Y, 0xFFD1D5DB, true);
            cursor += SEPARATOR_X_PADDING * 2 + 4;
            ctx.drawText(tr, line1Players_t, cursor, line1Y, 0xFFE5E7EB, true);
        }

        // Plus de ligne 2 ici — la version MC/Fabric est déjà affichée
        // dans CreditsCorner (coin bas-droite). On évite la duplication
        // qui faisait apparaître "Minecraft 1.21.1 · Fabric Loader 0.16.5"
        // deux fois à l'écran.
    }

    /** Hauteur totale en pixels pour le layouting du parent. */
    public static int height() {
        return LINE_HEIGHT;
    }
}
