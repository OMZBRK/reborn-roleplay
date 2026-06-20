package fr.reborn.hud.ui;

import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.config.HudPresets;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.ui.style.RebornColors;
import fr.reborn.hud.ui.style.RoundedRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Side panel droit du HudEditScreen, version épurée.
 *
 * <p>Sections actives :
 * <ol>
 *   <li>Header — nom + description de l'élément sélectionné</li>
 *   <li>Presets — 4 presets cliquables (Default/Streamer/RP/Compact)</li>
 *   <li>Visibilité toggle — afficher / cacher</li>
 *   <li>Footer — Reset cet élément / Tout, Exporter / Importer</li>
 * </ol>
 *
 * <p><strong>Volontairement supprimés (UX feedback)</strong> : sliders
 * X/Y/Scale (redondants avec le drag direct + molette sur la box) et
 * anchor 3×3 (comportement source de confusion — concept gardé en
 * interne via {@link HudElement#defaultAnchor()}).
 *
 * <p>Pattern : pendant le render on populate {@link #hitTargets} avec
 * des rectangles cliquables + action. {@link #handleClick} dispatch
 * sur le premier hit. Aucune duplication de logique de layout.
 */
public final class HudEditSidePanel {

    public static final int WIDTH = HudEditChrome.SIDEPANEL_WIDTH;

    private static final int PAD = 10;
    private static final int LINE_HEIGHT = 11;

    private final HudConfig config;
    private final TextRenderer tr;

    private int x0, y0, height;
    private HudElement selectedElement = HudElement.CHAT;

    private final List<HitTarget> hitTargets = new ArrayList<>();

    public Runnable onResetAll = () -> {};
    public Runnable onExport   = () -> {};
    public Runnable onImport   = () -> {};
    /** Hook caller pour push undo snapshot avant mutation. */
    public Runnable onMutation = () -> {};
    /** Callback toast feedback (text). */
    public java.util.function.Consumer<String> onToast = s -> {};

    /** Cache des rect des preset rows pour right-click handling. */
    private final java.util.Map<String, int[]> presetRects = new java.util.HashMap<>();

    public HudEditSidePanel(HudConfig config, TextRenderer tr) {
        this.config = config;
        this.tr = tr;
    }

    public void setSelectedElement(HudElement element) { this.selectedElement = element; }
    public HudElement selectedElement() { return selectedElement; }

    public void layout(int screenWidth, int screenHeight) {
        this.x0 = screenWidth - WIDTH;
        this.y0 = HudEditChrome.TOPBAR_HEIGHT;
        this.height = screenHeight - HudEditChrome.TOPBAR_HEIGHT;
    }

    // ──────────────────────────────────────────────
    // RENDER
    // ──────────────────────────────────────────────

    public void render(DrawContext ctx, int mouseX, int mouseY) {
        hitTargets.clear();
        presetRects.clear();

        // Drop shadow gauche du panel (élévation visuelle)
        for (int i = 1; i <= 4; i++) {
            int alpha = (5 - i) * 14;
            ctx.fill(x0 - i, y0, x0 - i + 1, y0 + height, (alpha << 24));
        }

        ctx.fill(x0, y0, x0 + WIDTH, y0 + height, RebornColors.BG_PANEL_ELEVATED);
        ctx.fill(x0, y0, x0 + 1, y0 + height, RebornColors.BORDER);

        int y = y0;
        y = renderHeader(ctx, y);
        y = renderPresets(ctx, y, mouseX, mouseY);
        renderVisibilitySection(ctx, y, mouseX, mouseY);

        renderFooterActions(ctx, y0 + height - 56, mouseX, mouseY);
    }

    private int renderHeader(DrawContext ctx, int y) {
        ctx.drawText(tr,
            Text.literal("ÉLÉMENT").formatted(Formatting.BOLD),
            x0 + PAD, y + PAD, RebornColors.FOREGROUND_SUBTLE, false);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x0 + PAD, y + PAD + 11, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1f);
        ctx.drawText(tr,
            Text.literal(selectedElement.displayName().toUpperCase()).formatted(Formatting.BOLD),
            0, 0, RebornColors.FOREGROUND, false);
        ctx.getMatrices().pop();

        ctx.drawText(tr, Text.literal(selectedElement.description()),
            x0 + PAD, y + PAD + 25, RebornColors.FOREGROUND_MUTED, false);

        int bottom = y + 48;
        ctx.fill(x0 + PAD, bottom - 1, x0 + WIDTH - PAD, bottom, RebornColors.BORDER);
        return bottom;
    }

    private int renderPresets(DrawContext ctx, int y, int mouseX, int mouseY) {
        ctx.drawText(tr,
            Text.literal("PRESETS").formatted(Formatting.BOLD),
            x0 + PAD, y + PAD, RebornColors.FOREGROUND_MUTED, false);

        int rowY = y + PAD + LINE_HEIGHT + 2;
        String active = config.getActivePreset();

        // Render TOUS les presets (built-ins + customs) dans l'ordre d'insertion
        for (String id : config.getPresets().keySet()) {
            renderPresetRow(ctx, rowY, id, id.equals(active), mouseX, mouseY);
            rowY += 28;
        }

        // Bouton "+ Sauvegarder l'état actuel"
        renderSaveCurrentButton(ctx, rowY, mouseX, mouseY);
        rowY += 22;

        int bottom = rowY + 4;
        ctx.fill(x0 + PAD, bottom, x0 + WIDTH - PAD, bottom + 1, RebornColors.BORDER);
        return bottom + 6;
    }

    private void renderSaveCurrentButton(DrawContext ctx, int y, int mouseX, int mouseY) {
        int x = x0 + PAD;
        int w = WIDTH - PAD * 2;
        int h = 18;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int bg = hovered ? RebornColors.ACCENT_SOFT : 0x00000000;
        int border = hovered ? RebornColors.ACCENT : RebornColors.withAlpha(RebornColors.ACCENT, 0x66);
        RoundedRect.fill(ctx, x, y, w, h, 4, bg);
        RoundedRect.border(ctx, x, y, w, h, 4, border);
        String label = "+ Sauvegarder l'état";
        int lW = tr.getWidth(label);
        ctx.drawText(tr, Text.literal(label).formatted(Formatting.BOLD),
            x + (w - lW) / 2, y + (h - tr.fontHeight) / 2, RebornColors.ACCENT_HOVER, false);
        hitTargets.add(new HitTarget(x, y, w, h, this::saveCurrentAsPreset));
    }

    private void saveCurrentAsPreset() {
        onMutation.run();
        // Génère un nom incrémenté "Custom N" qui n'existe pas encore
        int n = 1;
        String id;
        do {
            id = "custom-" + n;
            n++;
        } while (config.getPresets().containsKey(id));
        config.saveAsNewPreset(id);
    }

    private void renderPresetRow(DrawContext ctx, int y, String id, boolean active,
                                 int mouseX, int mouseY) {
        int rowX = x0 + PAD;
        int rowW = WIDTH - PAD * 2;
        int rowH = 24;
        boolean hovered = inside(mouseX, mouseY, rowX, y, rowW, rowH);

        int bg = active ? RebornColors.ACCENT_SOFT
                        : (hovered ? 0x14FFFFFF : RebornColors.BG_INPUT);
        int border = active ? RebornColors.ACCENT
                            : (hovered ? RebornColors.BORDER_STRONG : RebornColors.BORDER);
        RoundedRect.fill(ctx, rowX, y, rowW, rowH, 6, bg);
        RoundedRect.border(ctx, rowX, y, rowW, rowH, 6, border);

        int cbX = rowX + 7, cbY = y + (rowH - 12) / 2;
        if (active) {
            RoundedRect.fill(ctx, cbX, cbY, 12, 12, 3, RebornColors.ACCENT);
            ctx.fill(cbX + 2, cbY + 6, cbX + 4,  cbY + 8,  0xFFFFFFFF);
            ctx.fill(cbX + 4, cbY + 7, cbX + 6,  cbY + 9,  0xFFFFFFFF);
            ctx.fill(cbX + 6, cbY + 4, cbX + 10, cbY + 7,  0xFFFFFFFF);
        } else {
            RoundedRect.border(ctx, cbX, cbY, 12, 12, 3, RebornColors.BORDER_STRONG);
        }

        ctx.drawText(tr, Text.literal(HudPresets.displayName(id)).formatted(Formatting.BOLD),
            cbX + 18, y + 4, RebornColors.FOREGROUND, false);
        ctx.drawText(tr, Text.literal(HudPresets.description(id)),
            cbX + 18, y + 14, RebornColors.FOREGROUND_MUTED, false);

        hitTargets.add(new HitTarget(rowX, y, rowW, rowH, () -> applyPreset(id)));
        presetRects.put(id, new int[]{rowX, y, rowW, rowH});
    }

    private void applyPreset(String id) {
        onMutation.run();
        config.applyPreset(id);
        onToast.accept("Preset « " + HudPresets.displayName(id) + " » appliqué");
    }

    /** Right-click sur preset → supprime (sauf Default). */
    public boolean handleRightClick(double mouseX, double mouseY) {
        for (var entry : presetRects.entrySet()) {
            int[] r = entry.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2]
                && mouseY >= r[1] && mouseY < r[1] + r[3]) {
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

    private void renderVisibilitySection(DrawContext ctx, int y, int mouseX, int mouseY) {
        HudElementState state = config.stateOf(selectedElement);

        // Section ÉCHELLE (boutons preset rapides)
        ctx.drawText(tr,
            Text.literal("ÉCHELLE").formatted(Formatting.BOLD),
            x0 + PAD, y + PAD, RebornColors.FOREGROUND_MUTED, false);
        int scaleY = y + PAD + LINE_HEIGHT + 4;
        renderScalePresets(ctx, scaleY, state.scale(), mouseX, mouseY);

        // Section VISIBILITÉ
        int visY = scaleY + 22;
        ctx.drawText(tr,
            Text.literal("VISIBILITÉ").formatted(Formatting.BOLD),
            x0 + PAD, visY, RebornColors.FOREGROUND_MUTED, false);
        renderVisibilityToggle(ctx, visY + LINE_HEIGHT + 4, state.visible(), mouseX, mouseY);
    }

    private void renderScalePresets(DrawContext ctx, int y, float currentScale, int mouseX, int mouseY) {
        float[] presets = {0.5f, 1.0f, 1.5f, 2.0f};
        String[] labels = {"0.5×", "1.0×", "1.5×", "2.0×"};
        int totalW = WIDTH - PAD * 2;
        int btnW = (totalW - 3 * 4) / 4;
        int btnH = 18;
        int x = x0 + PAD;
        for (int i = 0; i < presets.length; i++) {
            float p = presets[i];
            boolean active = Math.abs(currentScale - p) < 0.01f;
            boolean hovered = inside(mouseX, mouseY, x, y, btnW, btnH);
            int bg = active ? RebornColors.ACCENT_SOFT
                            : (hovered ? 0x14FFFFFF : RebornColors.BG_INPUT);
            int border = active ? RebornColors.ACCENT
                                : (hovered ? RebornColors.BORDER_STRONG : RebornColors.BORDER);
            int color = active ? RebornColors.FOREGROUND : RebornColors.FOREGROUND_SUBTLE;
            RoundedRect.fill(ctx, x, y, btnW, btnH, 4, bg);
            RoundedRect.border(ctx, x, y, btnW, btnH, 4, border);
            int lW = tr.getWidth(labels[i]);
            ctx.drawText(tr, Text.literal(labels[i]).formatted(Formatting.BOLD),
                x + (btnW - lW) / 2, y + (btnH - tr.fontHeight) / 2, color, false);
            final float fp = p;
            hitTargets.add(new HitTarget(x, y, btnW, btnH,
                () -> mutateState(s -> s.withScale(fp))));
            x += btnW + 4;
        }
    }

    private void renderVisibilityToggle(DrawContext ctx, int y, boolean isOn, int mouseX, int mouseY) {
        ctx.drawText(tr, Text.literal("Afficher cet élément"),
            x0 + PAD, y + 4, RebornColors.FOREGROUND, false);

        int switchW = 32, switchH = 16;
        int switchX = x0 + WIDTH - PAD - switchW;
        int switchY = y;

        int bg = isOn ? RebornColors.SUCCESS : 0x1FFFFFFF;
        RoundedRect.fill(ctx, switchX, switchY, switchW, switchH, switchH / 2, bg);
        int thumbS = switchH - 4;
        int thumbX = isOn ? switchX + switchW - thumbS - 2 : switchX + 2;
        RoundedRect.fill(ctx, thumbX, switchY + 2, thumbS, thumbS, thumbS / 2, 0xFFFFFFFF);

        hitTargets.add(new HitTarget(switchX, switchY, switchW, switchH,
            () -> mutateState(s -> s.withVisible(!s.visible()))));
    }

    private void renderFooterActions(DrawContext ctx, int y, int mouseX, int mouseY) {
        ctx.fill(x0, y, x0 + WIDTH, y + 1, RebornColors.BORDER);
        ctx.fill(x0, y, x0 + WIDTH, y + 56, 0x99000000);

        int btnW = (WIDTH - PAD * 2 - 5) / 2;
        int btnH = 18;

        renderActionButton(ctx, x0 + PAD, y + 7, btnW, btnH, "Cet élément", false, mouseX, mouseY,
            () -> mutateState(s -> HudElementState.DEFAULT));
        renderActionButton(ctx, x0 + PAD + btnW + 5, y + 7, btnW, btnH, "Tout", true, mouseX, mouseY,
            () -> { onMutation.run(); onResetAll.run(); });
        renderActionButton(ctx, x0 + PAD, y + 7 + btnH + 5, btnW, btnH, "Exporter", false, mouseX, mouseY,
            onExport);
        renderActionButton(ctx, x0 + PAD + btnW + 5, y + 7 + btnH + 5, btnW, btnH, "Importer", false, mouseX, mouseY,
            onImport);
    }

    private void renderActionButton(DrawContext ctx, int x, int y, int w, int h, String label,
                                    boolean danger, int mouseX, int mouseY, Runnable onClick) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int bg = hovered
            ? (danger ? RebornColors.DANGER_SOFT : 0x14FFFFFF)
            : RebornColors.BG_INPUT;
        int border = danger ? RebornColors.withAlpha(RebornColors.DANGER, 0x47) : RebornColors.BORDER_STRONG;
        int textColor = danger ? RebornColors.DANGER : RebornColors.FOREGROUND;

        RoundedRect.fill(ctx, x, y, w, h, 5, bg);
        RoundedRect.border(ctx, x, y, w, h, 5, border);
        int lW = tr.getWidth(label);
        ctx.drawText(tr, Text.literal(label).formatted(Formatting.BOLD),
            x + (w - lW) / 2, y + (h - tr.fontHeight) / 2, textColor, false);
        hitTargets.add(new HitTarget(x, y, w, h, onClick));
    }

    // ──────────────────────────────────────────────
    // INTERACTIONS
    // ──────────────────────────────────────────────

    public boolean handleClick(double mouseX, double mouseY, Consumer<HudElement> onSelectionRequest) {
        for (HitTarget t : hitTargets) {
            if (t.handle(mouseX, mouseY)) return true;
        }
        return false;
    }

    private void mutateState(UnaryOperator<HudElementState> mutator) {
        onMutation.run();
        HudElementState current = config.stateOf(selectedElement);
        HudElementState next = mutator.apply(current);
        if (next == null) next = HudElementState.DEFAULT;
        config.setState(selectedElement, next);
        config.save();
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
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                action.run();
                return true;
            }
            return false;
        }
    }
}
