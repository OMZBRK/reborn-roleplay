package fr.reborn.hud.menu.config;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.settings.SettingsTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Onglet « à venir » pour les piliers de la refonte encore en construction
 * (HUD / Chat / Crosshair). Affiche un titre + sous-titre + un badge BIENTÔT,
 * et un bouton d'action optionnel (ex. ouvrir l'éditeur HUD existant).
 *
 * <p>Implémente {@link SettingsTab} pour se brancher tel quel dans le hub
 * {@code ConfigShellScreen}.
 */
public class PlaceholderTab implements SettingsTab {

    private final String title;
    private final String subtitle;
    private final String actionLabel; // nullable
    private final Runnable action;    // nullable
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    public PlaceholderTab(String title, String subtitle, String actionLabel, Runnable action) {
        this.title = title;
        this.subtitle = subtitle;
        this.actionLabel = actionLabel;
        this.action = action;
    }

    public PlaceholderTab(String title, String subtitle) {
        this(title, subtitle, null, null);
    }

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        int cursorY = y + 64;
        if (actionLabel != null && action != null) {
            ButtonWidget btn = ButtonWidget.builder(
                RebornFont.body(actionLabel), b -> action.run())
                .dimensions(x, cursorY, 220, 22).build();
            widgets.add(btn);
            cursorY += 40;
        }
        contentHeight = cursorY - y;
    }

    @Override
    public List<ClickableWidget> widgets() {
        return widgets;
    }

    @Override
    public int height() {
        return contentHeight;
    }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        // Badge BIENTÔT.
        Text badge = RebornFont.bold("BIENTÔT");
        int badgeW = tr.getWidth(badge) + 16;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, badgeW, 16, 8,
            Colors.GOLD_SOFT, Colors.withAlpha(Colors.GOLD, 0.4f));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 8, y + 4, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, badge, 0, 0, Colors.GOLD, false);
        ctx.getMatrices().pop();

        // Titre.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y + 26, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1f);
        ctx.drawText(tr, RebornFont.bold(title), 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // Sous-titre.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y + 46, 0);
        ctx.getMatrices().scale(0.9f, 0.9f, 1f);
        ctx.drawText(tr, RebornFont.body(subtitle), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
    }
}
