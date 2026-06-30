package fr.reborn.hud.ui;

import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Écran du Mode Photo : la scène 3D reste visible (pas de fond assombri), un
 * <b>panneau</b> à droite avec un bouton <b>Capturer</b> et un bouton Quitter.
 * On regarde en <b>glissant</b> (clic gauche maintenu hors du panneau), on se
 * déplace en ZQSD (géré par {@link PhotoMode}). Curseur libre (écran) → le
 * bouton est cliquable.
 */
public class PhotoModeScreen extends Screen {

    private static final int BG = 0xCC0C0709;
    private static final int BORDER = 0xFF6E1B27;
    private static final int ACCENT = 0xFFA0182B;
    private static final int ACCENT_HOV = 0xFFC2364A;
    private static final int GOLD = 0xFFD9A95E;
    private static final int TEXT = 0xFFE8DCC8;

    private int pX, pY, pW, pH;

    public PhotoModeScreen() {
        super(Text.literal("Photo Mode"));
    }

    @Override
    protected void init() {
        PhotoMode.INSTANCE.begin(MinecraftClient.getInstance());
        pW = 156;
        pH = 116;
        pX = this.width - pW - 12;
        pY = (this.height - pH) / 2;
    }

    private int capX() { return pX + 12; }
    private int capY() { return pY + 30; }
    private int capW() { return pW - 24; }
    private int quitY() { return capY() + 30; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Pas de renderBackground : on veut voir la scène.
        TextRenderer tr = this.textRenderer;

        ctx.fill(pX, pY, pX + pW, pY + pH, BG);
        ctx.fill(pX, pY, pX + pW, pY + 1, BORDER);
        ctx.fill(pX, pY + pH - 1, pX + pW, pY + pH, BORDER);
        ctx.fill(pX, pY, pX + 1, pY + pH, BORDER);
        ctx.fill(pX + pW - 1, pY, pX + pW, pY + pH, BORDER);

        ctx.drawText(tr, Text.literal("PHOTO MODE").styled(s -> s.withBold(true)), pX + 12, pY + 10, GOLD, false);

        boolean capHov = in(mouseX, mouseY, capX(), capY(), capW(), 22);
        ctx.fill(capX(), capY(), capX() + capW(), capY() + 22, capHov ? ACCENT_HOV : ACCENT);
        center(ctx, tr, "Capturer", capX(), capY() + 7, capW(), 0xFFFFFFFF);

        boolean qHov = in(mouseX, mouseY, capX(), quitY(), capW(), 16);
        ctx.fill(capX(), quitY(), capX() + capW(), quitY() + 16, qHov ? 0x40FFFFFF : 0x22FFFFFF);
        center(ctx, tr, "Quitter [" + quitKey() + "]", capX(), quitY() + 4, capW(), TEXT);

        ctx.drawText(tr, Text.literal("Glisser : regarder"), pX + 12, quitY() + 24, 0xFF9A8B78, false);
        ctx.drawText(tr, Text.literal("ZQSD/Espace : bouger"), pX + 12, quitY() + 34, 0xFF9A8B78, false);
    }

    private String quitKey() {
        if (HudKeybinds.PHOTO != null) return HudKeybinds.PHOTO.getBoundKeyLocalizedText().getString();
        return "P";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (in((int) mx, (int) my, capX(), capY(), capW(), 22)) {
                PhotoMode.INSTANCE.requestCapture();
                return true;
            }
            if (in((int) mx, (int) my, capX(), quitY(), capW(), 16)) {
                close();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        // Glisser hors du panneau → regarder.
        if (button == 0 && !in((int) mx, (int) my, pX, pY, pW, pH)) {
            PhotoMode.INSTANCE.rotate(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (HudKeybinds.PHOTO != null && HudKeybinds.PHOTO.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        PhotoMode.INSTANCE.end(MinecraftClient.getInstance());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void center(DrawContext ctx, TextRenderer tr, String s, int x, int y, int w, int color) {
        ctx.drawText(tr, Text.literal(s), x + (w - tr.getWidth(s)) / 2, y, color, false);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
