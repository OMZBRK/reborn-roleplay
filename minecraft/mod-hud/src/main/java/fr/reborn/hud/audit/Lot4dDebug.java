package fr.reborn.hud.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.concentration.ConcentrationCanal;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.concentration.Plaques;
import fr.reborn.hud.concentration.Traces;
import fr.reborn.hud.interaction.InteractionItem;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.parchemin.ParcheminClient;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Banc de la concentration <b>maintenue</b> et de l'effet K+ (retours des 08 et 09/11), <b>inactif en production</b> :
 * {@code REBORN_LOT4_DEBUG=d} (monde solo de dev, jamais de serveur). Seule la lecture GLFW est simulée ({@link ClavierBanc}) ;
 * le serveur simulé applique la vraie limite de {@code PlaqueRegles.Limiteur} (quatre {@code on} par seconde glissante).
 * <ol>
 *   <li>Le maintien, horloge réelle : entrée à l'appui, sortie au relâchement, marche, accroupi, clic droit ; pas de nouvelle
 *       entrée touche tenue après un coup, un Malaxage, Échap, un écran, un refus du serveur ; menu qui reste après le
 *       relâchement, fin du mode à sa fermeture ; fenêtre quittée ; tchat ; répétition de la touche tenue dans le « /me » du
 *       menu et lettre suivie d'une fermeture sans tick entre les deux (vrai chemin du clavier) ; appuis rapides (10 fois, trois rythmes) ; bouton de
 *       souris ; touche non liée ; ancienne touche.</li>
 *   <li>Captures, horloges figées, 1920×1080 puis 1366×768 : jour, nuit, traces sur l'herbe, le sable, la pierre et la neige
 *       (automatique, couleur au choix), plaque et son halo, joueur survolé, menu ouvert après le relâchement.</li>
 * </ol>
 * → {@code run/screenshots/lot4d_*.png}, bilans « [lot4d] OK / ECHEC » au journal ; le jeu se ferme (« [lot4d] FIN »).
 */
public final class Lot4dDebug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();
    private static final Vector3f V = new Vector3f();
    /** Ticks avant de capturer une nouvelle couleur de traces : fenêtre en arrière-plan, la dernière image rendue date parfois de plus de 300 ms. */
    private static final int COULEUR_POSEE = 20;
    private static final String TEXTE = "Ici repose la <or>première garde</> du village. Celui qui lit ces lignes passe sous sa protection.";

    private record Pas(int id, double x, double y, double z, int ang, int fl, int age, int vie) { }

    private static final List<Pas> SCENE = new ArrayList<>();
    private static int prochainId = 1;

    private static int t = -1, etape, prochaine, echecs;
    private static int ox, oy, oz;
    private static long heure = -1;
    private static boolean pluie, volant;
    private static String mode = "survival", difficulte = "normal";
    /** Horloge figée des captures. */
    private static long h;
    /** Serveur simulé : {@code on} reçus, {@code off} reçus, {@code on} refusés par la limite, et les instants de la fenêtre. */
    private static int ons, offs, limites;
    private static final ArrayDeque<Long> FENETRE = new ArrayDeque<>();
    /** Compteurs relevés au début d'une série. */
    private static int on0, off0, lim0, sonE0, sonS0;
    private static long debutSerie;
    private static float dernierNiveau;
    private static boolean continu;
    private static boolean actionFaite;
    private static InputConstants.Key concentrationAvant, interactionAvant;
    private static boolean autoAvant;
    private static int couleurAvant;
    private static RebornCamera.Mode cameraAvant;

    private Lot4dDebug() {}

    public static void init() {
        if (!"d".equalsIgnoreCase(String.valueOf(System.getenv("REBORN_LOT4_DEBUG")))) return;

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            volant = true;
            vol(mc, true);
            var pr = RebornPrefs.INSTANCE;
            pr.ensureLoaded();
            autoAvant = pr.tracesAuto;
            couleurAvant = pr.tracesCouleur;
            pr.tracesAuto = true;
            cameraAvant = RebornCamera.INSTANCE.mode();
            concentrationAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.CONCENTRATION);
            interactionAvant = KeyMappingHelper.getBoundKeyOf(HudKeybinds.INTERACTION);
            ClavierBanc.poser();
            lier(HudKeybinds.CONCENTRATION, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y));
            lier(HudKeybinds.INTERACTION, InputConstants.UNKNOWN);
            ConcentrationCanal.debugServeur = Lot4dDebug::serveur;
        }, "préparation");
        pas(10, Lot4dDebug::batir, "scène");
        pas(40, Lot4dDebug::mannequin, "mannequin");
        pas(20, mc -> { }, "scène posée");

        maintien();
        relance();
        menu();
        fenetre();
        tchat();
        rapides();
        liaisons();
        captures("1080", true);
        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(25, mc -> { }, "fenêtre posée");
        captures("1366", false);

        pas(10, mc -> {
            ModeConcentration.reinitialiser();
            ModeConcentration.horloge = Util::getMillis;
            Plaques.horloge = Util::getMillis;
            Recit.horloge = Util::getMillis;
            Traces.horloge = Util::getMillis;
            ConcentrationCanal.debugServeur = null;
            ClavierBanc.retirer();
            InteractionMode.INSTANCE.debugClose();
            lier(HudKeybinds.CONCENTRATION, concentrationAvant);
            lier(HudKeybinds.INTERACTION, interactionAvant);
            RebornCamera.INSTANCE.setMode(cameraAvant);
            RebornPrefs.INSTANCE.tracesAuto = autoAvant;
            RebornPrefs.INSTANCE.tracesCouleur = couleurAvant;
            var c = mc.player.connection;
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 14, oy, oz - 8, ox + 14, oy + 8, oz + 28));
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> { volant = false; vol(mc, false); mc.player.connection.sendCommand("gamemode " + mode); }, "mode de jeu");
        pas(10, mc -> {
            System.out.println("[lot4d] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot4dDebug::tick);
    }

    /* ================================================================ maintien */

    private static void maintien() {
        pas(1, mc -> {
            epaule(mc, 0, 0, 7f);
            verifie(!ModeConcentration.actif(), "au repos : pas de mode");
            debut();
            ClavierBanc.tenir(true);
        }, "appui");
        pas(2, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.isActive(), "appui : entrée");
            verifie(ons - on0 == 1, "appui : un seul « on » au serveur (" + (ons - on0) + ")");
            verifie(ModeConcentration.sonsEntree() - sonE0 == 1, "appui : un souffle d'entrée");
        }, "vérif entrée");
        pas(30, mc -> {
            verifie(ModeConcentration.actif(), "1,5 s tenue : toujours concentré");
            verifie(ModeConcentration.niveau() >= 0.999f && ModeConcentration.filtreAffiche(), "effet plein, à l'écran");
            verifie(ons - on0 == 1, "tenue : aucun autre « on » (" + (ons - on0) + ")");
            mc.options.keyShift.setDown(true);
            mc.options.keyUp.setDown(true);
            posX = mc.player.getX();
            posZ = mc.player.getZ();
        }, "accroupi et marche");
        pas(10, mc -> {
            verifie(ModeConcentration.actif(), "accroupi et en marche : toujours concentré");
            double d = Math.hypot(mc.player.getX() - posX, mc.player.getZ() - posZ);
            verifie(d > 0.05, String.format(Locale.ROOT, "le joueur a marché (%.2f bloc)", d));
            mc.options.keyShift.setDown(false);
            mc.options.keyUp.setDown(false);
            ClavierBanc.I.bouton(GLFW.GLFW_MOUSE_BUTTON_RIGHT, true);
            ModeConcentration.clicDroit(true);
        }, "clic droit tenu");
        pas(5, mc -> {
            verifie(ModeConcentration.rotation(), "clic droit tenu : la caméra tourne, le mode reste");
            ClavierBanc.I.bouton(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
            ModeConcentration.clicDroit(false);
        }, "clic droit relâché");
        pas(3, mc -> {
            verifie(!ModeConcentration.rotation() && ModeConcentration.actif(), "clic droit relâché : curseur revenu, toujours concentré");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(2, mc -> {
            verifie(!ModeConcentration.actif() && "TOUCHE".equals(ModeConcentration.derniereSortie()), "relâchement : sortie (" + ModeConcentration.derniereSortie() + ")");
            verifie(!InteractionMode.INSTANCE.isActive(), "relâchement : curseur fermé");
            verifie(offs - off0 == 1, "relâchement : un « off » au serveur (" + (offs - off0) + ")");
            verifie(ModeConcentration.sonsSortie() - sonS0 == 1, "relâchement : un souffle de sortie");
        }, "vérif sortie");
        pas(8, mc -> verifie(!ModeConcentration.filtreVoulu() && ModeConcentration.niveau() == 0f, "effet retombé"), "fondu fini");
    }

    /* ================================================================ pas de relance, touche tenue */

    private static void relance() {
        // un coup donné : sortie ; toujours tenue, plus rien, même une fois le refus de 5 s passé
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "avant le coup : concentré");
            CombatState.INSTANCE.onHit(mc.player.getId(), 2f, System.currentTimeMillis());
        }, "coup donné");
        pas(3, mc -> verifie(!ModeConcentration.actif() && String.valueOf(ModeConcentration.derniereSortie()).contains("coup"),
            "coup : sortie (" + ModeConcentration.derniereSortie() + ")"), "vérif coup");
        pas(110, mc -> {
            verifie(!ModeConcentration.actif() && ModeConcentration.attendRelache(), "5,5 s après, toujours tenue : pas de nouvelle entrée");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(3, mc -> {
            verifie(!ModeConcentration.attendRelache(), "relâchée : un nouvel appui est permis");
            ClavierBanc.tenir(true);
        }, "nouvel appui");
        pas(2, mc -> {
            verifie(ModeConcentration.actif(), "nouvel appui : entrée");
            ClavierBanc.tenir(false);
        }, "relâchement");

        // refus : ne se relance pas quand le blocage cesse ; sortie par un blocage : pareil
        pas(8, mc -> malaxage(), "Malaxage en cours");
        pas(2, mc -> { malaxage(); ClavierBanc.tenir(true); }, "appui pendant le Malaxage");
        pas(2, mc -> {
            malaxage();
            verifie(!ModeConcentration.actif() && "malaxage du chakra".equals(ModeConcentration.dernierRefus()), "Malaxage : entrée refusée (" + ModeConcentration.dernierRefus() + ")");
            stopMalaxage();
        }, "fin du Malaxage, touche tenue");
        pas(12, mc -> {
            verifie(!ModeConcentration.actif(), "refus : pas de nouvel essai touche tenue");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(3, mc -> ClavierBanc.tenir(true), "nouvel appui");
        pas(2, mc -> {
            verifie(ModeConcentration.actif(), "nouvel appui après le refus : entrée");
            malaxage();
        }, "le Malaxage démarre");
        pas(2, mc -> {
            verifie(!ModeConcentration.actif() && String.valueOf(ModeConcentration.derniereSortie()).contains("malaxage"), "Malaxage : sortie");
            stopMalaxage();
        }, "fin du Malaxage");
        pas(12, mc -> {
            verifie(!ModeConcentration.actif(), "sortie imposée : pas de nouvelle entrée touche tenue");
            ClavierBanc.tenir(false);
        }, "relâchement");

        // Échap, par le vrai chemin du clavier
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "avant Échap : concentré");
            touche(mc, GLFW.GLFW_KEY_ESCAPE, true);
            touche(mc, GLFW.GLFW_KEY_ESCAPE, false);
        }, "Échap");
        pas(6, mc -> {
            verifie(!ModeConcentration.actif() && "ECHAP".equals(ModeConcentration.derniereSortie()) && mc.gui.screen() == null, "Échap : sortie sans pause");
            ClavierBanc.tenir(false);
        }, "relâchement");

        // un écran : sortie ; refermé touche tenue, rien
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(3, mc -> mc.setScreenAndShow(new net.minecraft.client.gui.screens.options.OptionsScreen(null, mc.options, true)), "Paramètres");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && "ECRAN".equals(ModeConcentration.derniereSortie()), "écran : sortie (" + ModeConcentration.derniereSortie() + ")");
            mc.setScreenAndShow(null);
        }, "Paramètres fermés");
        pas(8, mc -> {
            verifie(!ModeConcentration.actif(), "écran refermé, touche tenue : pas de nouvelle entrée");
            ClavierBanc.tenir(false);
        }, "relâchement");

        // refus ou fin imposée du serveur
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(3, mc -> ConcentrationCanal.recevoir("{\"v\":1,\"t\":\"etat\",\"on\":false,\"raison\":\"Trop vite.\"}"), "refus du serveur");
        pas(8, mc -> {
            verifie(!ModeConcentration.actif() && String.valueOf(ModeConcentration.derniereSortie()).startsWith("SERVEUR"), "serveur : sortie (" + ModeConcentration.derniereSortie() + ")");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(8, mc -> { }, "repos");
    }

    /* ================================================================ le menu reste après le relâchement */

    private static void menu() {
        pas(1, mc -> ClavierBanc.tenir(true), "appui");
        pas(2, Lot4dDebug::surMannequin, "curseur sur le mannequin");
        pas(4, mc -> {
            verifie(ModeConcentration.survol() >= 0, "tenue : contour sur le joueur survolé");
            InteractionMode.INSTANCE.onClick();
            verifie(InteractionMode.INSTANCE.menuOuvert(), "clic : menu ouvert");
            ClavierBanc.tenir(false);
        }, "clic, puis relâchement");
        pas(20, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.menuOuvert(), "relâchée : le menu reste");
            verifie(ModeConcentration.niveau() >= 0.999f, "relâchée : l'effet reste");
            clicHorsDuMenu();
        }, "clic hors du menu");
        pas(2, mc -> {
            verifie(!InteractionMode.INSTANCE.isActive() && !ModeConcentration.actif() && "TOUCHE".equals(ModeConcentration.derniereSortie()),
                "menu refermé, touche relâchée : le mode finit (" + ModeConcentration.derniereSortie() + ")");
        }, "vérif");
        // retenue pendant le menu : fermé, le mode continue
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(2, Lot4dDebug::surMannequin, "curseur sur le mannequin");
        pas(4, mc -> { InteractionMode.INSTANCE.onClick(); ClavierBanc.tenir(false); }, "clic, relâchement");
        pas(4, mc -> ClavierBanc.tenir(true), "appui, menu ouvert");
        pas(3, mc -> {
            verifie(InteractionMode.INSTANCE.menuOuvert(), "menu ouvert, touche reprise : le menu reste");
            clicHorsDuMenu();
        }, "clic hors du menu");
        pas(3, mc -> {
            verifie(ModeConcentration.actif() && !InteractionMode.INSTANCE.menuOuvert(), "menu refermé, touche tenue : le mode continue");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(2, mc -> verifie(!ModeConcentration.actif(), "relâchement : sortie"), "vérif");
        // action choisie : touche tenue, le mode continue ; relâchée, il finit
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(3, mc -> ouvrirAction(), "menu à une action");
        pas(3, mc -> {
            InteractionMode.INSTANCE.onClick();
            verifie(actionFaite, "action choisie");
        }, "action, touche tenue");
        pas(3, mc -> {
            verifie(ModeConcentration.actif() && !InteractionMode.INSTANCE.menuOuvert(), "action, touche tenue : le mode continue");
            ouvrirAction();
            ClavierBanc.tenir(false);
        }, "menu à une action, relâchement");
        pas(5, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.menuOuvert(), "relâchée, menu ouvert : le mode reste");
            InteractionMode.INSTANCE.onClick();
        }, "action, touche relâchée");
        pas(2, mc -> verifie(!ModeConcentration.actif(), "action, touche relâchée : le mode finit"), "vérif");
        // Échap menu ouvert, touche relâchée : tout finit
        pas(8, mc -> ClavierBanc.tenir(true), "appui");
        pas(2, Lot4dDebug::surMannequin, "curseur sur le mannequin");
        pas(4, mc -> { InteractionMode.INSTANCE.onClick(); ClavierBanc.tenir(false); }, "clic, relâchement");
        pas(4, mc -> { touche(mc, GLFW.GLFW_KEY_ESCAPE, true); touche(mc, GLFW.GLFW_KEY_ESCAPE, false); }, "Échap");
        pas(3, mc -> verifie(!ModeConcentration.actif() && "ECHAP".equals(ModeConcentration.derniereSortie()) && mc.gui.screen() == null, "Échap menu ouvert : tout finit, pas de pause"), "vérif");
        pas(8, mc -> { }, "repos");
    }

    /** Le curseur loin du menu, puis le vrai clic : il le referme. */
    private static void clicHorsDuMenu() {
        InteractionMode.INSTANCE.onMouseMove(100000, 100000, 1);
        InteractionMode.INSTANCE.onClick();
    }

    private static void ouvrirAction() {
        actionFaite = false;
        InteractionMode.INSTANCE.debugOpen("Moi", List.of(InteractionItem.action("Test", () -> actionFaite = true)), 0);
    }

    /* ================================================================ fenêtre quittée */

    private static void fenetre() {
        pas(1, mc -> ClavierBanc.tenir(true), "appui");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "avant : concentré");
            ClavierBanc.I.focus(false);
        }, "fenêtre quittée, touche tenue");
        pas(2, mc -> {
            verifie(!ModeConcentration.actif() && "TOUCHE".equals(ModeConcentration.derniereSortie()), "fenêtre quittée = relâchée");
            ClavierBanc.I.focus(true);
        }, "retour dans la fenêtre, touche tenue");
        pas(8, mc -> {
            verifie(!ModeConcentration.actif(), "retour touche enfoncée : ce n'est pas un appui");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(3, mc -> ClavierBanc.tenir(true), "nouvel appui");
        pas(2, mc -> verifie(ModeConcentration.actif(), "nouvel appui : entrée"), "vérif");
        pas(1, Lot4dDebug::surMannequin, "curseur sur le mannequin");
        pas(4, mc -> { InteractionMode.INSTANCE.onClick(); ClavierBanc.I.focus(false); }, "menu ouvert, fenêtre quittée");
        pas(4, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.menuOuvert(), "menu ouvert, fenêtre quittée : le menu reste");
            ClavierBanc.tenir(false);
            ClavierBanc.I.focus(true);
            InteractionMode.INSTANCE.debugClose();
        }, "menu fermé");
        pas(3, mc -> verifie(!ModeConcentration.actif(), "fini"), "vérif");
        pas(8, mc -> { }, "repos");
    }

    /* ================================================================ tchat */

    private static void tchat() {
        // la lettre tapée dans le tchat n'ouvre rien, même si on garde le doigt dessus en le fermant
        pas(1, mc -> mc.setScreenAndShow(new ChatScreen("", false)), "tchat ouvert");
        pas(3, mc -> ClavierBanc.tenir(true), "lettre tenue dans le tchat");
        pas(4, mc -> {
            verifie(!ModeConcentration.actif(), "lettre dans le tchat : pas de mode");
            mc.setScreenAndShow(null);
        }, "tchat fermé, lettre tenue");
        pas(6, mc -> {
            verifie(!ModeConcentration.actif(), "tchat fermé, lettre encore tenue : pas de mode");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(4, mc -> ClavierBanc.tenir(true), "appui");
        // le tchat ouvert pendant le maintien : le mode reste ; le relâchement le finit, le tchat reste
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "avant le tchat : concentré");
            mc.setScreenAndShow(new ChatScreen("", false));
        }, "tchat ouvert, touche tenue");
        pas(6, mc -> {
            verifie(ModeConcentration.actif(), "tchat ouvert, touche tenue : le mode reste");
            ClavierBanc.tenir(false);
        }, "relâchement, tchat ouvert");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && mc.gui.screen() instanceof ChatScreen, "relâchement : le mode finit, le tchat reste ouvert");
            mc.setScreenAndShow(null);
        }, "tchat fermé");
        pas(8, mc -> verifie(!ModeConcentration.actif(), "tchat fermé : rien"), "vérif");

        // « Action RP (/me)… » du menu, Y tenue : le système répète Y dans le tchat qui vient de s'ouvrir
        pas(8, mc -> { touche(mc, GLFW.GLFW_KEY_Y, true); ClavierBanc.tenir(true); }, "Y pressée hors écran");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "Y tenue : concentré");
            meDuMenu();
        }, "menu Moi, Action RP (/me)…");
        pas(3, mc -> {
            verifie(mc.gui.screen() instanceof ChatScreen && ModeConcentration.actif(), "/me du menu, Y tenue : tchat ouvert, le mode reste");
            verifie("/me ".equals(saisie(mc)), "/me du menu : pré-rempli (« " + saisie(mc) + " »)");
            repetition(mc, GLFW.GLFW_KEY_Y, 'y', 8);
            repetition(mc, GLFW.GLFW_KEY_Y, 'Y', 2);
            verifie("/me ".equals(saisie(mc)), "/me du menu : répétition de Y écartée (« " + saisie(mc) + " »)");
            // une autre lettre prend la répétition et se tape, elle et sa répétition
            touche(mc, GLFW.GLFW_KEY_A, true);
            caractere(mc, 'a');
            repetition(mc, GLFW.GLFW_KEY_A, 'a', 2);
            touche(mc, GLFW.GLFW_KEY_A, false);
            verifie("/me aaa".equals(saisie(mc)), "/me du menu : une autre lettre se tape (« " + saisie(mc) + " »)");
            touche(mc, GLFW.GLFW_KEY_Y, false);
            ClavierBanc.tenir(false);
        }, "répétition, autre lettre, relâchement");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && mc.gui.screen() instanceof ChatScreen, "relâchement : le mode finit, le tchat reste");
            // relâchée puis pressée sous le tchat, c'est une vraie lettre
            touche(mc, GLFW.GLFW_KEY_Y, true);
            caractere(mc, 'y');
            repetition(mc, GLFW.GLFW_KEY_Y, 'y', 1);
            touche(mc, GLFW.GLFW_KEY_Y, false);
            verifie("/me aaayy".equals(saisie(mc)), "Y pressée sous le tchat : se tape, répétition comprise (« " + saisie(mc) + " »)");
            mc.setScreenAndShow(null);
        }, "vraie lettre");
        // la marche : Z (US W) pressée après Y, c'est elle que le système répète
        pas(8, mc -> { touche(mc, GLFW.GLFW_KEY_Y, true); ClavierBanc.tenir(true); }, "appui");
        pas(2, mc -> touche(mc, GLFW.GLFW_KEY_W, true), "marche");
        pas(2, mc -> meDuMenu(), "menu Moi, Action RP (/me)…, en marchant");
        pas(3, mc -> {
            repetition(mc, GLFW.GLFW_KEY_W, 'z', 6);
            verifie("/me ".equals(saisie(mc)), "/me en marchant : répétition de la marche écartée (« " + saisie(mc) + " »)");
            touche(mc, GLFW.GLFW_KEY_W, false);
            touche(mc, GLFW.GLFW_KEY_Y, false);
            ClavierBanc.tenir(false);
            mc.setScreenAndShow(null);
        }, "répétition de la marche");
        // Retour arrière tenu : sa répétition (un événement du clavier, pas un caractère) n'efface pas le pré-rempli
        pas(8, mc -> { touche(mc, GLFW.GLFW_KEY_Y, true); ClavierBanc.tenir(true); }, "appui");
        pas(2, mc -> touche(mc, GLFW.GLFW_KEY_BACKSPACE, true), "retour arrière tenu");
        pas(2, mc -> meDuMenu(), "menu Moi, Action RP (/me)…");
        pas(3, mc -> {
            for (int i = 0; i < 6; i++) evenement(mc, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_REPEAT);
            verifie("/me ".equals(saisie(mc)), "/me : répétition du retour arrière écartée (« " + saisie(mc) + " »)");
            touche(mc, GLFW.GLFW_KEY_BACKSPACE, false);
            touche(mc, GLFW.GLFW_KEY_Y, false);
            ClavierBanc.tenir(false);
            mc.setScreenAndShow(null);
        }, "répétition du retour arrière");
        // appui bref hors écran : rien ne reste armé pour le prochain tchat
        pas(8, mc -> { touche(mc, GLFW.GLFW_KEY_Y, true); touche(mc, GLFW.GLFW_KEY_Y, false); }, "appui bref");
        pas(1, mc -> mc.setScreenAndShow(new ChatScreen("", false)), "tchat ouvert");
        pas(3, mc -> {
            caractere(mc, 'y');
            verifie("y".equals(saisie(mc)), "appui bref avant le tchat : rien n'y reste écarté (« " + saisie(mc) + " »)");
            mc.setScreenAndShow(null);
        }, "lettre");

        // lettre puis fermeture de l'écran dans le même tick : l'appui lui-même est relevé
        fermetureEnRoulement("tchat", () -> new ChatScreen("", false));
        fermetureEnRoulement("autre écran", () -> new net.minecraft.client.gui.screens.options.OptionsScreen(null, Minecraft.getInstance().options, true));
    }

    /** Menu « Moi » et clic sur « Action RP (/me)… » : la vraie entrée, le vrai {@code openChat}. */
    private static void meDuMenu() {
        InteractionMode.INSTANCE.debugOpen("Moi", InteractionMenus.forSelf(), 4);
        InteractionMode.INSTANCE.onClick();
    }

    private static void fermetureEnRoulement(String nom, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> ecran) {
        pas(8, mc -> mc.setScreenAndShow(ecran.get()), nom + " ouvert");
        pas(3, mc -> {
            touche(mc, GLFW.GLFW_KEY_Y, true);
            ClavierBanc.tenir(true);
            mc.setScreenAndShow(null);
            verifie(ModeConcentration.attendRelache(), nom + " : appui sous l'écran relevé sans attendre un tick");
        }, nom + " : lettre puis fermeture, sans tick entre les deux");
        pas(8, mc -> {
            verifie(!ModeConcentration.actif(), nom + " : lettre encore tenue, écran fermé : pas de mode");
            touche(mc, GLFW.GLFW_KEY_Y, false);
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(3, mc -> verifie(!ModeConcentration.attendRelache(), nom + " : relâchée, un nouvel appui est permis"), "vérif");
    }

    /* ================================================================ appuis rapides */

    private static void rapides() {
        rythme(1, 1);
        rythme(2, 1);
        rythme(3, 3);
        // appui juste après une rafale, tenu : entre dès que l'intervalle est passé, et reste
        pas(1, mc -> ClavierBanc.tenir(true), "appui final tenu");
        pas(8, mc -> {
            verifie(ModeConcentration.actif(), "appui tenu après la rafale : entrée sous 400 ms");
            ClavierBanc.tenir(false);
        }, "relâchement");
        pas(8, mc -> verifie(!ModeConcentration.actif() && !ModeConcentration.filtreVoulu(), "rien de coincé"), "vérif");
        // le fondu suit sans saut : relâchée à mi-entrée, l'effet redescend d'où il était
        pas(10, mc -> { ClavierBanc.tenir(true); }, "appui");
        pas(2, mc -> ClavierBanc.tenir(false), "relâchement à mi-entrée");
        pas(1, mc -> {
            dernierNiveau = ModeConcentration.niveau();
            continu = true;
            verifie(!ModeConcentration.actif() && dernierNiveau > 0f && dernierNiveau < 1f, "à mi-entrée : niveau " + dernierNiveau);
        }, "sortie à mi-entrée");
        for (int i = 0; i < 6; i++) {
            pas(1, mc -> {
                float n = ModeConcentration.niveau();
                continu &= n <= dernierNiveau;
                dernierNiveau = n;
            }, "niveau");
        }
        pas(1, mc -> verifie(continu && dernierNiveau == 0f, "fondu de sortie sans saut, jusqu'à 0"), "vérif");
    }

    /** Dix appuis : tenue {@code tenu} ticks, relâchée {@code repos} ticks. */
    private static void rythme(int tenu, int repos) {
        String nom = "10 appuis " + tenu * 50 + " ms / " + repos * 50 + " ms";
        pas(8, mc -> debut(), nom);
        for (int i = 0; i < 10; i++) {
            pas(1, mc -> ClavierBanc.tenir(true), "appui");
            pas(tenu, mc -> ClavierBanc.tenir(false), "relâché");
            if (repos > 1) pas(repos - 1, mc -> { }, "repos");
        }
        pas(10, mc -> {
            long ms = Util.getMillis() - debutSerie;
            int e = ons - on0;
            System.out.printf(Locale.ROOT, "[lot4d] %s : %d ms, %d on, %d off, %d souffles d'entrée, %d de sortie%n", nom, ms, e, offs - off0,
                ModeConcentration.sonsEntree() - sonE0, ModeConcentration.sonsSortie() - sonS0);
            verifie(limites - lim0 == 0, nom + " : aucun « on » refusé par la limite du serveur");
            verifie(!String.valueOf(ModeConcentration.derniereSortie()).startsWith("SERVEUR"), nom + " : aucun refus du serveur");
            verifie(e >= 1 && e == offs - off0, nom + " : autant de « on » que de « off » (" + e + ", " + (offs - off0) + ")");
            verifie(ModeConcentration.sonsEntree() - sonE0 <= 1 + ms / ModeConcentration.SON_INTERVALLE_MS, nom + " : souffles d'entrée espacés");
            verifie(ModeConcentration.sonsSortie() - sonS0 <= 1 + ms / ModeConcentration.SON_INTERVALLE_MS, nom + " : souffles de sortie espacés");
            verifie(!ModeConcentration.actif() && !InteractionMode.INSTANCE.isActive() && !ModeConcentration.filtreVoulu(), nom + " : rien de coincé");
        }, "bilan");
    }

    /* ================================================================ liaisons */

    private static void liaisons() {
        // bouton de souris
        pas(1, mc -> lier(HudKeybinds.CONCENTRATION, InputConstants.Type.MOUSE.getOrCreate(3)), "Concentration sur le bouton 4");
        pas(2, mc -> ClavierBanc.I.touche(GLFW.GLFW_KEY_Y, true), "Y tenue");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif(), "bouton de souris lié : Y ne fait rien");
            ClavierBanc.I.touche(GLFW.GLFW_KEY_Y, false);
            ClavierBanc.I.bouton(3, true);
        }, "bouton 4 tenu");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "bouton de souris tenu : entrée");
            ClavierBanc.I.bouton(3, false);
        }, "bouton 4 relâché");
        pas(3, mc -> verifie(!ModeConcentration.actif() && "TOUCHE".equals(ModeConcentration.derniereSortie()), "bouton de souris relâché : sortie"), "vérif");
        // bouton pressé sous un écran, refermé dans le même tick : l'appui lui-même est relevé
        pas(8, mc -> mc.setScreenAndShow(new ChatScreen("", false)), "tchat ouvert");
        pas(3, mc -> {
            bouton(mc, 3, true);
            ClavierBanc.I.bouton(3, true);
            mc.setScreenAndShow(null);
            verifie(ModeConcentration.attendRelache(), "bouton de souris sous l'écran : relevé sans attendre un tick");
        }, "bouton 4 sous le tchat, fermeture");
        pas(8, mc -> {
            verifie(!ModeConcentration.actif(), "bouton de souris encore tenu, écran fermé : pas de mode");
            bouton(mc, 3, false);
            ClavierBanc.I.bouton(3, false);
        }, "bouton 4 relâché");
        // aucune touche
        pas(8, mc -> { lier(HudKeybinds.CONCENTRATION, InputConstants.UNKNOWN); ClavierBanc.I.tout(true); }, "rien de lié, tout tenu");
        pas(5, mc -> {
            verifie(!ModeConcentration.actif(), "non liée : pas de mode");
            ClavierBanc.I.tout(false);
            lier(HudKeybinds.INTERACTION, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T));
        }, "ancienne touche sur T");
        // ancienne touche : se maintient, aucune bascule
        pas(4, mc -> ClavierBanc.I.touche(GLFW.GLFW_KEY_T, true), "T tenue");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "ancienne touche tenue : entrée");
            ClavierBanc.I.touche(GLFW.GLFW_KEY_T, false);
        }, "T relâchée");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif(), "ancienne touche relâchée : sortie (aucune bascule)");
            lier(HudKeybinds.CONCENTRATION, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Y));
        }, "Concentration sur Y, ancienne touche sur T");
        pas(8, mc -> ClavierBanc.I.touche(GLFW.GLFW_KEY_T, true), "T tenue");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "alias : T tenue entre aussi");
            ClavierBanc.I.touche(GLFW.GLFW_KEY_Y, true);
            ClavierBanc.I.touche(GLFW.GLFW_KEY_T, false);
        }, "Y prise, T lâchée");
        pas(3, mc -> {
            verifie(ModeConcentration.actif(), "l'une ou l'autre tenue : le mode reste");
            ClavierBanc.I.touche(GLFW.GLFW_KEY_Y, false);
            lier(HudKeybinds.INTERACTION, InputConstants.UNKNOWN);
        }, "relâchement");
        pas(8, mc -> verifie(!ModeConcentration.actif(), "relâchement : sortie"), "vérif");
    }

    private static void lier(KeyMapping km, InputConstants.Key k) {
        km.setKey(k);
        KeyMapping.resetMapping();
    }

    /* ================================================================ captures */

    private static void captures(String res, boolean complet) {
        String p = "lot4d_" + res + "_";
        pas(1, mc -> {
            figer(Util.getMillis());
            SCENE.clear();
            scenePistes(mc);
        }, "horloges figées, pistes");

        // jour
        pas(2, mc -> epaule(mc, 0, 1.0, 12f), "joueur");
        pas(25, mc -> shot(mc, p + "jour_hors"), "capture hors concentration");
        entrer();
        pas(20, mc -> avance(300), "fondu fini");
        pas(4, mc -> {
            curseurLoin(mc);
            verifie(ModeConcentration.filtreAffiche() && ModeConcentration.niveau() >= 0.999f, res + " : effet plein");
            verifie(Traces.nombre() == SCENE.size(), res + " : tous les pas reçus (" + Traces.nombre() + ")");
        }, "bilan");
        pas(3, mc -> shot(mc, p + "jour_effet"), "capture jour");
        sortir();

        // nuit
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(30, mc -> shot(mc, p + "nuit_hors"), "capture nuit hors concentration");
        entrer();
        pas(20, mc -> avance(300), "fondu fini");
        pas(4, Lot4dDebug::curseurLoin, "curseur");
        pas(3, mc -> shot(mc, p + "nuit_effet"), "capture nuit");
        sortir();
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(25, mc -> { }, "jour revenu");

        // traces : herbe, sable, pierre, neige, vues de haut
        pas(2, mc -> { SCENE.clear(); sceneSols(mc); epaule(mc, 3.5, 1.0, 34f); }, "sols");
        entrer();
        pas(20, mc -> avance(300), "fondu fini");
        pas(4, Lot4dDebug::curseurLoin, "curseur");
        pas(3, mc -> {
            int clair = 0, sombre = 0;
            for (Pas q : SCENE) {
                int s = Traces.solDe(q.id());
                if (s == 2) clair++;
                else if (s == 1) sombre++;
            }
            System.out.println("[lot4d] " + res + " sols lus : " + clair + " clairs, " + sombre + " sombres, sur " + SCENE.size());
            // sable et neige clairs, herbe et pierre sombres : deux pistes de chaque
            verifie(clair == sombre && clair + sombre == SCENE.size(), res + " : chaque pas a son sol");
            shot(mc, p + "traces_jour_auto");
        }, "capture traces de jour");
        if (complet) {
            pas(2, mc -> { RebornPrefs.INSTANCE.tracesAuto = false; RebornPrefs.INSTANCE.tracesCouleur = 0x6FB4FF; }, "bleu chakra");
            pas(COULEUR_POSEE, mc -> shot(mc, p + "traces_jour_chakra"), "capture bleu chakra");
            pas(2, mc -> RebornPrefs.INSTANCE.tracesCouleur = 0xD9443F, "rouge sang");
            pas(COULEUR_POSEE, mc -> shot(mc, p + "traces_jour_sang"), "capture rouge sang");
            pas(2, mc -> RebornPrefs.INSTANCE.tracesCouleur = 0xFF9A3C, "ambre");
            pas(COULEUR_POSEE, mc -> shot(mc, p + "traces_jour_ambre"), "capture ambre");
            pas(1, mc -> RebornPrefs.INSTANCE.tracesAuto = true, "automatique");
            pas(1, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
            pas(30, mc -> {
                verifie(Traces.contourNuit() > 0.5f, res + " : contour de nuit présent (" + Traces.contourNuit() + ")");
                shot(mc, p + "traces_nuit_auto");
            }, "capture traces de nuit");
            pas(1, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
            pas(25, mc -> { }, "jour revenu");
        }
        sortir();

        // plaque et son halo
        pas(2, mc -> { SCENE.clear(); a(mc, -6.0, 8.0, 0f, 6f); }, "devant la stèle");
        entrer();
        pas(24, mc -> avance(400), "lecture");
        pas(6, mc -> {
            avance(800);
            curseurLoin(mc);
            verifie(Plaques.lue() != null, res + " : plaque lue, touche tenue (" + Plaques.lue() + ")");
            verifie(ModeConcentration.actif(), res + " : toujours concentré");
        }, "bilan");
        pas(4, mc -> shot(mc, p + "plaque_halo_jour"), "capture plaque");
        if (complet) {
            pas(1, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
            pas(30, mc -> shot(mc, p + "plaque_halo_nuit"), "capture plaque de nuit");
            pas(1, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
            pas(25, mc -> { }, "jour revenu");
        }
        sortir();

        // joueur survolé, puis menu ouvert après le relâchement
        pas(2, mc -> { SCENE.clear(); epaule(mc, 0, 1.0, 7f); }, "joueur");
        entrer();
        pas(20, mc -> avance(300), "fondu fini");
        pas(2, Lot4dDebug::surMannequin, "curseur sur le mannequin");
        pas(6, mc -> {
            Entity m = entiteMannequin(mc);
            verifie(m != null && ModeConcentration.survol() == m.getId(), res + " : contour sur le joueur survolé");
            shot(mc, p + "survol_joueur");
        }, "capture survol");
        pas(2, mc -> {
            InteractionMode.INSTANCE.onClick();
            ClavierBanc.tenir(false);
        }, "clic, relâchement");
        pas(8, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.menuOuvert(), res + " : menu ouvert après le relâchement");
            verifie(ModeConcentration.niveau() >= 0.999f, res + " : effet tenu");
            shot(mc, p + "menu_apres_relache");
        }, "capture menu");
        pas(2, mc -> ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE), "sortie");
        pas(1, mc -> avance(600), "fondu fini");
        pas(4, mc -> {
            ModeConcentration.horloge = Util::getMillis;
            Plaques.horloge = Util::getMillis;
            Recit.horloge = Util::getMillis;
            Traces.horloge = Util::getMillis;
        }, "horloges rendues");
    }

    private static void entrer() {
        pas(1, mc -> {
            avance(400);
            ClavierBanc.tenir(true);
        }, "appui");
        pas(2, mc -> verifie(ModeConcentration.actif(), "appui : entrée (refus : " + ModeConcentration.dernierRefus() + ")"), "vérif");
    }

    private static void sortir() {
        pas(1, mc -> ClavierBanc.tenir(false), "relâchement");
        pas(2, mc -> {
            verifie(!ModeConcentration.actif(), "relâchement : sortie");
            avance(600);
        }, "fondu");
        pas(3, mc -> { }, "fondu fini");
    }

    private static void curseurLoin(Minecraft mc) {
        InteractionMode.INSTANCE.debugCursor(mc.getWindow().getGuiScaledWidth() * 0.92, mc.getWindow().getGuiScaledHeight() * 0.12);
    }

    private static void figer(long now) {
        h = now;
        ModeConcentration.horloge = () -> h;
        Plaques.horloge = () -> h;
        Recit.horloge = () -> h;
        Traces.horloge = () -> h;
    }

    private static void avance(long ms) { h += ms; }

    /* ================================================================ scène */

    /** Regard au sud ; à droite de l'écran (x croissant vers la gauche) : herbe, pierre, sable, neige ; à gauche, la stèle ; des fleurs, de la laine, un arbre pour les couleurs. */
    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 14, oy, oz - 8, ox + 14, oy + 8, oz + 28));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 14, oy - 1, oz - 8, ox + 14, oy - 1, oz + 28));
        // bandes, du centre vers la droite de l'écran (x monde décroissant)
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:sand", ox - 3, oy - 1, oz - 8, ox - 1, oy - 1, oz + 28));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", ox - 6, oy - 1, oz - 8, ox - 4, oy - 1, oz + 28));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:snow_block", ox - 9, oy - 1, oz - 8, ox - 7, oy - 1, oz + 28));
        // stèle à gauche
        c.sendCommand(String.format("setblock %d %d %d minecraft:stone_bricks", ox + 7, oy, oz + 12));
        c.sendCommand(String.format("setblock %d %d %d minecraft:chiseled_stone_bricks", ox + 7, oy + 1, oz + 12));
        c.sendCommand(String.format("setblock %d %d %d minecraft:oak_fence", ox + 5, oy, oz + 12));
        c.sendCommand(String.format("setblock %d %d %d minecraft:lantern", ox + 5, oy + 1, oz + 12));
        // des couleurs franches
        String[] laines = {"red_wool", "orange_wool", "yellow_wool", "lime_wool", "cyan_wool", "blue_wool", "purple_wool", "magenta_wool"};
        for (int i = 0; i < laines.length; i++) {
            c.sendCommand(String.format("setblock %d %d %d minecraft:%s", ox + 7 - i, oy, oz + 20, laines[i]));
            c.sendCommand(String.format("setblock %d %d %d minecraft:%s", ox + 7 - i, oy + 1, oz + 20, laines[i]));
        }
        String[] fleurs = {"poppy", "blue_orchid", "dandelion", "allium", "oxeye_daisy", "cornflower"};
        for (int i = 0; i < fleurs.length; i++) {
            c.sendCommand(String.format("setblock %d %d %d minecraft:%s", ox + 2 + i, oy, oz + 7 + (i % 2), fleurs[i]));
        }
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_log", ox + 11, oy, oz + 16, ox + 11, oy + 4, oz + 16));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true]", ox + 9, oy + 4, oz + 14, ox + 13, oy + 6, oz + 18));
        c.sendCommand("kill @e[type=minecraft:item,distance=..60]");
    }

    private static void mannequin(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand("kill @e[type=minecraft:mannequin,distance=..96]");
        c.sendCommand(String.format(Locale.ROOT, "summon minecraft:mannequin %.2f %d %.2f {NoGravity:1b,Rotation:[180f,0f]}", wx(1.5), oy, wz(5.0)));
    }

    private static void scenePistes(Minecraft mc) {
        // herbe puis sable en travers, marche vers le fond dans l'herbe, sable, pierre, neige
        piste(new double[][] {{-6.0, 3.0}, {9.0, 9.0}}, 1.2, 0, 900);
        piste(new double[][] {{-2.0, 2.5}, {-2.0, 16.0}}, 1.2, 0, 300);
        piste(new double[][] {{2.5, 2.5}, {2.5, 16.0}}, 1.2, 0, 1500);
    }

    private static void sceneSols(Minecraft mc) {
        // une piste par sol, vers le fond : herbe, sable, pierre, neige
        piste(new double[][] {{-1.5, 3.0}, {-1.5, 15.0}}, 1.1, 0, 300);
        piste(new double[][] {{2.5, 3.0}, {2.5, 15.0}}, 1.1, 0, 300);
        piste(new double[][] {{5.5, 3.0}, {5.5, 15.0}}, 1.1, 0, 300);
        piste(new double[][] {{8.5, 3.0}, {8.5, 15.0}}, 1.1, 0, 300);
    }

    private static void piste(double[][] pts, double pasL, int fl, int age) {
        double ax = wx(pts[0][0]), az = wz(pts[0][1]), bx = wx(pts[1][0]), bz = wz(pts[1][1]);
        double l = Math.hypot(bx - ax, bz - az), cap = Math.toDegrees(Math.atan2(bx - ax, bz - az)), r = Math.toRadians(cap);
        boolean droit = false;
        for (int i = 0; i <= (int) (l / pasL); i++) {
            double u = i * pasL / l, lat = droit ? 0.13 : -0.13;
            double x = ax + (bx - ax) * u - Math.cos(r) * lat, z = az + (bz - az) * u + Math.sin(r) * lat;
            SCENE.add(new Pas(prochainId++, x, oy, z, Math.floorMod((int) Math.round(cap + (droit ? -6 : 6)), 360), fl | (droit ? 1 : 0), age, 7200));
            droit = !droit;
        }
    }

    private static double wx(double mx) { return ox + 0.5 - mx; }

    private static double wz(double z) { return oz + 0.5 + z; }

    /* ================================================================ serveur simulé */

    private static void serveur(JsonObject in) {
        String a = in.has("a") ? in.get("a").getAsString() : "";
        switch (a) {
            case "on" -> {
                ons++;
                // PlaqueRegles.Limiteur, sur l'horloge réelle : le même calcul que le serveur
                long now = System.currentTimeMillis();
                while (!FENETRE.isEmpty() && now - FENETRE.peekFirst() >= 1000) FENETRE.pollFirst();
                if (FENETRE.size() >= 4) {
                    limites++;
                    FILE.add("{\"v\":1,\"t\":\"etat\",\"on\":false,\"raison\":\"Trop vite.\"}");
                    return;
                }
                FENETRE.addLast(now);
                FILE.add("{\"v\":1,\"t\":\"etat\",\"on\":true}");
                FILE.add(ancres().toString());
                FILE.add(traces());
            }
            case "off" -> offs++;
            case "lire" -> {
                JsonObject o = new JsonObject();
                o.addProperty("v", 1);
                o.addProperty("t", "texte");
                o.addProperty("id", in.get("id").getAsString());
                o.addProperty("rev", 1);
                JsonArray segs = new JsonArray();
                for (Recit.Segment s : Recit.segments(TEXTE)) {
                    JsonArray x = new JsonArray();
                    x.add(s.texte());
                    x.add(s.couleur());
                    segs.add(x);
                }
                o.add("segs", segs);
                FILE.add(o.toString());
            }
            default -> { }
        }
    }

    private static JsonObject ancres() {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "ancres");
        JsonArray l = new JsonArray();
        JsonObject x = new JsonObject();
        x.addProperty("id", "P1A");
        x.addProperty("rev", 1);
        x.addProperty("k", "bloc");
        x.addProperty("x", ox + 7);
        x.addProperty("y", oy);
        x.addProperty("z", oz + 12);
        JsonArray b = new JsonArray();
        b.add(1);
        b.add(2);
        b.add(1);
        x.add("b", b);
        x.addProperty("p", 6);
        x.addProperty("f", "north");
        l.add(x);
        o.add("l", l);
        return o;
    }

    private static String traces() {
        StringBuilder s = new StringBuilder("{\"v\":1,\"t\":\"traces\",\"plein\":true,\"o\":[");
        s.append(ox).append(',').append(oy).append(',').append(oz).append("],\"a\":[");
        boolean premier = true;
        for (Pas p : SCENE) {
            if (!premier) s.append(',');
            premier = false;
            s.append(p.id()).append(',').append(4242).append(',').append(Math.round((p.x() - ox) * 32)).append(',')
                .append(Math.round((p.y() - oy) * 32)).append(',').append(Math.round((p.z() - oz) * 32)).append(',')
                .append(p.ang()).append(',').append(p.fl()).append(',').append(100).append(',').append(p.age()).append(',').append(p.vie());
        }
        return s.append("],\"r\":[]}").toString();
    }

    /* ================================================================ outils */

    private static double posX, posZ;

    private static void debut() {
        on0 = ons;
        off0 = offs;
        lim0 = limites;
        sonE0 = ModeConcentration.sonsEntree();
        sonS0 = ModeConcentration.sonsSortie();
        debutSerie = Util.getMillis();
    }

    private static void surMannequin(Minecraft mc) {
        Entity m = entiteMannequin(mc);
        if (m == null) { verifie(false, "mannequin introuvable"); return; }
        if (!fr.reborn.hud.anchor.ProjectionMonde.versGui(m.getX(), m.getY() + 1.1, m.getZ(), V)) {
            verifie(false, "mannequin hors écran");
            return;
        }
        InteractionMode.INSTANCE.debugCursor(V.x, V.y);
    }

    private static Entity entiteMannequin(Minecraft mc) {
        Entity best = null;
        double d = 64 * 64;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!"minecraft:mannequin".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) continue;
            double x = e.distanceToSqr(mc.player);
            if (x < d) { d = x; best = e; }
        }
        return best;
    }

    /** Le joueur au sol en caméra d'épaule, regard au sud (l'orbite de la caméra Reborn). */
    private static void epaule(Minecraft mc, double mx, double z, float pitch) {
        a(mc, mx, z, 0f, pitch);
        RebornCamera.INSTANCE.initOrientation(0f, pitch);
    }

    private static void a(Minecraft mc, double mx, double z, float yaw, float pitch) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %d %.2f %.1f %.1f", wx(mx), oy, wz(z), yaw, pitch));
    }

    /** Message « Malaxage du chakra » en cours, comme ShinobiAbilities le renvoie à chaque tick. */
    private static void malaxage() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "conc");
        o.addProperty("st", "run");
        o.addProperty("rank", "D");
        o.addProperty("zone", 0.18);
        o.addProperty("c", 0.1);
        o.addProperty("pos", 0.12);
        o.addProperty("held", 0.4);
        o.addProperty("secs", 10);
        o.addProperty("stab", 0.8);
        o.addProperty("in", true);
        ParcheminClient.debugReceive(o.toString());
    }

    private static void stopMalaxage() {
        ParcheminClient.debugReceive("{\"t\":\"conc\",\"st\":\"stop\"}");
    }

    /** Une touche par le vrai chemin du clavier. */
    private static void touche(Minecraft mc, int glfw, boolean appui) {
        evenement(mc, glfw, appui ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
    }

    private static void evenement(Minecraft mc, int glfw, int action) {
        try {
            var m = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
            m.setAccessible(true);
            m.invoke(mc.keyboardHandler, mc.getWindow().handle(), action, new KeyEvent(glfw, GLFW.glfwGetKeyScancode(glfw), 0));
        } catch (ReflectiveOperationException e) {
            verifie(false, "keyPress indisponible : " + e);
        }
    }

    /** Un caractère par le vrai chemin du clavier (le rappel de GLFW, ensuite celui de l'écran). */
    private static void caractere(Minecraft mc, int cp) {
        try {
            var m = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, CharacterEvent.class);
            m.setAccessible(true);
            m.invoke(mc.keyboardHandler, mc.getWindow().handle(), new CharacterEvent(cp));
        } catch (ReflectiveOperationException e) {
            verifie(false, "charTyped indisponible : " + e);
        }
    }

    /** La répétition du système : l'événement {@code REPEAT} puis le caractère, {@code n} fois. */
    private static void repetition(Minecraft mc, int glfw, int cp, int n) {
        for (int i = 0; i < n; i++) {
            evenement(mc, glfw, GLFW.GLFW_REPEAT);
            caractere(mc, cp);
        }
    }

    private static void bouton(Minecraft mc, int b, boolean appui) {
        try {
            var m = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
            m.setAccessible(true);
            m.invoke(mc.mouseHandler, mc.getWindow().handle(), new MouseButtonInfo(b, 0), appui ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
        } catch (ReflectiveOperationException e) {
            verifie(false, "onButton indisponible : " + e);
        }
    }

    /** Ce qui est écrit dans le tchat ouvert. */
    private static String saisie(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ChatScreen ecran)) return "(pas de tchat)";
        try {
            var f = ChatScreen.class.getDeclaredField("input");
            f.setAccessible(true);
            return ((net.minecraft.client.gui.components.EditBox) f.get(ecran)).getValue();
        } catch (ReflectiveOperationException e) {
            return "(" + e + ")";
        }
    }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot4d] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot4d] " + nom);
            action.accept(mc);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (volant) vol(mc, true);
        String r;
        while ((r = FILE.poll()) != null) ConcentrationCanal.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
