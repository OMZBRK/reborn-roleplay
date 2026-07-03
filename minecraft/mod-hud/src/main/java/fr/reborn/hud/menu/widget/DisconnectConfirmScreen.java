package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

/**
 * Modal de confirmation « Se déconnecter ? » — ouvert depuis l'onglet
 * Déconnexion du menu ÉCHAP. Annuler revient au menu pause ; Déconnexion quitte
 * le serveur et retourne au menu principal. Même style que {@link QuitConfirmScreen}.
 */
public class DisconnectConfirmScreen extends Screen {

    private final Screen parent;

    private static final int CARD_W = 320;
    private static final int CARD_H = 170;

    public DisconnectConfirmScreen(Screen parent) {
        super(Text.literal("Se déconnecter"));
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

        this.addDrawableChild(RebornButton.ghost(
            cardX + cardW / 2 - btnW - gap / 2, btnY, btnW, btnH,
            "Annuler", b -> close()
        ));
        this.addDrawableChild(RebornButton.danger(
            cardX + cardW / 2 + gap / 2, btnY, btnW, btnH,
            "Déconnexion", b -> disconnect()
        ));
    }

    private void disconnect() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            client.world.disconnect();
        }
        client.disconnect();
        client.setScreen(new TitleScreen());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, Colors.BACKDROP_85);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        drawCard(context);
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

        int cardW = Math.min(CARD_W, this.width - 24);
        int cardH = Math.min(CARD_H, this.height - 24);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        DrawHelpers.dropShadow(context, cardX, cardY, cardW, cardH, 6);
        DrawHelpers.roundedOutlinedRect(context, cardX, cardY, cardW, cardH, 12,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
        context.fill(cardX + 12, cardY, cardX + cardW - 12, cardY + 2, Colors.DANGER);

        Text title = RebornFont.bold("Se déconnecter ?");
        float titleScale = 1.4f;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = cardX + (cardW - titleW) / 2;
        int titleY = cardY + 22;
        context.getMatrices().push();
        context.getMatrices().translate(titleX, titleY, 0);
        context.getMatrices().scale(titleScale, titleScale, 1f);
        context.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        context.getMatrices().pop();

        Text desc = RebornFont.body("Tu vas quitter le serveur et revenir");
        Text desc2 = RebornFont.body("au menu principal.");
        int descW = tr.getWidth(desc);
        int desc2W = tr.getWidth(desc2);
        int descY = cardY + 60;
        context.drawText(tr, desc, cardX + (cardW - descW) / 2, descY,
            Colors.FOREGROUND_SUBTLE, false);
        context.drawText(tr, desc2, cardX + (cardW - desc2W) / 2, descY + 12,
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
