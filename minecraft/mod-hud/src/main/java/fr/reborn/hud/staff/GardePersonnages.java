package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Fiche d'un joueur › section <b>Personnages</b> du Poste de garde (15/10) : tous les personnages du compte (en ligne
 * ou hors ligne), une ligne chacun (N°, nom, clan · village · grade · âge, joué / mort RP), les emplacements du compte
 * et « Créer un personnage… ». Clic sur une ligne = le détail <b>dans le volet</b> : identité, fiche shinobi (stats),
 * puis des mots d'action groupés (Modifier · Progression et jeu · Décisions lourdes), grisés avec un cadenas quand le
 * grade ne suffit pas. Chaque action ouvre un formulaire dans le volet ({@link GardeFormulaire}) ; les décisions
 * lourdes exigent un motif, la remise à zéro et la suppression le N° retapé.
 *
 * <p>Remplace l'éditeur en coffre ({@code /character edit}), le sélecteur de clan, {@code /character create},
 * {@code /persoreset} et {@code /persosuppr}. Le serveur revérifie tout et journalise ({@code PersonnagesPanel},
 * protocole : PLUGINS_REFERENCE §4 bis.2).
 */
final class GardePersonnages {

    private final StaffScreen s;
    /** Personnage dont le détail est ouvert (null = la liste). */
    String detail;
    private String demande;

    GardePersonnages(StaffScreen s) { this.s = s; }

    void ouvrir() { detail = null; demande = null; }

    boolean detailOuvert() { return detail != null; }

    /** Échap dans le détail : retour à la liste. */
    boolean echap() {
        if (detail == null) return false;
        detail = null;
        s.scroll = 0;
        RebornSounds.uiClick();
        return true;
    }

    /* ================================================================ données */

    private static JsonObject data(JsonObject p) { return StaffClient.persos(StaffScreen.str(p, "uuid")); }

    void demander(String uuid, boolean force) {
        if (uuid == null || uuid.isEmpty() || !force && uuid.equals(demande)) return;
        demande = uuid;
        JsonObject o = new JsonObject();
        o.addProperty("a", "persos");
        o.addProperty("t", uuid);
        StaffClient.send(o);
    }

    private static JsonObject trouver(JsonObject d, String id) {
        for (JsonElement e : StaffScreen.arr(d, "persos")) if (id.equals(StaffScreen.str(e.getAsJsonObject(), "id"))) return e.getAsJsonObject();
        return null;
    }

    /** Raison du verrou de la famille {@code droit} pour le spectateur (null = permis). */
    private static String verrou(JsonObject d, String droit) {
        int min = StaffScreen.num(StaffScreen.obj(d, "droits"), droit);
        if (min <= 0) min = 3;
        return StaffScreen.num(d, "grade") >= min ? null : "Réservé au grade " + StaffScreen.gradeName(min) + " et plus.";
    }

    static String nombre(long v) { return String.format("%,d", v).replace(',', ' ').replace(' ', ' '); }

    /** « Uchiha · Konoha · Genin · 14 ans » */
    private static String resume(JsonObject c, boolean age) {
        List<String> l = new ArrayList<>();
        if (!StaffScreen.str(c, "clan").isEmpty()) l.add(StaffScreen.str(c, "clan"));
        if (!StaffScreen.str(c, "villageCourt").isEmpty()) l.add(StaffScreen.str(c, "villageCourt"));
        l.add(StaffScreen.str(c, "rang"));
        if (age) l.add(StaffScreen.num(c, "age") + " ans");
        return String.join(" · ", l);
    }

    private static String slots(JsonObject d) {
        JsonObject sl = StaffScreen.obj(d, "slots");
        int used = StaffScreen.num(sl, "used"), total = StaffScreen.num(sl, "total"), bonus = StaffScreen.num(sl, "bonus");
        String b = bonus == 0 ? "" : " (bonus " + (bonus > 0 ? "+" : "") + bonus + ")";
        return total > 0 ? used + " / " + total + b : used + " utilisé" + (used > 1 ? "s" : "") + b;
    }

    /* ================================================================ section repliée / dépliée */

    int compte(JsonObject p) {
        JsonObject d = data(p);
        return d == null ? 0 : StaffScreen.arr(d, "persos").size();
    }

    String apercu(JsonObject p) {
        demander(StaffScreen.str(p, "uuid"), false);
        JsonObject d = data(p);
        if (d == null) return "…";
        JsonArray l = StaffScreen.arr(d, "persos");
        if (l.isEmpty()) return "Aucun personnage · " + slots(d) + " emplacements";
        List<String> n = new ArrayList<>();
        for (JsonElement e : l) {
            JsonObject c = e.getAsJsonObject();
            String nom = StaffScreen.str(c, "nom");
            n.add(StaffScreen.bool(c, "actif") ? nom + " (joué)" : StaffScreen.bool(c, "mort") ? nom + " (mort)" : nom);
        }
        return String.join(", ", n) + " · " + slots(d);
    }

    int contenu(JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int x = s.ix() + 13, w = s.iw() - 13;
        JsonObject d = data(p);
        if (d == null) { pz.texte("Chargement des personnages…", x, y + 4, 12, GRIS_2, false); return y + 10; }
        JsonArray l = StaffScreen.arr(d, "persos");
        if (l.isEmpty()) { pz.texte("Aucun personnage sur ce compte.", x, y + 4, 12, GRIS_2, false); y += 12; }
        for (JsonElement e : l) y = ligne(e.getAsJsonObject(), x, w, y);
        y += 3;
        // emplacements
        String vEmp = verrou(d, "emplacements");
        float lw = pz.texte("Emplacements", x, y + 4, 12, GRIS, false);
        pz.texte(slots(d), x + lw + 5, y + 4, 12, CREME, false);
        float rx = x + w;
        rx -= motVerrou("Retirer…", rx, y + 4, 12, false, vEmp, () -> formEmplacement(p, -1), true) + 9;
        motVerrou("Ajouter…", rx, y + 4, 12, false, vEmp, () -> formEmplacement(p, 1), true);
        y += 13;
        motVerrou("+ Créer un personnage…", x, y + 4, 13, true, verrou(d, "creer"), () -> formCreer(p), false);
        y += 13;
        if (!StaffScreen.bool(d, "online")) {
            for (String li : s.wrap("Hors ligne : chaque changement est enregistré tout de suite et s'applique à sa reconnexion. « Jouer ce personnage » demande qu'il soit connecté.", w, 12)) {
                pz.texte(li, x, y + 4, 12, GRIS_2, false);
                y += 9;
            }
            y += 2;
        } else if (StaffScreen.bool(d, "selection")) {
            pz.texte("À l'écran de sélection en ce moment.", x, y + 4, 12, GRIS_2, false);
            y += 11;
        }
        return y;
    }

    /** Une ligne (22 GUI) : pastille d'état, N° et nom, résumé ; « joué » / « mort RP » à droite ; clic = détail. */
    private int ligne(JsonObject c, int x, int w, int y) {
        GardeArt.Pinceau pz = s.pz;
        int h = 22;
        boolean mort = StaffScreen.bool(c, "mort"), actif = StaffScreen.bool(c, "actif");
        boolean hov = s.menu == null && s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov) {
            pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_3);
            pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR);
        }
        int col = actif ? OK : mort ? ROUGE : GRIS_3;
        pz.pastille(x + 1.5f, y + 7, 1.6f, col);
        float tx = x + 7;
        String pid = StaffScreen.str(c, "pid");
        if (!pid.isEmpty()) tx += pz.texte(pid, tx, y + 7, 12, GRIS_2, false) + 5;
        String tag = actif ? "joué" : mort ? "mort RP" : "";
        float tw = tag.isEmpty() ? 0 : pz.texteD(tag, x + w, y + 7, 12, actif ? OK : ROUGE, false);
        s.txt(StaffScreen.str(c, "nom"), tx, y + 7, x + w - tx - tw - 6, 14, mort ? GRIS : CREME, false);
        s.txt(resume(c, true), x + 7, y + 16, w - 7 - 12, 12, GRIS_2, false);
        pz.icone("droite", x + w - 6, y + 13, 6, hov ? CREME : GRIS_3);
        String id = StaffScreen.str(c, "id");
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> {
            detail = id;
            s.scroll = 0;
            RebornSounds.uiClick();
        }, null, null));
        pz.filetH(x + 7, x + w, y + h - 0.5f, SEP);
        return y + h;
    }

    /* ================================================================ détail d'un personnage */

    int detail(JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int x = s.ix(), w = s.iw();
        JsonObject d = data(p);
        JsonObject c = d == null ? null : trouver(d, detail);
        // « < Personnages »
        boolean hovB = s.hover(x - 3, y - 2, 70, 12);
        pz.icone("gauche", x - 2, y + 0.5f, 8, hovB ? CREME : GRIS);
        pz.texte("Personnages", x + 7, y + 4.5f, 13, hovB ? CREME : GRIS, false);
        if (s.cliquable(y - 2, 12)) s.hits.add(new StaffScreen.Hit(x - 3, y - 2, 70, 12, this::echap, null, null));
        if (c == null) {
            if (d != null) detail = null;   // supprimé entre-temps : retour à la liste
            return s.vide(y + 12, "Chargement…", null);
        }
        String pid = StaffScreen.str(c, "pid");
        if (!pid.isEmpty()) pz.texteD("N° " + pid, x + w, y + 4.5f, 12, GRIS_2, false);
        y += 15;
        boolean mort = StaffScreen.bool(c, "mort"), actif = StaffScreen.bool(c, "actif");
        String tag = actif ? "joué en ce moment" : mort ? "mort RP" : "";
        float tw = tag.isEmpty() ? 0 : pz.texteD(tag, x + w, y + 5, 12, actif ? OK : ROUGE, false);
        if (!tag.isEmpty()) pz.pastille(x + w - tw - 4.5f, y + 5, 1.5f, actif ? OK : ROUGE);
        s.txt(StaffScreen.str(c, "nom"), x, y + 5, w - tw - 12, 18, mort ? GRIS : CREME, true);
        y += 12;
        String vil = StaffScreen.str(c, "village");
        List<String> sous = new ArrayList<>();
        if (!StaffScreen.str(c, "clan").isEmpty()) sous.add(StaffScreen.str(c, "clan"));
        if (!vil.isEmpty()) sous.add(vil);
        sous.add(StaffScreen.str(c, "rang"));
        s.txt(String.join(" · ", sous), x, y + 4, w, 13, GRIS, false);
        y += 11;
        pz.filetH(x, x + w, y, FILET);
        y += 4;

        // identité en deux colonnes
        String cree = StaffScreen.str(c, "cree");
        JsonArray nat = StaffScreen.arr(c, "natures");
        List<String> natures = new ArrayList<>();
        for (JsonElement e : nat) natures.add(e.getAsString());
        String[][] kv = {
                {"Âge", StaffScreen.num(c, "age") + " ans", "Taille", StaffScreen.num(c, "taille") + " cm"},
                {"Sexe", StaffScreen.str(c, "sexe"), "Créé le", cree.isEmpty() ? "—" : cree},
                {"Art", art(StaffScreen.str(c, "art")), "Affinité", affinite(StaffScreen.str(c, "affinite"))},
                {"Natures", natures.isEmpty() ? "aucune" : String.join(" · ", natures), "Techniques", String.valueOf(StaffScreen.num(c, "techniques"))},
                {"Ryo", nombre(StaffScreen.lng(c, "ryo")), "Blessures", String.valueOf(StaffScreen.num(c, "blessures"))}};
        float c2 = x + w / 2f + 2;
        for (String[] r : kv) {
            cle(r[0], r[1], x, y, c2 - x - 6);
            cle(r[2], r[3], c2, y, x + w - c2);
            y += 10;
        }
        if (d.has("playtime")) {
            cle("Temps de jeu (compte)", (StaffScreen.num(d, "playtime") / 60) + " h", x, y, w);
            y += 10;
        }
        y += 3;

        // fiche shinobi
        pz.capitales("Fiche shinobi", x, y + 4, 11, GRIS_2);
        pz.texteD("PV " + nombre(StaffScreen.num(c, "pvMax")) + "  ·  Chakra " + nombre(StaffScreen.num(c, "chakraMax")), x + w, y + 4, 12, GRIS, false);
        y += 11;
        JsonArray st = StaffScreen.arr(c, "stats");
        float cw = w / 3f;
        for (int i = 0; i < st.size(); i++) {
            JsonObject o = st.get(i).getAsJsonObject();
            float cx = x + (i % 3) * cw;
            int cy = y + (i / 3) * 11;
            pz.fond(Math.round(cx), cy, Math.round(cx + cw - 3), cy + 10, NUIT_2);
            pz.texte(StaffScreen.str(o, "n"), cx + 4, cy + 5, 12, GRIS, false);
            pz.texteD(String.valueOf(StaffScreen.num(o, "v")), cx + cw - 7, cy + 5, 12, CREME, true);
        }
        y += ((st.size() + 2) / 3) * 11 + 1;
        int pts = StaffScreen.num(c, "points");
        s.txt((pts > 0 ? pts + " point" + (pts > 1 ? "s" : "") + " à répartir" : "Aucun point à répartir") + "  ·  bonus staff "
                + StaffScreen.num(c, "bonus") + "  ·  niveau " + StaffScreen.num(c, "niveau"), x, y + 4, w, 12, pts > 0 ? OR : GRIS_2, false);
        y += 11;
        pz.filetH(x, x + w, y, FILET);
        y += 5;

        // actions
        boolean enLigne = StaffScreen.bool(d, "online");
        String vMod = verrou(d, "modifier"), vProg = verrou(d, "progression");
        y = groupe("Modifier", y, x, w, new Object[][]{
                {"Identité…", vMod, (Runnable) () -> formIdentite(p, c), false},
                {"Clan et village…", vMod, (Runnable) () -> formOrigine(p, d, c), false},
                {"Art et affinité…", vMod, (Runnable) () -> formArt(p, c), false}});
        String vActif = verrou(d, "actif");
        if (vActif == null) vActif = !enLigne ? "Le joueur doit être en ligne." : actif ? "Déjà joué en ce moment." : mort ? "Mort RP : ressuscite-le d'abord." : null;
        y = groupe("Progression et jeu", y, x, w, new Object[][]{
                {"Grade et points…", vProg, (Runnable) () -> formProgression(p, d, c), false},
                {"Jouer ce personnage…", vActif, (Runnable) () -> formActif(p, c), false}});
        y = groupe("Décisions lourdes", y, x, w, new Object[][]{
                {mort ? "Ressusciter…" : "Déclarer mort RP…", verrou(d, "mort"), (Runnable) () -> formMort(p, c, !mort), !mort},
                {"Remettre à zéro…", verrou(d, "reset"), (Runnable) () -> formLourd(p, c, "reset"), true},
                {"Supprimer…", verrou(d, "suppr"), (Runnable) () -> formLourd(p, c, "suppr"), true}});
        if (!enLigne) {
            pz.texte("Hors ligne : appliqué à sa reconnexion.", x, y + 4, 12, GRIS_2, false);
            y += 11;
        }
        return y;
    }

    private void cle(String k, String v, float x, int y, float w) {
        float kw = s.pz.texte(k, x, y + 4, 12, GRIS_2, false);
        s.txt(v, x + kw + 4, y + 4, w - kw - 4, 12, CREME, false);
    }

    /** Un groupe d'actions : titre en petites capitales puis les mots, à la ligne quand il le faut. */
    private int groupe(String titre, int y, int x, int w, Object[][] mots) {
        s.pz.capitales(titre, x, y + 4, 11, GRIS_2);
        y += 11;
        float ox = x;
        for (Object[] m : mots) {
            String lab = (String) m[0];
            float mw = s.pz.largeur(lab, 13, false) + ((m[1] != null) ? 9 : 0);
            if (ox > x && ox + mw > x + w) { ox = x; y += 13; }
            ox += motVerrou(lab, ox, y + 4, 13, false, (String) m[1], (Runnable) m[2], false, (boolean) m[3]) + 11;
        }
        return y + 16;
    }

    private float motVerrou(String lab, float x, float cy, int taille, boolean primaire, String verrou, Runnable r, boolean droite) {
        return motVerrou(lab, x, cy, taille, primaire, verrou, r, droite, false);
    }

    /**
     * Mot d'action ; éteint (gris + cadenas, la raison au clic) si {@code verrou}. {@code droite} : x est le bord droit.
     * {@code danger} : rouge. Renvoie la largeur.
     */
    private float motVerrou(String lab, float x, float cy, int taille, boolean primaire, String verrou, Runnable r, boolean droite, boolean danger) {
        GardeArt.Pinceau pz = s.pz;
        float w = pz.largeur(lab, taille, primaire) + (verrou != null ? 9 : 0);
        float x0 = droite ? x - w : x;
        int X = Math.round(x0), Y = Math.round(cy - 6);
        boolean ok = s.cliquable(Y, 12);
        if (verrou != null) {
            pz.texte(lab, x0, cy, taille, GRIS_3, primaire);
            pz.icone("verrou", x0 + w - 6, cy - 3.5f, 6, GRIS_3);
            if (ok) s.hits.add(new StaffScreen.Hit(X - 1, Y, Math.round(w) + 2, 12, null, null, verrou));
            s.tip(X - 1, Y, Math.round(w) + 2, 12, verrou);
            return w;
        }
        boolean hov = s.hover(X - 1, Y, Math.round(w) + 2, 12) && ok;
        int col = danger ? (hov ? 0xFFF08A98 : ROUGE) : primaire ? (hov ? OR_CLAIR : OR) : hov ? CREME : 0xFFD8CCBA;
        pz.texte(lab, x0, cy, taille, col, primaire);
        if (hov) pz.filetH(x0, x0 + w, cy + 5.5f, col);
        if (ok) s.hits.add(new StaffScreen.Hit(X - 1, Y, Math.round(w) + 2, 12, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
        return w;
    }

    private static String art(String id) { return "KENJUTSU".equals(id) ? "Kenjutsu" : "Taïjutsu"; }

    private static String affinite(String id) {
        return switch (id) { case "INTELLIGENCE" -> "Intelligence"; case "AGILITY" -> "Agilité"; default -> "Force"; };
    }

    /* ================================================================ formulaires (dans le volet) */

    private static final List<String> JOURNAL = List.of("Inscrit au journal, visible des autres staffs.");

    private static String[][] paires(JsonObject d, String cle, String[][] defaut) {
        JsonArray a = StaffScreen.arr(d, cle);
        if (a.isEmpty()) return defaut;
        String[][] out = new String[a.size()][];
        for (int i = 0; i < a.size(); i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            out[i] = new String[]{StaffScreen.str(o, "id"), StaffScreen.str(o, "label")};
        }
        return out;
    }

    private static int indice(String[][] ch, String v) {
        for (int i = 0; i < ch.length; i++) if (ch[i][0].equalsIgnoreCase(v) || ch[i][1].equalsIgnoreCase(v)) return i;
        return -1;
    }

    private static final String[][] RANGS = {{"ACADEMICIEN", "Académicien"}, {"GENIN", "Genin"}, {"GENIN_CONFIRME", "Genin confirmé"},
            {"CHUNIN", "Chūnin"}, {"KONIN", "Konin"}, {"TOKUBETSU_JONIN", "Tokubetsu Jōnin"}, {"JONIN", "Jōnin"},
            {"COMMANDANT_JONIN", "Commandant Jōnin"}, {"KAGE", "Kage"}};
    private static final String[][] VILLAGES = {{"Konohagakure", "Konoha"}, {"Sunagakure", "Suna"}, {"Kirigakure", "Kiri"},
            {"Kumogakure", "Kumo"}, {"Iwagakure", "Iwa"}, {"Amegakure", "Ame"}, {"Déserteur", "Déserteur"}, {"", "Aucun"}};

    private void formIdentite(JsonObject p, JsonObject c) {
        String[][] sexes = {{"Homme", "Homme"}, {"Femme", "Femme"}};
        int is = Math.max(0, indice(sexes, StaffScreen.str(c, "sexe")));
        s.formulaire.ouvrir("noter", "Identité de " + StaffScreen.str(c, "nom"), "Seuls les champs changés sont appliqués.",
                "Enregistrer", false, null, List.of(
                        GardeFormulaire.Champ.texte("nom", "Nom", "Prénom Clan", true, StaffScreen.str(c, "nom")),
                        GardeFormulaire.Champ.texte("age", "Âge (ans)", "14", true, String.valueOf(StaffScreen.num(c, "age"))),
                        GardeFormulaire.Champ.texte("taille", "Taille (cm)", "170", true, String.valueOf(StaffScreen.num(c, "taille"))),
                        GardeFormulaire.Champ.choix("sexe", "Sexe", sexes, is),
                        GardeFormulaire.Champ.texte("motif", "Motif (facultatif)", "Demande du joueur, ticket n° 214…", false)),
                JOURNAL, v -> envoyer(p, c, "identite", v));
    }

    private void formOrigine(JsonObject p, JsonObject d, JsonObject c) {
        String[][] vil = paires(d, "villages", VILLAGES);
        int iv = indice(vil, StaffScreen.str(c, "village"));
        if (iv < 0) iv = indice(vil, StaffScreen.str(c, "villageCourt"));
        if (iv < 0) iv = vil.length - 1;
        s.formulaire.ouvrir("lien", "Clan et village de " + StaffScreen.str(c, "nom"), "Clan libre : un clan connu est corrigé (« uchiha » → Uchiha) ; « aucun » le retire.",
                "Enregistrer", false, null, List.of(
                        GardeFormulaire.Champ.choix("village", "Village", vil, iv),
                        GardeFormulaire.Champ.texte("clan", "Clan", "Uchiha, Sabaku, aucun…", true, StaffScreen.str(c, "clan").isEmpty() ? "aucun" : StaffScreen.str(c, "clan")),
                        GardeFormulaire.Champ.texte("motif", "Motif (facultatif)", "Changement validé en candidature…", false)),
                JOURNAL, v -> envoyer(p, c, "origine", v));
    }

    private void formArt(JsonObject p, JsonObject c) {
        String[][] arts = {{"TAIJUTSU", "Taïjutsu"}, {"KENJUTSU", "Kenjutsu"}};
        String[][] affs = {{"STRENGTH", "Force"}, {"INTELLIGENCE", "Intelligence"}, {"AGILITY", "Agilité"}};
        s.formulaire.ouvrir("epee", "Art et affinité de " + StaffScreen.str(c, "nom"), "Étiquettes RP : sans effet sur les stats.",
                "Enregistrer", false, null, List.of(
                        GardeFormulaire.Champ.choix("art", "Art", arts, Math.max(0, indice(arts, StaffScreen.str(c, "art")))),
                        GardeFormulaire.Champ.choix("affinite", "Affinité", affs, Math.max(0, indice(affs, StaffScreen.str(c, "affinite"))))),
                JOURNAL, v -> envoyer(p, c, "art", v));
    }

    private void formProgression(JsonObject p, JsonObject d, JsonObject c) {
        String[][] rangs = paires(d, "rangs", RANGS);
        s.formulaire.ouvrir("bouclier", "Grade et points de " + StaffScreen.str(c, "nom"),
                "Un changement de grade est inscrit aux états de service et recalcule les points.",
                "Enregistrer", false, null, List.of(
                        GardeFormulaire.Champ.choix("rang", "Grade", rangs, Math.max(0, indice(rangs, StaffScreen.str(c, "rangId")))),
                        GardeFormulaire.Champ.texte("bonus", "Points de stats bonus (total)", "0", true, String.valueOf(StaffScreen.num(c, "bonus"))),
                        GardeFormulaire.Champ.texte("motif", "Motif (facultatif)", "Examen chūnin réussi le 14/10…", false)),
                JOURNAL, v -> envoyer(p, c, "progression", v));
    }

    private void formActif(JsonObject p, JsonObject c) {
        s.formulaire.ouvrir("revenir", "Faire jouer " + StaffScreen.str(c, "nom") + " ?",
                "Le joueur quitte son personnage actuel et reprend celui-ci là où il l'avait laissé. Refusé s'il est à terre, en combat ou gelé.",
                "Basculer", false, null, List.of(GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", "Bloqué sur le mauvais personnage…", true)),
                JOURNAL, v -> envoyer(p, c, "actif", v));
    }

    private void formMort(JsonObject p, JsonObject c, boolean mort) {
        String nom = StaffScreen.str(c, "nom");
        s.formulaire.ouvrir(mort ? "crane" : "coeur", mort ? "Déclarer " + nom + " mort RP ?" : "Ressusciter " + nom + " ?",
                mort ? "Le personnage n'est plus jouable. S'il est joué, le joueur revient à la sélection. Réservé aux décisions hors scène (une mise à mort passe par les alertes)."
                        : "Le personnage redevient jouable.",
                mort ? "Déclarer mort" : "Ressusciter", mort, null,
                List.of(GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", mort ? "Décision du conseil, ticket n° 98…" : "Mort annulée après appel…", true)),
                JOURNAL, v -> envoyer(p, c, mort ? "mort" : "ressusciter", v));
    }

    private void formLourd(JsonObject p, JsonObject c, String op) {
        boolean suppr = "suppr".equals(op);
        String nom = StaffScreen.str(c, "nom"), pid = StaffScreen.str(c, "pid");
        s.formulaire.ouvrir(suppr ? "suppr" : "zero", (suppr ? "Supprimer " : "Remettre à zéro ") + nom + " ?",
                suppr ? "Le personnage disparaît et son emplacement est libéré ; son N° n'est jamais réutilisé. S'il est joué, le joueur est déconnecté. Irréversible."
                        : "XP, niveau, stats, techniques, grade, ryo, sacoche, inventaire, blessures et apparence. Gardés : N°, nom, clan, village, âge, taille. Irréversible.",
                suppr ? "Supprimer" : "Remettre à zéro", true, pid.isEmpty() ? nom : pid,
                List.of(GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", suppr ? "Demande du joueur, ticket n° 301…" : "Départ RP validé…", true)),
                List.of("Une sauvegarde est faite avant (sauvegardes/perso/).", "Inscrit au journal, visible des autres staffs."),
                v -> envoyer(p, c, op, v));
    }

    /** Le compte (pseudo) : les emplacements et la création portent sur le compte, pas sur un personnage. */
    private static String compte_(JsonObject p) {
        String n = StaffScreen.str(p, "name");
        return n.isEmpty() ? StaffScreen.name(p) : n;
    }

    private void formEmplacement(JsonObject p, int delta) {
        String nom = compte_(p);
        s.formulaire.ouvrir("plus", delta > 0 ? "Débloquer un emplacement pour " + nom : "Retirer un emplacement à " + nom,
                delta > 0 ? "Un personnage de plus possible sur ce compte (en plus de sa base : 2, premium 4, staff 7)."
                        : "Les personnages existants restent ; il ne pourra pas en créer au-delà.",
                delta > 0 ? "Débloquer" : "Retirer", delta < 0, null,
                List.of(GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", "Récompense d'event, achat…", true)),
                JOURNAL, v -> {
                    JsonObject o = base(p, "perso_slots");
                    o.addProperty("delta", delta);
                    o.addProperty("motif", v.getOrDefault("motif", ""));
                    StaffClient.send(o);
                });
    }

    private void formCreer(JsonObject p) {
        s.formulaire.ouvrir("joueurs", "Créer un personnage pour " + compte_(p),
                "Nom seulement : âge, clan et village se règlent ensuite (« Identité… », « Clan et village… ») ; apparence par défaut. Le N° est attribué tout de suite.",
                "Créer", false, null, List.of(
                        GardeFormulaire.Champ.texte("nom", "Nom", "Prénom Clan", true),
                        GardeFormulaire.Champ.texte("motif", "Motif (obligatoire)", "PNJ d'event, rattrapage d'un bug…", true)),
                JOURNAL, v -> {
                    JsonObject o = base(p, "perso_creer");
                    o.addProperty("nom", v.getOrDefault("nom", ""));
                    o.addProperty("motif", v.getOrDefault("motif", ""));
                    StaffClient.send(o);
                });
    }

    /**
     * Remise à zéro / suppression depuis le menu « Plus » de la fiche (personnage joué, visé par N°) : même
     * formulaire, motif obligatoire, N° retapé.
     */
    void lourdParPid(JsonObject p, String op) {
        JsonObject c = new JsonObject();
        c.addProperty("pid", StaffScreen.str(p, "pid"));
        c.addProperty("nom", StaffScreen.name(p));
        formLourd(p, c, op);
    }

    /** Banc d'essai ({@link StaffDebug}) : liste, détail, formulaires remplis. */
    void debug(JsonObject p, String cas) {
        JsonObject d = data(p);
        JsonArray l = StaffScreen.arr(d, "persos");
        if (l.isEmpty()) return;
        JsonObject c0 = l.get(0).getAsJsonObject(), c1 = l.get(Math.min(1, l.size() - 1)).getAsJsonObject();
        switch (cas) {
            case "persos_detail", "persos_refus" -> detail = StaffScreen.str(c0, "id");
            case "persos_identite" -> {
                detail = StaffScreen.str(c0, "id");
                formIdentite(p, c0);
                s.formulaire.debugValeur(1, "15");
                s.formulaire.debugValeur(3, "Anniversaire RP validé (ticket n° 214)");
            }
            case "persos_grade" -> {
                detail = StaffScreen.str(c0, "id");
                formProgression(p, d, c0);
                s.formulaire.debugValeur(1, "Examen chūnin réussi le 14/10");
            }
            case "persos_mort" -> {
                detail = StaffScreen.str(c1, "id");
                formMort(p, c1, true);
                s.formulaire.debugValeur(0, "Décision du conseil des clans, ticket n° 98");
            }
            case "persos_suppr" -> {
                detail = StaffScreen.str(c1, "id");
                formLourd(p, c1, "suppr");
                s.formulaire.debugValeur(0, "Demande du joueur, ticket n° 301");
                s.formulaire.debugExiger(StaffScreen.str(c1, "pid"));
            }
            case "persos_emplacement" -> {
                formEmplacement(p, 1);
                s.formulaire.debugValeur(0, "Récompense de l'event du 12/10");
            }
            case "persos_creer" -> {
                formCreer(p);
                s.formulaire.debugValeur(0, "Hana Uchiha");
                s.formulaire.debugValeur(1, "PNJ de l'event du 20/10");
            }
            default -> { }
        }
    }

    private static JsonObject base(JsonObject p, String a) {
        JsonObject o = new JsonObject();
        o.addProperty("a", a);
        o.addProperty("t", StaffScreen.str(p, "uuid"));
        return o;
    }

    private void envoyer(JsonObject p, JsonObject c, String op, Map<String, String> v) {
        JsonObject o = base(p, "perso_act");
        if (!StaffScreen.str(c, "id").isEmpty()) o.addProperty("c", StaffScreen.str(c, "id"));
        else o.addProperty("pid", StaffScreen.str(c, "pid"));
        o.addProperty("op", op);
        for (Map.Entry<String, String> e : new HashMap<>(v).entrySet()) o.addProperty(e.getKey(), e.getValue() == null ? "" : e.getValue());
        StaffClient.send(o);
        if ("suppr".equals(op)) detail = null;
    }
}
