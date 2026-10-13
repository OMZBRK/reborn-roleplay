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

    private static final String[] ACTIONS = { "Favori", "Retoucher", "Partager", "Ouvrir", "Supprimer", "Retour" };
    private static final int ACT_W = 66, ACT_H = 16, ACT_GAP = 6, STRIP_H = 30;

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
        Entry e = cur();
        boolean fav = ScreenshotLibrary.isFavorite(e.name());
        int hover = -1;

        // Plaques d'action.
        for (int i = 0; i < ACTIONS.length; i++) {
            String label = i == 0 ? (fav ? "En favori" : "Favori") : i == 2 && pending ? "Partagée" : ACTIONS[i];
            boolean act = (i == 0 && fav) || (i == 2 && pending);
            boolean hov = Da.in(mouseX, mouseY, actX(i), 8, ACT_W, ACT_H) && !confirm;
            if (hov) hover = i;
            fr.reborn.hud.menu.settings.ReglagesArt.segment(g, actX(i), 8, ACT_W, act ? 2 : hov ? 1 : 0);
            DaKit.inter8(g, f, DaKit.inter(label), actX(i) + ACT_W / 2f, 16,
                act || hov ? DaKit.CREME : i == 4 ? DaKit.ROUGE_CLAIR : DaKit.CREME_VAL, 1);
        }

        // Grande planche laquée + passe-partout de washi + image ajustée.
        int top = frameTop(), bottom = frameBottom();
        int maxW = Math.min(this.width - 120, 860), maxH = Math.min(bottom - top, 476);
        ScreenshotTextures.Tex t = ScreenshotTextures.full(e.path());
        ScreenshotTextures.Tex show = t != null ? t : ScreenshotTextures.thumb(e.path());
        float ar = show != null ? show.w() / (float) show.h() : 16f / 9f;
        int iw = Math.min(maxW - 32, Math.round((maxH - 40) * ar)), ih = Math.round(iw / ar);
        int fw = iw + 32, fh = ih + 40, fx = (this.width - fw) / 2, fy = top + (maxH - fh) / 2;
        GalerieArt.grand(g, fx, fy, fw, fh);
        int ix = fx + 16, iy = fy + 14;
        if (show != null) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, show.id(), ix, iy, 0f, 0f, iw, ih, show.w(), show.h(), show.w(), show.h());
        } else {
            g.fill(ix, iy, ix + iw, iy + ih, 0xFF2A2022);
        }
        Da.outline(g, ix - 1, iy - 1, iw + 2, ih + 2, 0xFF96826A);
        DaKit.inter7(g, f, DaKit.inter(GalleryScreen.dateOf(e)), ix + 1, iy + ih + 12, 0xFF64503C, 0);
        // sceau 撮 (celui du mode photo)
        DaKit.blit(g, DaKit.tex("photo", "cartouche"), fx + fw - 34, iy + ih + 3, 7, 7, 20, 20, 164, 38);
        DaKit.inter7(g, f, DaKit.inter((index + 1) + " / " + entries.size()), fx + fw, fy - 6, DaKit.DOUX, 2);

        // Flèches.
        int ay = top + maxH / 2 - 12;
        boolean lh = Da.in(mouseX, mouseY, 16, ay, 24, 24) && !confirm, rh = Da.in(mouseX, mouseY, this.width - 40, ay, 24, 24) && !confirm;
        GalerieArt.fleche(g, 16, ay, false, lh);
        GalerieArt.fleche(g, this.width - 40, ay, true, rh);
        if (lh) hover = 10;
        if (rh) hover = 11;

        // Pellicule : captures voisines.
        int stripY = this.height - STRIP_H - 20, n = Math.min(entries.size(), Math.max(3, (this.width - 60) / 62));
        int start = Math.max(0, Math.min(index - n / 2, entries.size() - n));
        int tw = 54, th = 22, total = n * (tw + 8) - 8, sx0 = (this.width - total) / 2;
        LotBlit film = LotBlit.ouvrir(g, GalerieArt.PELLICULE, 64, 32);
        for (int x = sx0 - 10; x < sx0 + total + 10; x += 64) film.blit(x, stripY, 0, 0, Math.min(64, sx0 + total + 10 - x), 30);
        film.fermer();
        LotGui cadres = LotGui.ouvrir(g);
        for (int k = 0; k < n; k++) {
            int i = start + k, x = sx0 + k * (tw + 8), y = stripY + 4;
            boolean hov = Da.in(mouseX, mouseY, x, y, tw, th) && !confirm;
            if (i == index) cadre(cadres, x - 2, y - 2, tw + 4, th + 4, DaKit.ROUGE);
            else if (hov) cadre(cadres, x - 1, y - 1, tw + 2, th + 2, DaKit.OR);
            if (hov) hover = 100 + i;
        }
        cadres.fermer();
        for (int k = 0; k < n; k++) {
            int i = start + k, x = sx0 + k * (tw + 8), y = stripY + 4;
            ScreenshotTextures.Tex tt = ScreenshotTextures.thumb(entries.get(i).path());
            if (tt != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tt.id(), x, y, 0f, 0f, tw, th, tt.w(), tt.h(), tt.w(), tt.h());
            else g.fill(x, y, x + tw, y + th, 0xFF2A2022);
        }
        if (hover != lastHover) { if (hover >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hover; }

        if (confirm) drawConfirm(g, f, mouseX, mouseY);
        DaKit.aides(g, f, fr.reborn.hud.menu.settings.ReglagesArt.PIECES, 0, 0, 120, 40,
            new String[][]{{"Flèches", "naviguer"}, {"Suppr", "supprimer"}, {"Échap", "retour"}}, this.width - 14,
            this.height - 8, 1f);
    }

    private static void cadre(LotGui l, int x, int y, int w, int h, int c) {
        l.fill(x, y, x + w, y + 1, c); l.fill(x, y + h - 1, x + w, y + h, c);
        l.fill(x, y, x + 1, y + h, c); l.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void drawConfirm(GuiGraphicsExtractor g, Font f, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        int w = 200, h = 64, x = (this.width - w) / 2, y = (this.height - h) / 2;
        GalerieArt.boite(g, x, y, w, h);
        DaKit.inter9(g, f, DaKit.inter("Supprimer cette capture ?"), x + w / 2f, y + 14, DaKit.CREME, 1);
        DaKit.inter7(g, f, DaKit.inter(GalleryScreen.dateOf(cur())), x + w / 2f, y + 26, DaKit.DOUX, 1);
        GalleryScreen.confirmerBoutons(g, f, x, y, w, mx, my);
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
