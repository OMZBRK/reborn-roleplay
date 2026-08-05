package fr.reborn.hud.menu.config;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.settings.SettingsTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

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
    private final List<AbstractWidget> widgets = new ArrayList<>();
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
            Button btn = Button.builder(
                RebornFont.body(actionLabel), b -> action.run())
                .bounds(x, cursorY, 220, 22).build();
            widgets.add(btn);
            cursorY += 40;
        }
        contentHeight = cursorY - y;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    @Override
    public int height() {
        return contentHeight;
    }

    @Override
    public void renderPassive(GuiGraphicsExtractor ctx, int x, int y, int width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        // Badge BIENTÔT.
        Component badge = RebornFont.bold("BIENTÔT");
        int badgeW = tr.width(badge) + 16;
        DrawHelpers.roundedOutlinedRect(ctx, x, y, badgeW, 16, 8,
            Colors.GOLD_SOFT, Colors.withAlpha(Colors.GOLD, 0.4f));
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 8, y + 4);
        ctx.pose().scale(0.85f, 0.85f);
        ctx.text(tr, badge, 0, 0, Colors.GOLD, false);
        ctx.pose().popMatrix();

        // Titre.
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y + 26);
        ctx.pose().scale(1.3f, 1.3f);
        ctx.text(tr, RebornFont.bold(title), 0, 0, Colors.WHITE_PURE, false);
        ctx.pose().popMatrix();

        // Sous-titre.
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y + 46);
        ctx.pose().scale(0.9f, 0.9f);
        ctx.text(tr, RebornFont.body(subtitle), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.pose().popMatrix();
    }
}
