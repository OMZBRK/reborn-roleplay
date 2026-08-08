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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panneau latéral droit de l'éditeur HUD, refondu façon Zenkai — sobre, en
 * <b>carte flottante insérée</b> (marges autour), coins carrés, compact et aéré
 * (cf {@code screenmodifhudzenkai.png}).
 *
 * <p>De haut en bas : header (titre + ✕), section ÉLÉMENTS (œil + nom + badge
 * d'échelle), section PRESETS (champ nommable + Enregistrer + liste), footer
 * épinglé (Tout réinitialiser / Appliquer).
 */
public final class HudEditSidePanel {

    public static final int WIDTH = 178;
    public static final int MARGIN = 8;

    private static final int PAD = 9;
    private static final int CARD_R = 4;   // coin de la carte
    private static final int BTN_R = 2;    // coins carrés des boutons/lignes
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 30;
    private static final int LABEL_H = 12;
    private static final int ROW_H = 14;
    private static final int INPUT_H = 16;
    private static final int PRESET_ROW_H = 17;
    private static final int CLOSE_SZ = 13;
    private static final int EYE_SZ = 11;

    private final HudConfig config;
    private final Font tr;

    private int x0, y0, width, height;
    private HudElement selectedElement = HudElement.CHAT;

    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final java.util.Map<String, int[]> presetRects = new java.util.HashMap<>();

    // Positions calculées en layout() (indépendantes de la souris).
    private int presetInputX, presetInputY, presetInputW;
    private int presetListTop, footerTop;

    public Runnable onClose = () -> {};
    public Runnable onApply = () -> {};
    public Runnable onResetAll = () -> {};
    public Runnable onSavePreset = () -> {};
    /** Ouvre les réglages du chat (petit engrenage dans le header). */
    public Runnable onOpenChatSettings = () -> {};
    public Runnable onMutation = () -> {};
    public Consumer<String> onToast = s -> {};
    public Consumer<HudElement> onSelect = e -> {};

    public HudEditSidePanel(HudConfig config, Font tr) {
        this.config = config;
        this.tr = tr;
    }

    public void setSelectedElement(HudElement element) { this.selectedElement = element; }
    public HudElement selectedElement() { return selectedElement; }

    /** Bord gauche de la carte (limite du canvas). */
    public int leftEdge() { return x0; }

    public void layout(int screenWidth, int screenHeight) {
        this.width = WIDTH;
        this.x0 = screenWidth - WIDTH - MARGIN;
        this.y0 = MARGIN;
        this.height = screenHeight - MARGIN * 2;

        int y = y0 + HEADER_H + 7;               // sous le header
        y += LABEL_H + 3;                         // "ÉLÉMENTS"
        y += HudElement.EDITABLE.length * ROW_H;  // lignes éléments
        y += 10 + LABEL_H + 3;                    // "PRESETS"
        this.presetInputY = y;
        this.presetInputX = x0 + PAD;
        int saveW = arcW(tr, "Enregistrer") + 12;
        this.presetInputW = WIDTH - PAD * 2 - saveW - 5;
        this.presetListTop = presetInputY + INPUT_H + 6;
        this.footerTop = y0 + height - FOOTER_H;
    }

    /** Rect {x,y,w,h} du champ de saisie du nom de preset (pour l'EditBox). */
    public int[] presetInputRect() {
        return new int[]{presetInputX + 6, presetInputY + (INPUT_H - 8) / 2, presetInputW - 10, 8};
    }

    /** Échelle de la police ArcadePix (native ~2× trop grosse pour ce panneau). */
    static final float ARC = 0.5f;

    /** Texte en police ArcadePix (comme le main-menu), majuscules ASCII + tronqué. */
    public static Component arc(String s) {
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

        // Carte — fond éclairci pour la lisibilité (pas d'ombre pixellisée : elle
        // laissait des lignes/pixels parasites autour de la carte).
        FlatRect.fill(ctx, x0, y0, WIDTH, height, CARD_R, 0xF0301C22);
        FlatRect.border(ctx, x0, y0, WIDTH, height, CARD_R, Colors.BORDER_STRONG);

        renderHeader(ctx, mouseX, mouseY);

        int y = y0 + HEADER_H + 7;
        y = renderSectionLabel(ctx, "ÉLÉMENTS", y) + 3;
        for (HudElement e : HudElement.EDITABLE) {
            renderElementRow(ctx, e, y, mouseX, mouseY);
            y += ROW_H;
        }

        y += 10;
        renderSectionLabel(ctx, "PRESETS", y);
        renderPresetInput(ctx, mouseX, mouseY);
        renderPresetList(ctx, mouseX, mouseY);

        renderFooter(ctx, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        FlatRect.fill(ctx, x0 + PAD, y0 + (HEADER_H - 7) / 2, 7, 7, BTN_R, Colors.ACCENT);
        arcText(ctx, tr, "Editeur d'interface",
            x0 + PAD + 12, y0 + (HEADER_H - tr.lineHeight) / 2 + 1, Colors.FOREGROUND);

        int cx = x0 + WIDTH - PAD - CLOSE_SZ;
        int cy = y0 + (HEADER_H - CLOSE_SZ) / 2;
        boolean hov = inside(mouseX, mouseY, cx, cy, CLOSE_SZ, CLOSE_SZ);
        if (hov) FlatRect.fill(ctx, cx, cy, CLOSE_SZ, CLOSE_SZ, BTN_R, Colors.DANGER_SOFT);
        HudEditChrome.renderIconGlyph(ctx, "close", cx + CLOSE_SZ / 2, cy + CLOSE_SZ / 2,
            hov ? Colors.DANGER : Colors.FOREGROUND_SUBTLE);
        hitTargets.add(new HitTarget(cx, cy, CLOSE_SZ, CLOSE_SZ, onClose));

        // Petit engrenage « réglages chat » à gauche du ✕ (sobre).
        int gx = cx - CLOSE_SZ - 3;
        boolean gHov = inside(mouseX, mouseY, gx, cy, CLOSE_SZ, CLOSE_SZ);
        if (gHov) FlatRect.fill(ctx, gx, cy, CLOSE_SZ, CLOSE_SZ, BTN_R, Colors.ACCENT_SOFT);
        IconTextures.draw(ctx, "gear", gx + 1, cy + 1, CLOSE_SZ - 2,
            gHov ? Colors.FOREGROUND : Colors.FOREGROUND_MUTED);
        hitTargets.add(new HitTarget(gx, cy, CLOSE_SZ, CLOSE_SZ, onOpenChatSettings));

        ctx.fill(x0 + PAD, y0 + HEADER_H, x0 + WIDTH - PAD, y0 + HEADER_H + 1, Colors.BORDER);
    }

    private int renderSectionLabel(GuiGraphicsExtractor ctx, String label, int y) {
        arcText(ctx, tr, label, x0 + PAD, y, Colors.FOREGROUND_MUTED);
        return y + LABEL_H;
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
        arcText(ctx, tr, e.displayName(), eyeX + EYE_SZ + 6, y, nameColor);

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

    private void renderPresetInput(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        FlatRect.fill(ctx, presetInputX, presetInputY, presetInputW, INPUT_H, BTN_R, Colors.SURFACE);
        FlatRect.border(ctx, presetInputX, presetInputY, presetInputW, INPUT_H, BTN_R, Colors.BORDER);

        int saveX = presetInputX + presetInputW + 5;
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
        int maxBottom = footerTop - 5;

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
            rowX + 7, y + (PRESET_ROW_H - tr.lineHeight) / 2,
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
        int gap = 5;
        int applyW = 58;
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
