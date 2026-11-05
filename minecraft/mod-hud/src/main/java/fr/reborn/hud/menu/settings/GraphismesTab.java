package fr.reborn.hud.menu.settings;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * Onglet GRAPHISMES (gabarit o2 du lot 3) : Affichage, Rendu, Effets. Remplace l'onglet Vidéo et les options vidéo de
 * Minecraft (CADRAGE §8.2, §8.4) : <b>uniquement des réglages réellement câblés</b> sur {@code mc.options}, plus la
 * « Taille des menus Reborn » ({@link RebornPrefs#menusPetits}, lue par {@code ui/MenuScale}).
 */
public final class GraphismesTab {

    private GraphismesTab() {}

    /** Images par seconde proposées ; 260 = « Sans limite » (le défaut Reborn, {@code runtime/FpsDefauts}). */
    private static final int[] FPS = {30, 60, 120, 144, 240, 260};
    private static final String[] FPS_LIB = {"30 i/s", "60 i/s", "120 i/s", "144 i/s", "240 i/s", "Sans limite"};

    public static OngletPages onglet() {
        return new OngletPages(
            new OngletPages.Page("Affichage", new PageReglages(GraphismesTab::affichage), GraphismesTab::resetAffichage),
            new OngletPages.Page("Rendu", new PageReglages(GraphismesTab::rendu), GraphismesTab::resetRendu),
            new OngletPages.Page("Effets", new PageReglages(GraphismesTab::effets), GraphismesTab::resetEffets));
    }

    private static Options options() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.options;
    }

    // ─── Affichage ───────────────────────────────────────
    private static void affichage(PageReglages p) {
        Options o = options();
        if (o == null) return;
        RebornPrefs prefs = RebornPrefs.INSTANCE;

        p.choix("Taille des menus Reborn", "Automatique : comme avant, à la hauteur de l'écran. Petite : un cran plus petit.",
            new SegmentedControl.Option[] {
                new SegmentedControl.Option("auto", "Automatique"),
                new SegmentedControl.Option("petite", "Petite"),
            },
            prefs.menusPetits ? "petite" : "auto",
            v -> {
                prefs.menusPetits = "petite".equals(v);
                prefs.save();
                SectionedTab.demanderRedimension();
            });

        p.choix("Taille du HUD en jeu", "Barre de vie, rappels, tchat, écrans de Minecraft ; les menus Reborn ne bougent pas.",
            new SegmentedControl.Option[] {
                new SegmentedControl.Option("0", "Auto"),
                new SegmentedControl.Option("1", "1"),
                new SegmentedControl.Option("2", "2"),
                new SegmentedControl.Option("3", "3"),
                new SegmentedControl.Option("4", "4"),
            },
            String.valueOf(o.guiScale().get()),
            v -> applyGuiScale(parseInt(v, 0)));

        p.curseur("Distance de rendu", "Plus haut : plus lourd à charger.", o.renderDistance().get(), 2, 32, "",
            v -> set(o, o.renderDistance(), v));
        p.curseur("Champ de vision", "70 par défaut ; plus large, la vue se déforme sur les bords.", o.fov().get(), 30, 110,
            "", v -> set(o, o.fov(), v));
        p.curseur("Luminosité", null, (int) Math.round(o.gamma().get() * 100), 0, 100, "%",
            v -> set(o, o.gamma(), v / 100.0));
        p.liste("Images par seconde", "Limite du jeu ; 60 suffit en RP.", FPS_LIB, indexFps(o.framerateLimit().get()),
            i -> set(o, o.framerateLimit(), FPS[i]));
        p.bascule("Synchronisation verticale", "Limite les déchirures, plafonne aux Hz de l'écran.", o.enableVsync().get(),
            v -> set(o, o.enableVsync(), v));
        Minecraft mc = Minecraft.getInstance();
        p.bascule("Plein écran", "Aussi avec F11.", mc.getWindow().isFullscreen(), GraphismesTab::applyFullscreen);
    }

    private static void resetAffichage() {
        Options o = options();
        if (o == null) return;
        RebornPrefs.INSTANCE.menusPetits = false;
        RebornPrefs.INSTANCE.save();
        o.guiScale().set(0);
        o.renderDistance().set(12);
        o.fov().set(70);
        o.gamma().set(0.5);
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
        o.save();
        SectionedTab.demanderRedimension();
    }

    // ─── Rendu ───────────────────────────────────────────
    /**
     * Rapide et Détaillée seulement : « Fabuleuse » demande un contrôle de la carte graphique (écran d'avertissement de
     * Minecraft) et pèse trop pour la cible 4 Go ; elle reste dans les réglages avancés. Un préréglage hors liste
     * s'affiche en troisième valeur, que l'on ne peut pas choisir.
     */
    private static final GraphicsPreset[] PRESETS = {GraphicsPreset.FAST, GraphicsPreset.FANCY};
    private static final String[] PRESETS_LIB = {"Rapide", "Détaillée"};
    private static final String[] PRESETS_LIB_FAB = {"Rapide", "Détaillée", "Fabuleuse"};
    private static final String[] PRESETS_LIB_PERSO = {"Rapide", "Détaillée", "Personnalisée"};
    private static final CloudStatus[] NUAGES = {CloudStatus.OFF, CloudStatus.FAST, CloudStatus.FANCY};
    private static final String[] NUAGES_LIB = {"Aucun", "Rapides", "Détaillés"};
    private static final ParticleStatus[] PARTICULES = {ParticleStatus.ALL, ParticleStatus.DECREASED, ParticleStatus.MINIMAL};
    private static final String[] PARTICULES_LIB = {"Toutes", "Réduites", "Minimales"};

    private static void rendu(PageReglages p) {
        Options o = options();
        if (o == null) return;
        GraphicsPreset gp = o.graphicsPreset().get();
        int iPreset = indexOf(PRESETS, gp);
        // « Personnalisée » s'affiche quand un réglage a quitté le préréglage ; un clic repart sur Rapide
        p.liste("Qualité graphique", "Change d'un coup les réglages ci-dessous.",
            iPreset >= 0 ? PRESETS_LIB : gp == GraphicsPreset.FABULOUS ? PRESETS_LIB_FAB : PRESETS_LIB_PERSO,
            iPreset < 0 ? 2 : iPreset,
            i -> {
                if (i < PRESETS.length) {
                    o.graphicsPreset().set(PRESETS[i]);
                    o.save();
                }
                SectionedTab.demanderRafraichir();
            });
        p.liste("Nuages", null, NUAGES_LIB, Math.max(0, indexOf(NUAGES, o.cloudStatus().get())),
            i -> setPerso(o, o.cloudStatus(), NUAGES[i]));
        p.liste("Particules", "Fumées, étincelles, effets des techniques de Minecraft.", PARTICULES_LIB,
            Math.max(0, indexOf(PARTICULES, o.particles().get())), i -> setPerso(o, o.particles(), PARTICULES[i]));
        p.curseur("Distance de simulation", "Distance où le monde bouge encore autour de toi.",
            o.simulationDistance().get(), 5, 32, "", v -> setPerso(o, o.simulationDistance(), v));
        p.curseur("Distance des entités", "Joueurs, objets et animaux visibles de plus loin.",
            (int) Math.round(o.entityDistanceScaling().get() * 100), 50, 500, "%",
            v -> setPerso(o, o.entityDistanceScaling(), v / 100.0));
        p.bascule("Ombres des entités", null, o.entityShadows().get(), v -> setPerso(o, o.entityShadows(), v));
        p.bascule("Occlusion ambiante", "Ombres douces dans les coins et sous les blocs.", o.ambientOcclusion().get(),
            v -> setPerso(o, o.ambientOcclusion(), v));
        p.curseur("Mélange des biomes", "Transition des couleurs d'herbe et d'eau entre biomes.",
            o.biomeBlendRadius().get(), 0, 7, "", v -> setPerso(o, o.biomeBlendRadius(), v));
    }

    private static void resetRendu() {
        Options o = options();
        if (o == null) return;
        o.graphicsPreset().set(GraphicsPreset.FANCY);
        o.simulationDistance().set(12);
        o.entityDistanceScaling().set(1.0);
        o.biomeBlendRadius().set(2);
        o.save();
    }

    // ─── Effets ──────────────────────────────────────────
    private static void effets(PageReglages p) {
        Options o = options();
        if (o == null) return;
        p.bascule("Balancement de la vue", "La caméra suit le pas.", o.bobView().get(), v -> set(o, o.bobView(), v));
        p.curseur("Distorsion de l'écran", "Nausée, portails.", pct(o.screenEffectScale()), 0, 100, "%",
            v -> set(o, o.screenEffectScale(), v / 100.0));
        p.curseur("Effet de champ de vision", "La vue s'élargit en courant.", pct(o.fovEffectScale()), 0, 100, "%",
            v -> set(o, o.fovEffectScale(), v / 100.0));
        p.curseur("Secousse aux dégâts", "La caméra penche quand tu es touché.", pct(o.damageTiltStrength()), 0, 100, "%",
            v -> set(o, o.damageTiltStrength(), v / 100.0));
        p.curseur("Effet d'obscurité", null, pct(o.darknessEffectScale()), 0, 100, "%",
            v -> set(o, o.darknessEffectScale(), v / 100.0));
        p.bascule("Vignette", "Bords de l'écran assombris.", o.vignette().get(), v -> set(o, o.vignette(), v));
        p.bascule("Éclairs", "Le ciel s'illumine pendant l'orage.", !o.hideLightningFlash().get(),
            v -> set(o, o.hideLightningFlash(), !v));
    }

    private static void resetEffets() {
        Options o = options();
        if (o == null) return;
        o.bobView().set(true);
        o.screenEffectScale().set(1.0);
        o.fovEffectScale().set(1.0);
        o.damageTiltStrength().set(1.0);
        o.darknessEffectScale().set(1.0);
        o.vignette().set(true);
        o.hideLightningFlash().set(false);
        o.save();
    }

    // ─── Outils ──────────────────────────────────────────
    /** Appliqué tout de suite ; {@code options.txt} écrit au relâché d'un curseur ({@link Enregistrement}). */
    private static <T> void set(Options o, OptionInstance<T> opt, T v) {
        opt.set(v);
        Enregistrement.options();
    }

    /**
     * Réglage de rendu : Minecraft peut basculer le préréglage en « Personnalisée » ; la page n'est relue que si le
     * préréglage affiché a changé (et l'écran attend la fin d'un glissé pour la refaire).
     */
    private static <T> void setPerso(Options o, OptionInstance<T> opt, T v) {
        GraphicsPreset avant = o.graphicsPreset().get();
        set(o, opt, v);
        if (o.graphicsPreset().get() != avant) SectionedTab.demanderRafraichir();
    }

    private static int pct(OptionInstance<Double> opt) {
        return (int) Math.round(opt.get() * 100);
    }

    private static <T> int indexOf(T[] tab, T v) {
        for (int i = 0; i < tab.length; i++) if (tab[i] == v) return i;
        return -1;
    }

    private static int indexFps(int fps) {
        int best = 0;
        for (int i = 1; i < FPS.length; i++) if (Math.abs(FPS[i] - fps) < Math.abs(FPS[best] - fps)) best = i;
        return best;
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Échelle du HUD (et des écrans de Minecraft) ; re-mise en page immédiate. */
    private static void applyGuiScale(int scale) {
        Options o = options();
        if (o == null) return;
        o.guiScale().set(scale);
        o.save();
        SectionedTab.demanderRedimension();
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
