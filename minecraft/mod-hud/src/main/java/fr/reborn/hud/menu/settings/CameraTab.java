package fr.reborn.hud.menu.settings;

import fr.reborn.hud.animation.AnimationMenuScreen;
import fr.reborn.hud.camera.CameraPreset;
import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Onglet Caméra — expose la vue épaule 3e personne Reborn. Depuis le 15/10 c'est le seul endroit (avec le menu caméra
 * qu'il ouvre) où l'on règle le style Reborn / Minecraft, l'épaule, le préréglage et le repositionnement : les touches
 * Y / U / I / O / ' ont disparu, la touche Vue (F5) bascule seulement épaule ↔ 1ʳᵉ personne. Tout est câblé en direct
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
        Minecraft mc0 = Minecraft.getInstance();
        String vue = mc0 != null && mc0.options != null
            ? mc0.options.keyTogglePerspective.getTranslatedKeyMessage().getString() : "F5";

        section("Caméra");

        row("Style", "Reborn : épaule et 1re personne (" + vue + "). Minecraft : vues d'origine",
            (cx, cy, cw) -> new SegmentedControl(cx, cy, cw, 24,
                new SegmentedControl.Option[] {
                    new SegmentedControl.Option("reborn", "Reborn"),
                    new SegmentedControl.Option("vanilla", "Minecraft"),
                },
                cam.isVanilla() ? "vanilla" : "reborn",
                v -> cam.setVanilla("vanilla".equals(v), Minecraft.getInstance())));

        CameraPreset[] ps = CameraPreset.values();
        SegmentedControl.Option[] opts = new SegmentedControl.Option[ps.length];
        for (int i = 0; i < ps.length; i++) opts[i] = new SegmentedControl.Option(ps[i].name(), ps[i].label);
        row("Préréglage", "Cadrage de départ, ajustable ensuite",
            (cx, cy, cw) -> new SegmentedControl(cx, cy, cw, 24, opts, cam.preset().name(),
                v -> { cam.setPreset(CameraPreset.valueOf(v)); cam.saveToPrefs(); }));

        labelRow("Repositionner en direct", "Distance, décalage, hauteur : la caméra bouge pendant le réglage");
        actionButton("→ Ouvrir le menu caméra", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) mc.setScreenAndShow(new fr.reborn.hud.camera.CameraScreen(parent));
        });

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
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.setScreenAndShow(new AnimationMenuScreen(parent).demarches());
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
