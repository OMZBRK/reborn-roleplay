package fr.reborn.hud.ui;

import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.config.HudPresets;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import fr.reborn.hud.ui.style.IconTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panneau latéral droit de l'éditeur HUD, au niveau de la fiche shinobi (DA §4.1, §6) : planche laquée étroite
 * (textures {@code textures/gui/editeur/*}, {@link EditeurArt}), signature des outils du charpentier — équerre et
 * cordeau à encre en tête, règle graduée d'or comme filet —, lignes et plaques laquées, texte {@link DaKit}.
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
    /** Pied : « Personnaliser le viseur » (15/10, le viseur n'a plus de touche), puis Tout réinitialiser / Appliquer. */
    private static final int FOOTER_H = 47;
    private static final int LABEL_H = 11;
    private static final int ROW_H = 14;
    private static final int INPUT_H = 15;
    private static final int PRESET_ROW_H = 15;
    private static final int CLOSE_SZ = 12;
    private static final int EYE_SZ = 10;
    /** Lignes d'éléments minimum avant de rogner ailleurs. */
    private static final int MIN_LIST_ROWS = 3;
    /** Hauteur du bloc inspecteur : label + ligne X/Y + ligne taille/réinit. */
    /** Ligne d'option propre à l'élément (case « Afficher toute l'escouade » pour la radio), réservée pour tous. */
    private static final int OPTION_H = 14;
    private static final int INSPECTOR_H = LABEL_H + 3 + INPUT_H + 4 + INPUT_H + OPTION_H;

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
    /** Ouvre l'éditeur de viseur (bouton du pied de carte). */
    public Runnable onOpenCrosshair = () -> {};
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

        // Planche laquée (texture générée, coupée à la hauteur de l'écran) : un seul élément GUI.
        DaKit.coupe(ctx, EditeurArt.PANNEAU, x0 - 2, y0 - 2, WIDTH + 8, height + 8, 0, 0, 156, 532, 18, 18, 156, 532);

        renderHeader(ctx, mouseX, mouseY);

        int y = y0 + HEADER_H + 6;
        renderSectionLabel(ctx, "Éléments", y);
        renderElementList(ctx, mouseX, mouseY);
        renderInspector(ctx, mouseX, mouseY);

        renderSectionLabel(ctx, "Presets", presetInputY - LABEL_H - 3);
        renderPresetInput(ctx, mouseX, mouseY);
        renderPresetList(ctx, mouseX, mouseY);

        renderFooter(ctx, mouseX, mouseY);
    }

    /** Languette repliée : planche étroite, chevron « ‹ » d'ouverture au milieu. */
    private void renderCollapsedTab(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        boolean hov = inside(mouseX, mouseY, tabX, y0, TAB_W, height);
        DaKit.coupe(ctx, EditeurArt.LANGUETTE, tabX - 2, y0 - 2, TAB_W + 8, height + 8, 0, 0, 22, 532, 6, 18, 22, 532);
        DaKit.blit(ctx, EditeurArt.PIECES, tabX + (TAB_W - 5) / 2, y0 + height / 2 - 4, 34, 0, 5, 9, 160, 64,
            DaKit.blanc(hov ? 1f : 0.7f));
        hitTargets.add(new HitTarget(tabX, y0, TAB_W, height, this::toggleCollapsed));
    }

    private void renderHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // signature : l'équerre et le cordeau du charpentier, puis le titre
        DaKit.blit(ctx, EditeurArt.PIECES, x0 + PAD + 2, y0 + 5, 0, 0, 24, 18, 160, 64);
        DaKit.arcFin(ctx, tr, DaKit.arcade("Éditeur"), x0 + PAD + 30, y0 + 8, DaKit.OR_CLAIR, 0, true);
        DaKit.arcFin(ctx, tr, DaKit.arcade("du HUD"), x0 + PAD + 30, y0 + 14, DaKit.OR_ETIQ, 0, false);

        int cy = y0 + (HEADER_H - CLOSE_SZ) / 2;

        int cx = x0 + WIDTH - PAD - CLOSE_SZ;
        boolean hov = inside(mouseX, mouseY, cx, cy, CLOSE_SZ, CLOSE_SZ);
        HudEditChrome.renderIconGlyph(ctx, "close", cx + CLOSE_SZ / 2, cy + CLOSE_SZ / 2,
            hov ? DaKit.ROUGE_CLAIR : DaKit.DOUX);
        hitTargets.add(new HitTarget(cx, cy, CLOSE_SZ, CLOSE_SZ, onClose));

        // Repli du panneau (chevron ›) — à gauche du ✕.
        int vx = cx - CLOSE_SZ - 2;
        boolean vHov = inside(mouseX, mouseY, vx, cy, CLOSE_SZ, CLOSE_SZ);
        DaKit.blit(ctx, EditeurArt.PIECES, vx + (CLOSE_SZ - 5) / 2, cy + (CLOSE_SZ - 9) / 2, 28, 0, 5, 9, 160, 64,
            DaKit.blanc(vHov ? 1f : 0.65f));
        hitTargets.add(new HitTarget(vx, cy, CLOSE_SZ, CLOSE_SZ, this::toggleCollapsed));

        // Petit engrenage « réglages chat » à gauche du repli.
        int gx = vx - CLOSE_SZ - 2;
        boolean gHov = inside(mouseX, mouseY, gx, cy, CLOSE_SZ, CLOSE_SZ);
        IconTextures.draw(ctx, "gear", gx + 1, cy + 1, CLOSE_SZ - 2, gHov ? DaKit.CREME : DaKit.DOUX);
        hitTargets.add(new HitTarget(gx, cy, CLOSE_SZ, CLOSE_SZ, onOpenChatSettings));

        // règle graduée d'or sous l'en-tête
        DaKit.coupeH(ctx, EditeurArt.PIECES, x0 + PAD, y0 + HEADER_H - 2, WIDTH - 2 * PAD, 0, 20, 132, 5, 6, 160, 64);
    }

    private void renderSectionLabel(GuiGraphicsExtractor ctx, String label, int y) {
        DaKit.arcFin(ctx, tr, DaKit.arcade(label), x0 + PAD, y + 1, DaKit.OR_ETIQ, 0, false);
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
            LotGui l = LotGui.ouvrir(ctx);
            l.fill(trackX, listTop, trackX + 1, listTop + trackH, DaKit.SEP);
            int thumbH = Math.max(6, trackH * listRows / all.length);
            int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, all.length - listRows);
            l.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, DaKit.OR);
            l.fermer();
        }
    }

    private void renderElementRow(GuiGraphicsExtractor ctx, HudElement e, int y, int mouseX, int mouseY) {
        HudElementState st = config.stateOf(e);
        int rowX = x0 + PAD - 3;
        int rowW = WIDTH - (PAD - 3) - PAD;
        boolean selected = e == selectedElement;
        boolean rowHover = inside(mouseX, mouseY, rowX, y - 2, rowW, ROW_H);

        if (selected || rowHover) {
            DaKit.coupeH(ctx, EditeurArt.LIGNES, rowX - 1, y - 3, rowW + 2, 0, selected ? 16 : 0, 140, 16, 6, 140, 32);
        }

        int eyeX = x0 + PAD;
        int eyeY = y + (tr.lineHeight - EYE_SZ) / 2;
        boolean eyeHover = inside(mouseX, mouseY, eyeX - 2, eyeY - 2, EYE_SZ + 4, EYE_SZ + 4);
        int eyeColor = st.visible() ? (eyeHover ? DaKit.CREME : DaKit.DOUX) : DaKit.ROUGE_CLAIR;
        IconTextures.draw(ctx, st.visible() ? "eye_open" : "eye_closed", eyeX, eyeY, EYE_SZ, eyeColor);

        int nameColor = st.visible() ? (selected || rowHover ? DaKit.CREME : DaKit.CREME_VAL) : DaKit.INACTIF;
        DaKit.inter8(ctx, tr, DaKit.inter(e.displayName()), eyeX + EYE_SZ + 5, y + 4, nameColor, 0);

        if (Math.abs(st.scale() - 1.0f) > 0.01f) {
            String bs = String.format("×%.2f", st.scale());
            DaKit.inter7(ctx, tr, DaKit.inter(bs), x0 + WIDTH - PAD - 2, y + 4, DaKit.OR_ETIQ, 2);
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
            ? "Sélection" : selectedElement.displayName(), inspectorTop);

        int fieldW = (WIDTH - PAD * 2 - 4) / 2;
        int rowY = inspectorTop + LABEL_H + 3;
        int row2Y = rowY + INPUT_H + 4;

        drawField(ctx, x0 + PAD, rowY, fieldW, "X");
        drawField(ctx, x0 + PAD + fieldW + 4, rowY, fieldW, "Y");
        drawField(ctx, x0 + PAD, row2Y, fieldW, "%");

        int resetX = x0 + PAD + fieldW + 4;
        boolean hov = inside(mouseX, mouseY, resetX, row2Y, fieldW, INPUT_H);
        bouton(ctx, resetX, row2Y, fieldW, hov ? 1 : 0, "Réinit.", hov ? DaKit.CREME : DaKit.CREME_VAL);
        hitTargets.add(new HitTarget(resetX, row2Y, fieldW, INPUT_H, onResetSelected));

        // Option de l'élément « Escouade radio » : toute l'escouade (ceux qui se taisent en retrait).
        if (selectedElement == HudElement.ESCOUADE) {
            int oy = row2Y + INPUT_H + 4, ox = x0 + PAD;
            boolean on = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.radioToutEscouade;
            boolean oh = inside(mouseX, mouseY, ox, oy, WIDTH - PAD * 2, OPTION_H - 2);
            int bord = oh ? DaKit.CREME : DaKit.CREME_VAL;
            ctx.fill(ox, oy + 2, ox + 8, oy + 3, bord);
            ctx.fill(ox, oy + 9, ox + 8, oy + 10, bord);
            ctx.fill(ox, oy + 2, ox + 1, oy + 10, bord);
            ctx.fill(ox + 7, oy + 2, ox + 8, oy + 10, bord);
            if (on) ctx.fill(ox + 2, oy + 4, ox + 6, oy + 8, DaKit.OR_CLAIR);
            DaKit.inter8(ctx, tr, DaKit.inter("Afficher toute l'escouade"), ox + 13, oy + 6, oh ? DaKit.CREME : DaKit.CREME_VAL, 0);
            hitTargets.add(new HitTarget(ox, oy, WIDTH - PAD * 2, OPTION_H - 2, () -> {
                var pr = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE;
                pr.radioToutEscouade = !pr.radioToutEscouade;
                pr.save();
            }));
        }
    }

    /** Cadre d'un champ de saisie + son préfixe (X / Y / %). */
    private void drawField(GuiGraphicsExtractor ctx, int x, int y, int w, String prefix) {
        DaKit.coupeH(ctx, EditeurArt.CHAMP, x - 1, y - 1, w + 2, 0, 0, 140, 17, 6, 140, 19);
        DaKit.arcFin(ctx, tr, DaKit.arcade(prefix), x + 4, y + 6, DaKit.OR_ETIQ, 0, false);
    }

    /** Plaque laquée 15 de haut (0 repos, 1 survol, 2 actif, 3 inactif), libellé Inter 8 centré. */
    private void bouton(GuiGraphicsExtractor ctx, int x, int y, int w, int etat, String lib, int col) {
        DaKit.coupeH(ctx, EditeurArt.BOUTONS, x - 1, y - 1, w + 3, 0, etat * 19, 140, 19, 6, 140, 76);
        DaKit.inter8(ctx, tr, DaKit.inter(lib), x + w / 2f, y + INPUT_H / 2f, col, 1);
    }

    // ─────────── Presets ───────────

    private void renderPresetInput(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        DaKit.coupeH(ctx, EditeurArt.CHAMP, presetInputX - 1, presetInputY - 1, presetInputW + 2, 0, 0, 140, 17, 6, 140, 19);

        int saveX = presetInputX + presetInputW + 4;
        int saveW = x0 + WIDTH - PAD - saveX;
        boolean hov = inside(mouseX, mouseY, saveX, presetInputY, saveW, INPUT_H);
        bouton(ctx, saveX, presetInputY, saveW, 2, "Enregistrer", hov ? DaKit.CREME : DaKit.CREME_VAL);
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
            DaKit.inter7(ctx, tr, DaKit.inter("+ " + (total - shown) + " autres"), x0 + PAD, y + 4, DaKit.DOUX, 0);
        }
    }

    private void renderPresetRow(GuiGraphicsExtractor ctx, String id, int y, boolean active,
                                 int mouseX, int mouseY) {
        int rowX = x0 + PAD;
        int rowW = WIDTH - PAD * 2;
        boolean hover = inside(mouseX, mouseY, rowX, y, rowW, PRESET_ROW_H);
        DaKit.coupeH(ctx, EditeurArt.BOUTONS, rowX - 1, y - 1, rowW + 3, 0, (active ? 2 : hover ? 1 : 0) * 19, 140, 19, 6,
            140, 76);
        DaKit.inter8(ctx, tr, DaKit.inter(HudPresets.displayName(id)), rowX + 7, y + PRESET_ROW_H / 2f,
            active || hover ? DaKit.CREME : DaKit.CREME_VAL, 0);

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

    /** Rect du bouton « Personnaliser le viseur » (x, y, w, h) — le banc des touches y clique. */
    public int[] viseurRect() { return new int[]{x0 + PAD, footerTop + 6, WIDTH - PAD * 2, INPUT_H}; }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        DaKit.coupeH(ctx, EditeurArt.PIECES, x0 + PAD, footerTop - 2, WIDTH - 2 * PAD, 0, 20, 132, 5, 6, 160, 64);

        int viseurY = footerTop + 6;
        int vw = WIDTH - PAD * 2;
        boolean vHov = inside(mouseX, mouseY, x0 + PAD, viseurY, vw, INPUT_H);
        bouton(ctx, x0 + PAD, viseurY, vw, vHov ? 1 : 0, "Personnaliser le viseur", vHov ? DaKit.CREME : DaKit.CREME_VAL);
        hitTargets.add(new HitTarget(x0 + PAD, viseurY, vw, INPUT_H, onOpenCrosshair));

        int btnY = viseurY + INPUT_H + 4;
        int gap = 4;
        int applyW = 56;
        int resetW = WIDTH - PAD * 2 - applyW - gap;
        int resetX = x0 + PAD;
        int applyX = resetX + resetW + gap;

        boolean rHov = inside(mouseX, mouseY, resetX, btnY, resetW, INPUT_H);
        bouton(ctx, resetX, btnY, resetW, rHov ? 1 : 0, "Tout réinitialiser", rHov ? DaKit.ROUGE_CLAIR : 0xFFC86470);
        hitTargets.add(new HitTarget(resetX, btnY, resetW, INPUT_H, () -> { onMutation.run(); onResetAll.run(); }));

        boolean aHov = inside(mouseX, mouseY, applyX, btnY, applyW, INPUT_H);
        bouton(ctx, applyX, btnY, applyW, 2, "Appliquer", aHov ? DaKit.CREME : DaKit.CREME_VAL);
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
