package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serveur simulé du Poste de garde, pour le banc d'essai seulement ({@link StaffDebug}, {@code REBORN_STAFF_DEBUG}) :
 * répond aux demandes {@code journal}, {@code notes}, {@code note_add}, {@code note_del}, {@code act},
 * {@code annonce}, {@code seisme} et {@code modestaff} exactement comme le décrit le protocole
 * (PLUGINS_REFERENCE §4 bis), à partir d'un journal d'exemple de ~200 entrées sur toutes les catégories.
 * Jamais actif en production ({@link StaffClient#demo} n'est posé que par le banc).
 */
final class StaffDemo {

    private static final List<JsonObject> JOURNAL = new ArrayList<>();
    private static final Map<String, JsonArray> NOTES = new HashMap<>();
    private static String profil;

    private StaffDemo() {}

    static void activer(String moi, String profilJson) {
        StaffClient.demo = true;
        profil = profilJson;
        if (JOURNAL.isEmpty()) remplir(moi);
        if (NOTES.isEmpty()) {
            JsonArray n = new JsonArray();
            n.add(note("n1", "13/10 22:41", "Hiro", 2, "avertissement", "Averti pour HRP au marché (2e fois). Prochaine fois : sanction.", true));
            n.add(note("n2", "12/10 19:05", "Omz", 4, "surveillé", "Soupçon de double compte avec Raiden_alt : même IP, se connectent l'un après l'autre.", true));
            n.add(note("n3", "09/10 17:30", "Aya", 1, "", "Très bon RP pendant l'examen chūnin, à proposer pour l'event du 20.", false));
            NOTES.put("42", n);
        }
    }

    static void handle(JsonObject o) {
        String a = StaffScreen.str(o, "a");
        switch (a) {
            case "journal" -> StaffClient.debugReceive(journal(o).toString());
            case "notes" -> StaffClient.debugReceive(notes(StaffScreen.str(o, "pid")).toString());
            case "note_add" -> {
                String pid = StaffClient.normPid(StaffScreen.str(o, "pid"));
                JsonArray neuf = new JsonArray();
                neuf.add(note("n" + System.nanoTime(), new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date()),
                        "Toi", 2, StaffScreen.str(o, "etiquette"), StaffScreen.str(o, "texte"), true));
                neuf.addAll(NOTES.getOrDefault(pid, new JsonArray()));
                NOTES.put(pid, neuf);
                StaffClient.debugReceive(notes(pid).toString());
                toast("Note ajoutée.");
            }
            case "note_del" -> {
                String pid = StaffClient.normPid(StaffScreen.str(o, "pid"));
                JsonArray n = NOTES.get(pid);
                if (n != null) for (int i = 0; i < n.size(); i++)
                    if (StaffScreen.str(n.get(i).getAsJsonObject(), "id").equals(StaffScreen.str(o, "id"))) { n.remove(i); break; }
                StaffClient.debugReceive(notes(pid).toString());
                toast("Note supprimée.");
            }
            case "act" -> act(o);
            case "annonce" -> {
                JsonObject an = new JsonObject();
                an.addProperty("t", "annonce");
                an.addProperty("portee", "global");
                an.addProperty("titre", StaffScreen.str(o, "titre"));
                an.addProperty("texte", StaffScreen.str(o, "m"));
                an.addProperty("s", StaffScreen.num(o, "s"));
                EffetsStaff.recevoir(an.toString());
                toast("Annonce envoyée à tout le serveur.");
            }
            case "seisme" -> {
                JsonObject se = new JsonObject();
                se.addProperty("t", "seisme");
                se.addProperty("f", StaffScreen.num(o, "f"));
                se.addProperty("s", StaffScreen.num(o, "s"));
                EffetsStaff.recevoir(se.toString());
                toast("Séisme déclenché.");
            }
            case "modestaff" -> {
                JsonObject snap = StaffClient.snapshot();
                if (snap != null) {
                    boolean on = !StaffScreen.bool(snap, "modeStaff");
                    snap.addProperty("modeStaff", on);
                    snap.addProperty("modeAnim", false);
                    toast(on ? "Mode staff activé : invisible, aucune mécanique RP." : "Mode staff coupé.");
                }
            }
            case "modeanim" -> {
                JsonObject snap = StaffClient.snapshot();
                if (snap != null) {
                    boolean on = !StaffScreen.bool(snap, "modeAnim");
                    snap.addProperty("modeAnim", on);
                    snap.addProperty("modeStaff", false);
                    toast(on ? "Mode animation : mécaniques RP permises, tout est journalisé." : "Mode animation coupé.");
                }
            }
            case "cherche" -> StaffClient.debugReceive(cherche(o).toString());
            case "kill" -> toast("ok".equals(StaffScreen.str(o, "v")) ? "Mise à mort validée." : "Mise à mort refusée.");
            case "dismiss" -> toast("Alerte ignorée.");
            case "profile" -> { if (profil != null) StaffClient.debugReceive(profil); }
            case "persos" -> StaffClient.debugReceive(persos(StaffScreen.str(o, "t")).toString());
            case "perso_act" -> persoAct(o);
            case "perso_slots" -> {
                JsonObject d = persos(StaffScreen.str(o, "t"));
                JsonObject sl = d.getAsJsonObject("slots");
                int delta = StaffScreen.num(o, "delta") >= 0 ? 1 : -1;
                sl.addProperty("bonus", StaffScreen.num(sl, "bonus") + delta);
                if (StaffScreen.num(sl, "total") > 0) sl.addProperty("total", StaffScreen.num(sl, "total") + delta);
                StaffClient.debugReceive(d.toString());
                toast(delta > 0 ? "Emplacement débloqué." : "Emplacement retiré.");
            }
            case "perso_creer" -> {
                JsonObject d = persos(StaffScreen.str(o, "t"));
                JsonObject c = perso("demo-" + System.nanoTime(), "01413", StaffScreen.str(o, "nom"), "", "", "Genin", 12, 180, false, false, "15/10/2026");
                d.getAsJsonArray("persos").add(c);
                d.getAsJsonObject("slots").addProperty("used", d.getAsJsonArray("persos").size());
                StaffClient.debugReceive(d.toString());
                toast("Personnage « " + StaffScreen.str(o, "nom") + " » créé (N° 01413).");
            }
            default -> { }
        }
    }

    /* ================================================================ personnages d'un compte (fiche › Personnages) */

    private static final Map<String, JsonObject> PERSOS = new HashMap<>();
    /** Compte hors ligne du banc (personnages de « sorasan »). */
    static final String HORS_LIGNE = "00000000-0000-0000-0000-000000000009";

    private static JsonObject perso(String id, String pid, String nom, String clan, String village, String rang, int age, int taille,
                                    boolean actif, boolean mort, String cree) {
        JsonObject c = new JsonObject();
        c.addProperty("id", id);
        c.addProperty("pid", pid);
        c.addProperty("nom", nom);
        c.addProperty("clan", clan);
        c.addProperty("village", village);
        c.addProperty("villageCourt", village.replace("gakure", ""));
        c.addProperty("rang", rang);
        c.addProperty("rangId", GardeActions.sans(rang).toUpperCase(Locale.ROOT).replace(' ', '_'));
        c.addProperty("age", age);
        c.addProperty("taille", taille);
        c.addProperty("sexe", "Homme");
        c.addProperty("art", "TAIJUTSU");
        c.addProperty("affinite", "STRENGTH");
        c.addProperty("mort", mort);
        c.addProperty("actif", actif);
        c.addProperty("cree", cree);
        c.addProperty("niveau", 4);
        c.addProperty("pvMax", 1380);
        c.addProperty("chakraMax", 16000);
        c.addProperty("points", 3);
        c.addProperty("bonus", 2);
        c.addProperty("techniques", 6);
        c.addProperty("blessures", 1);
        c.addProperty("ryo", 12450);
        JsonArray nat = new JsonArray();
        nat.add("Katon");
        c.add("natures", nat);
        JsonArray st = new JsonArray();
        String[] noms = {"Taïjutsu", "Kenjutsu", "Ninjutsu", "Contrôle", "Vigueur", "Chakra"};
        int[] vals = {14, 6, 11, 9, 12, 10};
        for (int i = 0; i < noms.length; i++) {
            JsonObject x = new JsonObject();
            x.addProperty("n", noms[i]);
            x.addProperty("v", vals[i]);
            st.add(x);
        }
        c.add("stats", st);
        return c;
    }

    private static JsonObject persos(String uuid) {
        JsonObject d = PERSOS.get(uuid);
        if (d == null) {
            boolean horsLigne = HORS_LIGNE.equals(uuid);
            d = new JsonObject();
            d.addProperty("t", "persos");
            d.addProperty("uuid", uuid);
            d.addProperty("pseudo", horsLigne ? "sorasan" : "Raiden_FR");
            d.addProperty("online", !horsLigne);
            d.addProperty("selection", false);
            JsonArray l = new JsonArray();
            if (horsLigne) {
                l.add(perso("demo-sora", "00009", "Sora Uchiha", "Uchiha", "Konohagakure", "Jōnin", 27, 183, false, false, "28/09/2026"));
                l.add(perso("demo-kiyo", "00311", "Kiyo Hōzuki", "Hozuki", "Kirigakure", "Genin", 15, 164, false, false, "11/10/2026"));
            } else {
                l.add(perso("demo-kazuki", "00042", "Kazuki Uchiha", "Uchiha", "Konohagakure", "Genin", 14, 168, true, false, "02/10/2026"));
                l.add(perso("demo-ren", "00118", "Ren Uchiha", "Uchiha", "Konohagakure", "Chūnin", 19, 181, false, false, "07/10/2026"));
                l.add(perso("demo-akira", "00087", "Akira Sabaku", "Sabaku", "Sunagakure", "Tokubetsu Jōnin", 31, 176, false, true, ""));
                d.addProperty("playtime", 2472);
            }
            d.add("persos", l);
            JsonObject sl = new JsonObject();
            sl.addProperty("used", l.size());
            sl.addProperty("bonus", horsLigne ? 1 : 2);
            sl.addProperty("base", horsLigne ? -1 : 2);
            sl.addProperty("total", horsLigne ? -1 : 4);
            d.add("slots", sl);
            JsonObject dr = new JsonObject();
            String[][] g = {{"voir", "1"}, {"modifier", "2"}, {"actif", "2"}, {"progression", "3"}, {"mort", "3"}, {"emplacements", "3"},
                    {"creer", "3"}, {"reset", "3"}, {"suppr", "3"}};
            for (String[] x : g) dr.addProperty(x[0], Integer.parseInt(x[1]));
            d.add("droits", dr);
            PERSOS.put(uuid, d);
        }
        d.addProperty("grade", StaffScreen.grade(StaffClient.snapshot()));
        return d;
    }

    /** Vide les personnages simulés (nouveau cas du banc). */
    static void persosAZero() { PERSOS.clear(); }

    private static void persoAct(JsonObject o) {
        JsonObject d = persos(StaffScreen.str(o, "t"));
        JsonArray l = d.getAsJsonArray("persos");
        JsonObject c = null;
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            JsonObject x = l.get(i).getAsJsonObject();
            if (StaffScreen.str(x, "id").equals(StaffScreen.str(o, "c")) || !StaffScreen.str(o, "pid").isEmpty()
                    && StaffClient.normPid(StaffScreen.str(x, "pid")).equals(StaffClient.normPid(StaffScreen.str(o, "pid")))) { c = x; idx = i; }
        }
        if (c == null) { toast("Personnage introuvable (supprimé ?)."); return; }
        String nom = StaffScreen.str(c, "nom");
        final JsonObject cc = c;
        String msg = switch (StaffScreen.str(o, "op")) {
            case "identite" -> {
                for (String k : new String[]{"nom", "sexe"}) if (!StaffScreen.str(o, k).isEmpty()) cc.addProperty(k, StaffScreen.str(o, k));
                for (String k : new String[]{"age", "taille"}) {
                    try { cc.addProperty(k, Integer.parseInt(StaffScreen.str(o, k))); } catch (NumberFormatException ignored) { }
                }
                yield "Identité mise à jour.";
            }
            case "origine" -> {
                cc.addProperty("village", StaffScreen.str(o, "village"));
                cc.addProperty("villageCourt", StaffScreen.str(o, "village").replace("gakure", ""));
                cc.addProperty("clan", StaffScreen.str(o, "clan").equalsIgnoreCase("aucun") ? "" : StaffScreen.str(o, "clan"));
                yield "Clan et village mis à jour.";
            }
            case "art" -> { cc.addProperty("art", StaffScreen.str(o, "art")); cc.addProperty("affinite", StaffScreen.str(o, "affinite")); yield "Art et affinité mis à jour."; }
            case "progression" -> {
                cc.addProperty("rangId", StaffScreen.str(o, "rang"));
                try { cc.addProperty("bonus", Integer.parseInt(StaffScreen.str(o, "bonus"))); } catch (NumberFormatException ignored) { }
                yield "Progression mise à jour.";
            }
            case "actif" -> {
                for (var e : l) e.getAsJsonObject().addProperty("actif", false);
                cc.addProperty("actif", true);
                yield "Le joueur joue maintenant " + nom + ".";
            }
            case "mort" -> { cc.addProperty("mort", true); cc.addProperty("actif", false); yield nom + " est mort RP."; }
            case "ressusciter" -> { cc.addProperty("mort", false); yield nom + " est ressuscité."; }
            case "reset" -> { cc.addProperty("rang", "Genin"); cc.addProperty("points", 0); cc.addProperty("bonus", 0); yield nom + " remis à zéro. Sauvegarde faite."; }
            case "suppr" -> { l.remove(idx); d.getAsJsonObject("slots").addProperty("used", l.size()); yield nom + " supprimé. Sauvegarde faite."; }
            default -> "Action envoyée.";
        };
        StaffClient.debugReceive(d.toString());
        toast(msg);
    }

    /** Index des N° du serveur simulé (personnages hors ligne compris). */
    private static final String[][] INDEX = {
            {"00009", "Sora Uchiha", "sorasan", "Jōnin", "il y a 2 j"}, {"00118", "Ren Uchiha", "renU", "Genin", "il y a 5 h"},
            {"00077", "Kazuki Nara", "kaznara", "Chūnin", "hier"}};

    private static JsonObject cherche(JsonObject q) {
        String t = GardeActions.sans(StaffScreen.str(q, "q"));
        JsonObject r = new JsonObject();
        r.addProperty("t", "cherche");
        r.addProperty("req", StaffScreen.num(q, "req"));
        r.addProperty("q", t);
        JsonArray js = new JsonArray();
        for (String[] e : INDEX) {
            JsonObject p = new JsonObject();
            p.addProperty("uuid", "00000000-0000-0000-0000-0000000" + e[0]);
            p.addProperty("pid", e[0]);
            p.addProperty("perso", e[1]);
            p.addProperty("name", e[2]);
            p.addProperty("rank", e[3]);
            p.addProperty("online", false);
            p.addProperty("vu", e[4]);
            boolean ok = true;
            for (String w : t.split(" +")) if (!GardeRecherche.correspond(p, w)) ok = false;
            if (ok) js.add(p);
        }
        r.add("joueurs", js);
        return r;
    }

    private static void toast(String m) {
        JsonObject t = new JsonObject();
        t.addProperty("t", "toast");
        t.addProperty("m", m);
        StaffClient.debugReceive(t.toString());
    }

    private static void act(JsonObject o) {
        String op = StaffScreen.str(o, "op");
        JsonObject p = profil == null ? null : JsonParser.parseString(profil).getAsJsonObject();
        String msg = switch (op) {
            case "freeze" -> { if (p != null) { p.addProperty("gele", true); p.addProperty("gelePar", "toi"); p.addProperty("geleMotif", StaffScreen.str(o, "m")); } yield "Joueur gelé."; }
            case "unfreeze" -> { if (p != null) p.addProperty("gele", false); yield "Joueur dégelé."; }
            case "blind" -> {
                boolean on = p == null || !StaffScreen.bool(p, "noir");
                if (p != null) p.addProperty("noir", on);
                yield on ? "Écran noir envoyé." : "Écran noir levé.";
            }
            case "tp" -> { if (p != null) p.addProperty("back", true); yield "Téléporté au joueur."; }
            case "back" -> "Retour à ta position.";
            case "bring" -> { if (p != null) p.addProperty("retour", true); yield "Joueur ramené."; }
            case "announce" -> "Annonce personnelle envoyée.";
            case "perso_reset" -> "Personnage remis à zéro.";
            case "perso_suppr" -> "Personnage supprimé.";
            default -> "Action « " + op + " » envoyée.";
        };
        if (p != null) {
            profil = p.toString();
            StaffClient.debugReceive(profil);
        }
        toast(msg);
    }

    private static JsonObject notes(String pid) {
        JsonObject r = new JsonObject();
        r.addProperty("t", "notes");
        r.addProperty("pid", pid);
        r.addProperty("perso", "Kazuki Uchiha");
        r.addProperty("pseudo", "Raiden_FR");
        r.add("entries", NOTES.getOrDefault(StaffClient.normPid(pid), new JsonArray()).deepCopy());
        return r;
    }

    private static JsonObject note(String id, String date, String auteur, int grade, String etiq, String texte, boolean suppr) {
        JsonObject n = new JsonObject();
        n.addProperty("id", id);
        n.addProperty("date", date);
        n.addProperty("auteur", auteur);
        n.addProperty("auteurGrade", grade);
        n.addProperty("etiquette", etiq);
        n.addProperty("texte", texte);
        n.addProperty("suppr", suppr);
        return n;
    }

    /* ================================================================ journal d'exemple */

    private static JsonObject personne(String uuid, String pseudo, String pid, String perso, int grade) {
        JsonObject p = new JsonObject();
        p.addProperty("uuid", uuid);
        p.addProperty("pseudo", pseudo);
        p.addProperty("pid", pid);
        p.addProperty("perso", perso);
        p.addProperty("grade", grade);
        p.addProperty("gradeName", StaffScreen.gradeName(grade).replace("Modo", "Modérateur"));
        return p;
    }

    private static void remplir(String moi) {
        JsonObject[] gens = {
                personne(moi, "Raiden_FR", "00042", "Kazuki Uchiha", 0),
                personne(moi, "Mika_", "00007", "Ren Hyūga", 0),
                personne(moi, "Dais", "00013", "Daisuke Sarutobi", 0),
                personne(moi, "Aya", "00000", "Aya Haruno", 1),
                personne(moi, "Hiro", "00021", "Hiroshi Nara", 2),
                personne(moi, "Omz", "00001", "Omura Senju", 4),
                personne(moi, "xX_Raiden_FR_Officiel_Xx", "01412", "Kazuki Uchiha-Senju no Mikoto", 0)};
        Object[][] modeles = {
                {"JOUEUR", "CONNEXION", false, "depuis le launcher 0.3.46"},
                {"JOUEUR", "DECONNEXION", false, "session de 1 h 12"},
                {"JOUEUR", "CHOIX_PERSO", false, "personnage n° 2 sur 3"},
                {"INVENTAIRE", "RAMASSE", false, "Kunai ×3 · commun"},
                {"INVENTAIRE", "LACHE", false, "Parchemin vierge ×1 · peu commun"},
                {"INVENTAIRE", "EQUIPE", false, "Sac de voyage (24 places)"},
                {"OBJET", "OBTENU", false, "Parchemin de Katon : Gōkakyū · rare · bibliothèque de Konoha"},
                {"COMBAT", "DEGATS", true, "6,5 PV · kunai"},
                {"COMBAT", "AGRESSION_HORS_ZONE", true, "place du marché · 3e victime en 5 min"},
                {"KO", "A_TERRE", true, "45 s avant l'inconscience"},
                {"KO", "REVEIL", false, "relevé par un médic"},
                {"DUEL", "DEBUT", true, "terrain 3 · techniques autorisées"},
                {"DUEL", "FIN", true, "victoire · abandon de l'adversaire"},
                {"ECONOMIE", "GAIN", false, "120 ryo · mission rang D"},
                {"ECONOMIE", "ACHAT", false, "Kunai ×10 · 50 ryo · échoppe d'Ichiraku"},
                {"INTERACTION", "PORTER", true, "porté sur le dos"},
                {"INTERACTION", "FOUILLE", true, "a ouvert la sacoche de la cible"},
                {"INTERACTION", "SOIN", true, "paume de soin · +38 PV"},
                {"BIBLIOTHEQUE", "PRISE", false, "Parchemin de Suiton · séance 2/5"},
                {"STAFF", "TP", true, "Poste de garde"},
                {"STAFF", "GEL", true, "motif : refus d'obtempérer"},
                {"STAFF", "ECRAN_NOIR", true, "« Vous perdez connaissance… » · 30 s"},
                {"STAFF", "ANNONCE", false, "« Le marché ouvre dans 10 minutes. »"},
                {"COMMANDE", "COMMANDE", false, "/me range son kunai dans son étui"},
                {"COMMANDE", "COMMANDE", false, "/tp Raiden_FR"},
                {"CHAT", "MESSAGE", false, "quelqu'un pour une mission ce soir ?"},
                {"SYSTEME", "RECHARGEMENT", false, "ShinobiCore rechargé (config.yml)"}};
        long now = System.currentTimeMillis();
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE);
        int n = 0;
        for (int i = 0; i < 204; i++) {
            Object[] m = modeles[(i * 7 + i / 5) % modeles.length];
            long at = now - i * 263_000L - (i % 3) * 17_000L;
            JsonObject e = new JsonObject();
            e.addProperty("id", "demo-" + i);
            e.addProperty("at", at);
            e.addProperty("date", f.format(new Date(at)));
            e.addProperty("cat", (String) m[0]);
            e.addProperty("action", (String) m[1]);
            JsonObject ac = gens[(i * 3) % gens.length];
            if ("STAFF".equals(m[0])) ac = gens[3 + (i % 3)];
            if ("SYSTEME".equals(m[0])) ac = personne("", "CONSOLE", "", "", 4);
            e.add("acteur", ac.deepCopy());
            if ((boolean) m[2]) {
                JsonObject ci = gens[(i * 5 + 1) % gens.length];
                if (ci.get("pid").getAsString().equals(ac.get("pid").getAsString())) ci = gens[(i * 5 + 2) % gens.length];
                e.add("cible", ci.deepCopy());
            } else e.add("cible", null);
            e.addProperty("detail", (String) m[3]);
            e.addProperty("lieu", i % 4 == 0 ? "" : "world " + (812 - i * 3) + " " + (64 + i % 6) + " " + (-233 + i * 2));
            JOURNAL.add(e);
            n++;
        }
    }

    private static JsonObject journal(JsonObject q) {
        List<String> cats = new ArrayList<>();
        for (var c : StaffScreen.arr(q, "cat")) cats.add(c.getAsString());
        String pid = StaffClient.normPid(StaffScreen.str(q, "pid"));
        String joueur = StaffScreen.str(q, "joueur").toLowerCase();
        String txt = StaffScreen.str(q, "q").toLowerCase();
        long du = StaffScreen.lng(q, "du");
        List<JsonObject> ok = new ArrayList<>();
        for (JsonObject e : JOURNAL) {
            if (!cats.isEmpty() && !cats.contains(StaffScreen.str(e, "cat"))) continue;
            if (du > 0 && StaffScreen.lng(e, "at") < du) continue;
            JsonObject ac = StaffScreen.obj(e, "acteur"), ci = StaffScreen.obj(e, "cible");
            if (!pid.isEmpty() && !(pid.equals(StaffClient.normPid(StaffScreen.str(ac, "pid"))) || ci != null && pid.equals(StaffClient.normPid(StaffScreen.str(ci, "pid")))))
                continue;
            if (!joueur.isEmpty()) {
                String t = (StaffScreen.str(ac, "pseudo") + " " + StaffScreen.str(ac, "perso") + " "
                        + (ci == null ? "" : StaffScreen.str(ci, "pseudo") + " " + StaffScreen.str(ci, "perso"))).toLowerCase();
                if (!t.contains(joueur)) continue;
            }
            if (!txt.isEmpty() && !(StaffScreen.str(e, "action") + " " + StaffScreen.str(e, "detail")).toLowerCase().contains(txt)) continue;
            ok.add(e);
        }
        int par = Math.max(1, Math.min(200, q.has("par") ? StaffScreen.num(q, "par") : 50));
        int pages = Math.max(1, (ok.size() + par - 1) / par);
        int page = Math.max(0, Math.min(pages - 1, StaffScreen.num(q, "page")));
        JsonObject r = new JsonObject();
        r.addProperty("t", "journal");
        r.addProperty("req", StaffScreen.num(q, "req"));
        r.addProperty("total", ok.size());
        r.addProperty("page", page);
        r.addProperty("pages", pages);
        r.addProperty("source", "memoire");
        JsonArray cs = new JsonArray();
        for (Object[] c : GardeJournal.CATS) {
            JsonObject co = new JsonObject();
            co.addProperty("id", (String) c[0]);
            co.addProperty("label", (String) c[1]);
            cs.add(co);
        }
        r.add("cats", cs);
        JsonArray es = new JsonArray();
        for (int i = page * par; i < Math.min(ok.size(), (page + 1) * par); i++) es.add(ok.get(i));
        r.add("entries", es);
        return r;
    }
}
