package fr.reborn.hud.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.anchor.ProjectionMonde;
import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.staff.GardePortesDebug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.joml.Vector3f;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Banc de captures de l'outil <b>« Portes »</b> du Poste de garde (F7 › Outils › Portes scellées ; 16/11), <b>inactif en
 * production</b> : {@code REBORN_LOT6_DEBUG=portes} (monde solo de dev, jamais de serveur). Le serveur est <b>simulé côté
 * client</b> au format du protocole ({@code portes}, {@code porte_verifier}, {@code porte_creer}… → {@code toast},
 * {@code portes}, {@code porte_verif}), avec les données des maquettes (D3 « Archives du clan », D4 « Sas de la prison »…).
 * Les clics passent par les vraies zones cliquables du volet ({@link GardePortesDebug}).
 *
 * <p>Scène : un mur de briques de 3 × 2 à six blocs devant le joueur (retiré à la fin). Captures
 * {@code run/screenshots/portes_*.png} : sélecteur (dalle valable, dalle trop épaisse), nouvelle porte (suite de trois
 * signes), fiche d'un modérateur (un blocage), sous-éditeurs (renommer, changer la levée, suite de douze signes), liste,
 * ligne de l'outil dans la liste des outils, fiche d'un Helper, confirmation du retrait, liste en 1366 × 768.
 * Bilans « [portes] OK / ECHEC » (format de chaque message, droits, vérification de la dalle), puis le jeu se ferme
 * (« [portes] FIN »). <b>Aucune suite n'est écrite au journal</b> : les messages reçus sont cités par leur action seule.
 */
public final class Lot6PortesDebug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs;
    /**
     * Images rendues, et leur compte à la dernière étape : les zones cliquables du volet sont relevées au rendu, une étape
     * ne part donc qu'après {@value #IMAGES_ENTRE_ETAPES} images neuves (fenêtre en arrière-plan ou poste verrouillé : le
     * jeu rend peu d'images, les vérifications liraient un volet périmé).
     */
    private static int images, imagesEtape;
    private static final int IMAGES_ENTRE_ETAPES = 3;
    private static long heure = -1;
    private static boolean pluie, volant;
    private static String mode = "survival", difficulte = "normal";
    private static RebornCamera.Mode cameraAvant;
    /** Origine de la scène (pieds du joueur au départ). */
    private static int ox, oy, oz;
    /** Horloge figée de l'écart entre deux vérifications de dalle. */
    private static long h = 1_000_000;
    private static final Vector3f V = new Vector3f();

    private static final String SNAP = "{\"t\":\"snap\",\"grade\":2,\"modeStaff\":true,\"modeAnim\":false,\"animPossible\":false,"
        + "\"staffOnline\":1,\"players\":[],\"alerts\":[],\"chat\":[],\"commands\":[]}";
    private static final String REN = "5f0c1f6e-2f6a-4f0e-9a51-7c1b3c0d9e21", MODO = "Réservé aux modérateurs.";

    /* ================================================================ serveur simulé */

    private static final class Porte {
        String id, nom, monde = "world", etat = "fermee", creePar = "Kaede", modifPar = "";
        int x, y, z, dx, dy, dz, leve, reste = -1;
        int[] signes = {};
        long creeLe = le(15, 18, 40), modifLe;
        /** Joueurs bloqués : {uuid, nom, secondes restantes}. */
        final List<String[]> blocages = new ArrayList<>();
    }

    private static final Map<String, Porte> PORTES = new LinkedHashMap<>();
    private static boolean actif = true, signesAuHelper, retenir;
    private static int grade = 2;
    /** Messages reçus du mod, dans l'ordre (jamais écrits en entier au journal : ils portent des suites). */
    private static final List<JsonObject> RECUS = new ArrayList<>();
    /** Réponses rendues au tick suivant (comme le réseau) ; réponses {@code porte_verif} retenues par le banc. */
    private static final ArrayDeque<String> FILE = new ArrayDeque<>(), RETENUES = new ArrayDeque<>();
    private static int n1, n2, memo;

    private Lot6PortesDebug() {}

    public static void init() {
        if (!"portes".equalsIgnoreCase(String.valueOf(System.getenv("REBORN_LOT6_DEBUG")))) return;

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
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            volant = true;
            vol(mc, true);
        }, "préparation");
        pas(10, Lot6PortesDebug::batir, "scène : un mur de 3 × 2");
        pas(10, mc -> {
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %d %.2f 0 6", ox + 1.9, oy, oz + 0.3));
            cameraAvant = RebornCamera.INSTANCE.mode();
            RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
            RebornCamera.INSTANCE.initOrientation(0f, 6f);
        }, "joueur à six blocs du mur");
        pas(30, mc -> {
            donnees();
            GardePortesDebug.serveur(Lot6PortesDebug::serveur);
            GardePortesDebug.horloge(() -> h);
            System.out.println("[portes] origine " + ox + " " + oy + " " + oz);
        }, "serveur simulé, horloge des vérifications figée");

        serieCreation();
        serieFiche();
        serieListe();
        serieAdmin();
        serieHelper();
        serieRetrait();

        // ───────── 1366 × 768
        pas(2, mc -> {
            grade = 2;
            donnees();
            creerD3();
            mc.getWindow().setWindowed(1366, 768);
            mc.resizeGui();
        }, "fenêtre 1366 × 768");
        pas(20, mc -> {
            GardePortesDebug.ouvrir(mc, SNAP);
            GardePortesDebug.fiche(mc, "D3");
            GardePortesDebug.liste(mc);
        }, "liste en 1366 × 768");
        pas(10, mc -> GardePortesDebug.recevoir(liste(null)), "liste fraîche");
        pas(6, mc -> {
            verifie(GardePortesDebug.plaqueVisible(mc), "1366 × 768 : la liste tient dans le volet (« Nouvelle porte » visible sans défiler)");
            shot(mc, "portes_768");
        }, "capture 1366 × 768");
        pas(4, mc -> { mc.getWindow().setWindowed(1920, 1080); mc.resizeGui(); }, "fenêtre 1920 × 1080");

        pas(10, mc -> {
            GardePortesDebug.fermer(mc);
            GardePortesDebug.effacerToasts();
            var c = mc.player.connection;
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 1, oy, oz + 6, ox + 1, oy + 1, oz + 6));
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            RebornCamera.INSTANCE.setMode(cameraAvant);
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> { volant = false; vol(mc, false); mc.player.connection.sendCommand("gamemode " + mode); }, "mode de jeu");
        pas(10, mc -> {
            System.out.println("[portes] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot6PortesDebug::tick);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "lot6-portes-banc"), (g, dt) -> images++);
    }

    /* ================================================================ séries */

    /** Liste, sélecteur de dalle (vérification, refus), nouvelle porte, création. */
    private static void serieCreation() {
        pas(10, mc -> GardePortesDebug.ouvrir(mc, SNAP), "Poste de garde › Outils › Portes");
        pas(6, mc -> {
            JsonObject m = dernier("portes");
            verifie(m != null && cles(m, "a", "r") && m.get("r").getAsInt() == 64, "ouverture : demande « portes » avec r = 64");
            verifie("liste".equals(GardePortesDebug.vueCourante(mc)), "l'outil s'ouvre sur la liste");
        }, "liste demandée");
        pas(45, mc -> verifie(compte("portes") >= 2, "liste redemandée toutes les 2 s, outil ouvert (" + compte("portes") + " demandes)"), "rafraîchissement");

        // ---- sélecteur : un clic, puis Maj + clic ; demandes numérotées, 250 ms d'écart, réponses périmées ignorées
        pas(2, mc -> verifie("ok".equals(GardePortesDebug.plaque(mc)), "« Nouvelle porte » ouvre le sélecteur de dalle"), "clic « Nouvelle porte »");
        pas(4, mc -> {
            verifie("selecteur".equals(GardePortesDebug.vueCourante(mc)) && GardePortesDebug.replie(mc), "sélecteur ouvert, volet replié");
            verifie("refus : Clique d'abord un bloc dans le monde (Maj + clic : second coin).".equals(GardePortesDebug.plaque(mc)),
                "« Valider la dalle » éteint tant que rien n'est choisi");
            retenir = true;
            clicSur(mc, ox + 1, oy, oz + 6, false, false);
        }, "clic : premier coin");
        pas(3, mc -> {
            JsonObject m = dernier("porte_verifier");
            boolean ok = compte("porte_verifier") == 1 && m != null && cles(m, "a", "n", "boite") && boiteVaut(m, ox + 1, oy, oz + 6, 1, 1, 1);
            verifie(ok, "un seul clic : dalle d'un bloc, « porte_verifier » avec son numéro et sa boîte");
            n1 = m == null ? -1 : m.get("n").getAsInt();
            clicSur(mc, ox - 1, oy + 1, oz + 6, true, false);
        }, "Maj + clic : second coin");
        pas(3, mc -> {
            verifie(compte("porte_verifier") == 1, "pas de seconde demande avant 250 ms");
            // la réponse à la dalle d'un bloc arrive après le second coin : elle ne vaut pas pour la nouvelle dalle
            GardePortesDebug.recevoir(RETENUES.poll());
        }, "réponse de la première dalle, en retard");
        pas(3, mc -> {
            verifie(GardePortesDebug.etatDalle(mc) == 2, "réponse de la dalle d'avant ignorée (toujours en vérification)");
            h += 250;
        }, "250 ms passent");
        pas(3, mc -> {
            JsonObject m = dernier("porte_verifier");
            n2 = m == null ? -1 : m.get("n").getAsInt();
            verifie(compte("porte_verifier") == 2 && n2 == n1 + 1 && boiteVaut(m, ox - 1, oy, oz + 6, 3, 2, 1),
                "seconde demande après 250 ms : numéro suivant, boîte de 3 × 2 × 1");
            GardePortesDebug.recevoir("{\"t\":\"porte_verif\",\"n\":" + n1 + ",\"ok\":false,\"m\":\"Réponse périmée.\"}");
        }, "une réponse au numéro d'avant arrive");
        pas(3, mc -> {
            verifie(GardePortesDebug.etatDalle(mc) == 2, "réponse « porte_verif » périmée ignorée (numéro d'avant)");
            retenir = false;
            GardePortesDebug.recevoir(RETENUES.poll());
        }, "la bonne réponse arrive");
        pas(3, mc -> {
            verifie(GardePortesDebug.etatDalle(mc) == 3, "réponse au bon numéro : dalle valable");
            sourisSur(mc, ox - 1, oy + 1, oz + 6, false);
        }, "dalle valable");
        pas(8, mc -> {
            System.out.println("[portes] " + GardePortesDebug.bilan(mc));
            shot(mc, "portes_selecteur");
        }, "capture sélecteur");

        // ---- dalle trop épaisse : refus sur place, sans demande au serveur
        pas(2, mc -> {
            int avant = compte("porte_verifier");
            clicSur(mc, ox, oy - 1, oz + 2, false, true);
            clicSur(mc, ox + 4, oy - 1, oz + 5, true, true);
            verifie(GardePortesDebug.etatDalle(mc) == 1 && GardePortesDebug.raisonDalle(mc).contains("épaisse"),
                "dalle de 5 × 1 × 4 : refusée sur place (" + GardePortesDebug.raisonDalle(mc) + ")");
            h += 250;
            memo = avant;
        }, "dalle trop épaisse au sol");
        pas(4, mc -> {
            verifie(compte("porte_verifier") == memo, "dalle refusée sur place : aucune demande au serveur");
            verifie(GardePortesDebug.plaque(mc).startsWith("refus : Trop épaisse"), "« Valider la dalle » éteint, la raison au clic");
            verifie(GardePortesDebug.entree(mc, false) && "selecteur".equals(GardePortesDebug.vueCourante(mc)), "Entrée ne valide pas une dalle refusée");
            GardePortesDebug.effacerToasts();
        }, "refus");
        pas(8, mc -> {
            System.out.println("[portes] " + GardePortesDebug.bilan(mc));
            shot(mc, "portes_selecteur_refus");
        }, "capture refus");

        // ---- retour au mur, validation
        pas(2, mc -> {
            h += 250;
            clicSur(mc, ox + 1, oy, oz + 6, false, false);
            clicSur(mc, ox - 1, oy + 1, oz + 6, true, false);
        }, "dalle du mur, à nouveau");
        pas(5, mc -> {
            verifie(GardePortesDebug.etatDalle(mc) == 3, "dalle du mur valable");
            verifie("ok".equals(GardePortesDebug.plaque(mc)), "« Valider la dalle » allumé");
        }, "clic « Valider la dalle »");

        // ---- nouvelle porte : nom, suite sur la grille 3A, levée
        pas(4, mc -> {
            System.out.println("[portes] " + GardePortesDebug.bilan(mc));
            verifie("nouvelle".equals(GardePortesDebug.vueCourante(mc)) && !GardePortesDebug.replie(mc), "dalle validée : formulaire « Nouvelle porte », volet déplié");
            verifie(GardePortesDebug.levee(mc) == 2, "levée par défaut : toute la hauteur de la dalle (2)");
            verifie("refus : Donne un nom à la porte.".equals(GardePortesDebug.plaque(mc)), "« Sceller la porte » éteint sans nom");
            GardePortesDebug.effacerToasts();
            GardePortesDebug.nom(mc, "Archives du clan");
        }, "nom");
        pas(4, mc -> {
            verifie("refus : La suite compte 2 signes au moins.".equals(GardePortesDebug.plaque(mc)), "« Sceller la porte » éteint sans suite");
            verifie("ok".equals(GardePortesDebug.caseSigne(mc, 2)), "clic sur la case d'un signe : il s'ajoute à la suite");
        }, "premier signe");
        pas(4, mc -> {
            verifie(GardePortesDebug.tailleSuite(mc) == 1 && "refus : La suite compte 2 signes au moins.".equals(GardePortesDebug.plaque(mc)),
                "« Sceller la porte » éteint avec un seul signe");
            GardePortesDebug.caseSigne(mc, 5);
        }, "deuxième signe");
        pas(4, mc -> {
            verifie(GardePortesDebug.raisonSceller(mc) == null, "deux signes : « Sceller la porte » allumé");
            GardePortesDebug.caseSigne(mc, 10);
            GardePortesDebug.caseSigne(mc, 10);
            verifie(GardePortesDebug.tailleSuite(mc) == 4, "un même signe peut revenir");
            verifie(GardePortesDebug.retourArriere(mc) && GardePortesDebug.tailleSuite(mc) == 3, "Retour arrière : le dernier signe s'efface");
            verifie("absente".equals(GardePortesDebug.levee(mc, true)), "levée bornée à la hauteur de la dalle (« + » éteint à 2)");
            verifie("ok".equals(GardePortesDebug.levee(mc, false)) && GardePortesDebug.levee(mc) == 1, "« − » : levée de 1");
        }, "troisième signe, levée");
        pas(4, mc -> {
            verifie("absente".equals(GardePortesDebug.levee(mc, false)), "levée bornée à 1 (« − » éteint)");
            verifie("ok".equals(GardePortesDebug.levee(mc, true)) && GardePortesDebug.levee(mc) == 2, "« + » : levée de 2");
            GardePortesDebug.effacerToasts();
        }, "bornes de la levée");
        pas(8, mc -> shot(mc, "portes_nouvelle"), "capture nouvelle porte");
        pas(2, mc -> {
            verifie(GardePortesDebug.entree(mc, true), "Ctrl + Entrée scelle la porte");
            GardePortesDebug.entree(mc, true);
            JsonObject m = dernier("porte_creer");
            boolean ok = compte("porte_creer") == 1 && m != null && cles(m, "a", "boite", "nom", "signes", "leve")
                && boiteVaut(m, ox - 1, oy, oz + 6, 3, 2, 1) && "Archives du clan".equals(m.get("nom").getAsString())
                && suiteVaut(m, 2, 5, 10) && m.get("leve").getAsInt() == 2;
            verifie(ok, "« porte_creer » : boite, nom, signes (trois ordinaux), leve ; un seul envoi pour deux appuis");
        }, "Ctrl + Entrée");
        pas(6, mc -> verifie("fiche".equals(GardePortesDebug.vueCourante(mc)) && "D3".equals(GardePortesDebug.ficheId(mc)),
            "liste revenue avec « dernier » : la fiche de la porte créée s'ouvre (" + GardePortesDebug.ficheId(mc) + ")"), "porte créée");
    }

    /** Fiche d'un modérateur : suite lisible, blocage, actions, sous-éditeurs. */
    private static void serieFiche() {
        pas(2, mc -> {
            Porte d = PORTES.get("D3");
            d.modifPar = "Omar";
            d.modifLe = le(16, 10, 12);
            d.blocages.add(new String[]{REN, "Ren Hyuga", "80"});
            GardePortesDebug.effacerToasts();
            GardePortesDebug.souris(mc, -100, -100);
            GardePortesDebug.recevoir(liste(null));
        }, "fiche D3 : un joueur bloqué");
        pas(6, mc -> {
            verifie(GardePortesDebug.ficheSuiteVaut(mc, "D3", 2, 5, 10), "fiche d'un modérateur : la suite se lit en noms de signes");
            GardePortesDebug.recevoir(liste(null));
        }, "liste fraîche");
        pas(4, mc -> shot(mc, "portes_fiche"), "capture fiche (modérateur)");
        pas(2, mc -> {
            verifie("ok".equals(GardePortesDebug.lever(mc)), "« Lever » cliquable pour un modérateur");
            JsonObject m = dernier("porte_debloquer");
            verifie(m != null && cles(m, "a", "id", "joueur") && "D3".equals(m.get("id").getAsString()) && REN.equals(m.get("joueur").getAsString()),
                "« porte_debloquer » : id, joueur (UUID)");
        }, "lever le blocage");
        pas(5, mc -> {
            verifie("absente".equals(GardePortesDebug.lever(mc)), "blocage levé : la ligne disparaît");
            verifie("ok".equals(GardePortesDebug.action(mc, 0)), "« Ouvrir pour essai » cliquable");
            JsonObject m = dernier("porte_ouvrir");
            verifie(m != null && cles(m, "a", "id") && "D3".equals(m.get("id").getAsString()), "« porte_ouvrir » : id");
        }, "ouvrir pour essai");
        pas(5, mc -> {
            verifie("ok".equals(GardePortesDebug.action(mc, 0)), "porte levée : la première action devient « Fermer »");
            JsonObject m = dernier("porte_fermer");
            verifie(m != null && cles(m, "a", "id") && "D3".equals(m.get("id").getAsString()), "« porte_fermer » : id");
            verifie(GardePortesDebug.action(mc, 3).startsWith("refus : La porte doit d'abord être fermée"), "porte levée : « Changer la levée » éteint");
        }, "fermer");
        pas(5, mc -> {
            verifie("ok".equals(GardePortesDebug.action(mc, 4)), "« Aller devant » cliquable");
            JsonObject m = dernier("porte_tp");
            verifie(m != null && cles(m, "a", "id") && "D3".equals(m.get("id").getAsString()), "« porte_tp » : id");
            verifie("ok".equals(GardePortesDebug.action(mc, 1)), "« Changer la suite » ouvre la grille");
        }, "aller devant, changer la suite");
        pas(4, mc -> {
            verifie("suite".equals(GardePortesDebug.vueCourante(mc)) && GardePortesDebug.tailleSuite(mc) == 3, "grille préremplie de la suite actuelle (3 signes)");
            GardePortesDebug.caseSigne(mc, 0);
        }, "un signe de plus");
        pas(4, mc -> {
            verifie("ok".equals(GardePortesDebug.plaque(mc)), "« Enregistrer la suite » allumé");
            JsonObject m = dernier("porte_signes");
            verifie(m != null && cles(m, "a", "id", "signes") && "D3".equals(m.get("id").getAsString()) && suiteVaut(m, 2, 5, 10, 0), "« porte_signes » : id, signes (quatre ordinaux)");
        }, "enregistrer la suite");
        pas(5, mc -> {
            verifie("fiche".equals(GardePortesDebug.vueCourante(mc)), "suite enregistrée : retour à la fiche");
            PORTES.get("D3").signes = new int[]{2, 5, 10};
            verifie("ok".equals(GardePortesDebug.action(mc, 2)), "« Renommer » ouvre le champ");
        }, "renommer");
        pas(4, mc -> {
            verifie("nom".equals(GardePortesDebug.vueCourante(mc)), "champ du nom ouvert");
            GardePortesDebug.nom(mc, "Archives du clan, aile nord");
        }, "nom saisi");
        pas(6, mc -> shot(mc, "portes_nom"), "capture renommer");
        pas(2, mc -> {
            verifie(GardePortesDebug.entree(mc, false), "Entrée renomme");
            JsonObject m = dernier("porte_nom");
            verifie(m != null && cles(m, "a", "id", "nom") && "D3".equals(m.get("id").getAsString()) && "Archives du clan, aile nord".equals(m.get("nom").getAsString()), "« porte_nom » : id, nom");
        }, "nouveau nom");
        pas(5, mc -> {
            verifie("fiche".equals(GardePortesDebug.vueCourante(mc)), "nom enregistré : retour à la fiche");
            PORTES.get("D3").nom = "Archives du clan";
            verifie("ok".equals(GardePortesDebug.action(mc, 3)), "« Changer la levée » ouvre le réglage");
        }, "changer la levée");
        pas(4, mc -> {
            verifie("leve".equals(GardePortesDebug.vueCourante(mc)) && GardePortesDebug.levee(mc) == 2, "réglage ouvert sur la levée actuelle (2)");
            GardePortesDebug.levee(mc, false);
        }, "levée de 1");
        pas(6, mc -> shot(mc, "portes_leve"), "capture changer la levée");
        pas(2, mc -> {
            verifie("ok".equals(GardePortesDebug.plaque(mc)), "« Enregistrer » allumé");
            JsonObject m = dernier("porte_leve");
            verifie(m != null && cles(m, "a", "id", "leve") && "D3".equals(m.get("id").getAsString()) && m.get("leve").getAsInt() == 1, "« porte_leve » : id, leve");
        }, "enregistrer la levée");
        pas(5, mc -> {
            verifie("fiche".equals(GardePortesDebug.vueCourante(mc)), "levée enregistrée : retour à la fiche");
            PORTES.get("D3").leve = 2;
            GardePortesDebug.action(mc, 1);
        }, "changer la suite, à nouveau");
        pas(4, mc -> {
            // douze signes : la ligne composée passe à la ligne, un treizième est refusé
            for (int i : new int[]{2, 11, 7, 2, 8, 6, 9, 2, 4}) GardePortesDebug.caseSigne(mc, i);
            verifie(GardePortesDebug.tailleSuite(mc) == 12, "suite de douze signes");
            GardePortesDebug.caseSigne(mc, 1);
            verifie(GardePortesDebug.tailleSuite(mc) == 12 && GardePortesDebug.dernierToast().startsWith("12 signes"), "treizième signe refusé (" + GardePortesDebug.dernierToast() + ")");
            GardePortesDebug.effacerToasts();
        }, "suite pleine");
        pas(8, mc -> shot(mc, "portes_suite"), "capture changer la suite (douze signes)");
        pas(3, mc -> {
            verifie(GardePortesDebug.echap(mc) && "fiche".equals(GardePortesDebug.vueCourante(mc)), "Échap : sous-éditeur → fiche");
            verifie(GardePortesDebug.echap(mc) && "liste".equals(GardePortesDebug.vueCourante(mc)), "Échap : fiche → liste");
            verifie(!GardePortesDebug.echap(mc), "Échap : la liste rend la main aux outils");
        }, "retours");
    }

    /** Liste d'un modérateur : données des maquettes, repères du monde, système réservé aux admins. */
    private static void serieListe() {
        pas(2, mc -> {
            Porte d = PORTES.get("D3");
            d.modifPar = "Omar";
            d.modifLe = le(16, 10, 12);
            d.blocages.clear();
            GardePortesDebug.effacerToasts();
            GardePortesDebug.recevoir(liste(null));
        }, "liste des maquettes");
        pas(6, mc -> GardePortesDebug.recevoir(liste(null)), "liste fraîche");
        pas(4, mc -> {
            String b = GardePortesDebug.bilan(mc);
            System.out.println("[portes] " + b);
            verifie(b.endsWith("repères 4"), "portes à moins de 64 blocs cernées dans le monde (4 sur 7)");
            shot(mc, "portes_liste");
        }, "capture liste");
        pas(2, mc -> {
            int avant = compte("porte_systeme");
            verifie("refus : Réservé aux admins.".equals(GardePortesDebug.systeme(mc, true)), "modérateur : « Éteint » ne répond pas (réservé aux admins)");
            verifie(compte("porte_systeme") == avant && !GardePortesDebug.confirmation(mc), "modérateur : aucune demande « porte_systeme »");
            verifie("ok".equals(GardePortesDebug.rangee(mc, 0)), "clic sur une rangée");
        }, "système, rangée");
        pas(3, mc -> {
            verifie("fiche".equals(GardePortesDebug.vueCourante(mc)) && "D3".equals(GardePortesDebug.ficheId(mc)), "rangée cliquée : fiche de la porte la plus proche (D3)");
            GardePortesDebug.liste(mc);
            GardePortesDebug.outils(mc);
        }, "fiche ouverte par la liste");
        pas(8, mc -> shot(mc, "portes_outils"), "capture de la liste des outils (ligne « Portes scellées »)");
        pas(2, mc -> GardePortesDebug.ouvrir(mc, SNAP), "retour à l'outil");
    }

    /** Admin : éteindre demande une confirmation, allumer non. */
    private static void serieAdmin() {
        pas(2, mc -> { grade = 3; GardePortesDebug.recevoir(liste(null)); }, "grade Admin");
        pas(4, mc -> {
            verifie("ok".equals(GardePortesDebug.systeme(mc, true)), "admin : « Éteint » cliquable");
            verifie(GardePortesDebug.confirmation(mc) && compte("porte_systeme") == 0, "éteindre : confirmation dans le volet, rien d'envoyé avant");
            verifie(GardePortesDebug.confirmer(mc), "confirmation validée");
            JsonObject m = dernier("porte_systeme");
            verifie(m != null && cles(m, "a", "on") && m.get("on").getAsJsonPrimitive().isBoolean() && !m.get("on").getAsBoolean(), "« porte_systeme » : on = false");
        }, "éteindre le système");
        pas(5, mc -> {
            verifie("ok".equals(GardePortesDebug.systeme(mc, false)) && !GardePortesDebug.confirmation(mc), "allumer : sans confirmation");
            JsonObject m = dernier("porte_systeme");
            verifie(compte("porte_systeme") == 2 && m.get("on").getAsBoolean(), "« porte_systeme » : on = true");
        }, "rallumer le système");
        pas(4, mc -> GardePortesDebug.effacerToasts(), "système rallumé");
    }

    /** Helper : la suite n'est jamais montrée, les modifications sont éteintes. */
    private static void serieHelper() {
        pas(2, mc -> {
            grade = 1;
            PORTES.get("D3").blocages.add(new String[]{REN, "Ren Hyuga", "80"});
            RECUS.clear();
            GardePortesDebug.fiche(mc, "D3");
            GardePortesDebug.recevoir(liste(null));
        }, "grade Helper, fiche D3");
        pas(6, mc -> {
            boolean sans = true;
            for (JsonElement e : com.google.gson.JsonParser.parseString(liste(null)).getAsJsonObject().getAsJsonArray("l"))
                if (e.getAsJsonObject().has("signes")) sans = false;
            verifie(sans, "liste d'un Helper : aucune suite dans le message, le nombre de signes seulement");
            String s = GardePortesDebug.ficheSuiteSansSigne(mc, "D3");
            verifie("3 signes  ·  réservée aux modérateurs".equals(s), "fiche d'un Helper : « " + s + " »");
            signesAuHelper = true;
            GardePortesDebug.recevoir(liste(null));
        }, "un serveur fautif envoie la suite à un Helper");
        pas(4, mc -> {
            String s = GardePortesDebug.ficheSuiteSansSigne(mc, "D3");
            verifie("3 signes  ·  réservée aux modérateurs".equals(s), "suite jamais affichée pour un Helper, même reçue (« " + s + " »)");
            signesAuHelper = false;
            GardePortesDebug.recevoir(liste(null));
        }, "liste conforme");
        pas(4, mc -> GardePortesDebug.recevoir(liste(null)), "liste fraîche");
        pas(4, mc -> shot(mc, "portes_fiche_helper"), "capture fiche (Helper)");
        pas(2, mc -> {
            boolean eteintes = true;
            for (int i : new int[]{0, 1, 2, 3, 6}) if (!("refus : " + MODO).equals(GardePortesDebug.action(mc, i))) eteintes = false;
            verifie(eteintes, "Helper : ouvrir, changer la suite, renommer, changer la levée et retirer éteints (« " + MODO + " »)");
            verifie("absente".equals(GardePortesDebug.lever(mc)), "Helper : pas de « Lever » sur un blocage");
            verifie("ok".equals(GardePortesDebug.action(mc, 4)) && compte("porte_tp") == 1, "Helper : « Aller devant » permis");
            GardePortesDebug.liste(mc);
        }, "actions d'un Helper");
        pas(4, mc -> {
            verifie(("refus : " + MODO).equals(GardePortesDebug.plaque(mc)), "Helper : « Nouvelle porte » éteint");
            int autres = 0;
            for (JsonObject m : RECUS) {
                String a = m.get("a").getAsString();
                if (!"portes".equals(a) && !"porte_tp".equals(a)) autres++;
            }
            verifie(autres == 0, "Helper : aucune demande de modification n'est partie");
            GardePortesDebug.effacerToasts();
        }, "liste d'un Helper");
    }

    /** Dalle refusée par le serveur (recoupe), puis retrait du sceau avec confirmation. */
    private static void serieRetrait() {
        pas(2, mc -> {
            grade = 2;
            PORTES.get("D3").blocages.clear();
            RECUS.clear();
            GardePortesDebug.recevoir(liste(null));
        }, "grade Modérateur");
        pas(4, mc -> {
            GardePortesDebug.plaque(mc);
            h += 250;
            clicSur(mc, ox, oy, oz + 6, false, false);
        }, "nouvelle porte sur le mur déjà scellé");
        pas(5, mc -> {
            verifie(GardePortesDebug.etatDalle(mc) == 4 && "Cette dalle recoupe la porte D3.".equals(GardePortesDebug.raisonDalle(mc)),
                "refus du serveur (« porte_verif » ok = false) : la raison s'affiche (" + GardePortesDebug.raisonDalle(mc) + ")");
            verifie(GardePortesDebug.echap(mc) && "liste".equals(GardePortesDebug.vueCourante(mc)), "Échap : sélecteur sans dalle → liste");
            GardePortesDebug.fiche(mc, "D3");
            GardePortesDebug.souris(mc, -100, -100);
        }, "dalle qui recoupe D3");
        pas(4, mc -> {
            verifie("ok".equals(GardePortesDebug.action(mc, 6)), "« Retirer le sceau… » cliquable");
            verifie(GardePortesDebug.confirmation(mc) && compte("porte_retirer") == 0, "retirer : confirmation dans le volet, rien d'envoyé avant");
        }, "retirer le sceau");
        pas(8, mc -> shot(mc, "portes_retirer"), "capture confirmation du retrait");
        pas(2, mc -> {
            verifie(GardePortesDebug.confirmer(mc), "confirmation validée");
            JsonObject m = dernier("porte_retirer");
            verifie(m != null && cles(m, "a", "id") && "D3".equals(m.get("id").getAsString()), "« porte_retirer » : id");
        }, "confirmer");
        pas(5, mc -> {
            verifie("liste".equals(GardePortesDebug.vueCourante(mc)) && !PORTES.containsKey("D3"), "sceau retiré : retour à la liste");
            GardePortesDebug.effacerToasts();
        }, "porte retirée");
    }

    /* ================================================================ scène */

    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 5, oy, oz - 2, ox + 5, oy + 5, oz + 9));
        // un sol à nous (le banc peut partir en l'air ou sur un relief) : il reste après le banc
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 5, oy - 1, oz - 2, ox + 5, oy - 1, oz + 9));
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 1, oy, oz + 6, ox + 1, oy + 1, oz + 6));
    }

    /** Portes des maquettes, sans D3 (elle est créée par le banc sur le mur) ; celles d'à côté sont derrière le joueur. */
    private static void donnees() {
        PORTES.clear();
        actif = true;
        porte("D4", "Sas de la prison", ox, oy, oz - 10, 2, 3, 1, 3, new int[]{0, 3, 7, 11});
        PORTES.get("D4").etat = "levee";
        PORTES.get("D4").reste = 5;
        porte("D5", "Sas de la prison, intérieur", ox, oy, oz - 13, 2, 3, 1, 3, new int[]{1, 4, 4, 9});
        porte("D1", "Passage sous le temple", ox - 1, oy, oz - 29, 3, 3, 2, 3, new int[]{6, 2, 8, 2, 0, 5});
        PORTES.get("D1").blocages.add(new String[]{"0b7f3e0a-52c1-4c6e-8a55-1d2c3b4a5f60", "Daichi Sarutobi", "45"});
        porte("D2", "Porte des sceaux", ox + 110, oy, oz + 40, 2, 2, 1, 2, new int[]{5, 10, 2});
        porte("D6", "Réserve du Hokage", ox - 200, oy + 12, oz - 90, 4, 3, 1, 3, new int[]{11, 0, 3, 6, 1});
        porte("D7", "Cellule du bas", 14, 40, -210, 2, 2, 1, 2, new int[]{7, 7, 3});
        PORTES.get("D7").monde = "world_nether";
    }

    private static Porte porte(String id, String nom, int x, int y, int z, int dx, int dy, int dz, int leve, int[] signes) {
        Porte d = new Porte();
        d.id = id;
        d.nom = nom;
        d.x = x; d.y = y; d.z = z;
        d.dx = dx; d.dy = dy; d.dz = dz;
        d.leve = leve;
        d.signes = signes;
        PORTES.put(id, d);
        return d;
    }

    /** La porte du mur, telle que le banc l'a créée (pour les séries qui repartent d'un état connu). */
    private static void creerD3() {
        Porte d = porte("D3", "Archives du clan", ox - 1, oy, oz + 6, 3, 2, 1, 2, new int[]{2, 5, 10});
        d.modifPar = "Omar";
        d.modifLe = le(16, 10, 12);
    }

    private static long le(int jour, int heure, int minute) {
        return LocalDateTime.of(2026, 11, jour, heure, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /* ================================================================ serveur simulé : protocole */

    private static void serveur(JsonObject in) {
        RECUS.add(in.deepCopy());
        String a = in.get("a").getAsString(), id = in.has("id") ? in.get("id").getAsString() : null;
        // l'action et l'id seulement : un message peut porter une suite
        System.out.println("[portes] staff → " + a + (id == null ? "" : " " + id));
        if ("portes".equals(a)) { FILE.add(liste(null)); return; }
        if ("porte_tp".equals(a)) { repondre("Téléporté devant " + id + ".", id); return; }
        if (grade < 2) { repondre(MODO, null); return; }
        Porte d = id == null ? null : PORTES.get(id);
        switch (a) {
            case "porte_verifier" -> {
                String m = recoupe(in.getAsJsonObject("boite"));
                String r = "{\"t\":\"porte_verif\",\"n\":" + in.get("n").getAsInt() + ",\"ok\":" + (m == null) + ",\"m\":\"" + (m == null ? "" : m) + "\"}";
                (retenir ? RETENUES : FILE).add(r);
            }
            case "porte_creer" -> {
                JsonObject b = in.getAsJsonObject("boite");
                JsonArray s = in.getAsJsonArray("signes");
                int[] signes = new int[s.size()];
                for (int i = 0; i < signes.length; i++) signes[i] = s.get(i).getAsInt();
                int leve = in.get("leve").getAsInt();
                String neuf = PORTES.containsKey("D3") ? "D" + (PORTES.size() + 2) : "D3";
                porte(neuf, in.get("nom").getAsString(), b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt(),
                    b.get("dx").getAsInt(), b.get("dy").getAsInt(), b.get("dz").getAsInt(), leve == 0 ? b.get("dy").getAsInt() : leve, signes);
                repondre("Porte " + neuf + " créée · " + signes.length + " signes.", neuf);
            }
            case "porte_signes" -> {
                JsonArray s = in.getAsJsonArray("signes");
                d.signes = new int[s.size()];
                for (int i = 0; i < d.signes.length; i++) d.signes[i] = s.get(i).getAsInt();
                modifie(d);
                repondre("Porte " + id + " : suite enregistrée (" + d.signes.length + " signes).", id);
            }
            case "porte_nom" -> { d.nom = in.get("nom").getAsString(); modifie(d); repondre("Porte " + id + " renommée.", id); }
            case "porte_leve" -> { d.leve = in.get("leve").getAsInt(); modifie(d); repondre("Porte " + id + " : levée enregistrée.", id); }
            case "porte_ouvrir" -> { d.etat = "levee"; d.reste = 8; repondre("Porte " + id + " se lève.", id); }
            case "porte_fermer" -> { d.etat = "fermee"; d.reste = -1; repondre("Porte " + id + " se referme.", id); }
            case "porte_debloquer" -> {
                String joueur = in.get("joueur").getAsString();
                d.blocages.removeIf(b -> b[0].equals(joueur));
                repondre("Porte " + id + " : blocage levé.", id);
            }
            case "porte_retirer" -> { PORTES.remove(id); repondre("Porte " + id + " retirée (les blocs restent en place).", id); }
            case "porte_systeme" -> {
                if (grade < 3) { repondre("Réservé aux admins.", null); return; }
                actif = in.get("on").getAsBoolean();
                repondre(actif ? "Mudras allumés pour tout le serveur." : "Mudras éteints pour tout le serveur.", null);
            }
            default -> { }
        }
    }

    private static void modifie(Porte d) {
        d.modifPar = "BancReborn";
        d.modifLe = System.currentTimeMillis();
    }

    /** Un toast, puis la liste à jour (avec {@code dernier}). */
    private static void repondre(String toast, String dernier) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("m", toast);
        FILE.add(o.toString());
        FILE.add(liste(dernier));
    }

    /** La boîte recoupe-t-elle une porte du monde du staff ? (la seule règle de contenu que le banc simule) */
    private static String recoupe(JsonObject b) {
        int x = b.get("x").getAsInt(), y = b.get("y").getAsInt(), z = b.get("z").getAsInt();
        int dx = b.get("dx").getAsInt(), dy = b.get("dy").getAsInt(), dz = b.get("dz").getAsInt();
        for (Porte d : PORTES.values()) {
            if (!"world".equals(d.monde)) continue;
            if (x < d.x + d.dx && d.x < x + dx && y < d.y + d.dy && d.y < y + dy && z < d.z + d.dz && d.z < z + dz)
                return "Cette dalle recoupe la porte " + d.id + ".";
        }
        return null;
    }

    private static double distance(Porte d) {
        if (!"world".equals(d.monde)) return -1;
        var eye = Minecraft.getInstance().player.getEyePosition();
        double dx = Math.max(Math.max(d.x - eye.x, 0), eye.x - (d.x + d.dx));
        double dy = Math.max(Math.max(d.y - eye.y, 0), eye.y - (d.y + d.dy));
        double dz = Math.max(Math.max(d.z - eye.z, 0), eye.z - (d.z + d.dz));
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10) / 10.0;
    }

    /** Message {@code portes} : le monde du staff d'abord, de la plus proche à la plus lointaine ; la suite pour un Modo seulement. */
    private static String liste(String dernier) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "portes");
        o.addProperty("actif", actif);
        o.addProperty("peutGerer", grade >= 2);
        o.addProperty("peutSysteme", grade >= 3);
        o.addProperty("bloque", false);
        o.addProperty("r", 64);
        if (dernier != null) o.addProperty("dernier", dernier);
        List<Porte> tri = new ArrayList<>(PORTES.values());
        tri.sort(Comparator.comparingDouble(d -> distance(d) < 0 ? Double.MAX_VALUE : distance(d)));
        JsonArray l = new JsonArray();
        for (Porte d : tri) {
            JsonObject x = new JsonObject();
            x.addProperty("id", d.id);
            x.addProperty("nom", d.nom);
            x.addProperty("monde", d.monde);
            x.addProperty("x", d.x);
            x.addProperty("y", d.y);
            x.addProperty("z", d.z);
            x.addProperty("dx", d.dx);
            x.addProperty("dy", d.dy);
            x.addProperty("dz", d.dz);
            x.addProperty("leve", d.leve);
            x.addProperty("n", d.signes.length);
            if (grade >= 2 || signesAuHelper) {
                JsonArray s = new JsonArray();
                for (int i : d.signes) s.add(i);
                x.add("signes", s);
            }
            x.addProperty("etat", d.etat);
            x.addProperty("reste", d.reste);
            x.addProperty("dist", distance(d));
            JsonObject cree = new JsonObject();
            cree.addProperty("par", d.creePar);
            cree.addProperty("le", d.creeLe);
            x.add("cree", cree);
            if (!d.modifPar.isEmpty()) {
                JsonObject modif = new JsonObject();
                modif.addProperty("par", d.modifPar);
                modif.addProperty("le", d.modifLe);
                x.add("modif", modif);
            }
            JsonArray bl = new JsonArray();
            for (String[] b : d.blocages) {
                JsonObject j = new JsonObject();
                j.addProperty("u", b[0]);
                j.addProperty("nom", b[1]);
                j.addProperty("reste", Integer.parseInt(b[2]));
                bl.add(j);
            }
            x.add("blocages", bl);
            l.add(x);
        }
        o.add("l", l);
        return o.toString();
    }

    /* ================================================================ messages reçus */

    private static int compte(String a) {
        int n = 0;
        for (JsonObject m : RECUS) if (a.equals(m.get("a").getAsString())) n++;
        return n;
    }

    private static JsonObject dernier(String a) {
        for (int i = RECUS.size() - 1; i >= 0; i--) if (a.equals(RECUS.get(i).get("a").getAsString())) return RECUS.get(i);
        return null;
    }

    /** Le message porte exactement ces champs. */
    private static boolean cles(JsonObject m, String... k) { return m != null && m.keySet().equals(Set.of(k)); }

    private static boolean boiteVaut(JsonObject m, int x, int y, int z, int dx, int dy, int dz) {
        if (m == null || !m.has("boite") || !m.get("boite").isJsonObject()) return false;
        JsonObject b = m.getAsJsonObject("boite");
        return cles(b, "x", "y", "z", "dx", "dy", "dz") && b.get("x").getAsInt() == x && b.get("y").getAsInt() == y && b.get("z").getAsInt() == z
            && b.get("dx").getAsInt() == dx && b.get("dy").getAsInt() == dy && b.get("dz").getAsInt() == dz;
    }

    /** {@code signes} est un tableau de ces ordinaux, dans l'ordre (comparé sans jamais l'écrire). */
    private static boolean suiteVaut(JsonObject m, int... signes) {
        if (m == null || !m.has("signes") || !m.get("signes").isJsonArray()) return false;
        JsonArray s = m.getAsJsonArray("signes");
        if (s.size() != signes.length) return false;
        for (int i = 0; i < signes.length; i++) {
            var e = s.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || e.getAsInt() != signes[i]) return false;
        }
        return true;
    }

    /* ================================================================ outils */

    /** La souris du volet sur la face nord du bloc (x, y, z), ou sur son dessus ({@code haut}). */
    private static boolean sourisSur(Minecraft mc, int x, int y, int z, boolean haut) {
        boolean vu = haut ? ProjectionMonde.versGui(x + 0.5, y + 1.0, z + 0.5, V) : ProjectionMonde.versGui(x + 0.5, y + 0.5, z - 0.01, V);
        if (!vu) { verifie(false, "bloc hors écran"); return false; }
        GardePortesDebug.souris(mc, V.x, V.y);
        return true;
    }

    private static void clicSur(Minecraft mc, int x, int y, int z, boolean maj, boolean haut) {
        if (sourisSur(mc, x, y, z, haut)) GardePortesDebug.clicMonde(mc, V.x, V.y, maj);
    }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[portes] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[portes] " + nom);
            action.accept(mc);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        System.out.println("[portes] capture " + name);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        mc.getFramerateLimitTracker().onInputReceived();
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (volant) vol(mc, true);
        // réponses du serveur simulé : une par tick, comme le réseau
        String r = FILE.poll();
        if (r != null) GardePortesDebug.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine || images - imagesEtape < IMAGES_ENTRE_ETAPES) return;
        ETAPES.get(etape).accept(mc);
        imagesEtape = images;
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
