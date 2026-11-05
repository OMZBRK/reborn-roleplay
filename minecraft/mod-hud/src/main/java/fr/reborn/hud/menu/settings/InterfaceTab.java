package fr.reborn.hud.menu.settings;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.camera.CameraPreset;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.chat.ChatSettingsScreen;
import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.crosshair.CrosshairScreen;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ui.HudEditScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Onglet INTERFACE (gabarit o2 du lot 3) : HUD, Rappels de touches, Viseur, Caméra, Chat, Noms, Liste des joueurs,
 * Objets au sol. Reprend l'ancien onglet Interface et l'onglet Caméra ; les éditeurs (HUD, viseur, tchat) gardent leur
 * écran, ouvert d'une ligne « action » et qui revient ici.
 *
 * <p><b>Rappels de touches</b> : la visibilité et la taille sont celles de l'élément {@link HudElement#RAPPELS} dans
 * la configuration du HUD (une seule source de vérité avec l'éditeur) ; « Avec des notifications » et « Portée de la
 * voix » sont {@link RebornPrefs#rappelsAvecNotifs} et {@link RebornPrefs#rappelsPortee}, lus par
 * {@code keybind/RappelsTouches} ; « Déplacer » ouvre l'éditeur de HUD avec les rappels déjà choisis.
 */
public final class InterfaceTab {

    private InterfaceTab() {}

    /** Rang des sous-catégories (ouverture directe : ancien onglet Caméra, tchat de Minecraft redirigé). */
    public static final int HUD = 0, RAPPELS = 1, VISEUR = 2, CAMERA = 3, CHAT = 4, NOMS = 5, LISTE = 6, SOL = 7;

    public static OngletPages onglet(Screen ecran) {
        return new OngletPages(
            new OngletPages.Page("HUD", new PageReglages(p -> hud(p, ecran)), InterfaceTab::resetHud),
            new OngletPages.Page("Rappels de touches", new PageReglages(p -> rappels(p, ecran)), InterfaceTab::resetRappels),
            new OngletPages.Page("Viseur", new PageReglages(p -> viseur(p, ecran)), InterfaceTab::resetViseur),
            new OngletPages.Page("Caméra", new CameraTab(ecran), InterfaceTab::resetCamera),
            new OngletPages.Page("Chat", new PageReglages(p -> chat(p, ecran)), null),
            new OngletPages.Page("Noms", new PageReglages(InterfaceTab::noms), InterfaceTab::resetNoms),
            new OngletPages.Page("Liste des joueurs", new PageReglages(InterfaceTab::liste), InterfaceTab::resetListe),
            new OngletPages.Page("Objets au sol", new PageReglages(InterfaceTab::sol), InterfaceTab::resetSol));
    }

    // ─── HUD ─────────────────────────────────────────────
    private static void hud(PageReglages p, Screen ecran) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.action("Éditeur de HUD", "Place et redimensionne les éléments de l'écran.", "Ouvrir",
            () -> ouvrir(new HudEditScreen(ecran)));
        p.bascule("Qui parle", "Noms de ceux qui parlent autour de toi, en bas à droite.", visible(HudElement.QUI_PARLE),
            v -> montrer(HudElement.QUI_PARLE, v));
        p.bascule("Escouade au complet", "L'escouade montre aussi ceux qui se taisent, en retrait.",
            prefs.radioToutEscouade, v -> { prefs.radioToutEscouade = v; prefs.save(); });
    }

    private static void resetHud() {
        montrer(HudElement.QUI_PARLE, true);
        RebornPrefs.INSTANCE.radioToutEscouade = false;
        RebornPrefs.INSTANCE.save();
    }

    // ─── Rappels de touches ──────────────────────────────
    private static void rappels(PageReglages p, Screen ecran) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.bascule("Afficher les rappels",
            "Concentration en temps normal ; le reste en concentration, en combat ou pendant une action.",
            visible(HudElement.RAPPELS), v -> montrer(HudElement.RAPPELS, v));
        p.action("Position", "En bas à droite par défaut. Se déplace dans l'éditeur de HUD.", "Déplacer",
            () -> ouvrir(new HudEditScreen(ecran, HudElement.RAPPELS)));
        p.curseur("Taille", null, Math.round(etat(HudElement.RAPPELS).scale() * 100), 50, 200, "%", v -> {
            HudConfig cfg = RebornHudClient.config();
            cfg.setState(HudElement.RAPPELS, cfg.stateOf(HudElement.RAPPELS).withScale(v / 100f));
            Enregistrement.hud();
        });
        p.bascule("Avec des notifications", "Deux lignes au plus quand une notification est affichée.",
            prefs.rappelsAvecNotifs, v -> { prefs.rappelsAvecNotifs = v; prefs.save(); });
        KeyMapping h = HudKeybinds.VOIX_PORTEE;
        String touche = h != null && !h.isUnbound() ? h.getTranslatedKeyMessage().getString() : null;
        p.bascule("Portée de la voix", touche != null ? "2,5 s après un appui sur " + touche + "."
            : "2,5 s après un changement de portée.", prefs.rappelsPortee, v -> { prefs.rappelsPortee = v; prefs.save(); });
        p.action("Remettre en bas à droite", "Position et taille d'origine.", "Remettre", () -> {
            remettreRappels();
            SectionedTab.demanderRafraichir();
        });
    }

    /** Rappels : position et taille d'origine (en bas à droite), visibilité gardée. */
    private static void remettreRappels() {
        fr.reborn.hud.keybind.RappelsTouches.remettreParDefaut();
    }

    private static void resetRappels() {
        HudConfig cfg = RebornHudClient.config();
        cfg.setState(HudElement.RAPPELS, HudElement.RAPPELS.defaultState().withVisible(true));
        cfg.save();
        RebornPrefs.INSTANCE.rappelsAvecNotifs = true;
        RebornPrefs.INSTANCE.rappelsPortee = true;
        RebornPrefs.INSTANCE.save();
    }

    // ─── Viseur ──────────────────────────────────────────
    private static void viseur(PageReglages p, Screen ecran) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.bascule("Viseur Reborn", "Sinon, le viseur de Minecraft.", prefs.crosshairEnabled,
            v -> { prefs.crosshairEnabled = v; prefs.save(); });
        p.action("Personnaliser le viseur", "Modèle, couleur, réaction aux coups, marque de touche.", "Ouvrir",
            () -> ouvrir(new CrosshairScreen(ecran)));
    }

    /** Viseur : les valeurs de départ de {@link RebornPrefs}. */
    private static void resetViseur() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.crosshairEnabled = false;
        p.crosshairPreset = 0;
        p.crosshairScale = 100;
        p.crosshairColor = 0xFFFFFFFF;
        p.crosshairRainbow = false;
        p.crosshairDynamic = false;
        p.crosshairHitMarker = true;
        p.crosshairStyle = "preset";
        p.crosshairGap = 4;
        p.crosshairLength = 6;
        p.crosshairThickness = 2;
        p.save();
    }

    // ─── Caméra (CameraTab) ──────────────────────────────
    private static void resetCamera() {
        RebornCamera cam = RebornCamera.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (cam.isVanilla() && mc != null) cam.setVanilla(false, mc);
        cam.applyPreset(CameraPreset.values()[0]);
        cam.setSide(1);
        cam.setTurnSpeed(0.5);
        cam.saveToPrefs();
    }

    // ─── Chat ────────────────────────────────────────────
    private static void chat(PageReglages p, Screen ecran) {
        p.action("Tchat Reborn", "Onglets, filtres et apparence du tchat.", "Réglages",
            () -> ouvrir(new ChatSettingsScreen(ecran)));
    }

    // ─── Noms ────────────────────────────────────────────
    private static void noms(PageReglages p) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.choix("Style", "Allure du nom des autres joueurs, au-dessus de leur tête.",
            new SegmentedControl.Option[] {
                new SegmentedControl.Option("0", "Détouré"),
                new SegmentedControl.Option("1", "Filet doré"),
                new SegmentedControl.Option("2", "Village"),
                new SegmentedControl.Option("3", "Ruban"),
            },
            String.valueOf(prefs.nametagStyle),
            v -> { prefs.nametagStyle = Integer.parseInt(v); prefs.save(); });
    }

    private static void resetNoms() {
        RebornPrefs.INSTANCE.nametagStyle = 0;
        RebornPrefs.INSTANCE.save();
    }

    // ─── Liste des joueurs ───────────────────────────────
    private static void liste(PageReglages p) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.bascule("Maintenir pour afficher", "Sinon, un appui ouvre la liste interactive.", prefs.tablistHold,
            v -> { prefs.tablistHold = v; prefs.save(); });
    }

    private static void resetListe() {
        RebornPrefs.INSTANCE.tablistHold = false;
        RebornPrefs.INSTANCE.save();
    }

    // ─── Objets au sol ───────────────────────────────────
    // (La couleur de l'aura de méditation n'est plus choisie ici depuis le 19/10 : article de la boutique de compte.)
    private static void sol(PageReglages p) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        p.bascule("Lueur de rareté", "Petit halo sous les objets au sol, à la couleur de leur rareté.", prefs.aurasSol,
            v -> { prefs.aurasSol = v; prefs.save(); });
    }

    private static void resetSol() {
        RebornPrefs.INSTANCE.aurasSol = true;
        RebornPrefs.INSTANCE.save();
    }

    // ─── Outils ──────────────────────────────────────────
    private static void ouvrir(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreenAndShow(screen);
    }

    private static HudElementState etat(HudElement e) {
        try {
            return RebornHudClient.config().stateOf(e);
        } catch (RuntimeException ex) {
            return e.defaultState();
        }
    }

    private static boolean visible(HudElement e) {
        return etat(e).visible();
    }

    private static void montrer(HudElement e, boolean v) {
        HudConfig cfg = RebornHudClient.config();
        cfg.setState(e, cfg.stateOf(e).withVisible(v));
        cfg.save();
    }
}
