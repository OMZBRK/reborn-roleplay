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
 * Éditeur de viseur dédié (pilier 2 — façon Custom Crosshair Mod) : aperçu
 * live à gauche, réglages scrollables à droite, bouton Réinitialiser.
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
        ctx.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
        ctx.fill(0, 0, this.width, HEADER_H, Colors.SURFACE);
        ctx.fill(0, HEADER_H, this.width, HEADER_H + 1, Colors.BORDER);

        Component title = RebornFont.bold("ÉDITEUR DE VISEUR");
        ctx.pose().pushMatrix();
        ctx.pose().translate(20, (HEADER_H - 14) / 2f);
        ctx.pose().scale(1.2f, 1.2f);
        ctx.text(this.font, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // ─── Panneau d'aperçu (gauche) ───
        int px = previewX(), py = previewY(), pw = previewW(), ph = previewH();
        DrawHelpers.roundedOutlinedRect(ctx, px, py, pw, ph, 10,
            Colors.BACKGROUND, Colors.BORDER_STRONG);
        // Damier subtil pour juger la lisibilité sur fond clair/sombre.
        drawCheckerboard(ctx, px + 1, py + 1, pw - 2, ph - 2);
        ctx.text(this.font, RebornFont.bold("APERÇU"),
            px + 12, py + 10, Colors.FOREGROUND_SUBTLE, false);
        // Viseur centré (×2 pour bien le voir).
        CrosshairManager.drawPreview(ctx, px + pw / 2, py + ph / 2, 2.0f);

        // ─── Réglages (droite, scrollable) ───
        int contentYScrolled = contentTopBase() - scrollY;
        ctx.enableScissor(contentX() - 4, viewportTop(), this.width, viewportBottom());
        tab.renderPassive(ctx, contentX(), contentYScrolled, contentW());
        for (AbstractWidget w : contentWidgets) {
            if (w.visible) {
                w.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
        ctx.disableScissor();

        renderScrollbar(ctx);
    }

    private void drawCheckerboard(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        // Fond plein (1 fill) puis SEULEMENT les cellules alternées → ~2× moins
        // de fills (le mode retained 26.1 pénalise chaque fill).
        int cell = 12;
        ctx.fill(x, y, x + w, y + h, Colors.SURFACE_ELEVATED);
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = 0; xx < w; xx += cell) {
                if (((xx / cell) + (yy / cell)) % 2 != 0) continue;
                ctx.fill(x + xx, y + yy,
                    x + Math.min(xx + cell, w), y + Math.min(yy + cell, h), Colors.SURFACE);
            }
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx) {
        if (!hasScroll()) return;
        int top = viewportTop();
        int vh = viewportH();
        int x = scrollbarX();
        DrawHelpers.roundedRect(ctx, x, top, SCROLLBAR_W, vh, SCROLLBAR_W / 2, Colors.SURFACE);
        int thumbH = thumbHeight();
        int thumbY = thumbY();
        int color = draggingScrollbar ? Colors.ACCENT : Colors.BORDER_STRONG;
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
