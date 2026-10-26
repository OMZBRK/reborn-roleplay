package fr.reborn.hud.douane;

import fr.reborn.hud.nameplate.Nameplates;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.staff.GardeDouaneDebug;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Banc de captures de la douane (lot E, 25/10), <b>inactif en production</b> ({@code REBORN_DOUANE_DEBUG=1}, monde solo
 * de dev, jamais de serveur) : rejoue des messages du canal {@code reborn:douane} au format exact du protocole v1
 * (PLUGINS_REFERENCE §4 decies.4 à .6) et capture chaque écran dans {@code run/screenshots/douane_*.png}, puis ferme le
 * jeu. Côté candidat : hall (ligne, rappel, plaque du pseudo sur un mannequin), hall fermé, vérification de l'accès,
 * appel et fondu, bureau (plaque « Examinateur »), question, Bienvenue, résultats. Côté examinateur : onglet Douane du
 * Poste de garde (file, survol), dossier en volet élargi, questions et décision, envoi en attente, retrait, indicateur
 * du mode, puis la file et la ligne en 1366 × 768. Les demandes C → S sont interceptées (journal).
 *
 * <p>Le monde est remis en état à la fin : mannequin retiré, mode de jeu, heure, météo, échelle et fenêtre rendus.
 */
public final class DouaneDebug {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-douane-banc");
    private static int t = -1;
    private static String mode = "survival";
    private static long heure = -1;
    private static boolean pluie;
    private static int echelleAvant = -1;
    private static UUID mannequin;

    // candidats et examinateurs de la séance fictive des maquettes (J6 à J8)
    private static final UUID KAZUKI = UUID.fromString("00000000-0000-4000-8000-00000000d001"),
            SORA = UUID.fromString("00000000-0000-4000-8000-00000000d002"),
            LUNAA = UUID.fromString("00000000-0000-4000-8000-00000000d003"),
            SHIRO = UUID.fromString("00000000-0000-4000-8000-00000000d004"),
            NEKO = UUID.fromString("00000000-0000-4000-8000-00000000d005"),
            MIRAI = UUID.fromString("00000000-0000-4000-8000-00000000d006"),
            PATOCHE = UUID.fromString("00000000-0000-4000-8000-00000000d007");

    private static final String TEXTE_NEGATIF =
            "La douane n'est pas validée aujourd'hui. Le commentaire de l'examinateur est dans ton launcher.";
    private static final String TEXTE_DIFFERE = "La douane est terminée. Résultat sous peu dans ton launcher.";
    private static final String ENVOI_ATTENTE = "Décision en attente d'envoi (API injoignable). Nouvel essai dans 30 s.";
    private static final String Q1 = "Un camarade est à terre après un duel perdu. Que fait ton personnage ?",
            Q2 = "Ton personnage voit un enfant voler une pomme au marché. Que fait-il ?",
            Q3 = "Un inconnu masqué offre une mission bien payée. Que fait ton personnage ?";
    private static final String MOTIF = "Tes réponses aux mises en situation restent très courtes. Dis ce que ton personnage "
            + "pense et ressent, pas seulement ce qu'il fait.";
    private static final String NOTES = "Règlement bien compris : KO et mort RP clairs (question 1). Réponses justes mais en une "
            + "phrase, sans rien du personnage. Voix posée. Peut repasser dès demain.";

    private DouaneDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_DOUANE_DEBUG");
        if (v == null || v.isBlank()) return;
        ClientTickEvents.END_CLIENT_TICK.register(DouaneDebug::tick);
    }

    private static void recu(String json) { DouaneClient.debugReceive(json); }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        var c = mc.player.connection;
        if (t > 22 && t % 4 == 0) mc.gui.hud.getChat().clearMessages(false);
        if (t < 20 && mc.player.isDeadOrDying() && t % 5 == 0) { mc.player.respawn(); mc.setScreenAndShow(null); }
        if (t > 20 && t < 640) vol(mc, true);
        switch (t) {
            case 20 -> {
                if (mc.gameMode != null) mode = mc.gameMode.getPlayerMode().getName();
                var srv = mc.getSingleplayerServer();
                if (srv != null) { heure = srv.overworld().getOverworldClockTime(); pluie = srv.overworld().isRaining(); }
                echelleAvant = mc.options.guiScale().get();
                mc.options.guiScale().set(2);
                mc.resizeGui();
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                DouaneClient.debugEffacer();
                DouaneClient.debugEnvoi = o -> LOG.info("[douane-banc] C → S : {}", o);
                c.sendCommand("gamemode creative");
                c.sendCommand("kill @e[type=minecraft:mannequin,distance=..96]");
                c.sendCommand("time set 6000");
                c.sendCommand("weather clear");
                c.sendCommand("tp @s ~ ~ ~ ~ 4");
                skins();
            }
            case 40 -> {
                // un « autre candidat » à 3,2 blocs, droit devant, tourné vers la caméra
                double yaw = Math.toRadians(mc.player.getYRot());
                double dx = -Math.sin(yaw), dz = Math.cos(yaw);
                double x = mc.player.getX() + dx * 3.2, z = mc.player.getZ() + dz * 3.2;
                float face = (float) Math.toDegrees(Math.atan2(-(mc.player.getX() - x), mc.player.getZ() - z));
                c.sendCommand(String.format(Locale.ROOT,
                        "summon minecraft:mannequin %.2f %.2f %.2f {NoGravity:1b,Rotation:[%.1ff,0f]}", x, mc.player.getY(), z, face));
            }
            // ───────────── candidat : le hall (J1)
            case 60 -> {
                Entity m = mannequinProche(mc);
                mannequin = m == null ? null : m.getUUID();
                if (mannequin != null) DouaneClient.DEBUG_NOMS.put(mannequin, "Sora_Kaze");
                Nameplates.debugAvatars = true;
                regarder(mc);
                recu("{\"t\":\"plaques\",\"v\":1,\"examinateurs\":[]}");
                recu("{\"t\":\"ligne\",\"v\":1,\"etat\":\"attente\",\"texte\":\"DOUANE · En attente · 3ᵉ\",\"rang\":3,\"total\":5}");
            }
            case 76 -> shot(mc, "douane_01_hall_attente");
            case 78 -> {
                recu("{\"t\":\"attente\",\"v\":1,\"on\":true,\"texte\":\"DOUANE · Ouverture dès l'arrivée d'un examinateur\"}");
                recu("{\"t\":\"ligne\",\"v\":1,\"etat\":\"ferme\",\"texte\":\"DOUANE · Ouverture dès l'arrivée d'un examinateur\"}");
            }
            case 92 -> shot(mc, "douane_02_hall_ferme");
            case 94 -> {
                // avant l'admission : ni ligne ni plaques, l'écran neutre seul
                DouaneClient.debugEffacer();
                if (mannequin != null) DouaneClient.DEBUG_NOMS.put(mannequin, "Sora_Kaze");
                recu("{\"t\":\"attente\",\"v\":1,\"on\":true,\"texte\":\"Vérification de l'accès…\"}");
            }
            case 106 -> shot(mc, "douane_03_verification");
            // ───────────── l'appel (J2)
            case 108 -> {
                recu("{\"t\":\"attente\",\"v\":1,\"on\":false}");
                DouaneClient.attenteTexte = null;
                // vérification de l'accès levée sans « fin » (aiguillage vers la sélection) : le HUD doit revenir
                LOG.info("[douane-banc] HUD masqué après l'attente seule : {} (attendu : false)", DouaneClient.masqueHud());
                recu("{\"t\":\"plaques\",\"v\":1,\"examinateurs\":[]}");
                recu("{\"t\":\"ligne\",\"v\":1,\"etat\":\"attente\",\"texte\":\"DOUANE · En attente · 1ᵉʳ\",\"rang\":1,\"total\":4}");
                DouaneClient.rappelAt = 0;
            }
            case 120 -> recu("{\"t\":\"appel\",\"v\":1,\"texte\":\"Un examinateur t'appelle\",\"fondu\":10}");
            case 122 -> shot(mc, "douane_04_appel");
            case 128 -> shot(mc, "douane_05_appel_fondu");
            case 130 -> {
                // la téléportation au bureau, côté serveur, environ 10 ticks après l'appel ; l'autre candidat devient
                // l'examinateur du bureau (plaque « Examinateur »)
                recu("{\"t\":\"ligne\",\"v\":1,\"etat\":\"bureau\",\"texte\":\"DOUANE · Entretien en cours\"}");
                if (mannequin != null) recu("{\"t\":\"plaques\",\"v\":1,\"examinateurs\":[\"" + mannequin + "\"]}");
            }
            case 152 -> shot(mc, "douane_06_bureau");
            // ───────────── la question (J3)
            case 154 -> recu("{\"t\":\"question\",\"v\":1,\"texte\":\"" + Q2 + "\"}");
            case 166 -> shot(mc, "douane_07_question");
            case 168 -> recu("{\"t\":\"question\",\"v\":1,\"texte\":\"Ton chef d'équipe t'ordonne de laisser un blessé derrière"
                    + " vous pour finir la mission à temps ; le blessé est un ami d'enfance. Que fait ton personnage, et que dit-il à son"
                    + " chef une fois rentré au village ?\"}");
            case 180 -> shot(mc, "douane_07b_question_longue");
            // ───────────── Bienvenue (J4)
            case 182 -> recu("{\"t\":\"bienvenue\",\"v\":1,\"titre\":\"Bienvenue sur Reborn\",\"s\":3}");
            case 206 -> shot(mc, "douane_08_bienvenue");
            case 250 -> recu("{\"t\":\"fin\",\"v\":1}");
            // ───────────── résultats (J5)
            case 252 -> recu("{\"t\":\"resultat\",\"v\":1,\"resultat\":\"NEEDS_REVISION\",\"texte\":\"" + TEXTE_NEGATIF + "\",\"s\":8}");
            case 276 -> shot(mc, "douane_09_resultat_revoir");
            case 278 -> recu("{\"t\":\"resultat\",\"v\":1,\"resultat\":\"REJECTED\",\"texte\":\"" + TEXTE_NEGATIF + "\",\"s\":8}");
            case 302 -> shot(mc, "douane_10_resultat_refuse");
            case 304 -> recu("{\"t\":\"resultat\",\"v\":1,\"resultat\":\"PENDING\",\"texte\":\"" + TEXTE_DIFFERE + "\",\"s\":8}");
            case 328 -> shot(mc, "douane_11_resultat_differe");
            // ───────────── examinateur : l'onglet Douane (J6)
            case 330 -> {
                DouaneClient.debugEffacer();
                Nameplates.debugAvatars = false;
                micros();
                recu("{\"t\":\"mode\",\"v\":1,\"examinateur\":true}");
                // textes tels que ShinobiCore les écrit (ReglesDouane.libelle, SuiteDecision.report)
                recu("{\"t\":\"envoi\",\"v\":1,\"etat\":\"ok\",\"resultat\":\"APPROVED\",\"texte\":\"Décision enregistrée : Accepté (kenzo_).\"}");
                recu("{\"t\":\"envoi\",\"v\":1,\"etat\":\"attente\",\"resultat\":\"NEEDS_REVISION\",\"texte\":\"" + ENVOI_ATTENTE + "\"}");
                DouaneClient.toast = null;
                recu(etat(false));
                GardeDouaneDebug.ouvrir(mc, snapStaff(mc));
                GardeDouaneDebug.toast("Douane · neko_sama est arrivé au hall");
                GardeDouaneDebug.survolLigne(mc, 0, 0);
            }
            case 352 -> shot(mc, "douane_12_garde_douane");
            // ───────────── le dossier, volet élargi (J7)
            case 354 -> {
                tourner(mc, 35);
                GardeDouaneDebug.effacerToasts();
                recu(etat(true));
                recu(dossier());
                GardeDouaneDebug.sansSurvol(mc);
                GardeDouaneDebug.dossier(mc, KAZUKI.toString(), "dossier");
            }
            case 378 -> shot(mc, "douane_13_garde_dossier");
            // ───────────── questions et décision (J8)
            case 380 -> {
                recu(tirage(Q1, false, 63));
                recu(tirage(Q1, true, -1));
                recu(tirage(Q2, false, 62));
                recu(tirage(Q2, true, -1));
                recu(tirage(Q3, false, 61));
                GardeDouaneDebug.dossier(mc, KAZUKI.toString(), "decision");
                GardeDouaneDebug.decision(mc, "NEEDS_REVISION", MOTIF, NOTES);
                GardeDouaneDebug.survolEnvoi(mc);
            }
            case 402 -> shot(mc, "douane_14_garde_decision");
            case 404 -> {
                GardeDouaneDebug.sansSurvol(mc);
                recu("{\"t\":\"envoi\",\"v\":1,\"etat\":\"attente\",\"resultat\":\"NEEDS_REVISION\",\"texte\":\"" + ENVOI_ATTENTE + "\"}");
                recu("{\"t\":\"toast\",\"v\":1,\"m\":\"Kazuki quitte la douane : le résultat lui parviendra dans son launcher dès"
                        + " l'envoi. Ton bureau est libre.\",\"erreur\":false}");
            }
            case 424 -> shot(mc, "douane_15_garde_envoi_attente");
            case 426 -> GardeDouaneDebug.effacerToasts();
            case 427 -> GardeDouaneDebug.retrait(mc, KAZUKI.toString(), "Propos déplacés envers les autres candidats au hall.");
            case 446 -> shot(mc, "douane_16_garde_retrait");
            case 448 -> {
                mc.setScreenAndShow(null);
                tourner(mc, -35);
            }
            case 462 -> shot(mc, "douane_17_mode_examinateur");
            // ───────────── petite fenêtre : 1366 × 768
            case 464 -> {
                mc.getWindow().setWindowed(1366, 768);
                mc.options.guiScale().set(2);
                mc.resizeGui();
            }
            case 476 -> {
                recu(etat(false));
                GardeDouaneDebug.ouvrir(mc, snapStaff(mc));
                GardeDouaneDebug.survolLigne(mc, 1, 1);
            }
            case 498 -> shot(mc, "douane_18_garde_1366");
            case 500 -> {
                GardeDouaneDebug.dossier(mc, KAZUKI.toString(), "decision");
                GardeDouaneDebug.decision(mc, "REJECTED", "", "");
                GardeDouaneDebug.sansSurvol(mc);
                recu(etat(true));
            }
            case 522 -> shot(mc, "douane_19_garde_decision_1366");
            case 524 -> {
                mc.setScreenAndShow(null);
                DouaneClient.debugEffacer();
                Nameplates.debugAvatars = true;
                if (mannequin != null) DouaneClient.DEBUG_NOMS.put(mannequin, "Sora_Kaze");
                recu("{\"t\":\"plaques\",\"v\":1,\"examinateurs\":[]}");
                recu("{\"t\":\"ligne\",\"v\":1,\"etat\":\"attente\",\"texte\":\"DOUANE · En attente · 3ᵉ\",\"rang\":3,\"total\":5}");
            }
            case 540 -> shot(mc, "douane_20_hall_1366");
            // ───────────── remise en état
            case 560 -> {
                DouaneClient.debugEffacer();
                DouaneClient.debugEnvoi = null;
                Nameplates.debugAvatars = false;
                VoiceState.DEBUG_MICROS.clear();
                for (UUID u : new UUID[]{KAZUKI, SORA, LUNAA, SHIRO, NEKO, MIRAI, PATOCHE}) RebornSkins.clear(u);
                c.sendCommand("kill @e[type=minecraft:mannequin,distance=..40]");
                if (heure >= 0) c.sendCommand("time set " + heure);
                c.sendCommand(pluie ? "weather rain" : "weather clear");
                mc.getWindow().setWindowed(1920, 1080);
                if (echelleAvant >= 0) mc.options.guiScale().set(echelleAvant);
                mc.resizeGui();
            }
            case 640 -> { vol(mc, false); c.sendCommand("gamemode " + mode); }
            case 650 -> mc.stop();
            default -> { }
        }
    }

    /* ================================================================ messages d'exemple (protocole v1) */

    /**
     * Convocation n° {@code n} de la séance fictive (0 ou 1), d'un seul Instant pour la file et le dossier : la première
     * au quart d'heure sous « maintenant − 23 min », la seconde 30 min plus tard. Les arrivées de la file (attente de 33,
     * 27, 7, 4 et 2 min) tombent ainsi toujours dans la fenêtre H−15 / H+30 de leur convocation.
     */
    private static Instant convocation(int n) {
        long quart = 15 * 60_000L;
        long c1 = Math.floorDiv(System.currentTimeMillis() - 23 * 60_000L, quart) * quart;
        return Instant.ofEpochMilli(c1 + n * 30 * 60_000L);
    }

    /** « HH:mm » dans le fuseau de la douane, comme ShinobiCore ({@code douane.fuseau}, Europe/Paris par défaut). */
    private static String hhmm(Instant i) {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Paris")).format(i);
    }

    private static String place(UUID u, String pseudo, int rang, int attente, Instant conv, String village, boolean deux) {
        return "{\"u\":\"" + u + "\",\"pseudo\":\"" + pseudo + "\",\"rang\":" + rang + ",\"attenteMin\":" + attente
                + ",\"convocation\":\"" + hhmm(conv) + "\",\"convocationAt\":" + conv.toEpochMilli()
                + ",\"village\":\"" + village + "\",\"deuxiemePassage\":" + deux + ",\"enTete\":false}";
    }

    private static String bureau(String id, UUID ex, String exPseudo, UUID cand, String candPseudo) {
        boolean libre = ex == null && cand == null;
        return "{\"id\":\"" + id + "\",\"libre\":" + libre + ",\"examinateur\":" + q(ex) + ",\"examinateurPseudo\":" + q(exPseudo)
                + ",\"candidat\":" + q(cand) + ",\"candidatPseudo\":" + q(candPseudo) + ",\"absent\":false,\"envoi\":\"aucun\"}";
    }

    private static String q(Object o) { return o == null ? "null" : "\"" + o + "\""; }

    /** État de l'onglet : la file de la séance des maquettes ; {@code auBureau} : Kazuki est à mon bureau (b2). */
    private static String etat(boolean auBureau) {
        String moi = Minecraft.getInstance().player.getUUID().toString();
        StringBuilder f = new StringBuilder();
        int r = 1;
        Instant c1 = convocation(0), c2 = convocation(1);
        if (!auBureau) f.append(place(KAZUKI, "Kazuki", r++, 33, c1, "konoha", false)).append(',');
        f.append(place(SORA, "Sora_Kaze", r++, 27, c1, "kiri", true)).append(',');
        f.append(place(LUNAA, "Lunaa", r++, 7, c2, "suna", false)).append(',');
        f.append(place(SHIRO, "ShiroKuma", r++, 4, c2, "kumo", false)).append(',');
        f.append(place(NEKO, "neko_sama", r, 2, c2, "iwa", false));
        String b2 = auBureau ? bureau("b2", UUID.fromString(moi), Minecraft.getInstance().player.getGameProfile().name(), KAZUKI, "Kazuki")
                : bureau("b2", null, null, null, null);
        return "{\"t\":\"etat\",\"v\":1,\"actif\":true,\"ouvert\":true,\"mode\":true,\"peut\":true,\"monBureau\":"
                + (auBureau ? "\"b2\"" : "null") + (auBureau ? ",\"question\":{\"texte\":\"" + Q3 + "\",\"montree\":false}" : "")
                + ",\"enAttente\":" + (auBureau ? 4 : 5) + ",\"envoisEnAttente\":1,\"file\":[" + f + "],\"bureaux\":["
                + bureau("b1", PATOCHE, "Patoche", MIRAI, "mirai_") + "," + b2 + "," + bureau("b3", null, null, null, null) + "]}";
    }

    private static String tirage(String texte, boolean montree, int reste) {
        return "{\"t\":\"tirage\",\"v\":1,\"texte\":\"" + texte + "\",\"montree\":" + montree + (reste >= 0 ? ",\"reste\":" + reste : "") + "}";
    }

    /** Réponse de GET /game/douane/dossier telle quelle (game-douane.vues.ts › DossierDouaneVue). */
    private static String dossier() {
        Instant n = Instant.now();
        // même convocation que la file ; arrivée = attente de 33 min de la file, après l'ouverture de l'entrée (H−15)
        Instant conv = convocation(0);
        return "{\"t\":\"dossier\",\"v\":1,\"u\":\"" + KAZUKI + "\",\"dossier\":{\"applicationId\":\"banc-candidature\",\"formVersion\":2,"
                + "\"candidat\":{\"mcUuid\":\"" + KAZUKI + "\",\"pseudo\":\"Kazuki\",\"ageDeclare\":19,\"anneesRp\":\"3 à 5 ans\","
                + "\"connaissanceNaruto\":\"Bonne connaissance\"},"
                + "\"personnage\":{\"prenom\":\"Kaito\",\"nomFamille\":\"Arashi\",\"sexe\":\"Homme\",\"age\":16,\"village\":\"Konohagakure\",\"type\":\"Ninja\"},"
                + "\"extraits\":["
                + "{\"champ\":\"history\",\"titre\":\"Histoire\",\"texte\":\"Kaito est né à Konoha, chez les Arashi, une famille sans clan qui tient"
                + " les registres du village depuis trois générations.\\nSon père, archiviste, lui a appris à lire avant de lui apprendre à lancer un"
                + " kunai.\\nÀ huit ans, son père disparaît du jour au lendemain.\",\"tronque\":true,\"caracteres\":1128},"
                + "{\"champ\":\"objectives\",\"titre\":\"Objectifs roleplay\",\"texte\":\"À court terme : réussir l'examen chūnin qu'il a raté une"
                + " première fois.\\nÀ moyen terme : intégrer la cellule de renseignement du village.\\nPlus loin : comprendre pourquoi son père a été"
                + " rayé des registres.\",\"tronque\":false,\"caracteres\":484},"
                + "{\"champ\":\"availability\",\"titre\":\"Disponibilités\",\"texte\":\"En semaine après 20 h, le samedi toute la journée. Moins le"
                + " dimanche.\",\"tronque\":false,\"caracteres\":69}],"
                + "\"noteEcrit\":{\"texte\":\"Écrit sérieux, histoire bien construite. À creuser : ce que Kaito sait de la disparition de son père,"
                + " pourquoi il veut le renseignement, et ce qu'il ferait face à un ordre injuste. Règlement bien lu (KO, mort RP).\",\"tronquee\":false},"
                + "\"voix\":{\"presente\":true,\"dureeSec\":42,\"consigne\":\"Voix : à réécouter dans le panel\"},"
                + "\"passage\":{\"reservationId\":\"banc-place\",\"seanceId\":\"banc-seance\",\"convocationAt\":\"" + conv + "\","
                + "\"entreeOuvreAt\":\"" + conv.minusSeconds(15 * 60) + "\",\"entreeFermeAt\":\"" + conv.plusSeconds(30 * 60) + "\","
                + "\"arriveeAt\":\"" + n.minusSeconds(33 * 60) + "\",\"appelAt\":\"" + n.minusSeconds(2 * 60) + "\","
                + "\"deuxiemePassage\":false,\"motifPrecedent\":null},"
                + "\"lienPanel\":\"https://panel.exemple.test/whitelist/banc-candidature\"}}";
    }

    /** Instantané du Poste de garde (reborn:staff) : un Helper examinateur, une alerte « douane ». */
    private static String snapStaff(Minecraft mc) {
        return "{\"t\":\"snap\",\"grade\":1,\"gradeName\":\"Helper\",\"staffOnline\":2,\"modeStaff\":false,\"modeAnim\":false,"
                + "\"alerts\":[{\"id\":\"douane-1\",\"kind\":\"douane\",\"uuid\":\"" + NEKO + "\",\"name\":\"neko_sama\",\"perso\":\"\",\"pid\":\"\","
                + "\"online\":true,\"motif\":\"Candidat arrivé à la douane\",\"ctx\":[\"à l'instant\"],\"score\":0}],"
                + "\"traitees\":[],\"players\":[],\"chat\":[],\"journal\":[],\"commands\":[]}";
    }

    /* ================================================================ outils du banc */

    private static void micros() {
        VoiceState.DEBUG_MICROS.put(KAZUKI, VoiceState.MICRO_ACTIF);
        VoiceState.DEBUG_MICROS.put(SORA, VoiceState.MICRO_ACTIF);
        VoiceState.DEBUG_MICROS.put(LUNAA, VoiceState.MICRO_COUPE);
        VoiceState.DEBUG_MICROS.put(SHIRO, VoiceState.MICRO_SANS_VOIX);
        VoiceState.DEBUG_MICROS.put(NEKO, VoiceState.MICRO_ACTIF);
        VoiceState.DEBUG_MICROS.put(MIRAI, VoiceState.MICRO_ACTIF);
    }

    /** Visages des candidats (têtes composées par RebornSkins, prêtées au Poste de garde). */
    private static void skins() {
        try {
            RebornSkins.applySpec(KAZUKI, look(false, 4, "Cheveux_4", 0xFF5A2E1A, 0xFF3A2A20));
            RebornSkins.applySpec(SORA, look(true, 3, "Cheveux_Femme", 0xFF8E6AB4, 0xFF6A3A50));
            RebornSkins.applySpec(LUNAA, look(true, 6, "Cheveux_Femme", 0xFF1A1414, 0xFF2A2A40));
            RebornSkins.applySpec(SHIRO, look(false, 1, "Cheveux_Base", 0xFFE2C070, 0xFF5A70C0));
            RebornSkins.applySpec(NEKO, look(true, 2, "Cheveux_Femme", 0xFF2A2440, 0xFF3FA060));
            RebornSkins.applySpec(MIRAI, look(true, 7, "Cheveux_Femme", 0xFF6A3A22, 0xFF3A2A1A));
        } catch (RuntimeException e) {
            LOG.warn("[douane-banc] têtes non composées : {}", e.toString());
        }
    }

    private static SkinSpec look(boolean female, int skin, String hair, int hairColor, int eye) {
        SkinSpec l = new SkinSpec();
        l.female = female;
        l.skinColor = SkinSpec.skinRamp(skin / 11f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = eye;
        return l;
    }

    private static void vol(Minecraft mc, boolean on) {
        var a = mc.player.getAbilities();
        a.mayfly = on || a.instabuild;
        a.flying = on;
        mc.player.onUpdateAbilities();
    }

    private static Entity mannequinProche(Minecraft mc) {
        Entity best = null;
        double d = 64;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!"minecraft:mannequin".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) continue;
            double x = e.distanceToSqr(mc.player);
            if (x < d) { d = x; best = e; }
        }
        return best;
    }

    /** Le viseur sur la poitrine du mannequin (la plaque n'apparaît que si on le regarde vraiment, de près). */
    private static void regarder(Minecraft mc) {
        Entity m = mannequinProche(mc);
        if (m == null) return;
        double dx = m.getX() - mc.player.getX(), dz = m.getZ() - mc.player.getZ();
        double dy = m.getY() + 1.3 - mc.player.getEyeY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f %.1f", yaw, pitch));
    }

    /** Tourne la tête de {@code deg} degrés (l'examinateur garde le candidat en vue à gauche du volet élargi). */
    private static void tourner(Minecraft mc, float deg) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s ~ ~ ~ ~%.1f ~", deg));
    }

    private static void shot(Minecraft mc, String nom) {
        Screenshot.grab(mc.gameDirectory, nom + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        LOG.info("[douane-banc] capture {}", nom);
    }
}
