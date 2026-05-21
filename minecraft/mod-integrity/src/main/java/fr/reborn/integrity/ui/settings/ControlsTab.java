package fr.reborn.integrity.ui.settings;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab Contrôles — affichage des keybinds RP en mode readonly + lien
 * vers ControlsOptionsScreen vanilla pour le rebind effectif.
 * Référence : {@code settings.jsx::ControlsTab}.
 */
public class ControlsTab implements SettingsTab {

    private final Screen parent;
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    private static final int ROW_HEIGHT = 26;

    /** Keybinds RP affichés (display only — pas de rebind dans cette tab). */
    private static final String[][] BINDS = {
        {"Avancer", "Z"},
        {"Reculer", "S"},
        {"Gauche", "Q"},
        {"Droite", "D"},
        {"Sauter", "ESPACE"},
        {"Sprint", "CTRL G."},
        {"Chat RP", "T"},
        {"Chat OOC", "U"},
        {"Emote / Action", "K"},
        {"Fiche personnage", "F1"},
        {"Carte régionale", "M"},
        {"Push-to-Talk", "V"},
    };

    public ControlsTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        int cursorY = y + BINDS.length * ROW_HEIGHT + 16;

        // Lien rebind avancé.
        ButtonWidget advBtn = ButtonWidget.builder(
            RebornFont.body("→ Tous les contrôles (vanilla)"),
            b -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.setScreen(new ControlsOptionsScreen(parent, mc.options));
                }
            }
        ).dimensions(x + width - 240, cursorY, 240, 22).build();
        widgets.add(advBtn);
        cursorY += 40;

        contentHeight = cursorY - y;
    }

    @Override public List<ClickableWidget> widgets() { return widgets; }
    @Override public int height() { return contentHeight; }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y - 28, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(tr, RebornFont.bold("TOUCHES ESSENTIELLES · RP"), 0, 0,
            Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        int cursorY = y;
        for (String[] bind : BINDS) {
            // Label gauche.
            ctx.drawText(tr, RebornFont.body(bind[0]), x, cursorY + 7, Colors.FOREGROUND, false);

            // Key pill droite.
            Text keyLabel = RebornFont.bold(bind[1]);
            int keyW = tr.getWidth(keyLabel) + 16;
            int keyH = 16;
            int keyX = x + width - keyW;
            DrawHelpers.roundedOutlinedRect(ctx, keyX, cursorY + 3, keyW, keyH, 4,
                Colors.BACKGROUND, Colors.BORDER_STRONG);
            ctx.drawText(tr, keyLabel, keyX + 8, cursorY + 7, Colors.FOREGROUND, false);

            cursorY += ROW_HEIGHT;
        }
    }
}
