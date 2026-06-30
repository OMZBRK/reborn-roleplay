package fr.reborn.hud.ui;

import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Écran du Mode Photo (façon Zenkai) :
 * <ul>
 *   <li><b>Idle</b> : panneau d'options à droite — vitesse caméra, réinitialiser
 *       position, bouton Capturer, Quitter.</li>
 *   <li><b>En mouvement</b> (ZQSD ou glisser) : le panneau se réduit à un petit
 *       indicateur « ● Photo Mode » pour dégager la vue.</li>
 * </ul>
 * Scène 3D visible (pas de fond assombri). Regarder = glisser hors du panneau.
 */
public class PhotoModeScreen extends Screen {

    private static final int BG = 0xE60C0709;
    private static final int BORDER = 0xFF6E1B27;
    private static final int ACCENT = 0xFFA0182B;
    private static final int ACCENT_HOV = 0xFFC2364A;
    private static final int GREEN = 0xFF3FA85B;
    private static final int GREEN_HOV = 0xFF4ECE6F;
    private static final int GOLD = 0xFFD9A95E;
    private static final int TEXT = 0xFFE8DCC8;
    private static final int MUTED = 0xFF9A8B78;
    private static final int SUB = 0xFF170C10;

    private static final int PW = 172, PH = 150;
    private int pX, pY;
    private boolean dragging = false;

    public PhotoModeScreen() {
        super(Text.literal("Photo Mode"));
    }

    @Override
    protected void init() {
        PhotoMode.INSTANCE.begin(MinecraftClient.getInstance());
        pX = this.width - PW - 12;
        pY = (this.height - PH) / 2;
    }

    // Rects (sync render/clic).
    private int spdMinusX() { return pX + PW - 58; }
    private int spdPlusX()  { return pX + PW - 22; }
    private int spdY()      { return pY + 28; }
    private int capY()      { return pY + 50; }
    private int resetY()    { return pY + 78; }
    private int quitY()     { return pY + 100; }
    private int btnX()      { return pX + 12; }
    private int btnW()      { return PW - 24; }

    private boolean moving() {
        return dragging || PhotoMode.INSTANCE.anyMoveKeyDown(MinecraftClient.getInstance());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = this.textRenderer;

        if (moving()) {
            // Mini-indicateur en bas.
            String s = "● Photo Mode";
            int w = tr.getWidth(s) + 16;
            int x = (this.width - w) / 2, y = this.height - 26;
            ctx.fill(x, y, x + w, y + 16, BG);
            ctx.fill(x, y, x + w, y + 1, ACCENT);
            ctx.drawText(tr, Text.literal(s), x + 8, y + 4, GOLD, false);
            return;
        }

        // Panneau complet.
        ctx.fill(pX, pY, pX + PW, pY + PH, BG);
        border(ctx, pX, pY, PW, PH);
        ctx.drawText(tr, Text.literal("● PHOTO MODE").styled(s -> s.withBold(true)), pX + 12, pY + 10, GOLD, false);

        // Vitesse caméra.
        ctx.drawText(tr, Text.literal("Vitesse"), pX + 12, spdY() + 3, TEXT, false);
        button(ctx, tr, "-", spdMinusX(), spdY(), 16, 14, in(mouseX, mouseY, spdMinusX(), spdY(), 16, 14), SUB);
        String spd = String.format("%.2f", PhotoMode.INSTANCE.getCameraSpeed());
        ctx.drawText(tr, Text.literal(spd), spdMinusX() + 19, spdY() + 3, GOLD, false);
        button(ctx, tr, "+", spdPlusX(), spdY(), 16, 14, in(mouseX, mouseY, spdPlusX(), spdY(), 16, 14), SUB);

        // Capturer (vert, façon Zenkai).
        boolean capHov = in(mouseX, mouseY, btnX(), capY(), btnW(), 22);
        ctx.fill(btnX(), capY(), btnX() + btnW(), capY() + 22, capHov ? GREEN_HOV : GREEN);
        center(ctx, tr, "Capturer", btnX(), capY() + 7, btnW(), 0xFF0A1A0E);

        // Réinitialiser position.
        button(ctx, tr, "Réinitialiser position", btnX(), resetY(), btnW(), 16,
            in(mouseX, mouseY, btnX(), resetY(), btnW(), 16), SUB);

        // Quitter [touche].
        boolean qHov = in(mouseX, mouseY, btnX(), quitY(), btnW(), 16);
        ctx.fill(btnX(), quitY(), btnX() + btnW(), quitY() + 16, qHov ? ACCENT_HOV : ACCENT);
        center(ctx, tr, "Quitter [" + quitKey() + "]", btnX(), quitY() + 4, btnW(), 0xFFFFFFFF);

        ctx.drawText(tr, Text.literal("Glisser : regarder"), pX + 12, quitY() + 22, MUTED, false);
        ctx.drawText(tr, Text.literal("ZQSD/Espace : bouger"), pX + 12, quitY() + 32, MUTED, false);
    }

    private String quitKey() {
        if (HudKeybinds.PHOTO != null) return HudKeybinds.PHOTO.getBoundKeyLocalizedText().getString();
        return "P";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !moving()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (in((int) mx, (int) my, spdMinusX(), spdY(), 16, 14)) { PhotoMode.INSTANCE.addCameraSpeed(-0.05f); return true; }
            if (in((int) mx, (int) my, spdPlusX(), spdY(), 16, 14)) { PhotoMode.INSTANCE.addCameraSpeed(0.05f); return true; }
            if (in((int) mx, (int) my, btnX(), capY(), btnW(), 22)) { PhotoMode.INSTANCE.requestCapture(); return true; }
            if (in((int) mx, (int) my, btnX(), resetY(), btnW(), 16)) { PhotoMode.INSTANCE.resetPosition(mc); return true; }
            if (in((int) mx, (int) my, btnX(), quitY(), btnW(), 16)) { close(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && !in((int) mx, (int) my, pX, pY, PW, PH)) {
            dragging = true;
            PhotoMode.INSTANCE.rotate(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
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

    private static void border(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + 1, BORDER);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER);
        ctx.fill(x, y, x + 1, y + h, BORDER);
        ctx.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    private static void button(DrawContext ctx, TextRenderer tr, String label, int x, int y, int w, int h,
                               boolean hover, int bg) {
        ctx.fill(x, y, x + w, y + h, hover ? 0x40FFFFFF : bg);
        center(ctx, tr, label, x, y + (h - 8) / 2, w, hover ? 0xFFFFFFFF : TEXT);
    }

    private static void center(DrawContext ctx, TextRenderer tr, String s, int x, int y, int w, int color) {
        ctx.drawText(tr, Text.literal(s), x + (w - tr.getWidth(s)) / 2, y, color, false);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
