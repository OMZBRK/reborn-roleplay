package fr.reborn.hud.parchemin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;


/**
 * Banc d'essai des parchemins, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_PARCHEMIN_DEBUG=1}
 * (client de dev). Bibliothèques v2 : fonds rempli d'un clan (membre), arrivages de l'Académie, réserve du palais vue
 * par un non-membre (cadenas, survol du sceau), dépôt depuis la sacoche (choix puis fonds), journal ; puis la lecture
 * sur place, la lecture d'un rouleau (rang C en cours, rang S en fin de parcours) et le réglage staff (type, vol,
 * réserve de secrets, recherche, simulation) ; puis le vol (fiche « voler », fouille en cours, rouleau volé), le dépôt
 * refusé (bulle explicative) et le journal avec un vol. Capture chaque écran dans run/screenshots/, puis ferme le jeu.
 */
public final class ParcheminDebug {

    private static int ticks = -1;

    private ParcheminDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_PARCHEMIN_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ParcheminDebug::tick);
    }

    public static Technique find(String id) {
        for (Technique t : Technique.DEMO) if (t.id().equals(id)) return t;
        return Technique.DEMO.get(0);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        if (ticks == 30) {
            mc.player.connection.sendCommand("time set 6000");
            mc.player.connection.sendCommand("clear @s");
            mc.gui.hud.getChat().clearMessages(false);
        }
        if (ticks < 40) return;
        switch (ticks - 40) {
            // fonds d'un clan, vu par un membre (fiche : déposé par, lecture sur place)
            case 5 -> ParcheminClient.debugReceive(clanJson().toString());
            case 8 -> with(mc, BibliothequeScreen.class, s -> s.debugHover = 2);
            case 20 -> shot(mc, "bib_1_fonds_clan");
            // arrivages de l'Académie (onglet Arrivages)
            case 22 -> mc.setScreenAndShow(null);
            case 23 -> ParcheminClient.debugReceive(libJson().toString());
            case 25 -> with(mc, BibliothequeScreen.class, s -> { s.setTab(BibliothequeScreen.ARRIVAGES); s.debugHover = s.firstFilled(); });
            case 36 -> shot(mc, "bib_2_arrivages");
            // réserve du palais vue par un non-membre : cadenas, fiche « réservé », puis survol du sceau
            case 38 -> mc.setScreenAndShow(null);
            case 39 -> ParcheminClient.debugReceive(palaisJson().toString());
            case 41 -> with(mc, BibliothequeScreen.class, s -> s.debugHover = 4);
            case 52 -> shot(mc, "bib_3_non_membre");
            case 54 -> with(mc, BibliothequeScreen.class, s -> { s.debugHover = -1; s.debugSeal = true; });
            case 64 -> shot(mc, "bib_4_sceau_membres");
            // bibliothèque personnelle : dépôt depuis la sacoche
            case 66 -> mc.setScreenAndShow(null);
            case 67 -> ParcheminClient.debugReceive(persoJson(false).toString());
            case 69 -> with(mc, BibliothequeScreen.class, s -> { s.openPicker(5); s.debugPick = 1; });
            case 80 -> shot(mc, "bib_5_depot_choix");
            case 82 -> mc.setScreenAndShow(null);
            case 84 -> ParcheminClient.debugReceive(persoJson(true).toString());
            case 85 -> with(mc, BibliothequeScreen.class, s -> { s.toast("Rouleau déposé : Doton : Doryūheki"); s.debugHover = 5; });
            case 94 -> shot(mc, "bib_6_depot_fait");
            case 96 -> with(mc, BibliothequeScreen.class, s -> { s.debugHover = -1; s.showLog(logJson(false)); });
            case 106 -> shot(mc, "bib_7_journal");
            // lecture sur place, lecture d'un rouleau en main
            case 108 -> readOnsite(mc, "katon_ryuka", 2, true);
            case 128 -> shot(mc, "bib_8_lecture_sur_place");
            case 130 -> read(mc, "katon_gokakyu", 1, "maintenant", true, false);
            case 150 -> shot(mc, "bib_9_lecture_c");
            case 152 -> read(mc, "rasengan", 7, "2 h 10", false, false);
            case 172 -> shot(mc, "bib_10_lecture_s");
            // réglages staff : type au lieu du modèle
            case 174 -> ParcheminClient.debugReceive(cfgJson().toString());
            case 184 -> shot(mc, "bib_11_reglages");
            case 186 -> with(mc, BibliothequeStaffScreen.class, s -> { s.setPage(BibliothequeStaffScreen.PAGE_TECHNIQUES); s.setFilter(3); });
            case 196 -> shot(mc, "bib_12_reglages_techniques");
            // réserve de secrets, recherche, simulation qui en tient compte
            case 198 -> with(mc, BibliothequeStaffScreen.class, s -> s.setPage(BibliothequeStaffScreen.PAGE_SECRETS));
            case 208 -> shot(mc, "bib_13_secrets");
            case 210 -> with(mc, BibliothequeStaffScreen.class, s -> s.setQuery("uchiha"));
            case 220 -> shot(mc, "bib_14_secrets_recherche");
            case 222 -> with(mc, BibliothequeStaffScreen.class, s -> { s.setQuery(""); s.setPage(BibliothequeStaffScreen.PAGE_SIMULATION); });
            case 232 -> shot(mc, "bib_15_reglages_simulation");
            // vol : fiche « voler », fouille en cours, rouleau volé
            case 234 -> mc.setScreenAndShow(null);
            case 235 -> ParcheminClient.debugReceive(palaisVolJson(false).toString());
            case 237 -> with(mc, BibliothequeScreen.class, s -> s.debugHover = 3);
            case 248 -> shot(mc, "bib_16_vol_fiche");
            case 250 -> with(mc, BibliothequeScreen.class, s -> { s.debugHover = -1; s.debugSteal(3, 12_000, 0.62f); });
            case 260 -> shot(mc, "bib_17_vol_en_cours");
            case 262 -> {
                com.google.gson.JsonObject done = new com.google.gson.JsonObject();
                done.addProperty("t", "steal");
                done.addProperty("state", "done");
                ParcheminClient.debugReceive(done.toString());
                ParcheminClient.debugReceive(palaisVolJson(true).toString());
                with(mc, BibliothequeScreen.class, s -> s.toast("Rouleau volé : Omote Renge"));
            }
            case 272 -> shot(mc, "bib_18_vol_reussi");
            // dépôt refusé : bulle explicative au survol du bouton grisé
            case 274 -> with(mc, BibliothequeScreen.class, s -> s.debugDepositTip = true);
            case 284 -> shot(mc, "bib_19_depot_refuse");
            // journal avec un vol
            case 286 -> mc.setScreenAndShow(null);
            case 287 -> ParcheminClient.debugReceive(persoJson(true).toString());
            case 289 -> with(mc, BibliothequeScreen.class, s -> s.showLog(logJson(true)));
            case 299 -> shot(mc, "bib_20_journal_vol");
            case 305 -> mc.stop();
            default -> { }
        }
    }

    private static void read(Minecraft mc, String id, int done, String next, boolean master, boolean known) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "read");
        o.add("tech", tech(find(id)));
        o.addProperty("done", done);
        o.addProperty("nextIn", next);
        o.addProperty("master", master);
        o.addProperty("known", known);
        ParcheminClient.debugReceive(o.toString());
    }

    private static void readOnsite(Minecraft mc, String id, int done, boolean master) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "read");
        o.add("tech", tech(find(id)));
        o.addProperty("done", done);
        o.addProperty("nextIn", "maintenant");
        o.addProperty("master", master);
        o.addProperty("known", false);
        com.google.gson.JsonObject l = new com.google.gson.JsonObject();
        l.addProperty("id", "archives-uchiha");
        l.addProperty("name", "Archives Uchiha");
        o.add("onsite", l);
        ParcheminClient.debugReceive(o.toString());
    }

    /** Champs v2 communs : type, propriétaire, accès, droits du joueur, fonds et sacoche. */
    private static void v2(com.google.gson.JsonObject o, String type, String label, String owner, boolean membres,
                           boolean member, boolean deposit, boolean draws, boolean onsite, boolean journal,
                           String[] fonds, String[] by, String[] bag) {
        o.addProperty("type", type);
        o.addProperty("typeLabel", label);
        o.addProperty("owner", owner);
        o.addProperty("access", membres ? "membres" : "libre");
        o.addProperty("member", member);
        o.addProperty("canTake", !membres || member);
        o.addProperty("canDeposit", deposit);
        o.addProperty("draws", draws);
        o.addProperty("onsite", onsite);
        o.addProperty("journal", journal);
        com.google.gson.JsonArray f = new com.google.gson.JsonArray();
        for (int i = 0; i < 9; i++) {
            String id = i < fonds.length ? fonds[i] : null;
            if (id == null) { f.add(com.google.gson.JsonNull.INSTANCE); continue; }
            com.google.gson.JsonObject t = tech(find(id));
            t.addProperty("by", by[i]);
            f.add(t);
        }
        o.add("fonds", f);
        com.google.gson.JsonArray b = new com.google.gson.JsonArray();
        for (int i = 0; i < bag.length; i++) {
            com.google.gson.JsonObject t = tech(find(bag[i]));
            t.addProperty("ref", "H" + i);
            b.add(t);
        }
        o.add("bag", b);
    }

    private static com.google.gson.JsonObject base(String id, String name, int count, String[] cells) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "lib");
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("count", count);
        o.addProperty("refreshIn", "2 h 41");
        com.google.gson.JsonArray c = new com.google.gson.JsonArray();
        for (int i = 0; i < 9; i++) {
            String t = cells != null && i < cells.length ? cells[i] : null;
            c.add(t == null ? com.google.gson.JsonNull.INSTANCE : tech(find(t)));
        }
        o.add("cells", c);
        o.addProperty("weight", 7.2);
        o.addProperty("maxWeight", 12);
        o.addProperty("staff", false);
        return o;
    }

    /** Archives d'un clan, vues par un membre : fonds rempli, pas d'arrivages, lecture sur place. */
    public static com.google.gson.JsonObject clanJson() {
        com.google.gson.JsonObject o = base("archives-uchiha", "Archives Uchiha", 0, null);
        v2(o, "CLAN", "Clan", "Uchiha", true, true, true, false, true, false,
            new String[]{"katon_hosenka", null, "katon_ryuka", "kawarimi", "katon_gokakyu", null, "bunshin", null, "raiton_chidori"},
            new String[]{"Fugaku Uchiha", null, "Fugaku Uchiha", "Mikoto Uchiha", "Shisui Uchiha", null, "Mikoto Uchiha", null, "Shisui Uchiha"},
            new String[]{"doton_doryuheki"});
        return o;
    }

    /** Réserve du palais vue par un non-membre : il voit, ne peut pas prendre. */
    public static com.google.gson.JsonObject palaisJson() {
        com.google.gson.JsonObject o = base("reserve-palais", "Reserve du palais", 6,
            new String[]{null, "suiton_suiryudan", null, "kiri_sakura", null, null});
        v2(o, "PALAIS", "Palais du Kage", "Konoha", true, false, false, true, false, false,
            new String[]{"doton_yomi", "mikazuki", null, "omote_renge", "ura_renge", "suiton_suiryudan"},
            new String[]{"Hiruzen Sarutobi", "Hiruzen Sarutobi", null, "Danzo Shimura", "Hiruzen Sarutobi", "Danzo Shimura"},
            new String[]{});
        return o;
    }

    /**
     * Réserve du palais vue par un non-membre, vol permis : fiche « voler · 12 s », durées par rang, raison du dépôt
     * refusé ; {@code apres} = le rouleau B (case 3) a été volé.
     */
    public static com.google.gson.JsonObject palaisVolJson(boolean apres) {
        com.google.gson.JsonObject o = base("reserve-palais", "Reserve du palais", 6,
            new String[]{null, "suiton_suiryudan", null, "kiri_sakura", null, null});
        v2(o, "PALAIS", "Palais du Kage", "Konoha", true, false, false, true, false, false,
            new String[]{"doton_yomi", "mikazuki", null, apres ? null : "omote_renge", "ura_renge", "suiton_suiryudan"},
            new String[]{"Hiruzen Sarutobi", "Hiruzen Sarutobi", null, apres ? null : "Danzo Shimura", "Hiruzen Sarutobi", "Danzo Shimura"},
            new String[]{});
        o.addProperty("canSteal", true);
        com.google.gson.JsonArray secs = new com.google.gson.JsonArray();
        for (double d : new double[]{6, 9, 12, 18, 24}) secs.add(d);
        o.add("stealSecs", secs);
        o.addProperty("depositWhy", "Seuls le Kage et son conseil déposent ici.");
        if (apres) o.addProperty("weight", 7.4);
        return o;
    }

    /** Bibliothèque personnelle, vue par son propriétaire : dépôt et journal. */
    public static com.google.gson.JsonObject persoJson(boolean apres) {
        com.google.gson.JsonObject o = base("bibliotheque-de-itachi", "Bibliotheque de Itachi", 0, null);
        String[] fonds = {"kawarimi", "katon_gokakyu", "iai_giri", null, "futon_daitoppa", apres ? "doton_doryuheki" : null};
        String[] by = {"Itachi Uchiha", "Itachi Uchiha", "Itachi Uchiha", null, "Itachi Uchiha", apres ? "Itachi Uchiha" : null};
        v2(o, "PERSONNELLE", "Personnelle", "Itachi Uchiha", true, true, true, false, false, true, fonds, by,
            apres ? new String[]{"katon_hosenka", "shishi_rendan"} : new String[]{"katon_hosenka", "doton_doryuheki", "shishi_rendan"});
        return o;
    }

    /** Journal d'une bibliothèque ({@code lib_log}). */
    public static com.google.gson.JsonObject logJson(boolean vol) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "lib_log");
        o.addProperty("id", "bibliotheque-de-itachi");
        o.addProperty("name", "Bibliothèque de Itachi");
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        String[][] rows = vol ? new String[][]{
            {"13/10 16:02", "Kazuki Uchiha", "vol", "fonds", "Fūton : Daitoppa", "C"},
            {"13/10 15:42", "Itachi Uchiha", "depot", "fonds", "Doton : Doryūheki", "C"},
            {"13/10 11:05", "Shisui Uchiha", "prise", "fonds", "Bunshin", "D"},
            {"12/10 21:17", "Itachi Uchiha", "depot", "fonds", "Fūton : Daitoppa", "C"},
            {"12/10 21:16", "Itachi Uchiha", "depot", "fonds", "Iai-giri", "D"},
            {"11/10 18:30", "Shisui Uchiha", "depot", "fonds", "Bunshin", "D"},
        } : new String[][]{
            {"13/10 15:42", "Itachi Uchiha", "depot", "fonds", "Doton : Doryūheki", "C"},
            {"13/10 11:05", "Shisui Uchiha", "prise", "fonds", "Bunshin", "D"},
            {"12/10 21:17", "Itachi Uchiha", "depot", "fonds", "Fūton : Daitoppa", "C"},
            {"12/10 21:16", "Itachi Uchiha", "depot", "fonds", "Iai-giri", "D"},
            {"11/10 18:30", "Shisui Uchiha", "depot", "fonds", "Bunshin", "D"},
            {"10/10 09:12", "Itachi Uchiha", "depot", "fonds", "Katon : Gōkakyū", "C"},
            {"10/10 09:11", "Itachi Uchiha", "depot", "fonds", "Kawarimi", "D"},
        };
        for (String[] r : rows) {
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("when", r[0]);
            j.addProperty("who", r[1]);
            j.addProperty("act", r[2]);
            j.addProperty("shelf", r[3]);
            j.addProperty("tech", r[4]);
            j.addProperty("rank", r[5]);
            a.add(j);
        }
        o.add("entries", a);
        return o;
    }

    public static com.google.gson.JsonObject tech(Technique t) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("id", t.id());
        o.addProperty("name", t.name());
        o.addProperty("branch", t.branch());
        o.addProperty("nature", t.nature());
        o.addProperty("rank", String.valueOf(t.rank()));
        o.addProperty("desc", t.desc());
        com.google.gson.JsonArray sg = new com.google.gson.JsonArray();
        t.signs().forEach(sg::add);
        o.add("signs", sg);
        return o;
    }

    /** Ce qu'envoie ShinobiAbilities à l'ouverture d'une bibliothèque. */
    public static com.google.gson.JsonObject libJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "lib");
        o.addProperty("id", "academie");
        o.addProperty("name", "Bibliothèque de l'Académie");
        o.addProperty("count", 6);
        o.addProperty("refreshIn", "2 h 41");
        com.google.gson.JsonArray cells = new com.google.gson.JsonArray();
        String[] ids = {"kawarimi", null, "katon_gokakyu", "konoha_senpu", "raiton_chidori", "tsubame_gaeshi", null, null, null};
        for (String id : ids) cells.add(id == null ? com.google.gson.JsonNull.INSTANCE : tech(find(id)));
        o.add("cells", cells);
        o.addProperty("weight", 7.2);
        o.addProperty("maxWeight", 12);
        o.addProperty("staff", true);
        v2(o, "ACADEMIE", "Académie", "Konoha", true, true, true, true, true, true,
            new String[]{"kesa_giri", null, null, "konoha_reppu"},
            new String[]{"Iruka Umino", null, null, "Iruka Umino"}, new String[]{"katon_hosenka"});
        return o;
    }

    /** Ce qu'envoie ShinobiAbilities pour /bibliotheque regler. */
    public static com.google.gson.JsonObject cfgJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("t", "lib_cfg");
        o.addProperty("id", "reserve-palais");
        o.addProperty("name", "Réserve du palais");
        o.addProperty("count", 6);
        o.addProperty("delayH", 3);
        o.addProperty("model", 2);
        o.addProperty("type", "PALAIS");
        o.addProperty("owner", "Konoha");
        o.addProperty("access", "membres");
        o.addProperty("draws", true);
        o.addProperty("fondsCount", 5);
        com.google.gson.JsonArray r = new com.google.gson.JsonArray();
        for (double d : Tirage.PRIVEE) r.add(d);
        o.add("byRank", r);
        com.google.gson.JsonObject ov = new com.google.gson.JsonObject();
        ov.addProperty("raiton_chidori", 1.0);
        o.add("overrides", ov);
        com.google.gson.JsonArray techs = new com.google.gson.JsonArray();
        for (Technique t : Technique.DEMO) {
            if (t.rank() == 'S') continue;                     // le rang S est secret : hors du tirage commun
            techs.add(tech(t));
        }
        o.add("techs", techs);
        // ─── vol ───
        o.addProperty("theft", true);
        o.addProperty("theftSecs", 6);
        o.addProperty("theftNamed", false);
        // ─── secrets : réserve par défaut du palais (A et S) + une technique ajoutée à la main ───
        o.addProperty("commons", true);
        o.addProperty("reserveCustom", true);
        com.google.gson.JsonObject def = new com.google.gson.JsonObject(), res = new com.google.gson.JsonObject();
        com.google.gson.JsonArray st = new com.google.gson.JsonArray();
        for (Technique t : Technique.DEMO) {
            com.google.gson.JsonObject j = light(t.id(), t.name(), t.branch(), t.nature(), t.rank(), t.rank() == 'S', null, false);
            if (t.rank() == 'S') j.addProperty("slots", t.id().equals("rasengan") ? "2/2" : "1/2");
            st.add(j);
            if (t.rank() == 'A' || t.rank() == 'S') {
                def.addProperty(t.id(), t.rank() == 'S' ? 0.5 : 3.0);
                res.addProperty(t.id(), t.rank() == 'S' ? 0.5 : 3.0);
            }
        }
        String[][] extra = {
            {"magen_kasegui", "Magen : Kasegui no Jutsu", "Dōjutsu", "B", "Uchiha", ""},
            {"sharingan_izanagi", "Izanagi", "Dōjutsu", "S", "Uchiha", ""},
            {"sharingan_amaterasu", "Amaterasu", "Dōjutsu", "S", "Uchiha", ""},
            {"juken_hakke_64", "Hakke Rokujūyon Shō", "Jūken", "A", "Hyuga", ""},
            {"juken_kaiten", "Hakkeshō Kaiten", "Jūken", "B", "Hyuga", ""},
            {"mokuton_jukai", "Mokuton : Jukai Kōtan", "Kekkei genkai", "A", "Senju", ""},
            {"kinjutsu_shiki_fujin", "Shiki Fūjin", "Kinjutsu", "S", "", "anbu"},
            {"kinjutsu_edo_tensei", "Edo Tensei", "Kinjutsu", "S", "", "anbu"},
            {"juinjutsu_zekka", "Zekka Nenkon no Juin", "Juinjutsu", "A", "", "anbu"},
        };
        for (String[] e : extra) {
            st.add(light(e[0], e[1], e[2], "", e[3].charAt(0), true, e[4].isEmpty() ? null : e[4], !e[5].isEmpty()));
        }
        res.addProperty("kinjutsu_shiki_fujin", 0.3);
        o.add("reserve", res);
        o.add("reserveDefault", def);
        com.google.gson.JsonArray sp = new com.google.gson.JsonArray();
        for (double d : new double[]{30, 20, 10, 3, 0.5}) sp.add(d);
        o.add("secretPct", sp);
        o.add("secretTechs", st);
        return o;
    }

    private static com.google.gson.JsonObject light(String id, String name, String branch, String nature, char rank,
                                                    boolean secret, String clan, boolean anbu) {
        com.google.gson.JsonObject j = new com.google.gson.JsonObject();
        j.addProperty("id", id);
        j.addProperty("name", name);
        j.addProperty("branch", branch);
        j.addProperty("nature", nature);
        j.addProperty("rank", String.valueOf(rank));
        j.addProperty("secret", secret);
        if (clan != null) j.addProperty("clan", clan);
        if (anbu) j.addProperty("anbu", true);
        return j;
    }

    private interface Act<T> { void run(T s); }

    private static <T> void with(Minecraft mc, Class<T> c, Act<T> a) {
        if (c.isInstance(mc.gui.screen())) a.run(c.cast(mc.gui.screen()));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
    }
}
