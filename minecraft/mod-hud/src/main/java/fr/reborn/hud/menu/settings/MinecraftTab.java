package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Onglet « Minecraft » des Paramètres Reborn — donne accès aux réglages
 * <b>vanilla de base</b>. On ouvre l'{@code OptionsScreen} standard (dont le
 * {@code OptionsScreenMixin} masque déjà Resource Packs / Skin / Online, non
 * pertinents sur un serveur RP), plus deux raccourcis directs Vidéo/Contrôles.
 */
public class MinecraftTab implements SettingsTab {

    private static final int ROW_H = 24;
    private static final int GAP = 8;
    private static final int MAX_W = 360;

    private final Screen parent;
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentH = 0;

    public MinecraftTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        int bw = Math.min(width, MAX_W);
        int cy = y + 6;

        widgets.add(ButtonWidget.builder(RebornFont.body("Options Minecraft (complètes)"),
                b -> mc.setScreen(new OptionsScreen(parent, mc.options)))
            .dimensions(x, cy, bw, ROW_H).build());
        cy += ROW_H + GAP;

        widgets.add(ButtonWidget.builder(RebornFont.body("Vidéo"),
                b -> mc.setScreen(new VideoOptionsScreen(parent, mc, mc.options)))
            .dimensions(x, cy, bw, ROW_H).build());
        cy += ROW_H + GAP;

        widgets.add(ButtonWidget.builder(RebornFont.body("Contrôles"),
                b -> mc.setScreen(new ControlsOptionsScreen(parent, mc.options)))
            .dimensions(x, cy, bw, ROW_H).build());
        cy += ROW_H + GAP;

        contentH = (cy - y) + 22; // marge pour le hint
    }

    @Override
    public List<ClickableWidget> widgets() {
        return widgets;
    }

    @Override
    public int height() {
        return contentH;
    }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        ctx.drawText(mc.textRenderer,
            RebornFont.body("Réglages Minecraft de base. Les packs de ressources sont gérés par le launcher."),
            x, y + contentH - 14, Colors.FOREGROUND_MUTED, false);
    }
}
