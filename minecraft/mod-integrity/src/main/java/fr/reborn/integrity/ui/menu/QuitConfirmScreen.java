package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Modal de confirmation "Quitter Reborn ?" — overlay au-dessus du
 * main menu. Click en dehors / Annuler ramène au main menu. Quitter
 * appelle {@code MinecraftClient#scheduleStop}.
 *
 * <p>Layout : backdrop noir 75% + card centrée (380×160) avec titre,
 * description, 2 boutons (Annuler ghost + Quitter rouge).
 */
public class QuitConfirmScreen extends Screen {

    private final Screen parent;

    private static final int CARD_W = 380;
    private static final int CARD_H = 160;

    public QuitConfirmScreen(Screen parent) {
        super(Text.literal("Quitter Reborn"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        int btnW = 130;
        int btnH = 32;
        int gap = 12;
        int btnY = cardY + CARD_H - 24 - btnH;

        // Annuler (ghost) à gauche.
        this.addDrawableChild(ButtonWidget.builder(
            RebornFont.bold("Annuler"),
            b -> close()
        ).dimensions(cardX + CARD_W / 2 - btnW - gap / 2, btnY, btnW, btnH).build());

        // Quitter (rouge) à droite.
        this.addDrawableChild(ButtonWidget.builder(
            RebornFont.bold("Quitter"),
            b -> MinecraftClient.getInstance().scheduleStop()
        ).dimensions(cardX + CARD_W / 2 + gap / 2, btnY, btnW, btnH).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fond noir total — pas de re-render du main menu derrière (Tauri
        // n'expose pas de shader blur natif pour faire le vrai "blur"
        // souhaité, donc fallback sur un fond opaque clean qui focus
        // l'attention sur la modal).
        context.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        // Card.
        DrawHelpers.dropShadow(context, cardX, cardY, CARD_W, CARD_H, 6);
        DrawHelpers.roundedOutlinedRect(context, cardX, cardY, CARD_W, CARD_H, 12,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
        // Trait d'accent en haut.
        context.fill(cardX + 12, cardY, cardX + CARD_W - 12, cardY + 2, Colors.ACCENT);

        // Titre.
        Text title = RebornFont.bold("Quitter Reborn ?");
        float titleScale = 1.6f;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = cardX + (CARD_W - titleW) / 2;
        int titleY = cardY + 24;
        context.getMatrices().push();
        context.getMatrices().translate(titleX, titleY, 0);
        context.getMatrices().scale(titleScale, titleScale, 1f);
        context.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        context.getMatrices().pop();

        // Description.
        Text desc = RebornFont.body("Tu vas être déconnecté du serveur et fermer Minecraft.");
        int descW = tr.getWidth(desc);
        int descX = cardX + (CARD_W - descW) / 2;
        int descY = cardY + 60;
        context.drawText(tr, desc, descX, descY, Colors.FOREGROUND_SUBTLE, false);
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
