package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.faction.FactionClient;
import fr.reborn.hud.faction.Insignes;
import fr.reborn.hud.menu.RebornSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Poste de garde › Outils › <b>Factions</b> (FACTION.md §11, maquette v2 04, 16/10) : la liste des factions du serveur
 * (emblème réel, tag, type, effectif, chef ou « chef à nommer »), « + Créer une faction » (Admin), puis la fiche d'une
 * faction : outils compacts (Chef…, Section…, Renommer, Dissoudre ; Porte, Coffre et Quotas au lot suivant), ses
 * sections (renommer, cacher, places, chef, caisse, dissoudre) et son journal. Toute action demande un <b>motif</b>, est
 * revérifiée par le serveur (grades : voir Helper, sections et chefs Modérateur, faction / dissoudre / caisse Admin) et
 * journalisée (catégorie FACTION). Données : canal {@code reborn:faction} ({@link FactionClient#staff}).
 */
final class GardeFactions {

    private final StaffScreen s;
    /** Faction ouverte (tag) ou null = la liste. */
    String fiche;
    private final List<Object[]> insignes = new ArrayList<>();

    GardeFactions(StaffScreen s) { this.s = s; }

    void ouvrir() {
        fiche = null;
        FactionClient.send("staff");
    }

    String pied() { return fiche != null ? "Échap : retour aux outils  ·  ‹ Factions : la liste" : "Clic : la fiche de la faction"; }

    Object[] resume() {
        JsonObject st = FactionClient.staff();
        int n = st == null ? 0 : StaffScreen.arr(st, "factions").size();
        return new Object[]{n, "Créer une faction, nommer le chef, corriger une section, journal"};
    }

    private int grade() { return StaffScreen.num(FactionClient.staff(), "grade"); }

    private JsonObject faction(String tag) {
        for (JsonElement e : StaffScreen.arr(FactionClient.staff(), "factions"))
            if (tag.equals(StaffScreen.str(e.getAsJsonObject(), "tag"))) return e.getAsJsonObject();
        return null;
    }

    private void insigne(String nom, float x, float y, int t, int col) { insignes.add(new Object[]{nom, x, y, t, col}); }

    /** Vide le pinceau puis pose les insignes (au-dessus des fonds déjà peints). */
    private void poserInsignes() {
        if (insignes.isEmpty() || s.ctxCourant == null) return;
        s.pz.vider(s.ctxCourant);
        for (Object[] i : insignes) Insignes.poser(s.ctxCourant, (String) i[0], (float) i[1], (float) i[2], (int) i[3], (int) i[4]);
        insignes.clear();
    }

    int corps(JsonObject snap, int y) {
        insignes.clear();
        JsonObject st = FactionClient.staff();
        int r;
        if (st == null) r = s.vide(y, "Chargement des factions…", null);
        else if (fiche != null && faction(fiche) != null) r = fiche(faction(fiche), y + 2);
        else r = liste(st, y + 2);
        poserInsignes();
        return r;
    }

    /* ---------------------------------------------------------------- liste */

    private int liste(JsonObject st, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw(), g = grade();
        pz.capitales("Factions de ce serveur", ix, y + 4, 11, GRIS_2);
        s.refusVerrou = "Créer une faction : Admin et plus.";
        float w = pz.largeur("+ Créer une faction", 13, true);
        if (g >= 3) s.motCouleur("+ Créer une faction", ix + iw - w, y + 4, 13, OR, this::creerFaction);
        else pz.texteD("+ Créer une faction", ix + iw, y + 4, 13, GRIS_3, true);
        s.refusVerrou = null;
        y += 12;
        for (JsonElement e : StaffScreen.arr(st, "factions")) {
            JsonObject f = e.getAsJsonObject();
            int h = 22;
            boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
            if (hov) { pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_3); pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR); }
            insigne(StaffScreen.str(f, "embleme"), ix, y + 5.5f, 11, OR);
            s.txt(StaffScreen.str(f, "nom"), ix + 16, y + 6.5f, iw - 92, 14, CREME, false);
            String sous = StaffScreen.str(f, "tag") + " · " + StaffScreen.str(f, "type") + " · " + StaffScreen.num(f, "n") + " membres";
            s.txt(sous, ix + 16, y + 15.5f, iw - 30, 12, GRIS_2, false);
            JsonObject ch = StaffScreen.obj(f, "chef");
            if (ch != null) pz.texteD(s.fit(StaffScreen.str(ch, "nom"), 70, 12, false), ix + iw, y + 6.5f, 12, GRIS, false);
            else pz.texteD("chef à nommer", ix + iw, y + 6.5f, 12, TERRE, false);
            String tag = StaffScreen.str(f, "tag");
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> { fiche = tag; s.scroll = 0; RebornSounds.uiClick(); }, null, null));
            y += h;
            pz.filetH(ix + 16, ix + iw, y - 0.5f, SEP);
        }
        y += 6;
        for (String l : s.wrap("Les six villages existent d'office (emblème réel, tag KON, SUN…) : il ne reste qu'à nommer leur chef. "
                + "Une organisation (Kurogane…) se crée ici ; ses membres sont des personnages sans village.", iw, 12)) {
            pz.texte(l, ix, y + 4, 12, GRIS_2, false);
            y += 9;
        }
        return y + 4;
    }

    /* ---------------------------------------------------------------- fiche d'une faction */

    private static final String[][] OUTILS = {{"joueurs", "Chef…"}, {"drapeau", "Section…"}, {"verrou", "Porte…"}, {"sac", "Coffre…"},
            {"horloge", "Quotas"}, {"journal", "Journal"}, {"noter", "Renommer"}, {"suppr", "Dissoudre"}};

    private int fiche(JsonObject f, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw(), g = grade();
        String tag = StaffScreen.str(f, "tag");
        boolean orga = "organisation".equals(StaffScreen.str(f, "type"));
        // retour à la liste
        boolean hovR = s.hover(ix - 3, y - 2, 50, 12);
        pz.icone("gauche", ix - 2, y + 0.5f, 8, hovR ? CREME : GRIS);
        pz.texte("Factions", ix + 7, y + 4.5f, 13, hovR ? CREME : GRIS, false);
        s.hits.add(new StaffScreen.Hit(ix - 3, y - 2, 50, 12, () -> { fiche = null; s.scroll = 0; RebornSounds.uiClick(); }, null, null));
        y += 15;
        insigne(StaffScreen.str(f, "embleme"), ix, y + 2, 14, OR);
        s.txt(StaffScreen.str(f, "nom"), ix + 19, y + 5, iw - 19, 16, CREME, true);
        s.txt(tag + " · " + StaffScreen.str(f, "type") + " · " + StaffScreen.num(f, "n") + " membres · taxe " + StaffScreen.num(f, "taux") + " %",
                ix + 19, y + 14.5f, iw - 19, 12, GRIS_2, false);
        y += 22;
        JsonObject ch = StaffScreen.obj(f, "chef");
        pz.texte("Chef", ix, y + 4, 12, GRIS, false);
        if (ch != null) s.txt(StaffScreen.str(ch, "nom") + " · " + StaffScreen.str(ch, "pid"), ix + 30, y + 4, iw - 30, 13, CREME, false);
        else pz.texte("à nommer", ix + 30, y + 4, 13, TERRE, false);
        y += 13;
        // outils en grille (4 × 2), comme la maquette v2 04
        int cw = (iw - 9) / 4, chh = 26;
        for (int i = 0; i < OUTILS.length; i++) {
            String[] o = OUTILS[i];
            int cx = ix + (i % 4) * (cw + 3), cy = y + (i / 4) * (chh + 3);
            int need = switch (o[1]) { case "Chef…", "Section…" -> 2; case "Renommer", "Dissoudre" -> 3; default -> 1; };
            boolean lot = o[1].equals("Porte…") || o[1].equals("Coffre…") || o[1].equals("Quotas");
            boolean eteint = lot || g < need || (o[1].equals("Dissoudre") && !orga);
            boolean hov = !eteint && s.hover(cx, cy, cw, chh) && s.cliquable(cy, chh);
            pz.fond(cx, cy, cx + cw, cy + chh, hov ? NUIT_3 : NUIT_2);
            pz.cadre(cx, cy, cw, chh, hov ? OR_DOUX : FILET);
            pz.icone(o[0], cx + cw / 2f - 4, cy + 4, 8, eteint ? GRIS_3 : GRIS);
            pz.texteC(s.fit(o[1], cw - 4, 12, false), cx + cw / 2f, cy + 19, 12, eteint ? GRIS_3 : GRIS, false);
            String refus = lot ? "Lot suivant : les portes, coffres et quotas se posent après la beta (FACTION.md §13, F5)."
                    : o[1].equals("Dissoudre") && !orga ? "Un village ne se dissout pas." : "Réservé au grade " + StaffScreen.gradeName(need) + " et plus.";
            Runnable r = switch (o[1]) {
                case "Chef…" -> () -> chef(f);
                case "Section…" -> () -> creerSection(f);
                case "Renommer" -> () -> renommer(f);
                case "Dissoudre" -> () -> dissoudre(f);
                case "Journal" -> () -> { s.scroll = Integer.MAX_VALUE / 4; RebornSounds.uiClick(); };
                default -> null;
            };
            if (s.cliquable(cy, chh)) s.hits.add(new StaffScreen.Hit(cx, cy, cw, chh, eteint ? null : r, null, refus));
            s.tip(cx, cy, cw, chh, eteint ? refus : null);
        }
        y += 2 * (chh + 3) + 5;
        pz.filetH(ix, ix + iw, y + 0.5f, FILET);
        y += 7;
        JsonArray ss = StaffScreen.arr(f, "sections");
        pz.capitales("Sections · " + ss.size(), ix, y + 3, 11, GRIS_2);
        pz.texteD("caisse", ix + iw, y + 3, 11, GRIS_3, false);
        y += 10;
        if (ss.isEmpty()) { pz.texte(orga ? "La section racine porte les grades de l'organisation." : "Aucune section.", ix, y + 4, 12, GRIS_2, false); y += 12; }
        for (JsonElement e : ss) {
            JsonObject x = e.getAsJsonObject();
            int h = 21;
            boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h) && s.menu == null;
            if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
            insigne(StaffScreen.str(x, "insigne"), ix, y + 4.5f, 11, OR);
            float lw = s.txt(StaffScreen.str(x, "nom"), ix + 16, y + 5.5f, iw - 70, 14, CREME, false);
            if (StaffScreen.bool(x, "cachee")) { pz.icone("verrou", ix + 19 + lw, y + 2, 6, GRIS_2); pz.texte("cachée", ix + 28 + lw, y + 5.5f, 11, GRIS_2, false); }
            String chefN = StaffScreen.str(x, "chefNom");
            s.txt((chefN.isEmpty() ? "sans chef" : chefN) + " · " + StaffScreen.num(x, "n") + " / " + StaffScreen.num(x, "places") + " places",
                    ix + 16, y + 14.5f, iw - 60, 12, GRIS_2, false);
            pz.texteD(String.format(java.util.Locale.ROOT, "%,d", StaffScreen.lng(x, "caisse")).replace(',', ' '), ix + iw, y + 5.5f, 13, OR_CLAIR, false);
            final int yy = y;
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> menuSection(f, x, yy), null, null));
            y += h;
        }
        y += 4;
        pz.filetH(ix, ix + iw, y + 0.5f, FILET);
        y += 7;
        JsonArray ks = StaffScreen.arr(f, "charges");
        if (!ks.isEmpty()) {
            pz.capitales("Charges", ix, y + 3, 11, GRIS_2);
            List<String> l = new ArrayList<>();
            for (JsonElement e : ks) l.add(e.getAsString());
            s.txt(String.join(" · ", l), ix + 46, y + 3, iw - 46, 12, GRIS, false);
            y += 13;
        }
        pz.capitales("Journal (catégorie Faction)", ix, y + 3, 11, GRIS_2);
        y += 11;
        JsonArray jo = StaffScreen.arr(f, "journal");
        if (jo.isEmpty()) { pz.texte("Rien au journal.", ix, y + 4, 12, GRIS_2, false); y += 12; }
        for (JsonElement e : jo) {
            JsonObject l = e.getAsJsonObject();
            pz.texte(new java.text.SimpleDateFormat("dd/MM").format(new java.util.Date(StaffScreen.lng(l, "le"))), ix, y + 4, 11, GRIS_3, false);
            List<String> ls = s.wrap(StaffScreen.str(l, "t"), iw - 26, 12);
            for (int i = 0; i < ls.size(); i++) pz.texte(ls.get(i), ix + 26, y + 4 + i * 9, 12, GRIS, false);
            y += Math.max(1, ls.size()) * 9 + 4;
        }
        return y + 4;
    }

    /* ---------------------------------------------------------------- actions (formulaire dans le volet, motif obligatoire) */

    private static final List<String> NOTES = List.of("Inscrit au journal (catégorie Faction), visible des autres staffs.");

    private void envoyer(String a, Map<String, String> v, String... cles) {
        JsonObject o = new JsonObject();
        o.addProperty("a", a);
        for (int i = 0; i + 1 < cles.length; i += 2) o.addProperty(cles[i], cles[i + 1]);
        for (Map.Entry<String, String> e : v.entrySet()) {
            String k = e.getKey(), val = e.getValue();
            if (k.equals("places") || k.equals("n")) {
                try { o.addProperty(k, Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) { }
            } else if (k.equals("cachee")) o.addProperty(k, "oui".equals(val));
            else o.addProperty(k, val);
        }
        FactionClient.send(o);
        RebornSounds.confirm();
    }

    private void creerFaction() {
        String[][] types = {{"village", "village"}, {"organisation", "organisation"}};
        s.formulaire.ouvrir("drapeau", "Créer une faction", "Les six villages existent déjà : une nouvelle faction est d'abord une organisation.",
                "Créer", false, null, List.of(
                        GardeFormulaire.Champ.texte("nom", "Nom", "Kurogane", true),
                        GardeFormulaire.Champ.texte("tag", "Tag (3 lettres)", "KRG", true),
                        GardeFormulaire.Champ.choix("type", "Type", types, 1),
                        GardeFormulaire.Champ.texte("motif", "Motif", "Création validée en réunion staff du 16/10", true)),
                NOTES, v -> envoyer("staff_faction", v, "embleme", "organisation".equals(v.get("type")) ? "lune" : ""));
    }

    private void chef(JsonObject f) {
        s.formulaire.ouvrir("joueurs", "Chef de " + StaffScreen.str(f, "nom"), "Le chef de faction (le Kage pour un village) est une figure publique : "
                        + "il crée les sections, règle la taxe et les seuils.", "Nommer", false, null, List.of(
                        GardeFormulaire.Champ.texte("chef", "N° ou nom exact (vide = aucun)", "#00031", false),
                        GardeFormulaire.Champ.texte("motif", "Motif", "Nommé par le conseil RP", true)),
                NOTES, v -> envoyer("staff_chef", v, "tag", StaffScreen.str(f, "tag")));
    }

    private void creerSection(JsonObject f) {
        String[][] modeles = {{"libre", "libre"}, {"anbu", "ANBU"}, {"police", "Police"}, {"medical", "Médical"}, {"academie", "Académie"},
                {"missions", "Missions"}, {"garde", "Garde"}};
        String[][] cache = {{"non", "non"}, {"oui", "cachée"}};
        s.formulaire.ouvrir("drapeau", "Créer une section · " + StaffScreen.str(f, "tag"), "Un modèle préremplit l'insigne, les grades et la case « cachée ».",
                "Créer", false, null, List.of(
                        GardeFormulaire.Champ.choix("modele", "Modèle", modeles),
                        GardeFormulaire.Champ.texte("nom", "Nom (vide = celui du modèle)", "Brigade des archives", false),
                        GardeFormulaire.Champ.texte("chef", "Chef : N° ou nom exact", "#00042", true),
                        GardeFormulaire.Champ.choix("cachee", "Cachée", cache),
                        GardeFormulaire.Champ.texte("motif", "Motif", "Demande du Hokage (ticket 31)", true)),
                NOTES, v -> envoyer("staff_section", v, "tag", StaffScreen.str(f, "tag")));
    }

    private void renommer(JsonObject f) {
        s.formulaire.ouvrir("noter", "Renommer " + StaffScreen.str(f, "nom"), null, "Renommer", false, null, List.of(
                        GardeFormulaire.Champ.texte("nom", "Nom", "", true, StaffScreen.str(f, "nom")),
                        GardeFormulaire.Champ.texte("motif", "Motif", "Correction d'orthographe", true)),
                NOTES, v -> envoyer("staff_renommer", v, "tag", StaffScreen.str(f, "tag")));
    }

    private void dissoudre(JsonObject f) {
        s.formulaire.ouvrir("suppr", "Dissoudre " + StaffScreen.str(f, "nom") + " ?", "Ses membres perdent la faction ; ses sections disparaissent.",
                "Dissoudre", true, null, List.of(GardeFormulaire.Champ.texte("motif", "Motif", "Organisation abandonnée", true)),
                NOTES, v -> { envoyer("staff_dissoudre_faction", v, "tag", StaffScreen.str(f, "tag")); fiche = null; });
    }

    private void menuSection(JsonObject f, JsonObject x, int y) {
        String id = StaffScreen.str(x, "id"), nom = StaffScreen.str(x, "nom");
        int g = grade();
        List<StaffScreen.Item> items = new ArrayList<>();
        items.add(new StaffScreen.Item("noter", "Corriger…", (char) 0, g < 2 ? "Modo" : null, false, () -> corriger(x)));
        items.add(new StaffScreen.Item("sac", "Caisse…", (char) 0, g < 3 ? "Admin" : null, false, () -> caisse(x)));
        items.add(null);
        items.add(new StaffScreen.Item("suppr", "Dissoudre…", (char) 0, g < 3 ? "Admin" : null, true, () ->
                s.formulaire.ouvrir("suppr", "Dissoudre « " + nom + " » ?", "Membres libérés, caisse versée au trésor du village.", "Dissoudre", true, null,
                        List.of(GardeFormulaire.Champ.texte("motif", "Motif", "Section inactive depuis un mois", true)), NOTES,
                        v -> envoyer("staff_section_dissoudre", v, "id", id))));
        s.ouvrirMenu(items, s.px0() - 4, y + 10, x);
    }

    private void corriger(JsonObject x) {
        String[][] cache = {{"non", "visible"}, {"oui", "cachée"}};
        s.formulaire.ouvrir("noter", "Corriger « " + StaffScreen.str(x, "nom") + " »", "Laisse « Nouveau chef » vide pour ne pas le changer.", "Enregistrer",
                false, null, List.of(
                        GardeFormulaire.Champ.texte("nom", "Nom", "", true, StaffScreen.str(x, "nom")),
                        GardeFormulaire.Champ.texte("places", "Places", "8", true, String.valueOf(StaffScreen.num(x, "places"))),
                        GardeFormulaire.Champ.choix("cachee", "Visibilité", cache, StaffScreen.bool(x, "cachee") ? 1 : 0),
                        GardeFormulaire.Champ.texte("chef", "Nouveau chef : N° ou nom exact", "", false),
                        GardeFormulaire.Champ.texte("motif", "Motif", "Demande du chef de faction", true)),
                NOTES, v -> envoyer("staff_section_maj", v, "id", StaffScreen.str(x, "id")));
    }

    private void caisse(JsonObject x) {
        String[][] ops = {{"ajouter", "ajouter"}, {"retirer", "retirer"}, {"fixer", "fixer"}};
        s.formulaire.ouvrir("sac", "Caisse de « " + StaffScreen.str(x, "nom") + " »", "Contre-passation ou correction : la taxe ne s'applique pas.",
                "Corriger", false, null, List.of(
                        GardeFormulaire.Champ.choix("op", "Opération", ops),
                        GardeFormulaire.Champ.texte("n", "Montant (ryo)", "500", true),
                        GardeFormulaire.Champ.texte("motif", "Motif", "Erreur de versement (ticket 44)", true)),
                NOTES, v -> envoyer("staff_caisse", v, "id", StaffScreen.str(x, "id")));
    }

    /* ---------------------------------------------------------------- banc */

    void debugFiche(String tag) { fiche = tag; }

    void debugCreer() { creerFaction(); s.formulaire.debugValeur(0, "Kurogane"); s.formulaire.debugValeur(1, "KRG"); s.formulaire.debugValeur(2, "Création validée en réunion staff du 16/10"); }
}
