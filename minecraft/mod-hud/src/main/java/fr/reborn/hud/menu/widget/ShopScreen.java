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
import net.minecraft.resources.Identifier;

/**
 * Écran Boutique Reborn — ouvert depuis la carte Boutique du menu ÉCHAP.
 * Stub pour l'instant (« Bientôt disponible ») : la vraie boutique in-game
 * (catégories, items, monnaie) sera conçue plus tard. Même fond/logo que le
 * menu pause pour la cohérence visuelle.
 */
public class ShopScreen extends Screen {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    private final Screen parent;

    public ShopScreen(Screen parent) {
        super(Component.literal("Boutique"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(RebornButton.ghost(
            20, 18, 96, 26, "< Retour", b -> onClose()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int bottomTint = Colors.lerp(Colors.BACKGROUND, Colors.ACCENT, 0.10f);
        DrawHelpers.verticalGradient(ctx, 0, 0, this.width, this.height, Colors.BACKGROUND, bottomTint);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.extractBackground(ctx, mouseX, mouseY, delta);

        int logoW = Math.min(Math.round(this.width * 0.14f), 190);
        int logoH = Math.round(logoW * (float) LOGO_TEX_H / LOGO_TEX_W);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, (this.width - logoW) / 2, 14, 0f, 0f, logoW, logoH, LOGO_TEX_W, LOGO_TEX_H);

        Minecraft mc = Minecraft.getInstance();
        Font tr = mc.font;

        Component title = RebornFont.arcade("BOUTIQUE");
        float sc = 2.4f;
        int tw = Math.round(tr.width(title) * sc);
        ctx.pose().pushMatrix();
        ctx.pose().translate((this.width - tw) / 2f, this.height / 2f - 24);
        ctx.pose().scale(sc, sc);
        ctx.text(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.pose().popMatrix();

        Component hint = RebornFont.arcade("BIENTOT DISPONIBLE");
        ctx.text(tr, hint, (this.width - tr.width(hint)) / 2, this.height / 2 + 16,
            Colors.FOREGROUND_MUTED, false);

        for (GuiEventListener e : this.children()) {
            if (e instanceof Renderable d) d.extractRenderState(ctx, mouseX, mouseY, delta);
        }
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
