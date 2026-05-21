package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Bouton icône Reborn — fond rond léger + icône custom centrée +
 * tooltip optionnel au survol.
 *
 * <p>Utilisé pour les boutons compacts du main menu : BottomRightIcons
 * (Settings / Globe / Discord), TopRightQuit (X), contrôles OST
 * (prev / play / next / volume / playlist).
 *
 * <p>Le draw de l'icône est délégué à un {@link IconDrawer} fonctionnel —
 * c'est l'appelant qui passe sa fonction d'icône (typiquement une méthode
 * statique d'{@link fr.reborn.integrity.ui.IconPack}).
 */
public class IconButton extends ButtonWidget {

    /** Fonction qui dessine une icône dans un carré (x, y, size, color). */
    @FunctionalInterface
    public interface IconDrawer {
        void draw(DrawContext ctx, int x, int y, int size, int color);
    }

    private final IconDrawer iconDrawer;
    private final String tooltip;
    /** Position du tooltip : true = en-dessous, false = au-dessus. */
    private final boolean tooltipBelow;
    /** Si true, le bouton n'a pas de fond — juste l'icône. Utilisé pour
     *  les contrôles flottants comme le X de quit en haut-droite. */
    private boolean ghost = false;

    public IconButton(int x, int y, int size, IconDrawer iconDrawer, String tooltip,
                      boolean tooltipBelow, PressAction onPress) {
        super(x, y, size, size, Text.literal(tooltip == null ? "" : tooltip),
              onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        this.iconDrawer = iconDrawer;
        this.tooltip = tooltip;
        this.tooltipBelow = tooltipBelow;
    }

    public IconButton ghost() {
        this.ghost = true;
        return this;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        int x0 = getX();
        int y0 = getY();
        int s = getWidth();
        boolean hovered = isHovered();

        // Fond rond léger sauf en mode ghost.
        if (!ghost) {
            int bg = hovered ? Colors.SURFACE_OVERLAY : Colors.SURFACE_ELEVATED;
            int border = hovered ? Colors.ACCENT : Colors.BORDER_STRONG;
            DrawHelpers.roundedOutlinedRect(context, x0, y0, s, s, 6, bg, border);
        }

        // Icône centrée.
        int iconSize = s - (ghost ? 4 : 10);
        int iconX = x0 + (s - iconSize) / 2;
        int iconY = y0 + (s - iconSize) / 2;
        int iconColor = hovered ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE;
        if (!active) iconColor = Colors.MUTED;
        iconDrawer.draw(context, iconX, iconY, iconSize, iconColor);

        // Tooltip simple — petit cartouche sombre avec texte body.
        if (hovered && tooltip != null && !tooltip.isEmpty()) {
            renderTooltip(context, client, x0 + s / 2, y0);
        }
    }

    private void renderTooltip(DrawContext context, MinecraftClient client, int anchorX, int anchorY) {
        var tr = client.textRenderer;
        Text tipText = RebornFont.body(tooltip);
        int textW = tr.getWidth(tipText);
        int paddingX = 8;
        int paddingY = 4;
        int tipW = textW + 2 * paddingX;
        int tipH = tr.fontHeight + 2 * paddingY;
        int tipX = anchorX - tipW / 2;
        int tipY = tooltipBelow ? (anchorY + getHeight() + 6) : (anchorY - tipH - 6);

        // Tooltip rendu PAR-DESSUS le reste — push Z+200.
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        DrawHelpers.roundedOutlinedRect(
            context, tipX, tipY, tipW, tipH, 4,
            Colors.SURFACE_OVERLAY, Colors.BORDER_STRONG
        );
        context.drawText(tr, tipText,
            tipX + paddingX, tipY + paddingY, Colors.FOREGROUND, false);
        context.getMatrices().pop();
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        if (tooltip != null && !tooltip.isEmpty()) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, tooltip);
        }
    }
}
