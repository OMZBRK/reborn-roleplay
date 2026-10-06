package fr.reborn.hud.ui;

import fr.reborn.hud.immersion.PhotoFrames;
import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.screenshot.ScreenshotLibrary;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Mode photo — viseur DA Reborn.
 * <ul>
 *   <li>Viseur : repères d'angle, grille des tiers en pointillés, cercle central.</li>
 *   <li>Plaque en haut à gauche : sceau 撮, « Mode photo », FOV / inclinaison / vitesse en direct.</li>
 *   <li>Panneau laqué à droite : réglettes Vitesse, Champ de vision, Inclinaison, Flou ; filtres
 *       (naturel, encre, sépia, nuit) ; cadre appliqué au fichier (aucun, makimono, polaroïd) ; recentrer.</li>
 *   <li>Déclencheur rond en bas au centre, dernières photos en bas à gauche.</li>
 * </ul>
 * En mouvement (ZQSD / glisser) l'interface s'efface pour ne laisser que le viseur. À la capture,
 * l'interface est masquée quelques images (la photo est propre), puis flash + obturateur.
 * Molette = champ de vision, Maj + molette = inclinaison, Entrée = capturer, P / Échap = quitter.
 */
public class PhotoModeScreen extends Screen {

    private static final int PW = 96, PH = 214;
    private static final int HIDE_FRAMES = 6;
    private static final String[] SLIDERS = { "Vitesse", "Champ de vision", "Inclinaison", "Flou" };
    private static final int[] SWATCH = { 0xFFECDCC4, 0xFF96969E, 0xFFD69E70, 0xFF6E8CBE };

    private int pX, pY;
    private boolean draggingView = false;
    private int draggingSlider = -1;
    private long openedAt;
    private int lastHover = -1;
    private long lastTick = 0;

    // capture
    private int hideFrames = 0;
    private long flashAt = 0;
    private long captureAt = 0;
    private int waitTicks = 0;
    private long lastSize = -1;
    private List<ScreenshotLibrary.Entry> recent = List.of();
    private int recentTimer = 0;

    public PhotoModeScreen() {
        super(Component.literal("Photo Mode"));
    }

    @Override
    protected void init() {
        boolean fresh = !PhotoMode.INSTANCE.isActive();
        PhotoMode.INSTANCE.begin(Minecraft.getInstance());   // sans effet si déjà actif (retour du chat)
        layout();
        openedAt = System.currentTimeMillis();
        recent = latest();
        if (fresh) RebornSounds.playReborn("photo.open", 1f, 0.5f);
    }

    private void layout() {
        pX = this.width - PW - 16;
        pY = Math.max(56, (this.height - PH) / 2 - 10);
    }

    private static List<ScreenshotLibrary.Entry> latest() {
        List<ScreenshotLibrary.Entry> all = ScreenshotLibrary.list(false);
        return all.subList(0, Math.min(4, all.size()));
    }

    private boolean moving() {
        return draggingView || PhotoMode.INSTANCE.anyMoveKeyDown(Minecraft.getInstance());
    }

    // ── valeurs des réglettes (0..1) ─────────────────────────────
    private static float sliderValue(int i) {
        PhotoMode p = PhotoMode.INSTANCE;
        return switch (i) {
            case 0 -> (p.getCameraSpeed() - 0.05f) / (2.0f - 0.05f);
            case 1 -> (p.fov() - PhotoMode.FOV_MIN) / (PhotoMode.FOV_MAX - PhotoMode.FOV_MIN);
            case 2 -> (p.roll() + PhotoMode.ROLL_MAX) / (2 * PhotoMode.ROLL_MAX);
            default -> p.blur() / (float) (PhotoMode.BLUR_LEVELS - 1);
        };
    }

    private static void setSlider(int i, float t) {
        PhotoMode p = PhotoMode.INSTANCE;
        t = Math.max(0, Math.min(1, t));
        switch (i) {
            case 0 -> p.setCameraSpeed(Math.round((0.05f + t * 1.95f) * 20) / 20f);
            case 1 -> p.setFov(Math.round(PhotoMode.FOV_MIN + t * (PhotoMode.FOV_MAX - PhotoMode.FOV_MIN)));
            case 2 -> p.setRoll(Math.round(-PhotoMode.ROLL_MAX + t * 2 * PhotoMode.ROLL_MAX));
            default -> p.setBlur(Math.round(t * (PhotoMode.BLUR_LEVELS - 1)));
        }
    }

    private static String sliderText(int i) {
        PhotoMode p = PhotoMode.INSTANCE;
        return switch (i) {
            case 0 -> String.format(Locale.ROOT, "x%.1f", p.getCameraSpeed() / 0.35f);
            case 1 -> Math.round(p.fov()) + "";
            case 2 -> Math.round(p.roll()) + "";
            default -> Math.round(p.blur() * 100f / (PhotoMode.BLUR_LEVELS - 1)) + "%";
        };
    }

    // ── géométrie ────────────────────────────────────────────────
    private int sliderY(int i) { return pY + 12 + i * 30; }
    private int sliderX() { return pX + 8; }
    private int sliderW() { return PW - 16; }
    private int swatchY() { return pY + 136; }
    private int frameY() { return pY + 176; }
    private int resetY() { return pY + 194; }
    private int shutterX() { return this.width / 2; }
    private int shutterY() { return this.height - 44; }

    // ── rendu ────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Pas de flou : la scène doit rester nette pour cadrer.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();
        Font f = this.font;
        long now = System.currentTimeMillis();

        // Capture : interface masquée quelques images pour une photo propre.
        if (hideFrames > 0) {
            hideFrames--;
            if (hideFrames == HIDE_FRAMES - 2) PhotoMode.INSTANCE.requestCapture();
            if (hideFrames == 0) {
                flashAt = now;
                captureAt = now;
                waitTicks = 0; lastSize = -1;
                RebornSounds.playReborn("photo.shutter", 1f, 0.8f);
            }
            return;
        }

        drawViewfinder(g);

        if (!moving()) {
            float appear = Math.min(1f, (now - openedAt) / 220f);
            int slide = Math.round((1f - (1f - (1f - appear) * (1f - appear) * (1f - appear))) * 40f);
            drawPlaque(g, f);
            g.pose().pushMatrix();
            g.pose().translate(slide, 0);
            int hover = drawPanel(g, f, mouseX - slide, mouseY);
            g.pose().popMatrix();
            hover = drawBottom(g, f, mouseX, mouseY, hover);
            if (hover != lastHover) { if (hover >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hover; }
            Da.hint(g, f, this.width, this.height,
                "ZQSD/Espace : voler   Molette : FOV   Maj+molette : incliner   Entree : capturer   " + quitKey() + " : quitter");
        }

        // Flash après la capture.
        long fl = now - flashAt;
        if (flashAt > 0 && fl < 220) {
            int a = (int) (200 * (1f - fl / 220f));
            g.fill(0, 0, this.width, this.height, (a << 24) | 0xFFFFFF);
        }
    }

    private void drawViewfinder(GuiGraphicsExtractor g) {
        int w = this.width, h = this.height, m = 16, l = 16;
        int c = 0xFFF4EAD6;
        int[][] corners = { { m, m, 1, 1 }, { w - m, m, -1, 1 }, { m, h - m, 1, -1 }, { w - m, h - m, -1, -1 } };
        for (int[] k : corners) {
            int x = k[0], y = k[1], sx = k[2], sy = k[3];
            g.fill(Math.min(x, x + l * sx), y - (sy > 0 ? 0 : 1), Math.max(x, x + l * sx), y + (sy > 0 ? 2 : 1), c);
            g.fill(x - (sx > 0 ? 0 : 1), Math.min(y, y + l * sy), x + (sx > 0 ? 2 : 1), Math.max(y, y + l * sy), c);
        }
        // grille des tiers (pointillés)
        for (int k = 1; k <= 2; k++) {
            int gx = w * k / 3, gy = h * k / 3;
            for (int y = 0; y < h; y += 3) g.fill(gx, y, gx + 1, y + 1, 0x90FFFFFF);
            for (int x = 0; x < w; x += 3) g.fill(x, gy, x + 1, gy + 1, 0x90FFFFFF);
        }
        ring(g, w / 2, h / 2, 7, 0xC0FFFFFF);
    }

    private void drawPlaque(GuiGraphicsExtractor g, Font f) {
        PhotoMode p = PhotoMode.INSTANCE;
        int x = 26, y = 24, w = 150, h = 28;
        Da.panel(g, x, y, w, h);
        g.fill(x + 4, y + 4, x + 24, y + 24, Da.RED);
        Da.outline(g, x + 4, y + 4, 20, 20, Da.GOLD);
        g.text(f, Component.literal("撮"), x + 9, y + 10, Da.CREAM, false);
        Da.text(g, f, "Mode photo", x + 30, y + 6, Da.title(), Da.CREAM, 0);
        Da.text(g, f, String.format(Locale.ROOT, "FOV %d - INCL %d - x%.1f",
                Math.round(p.fov()), Math.round(p.roll()), p.getCameraSpeed() / 0.35f),
            x + 30, y + 17, Da.small(), Da.GOLD, 0);
    }

    /** Panneau droit ; renvoie l'id survolé (-1 = aucun). */
    private int drawPanel(GuiGraphicsExtractor g, Font f, double mx, double my) {
        PhotoMode p = PhotoMode.INSTANCE;
        float s = Da.small();
        int hover = -1;
        Da.panel(g, pX, pY, PW, PH);
        for (int i = 0; i < SLIDERS.length; i++) {
            int y = sliderY(i), x = sliderX(), w = sliderW();
            Da.text(g, f, SLIDERS[i], x, y, s, Da.MUTED, 0);
            Da.text(g, f, sliderText(i), x + w, y, s, Da.GOLD, 2);
            int ry = y + 12;
            g.fill(x, ry, x + w, ry + 1, 0xFF6E5A50);
            int kx = x + Math.round((w - 1) * sliderValue(i));
            g.fill(x, ry - 1, kx, ry + 1, Da.GOLD);
            boolean hot = draggingSlider == i || Da.in(mx, my, x - 3, ry - 5, w + 6, 11);
            g.fill(kx - 2, ry - 4, kx + 3, ry + 4, Da.CREAM);
            Da.outline(g, kx - 2, ry - 4, 5, 8, hot ? Da.RED_HOV : Da.RED);
            if (hot) hover = 10 + i;
        }
        // filtres
        Da.text(g, f, "Filtre", sliderX(), swatchY() - 9, s, Da.MUTED, 0);
        for (int i = 0; i < SWATCH.length; i++) {
            int x = sliderX() + i * 20, y = swatchY();
            boolean sel = p.filter() == i, hov = Da.in(mx, my, x, y, 16, 16);
            g.fill(x, y, x + 16, y + 16, SWATCH[i]);
            g.fill(x + 1, y + 1, x + 15, y + 2, 0x40FFFFFF);
            Da.outline(g, x - (sel ? 1 : 0), y - (sel ? 1 : 0), 16 + (sel ? 2 : 0), 16 + (sel ? 2 : 0),
                sel ? Da.GOLD : hov ? Da.GOLD_D : 0xFF3C3234);
            if (hov) hover = 20 + i;
        }
        Da.text(g, f, PhotoMode.FILTER_LABELS[p.filter()], pX + PW / 2f, swatchY() + 22, s, Da.GOLD, 1);
        boolean fh = Da.in(mx, my, sliderX(), frameY(), sliderW(), 13);
        Da.plate(g, f, sliderX(), frameY(), sliderW(), 13, "Cadre : " + PhotoMode.FRAMES[p.frame()] + "  >",
            p.frame() > 0, fh, true);
        boolean rh = Da.in(mx, my, sliderX(), resetY(), sliderW(), 13);
        Da.plate(g, f, sliderX(), resetY(), sliderW(), 13, "Recentrer", false, rh, true);
        if (fh) hover = 30; if (rh) hover = 31;
        return hover;
    }

    private int drawBottom(GuiGraphicsExtractor g, Font f, int mx, int my, int hover) {
        // déclencheur
        int cx = shutterX(), cy = shutterY();
        boolean sh = dist(mx, my, cx, cy) <= 15;
        disc(g, cx + 1, cy + 2, 15, 0x70000000);
        disc(g, cx, cy, 15, Da.GOLD);
        disc(g, cx, cy, 13, sh ? Da.RED_HOV : Da.RED);
        disc(g, cx, cy, 6, Da.CREAM);
        if (sh) hover = 40;
        // dernières photos
        int y = this.height - 56;
        for (int i = 0; i < recent.size(); i++) {
            int x = 30 + i * 46;
            g.fill(x - 2, y - 2, x + 42, y + 24, 0xFFF4EEDC);
            ScreenshotTextures.Tex t = ScreenshotTextures.thumb(recent.get(i).path());
            if (t != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0f, 0f, 40, 22, t.w(), t.h(), t.w(), t.h());
            else g.fill(x, y, x + 40, y + 22, 0xFF2A2022);
        }
        return hover;
    }

    private static double dist(double x, double y, double cx, double cy) { return Math.hypot(x - cx, y - cy); }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int col) {
        for (int a = 0; a < 48; a++) {
            double t = a * Math.PI * 2 / 48;
            int x = cx + (int) Math.round(Math.cos(t) * r), y = cy + (int) Math.round(Math.sin(t) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    // ── tick : suivi du fichier capturé (cadre + bande) ─────────
    @Override
    public void tick() {
        if (captureAt > 0 && ++waitTicks % 5 == 0) {
            List<ScreenshotLibrary.Entry> l = ScreenshotLibrary.list(false);
            if (!l.isEmpty() && l.get(0).modified() >= captureAt - 2000) {
                long size;
                try { size = Files.size(l.get(0).path()); } catch (Exception e) { size = -1; }
                if (size > 0 && size == lastSize) {
                    PhotoFrames.applyAsync(l.get(0).path(), PhotoMode.INSTANCE.frame());
                    captureAt = 0;
                    recentTimer = PhotoMode.INSTANCE.frame() > 0 ? 30 : 1;
                }
                lastSize = size;
            }
            if (waitTicks > 200) captureAt = 0;
        }
        if (recentTimer > 0 && --recentTimer == 0) recent = latest();
    }

    // ── interactions ─────────────────────────────────────────────
    private void capture() {
        if (hideFrames > 0 || captureAt > 0) return;
        hideFrames = HIDE_FRAMES;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && !moving() && hideFrames == 0) {
            PhotoMode p = PhotoMode.INSTANCE;
            Minecraft mc = Minecraft.getInstance();
            for (int i = 0; i < SLIDERS.length; i++) {
                int ry = sliderY(i) + 12;
                if (Da.in(mx, my, sliderX() - 3, ry - 6, sliderW() + 6, 13)) {
                    draggingSlider = i; dragSlider(mx); return true;
                }
            }
            for (int i = 0; i < SWATCH.length; i++) {
                if (Da.in(mx, my, sliderX() + i * 20, swatchY(), 16, 16)) {
                    p.setFilter(i); RebornSounds.playReborn("photo.filter", 1f, 0.6f); return true;
                }
            }
            if (Da.in(mx, my, sliderX(), frameY(), sliderW(), 13)) { p.cycleFrame(); RebornSounds.playReborn("photo.filter", 0.9f, 0.6f); return true; }
            if (Da.in(mx, my, sliderX(), resetY(), sliderW(), 13)) { p.resetPosition(mc); RebornSounds.playReborn("esc.select", 1.1f, 0.35f); return true; }
            if (dist(mx, my, shutterX(), shutterY()) <= 15) { capture(); return true; }
            if (Da.in(mx, my, pX, pY, PW, PH)) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void dragSlider(double mx) {
        float before = sliderValue(draggingSlider);
        setSlider(draggingSlider, (float) ((mx - sliderX()) / (sliderW() - 1)));
        long now = System.currentTimeMillis();
        if (sliderValue(draggingSlider) != before && now - lastTick > 60) {
            RebornSounds.playReborn("photo.tick", 1f, 0.35f);
            lastTick = now;
        }
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider >= 0) { dragSlider(event.x()); return true; }
        if (event.button() == 0 && !Da.in(event.x(), event.y(), pX, pY, PW, PH)) {
            draggingView = true;
            PhotoMode.INSTANCE.rotate(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingView = false;
        draggingSlider = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v == 0) return false;
        PhotoMode p = PhotoMode.INSTANCE;
        if (hasShiftDown()) p.setRoll(p.roll() + (float) Math.signum(v));
        else p.setFov(p.fov() - (float) Math.signum(v) * 2f);
        long now = System.currentTimeMillis();
        if (now - lastTick > 60) { RebornSounds.playReborn("photo.tick", 1f, 0.3f); lastTick = now; }
        return true;
    }

    private static boolean hasShiftDown() {
        var w = Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private String quitKey() {
        if (HudKeybinds.PHOTO != null) return HudKeybinds.PHOTO.getTranslatedKeyMessage().getString();
        return "P";
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (HudKeybinds.PHOTO != null && HudKeybinds.PHOTO.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { capture(); return true; }
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
