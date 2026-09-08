package fr.reborn.hud.ui;

import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.config.HudPresets;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import fr.reborn.hud.ui.style.IconTextures;
import fr.reborn.hud.ui.style.FlatRect;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panneau latéral droit de l'éditeur HUD, sobre façon Zenkai — carte flottante
 * insérée (marges autour), coins carrés, compact.
 *
 * <p>De haut en bas : header (titre + engrenage + repli + ✕), section ÉLÉMENTS
 * (liste <b>scrollable</b> œil + nom + badge d'échelle), <b>INSPECTEUR</b> de
 * l'élément sélectionné (X / Y / taille saisis au clavier + réinit.), section
 * PRESETS (champ nommable + Enregistrer + liste), footer épinglé (Tout
 * réinitialiser / Appliquer).
 *
 * <h2>Ce qui a changé (retours de test staff)</h2>
 * <ul>
 *   <li><b>Largeur 178 → {@value #WIDTH}</b> : à GUI Size 3–4 l'ancienne carte
 *       mangeait plus d'un tiers de la largeur utile et cachait tout le bord
 *       droit de l'écran (scoreboard compris).</li>
 *   <li><b>Repliable</b> ({@link #toggleCollapsed()}) : réduite à une languette
 *       de {@value #TAB_W} px, le canvas récupère toute la largeur — on juge le
 *       placement sans rien masquer, puis on rouvre.</li>
 *   <li><b>Liste scrollable</b> : plus de section écrasée / presets invisibles
 *       quand la hauteur utile est petite. Défilement par lignes entières, donc
 *       aucun clipping n'est nécessaire.</li>
 *   <li><b>Inspecteur</b> : saisie chiffrée de la position et de la taille, au
 *       lieu du seul drag + molette.</li>
 * </ul>
 */
public final class HudEditSidePanel {

    /** Largeur de la carte dépliée. */
    public static final int WIDTH = 148;
    /** Largeur de la languette quand le panneau est replié. */
    public static final int TAB_W = 14;
    public static final int MARGIN = 8;

    private static final int PAD = 8;
    private static final int CARD_R = 4;   // coin de la carte
    private static final int BTN_R = 2;    // coins carrés des boutons/lignes
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 28;
    private static final int LABEL_H = 11;
    private static final int ROW_H = 14;
    private static final int INPUT_H = 15;
    private static final int PRESET_ROW_H = 15;
    private static final int CLOSE_SZ = 12;
    private static final int EYE_SZ = 10;
    /** Lignes d'éléments minimum avant de rogner ailleurs. */
    private static final int MIN_LIST_ROWS = 3;
    /** Hauteur du bloc inspecteur : label + ligne X/Y + ligne taille/réinit. */
    private static final int INSPECTOR_H = LABEL_H + 3 + INPUT_H + 4 + INPUT_H;

    private final HudConfig config;
    private final Font tr;

    /** x de la carte dépliée — toujours calculé, même repliée, pour que les rects
     *  internes (dont ceux des EditBox de l'écran) restent valides. */
    private int x0;
    /** x de la languette quand la carte est repliée. */
    private int tabX;
    private int y0, height;
    private HudElement selectedElement = HudElement.CHAT;
    private boolean collapsed = false;

    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final java.util.Map<String, int[]> presetRects = new java.util.HashMap<>();

    // Positions calculées en layout() (indépendantes de la souris).
    private int presetInputX, presetInputY, presetInputW;
    private int presetListTop, footerTop;
    private int listTop, listRows;
    private int inspectorTop;
    private int scroll;                     // index de la première ligne affichée

    public Runnable onClose = () -> {};
    public Runnable onApply = () -> {};
    public Runnable onResetAll = () -> {};
    public Runnable onSavePreset = () -> {};
    /** Ouvre les réglages du chat (petit engrenage dans le header). */
    public Runnable onOpenChatSettings = () -> {};
    public Runnable onMutation = () -> {};
    /** Replie / déplie la carte — l'écran doit re-layouter ses champs de saisie. */
    public Runnable onToggleCollapse = () -> {};
    /** Remet l'élément sélectionné à son placement d'origine. */
    public Runnable onResetSelected = () -> {};
    public Consumer<String> onToast = s -> {};
    public Consumer<HudElement> onSelect = e -> {};

    public HudEditSidePanel(HudConfig config, Font tr) {
        this.config = config;
        this.tr = tr;
    }

    public void setSelectedElement(HudElement element) {
        this.selectedElement = element;
        revealSelected();
    }

    public HudElement selectedElement() { return selectedElement; }

    public boolean isCollapsed() { return collapsed; }

    public void toggleCollapsed() {
        this.collapsed = !this.collapsed;
        onToggleCollapse.run();
    }

    /** Bord gauche de la zone occupée (limite du canvas) — languette si replié. */
    public int leftEdge() { return collapsed ? tabX : x0; }

    /**
     * Recalcule TOUTE la géométrie, y compris repliée : les {@code EditBox} de
     * {@link HudEditScreen} sont positionnées une fois à l'{@code init()} et ne
     * bougent plus, donc un redimensionnement de fenêtre pendant que le panneau
     * est replié doit quand même mettre les rects à jour.
     */
    public void layout(int screenWidth, int screenHeight) {
        this.x0 = screenWidth - WIDTH - MARGIN;
        this.tabX = screenWidth - TAB_W - MARGIN;
        this.y0 = MARGIN;
        this.height = screenHeight - MARGIN * 2;
        this.footerTop = y0 + height - FOOTER_H;

        int contentTop = y0 + HEADER_H + 6;
        // Blocs de bas de carte, réservés d'abord : le bloc presets doit garder au
        // moins son champ + 2 lignes, sinon les presets deviennent inatteignables.
        int presetsMin = LABEL_H + 3 + INPUT_H + 5 + 2 * (PRESET_ROW_H + 3);
        int available = footerTop - contentTop - 6;   // marge avant le footer

        int listBudget = available - INSPECTOR_H - 8 - presetsMin - 8 - (LABEL_H + 3);
        int maxRows = HudElement.EDITABLE.length;
        int rows = Math.max(MIN_LIST_ROWS, Math.min(maxRows, listBudget / ROW_H));
        // Hauteur utile minuscule (petite fenêtre à GUI Size 4) : on rogne la liste
        // jusqu'à ce que le champ preset tienne AU-DESSUS du footer. Sans ce
        // garde-fou, presets et boutons se chevauchaient et devenaient incliquables.
        while (rows > 1 && presetInputYFor(contentTop, rows) + INPUT_H > footerTop - 4) rows--;
        this.listRows = rows;

        this.listTop = contentTop + LABEL_H + 3;
        this.inspectorTop = listTop + listRows * ROW_H + 8;

        this.presetInputY = presetInputYFor(contentTop, listRows);
        this.presetInputX = x0 + PAD;
        int saveW = arcW(tr, "Enregistrer") + 10;
        this.presetInputW = WIDTH - PAD * 2 - saveW - 4;
        this.presetListTop = presetInputY + INPUT_H + 5;

        clampScroll();
    }

    /** Y du champ « nom du preset » pour un nombre de lignes d'éléments donné. */
    private static int presetInputYFor(int contentTop, int rows) {
        return contentTop + LABEL_H + 3      // label ÉLÉMENTS
             + rows * ROW_H + 8              // liste
             + INSPECTOR_H + 8               // inspecteur
             + LABEL_H + 3;                  // label PRESETS
    }

    private void clampScroll() {
        int max = Math.max(0, HudElement.EDITABLE.length - listRows);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    /** Fait défiler la liste pour que l'élément sélectionné reste visible. */
    private void revealSelected() {
        if (listRows <= 0) return;
        int idx = indexOf(selectedElement);
        if (idx < 0) return;
        if (idx < scroll) scroll = idx;
        else if (idx >= scroll + listRows) scroll = idx - listRows + 1;
        clampScroll();
    }

    private static int indexOf(HudElement e) {
        HudElement[] all = HudElement.EDITABLE;
        for (int i = 0; i < all.length; i++) if (all[i] == e) return i;
        return -1;
    }

    /** Molette sur la liste d'éléments → défilement. Vrai si consommé. */
    public boolean handleScroll(double mouseX, double mouseY, double amount) {
        if (collapsed || listRows <= 0) return false;
        if (!inside((int) mouseX, (int) mouseY, x0, listTop, WIDTH, listRows * ROW_H)) return false;
        scroll -= (int) Math.signum(amount);
        clampScroll();
        return true;
    }

    /** Rect {x,y,w,h} du champ de saisie du nom de preset (pour l'EditBox). */
    public int[] presetInputRect() {
        return new int[]{presetInputX + 5, presetInputY + (INPUT_H - 8) / 2, presetInputW - 9, 8};
    }

    /**
     * Rects {x,y,w,h} des trois champs de l'inspecteur — X, Y, taille (en %) —
     * dans cet ordre. Les EditBox de l'écran s'y posent ; le cadre est dessiné ici.
     */
    public int[][] inspectorFieldRects() {
        int fieldW = (WIDTH - PAD * 2 - 4) / 2;
        int rowY = inspectorTop + LABEL_H + 3;
        int row2Y = rowY + INPUT_H + 4;
        int textY = (INPUT_H - 8) / 2;
        return new int[][]{
            {x0 + PAD + 13, rowY + textY, fieldW - 17, 8},                       // X
            {x0 + PAD + fieldW + 4 + 13, rowY + textY, fieldW - 17, 8},          // Y
            {x0 + PAD + 25, row2Y + textY, fieldW - 29, 8},                      // taille %
        };
    }

    /** Échelle de la police ArcadePix (native ~2× trop grosse pour ce panneau). */
    static final float ARC = 0.5f;

    /** Texte en police ArcadePix (comme le main-menu), majuscules ASCII + tronqué. */
    public static net.minecraft.network.chat.Component arc(String s) {
        return RebornFont.arcade(EscData.arcadeSafe(s, 42));
    }

    /** Largeur (px écran) d'un texte arcade rendu à l'échelle {@link #ARC}. */
    public static int arcW(Font tr, String s) {
        return Math.round(tr.width(arc(s)) * ARC);
    }

    /** Dessine un texte arcade à l'échelle {@link #ARC}, coin haut-gauche à (x,y). */
    public static void arcText(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(ARC, ARC);
        ctx.text(tr, arc(s), 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    // ──────────────────────────────────────────────
    // RENDER
    // ──────────────────────────────────────────────

    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        hitTargets.clear();
        presetRects.clear();

        if (collapsed) {
            renderCollapsedTab(ctx, mouseX, mouseY);
            return;
        }

        // Carte — fond éclairci pour la lisibilité (pas d'ombre pixellisée : elle
        // laissait des lignes/pixels parasites autour de la carte).
        FlatRect.fill(ctx, x0, y0, WIDTH, height, CARD_R, 0xF0301C22);
        FlatRect.border(ctx, x0, y0, WIDTH, height, CARD_R, Colors.BORDER_STRONG);

        renderHeader(ctx, mouseX, mouseY);

        int y = y0 + HEADER_H + 6;
        renderSectionLabel(ctx, "Elements", y);
        renderElementList(ctx, mouseX, mouseY);
        renderInspector(ctx, mouseX, mouseY);

        renderSectionLabel(ctx, "Presets", presetInputY - LABEL_H - 3);
        renderPresetInput(ctx, mouseX, mouseY);
        renderPresetList(ctx, mouseX, mouseY);

        renderFooter(ctx, mouseX, mouseY);
    }

    /** Languette repliée : une bande cliquable avec un chevron « ‹ » vers le canvas. */
    private void renderCollapsedTab(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        boolean hov = inside(mouseX, mouseY, tabX, y0, TAB_W, height);
        FlatRect.fill(ctx, tabX, y0, TAB_W, height, CARD_R, hov ? 0xF03D2229 : 0xF0301C22);
        FlatRect.border(ctx, tabX, y0, TAB_W, height, CARD_R, Colors.BORDER_STRONG);
        // Pastille accent + chevron d'ouverture, centrés verticalement.
        FlatRect.fill(ctx, tabX + (TAB_W - 5) / 2, y0 + 8, 5, 5, BTN_R, Colors.ACCENT);
        drawChevron(ctx, tabX + TAB_W / 2, y0 + height / 2, false,
            hov ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE);
        hitTargets.add(new HitTarget(tabX, y0, TAB_W, height, this::toggleCollapsed));
    }

    /** Chevron 5×9 : {@code pointRight=true} referme (›), sinon ouvre (‹). */
    private static void drawChevron(GuiGraphicsExtractor ctx, int cx, int cy,
                                    boolean pointRight, int color) {
        int dir = pointRight ? 1 : -1;
        for (int i = 0; i < 4; i++) {
            ctx.fill(cx + dir * (i - 1), cy - 4 + i, cx + dir * (i - 1) + 1, cy - 2 + i, color);
            ctx.fill(cx + dir * (i - 1), cy + 3 - i, cx + dir * (i - 1) + 1, cy + 5 - i, color);
        }
    }

    private void renderHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        FlatRect.fill(ctx, x0 + PAD, y0 + (HEADER_H - 6) / 2, 6, 6, BTN_R, Colors.ACCENT);
        arcText(ctx, tr, "Editeur d'interface",
            x0 + PAD + 10, y0 + (HEADER_H - tr.lineHeight) / 2 + 1, Colors.FOREGROUND);

        int cy = y0 + (HEADER_H - CLOSE_SZ) / 2;

        int cx = x0 + WIDTH - PAD - CLOSE_SZ;
        boolean hov = inside(mouseX, mouseY, cx, cy, CLOSE_SZ, CLOSE_SZ);
        if (hov) FlatRect.fill(ctx, cx, cy, CLOSE_SZ, CLOSE_SZ, BTN_R, Colors.DANGER_SOFT);
        HudEditChrome.renderIconGlyph(ctx, "close", cx + CLOSE_SZ / 2, cy + CLOSE_SZ / 2,
            hov ? Colors.DANGER : Colors.FOREGROUND_SUBTLE);
        hitTargets.add(new HitTarget(cx, cy, CLOSE_SZ, CLOSE_SZ, onClose));

        // Repli du panneau (chevron ›) — à gauche du ✕.
        int vx = cx - CLOSE_SZ - 2;
        boolean vHov = inside(mouseX, mouseY, vx, cy, CLOSE_SZ, CLOSE_SZ);
        if (vHov) FlatRect.fill(ctx, vx, cy, CLOSE_SZ, CLOSE_SZ, BTN_R, Colors.ACCENT_SOFT);
        drawChevron(ctx, vx + CLOSE_SZ / 2, cy + CLOSE_SZ / 2, true,
            vHov ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE);
        hitTargets.add(new HitTarget(vx, cy, CLOSE_SZ, CLOSE_SZ, this::toggleCollapsed));

        // Petit engrenage « réglages chat » à gauche du repli.
        int gx = vx - CLOSE_SZ - 2;
        boolean gHov = inside(mouseX, mouseY, gx, cy, CLOSE_SZ, CLOSE_SZ);
        if (gHov) FlatRect.fill(ctx, gx, cy, CLOSE_SZ, CLOSE_SZ, BTN_R, Colors.ACCENT_SOFT);
        IconTextures.draw(ctx, "gear", gx + 1, cy + 1, CLOSE_SZ - 2,
            gHov ? Colors.FOREGROUND : Colors.FOREGROUND_MUTED);
        hitTargets.add(new HitTarget(gx, cy, CLOSE_SZ, CLOSE_SZ, onOpenChatSettings));

        ctx.fill(x0 + PAD, y0 + HEADER_H, x0 + WIDTH - PAD, y0 + HEADER_H + 1, Colors.BORDER);
    }

    private void renderSectionLabel(GuiGraphicsExtractor ctx, String label, int y) {
        arcText(ctx, tr, label, x0 + PAD, y, Colors.FOREGROUND_MUTED);
    }

    // ─────────── Liste d'éléments (scrollable) ───────────

    private void renderElementList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        HudElement[] all = HudElement.EDITABLE;
        clampScroll();
        int y = listTop;
        for (int i = scroll; i < Math.min(all.length, scroll + listRows); i++) {
            renderElementRow(ctx, all[i], y, mouseX, mouseY);
            y += ROW_H;
        }
        // Barre de défilement fine à droite, seulement si ça déborde.
        if (all.length > listRows) {
            int trackH = listRows * ROW_H;
            int trackX = x0 + WIDTH - PAD + 2;
            ctx.fill(trackX, listTop, trackX + 1, listTop + trackH, Colors.BORDER);
            int thumbH = Math.max(6, trackH * listRows / all.length);
            int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, all.length - listRows);
            ctx.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, Colors.ACCENT);
        }
    }

    private void renderElementRow(GuiGraphicsExtractor ctx, HudElement e, int y, int mouseX, int mouseY) {
        HudElementState st = config.stateOf(e);
        int rowX = x0 + PAD - 3;
        int rowW = WIDTH - (PAD - 3) - PAD;
        boolean selected = e == selectedElement;
        boolean rowHover = inside(mouseX, mouseY, rowX, y - 2, rowW, ROW_H);

        if (selected) {
            FlatRect.fill(ctx, rowX, y - 2, rowW, ROW_H, BTN_R, Colors.ACCENT_SOFT);
            ctx.fill(rowX, y - 2, rowX + 2, y - 2 + ROW_H, Colors.ACCENT);
        } else if (rowHover) {
            FlatRect.fill(ctx, rowX, y - 2, rowW, ROW_H, BTN_R, 0x12FFFFFF);
        }

        int eyeX = x0 + PAD;
        int eyeY = y + (tr.lineHeight - EYE_SZ) / 2;
        boolean eyeHover = inside(mouseX, mouseY, eyeX - 2, eyeY - 2, EYE_SZ + 4, EYE_SZ + 4);
        int eyeColor = st.visible()
            ? (eyeHover ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE)
            : Colors.DANGER;
        IconTextures.draw(ctx, st.visible() ? "eye_open" : "eye_closed", eyeX, eyeY, EYE_SZ, eyeColor);

        int nameColor = st.visible()
            ? (selected ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE)
            : Colors.FOREGROUND_MUTED;
        arcText(ctx, tr, e.displayName(), eyeX + EYE_SZ + 5, y, nameColor);

        if (Math.abs(st.scale() - 1.0f) > 0.01f) {
            String bs = String.format("x%.2f", st.scale());
            arcText(ctx, tr, bs, x0 + WIDTH - PAD - arcW(tr, bs), y, Colors.FOREGROUND_MUTED);
        }

        hitTargets.add(new HitTarget(eyeX - 2, eyeY - 2, EYE_SZ + 6, EYE_SZ + 4, () -> toggleVisible(e)));
        hitTargets.add(new HitTarget(rowX, y - 2, rowW, ROW_H, () -> onSelect.accept(e)));
    }

    private void toggleVisible(HudElement e) {
        onMutation.run();
        HudElementState st = config.stateOf(e);
        config.setState(e, st.withVisible(!st.visible()));
        config.save();
    }

    // ─────────── Inspecteur de l'élément sélectionné ───────────

    /**
     * Cadres + libellés des champs numériques. Le TEXTE des champs est dessiné
     * par les {@code EditBox} de {@link HudEditScreen} (posées sur les rects de
     * {@link #inspectorFieldRects()}) — ici on ne peint que le chrome.
     */
    private void renderInspector(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        renderSectionLabel(ctx, selectedElement == null
            ? "Selection" : selectedElement.displayName(), inspectorTop);

        int fieldW = (WIDTH - PAD * 2 - 4) / 2;
        int rowY = inspectorTop + LABEL_H + 3;
        int row2Y = rowY + INPUT_H + 4;

        drawField(ctx, x0 + PAD, rowY, fieldW, "X");
        drawField(ctx, x0 + PAD + fieldW + 4, rowY, fieldW, "Y");
        drawField(ctx, x0 + PAD, row2Y, fieldW, "%");

        int resetX = x0 + PAD + fieldW + 4;
        boolean hov = inside(mouseX, mouseY, resetX, row2Y, fieldW, INPUT_H);
        FlatRect.fill(ctx, resetX, row2Y, fieldW, INPUT_H, BTN_R, hov ? 0x22FFFFFF : Colors.SURFACE);
        FlatRect.border(ctx, resetX, row2Y, fieldW, INPUT_H, BTN_R, Colors.BORDER);
        arcText(ctx, tr, "Reinit.", resetX + (fieldW - arcW(tr, "Reinit.")) / 2,
            row2Y + (INPUT_H - tr.lineHeight) / 2 + 1,
            hov ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE);
        hitTargets.add(new HitTarget(resetX, row2Y, fieldW, INPUT_H, onResetSelected));
    }

    /** Cadre d'un champ de saisie + son préfixe (X / Y / %). */
    private void drawField(GuiGraphicsExtractor ctx, int x, int y, int w, String prefix) {
        FlatRect.fill(ctx, x, y, w, INPUT_H, BTN_R, Colors.SURFACE);
        FlatRect.border(ctx, x, y, w, INPUT_H, BTN_R, Colors.BORDER);
        arcText(ctx, tr, prefix, x + 4, y + (INPUT_H - tr.lineHeight) / 2 + 1, Colors.FOREGROUND_MUTED);
    }

    // ─────────── Presets ───────────

    private void renderPresetInput(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        FlatRect.fill(ctx, presetInputX, presetInputY, presetInputW, INPUT_H, BTN_R, Colors.SURFACE);
        FlatRect.border(ctx, presetInputX, presetInputY, presetInputW, INPUT_H, BTN_R, Colors.BORDER);

        int saveX = presetInputX + presetInputW + 4;
        int saveW = x0 + WIDTH - PAD - saveX;
        boolean hov = inside(mouseX, mouseY, saveX, presetInputY, saveW, INPUT_H);
        FlatRect.fill(ctx, saveX, presetInputY, saveW, INPUT_H, BTN_R,
            hov ? Colors.ACCENT_HOVER : Colors.ACCENT);
        arcText(ctx, tr, "Enregistrer",
            saveX + (saveW - arcW(tr, "Enregistrer")) / 2, presetInputY + (INPUT_H - tr.lineHeight) / 2 + 1,
            Colors.FOREGROUND);
        hitTargets.add(new HitTarget(saveX, presetInputY, saveW, INPUT_H, onSavePreset));
    }

    private void renderPresetList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        String active = config.getActivePreset();
        int y = presetListTop;
        int maxBottom = footerTop - 4;

        int shown = 0, total = config.getPresets().size();
        for (String id : config.getPresets().keySet()) {
            if (y + PRESET_ROW_H > maxBottom) break;
            renderPresetRow(ctx, id, y, id.equals(active), mouseX, mouseY);
            y += PRESET_ROW_H + 3;
            shown++;
        }
        if (shown < total && y + tr.lineHeight <= maxBottom) {
            arcText(ctx, tr, "+ " + (total - shown) + " autres", x0 + PAD, y + 1, Colors.FOREGROUND_MUTED);
        }
    }

    private void renderPresetRow(GuiGraphicsExtractor ctx, String id, int y, boolean active,
                                 int mouseX, int mouseY) {
        int rowX = x0 + PAD;
        int rowW = WIDTH - PAD * 2;
        boolean hover = inside(mouseX, mouseY, rowX, y, rowW, PRESET_ROW_H);

        int bg = active ? Colors.ACCENT_SOFT : (hover ? 0x12FFFFFF : Colors.SURFACE);
        int border = active ? Colors.ACCENT : (hover ? Colors.BORDER_STRONG : Colors.BORDER);
        FlatRect.fill(ctx, rowX, y, rowW, PRESET_ROW_H, BTN_R, bg);
        FlatRect.border(ctx, rowX, y, rowW, PRESET_ROW_H, BTN_R, border);

        arcText(ctx, tr, HudPresets.displayName(id),
            rowX + 6, y + (PRESET_ROW_H - tr.lineHeight) / 2,
            active ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE);

        hitTargets.add(new HitTarget(rowX, y, rowW, PRESET_ROW_H, () -> applyPreset(id)));
        presetRects.put(id, new int[]{rowX, y, rowW, PRESET_ROW_H});
    }

    private void applyPreset(String id) {
        onMutation.run();
        config.applyPreset(id);
        onToast.accept("Preset « " + HudPresets.displayName(id) + " » appliqué");
    }

    /** Clic droit sur un preset → supprime (sauf Default). */
    public boolean handleRightClick(double mouseX, double mouseY) {
        for (var entry : presetRects.entrySet()) {
            int[] r = entry.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                String id = entry.getKey();
                if (HudPresets.DEFAULT.equals(id)) {
                    onToast.accept("Default ne peut pas être supprimé");
                    return true;
                }
                onMutation.run();
                config.deletePreset(id);
                onToast.accept("Preset « " + HudPresets.displayName(id) + " » supprimé");
                return true;
            }
        }
        return false;
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(x0 + PAD, footerTop, x0 + WIDTH - PAD, footerTop + 1, Colors.BORDER);

        int btnY = footerTop + (FOOTER_H - INPUT_H) / 2;
        int gap = 4;
        int applyW = 52;
        int resetW = WIDTH - PAD * 2 - applyW - gap;
        int resetX = x0 + PAD;
        int applyX = resetX + resetW + gap;

        boolean rHov = inside(mouseX, mouseY, resetX, btnY, resetW, INPUT_H);
        FlatRect.fill(ctx, resetX, btnY, resetW, INPUT_H, BTN_R,
            rHov ? Colors.DANGER_SOFT : Colors.SURFACE);
        FlatRect.border(ctx, resetX, btnY, resetW, INPUT_H, BTN_R,
            RebornColors.withAlpha(Colors.DANGER, 0x66));
        arcText(ctx, tr, "Reinitialiser",
            resetX + (resetW - arcW(tr, "Reinitialiser")) / 2, btnY + (INPUT_H - tr.lineHeight) / 2 + 1,
            Colors.DANGER);
        hitTargets.add(new HitTarget(resetX, btnY, resetW, INPUT_H, () -> { onMutation.run(); onResetAll.run(); }));

        boolean aHov = inside(mouseX, mouseY, applyX, btnY, applyW, INPUT_H);
        FlatRect.fill(ctx, applyX, btnY, applyW, INPUT_H, BTN_R,
            aHov ? Colors.ACCENT_HOVER : Colors.ACCENT);
        arcText(ctx, tr, "Appliquer",
            applyX + (applyW - arcW(tr, "Appliquer")) / 2, btnY + (INPUT_H - tr.lineHeight) / 2 + 1,
            Colors.FOREGROUND);
        hitTargets.add(new HitTarget(applyX, btnY, applyW, INPUT_H, onApply));
    }

    // ──────────────────────────────────────────────
    // INTERACTIONS
    // ──────────────────────────────────────────────

    public boolean handleClick(double mouseX, double mouseY) {
        for (HitTarget t : hitTargets) {
            if (t.handle(mouseX, mouseY)) return true;
        }
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static class HitTarget {
        final int x, y, w, h;
        final Runnable action;
        HitTarget(int x, int y, int w, int h, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }
        boolean handle(double mx, double my) {
            if (mx >= x && mx < x + w && my >= y && my < y + h) { action.run(); return true; }
            return false;
        }
    }
}
