package fr.reborn.integrity.ui.settings;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab Discord — 3 toggles + preview card live de la Rich Presence.
 * Référence : {@code settings.jsx::DiscordTab}.
 */
public class DiscordTab implements SettingsTab {

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    private static final int ROW_HEIGHT = 38;
    private static final int CONTROL_W = 240;

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        int cursorY = y;
        int controlX = x + width - CONTROL_W;

        widgets.add(new ToggleBig(controlX + CONTROL_W - ToggleBig.DEFAULT_WIDTH,
            cursorY + 4, prefs.discordEnabled,
            v -> { prefs.discordEnabled = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        widgets.add(new ToggleBig(controlX + CONTROL_W - ToggleBig.DEFAULT_WIDTH,
            cursorY + 4, prefs.discordShowCharacter,
            v -> { prefs.discordShowCharacter = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        widgets.add(new ToggleBig(controlX + CONTROL_W - ToggleBig.DEFAULT_WIDTH,
            cursorY + 4, prefs.discordShowMap,
            v -> { prefs.discordShowMap = v; prefs.save(); }));
        cursorY += ROW_HEIGHT + 20;

        // Preview card height (calculée dans renderPassive).
        cursorY += 100;

        contentHeight = cursorY - y;
    }

    @Override public List<ClickableWidget> widgets() { return widgets; }
    @Override public int height() { return contentHeight; }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;
        RebornPrefs prefs = RebornPrefs.INSTANCE;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y - 28, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(tr, RebornFont.bold("DISCORD RICH PRESENCE"), 0, 0,
            Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        String[][] rows = {
            {"Activer Rich Presence", "Affiche ton activité Reborn sur Discord"},
            {"Afficher mon personnage RP", null},
            {"Afficher la map actuelle", null},
        };

        int cursorY = y;
        for (String[] row : rows) {
            ctx.drawText(tr, RebornFont.bold(row[0]), x, cursorY + 8, Colors.WHITE_PURE, false);
            if (row[1] != null) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x, cursorY + 20, 0);
                ctx.getMatrices().scale(0.85f, 0.85f, 1f);
                ctx.drawText(tr, RebornFont.body(row[1]), 0, 0, Colors.FOREGROUND_MUTED, false);
                ctx.getMatrices().pop();
            }
            cursorY += ROW_HEIGHT;
        }

        // Preview card.
        cursorY += 20;
        ctx.drawText(tr, RebornFont.bold("APERÇU"), x, cursorY, Colors.FOREGROUND_SUBTLE, false);
        cursorY += 14;

        int previewH = 80;
        DrawHelpers.roundedOutlinedRect(ctx, x, cursorY, width, previewH, 8,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // Avatar carré "RB".
        int avatarSize = 48;
        DrawHelpers.roundedRect(ctx, x + 12, cursorY + 16, avatarSize, avatarSize, 6,
            Colors.ACCENT_SOFT);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 12 + avatarSize / 2 - 8, cursorY + 16 + avatarSize / 2 - 6, 0);
        ctx.getMatrices().scale(1.4f, 1.4f, 1f);
        ctx.drawText(tr, RebornFont.bold("RB"), 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // Texte preview.
        int textX = x + 12 + avatarSize + 12;
        ctx.drawText(tr, RebornFont.bold("Joue à Reborn Roleplay"),
            textX, cursorY + 14, Colors.WHITE_PURE, false);
        int lineY = cursorY + 28;
        if (prefs.discordShowCharacter) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(textX, lineY, 0);
            ctx.getMatrices().scale(0.85f, 0.85f, 1f);
            ctx.drawText(tr, RebornFont.body("Personnage · Hikami Yorishiro"),
                0, 0, Colors.FOREGROUND_SUBTLE, false);
            ctx.getMatrices().pop();
            lineY += 11;
        }
        if (prefs.discordShowMap) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(textX, lineY, 0);
            ctx.getMatrices().scale(0.85f, 0.85f, 1f);
            ctx.drawText(tr, RebornFont.body("Région · Vallée des Lanternes"),
                0, 0, Colors.FOREGROUND_SUBTLE, false);
            ctx.getMatrices().pop();
            lineY += 11;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(textX, cursorY + previewH - 16, 0);
        ctx.getMatrices().scale(0.8f, 0.8f, 1f);
        ctx.drawText(tr, RebornFont.body("Joue depuis 2 h 12"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
    }
}
