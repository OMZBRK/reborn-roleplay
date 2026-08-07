package fr.reborn.hud.chat;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.ui.HudEditSidePanel;
import fr.reborn.hud.ui.style.FlatRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Réglages du chat — même identité visuelle que l'éditeur HUD (thème Akatsuki
 * crimson, police ArcadePix, formes carrées {@link FlatRect}). Carte modale
 * centrée, ouverte depuis l'éditeur HUD (bouton dédié) ou {@code Ctrl+M}.
 *
 * <p>Chaque toggle/slider/couleur est persisté immédiatement (pas de bouton
 * Appliquer) — UX live.
 */
public final class ChatSettingsScreen extends Screen {

    private static final int CARD_W = 224;
    private static final int PAD = 12;
    private static final int ROW = 16;

    private final Screen parent;
    private final ChatSettings settings;

    private final List<Toggle> toggles = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<Cycle> cycles = new ArrayList<>();

    private static final int[] HL_COLORS = {
        0xFFA0182B, 0xFFEF4444, 0xFFD9A95E, 0xFF4ADE80, 0xFF38BDF8, 0xFF8B5CF6
    };

    public ChatSettingsScreen(Screen parent) {
        super(Component.literal("Paramètres Chat"));
        this.parent = parent;
        this.settings = RebornHudClient.config().getChatSettings();
    }

    private int cardW() { return Math.min(CARD_W, this.width - 24); }
    private int cardH() { return Math.min(356, this.height - 24); }
    private int cardX() { return (this.width - cardW()) / 2; }
    private int cardY() { return (this.height - cardH()) / 2; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Colors.BACKDROP_60);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractBackground(ctx, mouseX, mouseY, delta);
        toggles.clear();
        sliders.clear();
        swatches.clear();
        cycles.clear();

        Font tr = this.font;
        int cx = cardX(), cy = cardY(), cw = cardW(), ch = cardH();

        FlatRect.fill(ctx, cx, cy, cw, ch, 3, Colors.SURFACE_ELEVATED);
        FlatRect.border(ctx, cx, cy, cw, ch, 3, Colors.BORDER_STRONG);

        // Header : pastille + titre + séparateur.
        FlatRect.fill(ctx, cx + PAD, cy + 11, 7, 7, 2, Colors.ACCENT);
        HudEditSidePanel.arcText(ctx, tr, "Parametres chat", cx + PAD + 12, cy + 10, Colors.FOREGROUND);
        ctx.fill(cx + PAD, cy + 24, cx + cw - PAD, cy + 25, Colors.BORDER);

        int y = cy + 32;
        HudEditSidePanel.arcText(ctx, tr, "Affichage", cx + PAD, y, Colors.FOREGROUND_MUTED);
        y += 13;

        y = toggle(ctx, y, "Timestamps", settings.showTimestamps, mouseX, mouseY, b -> settings.showTimestamps = b);
        y = toggle(ctx, y, "Tetes de joueurs", settings.chatHeads, mouseX, mouseY, b -> settings.chatHeads = b);
        y = toggle(ctx, y, "Badges de rang", settings.chatBadges, mouseX, mouseY, b -> settings.chatBadges = b);
        y = toggle(ctx, y, "Animation d'arrivee", settings.chatAnimation, mouseX, mouseY, b -> settings.chatAnimation = b);
        y = toggle(ctx, y, "Machine a ecrire", settings.chatTyping, mouseX, mouseY, b -> settings.chatTyping = b);
        y = toggle(ctx, y, "Texte anime (saisie)", settings.animatedTyping, mouseX, mouseY, b -> settings.animatedTyping = b);
        y = cycle(ctx, y, "Style anim", new String[]{"Grossir", "Fondu", "Glisser"},
            settings.typingCursorStyle, mouseX, mouseY, v -> settings.typingCursorStyle = v);
        y = toggle(ctx, y, "Highlight mentions", settings.highlightMentions, mouseX, mouseY, b -> settings.highlightMentions = b);
        y = toggle(ctx, y, "Son sur mention", settings.soundOnMention, mouseX, mouseY, b -> settings.soundOnMention = b);
        y = colorRow(ctx, y, "Couleur mention", settings.highlightColor, mouseX, mouseY);

        y += 2;
        ctx.fill(cx + PAD, y, cx + cw - PAD, y + 1, Colors.BORDER);
        y += 7;
        HudEditSidePanel.arcText(ctx, tr, "Dimensions", cx + PAD, y, Colors.FOREGROUND_MUTED);
        y += 13;

        y = slider(ctx, y, "Opacite", settings.opacity, 30, 100, "%", mouseX, mouseY, v -> settings.opacity = v);
        y = slider(ctx, y, "Taille texte", settings.textSize, 10, 16, "PX", mouseX, mouseY, v -> settings.textSize = v);

        // Bouton Fermer.
        int bY = cy + ch - 26, bX = cx + PAD, bW = cw - PAD * 2, bH = 16;
        boolean hov = inside(mouseX, mouseY, bX, bY, bW, bH);
        FlatRect.fill(ctx, bX, bY, bW, bH, 2, hov ? Colors.ACCENT_HOVER : Colors.ACCENT);
        HudEditSidePanel.arcText(ctx, tr, "Fermer",
            bX + (bW - HudEditSidePanel.arcW(tr, "Fermer")) / 2, bY + (bH - tr.lineHeight) / 2 + 1, Colors.FOREGROUND);
    }

    private int toggle(GuiGraphicsExtractor ctx, int y, String label, boolean state,
                       int mouseX, int mouseY, java.util.function.Consumer<Boolean> onSet) {
        int cx = cardX(), cw = cardW();
        HudEditSidePanel.arcText(ctx, this.font, label, cx + PAD, y + 3, Colors.FOREGROUND);
        int sw = 24, sh = 11;
        int sx = cx + cw - PAD - sw, sy = y + 1;
        FlatRect.fill(ctx, sx, sy, sw, sh, 2, state ? Colors.ACCENT : Colors.SURFACE);
        FlatRect.border(ctx, sx, sy, sw, sh, 2, state ? Colors.ACCENT : Colors.BORDER_STRONG);
        int th = sh - 4;
        int tx = state ? sx + sw - th - 2 : sx + 2;
        FlatRect.fill(ctx, tx, sy + 2, th, th, 1, Colors.FOREGROUND);
        toggles.add(new Toggle(sx, sy, sw, sh, state, onSet));
        return y + ROW;
    }

    private int slider(GuiGraphicsExtractor ctx, int y, String label, int value, int min, int max,
                       String unit, int mouseX, int mouseY, java.util.function.IntConsumer onSet) {
        int cx = cardX(), cw = cardW();
        HudEditSidePanel.arcText(ctx, this.font, label, cx + PAD, y, Colors.FOREGROUND);
        String vStr = value + unit;
        HudEditSidePanel.arcText(ctx, this.font, vStr,
            cx + cw - PAD - HudEditSidePanel.arcW(this.font, vStr), y, Colors.ACCENT_HOVER);

        int trackY = y + 11, trackX = cx + PAD, trackW = cw - PAD * 2;
        int fillX = trackX + (value - min) * trackW / Math.max(1, max - min);
        ctx.fill(trackX, trackY, trackX + trackW, trackY + 3, Colors.SURFACE);
        ctx.fill(trackX, trackY, fillX, trackY + 3, Colors.ACCENT);
        FlatRect.fill(ctx, fillX - 3, trackY - 2, 6, 7, 1, Colors.FOREGROUND);
        sliders.add(new Slider(trackX, trackY - 4, trackW, 12, min, max, onSet));
        return y + 21;
    }

    private int colorRow(GuiGraphicsExtractor ctx, int y, String label, int current, int mouseX, int mouseY) {
        int cx = cardX(), cw = cardW();
        HudEditSidePanel.arcText(ctx, this.font, label, cx + PAD, y + 2, Colors.FOREGROUND);
        int size = 11, gap = 4;
        int total = HL_COLORS.length * size + (HL_COLORS.length - 1) * gap;
        int sx = cx + cw - PAD - total;
        for (int color : HL_COLORS) {
            boolean sel = (color & 0xFFFFFF) == (current & 0xFFFFFF);
            if (sel) FlatRect.fill(ctx, sx - 1, y - 1, size + 2, size + 2, 1, Colors.FOREGROUND);
            FlatRect.fill(ctx, sx, y, size, size, 1, color);
            swatches.add(new Swatch(sx, y, size, color));
            sx += size + gap;
        }
        return y + ROW + 2;
    }

    private int cycle(GuiGraphicsExtractor ctx, int y, String label, String[] opts, int idx,
                      int mouseX, int mouseY, java.util.function.IntConsumer onSet) {
        int cx = cardX(), cw = cardW();
        HudEditSidePanel.arcText(ctx, this.font, label, cx + PAD, y + 3, Colors.FOREGROUND);
        String cur = opts[Math.max(0, Math.min(opts.length - 1, idx))];
        int bw = 64, bh = 12, bx = cx + cw - PAD - bw, by = y + 1;
        boolean hov = inside(mouseX, mouseY, bx, by, bw, bh);
        FlatRect.fill(ctx, bx, by, bw, bh, 2, hov ? Colors.ACCENT_SOFT : Colors.SURFACE);
        FlatRect.border(ctx, bx, by, bw, bh, 2, Colors.BORDER_STRONG);
        HudEditSidePanel.arcText(ctx, this.font, cur,
            bx + (bw - HudEditSidePanel.arcW(this.font, cur)) / 2, by + 2, Colors.FOREGROUND);
        cycles.add(new Cycle(bx, by, bw, bh, idx, opts.length, onSet));
        return y + ROW;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        for (Toggle t : toggles) {
            if (inside((int) mx, (int) my, t.x, t.y, t.w, t.h)) {
                t.onSet.accept(!t.state);
                RebornHudClient.config().save();
                return true;
            }
        }
        for (Slider s : sliders) {
            if (inside((int) mx, (int) my, s.x, s.y, s.w, s.h)) {
                int v = s.min + (int) Math.round((mx - s.x) / (double) s.w * (s.max - s.min));
                s.onSet.accept(Math.max(s.min, Math.min(s.max, v)));
                RebornHudClient.config().save();
                return true;
            }
        }
        for (Swatch sw : swatches) {
            if (inside((int) mx, (int) my, sw.x, sw.y, sw.size, sw.size)) {
                settings.highlightColor = sw.color;
                RebornHudClient.config().save();
                return true;
            }
        }
        for (Cycle c : cycles) {
            if (inside((int) mx, (int) my, c.x, c.y, c.w, c.h)) {
                c.onSet.accept((c.idx + 1) % c.count);
                RebornHudClient.config().save();
                return true;
            }
        }
        int bY = cardY() + cardH() - 26;
        if (inside((int) mx, (int) my, cardX() + PAD, bY, cardW() - PAD * 2, 16)) { onClose(); return true; }
        // Clic hors carte → ferme.
        if (!inside((int) mx, (int) my, cardX(), cardY(), cardW(), cardH())) { onClose(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        RebornHudClient.config().save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private record Toggle(int x, int y, int w, int h, boolean state,
                          java.util.function.Consumer<Boolean> onSet) {}
    private record Slider(int x, int y, int w, int h, int min, int max,
                          java.util.function.IntConsumer onSet) {}
    private record Swatch(int x, int y, int size, int color) {}
    private record Cycle(int x, int y, int w, int h, int idx, int count,
                         java.util.function.IntConsumer onSet) {}
}
