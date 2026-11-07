package fr.reborn.hud.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.anchor.ProjectionMonde;
import fr.reborn.hud.concentration.ConcentrationCanal;
import fr.reborn.hud.concentration.HaloPlaque;
import fr.reborn.hud.concentration.ModeConcentration;
import fr.reborn.hud.concentration.Plaques;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.recit.Gabarit;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.recit.RecitHud;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.staff.GardePlaquesDebug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Banc de captures du lot 4 « Concentration », <b>étape B</b> (plaques), <b>inactif en production</b> :
 * {@code REBORN_LOT4_DEBUG=b} (monde solo de dev, jamais de serveur). Le serveur est <b>simulé côté client</b> :
 * {@link ConcentrationCanal#debugServeur} répond à {@code on} (état accepté, ancres de la scène) et à {@code lire} (le
 * texte), {@link GardePlaquesDebug#serveur} aux actions {@code plaque*} du Poste de garde. Les règles du serveur
 * (portée + 1,5, ligne de vue, droits, fichier) sont couvertes par les tests JUnit de ShinobiCore.
 *
 * <p>Scène bâtie autour du joueur (rendue à l'air à la fin) : stèle A (zone 1, lanterne), deux stèles voisines (zone 2),
 * une stèle derrière un mur (zone 3), une stèle de portée 3 (zone 4), une stèle au texte long (zone 5). Horloges figées
 * ({@link Recit#horloge}, {@link Plaques#horloge}, {@link ModeConcentration#horloge}) pour les images du fondu et du halo.
 * <ol>
 *   <li>1920 × 1080, jour : lecture automatique à 2, 4 et 6 blocs ; halo à 0, 120, 250 ms, sortie à 175 ms, fin ;
 *       deux stèles (la plus centrée, puis l'autre après un pas de côté) ; hors de portée ; mur entre les deux ; narration du
 *       staff qui passe devant puis la plaque qui revient ; texte long (page 1, page 2).</li>
 *   <li>Nuit : 2, 4, 6 blocs.</li>
 *   <li>Outil « Plaques » : liste et repères, fiche avec aperçu, sélecteur (fil de fer, bloc choisi en or, boîte de deux
 *       coins), éditeur avec aperçu en direct.</li>
 *   <li>Les captures clés à nouveau en 1366 × 768.</li>
 * </ol>
 * → {@code run/screenshots/lot4b_*.png}, bilans « [lot4b] OK / ECHEC » au journal ; le jeu se ferme (« [lot4b] FIN »).
 */
public final class Lot4bDebug {

    private static final List<Consumer<Minecraft>> ETAPES = new ArrayList<>();
    private static final List<Integer> DELAIS = new ArrayList<>();
    private static int t = -1, etape, prochaine, echecs;
    private static long heure = -1;
    private static boolean pluie, volant;
    private static String mode = "survival", difficulte = "normal";
    private static long h;
    /** Origine de la scène (pieds du joueur au départ). */
    private static int ox, oy, oz;
    private static final Vector3f V = new Vector3f();
    /** Réponses du serveur simulé, rendues au tick suivant (comme le réseau). */
    private static final ArrayDeque<String> FILE = new ArrayDeque<>();

    private record Pl(String id, String titre, int dx0, int dz0, int h, int portee, String texte) { }

    private static final String TEXTE_A = "Ici reposent les <or>gardiens de la porte Est</>. Ils ont tenu la <rouge>lame</> "
        + "jusqu'à l'aube, sans un mot ; que leurs <ombre>ombres</> veillent encore sur ce passage.";
    private static final String TEXTE_B1 = "Salle du serment. Nul ne franchit ce seuil sans avoir juré devant le <rouge>feu</> de la Feuille.";
    private static final String TEXTE_B2 = "Borne du sud. Au-delà de cette pierre, nul ne passe sans le <or>laissez-passer</> du Hokage.";
    private static final String TEXTE_W = "Ce texte est derrière le mur : il ne doit jamais s'afficher.";
    private static final String TEXTE_R = "Plaque de portée 3 : trop loin, elle reste muette.";
    private static final String TEXTE_L = "Voyageur, si tu lis ces mots, c'est que tu as su faire silence. Ce sanctuaire fut bâti par "
        + "les premiers gardiens du village, au temps où la <or>porte Est</> n'était qu'une palissade de bois.\n\nIls ont déposé "
        + "ici leurs armes avant chaque saison de guerre, et les ont reprises en silence. Beaucoup ne sont pas revenus. Leurs "
        + "<ombre>ombres</> marchent encore entre les pierres, dit-on, et ne craignent que ceux qui oublient leur nom.\n\nSi tu "
        + "portes un nom de ce village, incline-toi. Si tu viens d'ailleurs, passe ton chemin sans toucher aux offrandes : la "
        + "<rouge>lame</> des gardiens n'a jamais connu le repos, et la nuit tombe vite sur ceux qui manquent de respect aux "
        + "morts. Que la Feuille garde ta route.";

    /** Plaques de la scène : id, titre, décalage x, décalage z (depuis l'origine), hauteur, portée, texte. */
    private static final Map<String, Pl> PL = new LinkedHashMap<>();

    static {
        PL.put("P1A", new Pl("P1A", "Stèle du premier gardien", 1, 6, 2, 6, TEXTE_A));
        PL.put("P2B", new Pl("P2B", "Salle du serment", 12, 6, 2, 6, TEXTE_B1));
        PL.put("P2C", new Pl("P2C", "Borne du sud", 14, 6, 2, 6, TEXTE_B2));
        PL.put("P3D", new Pl("P3D", "Derrière le mur", -10, 6, 2, 6, TEXTE_W));
        PL.put("P4E", new Pl("P4E", "Portée courte", 22, 6, 2, 3, TEXTE_R));
        PL.put("P5F", new Pl("P5F", "Sanctuaire", 32, 6, 2, 6, TEXTE_L));
    }

    private Lot4bDebug() {}

    public static void init() {
        if (!"b".equalsIgnoreCase(String.valueOf(System.getenv("REBORN_LOT4_DEBUG")))) return;

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
            // pas de monstre qui passe devant une stèle (un creeper choisi par le sélecteur) : paisible le temps du banc
            difficulte = mc.level.getDifficulty().getSerializedName();
            c.sendCommand("difficulty peaceful");
            for (int i = 0; i < 9; i++) c.sendCommand("item replace entity @s hotbar." + i + " with minecraft:air");
            ox = mc.player.getBlockX();
            oy = mc.player.getBlockY();
            oz = mc.player.getBlockZ();
            volant = true;
            vol(mc, true);
        }, "préparation");
        pas(10, Lot4bDebug::batir, "scène");
        pas(30, mc -> {
            figer(Util.getMillis());
            ConcentrationCanal.debugServeur = Lot4bDebug::serveur;
            System.out.println("[lot4b] origine " + ox + " " + oy + " " + oz + ", halo " + HaloPlaque.palette);
        }, "horloges figées, serveur simulé");

        serieLecture("1080", "jour", true);
        pas(2, mc -> mc.player.connection.sendCommand("time set 18000"), "nuit");
        pas(20, mc -> { }, "nuit tombée");
        serieLecture("1080", "nuit", false);
        pas(2, mc -> mc.player.connection.sendCommand("time set 6000"), "jour");
        pas(10, mc -> { }, "jour revenu");
        serieOutil("1080");

        // ───────── 1366 × 768
        pas(2, mc -> { mc.getWindow().setWindowed(1366, 768); mc.resizeGui(); }, "fenêtre 1366 × 768");
        pas(20, mc -> { }, "fenêtre posée");
        serieLecture("1366", "jour", false);
        serieOutil("1366");

        pas(10, mc -> {
            ModeConcentration.reinitialiser();
            ModeConcentration.horloge = Util::getMillis;
            Plaques.horloge = Util::getMillis;
            Recit.horloge = Util::getMillis;
            ConcentrationCanal.debugServeur = null;
            InteractionMode.INSTANCE.debugClose();
            GardePlaquesDebug.fermer(mc);
            var c = mc.player.connection;
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 13, oy, oz - 4, ox + 36, oy + 5, oz + 16));
            if (heure >= 0) c.sendCommand("time set " + heure);
            c.sendCommand(pluie ? "weather rain" : "weather clear");
            c.sendCommand("difficulty " + difficulte);
            c.sendCommand(String.format("tp @s %d %d %d 0 8", ox, oy, oz));
            RebornSession.debugForceRp = false;
        }, "remise en état");
        pas(10, mc -> { volant = false; vol(mc, false); mc.player.connection.sendCommand("gamemode " + mode); }, "mode de jeu");
        pas(10, mc -> {
            System.out.println("[lot4b] FIN (" + echecs + " échec(s))");
            mc.stop();
        }, "fin");
        ClientTickEvents.END_CLIENT_TICK.register(Lot4bDebug::tick);
    }

    /* ================================================================ scène */

    private static void batir(Minecraft mc) {
        var c = mc.player.connection;
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:air", ox - 13, oy, oz - 4, ox + 36, oy + 8, oz + 16));
        // un sol à nous (le banc peut partir en l'air ou sur un relief) : il reste après le banc, la scène repart d'ici
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", ox - 13, oy - 1, oz - 4, ox + 36, oy - 1, oz + 16));
        for (Pl p : PL.values()) {
            int x = ox + p.dx0(), z = oz + p.dz0();
            c.sendCommand(String.format("setblock %d %d %d minecraft:stone_bricks", x, oy, z));
            c.sendCommand(String.format("setblock %d %d %d minecraft:chiseled_stone_bricks", x, oy + 1, z));
            // une allée vers chaque stèle
            c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:dirt_path", x - 1, oy - 1, z - 7, x, oy - 1, z - 1));
        }
        // lanterne de la zone 1 (la nuit)
        c.sendCommand(String.format("setblock %d %d %d minecraft:oak_fence", ox + 3, oy, oz + 6));
        c.sendCommand(String.format("setblock %d %d %d minecraft:oak_fence", ox + 3, oy + 1, oz + 6));
        c.sendCommand(String.format("setblock %d %d %d minecraft:lantern", ox + 3, oy + 2, oz + 6));
        // le mur de la zone 3
        c.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:stone_bricks", ox - 12, oy, oz + 4, ox - 8, oy + 3, oz + 4));
    }

    /**
     * Joueur posé à {@code d} blocs de la stèle {@code id} (face nord), regard au sud, un bloc à gauche de la stèle : la
     * caméra à l'épaule la voit à droite du personnage (comme la planche c4), pas cachée derrière lui. Le yaw ne sert qu'à
     * l'entrée : en concentration la caméra est figée.
     */
    private static void devant(Minecraft mc, String id, double d, float yaw) {
        Pl p = PL.get(id);
        a(mc, ox + p.dx0() + 2.0, oz + p.dz0() - d, yaw);
    }

    /** Joueur posé en (x, z) absolus, au sol de la scène. */
    private static void a(Minecraft mc, double x, double z, float yaw) {
        mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %d %.2f %.1f 6", x, oy, z, yaw));
    }

    /* ================================================================ serveur simulé */

    private static void serveur(JsonObject in) {
        String a = in.has("a") ? in.get("a").getAsString() : "";
        switch (a) {
            case "on" -> {
                FILE.add("{\"v\":1,\"t\":\"etat\",\"on\":true}");
                FILE.add(ancres().toString());
            }
            case "lire" -> {
                String id = in.get("id").getAsString();
                Pl p = PL.get(id);
                if (p == null) { FILE.add("{\"v\":1,\"t\":\"texte_refus\",\"id\":\"" + id + "\"}"); return; }
                JsonObject o = new JsonObject();
                o.addProperty("v", 1);
                o.addProperty("t", "texte");
                o.addProperty("id", id);
                o.addProperty("rev", 1);
                o.add("segs", segs(p.texte()));
                FILE.add(o.toString());
                System.out.println("[lot4b] serveur : texte de " + id + " envoyé");
            }
            default -> { }
        }
    }

    private static JsonObject ancres() {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "ancres");
        JsonArray l = new JsonArray();
        for (Pl p : PL.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", p.id());
            x.addProperty("rev", 1);
            x.addProperty("k", "bloc");
            x.addProperty("x", ox + p.dx0());
            x.addProperty("y", oy);
            x.addProperty("z", oz + p.dz0());
            JsonArray b = new JsonArray();
            b.add(1);
            b.add(p.h());
            b.add(1);
            x.add("b", b);
            x.addProperty("p", p.portee());
            l.add(x);
        }
        o.add("l", l);
        return o;
    }

    private static JsonArray segs(String balise) {
        JsonArray a = new JsonArray();
        for (Recit.Segment s : Recit.segments(balise)) {
            JsonArray x = new JsonArray();
            x.add(s.texte());
            x.add(s.couleur());
            a.add(x);
        }
        return a;
    }

    /* ================================================================ lecture */

    private static void serieLecture(String res, String moment, boolean complete) {
        String p = "lot4b_" + res + "_" + moment + "_";
        int[] distances = complete || "nuit".equals(moment) || "1366".equals(res) ? new int[]{2, 4, 6} : new int[]{4};
        for (int d : distances) {
            double dd = d == 6 ? 5.9 : d;
            pas(2, mc -> devant(mc, "P1A", dd, 0f), "devant P1A à " + d + " blocs");
            pas(15, Lot4bDebug::entrer, "entrée");
            pas(14, mc -> { avance(300); }, "fondus finis (texte arrivé)");
            pas(6, mc -> {
                verifie("P1A".equals(Plaques.lue()), res + " " + moment + " " + d + " blocs : P1A lue automatiquement (" + Plaques.bilan() + ")");
                verifie(RecitHud.ancree(), res + " " + moment + " " + d + " blocs : texte ancré devant l'objet (palier " + RecitHud.palier() + " px)");
                shot(mc, p + "lecture_" + d + "b");
            }, "capture " + d + " blocs");
            pas(1, mc -> sortir(), "sortie");
        }
        if (!complete) return;
        // halo : 0, 120, 250 ms, sortie à 175 ms, fin (la stèle à 4 blocs ; le mode est resté)
        pas(1, mc -> sortir(), "sortie (halo)");
        pas(4, mc -> devant(mc, "P1A", 4, 0f), "devant P1A");
        pas(10, Lot4bDebug::entrer, "entrée");
        pas(1, mc -> avance(250), "effet d'écran plein");
        pas(10, mc -> {
            verifie(RecitHud.opacite() <= 0.01f, "halo 0 ms : rien encore (opacité " + RecitHud.opacite() + ")");
            shot(mc, p + "halo_000ms");
        }, "capture 0 ms");
        pas(1, mc -> avance(120), "120 ms");
        pas(10, mc -> {
            float a = RecitHud.opacite();
            verifie(a > 0.5f && a < 0.95f, "halo 120 ms : à mi-fondu (opacité " + a + ")");
            shot(mc, p + "halo_120ms");
        }, "capture 120 ms");
        pas(1, mc -> avance(130), "250 ms");
        pas(10, mc -> {
            verifie(RecitHud.opacite() >= 0.99f, "halo 250 ms : plein (opacité " + RecitHud.opacite() + ")");
            shot(mc, p + "halo_250ms");
        }, "capture 250 ms");
        // le joueur recule hors de portée : grâce de 150 ms, puis fondu de sortie de 350 ms
        pas(1, mc -> devant(mc, "P1A", 8.5, 0f), "recul hors de portée");
        pas(4, mc -> avance(160), "grâce passée");
        pas(2, mc -> avance(175), "175 ms de sortie");
        pas(8, mc -> {
            float a = RecitHud.opacite();
            verifie(Plaques.choisie() == null && a > 0.2f && a < 0.8f, "sortie 175 ms : à mi-fondu (opacité " + a + ", " + Plaques.bilan() + ")");
            shot(mc, p + "halo_sortie_175ms");
        }, "capture sortie");
        pas(1, mc -> avance(400), "sortie finie");
        pas(10, mc -> {
            verifie(RecitHud.opacite() <= 0.01f, "sortie finie : plus rien (opacité " + RecitHud.opacite() + ")");
            shot(mc, p + "halo_fin");
        }, "capture fin");
        pas(1, mc -> sortir(), "sortie");
        // deux stèles devant : la plus centrée, puis l'autre après un pas de côté
        pas(2, mc -> a(mc, ox + 13.3, oz + 2, 0f), "zone 2 : entre les deux stèles, P2B la plus centrée");
        pas(12, Lot4bDebug::entrer, "entrée");
        pas(14, mc -> avance(300), "fondus");
        pas(10, mc -> {
            verifie("P2B".equals(Plaques.lue()), "deux stèles : la plus centrée (P2B) est lue (" + Plaques.bilan() + ")");
            shot(mc, p + "deux_plaques_centree");
        }, "capture deux plaques");
        pas(2, mc -> a(mc, ox + 15.3, oz + 2, 0f), "un pas à gauche : P2C devient la plus centrée");
        pas(6, mc -> avance(160), "coupe");
        pas(6, mc -> avance(300), "fondu");
        pas(6, mc -> {
            verifie("P2C".equals(Plaques.lue()), "un pas de côté : P2C prend la main (" + Plaques.bilan() + ")");
            shot(mc, p + "deux_plaques_autre");
        }, "capture autre plaque");
        pas(1, mc -> sortir(), "sortie");
        // hors de portée
        pas(2, mc -> devant(mc, "P4E", 5, 0f), "zone 4 : portée 3, à 5 blocs");
        pas(12, Lot4bDebug::entrer, "entrée");
        pas(14, mc -> avance(300), "fondus");
        pas(10, mc -> {
            verifie(Plaques.choisie() == null && !RecitHud.ancree(), "hors de portée : rien (" + Plaques.bilan() + ")");
            shot(mc, p + "hors_portee");
        }, "capture hors portée");
        pas(1, mc -> sortir(), "sortie");
        // mur entre les deux
        pas(2, mc -> devant(mc, "P3D", 3.5, 0f), "zone 3 : stèle derrière un mur");
        pas(12, Lot4bDebug::entrer, "entrée");
        pas(14, mc -> avance(300), "fondus");
        pas(10, mc -> {
            verifie(Plaques.choisie() == null && !RecitHud.ancree(), "mur entre les deux : rien (" + Plaques.bilan() + ")");
            shot(mc, p + "mur");
        }, "capture mur");
        pas(1, mc -> sortir(), "sortie");
        // narration du staff qui passe devant, puis la plaque revient
        pas(2, mc -> devant(mc, "P1A", 4, 0f), "zone 1");
        pas(12, Lot4bDebug::entrer, "entrée");
        pas(14, mc -> avance(300), "fondus");
        pas(6, mc -> Recit.montrer("narration-banc", Recit.segments("Au loin, la <rouge>cloche</> du village sonne trois coups."),
            6, Recit.Mode.REMPLACE, Gabarit.NARRATION), "narration");
        pas(6, mc -> avance(200), "coupe de la plaque");
        pas(6, mc -> avance(700), "narration entrée");
        pas(6, mc -> {
            verifie(!RecitHud.ancree(), "narration : la plaque s'efface, la narration passe devant (" + RecitHud.bilan() + ")");
            shot(mc, p + "narration_devant");
        }, "capture narration");
        pas(2, mc -> Recit.cacher("narration-banc"), "fin de la narration");
        pas(4, mc -> avance(800), "narration sortie");
        pas(6, mc -> avance(500), "plaque revenue");
        pas(6, mc -> {
            verifie(RecitHud.ancree() && "P1A".equals(Plaques.lue()), "après la narration : la plaque revient (" + RecitHud.bilan() + ")");
            shot(mc, p + "narration_retour");
        }, "capture retour");
        pas(1, mc -> sortir(), "sortie");
        // texte long : pages
        pas(2, mc -> devant(mc, "P5F", 4, 0f), "zone 5 : texte long");
        pas(12, Lot4bDebug::entrer, "entrée");
        pas(14, mc -> avance(300), "fondus");
        pas(10, mc -> {
            verifie("P5F".equals(Plaques.lue()), "texte long lu (" + RecitHud.bilan() + ")");
            shot(mc, p + "pages_1");
        }, "capture page 1");
        pas(1, mc -> verifie(Plaques.clicPage(), "clic gauche : page suivante"), "clic");
        pas(4, mc -> avance(200), "fondu de page");
        pas(6, mc -> shot(mc, p + "pages_2"), "capture page 2");
        pas(1, mc -> sortir(), "sortie");
    }

    /* ================================================================ outil « Plaques » */

    private static final String SNAP = "{\"t\":\"snap\",\"grade\":3,\"modeStaff\":true,\"modeAnim\":false,\"animPossible\":true,"
        + "\"staffOnline\":1,\"players\":[],\"alerts\":[],\"chat\":[],\"commands\":[]}";

    private static void serieOutil(String res) {
        String p = "lot4b_" + res + "_outil_";
        pas(2, mc -> a(mc, ox + 16.5, oz + 1, 8f), "devant les stèles de la zone 2, à gauche");
        pas(10, mc -> {
            GardePlaquesDebug.serveur(Lot4bDebug::serveurStaff);
            GardePlaquesDebug.ouvrir(mc, SNAP);
        }, "Poste de garde › Outils › Plaques");
        pas(12, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
            shot(mc, p + "liste");
        }, "capture liste");
        pas(2, mc -> GardePlaquesDebug.fiche(mc, "P2B", true), "fiche P2B + aperçu");
        pas(12, mc -> shot(mc, p + "fiche_apercu"), "capture fiche");
        // sélecteur : survol, choix, boîte
        pas(2, mc -> {
            GardePlaquesDebug.liste(mc);
            GardePlaquesDebug.editeur(mc, "", "", 6, null, null, null);
            GardePlaquesDebug.selecteur(mc);
        }, "éditeur, sélecteur ouvert");
        pas(4, mc -> sourisSur(mc, ox + 14, oy + 1, oz + 6), "souris sur P2C (haut)");
        pas(8, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
            verifie(GardePlaquesDebug.bilan(mc).contains("visé 1"), res + " sélecteur : bloc survolé (fil de fer)");
            shot(mc, p + "selecteur_survol");
        }, "capture survol");
        pas(1, mc -> clicSur(mc, ox + 14, oy, oz + 6, false), "clic : bloc du bas");
        pas(1, mc -> sourisSur(mc, ox + 13, oy + 2, oz + 6), "souris ailleurs");
        pas(8, mc -> {
            verifie(GardePlaquesDebug.bilan(mc).contains("choisi 1"), res + " sélecteur : bloc choisi en or");
            shot(mc, p + "selecteur_bloc");
        }, "capture bloc choisi");
        // Maj enfoncée : aperçu de la boîte jusqu'au bloc survolé, avant le clic (c5-b, or à 55 %)
        pas(1, mc -> {
            GardePlaquesDebug.maj(true);
            sourisSur(mc, ox + 12, oy + 1, oz + 6);
        }, "Maj enfoncée, souris sur le second coin");
        pas(8, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
            verifie(GardePlaquesDebug.bilan(mc).contains("repères 3"), res + " sélecteur : aperçu de la boîte sous Maj (choisi + survol + aperçu)");
            shot(mc, p + "selecteur_maj_apercu");
            GardePlaquesDebug.maj(false);
        }, "capture aperçu Maj");
        // bloc seul validé : l'éditeur propose « côté nord seulement » / « tous côtés »
        pas(1, mc -> verifie(GardePlaquesDebug.valider(mc), res + " bloc seul validé"), "valider le bloc");
        pas(10, mc -> {
            String b = GardePlaquesDebug.bilan(mc) + " · " + GardePlaquesDebug.apercu(mc);
            System.out.println("[lot4b] " + b);
            verifie(b.contains("\"f\":\"north\"") && b.contains("face north seule"), res + " éditeur : bloc lisible du côté cliqué (face nord)");
            shot(mc, p + "editeur_face");
        }, "capture éditeur, face");
        pas(2, mc -> GardePlaquesDebug.rouvrir(mc), "sélecteur rouvert (choix gardé)");
        // colonne du volet, sous le bandeau replié : on y vise encore le monde
        pas(1, mc -> GardePlaquesDebug.sousBandeau(mc), "souris sous le bandeau");
        pas(8, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
            verifie(!GardePlaquesDebug.bilan(mc).contains("visé 0"), res + " sélecteur : survol sous le bandeau du volet");
            shot(mc, p + "selecteur_sous_bandeau");
        }, "capture sous le bandeau");
        pas(1, mc -> clicSur(mc, ox + 12, oy + 1, oz + 6, true), "Maj + clic : second coin");
        pas(8, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
            verifie(GardePlaquesDebug.bilan(mc).contains("choisi 2"), res + " sélecteur : boîte de deux coins");
            shot(mc, p + "selecteur_boite");
        }, "capture boîte");
        pas(1, mc -> verifie(GardePlaquesDebug.valider(mc), res + " sélecteur validé (Entrée)"), "valider");
        pas(2, mc -> GardePlaquesDebug.editeur(mc, "Ici repose le <or>premier gardien</> de la porte Est. Il a tenu seul "
                + "jusqu'à l'aube, la <rouge>lame</> brisée. Que les <ombre>ombres</> ne passent jamais ce seuil.",
            "Stèle des trois gardiens", 6, boiteJson(), "Boîte 3 × 2 × 1 · 6 blocs",
            new double[]{ox + 12, oy, oz + 6, ox + 15, oy + 2, oz + 7}), "éditeur rempli");
        pas(16, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
            shot(mc, p + "editeur_apercu");
        }, "capture éditeur");
        // texte long : le staff voit le découpage des pages dans l'aperçu (« Page 1 / 2 › »)
        pas(2, mc -> GardePlaquesDebug.editeur(mc, TEXTE_L, "Sanctuaire des premiers gardiens", 6, boiteJson(),
            "Boîte 3 × 2 × 1 · 6 blocs", new double[]{ox + 12, oy, oz + 6, ox + 15, oy + 2, oz + 7}), "éditeur, texte long");
        pas(16, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.apercu(mc));
            verifie(!GardePlaquesDebug.apercu(mc).contains("/ 1,") && !GardePlaquesDebug.apercu(mc).contains("/ 0,"), res + " aperçu : texte long en plusieurs pages");
            shot(mc, p + "editeur_apercu_page1");
            GardePlaquesDebug.pageSuivante(mc);
        }, "capture aperçu page 1");
        pas(12, mc -> {
            System.out.println("[lot4b] " + GardePlaquesDebug.apercu(mc));
            verifie(GardePlaquesDebug.apercu(mc).contains("page 2 /"), res + " aperçu : page 2 montrée");
            shot(mc, p + "editeur_apercu_page2");
        }, "capture aperçu page 2");
        pas(2, mc -> GardePlaquesDebug.fermer(mc), "volet fermé");
    }

    private static JsonObject boiteJson() {
        JsonObject a = new JsonObject();
        a.addProperty("k", "bloc");
        a.addProperty("x", ox + 12);
        a.addProperty("y", oy);
        a.addProperty("z", oz + 6);
        JsonArray b = new JsonArray();
        b.add(3);
        b.add(2);
        b.add(1);
        a.add("b", b);
        return a;
    }

    /** Serveur simulé de l'outil : liste des plaques de la scène (distances réelles), créations acceptées. */
    private static void serveurStaff(JsonObject in) {
        String a = in.get("a").getAsString();
        System.out.println("[lot4b] staff → " + in);
        if ("plaques".equals(a)) GardePlaquesDebug.recevoir(listeStaff(null).toString());
    }

    private static JsonObject listeStaff(String dernier) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        o.addProperty("t", "plaques");
        o.addProperty("grade", 3);
        o.addProperty("anim", false);
        o.addProperty("peutCreer", true);
        o.addProperty("temporaire", false);
        o.addProperty("peutRestaurer", true);
        o.addProperty("voirTout", true);
        o.addProperty("rayon", 64);
        o.addProperty("texteMax", 700);
        if (dernier != null) o.addProperty("dernier", dernier);
        JsonArray l = new JsonArray();
        var eye = mc.player.getEyePosition();
        int i = 0;
        for (Pl p : PL.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", p.id());
            x.addProperty("titre", p.titre());
            String plat = Recit.segments(p.texte()).stream().map(Recit.Segment::texte).reduce("", String::concat);
            x.addProperty("texte", plat.length() > 160 ? plat.substring(0, 160) : plat);
            x.add("segs", segs(p.texte()));
            x.addProperty("k", "bloc");
            x.addProperty("monde", "world");
            x.addProperty("x", ox + p.dx0());
            x.addProperty("y", oy);
            x.addProperty("z", oz + p.dz0());
            JsonArray b = new JsonArray();
            b.add(1);
            b.add(p.h());
            b.add(1);
            x.add("b", b);
            double dx = Math.max(Math.max(ox + p.dx0() - eye.x, 0), eye.x - (ox + p.dx0() + 1));
            double dz = Math.max(Math.max(oz + p.dz0() - eye.z, 0), eye.z - (oz + p.dz0() + 1));
            x.addProperty("dist", Math.round(Math.sqrt(dx * dx + dz * dz) * 10) / 10.0);
            x.addProperty("p", p.portee());
            x.addProperty("actif", true);
            x.addProperty("expire", "P4E".equals(p.id()) ? 1380 : 0);
            x.addProperty("temporaire", "P4E".equals(p.id()));
            x.addProperty("rev", 1);
            x.addProperty("par", i % 2 == 0 ? "Omar" : "Kaede");
            x.addProperty("le", "07/11 18:40");
            x.addProperty("modifPar", "");
            x.addProperty("modifLe", "");
            x.addProperty("lectures", 37 - i * 6);
            x.addProperty("moi", i % 2 == 0);
            x.addProperty("peutModifier", true);
            l.add(x);
            i++;
        }
        o.add("l", l);
        JsonArray c = new JsonArray();
        JsonObject x = new JsonObject();
        x.addProperty("id", "P0Z");
        x.addProperty("titre", "Ancienne pancarte");
        x.addProperty("texte", "Fermé pour travaux.");
        x.addProperty("k", "bloc");
        x.addProperty("monde", "world");
        x.addProperty("par", "Kaede");
        x.addProperty("le", "05/11 21:10");
        x.addProperty("motif", "doublon");
        x.addProperty("reste", 28);
        c.add(x);
        o.add("corbeille", c);
        return o;
    }

    /** La souris du volet sur le centre de la face nord du bloc (x, y, z) (projeté comme le monde). */
    private static void sourisSur(Minecraft mc, int x, int y, int z) {
        if (!ProjectionMonde.versGui(x + 0.5, y + 0.5, z - 0.01, V)) { verifie(false, "bloc hors écran"); return; }
        GardePlaquesDebug.souris(mc, V.x, V.y);
    }

    private static void clicSur(Minecraft mc, int x, int y, int z, boolean maj) {
        if (!ProjectionMonde.versGui(x + 0.5, y + 0.5, z - 0.01, V)) { verifie(false, "bloc hors écran"); return; }
        GardePlaquesDebug.souris(mc, V.x, V.y);
        GardePlaquesDebug.clicMonde(mc, V.x, V.y, maj);
        System.out.println("[lot4b] " + GardePlaquesDebug.bilan(mc));
    }

    /* ================================================================ outils */

    private static void entrer(Minecraft mc) {
        avance(300);   // anti-rebond
        boolean ok = ModeConcentration.entrer();
        verifie(ok, "entrée en concentration (refus : " + ModeConcentration.dernierRefus() + ")");
    }

    private static void sortir() {
        ModeConcentration.sortir(ModeConcentration.Raison.TOUCHE);
        avance(600);
    }

    private static void figer(long now) {
        h = now;
        ModeConcentration.horloge = () -> h;
        Plaques.horloge = () -> h;
        Recit.horloge = () -> h;
    }

    private static void avance(long ms) { h += ms; }

    private static void vol(Minecraft mc, boolean on) {
        var ab = mc.player.getAbilities();
        ab.flying = on && ab.mayfly;
        mc.player.onUpdateAbilities();
    }

    private static void verifie(boolean ok, String quoi) {
        if (!ok) echecs++;
        System.out.println("[lot4b] " + (ok ? "OK " : "ECHEC ") + quoi);
    }

    private static void pas(int delai, Consumer<Minecraft> action, String nom) {
        DELAIS.add(delai);
        ETAPES.add(mc -> {
            System.out.println("[lot4b] " + nom);
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
        // réponses du serveur simulé : une par tick, comme le réseau
        String r = FILE.poll();
        if (r != null) ConcentrationCanal.recevoir(r);
        t++;
        if (t == 0) prochaine = 60 + DELAIS.get(0);
        if (etape >= ETAPES.size() || t < prochaine) return;
        ETAPES.get(etape).accept(mc);
        etape++;
        if (etape < ETAPES.size()) prochaine = t + DELAIS.get(etape);
    }
}
