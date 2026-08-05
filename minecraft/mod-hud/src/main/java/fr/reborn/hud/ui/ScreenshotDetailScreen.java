package fr.reborn.hud.ui;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.screenshot.ScreenshotLibrary;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Vue détail d'un screenshot : image en grand (fit) + barre d'actions
 * (précédent/suivant, favori, éditer, ouvrir, supprimer, retour). Navigable au
 * clavier (←/→, Échap).
 */
public class ScreenshotDetailScreen extends Screen {

    private final Screen parent;
    private final List<Entry> entries;
    private int index;

    private record Btn(String label, int x, int y, int w, Runnable action) {}
    private java.util.List<Btn> buttons = new java.util.ArrayList<>();

    public ScreenshotDetailScreen(Screen parent, List<Entry> entries, int index) {
        super(Component.literal("Screenshot"));
        this.parent = parent;
        this.entries = entries;
        this.index = index;
    }

    private Entry cur() { return entries.get(index); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xE6000000);
        Font tr = this.font;
        Entry e = cur();

        // Nom + position.
        ctx.text(tr, RebornFont.bold(e.name()), 12, 10, Colors.GOLD, false);
        String pos = (index + 1) + " / " + entries.size();
        ctx.text(tr, Component.literal(pos), this.width - tr.width(pos) - 12, 10, Colors.FOREGROUND_MUTED, false);

        // Image (fit).
        int top = 28, bottom = this.height - 40;
        int availW = this.width - 24, availH = bottom - top;
        ScreenshotTextures.Tex t = ScreenshotTextures.get(e.path());
        if (t != null && t.w() > 0 && t.h() > 0) {
            float scale = Math.min(availW / (float) t.w(), availH / (float) t.h());
            int dw = Math.round(t.w() * scale), dh = Math.round(t.h() * scale);
            int ix = (this.width - dw) / 2, iy = top + (availH - dh) / 2;
            ctx.fill(ix - 1, iy - 1, ix + dw + 1, iy + dh + 1, Colors.BORDER_STRONG);
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, t.id(), ix, iy, 0f, 0f, dw, dh, t.w(), t.h());
        } else {
            ctx.text(tr, Component.literal("Image illisible"), this.width / 2 - 30, this.height / 2, Colors.FOREGROUND_MUTED, false);
        }

        // Barre d'actions.
        buildButtons();
        for (Btn b : buttons) {
            boolean hov = in(mouseX, mouseY, b.x, b.y, b.w, 16);
            ctx.fill(b.x, b.y, b.x + b.w, b.y + 16, hov ? Colors.ACCENT_HOVER : Colors.BACKDROP_85);
            ctx.text(tr, Component.literal(b.label), b.x + (b.w - tr.width(b.label)) / 2, b.y + 4,
                hov ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
        }
    }

    private void buildButtons() {
        buttons.clear();
        Entry e = cur();
        boolean fav = ScreenshotLibrary.isFavorite(e.name());
        boolean pending = fr.reborn.hud.screenshot.ShareQueue.isPending(e.name());
        String[][] defs = {
            {"◀"}, {fav ? "♥ Favori" : "♡ Favori"}, {"Éditer"},
            {pending ? "✓ Partagé" : "Partager"}, {"Ouvrir"}, {"Supprimer"}, {"Retour"}, {"▶"},
        };
        Runnable[] acts = {
            () -> nav(-1),
            () -> { ScreenshotLibrary.toggleFavorite(e.name()); },
            () -> openEditor(),
            this::openShare,
            () -> Util.getPlatform().openUri(e.path().toUri()),
            () -> deleteCurrent(),
            this::onClose,
            () -> nav(1),
        };
        int y = this.height - 30;
        int totalW = 0, gap = 6;
        int[] ws = new int[defs.length];
        for (int i = 0; i < defs.length; i++) { ws[i] = this.font.width(defs[i][0]) + 16; totalW += ws[i]; }
        totalW += gap * (defs.length - 1);
        int x = (this.width - totalW) / 2;
        for (int i = 0; i < defs.length; i++) {
            buttons.add(new Btn(defs[i][0], x, y, ws[i], acts[i]));
            x += ws[i] + gap;
        }
    }

    private void openEditor() {
        Minecraft.getInstance().setScreen(new ScreenshotEditorScreen(this, cur()));
    }

    private void openShare() {
        Minecraft.getInstance().setScreen(new ScreenshotShareScreen(this, cur()));
    }

    private void nav(int d) {
        if (entries.isEmpty()) return;
        index = ((index + d) % entries.size() + entries.size()) % entries.size();
    }

    private void deleteCurrent() {
        ScreenshotLibrary.delete(cur());
        entries.remove(index);
        if (entries.isEmpty()) { onClose(); return; }
        if (index >= entries.size()) index = entries.size() - 1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        for (Btn b : buttons) {
            if (in((int) mx, (int) my, b.x, b.y, b.w, 16)) { b.action().run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        if (keyCode == GLFW.GLFW_KEY_LEFT) { nav(-1); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { nav(1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
