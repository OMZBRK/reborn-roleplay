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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Écran Boutique Reborn — ouvert depuis la carte Boutique du menu ÉCHAP.
 * Stub pour l'instant (« Bientôt disponible ») : la vraie boutique in-game
 * (catégories, items, monnaie) sera conçue plus tard. Même fond/logo que le
 * menu pause pour la cohérence visuelle.
 */
public class ShopScreen extends Screen {

    private static final Identifier LOGO = Identifier.of("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    private final Screen parent;

    public ShopScreen(Screen parent) {
        super(Text.literal("Boutique"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(RebornButton.ghost(
            20, 18, 96, 26, "< Retour", b -> close()));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int bottomTint = Colors.lerp(Colors.BACKGROUND, Colors.ACCENT, 0.10f);
        DrawHelpers.verticalGradient(ctx, 0, 0, this.width, this.height, Colors.BACKGROUND, bottomTint);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);

        int logoW = Math.min(Math.round(this.width * 0.14f), 190);
        int logoH = Math.round(logoW * (float) LOGO_TEX_H / LOGO_TEX_W);
        ctx.drawTexture(LOGO, (this.width - logoW) / 2, 14, logoW, logoH,
            0f, 0f, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        Text title = RebornFont.arcade("BOUTIQUE");
        float sc = 2.4f;
        int tw = Math.round(tr.getWidth(title) * sc);
        ctx.getMatrices().push();
        ctx.getMatrices().translate((this.width - tw) / 2f, this.height / 2f - 24, 0);
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        Text hint = RebornFont.arcade("BIENTOT DISPONIBLE");
        ctx.drawText(tr, hint, (this.width - tr.getWidth(hint)) / 2, this.height / 2 + 16,
            Colors.FOREGROUND_MUTED, false);

        for (Element e : this.children()) {
            if (e instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
        }
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
