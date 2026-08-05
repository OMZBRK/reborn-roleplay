package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modal de confirmation "Quitter Reborn ?" — overlay au-dessus du
 * main menu. Click en dehors / Annuler ramène au main menu. Quitter
 * appelle {@code Minecraft#stop}.
 *
 * <p>Layout : backdrop noir + card centrée (320×170) avec :
 * <ol>
 *   <li>Trait d'accent bleu en haut (2px).</li>
 *   <li>Titre "Quitter Reborn ?" en gras Inter, échelle 1.4x.</li>
 *   <li>Description en body Inter (échelle 1.0).</li>
 *   <li>2 boutons RebornButton (Annuler ghost / Quitter danger) rendus
 *       APRÈS la card pour garantir leur visibilité (sinon la card les
 *       masque — bug fixed).</li>
 * </ol>
 */
public class QuitConfirmScreen extends Screen {

    private final Screen parent;

    private static final int CARD_W = 320;
    private static final int CARD_H = 170;

    public QuitConfirmScreen(Screen parent) {
        super(Component.literal("Quitter Reborn"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardW = Math.min(CARD_W, this.width - 24);
        int cardH = Math.min(CARD_H, this.height - 24);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        int btnW = 120;
        int btnH = 32;
        int gap = 12;
        int btnY = cardY + cardH - 22 - btnH;

        this.addRenderableWidget(RebornButton.ghost(
            cardX + cardW / 2 - btnW - gap / 2, btnY, btnW, btnH,
            "Annuler", b -> onClose()
        ));
        this.addRenderableWidget(RebornButton.danger(
            cardX + cardW / 2 + gap / 2, btnY, btnW, btnH,
            "Quitter", b -> Minecraft.getInstance().stop()
        ));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Fond noir total. Pas de blur natif sans shader custom, donc
        // overlay opaque qui focus l'attention sur la modal.
        context.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Ordre critique : background → card+contenu → enfants (boutons).
        // Sinon super.extractRenderState() dessine les boutons EN PREMIER, puis on
        // dessine la card par-dessus et les boutons sont masqués.
        this.extractBackground(context, mouseX, mouseY, delta);

        drawCard(context);

        // Enfants (RebornButton ghost + danger) au-dessus de la card.
        for (GuiEventListener e : this.children()) {
            if (e instanceof Renderable d) {
                d.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
    }

    private void drawCard(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;

        int cardW = Math.min(CARD_W, this.width - 24);
        int cardH = Math.min(CARD_H, this.height - 24);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        // Drop shadow + card BG.
        DrawHelpers.dropShadow(context, cardX, cardY, cardW, cardH, 6);
        DrawHelpers.roundedOutlinedRect(context, cardX, cardY, cardW, cardH, 12,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
        // Trait d'accent bleu en haut.
        context.fill(cardX + 12, cardY, cardX + cardW - 12, cardY + 2, Colors.ACCENT);

        // Titre — Bebas Neue display équilibré (1.4x).
        Component title = RebornFont.bold("Quitter Reborn ?");
        float titleScale = 1.4f;
        int titleW = Math.round(tr.width(title) * titleScale);
        int titleX = cardX + (cardW - titleW) / 2;
        int titleY = cardY + 22;
        context.pose().pushMatrix();
        context.pose().translate(titleX, titleY);
        context.pose().scale(titleScale, titleScale);
        context.text(tr, title, 0, 0, Colors.WHITE_PURE, false);
        context.pose().popMatrix();

        // Description — taille normale, lisible, ligne sous le titre.
        Component desc = RebornFont.body("Tu vas être déconnecté du serveur");
        Component desc2 = RebornFont.body("et fermer Minecraft.");
        int descW = tr.width(desc);
        int desc2W = tr.width(desc2);
        int descY = cardY + 60;
        context.text(tr, desc, cardX + (cardW - descW) / 2, descY,
            Colors.FOREGROUND_SUBTLE, false);
        context.text(tr, desc2, cardX + (cardW - desc2W) / 2, descY + 12,
            Colors.FOREGROUND_SUBTLE, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
