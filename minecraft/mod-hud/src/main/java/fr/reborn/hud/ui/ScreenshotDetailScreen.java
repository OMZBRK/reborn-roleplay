package fr.reborn.hud.ui;

import fr.reborn.hud.menu.RebornSounds;
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
 * Vue détail d'un screenshot — DA Reborn : le tirage sous passe-partout dans un cadre laqué,
 * plaques d'action en haut (favori, éditer, partager, ouvrir, supprimer, retour), flèches ‹ ›,
 * bande de film des captures voisines en bas (clic = y aller). Navigation ←/→, Échap = retour.
 *
 * <p>Corrigé : image complète chargée en arrière-plan (la vignette s'affiche en attendant) ;
 * l'état « partagé » n'est plus relu sur disque à chaque image ; suppression confirmée.
 */
public class ScreenshotDetailScreen extends Screen {

    private static final String[] ACTIONS = { "Favori", "Editer", "Partager", "Ouvrir", "Supprimer", "Retour" };
    private static final int ACT_W = 66, ACT_H = 14, ACT_GAP = 6, STRIP_H = 30;

    private final Screen parent;
    private final List<Entry> entries;
    private int index;
    private boolean pending;          // état « partagé » de la capture courante (lu une fois par capture)
    private boolean confirm = false;
    private int lastHover = -1;

    public ScreenshotDetailScreen(Screen parent, List<Entry> entries, int index) {
        super(Component.literal("Screenshot"));
        this.parent = parent;
        this.entries = entries;
        this.index = index;
    }

    @Override
    protected void init() { refreshState(); }

    private Entry cur() { return entries.get(index); }

    private void refreshState() {
        pending = !entries.isEmpty() && fr.reborn.hud.screenshot.ShareQueue.isPending(cur().name());
    }

    // ── géométrie ────────────────────────────────────────────────
    private int actX(int i) {
        int total = ACTIONS.length * ACT_W + (ACTIONS.length - 1) * ACT_GAP;
        return (this.width - total) / 2 + i * (ACT_W + ACT_GAP);
    }
    private int frameTop() { return 30; }
    private int frameBottom() { return this.height - STRIP_H - 26; }

    // ── rendu ────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xE0080406);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (entries.isEmpty()) { onClose(); return; }
        Font f = this.font;
        float s = Da.small();
        Entry e = cur();
        boolean fav = ScreenshotLibrary.isFavorite(e.name());
        int hover = -1;

        // Plaques d'action.
        for (int i = 0; i < ACTIONS.length; i++) {
            String label = i == 0 ? (fav ? "Favori" : "Favori +") : i == 2 && pending ? "Partage" : ACTIONS[i];
            boolean act = (i == 0 && fav) || (i == 2 && pending);
            boolean hov = Da.in(mouseX, mouseY, actX(i), 8, ACT_W, ACT_H) && !confirm;
            if (hov) hover = i;
            Da.plate(g, f, actX(i), 8, ACT_W, ACT_H, label, act, hov, true);
        }

        // Cadre laqué + passe-partout + image ajustée.
        int top = frameTop(), bottom = frameBottom();
        int maxW = this.width - 120, maxH = bottom - top;
        ScreenshotTextures.Tex t = ScreenshotTextures.full(e.path());
        ScreenshotTextures.Tex show = t != null ? t : ScreenshotTextures.thumb(e.path());
        float ar = show != null ? show.w() / (float) show.h() : 16f / 9f;
        int iw = Math.min(maxW - 32, Math.round((maxH - 40) * ar)), ih = Math.round(iw / ar);
        int fw = iw + 32, fh = ih + 40, fx = (this.width - fw) / 2, fy = top + (maxH - fh) / 2;
        Da.panel(g, fx, fy, fw, fh);
        g.fill(fx + 6, fy + 6, fx + fw - 6, fy + fh - 6, 0xFFF0E8D6);
        int ix = fx + 16, iy = fy + 14;
        if (show != null) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, show.id(), ix, iy, 0f, 0f, iw, ih, show.w(), show.h(), show.w(), show.h());
        } else {
            g.fill(ix, iy, ix + iw, iy + ih, 0xFF2A2022);
        }
        Da.outline(g, ix - 1, iy - 1, iw + 2, ih + 2, 0xFF96826A);
        Da.text(g, f, GalleryScreen.dateOf(e), ix + 1, iy + ih + 8, s, 0xFF64503C, 0);
        // sceau 撮
        int sx = fx + fw - 30, sy = iy + ih + 4;
        g.fill(sx, sy, sx + 14, sy + 14, Da.RED);
        Da.outline(g, sx, sy, 14, 14, 0xFF781418);
        g.text(f, Component.literal("撮"), sx + 3, sy + 3, Da.CREAM, false);
        Da.text(g, f, (index + 1) + " / " + entries.size(), fx + fw, fy - 9, s, Da.MUTED, 2);

        // Flèches.
        int ay = top + maxH / 2 - 12;
        boolean lh = Da.in(mouseX, mouseY, 16, ay, 24, 24) && !confirm, rh = Da.in(mouseX, mouseY, this.width - 40, ay, 24, 24) && !confirm;
        Da.plate(g, f, 16, ay, 24, 24, "<", false, lh, true);
        Da.plate(g, f, this.width - 40, ay, 24, 24, ">", false, rh, true);
        if (lh) hover = 10; if (rh) hover = 11;

        // Bande de film : captures voisines.
        int stripY = this.height - STRIP_H - 20, n = Math.min(entries.size(), Math.max(3, (this.width - 60) / 62));
        int start = Math.max(0, Math.min(index - n / 2, entries.size() - n));
        int tw = 54, th = 22, total = n * (tw + 8) - 8, sx0 = (this.width - total) / 2;
        g.fill(sx0 - 10, stripY, sx0 + total + 10, stripY + STRIP_H, 0xFF100C0E);
        for (int x = sx0 - 8; x < sx0 + total + 8; x += 8) {
            g.fill(x, stripY + 2, x + 4, stripY + 5, 0xFF3C3234);
            g.fill(x, stripY + STRIP_H - 5, x + 4, stripY + STRIP_H - 2, 0xFF3C3234);
        }
        for (int k = 0; k < n; k++) {
            int i = start + k, x = sx0 + k * (tw + 8), y = stripY + 4;
            ScreenshotTextures.Tex tt = ScreenshotTextures.thumb(entries.get(i).path());
            if (tt != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tt.id(), x, y, 0f, 0f, tw, th, tt.w(), tt.h(), tt.w(), tt.h());
            else g.fill(x, y, x + tw, y + th, 0xFF2A2022);
            boolean hov = Da.in(mouseX, mouseY, x, y, tw, th) && !confirm;
            if (i == index) Da.outline(g, x - 2, y - 2, tw + 4, th + 4, Da.RED);
            else if (hov) Da.outline(g, x - 1, y - 1, tw + 2, th + 2, Da.GOLD);
            if (hov) hover = 100 + i;
        }
        if (hover != lastHover) { if (hover >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hover; }

        if (confirm) drawConfirm(g, f, mouseX, mouseY);
        Da.hint(g, f, this.width, this.height, "Fleches : naviguer     Suppr : supprimer     Echap : retour");
    }

    private void drawConfirm(GuiGraphicsExtractor g, Font f, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        int w = 200, h = 64, x = (this.width - w) / 2, y = (this.height - h) / 2;
        Da.panel(g, x, y, w, h);
        Da.text(g, f, "Supprimer cette capture ?", x + w / 2f, y + 12, Da.small(), Da.CREAM, 1);
        Da.text(g, f, GalleryScreen.dateOf(cur()), x + w / 2f, y + 22, Da.small(), Da.MUTED, 1);
        Da.plate(g, f, x + 20, y + 38, 74, 15, "Annuler", false, Da.in(mx, my, x + 20, y + 38, 74, 15), true);
        Da.plate(g, f, x + w - 94, y + 38, 74, 15, "Supprimer", true, Da.in(mx, my, x + w - 94, y + 38, 74, 15), true);
    }

    // ── actions ──────────────────────────────────────────────────
    private void nav(int d) {
        if (entries.isEmpty()) return;
        index = ((index + d) % entries.size() + entries.size()) % entries.size();
        refreshState();
        RebornSounds.playReborn("esc.page", 1.1f, 0.3f);
    }

    private void deleteCurrent() {
        ScreenshotLibrary.delete(cur());
        entries.remove(index);
        RebornSounds.playReborn("sacoche.deny", 0.9f, 0.4f);
        if (entries.isEmpty()) { onClose(); return; }
        if (index >= entries.size()) index = entries.size() - 1;
        refreshState();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        if (confirm) {
            int w = 200, h = 64, x = (this.width - w) / 2, y = (this.height - h) / 2;
            if (Da.in(mx, my, x + w - 94, y + 38, 74, 15)) deleteCurrent();
            confirm = false;
            return true;
        }
        for (int i = 0; i < ACTIONS.length; i++) {
            if (!Da.in(mx, my, actX(i), 8, ACT_W, ACT_H)) continue;
            click();
            switch (i) {
                case 0 -> ScreenshotLibrary.toggleFavorite(cur().name());
                case 1 -> Minecraft.getInstance().setScreenAndShow(new ScreenshotEditorScreen(this, cur()));
                case 2 -> Minecraft.getInstance().setScreenAndShow(new ScreenshotShareScreen(this, cur()));
                case 3 -> Util.getPlatform().openUri(cur().path().toUri());
                case 4 -> confirm = true;
                default -> onClose();
            }
            return true;
        }
        int ay = frameTop() + (frameBottom() - frameTop()) / 2 - 12;
        if (Da.in(mx, my, 16, ay, 24, 24)) { nav(-1); return true; }
        if (Da.in(mx, my, this.width - 40, ay, 24, 24)) { nav(1); return true; }
        int stripY = this.height - STRIP_H - 20, n = Math.min(entries.size(), Math.max(3, (this.width - 60) / 62));
        int start = Math.max(0, Math.min(index - n / 2, entries.size() - n));
        int tw = 54, total = n * (tw + 8) - 8, sx0 = (this.width - total) / 2;
        for (int k = 0; k < n; k++) {
            if (Da.in(mx, my, sx0 + k * (tw + 8), stripY + 4, tw, 22)) { index = start + k; refreshState(); click(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (confirm) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { confirm = false; return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirm = false; deleteCurrent(); return true; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) { nav(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { nav(1); return true; }
        if (key == GLFW.GLFW_KEY_DELETE) { confirm = true; return true; }
        return super.keyPressed(event);
    }

    private static void click() { RebornSounds.playReborn("esc.select", 1.1f, 0.4f); }

    @Override
    public void onClose() {
        ScreenshotTextures.releaseFull();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
