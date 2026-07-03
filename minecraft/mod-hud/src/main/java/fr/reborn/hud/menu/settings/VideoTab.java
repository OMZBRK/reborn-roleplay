package fr.reborn.hud.menu.settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;

import java.util.function.Consumer;

/**
 * Onglet Vidéo — <b>uniquement des réglages réellement câblés</b> sur
 * {@code mc.options} (client-side). Les anciens contrôles morts (Résolution,
 * Mode fenêtre) et le bouton « Options avancées Minecraft » ont été retirés :
 * toute redirection vanilla vit désormais dans l'onglet Minecraft.
 */
public class VideoTab extends SectionedTab {

    @SuppressWarnings("unused")
    private final Screen parent;

    public VideoTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected void build() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions o = mc.options;

        section("Affichage");

        // Échelle interface (GUI) — LE réglage anti-menus-géants.
        int guiScale = o.getGuiScale().getValue();
        row("Échelle de l'interface", "Réduisez si les menus dépassent de l'écran",
            (cx, cy, cw) -> new SegmentedControl(cx, cy, cw, 24,
                new SegmentedControl.Option[] {
                    new SegmentedControl.Option("0", "Auto"),
                    new SegmentedControl.Option("1", "1"),
                    new SegmentedControl.Option("2", "2"),
                    new SegmentedControl.Option("3", "3"),
                },
                String.valueOf(guiScale),
                v -> applyGuiScale(parseInt(v, 0))));

        // FPS Max.
        row("FPS max", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                o.getMaxFps().getValue(), 30, 260, " fps",
                v -> apply(o, opts -> opts.getMaxFps().setValue(v))));

        // Distance de rendu.
        row("Distance de rendu", "Plus haut = plus lourd à charger",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                o.getViewDistance().getValue(), 4, 32, " chunks",
                v -> apply(o, opts -> opts.getViewDistance().setValue(v))));

        // Luminosité (gamma 0..1 exposé en %).
        int gammaPct = (int) Math.round(o.getGamma().getValue() * 100);
        row("Luminosité", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                gammaPct, 0, 100, "%",
                v -> apply(o, opts -> opts.getGamma().setValue(v / 100.0))));

        section("Fenêtre");

        // V-Sync.
        row("V-Sync", "Limite le tearing, plafonne aux Hz de l'écran",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.getEnableVsync().getValue(),
                v -> apply(o, opts -> opts.getEnableVsync().setValue(v))));

        // Plein écran — bascule la vraie fenêtre.
        row("Plein écran", null,
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                mc.getWindow().isFullscreen(),
                v -> applyFullscreen(v)));

        spacer(4);
    }

    private static void apply(GameOptions o, Consumer<GameOptions> change) {
        change.accept(o);
        o.write();
    }

    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    /** Applique l'échelle GUI + re-layout immédiat (sinon effet au prochain resize). */
    private static void applyGuiScale(int scale) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        mc.options.getGuiScale().setValue(scale);
        mc.options.write();
        mc.onResolutionChanged();
    }

    /** Bascule le plein écran réel de la fenêtre (idempotent via l'état courant). */
    private static void applyFullscreen(boolean fullscreen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.getWindow().isFullscreen() != fullscreen) {
            mc.getWindow().toggleFullscreen();
        }
        mc.options.getFullscreen().setValue(fullscreen);
        mc.options.write();
    }
}
