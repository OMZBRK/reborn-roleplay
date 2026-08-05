package fr.reborn.hud.menu.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Options;

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
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Options o = mc.options;

        section("Affichage");

        // Échelle interface (GUI) — LE réglage anti-menus-géants.
        int guiScale = o.guiScale().get();
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
                o.framerateLimit().get(), 30, 260, " fps",
                v -> apply(o, opts -> opts.framerateLimit().set(v))));

        // Distance de rendu.
        row("Distance de rendu", "Plus haut = plus lourd à charger",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                o.renderDistance().get(), 4, 32, " chunks",
                v -> apply(o, opts -> opts.renderDistance().set(v))));

        // Luminosité (gamma 0..1 exposé en %).
        int gammaPct = (int) Math.round(o.gamma().get() * 100);
        row("Luminosité", null,
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                gammaPct, 0, 100, "%",
                v -> apply(o, opts -> opts.gamma().set(v / 100.0))));

        section("Fenêtre");

        // V-Sync.
        row("V-Sync", "Limite le tearing, plafonne aux Hz de l'écran",
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                o.enableVsync().get(),
                v -> apply(o, opts -> opts.enableVsync().set(v))));

        // Plein écran — bascule la vraie fenêtre.
        row("Plein écran", null,
            (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH, cy,
                mc.getWindow().isFullscreen(),
                v -> applyFullscreen(v)));

        spacer(4);
    }

    private static void apply(Options o, Consumer<Options> change) {
        change.accept(o);
        o.save();
    }

    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    /** Applique l'échelle GUI + re-layout immédiat (sinon effet au prochain resize). */
    private static void applyGuiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        mc.options.guiScale().set(scale);
        mc.options.save();
        mc.resizeGui();
    }

    /** Bascule le plein écran réel de la fenêtre (idempotent via l'état courant). */
    private static void applyFullscreen(boolean fullscreen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.getWindow().isFullscreen() != fullscreen) {
            mc.getWindow().toggleFullScreen();
        }
        mc.options.fullscreen().set(fullscreen);
        mc.options.save();
    }
}
