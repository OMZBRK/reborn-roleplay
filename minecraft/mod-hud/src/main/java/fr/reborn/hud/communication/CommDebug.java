package fr.reborn.hud.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Banc de captures de la cloche et des volières (maquettes v3c 01 à 06), <b>inactif en production</b> : ne fait rien
 * sauf si {@code REBORN_CLOCHE_DEBUG} ou {@code REBORN_VOLIERE_DEBUG} est posé (client de dev, monde solo). Le serveur est
 * simulé : les messages {@code reborn:communication} arrivent tels que ShinobiCore les envoie (données des maquettes).
 * {@code =1} : tous les cas du banc en 1920×1080 à l'échelle GUI 2 ; {@code =<cas>,<cas>} : ces cas. Captures
 * {@code run/screenshots/cloche_<nn>_<cas>.png} / {@code voliere_<nn>_<cas>.png}, puis le jeu se ferme.
 */
public final class CommDebug {

    static final String[] CLOCHE = {"rediger", "bande_normale", "bande_importante", "bande_danger", "bande_fondu", "chef_section",
            "menu_r", "echelle3", "petit", "sans_delai"};
    static final String[] VOLIERE = {"ecrire", "ajouter", "missives", "lecture", "alerte_missive", "alerte_palais", "transfert",
            "menu_r", "vide"};

    private record Shot(String nom, String cas, int fw, int fh, int echelle) { }

    private static final int STEP = 40;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int t = -1, echelleAvant = -1;
    private static boolean pret;

    static final UUID KENTA = id("kenta"), AYA = id("aya"), YUI = id("yui"), YUTO = id("yuto"), MEI = id("mei");

    private static UUID id(String n) { return UUID.nameUUIDFromBytes(("comm-demo:" + n).getBytes()); }

    private CommDebug() {}

    static void init() {
        ajouter("REBORN_CLOCHE_DEBUG", "cloche", CLOCHE);
        ajouter("REBORN_VOLIERE_DEBUG", "voliere", VOLIERE);
        if (!SHOTS.isEmpty()) ClientTickEvents.END_CLIENT_TICK.register(CommDebug::tick);
    }

    private static void ajouter(String env, String prefixe, String[] cas) {
        String v = System.getenv(env);
        if (v == null || v.isBlank()) return;
        List<String> only = "1".equals(v.trim()) ? List.of() : List.of(v.trim().split(","));
        for (int i = 0; i < cas.length; i++) {
            String c = cas[i];
            if (!only.isEmpty() && !only.contains(c)) continue;
            int fw = 1920, fh = 1080, e = 2;
            if (c.equals("echelle3")) e = 3;
            if (c.equals("petit")) { fw = 1366; fh = 768; }
            SHOTS.add(new Shot(String.format("%s_%02d_%s", prefixe, i + 1, c), prefixe + ":" + c, fw, fh, e));
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        if (t == 20) {
            mc.player.connection.sendCommand("time set 1000");
            mc.player.connection.sendCommand("weather clear");
            mc.gui.hud.getChat().clearMessages(false);
            echelleAvant = mc.options.guiScale().get();
            CommClient.demo = true;
            skins(mc);
            pret = true;
        }
        if (t < 160 || !pret) return;
        int i = (t - 160) / STEP, k = (t - 160) % STEP;
        if (i >= SHOTS.size()) {
            if (k == 2) {
                mc.setScreenAndShow(null);
                InteractionMode.INSTANCE.debugClose();
                CommClient.demo = false;
                CommClient.effacer();
                fr.reborn.hud.runtime.RebornSession.debugForceRp = false;
                if (echelleAvant >= 0) { mc.options.guiScale().set(echelleAvant); mc.resizeGui(); }
            }
            if (k == 6) mc.stop();
            return;
        }
        Shot s = SHOTS.get(i);
        if (k == 0) {
            mc.setScreenAndShow(null);
            InteractionMode.INSTANCE.debugClose();
            CommClient.effacer();
            CommClient.annonce.effacer();
            CommClient.lettre.effacer();
            CommClient.dests.clear();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (mc.getWindow().getScreenWidth() != s.fw() || mc.getWindow().getScreenHeight() != s.fh()) mc.getWindow().setWindowed(s.fw(), s.fh());
            mc.options.guiScale().set(s.echelle());
            mc.resizeGui();
        }
        if (k == 4) { mc.gui.hud.getChat().clearMessages(false); setup(mc, s.cas()); }
        if (k == 12) suite(mc, s.cas());
        if (k == STEP - 3) Screenshot.grab(mc.gameDirectory, s.nom() + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }

    /* ================================================================ données des maquettes */

    static void recevoir(JsonObject o) { CommClient.recevoir(o.toString()); }

    private static JsonArray segs(String... ts) {
        JsonArray a = new JsonArray();
        for (int i = 0; i + 1 < ts.length; i += 2) { JsonArray x = new JsonArray(); x.add(ts[i]); x.add(ts[i + 1]); a.add(x); }
        return a;
    }

    private static JsonObject sec(String id, String nom, String ins, boolean nouvelle) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nom", nom);
        o.addProperty("insigne", ins);
        if (nouvelle) o.addProperty("nouvelle", true);
        return o;
    }

    /** Cas « sans délai » : un Commandant Jōnin (seuil « Sonner sans délai » du village) juste après une annonce. */
    static JsonObject clocheSansDelai() {
        JsonObject o = cloche(false);
        o.addProperty("nom", "Hiroshi Hatake");
        o.addProperty("titre", "Commandant Jōnin");
        o.addProperty("libreDepuis", 25);
        o.addProperty("sansDelai", "grade");
        return o;
    }

    static JsonObject cloche(boolean chef) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "cloche");
        o.addProperty("ouvrir", true);
        o.addProperty("id", "kon_cloche_1");
        o.addProperty("lieu", "Place centrale de Konoha");
        o.addProperty("nom", chef ? "Aki Uchiha" : "Daisuke Senju");
        o.addProperty("titre", chef ? "Commissaire de la Police" : "Hokage");
        o.addProperty("village", !chef);
        o.addProperty("max", 200);
        JsonArray s = new JsonArray();
        if (chef) s.add(sec("kon_police", "Police", "police", false));
        else {
            s.add(sec("kon_police", "Police", "police", false));
            s.add(sec("kon_garde", "Garde des portes", "garde", false));
            s.add(sec("kon_brigade_port", "Brigade du port", "lune", true));
            s.add(sec("kon_medical", "Corps médical", "medical", false));
            s.add(sec("kon_academie", "Académie", "academie", false));
            s.add(sec("kon_missions", "Bureau des missions", "missions", false));
            s.add(sec("kon_archives", "Brigade des archives", "missions", false));
            s.add(sec("kon_sentinelles", "Corps des sentinelles", "garde", false));
            s.add(sec("kon_palais", "Gardes du palais", "conseil", false));
            s.add(sec("kon_decryptage", "Unité de décryptage", "bandeau", false));
        }
        o.add("sections", s);
        if (chef) o.addProperty("fixe", "kon_police");
        JsonArray g = new JsonArray();
        for (String x : new String[]{"Académicien", "Genin", "Genin confirmé", "Chūnin", "Konin", "Tokubetsu Jōnin", "Jōnin",
                "Commandant Jōnin", "Kage"}) g.add(x);
        o.add("grades", g);
        o.addProperty("libreDepuis", 360);
        o.addProperty("attenteCloche", 0);
        o.addProperty("attenteMoi", 0);
        o.addProperty("delaiMoi", 600);
        o.addProperty("dangers", 3);
        o.addProperty("zone", 38);
        o.addProperty("concernes", chef ? 6 : 13);
        return o;
    }

    private static JsonObject contact(String ref, String nom, String sous, UUID mc) {
        JsonObject c = new JsonObject();
        c.addProperty("ref", ref);
        c.addProperty("nom", nom);
        c.addProperty("sous", sous);
        if (mc != null) c.addProperty("mc", mc.toString());
        return c;
    }

    private static JsonObject dest(String id, String nom, String lieu, int delai, int cout, String cond) {
        JsonObject d = new JsonObject();
        d.addProperty("id", id);
        d.addProperty("nom", nom);
        d.addProperty("lieu", lieu);
        d.addProperty("delai", delai);
        d.addProperty("cout", cout);
        if (cond != null) d.addProperty("cond", cond);
        return d;
    }

    private static JsonObject missive(String id, String pour, String[] autres, String signe, String de, long ilYa, boolean scellee, boolean toi) {
        JsonObject m = new JsonObject();
        m.addProperty("id", id);
        m.addProperty("pour", pour);
        JsonArray a = new JsonArray();
        for (String x : autres) a.add(x);
        m.add("autres", a);
        if (toi) m.addProperty("toi", true);
        if (signe != null) m.addProperty("signe", signe);
        m.addProperty("de", de);
        m.addProperty("le", System.currentTimeMillis() - ilYa);
        m.addProperty("scellee", scellee);
        return m;
    }

    static JsonObject voliere(int onglet, boolean enMain, boolean vide) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "voliere");
        o.addProperty("ouvrir", true);
        o.addProperty("onglet", onglet);
        o.addProperty("id", "kon_grande_1");
        o.addProperty("nom", "Volière de Konoha");
        o.addProperty("type", "Grande volière");
        o.addProperty("moi", onglet == 1 ? "Mei Kazane" : "Aki Uchiha");
        o.addProperty("now", System.currentTimeMillis());
        o.addProperty("bourse", 1240);
        o.addProperty("max", 600);
        o.addProperty("adresseMax", 40);
        o.addProperty("destMax", 10);
        o.addProperty("garde", 7);
        o.addProperty("capacite", 40);
        JsonArray c = new JsonArray();
        c.add(contact("c:" + id("p-kenta"), "Kenta Inuzuka", "Genin confirmé · connu", KENTA));
        c.add(contact("c:" + id("p-aya"), "Aya Inuzuka", "Chūnin · connue", AYA));
        c.add(contact("c:" + id("p-yui"), "Yui Nara", "Jōnin · connue", YUI));
        c.add(contact("c:" + id("p-yuto"), "Yūto Akimichi", "connu", YUTO));
        c.add(contact("c:" + id("p-mei"), "Mei Kazane", "Genin · connue", MEI));
        o.add("contacts", c);
        JsonArray d = new JsonArray();
        d.add(dest("kon_grande_1", "Ici, grande volière de Konoha", "Grande volière de Konoha", 60, 10, null));
        d.add(dest("kon_petite_2", "Petite volière du quartier est", "Petite volière du quartier est", 180, 10, null));
        d.add(dest("sun_tour_1", "Tour de volière de Suna", "Tour de volière de Suna", 1080, 40, null));
        d.add(dest("kon_palais_1", "Volière du palais du Hokage", "Volière du palais du Hokage", 60, 10, "Chūnin et plus"));
        o.add("destinations", d);
        JsonArray m = new JsonArray();
        if (!vide) {
            m.add(missive("M7K2QF", "Mei Kazane", new String[]{"Aya Inuzuka", "Yui Nara"}, "Kenta Inuzuka", "tour de Suna", 12 * 60_000L, true, true));
            m.add(missive("M7K2QG", "Aya Inuzuka", new String[]{"Mei Kazane", "Yui Nara"}, "Kenta Inuzuka", "tour de Suna", 12 * 60_000L, true, false));
            m.add(missive("M3PZ8A", "Au chef de la Police", new String[0], null, "petite volière est", 26 * 3_600_000L, false, false));
            m.add(missive("M9HH4R", "Hana Kurenai", new String[0], "Yui Nara", "tour de Kiri", 30 * 3_600_000L, true, false));
            m.add(missive("M2WQ6T", "Yui Nara", new String[0], null, "grande volière", 3 * 86_400_000L + 3_600_000L, false, false));
        }
        o.add("missives", m);
        o.addProperty("peutPrendre", true);
        o.addProperty("seuilPrendre", "Genin");
        if (enMain) {
            JsonObject x = new JsonObject();
            x.addProperty("id", "M3PZ8A");
            x.addProperty("pour", "Au chef de la Police");
            x.addProperty("scellee", false);
            o.add("enMain", x);
        }
        return o;
    }

    static JsonObject lettre() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "lettre");
        o.addProperty("id", "M7K2QF");
        JsonArray a = new JsonArray();
        a.add("Kenta Inuzuka");
        a.add("Aya Inuzuka");
        o.add("a", a);
        o.addProperty("lieu", "tour de Suna");
        o.addProperty("heure", "21 h 18");
        o.add("texte", segs("Kenta, Aya,\nLe convoi de Suna passera par la porte est après-demain, à la nuit. Préviens les tiens, sans bruit. "
                + "Je serai au relais du pont.\n", "", "Brûle ceci après lecture.", "i"));
        o.addProperty("signe", "Aki Uchiha");
        o.addProperty("dejaBrise", true);
        return o;
    }

    /* ================================================================ réponses simulées */

    static void demande(JsonObject q) {
        String a = CommClient.str(q, "a");
        Minecraft mc = Minecraft.getInstance();
        switch (a) {
            case "compter" -> {
                int n = 0;
                for (var e : CommClient.arr(q, "sections")) n += 4;
                JsonObject o = new JsonObject();
                o.addProperty("t", "compte");
                o.addProperty("zone", 38);
                o.addProperty("concernes", CommClient.bool(q, "tout") ? 38 : Math.min(38, n + 1));
                recevoir(o);
            }
            case "sonner" -> {
                JsonObject f = new JsonObject();
                f.addProperty("t", "ferme");
                recevoir(f);
                JsonObject an = new JsonObject();
                an.addProperty("t", "annonce");
                an.addProperty("type", CommClient.str(q, "type"));
                an.addProperty("titre", "ANNONCE DU VILLAGE");
                an.add("texte", q.get("texte"));
                an.addProperty("signe", "Daisuke Senju, Hokage");
                an.addProperty("s", 10);
                recevoir(an);
            }
            case "ouvrir" -> recevoir(CommClient.str(q, "id").contains("cloche") ? cloche(false) : voliere(CommClient.bool(q, "missives") ? 1 : 0, false, false));
            default -> CommClient.toast("Banc d'essai : « " + a + " » simulé.", false);
        }
        if (mc.player == null) return;
    }

    /* ================================================================ cas */

    private static void setup(Minecraft mc, String cas) {
        switch (cas) {
            case "cloche:rediger", "cloche:echelle3", "cloche:petit" -> {
                CommClient.annonce.poser(List.of(new CommArt.Seg("Réunion ce soir sur la place centrale, à 20 h. ", ""),
                        new CommArt.Seg("Tous les Chūnin et plus", "g"), new CommArt.Seg(" sont attendus, Brigade du port comprise.", "")));
                CommClient.annonceType = "normale";
                recevoir(cloche(false));
                if (mc.gui.screen() instanceof ClocheScreen c) {
                    c.debugCocher("kon_police", "kon_garde", "kon_brigade_port");
                    c.debugGrades("Chūnin", "Konin", "Tokubetsu Jōnin", "Jōnin", "Commandant Jōnin", "Kage");
                }
            }
            case "cloche:sans_delai" -> {
                CommClient.annonce.poser(List.of(new CommArt.Seg("Deuxième appel : ", ""),
                        new CommArt.Seg("toutes les équipes de garde", "g"), new CommArt.Seg(" se rassemblent à la porte nord, maintenant.", "")));
                CommClient.annonceType = "danger";
                recevoir(clocheSansDelai());
                if (mc.gui.screen() instanceof ClocheScreen c) {
                    c.debugCocher("kon_garde", "kon_sentinelles");
                    c.debugGrades("Genin confirmé", "Chūnin", "Konin", "Tokubetsu Jōnin", "Jōnin", "Commandant Jōnin", "Kage");
                }
            }
            case "cloche:chef_section" -> {
                CommClient.annonce.poser(List.of(new CommArt.Seg("Patrouille de nuit renforcée à la porte nord : ", ""),
                        new CommArt.Seg("tous au poste à 22 h", "g"), new CommArt.Seg(".", "")));
                CommClient.annonceType = "importante";
                recevoir(cloche(true));
            }
            case "cloche:bande_normale" -> annonce("normale", "Daisuke Senju, Hokage", 3800, "Réunion ce soir sur la place centrale, à 20 h. ", "",
                    "Tous les Chūnin et plus", "g", " sont attendus.", "");
            case "cloche:bande_importante" -> annonce("importante", "Hana Kurenai, Conseillère", 7800,
                    "Le marché est fermé demain : convoi de Suna attendu à la porte est.", "");
            case "cloche:bande_danger" -> annonce("danger", "Aki Uchiha, Commissaire de la Police", 3000, "Intrus repérés près de la porte nord. ", "",
                    "Restez chez vous", "g", ", la Police arrive.", "");
            case "cloche:bande_fondu" -> {
                annonce("normale", "Daisuke Senju, Hokage", 8_550, "Réunion ce soir sur la place centrale, à 20 h. ", "",
                        "Tous les Chūnin et plus", "g", " sont attendus.", "");
                BandeHud.alerte("missive", "tour de volière de Suna", 8);
            }
            case "cloche:menu_r", "voliere:menu_r" -> {
                fr.reborn.hud.runtime.RebornSession.debugForceRp = true;
                BlockPos p = mc.player.blockPosition().relative(mc.player.getDirection(), 3);
                boolean cl = cas.startsWith("cloche");
                CommClient.debugPoints(List.of(new CommClient.Point(cl ? "cloche" : "voliere", cl ? "kon_cloche_1" : "kon_grande_1",
                        p.getX(), p.getY(), p.getZ(), cl ? "Sonner la cloche" : "Volière de Konoha")));
                InteractionMode.INSTANCE.debugOpen(CommClient.titreR(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), InteractionMenus.forBlock(p), 0);
            }
            case "voliere:ecrire", "voliere:ajouter" -> {
                brouillon();
                recevoir(voliere(0, false, false));
            }
            case "voliere:missives" -> recevoir(voliere(1, false, false));
            case "voliere:vide" -> recevoir(voliere(1, false, true));
            case "voliere:transfert" -> recevoir(voliere(1, true, false));
            case "voliere:lecture" -> recevoir(lettre());
            case "voliere:alerte_missive" -> BandeHud.alerte("missive", "tour de volière de Suna", 8);
            case "voliere:alerte_palais" -> {
                BandeHud.alerte("missive", "tour de volière de Suna", 8);
                BandeHud.alerte("palais", "", 8);
            }
            default -> { }
        }
    }

    private static void annonce(String type, String signe, long age, String... ts) {
        List<CommArt.Seg> l = new ArrayList<>();
        for (int i = 0; i + 1 < ts.length; i += 2) l.add(new CommArt.Seg(ts[i], ts[i + 1]));
        int s = type.equals("danger") ? 15 : type.equals("importante") ? 12 : 10;
        BandeHud.debug("annonce", type, "ANNONCE DU VILLAGE", l, "— Signé : " + signe, age, s * 1000L);
    }

    private static void brouillon() {
        CommClient.lettre.poser(List.of(new CommArt.Seg("Kenta, Aya,\nLe convoi de Suna passera par la porte est après-demain, à la nuit. "
                + "Préviens les tiens, sans bruit. Je serai au relais du pont.\n", ""), new CommArt.Seg("Brûle ceci après lecture.", "i")));
        CommClient.dests.clear();
        CommClient.dests.add(new CommClient.Dest("c:" + id("p-kenta"), "Kenta Inuzuka"));
        CommClient.dests.add(new CommClient.Dest("c:" + id("p-aya"), "Aya Inuzuka"));
        CommClient.vers = "sun_tour_1";
        CommClient.signee = true;
    }

    private static void suite(Minecraft mc, String cas) {
        switch (cas) {
            case "voliere:ajouter" -> { if (mc.gui.screen() instanceof VoliereScreen v) v.debugAjout("Yu"); }
            case "voliere:transfert" -> {
                if (mc.gui.screen() instanceof VoliereScreen v) {
                    CommClient.dests.add(new CommClient.Dest("", "Au chef de la Police"));
                    v.debugTransfert();
                    CommClient.dests.clear();
                    CommClient.dests.add(new CommClient.Dest("c:" + id("p-yui"), "Yui Nara"));
                    CommClient.vers = "kon_palais_1";
                }
            }
            case "voliere:missives" -> {
                if (mc.gui.screen() instanceof VoliereScreen v) {
                    int x0 = (v.width - 410) / 2, y0 = Math.max(4, (v.height - 352) / 2 - 7);
                    v.debugSouris = new int[]{x0 + 200, y0 + 124 + 14};
                }
            }
            case "cloche:rediger", "cloche:echelle3", "cloche:petit" -> { }
            default -> { }
        }
    }

    /* ---------------------------------------------------------------- têtes RP */

    private static void skins(Minecraft mc) {
        Object[][] l = {
                {KENTA, false, 7, "Cheveux_Base", 0xFF6A3A22, "tenueh3"},
                {AYA, true, 3, "Cheveux_Femme", 0xFF5A3A2A, "Complet_Sakura"},
                {YUI, true, 1, "Cheveux_Femme", 0xFF2A2018, "Complet_Tsunade"},
                {YUTO, false, 5, "Cheveux_Base", 0xFF3A2A1A, "tenueh9"},
                {MEI, true, 2, "Cheveux_Femme", 0xFF2A2440, "Complet_Femme1"}};
        for (Object[] o : l) {
            try { RebornSkins.applySpec((UUID) o[0], look((boolean) o[1], (int) o[2], (String) o[3], (int) o[4], (String) o[5])); }
            catch (RuntimeException ignored) { }
        }
    }

    private static SkinSpec look(boolean female, int skin, String hair, int hairColor, String outfit) {
        SkinSpec l = new SkinSpec();
        l.female = female;
        l.slim = female;
        l.skinColor = SkinSpec.skinRamp(skin / 11f);
        l.hairId = hair;
        l.hairColor = hairColor;
        l.eyeColor = l.eyeColorRight = 0xFF2A2420;
        l.outfitId = outfit;
        return l;
    }
}
