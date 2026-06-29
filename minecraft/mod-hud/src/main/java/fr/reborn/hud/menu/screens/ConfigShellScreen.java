package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.crosshair.CrosshairScreen;
import fr.reborn.hud.menu.config.ConfigNavButton;
import fr.reborn.hud.menu.config.PlaceholderTab;
import fr.reborn.hud.menu.settings.AccountTab;
import fr.reborn.hud.menu.settings.AudioTab;
import fr.reborn.hud.menu.settings.ControlsTab;
import fr.reborn.hud.menu.settings.DiscordTab;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.VideoTab;
import fr.reborn.hud.menu.widget.IconButton;
import fr.reborn.hud.chat.ChatSettingsScreen;
import fr.reborn.hud.ui.HudEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hub de configuration Reborn — refonte façon OneConfig (cf
 * {@code docs/MOD_HUD_REDESIGN.md}, pilier 1).
 *
 * <p>Layout : top bar (titre + recherche + fermer), sidebar verticale de
 * catégories à gauche, zone de contenu scrollable à droite. Remplace
 * l'ancien écran à onglets horizontaux (RebornOptionsScreen).
 *
 * <p>Chaque catégorie est un {@link SettingsTab} (on réutilise les onglets
 * existants Vidéo/Audio/Contrôles/Discord/Compte ; HUD/Chat/Viseur sont des
 * {@link PlaceholderTab} en attendant leur pilier). Le contenu est rendu à la
 * main dans un scissor (mêmes mécaniques que RebornOptionsScreen) : les
 * widgets cliquables du contenu sont {@code addSelectableChild} (input only)
 * et dessinés/scrollés manuellement, tandis que la sidebar + la top bar
 * restent fixes.
 */
public class ConfigShellScreen extends Screen {

    private final Screen parent;
    private final CategoryDef[] categories;
    private int activeIdx;

    private static final int HEADER_H = 52;
    private static final int SIDEBAR_MAX = 200;
    private static final int SIDEBAR_MIN = 132;
    private static final int CONTENT_PAD = 28;
    private static final int CONTENT_TOP_PAD = 40;
    private static final int MAX_CONTENT_W = 560;
    private static final int VIEWPORT_BOTTOM_PAD = 12;
    private static final int BOTTOM_MARGIN = 24;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_MIN_THUMB = 28;

    private record CategoryDef(String id, String label, SettingsTab tab) {}

    private String searchQuery = "";
    private TextFieldWidget searchField;
    private final List<ConfigNavButton> navButtons = new ArrayList<>();

    private int scrollY = 0;
    private List<ClickableWidget> contentWidgets = List.of();
    private int[] baseWidgetY = new int[0];

    private boolean draggingScrollbar = false;
    private int scrollbarGrabDy = 0;

    public ConfigShellScreen(Screen parent) {
        this(parent, 0);
    }

    public ConfigShellScreen(Screen parent, int initialIdx) {
        super(Text.literal("Paramètres Reborn"));
        this.parent = parent;
        RebornPrefs.INSTANCE.ensureLoaded();
        this.categories = new CategoryDef[] {
            new CategoryDef("video", "Vidéo", new VideoTab(this)),
            new CategoryDef("audio", "Audio", new AudioTab()),
            new CategoryDef("controls", "Contrôles", new ControlsTab(this)),
            new CategoryDef("hud", "HUD", new PlaceholderTab(
                "Éditeur HUD",
                "Placez et personnalisez vos éléments d'interface.",
                "Ouvrir l'éditeur HUD",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null) mc.setScreen(new HudEditScreen(this));
                })),
            new CategoryDef("chat", "Chat", new PlaceholderTab(
                "Chat Reborn",
                "Onglets, filtres et apparence du chat.",
                "Réglages du chat",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null) mc.setScreen(new ChatSettingsScreen(this));
                })),
            new CategoryDef("crosshair", "Viseur", new PlaceholderTab(
                "Éditeur de viseur",
                "Modèle, couleur, dynamique, hit-marker — aperçu en direct.",
                "Ouvrir l'éditeur de viseur",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null) mc.setScreen(new CrosshairScreen(this));
                })),
            new CategoryDef("discord", "Discord", new DiscordTab()),
            new CategoryDef("account", "Compte", new AccountTab()),
        };
        this.activeIdx = Math.max(0, Math.min(categories.length - 1, initialIdx));
    }

    private SettingsTab activeTab() {
        return categories[activeIdx].tab;
    }

    // ─── Géométrie ───────────────────────────────────────
    private int viewportTop() { return HEADER_H + 1; }
    private int viewportBottom() { return this.height - VIEWPORT_BOTTOM_PAD; }
    private int viewportH() { return Math.max(0, viewportBottom() - viewportTop()); }
    /** Largeur de sidebar adaptative : se resserre sur petit écran / GUI Scale
     *  élevé pour laisser de la place au contenu (sinon les contrôles fixes
     *  écrasent les labels). */
    private int sidebarW() {
        return Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, Math.round(this.width * 0.24f)));
    }
    private int contentX() { return sidebarW() + CONTENT_PAD; }
    private int contentW() {
        return Math.max(220, Math.min(MAX_CONTENT_W, this.width - contentX() - CONTENT_PAD));
    }
    private int contentTopBase() { return viewportTop() + CONTENT_TOP_PAD; }
    private int contentBottom() { return contentTopBase() + activeTab().height() + BOTTOM_MARGIN; }
    private int maxScroll() { return Math.max(0, contentBottom() - viewportBottom()); }
    private boolean hasScroll() { return maxScroll() > 0; }

    private int searchX() { return sidebarW() + CONTENT_PAD; }
    private int searchY() { return (HEADER_H - 18) / 2; }
    private int searchW() { return Math.min(240, this.width - searchX() - 60); }

    @Override
    protected void init() {
        // Bouton fermer (top-right).
        this.addDrawableChild(new IconButton(
            this.width - 18 - 16, (HEADER_H - 16) / 2, 16,
            IconPack::close, "Fermer", true,
            b -> close()
        ).ghost()
            .withIdleColor(Colors.FOREGROUND_MUTED)
            .withHoverColor(Colors.DANGER)
            .withTooltipPlacement(IconButton.TooltipPlacement.LEFT));

        // Barre de recherche.
        searchField = new TextFieldWidget(
            this.textRenderer, searchX(), searchY(), searchW(), 18,
            Text.literal("Rechercher"));
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(40);
        searchField.setText(searchQuery);
        searchField.setPlaceholder(RebornFont.body("Rechercher une catégorie…"));
        searchField.setChangedListener(q -> {
            searchQuery = q;
            rebuildNav();
        });
        this.addDrawableChild(searchField);

        rebuildNav();
        rebuildContent();
    }

    /** (Re)construit la sidebar selon le filtre de recherche, sans toucher au
     *  champ de recherche (préserve focus + texte). */
    private void rebuildNav() {
        for (ConfigNavButton b : navButtons) {
            this.remove(b);
        }
        navButtons.clear();

        String q = searchQuery.trim().toLowerCase(Locale.ROOT);

        // Indices des catégories affichées (filtre recherche).
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < categories.length; i++) {
            if (q.isEmpty() || categories[i].label.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(i);
            }
        }
        if (shown.isEmpty()) return;

        // Espacement adaptatif : on tasse les lignes pour que toutes les
        // catégories tiennent dans la hauteur dispo (évite l'empilement à GUI
        // Scale élevé). Plancher pour rester lisible/cliquable.
        int navX = 12;
        int navW = sidebarW() - 24;
        int top = HEADER_H + 14;
        int avail = this.height - top - 12;
        int spacing = Math.min(32, Math.max(22, avail / shown.size()));
        int rowH = spacing - 2;

        int cursorY = top;
        for (int idx : shown) {
            ConfigNavButton btn = new ConfigNavButton(
                navX, cursorY, navW, rowH, categories[idx].label,
                () -> idx == activeIdx,
                b -> selectCategory(idx));
            this.addDrawableChild(btn);
            navButtons.add(btn);
            cursorY += spacing;
        }
    }

    /** (Re)layout le contenu de la catégorie active dans la zone scrollable. */
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
        // Fond global.
        ctx.fill(0, 0, this.width, this.height, Colors.BACKGROUND);
        // Top bar + sidebar en surface, pour détacher le contenu.
        ctx.fill(0, 0, this.width, HEADER_H, Colors.SURFACE);
        ctx.fill(0, HEADER_H, sidebarW(), this.height, Colors.SURFACE);
        // Séparateurs.
        ctx.fill(0, HEADER_H, this.width, HEADER_H + 1, Colors.BORDER);
        ctx.fill(sidebarW(), HEADER_H, sidebarW() + 1, this.height, Colors.BORDER);

        // Titre dans la top bar.
        Text title = RebornFont.bold("PARAMÈTRES");
        ctx.getMatrices().push();
        ctx.getMatrices().translate(16, (HEADER_H - 14) / 2f, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(this.textRenderer, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // Boîte de la recherche (le TextFieldWidget est rendu par-dessus).
        DrawHelpers.roundedOutlinedRect(ctx, searchX() - 8, searchY() - 4,
            searchW() + 16, 26, 6, Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Fond + chrome fixe (sidebar nav, recherche, fermer).
        super.render(ctx, mouseX, mouseY, delta);

        // Contenu scrollable (clippé).
        int contentX = contentX();
        int contentW = contentW();
        int contentYScrolled = contentTopBase() - scrollY;

        ctx.enableScissor(sidebarW() + 1, viewportTop(), this.width, viewportBottom());
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
        return Math.min(contentX() + contentW() + 10, this.width - SCROLLBAR_W - 6);
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
        return mouseX >= sidebarW() && mouseY >= viewportTop() && mouseY <= viewportBottom();
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
