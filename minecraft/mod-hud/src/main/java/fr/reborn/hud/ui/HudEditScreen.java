package fr.reborn.hud.ui;

import com.google.gson.Gson;
import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatSettingsScreen;
import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.config.HudConfigSnapshot;
import fr.reborn.hud.config.HudHistory;
import fr.reborn.hud.editor.AlignmentGuides;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.ui.style.BackgroundGrid;
import fr.reborn.hud.ui.style.Glow;
import fr.reborn.hud.ui.style.RebornColors;
import fr.reborn.hud.ui.style.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Éditeur HUD interactif refondu façon Lunar / Feather Client avec le
 * style visuel Reborn (palette {@link RebornColors}, rounded rectangles
 * via {@link RoundedRect}, glow via {@link Glow}).
 *
 * <p>Structure :
 * <ul>
 *   <li>{@link HudEditChrome} — top bar (logo + search + undo/redo + close)
 *       et footer keybar.</li>
 *   <li>{@link HudEditSidePanel} — panneau droit 320px (presets + transform
 *       + actions).</li>
 *   <li>Edit zone — boxes draggables avec label pill au-dessus, eye toggle
 *       in-box, resize handle (visuel, drag câblé en CHANTIER C).</li>
 * </ul>
 *
 * <p>Logique préservée du POC initial : drag du corps de boîte, molette
 * scroll = scale, clic sur eye = visibilité, save HudConfig au release.
 */
public class HudEditScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/editor");

    // ─────────── Layout zone d'édition ───────────
    private static final int EYE_BTN_SIZE = 16;
    private static final int GEAR_BTN_SIZE = 16;
    private static final int RESIZE_HANDLE_SIZE = 10;

    // ─────────── State ───────────
    private final Screen parent;
    private final HudConfig config;
    private final HudEditSidePanel sidePanel;

    private HudElement draggedElement = null;
    private HudElement selectedElement = HudElement.CHAT;
    /** Multi-selection : Shift+clic ajoute/enleve. Drag bouge tous les selectionnes du meme delta. */
    private final Set<HudElement> selectedElements = EnumSet.of(HudElement.CHAT);
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private EditBox searchField;
    private String searchQuery = "";

    /** True quand le drag courant est un drag-resize sur la poignée BR, false = move. */
    private boolean draggingResize = false;
    /** Snapshot des bounds initiaux au début d'un drag-resize. */
    private HudElementBounds resizeStartBounds = null;
    private float resizeStartScale = 1.0f;

    /** Stack undo/redo, push apres chaque action (drag end, scroll, eye toggle, preset). */
    private final HudHistory history = new HudHistory();
    private HudConfigSnapshot snapshotBeforeAction = null;

    /** Guides d'alignement actifs pour le frame courant (rendus pendant drag). */
    private List<AlignmentGuides.Guide> activeGuides = List.of();
    /** Keybar collapsed = juste un bouton "?" sous la top bar.
     *  Collapsed PAR DEFAUT pour ne PAS interferer avec la boss bar /
     *  la zone d'edition. Le user click sur "?" pour deplier. */
    private boolean keybarCollapsed = true;
    /** Side panel masque par defaut — pattern big studio (Figma) : settings
     *  contextuels uniquement quand l'utilisateur en a besoin. Toggle via
     *  l'icone engrenage sur chaque box. */
    private boolean sidePanelOpen = false;
    /** Rect du toggle du keybar (collapsed → toute la pille, expanded → coin gauche). */
    private int[] keybarToggleRect = null;

    /** Pixels par cran de molette. */
    private static final float SCALE_STEP = 0.05f;

    public HudEditScreen(Screen parent) {
        super(Component.translatable("reborn-hud.screen.title"));
        this.parent = parent;
        this.config = RebornHudClient.config();
        this.sidePanel = new HudEditSidePanel(config, Minecraft.getInstance().font);
        this.sidePanel.setSelectedElement(this.selectedElement);
        this.sidePanel.onResetAll = () -> {
            config.resetAll();
            config.save();
        };
        this.sidePanel.onExport = () -> exportConfigToClipboard();
        this.sidePanel.onImport = () -> importConfigFromClipboard();
        this.sidePanel.onMutation = () -> history.push(HudConfigSnapshot.capture(config));
        this.sidePanel.onToast = this::showToast;
    }

    @Override
    protected void init() {
        sidePanel.layout(this.width, this.height);

        // Search field dans la top bar TOP-RIGHT — colle au coin droit, juste a
        // gauche des icon buttons. Quand le side panel s'ouvre il glisse
        // SOUS la search/btns (z-order = panel sous chrome). Donc on
        // ancre directement au bord droit ecran, pas au left edge du panel.
        int searchW = HudEditChrome.SEARCH_WIDTH;
        int btnsTotalW = HudEditChrome.ICONBTN_SIZE * 3 + HudEditChrome.ICONBTN_GAP * 2 + 12; // 3 btns + divider
        int rightEdge = this.width - 8; // 8px margin du bord droit
        int searchX = rightEdge - btnsTotalW - searchW - 8;
        int searchY = (HudEditChrome.TOPBAR_HEIGHT - 22) / 2;
        searchField = new EditBox(this.font,
            searchX + 26, searchY + 6, searchW - 32, 10, Component.literal("Rechercher un élément..."));
        searchField.setBordered(false);
        searchField.setMaxLength(40);
        searchField.setChangedListener(s -> searchQuery = s == null ? "" : s.toLowerCase());
        this.addRenderableWidget(searchField);
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Pas de blur vanilla : on veut voir le HUD derrière clairement.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Edit zone : largeur effective depend de l'etat du side panel
        int editRight = sidePanelOpen ? this.width - HudEditSidePanel.WIDTH : this.width;

        // 1) Overlay sombre subtil sur la zone d'edition
        ctx.fill(0, HudEditChrome.TOPBAR_HEIGHT,
            editRight, this.height, 0x40000000);

        // 2) Grid dots subtil — "canvas" feel a la Figma
        BackgroundGrid.render(ctx, 0, HudEditChrome.TOPBAR_HEIGHT, editRight, this.height);

        // 3) Side panel uniquement si l'utilisateur a clique sur l'engrenage d'une box
        if (sidePanelOpen) {
            sidePanel.render(ctx, mouseX, mouseY);
        }

        // 4) Chrome top bar
        HudEditChrome.renderTopBar(ctx, this.width);
        HudEditChrome.renderLogoAndTitle(ctx, this.font, this.height);
        renderSearchBox(ctx, mouseX, mouseY);
        renderTopBarButtons(ctx, mouseX, mouseY);

        // 5) Vanilla widget render (search input field text)
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // 6) Boxes HUD rendues par-dessus le chrome — sinon boss bar (y=12)
        //    et scoreboard (right edge) seraient invisibles / unclickables.
        HudElement hovered = elementUnderMouse(mouseX, mouseY);
        for (HudElement element : HudElement.values()) {
            if (element == draggedElement) continue;
            if (!matchesSearch(element)) continue;
            boolean isHovered = hovered == element;
            // Une box est "selected" visuellement si dans le multi-select OU
            // si elle est l'element edite par le side panel ouvert.
            boolean isSelected = selectedElements.contains(element)
                || (selectedElement == element && sidePanelOpen);
            renderHudBox(ctx, element, isHovered, isSelected, false, mouseX, mouseY);
        }
        if (draggedElement != null) {
            renderHudBox(ctx, draggedElement, false,
                selectedElements.contains(draggedElement)
                    || (draggedElement == selectedElement && sidePanelOpen),
                true, mouseX, mouseY);
        }

        // 7) Keybar top-left sous le top bar (collapsible)
        int modified = countModifiedElements();
        this.keybarToggleRect = HudEditChrome.renderKeybar(
            ctx, this.font, this.width,
            HudElement.values().length, modified, keybarCollapsed);

        // 8) Alignment guides — uniquement pendant un drag-move actif
        if (!activeGuides.isEmpty() && draggedElement != null && !draggingResize) {
            AlignmentGuides.renderGuides(ctx, activeGuides, this.width, this.height, this.font);
        }

        // 9) Version badge bottom-left
        HudEditChrome.renderVersionBadge(ctx, this.font, this.height);

        // 10) Live coords pendant un drag (close-by floating pill)
        renderLiveCoordsPill(ctx, mouseX, mouseY);

        // 11) Tooltip icon button au hover (top bar)
        renderIconButtonTooltip(ctx, mouseX, mouseY);

        // 12) Toast feedback (Ctrl+Z / Export / Import)
        renderToast(ctx);
    }

    private boolean matchesSearch(HudElement element) {
        if (searchQuery == null || searchQuery.isBlank()) return true;
        return element.displayName().toLowerCase().contains(searchQuery)
            || element.id().contains(searchQuery);
    }

    private int countModifiedElements() {
        int n = 0;
        for (HudElement e : HudElement.values()) {
            HudElementState s = config.stateOf(e);
            if (s.x() != 0 || s.y() != 0 || s.scale() != 1.0f || !s.visible() || s.anchor() != null) n++;
        }
        return n;
    }

    private void renderSearchBox(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int searchW = HudEditChrome.SEARCH_WIDTH;
        int btnsTotalW = HudEditChrome.ICONBTN_SIZE * 3 + HudEditChrome.ICONBTN_GAP * 2 + 12;
        int rightEdge = this.width - 8;
        int x = rightEdge - btnsTotalW - searchW - 8;
        int y = (HudEditChrome.TOPBAR_HEIGHT - 22) / 2;
        boolean focused = searchField != null && searchField.isFocused();
        int borderColor = focused ? RebornColors.ACCENT : RebornColors.BORDER;

        RoundedRect.fill(ctx, x, y, searchW, 22, 8, RebornColors.BG_INPUT);
        RoundedRect.border(ctx, x, y, searchW, 22, 8, borderColor);
        if (focused) Glow.roundedRect(ctx, x, y, searchW, 22, 8, RebornColors.ACCENT_SOFT);

        // Icone search 15×15
        HudEditChrome.renderIconGlyph(ctx, "search", x + 12, y + 11, RebornColors.FOREGROUND_MUTED);

        // Placeholder si vide et pas focus
        if (!focused && (searchQuery == null || searchQuery.isBlank())) {
            ctx.text(this.font, Component.literal("Rechercher un élément..."),
                x + 26, y + 7, RebornColors.FOREGROUND_MUTED, false);
        }
    }

    private void renderTopBarButtons(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int btnsRightEdge = this.width - 8;
        int btnY = (HudEditChrome.TOPBAR_HEIGHT - HudEditChrome.ICONBTN_SIZE) / 2;
        int btnSz = HudEditChrome.ICONBTN_SIZE;
        int gap = HudEditChrome.ICONBTN_GAP;
        int xCursor = btnsRightEdge - btnSz;

        // 1. Close button (rightmost, accent danger)
        boolean closeHovered = mouseX >= xCursor && mouseX < xCursor + btnSz
            && mouseY >= btnY && mouseY < btnY + btnSz;
        HudEditChrome.renderIconButton(ctx, xCursor, btnY, closeHovered, true, 0);
        HudEditChrome.renderIconGlyph(ctx, "close",
            xCursor + btnSz / 2, btnY + btnSz / 2,
            closeHovered ? RebornColors.DANGER : RebornColors.FOREGROUND);

        // Divider visible avant les actions
        xCursor -= 12;
        ctx.fill(xCursor, btnY + 5, xCursor + 1, btnY + btnSz - 5, RebornColors.BORDER_STRONG);
        xCursor -= btnSz + 6;

        // 2. Settings (gear → ChatSettingsScreen)
        boolean settingsHovered = mouseX >= xCursor && mouseX < xCursor + btnSz
            && mouseY >= btnY && mouseY < btnY + btnSz;
        HudEditChrome.renderIconButton(ctx, xCursor, btnY, settingsHovered, false, 0);
        HudEditChrome.renderIconGlyph(ctx, "gear",
            xCursor + btnSz / 2, btnY + btnSz / 2,
            settingsHovered ? RebornColors.ACCENT_HOVER : RebornColors.FOREGROUND_SUBTLE);
        xCursor -= btnSz + gap;

        // 3. Redo
        boolean redoHovered = mouseX >= xCursor && mouseX < xCursor + btnSz
            && mouseY >= btnY && mouseY < btnY + btnSz;
        HudEditChrome.renderIconButton(ctx, xCursor, btnY, redoHovered, false, 0);
        HudEditChrome.renderIconGlyph(ctx, "redo",
            xCursor + btnSz / 2, btnY + btnSz / 2,
            redoHovered ? RebornColors.ACCENT_HOVER : RebornColors.FOREGROUND_SUBTLE);
        xCursor -= btnSz + gap;

        // 4. Undo
        boolean undoHovered = mouseX >= xCursor && mouseX < xCursor + btnSz
            && mouseY >= btnY && mouseY < btnY + btnSz;
        HudEditChrome.renderIconButton(ctx, xCursor, btnY, undoHovered, false, 0);
        HudEditChrome.renderIconGlyph(ctx, "undo",
            xCursor + btnSz / 2, btnY + btnSz / 2,
            undoHovered ? RebornColors.ACCENT_HOVER : RebornColors.FOREGROUND_SUBTLE);
    }

    private void renderHudBox(GuiGraphicsExtractor ctx, HudElement element, boolean hovered, boolean selected,
                              boolean dragging, int mouseX, int mouseY) {
        HudElementState state = config.stateOf(element);
        HudElementBounds bounds = HudElementBounds.currentFor(
            element, state, this.width, this.height);

        int bx = bounds.x(), by = bounds.y(), bw = bounds.width(), bh = bounds.height();

        boolean active = hovered || selected || dragging;
        boolean hidden = !state.visible();
        boolean overlapping = state.visible() && hasOverlapWith(element, bounds);

        // ─── Mode minimal (non-actif) : juste un outline fin discret. ───
        if (!active && !hidden) {
            int subtleBorder = overlapping
                ? RebornColors.withAlpha(RebornColors.WARNING, 0x80)
                : RebornColors.withAlpha(RebornColors.FOREGROUND_MUTED, 0x4D);
            RoundedRect.border(ctx, bx, by, bw, bh, 3, subtleBorder);
            return;
        }

        // ─── Mode rich (hovered / selected / dragged / hidden) ───
        int borderColor;
        int fillColor;
        if (dragging) {
            borderColor = RebornColors.ACCENT;
            fillColor   = RebornColors.ACCENT_SOFT;
        } else if (selected) {
            borderColor = RebornColors.ACCENT;
            fillColor   = RebornColors.withAlpha(RebornColors.ACCENT, 0x10);
        } else if (hovered) {
            borderColor = RebornColors.ACCENT_HOVER;
            fillColor   = RebornColors.withAlpha(RebornColors.ACCENT, 0x10);
        } else { // hidden
            borderColor = RebornColors.DANGER;
            fillColor   = RebornColors.DANGER_SOFT;
        }

        if (selected || dragging) {
            Glow.roundedRect(ctx, bx, by, bw, bh, 4,
                RebornColors.withAlpha(RebornColors.ACCENT, dragging ? 0x66 : 0x30));
        }
        RoundedRect.fill(ctx, bx, by, bw, bh, 4, fillColor);
        if (selected || dragging || hovered) {
            RoundedRect.borderThick(ctx, bx, by, bw, bh, 4, borderColor);
        } else {
            RoundedRect.border(ctx, bx, by, bw, bh, 4, borderColor);
        }

        // Label pill au-dessus (auto-placement si box dans chrome top)
        renderLabelPill(ctx, element, state, bx, by, bw, bh);

        // Eye + Gear button : position auto = INSIDE box top-right si possible,
        //                     SOUS la chrome si la box est sous le top bar.
        boolean inChrome = by < HudEditChrome.TOPBAR_HEIGHT;
        int btnY = inChrome
            ? HudEditChrome.TOPBAR_HEIGHT + 4
            : by + 3;
        int btnX = bx + bw - GEAR_BTN_SIZE - 4;
        renderGearButton(ctx, btnX, btnY,
            inside(mouseX, mouseY, btnX, btnY, GEAR_BTN_SIZE, GEAR_BTN_SIZE));
        btnX -= EYE_BTN_SIZE + 3;
        renderEyeButton(ctx, btnX, btnY, state.visible(),
            inside(mouseX, mouseY, btnX, btnY, EYE_BTN_SIZE, EYE_BTN_SIZE));

        if (bw >= RESIZE_HANDLE_SIZE * 2 && bh >= RESIZE_HANDLE_SIZE * 2) {
            int rhX = bx + bw - RESIZE_HANDLE_SIZE / 2 - 1;
            int rhY = by + bh - RESIZE_HANDLE_SIZE / 2 - 1;
            renderResizeHandle(ctx, rhX, rhY,
                inside(mouseX, mouseY, rhX, rhY, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE));
        }
    }

    /** Engrenage — vrai PNG settings.png + bg subtil + border hover. */
    private void renderGearButton(GuiGraphicsExtractor ctx, int x, int y, boolean hovered) {
        int bg = hovered ? 0xCC0A0B0F : 0xB3070811;
        int border = hovered ? RebornColors.ACCENT : RebornColors.BORDER_STRONG;
        RoundedRect.fill(ctx, x, y, GEAR_BTN_SIZE, GEAR_BTN_SIZE, 4, bg);
        RoundedRect.border(ctx, x, y, GEAR_BTN_SIZE, GEAR_BTN_SIZE, 4, border);
        int color = hovered ? RebornColors.ACCENT_HOVER : RebornColors.FOREGROUND_SUBTLE;
        // Vrai PNG settings via IconTextures
        int iconSize = GEAR_BTN_SIZE - 4;
        fr.reborn.hud.ui.style.IconTextures.draw(ctx, "gear",
            x + (GEAR_BTN_SIZE - iconSize) / 2,
            y + (GEAR_BTN_SIZE - iconSize) / 2,
            iconSize, color);
    }

    private void renderLabelPill(GuiGraphicsExtractor ctx, HudElement element, HudElementState state,
                                  int bx, int by, int bw, int bh) {
        String name = element.displayName().toUpperCase();
        String coords = String.format("(%+d, %+d) · x%.2f", state.x(), state.y(), state.scale());
        if (!state.visible()) coords = "masqué";

        boolean modified = isStateModified(state);

        int nameW = this.font.width(name);
        int coordW = this.font.width(coords);
        int dotW = modified ? 8 : 0;
        int pillW = nameW + 8 + coordW + 14 + dotW;
        int pillH = 14;
        int pillX = bx;
        int pillY = by - pillH - 4;
        if (pillY < HudEditChrome.TOPBAR_HEIGHT + 2) {
            pillY = Math.max(by + bh + 4, HudEditChrome.TOPBAR_HEIGHT + 4);
        }

        RoundedRect.fill(ctx, pillX, pillY, pillW, pillH, 4, 0xD1070811);
        RoundedRect.border(ctx, pillX, pillY, pillW, pillH, 4, RebornColors.BORDER);

        int textX = pillX + 7;

        // Modified indicator dot a gauche du nom
        if (modified) {
            ctx.fill(textX, pillY + 5, textX + 4, pillY + 9, RebornColors.ACCENT);
            textX += 7;
        }

        ctx.text(this.font, Component.literal(name).withStyle(ChatFormatting.BOLD),
            textX, pillY + 3,
            state.visible() ? RebornColors.FOREGROUND : RebornColors.FOREGROUND_SUBTLE, false);

        ctx.text(this.font, Component.literal(coords),
            textX + nameW + 6, pillY + 3, RebornColors.FOREGROUND_SUBTLE, false);
    }

    /** GuiEventListener a un state ≠ defaut → afficher indicateur. */
    private static boolean isStateModified(HudElementState state) {
        return state.x() != 0 || state.y() != 0 || state.scale() != 1.0f
            || !state.visible() || state.anchor() != null;
    }

    /** Vérifie si la box overlap avec une autre visible (pour warning). */
    private boolean hasOverlapWith(HudElement element, HudElementBounds bounds) {
        for (HudElement other : HudElement.values()) {
            if (other == element) continue;
            HudElementState os = config.stateOf(other);
            if (!os.visible()) continue;
            HudElementBounds ob = HudElementBounds.currentFor(other, os, this.width, this.height);
            if (bounds.x() < ob.right() && bounds.right() > ob.x()
                && bounds.y() < ob.bottom() && bounds.bottom() > ob.y()) {
                return true;
            }
        }
        return false;
    }

    private void renderEyeButton(GuiGraphicsExtractor ctx, int x, int y, boolean visible, boolean hovered) {
        int bg = hovered ? 0xCC0A0B0F : 0xB3070811;
        int border = hovered ? RebornColors.BORDER_STRONG : RebornColors.BORDER;
        RoundedRect.fill(ctx, x, y, EYE_BTN_SIZE, EYE_BTN_SIZE, 5, bg);
        RoundedRect.border(ctx, x, y, EYE_BTN_SIZE, EYE_BTN_SIZE, 5, border);
        int color = visible ? RebornColors.FOREGROUND_SUBTLE : RebornColors.DANGER;
        int iconSize = EYE_BTN_SIZE - 4;
        fr.reborn.hud.ui.style.IconTextures.draw(ctx, visible ? "eye_open" : "eye_closed",
            x + (EYE_BTN_SIZE - iconSize) / 2,
            y + (EYE_BTN_SIZE - iconSize) / 2,
            iconSize, color);
    }

    private void renderResizeHandle(GuiGraphicsExtractor ctx, int x, int y, boolean hovered) {
        int s = RESIZE_HANDLE_SIZE;
        RoundedRect.fill(ctx, x, y, s, s, 3, RebornColors.ACCENT);
        if (hovered) {
            Glow.roundedRect(ctx, x, y, s, s, 3, RebornColors.ACCENT_GLOW);
        }
        // 3 lignes diagonales blanches
        for (int i = 0; i < 3; i++) {
            int o = i * 3;
            ctx.fill(x + 3 + o, y + s - 4, x + 4 + o, y + s - 3, 0xFFFFFFFF);
            ctx.fill(x + s - 4, y + 3 + o, x + s - 3, y + 4 + o, 0xFFFFFFFF);
        }
    }

    private HudElement elementUnderMouse(int mouseX, int mouseY) {
        // Pas de restriction de zone : les boxes peuvent etre cliquees meme
        // si elles sont visuellement sous le top bar ou le side panel (z-order
        // place les boxes au-dessus du chrome). Le mouseClicked teste la
        // priorite chrome > box.
        if (draggedElement != null) return draggedElement;
        HudElement[] values = HudElement.values();
        for (int i = values.length - 1; i >= 0; i--) {
            HudElement element = values[i];
            if (!matchesSearch(element)) continue;
            HudElementState state = config.stateOf(element);
            HudElementBounds bounds = HudElementBounds.currentFor(
                element, state, this.width, this.height);
            if (bounds.contains(mouseX, mouseY)) return element;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Right-click dans le side panel → context actions (delete preset par ex.)
        if (button == 1 && sidePanelOpen
                && mouseX >= this.width - HudEditSidePanel.WIDTH
                && mouseY >= HudEditChrome.TOPBAR_HEIGHT) {
            if (sidePanel.handleRightClick(mouseX, mouseY)) return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 1. Click "?" du keybar → ouvre HelpScreen
        if (keybarToggleRect != null
                && mouseX >= keybarToggleRect[0]
                && mouseX < keybarToggleRect[0] + keybarToggleRect[2]
                && mouseY >= keybarToggleRect[1]
                && mouseY < keybarToggleRect[1] + keybarToggleRect[3]) {
            Minecraft.getInstance().setScreen(new HudHelpScreen(this));
            return true;
        }

        // 2. Side panel : absorbe TOUT clic dans sa zone (meme widgets non
        //    hit-testes), sinon il se fermait au clic sur slider/anchor/preset
        //    a cause du fallback "click hors box → close" plus bas.
        if (sidePanelOpen && mouseX >= this.width - HudEditSidePanel.WIDTH
                          && mouseY >= HudEditChrome.TOPBAR_HEIGHT) {
            sidePanel.handleClick(mouseX, mouseY, this::selectElement);
            return true;
        }

        // 3. Top bar buttons
        if (mouseY < HudEditChrome.TOPBAR_HEIGHT) {
            if (handleTopBarClick(mouseX, mouseY)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 4. Vanilla widget pass (search input)
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // 5. HUD box
        HudElement element = elementUnderMouse((int) mouseX, (int) mouseY);
        if (element == null) {
            // Click hors box → ferme le side panel (UX Figma : click outside closes)
            if (sidePanelOpen) {
                sidePanelOpen = false;
                return true;
            }
            return false;
        }

        HudElementState state = config.stateOf(element);
        HudElementBounds bounds = HudElementBounds.currentFor(
            element, state, this.width, this.height);

        // Calcul des positions des boutons (meme logique que renderHudBox)
        boolean inChrome = bounds.y() < HudEditChrome.TOPBAR_HEIGHT;
        int btnY = inChrome
            ? HudEditChrome.TOPBAR_HEIGHT + 4
            : bounds.y() + 3;
        int gearX = bounds.x() + bounds.width() - GEAR_BTN_SIZE - 4;
        int eyeX = gearX - EYE_BTN_SIZE - 3;

        // Click sur engrenage → ouvre/cible le side panel
        if (inside((int) mouseX, (int) mouseY, gearX, btnY, GEAR_BTN_SIZE, GEAR_BTN_SIZE)) {
            selectElement(element);
            sidePanelOpen = true;
            return true;
        }

        // Click sur eye → toggle visibilite (avec snapshot pour undo)
        if (inside((int) mouseX, (int) mouseY, eyeX, btnY, EYE_BTN_SIZE, EYE_BTN_SIZE)) {
            history.push(HudConfigSnapshot.capture(config));
            config.setState(element, state.withVisible(!state.visible()));
            config.save();
            return true;
        }

        // Click sur la poignee resize BR → drag-resize
        if (bounds.width() >= RESIZE_HANDLE_SIZE * 2 && bounds.height() >= RESIZE_HANDLE_SIZE * 2) {
            int rhX = bounds.x() + bounds.width() - RESIZE_HANDLE_SIZE / 2 - 1;
            int rhY = bounds.y() + bounds.height() - RESIZE_HANDLE_SIZE / 2 - 1;
            if (inside((int) mouseX, (int) mouseY, rhX, rhY, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE)) {
                snapshotBeforeAction = HudConfigSnapshot.capture(config);
                selectElement(element);
                this.draggedElement = element;
                this.draggingResize = true;
                this.resizeStartBounds = bounds;
                this.resizeStartScale = state.scale();
                return true;
            }
        }

        // Multi-select : Shift+clic ajoute / enleve a la selection
        boolean shift = rebornHasShiftDown();
        if (shift) {
            if (selectedElements.contains(element)) {
                selectedElements.remove(element);
                if (!selectedElements.isEmpty()) {
                    selectedElement = selectedElements.iterator().next();
                }
            } else {
                selectedElements.add(element);
                selectedElement = element;
            }
            sidePanel.setSelectedElement(selectedElement);
            return true;
        }

        // Click corps simple → select unique + start drag-move (snapshot pour undo)
        snapshotBeforeAction = HudConfigSnapshot.capture(config);
        selectElement(element);
        this.draggedElement = element;
        this.draggingResize = false;
        this.dragOffsetX = (int) (mouseX - bounds.x());
        this.dragOffsetY = (int) (mouseY - bounds.y());
        return true;
    }

    /**
     * Wrappers vers les helpers static de {@link Screen} — plus fiables que
     * GLFW direct (le state peut etre stale pendant un event Screen).
     */
    private static boolean rebornHasShiftDown() { return Screen.hasShiftDown(); }
    private static boolean rebornHasCtrlDown()  { return Screen.hasControlDown(); }

    private boolean handleTopBarClick(double mouseX, double mouseY) {
        int btnY = (HudEditChrome.TOPBAR_HEIGHT - HudEditChrome.ICONBTN_SIZE) / 2;
        int btnsRightEdge = this.width - 8;
        int btnSz = HudEditChrome.ICONBTN_SIZE;
        int gap = HudEditChrome.ICONBTN_GAP;

        // Layout droite → gauche : close, [divider 12+1+12], settings, redo, undo
        int closeX    = btnsRightEdge - btnSz;
        int settingsX = closeX - 12 - 1 - 12 - btnSz;
        int redoX     = settingsX - gap - btnSz;
        int undoX     = redoX - gap - btnSz;

        if (inside((int) mouseX, (int) mouseY, closeX, btnY, btnSz, btnSz)) {
            close();
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, settingsX, btnY, btnSz, btnSz)) {
            openChatSettings();
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, redoX, btnY, btnSz, btnSz)) {
            HudConfigSnapshot current = HudConfigSnapshot.capture(config);
            HudConfigSnapshot next = history.redo(current);
            if (next != null) { next.applyTo(config); showToast("Refait"); }
            else showToast("Rien à refaire");
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, undoX, btnY, btnSz, btnSz)) {
            HudConfigSnapshot current = HudConfigSnapshot.capture(config);
            HudConfigSnapshot prev = history.undo(current);
            if (prev != null) { prev.applyTo(config); showToast("Annulé"); }
            else showToast("Rien à annuler");
            return true;
        }
        return false;
    }

    /** Ouvre le settings panel du chat. */
    public void openChatSettings() {
        Minecraft.getInstance().setScreen(new ChatSettingsScreen(this));
    }

    private void selectElement(HudElement element) {
        this.selectedElement = element;
        this.selectedElements.clear();
        this.selectedElements.add(element);
        this.sidePanel.setSelectedElement(element);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || draggedElement == null) {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        HudElementState state = config.stateOf(draggedElement);

        if (draggingResize) {
            // Drag-resize : ratio drag / taille originale → nouveau scale
            int dxFromTL = (int) mouseX - resizeStartBounds.x();
            int dyFromTL = (int) mouseY - resizeStartBounds.y();
            int origW = Math.max(8, resizeStartBounds.width());
            int origH = Math.max(8, resizeStartBounds.height());
            float scaleW = dxFromTL / (float) origW;
            float scaleH = dyFromTL / (float) origH;
            // On prend la moyenne pour avoir un scale isotrope
            float newScale = Math.max(scaleW, scaleH) * resizeStartScale;
            config.setState(draggedElement, state.withScale(newScale));
            return true;
        }

        // Drag-move avec alignment guides
        int targetX = (int) (mouseX - dragOffsetX);
        int targetY = (int) (mouseY - dragOffsetY);
        HudElementBounds vanilla = HudElementBounds.vanillaFor(
            draggedElement, this.width, this.height);

        // Calcul snap + guides
        HudElementBounds curBounds = HudElementBounds.currentFor(
            draggedElement, state, this.width, this.height);
        AlignmentGuides.SnapResult snap = AlignmentGuides.compute(
            targetX, targetY, curBounds.width(), curBounds.height(), draggedElement,
            e -> HudElementBounds.currentFor(e, config.stateOf(e), this.width, this.height),
            this.width, this.height, rebornHasShiftDown());
        this.activeGuides = snap.guides();

        int snappedX = snap.newX();
        int snappedY = snap.newY();
        int newOffsetX = snappedX - vanilla.x();
        int newOffsetY = snappedY - vanilla.y();

        // Multi-drag : applique le meme delta a tous les selectionnes
        int deltaX = newOffsetX - state.x();
        int deltaY = newOffsetY - state.y();
        if (selectedElements.size() > 1 && selectedElements.contains(draggedElement)) {
            for (HudElement e : selectedElements) {
                HudElementState s = config.stateOf(e);
                config.setState(e, s.withDelta(deltaX, deltaY));
            }
        } else {
            config.setState(draggedElement, state.withPos(newOffsetX, newOffsetY));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedElement != null) {
            config.save();
            // Push snapshot APRES action pour pouvoir undo
            if (snapshotBeforeAction != null) {
                history.push(snapshotBeforeAction);
                snapshotBeforeAction = null;
            }
            draggedElement = null;
            draggingResize = false;
            activeGuides = List.of();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Z (QWERTY) ou Ctrl+W (AZERTY — meme position physique que Z) = undo
        // Ctrl+Y = redo (Y a la meme position en QWERTY et AZERTY).
        if (rebornHasCtrlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_W) {
                HudConfigSnapshot current = HudConfigSnapshot.capture(config);
                HudConfigSnapshot prev = history.undo(current);
                if (prev != null) {
                    prev.applyTo(config);
                    showToast("Annulé (" + history.undoDepth() + " restants)");
                } else {
                    showToast("Rien à annuler");
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                HudConfigSnapshot current = HudConfigSnapshot.capture(config);
                HudConfigSnapshot next = history.redo(current);
                if (next != null) {
                    next.applyTo(config);
                    showToast("Refait");
                } else {
                    showToast("Rien à refaire");
                }
                return true;
            }
            // Ctrl+M = chat settings panel (M comme Message)
            if (keyCode == GLFW.GLFW_KEY_M) {
                openChatSettings();
                return true;
            }
        }

        // F1 = ouvre HelpScreen
        if (keyCode == GLFW.GLFW_KEY_F1) {
            Minecraft.getInstance().setScreen(new HudHelpScreen(this));
            return true;
        }

        // Arrow keys = nudge la box selectionnee (Shift = 10px, sinon 1px)
        if (selectedElement != null) {
            int step = rebornHasShiftDown() ? 10 : 1;
            int dx = 0, dy = 0;
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT  -> dx = -step;
                case GLFW.GLFW_KEY_RIGHT -> dx =  step;
                case GLFW.GLFW_KEY_UP    -> dy = -step;
                case GLFW.GLFW_KEY_DOWN  -> dy =  step;
            }
            if (dx != 0 || dy != 0) {
                history.push(HudConfigSnapshot.capture(config));
                // Multi-select : applique le delta a tous les selectionnes
                if (selectedElements.size() > 1) {
                    for (HudElement e : selectedElements) {
                        HudElementState s = config.stateOf(e);
                        config.setState(e, s.withDelta(dx, dy));
                    }
                } else {
                    HudElementState s = config.stateOf(selectedElement);
                    config.setState(selectedElement, s.withDelta(dx, dy));
                }
                config.save();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ───── Toast feedback (fade-in + hold + fade-out) ─────
    private String toastText = null;
    private long toastShownAtMs = 0L;
    private static final long TOAST_DURATION_MS = 2400;
    private static final long TOAST_FADE_IN_MS = 180;
    private static final long TOAST_FADE_OUT_MS = 400;

    private void showToast(String text) {
        this.toastText = text;
        this.toastShownAtMs = System.currentTimeMillis();
    }

    private void renderToast(GuiGraphicsExtractor ctx) {
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastShownAtMs;
        if (age > TOAST_DURATION_MS) { toastText = null; return; }
        // Phase fade-in / hold / fade-out
        float opacity;
        if (age < TOAST_FADE_IN_MS) {
            opacity = age / (float) TOAST_FADE_IN_MS;
        } else if (age > TOAST_DURATION_MS - TOAST_FADE_OUT_MS) {
            opacity = (TOAST_DURATION_MS - age) / (float) TOAST_FADE_OUT_MS;
        } else {
            opacity = 1f;
        }
        // Slide-up subtil pendant fade-in (translate vers haut)
        int slideOffset = (int) ((1f - opacity) * 6f * (age < TOAST_FADE_IN_MS ? 1 : 0));
        int alpha = (int) (opacity * 255);
        int pillW = this.font.width(toastText) + 24;
        int pillH = 22;
        int pillX = (this.width - pillW) / 2;
        int pillY = this.height - 80 + slideOffset;
        RoundedRect.fill(ctx, pillX, pillY, pillW, pillH, 6,
            (alpha << 24) | (RebornColors.BG_PANEL_ELEVATED & 0x00FFFFFF));
        RoundedRect.border(ctx, pillX, pillY, pillW, pillH, 6,
            (alpha << 24) | (RebornColors.ACCENT & 0x00FFFFFF));
        ctx.text(this.font, Component.literal(toastText),
            pillX + 12, pillY + 7,
            (alpha << 24) | (RebornColors.FOREGROUND & 0x00FFFFFF), false);
    }

    // ───── Live coords pill pendant drag ─────
    private void renderLiveCoordsPill(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (draggedElement == null || draggingResize) return;
        HudElementState state = config.stateOf(draggedElement);
        String text = String.format("%+d, %+d", state.x(), state.y());
        if (selectedElements.size() > 1) {
            text = "(" + selectedElements.size() + ") " + text;
        }
        int w = this.font.width(text) + 14;
        int h = 16;
        // Position : a droite-bas du curseur, avec 14px d'offset pour ne pas occluder
        int x = mouseX + 14;
        int y = mouseY + 14;
        if (x + w > this.width - 4) x = mouseX - w - 14;
        if (y + h > this.height - 4) y = mouseY - h - 14;
        RoundedRect.fill(ctx, x, y, w, h, 4, 0xE6070811);
        RoundedRect.border(ctx, x, y, w, h, 4, RebornColors.ACCENT);
        ctx.text(this.font, Component.literal(text),
            x + 7, y + 4, RebornColors.FOREGROUND, false);
    }

    // ───── Tooltip pour icon buttons ─────
    private void renderIconButtonTooltip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (mouseY >= HudEditChrome.TOPBAR_HEIGHT) return;
        int btnY = (HudEditChrome.TOPBAR_HEIGHT - HudEditChrome.ICONBTN_SIZE) / 2;
        int btnSz = HudEditChrome.ICONBTN_SIZE;
        int gap = HudEditChrome.ICONBTN_GAP;
        int btnsRightEdge = this.width - 8;
        int closeX    = btnsRightEdge - btnSz;
        int settingsX = closeX - 12 - 1 - 12 - btnSz;
        int redoX     = settingsX - gap - btnSz;
        int undoX     = redoX - gap - btnSz;

        String label = null;
        int hoverX = -1;
        if (inside(mouseX, mouseY, closeX, btnY, btnSz, btnSz)) {
            label = "Fermer · Esc";  hoverX = closeX;
        } else if (inside(mouseX, mouseY, settingsX, btnY, btnSz, btnSz)) {
            label = "Paramètres chat · Ctrl+M";  hoverX = settingsX;
        } else if (inside(mouseX, mouseY, redoX, btnY, btnSz, btnSz)) {
            label = "Refaire · Ctrl+Y";  hoverX = redoX;
        } else if (inside(mouseX, mouseY, undoX, btnY, btnSz, btnSz)) {
            label = "Annuler · Ctrl+Z";  hoverX = undoX;
        }
        if (label == null) return;

        int w = this.font.width(label) + 10;
        int h = 14;
        int x = hoverX + btnSz / 2 - w / 2;
        if (x + w > this.width - 4) x = this.width - 4 - w;
        if (x < 4) x = 4;
        int y = btnY + btnSz + 4;
        RoundedRect.fill(ctx, x, y, w, h, 3, 0xE6070811);
        RoundedRect.border(ctx, x, y, w, h, 3, RebornColors.BORDER_STRONG);
        ctx.text(this.font, Component.literal(label),
            x + 5, y + 3, RebornColors.FOREGROUND, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        HudElement element = elementUnderMouse((int) mouseX, (int) mouseY);
        if (element == null) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        history.push(HudConfigSnapshot.capture(config));
        HudElementState state = config.stateOf(element);
        float delta = (float) verticalAmount * SCALE_STEP;
        config.setState(element, state.withScale(state.scale() + delta));
        config.save();
        return true;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Encode la config en JSON, la copie au clipboard ET écrit sur disque
     * dans {@code config/reborn-hud-exports/export-YYYYMMDD-HHMMSS.json}.
     */
    private void exportConfigToClipboard() {
        try {
            HudConfigSnapshot snap = HudConfigSnapshot.capture(config);
            String json = new Gson().toJson(snap);
            String encoded = "reborn-hud-v1:" + Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8));
            Minecraft.getInstance().keyboard.setClipboard(encoded);

            // Écriture disque dans ~/.minecraft/config/reborn-hud-exports/
            java.nio.file.Path exportDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("reborn-hud-exports");
            java.nio.file.Files.createDirectories(exportDir);
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                .format(new java.util.Date());
            java.nio.file.Path file = exportDir.resolve("export-" + stamp + ".json");
            java.nio.file.Files.writeString(file, json);

            LOGGER.info("config exportee : clipboard + {}", file);
            showToast("Exporté · clipboard + fichier ✓");
        } catch (java.io.IOException | RuntimeException e) {
            LOGGER.warn("export config echec : {}", e.getMessage());
            showToast("Échec export : " + e.getMessage());
        }
    }

    /** Lit le clipboard, decode base64, parse JSON, applique. */
    private void importConfigFromClipboard() {
        try {
            String content = Minecraft.getInstance().keyboard.getClipboard();
            if (content == null || content.isBlank()) {
                LOGGER.warn("clipboard vide");
                showToast("Presse-papier vide");
                return;
            }
            if (!content.startsWith("reborn-hud-v1:")) {
                LOGGER.warn("format clipboard invalide (header missing)");
                showToast("Format invalide (header manquant)");
                return;
            }
            String b64 = content.substring("reborn-hud-v1:".length()).trim();
            byte[] decoded = Base64.getDecoder().decode(b64);
            String json = new String(decoded, StandardCharsets.UTF_8);
            HudConfigSnapshot snap = new Gson().fromJson(json, HudConfigSnapshot.class);
            if (snap == null) {
                LOGGER.warn("decode JSON vide");
                showToast("Config vide ou invalide");
                return;
            }
            history.push(HudConfigSnapshot.capture(config));
            snap.applyTo(config);
            LOGGER.info("config importee depuis clipboard");
            showToast("Importé ✓ (Ctrl+Z pour annuler)");
        } catch (RuntimeException e) {
            LOGGER.warn("import config echec : {}", e.getMessage());
            showToast("Échec import : " + e.getMessage());
        }
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
