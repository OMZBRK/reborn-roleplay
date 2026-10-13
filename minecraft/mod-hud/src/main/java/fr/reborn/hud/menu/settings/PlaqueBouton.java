package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Bouton d'action des Paramètres : bouton-plaque laqué ({@link ReglagesArt#bouton}), flèche d'or et libellé Inter 9
 * crème. Le « → » éventuel en tête du libellé est remplacé par la flèche dessinée.
 */
public final class PlaqueBouton extends Button {

    private final String label;
    private final Runnable action;

    public PlaqueBouton(int x, int y, int w, int h, String label, Runnable action) {
        super(x, y, w, h, Component.literal(label), b -> {}, Button.DEFAULT_NARRATION);
        this.label = label.startsWith("→") ? label.substring(1).trim() : label;
        this.action = action;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font f = Minecraft.getInstance().font;
        boolean hov = isHovered() && active;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ReglagesArt.bouton(ctx, x, y, w, !active ? 2 : hov ? 1 : 0);
        Component c = DaKit.inter(label);
        int tw = f.width(c);
        float tx = x + w / 2f - (tw + 9) / 2f;
        ReglagesArt.fleche(ctx, Math.round(tx), y + h / 2 - 3, hov ? 1f : 0.75f);
        DaKit.inter9(ctx, f, c, tx + 9, y + h / 2f, hov ? DaKit.CREME : 0xFFD8CCB0, 0);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
