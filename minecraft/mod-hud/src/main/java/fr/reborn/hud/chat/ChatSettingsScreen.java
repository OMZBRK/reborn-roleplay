package fr.reborn.hud.chat;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.ui.style.RebornColors;
import fr.reborn.hud.ui.style.RoundedRect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings panel du chat (toggles + sliders). Accessible via l'icone
 * engrenage du chat header. Modal floating card centrée.
 *
 * <p>Toggles persistes immediatement dans {@link ChatSettings} via
 * {@code HudConfig.save()}. Pas de bouton "Apply" — UX "live preview".
 */
public final class ChatSettingsScreen extends Screen {

    private static final int CARD_WIDTH = 280;

    private final Screen parent;
    private final ChatSettings settings;

    /** Rects des toggles cliquables collectes pendant render. */
    private final List<Toggle> toggles = new ArrayList<>();
    /** Rects des sliders. */
    private final List<Slider> sliders = new ArrayList<>();

    public ChatSettingsScreen(Screen parent) {
        super(Text.literal("Paramètres Chat"));
        this.parent = parent;
        this.settings = RebornHudClient.config().getChatSettings();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        toggles.clear();
        sliders.clear();

        TextRenderer tr = this.textRenderer;
        int cardH = 290;
        int cardX = (this.width - CARD_WIDTH) / 2;
        int cardY = (this.height - cardH) / 2;

        RoundedRect.fill(ctx, cardX, cardY, CARD_WIDTH, cardH, 10, RebornColors.BG_PANEL_ELEVATED);
        RoundedRect.border(ctx, cardX, cardY, CARD_WIDTH, cardH, 10, RebornColors.BORDER_STRONG);

        // Header
        ctx.drawText(tr, Text.literal("PARAMÈTRES CHAT").formatted(Formatting.BOLD),
            cardX + 14, cardY + 12, RebornColors.FOREGROUND, false);
        ctx.fill(cardX + 14, cardY + 26, cardX + CARD_WIDTH - 14, cardY + 27, RebornColors.BORDER);

        // Section Affichage
        int y = cardY + 36;
        ctx.drawText(tr, Text.literal("AFFICHAGE").formatted(Formatting.BOLD),
            cardX + 14, y, RebornColors.FOREGROUND_MUTED, false);
        y += 14;

        y = renderToggle(ctx, cardX, y, "Timestamps en permanence",
            settings.showTimestamps, mouseX, mouseY, b -> settings.showTimestamps = b);
        y = renderToggle(ctx, cardX, y, "Compactage messages",
            settings.compactConsecutive, mouseX, mouseY, b -> settings.compactConsecutive = b);
        y = renderToggle(ctx, cardX, y, "Auto-scroll",
            settings.autoScroll, mouseX, mouseY, b -> settings.autoScroll = b);
        y = renderToggle(ctx, cardX, y, "Highlight mentions",
            settings.highlightMentions, mouseX, mouseY, b -> settings.highlightMentions = b);
        y = renderToggle(ctx, cardX, y, "Son sur mention",
            settings.soundOnMention, mouseX, mouseY, b -> settings.soundOnMention = b);

        // Separator
        y += 2;
        ctx.fill(cardX + 14, y, cardX + CARD_WIDTH - 14, y + 1, RebornColors.BORDER);
        y += 8;

        // Section Dimensions
        ctx.drawText(tr, Text.literal("DIMENSIONS").formatted(Formatting.BOLD),
            cardX + 14, y, RebornColors.FOREGROUND_MUTED, false);
        y += 14;

        y = renderSlider(ctx, cardX, y, "Opacité du panel", settings.opacity, 30, 100,
            "%", mouseX, mouseY, v -> settings.opacity = v);
        y = renderSlider(ctx, cardX, y, "Taille du texte", settings.textSize, 10, 16,
            "px", mouseX, mouseY, v -> settings.textSize = v);

        // Close button bottom
        int closeBtnY = cardY + cardH - 28;
        int closeBtnW = CARD_WIDTH - 28;
        int closeBtnH = 20;
        boolean closeHover = inside(mouseX, mouseY, cardX + 14, closeBtnY, closeBtnW, closeBtnH);
        RoundedRect.fill(ctx, cardX + 14, closeBtnY, closeBtnW, closeBtnH, 6,
            closeHover ? RebornColors.ACCENT_SOFT : RebornColors.BG_INPUT);
        RoundedRect.border(ctx, cardX + 14, closeBtnY, closeBtnW, closeBtnH, 6,
            closeHover ? RebornColors.ACCENT : RebornColors.BORDER_STRONG);
        int closeLabelW = tr.getWidth("Fermer");
        ctx.drawText(tr, Text.literal("Fermer").formatted(Formatting.BOLD),
            cardX + 14 + (closeBtnW - closeLabelW) / 2, closeBtnY + (closeBtnH - tr.fontHeight) / 2,
            closeHover ? RebornColors.ACCENT_HOVER : RebornColors.FOREGROUND, false);
    }

    private int renderToggle(DrawContext ctx, int cardX, int y, String label, boolean state,
                             int mouseX, int mouseY, java.util.function.Consumer<Boolean> onSet) {
        TextRenderer tr = this.textRenderer;
        ctx.drawText(tr, Text.literal(label),
            cardX + 14, y + 4, RebornColors.FOREGROUND, false);

        int switchW = 32, switchH = 14;
        int switchX = cardX + CARD_WIDTH - 14 - switchW;
        int switchY = y;
        int bg = state ? RebornColors.SUCCESS : 0x1FFFFFFF;
        RoundedRect.fill(ctx, switchX, switchY, switchW, switchH, switchH / 2, bg);
        int thumbS = switchH - 4;
        int thumbX = state ? switchX + switchW - thumbS - 2 : switchX + 2;
        RoundedRect.fill(ctx, thumbX, switchY + 2, thumbS, thumbS, thumbS / 2, 0xFFFFFFFF);

        toggles.add(new Toggle(switchX, switchY, switchW, switchH, state, onSet));
        return y + 18;
    }

    private int renderSlider(DrawContext ctx, int cardX, int y, String label, int value,
                             int min, int max, String unit, int mouseX, int mouseY,
                             java.util.function.IntConsumer onSet) {
        TextRenderer tr = this.textRenderer;
        ctx.drawText(tr, Text.literal(label),
            cardX + 14, y, RebornColors.FOREGROUND, false);
        String vStr = value + unit;
        int vW = tr.getWidth(vStr);
        ctx.drawText(tr, Text.literal(vStr),
            cardX + CARD_WIDTH - 14 - vW, y, RebornColors.ACCENT_HOVER, false);

        int trackY = y + 12;
        int trackX = cardX + 14;
        int trackW = CARD_WIDTH - 28;
        int fillX = trackX + (value - min) * trackW / Math.max(1, max - min);
        RoundedRect.fill(ctx, trackX, trackY, trackW, 3, 1, 0x1FFFFFFF);
        RoundedRect.fill(ctx, trackX, trackY, fillX - trackX, 3, 1, RebornColors.ACCENT);

        int thumbR = 5;
        RoundedRect.fill(ctx, fillX - thumbR, trackY - thumbR + 1, thumbR * 2, thumbR * 2, thumbR, 0xFFFFFFFF);

        sliders.add(new Slider(trackX, trackY - 4, trackW, 14, min, max, onSet));
        return y + 22;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        for (Toggle t : toggles) {
            if (inside((int) mouseX, (int) mouseY, t.x, t.y, t.w, t.h)) {
                t.onSet.accept(!t.state);
                RebornHudClient.config().save();
                return true;
            }
        }
        for (Slider s : sliders) {
            if (inside((int) mouseX, (int) mouseY, s.x, s.y, s.w, s.h)) {
                int v = s.min + (int) Math.round((mouseX - s.x) / (double) s.w * (s.max - s.min));
                v = Math.max(s.min, Math.min(s.max, v));
                s.onSet.accept(v);
                RebornHudClient.config().save();
                return true;
            }
        }
        // Close button hit-test
        int cardH = 290;
        int cardX = (this.width - CARD_WIDTH) / 2;
        int cardY = (this.height - cardH) / 2;
        int closeBtnY = cardY + cardH - 28;
        if (inside((int) mouseX, (int) mouseY, cardX + 14, closeBtnY, CARD_WIDTH - 28, 20)) {
            close();
            return true;
        }
        // Click outside card → close
        if (!inside((int) mouseX, (int) mouseY, cardX, cardY, CARD_WIDTH, cardH)) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        RebornHudClient.config().save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private record Toggle(int x, int y, int w, int h, boolean state,
                          java.util.function.Consumer<Boolean> onSet) {}
    private record Slider(int x, int y, int w, int h, int min, int max,
                          java.util.function.IntConsumer onSet) {}
}
