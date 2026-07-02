package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.RebornBranding.ServerTarget;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.ServerInfoState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Sélecteur de serveur sous JOUER — <b>staff-only</b> (le launcher pousse
 * {@code -Dreborn.staff=true} + le serveur dev). Affiche les <b>deux</b>
 * serveurs (BUILD / DEV) en deux lignes compactes, chacune avec son nombre de
 * joueurs en ligne (ping SLP live). La ligne sélectionnée est surlignée ;
 * cliquer une ligne choisit la cible et JOUER connecte à ce serveur. Le
 * compteur du serveur sélectionné s'affiche aussi au survol de JOUER.
 *
 * <p>Deux lignes toujours visibles (pas d'overlay) : évite tout recouvrement /
 * conflit d'ordre de clic avec les entrées du menu en dessous.
 */
public class ServerPickerWidget extends ClickableWidget {

    private static final int ROW_H = 15;
    /** Hauteur totale (2 lignes) — pour la réservation d'espace du menu. */
    public static final int HEIGHT = 2 * ROW_H;

    public ServerPickerWidget(int x, int y, int width) {
        super(x, y, width, HEIGHT, Text.literal("Serveur"));
    }

    /** Rafraîchit les deux pings actifs (throttlés en interne à 30s). */
    private void refreshPings() {
        ServerInfoState.INSTANCE.maybeRefresh();
        if (ServerInfoState.dev() != null) ServerInfoState.dev().maybeRefresh();
    }

    private static String countLabel(ServerInfoState s) {
        if (s == null) return "INDISPONIBLE";
        return s.isOnline() ? (s.getPlayers() + " EN LIGNE") : "HORS LIGNE";
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;
        refreshPings();

        int x = getX(), y = getY(), w = getWidth();
        ServerTarget target = RebornBranding.target();

        drawRow(ctx, tr, "BUILD", ServerInfoState.INSTANCE, x, y, w,
            target == ServerTarget.BUILD, mouseX, mouseY);
        drawRow(ctx, tr, "DEV", ServerInfoState.dev(), x, y + ROW_H, w,
            target == ServerTarget.DEV, mouseX, mouseY);
    }

    private void drawRow(DrawContext ctx, TextRenderer tr, String name, ServerInfoState s,
                         int x, int y, int w, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        int bg = active ? Colors.ACCENT_SOFT : (hover ? Colors.SURFACE_ELEVATED : Colors.SURFACE);
        ctx.fill(x, y, x + w, y + ROW_H, bg);
        if (active) ctx.fill(x, y, x + 2, y + ROW_H, Colors.ACCENT); // liseré gauche

        drawDot(ctx, x + 8, y + ROW_H / 2 - 1, s != null && s.isOnline());
        int nameColor = active ? Colors.WHITE_PURE
            : (hover ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED);
        ctx.drawText(tr, RebornFont.arcade(name), x + 16, y + (ROW_H - 8) / 2, nameColor, false);

        Text count = RebornFont.arcade(countLabel(s));
        int cw = tr.getWidth(count);
        ctx.drawText(tr, count, x + w - 8 - cw, y + (ROW_H - 8) / 2,
            (s != null && s.isOnline()) ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED, false);
    }

    /** Petit point d'état (vert en ligne / rouge hors ligne). */
    private static void drawDot(DrawContext ctx, int x, int y, boolean online) {
        ctx.fill(x, y, x + 3, y + 3, online ? Colors.SUCCESS : Colors.DANGER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.active || !this.visible) return false;
        int x = getX(), w = getWidth(), y = getY();
        if (mouseX < x || mouseX >= x + w) return false;
        if (mouseY >= y && mouseY < y + ROW_H) {
            select(ServerTarget.BUILD);
            return true;
        }
        if (mouseY >= y + ROW_H && mouseY < y + 2 * ROW_H) {
            select(ServerTarget.DEV);
            return true;
        }
        return false;
    }

    private void select(ServerTarget t) {
        RebornBranding.setTarget(t);
        playDownSound(MinecraftClient.getInstance().getSoundManager());
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, Text.literal("Sélecteur de serveur BUILD / DEV"));
    }
}
