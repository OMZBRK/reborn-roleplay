package fr.reborn.hud.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Serveur simulé du banc ({@link FactionDebug}) : les vues des maquettes v3 (Ren Hyūga, Genin du Corps médical ; Aki
 * Uchiha, chef de la Police ; Daisuke Senju, Hokage ; Hiroshi Nara, Jōnin sans section) au format exact du canal
 * {@code reborn:faction} (PLUGINS_REFERENCE §4 septies), filtrées comme le serveur les filtre (inconnus sans nom ni UUID,
 * ANBU en « ? » pour un gradé). Inactif hors banc.
 */
final class FactionDemo {

    private FactionDemo() {}

    static final UUID AKI = id("aki"), KENTA = id("kenta"), MEI = id("mei"), TARO = id("taro"), RENJI = id("renji"),
            SORA = id("sora"), HIROSHI = id("hiroshi"), YUI = id("yui"), AYA = id("aya"), REN = id("ren"), KAZUKI = id("kazuki"),
            NANAMI = id("nanami"), DAISUKE = id("daisuke"), HANA = id("hana");

    private static UUID id(String n) { return UUID.nameUUIDFromBytes(("faction-demo:" + n).getBytes()); }

    /** Le personnage joué par le banc (« ren », « aki », « daisuke », « hiroshi »). */
    static String qui = "ren";

    static UUID moi() { var p = Minecraft.getInstance().player; return p == null ? REN : p.getUUID(); }

    /* ================================================================ pièces */

    private static JsonObject p(String nom, UUID u, boolean online) {
        JsonObject o = new JsonObject();
        o.addProperty("nom", nom);
        o.addProperty("connu", true);
        o.addProperty("id", UUID.nameUUIDFromBytes(("perso:" + nom).getBytes()).toString());
        o.addProperty("online", online);
        if (u != null) o.addProperty("u", u.toString());
        return o;
    }

    private static JsonObject inconnu(String nom) {
        JsonObject o = new JsonObject();
        o.addProperty("nom", nom);
        o.addProperty("connu", false);
        return o;
    }

    private static JsonObject membre(JsonObject p, String grade, int rang, boolean chef, String rangVillage) {
        p.addProperty("grade", grade);
        p.addProperty("gradeId", grade.toLowerCase());
        p.addProperty("rang", rang);
        if (chef) p.addProperty("chef", true);
        if (rangVillage != null) p.addProperty("rangVillage", rangVillage);
        return p;
    }

    private static JsonObject moiP(String nom) {
        JsonObject o = p(nom, moi(), true);
        o.addProperty("moi", true);
        return o;
    }

    private static JsonArray a(Object... xs) {
        JsonArray r = new JsonArray();
        for (Object x : xs) {
            if (x instanceof JsonObject o) r.add(o);
            else r.add(String.valueOf(x));
        }
        return r;
    }

    private static JsonObject ligne(String t, long n, long ilya) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.addProperty("le", System.currentTimeMillis() - ilya);
        if (n != 0) o.addProperty("n", n);
        return o;
    }

    private static final long J = 86_400_000L, H = 3_600_000L;

    private static JsonObject base(String nom, String rang, String rangNom, String titre, String profil, boolean chefF, boolean chefS,
                                   Object... onglets) {
        JsonObject v = new JsonObject();
        v.addProperty("t", "vue");
        v.addProperty("ok", true);
        JsonObject f = new JsonObject();
        f.addProperty("tag", "KON");
        f.addProperty("nom", "Konohagakure");
        f.addProperty("type", "village");
        f.addProperty("embleme", "konoha");
        f.addProperty("n", 42);
        f.addProperty("kage", "Hokage");
        v.add("faction", f);
        JsonObject m = new JsonObject();
        m.addProperty("nom", nom);
        m.addProperty("rang", rang);
        m.addProperty("rangNom", rangNom);
        m.addProperty("profil", profil);
        m.addProperty("chefFaction", chefF);
        m.addProperty("chefSection", chefS);
        m.addProperty("titre", titre);
        v.add("moi", m);
        v.add("onglets", a(onglets));
        JsonObject peut = new JsonObject();
        peut.addProperty("annoncerVillage", chefF);
        peut.addProperty("annoncerSection", chefS);
        v.add("peut", peut);
        v.add("annonces", annonces());
        return v;
    }

    private static JsonArray annonces() {
        JsonObject a1 = new JsonObject();
        a1.addProperty("le", System.currentTimeMillis() - 2 * H);
        a1.addProperty("texte", "Une réunion aura lieu ce soir sur la place centrale à 20 h. Tous les Chūnin et plus sont attendus.");
        a1.addProperty("titre", "Hokage");
        a1.addProperty("auteur", "Daisuke Senju");
        JsonObject a2 = new JsonObject();
        a2.addProperty("le", System.currentTimeMillis() - 26 * H);
        a2.addProperty("texte", "Les portes nord fermeront au coucher du soleil jusqu'à nouvel ordre.");
        a2.addProperty("titre", "Hokage");
        a2.addProperty("auteur", "Daisuke Senju");
        return a(a1, a2);
    }

    /** Village vu par Ren (maquette 01). */
    private static JsonObject village(boolean ren) {
        JsonObject v = new JsonObject();
        v.addProperty("total", 42);
        JsonArray g = new JsonArray();
        g.add(groupe("KAGE", "Hokage", 1, 0, publique(p("Daisuke Senju", DAISUKE, true))));
        g.add(groupe("COMMANDANT_JONIN", "Commandant Jōnin", 1, 1));
        g.add(groupe("JONIN", "Jōnin", 6, 5, p("Yui Nara", YUI, true)));
        g.add(groupe("TOKUBETSU_JONIN", "Tokubetsu Jōnin", 3, 3));
        g.add(groupe("CHUNIN", "Chūnin", 10, 8, p("Aki Uchiha", AKI, true), p("Nanami Yamanaka", NANAMI, false)));
        g.add(groupe("GENIN_CONFIRME", "Genin confirmé", 7, 6, p("Aya Inuzuka", AYA, true)));
        g.add(ren ? groupe("GENIN", "Genin", 11, 9, moiP("Ren Hyūga"), p("Kazuki Uchiha", KAZUKI, false))
                : groupe("GENIN", "Genin", 11, 9, p("Ren Hyūga", REN, true), p("Kazuki Uchiha", KAZUKI, false)));
        g.add(groupe("ACADEMICIEN", "Académicien", 3, 3));
        v.add("groupes", g);
        return v;
    }

    private static JsonObject publique(JsonObject p) { p.addProperty("publique", true); return p; }

    private static JsonObject groupe(String rang, String nom, int total, int inc, JsonObject... connus) {
        JsonObject o = new JsonObject();
        o.addProperty("rang", rang);
        o.addProperty("nom", nom);
        o.addProperty("total", total);
        JsonArray c = new JsonArray();
        // en ligne en tête, comme le serveur
        for (JsonObject x : connus) if (x.get("online").getAsBoolean()) c.add(x);
        for (JsonObject x : connus) if (!x.get("online").getAsBoolean()) c.add(x);
        o.add("connus", c);
        o.addProperty("inconnus", inc);
        return o;
    }

    /* ================================================================ les quatre lecteurs */

    /** Ren Hyūga : Genin, Recrue du Corps médical, sans droit (maquette 01). */
    static JsonObject ren() {
        JsonObject v = base("Ren Hyūga", "GENIN", "Genin", "Recrue · Corps médical", "MEMBRE", false, false, "section", "village", "annonces");
        JsonObject s = new JsonObject();
        s.addProperty("id", "kon_corps_medical");
        s.addProperty("nom", "Corps médical");
        s.addProperty("insigne", "medical");
        s.addProperty("cachee", false);
        s.addProperty("places", 8);
        s.addProperty("n", 5);
        s.addProperty("chef", false);
        s.addProperty("monGrade", "Recrue");
        s.addProperty("monRang", 3);
        s.addProperty("chefNom", "Yui Nara");
        s.addProperty("chefFem", true);
        s.add("droits", new JsonArray());
        s.add("membres", a(membre(p("Yui Nara", YUI, true), "Médecin-chef", 0, true, null),
                membre(p("Aya Inuzuka", AYA, true), "Infirmière", 2, false, null),
                membre(inconnu("Inconnu"), "Infirmier", 2, false, null),
                membre(inconnu("Inconnue"), "Recrue", 3, false, null),
                membre(moiP("Ren Hyūga"), "Recrue", 3, false, null)));
        JsonObject t = new JsonObject();
        t.addProperty("id", "Complet_Ceremonie");
        t.addProperty("nom", "Blouse du Corps médical");
        t.addProperty("accessoire", "");
        s.add("tenue", t);
        s.add("lieux", a(lieu("Hôpital", "porte", ""), lieu("Pharmacie", "porte", ""), lieu("Réserve de soins", "coffre", "voir, déposer")));
        v.add("section", s);
        v.add("village", village(true));
        return v;
    }

    private static JsonObject lieu(String nom, String type, String acces) {
        JsonObject o = new JsonObject();
        o.addProperty("nom", nom);
        o.addProperty("type", type);
        o.addProperty("acces", acces);
        return o;
    }

    private static JsonObject grade(String id, String nom, int n, String... droits) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("n", n);
        o.add("droits", a((Object[]) droits));
        return o;
    }

    static final String[] TOUS = {"inviter", "inviter_distance", "exclure", "monter", "voir_caisse", "depenser", "coffre_voir",
            "coffre_retirer", "cloche_section", "journal"};

    /** Aki Uchiha : Chūnin, Commissaire et chef de la Police (maquettes 02 et 04). */
    static JsonObject aki() {
        JsonObject v = base("Aki Uchiha", "CHUNIN", "Chūnin", "Commissaire · Police", "CHEF_SECTION", false, true, "section", "village", "annonces");
        JsonObject s = new JsonObject();
        s.addProperty("id", "kon_police");
        s.addProperty("nom", "Police");
        s.addProperty("insigne", "police");
        s.addProperty("cachee", false);
        s.addProperty("places", 10);
        s.addProperty("n", 7);
        s.addProperty("chef", true);
        s.addProperty("monGrade", "Commissaire");
        s.addProperty("monRang", 0);
        s.addProperty("chefNom", "Aki Uchiha");
        s.add("droits", a((Object[]) TOUS));
        s.add("membres", a(membre(moiP("Aki Uchiha"), "Commissaire", 0, true, "Chūnin"),
                membre(p("Kenta Inuzuka", KENTA, true), "Inspecteur", 1, false, "Chūnin"),
                membre(p("Mei Kazane", MEI, true), "Agent", 2, false, "Genin confirmé"),
                membre(p("Tarō Akimichi", TARO, false), "Agent", 2, false, "Genin"),
                membre(p("Renji Sarutobi", RENJI, false), "Agent", 2, false, "Chūnin"),
                membre(p("Sora Hatake", SORA, true), "Recrue", 3, false, "Genin"),
                membre(p("Hiroshi Nara", HIROSHI, false), "Recrue", 3, false, "Jōnin")));
        JsonObject t = new JsonObject();
        t.addProperty("id", "tenueh25");
        t.addProperty("nom", "Uniforme de la Police");
        t.addProperty("accessoire", "brassard de la Police");
        s.add("tenue", t);
        JsonObject c = new JsonObject();
        c.addProperty("solde", 4250);
        c.addProperty("taux", 10);
        c.add("lignes", a(ligne("Amendes · poste de police", 600, J / 2), ligne("Taxe du village", -60, J / 2),
                ligne("Prime à Kenta Inuzuka", -300, 2 * J), ligne("Dotation du village", 2000, 3 * J)));
        s.add("caisse", c);
        s.add("grades", a(grade("commissaire", "Commissaire", 1, TOUS),
                grade("inspecteur", "Inspecteur", 1, "inviter", "exclure", "monter", "voir_caisse", "coffre_voir", "cloche_section", "journal"),
                grade("agent", "Agent", 3, "coffre_voir", "journal"), grade("recrue", "Recrue", 2)));
        s.add("lieux", a(lieu("Poste de police", "porte", ""), lieu("Cellules", "porte", ""), lieu("Coffre des saisies", "coffre", "voir, déposer")));
        s.add("journal", a(ligne("Sora Hatake rejoint la section Police (invité par Aki Uchiha)", 0, J / 2),
                ligne("Police : prime de 300 ryo à Kenta Inuzuka · « arrestation du voleur du marché »", 0, 2 * J),
                ligne("Police : Kenta Inuzuka Agent → Inspecteur", 0, 3 * J),
                ligne("Police : droits de « Agent » → coffre_voir, journal", 0, 4 * J)));
        v.add("section", s);
        v.add("village", village(false));
        return v;
    }

    static JsonObject prochesAki() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "proches");
        o.addProperty("distance", false);
        o.addProperty("n", 7);
        o.addProperty("places", 10);
        JsonObject k = proche("Kazuki Uchiha", KAZUKI, "Genin", 6, null);
        JsonObject n = proche("Nanami Yamanaka", NANAMI, "Chūnin", 14, null);
        JsonObject r = proche("Ren Hyūga", REN, "Genin", 9, "déjà dans une section");
        o.add("pres", a(k, n, r));
        o.add("loin", new JsonArray());
        return o;
    }

    private static JsonObject proche(String nom, UUID u, String rang, int d, String refus) {
        JsonObject o = new JsonObject();
        o.addProperty("id", UUID.nameUUIDFromBytes(("perso:" + nom).getBytes()).toString());
        o.addProperty("u", u.toString());
        o.addProperty("nom", nom);
        o.addProperty("rang", rang);
        o.addProperty("dist", d);
        if (refus != null) o.addProperty("refus", refus);
        return o;
    }

    private static JsonObject tuile(String id, String nom, String insigne, boolean cachee, int n, JsonObject chef, long caisse, JsonObject... connus) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("insigne", insigne);
        o.addProperty("cachee", cachee);
        o.addProperty("n", n);
        o.add("chef", chef);
        o.add("connus", a((Object[]) connus));
        if (caisse >= 0) o.addProperty("caisse", caisse);
        return o;
    }

    private static JsonObject q() { JsonObject o = new JsonObject(); o.addProperty("q", true); return o; }

    private static JsonObject avecGrade(JsonObject p, String g) { p.addProperty("grade", g); return p; }

    /** Hiroshi Nara : Jōnin, sans section ; l'ANBU est un « ? » (maquette 05). */
    static JsonObject hiroshi() {
        JsonObject v = base("Hiroshi Nara", "JONIN", "Jōnin", "Jōnin", "GRADE", false, false, "village", "sections", "annonces");
        v.add("village", village(false));
        v.add("sections", a(q(),
                tuile("kon_police", "Police", "police", false, 7, p("Aki Uchiha", AKI, true), -1, avecGrade(p("Kenta Inuzuka", KENTA, true), "Inspecteur")),
                tuile("kon_corps_medical", "Corps médical", "medical", false, 5, p("Yui Nara", YUI, true), -1, avecGrade(p("Aya Inuzuka", AYA, true), "Infirmière")),
                tuile("kon_academie", "Académie", "academie", false, 3, inconnu("Inconnue"), -1),
                tuile("kon_bureau", "Bureau des missions", "missions", false, 4, inconnu("Inconnue"), -1),
                tuile("kon_garde", "Garde des portes", "garde", false, 6, p("Renji Sarutobi", RENJI, false), -1),
                tuile("kon_archives", "Brigade des archives", "missions", false, 1, inconnu("Inconnue"), -1)));
        return v;
    }

    /** Daisuke Senju : Hokage, chef de faction (maquettes 03 et 05 encart). */
    static JsonObject daisuke() {
        JsonObject v = base("Daisuke Senju", "KAGE", "Kage", "Hokage", "CHEF_FACTION", true, false, "village", "sections", "annonces", "gestion");
        v.add("village", village(false));
        v.add("sections", a(
                tuile("kon_anbu", "ANBU", "anbu", true, 4, p("Kazuki Uchiha", KAZUKI, false), 1900),
                tuile("kon_police", "Police", "police", false, 7, p("Aki Uchiha", AKI, true), 4250),
                tuile("kon_corps_medical", "Corps médical", "medical", false, 5, p("Yui Nara", YUI, true), 3100),
                tuile("kon_academie", "Académie", "academie", false, 3, p("Nanami Yamanaka", NANAMI, false), 800),
                tuile("kon_bureau", "Bureau des missions", "missions", false, 4, p("Hana Kurenai", HANA, true), 6400),
                tuile("kon_garde", "Garde des portes", "garde", false, 6, p("Renji Sarutobi", RENJI, false), 1200)));
        JsonObject g = new JsonObject();
        g.add("droits", a("sections", "taxe", "caisses", "doter", "journal", "annonces", "alertes_palais", "inviter_org"));
        g.addProperty("chef", true);
        g.addProperty("taux", 10);
        g.addProperty("plafond", 30);
        g.addProperty("tresor", 48200);
        g.addProperty("taxeSemaine", 1140);
        g.add("seuils", a(seuil("voir_sections", "Voir les sections", "CHUNIN"), seuil("cloche_village", "Sonner la cloche du village", "JONIN"),
                seuil("cloche_sans_delai", "Sonner sans délai", "COMMANDANT_JONIN"),
                seuil("voliere_palais", "Écrire à la volière du palais", "CHUNIN"), seuil("alertes_palais", "Recevoir les alertes du palais", "JONIN"),
                seuil("prendre_voliere", "Prendre à une volière", "GENIN")));
        String[][] rangs = {{"ACADEMICIEN", "Académicien"}, {"GENIN", "Genin"}, {"GENIN_CONFIRME", "Genin confirmé"}, {"CHUNIN", "Chūnin"},
                {"KONIN", "Konin"}, {"TOKUBETSU_JONIN", "Tokubetsu Jōnin"}, {"JONIN", "Jōnin"}, {"COMMANDANT_JONIN", "Commandant Jōnin"}, {"KAGE", "Kage"}};
        JsonArray ra = new JsonArray();
        for (String[] r : rangs) { JsonObject o = new JsonObject(); o.addProperty("id", r[0]); o.addProperty("nom", r[1]); ra.add(o); }
        g.add("rangs", ra);
        g.add("sections", a(gs("kon_anbu", "ANBU", "anbu", true, "Kazuki Uchiha", 4, 6, 1900), gs("kon_police", "Police", "police", false, "Aki Uchiha", 7, 10, 4250),
                gs("kon_corps_medical", "Corps médical", "medical", false, "Yui Nara", 5, 8, 3100), gs("kon_academie", "Académie", "academie", false, "Nanami Yamanaka", 3, 6, 800),
                gs("kon_bureau", "Bureau des missions", "missions", false, "Hana Kurenai", 4, 6, 6400), gs("kon_garde", "Garde des portes", "garde", false, "Renji Sarutobi", 6, 8, 1200)));
        g.add("charges", a(charge("conseiller", "Conseiller", false, new String[]{"journal", "alertes_palais"}, "Mitsu Aburame", "Gen Hyūga"),
                charge("intendant", "Intendant", false, new String[]{"caisses", "doter", "taxe"}, "Ichirō Akimichi")));
        g.add("journal", a(ligne("Section « Brigade des archives » créée, chef : Nanami Yamanaka, 6 places", 0, H / 6),
                ligne("Taxe des sections : 8 % → 10 %", 0, J), ligne("« Voir les sections » dès Chūnin (avant : Jōnin)", 0, 2 * J)));
        g.add("modeles", a(mo("anbu", "ANBU", "anbu", true), mo("police", "Police", "police", false), mo("medical", "Corps médical", "medical", false),
                mo("academie", "Académie", "academie", false), mo("missions", "Bureau des missions", "missions", false), mo("garde", "Garde des portes", "garde", false)));
        g.add("candidats", a(cand("Yui Nara", "Jōnin", true), cand("Nanami Yamanaka", "Chūnin", false), cand("Aki Uchiha", "Chūnin", true),
                cand("Aya Inuzuka", "Genin confirmé", false), cand("Kazuki Uchiha", "Genin", true), cand("Ren Hyūga", "Genin", true)));
        v.add("gestion", g);
        return v;
    }

    private static JsonObject seuil(String id, String nom, String rang) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("rang", rang);
        return o;
    }

    private static JsonObject gs(String id, String nom, String ins, boolean cachee, String chef, int n, int places, long caisse) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("insigne", ins);
        o.addProperty("cachee", cachee);
        o.addProperty("chefNom", chef);
        o.addProperty("n", n);
        o.addProperty("places", places);
        o.addProperty("caisse", caisse);
        return o;
    }

    private static JsonObject charge(String id, String nom, boolean pub, String[] droits, String... titulaires) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("publique", pub);
        o.add("droits", a((Object[]) droits));
        JsonArray t = new JsonArray();
        for (String x : titulaires) { JsonObject y = new JsonObject(); y.addProperty("id", UUID.nameUUIDFromBytes(x.getBytes()).toString()); y.addProperty("nom", x); t.add(y); }
        o.add("titulaires", t);
        return o;
    }

    private static JsonObject mo(String id, String nom, String ins, boolean cachee) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("insigne", ins);
        o.addProperty("cachee", cachee);
        return o;
    }

    private static JsonObject cand(String nom, String rang, boolean section) {
        JsonObject o = new JsonObject();
        o.addProperty("id", UUID.nameUUIDFromBytes(("perso:" + nom).getBytes()).toString());
        o.addProperty("nom", nom);
        o.addProperty("rang", rang);
        if (section) o.addProperty("section", true);
        return o;
    }

    static JsonObject vue() {
        return switch (qui) {
            case "aki" -> aki();
            case "daisuke" -> daisuke();
            case "hiroshi" -> hiroshi();
            default -> ren();
        };
    }

    /* ================================================================ vue staff (Poste de garde › Outils › Factions) */

    static JsonObject staff() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "staff");
        o.addProperty("grade", 3);
        JsonArray fs = new JsonArray();
        fs.add(fac("KON", "Konohagakure", "village", "konoha", 42, "Daisuke Senju", "#00031",
                a(gs("kon_anbu", "ANBU", "anbu", true, "Kazuki Uchiha", 4, 6, 1900), gs("kon_police", "Police", "police", false, "Aki Uchiha", 7, 10, 4250),
                        gs("kon_corps_medical", "Corps médical", "medical", false, "Yui Nara", 5, 8, 3100), gs("kon_academie", "Académie", "academie", false, "Nanami Yamanaka", 3, 6, 800),
                        gs("kon_bureau", "Bureau des missions", "missions", false, "Hana Kurenai", 4, 6, 6400), gs("kon_garde", "Garde des portes", "garde", false, "Renji Sarutobi", 6, 8, 1200))));
        fs.add(fac("SUN", "Sunagakure", "village", "suna", 18, null, null, new JsonArray()));
        fs.add(fac("KIR", "Kirigakure", "village", "kiri", 15, "Mizu Hōzuki", "#00012", new JsonArray()));
        fs.add(fac("KUM", "Kumogakure", "village", "kumo", 9, null, null, new JsonArray()));
        fs.add(fac("IWA", "Iwagakure", "village", "iwa", 7, null, null, new JsonArray()));
        fs.add(fac("AME", "Amegakure", "village", "ame", 4, null, null, new JsonArray()));
        fs.add(fac("KRG", "Kurogane", "organisation", "lune", 5, "Gorō Akimichi", "#00057", new JsonArray()));
        o.add("factions", fs);
        return o;
    }

    private static JsonObject fac(String tag, String nom, String type, String emb, int n, String chef, String pid, JsonArray sections) {
        JsonObject o = new JsonObject();
        o.addProperty("tag", tag);
        o.addProperty("nom", nom);
        o.addProperty("type", type);
        o.addProperty("embleme", emb);
        o.addProperty("serveur", "shinobi");
        o.addProperty("n", n);
        o.addProperty("taux", 10);
        if (chef != null) {
            JsonObject c = new JsonObject();
            c.addProperty("id", UUID.nameUUIDFromBytes(chef.getBytes()).toString());
            c.addProperty("nom", chef);
            c.addProperty("pid", pid);
            o.add("chef", c);
        }
        for (var e : sections) {
            JsonObject s = e.getAsJsonObject();
            s.add("grades", a("Chef", "Membre"));
        }
        o.add("sections", sections);
        o.add("charges", "KON".equals(tag) ? a("Conseiller (2)", "Intendant (1)") : new JsonArray());
        o.add("journal", "KON".equals(tag) ? a(ligne("Section « Brigade des archives » créée, chef : Nanami Yamanaka, 6 places", 0, H / 6),
                ligne("Daisuke Senju devient chef de Konohagakure · « nommé par le conseil RP du 15/10 »", 0, J)) : new JsonArray());
        return o;
    }

    /* ================================================================ réponses */

    static void handle(JsonObject o) {
        String a = o.has("a") ? o.get("a").getAsString() : "";
        switch (a) {
            case "open" -> { JsonObject v = vue(); v.addProperty("ouvrir", true); FactionClient.recevoir(v.toString()); }
            case "refresh" -> FactionClient.recevoir(vue().toString());
            case "proches" -> FactionClient.recevoir(prochesAki().toString());
            case "staff" -> FactionClient.recevoir(staff().toString());
            case "close", "staff_close", "etat" -> { }
            case "inviter" -> FactionClient.toast("Invitation envoyée (banc).");
            default -> FactionClient.toast("C'est fait (banc : « " + a + " »).");
        }
    }
}
