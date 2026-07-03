package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import fr.reborn.hud.menu.settings.AccountTab;
import fr.reborn.hud.menu.settings.AudioTab;
import fr.reborn.hud.menu.settings.CameraTab;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.DiscordTab;
import fr.reborn.hud.menu.settings.InterfaceTab;
import fr.reborn.hud.menu.settings.MinecraftTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.VideoTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran Paramètres Reborn — refonte façon <b>Zenkai</b> : « ← Retour » en
 * haut-gauche, barre d'onglets <b>responsive</b> (Vidéo / Audio / Contrôles /
 * Caméra / Interface / Discord / Compte / Minecraft) qui se replie sur
 * plusieurs rangées centrées en petite fenêtre, puis le contenu de la catégorie
 * active dans une colonne centrée scrollable.
 *
 * <p>Chaque catégorie ne propose que des réglages <b>réellement câblés</b>
 * (client-side, via {@code mc.options} ou les managers Reborn). Toute
 * redirection vers les écrans vanilla est centralisée dans l'onglet
 * <b>Minecraft</b> (cf {@link MinecraftTab}).
 */
public class ConfigShellScreen extends Screen {

    private final Screen parent;
    private final CategoryDef[] categories;
    private int activeIdx;

    private static final Identifier LOGO = Identifier.of("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    private static final int RETURN_Y = 14;
    private static final int LOGO_Y = 10;
    private static final int TABBAR_Y = 62;
    private static final int TAB_H = 36;         // icône + label ArcadePix
    private static final int TAB_ICON = 16;
    private static final int TAB_PAD = 10;       // padding horizontal interne d'un onglet
    private static final int TAB_GAP = 4;
    private static final int TAB_ROW_GAP = 6;     // espace vertical entre 2 rangées d'onglets
    private static final int TABBAR_SIDE_PAD = 16; // marge latérale mini de la barre
    private static final int CONTENT_TOP_PAD = 22;
    private static final int MAX_CONTENT_W = 560;
    private static final int VIEWPORT_BOTTOM_PAD = 16;
    private static final int BOTTOM_MARGIN = 24;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_MIN_THUMB = 28;

    @FunctionalInterface
    private interface Icon {
        void draw(DrawContext ctx, int x, int y, int size, int color);
    }

    private record CategoryDef(String id, String label, Icon icon, SettingsTab tab) {}

    private int scrollY = 0;
    private List<ClickableWidget> contentWidgets = List.of();
    private int[] baseWidgetY = new int[0];

    private boolean draggingScrollbar = false;
    private int scrollbarGrabDy = 0;

    // Layout des onglets recalculé à l'init + à chaque frame (dépend de la
    // largeur écran) : la barre se replie sur plusieurs rangées centrées
    // quand elle déborde, pour rester lisible en petite fenêtre / GUI Scale 3.
    private int[] tabX = new int[0];
    private int[] tabY = new int[0];
    private int[] tabW = new int[0];
    private int tabRowCount = 1;

    public ConfigShellScreen(Screen parent) {
        this(parent, 0);
    }

    public ConfigShellScreen(Screen parent, int initialIdx) {
        super(Text.literal("Paramètres Reborn"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
        this.categories = new CategoryDef[] {
            new CategoryDef("video", "Vidéo", IconPack::monitor, new VideoTab(this)),
            new CategoryDef("audio", "Audio", IconPack::volume, new AudioTab()),
            new CategoryDef("controls", "Contrôles", IconPack::keyboard, new ControlsTab(this)),
            new CategoryDef("camera", "Caméra", IconPack::camera, new CameraTab(this)),
            new CategoryDef("interface", "Interface", IconPack::layout, new InterfaceTab(this)),
            new CategoryDef("discord", "Discord", IconPack::discord, new DiscordTab()),
            new CategoryDef("account", "Compte", IconPack::user, new AccountTab()),
            new CategoryDef("minecraft", "Minecraft", IconPack::cube, new MinecraftTab(this)),
        };
        this.activeIdx = Math.max(0, Math.min(categories.length - 1, initialIdx));
    }

    private SettingsTab activeTab() {
        return categories[activeIdx].tab;
    }

    // ─── Géométrie ───────────────────────────────────────
    private int tabBarHeight() {
        return tabRowCount * TAB_H + (tabRowCount - 1) * TAB_ROW_GAP;
    }
    private int viewportTop() { return TABBAR_Y + tabBarHeight() + 8; }
    private int viewportBottom() { return this.height - VIEWPORT_BOTTOM_PAD; }
    private int viewportH() { return Math.max(0, viewportBottom() - viewportTop()); }

    private int contentW() {
        return Math.max(240, Math.min(MAX_CONTENT_W, this.width - 80));
    }
    private int contentX() { return (this.width - contentW()) / 2; }
    private int contentTopBase() { return viewportTop() + CONTENT_TOP_PAD; }
    private int contentBottom() { return contentTopBase() + activeTab().height() + BOTTOM_MARGIN; }
    private int maxScroll() { return Math.max(0, contentBottom() - viewportBottom()); }
    private boolean hasScroll() { return maxScroll() > 0; }

    // ─── Onglets (barre responsive : repli multi-rangées centrées) ──
    /** Largeur d'un onglet = max(label ArcadePix, icône) + padding. */
    private int tabWidth(TextRenderer tr, String label) {
        int labelW = tr.getWidth(RebornFont.arcade(EscData.arcadeSafe(label, 20)));
        return Math.max(labelW, TAB_ICON) + TAB_PAD * 2;
    }

    /**
     * Répartit les onglets en rangées qui tiennent dans la largeur dispo
     * (packing gauche→droite), chaque rangée étant recentrée indépendamment.
     * Remplit {@link #tabX}/{@link #tabY}/{@link #tabW} + {@link #tabRowCount}.
     */
    private void computeTabLayout(TextRenderer tr) {
        int n = categories.length;
        if (tabX.length != n) {
            tabX = new int[n];
            tabY = new int[n];
            tabW = new int[n];
        }
        int avail = Math.max(120, this.width - 2 * TABBAR_SIDE_PAD);

        int[] w = new int[n];
        for (int i = 0; i < n; i++) w[i] = tabWidth(tr, categories[i].label);

        // Assignation gloutonne des rangées.
        int[] rowOf = new int[n];
        int row = 0, rowW = 0;
        for (int i = 0; i < n; i++) {
            int add = w[i] + (rowW > 0 ? TAB_GAP : 0);
            if (rowW > 0 && rowW + add > avail) {
                row++;
                rowW = 0;
                add = w[i];
            }
            rowOf[i] = row;
            rowW += add;
        }
        tabRowCount = row + 1;

        // Centrage par rangée.
        for (int r = 0; r < tabRowCount; r++) {
            int total = 0, cnt = 0;
            for (int i = 0; i < n; i++) {
                if (rowOf[i] == r) { total += w[i] + (cnt > 0 ? TAB_GAP : 0); cnt++; }
            }
            int cx = (this.width - total) / 2;
            for (int i = 0; i < n; i++) {
                if (rowOf[i] != r) continue;
                tabW[i] = w[i];
                tabX[i] = cx;
                tabY[i] = TABBAR_Y + r * (TAB_H + TAB_ROW_GAP);
                cx += w[i] + TAB_GAP;
            }
        }
    }

    @Override
    protected void init() {
        // La géométrie du contenu dépend de la hauteur de barre → calculer les
        // rangées d'onglets AVANT de layouter le contenu (et à chaque resize).
        computeTabLayout(this.textRenderer);
        rebuildContent();
    }

    private void rebuildContent() {
        for (ClickableWidget w : contentWidgets) {
            this.remove(w);
        }
        activeTab().layout(contentX(), contentTopBase(), contentW());
        contentWidgets = activeTab().widgets();
        baseWidgetY = new int[contentWidgets.size()];
        for (int i = 0; i < contentWidgets.size(); i++) {
            ClickableWidget w = contentWidgets.get(i);
            baseWidgetY[i] = w.getY();
            this.addSelectableChild(w);
        }
        clampScroll();
        applyScroll();
    }

    private void selectCategory(int idx) {
        if (idx == activeIdx) return;
        activeIdx = idx;
        scrollY = 0;
        rebuildContent();
    }

    private void applyScroll() {
        int top = viewportTop();
        int bottom = viewportBottom();
        for (int i = 0; i < contentWidgets.size(); i++) {
            ClickableWidget w = contentWidgets.get(i);
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
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Même fond que le menu ÉCHAP : un seul dégradé vertical doux.
        int bottomTint = Colors.lerp(Colors.BACKGROUND, Colors.ACCENT, 0.10f);
        DrawHelpers.verticalGradient(ctx, 0, 0, this.width, this.height, Colors.BACKGROUND, bottomTint);
        TextRenderer tr = this.textRenderer;
        computeTabLayout(tr);

        // Logo top-centre (comme l'ÉCHAP).
        int logoW = Math.min(Math.round(this.width * 0.10f), 132);
        int logoH = Math.round(logoW * (float) LOGO_TEX_H / LOGO_TEX_W);
        ctx.drawTexture(LOGO, (this.width - logoW) / 2, LOGO_Y, logoW, logoH,
            0f, 0f, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // « ‹ RETOUR » haut-gauche (hit-test dans mouseClicked).
        boolean backHover = overBack(mouseX, mouseY);
        ctx.drawText(tr, RebornFont.arcade("< RETOUR"), 16, RETURN_Y,
            backHover ? Colors.WHITE_PURE : Colors.FOREGROUND_MUTED, false);

        // Barre d'onglets responsive : icône au-dessus du label ArcadePix.
        for (int i = 0; i < categories.length; i++) {
            int x = tabX[i];
            int y = tabY[i];
            int w = tabW[i];
            boolean active = i == activeIdx;
            boolean hover = mouseX >= x && mouseX < x + w
                && mouseY >= y && mouseY < y + TAB_H;
            int bg = active ? Colors.ACCENT_SOFT : (hover ? Colors.SURFACE_ELEVATED : Colors.TRANSPARENT);
            if (bg != Colors.TRANSPARENT) {
                DrawHelpers.roundedRect(ctx, x, y, w, TAB_H, 8, bg);
            }
            int fg = active ? Colors.WHITE_PURE : (hover ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED);
            categories[i].icon.draw(ctx, x + (w - TAB_ICON) / 2, y + 4, TAB_ICON, fg);
            Text label = RebornFont.arcade(EscData.arcadeSafe(categories[i].label, 20));
            int lw = tr.getWidth(label);
            ctx.drawText(tr, label, x + (w - lw) / 2, y + 4 + TAB_ICON + 3, fg, false);
            if (active) {
                DrawHelpers.roundedRect(ctx, x + TAB_PAD, y + TAB_H - 2,
                    w - 2 * TAB_PAD, 2, 1, Colors.ACCENT);
            }
        }
        // Séparateur sous les onglets.
        ctx.fill(0, viewportTop() - 4, this.width, viewportTop() - 3, Colors.BORDER);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int contentX = contentX();
        int contentW = contentW();
        int contentYScrolled = contentTopBase() - scrollY;

        ctx.enableScissor(0, viewportTop(), this.width, viewportBottom());
        activeTab().renderPassive(ctx, contentX, contentYScrolled, contentW);
        for (ClickableWidget w : contentWidgets) {
            if (w.visible) {
                w.render(ctx, mouseX, mouseY, delta);
            }
        }
        ctx.disableScissor();

        renderScrollbar(ctx);
    }

    private void renderScrollbar(DrawContext ctx) {
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
        return Math.min(contentX() + contentW() + 12, this.width - SCROLLBAR_W - 6);
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
        return mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    private boolean overBack(double mouseX, double mouseY) {
        int w = this.textRenderer.getWidth(RebornFont.arcade("< RETOUR"));
        return mouseX >= 12 && mouseX <= 16 + w + 6 && mouseY >= RETURN_Y - 4 && mouseY <= RETURN_Y + 14;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // « ← Retour ».
            if (overBack(mouseX, mouseY)) {
                close();
                return true;
            }
            // Onglets (positions calculées par computeTabLayout, multi-rangées).
            for (int i = 0; i < categories.length && i < tabX.length; i++) {
                int x = tabX[i], y = tabY[i], w = tabW[i];
                if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + TAB_H) {
                    selectCategory(i);
                    return true;
                }
            }
            // Scrollbar.
            if (hasScroll() && overScrollbar(mouseX, mouseY)) {
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        RebornPrefs.INSTANCE.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
