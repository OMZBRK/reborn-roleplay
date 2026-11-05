package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Ligne « action » du gabarit o2 (« Déplacer › », « Ouvrir › ») : bouton-plaque laqué calé à droite, libellé Inter 9
 * à gauche, flèche d'or au bout. Largeur : celle du libellé + 30 GUI, 84 au moins ({@link #largeur}).
 */
public final class BoutonAction extends AbstractWidget {

    public static final int HAUTEUR = 20;

    private final String libelle;
    private final Runnable action;

    public BoutonAction(int x, int y, int w, String libelle, Runnable action) {
        super(x, y, w, HAUTEUR, Component.literal(libelle));
        this.libelle = libelle;
        this.action = action;
    }

    /** Largeur du bouton pour ce libellé. */
    public static int largeur(String libelle) {
        return Math.max(84, Minecraft.getInstance().font.width(DaKit.inter(libelle)) + 30);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean hot = isHovered() && active;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ReglagesArt.bouton(ctx, x, y, w, !active ? 2 : hot ? 1 : 0);
        DaKit.inter9(ctx, Minecraft.getInstance().font, DaKit.inter(libelle), x + 10, y + h / 2f,
            hot ? DaKit.CREME : DaKit.CREME_VAL, 0);
        ReglagesArt.fleche(ctx, x + w - 13, y + h / 2 - 4, hot ? 1f : 0.8f);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (action != null) action.run();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
