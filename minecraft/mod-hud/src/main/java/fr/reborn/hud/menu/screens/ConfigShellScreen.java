package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.AccountTab;
import fr.reborn.hud.menu.settings.AudioTab;
import fr.reborn.hud.menu.settings.CameraTab;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.DiscordTab;
import fr.reborn.hud.menu.settings.InterfaceTab;
import fr.reborn.hud.menu.settings.MinecraftTab;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.VideoTab;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Écran Paramètres Reborn, au niveau de la fiche shinobi (DA §4.1, §6). Signature : le byōbu, paravent à feuilles
 * d'or déplié dans une pièce de laque, la nuit ; devant lui, la planche laquée des réglages. En-tête suspendu
 * « PARAMÈTRES », huit onglets pendus à kanji (Vidéo 映, Audio 音, Contrôles 操, Caméra 写, Interface 面, Discord 話,
 * Compte 名, Minecraft 箱), plaque 閉 pour revenir (ou Échap). Le contenu de la catégorie active défile dans la
 * planche.
 *
 * <p>Chaque catégorie ne propose que des réglages <b>réellement câblés</b> (client-side, via {@code mc.options} ou les
 * managers Reborn). Toute redirection vers les écrans vanilla est centralisée dans l'onglet <b>Minecraft</b>.
 * Textures : {@code textures/gui/reglages/*} ({@code tools/gen_reglages_textures.py}, cf. {@link ReglagesArt}).
 */
public class ConfigShellScreen extends Screen {

    private final Screen parent;
    private final CategoryDef[] categories;
    private int activeIdx;

    private static final int TAB_ATTACHE = 36;     // haut des cellules d'onglets (fil d'or)
    private static final int PLANCHE_Y = 88;
    private static final int PAD_X = 24, PAD_Y = 16;
    private static final int MAX_CONTENT_W = 520;
    private static final int BOTTOM_MARGIN = 20;
    private static final int SCROLLBAR_MIN_THUMB = 24;

    private record CategoryDef(String id, String label, String kanji, SettingsTab tab) {}

    private int scrollY = 0;
    private List<AbstractWidget> contentWidgets = List.of();
    private int[] baseWidgetY = new int[0];

    private boolean draggingScrollbar = false;
    private int scrollbarGrabDy = 0;
    private int hoveredTab = -1;

    public ConfigShellScreen(Screen parent) {
        this(parent, 0);
    }

    public ConfigShellScreen(Screen parent, int initialIdx) {
        super(Component.literal("Paramètres Reborn"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
        this.categories = new CategoryDef[] {
            new CategoryDef("video", "Video", "映", new VideoTab(this)),
            new CategoryDef("audio", "Audio", "音", new AudioTab()),
            new CategoryDef("controls", "Controles", "操", new ControlsTab(this)),
            new CategoryDef("camera", "Camera", "写", new CameraTab(this)),
            new CategoryDef("interface", "Interface", "面", new InterfaceTab(this)),
            new CategoryDef("discord", "Discord", "話", new DiscordTab()),
            new CategoryDef("account", "Compte", "名", new AccountTab()),
            new CategoryDef("minecraft", "Minecraft", "箱", new MinecraftTab(this)),
        };
        this.activeIdx = Math.max(0, Math.min(categories.length - 1, initialIdx + ongletBanc()));
    }

    /** Banc de captures : onglet imposé ({@code REBORN_REGLAGES_ONGLET=<n>}, client de dev seulement). */
    private static int ongletBanc() {
        try {
            String v = System.getenv("REBORN_REGLAGES_ONGLET");
            return v == null || v.isBlank() ? 0 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private SettingsTab activeTab() {
        return categories[activeIdx].tab;
    }

    // ─── Géométrie ───────────────────────────────────────
    private int contentW() { return Math.max(260, Math.min(MAX_CONTENT_W, this.width - 2 * PAD_X - 64)); }
    private int contentX() { return (this.width - contentW()) / 2; }
    private int plancheX() { return contentX() - PAD_X; }
    private int plancheW() { return contentW() + 2 * PAD_X; }
    private int plancheH() { return Math.min(648, this.height - PLANCHE_Y - 8); }
    private int viewportTop() { return PLANCHE_Y + PAD_Y - 4; }
    private int viewportBottom() { return PLANCHE_Y + plancheH() - PAD_Y + 4; }
    private int viewportH() { return Math.max(0, viewportBottom() - viewportTop()); }
    private int contentTopBase() { return PLANCHE_Y + PAD_Y; }
    private int contentBottom() { return contentTopBase() + activeTab().height() + BOTTOM_MARGIN; }
    private int maxScroll() { return Math.max(0, contentBottom() - viewportBottom()); }
    private boolean hasScroll() { return maxScroll() > 0; }

    /** Centre de l'onglet i : huit onglets répartis sous la plaque de 520. */
    private int tabX(int i) {
        int span = Math.min(520, this.width - 40);
        float pitch = span / (float) categories.length;
        return Math.round(this.width / 2f - span / 2f + pitch * (i + 0.5f));
    }

    private int tabAt(double mx, double my) {
        if (my < TAB_ATTACHE + 6 || my > TAB_ATTACHE + 46) return -1;
        int span = Math.min(520, this.width - 40);
        float pitch = span / (float) categories.length;
        for (int i = 0; i < categories.length; i++) {
            if (Math.abs(mx - tabX(i)) <= pitch / 2f) return i;
        }
        return -1;
    }

    private int fermerX() { return this.width - 30; }

    private boolean overBack(double mx, double my) {
        return mx >= fermerX() - 13 && mx < fermerX() + 13 && my >= 0 && my < 46;
    }

    @Override
    protected void init() {
        rebuildContent();
    }

    private void rebuildContent() {
        for (AbstractWidget w : contentWidgets) {
            this.removeWidget(w);
        }
        activeTab().layout(contentX(), contentTopBase(), contentW());
        contentWidgets = activeTab().widgets();
        baseWidgetY = new int[contentWidgets.size()];
        for (int i = 0; i < contentWidgets.size(); i++) {
            AbstractWidget w = contentWidgets.get(i);
            baseWidgetY[i] = w.getY();
            this.addWidget(w);
        }
        clampScroll();
        applyScroll();
    }

    private void selectCategory(int idx) {
        if (idx == activeIdx) return;
        activeIdx = idx;
        scrollY = 0;
        RebornSounds.playReborn("stats.tab", 1.0f, 0.5f);
        rebuildContent();
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
        ReglagesArt.fond(ctx, this.width, this.height);
        Font f = this.font;
        int cx = this.width / 2;

        ReglagesArt.planche(ctx, plancheX(), PLANCHE_Y, plancheW(), plancheH());

        // Onglets pendus (sous l'en-tête) : plaquette à kanji, libellé ArcadePix ×0,5 dessous.
        int hov = tabAt(mouseX, mouseY);
        hoveredTab = hov;
        for (int i = 0; i < categories.length; i++) {
            int tx = tabX(i);
            boolean active = i == activeIdx, hot = i == hov && !active;
            ReglagesArt.onglet(ctx, tx, TAB_ATTACHE, active ? 2 : hot ? 1 : 0);
            int ty = TAB_ATTACHE + 8 + (active ? 2 : 0);
            DaKit.kanji16(ctx, f, categories[i].kanji, tx, ty + 3, active ? DaKit.CREME_KJ : hot ? DaKit.OR_CLAIR : DaKit.OR,
                active ? 0xFF140608 : 0);
            DaKit.arcFin(ctx, f, DaKit.arcade(categories[i].label), tx, ty + 26, active ? DaKit.CREME : hot ? 0xFFE8DCC0
                : 0xFFA88C5E, 1, active);
        }
        ReglagesArt.entete(ctx, cx);
        DaKit.arcX1(ctx, f, DaKit.arcade("Parametres"), cx, 19, DaKit.CREME, 1, true);

        // Plaque 閉 (retour) en haut à droite, touche Échap dessous.
        boolean backHover = overBack(mouseX, mouseY);
        ReglagesArt.fermer(ctx, fermerX(), backHover);
        DaKit.arcFin(ctx, f, DaKit.arcade("Echap"), fermerX(), 41, backHover ? DaKit.CREME : 0xFFA88C5E, 1, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        int contentX = contentX();
        int contentW = contentW();
        int contentYScrolled = contentTopBase() - scrollY;

        ctx.enableScissor(plancheX() + 4, viewportTop(), plancheX() + plancheW() - 4, viewportBottom());
        activeTab().renderPassive(ctx, contentX, contentYScrolled, contentW);
        for (AbstractWidget w : contentWidgets) {
            if (w.visible) {
                w.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
        ctx.disableScissor();

        renderScrollbar(ctx);
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx) {
        if (!hasScroll()) return;
        int top = viewportTop();
        int vh = viewportH();
        int x = scrollbarX();
        ReglagesArt.rail(ctx, x, top, vh);
        ReglagesArt.curseur(ctx, x - 1, thumbY(), thumbHeight());
    }

    private int scrollbarX() {
        return plancheX() + plancheW() - 12;
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
        if (button == 0) {
            // Plaque 閉 = retour.
            if (overBack(mouseX, mouseY)) {
                onClose();
                return true;
            }
            // Onglets pendus.
            int t = tabAt(mouseX, mouseY);
            if (t >= 0) {
                selectCategory(t);
                return true;
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dX, double dY) {
        double mouseY = event.y();
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(event, dX, dY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        int button = event.button();
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int x = scrollbarX();
        return mouseX >= x - 4 && mouseX <= x + 8
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
