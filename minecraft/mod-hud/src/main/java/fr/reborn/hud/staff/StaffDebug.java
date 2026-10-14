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
 * Banc d'essai visuel du Poste de garde, <b>inactif en production</b> : ne fait rien sauf si
 * {@code REBORN_STAFF_DEBUG} est posé (client de dev, {@code gradlew runClient}). Injecte un état d'exemple (comme
 * s'il venait du serveur), ouvre chaque onglet et sous-vue, prend une capture dans run/screenshots/, puis ferme le
 * jeu.
 *
 * <ul>
 *   <li>{@code =1} : chaque cas en 1920×1080, 1366×768 et 1920×1080 avec l'échelle GUI 3 du joueur (MenuScale
 *   ramène le menu à la taille de l'échelle 2 ; le HUD en jeu, lui, est à l'échelle 3) ;</li>
 *   <li>{@code =vite} : 1920×1080 seul ;</li>
 *   <li>{@code =<cas>,<cas>…} : ces cas seuls, aux trois tailles.</li>
 * </ul>
 * Captures {@code run/screenshots/garde_<taille>_<nn>_<cas>.png}.
 */
public final class StaffDebug {

    /** Une capture : nom, cas (onglet / sous-vue), fenêtre et échelle GUI, données denses (noms longs, listes longues). */
    private record Shot(String nom, String cas, int fw, int fh, int echelle, boolean dense) {}

    /** Tous les onglets et sous-vues du Poste de garde et de l'Infirmerie (audit et captures avant / après). */
    static final String[] CAS = {
            "alertes", "alertes_vides", "joueurs", "joueurs_recherche", "joueurs_aucun", "fiche_haut", "fiche_bas",
            "sanction", "sanction_sceau", "chat", "journal", "journal_filtre", "commandes", "commandes_recherche",
            "blesses", "blesses_p2", "zones", "zones_nouvelle", "hopitaux", "reglages",
            "dense_alertes", "dense_joueurs", "dense_fiche", "dense_journal",
            "fiche_sanctions", "fiche_notes", "fiche_comptes", "journal_detail", "journal_pid", "journal_vide",
            "annonces", "annonce_confirm", "action_noir", "action_suppr",
            "hud_annonce", "hud_annonce_perso", "hud_noir", "hud_plaques"};

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
                SHOTS.add(new Shot(String.format("garde_%s_%02d_%s", tag, i + 1, c),
                        "dense_fiche".equals(c) ? "fiche_haut" : c.replace("dense_", ""), f[0], f[1], f[2], c.startsWith("dense_")));
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register(StaffDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 13000");
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
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh()) {
                mc.getWindow().setWindowed(s.fw(), s.fh());
            }
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
        }
        if (k == 4) {
            inject(snap(me, s.dense(), s.cas()));
            inject(profile(me, s.dense()));
            StaffDemo.activer(me, profile(me, s.dense()));
            StaffClient.debugReceive(InfirmerieDebug.snapshot(me));
            if (s.cas().startsWith("hud_")) { hud(mc, s.cas()); return; }
            boolean ko = switch (s.cas()) {
                case "blesses", "blesses_p2", "zones", "zones_nouvelle", "hopitaux", "reglages" -> true;
                default -> false;
            };
            GardeScreen g = new GardeScreen(ko ? "ko" : "alerts");
            mc.setScreenAndShow(g);
            setup(g, s.cas(), me);
        }
        if (k == 8 && "fiche_bas".equals(s.cas()) && mc.gui.screen() instanceof StaffScreen g) g.mouseScrolled(0, 0, 0, -40);
        if (k == 8 && "journal_detail".equals(s.cas()) && mc.gui.screen() instanceof StaffScreen g) g.journalVue.debugDetail(2);
        if (k == STEP - 6 && "sanction_sceau".equals(s.cas()) && mc.gui.screen() instanceof StaffScreen g) g.debugSeal();
        if (k == STEP - 3) {
            Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
        }
    }

    private static void setup(GardeScreen g, String cas, String me) {
        switch (cas) {
            case "alertes", "alertes_vides" -> g.debugTab("alerts");
            case "joueurs" -> g.debugTab("players");
            case "joueurs_recherche" -> { g.debugTab("players"); g.debugSearch("#00042"); }
            case "joueurs_aucun" -> { g.debugTab("players"); g.debugSearch("Itachi"); }
            case "fiche_haut", "fiche_bas" -> g.debugProfile(me);
            case "fiche_sanctions" -> { g.debugProfile(me); g.fiche.sous = "sanctions"; }
            case "fiche_notes" -> { g.debugProfile(me); g.fiche.sous = "notes"; }
            case "fiche_comptes" -> { g.debugProfile(me); g.fiche.sous = "comptes"; }
            case "action_noir" -> { g.debugProfile(me); g.fiche.debugAction("blind"); g.formulaire.debugValeur(0, "Vous perdez connaissance…"); }
            case "action_suppr" -> { g.debugProfile(me); g.fiche.debugAction("perso_suppr"); }
            case "sanction", "sanction_sceau" -> { g.debugProfile(me); g.fiche.sous = "sanctions"; g.debugConfirm(3); }
            case "chat" -> g.debugTab("chat");
            case "journal", "journal_detail" -> g.debugTab("journal");
            case "journal_filtre" -> {
                g.debugTab("journal");
                g.journalVue.cats.add("COMBAT");
                g.journalVue.cats.add("KO");
                g.journalVue.periode = "24h";
                g.journalVue.demander();
            }
            case "journal_pid" -> { g.journalVue.filtrerPid("00042", "Kazuki Uchiha"); g.debugTab("journal"); }
            case "journal_vide" -> { g.debugTab("journal"); g.journalVue.debugTexte("introuvable"); }
            case "annonces" -> {
                g.debugTab("annonces");
                g.annoncesVue.debugRemplir(false, "Hokage", "Rassemblement devant la tour du Hokage dans dix minutes.", "");
            }
            case "annonce_confirm" -> {
                g.debugTab("annonces");
                g.annoncesVue.debugRemplir(false, "Hokage", "Rassemblement devant la tour du Hokage dans dix minutes.", "");
                g.annoncesVue.debugEnvoyer();
            }
            case "commandes" -> g.debugTab("cmds");
            case "commandes_recherche" -> { g.debugTab("cmds"); g.debugCmdSearch("ata"); }
            case "blesses" -> g.debugKoTab("blesses");
            case "blesses_p2" -> { g.debugKoTab("blesses"); g.debugPage(1); }
            case "zones" -> { g.debugKoTab("zones"); g.debugSelect("dojo"); }
            case "zones_nouvelle" -> { g.debugKoTab("zones"); g.debugDraft(15905, 9690, 15930, 9705); }
            case "hopitaux" -> g.debugKoTab("hopitaux");
            case "reglages" -> g.debugKoTab("reglages");
            default -> { }
        }
    }

    /** Effets en jeu (HUD) : annonce globale / personnelle, écran noir, plaques staff (sur soi, vue de face). */
    private static void hud(Minecraft mc, String cas) {
        switch (cas) {
            case "hud_annonce" -> {
                EffetsStaff.debugAnnonce(false, "Hokage", "Rassemblement devant la tour du Hokage dans dix minutes.", 1500);
                EffetsStaff.recevoir("{\"t\":\"seisme\",\"f\":3,\"s\":1}");
            }
            case "hud_annonce_perso" -> EffetsStaff.debugAnnonce(true, "", "Le Hokage te convoque à son bureau. Viens seul.", 1500);
            case "hud_noir" -> EffetsStaff.recevoir("{\"t\":\"noir\",\"on\":true,\"texte\":\"Vous perdez connaissance…\",\"fondu\":4,\"s\":0}");
            case "hud_plaques" -> {
                PlaquesStaff.debugSoi(mc.player.getUUID());
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }
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

    /** État d'exemple ; {@code dense} : noms et motifs longs, listes longues (pires cas de mise en page). */
    static String snap(String me, boolean dense, String cas) {
        JsonObject o = JsonParser.parseString(SNAP.replace("$ME", me)).getAsJsonObject();
        if ("alertes_vides".equals(cas)) o.add("alerts", new JsonArray());
        // champs du protocole du 14/10 (PLUGINS_REFERENCE §4 bis.2) : marqueurs des joueurs, mode staff
        o.addProperty("modeStaff", "annonces".equals(cas) || "joueurs".equals(cas));
        JsonArray pl = o.getAsJsonArray("players");
        JsonObject p0 = pl.get(0).getAsJsonObject();
        p0.addProperty("notes", 3);
        JsonArray et = new JsonArray();
        et.add("surveillé");
        et.add("avertissement");
        p0.add("etiquettes", et);
        pl.get(1).getAsJsonObject().addProperty("gele", true);
        pl.get(3).getAsJsonObject().addProperty("modeStaff", true);
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
            p.addProperty("village", "kirigakure");
            p.addProperty("rank", "Jōnin spécial");
            p.addProperty("state", "ATA pleine · repos 3/20 · gelé par Hiro");
            p.addProperty("ryo", 1234567);
            p.addProperty("playtime", 98765);
        }
        return p.toString();
    }

    private static void densify(JsonObject o, String me) {
        String[] persos = {"Kazuki Uchiha-Senju no Mikoto", "Ren Hyūga de la Branche Secondaire", "Daisuke Sarutobi",
                "Aya Haruno", "Kenta Aburame", "Sora Nara", "Mei Akimichi", "Hana Inuzuka", "Itsuki Yamanaka",
                "Shion Hōzuki", "Tetsuya Kaguya", "Yūki Senju", "Nobu Hatake", "Asuka Uzumaki-Namikaze"};
        String[] pseudos = {"xX_Raiden_FR_Officiel_Xx", "Mika_", "Dais", "Aya", "kenta.a", "sora", "mei.a", "hana_rp",
                "itsuki_y", "ShionHozukiDu75", "tetsu", "yuki", "nobu", "asuka_un"};
        JsonArray pl = new JsonArray();
        for (int i = 0; i < persos.length; i++) {
            JsonObject p = new JsonObject();
            p.addProperty("uuid", me);
            p.addProperty("name", pseudos[i]);
            p.addProperty("perso", persos[i]);
            p.addProperty("pid", String.format("%05d", 42 + i * 137));
            p.addProperty("rank", i % 3 == 0 ? "Jōnin spécial" : i % 3 == 1 ? "Genin" : "Chūnin");
            p.addProperty("village", i % 4 == 0 ? "kirigakure" : "konoha");
            p.addProperty("state", i % 5 == 0 ? "ATA pleine · repos 3/20 · gelé" : i % 5 == 2 ? "À terre" : "");
            pl.add(p);
        }
        o.add("players", pl);
        JsonArray al = o.getAsJsonArray("alerts");
        JsonObject a0 = al.get(0).getAsJsonObject();
        a0.addProperty("perso", persos[0]);
        a0.addProperty("name", pseudos[0]);
        a0.addProperty("rank", "Jōnin spécial");
        a0.addProperty("motif", "Combat hors terrain d'entraînement ×4 sur des joueurs sans arme, en pleine place du marché de Konoha");
        al.get(1).getAsJsonObject().addProperty("perso", persos[1]);
        for (int k = 0; k < 4; k++) al.add(al.get(k % 3).deepCopy());
        JsonArray j = o.getAsJsonArray("journal");
        for (int k = 0; k < 4; k++) {
            JsonObject e = new JsonObject();
            e.addProperty("time", "14:0" + k);
            e.addProperty("name", "Administrateur_Principal");
            e.addProperty("grade", 3);
            e.addProperty("kind", k % 2 == 0 ? "sanction" : "tp");
            e.addProperty("type", k % 2 == 0 ? "Bannissement temporaire" : "Téléportation forcée");
            e.addProperty("detail", "#00042 Kazuki Uchiha-Senju no Mikoto (xX_Raiden_FR_Officiel_Xx) · Combat hors terrain ×4, marché");
            j.add(e);
        }
    }

    private static void inject(String json) {
        StaffClient.debugReceive(json);
    }

    private static final String SNAP = """
            {"t":"snap","grade":2,"gradeName":"Modérateur","staffOnline":4,
             "alerts":[
              {"id":"ag-1","kind":"agression","uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","pid":"00042","rank":"Genin","village":"konoha","online":true,
               "motif":"Combat hors terrain d'entraînement ×4","ctx":["3 victimes différentes","world 812 64 -233","à l'instant"],"score":157},
              {"id":"aide-2","kind":"aide","uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","pid":"00007","rank":"Genin","online":true,
               "motif":"Appel à l'aide · à terre","ctx":["world 640 70 -120","il y a 2 min"],"score":0},
              {"id":"rep-3","kind":"signalement","uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","pid":"00013","rank":"Chūnin","online":true,
               "motif":"Signalé par 2 joueurs · HRP au chat","ctx":["world 700 66 -180","il y a 6 min"],"score":60}],
             "players":[
              {"uuid":"$ME","name":"Raiden_FR","perso":"Kazuki Uchiha","pid":"00042","rank":"Genin","village":"konoha","state":"ATA pleine · repos 3/20"},
              {"uuid":"$ME","name":"Mika_","perso":"Ren Hyūga","pid":"00007","rank":"Genin","village":"konoha","state":"À terre"},
              {"uuid":"$ME","name":"Dais","perso":"Daisuke Sarutobi","pid":"00013","rank":"Chūnin","village":"konoha","state":""},
              {"uuid":"$ME","name":"Aya","perso":"Aya Haruno","pid":"00000","rank":"Chūnin","village":"konoha","state":""}],
             "chat":[
              {"time":"17:41","grade":1,"name":"Aya","text":"Ren est à terre dans la forêt est, j'y vais.","sys":false},
              {"time":"17:42","grade":0,"name":"Système","text":"Alerte : Kazuki Uchiha (Raiden_FR) · combat hors terrain ×4 (score 157).","sys":true},
              {"time":"17:43","grade":2,"name":"Hiro","text":"Je le prends, c'est la deuxième fois cette semaine.","sys":false},
              {"time":"17:44","grade":4,"name":"Omz","text":"Ok, applique l'escalade et marque les preuves.","sys":false}],
             "journal":[
              {"time":"17:40","name":"Aya","grade":1,"kind":"soin","type":"Paume de soin","detail":"Ren Hyūga"},
              {"time":"17:38","name":"Aya","grade":1,"kind":"tp","type":"TP vers","detail":"#00007 Ren Hyūga (Mika_)"},
              {"time":"17:31","name":"Omz","grade":4,"kind":"ko","type":"ATA levée","detail":"Daisuke Sarutobi"},
              {"time":"17:20","name":"Hiro","grade":2,"kind":"ko","type":"Réveil forcé","detail":"#00042 Kazuki Uchiha (Raiden_FR)"},
              {"time":"16:58","name":"Omz","grade":4,"kind":"monde","type":"Zone créée","detail":"repos « ichiraku »"},
              {"time":"15:47","name":"Hiro","grade":2,"kind":"sanction","type":"Avertissement","detail":"Raiden_FR · Combat d'arène"}],
             "commands":[
              {"cat":"Joueurs et modération","cmd":"/garde","desc":"Ouvrir le Poste de garde","grade":1},
              {"cat":"Joueurs et modération","cmd":"/pid <joueur|#N°>","desc":"N° de personnage, compte, état (hors ligne avec #N°)","grade":1},
              {"cat":"Joueurs et modération","cmd":"/tp <joueur|#N°>","desc":"Aller à un joueur","grade":1},
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
             "record":[{"when":"05/10 · 21:14","text":"Mute 1 jour · Insultes · par Hiro","status":"actif","active":true},
                       {"when":"01/10 · 18:40","text":"Avertissement · Combat d'arène · par Omz","status":"noté","active":false}],
             "recordTotal":2,"recordActive":1,
             "offenses":[
              {"id":"hrp","name":"HRP","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"metagaming","name":"Metagaming","strikes":1,"next":"Mute 6 h","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"powergaming","name":"Powergaming","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"combat_arene","name":"Combat d'arène","strikes":1,"next":"Ban 1 jour","tone":"ban","locked":false,"lockGrade":"Modérateur"},
              {"id":"nlr","name":"NLR non respecté","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"fear_pain","name":"Fear / Pain RP ignoré","strikes":0,"next":"Avertissement","tone":"warn","locked":false,"lockGrade":"Helper"},
              {"id":"insultes","name":"Insultes","strikes":1,"next":"Mute 1 jour","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"spam","name":"Spam","strikes":0,"next":"Mute 15 min","tone":"mute","locked":false,"lockGrade":"Modérateur"},
              {"id":"triche","name":"Triche","strikes":0,"next":"Ban 30 jours","tone":"ban","locked":true,"lockGrade":"Admin"}],
             "msgs":[{"time":"06/10 à 17:48","text":"viens au marché on règle ça maintenant"},
                     {"time":"06/10 à 17:49","text":"le RP on s'en fiche, on se bat"},
                     {"time":"06/10 à 17:51","text":"gg ez, au suivant"}]}
            """;
}
