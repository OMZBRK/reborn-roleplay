package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.RebornBranding.ServerTarget;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.ServerInfoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Sélecteur de serveur en <b>popup au survol de JOUER</b> — staff-only. Le menu
 * reste propre (aucune ligne fixe) ; quand la souris passe sur l'entrée JOUER,
 * un petit panneau apparaît <b>à droite</b> (dans le vide, sans recouvrir les
 * autres entrées) avec les deux serveurs BUILD / DEV et leur nombre de joueurs
 * en ligne. Cliquer une ligne choisit la cible ; JOUER connecte au serveur
 * sélectionné.
 *
 * <p>Ancré sur l'entrée JOUER ({@link MenuEntryButton}) : le popup se
 * positionne à sa droite et la zone de survol couvre JOUER + le popup (gap
 * inclus) pour qu'on puisse glisser de l'un à l'autre sans qu'il disparaisse.
 */
public class ServerPickerWidget extends AbstractWidget {

    private static final int ROW_H = 16;
    private static final int POPUP_W = 150;
    private static final int GAP = 14;

    private final MenuEntryButton anchor;

    public ServerPickerWidget(MenuEntryButton anchor) {
        super(anchor.getX() + anchor.width() + GAP,
              anchor.getY() + (anchor.getHeight() - 2 * ROW_H) / 2,
              POPUP_W, 2 * ROW_H, Component.literal("Serveur"));
        this.anchor = anchor;
    }

    /** Zone de survol = boîte englobante JOUER + popup (gap compris). */
    private boolean revealed(double mx, double my) {
        int left = anchor.getX();
        int right = getX() + getWidth();
        int top = Math.min(anchor.getY(), getY());
        int bottom = Math.max(anchor.getY() + anchor.getHeight(), getY() + getHeight());
        return mx >= left && mx < right && my >= top && my < bottom;
    }

    private void refreshPings() {
        ServerInfoState.INSTANCE.maybeRefresh();
        if (ServerInfoState.dev() != null) ServerInfoState.dev().maybeRefresh();
    }

    private static String countLabel(ServerInfoState s) {
        if (s == null) return "INDISPONIBLE";
        return s.isOnline() ? (s.getPlayers() + " EN LIGNE") : "HORS LIGNE";
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        refreshPings();
        if (!revealed(mouseX, mouseY)) return; // rien hors survol de JOUER

        Font tr = mc.font;
        int x = getX(), y = getY(), w = getWidth();
        ServerTarget target = RebornBranding.target();

        ctx.pose().pushMatrix();
        ctx.pose().translate(0, 0, 30); // au-dessus du décor/menu
        // Panneau.
        ctx.fill(x, y, x + w, y + 2 * ROW_H, Colors.BACKDROP_85);
        drawRow(ctx, tr, "BUILD", ServerInfoState.INSTANCE, x, y, w,
            target == ServerTarget.BUILD, mouseX, mouseY);
        drawRow(ctx, tr, "DEV", ServerInfoState.dev(), x, y + ROW_H, w,
            target == ServerTarget.DEV, mouseX, mouseY);
        outline(ctx, x, y, w, 2 * ROW_H, Colors.BORDER_STRONG);
        ctx.pose().popMatrix();
    }

    private void drawRow(GuiGraphicsExtractor ctx, Font tr, String name, ServerInfoState s,
                         int x, int y, int w, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
        if (active) ctx.fill(x, y, x + w, y + ROW_H, Colors.ACCENT_SOFT);
        else if (hover) ctx.fill(x, y, x + w, y + ROW_H, Colors.SURFACE_ELEVATED);
        if (active) ctx.fill(x, y, x + 2, y + ROW_H, Colors.ACCENT);

        drawDot(ctx, x + 8, y + ROW_H / 2 - 1, s != null && s.isOnline());
        int nameColor = active ? Colors.WHITE_PURE
            : (hover ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED);
        ctx.text(tr, RebornFont.arcade(name), x + 16, y + (ROW_H - 8) / 2, nameColor, false);

        Component count = RebornFont.arcade(countLabel(s));
        int cw = tr.width(count);
        ctx.text(tr, count, x + w - 8 - cw, y + (ROW_H - 8) / 2,
            (s != null && s.isOnline()) ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED, false);
    }

    private static void drawDot(GuiGraphicsExtractor ctx, int x, int y, boolean online) {
        ctx.fill(x, y, x + 3, y + 3, online ? Colors.SUCCESS : Colors.DANGER);
    }

    private static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c);
        ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c);
        ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.active || !this.visible) return false;
        // On ne réagit qu'aux clics DANS le popup (le reste de la zone de survol
        // — dont JOUER — reste géré par l'entrée JOUER elle-même).
        int x = getX(), w = getWidth(), y = getY();
        if (!revealed(mouseX, mouseY)) return false;
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
        playDownSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        ;
    }
}
