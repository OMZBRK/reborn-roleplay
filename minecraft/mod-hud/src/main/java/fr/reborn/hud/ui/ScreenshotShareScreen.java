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
        int fieldW = 248;
        int fieldX = (this.width - fieldW) / 2;
        int fieldY = boxY() + 146;
        captionField = new EditBox(this.font, fieldX + 4, fieldY + 3, fieldW - 8, 10,
            Component.literal("caption"));
        captionField.setBordered(false);
        captionField.setMaxLength(280);
        captionField.setHint(Component.literal("Légende (optionnelle)…"));
        this.addRenderableWidget(captionField);
        this.setInitialFocus(captionField);
    }

    private void confirmShare() {
        ShareQueue.enqueue(entry.name(), captionField.getValue());
        doneAtMs = System.currentTimeMillis();
    }

    private static final int BW = 300, BH = 214;

    private int boxX() { return (this.width - BW) / 2; }

    private int boxY() { return Math.max(26, (this.height - BH) / 2); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xD8080406);
        Font tr = this.font;
        int bx = boxX(), by = boxY(), cx = this.width / 2;
        GalerieArt.boite(ctx, bx, by, BW, BH);
        GalerieArt.cartouche(ctx, tr, cx, by - 18, "Partager", "sur le fil d'actualité");

        // Aperçu : tirage sur passe-partout de washi.
        int previewH = 96, previewW = 170;
        int py = by + 24;
        ScreenshotTextures.Tex t = ScreenshotTextures.get(entry.path());
        LotBlit mat = LotBlit.ouvrir(ctx, GalerieArt.TIRAGE, 192, 132);
        GalerieArt.tirage(mat, cx - previewW / 2 - 6, py - 6, previewW + 12, previewH + 12);
        mat.fermer();
        if (t != null && t.w() > 0 && t.h() > 0) {
            float scale = Math.min(previewW / (float) t.w(), previewH / (float) t.h());
            int dw = Math.round(t.w() * scale), dh = Math.round(t.h() * scale);
            int ix = cx - dw / 2, iy = py + (previewH - dh) / 2;
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, t.id(), ix, iy, 0f, 0f, dw, dh, t.w(), t.h(), t.w(), t.h());
        }
        DaKit.inter7(ctx, tr, DaKit.inter(GalleryScreen.dateOf(entry)), cx, py + previewH + 14, DaKit.DOUX, 1);

        // Légende : champ laqué, texte de l'EditBox.
        int fieldX = cx - 124, fieldY = by + 146;
        DaKit.arcFin(ctx, tr, DaKit.arcade("Légende"), fieldX, fieldY - 8, DaKit.OR_ETIQ, 0, false);
        DaKit.coupeH(ctx, DaKit.tex("tablist", "champ"), fieldX - 1, fieldY - 1, 250, 0, 0, 260, 18, 6, 260, 18);
        captionField.extractRenderState(ctx, mouseX, mouseY, delta);
        if (captionField.getValue().isEmpty() && !captionField.isFocused()) {
            DaKit.inter7(ctx, tr, DaKit.inter("Légende (facultative)…"), fieldX + 5, fieldY + 7, DaKit.INACTIF, 0);
        }

        // Boutons.
        buildButtons();
        for (Btn b : buttons) {
            boolean hov = in(mouseX, mouseY, b.x, b.y, b.w, b.h);
            boolean primary = b.label.startsWith("Partager");
            fr.reborn.hud.menu.settings.ReglagesArt.segment(ctx, b.x, b.y, b.w, primary ? 2 : hov ? 1 : 0);
            DaKit.inter8(ctx, tr, DaKit.inter(b.label), b.x + b.w / 2f, b.y + 8, primary || hov ? DaKit.CREME : DaKit.CREME_VAL, 1);
        }

        // Retour après confirmation.
        if (doneAtMs > 0) {
            DaKit.inter8(ctx, tr, DaKit.inter("Ajoutée — elle sera publiée par le launcher"), cx, by + BH + 12, 0xFF7CAA94, 1);
        }
    }

    private void buildButtons() {
        buttons.clear();
        int y = boxY() + 176;
        int h = 16, gap = 8, w = 96;
        int x = (this.width - 2 * w - gap) / 2;
        buttons.add(new Btn("Partager", x, y, w, h, this::confirmShare));
        buttons.add(new Btn("Annuler", x + w + gap, y, w, h, this::onClose));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        for (Btn b : buttons) {
            if (in((int) mx, (int) my, b.x, b.y, b.w, b.h)) { b.action().run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (doneAtMs == 0) { confirmShare(); return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
