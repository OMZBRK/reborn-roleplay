package fr.reborn.hud.crosshair;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.config.CrosshairTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.widget.IconButton;
import fr.reborn.hud.menu.widget.RebornButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Éditeur de viseur dédié (pilier 2 — façon Custom Crosshair Mod), DA Reborn :
 * le monde reste visible ; aperçu live au centre d'une <b>cible de kyūdō</b> (mato)
 * sur pied laqué à gauche, réglages scrollables dans un panneau de laque noire
 * à liseré d'or à droite, plaque suspendue en en-tête, bouton Réinitialiser.
 * Ouvrable par keybind ({@code HudKeybinds}) et depuis le hub (catégorie
 * Viseur). Les réglages réutilisent {@link CrosshairTab}.
 */
public class CrosshairScreen extends Screen {

    private final Screen parent;
    private final CrosshairTab tab = new CrosshairTab();

    private static final int HEADER_H = 52;
    private static final int PAD = 24;
    private static final int GUTTER = 28;
    private static final int CONTENT_TOP_PAD = 36;
    private static final int VIEWPORT_BOTTOM_PAD = 12;
    private static final int BOTTOM_MARGIN = 24;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_MIN_THUMB = 28;

    private static final net.minecraft.resources.Identifier MATO =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn", "textures/gui/crosshair_editor/mato.png");
    private static final net.minecraft.resources.Identifier STAND =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn", "textures/gui/crosshair_editor/stand.png");
    private static final int GOLD = 0xFFF6CC78, GOLD_D = 0xFFAA8034, LACQ = 0xFF5C1418, CREAM = 0xFFFAEED6;

    private int scrollY = 0;
    private List<AbstractWidget> contentWidgets = List.of();
    private int[] baseWidgetY = new int[0];
    private boolean draggingScrollbar = false;
    private int scrollbarGrabDy = 0;

    public CrosshairScreen(Screen parent) {
        super(Component.literal("Éditeur de viseur"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
    }

    // ─── Géométrie ───
    private int previewX() { return PAD; }
    private int previewW() { return Math.max(180, Math.min(360, Math.round(this.width * 0.36f))); }
    private int previewY() { return HEADER_H + 20; }
    private int previewH() { return Math.max(120, this.height - previewY() - PAD); }

    private int contentX() { return previewX() + previewW() + GUTTER; }
    private int contentW() { return Math.max(200, this.width - contentX() - PAD); }
    private int viewportTop() { return HEADER_H + 1; }
    private int viewportBottom() { return this.height - VIEWPORT_BOTTOM_PAD; }
    private int viewportH() { return Math.max(0, viewportBottom() - viewportTop()); }
    private int contentTopBase() { return viewportTop() + CONTENT_TOP_PAD; }
    private int contentBottom() { return contentTopBase() + tab.height() + BOTTOM_MARGIN; }
    private int maxScroll() { return Math.max(0, contentBottom() - viewportBottom()); }
    private boolean hasScroll() { return maxScroll() > 0; }

    @Override
    protected void init() {
        // Fermer (top-right).
        this.addRenderableWidget(new IconButton(
            this.width - 18 - 16, (HEADER_H - 16) / 2, 16,
            IconPack::close, "Fermer", true,
            b -> onClose()
        ).ghost()
            .withIdleColor(Colors.FOREGROUND_MUTED)
            .withHoverColor(Colors.DANGER)
            .withTooltipPlacement(IconButton.TooltipPlacement.LEFT));

        // Réinitialiser (à gauche du X).
        this.addRenderableWidget(RebornButton.ghost(
            this.width - 18 - 16 - 8 - 96, (HEADER_H - 20) / 2, 96, 20,
            "Réinitialiser", b -> resetSettings()));

        rebuildContent();
    }

    private void rebuildContent() {
        for (AbstractWidget w : contentWidgets) {
            this.removeWidget(w);
        }
        tab.layout(contentX(), contentTopBase(), contentW());
        contentWidgets = tab.widgets();
        baseWidgetY = new int[contentWidgets.size()];
        for (int i = 0; i < contentWidgets.size(); i++) {
            AbstractWidget w = contentWidgets.get(i);
            baseWidgetY[i] = w.getY();
            this.addWidget(w);
        }
        clampScroll();
        applyScroll();
    }

    private void resetSettings() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.crosshairPreset = 0;
        p.crosshairScale = 100;
        p.crosshairColor = 0xFFFFFFFF;
        p.crosshairRainbow = false;
        p.crosshairDynamic = false;
        p.crosshairHitMarker = true;
        p.save();
        scrollY = 0;
        this.rebuildWidgets();
    }

    private void applyScroll() {
        int top = viewportTop();
        int bottom = viewportBottom();
        for (int i = 0; i < contentWidgets.size(); i++) {
            AbstractWidget w = contentWidgets.get(i);
            int y = baseWidgetY[i] - scrollY;
            w.setY(y);
            boolean outside = (y + w.getHeight() < top) || (y > bottom);
            w.visible = !outside;
            w.active = !outside;
        }
    }

    private void clampScroll() {
        scrollY = Math.max(0, Math.min(scrollY, maxScroll()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Le monde reste visible : voile léger, un peu plus dense derrière le panneau de réglages.
        ctx.fill(0, 0, this.width, this.height, 0x38080408);
        DrawHelpers.horizontalGradient(ctx, contentX() - 40, 0, this.width - contentX() + 40, this.height, 0x00000000, 0x70080408);
        // Plaque laquée suspendue (titre).
        int cx = this.width / 2, pw = 160, ph = 28, py = 8;
        for (int k = 0; k <= 8; k++) {
            ctx.fill(cx - 60 + k, k, cx - 59 + k, k + 1, 0xFFC8A05A);
            ctx.fill(cx + 59 - k, k, cx + 60 - k, k + 1, 0xFFC8A05A);
        }
        ctx.fill(cx - pw / 2 + 2, py + 3, cx + pw / 2 + 2, py + ph + 3, 0x80000000);
        ctx.fill(cx - pw / 2, py, cx + pw / 2, py + ph, LACQ);
        outline(ctx, cx - pw / 2, py, pw, ph, GOLD);
        outline(ctx, cx - pw / 2 + 2, py + 2, pw - 4, ph - 4, 0xFF963C32);
        Component t = RebornFont.arcade("VISEUR");
        ctx.text(this.font, t, cx - this.font.width(t) / 2, py + 5, CREAM, false);
        Component sub = RebornFont.arcade("APERCU EN DIRECT");
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - this.font.width(sub) * 0.75f / 2f, py + 17);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(this.font, sub, 0, 0, 0xFFE6B4A0, false);
        ctx.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // ─── Cible de kyūdō (gauche) : le viseur s'affiche en son centre ───
        int px = previewX(), py = previewY(), pw = previewW(), ph = previewH();
        int size = Math.min(pw - 20, ph - 70);
        size = Math.max(64, size - size % 32);                 // multiple de 32 : anneaux nets
        int tcx = px + pw / 2, tcy = py + 10 + size / 2;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, STAND, tcx - 32, tcy + size / 2 - 6, 0f, 0f, 64, 40, 64, 40);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MATO, tcx - size / 2, tcy - size / 2, 0f, 0f,
            size, size, 128, 128, 128, 128);
        CrosshairManager.drawPreview(ctx, tcx, tcy, 2.0f);
        Component hint = RebornFont.arcade("APERCU DU VISEUR");
        ctx.pose().pushMatrix();
        ctx.pose().translate(tcx - this.font.width(hint) * 0.7f / 2f, tcy + size / 2 + 40);
        ctx.pose().scale(0.7f, 0.7f);
        ctx.text(this.font, hint, 0, 0, 0xFFE6D2BE, false);
        ctx.pose().popMatrix();

        // ─── Panneau de réglages : laque noire, liseré d'or, coins dorés ───
        int cx0 = contentX() - 14, cy0 = viewportTop() + 14, cx1 = Math.min(this.width - 10, contentX() + contentW() + 18), cy1 = viewportBottom() - 4;
        ctx.fill(cx0 + 2, cy0 + 3, cx1 + 2, cy1 + 3, 0x70000000);
        ctx.fill(cx0, cy0, cx1, cy1, 0xE00E0A0C);
        outline(ctx, cx0, cy0, cx1 - cx0, cy1 - cy0, GOLD);
        kanagu(ctx, cx0, cy0, cx1 - cx0, cy1 - cy0);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        int contentYScrolled = contentTopBase() - scrollY;
        ctx.enableScissor(contentX() - 4, viewportTop() + 16, this.width, viewportBottom() - 6);
        tab.renderPassive(ctx, contentX(), contentYScrolled, contentW());
        for (AbstractWidget w : contentWidgets) {
            if (w.visible) {
                w.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
        ctx.disableScissor();

        renderScrollbar(ctx);
    }

    private static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c); ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void kanagu(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        int c = GOLD, s = 6;
        ctx.fill(x, y, x + s, y + 2, c); ctx.fill(x, y, x + 2, y + s, c);
        ctx.fill(x + w - s, y, x + w, y + 2, c); ctx.fill(x + w - 2, y, x + w, y + s, c);
        ctx.fill(x, y + h - 2, x + s, y + h, c); ctx.fill(x, y + h - s, x + 2, y + h, c);
        ctx.fill(x + w - s, y + h - 2, x + w, y + h, c); ctx.fill(x + w - 2, y + h - s, x + w, y + h, c);
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx) {
        if (!hasScroll()) return;
        int top = viewportTop();
        int vh = viewportH();
        int x = scrollbarX();
        DrawHelpers.roundedRect(ctx, x, top, SCROLLBAR_W, vh, SCROLLBAR_W / 2, Colors.SURFACE);
        int thumbH = thumbHeight();
        int thumbY = thumbY();
        int color = draggingScrollbar ? GOLD : GOLD_D;
        DrawHelpers.roundedRect(ctx, x, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W / 2, color);
    }

    private int scrollbarX() {
        return Math.min(contentX() + contentW() + 8, this.width - SCROLLBAR_W - 6);
    }

    private int thumbHeight() {
        int vh = viewportH();
        int totalH = Math.max(1, contentBottom() - viewportTop());
        return Math.max(SCROLLBAR_MIN_THUMB, (int) ((long) vh * vh / totalH));
    }

    private int thumbY() {
        int vh = viewportH();
        int thumbH = thumbHeight();
        int max = maxScroll();
        if (max <= 0) return viewportTop();
        int travel = vh - thumbH;
        return viewportTop() + (int) ((long) travel * scrollY / max);
    }

    private boolean inContentViewport(double mouseX, double mouseY) {
        return mouseX >= contentX() - 4 && mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (hasScroll() && inContentViewport(mouseX, mouseY)) {
            scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) (vAmount * 24)));
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        if (button == 0 && hasScroll() && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            int thumbY = thumbY();
            int thumbH = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                scrollbarGrabDy = (int) mouseY - thumbY;
            } else {
                scrollbarGrabDy = thumbH / 2;
                dragScrollbarTo(mouseY);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dX, double dY) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(event, dX, dY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int x = scrollbarX();
        return mouseX >= x - 4 && mouseX <= x + SCROLLBAR_W + 4
            && mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    private void dragScrollbarTo(double mouseY) {
        int vh = viewportH();
        int thumbH = thumbHeight();
        int travel = vh - thumbH;
        if (travel <= 0) return;
        int thumbTop = (int) mouseY - scrollbarGrabDy - viewportTop();
        thumbTop = Math.max(0, Math.min(travel, thumbTop));
        scrollY = (int) ((long) thumbTop * maxScroll() / travel);
        applyScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        RebornPrefs.INSTANCE.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
