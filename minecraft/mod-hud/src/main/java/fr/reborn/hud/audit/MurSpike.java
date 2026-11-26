package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.mur.MurCamera;
import fr.reborn.hud.mur.MurCanal;
import fr.reborn.hud.mur.MurClient;
import fr.reborn.hud.mur.MurFace;
import fr.reborn.hud.mur.MurRendu;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Banc de la <b>marche sur les murs</b> (lot 5, essai C0 du 23/11, caméra de paroi et passages C1a), <b>inactif en
 * production</b> : {@code REBORN_MUR_SPIKE=1} (monde solo de dev, jamais de serveur ; {@code REBORN_MUR_SPIKE=cam} ne joue
 * que la série de la caméra, {@code REBORN_MUR_SPIKE=coins} que celle des passages). Le serveur intégré n'a pas le plugin :
 * le serveur ShinobiAbilities est <b>simulé côté client</b> au format du canal {@code reborn:mur} ({@code permis},
 * {@code etat} avec le numéro {@code n} de la demande, {@code refus} avec l'action refusée {@code a}, rendus au tick
 * suivant, comme le réseau ; un {@code face} reçoit un accord, un refus ou rien, selon la série). Les touches sont les vraies ({@code KeyMapping}), le déplacement,
 * la boîte, la pose et le rendu passent par les vrais mixins : le joueur s'accroche et marche dans le monde, tick par tick.
 *
 * <p>Scène : un bloc de briques de 9 de large sur 12 de haut et 3 d'épaisseur (quatre faces marchables), une rangée de
 * laine tous les 4 blocs sur la face nord et un arbre devant (repères de la caméra de paroi), un obstacle au-dessus du
 * corps à 8 de haut, une cloison de pierre perpendiculaire à la face nord (coin rentrant, puis coin sortant à son bout),
 * un auvent au-dessus du sommet de la face nord (sommet sous auvent), un tronc de pierre de 1 × 1 isolé (tour complet), un mannequin posé sur le mur (« un autre joueur », son état vient du serveur simulé), puis un
 * mannequin debout au sol pour la série de la caméra, le tout retiré à la fin. L'obstacle est un barreau à 1,5 bloc de la paroi et non un surplomb collé au mur : le serveur
 * intégré du solo garde une boîte debout de 1,8 (il n'a pas la pose de nage que pose le plugin), il buterait sous un
 * surplomb avant le corps couché et renverrait le joueur en arrière (vu à la première passe : 17 corrections). Séries :
 * <ul>
 *   <li><b>protocole</b> : rien d'envoyé sans permis, format des messages, abandon après une seconde sans réponse, réponse
 *       tardive rendue, refus ;</li>
 *   <li><b>face nord</b> : boîte de 1,75 × 0,6 × 0,6 qui contient le cube du serveur, pose tenue, aucun glissement imposé
 *       de la caméra, 3,2 et 5,6 blocs/s, arrêt sous l'auvent du sommet, touches continues (aucun cran, le tangage ne
 *       change pas le sens), quatre sens, {@code P} à 0,9 de la paroi après 5 s de marche, virage d'un voisin (20° par
 *       tick au plus, cube le temps du virage), refus de face au bord (toujours au mur), annulation (accord en retard
 *       ignoré), abandon après une seconde sans accord, correction de position comptée, sortie par Espace ;</li>
 *   <li><b>obstacle au-dessus</b> (arrêt dessous, sortie par la touche de course de chakra), <b>refus reçu au mur</b>,
 *       <b>faces sud, est et ouest</b> (le serveur lâche, pied du mur : debout au sol à 0,9, dos au mur, un seul
 *       {@code lache} à la fin, deux fois, regard vers le vide puis regard au haut du mur, le cap rendu ne reculant
 *       jamais d'une image à l'autre ; permis retiré) ;</li>
 *   <li><b>passages</b> (C1a, temps 2) : coin rentrant puis coin sortant enchaînés sur la cloison, tour complet du tronc
 *       (quatre coins sortants, sans battement, puis lâché par le serveur : le corps se relève sans tourner), sommet libre
 *       (un seul {@code lache}, au début, debout sur le toit, face au toit) ;
 *       après chaque virage, {@code P} à 0,9 de la nouvelle paroi, boîte couchée sur elle, cap conservé ;</li>
 *   <li><b>caméra de paroi</b> (C1a) : roulis d'accroche lissé et plafonné, repère de surface, cadrage du joueur rendu,
 *       regard réel envoyé, roulis défait à la sortie, réglage « Droite » (aucun roulis, touches fixes), 1re personne
 *       passée en épaule puis rendue.</li>
 * </ul>
 * Captures {@code run/screenshots/mur_*.png} : {@code accroche}, {@code monter}, {@code descendre}, {@code cote},
 * {@code long}, {@code dos}, {@code boites} (boîtes de collision affichées), {@code second} et {@code second_boites} (vue
 * de côté par une caméra libre, celle du mode photo : le jeu ne dessine pas le joueur local vu par une autre entité) ;
 * {@code mur_cam_roulis_1} à {@code _8} (le roulis d'accroche, une image toutes les trois ticks) et
 * {@code mur_cam_<suit|droite>_<monter|longer|descendre>} (touche tenue, depuis le même pied de mur) ;
 * {@code mur_coin_rentrant_1} à {@code _3} et {@code mur_coin_sortant_1} à {@code _3} (trois images de chaque virage, jeu
 * ralenti à 4 ticks par seconde), {@code mur_sommet}, {@code mur_pied} et {@code mur_pied_haut} (milieu de la sortie). Bilans « [mur] OK /
 * ECHEC », puis « [mur] FIN (n échec(s)) » et le jeu se ferme.
 */
public final class MurSpike {

    /** {@code auTick} : étape jouée au tick près, sans attendre d'images (mesures de déplacement). */
    private record Etape(int delai, Predicate<Minecraft> action, String nom, boolean auTick) { }

    private enum Touche { AVANT, ARRIERE, GAUCHE, DROITE, COURSE, SAUT }

    private static final List<Etape> ETAPES = new ArrayList<>();
    /** Réponses du serveur simulé, rendues une par tick. */
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();
    /** Messages reçus du mod, dans l'ordre. */
    private static final List<JsonObject> RECUS = new ArrayList<>();
    private static final Set<Touche> TENUES = EnumSet.noneOf(Touche.class);
    private static int t = -1, etape, prochaine, echecs, attente;
    /** Images rendues (couche HUD du banc) ; une capture part trois images après sa demande. */
    private static int images, imagesEtape, imagesCapture;
    private static String capture;
    private static final int IMAGES_AVANT_CAPTURE = 3;
    /** Ce que le serveur simulé répond à une demande d'accroche : « etat », « refus » ou « muet ». */
    private static String reponse = "muet";
    /** Ce que le serveur simulé répond à un changement de face : « etat » (accord), « refus » ou « muet ». */
    private static String reponseFace = "etat";
    /** Fins de virage vues (face tenue, puis x, y, z de {@code P}), dans l'ordre. */
    private static final List<Object[]> ARRIVEES = new ArrayList<>();
    /**
     * {@code P} au tick où la dernière sortie s'est terminée (passage de la sortie au repos), avant que la physique du
     * jeu et une touche encore tenue ne le déplacent.
     */
    private static final double[] finSortie = new double[3];
    private static MurClient.Phase phaseAvant = MurClient.Phase.REPOS;
    /**
     * Cap rendu du corps local à chaque pose d'une sortie animée (sommet, pied ; degrés, {@link MurRendu#debugCapSortie}),
     * et, à la fin de la sortie : nombre de poses, reculs (un pas dans le sens contraire du mouvement d'ensemble), plus
     * grand pas d'une pose à l'autre, lacet du corps debout que donne la dernière pose (lacet de la paroi moins le cap).
     */
    private static final List<Float> CAPS = new ArrayList<>();
    private static float murSortie = Float.NaN, lacetDernierePose = Float.NaN;
    private static int posesSortie, reculsSortie;
    private static double pasCapMax;
    /** Lâcher immédiat suivi tick par tick : lacet rendu au lâcher, plus grand écart brut vu depuis (degrés). */
    private static boolean suiviLacetLache;
    private static float lacetLache = Float.NaN, ecartLacetLache;
    private static int correctionsAvant;
    /** Virage d'un voisin suivi tick par tick : plus grand pas du lacet rendu, cube vu pendant le virage. */
    private static boolean suiviVoisin, cubeVoisinVu;
    private static float lacetVoisinAvant = Float.NaN, pasVoisinMax;
    private static int ox, oy, oz;
    private static String mode = "creative", difficulte = "normal";
    private static long heure = -1;
    private static boolean pluie, sprintBascule, poseTombee;
    private static RebornCamera.Mode cameraAvant;
    /** Réglage « Caméra sur les murs » du joueur, rendu à la fin. */
    private static boolean murDroiteAvant;
    /**
     * Roulis de la caméra de paroi, mesuré à chaque image et à l'horloge réelle (le repère avance avec le temps des images,
     * pas avec les ticks : un tick de rattrapage ou une capture qui fige une image fausseraient une mesure au tick).
     * Vitesse la plus forte sur des fenêtres d'au moins {@value #FENETRE_NS} ns ; durées du quart de tour à l'accroche
     * (jusqu'à 85°) et au retour (jusqu'à 5°), en secondes, négatives tant qu'elles ne sont pas mesurées.
     */
    private static final long FENETRE_NS = 100_000_000L;
    private static long nsFenetre, nsAccroche, nsLache;
    private static double roulisFenetre, vitesseRoulisMax, quartAccroche = -1.0, quartRetour = -1.0;
    private static boolean corpsAvant;
    private static CameraType vueAvant;
    private static DebugScreenEntryStatus boitesAvant;
    /** Orbite de caméra voulue par l'étape en cours, reposée à chaque tick (NaN : laissée au mod, glissement de l'accroche). */
    private static double orbiteLacet = Double.NaN, orbiteTangage;
    /** Les touches tenues sont relâchées au tick même où l'accroche est confirmée. */
    private static boolean relacherALAccroche;
    /**
     * Les touches tenues sont relâchées au tick même où le prochain virage finit : sans cela, quand le jeu rend peu
     * d'images (captures lentes, fenêtre en arrière-plan), la marche reprend jusqu'au coin suivant avant le bilan.
     */
    private static boolean relacherAuVirage;
    /** Mesure d'un déplacement : départ et nombre de ticks écoulés depuis. */
    private static double mx, my, mz;
    private static int mTicks;
    private static Entity voisin;

    private MurSpike() {}

    public static void init() {
        String banc = System.getenv("REBORN_MUR_SPIKE");
        boolean complet = "1".equals(banc), coins = "coins".equals(banc);
        if (!complet && !coins && !"cam".equals(banc)) return;

        pas(5, mc -> {
            var c = mc.player.connection;
            if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
            var srv = mc.getSingleplayerServer();
            if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
            RebornSession.debugForceRp = true;
            c.sendCommand("gamemode creative");
            c.sendCommand("time set 6000");
            c.sendCommand("gamerule advance_time false");
            c.sendCommand("weather clear");
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            var ab = mc.player.getAbilities();
            ab.flying = false;
            mc.player.onUpdateAbilities();
            sprintBascule = Boolean.TRUE.equals(mc.options.toggleSprint().get());
            mc.options.toggleSprint().set(false);
            cameraAvant = RebornCamera.INSTANCE.mode();
            murDroiteAvant = RebornPrefs.INSTANCE.camMurDroite;
            RebornPrefs.INSTANCE.camMurDroite = false;   // en mémoire seulement : rien n'est écrit sur le disque
            vueAvant = mc.options.getCameraType();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            boitesAvant = mc.debugEntries.getStatus(DebugScreenEntries.ENTITY_HITBOXES);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER);
            System.out.println("[mur] origine " + ox + " " + oy + " " + oz);
        }, "préparation");
        pas(4, MurSpike::batir, "scène : mur de briques de 9 × 12, laine, arbre, obstacle, cloison, auvent, tronc");
        pas(4, MurSpike::degagerCiel, "ciel dégagé");
        pas(40, mc -> {
            MurCanal.debugServeur = MurSpike::serveur;
            MurRendu.debugCapSortie = c -> CAPS.add((float) c);
            System.out.println("[mur] lumière du ciel au pied du mur : "
                + mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new net.minecraft.core.BlockPos(ox, oy, oz + 4)));
        }, "serveur simulé");

        if (complet) {
            protocole();
            faceNord();
            plafond();
            autresFaces();
        }
        if (complet || coins) passages();
        if (!coins) camera();

        pas(5, mc -> {
            TENUES.clear();
            appliquerTouches(mc);
            orbiteLacet = Double.NaN;
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":false}");
            MurCanal.debugServeur = null;
            MurRendu.debugCapSortie = null;
            mc.player.connection.sendCommand("tick rate 20");
            if (PhotoMode.INSTANCE.isActive()) PhotoMode.INSTANCE.end(mc);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, boitesAvant);
            mc.options.toggleSprint().set(sprintBascule);
            RebornCamera.INSTANCE.setMode(cameraAvant);
            mc.options.setCameraType(vueAvant);
            RebornPrefs.INSTANCE.camMurDroite = murDroiteAvant;
            var c = mc.player.connection;
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 4, oy, oz + 3, ox + 4, oy + 13, oz + 8));
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox + 9, oy, oz + 1, ox + 9, oy + 5, oz + 1));
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 10, oy, oz, ox - 6, oy + 6, oz + 4));
            c.sendCommand("gamerule advance_time true");
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            c.sendCommand("gamemode " + mode);
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> {
            System.out.println("[mur] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");

        ClientTickEvents.START_CLIENT_TICK.register(MurSpike::garde);
        ClientTickEvents.END_CLIENT_TICK.register(MurSpike::tick);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "mur-banc"), (g, dt) -> {
                images++;
                mesureRoulis();
            });
    }

    /* ================================================================ séries */

    /** Rien sans permis ; format ; abandon après une seconde ; réponse tardive rendue ; refus. */
    private static void protocole() {
        placer(MurFace.N, 0.5);
        pas(1, mc -> tenir(Touche.AVANT), "poussée contre le mur, sans permis");
        pas(14, mc -> {
            verifie(mc.player.horizontalCollision, "le joueur pousse bien contre le mur");
            verifie(RECUS.isEmpty() && MurClient.phase() == MurClient.Phase.REPOS, "sans permis : rien n'est envoyé");
            TENUES.clear();
        }, "bilan sans permis");

        pas(3, mc -> {
            MurCanal.recevoir("{\"v\":2,\"t\":\"permis\",\"on\":true}");
            verifie(!MurClient.permis(), "message d'une autre version ignoré");
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":true,\"inconnu\":7}");
            verifie(MurClient.permis(), "permis reçu (champ inconnu ignoré)");
            reponse = "muet";
            tenir(Touche.AVANT);
        }, "permis, serveur muet");
        attendre(mc -> !RECUS.isEmpty(), 20, "demande d'accroche");
        pas(0, mc -> {
            JsonObject m = dernier();
            verifie(cles(m, "v", "a", "f", "n") && m.get("v").getAsInt() == 1 && "accroche".equals(action(m))
                && "N".equals(m.get("f").getAsString()) && m.get("n").getAsInt() == MurClient.numero(),
                "format de la demande, numéro compris : " + m);
            verifie(MurClient.phase() == MurClient.Phase.ATTENTE && mc.player.getPose() == Pose.STANDING && debout(mc.player),
                "le mod attend la réponse : rien n'a changé");
        }, "demande envoyée");
        pas(26, mc -> {
            verifie(MurClient.phase() == MurClient.Phase.REPOS && mc.player.getPose() == Pose.STANDING && debout(mc.player),
                "abandon après 1 s sans réponse : debout, boîte du jeu");
            verifie(RECUS.size() == 1, "aucune nouvelle demande tant que le joueur pousse (" + RECUS.size() + " message)");
            FILE.add(etat(mc.player.getId(), "N"));
        }, "réponse du serveur arrivée trop tard");
        pas(4, mc -> {
            JsonObject m = dernier();
            verifie(RECUS.size() == 2 && cles(m, "v", "a", "c") && "lache".equals(action(m)) && m.get("c").getAsInt() == 0,
                "réponse tardive rendue au serveur : " + m);
            verifie(!MurClient.corpsPris && mc.player.getPose() == Pose.STANDING && debout(mc.player), "réponse tardive : toujours debout");
            TENUES.clear();
        }, "bilan de la réponse tardive");

        pas(6, mc -> { reponse = "refus"; RECUS.clear(); tenir(Touche.AVANT); }, "nouvelle poussée, le serveur refuse");
        attendre(mc -> !RECUS.isEmpty(), 20, "demande d'accroche");
        pas(4, mc -> {
            verifie(RECUS.size() == 1 && MurClient.phase() == MurClient.Phase.REPOS && !MurClient.corpsPris && debout(mc.player),
                "refus : le mod reste debout");
            TENUES.clear();
        }, "bilan du refus");

        // avec de la latence, l'écho « - » d'un lache précédent arrive pendant l'attente de la demande suivante
        pas(6, mc -> { reponse = "muet"; RECUS.clear(); tenir(Touche.AVANT); }, "nouvelle poussée, serveur muet");
        attendre(mc -> !RECUS.isEmpty(), 20, "demande d'accroche");
        pas(0, mc -> FILE.add(etat(mc.player.getId(), "-")), "écho d'un lache précédent");
        pas(2, mc -> {
            verifie(MurClient.phase() == MurClient.Phase.ATTENTE && RECUS.size() == 1 && debout(mc.player),
                "écho « - » pendant l'attente : la demande tient");
            relacherALAccroche = true;
            FILE.add(etat(mc.player.getId(), "N"));
        }, "la vraie réponse arrive");
        attendre(mc -> MurClient.phase() == MurClient.Phase.MARCHE, 30, "bascule jouée après l'écho");
        pas(1, mc -> {
            verifie(MurClient.corpsPris && RECUS.size() == 1, "réponse suivie après l'écho : au mur, rien de rendu au serveur");
            reponse = "etat";
            RECUS.clear();
            tenir(Touche.SAUT);
        }, "Espace");
        pas(2, mc -> TENUES.clear(), "Espace relâchée");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        attendre(mc -> mc.player.onGround(), 60, "retombé au sol");
    }

    /** La marche complète sur la face nord, ses mesures et les captures de cadrage. */
    private static void faceNord() {
        MurFace f = MurFace.N;
        pas(2, mc -> mc.player.connection.sendCommand(String.format(Locale.ROOT,
            "summon minecraft:mannequin %.2f %d %.2f {NoGravity:1b,immovable:1b,pose:\"swimming\",Rotation:[0f,0f]}",
            ox - 1.5, oy + 9, oz + 6 - MurClient.DISTANCE)), "mannequin posé sur le mur (un autre joueur)");
        attendre(mc -> (voisin = trouver(mc, Mannequin.class)) != null, 40, "mannequin connu du client");

        accrocher(f, 0.5);
        pas(12, mc -> {
            RebornCamera cam = RebornCamera.INSTANCE;
            double ecart = Math.abs(Mth.wrapDegrees(cam.camYaw() - f.lacet));
            verifie(ecart < 0.01 && Math.abs(cam.camPitch()) < 0.01,
                String.format(Locale.ROOT, "aucun glissement imposé à l'accroche : orbite à %.2f° de la face, tangage %.2f°", ecart, cam.camPitch()));
            verifie(mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "caméra d'épaule au mur");
            verifie(MurCamera.actif && MurCamera.face() == f && MurCamera.roulis() > 5.0,
                String.format(Locale.ROOT, "la caméra rejoint le repère de la paroi : roulis de %.1f° en cours", MurCamera.roulis()));
            shot("mur_accroche");
        }, "capture de l'accroche");
        attendre(mc -> MurCamera.roulis() > 89.9, 60, "la paroi est devenue le sol (roulis de 90°)");

        // vitesses : orbite face au mur, « avant » monte tout droit
        pas(2, mc -> { orbite(f, 0, 0); tenir(Touche.AVANT); mesure(mc); }, "montée en marche");
        pendant(20, mc -> {
            double v = vitesse(mc);
            verifie(Math.abs(v - 3.2) <= 0.16 && Math.abs(mc.player.getX() - mx) < 1e-6 && mc.player.getY() > my,
                String.format(Locale.ROOT, "marche : %.3f blocs/s vers le haut (3,2 ± 5 %%)", v));
        }, "20 ticks de marche");
        pas(2, mc -> tenir(Touche.AVANT), "montée");
        pas(3, mc -> shot("mur_monter"), "capture : monter");
        pas(1, mc -> { tenir(Touche.AVANT, Touche.COURSE); mesure(mc); }, "montée avec la touche de sprint");
        pendant(15, mc -> {
            double v = vitesse(mc);
            verifie(Math.abs(v - 5.6) <= 0.28, String.format(Locale.ROOT, "course : %.3f blocs/s (5,6 ± 5 %%)", v));
        }, "15 ticks de course");
        pas(2, mc -> { RECUS.clear(); tenir(Touche.AVANT); mesure(mc); }, "montée jusqu'au sommet (sous l'auvent)");
        pendant(45, mc -> verifie(Math.abs(mc.player.getY() - (oy + 12 - MurClient.HAUTEUR)) < 1e-3 && MurClient.phase() == MurClient.Phase.MARCHE
            && RECUS.isEmpty(),
            String.format(Locale.ROOT, "sommet sous un auvent : arrêt au bord, y %+.3f (attendu %+.3f), toujours au mur, rien d'envoyé (%d message)",
                mc.player.getY() - oy, 12 - MurClient.HAUTEUR, RECUS.size())),
            "poussée contre le sommet");

        // descendre : le tangage de la caméra ne change pas le sens des touches (55° d'écart, touche tenue)
        pas(2, mc -> { orbite(f, 0, -25); tenir(Touche.ARRIERE); mesure(mc); }, "descente");
        pas(3, mc -> orbite(f, 0, 30), "la caméra s'incline (touche tenue)");
        pas(8, mc -> shot("mur_descendre"), "capture : descendre");
        pendant(30, mc -> {
            double v = vitesse(mc);
            verifie(mc.player.getY() < my - 3 && Math.abs(mc.player.getX() - mx) < 1e-6 && Math.abs(v - 3.2) <= 0.16,
                String.format(Locale.ROOT, "« arrière » descend tout droit, quel que soit le tangage : %.3f blocs/s, y %+.2f",
                    v, mc.player.getY() - my));
        }, "30 ticks de descente");

        // de côté
        pas(2, mc -> { orbite(f, 0, 0); tenir(Touche.DROITE); mesure(mc); }, "pas à droite");
        pendant(10, mc -> verifie((mc.player.getX() - mx) * f.tx > 1.4 && Math.abs(mc.player.getY() - my) < 1e-6,
            String.format(Locale.ROOT, "« droite » va à droite sur le mur : %+.2f sur x", mc.player.getX() - mx)), "10 ticks à droite");
        pas(2, mc -> { tenir(Touche.GAUCHE); mesure(mc); }, "pas à gauche");
        pendant(10, mc -> verifie((mc.player.getX() - mx) * f.tx < -1.4 && Math.abs(mc.player.getY() - my) < 1e-6,
            String.format(Locale.ROOT, "« gauche » va à gauche : %+.2f sur x", mc.player.getX() - mx)), "10 ticks à gauche");

        // cinq secondes de marche en boucle : haut, gauche, bas, droite
        double[] depart = new double[3];
        pas(2, mc -> {
            depart[0] = mc.player.getX();
            depart[1] = mc.player.getY();
            depart[2] = mc.player.getZ();
            tenir(Touche.AVANT);
            mesure(mc);
        }, "boucle de 5 s : haut");
        pendant(25, mc -> { tenir(Touche.GAUCHE); mesure(mc); }, "haut", false);
        pendant(25, mc -> { tenir(Touche.ARRIERE); mesure(mc); }, "gauche", false);
        pendant(25, mc -> { tenir(Touche.DROITE); mesure(mc); }, "bas", false);
        pendant(25, mc -> {
            LocalPlayer p = mc.player;
            double paroi = Math.abs(p.getZ() - (oz + 6));
            verifie(Math.abs(paroi - MurClient.DISTANCE) <= 0.01,
                String.format(Locale.ROOT, "après 5 s de marche, P à %.4f de la paroi (0,9 ± 0,01)", paroi));
            verifie(Math.abs(p.getX() - depart[0]) < 0.01 && Math.abs(p.getY() - depart[1]) < 0.01,
                String.format(Locale.ROOT, "boucle fermée : écart %.4f sur x, %.4f sur y", p.getX() - depart[0], p.getY() - depart[1]));
            verifie(boiteVaut(p, f), "boîte toujours juste après la marche");
        }, "droite");

        // l'autre joueur : son état vient du serveur, sa boîte suit
        pas(2, mc -> {
            verifie(voisin != null && cube(voisin), "mannequin en pose de nage : cube de 0,6 avant son état");
            if (voisin != null) FILE.add(etat(voisin.getId(), "N"));
        }, "état du voisin diffusé par le serveur");
        pas(10, mc -> verifie(voisin != null && boiteVaut(voisin, f) && MurClient.auMur(voisin),
            "voisin immobile : boîte couchée dès l'arrivée de son état"), "bilan du voisin");
        // le voisin tourne un coin : son corps tourne vers la nouvelle face, 20° par tick au plus, cube pendant le virage
        pas(1, mc -> {
            suiviVoisin = true;
            cubeVoisinVu = false;
            lacetVoisinAvant = Float.NaN;
            pasVoisinMax = 0f;
            if (voisin != null) FILE.add(etat(voisin.getId(), "W"));
        }, "le voisin passe sur la face W (virage)");
        pas(10, mc -> {
            float l = voisin == null ? Float.NaN : MurClient.lacetRendu(voisin.getId());
            verifie(voisin != null && Math.abs(l - MurFace.W.lacet) < 1e-3 && pasVoisinMax > 5f && pasVoisinMax <= 20.001f
                && cubeVoisinVu && boiteVaut(voisin, MurFace.W),
                String.format(Locale.ROOT, "virage du voisin : lacet rendu %.1f° (attendu %.1f), au plus %.1f° par tick, cube pendant le virage (%s), boîte couchée sur W à la fin",
                    l, MurFace.W.lacet, pasVoisinMax, cubeVoisinVu));
            if (voisin != null) FILE.add(etat(voisin.getId(), "N"));
        }, "bilan du virage du voisin, retour sur N");
        pas(10, mc -> {
            suiviVoisin = false;
            verifie(voisin != null && boiteVaut(voisin, f), "voisin revenu sur N : boîte couchée sur N");
        }, "bilan du retour du voisin");

        // touches continues : « avant » suit la visée sur la paroi au degré près (10° ne retombe pas à la verticale, 60°
        // reste 60°) ; puis le long du mur (90°) : à l'horizontale
        pas(2, mc -> { orbite(f, 0, 0); tenir(Touche.ARRIERE); mesure(mc); }, "descente vers le bas du mur");
        pendant(25, mc -> { }, "25 ticks de descente");
        pas(2, mc -> { orbite(f, 10, 0); tenir(Touche.AVANT); mesure(mc); }, "regard tourné de 10°");
        pendant(10, mc -> {
            double du = (mc.player.getX() - mx) * f.tx, dv = mc.player.getY() - my;
            double angle = Math.toDegrees(Math.atan2(du, dv));
            verifie(Math.abs(angle - 10.0) < 0.5,
                String.format(Locale.ROOT, "aucun cran : regard tourné de 10°, marche à %.2f° de la verticale", angle));
        }, "10 ticks à 10°");
        pas(2, mc -> { orbite(f, 60, -10); tenir(Touche.AVANT); mesure(mc); }, "de côté");
        pas(4, mc -> shot("mur_cote"), "capture : de côté");
        pendant(10, mc -> {
            double du = (mc.player.getX() - mx) * f.tx, dv = mc.player.getY() - my;
            double angle = Math.toDegrees(Math.atan2(du, dv));
            verifie(du > 1.2 && Math.abs(angle - 60.0) < 0.5,
                String.format(Locale.ROOT, "de côté, « avant » suit la visée : %+.2f le long du mur, %+.2f en hauteur (%.2f° de la verticale, visée à 60)",
                    du, dv, angle));
        }, "10 ticks de côté");
        pas(2, mc -> { reponseFace = "refus"; RECUS.clear(); orbite(f, 90, 0); tenir(Touche.AVANT); mesure(mc); }, "le long du mur (le serveur refusera le coin)");
        pas(4, mc -> shot("mur_long"), "capture : le long");
        pendant(50, mc -> {
            verifie(Math.abs(mc.player.getX() - (ox - 4 + MurClient.DEMI)) < 1e-3 && Math.abs(mc.player.getY() - my) < 1e-6
                && MurClient.phase() == MurClient.Phase.MARCHE,
                String.format(Locale.ROOT, "arrêt au bord du mur : x %+.3f (attendu %+.3f), toujours au mur", mc.player.getX() - ox, -4 + MurClient.DEMI));
            JsonObject m = RECUS.isEmpty() ? new JsonObject() : RECUS.get(0);
            verifie(RECUS.size() == 1 && cles(m, "v", "a", "f", "n") && "face".equals(action(m)) && "W".equals(m.get("f").getAsString())
                && MurClient.faceTenue() == f && MurCamera.face() == f,
                "refus de face : une seule demande (" + m + "), toujours au mur sur la face N, sans lâcher");
        }, "poussée contre le bord");
        // annulation : touche relâchée avant l'accord ; l'accord arrivé en retard est ignoré
        pas(1, mc -> { TENUES.clear(); reponseFace = "muet"; RECUS.clear(); }, "touches relâchées, le serveur se tait aux faces");
        pas(3, mc -> tenir(Touche.AVANT), "nouvelle poussée contre le bord");
        attendre(mc -> !RECUS.isEmpty(), 10, "demande de face");
        pas(0, mc -> TENUES.clear(), "touche relâchée avant l'accord");
        attendre(mc -> RECUS.size() >= 2, 10, "annulation envoyée");
        int[] demande = new int[1];
        pas(0, mc -> {
            JsonObject a = RECUS.get(0), b = RECUS.get(1);
            demande[0] = a.get("n").getAsInt();
            verifie("face".equals(action(a)) && "W".equals(a.get("f").getAsString()) && "face".equals(action(b))
                && "N".equals(b.get("f").getAsString()) && b.get("n").getAsInt() == demande[0] + 1,
                "annulation : l'ancienne face est redemandée avec le numéro suivant (" + a + " puis " + b + ")");
            FILE.add(etat(mc.player.getId(), "W", demande[0]));
        }, "l'accord de la première demande arrive en retard");
        pas(3, mc -> verifie(MurClient.phase() == MurClient.Phase.MARCHE && MurClient.faceTenue() == f && RECUS.size() == 2
            && Math.abs(mc.player.getX() - (ox - 4 + MurClient.DEMI)) < 1e-3,
            "accord en retard ignoré : toujours sur N, au bord, rien d'envoyé"), "bilan de l'annulation");
        // abandon : poussée tenue, aucun accord en une seconde
        pas(1, mc -> { RECUS.clear(); tenir(Touche.AVANT); }, "poussée tenue, le serveur se tait");
        pas(30, mc -> {
            boolean ok = RECUS.size() == 2 && "W".equals(RECUS.get(0).get("f").getAsString()) && "N".equals(RECUS.get(1).get("f").getAsString());
            verifie(ok && MurClient.phase() == MurClient.Phase.MARCHE && MurClient.faceTenue() == f
                && Math.abs(mc.player.getX() - (ox - 4 + MurClient.DEMI)) < 1e-3,
                "sans accord après 1 s : abandon, l'ancienne face redemandée, toujours au bord, plus de demande (" + RECUS.size() + " messages)");
            TENUES.clear();
            reponseFace = "etat";
        }, "bilan de l'abandon");

        // regard levé loin de la paroi : la caméra passerait derrière la paroi, l'anti-obstacle de la caméra d'épaule la
        // retient devant (comme le sol retient la caméra quand on regarde le ciel)
        pas(2, mc -> { orbite(f, 0, 0); tenir(Touche.GAUCHE); mesure(mc); }, "retour vers le milieu");
        pendant(12, mc -> { }, "12 ticks");
        pas(2, mc -> orbite(f, 0, -70), "regard levé loin de la paroi");
        pas(14, mc -> {
            Vec3 pos = mc.gameRenderer.mainCamera().position();
            double devant = (f.surNormale(pos.x, pos.z) - (oz + 6)) * f.sens();
            verifie(devant > 0.1, String.format(Locale.ROOT, "regard levé loin de la paroi : la caméra reste devant le mur (à %.2f)", devant));
            shot("mur_dos");
        }, "capture : regard levé loin de la paroi");

        // boîtes de collision affichées (F3 + B), puis la vue de côté par une caméra détachée
        pas(2, mc -> {
            orbite(f, 40, 25);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
        }, "boîtes de collision affichées");
        pas(14, mc -> shot("mur_boites"), "capture : boîtes");
        // le mode photo pose sa caméra libre à trois blocs derrière le regard du joueur : regard vers l'est, le long de
        // la paroi, elle voit le corps de profil
        pas(2, mc -> orbite(f, -90, 8), "regard le long de la paroi");
        pas(8, mc -> PhotoMode.INSTANCE.begin(mc), "caméra libre (mode photo), vue de côté");
        pas(14, mc -> shot("mur_second_boites"), "capture : vue de côté, boîtes affichées");
        pas(2, mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER), "boîtes masquées");
        pas(8, mc -> shot("mur_second"), "capture : vue de côté");
        pas(2, mc -> {
            PhotoMode.INSTANCE.end(mc);
            orbite(f, 0, 0);
        }, "retour à la caméra du joueur");

        // une correction de position du serveur pendant la marche
        pas(6, mc -> mc.player.connection.sendCommand("tp @s ~ ~ ~"), "le serveur repose le joueur (correction)");
        pas(8, mc -> verifie(MurClient.corrections() == 1 && MurClient.phase() == MurClient.Phase.MARCHE,
            "correction de position comptée : " + MurClient.corrections()), "bilan de la correction");

        // sortie par Espace
        pas(2, mc -> {
            verifie(!poseTombee && mc.player.getPose() == Pose.SWIMMING, "pose de nage tenue pendant toute la marche");
            RECUS.clear();
            tenir(Touche.SAUT);
        }, "Espace");
        pas(2, mc -> TENUES.clear(), "Espace relâchée");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        pas(0, mc -> {
            JsonObject m = dernier();
            verifie(RECUS.size() == 1 && cles(m, "v", "a", "c") && "lache".equals(action(m)) && m.get("c").getAsInt() == 1,
                "Espace lâche, format et compte des corrections : " + m);
            double paroi = Math.abs(mc.player.getZ() - (oz + 6));
            verifie(Math.abs(paroi - 0.3) < 0.01, String.format(Locale.ROOT, "sortie contre le mur, à %.3f de la paroi (0,3)", paroi));
        }, "bilan de la sortie");
        attendre(mc -> mc.player.onGround(), 60, "retombé au sol");
        pas(4, mc -> verifie(mc.player.getPose() == Pose.STANDING && debout(mc.player) && !MurClient.auMur(mc.player),
            "après la sortie : pose debout, boîte de 0,6 × 1,8 retrouvée"), "bilan debout");
        pas(2, mc -> {
            if (voisin != null) FILE.add(etat(voisin.getId(), "-"));
        }, "le voisin quitte le mur");
        pas(8, mc -> verifie(voisin != null && cube(voisin) && !MurClient.auMur(voisin) && !MurClient.actif,
            "voisin lâché : boîte du jeu retrouvée, plus aucun état"), "bilan du voisin");
    }

    /** Sous un obstacle : on s'arrête dessous, sans correction du serveur ; la touche de course de chakra lâche. */
    private static void plafond() {
        accrocher(MurFace.N, -2.5);
        pas(2, mc -> { orbite(MurFace.N, 0, 0); tenir(Touche.AVANT); mesure(mc); }, "montée sous l'obstacle");
        pendant(60, mc -> verifie(Math.abs(mc.player.getY() - (oy + 8 - MurClient.HAUTEUR)) < 1e-3 && MurClient.phase() == MurClient.Phase.MARCHE
            && MurClient.corrections() == 0,
            String.format(Locale.ROOT, "arrêt sous un plafond : y %+.3f (attendu %+.3f), toujours au mur, %d correction",
                mc.player.getY() - oy, 8 - MurClient.HAUTEUR, MurClient.corrections())),
            "poussée contre l'obstacle", false);
        pas(2, mc -> {
            RECUS.clear();
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(HudKeybinds.COURSE_CHAKRA));
        }, "touche de course de chakra");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        pas(0, mc -> verifie(RECUS.size() == 1 && "lache".equals(action(dernier())) && !NarutoRun.INSTANCE.isActive(),
            "la touche de course lâche le mur sans lancer la course"), "bilan de la touche de course");
        pas(20, mc -> {
            verifie(RECUS.size() == 1 && !MurClient.corpsPris && MurClient.phase() == MurClient.Phase.REPOS,
                "sortie avec AVANT tenue : aucune nouvelle demande (" + RECUS.size() + " message)");
            TENUES.clear();
        }, "bilan AVANT tenue");
        attendre(mc -> mc.player.onGround(), 80, "retombé au sol");
    }

    /** Nord : refus reçu au mur ; sud : le serveur lâche ; est : pied du mur ; ouest : permis retiré. */
    private static void autresFaces() {
        accrocher(MurFace.N, 2.5);
        pas(4, mc -> { RECUS.clear(); FILE.add("{\"v\":1,\"t\":\"refus\",\"m\":\"Plus de mur ici.\"}"); }, "refus reçu au mur");
        pas(3, mc -> verifie(!MurClient.corpsPris && RECUS.size() == 1 && "lache".equals(action(dernier())),
            "refus reçu au mur : le mod revient debout et rend son compte"), "bilan du refus au mur");
        attendre(mc -> mc.player.onGround(), 40, "retombé au sol");

        accrocher(MurFace.S, 0.5);
        pas(4, mc -> { RECUS.clear(); FILE.add(etat(mc.player.getId(), "-")); }, "le serveur lâche le joueur");
        pas(3, mc -> verifie(!MurClient.corpsPris && MurClient.phase() == MurClient.Phase.REPOS && RECUS.size() == 1
            && "lache".equals(action(dernier())), "lâché par le serveur : le mod sort et rend son compte"),
            "bilan du lâcher serveur");
        attendre(mc -> mc.player.onGround(), 40, "retombé au sol");
        pas(4, mc -> verifie(mc.player.getPose() == Pose.STANDING && debout(mc.player), "lâché par le serveur : debout"), "bilan debout");

        accrocher(MurFace.E, 7.5);
        pas(2, mc -> { orbite(MurFace.E, 0, 0); tenir(Touche.AVANT); mesure(mc); }, "montée de deux blocs");
        // descendre comme un joueur : demi-tour de la caméra, regard un peu levé loin de la paroi, Z ; jeu ralenti
        pendant(13, mc -> {
            RECUS.clear();
            orbite(MurFace.E, 180, -20);
            tenir(Touche.AVANT);
            mesure(mc);
            lent(mc, true);
        }, "montée", false);
        attendre(mc -> MurClient.phase() == MurClient.Phase.SORTIE, 80, "pied du mur : la sortie part");
        pas(0, mc -> verifie(RECUS.isEmpty(), "pied du mur : rien d'envoyé au début de la sortie (" + RECUS.size() + " message)"), "début de la sortie");
        pas(2, mc -> shot("mur_pied"), "capture : pied du mur");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "pied du mur : sortie jouée");
        pas(0, mc -> bilanPied("pied du mur"), "bilan du pied du mur");
        attendre(mc -> mc.player.onGround(), 40, "relevé au sol");
        pas(4, mc -> verifie(mc.player.getPose() == Pose.STANDING && debout(mc.player) && Math.abs(mc.player.getY() - oy) < 1e-3
            && RECUS.size() == 1,
            "pied du mur : debout sur le sol, aucun lache en trop"), "bilan debout");

        // descendre en visant le haut du mur (« Suit la paroi », S) : le regard reste face au mur pendant toute la sortie,
        // le corps rendu doit pourtant se redresser dos au mur, sans faire demi-tour d'une image à l'autre
        accrocher(MurFace.E, 7.5);
        pas(2, mc -> { orbite(MurFace.E, 0, 0); tenir(Touche.AVANT); mesure(mc); }, "montée de deux blocs (regard au haut du mur)");
        pendant(13, mc -> {
            RECUS.clear();
            tenir(Touche.ARRIERE);
            mesure(mc);
            lent(mc, true);
        }, "montée", false);
        attendre(mc -> MurClient.phase() == MurClient.Phase.SORTIE, 80, "pied du mur, regard au haut du mur : la sortie part");
        pas(2, mc -> shot("mur_pied_haut"), "capture : pied du mur, regard au haut du mur");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "pied du mur, regard au haut du mur : sortie jouée");
        pas(0, mc -> bilanPied("pied du mur, regard au haut du mur"), "bilan du pied du mur, regard au haut du mur");
        attendre(mc -> mc.player.onGround(), 40, "relevé au sol");
        pas(4, mc -> verifie(mc.player.getPose() == Pose.STANDING && debout(mc.player) && Math.abs(mc.player.getY() - oy) < 1e-3
            && RECUS.size() == 1,
            "pied du mur, regard au haut du mur : debout sur le sol, aucun lache en trop"), "bilan debout");

        accrocher(MurFace.W, 7.5);
        pas(4, mc -> {
            RECUS.clear();
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":false}");
            verifie(!MurClient.permis() && !MurClient.corpsPris && !MurClient.auMur(mc.player) && RECUS.size() == 1,
                "permis retiré : lâché tout de suite, boîte du jeu");
        }, "permis retiré au mur");
        attendre(mc -> mc.player.onGround(), 40, "retombé au sol");
        pas(4, mc -> verifie(mc.player.getPose() == Pose.STANDING && debout(mc.player), "permis retiré : debout"), "bilan debout");
        pas(4, mc -> { RECUS.clear(); orbite(MurFace.W, 0, 0); tenir(Touche.AVANT); }, "poussée sans permis");
        pas(14, mc -> {
            verifie(RECUS.isEmpty() && !MurClient.corpsPris, "permis retiré : plus rien n'est envoyé");
            TENUES.clear();
        }, "bilan sans permis");
    }

    /**
     * Bilan d'une sortie par le pied de la face est : au sol, à 0,9 du mur, un seul {@code lache} à la fin ; la dernière
     * pose rendue debout dos au mur, et le cap rendu qui ne recule jamais d'une image à l'autre. La pose est lue sur les
     * images de la sortie : après elle, le corps du jeu rejoint le regard, comme au sol.
     */
    private static void bilanPied(String quoi) {
        // à la fin de la sortie (la touche reste tenue : debout, le joueur s'éloigne ensuite du mur)
        double[] a = finSortie;
        double paroi = a[0] - (ox + 5);
        double dos = Math.abs(Mth.wrapDegrees(lacetDernierePose - (MurFace.E.lacet + 180f)));
        verifie(RECUS.size() == 1 && "lache".equals(action(dernier())) && Math.abs(a[1] - oy) < 1e-3 && Math.abs(paroi - 0.9) < 1e-3,
            String.format(Locale.ROOT, "%s : au sol (y %+.3f), à %.3f du mur (0,9), un seul lache, à la fin", quoi, a[1] - oy, paroi));
        verifie(posesSortie >= 4 && dos < 20.0,
            String.format(Locale.ROOT, "%s : dernière pose dos au mur (corps à %.1f° de la normale, %d poses)", quoi, dos, posesSortie));
        verifie(posesSortie >= 4 && reculsSortie == 0 && pasCapMax < 30.0,
            String.format(Locale.ROOT, "%s : le cap rendu ne recule pas (%d recul(s), pas au plus %.2f°)", quoi, reculsSortie, pasCapMax));
        TENUES.clear();
        lent(Minecraft.getInstance(), false);
    }

    /**
     * <b>Passages</b> (étape C1a, temps 2, cahier §2 et §3), le serveur simulé donnant son accord : coin rentrant contre
     * la cloison puis coin sortant à son bout (deux coins enchaînés, trois images de chaque virage, jeu ralenti), tour
     * complet du tronc de 1 × 1, sommet libre de la face sud. Après chaque virage : {@code P} à 0,9 de la nouvelle paroi,
     * boîte couchée sur elle, cap conservé, caméra sur la nouvelle face, aucun {@code lache}.
     */
    private static void passages() {
        MurFace n = MurFace.N, w = MurFace.W;
        // le permis est rendu touches relâchées : la série d'avant finit sur « permis retiré », et la garde de C0 exige
        // de cesser de pousser, permis en main, avant une nouvelle accroche
        pas(1, mc -> {
            TENUES.clear();
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":true}");
            reponseFace = "etat";
        }, "permis rendu, le serveur accepte les coins");

        // ---- coin rentrant (la cloison barre la marche vers l'est), puis coin sortant au bout de la cloison
        accrocher(n, 1.5);
        pas(1, mc -> { orbiteLibre(n, 0, 0); tenir(Touche.AVANT); mesure(mc); }, "montée d'un bloc");
        pendant(6, mc -> { }, "6 ticks de montée", false);
        pas(1, mc -> { RECUS.clear(); ARRIVEES.clear(); lent(mc, true); tenir(Touche.GAUCHE); }, "Q : vers la cloison, jeu ralenti");
        attendre(mc -> MurClient.phase() == MurClient.Phase.VIRAGE, 80, "coin rentrant : le virage part");
        pas(0, mc -> {
            JsonObject m = RECUS.isEmpty() ? new JsonObject() : RECUS.get(0);
            verifie(RECUS.size() == 1 && "face".equals(action(m)) && "W".equals(m.get("f").getAsString())
                && m.get("n").getAsInt() == MurClient.numero() && MurClient.faceTenue() == w && MurCamera.face() == w,
                "coin rentrant : une demande de face W (" + m + "), accord reçu, la caméra change de face au départ");
        }, "départ du coin rentrant");
        pas(1, mc -> shot("mur_coin_rentrant_1"), "coin rentrant, image 1");
        pas(1, mc -> shot("mur_coin_rentrant_2"), "coin rentrant, image 2");
        pas(1, mc -> shot("mur_coin_rentrant_3"), "coin rentrant, image 3");
        attendre(mc -> MurClient.phase() == MurClient.Phase.MARCHE, 10, "coin rentrant joué");
        pas(0, mc -> {
            // la position est celle de la fin du virage (la touche reste tenue : la marche a pu reprendre depuis)
            double[] a = arrivee();
            verifie(Math.abs(a[0] - (ox + 3 - MurClient.DISTANCE)) < 1e-6 && Math.abs(a[2] - (oz + 6 - MurClient.DEMI)) < 1e-6
                && boiteVaut(mc.player, w),
                String.format(Locale.ROOT, "coin rentrant : P à %.4f de la cloison (0,9), à %.4f du mur quitté (0,3), boîte couchée sur W",
                    (ox + 3) - a[0], (oz + 6) - a[2]));
            mesure(mc);
        }, "arrivée du coin rentrant");
        pendant(3, mc -> {
            verifie(mc.player.getZ() < mz - 0.4 && Math.abs(mc.player.getX() - mx) < 1e-6 && Math.abs(mc.player.getY() - my) < 1e-6,
                String.format(Locale.ROOT, "cap conservé : Q (vers l'est sur N) longe maintenant la cloison vers le nord, %+.2f sur z", mc.player.getZ() - mz));
            relacherAuVirage = true;   // Q est relâchée à la fin du coin sortant qui suit
        }, "3 ticks le long de la cloison", false);
        attendre(mc -> MurClient.phase() == MurClient.Phase.VIRAGE, 60, "coin sortant au bout de la cloison : le virage part");
        pas(2, mc -> shot("mur_coin_sortant_1"), "coin sortant, image 1");
        pas(3, mc -> shot("mur_coin_sortant_2"), "coin sortant, image 2");
        pas(3, mc -> shot("mur_coin_sortant_3"), "coin sortant, image 3");
        attendre(mc -> MurClient.phase() == MurClient.Phase.MARCHE, 12, "coin sortant joué");
        pas(0, mc -> {
            TENUES.clear();
            double[] a = arrivee();
            verifie(MurClient.faceTenue() == n && Math.abs(a[0] - (ox + 3 + MurClient.DEMI)) < 1e-6
                && Math.abs(a[2] - (oz + 3 - MurClient.DISTANCE)) < 1e-6 && boiteVaut(mc.player, n) && MurCamera.face() == n,
                String.format(Locale.ROOT, "coin sortant : sur la face N de la cloison, P à %.4f de sa paroi (0,9), bord arrière à %.4f de l'arête, boîte couchée",
                    (oz + 3) - a[2], a[0] - MurClient.DEMI - (ox + 3)));
            verifie(RECUS.size() == 2 && "N".equals(RECUS.get(1).get("f").getAsString()) && ARRIVEES.size() == 2,
                "deux coins enchaînés : deux demandes (W puis N), aucun lache, deux virages joués");
            lent(mc, false);
        }, "arrivée du coin sortant");
        sortieEspace();

        // ---- tour complet du tronc de 1 × 1 : quatre coins sortants de suite, sans battement
        accrocherEn(n, () -> new double[] {ox + 9.5, oz + 1 - 0.3}, "tronc");
        pas(1, mc -> { orbiteLibre(n, 0, 0); tenir(Touche.AVANT); mesure(mc); }, "montée d'un bloc et demi");
        pendant(10, mc -> { }, "10 ticks de montée", false);
        pas(1, mc -> { RECUS.clear(); ARRIVEES.clear(); tenir(Touche.DROITE); }, "D : le tour du tronc");
        attendre(mc -> ARRIVEES.size() >= 4, 200, "quatre coins joués");
        pas(0, mc -> {
            TENUES.clear();
            MurFace[] attendu = {MurFace.W, MurFace.S, MurFace.E, MurFace.N};
            StringBuilder vu = new StringBuilder();
            boolean ok = ARRIVEES.size() == 4;
            for (int i = 0; i < ARRIVEES.size(); i++) {
                Object[] a = ARRIVEES.get(i);
                MurFace fa = (MurFace) a[0];
                double px = (Double) a[1], pz = (Double) a[3];
                double plan = switch (fa) {
                    case N -> oz + 1;
                    case S -> oz + 2;
                    case W -> ox + 9;
                    case E -> ox + 10;
                };
                double dist = (fa.surNormale(px, pz) - plan) * fa.sens();
                vu.append(String.format(Locale.ROOT, " %s à %.4f", fa.code, dist));
                ok &= i < attendu.length && fa == attendu[i] && Math.abs(dist - MurClient.DISTANCE) < 1e-6;
            }
            int faces = 0;
            for (JsonObject m : RECUS) if ("face".equals(action(m))) faces++;
            verifie(ok, "tour du tronc : W, S, E, N, P à 0,9 de chaque paroi :" + vu);
            verifie(faces == 4 && RECUS.size() == 4 && MurClient.faceTenue() == n && boiteVaut(mc.player, n) && MurCamera.face() == n,
                "tour du tronc sans battement : quatre demandes de face, rien d'autre (" + RECUS.size() + " messages), revenu sur N");
        }, "bilan du tour du tronc");
        // lâcher immédiat après le tour : le lacet rendu s'est cumulé d'un tour entier, le corps doit se relever sans tourner
        pas(1, mc -> {
            lacetLache = MurClient.lacetRendu(mc.player.getId());
            ecartLacetLache = 0f;
            suiviLacetLache = true;
            FILE.add(etat(mc.player.getId(), "-"));
        }, "le serveur lâche le joueur après le tour du tronc");
        attendre(mc -> !suiviLacetLache, 20, "corps relevé après le lâcher");
        pas(0, mc -> verifie(Math.abs(Mth.wrapDegrees(lacetLache - MurFace.N.lacet)) < 1e-3 && ecartLacetLache < 1e-3,
            String.format(Locale.ROOT, "lâcher après le tour du tronc : lacet rendu cumulé à %.1f°, le corps se relève sans tourner (écart %.2f°)",
                lacetLache, ecartLacetLache)), "bilan du lâcher après le tour");
        attendre(mc -> mc.player.onGround(), 80, "retombé au sol");

        // ---- sommet libre (face sud) : debout sur le toit, un seul lache, au début de la sortie
        accrocher(MurFace.S, 0.5);
        pas(1, mc -> { RECUS.clear(); orbiteLibre(MurFace.S, 0, 10); tenir(Touche.AVANT, Touche.COURSE); }, "montée en course vers le sommet");
        attendre(mc -> mc.player.getY() > oy + 9, 80, "près du sommet");
        pas(0, mc -> lent(mc, true), "jeu ralenti");
        attendre(mc -> MurClient.phase() == MurClient.Phase.SORTIE, 40, "sommet : la sortie part");
        pas(0, mc -> verifie(RECUS.size() == 1 && "lache".equals(action(dernier())) && MurCamera.face() == null && MurCamera.actif,
            "sommet : lache envoyé au début de la sortie, la caméra défait son roulis (" + RECUS.size() + " message)"), "début de la sortie");
        pas(3, mc -> shot("mur_sommet"), "capture : sommet");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sommet : sortie jouée");
        pas(0, mc -> {
            TENUES.clear();
            // la dernière pose rendue de la sortie (après elle, le corps du jeu rejoint le regard, comme au sol)
            double face = Math.abs(Mth.wrapDegrees(lacetDernierePose - MurFace.S.lacet));
            // à la fin de la sortie (Z reste tenue : le joueur marche ensuite sur le toit)
            double[] a = finSortie;
            verifie(Math.abs(a[1] - (oy + 12)) < 1e-6 && Math.abs(a[2] - (oz + 9 - 0.3)) < 1e-6 && Math.abs(a[0] - (ox + 0.5)) < 1e-6,
                String.format(Locale.ROOT, "sommet : sur le toit (y %+.3f), à %.3f derrière l'arête (0,3)", a[1] - oy, (oz + 9) - a[2]));
            verifie(posesSortie >= 4 && face < 20.0,
                String.format(Locale.ROOT, "sommet : dernière pose face au toit (corps à %.1f° de la face, %d poses)", face, posesSortie));
            verifie(posesSortie >= 4 && reculsSortie == 0 && pasCapMax < 30.0,
                String.format(Locale.ROOT, "sommet : le cap rendu ne recule pas (%d recul(s), pas au plus %.2f°)", reculsSortie, pasCapMax));
            lent(mc, false);
        }, "bilan du sommet");
        pas(10, mc -> verifie(RECUS.size() == 1 && mc.player.getPose() == Pose.STANDING && debout(mc.player) && mc.player.onGround()
            && Math.abs(mc.player.getY() - (oy + 12)) < 1e-3,
            "sommet : debout sur le toit, écho du serveur ignoré, aucun lache en trop (" + RECUS.size() + " message)"), "bilan debout sur le toit");
    }

    /** Espace au mur, puis retour au sol. */
    private static void sortieEspace() {
        pas(1, mc -> tenir(Touche.SAUT), "Espace");
        pas(2, mc -> TENUES.clear(), "Espace relâchée");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        attendre(mc -> mc.player.onGround(), 80, "retombé au sol");
    }

    /** Ralentit le jeu (4 ticks par seconde, captures des virages) ou lui rend son allure ; le client suit le serveur. */
    private static void lent(Minecraft mc, boolean oui) {
        mc.player.connection.sendCommand(oui ? "tick rate 4" : "tick rate 20");
    }

    /**
     * <b>Caméra de paroi</b> (étape C1a, cahier §1), toujours depuis le même pied de la face nord : le roulis d'accroche
     * en huit images, puis monter, longer et descendre touche tenue, en « Suit la paroi » puis en « Droite », enfin la
     * 1re personne passée en épaule et rendue.
     */
    private static void camera() {
        MurFace f = MurFace.N;
        pas(2, mc -> {
            var c = mc.player.connection;
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
            c.sendCommand(String.format(Locale.ROOT,
                "summon minecraft:mannequin %.2f %d %.2f {NoGravity:1b,immovable:1b,Rotation:[150f,0f],CustomName:\"Repere\",CustomNameVisible:1b}",
                ox - 2.5, oy, oz + 2.5));
        }, "repère : un mannequin debout au sol, nom affiché (nametag sous le roulis)");

        // ---- « Suit la paroi » : le roulis d'accroche, image par image
        // permis rendu AVANT la mise en place : après le permis retiré de la série précédente, le mod exige que les
        // touches soient relâchées, permis en main, avant une nouvelle accroche (garde de C0)
        pas(1, mc -> MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":true}"), "permis rendu");
        placer(f, 0.5);
        pas(1, mc -> {
            RebornPrefs.INSTANCE.camMurDroite = false;
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":true}");
            reponse = "etat";
            RECUS.clear();
            tenir(Touche.AVANT);
            relacherALAccroche = true;
        }, "« Suit la paroi » : poussée contre la face " + f.code);
        attendre(mc -> MurClient.corpsPris, 30, "accroche confirmée");
        pas(0, mc -> shot("mur_cam_roulis_1"), "roulis d'accroche, image 1");
        for (int i = 2; i <= 8; i++) {
            String nom = "mur_cam_roulis_" + i;
            pas(3, mc -> shot(nom), "roulis d'accroche, image " + i);
        }
        attendre(mc -> MurCamera.roulis() > 89.9, 60, "roulis terminé");
        pas(0, mc -> {
            RebornCamera cam = RebornCamera.INSTANCE;
            Camera c = mc.gameRenderer.mainCamera();
            // les huit captures figent des images : la durée n'est jugée qu'à la 1re personne, sans capture (plus bas)
            System.out.println(String.format(Locale.ROOT, "[mur] quart de tour pendant les captures : 85° atteints en %.2f s", quartAccroche));
            verifie(vitesseRoulisMax <= 105.0,
                String.format(Locale.ROOT, "roulis plafonné à 100°/s : au plus %.1f°/s sur 100 ms d'horloge", vitesseRoulisMax));
            verifie(Math.abs(Mth.wrapDegrees(cam.camYaw() - f.lacet)) < 0.01 && Math.abs(cam.camPitch()) < 0.01,
                "aucun glissement imposé : l'orbite du joueur n'a pas bougé");
            double haut = c.upVector().x() * f.nx + c.upVector().z() * f.nz;
            verifie(haut > 0.999 && c.forwardVector().y() > 0.999 && Math.abs(c.leftVector().x() * f.tx + c.leftVector().z() * f.tz) > 0.999,
                String.format(Locale.ROOT, "repère de surface : le haut de la caméra est la normale (%.4f), la visée monte le long du mur (%.4f)",
                    haut, c.forwardVector().y()));
            verifie(Math.abs(mc.player.getXRot() + 90.0) < 1.0,
                String.format(Locale.ROOT, "regard de l'entité : la visée réelle, tangage %.1f° (-90 : vers le haut du mur)", mc.player.getXRot()));
            verifie(MurClient.phase() == MurClient.Phase.MARCHE && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK,
                "au mur, en caméra d'épaule");
        }, "bilan du roulis");

        // une secousse de fin de mise à jour (séisme du staff ; KO et faim passent par le même setRotation) garde le roulis
        pas(1, mc -> seisme("{\"t\":\"seisme\",\"f\":10,\"s\":1}"), "séisme du staff au mur");
        pas(6, mc -> {
            Camera c = mc.gameRenderer.mainCamera();
            double haut = c.upVector().x() * f.nx + c.upVector().z() * f.nz;
            double ecart = Math.toDegrees(Math.acos(Mth.clamp(c.forwardVector().dot(MurCamera.VISEE), -1f, 1f)));
            verifie(haut > 0.98 && ecart > 0.01 && ecart < 5.0,
                String.format(Locale.ROOT, "séisme au mur : la vue tremble autour de la visée de paroi (%.2f°), roulis gardé (haut · n = %.4f)",
                    ecart, haut));
        }, "bilan du séisme");
        attendre(mc -> fr.reborn.hud.staff.EffetsStaff.secousse() == null, 40, "séisme terminé");

        // monter, longer, descendre : Z seule, la souris choisit le sens (orbite relative à la paroi)
        pas(2, mc -> { orbite(f, 0, 10); tenir(Touche.AVANT); mesure(mc); }, "suit : monter (Z, visée vers le haut du mur)");
        pendant(25, mc -> {
            RebornCamera cam = RebornCamera.INSTANCE;
            Vec3 pos = mc.gameRenderer.mainCamera().position();
            double d = Math.sqrt(pos.distanceToSqr(MurCamera.pivotX, MurCamera.pivotY, MurCamera.pivotZ));
            double regle = Math.sqrt(cam.distance() * cam.distance() + cam.rightOffset() * cam.rightOffset() + cam.upOffset() * cam.upOffset());
            verifie(Math.abs(mc.player.getX() - mx) < 1e-6 && mc.player.getY() > my + 3.5,
                String.format(Locale.ROOT, "suit : Z monte tout droit, %+.2f en hauteur", mc.player.getY() - my));
            verifie(Math.abs(d - regle) < 0.05,
                String.format(Locale.ROOT, "cadrage du joueur rendu : caméra à %.3f du pivot (réglage : %.3f)", d, regle));
            double paroi = (f.surNormale(MurCamera.pivotX, MurCamera.pivotZ) - (oz + 6)) * f.sens();
            verifie(Math.abs(paroi - 1.62) < 0.02, String.format(Locale.ROOT, "pivot aux yeux du corps couché : à %.3f de la paroi (1,62)", paroi));
        }, "25 ticks de montée", false);
        pas(1, mc -> shot("mur_cam_suit_monter"), "capture : suit, monter");
        pas(1, mc -> { orbite(f, 90, 10); tenir(Touche.AVANT); mesure(mc); }, "suit : longer (Z, visée tournée de 90°)");
        pendant(10, mc -> {
            Vec3 regard = mc.player.getLookAngle();
            verifie((mc.player.getX() - mx) * f.tx > 1.4 && Math.abs(mc.player.getY() - my) < 1e-6,
                String.format(Locale.ROOT, "suit : Z longe le mur, %+.2f le long de la paroi", (mc.player.getX() - mx) * f.tx));
            verifie(regard.x * f.tx + regard.z * f.tz > 0.97 && Math.abs(regard.y) < 0.02,
                String.format(Locale.ROOT, "regard de l'entité : le long du mur (%.3f sur la tangente, %.3f en hauteur)",
                    regard.x * f.tx + regard.z * f.tz, regard.y));
        }, "10 ticks le long du mur", false);
        pas(1, mc -> shot("mur_cam_suit_longer"), "capture : suit, longer");
        pas(1, mc -> { orbite(f, 180, 10); tenir(Touche.AVANT); mesure(mc); }, "suit : descendre (Z, demi-tour)");
        pendant(12, mc -> verifie(Math.abs(mc.player.getX() - mx) < 1e-6 && mc.player.getY() < my - 1.7,
            String.format(Locale.ROOT, "suit : Z descend tout droit, %+.2f en hauteur", mc.player.getY() - my)), "12 ticks de descente", false);
        pas(1, mc -> shot("mur_cam_suit_descendre"), "capture : suit, descendre");

        // sortie : le roulis se défait comme il s'est fait
        pas(1, mc -> tenir(Touche.SAUT), "Espace");
        pas(2, mc -> TENUES.clear(), "Espace relâchée");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        pas(0, mc -> {
            verifie(MurCamera.actif && MurCamera.roulis() > 45.0,
                String.format(Locale.ROOT, "sortie : le roulis ne se défait pas d'un coup (%.1f° au lâcher)", MurCamera.roulis()));
            // un gel du jeu juste après le lâcher : les ticks de rattrapage passent sans image
            try { Thread.sleep(400L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "bilan du lâcher, puis un gel de 0,4 s");
        pas(0, mc -> verifie(MurCamera.actif && MurCamera.roulis() > 20.0,
            String.format(Locale.ROOT, "après le gel, le retour continue (%.1f°) : rien ne saute", MurCamera.roulis())), "bilan du gel");
        attendre(mc -> !MurCamera.actif, 80, "retour de la caméra terminé");
        pas(0, mc -> {
            // d'aplomb = aucun roulis : la gauche de la caméra reste horizontale (le tangage gardé incline le haut)
            Camera c = mc.gameRenderer.mainCamera();
            verifie(MurCamera.roulis() == 0.0 && Math.abs(c.leftVector().y()) < 1.0e-3 && c.upVector().y() > 0.5,
                String.format(Locale.ROOT, "retour terminé : caméra d'aplomb, repère du monde (gauche %.4f en hauteur)", c.leftVector().y()));
            verifie(quartRetour >= 0.8 && quartRetour <= 1.6 && vitesseRoulisMax >= 80.0 && vitesseRoulisMax <= 105.0,
                String.format(Locale.ROOT, "retour comme l'accroche : roulis sous 5° en %.2f s, au plus %.1f°/s (plafond 100, %d i/s)",
                    quartRetour, vitesseRoulisMax, mc.getFps()));
        }, "bilan du retour");
        attendre(mc -> mc.player.onGround(), 60, "retombé au sol");

        // ---- « Droite » : aucun roulis, touches fixes
        pas(2, mc -> RebornPrefs.INSTANCE.camMurDroite = true, "réglage « Droite »");
        accrocher(f, 0.5);
        pas(20, mc -> verifie(MurCamera.actif && MurCamera.roulis() == 0.0 && mc.gameRenderer.mainCamera().upVector().y() > 0.999,
            "droite : aucun roulis, la caméra reste d'aplomb"), "bilan de l'accroche en caméra droite");
        pas(2, mc -> { orbite(f, 30, 10); tenir(Touche.AVANT); mesure(mc); }, "droite : monter (Z, caméra tournée de 30°)");
        pendant(25, mc -> verifie(Math.abs(mc.player.getX() - mx) < 1e-6 && mc.player.getY() > my + 3.5,
            String.format(Locale.ROOT, "droite : Z monte tout droit malgré la caméra tournée, %+.2f en hauteur", mc.player.getY() - my)),
            "25 ticks de montée", false);
        pas(1, mc -> shot("mur_cam_droite_monter"), "capture : droite, monter");
        pas(1, mc -> { tenir(Touche.DROITE); mesure(mc); }, "droite : longer (D)");
        pendant(10, mc -> verifie((mc.player.getX() - mx) * f.tx > 1.4 && Math.abs(mc.player.getY() - my) < 1e-6,
            String.format(Locale.ROOT, "droite : D va à droite de l'écran sur la paroi, %+.2f", (mc.player.getX() - mx) * f.tx)),
            "10 ticks à droite", false);
        pas(1, mc -> shot("mur_cam_droite_longer"), "capture : droite, longer");
        pas(1, mc -> { tenir(Touche.ARRIERE); mesure(mc); }, "droite : descendre (S)");
        pendant(12, mc -> verifie(Math.abs(mc.player.getX() - mx) < 1e-6 && mc.player.getY() < my - 1.7,
            String.format(Locale.ROOT, "droite : S descend tout droit, %+.2f en hauteur", mc.player.getY() - my)), "12 ticks de descente", false);
        pas(1, mc -> shot("mur_cam_droite_descendre"), "capture : droite, descendre");
        // caméra passée de l'autre côté (dos au mur) : la droite de l'écran a changé de côté sur la paroi
        pas(2, mc -> { orbite(f, 180, 0); tenir(Touche.DROITE); mesure(mc); }, "droite : caméra dos au mur, D");
        pendant(6, mc -> verifie((mc.player.getX() - mx) * f.tx < -0.8 && MurCamera.roulis() == 0.0 && vitesseRoulisMax == 0.0,
            String.format(Locale.ROOT, "droite : dos au mur, D va toujours à droite de l'écran (%+.2f), toujours sans roulis",
                (mc.player.getX() - mx) * f.tx)), "6 ticks");
        pas(1, mc -> tenir(Touche.SAUT), "Espace");
        pas(2, mc -> TENUES.clear(), "Espace relâchée");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        attendre(mc -> !MurCamera.actif, 80, "retour de la caméra terminé");
        attendre(mc -> mc.player.onGround(), 60, "retombé au sol");

        // ---- 1re personne : passée en épaule le temps du mur, puis rendue
        pas(2, mc -> {
            RebornPrefs.INSTANCE.camMurDroite = false;
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.FIRST);
            RebornCamera.INSTANCE.tickView(mc);
        }, "1re personne");
        accrocher(f, 0.5);
        pas(4, mc -> verifie(RebornCamera.INSTANCE.mode() == RebornCamera.Mode.SHOULDER
            && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "1re personne : passée en épaule le temps du mur"), "bilan au mur");
        // aucune capture ici : la durée du quart de tour se juge à l'horloge réelle
        attendre(mc -> MurCamera.roulis() > 89.9, 60, "roulis terminé (sans capture)");
        pas(0, mc -> verifie(quartAccroche >= 0.8 && quartAccroche <= 1.6 && vitesseRoulisMax >= 80.0 && vitesseRoulisMax <= 105.0,
            String.format(Locale.ROOT, "un quart de tour dure environ une seconde : 85° atteints en %.2f s, au plus %.1f°/s (plafond 100, %d i/s)",
                quartAccroche, vitesseRoulisMax, mc.getFps())), "bilan du quart de tour");
        pas(1, mc -> tenir(Touche.SAUT), "Espace");
        pas(2, mc -> TENUES.clear(), "Espace relâchée");
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 20, "sortie jouée");
        attendre(mc -> !MurCamera.actif, 80, "retour de la caméra terminé");
        pas(2, mc -> {
            verifie(RebornCamera.INSTANCE.mode() == RebornCamera.Mode.FIRST && mc.options.getCameraType() == CameraType.FIRST_PERSON,
                "1re personne rendue après le retour de la caméra");
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            RebornCamera.INSTANCE.tickView(mc);
        }, "bilan de la 1re personne");
        attendre(mc -> mc.player.onGround(), 60, "retombé au sol");
    }

    /* ================================================================ gestes */

    /** Pied de la face {@code f}, debout contre le mur ; {@code d} : décalage depuis l'origine sur l'horizontale de la paroi. */
    /** {@code P} à la fin du dernier virage vu ({@code x, y, z}) ; NaN si aucun virage n'a été joué. */
    private static double[] arrivee() {
        if (ARRIVEES.isEmpty()) return new double[] {Double.NaN, Double.NaN, Double.NaN};
        Object[] a = ARRIVEES.get(ARRIVEES.size() - 1);
        return new double[] {(Double) a[1], (Double) a[2], (Double) a[3]};
    }

    private static double[] pied(MurFace f, double d) {
        return switch (f) {
            case N -> new double[] {ox + d, oz + 6 - 0.3};
            case S -> new double[] {ox + d, oz + 9 + 0.3};
            case E -> new double[] {ox + 5 + 0.3, oz + d};
            case W -> new double[] {ox - 4 - 0.3, oz + d};
        };
    }

    private static void placer(MurFace f, double d) {
        placerEn(f, () -> pied(f, d));
    }

    /**
     * Debout en {@code xz}, au sol, regard vers la face {@code f}. La place est lue à l'exécution de l'étape : l'origine
     * de la scène n'est connue qu'après la préparation, pas quand les étapes sont écrites.
     */
    private static void placerEn(MurFace f, Supplier<double[]> xz) {
        pas(4, mc -> {
            double[] q = xz.get();
            TENUES.clear();
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %d %.3f %.1f 0", q[0], oy, q[1], f.lacet));
            orbite(f, 0, 0);
        }, "au pied de la face " + f.code);
        attendre(mc -> {
            double[] q = xz.get();
            return mc.player.onGround() && Math.abs(mc.player.getX() - q[0]) < 0.01 && Math.abs(mc.player.getZ() - q[1]) < 0.01;
        }, 40, "posé au pied de la face " + f.code);
        pas(4, mc -> orbite(f, 0, 0), "regard vers le mur");
    }

    /** Pousse contre la face {@code f} jusqu'à l'accroche confirmée, puis vérifie boîte, pose et distance. */
    private static void accrocher(MurFace f, double d) {
        accrocherEn(f, () -> pied(f, d), "");
    }

    /** Comme {@link #accrocher}, depuis le pied {@code xz} (debout contre le mur, à 0,3 de sa surface), lu à l'exécution. */
    private static void accrocherEn(MurFace f, Supplier<double[]> xz, String lieu) {
        placerEn(f, xz);
        pas(1, mc -> {
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":true}");
            reponse = "etat";
            RECUS.clear();
            tenir(Touche.AVANT);
            relacherALAccroche = true;   // au tick même : la bascule ne doit pas enchaîner sur une montée
        }, "poussée contre la face " + f.code);
        attendre(mc -> MurClient.corpsPris, 30, "accroche confirmée (face " + f.code + ")");
        attendre(mc -> MurClient.phase() == MurClient.Phase.MARCHE, 20, "bascule jouée (face " + f.code + ")");
        pas(1, mc -> {
            LocalPlayer p = mc.player;
            AABB b = p.getBoundingBox();
            double[] q = xz.get();
            double paroi = Math.abs(f.surNormale(p.getX(), p.getZ()) - (f.surNormale(q[0], q[1]) - f.sens() * 0.3));
            verifie(RECUS.size() == 1 && f.code.equals(RECUS.get(0).get("f").getAsString()), "une seule demande, face " + f.code);
            verifie(boiteVaut(p, f), String.format(Locale.ROOT,
                "face %s : boîte de %.2f × %.2f × %.2f couchée sur la normale, qui contient le cube du serveur",
                f.code, Math.max(b.getXsize(), b.getZsize()), Math.min(b.getXsize(), b.getZsize()), b.getYsize()));
            verifie(Math.abs(paroi - MurClient.DISTANCE) < 1e-6 && Math.abs(p.getY() - (oy + 0.6)) < 1e-6,
                String.format(Locale.ROOT, "face %s : P à %.4f de la paroi, monté de %.2f", f.code, paroi, p.getY() - oy));
            verifie(p.getPose() == Pose.SWIMMING && !p.onGround(), "face " + f.code + " : pose de nage tenue");
        }, "bilan de l'accroche (face " + f.code + (lieu.isEmpty() ? "" : ", " + lieu) + ")");
    }

    private static void orbite(MurFace f, double ecart, double tangage) {
        orbiteLacet = f.lacet + ecart;
        orbiteTangage = tangage;
        RebornCamera.INSTANCE.initOrientation((float) orbiteLacet, (float) orbiteTangage);
    }

    /** Pose l'orbite une fois et la laisse au mod : un changement de face doit pouvoir la tourner (cap conservé). */
    private static void orbiteLibre(MurFace f, double ecart, double tangage) {
        orbiteLacet = Double.NaN;
        RebornCamera.INSTANCE.initOrientation((float) (f.lacet + ecart), (float) tangage);
    }

    /**
     * Le banc ne doit pas dépendre de qui touche au poste pendant la passe (vu à la cinquième : huit mesures faussées) :
     * la souris est rendue (saisie par la fenêtre, elle tournerait l'orbite), un écran ouvert au clavier est refermé,
     * l'orbite voulue et les touches tenues sont reposées. Joué au début et à la fin de chaque tick.
     */
    private static void garde(Minecraft mc) {
        if (mc.player == null || mc.level == null || t < 0) return;
        if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
        if (t > 60 && mc.gui.screen() != null) mc.setScreenAndShow(null);
        if (!Double.isNaN(orbiteLacet)) RebornCamera.INSTANCE.initOrientation((float) orbiteLacet, (float) orbiteTangage);
        appliquerTouches(mc);
    }

    private static void tenir(Touche... touches) {
        TENUES.clear();
        TENUES.addAll(List.of(touches));
    }

    private static void mesure(Minecraft mc) {
        mx = mc.player.getX();
        my = mc.player.getY();
        mz = mc.player.getZ();
        mTicks = 0;
    }

    /** Vitesse moyenne depuis {@link #mesure}, en blocs par seconde. */
    private static double vitesse(Minecraft mc) {
        double dx = mc.player.getX() - mx, dy = mc.player.getY() - my, dz = mc.player.getZ() - mz;
        return mTicks == 0 ? 0 : Math.sqrt(dx * dx + dy * dy + dz * dz) / mTicks * 20.0;
    }

    /* ================================================================ contrôles */

    /** La boîte de {@code e} est-elle celle du cahier pour la face {@code f}, et contient-elle le cube du serveur ? */
    private static boolean boiteVaut(Entity e, MurFace f) {
        AABB b = e.getBoundingBox();
        double x = e.getX(), y = e.getY(), z = e.getZ();
        double n0 = -f.sens() * MurClient.ARRIERE, n1 = f.sens() * MurClient.AVANT;
        double nMin = Math.min(n0, n1), nMax = Math.max(n0, n1);
        double minX = f.nx != 0 ? x + nMin : x - 0.3, maxX = f.nx != 0 ? x + nMax : x + 0.3;
        double minZ = f.nx != 0 ? z - 0.3 : z + nMin, maxZ = f.nx != 0 ? z + 0.3 : z + nMax;
        boolean juste = proche(b.minX, minX) && proche(b.maxX, maxX) && proche(b.minZ, minZ) && proche(b.maxZ, maxZ)
            && proche(b.minY, y) && proche(b.maxY, y + 0.6);
        boolean contient = b.minX <= x - 0.3 + 1e-9 && b.maxX >= x + 0.3 - 1e-9 && b.minZ <= z - 0.3 + 1e-9 && b.maxZ >= z + 0.3 - 1e-9
            && b.minY <= y + 1e-9 && b.maxY >= y + 0.6 - 1e-9;
        boolean taille = proche(Math.max(b.getXsize(), b.getZsize()), 1.75) && proche(Math.min(b.getXsize(), b.getZsize()), 0.6)
            && proche(b.getYsize(), 0.6);
        return juste && contient && taille;
    }

    private static boolean proche(double a, double b) { return Math.abs(a - b) < 1e-6; }

    /**
     * Fin d'une sortie animée : relit les poses rendues. Un recul est un pas du cap de sens contraire au pas non nul
     * d'avant (au-delà de 0,05°, le bruit des flottants) : le corps qui se redresse ne doit tourner que dans un sens.
     */
    private static void bilanPoses() {
        posesSortie = CAPS.size();
        reculsSortie = 0;
        pasCapMax = 0.0;
        lacetDernierePose = Float.NaN;
        int sens = 0;
        for (int i = 1; i < posesSortie; i++) {
            double d = Mth.wrapDegrees((double) CAPS.get(i) - CAPS.get(i - 1));
            pasCapMax = Math.max(pasCapMax, Math.abs(d));
            if (Math.abs(d) < 0.05) continue;
            int s = d > 0 ? 1 : -1;
            if (sens != 0 && s != sens) reculsSortie++;
            sens = s;
        }
        if (posesSortie > 0) lacetDernierePose = murSortie - CAPS.get(posesSortie - 1);
        System.out.println(String.format(Locale.ROOT, "[mur] sortie animée : %d poses, %d recul(s), pas du cap au plus %.2f°, dernière pose debout à %.1f°",
            posesSortie, reculsSortie, pasCapMax, lacetDernierePose));
    }

    /** Boîte debout du jeu : 0,6 × 1,8. */
    private static boolean debout(Entity e) {
        AABB b = e.getBoundingBox();
        return Math.abs(b.getXsize() - 0.6) < 1e-4 && Math.abs(b.getZsize() - 0.6) < 1e-4 && Math.abs(b.getYsize() - 1.8) < 1e-4;
    }

    /** Cube de la pose de nage : 0,6 de côté. */
    private static boolean cube(Entity e) {
        AABB b = e.getBoundingBox();
        return Math.abs(b.getXsize() - 0.6) < 1e-4 && Math.abs(b.getZsize() - 0.6) < 1e-4 && Math.abs(b.getYsize() - 0.6) < 1e-4;
    }

    private static <T extends Entity> T trouver(Minecraft mc, Class<T> type) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (type.isInstance(e) && e.distanceToSqr(mc.player) < 40 * 40) return type.cast(e);
        }
        return null;
    }

    private static boolean cles(JsonObject m, String... k) { return m != null && m.keySet().equals(Set.of(k)); }

    private static JsonObject dernier() { return RECUS.isEmpty() ? new JsonObject() : RECUS.get(RECUS.size() - 1); }

    private static String action(JsonObject m) { return m.has("a") ? m.get("a").getAsString() : ""; }

    /* ================================================================ serveur simulé (ShinobiAbilities MurTest) */

    private static String etat(int id, String face) {
        return "{\"v\":1,\"t\":\"etat\",\"e\":" + id + ",\"f\":\"" + face + "\"}";
    }

    /** L'exemplaire du joueur lui-même, avec le numéro de la demande à laquelle il répond (null : sans numéro). */
    private static String etat(int id, String face, Integer n) {
        return n == null ? etat(id, face) : "{\"v\":1,\"t\":\"etat\",\"e\":" + id + ",\"f\":\"" + face + "\",\"n\":" + n + "}";
    }

    private static void serveur(JsonObject o) {
        RECUS.add(o);
        Minecraft mc = Minecraft.getInstance();
        String a = o.has("a") ? o.get("a").getAsString() : "";
        System.out.println("[mur] reçu du mod : " + o);
        Integer n = o.has("n") ? o.get("n").getAsInt() : null;
        switch (a) {
            case "accroche" -> {
                if ("etat".equals(reponse)) FILE.add(etat(mc.player.getId(), o.get("f").getAsString(), n));
                else if ("refus".equals(reponse)) FILE.add("{\"v\":1,\"t\":\"refus\",\"m\":\"Pas de mur plein ici.\"}");
            }
            case "face" -> {
                if ("etat".equals(reponseFace)) FILE.add(etat(mc.player.getId(), o.get("f").getAsString(), n));
                else if ("refus".equals(reponseFace)) {
                    FILE.add("{\"v\":1,\"t\":\"refus\",\"m\":\"Pas de mur plein derrière cet angle.\",\"a\":\"face\""
                        + (n == null ? "" : ",\"n\":" + n) + "}");
                }
            }
            case "lache" -> FILE.add(etat(mc.player.getId(), "-"));
            default -> { }
        }
    }

    /* ================================================================ décor */

    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 12, oy, oz - 8, ox + 12, oy + 20, oz + 16));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 12, oy - 1, oz - 8, ox + 12, oy - 1, oz + 16));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:bricks", ox - 4, oy, oz + 6, ox + 4, oy + 11, oz + 8));
        // repères de la caméra de paroi : une rangée de laine tous les 4 blocs sur la face nord, un arbre devant le mur
        String[] laines = {"red_wool", "yellow_wool", "light_blue_wool"};
        for (int i = 0; i < laines.length; i++) {
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:%s", ox - 4, oy + 3 + 4 * i, oz + 6, ox + 4, oy + 3 + 4 * i, oz + 6, laines[i]));
        }
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_log", ox - 8, oy, oz + 2, ox - 8, oy + 4, oz + 2));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true] keep", ox - 10, oy + 3, oz, ox - 6, oy + 4, oz + 4));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true] keep", ox - 9, oy + 5, oz + 1, ox - 7, oy + 6, oz + 3));
        c.sendCommand(String.format("setblock %d %d %d minecraft:iron_bars", ox - 3, oy + 8, oz + 4));
        // passages : une cloison perpendiculaire à la face nord (coin rentrant, puis coin sortant à son bout), un auvent
        // au-dessus du sommet de la face nord (sommet sous auvent : arrêt), un tronc de 1 × 1 isolé (tour complet)
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox + 3, oy, oz + 3, ox + 3, oy + 4, oz + 5));
        // l'auvent ne couvre que le toit : le serveur intégré tient le marcheur debout (pas de plugin pour la pose de
        // nage), sa boîte de 1,8 buterait sous un auvent qui déborde au-dessus du vide et le ferait corriger
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:oak_planks", ox - 1, oy + 13, oz + 6, ox + 1, oy + 13, oz + 7));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", ox + 9, oy, oz + 1, ox + 9, oy + 5, oz + 1));
        c.sendCommand("kill @e[type=minecraft:item,distance=..60]");
        c.sendCommand("kill @e[type=minecraft:mannequin,distance=..60]");
    }

    /** Retire ce que d'autres bancs ont laissé au-dessus de la scène (plateau, pistes) : il ombrerait le mur. */
    private static void degagerCiel(Minecraft mc) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return;
        var lvl = srv.overworld();
        int dessus = oy + 21, hMax = 0;
        for (int x = ox - 12; x <= ox + 12; x++) {
            for (int z = oz - 8; z <= oz + 16; z++) {
                hMax = Math.max(hMax, lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z));
            }
        }
        for (int y = dessus; y < hMax; y += 50) {
            mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 12, y, oz - 8, ox + 12,
                Math.min(hMax - 1, y + 49), oz + 16));
        }
        System.out.println("[mur] ciel : " + (hMax > dessus ? "dégagé jusqu'à y " + hMax : "libre"));
    }

    /* ================================================================ outils */

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[mur] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        ETAPES.add(new Etape(delai, mc -> { action.accept(mc); return true; }, nom, false));
    }

    /** Étape qui attend une condition (au plus {@code max} ticks, puis échec et on continue). */
    private static void attendre(Predicate<Minecraft> cond, int max, String nom) {
        ETAPES.add(new Etape(1, mc -> {
            if (cond.test(mc)) { attente = 0; return true; }
            if (++attente > max) { attente = 0; verifie(false, "attente trop longue : " + nom); return true; }
            return false;
        }, "attente : " + nom, false));
    }

    /** Laisse courir {@code n} ticks depuis {@link #mesure}, touches tenues, puis joue {@code bilan} et relâche les touches. */
    private static void pendant(int n, Consumer<Minecraft> bilan, String nom) { pendant(n, bilan, nom, true); }

    private static void pendant(int n, Consumer<Minecraft> bilan, String nom, boolean relacher) {
        ETAPES.add(new Etape(0, mc -> {
            attente = 1;
            if (mTicks < n) return false;
            attente = 0;
            bilan.accept(mc);
            if (relacher) TENUES.clear();
            return true;
        }, nom, true));
    }

    /** Demande une capture : elle part quand {@value #IMAGES_AVANT_CAPTURE} images de plus sont dessinées. */
    private static void shot(String nom) {
        capture = nom;
        imagesCapture = images;
    }

    private static void prendre(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, capture + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println(String.format(Locale.ROOT, "[mur] capture %s (%d i/s, roulis %.1f°)", capture, mc.getFps(), MurCamera.roulis()));
        capture = null;
        imagesEtape = images;
    }

    private static void appliquerTouches(Minecraft mc) {
        var o = mc.options;
        poser(o.keyUp, Touche.AVANT);
        poser(o.keyDown, Touche.ARRIERE);
        poser(o.keyLeft, Touche.GAUCHE);
        poser(o.keyRight, Touche.DROITE);
        poser(o.keySprint, Touche.COURSE);
        poser(o.keyJump, Touche.SAUT);
    }

    private static void poser(KeyMapping k, Touche touche) {
        boolean voulu = TENUES.contains(touche);
        if (k.isDown() != voulu) k.setDown(voulu);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        // réponses du serveur simulé : une par tick, comme le réseau
        String r = FILE.poll();
        if (r != null) MurCanal.recevoir(r);
        t++;
        mTicks++;
        // le mode photo masque le HUD, donc la couche qui compte les images : un tick y vaut une image
        if (PhotoMode.INSTANCE.isActive()) images++;
        if (MurClient.corpsPris && mc.player.getPose() != Pose.SWIMMING) poseTombee = true;
        // accroche et lâcher : la mesure du roulis repart de zéro
        if (MurClient.corpsPris != corpsAvant) {
            long ns = net.minecraft.util.Util.getNanos();
            if (MurClient.corpsPris) { nsAccroche = ns; quartAccroche = -1.0; }
            else { nsLache = ns; quartRetour = -1.0; }
            corpsAvant = MurClient.corpsPris;
            nsFenetre = 0L;
            vitesseRoulisMax = 0.0;
        }
        MurClient.Phase ph = MurClient.phase();
        if (phaseAvant == MurClient.Phase.VIRAGE && ph == MurClient.Phase.MARCHE) {
            ARRIVEES.add(new Object[] {MurClient.faceTenue(), mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            System.out.println(String.format(Locale.ROOT, "[mur] virage joué : face %s, P %.4f %.4f %.4f", MurClient.faceTenue(),
                mc.player.getX() - ox, mc.player.getY() - oy, mc.player.getZ() - oz));
            if (relacherAuVirage) {
                relacherAuVirage = false;
                TENUES.clear();
            }
        }
        // chaque correction de position du serveur est datée : l'étape où elle tombe dit qui l'a provoquée
        int corrections = MurClient.corrections();
        if (MurClient.corpsPris && corrections > correctionsAvant) {
            System.out.println(String.format(Locale.ROOT, "[mur] correction de position n° %d (étape « %s », phase %s), P %.4f %.4f %.4f",
                corrections, etape < ETAPES.size() ? ETAPES.get(etape).nom() : "-", ph,
                mc.player.getX() - ox, mc.player.getY() - oy, mc.player.getZ() - oz));
        }
        correctionsAvant = corrections;
        if (phaseAvant != MurClient.Phase.SORTIE && ph == MurClient.Phase.SORTIE) {
            // une sortie part : les poses rendues repartent de zéro (aucune image n'a encore été dessinée depuis ce tick)
            CAPS.clear();
            murSortie = MurClient.lacetRendu(mc.player.getId());
        }
        if (phaseAvant == MurClient.Phase.SORTIE && ph == MurClient.Phase.REPOS) {
            finSortie[0] = mc.player.getX();
            finSortie[1] = mc.player.getY();
            finSortie[2] = mc.player.getZ();
            bilanPoses();
        }
        phaseAvant = ph;
        if (suiviLacetLache) {
            float l = MurClient.lacetRendu(mc.player.getId());
            if (Float.isNaN(l)) suiviLacetLache = false;
            else ecartLacetLache = Math.max(ecartLacetLache, Math.abs(l - lacetLache));
        }
        if (suiviVoisin && voisin != null) {
            float l = MurClient.lacetRendu(voisin.getId());
            if (!Float.isNaN(lacetVoisinAvant) && !Float.isNaN(l)) pasVoisinMax = Math.max(pasVoisinMax, Math.abs(l - lacetVoisinAvant));
            lacetVoisinAvant = l;
            if (cube(voisin) && Math.abs(l - MurFace.W.lacet) > 1f) cubeVoisinVu = true;
        }
        if (relacherALAccroche && MurClient.corpsPris) {
            relacherALAccroche = false;
            TENUES.clear();
            orbiteLacet = Double.NaN;   // l'orbite est rendue au joueur : le mod ne doit plus la déplacer
        }
        etapes(mc);
        garde(mc);
    }

    /** Un message du canal {@code reborn:ecran}, comme s'il venait du serveur ({@code EffetsStaff.recevoir} est du paquet staff). */
    private static void seisme(String json) {
        try {
            var m = fr.reborn.hud.staff.EffetsStaff.class.getDeclaredMethod("recevoir", String.class);
            m.setAccessible(true);
            m.invoke(null, json);
        } catch (ReflectiveOperationException e) {
            verifie(false, "séisme : message non remis (" + e + ")");
        }
    }

    /** À chaque image, après la caméra (couche HUD du banc) : vitesse du roulis et durées des quarts de tour. */
    private static void mesureRoulis() {
        long ns = net.minecraft.util.Util.getNanos();
        double r = MurCamera.roulis();
        if (nsFenetre == 0L) {
            nsFenetre = ns;
            roulisFenetre = r;
        } else if (ns - nsFenetre >= FENETRE_NS) {
            vitesseRoulisMax = Math.max(vitesseRoulisMax, Math.abs(r - roulisFenetre) / ((ns - nsFenetre) / 1.0e9));
            nsFenetre = ns;
            roulisFenetre = r;
        }
        if (corpsAvant && nsAccroche > 0L && quartAccroche < 0.0 && r > 85.0) quartAccroche = (ns - nsAccroche) / 1.0e9;
        if (!corpsAvant && nsLache > 0L && quartRetour < 0.0 && r < 5.0) quartRetour = (ns - nsLache) / 1.0e9;
    }

    private static void etapes(Minecraft mc) {
        if (capture != null) {
            if (images - imagesCapture >= IMAGES_AVANT_CAPTURE) prendre(mc);
            return;
        }
        if (t == 0) prochaine = 60 + ETAPES.get(0).delai();
        if (etape >= ETAPES.size() || t < prochaine) return;
        Etape e = ETAPES.get(etape);
        // au moins deux images rendues depuis l'étape d'avant (fenêtre en arrière-plan : le jeu rend peu d'images)
        if (!e.auTick() && attente == 0 && images - imagesEtape < 2) return;
        if (attente == 0) System.out.println("[mur] " + e.nom());
        if (!e.action().test(mc)) return;
        imagesEtape = images;
        etape++;
        if (etape < ETAPES.size()) prochaine = t + ETAPES.get(etape).delai();
    }
}
