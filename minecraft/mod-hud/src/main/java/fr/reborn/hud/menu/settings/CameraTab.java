package fr.reborn.hud.menu.settings;

import fr.reborn.hud.animation.AnimationMenuScreen;
import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Onglet Caméra — expose la vue épaule 3e personne Reborn (feature phare
 * jusque-là seulement ajustable au clavier en jeu). Tout est câblé en direct
 * sur {@link RebornCamera#INSTANCE} et persisté via {@code saveToPrefs()}.
 */
public class CameraTab extends SectionedTab {

    // Bornes miroir de RebornCamera (package-privées là-bas), exposées en %.
    private static final double DIST_MIN = 2.0, DIST_MAX = 6.5;
    private static final double UP_MIN = -0.6, UP_MAX = 1.2;
    private static final double TURN_MIN = 0.1, TURN_MAX = 1.0;

    private final Screen parent;

    public CameraTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected void build() {
        RebornCamera cam = RebornCamera.INSTANCE;

        section("Vue 3e personne");

        row("Épaule", "Côté par-dessus lequel la caméra regarde",
            (cx, cy, cw) -> new SegmentedControl(cx, cy, cw, 24,
                new SegmentedControl.Option[] {
                    new SegmentedControl.Option("l", "Gauche"),
                    new SegmentedControl.Option("r", "Droite"),
                },
                cam.side() < 0 ? "l" : "r",
                v -> { cam.setSide("l".equals(v) ? -1 : 1); cam.saveToPrefs(); }));

        row("Distance", "Recul de la caméra derrière le personnage",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pctOf(cam.distance(), DIST_MIN, DIST_MAX), 0, 100, "%",
                v -> { cam.setDistance(fromPct(v, DIST_MIN, DIST_MAX)); cam.saveToPrefs(); }));

        row("Hauteur", "Décalage vertical de la visée",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pctOf(cam.upOffset(), UP_MIN, UP_MAX), 0, 100, "%",
                v -> { cam.setUp(fromPct(v, UP_MIN, UP_MAX)); cam.saveToPrefs(); }));

        row("Vitesse de rotation", "Réactivité du corps vers la direction",
            (cx, cy, cw) -> new SliderWidget(cx, cy, cw, 24,
                pctOf(cam.turnSpeed(), TURN_MIN, TURN_MAX), 0, 100, "%",
                v -> { cam.setTurnSpeed(fromPct(v, TURN_MIN, TURN_MAX)); cam.saveToPrefs(); }));

        section("Démarche");

        labelRow("Style de marche", "Animation utilisée quand vous marchez");
        actionButton("→ Choisir ma démarche", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) mc.setScreen(new AnimationMenuScreen(parent));
        });

        spacer(4);
    }

    private static int pctOf(double v, double min, double max) {
        return (int) Math.round(clamp01((v - min) / (max - min)) * 100);
    }

    private static double fromPct(int p, double min, double max) {
        return min + (p / 100.0) * (max - min);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
