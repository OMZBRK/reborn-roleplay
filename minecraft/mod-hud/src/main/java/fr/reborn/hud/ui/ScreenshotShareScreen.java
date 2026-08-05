package fr.reborn.hud.ui;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import fr.reborn.hud.screenshot.ShareQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Petit écran de partage : légende optionnelle + confirmation. Empile la
 * demande dans {@link ShareQueue} (le launcher fera l'upload réel vers
 * /v1/shots — le mod n'a pas le JWT). Pas d'appel réseau côté mod.
 */
public class ScreenshotShareScreen extends Screen {

    private final Screen parent;
    private final Entry entry;
    private EditBox captionField;
    private long doneAtMs = 0L; // > 0 quand la demande est empilée (feedback)

    private record Btn(String label, int x, int y, int w, int h, Runnable action) {}
    private final java.util.List<Btn> buttons = new java.util.ArrayList<>();

    public ScreenshotShareScreen(Screen parent, Entry entry) {
        super(Component.literal("Partager"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int fieldW = Math.min(320, this.width - 80);
        int fieldX = (this.width - fieldW) / 2;
        int fieldY = this.height / 2 + 20;
        captionField = new EditBox(this.textRenderer, fieldX, fieldY, fieldW, 16,
            Component.literal("caption"));
        captionField.setMaxLength(280);
        captionField.setHint(Component.literal("Légende (optionnelle)…"));
        this.addRenderableWidget(captionField);
        this.setInitialFocus(captionField);
    }

    private void confirmShare() {
        ShareQueue.enqueue(entry.name(), captionField.getText());
        doneAtMs = System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xE6000000);
        Font tr = this.textRenderer;

        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(tr, RebornFont.bold("Partager sur le feed"), cx, this.height / 2 - 90, Colors.GOLD);
        ctx.drawCenteredTextWithShadow(tr, Component.literal(entry.name()), cx, this.height / 2 - 74, Colors.FOREGROUND_MUTED);

        // Aperçu de l'image (fit dans une petite zone).
        int previewH = 96, previewW = 170;
        int px = cx - previewW / 2, py = this.height / 2 - 58;
        ScreenshotTextures.Tex t = ScreenshotTextures.get(entry.path());
        if (t != null && t.w() > 0 && t.h() > 0) {
            float scale = Math.min(previewW / (float) t.w(), previewH / (float) t.h());
            int dw = Math.round(t.w() * scale), dh = Math.round(t.h() * scale);
            int ix = cx - dw / 2, iy = py + (previewH - dh) / 2;
            ctx.fill(ix - 1, iy - 1, ix + dw + 1, iy + dh + 1, Colors.BORDER_STRONG);
            ctx.drawTexture(t.id(), ix, iy, dw, dh, 0f, 0f, t.w(), t.h(), t.w(), t.h());
        }

        // Label + champ légende (rendu explicite, comme l'éditeur — pas de
        // super.render qui repeindrait le fond par-dessus notre contenu).
        ctx.text(tr, Component.literal("Légende"), captionField.getX(), captionField.getY() - 12,
            Colors.FOREGROUND_SUBTLE, false);
        captionField.render(ctx, mouseX, mouseY, delta);

        // Boutons.
        buildButtons();
        for (Btn b : buttons) {
            boolean hov = in(mouseX, mouseY, b.x, b.y, b.w, b.h);
            boolean primary = b.label.startsWith("Partager");
            int bg = primary ? (hov ? Colors.ACCENT_HOVER : Colors.ACCENT) : (hov ? Colors.ACCENT_HOVER : Colors.BACKDROP_85);
            ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
            ctx.text(tr, Component.literal(b.label), b.x + (b.w - tr.width(b.label)) / 2, b.y + (b.h - 8) / 2,
                Colors.WHITE_PURE, false);
        }

        // Feedback après confirmation.
        if (doneAtMs > 0) {
            ctx.drawCenteredTextWithShadow(tr,
                Component.literal("✓ Ajouté — sera publié via le launcher"),
                cx, captionField.getY() + 44, 0xFF4ECE6F);
        }
    }

    private void buildButtons() {
        buttons.clear();
        int y = captionField.getY() + 26;
        int h = 18, gap = 8;
        int wShare = this.textRenderer.width("Partager") + 28;
        int wCancel = this.textRenderer.width("Annuler") + 28;
        int total = wShare + wCancel + gap;
        int x = (this.width - total) / 2;
        buttons.add(new Btn("Partager", x, y, wShare, h, this::confirmShare));
        buttons.add(new Btn("Annuler", x + wShare + gap, y, wCancel, h, this::close));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Btn b : buttons) {
            if (in((int) mx, (int) my, b.x, b.y, b.w, b.h)) { b.action().run(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (doneAtMs == 0) { confirmShare(); return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
