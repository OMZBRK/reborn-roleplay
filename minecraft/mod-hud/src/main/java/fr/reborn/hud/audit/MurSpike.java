package fr.reborn.hud.audit;

import com.google.gson.JsonObject;
import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.mur.MurCamera;
import fr.reborn.hud.mur.MurCanal;
import fr.reborn.hud.mur.MurClient;
import fr.reborn.hud.mur.MurFace;
import fr.reborn.hud.runtime.RebornSession;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Banc de l'<b>essai technique de la marche sur les murs</b> (lot 5, étape C0, 23/11), <b>inactif en production</b> :
 * {@code REBORN_MUR_SPIKE=1} (monde solo de dev, jamais de serveur). Le serveur intégré n'a pas le plugin : le serveur
 * ShinobiAbilities est <b>simulé côté client</b> au format du canal {@code reborn:mur} ({@code permis}, {@code etat},
 * {@code refus} rendus au tick suivant, comme le réseau). Les touches sont les vraies ({@code KeyMapping}), le déplacement,
 * la boîte, la pose et le rendu passent par les vrais mixins : le joueur s'accroche et marche dans le monde, tick par tick.
 *
 * <p>Scène : un bloc de briques de 9 de large sur 12 de haut et 3 d'épaisseur (quatre faces marchables), un obstacle
 * au-dessus du corps à 8 de haut, un mannequin posé sur le mur (« un autre joueur », son état vient du serveur simulé), le
 * tout retiré à la fin. L'obstacle est un barreau à 1,5 bloc de la paroi et non un surplomb collé au mur : le serveur
 * intégré du solo garde une boîte debout de 1,8 (il n'a pas la pose de nage que pose le plugin), il buterait sous un
 * surplomb avant le corps couché et renverrait le joueur en arrière (vu à la première passe : 17 corrections). Séries :
 * <ul>
 *   <li><b>protocole</b> : rien d'envoyé sans permis, format des messages, abandon après une seconde sans réponse, réponse
 *       tardive rendue, refus ;</li>
 *   <li><b>face nord</b> : boîte de 1,75 × 0,6 × 0,6 qui contient le cube du serveur, pose tenue, glissement de la caméra,
 *       3,2 et 5,6 blocs/s, arrêt au sommet et au bord, base figée, quatre sens, {@code P} à 0,9 de la paroi après 5 s de
 *       marche, correction de position comptée, sortie par Espace ;</li>
 *   <li><b>obstacle au-dessus</b> (arrêt dessous, sortie par la touche de course de chakra), <b>refus reçu au mur</b>,
 *       <b>faces sud, est et ouest</b> (le serveur lâche, pied du mur, permis retiré).</li>
 * </ul>
 * Captures {@code run/screenshots/mur_*.png} : les six cadrages de l'essai 7 ({@code accroche}, {@code monter},
 * {@code descendre}, {@code cote}, {@code long}, {@code dos}), {@code boites} (boîtes de collision affichées),
 * {@code second} et {@code second_boites} (vue de côté par une caméra libre, celle du mode photo : le jeu ne dessine pas
 * le joueur local vu par une autre entité). Bilans « [mur] OK / ECHEC », puis « [mur] FIN (n échec(s)) » et le jeu se
 * ferme.
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
    private static int ox, oy, oz;
    private static String mode = "creative", difficulte = "normal";
    private static long heure = -1;
    private static boolean pluie, sprintBascule, poseTombee;
    private static RebornCamera.Mode cameraAvant;
    private static CameraType vueAvant;
    private static DebugScreenEntryStatus boitesAvant;
    /** Orbite de caméra voulue par l'étape en cours, reposée à chaque tick (NaN : laissée au mod, glissement de l'accroche). */
    private static double orbiteLacet = Double.NaN, orbiteTangage;
    /** Les touches tenues sont relâchées au tick même où l'accroche est confirmée. */
    private static boolean relacherALAccroche;
    /** Mesure d'un déplacement : départ et nombre de ticks écoulés depuis. */
    private static double mx, my, mz;
    private static int mTicks;
    private static Entity voisin;

    private MurSpike() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_MUR_SPIKE"))) return;

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
            vueAvant = mc.options.getCameraType();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            boitesAvant = mc.debugEntries.getStatus(DebugScreenEntries.ENTITY_HITBOXES);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER);
            System.out.println("[mur] origine " + ox + " " + oy + " " + oz);
        }, "préparation");
        pas(4, MurSpike::batir, "scène : mur de briques de 9 × 12, obstacle à 8 de haut");
        pas(4, MurSpike::degagerCiel, "ciel dégagé");
        pas(40, mc -> {
            MurCanal.debugServeur = MurSpike::serveur;
            System.out.println("[mur] lumière du ciel au pied du mur : "
                + mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, new net.minecraft.core.BlockPos(ox, oy, oz + 4)));
        }, "serveur simulé");

        protocole();
        faceNord();
        plafond();
        autresFaces();

        pas(5, mc -> {
            TENUES.clear();
            appliquerTouches(mc);
            orbiteLacet = Double.NaN;
            MurCanal.recevoir("{\"v\":1,\"t\":\"permis\",\"on\":false}");
            MurCanal.debugServeur = null;
            if (PhotoMode.INSTANCE.isActive()) PhotoMode.INSTANCE.end(mc);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, boitesAvant);
            mc.options.toggleSprint().set(sprintBascule);
            RebornCamera.INSTANCE.setMode(cameraAvant);
            mc.options.setCameraType(vueAvant);
            var c = mc.player.connection;
            c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 4, oy, oz + 4, ox + 4, oy + 11, oz + 8));
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
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "mur-banc"), (g, dt) -> images++);
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
            verifie(cles(m, "v", "a", "f") && m.get("v").getAsInt() == 1 && "accroche".equals(action(m))
                && "N".equals(m.get("f").getAsString()), "format de la demande : " + m);
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
            verifie(Math.abs(ecart - 35) < 1.5 && Math.abs(cam.camPitch() + 25) < 1.5,
                String.format(Locale.ROOT, "glissement de l'accroche : écart %.1f° (35), regard levé de %.1f° (25)", ecart, -cam.camPitch()));
            verifie(mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "caméra d'épaule au mur");
            shot("mur_accroche");
        }, "capture de l'accroche");
        pas(4, mc -> {
            double avant = RebornCamera.INSTANCE.camYaw();
            verifie(Math.abs(avant - (f.lacet + 35)) < 1.5, "un seul glissement : l'orbite ne bouge plus ensuite");
        }, "caméra au repos");

        // vitesses : face au mur à 35°, regard levé de 25°, « avant » monte tout droit
        pas(2, mc -> { orbite(f, 35, -25); tenir(Touche.AVANT); mesure(mc); }, "montée en marche");
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
        pas(2, mc -> { tenir(Touche.AVANT); mesure(mc); }, "montée jusqu'au sommet");
        pendant(45, mc -> verifie(Math.abs(mc.player.getY() - (oy + 12 - MurClient.HAUTEUR)) < 1e-3 && MurClient.phase() == MurClient.Phase.MARCHE,
            String.format(Locale.ROOT, "arrêt au bord du sommet : y %+.3f (attendu %+.3f), toujours au mur", mc.player.getY() - oy, 12 - MurClient.HAUTEUR)),
            "poussée contre le sommet");

        // descendre : la base est figée à l'appui, la caméra plonge ensuite de 55° sans inverser les touches
        pas(2, mc -> { orbite(f, 35, -25); tenir(Touche.ARRIERE); mesure(mc); }, "descente");
        pas(3, mc -> orbite(f, 35, 30), "la caméra plonge (touche tenue)");
        pas(8, mc -> shot("mur_descendre"), "capture : descendre");
        pendant(30, mc -> {
            double v = vitesse(mc);
            verifie(mc.player.getY() < my - 3 && Math.abs(mc.player.getX() - mx) < 1e-6 && Math.abs(v - 3.2) <= 0.16,
                String.format(Locale.ROOT, "« arrière » descend tout droit, base figée malgré la caméra : %.3f blocs/s, y %+.2f",
                    v, mc.player.getY() - my));
        }, "30 ticks de descente");

        // de côté
        pas(2, mc -> { orbite(f, 35, -25); tenir(Touche.DROITE); mesure(mc); }, "pas à droite");
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

        // de côté (70°, regard levé de 10°) : « avant » suit le regard, en biais vers le haut (72° de la verticale, hors
        // des crans) ; puis le long du mur (90°) : à l'horizontale
        pas(2, mc -> { orbite(f, 35, -25); tenir(Touche.ARRIERE); mesure(mc); }, "descente vers le bas du mur");
        pendant(15, mc -> { }, "15 ticks de descente");
        pas(2, mc -> { orbite(f, 70, -10); tenir(Touche.AVANT); mesure(mc); }, "de côté");
        pas(4, mc -> shot("mur_cote"), "capture : de côté");
        pendant(10, mc -> {
            double du = (mc.player.getX() - mx) * f.tx, dv = mc.player.getY() - my;
            double angle = Math.toDegrees(Math.atan2(du, dv));
            verifie(du > 1.2 && Math.abs(angle - 72.5) < 2.0,
                String.format(Locale.ROOT, "de côté, « avant » suit le regard : %+.2f le long du mur, %+.2f en hauteur (%.1f° de la verticale, étude : 72)",
                    du, dv, angle));
        }, "10 ticks de côté");
        pas(2, mc -> { orbite(f, 90, 0); tenir(Touche.AVANT); mesure(mc); }, "le long du mur");
        pas(4, mc -> shot("mur_long"), "capture : le long");
        pendant(50, mc -> verifie(Math.abs(mc.player.getX() - (ox - 4 + MurClient.DEMI)) < 1e-3 && Math.abs(mc.player.getY() - my) < 1e-6
            && MurClient.phase() == MurClient.Phase.MARCHE,
            String.format(Locale.ROOT, "arrêt au bord du mur : x %+.3f (attendu %+.3f), toujours au mur", mc.player.getX() - ox, -4 + MurClient.DEMI)),
            "poussée contre le bord");

        // dos au mur : le corps s'efface de notre écran, la caméra se rapproche
        pas(2, mc -> { orbite(f, 35, -25); tenir(Touche.GAUCHE); mesure(mc); }, "retour vers le milieu");
        pendant(12, mc -> { }, "12 ticks");
        pas(2, mc -> orbite(f, 180, 0), "dos au mur");
        pas(14, mc -> {
            verifie(MurCamera.efface() && Math.abs(MurCamera.recul - 2.0) < 1e-6, "dos au mur : notre corps s'efface, recul de 2");
            shot("mur_dos");
        }, "capture : dos au mur");

        // boîtes de collision affichées (F3 + B), puis la vue de côté par une caméra détachée
        pas(2, mc -> {
            orbite(f, 60, -12);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
        }, "boîtes de collision affichées");
        pas(14, mc -> {
            verifie(!MurCamera.efface(), "regard revenu vers le mur : le corps reparaît");
            shot("mur_boites");
        }, "capture : boîtes");
        // le mode photo pose sa caméra libre à trois blocs derrière le regard du joueur : regard vers l'est, le long de
        // la paroi, elle voit le corps de profil
        pas(2, mc -> orbite(f, -90, 8), "regard le long de la paroi");
        pas(8, mc -> PhotoMode.INSTANCE.begin(mc), "caméra libre (mode photo), vue de côté");
        pas(14, mc -> shot("mur_second_boites"), "capture : vue de côté, boîtes affichées");
        pas(2, mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER), "boîtes masquées");
        pas(8, mc -> shot("mur_second"), "capture : vue de côté");
        pas(2, mc -> {
            PhotoMode.INSTANCE.end(mc);
            orbite(f, 35, -25);
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
        pas(2, mc -> { orbite(MurFace.N, 35, -25); tenir(Touche.AVANT); mesure(mc); }, "montée sous l'obstacle");
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
        pas(2, mc -> { orbite(MurFace.E, 35, -25); tenir(Touche.AVANT); mesure(mc); }, "montée de deux blocs");
        pendant(13, mc -> { RECUS.clear(); tenir(Touche.ARRIERE); mesure(mc); }, "montée", false);
        attendre(mc -> MurClient.phase() == MurClient.Phase.REPOS, 40, "pied du mur : le mod lâche de lui-même");
        pas(0, mc -> {
            verifie(RECUS.size() == 1 && "lache".equals(action(dernier())) && mc.player.getY() - oy < 1.0,
                String.format(Locale.ROOT, "à moins d'un bloc du sol en descendant : lâché à y %+.2f", mc.player.getY() - oy));
            TENUES.clear();
        }, "bilan du pied du mur");
        attendre(mc -> mc.player.onGround(), 40, "relevé au sol");
        pas(4, mc -> verifie(mc.player.getPose() == Pose.STANDING && debout(mc.player) && Math.abs(mc.player.getY() - oy) < 1e-3,
            "pied du mur : debout sur le sol"), "bilan debout");

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

    /* ================================================================ gestes */

    /** Pied de la face {@code f}, debout contre le mur ; {@code d} : décalage depuis l'origine sur l'horizontale de la paroi. */
    private static double[] pied(MurFace f, double d) {
        return switch (f) {
            case N -> new double[] {ox + d, oz + 6 - 0.3};
            case S -> new double[] {ox + d, oz + 9 + 0.3};
            case E -> new double[] {ox + 5 + 0.3, oz + d};
            case W -> new double[] {ox - 4 - 0.3, oz + d};
        };
    }

    private static void placer(MurFace f, double d) {
        pas(4, mc -> {
            TENUES.clear();
            double[] xz = pied(f, d);
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %d %.3f %.1f 0", xz[0], oy, xz[1], f.lacet));
            orbite(f, 0, 0);
        }, "au pied de la face " + f.code);
        attendre(mc -> {
            double[] xz = pied(f, d);
            return mc.player.onGround() && Math.abs(mc.player.getX() - xz[0]) < 0.01 && Math.abs(mc.player.getZ() - xz[1]) < 0.01;
        }, 40, "posé au pied de la face " + f.code);
        pas(4, mc -> orbite(f, 0, 0), "regard vers le mur");
    }

    /** Pousse contre la face {@code f} jusqu'à l'accroche confirmée, puis vérifie boîte, pose et distance. */
    private static void accrocher(MurFace f, double d) {
        placer(f, d);
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
            double[] xz = pied(f, d);
            double paroi = Math.abs(f.surNormale(p.getX(), p.getZ()) - (f.surNormale(xz[0], xz[1]) - f.sens() * 0.3));
            verifie(RECUS.size() == 1 && f.code.equals(RECUS.get(0).get("f").getAsString()), "une seule demande, face " + f.code);
            verifie(boiteVaut(p, f), String.format(Locale.ROOT,
                "face %s : boîte de %.2f × %.2f × %.2f couchée sur la normale, qui contient le cube du serveur",
                f.code, Math.max(b.getXsize(), b.getZsize()), Math.min(b.getXsize(), b.getZsize()), b.getYsize()));
            verifie(Math.abs(paroi - MurClient.DISTANCE) < 1e-6 && Math.abs(p.getY() - (oy + 0.6)) < 1e-6,
                String.format(Locale.ROOT, "face %s : P à %.4f de la paroi, monté de %.2f", f.code, paroi, p.getY() - oy));
            verifie(p.getPose() == Pose.SWIMMING && !p.onGround(), "face " + f.code + " : pose de nage tenue");
        }, "bilan de l'accroche (face " + f.code + ")");
    }

    private static void orbite(MurFace f, double ecart, double tangage) {
        orbiteLacet = f.lacet + ecart;
        orbiteTangage = tangage;
        RebornCamera.INSTANCE.initOrientation((float) orbiteLacet, (float) orbiteTangage);
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

    private static void serveur(JsonObject o) {
        RECUS.add(o);
        Minecraft mc = Minecraft.getInstance();
        String a = o.has("a") ? o.get("a").getAsString() : "";
        System.out.println("[mur] reçu du mod : " + o);
        switch (a) {
            case "accroche" -> {
                if ("etat".equals(reponse)) FILE.add(etat(mc.player.getId(), o.get("f").getAsString()));
                else if ("refus".equals(reponse)) FILE.add("{\"v\":1,\"t\":\"refus\",\"m\":\"Pas de mur plein ici.\"}");
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
        c.sendCommand(String.format("setblock %d %d %d minecraft:iron_bars", ox - 3, oy + 8, oz + 4));
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
        System.out.println("[mur] capture " + capture + " (" + mc.getFps() + " i/s)");
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
        if (relacherALAccroche && MurClient.corpsPris) {
            relacherALAccroche = false;
            TENUES.clear();
            orbiteLacet = Double.NaN;   // le glissement de l'accroche appartient au mod
        }
        etapes(mc);
        garde(mc);
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
