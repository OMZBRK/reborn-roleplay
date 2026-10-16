package fr.reborn.hud.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Banc de la carte des touches du 15/10, <b>inactif en production</b> ({@code REBORN_TOUCHES_DEBUG=1}, monde solo de
 * dev, jamais de serveur). Les touches passent par le <b>vrai</b> chemin du clavier ({@code KeyboardHandler.keyPress},
 * par réflexion) : écrans, toutes les {@link KeyMapping} liées à la touche, mods qui l'écoutent. Étapes : B bref
 * (écran de l'escouade) puis B maintenu (pas d'écran), F5 (1ʳᵉ personne puis épaule), triple appui sur le sprint
 * (course de chakra, puis arrêt au relâchement), P puis G (galerie depuis le mode photo), Échap → INTERFACE (éditeur
 * de HUD → « Personnaliser le viseur ») et carte Personnage, N (menu OST si mod-ost est là), « . » (émotes,
 * démarches), Réglages → Contrôles et Caméra. Écrit « [touches] OK … » / « ECHEC … » dans le journal et
 * {@code run/touches_banc.txt} (avec la carte réelle des touches et les conflits restants), captures
 * {@code run/screenshots/touches_*.png}, puis ferme le jeu.
 */
public final class TouchesDebug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static final StringBuilder LOG = new StringBuilder();
    private static int t = -1, etape, prochaine, echelleAvant = -1, X, Y, Z;
    private static double x0, y0, z0;
    private static String modeAvant = "survival";
    private static Method keyPress;

    private TouchesDebug() {}

    static void init() {
        if (!"1".equals(System.getenv("REBORN_TOUCHES_DEBUG"))) return;
        programme();
        ClientTickEvents.END_CLIENT_TICK.register(TouchesDebug::tick);
    }

    // ------------------------------------------------------------------ programme

    private static void programme() {
        pas(1, TouchesDebug::preparer, "préparation");
        pas(10, mc -> carte(mc, "carte après migration"), "carte des touches");
        pas(1, TouchesDebug::dispositions, "dispositions simulées");

        // B bref → écran de l'escouade ; B dans l'écran → fermé
        pas(4, mc -> fr.reborn.hud.radio.RadioClient.debugReceive(etatRadio(mc, "ok")), "escouade simulée");
        pas(2, mc -> touche(mc, cle(HudKeybinds.RADIO_PARLER), true), "B pressée");
        pas(2, mc -> touche(mc, cle(HudKeybinds.RADIO_PARLER), false), "B relâchée (100 ms)");
        pas(10, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.radio.RadioScreen, "B bref ouvre l'écran de l'escouade : " + nom(mc)), "vérif");
        pas(1, mc -> shot(mc, "touches_01_b_bref_escouade"), "capture");
        pas(2, mc -> touche(mc, cle(HudKeybinds.RADIO_PARLER), true), "B dans l'écran");
        pas(1, mc -> touche(mc, cle(HudKeybinds.RADIO_PARLER), false), "B relâchée");
        pas(4, mc -> verifie(ecran(mc) == null, "B referme l'écran : " + nom(mc)), "vérif");
        pas(1, mc -> verifie(KeyMapping.get("key.emotecraft.fastchoose") == null
            || KeyMapping.get("key.emotecraft.fastchoose").isUnbound(), "Emotecraft « fastchoose » sans touche"), "vérif");

        // B maintenu → pas d'écran (on parle) ; oreillette coupée = rappel discret dans l'élément Escouade
        pas(2, mc -> fr.reborn.hud.radio.RadioClient.debugReceive(etatRadio(mc, "coupee")), "oreillette coupée");
        pas(2, mc -> touche(mc, cle(HudKeybinds.RADIO_PARLER), true), "B maintenue");
        pas(16, mc -> verifie(ecran(mc) == null, "B maintenue : pas d'écran (" + nom(mc) + ")"), "vérif");
        pas(1, mc -> shot(mc, "touches_02_b_maintenu_parler"), "capture");
        pas(2, mc -> touche(mc, cle(HudKeybinds.RADIO_PARLER), false), "B relâchée (900 ms)");
        pas(6, mc -> verifie(ecran(mc) == null, "B maintenue puis relâchée : toujours pas d'écran (" + nom(mc) + ")"), "vérif");

        // F5 : épaule ↔ 1re personne
        pas(2, TouchesDebug::depart, "départ de la piste");
        pas(10, mc -> touche(mc, cle(mc.options.keyTogglePerspective), true), "Vue pressée");
        pas(1, mc -> touche(mc, cle(mc.options.keyTogglePerspective), false), "Vue relâchée");
        pas(6, mc -> verifie(RebornCamera.INSTANCE.mode() == RebornCamera.Mode.FIRST
            && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON,
            "Vue → 1re personne (" + RebornCamera.INSTANCE.mode() + ", " + mc.options.getCameraType() + ")"), "vérif");
        pas(1, mc -> shot(mc, "touches_03_f5_premiere_personne"), "capture");
        pas(2, mc -> touche(mc, cle(mc.options.keyTogglePerspective), true), "Vue pressée");
        pas(1, mc -> touche(mc, cle(mc.options.keyTogglePerspective), false), "Vue relâchée");
        pas(6, mc -> verifie(RebornCamera.INSTANCE.mode() == RebornCamera.Mode.SHOULDER
            && mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK,
            "Vue → épaule (" + RebornCamera.INSTANCE.mode() + ", " + mc.options.getCameraType() + ")"), "vérif");
        pas(1, mc -> shot(mc, "touches_04_f5_epaule"), "capture");

        // Allures : 2 appuis = sprint, 3 appuis = course de chakra, relâcher = fin (vue de côté : regard libre, Alt)
        pas(2, TouchesDebug::depart, "départ de la piste");
        pas(6, mc -> touche(mc, cle(mc.options.keyUp), true), "Avancer maintenue");
        pas(6, mc -> appuiSprint(mc, true), "sprint 1");
        pas(1, mc -> appuiSprint(mc, false), "");
        pas(2, mc -> appuiSprint(mc, true), "sprint 2 (maintenu)");
        pas(4, mc -> verifie(fr.reborn.hud.allure.Allure.INSTANCE.etat() == fr.reborn.hud.allure.Allure.Etat.SPRINT,
            "2 appuis : sprint (" + fr.reborn.hud.allure.Allure.INSTANCE.etat() + ")"), "vérif");
        pas(1, mc -> deCote(mc, true), "regard libre (Alt), vue de côté");
        pas(8, mc -> shot(mc, "touches_05_sprint_deux_appuis"), "capture");
        pas(1, mc -> appuiSprint(mc, false), "sprint relâché");
        pas(1, mc -> deCote(mc, false), "fin du regard libre");
        pas(1, mc -> touche(mc, cle(mc.options.keyUp), false), "Avancer relâchée");
        pas(10, TouchesDebug::depart, "départ de la piste");
        pas(6, mc -> touche(mc, cle(mc.options.keyUp), true), "Avancer maintenue");
        pas(6, mc -> appuiSprint(mc, true), "triple 1");
        pas(1, mc -> appuiSprint(mc, false), "");
        pas(2, mc -> appuiSprint(mc, true), "triple 2");
        pas(1, mc -> appuiSprint(mc, false), "");
        pas(2, mc -> appuiSprint(mc, true), "triple 3 (maintenu)");
        pas(3, mc -> verifie(!fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive(), "3 appuis : plus de course de chakra (16/10)"), "vérif");
        pas(1, mc -> deCote(mc, true), "regard libre (Alt), vue de côté");
        pas(10, mc -> shot(mc, "touches_06_triple_appui_sans_course"), "capture");
        pas(1, mc -> deCote(mc, false), "fin du regard libre");
        pas(1, mc -> appuiSprint(mc, false), "sprint relâché");
        pas(10, mc -> verifie(!fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive(), "toujours pas de course"), "vérif");
        pas(1, mc -> touche(mc, cle(mc.options.keyUp), false), "Avancer relâchée");
        pas(10, TouchesDebug::depart, "départ de la piste");

        // Touche dédiée (X, bascule) : un appui lance la course sans le sprint, un autre l'arrête
        pas(6, mc -> touche(mc, cle(mc.options.keyUp), true), "Avancer maintenue");
        pas(4, mc -> touche(mc, cle(HudKeybinds.COURSE_CHAKRA), true), "X");
        pas(1, mc -> touche(mc, cle(HudKeybinds.COURSE_CHAKRA), false), "");
        pas(3, mc -> verifie(fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive(), "X : course de chakra"), "vérif");
        pas(1, mc -> deCote(mc, true), "regard libre (Alt), vue de côté");
        pas(10, mc -> shot(mc, "touches_06b_course_touche_x"), "capture");
        pas(1, mc -> deCote(mc, false), "fin du regard libre");
        pas(6, mc -> verifie(fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive(), "X relâché : la course continue"), "vérif");
        pas(1, mc -> touche(mc, cle(HudKeybinds.COURSE_CHAKRA), true), "X (2e appui)");
        pas(1, mc -> touche(mc, cle(HudKeybinds.COURSE_CHAKRA), false), "");
        pas(4, mc -> verifie(!fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive(), "2e appui sur X : arrêt"), "vérif");
        pas(1, mc -> touche(mc, cle(mc.options.keyUp), false), "Avancer relâchée");
        pas(10, TouchesDebug::depart, "départ de la piste");

        // P puis G : galerie depuis le mode photo
        pas(10, mc -> touche(mc, cle(HudKeybinds.PHOTO), true), "P");
        pas(1, mc -> touche(mc, cle(HudKeybinds.PHOTO), false), "");
        pas(14, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.ui.PhotoModeScreen, "P ouvre le mode photo : " + nom(mc)), "vérif");
        pas(1, mc -> shot(mc, "touches_07_mode_photo_bouton_galerie"), "capture");
        pas(2, mc -> touche(mc, GLFW.GLFW_KEY_G, true), "G dans le mode photo");
        pas(1, mc -> touche(mc, GLFW.GLFW_KEY_G, false), "");
        pas(12, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.ui.GalleryScreen, "G ouvre la galerie : " + nom(mc)), "vérif");
        pas(1, mc -> shot(mc, "touches_08_galerie_depuis_photo"), "capture");
        pas(2, mc -> echap(mc), "Échap (galerie)");
        pas(8, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.ui.PhotoModeScreen, "fermer la galerie ramène au mode photo : " + nom(mc)), "vérif");
        pas(2, mc -> echap(mc), "Échap (mode photo)");
        pas(6, mc -> verifie(ecran(mc) == null, "Échap quitte le mode photo : " + nom(mc)), "vérif");

        // Échap → INTERFACE (éditeur de HUD, viseur) et Personnage
        pas(4, mc -> echap(mc), "Échap");
        pas(22, mc -> fr.reborn.hud.menu.esc.EscTokonoma.survolBanc = fr.reborn.hud.menu.esc.EscTokonoma.BANC_INTERFACE, "survol INTERFACE");
        pas(12, mc -> shot(mc, "touches_09_echap_interface"), "capture");
        pas(1, mc -> fr.reborn.hud.menu.esc.EscTokonoma.survolBanc = fr.reborn.hud.menu.esc.EscTokonoma.BANC_PERSONNAGE, "survol Personnage");
        pas(12, mc -> shot(mc, "touches_10_echap_personnage"), "capture");
        pas(1, mc -> fr.reborn.hud.menu.esc.EscTokonoma.survolBanc = -1, "");
        pas(2, mc -> fr.reborn.hud.menu.esc.EscDebug.clic(mc, 1), "clic INTERFACE");
        pas(8, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.ui.HudEditScreen, "INTERFACE ouvre l'éditeur de HUD : " + nom(mc)), "vérif");
        pas(4, mc -> shot(mc, "touches_11_editeur_hud"), "capture");
        pas(1, TouchesDebug::clicViseur, "clic « Personnaliser le viseur »");
        pas(8, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.crosshair.CrosshairScreen, "le bouton ouvre le viseur : " + nom(mc)), "vérif");
        pas(4, mc -> shot(mc, "touches_12_viseur_depuis_editeur"), "capture");
        pas(1, mc -> echap(mc), "Échap (viseur)");
        pas(4, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.ui.HudEditScreen, "retour à l'éditeur : " + nom(mc)), "vérif");
        pas(1, mc -> echap(mc), "Échap (éditeur)");
        pas(6, mc -> verifie(ecran(mc) instanceof net.minecraft.client.gui.screens.PauseScreen, "retour à l'Échap : " + nom(mc)), "vérif");
        pas(4, mc -> fr.reborn.hud.menu.esc.EscDebug.clicUnites(mc, 107, 208), "clic carte Personnage");
        pas(30, mc -> verifie(ecran(mc) != null && !(ecran(mc) instanceof net.minecraft.client.gui.screens.PauseScreen),
            "la carte Personnage ouvre la sélection : " + nom(mc)), "vérif");
        pas(1, mc -> shot(mc, "touches_13_personnage"), "capture");
        pas(2, mc -> mc.setScreenAndShow(null), "fermer");

        // N : menu OST ; « . » : animations
        pas(6, mc -> touche(mc, GLFW.GLFW_KEY_N, true), "N");
        pas(1, mc -> touche(mc, GLFW.GLFW_KEY_N, false), "");
        pas(12, mc -> verifie(KeyMapping.get("key.reborn-ost.open_menu") == null
            || nom(mc).contains("Ost"), "N ouvre le menu OST : " + nom(mc)
            + (KeyMapping.get("key.reborn-ost.open_menu") == null ? " (mod-ost absent)" : "")), "vérif");
        pas(1, mc -> shot(mc, "touches_14_n_ost"), "capture");
        pas(2, mc -> mc.setScreenAndShow(null), "fermer");
        pas(4, mc -> touche(mc, cle(HudKeybinds.ANIMATIONS), true), "« . »");
        pas(1, mc -> touche(mc, cle(HudKeybinds.ANIMATIONS), false), "");
        pas(10, mc -> verifie(ecran(mc) instanceof fr.reborn.hud.animation.AnimationMenuScreen, "« . » ouvre les animations : " + nom(mc)), "vérif");
        pas(1, mc -> shot(mc, "touches_15_animations_emotes"), "capture");
        pas(1, mc -> { if (ecran(mc) instanceof fr.reborn.hud.animation.AnimationMenuScreen a) a.demarches(); }, "onglet Démarches");
        pas(14, mc -> shot(mc, "touches_16_animations_demarches"), "capture");
        pas(1, mc -> touche(mc, cle(HudKeybinds.ANIMATIONS), true), "« . » dans le menu");
        pas(1, mc -> touche(mc, cle(HudKeybinds.ANIMATIONS), false), "");
        pas(4, mc -> verifie(ecran(mc) == null, "« . » referme le menu : " + nom(mc)), "vérif");

        // Réglages → Contrôles, Caméra
        pas(4, mc -> mc.setScreenAndShow(new fr.reborn.hud.menu.screens.ConfigShellScreen(null, 2)), "Réglages → Contrôles");
        pas(14, mc -> shot(mc, "touches_17_reglages_controles"), "capture");
        pas(1, mc -> defiler(mc, 9), "défiler");
        pas(14, mc -> shot(mc, "touches_18_reglages_controles_suite"), "capture");
        pas(1, mc -> defiler(mc, 40), "défiler");
        pas(14, mc -> shot(mc, "touches_19_reglages_controles_fin"), "capture");
        pas(1, mc -> mc.setScreenAndShow(new fr.reborn.hud.menu.screens.ConfigShellScreen(null, 3)), "Réglages → Caméra");
        pas(14, mc -> shot(mc, "touches_20_reglages_camera"), "capture");
        pas(2, mc -> mc.setScreenAndShow(null), "fermer");

        pas(4, TouchesDebug::rendre, "remise en état");
        pas(10, mc -> {
            ecrire(mc);
            System.out.println("[touches] FIN");
            mc.stop();
        }, "fin");
    }

    // ------------------------------------------------------------------ étapes

    private static void preparer(Minecraft mc) {
        if (mc.gameMode != null) modeAvant = mc.gameMode.getPlayerMode().getName();
        echelleAvant = mc.options.guiScale().get();
        mc.options.guiScale().set(2);
        mc.resizeGui();
        fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
        fr.reborn.hud.allure.Allure.INSTANCE.banc(false);           // allures simulées (pas de plugin en solo)
        fr.reborn.hud.runtime.VitalsFeed.update(82, 100, 64, 100);
        RebornCamera.INSTANCE.setVanilla(false, mc);
        var c = mc.player.connection;
        c.sendCommand("gamerule sendCommandFeedback false");
        c.sendCommand("gamerule send_command_feedback false");
        chute(c, false);
        // piste dans le ciel (rendue à la fin) : un fond dégagé, de quoi trotter, sprinter et courir
        x0 = mc.player.getX(); y0 = mc.player.getY(); z0 = mc.player.getZ();
        X = (int) Math.floor(x0); Y = (int) Math.floor(y0) + 40; Z = (int) Math.floor(z0);
        c.sendCommand("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 4) + " " + (Y - 1) + " " + (Z + 90)
            + " minecraft:polished_andesite");
        c.sendCommand("fill " + (X - 4) + " " + Y + " " + (Z - 4) + " " + (X + 4) + " " + (Y + 4) + " " + (Z + 90)
            + " minecraft:air");
        c.sendCommand("gamemode survival");
        c.sendCommand("time set 6000");
        c.sendCommand("weather clear");
        c.sendCommand("effect give @s minecraft:resistance 600 4 true");
        c.sendCommand("effect give @s minecraft:saturation 600 1 true");
        mc.gui.hud.getChat().clearMessages(false);
    }

    private static void depart(Minecraft mc) {
        mc.player.connection.sendCommand("tp @s " + X + ".5 " + Y + " " + Z + ".5 0 12");
        RebornCamera.INSTANCE.initOrientation(0f, 12f);
    }

    /** Regard libre (vraie touche Alt) et caméra tournée d'un quart de tour : on voit le personnage de côté. */
    private static void deCote(Minecraft mc, boolean on) {
        if (on) {
            touche(mc, cle(HudKeybinds.FREELOOK), true);
            RebornCamera.INSTANCE.rotateCamera(-560, 0);
        } else {
            RebornCamera.INSTANCE.rotateCamera(560, 0);
            touche(mc, cle(HudKeybinds.FREELOOK), false);
        }
    }

    private static void chute(net.minecraft.client.multiplayer.ClientPacketListener c, boolean on) {
        c.sendCommand("gamerule fallDamage " + on);
        c.sendCommand("gamerule fall_damage " + on);
    }

    /**
     * Table de correspondance de {@link TouchesMigration#cible} sur des claviers simulés (QWERTY, AZERTY, QWERTZ), puis
     * ce que le vrai clavier du poste affiche pour les touches Reborn.
     */
    private static void dispositions(Minecraft mc) {
        java.util.function.IntFunction<String> qwerty = TouchesMigration::nomUs;
        Map<Integer, String> az = new java.util.HashMap<>();
        az.put(GLFW.GLFW_KEY_Q, "a"); az.put(GLFW.GLFW_KEY_A, "q"); az.put(GLFW.GLFW_KEY_W, "z"); az.put(GLFW.GLFW_KEY_Z, "w");
        az.put(GLFW.GLFW_KEY_SEMICOLON, "m"); az.put(GLFW.GLFW_KEY_M, ","); az.put(GLFW.GLFW_KEY_COMMA, ";");
        az.put(GLFW.GLFW_KEY_PERIOD, ":"); az.put(GLFW.GLFW_KEY_SLASH, "!");
        java.util.function.IntFunction<String> azerty = k -> az.containsKey(k) ? az.get(k) : TouchesMigration.nomUs(k);
        java.util.function.IntFunction<String> qwertz = k -> k == GLFW.GLFW_KEY_Y ? "z" : k == GLFW.GLFW_KEY_Z ? "y"
            : TouchesMigration.nomUs(k);
        int[] defauts = {GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_B,
            GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_ENTER,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_F1};
        boolean okQwerty = true;
        for (int d : defauts) okQwerty &= TouchesMigration.cible(d, qwerty) == null;
        verifie(okQwerty, "QWERTY simulé : aucune touche ne bouge");
        verifie(eq(TouchesMigration.cible(GLFW.GLFW_KEY_M, azerty), GLFW.GLFW_KEY_SEMICOLON), "AZERTY simulé : Carte M → touche US « ; » (affiche m)");
        verifie(eq(TouchesMigration.cible(GLFW.GLFW_KEY_Z, azerty), GLFW.GLFW_KEY_W), "AZERTY simulé : Dégainer Z → touche US W (affiche z ; prise par Avancer, donc gardée)");
        verifie(eq(TouchesMigration.cible(GLFW.GLFW_KEY_PERIOD, azerty), GLFW.GLFW_KEY_COMMA), "AZERTY simulé : Animations . → touche US « , » (gravée « ; . »)");
        boolean autres = true;
        for (int d : defauts) if (d != GLFW.GLFW_KEY_M && d != GLFW.GLFW_KEY_Z && d != GLFW.GLFW_KEY_PERIOD) autres &= TouchesMigration.cible(d, azerty) == null;
        verifie(autres, "AZERTY simulé : K R F B P G C N Entrée Alt F1 ne bougent pas");
        verifie(eq(TouchesMigration.cible(GLFW.GLFW_KEY_Z, qwertz), GLFW.GLFW_KEY_Y), "QWERTZ simulé : Dégainer Z → touche US Y (affiche z)");
        verifie(TouchesMigration.cible(GLFW.GLFW_KEY_M, qwertz) == null && TouchesMigration.cible(GLFW.GLFW_KEY_PERIOD, qwertz) == null,
            "QWERTZ simulé : Carte et Animations ne bougent pas");
        StringBuilder vrai = new StringBuilder("clavier du poste :");
        for (KeyMapping km : HudKeybinds.REBORN_KEYS) vrai.append(' ').append(HudKeybinds.nom(km, "?"));
        LOG.append(vrai).append(System.lineSeparator());
        verifie("M".equals(HudKeybinds.nom(HudKeybinds.MAP, "?")) || "M".equals(HudKeybinds.nom(GLFW.GLFW_KEY_M)),
            "la carte affiche « M » sur le clavier du poste (" + HudKeybinds.nom(HudKeybinds.MAP, "?") + ")");
    }

    private static boolean eq(Integer a, int b) { return a != null && a == b; }

    private static void rendre(Minecraft mc) {
        fr.reborn.hud.radio.RadioClient.clear();
        fr.reborn.hud.runtime.RebornSession.debugForceRp = false;
        if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
        var c = mc.player.connection;
        c.sendCommand("tp @s " + x0 + " " + y0 + " " + z0);
        c.sendCommand("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 4) + " " + (Y - 1) + " " + (Z + 90)
            + " minecraft:air");
        chute(c, true);
        c.sendCommand("gamemode " + modeAvant);
        c.sendCommand("effect clear @s");
        carte(mc, "carte en fin de banc");
    }

    private static String etatRadio(Minecraft mc, String ore) {
        String me = mc.player.getUUID().toString();
        return "{\"t\":\"etat\",\"ore\":\"" + ore + "\",\"num\":12,\"esc\":{\"id\":\"e1\",\"nom\":\"Kagerō\",\"n\":3,\"max\":8,"
            + "\"createur\":true,\"membres\":["
            + "{\"id\":\"" + me + "\",\"perso\":\"" + me + "\",\"nom\":\"Aki Uchiha\",\"court\":\"Aki U.\",\"etat\":\"en_ligne\",\"ore\":\"ok\",\"createur\":true,\"moi\":true},"
            + "{\"id\":\"00000000-0000-4000-8000-00000000b002\",\"perso\":\"00000000-0000-4000-8000-00000000b002\",\"nom\":\"Sakura Haruno\",\"court\":\"Sakura H.\",\"etat\":\"en_ligne\",\"ore\":\"ok\",\"createur\":false,\"moi\":false},"
            + "{\"id\":\"00000000-0000-4000-8000-00000000b003\",\"perso\":\"00000000-0000-4000-8000-00000000b003\",\"nom\":\"Ren Hyūga\",\"court\":\"Ren H.\",\"etat\":\"hors_ligne\",\"ore\":\"\",\"createur\":false,\"moi\":false}"
            + "]}}";
    }

    private static void clicViseur(Minecraft mc) {
        if (!(ecran(mc) instanceof fr.reborn.hud.ui.HudEditScreen h)) { verifie(false, "pas d'éditeur pour cliquer"); return; }
        int[] c = h.debugCentreViseur();
        h.mouseClicked(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(0, 0)), false);
        Screen s = ecran(mc);
        if (s != null) s.mouseReleased(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(0, 0)));
    }

    private static void defiler(Minecraft mc, int crans) {
        Screen s = ecran(mc);
        if (s != null) s.mouseScrolled(s.width / 2.0, s.height / 2.0, 0, -crans);
    }

    // ------------------------------------------------------------------ clavier

    private static int cle(KeyMapping km) {
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
        return k.getType() == InputConstants.Type.KEYSYM ? k.getValue() : -1;
    }

    /** Une touche par le vrai chemin du clavier (écran ouvert ou non, toutes les liaisons, mods à l'écoute). */
    private static void touche(Minecraft mc, int glfw, boolean appui) {
        if (glfw < 0) { verifie(false, "touche non liée au clavier"); return; }
        try {
            if (keyPress == null) {
                keyPress = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
                keyPress.setAccessible(true);
            }
            int scan = GLFW.glfwGetKeyScancode(glfw);
            keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), appui ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE,
                new KeyEvent(glfw, scan, 0));
        } catch (ReflectiveOperationException e) {
            verifie(false, "keyPress indisponible : " + e);
        }
    }

    private static void appuiSprint(Minecraft mc, boolean appui) { touche(mc, cle(mc.options.keySprint), appui); }

    private static void echap(Minecraft mc) {
        touche(mc, GLFW.GLFW_KEY_ESCAPE, true);
        touche(mc, GLFW.GLFW_KEY_ESCAPE, false);
    }

    // ------------------------------------------------------------------ carte réelle et conflits

    private static void carte(Minecraft mc, String titre) {
        LOG.append("\n== ").append(titre).append(" ==\n");
        Map<String, List<String>> parTouche = new LinkedHashMap<>();
        for (KeyMapping km : mc.options.keyMappings) {
            InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
            boolean debug = KeyMapping.Category.DEBUG.equals(km.getCategory());
            String n = km.getName();
            if (n.startsWith("key.reborn") || n.startsWith("key.emotecraft") || n.startsWith("key.plasmovoice")
                || n.equals("key.togglePerspective") || n.equals("key.sprint") || n.equals("key.quickActions")
                || n.equals("key.socialInteractions") || n.equals("key.inventory") || n.equals("key.advancements")) {
                LOG.append(String.format("  %-34s %-28s affiche « %s »%n", n, k.getName(), k.getDisplayName().getString()));
            }
            if (k.equals(InputConstants.UNKNOWN) || debug) continue;
            parTouche.computeIfAbsent(k.getName(), x -> new ArrayList<>()).add(n);
        }
        try {
            for (String l : fr.reborn.hud.voice.RebornVoiceAddon.raccourcis()) LOG.append("  ").append(l).append('\n');
        } catch (Throwable ignored) {
            LOG.append("  (Plasmo Voice absent)\n");
        }
        LOG.append("  conflits (même touche, hors F3) :\n");
        parTouche.forEach((k, l) -> { if (l.size() > 1) LOG.append("    ").append(k).append(" : ").append(l).append('\n'); });
    }

    // ------------------------------------------------------------------ mécanique

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            if (!nom.isEmpty()) System.out.println("[touches] " + nom);
            action.accept(mc);
        });
    }

    private static Screen ecran(Minecraft mc) { return mc.gui.screen(); }

    private static String nom(Minecraft mc) { return ecran(mc) == null ? "aucun écran" : ecran(mc).getClass().getSimpleName(); }

    private static void verifie(boolean ok, String quoi) {
        String l = (ok ? "OK " : "ECHEC ") + quoi;
        System.out.println("[touches] " + l);
        LOG.append(l).append('\n');
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void ecrire(Minecraft mc) {
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("touches_banc.txt"), LOG.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[touches] journal non écrit : " + e);
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        // fenêtre du client de dev souvent à l'arrière-plan : sans « activité », Minecraft bride les images par seconde
        // et une capture prise juste après un changement d'état montrerait l'image d'avant
        mc.getFramerateLimitTracker().onInputReceived();
        if (etape > 0 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t == 0) prochaine = 80 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
