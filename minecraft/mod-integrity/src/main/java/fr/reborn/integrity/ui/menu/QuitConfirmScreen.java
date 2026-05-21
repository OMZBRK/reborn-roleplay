package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Modal de confirmation "Quitter Reborn ?" — overlay au-dessus du
 * main menu. Click en dehors / Annuler ramène au main menu. Quitter
 * appelle {@code MinecraftClient#scheduleStop}.
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
        super(Text.literal("Quitter Reborn"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        int btnW = 120;
        int btnH = 32;
        int gap = 12;
        int btnY = cardY + CARD_H - 22 - btnH;

        this.addDrawableChild(RebornButton.ghost(
            cardX + CARD_W / 2 - btnW - gap / 2, btnY, btnW, btnH,
            "Annuler", b -> close()
        ));
        this.addDrawableChild(RebornButton.danger(
            cardX + CARD_W / 2 + gap / 2, btnY, btnW, btnH,
            "Quitter", b -> MinecraftClient.getInstance().scheduleStop()
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fond noir total. Pas de blur natif sans shader custom, donc
        // overlay opaque qui focus l'attention sur la modal.
        context.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Ordre critique : background → card+contenu → enfants (boutons).
        // Sinon super.render() dessine les boutons EN PREMIER, puis on
        // dessine la card par-dessus et les boutons sont masqués.
        this.renderBackground(context, mouseX, mouseY, delta);

        drawCard(context);

        // Enfants (RebornButton ghost + danger) au-dessus de la card.
        for (Element e : this.children()) {
            if (e instanceof Drawable d) {
                d.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void drawCard(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        // Drop shadow + card BG.
        DrawHelpers.dropShadow(context, cardX, cardY, CARD_W, CARD_H, 6);
        DrawHelpers.roundedOutlinedRect(context, cardX, cardY, CARD_W, CARD_H, 12,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
        // Trait d'accent bleu en haut.
        context.fill(cardX + 12, cardY, cardX + CARD_W - 12, cardY + 2, Colors.ACCENT);

        // Titre — Bebas Neue display équilibré (1.4x).
        Text title = RebornFont.bold("Quitter Reborn ?");
        float titleScale = 1.4f;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = cardX + (CARD_W - titleW) / 2;
        int titleY = cardY + 22;
        context.getMatrices().push();
        context.getMatrices().translate(titleX, titleY, 0);
        context.getMatrices().scale(titleScale, titleScale, 1f);
        context.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        context.getMatrices().pop();

        // Description — taille normale, lisible, ligne sous le titre.
        Text desc = RebornFont.body("Tu vas être déconnecté du serveur");
        Text desc2 = RebornFont.body("et fermer Minecraft.");
        int descW = tr.getWidth(desc);
        int desc2W = tr.getWidth(desc2);
        int descY = cardY + 60;
        context.drawText(tr, desc, cardX + (CARD_W - descW) / 2, descY,
            Colors.FOREGROUND_SUBTLE, false);
        context.drawText(tr, desc2, cardX + (CARD_W - desc2W) / 2, descY + 12,
            Colors.FOREGROUND_SUBTLE, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
