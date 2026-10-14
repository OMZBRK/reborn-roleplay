package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Banc d'essai visuel du Poste de garde v3 (le volet), <b>inactif en production</b> : ne fait rien sauf si
 * {@code REBORN_STAFF_DEBUG} est posé (client de dev). Injecte un état d'exemple (celui des maquettes A du 14/10),
 * ouvre chaque vue, prend une capture dans run/screenshots/, puis ferme le jeu.
 *
 * <ul>
 *   <li>{@code =1} : chaque cas en 1920×1080, 1366×768 et 1920×1080 avec l'échelle GUI 3 du joueur ;</li>
 *   <li>{@code =vite} : 1920×1080 seul ;</li>
 *   <li>{@code =<cas>,<cas>…} : ces cas seuls, aux trois tailles.</li>
 * </ul>
 * Captures {@code run/screenshots/garde_<taille>_<nn>_<cas>.png}. Les demandes partent vers {@link StaffDemo}.
 */
public final class StaffDebug {

    private record Shot(String nom, String cas, int fw, int fh, int echelle, boolean dense) {}

    static final String[] CAS = {
            "alertes", "alertes_traitees", "alertes_vides", "alertes_valider",
            "joueurs", "joueurs_survol", "joueurs_menu", "recherche", "recherche_action", "recherche_hors_ligne",
            "fiche", "fiche_plus", "fiche_radio", "fiche_notes", "fiche_sanctions", "sanction", "fiche_comptes",
            "action_noir", "action_suppr",
            "journal", "journal_filtre", "journal_menu", "journal_vide",
            "outils", "annonces", "annonce_confirm", "seisme", "chat", "commandes", "modes",
            "blesses", "zones", "zones_nouvelle", "hopitaux", "reglages",
            "dense_joueurs", "dense_fiche", "dense_alertes",
            "hud_annonce", "hud_annonce_perso", "hud_noir", "hud_plaques", "hud_mode"};

    private static final int STEP = 30;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int t = -1;

    private StaffDebug() {}

    public static void init() {
        String v = System.getenv("REBORN_STAFF_DEBUG");
        if (v == null || v.isBlank()) return;
        v = v.trim();
        boolean vite = "vite".equalsIgnoreCase(v);
        int[][] tailles = vite ? new int[][]{{1920, 1080, 2}}
                : new int[][]{{1920, 1080, 2}, {1366, 768, 2}, {1920, 1080, 3}};
        Set<String> only = new HashSet<>();
        if (!"1".equals(v) && !vite) only.addAll(List.of(v.split(",")));
        for (int[] f : tailles) {
            String tag = f[2] == 3 ? "s3" : f[0] + "x" + f[1];
            for (int i = 0; i < CAS.length; i++) {
                String c = CAS[i];
                if (!only.isEmpty() && !only.contains(c)) continue;
                SHOTS.add(new Shot(String.format("garde_%s_%02d_%s", tag, i + 1, c), c.replace("dense_", ""), f[0], f[1], f[2], c.startsWith("dense_")));
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register(StaffDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 1000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
        }
        if (t < 40) return;
        int i = (t - 40) / STEP, k = (t - 40) % STEP;
        if (i >= SHOTS.size()) {
            if (k == 2) mc.setScreenAndShow(null);
            if (k == 6) mc.stop();
            return;
        }
        Shot s = SHOTS.get(i);
        String me = mc.player.getUUID().toString();
        if (k == 0) {
            mc.setScreenAndShow(null);
            EffetsStaff.effacer();
            PlaquesStaff.debugEffacer();
            StaffClient.modeAnim = false;
            StaffClient.debugEffacerToasts();
            StaffClient.modeStaff = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh()) {
                mc.getWindow().setWindowed(s.fw(), s.fh());
            }
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
        }
        if (k == 4) {
            mc.gui.hud.getChat().clearMessages(false);
            inject(snap(me, s.dense(), s.cas()));
            inject(profile(me, s.dense()));
            StaffDemo.activer(me, profile(me, s.dense()));
            StaffClient.debugReceive(InfirmerieDebug.snapshot(me));
            if (s.cas().startsWith("hud_")) { hud(mc, s.cas()); return; }
            GardeScreen g = new GardeScreen("alerts");
            mc.setScreenAndShow(g);
            setup(g, s.cas(), me);
        }
        if (k == 10 && mc.gui.screen() instanceof GardeScreen g) suite(g, s.cas(), me);
        if (k == STEP - 3) {
            Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        }
    }

    private static void setup(GardeScreen g, String cas, String me) {
        switch (cas) {
            case "alertes", "alertes_vides", "alertes_valider" -> g.debugTab("alerts");
            case "alertes_traitees" -> { g.debugTab("alerts"); g.alerteVue = "traitees"; }
            case "joueurs", "joueurs_survol", "joueurs_menu" -> g.debugTab("players");
            case "recherche" -> g.debugSearch("uchi");
            case "recherche_action" -> g.debugSearch("gel kazu");
            case "recherche_hors_ligne" -> g.debugSearch("sora");
            case "fiche", "fiche_plus", "fiche_radio", "fiche_notes", "fiche_sanctions", "sanction", "fiche_comptes",
                 "action_noir", "action_suppr" -> g.debugProfile(me);
            case "journal", "journal_vide", "journal_menu" -> g.debugTab("journal");
            case "journal_filtre" -> {
                g.debugTab("journal");
                g.journalVue.filtrerPid("00042", "Kazuki Uchiha");
                g.journalVue.cats.add("KO");
                g.journalVue.cats.add("COMBAT");
                g.journalVue.periode = "24h";
                g.journalVue.demander();
            }
            case "outils" -> g.debugTab("outils");
            case "annonces" -> {
                g.debugTab("annonces");
                g.annoncesVue.debugRemplir(false, "Hokage", "Rassemblement devant la tour du Hokage dans dix minutes.", "");
            }
            case "annonce_confirm" -> {
                g.debugTab("annonces");
                g.annoncesVue.debugRemplir(false, "Hokage", "Rassemblement devant la tour du Hokage dans dix minutes.", "");
                g.annoncesVue.debugEnvoyer();
            }
            case "seisme", "chat", "modes" -> g.debugTab(cas);
            case "commandes" -> g.debugTab("cmds");
            case "blesses", "zones", "zones_nouvelle", "hopitaux", "reglages" -> g.debugKoTab(cas.equals("zones_nouvelle") ? "zones" : cas);
            default -> { }
        }
    }

    /** Étapes qui demandent la fiche ou le journal arrivés (quelques ticks après l'ouverture). */
    private static void suite(GardeScreen g, String cas, String me) {
        switch (cas) {
            case "alertes_valider" -> g.debugValiderRpk();
            case "joueurs_survol" -> g.debugSurvolLigne(1, 1);
            case "joueurs_menu" -> {
                JsonObject p = StaffClient.snapshot().getAsJsonArray("players").get(1).getAsJsonObject();
                g.ouvrirMenuJoueur(p, g.px0() - 4, g.vy0() + 40);
            }
            case "fiche_plus" -> g.debugMenuPlus();
            case "fiche_radio" -> {
                GardeActions.lancer(g, GardeActions.RADIO, StaffClient.profile());
                g.formulaire.debugValeur(0, "Plainte n° 212 : harcèlement radio");
            }
            case "fiche_notes" -> g.fiche.ouvrirSection("notes");
            case "fiche_sanctions" -> g.fiche.ouvrirSection("sanctions");
            case "fiche_comptes" -> g.fiche.ouvrirSection("comptes");
            case "sanction" -> { g.fiche.ouvrirSection("sanctions"); g.fiche.debugInfraction(3); }
            case "action_noir" -> {
                GardeActions.lancer(g, GardeActions.NOIR, StaffClient.profile());
                g.formulaire.debugValeur(0, "Vous perdez connaissance…");
            }
            case "action_suppr" -> GardeActions.lancer(g, GardeActions.SUPPR, StaffClient.profile());
            case "journal_filtre" -> g.journalVue.debugDeplier(0);
            case "journal_menu" -> g.debugMenuFiltre();
            case "journal_vide" -> g.journalVue.debugTexte("introuvable");
            case "zones" -> g.debugSelect("dojo");
            case "zones_nouvelle" -> g.debugDraft(15905, 9690, 15930, 9705);
            default -> { }
        }
    }

    /** Effets en jeu (HUD) : annonce globale / personnelle, écran noir, plaques staff, indicateur de mode. */
    private static void hud(Minecraft mc, String cas) {
        switch (cas) {
            case "hud_annonce" -> EffetsStaff.debugAnnonce(false, "Hokage", "Rassemblement devant la tour du Hokage dans dix minutes.", 1500);
            case "hud_annonce_perso" -> EffetsStaff.debugAnnonce(true, "", "Le Hokage te convoque à son bureau. Viens seul.", 1500);
            case "hud_noir" -> EffetsStaff.recevoir("{\"t\":\"noir\",\"on\":true,\"texte\":\"Vous perdez connaissance…\",\"fondu\":4,\"s\":0}");
            case "hud_plaques" -> {
                PlaquesStaff.debugSoi(mc.player.getUUID());
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }
            case "hud_mode" -> StaffClient.debugReceive("{\"t\":\"mode\",\"staff\":false,\"anim\":true}");
            default -> { }
        }
    }

    /** Audit responsive : Poste de garde ouvert sur {@code tab} (fiche du joueur si {@code profile}). */
    public static void openDemo(Minecraft mc, String tab, boolean profile) {
        String me = mc.player.getUUID().toString();
        inject(snap(me, false, tab));
        inject(profile(me, false));
        GardeScreen s = new GardeScreen("alerts");
        mc.setScreenAndShow(s);
        s.debugTab(tab);
        if (profile) s.debugProfile(me);
    }

    /* ================================================================ données d'exemple (maquettes A) */

    private static final String[][] JOUEURS = {
            {"00042", "Kazuki Uchiha", "Raiden_FR", "Genin", "ATA pleine · repos 3/20"},
            {"00007", "Ren Hyūga", "Mika_", "Genin", "À terre"},
            {"00031", "Renji Inuzuka", "Renjiii", "Genin", ""},
            {"00013", "Daisuke Sarutobi", "Dais", "Chūnin", ""},
            {"00017", "Aki Uchiha", "Akii", "Chūnin", ""},
            {"00021", "Hiroshi Nara", "Hiro_N", "Genin", ""},
            {"00057", "Yui Hyūga", "yuyu", "Académicien", ""},
            {"00064", "Mei Uzumaki", "meimei", "Genin", "Duel"},
            {"00088", "Kenta Ishida", "KentaI", "Genin", ""},
            {"00102", "Sakura Yamanaka", "Saku_", "Genin", ""},
            {"00115", "Tarō Akimichi", "Tarotaro", "Académicien", ""},
            {"00121", "Nanami Hatake-Senju no Mikoto", "nanamii", "Chūnin", ""},
            {"00000", "Aya Haruno", "Aya", "Chūnin", ""}};

    static String snap(String me, boolean dense, String cas) {
        JsonObject o = JsonParser.parseString(SNAP.replace("$ME", me)).getAsJsonObject();
        if ("alertes_vides".equals(cas)) o.add("alerts", new JsonArray());
        JsonArray pl = new JsonArray();
        for (int i = 0; i < JOUEURS.length; i++) {
            String[] j = JOUEURS[i];
            JsonObject p = new JsonObject();
            p.addProperty("uuid", me);
            p.addProperty("pid", j[0]);
            p.addProperty("perso", j[1]);
            p.addProperty("name", j[2]);
            p.addProperty("rank", j[3]);
            p.addProperty("village", "konoha");
            p.addProperty("state", j[4]);
            p.addProperty("online", true);
            pl.add(p);
        }
        JsonObject p0 = pl.get(0).getAsJsonObject();
        p0.addProperty("notes", 3);
        JsonArray et = new JsonArray();
        et.add("surveillé");
        p0.add("etiquettes", et);
        pl.get(1).getAsJsonObject().addProperty("gele", true);
        pl.get(2).getAsJsonObject().addProperty("noir", true);
        pl.get(5).getAsJsonObject().addProperty("notes", 1);
        pl.get(12).getAsJsonObject().addProperty("modeStaff", true);
        o.add("players", pl);
        o.addProperty("modeStaff", !"modes".equals(cas));
        o.addProperty("modeAnim", false);
        o.addProperty("animPossible", true);
        if ("action_suppr".equals(cas)) { o.addProperty("grade", 3); o.addProperty("gradeName", "Admin"); }
        if (dense) densify(o, me);
        return o.toString();
    }

    static String profile(String me, boolean dense) {
        JsonObject p = JsonParser.parseString(PROFILE.replace("$ME", me)).getAsJsonObject();
        p.addProperty("notes", 3);
        p.addProperty("back", true);
        p.addProperty("retour", false);
        JsonArray et = new JsonArray();
        et.add("surveillé");
        et.add("avertissement");
        p.add("etiquettes", et);
        if (dense) {
            p.addProperty("gele", true);
            p.addProperty("gelePar", "Hiro");
            p.addProperty("geleMotif", "refus d'obtempérer");
            p.addProperty("noir", true);
            p.addProperty("perso", "Kazuki Uchiha-Senju no Mikoto");
            p.addProperty("name", "xX_Raiden_FR_Officiel_Xx");
            p.addProperty("rank", "Jōnin spécial");
            p.addProperty("state", "ATA pleine · repos 3/20 · gelé par Hiro");
            p.addProperty("ryo", 1234567);
            p.addProperty("playtime", 98765);
        }
        return p.toString();
    }

    private static void densify(JsonObject o, String me) {
        JsonArray pl = o.getAsJsonArray("players");
        for (int i = 0; i < 14; i++) {
            JsonObject p = pl.get(i % pl.size()).getAsJsonObject().deepCopy();
            p.addProperty("perso", p.get("perso").getAsString() + " de la Branche Secondaire");
            p.addProperty("name", "xX_" + p.get("name").getAsString() + "_Officiel_Xx");
            p.addProperty("pid", String.format("%05d", 1000 + i * 137));
            pl.add(p);
        }
        JsonArray al = o.getAsJsonArray("alerts");
        JsonObject a0 = al.get(1).getAsJsonObject();
        a0.addProperty("perso", "Kazuki Uchiha-Senju no Mikoto");
        a0.addProperty("motif", "Combat hors terrain d'entraînement ×4 sur des joueurs sans arme, en pleine place du marché de Konoha");
        for (int k = 0; k < 4; k++) al.add(al.get(k % 4).deepCopy());
    }

    private static void inject(String json) { StaffClient.debugReceive(json); }

    private static final String SNAP = """
            {"t":"snap","grade":2,"gradeName":"Modérateur","staffOnline":4,
             "alerts":[
              {"id":"kill-7","kind":"mort","uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","pid":"00042","rank":"Genin","online":true,
               "motif":"veut achever 00007 Ren Hyūga","ctx":["place du marché","à l'instant"],"score":0},
              {"id":"ag-1","kind":"agression","uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","pid":"00042","rank":"Genin","online":true,
               "motif":"combat hors terrain ×4","ctx":["3 victimes","il y a 1 min"],"score":157},
              {"id":"aide-2","kind":"aide","uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","pid":"00007","rank":"Genin","online":true,
               "motif":"« Je suis coincé dans un mur »","ctx":["il y a 2 min"],"score":0},
              {"id":"rep-3","kind":"signalement","uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","pid":"00013","rank":"Chūnin","online":true,
               "motif":"signalé par 2 joueurs","ctx":["HRP au chat","il y a 6 min"],"score":0}],
             "traitees":[
              {"kind":"aide","pid":"00021","perso":"Hiroshi Nara","par":"Patoche","quoi":"y est allé","time":"20:41","moi":false},
              {"kind":"agression","pid":"00064","perso":"Mei Uzumaki","par":"Aya","quoi":"l'a ignorée","time":"20:12","moi":false},
              {"kind":"mort","pid":"00042","perso":"Kazuki Uchiha","par":"Toi","quoi":"a refusé la mise à mort","time":"19:55","moi":true}],
             "chat":[
              {"time":"17:41","grade":1,"name":"Aya","text":"Ren est à terre dans la forêt est, j'y vais.","sys":false},
              {"time":"17:42","grade":0,"name":"Système","text":"Alerte : Kazuki Uchiha (Raiden_FR) · combat hors terrain ×4 (score 157).","sys":true},
              {"time":"17:43","grade":2,"name":"Hiro","text":"Je le prends, c'est la deuxième fois cette semaine.","sys":false},
              {"time":"17:44","grade":4,"name":"Omz","text":"Ok, applique l'escalade et marque les preuves.","sys":false}],
             "journal":[],
             "commands":[
              {"cat":"Joueurs et modération","cmd":"/garde","desc":"Ouvrir le Poste de garde","grade":1},
              {"cat":"Joueurs et modération","cmd":"/pid <joueur|#N°>","desc":"N° de personnage, compte, état (hors ligne avec #N°)","grade":1},
              {"cat":"Joueurs et modération","cmd":"/modestaff","desc":"Mode staff : invisible, aucune mécanique RP","grade":1},
              {"cat":"Joueurs et modération","cmd":"/modeanim","desc":"Mode animation : jouer pour un event, journalisé","grade":1},
              {"cat":"Joueurs et modération","cmd":"/vanish","desc":"Devenir invisible","grade":2},
              {"cat":"Joueurs et modération","cmd":"/pardon <joueur>","desc":"Lever un bannissement","grade":3},
              {"cat":"KO, ATA et soins","cmd":"/ata lever <joueur>","desc":"Lever l'ATA","grade":2},
              {"cat":"KO, ATA et soins","cmd":"/hopital set <village|defaut>","desc":"Placer le lit de réveil d'un village","grade":3},
              {"cat":"Serveur","cmd":"/sc reload","desc":"Recharger ShinobiCore","grade":4}]}
            """;

    private static final String PROFILE = """
            {"t":"profile","uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","pid":"00042","rank":"Genin","village":"konoha","online":true,
             "state":"ATA pleine · repos 3/20","hp":412,"hpMax":1380,"chakra":6200,"chakraMax":16000,"ryo":12450,"playtime":2472,"ping":38,
             "frozen":false,"watching":false,"score":157,
             "alts":[{"uuid":"$ME","name":"Raiden_alt","seen":"il y a 3 h"}],
             "record":[{"when":"12/10","text":"Mute 1 h · HRP au chat · par Hiro","status":"actif","active":true},
                       {"when":"01/10","text":"Avertissement · Combat d'arène · par Omz","status":"noté","active":false}],
             "recordTotal":2,"recordActive":1,
             "offenses":[
              {"id":"hrp","name":"HRP","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"metagaming","name":"Metagaming","strikes":1,"next":"Mute 6 h","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"powergaming","name":"Powergaming","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"combat_arene","name":"Combat d'arène","strikes":1,"next":"Ban 1 jour","tone":"ban","locked":false,"lockGrade":"Modérateur"},
              {"id":"nlr","name":"NLR non respecté","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"insultes","name":"Insultes","strikes":1,"next":"Mute 1 jour","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"triche","name":"Triche","strikes":0,"next":"Ban 30 jours","tone":"ban","locked":true,"lockGrade":"Admin"}],
             "msgs":[{"time":"14/10 à 17:48","text":"viens au marché on règle ça maintenant"},
                     {"time":"14/10 à 17:49","text":"le RP on s'en fiche, on se bat"},
                     {"time":"14/10 à 17:51","text":"gg ez, au suivant"}]}
            """;
}
