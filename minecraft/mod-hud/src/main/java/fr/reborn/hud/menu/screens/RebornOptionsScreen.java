package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.widget.IconButton;
import fr.reborn.hud.menu.settings.AccountTab;
import fr.reborn.hud.menu.settings.AudioTab;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.DiscordTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.TabButton;
import fr.reborn.hud.menu.settings.VideoTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Screen Paramètres Reborn — refonte v2 avec 5 tabs horizontaux.
 * Référence : {@code settings.jsx::SettingsScreen}.
 *
 * <p>Switch de tab via clearAndInit() — re-build complet de l'écran.
 *
 * <h2>Scroll</h2>
 * Le contenu du tab actif vit dans un <b>viewport scrollable</b> situé
 * entre la barre de tabs (haut) et le bas de l'écran. Le header (titre,
 * retour, fermer) et la barre de tabs restent fixes.
 *
 * <p>Mécanique : les widgets cliquables du tab sont enregistrés via
 * {@code addSelectableChild} (input only, pas de rendu auto par le parent)
 * puis rendus à la main dans un scissor borné au viewport, à une position Y
 * décalée de {@code -scrollY}. Les éléments passifs (labels/banners) sont
 * rendus par {@code renderPassive} avec le même décalage. Le clamp de scroll
 * utilise la hauteur réelle remontée par {@link SettingsTab#height()}.
 *
 * <p>Cette approche (vs addDrawableChild) est nécessaire pour clipper
 * uniquement le contenu — {@code super.render()} dessine tous les drawables
 * d'un bloc, sans moyen de scissor le seul contenu.
 */
public class RebornOptionsScreen extends Screen {

    private final Screen parent;
    private final TabDef[] tabs;
    private int activeTabIdx;
    private SettingsTab activeTab;

    private static final int HEADER_H = 56;
    private static final int TABS_H = 36;
    private static final int CONTENT_TOP_PADDING = 36;

    /** Colonne de contenu centrée, largeur max — évite l'étirement plein écran. */
    private static final int MAX_CONTENT_W = 620;
    private static final int SIDE_PAD = 28;
    /** Marge sous le dernier élément avant la fin de la zone scrollable. */
    private static final int BOTTOM_MARGIN = 24;
    /** Padding bas du viewport (entre le contenu clippé et le bord écran). */
    private static final int VIEWPORT_BOTTOM_PAD = 12;

    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_MIN_THUMB = 28;

    /** Offset de scroll courant (px), 0 = haut. */
    private int scrollY = 0;
    /** Widgets cliquables du tab actif (rendus/scrollés à la main). */
    private List<ClickableWidget> contentWidgets = List.of();
    /** Y de référence (scroll = 0) de chaque widget, parallèle à contentWidgets. */
    private int[] baseWidgetY = new int[0];

    private boolean draggingScrollbar = false;
    /** Décalage Y entre le clic et le haut du thumb au début du drag. */
    private int scrollbarGrabDy = 0;

    private record TabDef(String id, String label, SettingsTab tab) {}

    public RebornOptionsScreen(Screen parent) {
        this(parent, 0);
    }

    public RebornOptionsScreen(Screen parent, int initialTabIdx) {
        super(Text.literal("Paramètres Reborn"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
        this.tabs = new TabDef[] {
            new TabDef("video", "Vidéo", new VideoTab(this)),
            new TabDef("audio", "Audio", new AudioTab()),
            new TabDef("controls", "Contrôles", new ControlsTab(this)),
            new TabDef("discord", "Discord", new DiscordTab()),
            new TabDef("account", "Compte", new AccountTab()),
        };
        this.activeTabIdx = Math.max(0, Math.min(tabs.length - 1, initialTabIdx));
        this.activeTab = tabs[this.activeTabIdx].tab;
    }

    // ────────────────────────────────────────────────────────
    // Géométrie viewport / colonne de contenu
    // ────────────────────────────────────────────────────────

    private int contentW() {
        return Math.min(MAX_CONTENT_W, this.width - 2 * SIDE_PAD);
    }

    private int contentX() {
        return (this.width - contentW()) / 2;
    }

    /** Haut de la zone scrollable (juste sous le séparateur des tabs). */
    private int viewportTop() {
        return HEADER_H + TABS_H + 2;
    }

    private int viewportBottom() {
        return this.height - VIEWPORT_BOTTOM_PAD;
    }

    private int viewportH() {
        return Math.max(0, viewportBottom() - viewportTop());
    }

    /** Y de base (scroll 0) passé à layout()/renderPassive() comme origine du contenu. */
    private int contentTopBase() {
        return viewportTop() + CONTENT_TOP_PADDING;
    }

    /** Bas absolu du contenu (scroll 0), marge incluse. */
    private int contentBottom() {
        return contentTopBase() + activeTab.height() + BOTTOM_MARGIN;
    }

    private int maxScroll() {
        return Math.max(0, contentBottom() - viewportBottom());
    }

    private boolean hasScroll() {
        return maxScroll() > 0;
    }

    @Override
    protected void init() {
        // ─── Header : bouton retour gauche + X close droite ───
        this.addDrawableChild(new IconButton(
            18, 18, 18,
            IconPack::chevronLeft, "Retour", false,
            b -> close()
        ).ghost().withIdleColor(Colors.FOREGROUND_SUBTLE));

        this.addDrawableChild(new IconButton(
            this.width - 18 - 16, 18, 16,
            IconPack::close, "Fermer", true,
            b -> close()
        ).ghost()
            .withIdleColor(Colors.FOREGROUND_MUTED)
            .withHoverColor(Colors.DANGER)
            .withTooltipPlacement(IconButton.TooltipPlacement.LEFT));

        // ─── Tabs horizontaux ───
        int totalTabsW = Math.min(this.width - 80, 540);
        int tabW = totalTabsW / tabs.length;
        int startX = (this.width - totalTabsW) / 2;
        int tabsY = HEADER_H;

        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            this.addDrawableChild(new TabButton(
                startX + i * tabW, tabsY, tabW, TABS_H,
                tabs[i].label,
                () -> idx == activeTabIdx,
                b -> {
                    if (idx != activeTabIdx) {
                        activeTabIdx = idx;
                        activeTab = tabs[idx].tab;
                        scrollY = 0;
                        this.clearAndInit();
                    }
                }
            ));
        }

        // ─── Layout du tab actif (positions NON scrollées = référence) ───
        int contentX = contentX();
        int contentW = contentW();
        activeTab.layout(contentX, contentTopBase(), contentW);

        // Widgets cliquables : input only (pas de rendu auto) — on les
        // dessine à la main dans le scissor du viewport.
        contentWidgets = activeTab.widgets();
        baseWidgetY = new int[contentWidgets.size()];
        for (int i = 0; i < contentWidgets.size(); i++) {
            ClickableWidget w = contentWidgets.get(i);
            baseWidgetY[i] = w.getY();
            this.addSelectableChild(w);
        }

        clampScroll();
        applyScroll();
    }

    /** Repositionne les widgets selon scrollY et masque ceux hors viewport. */
    private void applyScroll() {
        int top = viewportTop();
        int bottom = viewportBottom();
        for (int i = 0; i < contentWidgets.size(); i++) {
            ClickableWidget w = contentWidgets.get(i);
            int y = baseWidgetY[i] - scrollY;
            w.setY(y);
            // Hors viewport (totalement) → invisible : pas de rendu, pas de clic
            // fantôme sous le header.
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
        ctx.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dessine fond + header fixes (retour/X) + tabs.
        super.render(ctx, mouseX, mouseY, delta);

        var tr = MinecraftClient.getInstance().textRenderer;

        // ─── Titre centré ───
        Text title = RebornFont.bold("PARAMÈTRES REBORN");
        float titleScale = 1.4f;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = (this.width - titleW) / 2;
        int titleY = 18;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1f);
        ctx.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // ─── Séparations ───
        ctx.fill(40, HEADER_H - 1, this.width - 40, HEADER_H, Colors.BORDER);
        ctx.fill(40, HEADER_H + TABS_H, this.width - 40, HEADER_H + TABS_H + 1, Colors.BORDER);

        // ─── Contenu scrollable (clippé au viewport) ───
        int contentX = contentX();
        int contentW = contentW();
        int contentYScrolled = contentTopBase() - scrollY;

        ctx.enableScissor(0, viewportTop(), this.width, viewportBottom());
        // Passif (labels, banners, sections).
        activeTab.renderPassive(ctx, contentX, contentYScrolled, contentW);
        // Widgets cliquables — rendus à la main (ils sont selectable-only).
        for (ClickableWidget w : contentWidgets) {
            if (w.visible) {
                w.render(ctx, mouseX, mouseY, delta);
            }
        }
        ctx.disableScissor();

        // ─── Scrollbar ───
        renderScrollbar(ctx);
    }

    private void renderScrollbar(DrawContext ctx) {
        if (!hasScroll()) return;
        int top = viewportTop();
        int vh = viewportH();
        int x = scrollbarX();

        // Track.
        DrawHelpers.roundedRect(ctx, x, top, SCROLLBAR_W, vh, SCROLLBAR_W / 2, Colors.SURFACE);

        // Thumb.
        int thumbH = thumbHeight();
        int thumbY = thumbY();
        int thumbColor = draggingScrollbar ? Colors.ACCENT : Colors.BORDER_STRONG;
        DrawHelpers.roundedRect(ctx, x, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W / 2, thumbColor);
    }

    private int scrollbarX() {
        // Collé au bord droit de la colonne de contenu, mais jamais hors écran.
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (hasScroll() && mouseY >= viewportTop() && mouseY <= viewportBottom()) {
            scrollY = Math.max(0, Math.min(maxScroll(), scrollY - (int) (verticalAmount * 24)));
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Priorité au drag de scrollbar avant de dispatcher aux widgets.
        if (button == 0 && hasScroll() && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            int thumbY = thumbY();
            int thumbH = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                // Clic sur le thumb : on garde l'offset de prise.
                scrollbarGrabDy = (int) mouseY - thumbY;
            } else {
                // Clic sur le track : centre le thumb sous le curseur.
                scrollbarGrabDy = thumbH / 2;
                dragScrollbarTo(mouseY);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        // Zone de clic élargie (±4px) pour ne pas exiger une précision pixel.
        return mouseX >= x - 4 && mouseX <= x + SCROLLBAR_W + 4
            && mouseY >= viewportTop() && mouseY <= viewportBottom();
    }

    /** Mappe une position Y souris vers un scrollY (drag/track click). */
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
