package fr.reborn.hud.menu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Widget mini-card en coin haut-gauche affichant l'etat du serveur
 * Reborn.
 *
 * <p>Layout (200x40) :
 * <pre>
 *   ┌──────────────────────────┐
 *   │ ● Reborn Roleplay        │  ← dot + nom serveur
 *   │   12 / 20 joueurs        │  ← compteur
 *   └──────────────────────────┘
 * </pre>
 *
 * Le dot est vert si online, rouge si offline.
 */
public class ServerInfoWidget extends AbstractWidget {

    private static final int FG = 0xFFFFFAF0;
    private static final int FG_DIM = 0xAAFFFAF0;
    private static final int BG = 0xCC0A0A0A;
    private static final int ACCENT = 0xFFC9A66B;
    private static final int DOT_GREEN = 0xFF4ADE80;
    private static final int DOT_RED = 0xFFEF4444;

    private static final int PADDING = 8;
    private static final int DOT_SIZE = 6;

    private final Font font;

    public ServerInfoWidget(int x, int y, Font font) {
        super(x, y, 200, 40, Component.literal("Reborn Server"));
        this.font = font;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ServerInfoState st = ServerInfoState.INSTANCE;
        st.maybeRefresh();

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;

        // Fond + accent.
        context.fill(x0, y0, x1, y1, BG);
        context.fill(x0, y0, x1, y0 + 1, ACCENT);

        // Dot vert/rouge.
        int dotX = x0 + PADDING;
        int dotY = y0 + 10;
        int dotColor = st.isOnline() ? DOT_GREEN : DOT_RED;
        context.fill(dotX, dotY, dotX + DOT_SIZE, dotY + DOT_SIZE, dotColor);

        // Ligne 1 : nom serveur.
        String title = st.isOnline() ? "Reborn Roleplay" : "Serveur hors ligne";
        context.text(font, RebornFont.arcade(title), dotX + DOT_SIZE + 6, y0 + 9, FG, false);

        // Ligne 2 : compteur joueurs ou message.
        String line2;
        if (st.isOnline()) {
            line2 = st.getPlayers() + " / " + st.getMaxPlayers() + " joueurs";
        } else {
            line2 = "Reconnexion...";
        }
        context.text(font, RebornFont.arcade(line2), x0 + PADDING, y0 + 23, FG_DIM, false);
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        // No-op pour l'instant. PR ulterieure : ouvrir un mini-dashboard
        // serveur (latence, MOTD, version) au click.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) { /* narration phase2 */ }
}
