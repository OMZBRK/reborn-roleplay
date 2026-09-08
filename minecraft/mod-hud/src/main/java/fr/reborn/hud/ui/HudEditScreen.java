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
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.ui.style.FlatRect;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.Minecraft;
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
 * Éditeur HUD — version <b>sobre</b> façon Zenkai (cf {@code screenmodifhudzenkai.png}).
 *
 * <p>Deux zones :
 * <ul>
 *   <li><b>Panneau droit</b> ({@link HudEditSidePanel}), <b>repliable</b> : liste
 *       scrollable des éléments (œil + nom), inspecteur de l'élément sélectionné
 *       (X / Y / taille au clavier), presets nommables, Tout réinitialiser /
 *       Appliquer. Replié, il ne laisse qu'une languette et le canvas récupère
 *       toute la largeur.</li>
 *   <li><b>Canvas</b> (à gauche du panneau) : boîtes fines draggables (déplacer),
 *       molette = échelle, poignée BR = resize. Plus de grille de points ni de
 *       chrome — juste un voile sombre subtil.</li>
 * </ul>
 *
 * <p>Raccourcis : Ctrl+Z/Y annuler/refaire, flèches = nudge (Shift ×10),
 * Ctrl+E/I export/import config, Ctrl+M paramètres chat, Échap fermer.
 */
public class HudEditScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/editor");

    private static final int RESIZE_HANDLE_SIZE = 9;
    private static final float SCALE_STEP = 0.05f;

    private final Screen parent;
    private final HudConfig config;
    private final HudEditSidePanel sidePanel;

    private HudElement draggedElement = null;
    private HudElement selectedElement = HudElement.CHAT;
    private final Set<HudElement> selectedElements = EnumSet.of(HudElement.CHAT);
    private int dragOffsetX = 0, dragOffsetY = 0;

    private boolean draggingResize = false;
    private HudElementBounds resizeStartBounds = null;
    private float resizeStartScale = 1.0f;
    /** Écart curseur ↔ coin bas-droit au moment de la saisie de la poignée : sans
     *  lui, le premier pixel de drag faisait sauter l'échelle (la poignée est
     *  centrée SUR le coin, donc dx < largeur au moment du clic). */
    private int resizeGrabDX = 0, resizeGrabDY = 0;

    private final HudHistory history = new HudHistory();
    private HudConfigSnapshot snapshotBeforeAction = null;
    private List<AlignmentGuides.Guide> activeGuides = List.of();

    private EditBox presetNameField;

    // Inspecteur : saisie chiffrée de la position et de la taille de l'élément
    // sélectionné (demande staff : « pouvoir mettre la taille que je veux en
    // écrivant la taille »). La taille est en POURCENT, plus lisible que x0,85.
    private EditBox fieldX, fieldY, fieldScale;
    /** Vrai pendant qu'on recopie la config dans les champs → le responder
     *  ne doit pas ré-appliquer ce qu'il vient de lire (boucle). */
    private boolean syncingFields = false;
    /** Debounce de l'historique : une frappe continue = une seule entrée d'undo. */
    private long lastFieldEditMs = 0L;

    public HudEditScreen(Screen parent) {
        super(Component.translatable("reborn-hud.screen.title"));
        this.parent = parent;
        this.config = RebornHudClient.config();
        this.sidePanel = new HudEditSidePanel(config, Minecraft.getInstance().font);
        this.sidePanel.setSelectedElement(this.selectedElement);
        this.sidePanel.onClose = this::onClose;
        this.sidePanel.onApply = () -> { config.save(); onClose(); };
        this.sidePanel.onResetAll = () -> { config.resetAll(); config.save(); showToast("HUD réinitialisé"); };
        this.sidePanel.onMutation = () -> history.push(HudConfigSnapshot.capture(config));
        this.sidePanel.onToast = this::showToast;
        this.sidePanel.onSelect = this::selectElement;
        this.sidePanel.onSavePreset = this::savePresetFromField;
        this.sidePanel.onOpenChatSettings = this::openChatSettings;
        this.sidePanel.onResetSelected = this::resetSelectedElement;
        this.sidePanel.onToggleCollapse = () -> {
            sidePanel.layout(this.width, this.height);
            updateWidgetVisibility();
        };
    }

    @Override
    protected void init() {
        sidePanel.layout(this.width, this.height);
        int[] r = sidePanel.presetInputRect();
        presetNameField = new EditBox(this.font, r[0], r[1], r[2], r[3], Component.literal("Nom du preset…"));
        presetNameField.setBordered(false);
        presetNameField.setMaxLength(24);
        presetNameField.setTextColor(Colors.FOREGROUND);
        this.addRenderableWidget(presetNameField);

        int[][] fr = sidePanel.inspectorFieldRects();
        fieldX = numericField(fr[0], 5, v -> applyPositionField(v, true));
        fieldY = numericField(fr[1], 5, v -> applyPositionField(v, false));
        fieldScale = numericField(fr[2], 3, this::applyScaleField);
        this.addRenderableWidget(fieldX);
        this.addRenderableWidget(fieldY);
        this.addRenderableWidget(fieldScale);

        syncInspectorFields();
        updateWidgetVisibility();
    }

    /** Champ de saisie entier (signe autorisé), appliqué à chaque frappe valide. */
    private EditBox numericField(int[] r, int maxDigits, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, r[0], r[1], r[2], r[3], Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxDigits + 1);
        box.setTextColor(Colors.FOREGROUND);
        // On accepte l'état intermédiaire "" et "-" pour ne pas bloquer la frappe ;
        // applyXxx ignore simplement ce qui ne parse pas.
        box.setFilter(v -> isNumericDraft(v, maxDigits));
        box.setResponder(v -> { if (!syncingFields) onChange.accept(v); });
        return box;
    }

    /** Saisie en cours acceptable : "", "-", ou jusqu'à {@code maxDigits} chiffres. */
    private static boolean isNumericDraft(String v, int maxDigits) {
        int digits = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '-') {
                if (i != 0) return false;
                continue;
            }
            if (c < '0' || c > '9') return false;
            if (++digits > maxDigits) return false;
        }
        return true;
    }

    /** Recopie l'état de l'élément sélectionné dans les champs NON focus. */
    private void syncInspectorFields() {
        if (fieldX == null || selectedElement == null) return;
        HudElementState st = config.stateOf(selectedElement);
        syncingFields = true;
        if (!fieldX.isFocused()) fieldX.setValue(String.valueOf(st.x()));
        if (!fieldY.isFocused()) fieldY.setValue(String.valueOf(st.y()));
        if (!fieldScale.isFocused()) fieldScale.setValue(String.valueOf(Math.round(st.scale() * 100f)));
        syncingFields = false;
    }

    /** Panneau replié → aucun champ de saisie visible ni cliquable. */
    private void updateWidgetVisibility() {
        boolean visible = !sidePanel.isCollapsed();
        for (EditBox box : new EditBox[]{presetNameField, fieldX, fieldY, fieldScale}) {
            if (box == null) continue;
            box.setVisible(visible);
            if (!visible) box.setFocused(false);
        }
    }

    /** Historique debouncé : une rafale de frappes ne crée qu'une entrée d'undo. */
    private void pushFieldHistory() {
        long now = System.currentTimeMillis();
        if (now - lastFieldEditMs > 800L) history.push(HudConfigSnapshot.capture(config));
        lastFieldEditMs = now;
    }

    private void applyPositionField(String raw, boolean isX) {
        if (selectedElement == null) return;
        Integer v = parseIntOrNull(raw);
        if (v == null) return;
        pushFieldHistory();
        HudElementState st = config.stateOf(selectedElement);
        config.setState(selectedElement, isX ? st.withPos(v, st.y()) : st.withPos(st.x(), v));
        config.save();
    }

    /**
     * Taille saisie en pourcent. On <b>épingle le coin haut-gauche</b> : sans ça,
     * l'élément se téléporte en grandissant (l'offset est mesuré depuis l'ancre,
     * qui n'est le coin haut-gauche que pour TOP_LEFT).
     */
    private void applyScaleField(String raw) {
        if (selectedElement == null) return;
        Integer pct = parseIntOrNull(raw);
        // 25 % = plancher de HudElementState ; en dessous c'est une frappe en cours
        // ("1" de "150"), qu'on ignore plutôt que d'écraser la valeur.
        if (pct == null || pct < 25) return;
        pushFieldHistory();
        HudElementState st = config.stateOf(selectedElement);
        HudElementBounds before = HudElementBounds.currentFor(selectedElement, st, this.width, this.height);
        HudElementState scaled = st.withScale(pct / 100f);
        int[] off = HudElementBounds.offsetForTopLeft(
            selectedElement, scaled, this.width, this.height, before.x(), before.y());
        config.setState(selectedElement, scaled.withPos(off[0], off[1]));
        config.save();
    }

    private static Integer parseIntOrNull(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;   // "" ou "-" pendant la frappe
        }
    }

    /** Remet l'élément sélectionné à son placement d'origine (bouton « Réinit. »). */
    private void resetSelectedElement() {
        if (selectedElement == null) return;
        history.push(HudConfigSnapshot.capture(config));
        config.setState(selectedElement, selectedElement.defaultState());
        config.save();
        showToast(selectedElement.displayName() + " réinitialisé");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Pas de blur vanilla : on veut voir le HUD derrière clairement.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int editRight = sidePanel.leftEdge();

        // 1) Voile sombre subtil sur le canvas (pas de grille de points).
        ctx.fill(0, 0, editRight, this.height, 0x40000000);

        // 2) Boîtes HUD (dessinées avant le panneau : le panneau opaque couvre
        //    tout débordement à droite).
        HudElement hovered = elementUnderMouse(mouseX, mouseY);
        for (HudElement e : HudElement.EDITABLE) {
            if (e == draggedElement) continue;
            renderHudBox(ctx, e, hovered == e,
                selectedElements.contains(e) || e == selectedElement, false, mouseX, mouseY);
        }
        if (draggedElement != null) {
            renderHudBox(ctx, draggedElement, false, true, true, mouseX, mouseY);
        }

        // 3) Guides d'alignement pendant un drag-move.
        if (!activeGuides.isEmpty() && draggedElement != null && !draggingResize) {
            AlignmentGuides.renderGuides(ctx, activeGuides, this.width, this.height, this.font);
        }

        // 4) Panneau latéral permanent.
        sidePanel.render(ctx, mouseX, mouseY);

        // 4bis) L'élément sélectionné/dragué situé SOUS le panneau (ex. scoreboard
        //       au bord droit) est redessiné PAR-DESSUS pour rester visible et
        //       saisissable.
        HudElement onTop = draggedElement != null ? draggedElement : selectedElement;
        if (onTop != null) {
            HudElementBounds ob = HudElementBounds.currentFor(onTop, config.stateOf(onTop), this.width, this.height);
            if (ob.right() > sidePanel.leftEdge()) {
                renderHudBox(ctx, onTop, false, true, draggedElement == onTop, mouseX, mouseY);
            }
        }

        // 5) Champs de l'inspecteur : ils suivent l'élément sélectionné et le drag
        //    en cours (sauf celui qu'on est en train d'éditer au clavier).
        syncInspectorFields();

        // 6) Widgets vanilla (texte des EditBox).
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // 7) Placeholder du champ preset si vide et non focus.
        if (!sidePanel.isCollapsed() && presetNameField != null
                && presetNameField.getValue().isBlank() && !presetNameField.isFocused()) {
            int[] r = sidePanel.presetInputRect();
            HudEditSidePanel.arcText(ctx, this.font, "Nom du preset", r[0], r[1], Colors.FOREGROUND_MUTED);
        }

        // 7) Feedback.
        renderLiveCoordsPill(ctx, mouseX, mouseY);
        renderToast(ctx);
    }

    // ─────────── Canvas boxes ───────────

    private void renderHudBox(GuiGraphicsExtractor ctx, HudElement element, boolean hovered, boolean selected,
                              boolean dragging, int mouseX, int mouseY) {
        HudElementState state = config.stateOf(element);
        HudElementBounds b = HudElementBounds.currentFor(element, state, this.width, this.height);
        int bx = b.x(), by = b.y(), bw = b.width(), bh = b.height();
        boolean hidden = !state.visible();
        boolean active = hovered || selected || dragging;

        // Idle : simple outline fin.
        if (!active) {
            int col = hidden
                ? RebornColors.withAlpha(Colors.DANGER, 0x66)
                : RebornColors.withAlpha(Colors.FOREGROUND_MUTED, 0x40);
            FlatRect.border(ctx, bx, by, bw, bh, 3, col);
            return;
        }

        // Actif : outline accent (ou danger si masqué) + léger fill + label.
        int border = hidden ? Colors.DANGER
                   : dragging || selected ? Colors.ACCENT
                   : Colors.ACCENT_HOVER;
        FlatRect.fill(ctx, bx, by, bw, bh, 3, RebornColors.withAlpha(border, 0x14));
        if (dragging || selected) {
            FlatRect.borderThick(ctx, bx, by, bw, bh, 3, border);
        } else {
            FlatRect.border(ctx, bx, by, bw, bh, 3, border);
        }

        renderBoxLabel(ctx, element, state, bx, by, bw, bh);

        // Poignée resize BR uniquement sur l'élément sélectionné/dragué.
        if (selected || dragging) {
            int[] h = resizeHandleRect(b);
            if (h != null) FlatRect.fill(ctx, h[0], h[1], h[2], h[3], 2, Colors.ACCENT);
        }
    }

    /**
     * Rect {x,y,w,h} de la poignée de redimensionnement (coin bas-droit), ou
     * {@code null} si la box est trop petite pour en porter une.
     *
     * <p>Une seule définition partagée par le rendu ET le hit-test : elles
     * divergeaient, d'où une poignée visible mais pas saisissable.
     */
    private static int[] resizeHandleRect(HudElementBounds b) {
        if (b.width() < RESIZE_HANDLE_SIZE * 2 || b.height() < RESIZE_HANDLE_SIZE * 2) return null;
        return new int[]{
            b.x() + b.width() - RESIZE_HANDLE_SIZE / 2 - 1,
            b.y() + b.height() - RESIZE_HANDLE_SIZE / 2 - 1,
            RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE
        };
    }

    /**
     * Élément dont la poignée de resize est sous le curseur. On ne teste QUE la
     * sélection : ce sont les seules poignées dessinées. Avant, le hit-test
     * portait sur l'élément survolé — dès qu'une autre box recouvrait le coin
     * (chat / hotbar / cooldowns se chevauchent en bas d'écran), le clic partait
     * en déplacement au lieu du redimensionnement.
     */
    private HudElement resizeHandleUnderMouse(int mx, int my) {
        for (HudElement e : selectedElements) {
            if (onResizeHandle(e, mx, my)) return e;
        }
        return selectedElement != null && onResizeHandle(selectedElement, mx, my) ? selectedElement : null;
    }

    private boolean onResizeHandle(HudElement e, int mx, int my) {
        int[] h = resizeHandleRect(
            HudElementBounds.currentFor(e, config.stateOf(e), this.width, this.height));
        return h != null && inside(mx, my, h[0], h[1], h[2], h[3]);
    }

    /** Démarre un redimensionnement sur {@code element} (poignée déjà validée). */
    private void beginResize(HudElement element, int mouseX, int mouseY) {
        HudElementState state = config.stateOf(element);
        HudElementBounds bounds = HudElementBounds.currentFor(element, state, this.width, this.height);
        snapshotBeforeAction = HudConfigSnapshot.capture(config);
        selectElement(element);
        draggedElement = element;
        draggingResize = true;
        resizeStartBounds = bounds;
        resizeStartScale = state.scale();
        resizeGrabDX = mouseX - bounds.right();
        resizeGrabDY = mouseY - bounds.bottom();
    }

    private void renderBoxLabel(GuiGraphicsExtractor ctx, HudElement element, HudElementState state,
                                int bx, int by, int bw, int bh) {
        String nameS = element.displayName();
        String coordS = state.visible()
            ? String.format("%+d, %+d  x%.2f", state.x(), state.y(), state.scale())
            : "masque";
        int nameW = HudEditSidePanel.arcW(this.font, nameS);
        int coordW = HudEditSidePanel.arcW(this.font, coordS);
        int pillW = nameW + coordW + 18;
        int pillH = 12;
        int px = bx;
        int py = by - pillH - 3;
        if (py < 2) py = Math.min(by + bh + 3, this.height - pillH - 2);

        FlatRect.fill(ctx, px, py, pillW, pillH, 3, Colors.SURFACE_ELEVATED);
        FlatRect.border(ctx, px, py, pillW, pillH, 3, Colors.BORDER);
        HudEditSidePanel.arcText(ctx, this.font, nameS,
            px + 6, py + 3, state.visible() ? Colors.FOREGROUND : Colors.DANGER);
        HudEditSidePanel.arcText(ctx, this.font, coordS,
            px + 6 + nameW + 6, py + 3, Colors.FOREGROUND_SUBTLE);
    }

    private HudElement elementUnderMouse(int mouseX, int mouseY) {
        if (mouseX >= sidePanel.leftEdge()) return null; // zone panneau
        if (draggedElement != null) return draggedElement;
        HudElement[] values = HudElement.EDITABLE;
        for (int i = values.length - 1; i >= 0; i--) {
            HudElement e = values[i];
            HudElementBounds b = HudElementBounds.currentFor(e, config.stateOf(e), this.width, this.height);
            if (b.contains(mouseX, mouseY)) return e;
        }
        return null;
    }

    /**
     * Permet de saisir (drag) l'élément SÉLECTIONNÉ quand sa box déborde SOUS le
     * panneau (ex. scoreboard au bord droit). Ne se déclenche que dans ce cas —
     * n'interfère pas avec les clics normaux du panneau pour les autres éléments.
     */
    private boolean reborn$tryDragSelectedUnderPanel(int mouseX, int mouseY) {
        if (selectedElement == null) return false;
        HudElementBounds b = HudElementBounds.currentFor(
            selectedElement, config.stateOf(selectedElement), this.width, this.height);
        if (b.right() <= sidePanel.leftEdge()) return false; // pas sous le panneau
        // Poignée d'abord : elle est dessinée sur le coin, donc souvent pile sous
        // le panneau pour un élément ancré à droite.
        if (onResizeHandle(selectedElement, mouseX, mouseY)) {
            beginResize(selectedElement, mouseX, mouseY);
            return true;
        }
        if (!b.contains(mouseX, mouseY)) return false;
        snapshotBeforeAction = HudConfigSnapshot.capture(config);
        draggedElement = selectedElement;
        draggingResize = false;
        dragOffsetX = mouseX - b.x();
        dragOffsetY = mouseY - b.y();
        return true;
    }

    // ─────────── Mouse ───────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        boolean inPanel = mouseX >= sidePanel.leftEdge();

        // Panneau : clic droit = suppr preset, clic gauche = hit targets.
        if (inPanel) {
            if (button == 1) { sidePanel.handleRightClick(mouseX, mouseY); return true; }
            if (button == 0) {
                if (super.mouseClicked(event, doubleClick)) return true; // EditBox focus
                // Élément sélectionné situé SOUS le panneau (ex. scoreboard) →
                // priorité au drag de sa box (sinon impossible à saisir).
                if (reborn$tryDragSelectedUnderPanel((int) mouseX, (int) mouseY)) return true;
                sidePanel.handleClick(mouseX, mouseY);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (button != 0) return super.mouseClicked(event, doubleClick);
        if (super.mouseClicked(event, doubleClick)) return true;

        // Canvas : défocus les champs de saisie au clic hors panneau.
        clearFieldFocus();

        // Poignée resize BR : prioritaire sur tout le reste, et testée sur la
        // SÉLECTION (seules poignées dessinées) plutôt que sur la box survolée.
        HudElement handleTarget = resizeHandleUnderMouse((int) mouseX, (int) mouseY);
        if (handleTarget != null) {
            beginResize(handleTarget, (int) mouseX, (int) mouseY);
            return true;
        }

        HudElement element = elementUnderMouse((int) mouseX, (int) mouseY);
        if (element == null) return false;

        HudElementBounds bounds = HudElementBounds.currentFor(
            element, config.stateOf(element), this.width, this.height);

        // Shift+clic = multi-sélection.
        if (rebornHasShiftDown()) {
            if (selectedElements.contains(element)) {
                selectedElements.remove(element);
                if (!selectedElements.isEmpty()) selectedElement = selectedElements.iterator().next();
            } else {
                selectedElements.add(element);
                selectedElement = element;
            }
            sidePanel.setSelectedElement(selectedElement);
            return true;
        }

        // Clic simple : si l'élément saisi fait DÉJÀ partie d'une multi-sélection, on
        // GARDE le groupe (drag groupé) ; sinon sélection unique. Sans ça, le clic de
        // saisie vidait la sélection (selectElement) → groupe impossible à déplacer.
        snapshotBeforeAction = HudConfigSnapshot.capture(config);
        if (selectedElements.size() > 1 && selectedElements.contains(element)) {
            selectedElement = element;                 // ancre du drag, sans vider le groupe
            sidePanel.setSelectedElement(element);
        } else {
            selectElement(element);                    // sélection unique
        }
        draggedElement = element;
        draggingResize = false;
        dragOffsetX = (int) (mouseX - bounds.x());
        dragOffsetY = (int) (mouseY - bounds.y());
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double mouseX = event.x(), mouseY = event.y();
        if (event.button() != 0 || draggedElement == null) return super.mouseDragged(event, dx, dy);
        HudElementState state = config.stateOf(draggedElement);

        if (draggingResize) {
            // On raisonne sur le point saisi (curseur - écart au coin), pas sur le
            // curseur brut : sinon l'échelle sautait dès le 1er pixel de drag.
            int dxFromTL = (int) mouseX - resizeGrabDX - resizeStartBounds.x();
            int dyFromTL = (int) mouseY - resizeGrabDY - resizeStartBounds.y();
            int origW = Math.max(8, resizeStartBounds.width());
            int origH = Math.max(8, resizeStartBounds.height());
            float ratio = Math.max(dxFromTL / (float) origW, dyFromTL / (float) origH);
            HudElementState scaled = state.withScale(Math.max(0.05f, ratio) * resizeStartScale);
            // Le coin haut-gauche reste où il est : un resize ne doit jamais
            // déplacer l'élément, quel que soit son anchor.
            int[] off = HudElementBounds.offsetForTopLeft(draggedElement, scaled,
                this.width, this.height, resizeStartBounds.x(), resizeStartBounds.y());
            config.setState(draggedElement, scaled.withPos(off[0], off[1]));
            return true;
        }

        int targetX = (int) (mouseX - dragOffsetX);
        int targetY = (int) (mouseY - dragOffsetY);
        HudElementBounds curBounds = HudElementBounds.currentFor(draggedElement, state, this.width, this.height);
        AlignmentGuides.SnapResult snap = AlignmentGuides.compute(
            targetX, targetY, curBounds.width(), curBounds.height(), draggedElement,
            e -> HudElementBounds.currentFor(e, config.stateOf(e), this.width, this.height),
            this.width, this.height, rebornHasShiftDown());
        this.activeGuides = snap.guides();

        // Offset mesuré depuis l'ANCRE et à l'échelle courante : soustraire
        // vanilla.x() marchait par hasard à l'échelle 1 et faisait glisser la box
        // sous le curseur dès qu'elle était scalée (Vitals, Cooldowns, Endurance
        // le sont par défaut).
        int[] newOffset = HudElementBounds.offsetForTopLeft(
            draggedElement, state, this.width, this.height, snap.newX(), snap.newY());
        int newOffsetX = newOffset[0];
        int newOffsetY = newOffset[1];
        int deltaX = newOffsetX - state.x();
        int deltaY = newOffsetY - state.y();
        if (selectedElements.size() > 1 && selectedElements.contains(draggedElement)) {
            for (HudElement e : selectedElements) {
                config.setState(e, config.stateOf(e).withDelta(deltaX, deltaY));
            }
        } else {
            config.setState(draggedElement, state.withPos(newOffsetX, newOffsetY));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0 && draggedElement != null) {
            config.save();
            if (snapshotBeforeAction != null) { history.push(snapshotBeforeAction); snapshotBeforeAction = null; }
            draggedElement = null;
            draggingResize = false;
            activeGuides = List.of();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        // Molette au-dessus de la liste d'éléments = défilement de la liste.
        if (sidePanel.handleScroll(mouseX, mouseY, vAmount)) return true;
        HudElement element = elementUnderMouse((int) mouseX, (int) mouseY);
        if (element == null) return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        history.push(HudConfigSnapshot.capture(config));
        HudElementState state = config.stateOf(element);
        config.setState(element, state.withScale(state.scale() + (float) vAmount * SCALE_STEP));
        config.save();
        return true;
    }

    // ─────────── Keyboard ───────────

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();

        // Champ preset focus : Entrée = enregistrer, sinon laisse la saisie.
        if (presetNameField != null && presetNameField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                savePresetFromField();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { presetNameField.setFocused(false); return true; }
            return super.keyPressed(event);
        }

        // Champ numérique de l'inspecteur focus : la saisie prime (sinon les
        // flèches nudgeraient l'élément au lieu de déplacer le curseur de texte).
        EditBox numeric = focusedNumericField();
        if (numeric != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                numeric.setFocused(false);
                syncInspectorFields();
                return true;
            }
            return super.keyPressed(event);
        }

        if (rebornHasCtrlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_W) {
                HudConfigSnapshot prev = history.undo(HudConfigSnapshot.capture(config));
                if (prev != null) { prev.applyTo(config); showToast("Annulé (" + history.undoDepth() + " restants)"); }
                else showToast("Rien à annuler");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                HudConfigSnapshot next = history.redo(HudConfigSnapshot.capture(config));
                if (next != null) { next.applyTo(config); showToast("Refait"); }
                else showToast("Rien à refaire");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_E) { exportConfigToClipboard(); return true; }
            if (keyCode == GLFW.GLFW_KEY_I) { importConfigFromClipboard(); return true; }
            if (keyCode == GLFW.GLFW_KEY_M) { openChatSettings(); return true; }
        }

        if (keyCode == GLFW.GLFW_KEY_F1) {
            Minecraft.getInstance().setScreenAndShow(new HudHelpScreen(this));
            return true;
        }

        // Flèches = nudge l'élément sélectionné (Shift ×10).
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
                if (selectedElements.size() > 1) {
                    for (HudElement e : selectedElements) config.setState(e, config.stateOf(e).withDelta(dx, dy));
                } else {
                    config.setState(selectedElement, config.stateOf(selectedElement).withDelta(dx, dy));
                }
                config.save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** Champ numérique de l'inspecteur actuellement focus, ou {@code null}. */
    private EditBox focusedNumericField() {
        for (EditBox box : new EditBox[]{fieldX, fieldY, fieldScale}) {
            if (box != null && box.isFocused()) return box;
        }
        return null;
    }

    private void clearFieldFocus() {
        for (EditBox box : new EditBox[]{presetNameField, fieldX, fieldY, fieldScale}) {
            if (box != null) box.setFocused(false);
        }
    }

    private static boolean rebornHasShiftDown() {
        com.mojang.blaze3d.platform.Window w = Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean rebornHasCtrlDown() {
        com.mojang.blaze3d.platform.Window w = Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    // ─────────── Actions ───────────

    public void openChatSettings() {
        Minecraft.getInstance().setScreenAndShow(new ChatSettingsScreen(this));
    }

    private void selectElement(HudElement element) {
        this.selectedElement = element;
        this.selectedElements.clear();
        this.selectedElements.add(element);
        this.sidePanel.setSelectedElement(element);
        // Sinon la frappe en cours s'appliquerait au NOUVEL élément sélectionné.
        clearFieldFocus();
        syncInspectorFields();
    }

    private void savePresetFromField() {
        String name = presetNameField == null ? "" : presetNameField.getValue().trim();
        if (name.isBlank()) { showToast("Nomme d'abord le preset"); return; }
        history.push(HudConfigSnapshot.capture(config));
        config.saveAsNewPreset(name);
        presetNameField.setValue("");
        presetNameField.setFocused(false);
        showToast("Preset « " + name + " » enregistré");
    }

    private void exportConfigToClipboard() {
        try {
            HudConfigSnapshot snap = HudConfigSnapshot.capture(config);
            String json = new Gson().toJson(snap);
            String encoded = "reborn-hud-v1:" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            Minecraft.getInstance().keyboardHandler.setClipboard(encoded);
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("reborn-hud-exports");
            java.nio.file.Files.createDirectories(dir);
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            java.nio.file.Files.writeString(dir.resolve("export-" + stamp + ".json"), json);
            showToast("Exporté · presse-papier + fichier ✓");
        } catch (java.io.IOException | RuntimeException e) {
            LOGGER.warn("export config echec : {}", e.getMessage());
            showToast("Échec export : " + e.getMessage());
        }
    }

    private void importConfigFromClipboard() {
        try {
            String content = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (content == null || content.isBlank() || !content.startsWith("reborn-hud-v1:")) {
                showToast("Presse-papier vide ou format invalide");
                return;
            }
            String b64 = content.substring("reborn-hud-v1:".length()).trim();
            String json = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            HudConfigSnapshot snap = new Gson().fromJson(json, HudConfigSnapshot.class);
            if (snap == null) { showToast("Config vide ou invalide"); return; }
            history.push(HudConfigSnapshot.capture(config));
            snap.applyTo(config);
            showToast("Importé ✓ (Ctrl+Z pour annuler)");
        } catch (RuntimeException e) {
            LOGGER.warn("import config echec : {}", e.getMessage());
            showToast("Échec import : " + e.getMessage());
        }
    }

    // ─────────── Toast + live coords ───────────

    private String toastText = null;
    private long toastShownAtMs = 0L;
    private static final long TOAST_DURATION_MS = 2400, TOAST_FADE_IN_MS = 180, TOAST_FADE_OUT_MS = 400;

    private void showToast(String text) {
        this.toastText = text;
        this.toastShownAtMs = System.currentTimeMillis();
    }

    private void renderToast(GuiGraphicsExtractor ctx) {
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastShownAtMs;
        if (age > TOAST_DURATION_MS) { toastText = null; return; }
        float opacity = age < TOAST_FADE_IN_MS ? age / (float) TOAST_FADE_IN_MS
            : age > TOAST_DURATION_MS - TOAST_FADE_OUT_MS ? (TOAST_DURATION_MS - age) / (float) TOAST_FADE_OUT_MS
            : 1f;
        int alpha = (int) (opacity * 255);
        int pillW = HudEditSidePanel.arcW(this.font, toastText) + 24, pillH = 20;
        int pillX = (sidePanel.leftEdge() - pillW) / 2;
        int pillY = this.height - 40;
        FlatRect.fill(ctx, pillX, pillY, pillW, pillH, 6,
            (alpha << 24) | (Colors.SURFACE_ELEVATED & 0x00FFFFFF));
        FlatRect.border(ctx, pillX, pillY, pillW, pillH, 6,
            (alpha << 24) | (Colors.ACCENT & 0x00FFFFFF));
        HudEditSidePanel.arcText(ctx, this.font, toastText,
            pillX + 12, pillY + 6, (alpha << 24) | (Colors.FOREGROUND & 0x00FFFFFF));
    }

    private void renderLiveCoordsPill(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (draggedElement == null || draggingResize) return;
        HudElementState state = config.stateOf(draggedElement);
        String raw = String.format("%+d, %+d", state.x(), state.y());
        if (selectedElements.size() > 1) raw = "(" + selectedElements.size() + ") " + raw;
        int w = HudEditSidePanel.arcW(this.font, raw) + 14, h = 15;
        int x = mouseX + 14, y = mouseY + 14;
        if (x + w > this.width - 4) x = mouseX - w - 14;
        if (y + h > this.height - 4) y = mouseY - h - 14;
        FlatRect.fill(ctx, x, y, w, h, 4, Colors.SURFACE_ELEVATED);
        FlatRect.border(ctx, x, y, w, h, 4, Colors.ACCENT);
        HudEditSidePanel.arcText(ctx, this.font, raw, x + 7, y + 4, Colors.FOREGROUND);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
