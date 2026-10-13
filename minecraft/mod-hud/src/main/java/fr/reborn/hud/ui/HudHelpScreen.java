package fr.reborn.hud.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Aide de l'éditeur de HUD : tous les raccourcis groupés par catégorie, sur un feuillet laqué qui porte la signature de
 * l'éditeur (équerre et cordeau du charpentier, règle graduée d'or ; {@code textures/gui/editeur/aide.png}, un blit).
 * Un clic ou une touche referme.
 */
public final class HudHelpScreen extends Screen {

    private static final int W = 376, H = 292;
    private static final String[][] SECTIONS = {
        {"Navigation",
            "H · Ouvrir / fermer l'éditeur",
            "Échap · Fermer (panneau ou éditeur)",
            "Ctrl+M · Réglages du chat"},
        {"Édition",
            "Clic + glisser · Déplacer un élément",
            "Flèches · Décaler d'1 px (Maj = 10 px)",
            "Molette · Changer la taille",
            "Maj + glisser · Déplacer sans aimantation",
            "Ctrl + clic · Sélection multiple"},
        {"Historique",
            "Ctrl+Z / Ctrl+W · Annuler",
            "Ctrl+Y · Rétablir"},
        {"Éléments",
            "Clic sur l'œil · Afficher / masquer",
            "Engrenage · Réglages du chat",
            "Poignée rouge · Redimensionner"},
        {"Presets",
            "Clic · Appliquer",
            "Clic droit · Supprimer"}
    };

    private final Screen parent;

    public HudHelpScreen(Screen parent) {
        super(Component.literal("Éditeur de HUD — aide"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x70060306);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font f = this.font;
        int x = (this.width - W) / 2, y = Math.max(4, (this.height - H) / 2);
        DaKit.blit(ctx, EditeurArt.AIDE, x - 2, y - 2, 0, 0, 384, 300, 384, 300);
        DaKit.arcX1(ctx, f, DaKit.arcade("Raccourcis de l'éditeur"), x + 46, y + 16, DaKit.OR_CLAIR, 0, true);

        int yy = y + 50;
        for (String[] sec : SECTIONS) {
            DaKit.arcFin(ctx, f, DaKit.arcade(sec[0]), x + 16, yy - 2, DaKit.OR_ETIQ, 0, false);
            yy += 9;
            for (int i = 1; i < sec.length; i++) {
                String line = sec[i];
                int k = line.indexOf(" · ");
                String touches = line.substring(0, k), desc = line.substring(k + 3);
                float w = DaKit.inter7(ctx, f, DaKit.inter(touches), x + 24, yy, DaKit.OR_CLAIR, 0);
                DaKit.inter7(ctx, f, DaKit.inter(desc), x + 24 + Math.max(84, w + 8), yy, DaKit.CREME_VAL, 0);
                yy += 10;
            }
            yy += 5;
        }
        DaKit.inter7(ctx, f, DaKit.inter("Clique ou appuie sur une touche pour fermer"), x + W / 2f, y + H - 12,
            DaKit.argb(0xC2B59A, 0.7f), 1);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        onClose();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
