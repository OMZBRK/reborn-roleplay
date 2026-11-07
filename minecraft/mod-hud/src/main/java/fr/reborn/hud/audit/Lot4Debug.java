package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.RappelsTouches;
import fr.reborn.hud.parchemin.ParcheminClient;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Banc de captures du lot 4 « Concentration », étape A (Game Plan n° 17), <b>inactif en production</b> :
 * {@code REBORN_LOT4_DEBUG=1} (monde solo de dev, jamais de serveur). Un mannequin « autre joueur » à 3,4 blocs, décalé
 * à droite ; horloge du fondu figée ({@link ModeConcentration#horloge}) pour les images de transition.
 * <ol>
 *   <li>Jour : avant l'entrée (rappels « Concentration [Y] » seule), entrée à 125 ms, concentré (effet plein, curseur,
 *       six rappels), curseur sur le mannequin (contour lumineux), clic (menu de l'entité, contour coupé), menu d'un
 *       joueur (notre kit), sortie à 75 ms, après la sortie.</li>
 *   <li>Nuit : avant, concentré, curseur sur le mannequin.</li>
 *   <li>Blocages : entrée refusée pendant le Malaxage du chakra (capture), puis sortie quand il démarre ; entrée refusée
 *       5 s après un coup ; sortie au coup ; écran ouvert = sortie ; tchat = le mode reste ; Échap (vrai chemin du
 *       clavier) = sortie sans pause ; touche Concentration (vrai chemin) = entrée puis sortie.</li>
 *   <li>Les captures clés à nouveau en 1366 × 768.</li>
 * </ol>
 * → {@code run/screenshots/lot4_*.png}, bilans « [lot4] OK / ECHEC » au journal ; le jeu se ferme (« [lot4] FIN »).
 * Le monde est rendu : mannequin retiré, heure, météo et mode de jeu remis.
 */
public final class Lot4Debug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs;
    private static long heure = -1;
    private static boolean pluie, volant;
    private static String mode = "survival";
    /** Horloge figée du fondu (ms). */
    private static long h0, h;
    private static Method keyPress;
    private static final String NOM = "Ren_Hyuga";
    private static final Vector3f V = new Vector3f();

    private Lot4Debug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_LOT4_DEBUG"))) return;

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..96]");
            c.sendCommand("time set 6000");
            c.sendCommand("weather clear");
            c.sendCommand("tp @s ~ ~ ~ ~ 8");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            volant = true;
            vol(mc, true);
        }, "préparation");
        pas(30, mc -> {
            // un « autre joueur » à 3,4 blocs, décalé à droite (la caméra épaule cache ce qui est droit devant)
            double yaw = Math.toRadians(mc.player.getYRot());
            double dx = -Math.sin(yaw), dz = Math.cos(yaw);
            double x = mc.player.getX() + dx * 3.4 - dz * 1.5, z = mc.player.getZ() + dz * 3.4 + dx * 1.5;
            float face = (float) Math.toDegrees(Math.atan2(-(mc.player.getX() - x), mc.player.getZ() - z));
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {NoGravity:1b,Rotation:[%.1ff,0f]}", x, mc.player.getY(), z, face));
            figer(Util.getMillis());
        }, "mannequin, horloge figée");

        // ───────── jour
        serie("jour", "1080", true);

        // ───────── nuit
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(30, mc -> shot(mc, "lot4_1080_nuit_01_avant"), "capture");
        pas(1, mc -> entrer(mc), "entrée");
        pas(1, mc -> avance(250), "fondu fini");
        pas(6, mc -> shot(mc, "lot4_1080_nuit_02_concentre"), "capture");
        pas(1, Lot4Debug::surMannequin, "curseur sur le mannequin");
        pas(6, mc -> {
            verifie(ModeConcentration.survol() >= 0, "nuit : contour sur l'entité survolée (id " + ModeConcentration.survol() + ")");
            shot(mc, "lot4_1080_nuit_03_survol_contour");
        }, "capture");
        // un invisible ne se trahit pas : ni contour, ni étiquette
        pas(1, mc -> mc.player.connection.sendCommand(
            "effect give @e[type=minecraft:mannequin,distance=..12] minecraft:invisibility 60 0 true"), "mannequin invisible");
        pas(4, Lot4Debug::surMannequin, "curseur sur l'invisible");
        pas(4, mc -> {
            verifie(ModeConcentration.survol() < 0 && !ModeConcentration.cibleInteractive(),
                "invisible : aucun contour, aucune étiquette (survol " + ModeConcentration.survol() + ")");
            shot(mc, "lot4_1080_nuit_04_invisible");
        }, "vérif + capture");
        pas(1, mc -> mc.player.connection.sendCommand("effect clear @e[type=minecraft:mannequin,distance=..12]"), "visible");
        pas(1, mc -> { ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE); avance(200); }, "sortie");
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");

        // ───────── blocages
        pas(20, mc -> malaxage(), "Malaxage du chakra (run)");
        pas(2, mc -> {
            malaxage();
            boolean ok = ModeConcentration.entrer();
            verifie(!ok && "malaxage du chakra".equals(ModeConcentration.dernierRefus()),
                "entrée refusée pendant le Malaxage du chakra (refus : " + ModeConcentration.dernierRefus() + ")");
        }, "entrée pendant le Malaxage");
        pas(4, mc -> { malaxage(); shot(mc, "lot4_1080_refus_malaxage"); }, "capture");
        pas(1, mc -> stopMalaxage(), "fin du Malaxage");
        pas(3, mc -> verifie(ModeConcentration.entrer(), "entrée permise après le Malaxage"), "entrée");
        pas(2, mc -> malaxage(), "le Malaxage démarre");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && String.valueOf(ModeConcentration.derniereSortie()).contains("malaxage"),
                "sortie quand le Malaxage démarre (" + ModeConcentration.derniereSortie() + ")");
            stopMalaxage();
            avance(200);
        }, "vérif");
        // ATA (repos actif après un KO) : la concentration reste permise, le filtre du KO garde l'emplacement ;
        // à terre (phase 1) : refusée
        pas(1, mc -> fr.reborn.hud.ko.KoClient.update(ko(0, 1)), "ATA reçue");
        pas(20, mc -> {
            boolean ok = ModeConcentration.entrer();
            verifie(ok, "ATA : entrée permise (refus : " + ModeConcentration.dernierRefus() + ")");
            avance(250);
        }, "entrée en ATA");
        pas(6, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.isActive(), "ATA : concentré, curseur actif");
            shot(mc, "lot4_1080_ata_concentre");
        }, "capture ATA");
        pas(1, mc -> fr.reborn.hud.ko.KoClient.update(ko(1, 0)), "à terre reçu");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && String.valueOf(ModeConcentration.derniereSortie()).contains("à terre"),
                "à terre : sortie (" + ModeConcentration.derniereSortie() + ")");
            avance(300);
            boolean ok = ModeConcentration.entrer();
            verifie(!ok && "à terre".equals(ModeConcentration.dernierRefus()),
                "à terre : entrée refusée (refus : " + ModeConcentration.dernierRefus() + ")");
            fr.reborn.hud.ko.KoClient.clear();
            avance(300);
        }, "vérif à terre");
        pas(20, mc -> { }, "filtre du KO retombé");
        // combat : un coup donné ferme le mode, puis refus 5 s
        pas(3, mc -> verifie(ModeConcentration.entrer(), "entrée (avant le coup)"), "entrée");
        pas(3, mc -> CombatState.INSTANCE.onHit(mc.player.getId(), 2f, System.currentTimeMillis()), "coup donné");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && String.valueOf(ModeConcentration.derniereSortie()).contains("coup"),
                "sortie au coup (" + ModeConcentration.derniereSortie() + ")");
            avance(200);
            boolean ok = ModeConcentration.entrer();
            verifie(!ok && "combat".equals(ModeConcentration.dernierRefus()),
                "entrée refusée juste après un coup (refus : " + ModeConcentration.dernierRefus() + ")");
        }, "vérif + refus 5 s");
        pas(110, mc -> {
            verifie(ModeConcentration.entrer(), "entrée permise 5 s après le coup");
            avance(250);   // horloge figée : fondu d'entrée fini pour la capture du tchat
        }, "entrée après 5,5 s");
        // tchat : le mode reste ; autre écran : sortie
        pas(3, mc -> mc.setScreenAndShow(new ChatScreen("", false)), "tchat ouvert");
        pas(6, mc -> {
            verifie(ModeConcentration.actif(), "tchat ouvert : le mode reste");
            shot(mc, "lot4_1080_tchat_ouvert");
        }, "vérif + capture");
        pas(2, mc -> mc.setScreenAndShow(null), "tchat fermé");
        pas(3, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.isActive(), "tchat fermé : toujours concentré");
            mc.setScreenAndShow(new net.minecraft.client.gui.screens.options.OptionsScreen(null, mc.options, true));
        }, "Paramètres ouverts");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && "ECRAN".equals(ModeConcentration.derniereSortie()),
                "écran ouvert : sortie (" + ModeConcentration.derniereSortie() + ")");
            mc.setScreenAndShow(null);
            avance(200);
        }, "vérif");
        // Échap, par le vrai chemin du clavier : sortie, pas de pause
        pas(3, mc -> verifie(ModeConcentration.entrer(), "entrée (avant Échap)"), "entrée");
        pas(3, mc -> { touche(mc, GLFW.GLFW_KEY_ESCAPE, true); touche(mc, GLFW.GLFW_KEY_ESCAPE, false); }, "Échap");
        pas(3, mc -> {
            verifie(!ModeConcentration.actif() && "ECHAP".equals(ModeConcentration.derniereSortie())
                && mc.gui.screen() == null, "Échap : sortie sans pause (écran : " + mc.gui.screen() + ")");
            avance(300);
        }, "vérif");
        // touche Concentration, par le vrai chemin du clavier
        pas(3, mc -> {
            int k = cle();
            System.out.println("[lot4] touche Concentration : " + HudKeybinds.nom(HudKeybinds.CONCENTRATION, "aucune")
                + " · ancienne touche Interaction : " + HudKeybinds.nom(HudKeybinds.INTERACTION, "aucune"));
            touche(mc, k, true);
            touche(mc, k, false);
        }, "appui Concentration");
        pas(4, mc -> {
            verifie(ModeConcentration.actif(), "touche Concentration : entrée");
            avance(300);
            int k = cle();
            touche(mc, k, true);
            touche(mc, k, false);
        }, "second appui");
        pas(4, mc -> {
            verifie(!ModeConcentration.actif() && "TOUCHE".equals(ModeConcentration.derniereSortie()),
                "touche Concentration : sortie (" + ModeConcentration.derniereSortie() + ")");
            avance(300);
        }, "vérif");

        // ───────── 1366 × 768
        pas(2, mc -> {
            mc.getWindow().setWindowed(1366, 768);
            mc.resizeGui();
        }, "fenêtre 1366 × 768");
        serie("jour", "1366", false);

        pas(10, mc -> {
            ModeConcentration.reinitialiser();
            ModeConcentration.horloge = Util::getMillis;
            InteractionMode.INSTANCE.debugClose();
            var c = mc.player.connection;
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> { volant = false; vol(mc, false); mc.player.connection.sendCommand("gamemode " + mode); }, "mode de jeu");
        pas(10, mc -> {
            System.out.println("[lot4] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot4Debug::tick);
    }

    /** Série de captures : avant, entrée à 125 ms, concentré, survol, menu, (menu d'un joueur), sortie à 75 ms, après. */
    private static void serie(String moment, String res, boolean complete) {
        String p = "lot4_" + res + "_" + moment + "_";
        pas(20, mc -> shot(mc, p + "01_avant"), "capture avant");
        pas(1, Lot4Debug::entrer, "entrée");
        pas(6, mc -> {
            verifie(ModeConcentration.actif() && InteractionMode.INSTANCE.isActive(), res + " : concentré, curseur actif");
            verifie(ModeConcentration.filtreAffiche(), res + " : filtre de la concentration à l'écran");
            System.out.printf(java.util.Locale.ROOT, "[lot4] %s rappels %s%n", res, RappelsTouches.bilan());
            shot(mc, p + "02_entree_0ms");
        }, "capture 0 ms");
        pas(1, mc -> avance(125), "125 ms");
        pas(5, mc -> {
            float n = ModeConcentration.niveau();
            verifie(n > 0.4f && n < 0.6f, res + " : niveau de l'effet à 125 ms = " + n + " (0,5 attendu, courbe douce)");
            shot(mc, p + "03_entree_125ms");
        }, "capture 125 ms");
        pas(1, mc -> avance(125), "250 ms");
        pas(5, mc -> {
            verifie(ModeConcentration.niveau() >= 0.999f, res + " : effet plein à 250 ms");
            System.out.printf(java.util.Locale.ROOT, "[lot4] %s rappels %s%n", res, RappelsTouches.bilan());
            shot(mc, p + "04_concentre");
        }, "capture concentré");
        pas(1, Lot4Debug::surMannequin, "curseur sur le mannequin");
        pas(6, mc -> {
            Entity m = mannequin(mc);
            verifie(m != null && ModeConcentration.survol() == m.getId(), res + " : contour sur le mannequin survolé");
            verifie(ModeConcentration.cibleInteractive(), res + " : étiquette « Clic interagir » sur la cible");
            shot(mc, p + "05_survol_contour");
        }, "capture survol");
        pas(1, mc -> InteractionMode.INSTANCE.onClick(), "clic");
        pas(6, mc -> {
            Entity m = mannequin(mc);
            verifie(InteractionMode.INSTANCE.menuOuvert(), res + " : menu ouvert au clic");
            verifie(m == null || !(m instanceof net.minecraft.world.entity.LivingEntity le) || !ModeConcentration.halo(le),
                res + " : contour coupé menu ouvert");
            System.out.printf(java.util.Locale.ROOT, "[lot4] %s rappels menu ouvert %s%n", res, RappelsTouches.bilan());
            shot(mc, p + "06_menu_entite");
            // clic droit maintenu menu ouvert : pas de rotation (le panneau resterait figé)
            ModeConcentration.clicDroit(true);
            verifie(!ModeConcentration.rotation(), res + " : pas de rotation menu ouvert");
            ModeConcentration.clicDroit(false);
        }, "capture menu");
        if (complete) {
            pas(1, mc -> {
                List<fr.reborn.hud.interaction.InteractionItem> it = InteractionMenus.forPlayer(NOM);
                int idx = -1;
                for (int i = 0; i < it.size(); i++) if (it.get(i).label().startsWith("Porter")) { idx = i; break; }
                InteractionMode.INSTANCE.debugOpen(NOM, it, idx);
            }, "menu d'un joueur");
            pas(8, mc -> shot(mc, p + "07_menu_joueur"), "capture menu joueur");
        }
        pas(1, mc -> {
            ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE);
            avance(0);
        }, "sortie");
        pas(1, mc -> avance(75), "sortie 75 ms");
        pas(5, mc -> {
            float n = ModeConcentration.niveau();
            verifie(n > 0.4f && n < 0.6f, res + " : niveau à 75 ms de la sortie = " + n);
            verifie(!InteractionMode.INSTANCE.isActive(), res + " : curseur fermé à la sortie");
            shot(mc, p + "08_sortie_75ms");
        }, "capture sortie");
        pas(1, mc -> avance(100), "sortie finie");
        pas(6, mc -> {
            verifie(ModeConcentration.niveau() <= 0.001f, res + " : effet fini 150 ms après la sortie");
            System.out.printf(java.util.Locale.ROOT, "[lot4] %s rappels après %s%n", res, RappelsTouches.bilan());
            shot(mc, p + "09_apres");
        }, "capture après");
    }

    /* ================================================================ outils */

    private static void entrer(Minecraft mc) {
        avance(300);   // anti-rebond
        boolean ok = ModeConcentration.entrer();
        verifie(ok, "entrée en concentration (refus : " + ModeConcentration.dernierRefus() + ")");
    }

    /** État KO factice : phase (0 aucune, 1 à terre, 2 inconscient) et ATA (0, 1, 2). */
    private static fr.reborn.hud.ko.KoPayload ko(int phase, int ata) {
        return new fr.reborn.hud.ko.KoPayload((byte) phase, (byte) 0, (short) 60, (short) 60, false, (byte) ata, 0f,
            (short) 0, (short) 10, false, false);
    }

    private static void figer(long now) {
        h0 = h = now;
        ModeConcentration.horloge = () -> h;
    }

    private static void avance(long ms) { h += ms; }

    /** Le curseur sur la poitrine du mannequin (projetée comme le monde). */
    private static void surMannequin(Minecraft mc) {
        Entity m = mannequin(mc);
        if (m == null) { verifie(false, "mannequin introuvable"); return; }
        if (!fr.reborn.hud.anchor.ProjectionMonde.versGui(m.getX(), m.getY() + 1.1, m.getZ(), V)) {
            verifie(false, "mannequin hors écran");
            return;
        }
        InteractionMode.INSTANCE.debugCursor(V.x, V.y);
    }

    private static Entity mannequin(Minecraft mc) {
        Entity best = null;
        double d = 64;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!"minecraft:mannequin".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) continue;
            double x = e.distanceToSqr(mc.player);
            if (x < d) { d = x; best = e; }
        }
        return best;
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

    private static int cle() {
        InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(HudKeybinds.CONCENTRATION);
        return k.getType() == InputConstants.Type.KEYSYM ? k.getValue() : -1;
    }

    /** Une touche par le vrai chemin du clavier (comme {@code TouchesDebug}). */
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

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot4] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot4] " + nom);
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
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (volant) vol(mc, true);
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
