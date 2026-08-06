package fr.reborn.hud.ui;

import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran du Mode Photo (façon Zenkai) avec animation de slide à l'ouverture :
 * <ul>
 *   <li><b>Idle</b> : panneau d'options à droite — vitesse caméra, réinitialiser
 *       position, Capturer, Quitter.</li>
 *   <li><b>En mouvement</b> (ZQSD/glisser) : mini-indicateur « ● Photo Mode ».</li>
 * </ul>
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

    private static final int PW = 180, PH = 150, ANIM_MS = 200;
    private int pX, pY;
    private boolean dragging = false;
    private long openedAt;

    public PhotoModeScreen() {
        super(Component.literal("Photo Mode"));
    }

    @Override
    protected void init() {
        PhotoMode.INSTANCE.begin(Minecraft.getInstance());
        pX = this.width - PW - 12;
        pY = (this.height - PH) / 2;
        openedAt = System.currentTimeMillis();
    }

    private float ease() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) ANIM_MS);
        return 1f - (1f - t) * (1f - t) * (1f - t); // ease-out cubic
    }

    // Rects (coords finales — les clics utilisent la position finale).
    private int spdMinusX() { return pX + PW - 60; }
    private int spdPlusX()  { return pX + PW - 18; }
    private int spdY()      { return pY + 28; }
    private int capY()      { return pY + 50; }
    private int resetY()    { return pY + 78; }
    private int quitY()     { return pY + 100; }
    private int btnX()      { return pX + 12; }
    private int btnW()      { return PW - 24; }

    private boolean moving() {
        return dragging || PhotoMode.INSTANCE.anyMoveKeyDown(Minecraft.getInstance());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Pas de flou : le mode photo doit montrer la scène 3D NETTE pour cadrer.
        // (Screen applique par défaut un backdrop flou quand un monde est chargé.)
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font tr = this.font;

        if (moving()) {
            String s = "● Photo Mode";
            int w = tr.width(s) + 16;
            int x = (this.width - w) / 2, y = this.height - 26;
            ctx.fill(x, y, x + w, y + 16, BG);
            ctx.fill(x, y, x + w, y + 1, ACCENT);
            ctx.text(tr, Component.literal(s), x + 8, y + 4, GOLD, false);
            return;
        }

        // Slide depuis la droite.
        float slide = (1f - ease()) * 44f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(slide, 0);

        ctx.fill(pX, pY, pX + PW, pY + PH, BG);
        border(ctx, pX, pY, PW, PH);
        ctx.text(tr, Component.literal("● PHOTO MODE").withStyle(s -> s.withBold(true)), pX + 12, pY + 10, GOLD, false);

        // Vitesse : label  [-]  valeur  [+]
        ctx.text(tr, Component.literal("Vitesse"), pX + 12, spdY() + 3, TEXT, false);
        button(ctx, tr, "-", spdMinusX(), spdY(), 14, 14, in(mouseX - slide, mouseY, spdMinusX(), spdY(), 14, 14), SUB);
        center(ctx, tr, String.format("%.2f", PhotoMode.INSTANCE.getCameraSpeed()), spdMinusX() + 16, spdY() + 3, 24, GOLD);
        button(ctx, tr, "+", spdPlusX(), spdY(), 14, 14, in(mouseX - slide, mouseY, spdPlusX(), spdY(), 14, 14), SUB);

        boolean capHov = in(mouseX - slide, mouseY, btnX(), capY(), btnW(), 22);
        ctx.fill(btnX(), capY(), btnX() + btnW(), capY() + 22, capHov ? GREEN_HOV : GREEN);
        center(ctx, tr, "Capturer", btnX(), capY() + 7, btnW(), 0xFF0A1A0E);

        button(ctx, tr, "Réinitialiser position", btnX(), resetY(), btnW(), 16,
            in(mouseX - slide, mouseY, btnX(), resetY(), btnW(), 16), SUB);

        boolean qHov = in(mouseX - slide, mouseY, btnX(), quitY(), btnW(), 16);
        ctx.fill(btnX(), quitY(), btnX() + btnW(), quitY() + 16, qHov ? ACCENT_HOV : ACCENT);
        center(ctx, tr, "Quitter [" + quitKey() + "]", btnX(), quitY() + 4, btnW(), 0xFFFFFFFF);

        ctx.text(tr, Component.literal("Glisser : regarder"), pX + 12, quitY() + 22, MUTED, false);
        ctx.text(tr, Component.literal("ZQSD/Espace : bouger"), pX + 12, quitY() + 32, MUTED, false);

        ctx.pose().popMatrix();
    }

    private String quitKey() {
        if (HudKeybinds.PHOTO != null) return HudKeybinds.PHOTO.getTranslatedKeyMessage().getString();
        return "P";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (button == 0 && !moving()) {
            Minecraft mc = Minecraft.getInstance();
            if (in(mx, my, spdMinusX(), spdY(), 14, 14)) { PhotoMode.INSTANCE.addCameraSpeed(-0.05f); return true; }
            if (in(mx, my, spdPlusX(), spdY(), 14, 14)) { PhotoMode.INSTANCE.addCameraSpeed(0.05f); return true; }
            if (in(mx, my, btnX(), capY(), btnW(), 22)) { PhotoMode.INSTANCE.requestCapture(); return true; }
            if (in(mx, my, btnX(), resetY(), btnW(), 16)) { PhotoMode.INSTANCE.resetPosition(mc); return true; }
            if (in(mx, my, btnX(), quitY(), btnW(), 16)) { onClose(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (button == 0 && !in(mx, my, pX, pY, PW, PH)) {
            dragging = true;
            PhotoMode.INSTANCE.rotate(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x(), my = event.y(); int button = event.button();
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        if (HudKeybinds.PHOTO != null && HudKeybinds.PHOTO.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        PhotoMode.INSTANCE.end(Minecraft.getInstance());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void border(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + 1, BORDER);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER);
        ctx.fill(x, y, x + 1, y + h, BORDER);
        ctx.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    private static void button(GuiGraphicsExtractor ctx, Font tr, String label, int x, int y, int w, int h,
                               boolean hover, int bg) {
        ctx.fill(x, y, x + w, y + h, hover ? 0x40FFFFFF : bg);
        center(ctx, tr, label, x, y + (h - 8) / 2, w, hover ? 0xFFFFFFFF : TEXT);
    }

    private static void center(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y, int w, int color) {
        ctx.text(tr, Component.literal(s), x + (w - tr.width(s)) / 2, y, color, false);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
