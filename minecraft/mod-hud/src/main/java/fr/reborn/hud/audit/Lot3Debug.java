package fr.reborn.hud.audit;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.RappelsTouches;
import fr.reborn.hud.keybind.TouchesConflits;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.screens.ConfigShellScreen.Onglet;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Banc de captures du lot 3 « Échap et touches » (Game Plan n° 17), <b>inactif en production</b> :
 * {@code REBORN_LOT3_DEBUG=1} (monde solo de dev). Paramètres (les 5 onglets, Graphismes › Affichage en Automatique
 * puis en Petite, Interface › Rappels de touches, Audio › Voix), Commandes › Monde (Concentration) et › Voix et
 * escouade (Couper le micro), un conflit (Concentration sur F), rappels en jeu (normal, concentration, combat) et
 * l'éditeur de HUD ouvert sur les rappels → {@code run/screenshots/lot3_*.png}. L'Échap a son propre banc
 * ({@code REBORN_ESC_DEBUG=captures}), les macrons celui des noms ({@code REBORN_NAMETAG_DEBUG=1}). Ferme le jeu à la
 * fin (« [lot3] FIN ») ; rien n'est sauvegardé (touches et préférences rendues telles quelles).
 */
public final class Lot3Debug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine;
    private static InputConstants.Key concentrationAvant;
    private static boolean petitsAvant;

    private Lot3Debug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_LOT3_DEBUG"))) return;

        pas(5, mc -> {
            RebornSession.debugForceRp = true;
            mc.player.connection.sendCommand("time set 6000");
            mc.player.connection.sendCommand("weather clear");
            petitsAvant = RebornPrefs.INSTANCE.menusPetits;
            // la ligne « Concentration » n'apparaît qu'une fois la concentration branchée (lot 4) : le banc la montre
            RappelsTouches.concentrationBranchee = true;
        }, "préparation");

        // ── rappels en jeu ──
        pas(40, mc -> mc.setScreenAndShow(null), "jeu");
        pas(10, mc -> shot(mc, "lot3_rappels_01_normal"), "capture");
        pas(1, mc -> RappelsTouches.debugMode = RappelsTouches.Mode.CONCENTRATION, "mode concentration");
        pas(6, mc -> shot(mc, "lot3_rappels_02_concentration"), "capture");
        pas(1, mc -> RappelsTouches.debugMode = RappelsTouches.Mode.COMBAT, "mode combat");
        pas(6, mc -> shot(mc, "lot3_rappels_03_combat"), "capture");
        pas(1, mc -> RappelsTouches.debugMode = null, "mode normal");
        // plafond (3 lignes) : trois actions et la portée en plus de Concentration ; Concentration doit rester
        pas(1, mc -> {
            RappelsTouches.DEBUG.add(RappelsTouches.Rappel.ANIMATION);
            RappelsTouches.DEBUG.add(RappelsTouches.Rappel.COURSE);
            RappelsTouches.DEBUG.add(RappelsTouches.Rappel.RADIO);
            RappelsTouches.DEBUG.add(RappelsTouches.Rappel.PORTEE);
        }, "plafond");
        pas(6, mc -> {
            System.out.println("[lot3] rappels " + RappelsTouches.bilan());
            shot(mc, "lot3_rappels_04_plafond");
        }, "capture");
        pas(1, mc -> RappelsTouches.DEBUG.clear(), "plafond rendu");

        // ── éditeur de HUD ouvert sur les rappels (panneau replié à gauche) ──
        pas(4, RappelsTouches::ouvrirEditeur, "éditeur sur RAPPELS");
        pas(15, mc -> shot(mc, "lot3_editeur_rappels"), "capture");
        pas(2, mc -> mc.setScreenAndShow(null), "fermer");

        // ── Paramètres ──
        page(Onglet.COMMANDES, 2, "lot3_param_01_commandes_monde");
        page(Onglet.COMMANDES, 3, "lot3_param_02_commandes_voix");
        page(Onglet.INTERFACE, 1, "lot3_param_03_interface_rappels");
        page(Onglet.INTERFACE, 0, "lot3_param_04_interface_hud");
        page(Onglet.AUDIO, 1, "lot3_param_05_audio_voix");
        page(Onglet.AUDIO, 0, "lot3_param_06_audio_general");
        page(Onglet.GRAPHISMES, 0, "lot3_param_07_graphismes_affichage");
        page(Onglet.GRAPHISMES, 1, "lot3_param_08_graphismes_rendu");
        page(Onglet.COMPTE, 0, "lot3_param_09_compte");
        page(Onglet.COMPTE, 4, "lot3_param_10_compte_avance");

        // retour du 05/11 : « Options de Minecraft › » au pied, l'écran d'origine sans redirection, retour aux Paramètres
        pas(3, mc -> {
            ConfigShellScreen.debugSurvolMinecraft = true;
            mc.setScreenAndShow(new ConfigShellScreen(null, Onglet.GRAPHISMES, 0));
        }, "Graphismes, lien survolé");
        pas(14, mc -> shot(mc, "lot3_r0197_01_lien_options_minecraft_survol"), "capture");
        pas(1, mc -> ConfigShellScreen.debugSurvolMinecraft = false, "survol rendu");
        pas(6, mc -> shot(mc, "lot3_r0197_02_lien_options_minecraft"), "capture");
        pas(2, mc -> {
            if (mc.gui.screen() instanceof ConfigShellScreen c) {
                double[] p = c.centreOptionsMinecraft();
                c.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(p[0], p[1],
                    new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            }
        }, "clic Options de Minecraft");
        pas(10, mc -> {
            var s = mc.gui.screen();
            System.out.println("[lot3] " + (s != null && s.getClass() == net.minecraft.client.gui.screens.options.OptionsScreen.class
                ? "OK" : "ECHEC") + " écran d'options de Minecraft ouvert sans redirection : " + nom(s));
            shot(mc, "lot3_r0197_03_options_minecraft_vanilla");
        }, "vérif + capture");
        pas(4, mc -> {
            var s = mc.gui.screen();
            if (s != null) s.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        }, "Échap (Terminé)");
        pas(10, mc -> {
            var s = mc.gui.screen();
            System.out.println("[lot3] " + (s instanceof ConfigShellScreen ? "OK" : "ECHEC")
                + " retour aux Paramètres : " + nom(s));
            shot(mc, "lot3_r0197_04_retour_parametres");
        }, "vérif + capture");
        // sous-écran Vidéo ouvert depuis l'écran d'origine : pas redirigé non plus
        pas(2, mc -> {
            if (mc.gui.screen() instanceof ConfigShellScreen c) c.ouvrirOptionsMinecraft();
        }, "Options de Minecraft");
        pas(4, mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(mc.gui.screen(), mc, mc.options)),
            "Vidéo depuis l'écran d'origine");
        pas(10, mc -> {
            var s = mc.gui.screen();
            System.out.println("[lot3] " + (s instanceof net.minecraft.client.gui.screens.options.VideoSettingsScreen ? "OK" : "ECHEC")
                + " Vidéo de Minecraft ouvert depuis l'écran d'origine : " + nom(s));
            shot(mc, "lot3_r0197_05_video_minecraft");
        }, "vérif + capture");
        pas(2, mc -> mc.setScreenAndShow(null), "fermer");
        pas(2, mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(null, mc.options, true)),
            "écran d'options ouvert ailleurs");
        pas(4, mc -> {
            var s = mc.gui.screen();
            System.out.println("[lot3] " + (s instanceof ConfigShellScreen ? "OK" : "ECHEC")
                + " hors du lien, la redirection reste : " + nom(s));
        }, "vérif redirection");

        // conflit : Concentration sur la touche de Contextuel (F)
        pas(2, mc -> {
            concentrationAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.CONCENTRATION);
            HudKeybinds.CONCENTRATION.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F));
            KeyMapping.resetMapping();
            TouchesConflits.invalider();
            mc.setScreenAndShow(new ConfigShellScreen(null, Onglet.COMMANDES, 2));
        }, "Concentration sur F");
        pas(14, mc -> shot(mc, "lot3_param_11_commandes_conflit"), "capture");
        pas(1, mc -> {
            HudKeybinds.CONCENTRATION.setKey(concentrationAvant);
            KeyMapping.resetMapping();
            TouchesConflits.invalider();
        }, "Concentration rendue");

        // Taille des menus Reborn : Petite
        pas(2, mc -> {
            RebornPrefs.INSTANCE.menusPetits = true;
            mc.setScreenAndShow(new ConfigShellScreen(null, Onglet.GRAPHISMES, 0));
        }, "menus petits");
        pas(4, Minecraft::resizeGui, "redimension");
        pas(14, mc -> shot(mc, "lot3_param_12_graphismes_petite"), "capture");
        pas(1, mc -> {
            RebornPrefs.INSTANCE.menusPetits = petitsAvant;
            mc.setScreenAndShow(null);
        }, "menus rendus");
        pas(4, Minecraft::resizeGui, "redimension");

        pas(20, mc -> {
            RebornSession.debugForceRp = false;
            RappelsTouches.concentrationBranchee = false;
            System.out.println("[lot3] FIN");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot3Debug::tick);
    }

    private static String nom(net.minecraft.client.gui.screens.Screen s) {
        return s == null ? "aucun" : s.getClass().getSimpleName();
    }

    private static void page(Onglet o, int sous, String nom) {
        pas(3, mc -> mc.setScreenAndShow(new ConfigShellScreen(null, o, sous)), o + "." + sous);
        pas(14, mc -> shot(mc, nom), "capture " + nom);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot3] " + nom);
            action.accept(mc);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        // fenêtre de dev souvent à l'arrière-plan : sans « activité », Minecraft bride les images
        mc.getFramerateLimitTracker().onInputReceived();
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
