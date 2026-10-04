package fr.reborn.hud.ui;

import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Écran du Mode Photo — DA Reborn, même fonctionnement qu'avant :
 * <ul>
 *   <li><b>Idle</b> : panneau laqué à droite (sceau 撮) — vitesse caméra −/+, Capturer,
 *       Réinitialiser la position, Quitter.</li>
 *   <li><b>En mouvement</b> (ZQSD / glisser) : panneau masqué, petite étiquette « Mode photo ».</li>
 * </ul>
 * Glisser hors du panneau = regarder ; T ou / = chat par-dessus la freecam.
 */
public class PhotoModeScreen extends Screen {

    private static final int PW = 150, PH = 148, ANIM_MS = 220;
    private int pX, pY;
    private boolean dragging = false;
    private long openedAt;
    private int lastHover = -1;

    public PhotoModeScreen() {
        super(Component.literal("Photo Mode"));
    }

    @Override
    protected void init() {
        boolean fresh = !PhotoMode.INSTANCE.isActive();
        PhotoMode.INSTANCE.begin(Minecraft.getInstance());   // sans effet si déjà actif (retour du chat)
        pX = this.width - PW - 12;
        pY = (this.height - PH) / 2;
        openedAt = System.currentTimeMillis();
        if (fresh) RebornSounds.playReborn("esc.open", 1.2f, 0.35f);
    }

    private float ease() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / (float) ANIM_MS);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // Rects (position finale ; le survol tient compte du glissé d'entrée).
    private int spdY()   { return pY + 34; }
    private int minusX() { return pX + PW - 62; }
    private int plusX()  { return pX + PW - 24; }
    private int capY()   { return pY + 58; }
    private int resetY() { return pY + 84; }
    private int quitY()  { return pY + 104; }
    private int btnX()   { return pX + 10; }
    private int btnW()   { return PW - 20; }

    private boolean moving() {
        return dragging || PhotoMode.INSTANCE.anyMoveKeyDown(Minecraft.getInstance());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Pas de flou : la scène doit rester nette pour cadrer.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font f = this.font;
        float s = Da.small();

        if (moving()) {
            String label = "Mode photo";
            int w = Da.width(f, label, s) + 26, x = (this.width - w) / 2, y = this.height - 24;
            ctx.fill(x, y, x + w, y + 13, 0xD00E0A0C);
            Da.outline(ctx, x, y, w, 13, Da.GOLD_D);
            ctx.fill(x + 5, y + 4, x + 10, y + 9, Da.RED);
            Da.text(ctx, f, label, x + 15, y + 3.5f, s, Da.CREAM, 0);
            return;
        }

        int slide = Math.round((1f - ease()) * 40f);
        double mx = mouseX - slide;
        ctx.pose().pushMatrix();
        ctx.pose().translate(slide, 0);

        Da.panel(ctx, pX, pY, PW, PH);
        // en-tête : sceau 撮 + titre
        ctx.fill(pX + 8, pY + 8, pX + 24, pY + 24, Da.RED);
        Da.outline(ctx, pX + 8, pY + 8, 16, 16, Da.GOLD);
        ctx.text(f, Component.literal("撮"), pX + 12, pY + 12, Da.CREAM, false);
        Da.text(ctx, f, "Mode photo", pX + 30, pY + 12, Da.title(), Da.CREAM, 0);
        ctx.fill(pX + 8, pY + 28, pX + PW - 8, pY + 29, Da.GOLD_D);

        // Vitesse : libellé, −, valeur, +
        Da.text(ctx, f, "Vitesse", pX + 10, spdY() + 4, s, Da.MUTED, 0);
        int hover = -1;
        boolean mh = Da.in(mx, mouseY, minusX(), spdY(), 14, 13), ph = Da.in(mx, mouseY, plusX(), spdY(), 14, 13);
        Da.plate(ctx, f, minusX(), spdY(), 14, 13, "-", false, mh, true);
        Da.plate(ctx, f, plusX(), spdY(), 14, 13, "+", false, ph, true);
        Da.text(ctx, f, String.format(Locale.ROOT, "%.2f", PhotoMode.INSTANCE.getCameraSpeed()),
            (minusX() + 14 + plusX()) / 2f, spdY() + 4, s, Da.GOLD, 1);
        if (mh) hover = 0; if (ph) hover = 1;

        boolean ch = Da.in(mx, mouseY, btnX(), capY(), btnW(), 20);
        Da.plate(ctx, f, btnX(), capY(), btnW(), 20, "Capturer", true, ch, true);
        boolean rh = Da.in(mx, mouseY, btnX(), resetY(), btnW(), 14);
        Da.plate(ctx, f, btnX(), resetY(), btnW(), 14, "Reinitialiser position", false, rh, true);
        boolean qh = Da.in(mx, mouseY, btnX(), quitY(), btnW(), 14);
        Da.plate(ctx, f, btnX(), quitY(), btnW(), 14, "Quitter [" + quitKey() + "]", false, qh, true);
        if (ch) hover = 2; if (rh) hover = 3; if (qh) hover = 4;

        Da.text(ctx, f, "Glisser : regarder", pX + 10, quitY() + 21, s, Da.MUTED, 0);
        Da.text(ctx, f, "ZQSD / Espace : bouger", pX + 10, quitY() + 30, s, Da.MUTED, 0);
        ctx.pose().popMatrix();

        if (hover != lastHover) { if (hover >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hover; }
    }

    private String quitKey() {
        if (HudKeybinds.PHOTO != null) return HudKeybinds.PHOTO.getTranslatedKeyMessage().getString();
        return "P";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && !moving()) {
            Minecraft mc = Minecraft.getInstance();
            if (Da.in(mx, my, minusX(), spdY(), 14, 13)) { PhotoMode.INSTANCE.addCameraSpeed(-0.05f); click(); return true; }
            if (Da.in(mx, my, plusX(), spdY(), 14, 13)) { PhotoMode.INSTANCE.addCameraSpeed(0.05f); click(); return true; }
            if (Da.in(mx, my, btnX(), capY(), btnW(), 20)) { PhotoMode.INSTANCE.requestCapture(); return true; }
            if (Da.in(mx, my, btnX(), resetY(), btnW(), 14)) { PhotoMode.INSTANCE.resetPosition(mc); click(); return true; }
            if (Da.in(mx, my, btnX(), quitY(), btnW(), 14)) { onClose(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && !Da.in(event.x(), event.y(), pX, pY, PW, PH)) {
            dragging = true;
            PhotoMode.INSTANCE.rotate(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (HudKeybinds.PHOTO != null && HudKeybinds.PHOTO.matches(event)) {
            onClose();
            return true;
        }
        // Ouvre le chat par-dessus la freecam (touche chat = T, ou commande = /).
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            if (mc.options.keyChat.matches(event)) {
                PhotoMode.INSTANCE.openChatOverlay(mc, false);
                return true;
            }
            if (mc.options.keyCommand.matches(event)) {
                PhotoMode.INSTANCE.openChatOverlay(mc, true);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private static void click() { RebornSounds.playReborn("esc.select", 1.1f, 0.35f); }

    @Override
    public void removed() {
        // On ne quitte PAS la freecam si on ouvre juste le chat par-dessus.
        if (!PhotoMode.INSTANCE.isSuspendedForChat()) {
            PhotoMode.INSTANCE.end(Minecraft.getInstance());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
