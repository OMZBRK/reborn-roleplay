package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Ligne « liste » du gabarit o2 (« 60 i/s ⌄ », « Détaillés ⌄ ») : bouton-plaque laqué de 136 GUI, valeur Inter 9 à
 * gauche, chevron d'or à droite. Un clic passe à la valeur suivante, un clic droit à la précédente (pas de menu
 * déroulant : la liste est courte, et rien ne s'ouvre par-dessus les autres lignes).
 */
public final class ChoixCyclique extends AbstractWidget {

    public static final int LARGEUR = 136, HAUTEUR = 20;

    private final String[] libelles;
    private final IntConsumer onChange;
    private int index;

    public ChoixCyclique(int x, int y, int w, int h, String[] libelles, int index, IntConsumer onChange) {
        super(x, y, w, h, Component.empty());
        this.libelles = libelles;
        this.index = Math.max(0, Math.min(libelles.length - 1, index));
        this.onChange = onChange;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (libelles.length == 0) return;
        boolean hot = isHovered();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ReglagesArt.bouton(ctx, x, y, w, hot ? 1 : 0);
        var f = Minecraft.getInstance().font;
        String v = DaKit.ellipse(f, libelles[index], w - 26);
        DaKit.inter9(ctx, f, DaKit.inter(v), x + 8, y + h / 2f, hot ? DaKit.CREME : DaKit.CREME_VAL, 0);
        ReglagesArt.chevron(ctx, x + w - 12, y + h / 2 - 1, DaKit.OR);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0 || info.button() == 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (libelles.length < 2) return;
        int n = libelles.length;
        index = event.button() == 1 ? (index + n - 1) % n : (index + 1) % n;
        if (onChange != null) onChange.accept(index);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
