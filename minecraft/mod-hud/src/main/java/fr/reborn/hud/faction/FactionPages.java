package fr.reborn.hud.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.skin.CharacterCatalog;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static fr.reborn.hud.faction.FactionArt.*;
import static fr.reborn.hud.faction.FactionScreen.arr;
import static fr.reborn.hud.faction.FactionScreen.bool;
import static fr.reborn.hud.faction.FactionScreen.date;
import static fr.reborn.hud.faction.FactionScreen.depuis;
import static fr.reborn.hud.faction.FactionScreen.lng;
import static fr.reborn.hud.faction.FactionScreen.num;
import static fr.reborn.hud.faction.FactionScreen.obj;
import static fr.reborn.hud.faction.FactionScreen.ryo;
import static fr.reborn.hud.faction.FactionScreen.str;

/**
 * Les pages du volet Faction (maquettes v3) : Village (01), Ma section du membre (01) et du chef (02), Inviter (02),
 * Grades de section et fiche d'un membre (04), Gestion du chef de faction et Créer une section (03), Sections des gradés
 * avec l'ANBU en « ? » (05), Annonces, Charges, journaux, Tenue, formulaires dans le volet. Coordonnées en GUI = px des
 * maquettes 1080p / 2 ; tailles de texte en px des maquettes.
 */
final class FactionPages {

    /** Droits de grade de section (miroir de ShinobiCore {@code Droits.SECTION}). */
    static final String[][] DROITS_SECTION = {
            {"inviter", "Inviter face à face et depuis le panneau"}, {"inviter_distance", "Inviter à distance (en ligne)"},
            {"exclure", "Exclure (grades inférieurs)"}, {"monter", "Monter et descendre (sous son grade)"},
            {"voir_caisse", "Voir la caisse en chiffres"}, {"depenser", "Dépenser la caisse (guichet)"},
            {"coffre_voir", "Coffres de la section : voir, déposer"}, {"coffre_retirer", "Coffres de la section : retirer"},
            {"cloche_section", "Annoncer à la section"}, {"journal", "Lire le journal de la section"}};

    /** Droits de charge (miroir de {@code Droits.CHARGE}). */
    static final String[][] DROITS_CHARGE = {
            {"sections", "Créer et cacher des sections, nommer leurs chefs"}, {"taxe", "Régler la taxe et les seuils"},
            {"caisses", "Voir les caisses des sections"}, {"doter", "Doter une section (guichet)"},
            {"journal", "Lire le journal de la faction"}, {"annonces", "Annoncer au village"},
            {"alertes_palais", "Recevoir les alertes du palais"}, {"inviter_org", "Inviter dans l'organisation"}};

    /** Insignes proposés à la création (maquette 03) puis les autres. */
    static final String[] INSIGNES = {"missions", "academie", "garde", "police", "medical", "anbu", "conseil", "bandeau", "lune"};

    private final FactionScreen s;
    private EditBox recherche, places, taux, nom, chefRech, placesNew, champForm, motif, accessoire;

    /* création d'une section */
    String modele = "", insigne = "missions", chefId = "", chefLabel = "";
    boolean cachee;
    /* grades */
    String gradeSel;
    final Set<String> droitsEdit = new LinkedHashSet<>();
    boolean droitsSales;
    /* tenue */
    String tenueSel;
    /* formulaire dans le volet */
    String formType, formId, formCible, formTitre, formTexte, formChoix = "village", formCandidat = "", formCandidatLabel = "";
    /* charges */
    String chargeId;
    final Set<String> chargeDroits = new LinkedHashSet<>();
    boolean chargePublique, chargeSale;
    /* journal filtré (fiche d'un membre) */
    String journalFiltre;
    /* section consultée depuis l'onglet Sections */
    String sectionVue;
    /* invitation depuis le panneau */
    String envoye;
    long envoyeA;

    FactionPages(FactionScreen s) { this.s = s; }

    void init() {
        recherche = s.box(recherche, "Chercher parmi ceux que tu connais", 40);
        places = s.box(places, "", 3);
        taux = s.box(taux, "", 3);
        nom = s.box(nom, "Nom de la section", 32);
        chefRech = s.box(chefRech, "Chercher un membre du village", 40);
        placesNew = s.box(placesNew, "", 3);
        champForm = s.box(champForm, "", 240);
        motif = s.box(motif, "Motif (facultatif)", 120);
        accessoire = s.box(accessoire, "Brassard, masque… (objet Nexo du coffre)", 60);
    }

    void tick() {
        if ("inviter".equals(s.sous) && s.ticks % 40 == 0) FactionClient.send("proches");
    }

    void ouvertOnglet(String id) {
        sectionVue = null;
        recherche.setValue("");
    }

    void ouvertSous(String sous) {
        if ("inviter".equals(sous)) { FactionClient.send("proches"); envoye = null; }
        if ("creer".equals(sous)) {
            modele = ""; insigne = "missions"; chefId = ""; chefLabel = ""; cachee = false;
            nom.setValue(""); chefRech.setValue(""); placesNew.setValue("8");
            s.setFocused(nom); nom.setFocused(true);
        }
        if ("tenue".equals(sous)) {
            JsonObject sec = obj(s.vue(), "section");
            JsonObject t = obj(sec, "tenue");
            tenueSel = str(t, "id");
            accessoire.setValue(str(t, "accessoire"));
        }
    }

    String pied() {
        if (s.sous == null) {
            return "gestion".equals(s.tab) ? "Enregistrer : Entrée   ·   " + j() + " ou Échap : fermer" : j() + " ou Échap : fermer";
        }
        return switch (s.sous) {
            case "grades", "creer", "tenue", "form" -> "Entrée : valider   ·   Échap : retour";
            default -> "Échap : retour";
        };
    }

    private static String j() { return fr.reborn.hud.keybind.HudKeybinds.nom(fr.reborn.hud.keybind.HudKeybinds.FACTION, "J"); }

    /* =================================================================== aiguillage */

    int corps(JsonObject v, String tab, int y) {
        if (s.sous != null) {
            String so = s.sous;
            if (so.equals("form")) return form(v, y);
            if (so.equals("inviter")) return inviter(v, y);
            if (so.equals("grades")) return grades(v, y);
            if (so.equals("lieux")) return lieux(v, y);
            if (so.equals("journal")) return journal(v, y, false);
            if (so.equals("journal_faction")) return journal(v, y, true);
            if (so.equals("tenue")) return tenue(v, y);
            if (so.equals("creer")) return creer(v, y);
            if (so.equals("charges")) return charges(v, y);
            if (so.startsWith("charge:")) return charge(v, y, so.substring(7));
            if (so.startsWith("section:")) return sectionDetail(v, y, so.substring(8));
        }
        return switch (tab) {
            case "section" -> section(v, y);
            case "sections" -> sections(v, y);
            case "annonces" -> annonces(v, y);
            case "gestion" -> gestion(v, y);
            default -> village(v, y);
        };
    }

    /* =================================================================== pièces */

    private int ix() { return s.ix(); }

    private int iw() { return s.iw(); }

    /** « ‹ Police » : retour à la page. */
    private int retour(int y, String label) {
        boolean hov = s.hover(ix() - 3, y - 2, Math.round(s.pz.largeur(label, 13, false)) + 14, 12) && s.cliquable(y - 2, 12);
        s.pz.icone("gauche", ix() - 2, y + 0.5f, 8, hov ? CREME : GRIS);
        s.pz.texte(label, ix() + 7, y + 4.5f, 13, hov ? CREME : GRIS, false);
        if (s.cliquable(y - 2, 12)) s.hits.add(new FactionScreen.Hit(ix() - 3, y - 2, Math.round(s.pz.largeur(label, 13, false)) + 14, 12, s::retour, null));
        return y + 16;
    }

    /** En-tête de bloc en petites capitales, et une valeur à droite. */
    private void caps(int y, String t, String droite, int colDroite) {
        s.pz.capitales(t, ix(), y + 3, 10, GRIS_2);
        if (droite != null) s.pz.texteD(droite, ix() + iw(), y + 3, 11, colDroite, false);
    }

    private void filet(int y) { s.pz.filetH(ix(), ix() + iw(), y + 0.5f, FILET); }

    /** Ligne repliée (maquettes : icône, titre, compteur or, aperçu, chevron) ; 25 GUI. */
    private int replie(int y, String icone, String label, String n, String apercu, Runnable r) {
        int h = 25;
        boolean hov = r != null && s.hover(s.px0() + 1, y, FactionScreen.PW - 2, h) && s.cliquable(y, h);
        if (hov) s.pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
        s.pz.icone(icone, ix(), y + 7, 8, hov ? OR : GRIS);
        float lw = s.txt(label, ix() + 13, y + 7.5f, iw() - 40, 14, CREME, false);
        if (n != null && !n.isEmpty()) s.pz.texte(n, ix() + 16 + lw, y + 7.5f, 12, OR, true);
        s.txt(apercu, ix() + 13, y + 17, iw() - 26, 12, GRIS_2, false);
        s.pz.icone("droite", ix() + iw() - 7, y + 8.5f, 7, GRIS_2);
        if (r != null && s.cliquable(y, h)) s.hits.add(new FactionScreen.Hit(s.px0() + 1, y, FactionScreen.PW - 2, h, () -> { r.run(); }, null));
        return y + h;
    }

    private static String sans(String t) { return DaKit.sansAccents(t == null ? "" : t).toLowerCase(Locale.ROOT); }

    private static String pluriel(int n, String mot) { return n + " " + mot + (n > 1 ? "s" : ""); }

    private static JsonObject section(JsonObject v) { return obj(v, "section"); }

    private static Set<String> set(JsonArray a) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    private void envoyer(String a, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("a", a);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object val = kv[i + 1];
            if (val instanceof Number n) o.addProperty((String) kv[i], n);
            else if (val instanceof Boolean b) o.addProperty((String) kv[i], b);
            else if (val instanceof JsonArray ja) o.add((String) kv[i], ja);
            else o.addProperty((String) kv[i], String.valueOf(val));
        }
        FactionClient.send(o);
    }

    /* =================================================================== Village (maquette 01, à droite) */

    private int village(JsonObject v, int y) {
        JsonObject f = obj(v, "faction");
        JsonObject vil = obj(v, "village");
        boolean orga = "organisation".equals(str(f, "type"));
        s.pz.texte((orga ? "Organisation" : "Village caché") + " · " + pluriel(num(vil, "total"), "membre"), ix(), y + 4, 13, GRIS, false);
        y += 13;
        s.champ(recherche, ix(), y, iw(), 15, "chercher", null);
        y += 22;
        String q = sans(recherche.getValue().trim());
        boolean filtre = !q.isEmpty();
        int vus = 0;
        for (JsonElement ge : arr(vil, "groupes")) {
            JsonObject g = ge.getAsJsonObject();
            JsonArray connus = arr(g, "connus");
            List<JsonObject> l = new ArrayList<>();
            for (JsonElement e : connus) {
                JsonObject p = e.getAsJsonObject();
                if (!filtre || sans(str(p, "nom")).contains(q)) l.add(p);
            }
            int total = num(g, "total"), inc = num(g, "inconnus");
            if (filtre && l.isEmpty()) continue;
            s.pz.capitales(str(g, "nom"), ix(), y + 3, 10, GRIS_2);
            if (!filtre) s.pz.texteD(connus.size() + " connu" + (connus.size() > 1 ? "s" : "") + " sur " + total, ix() + iw(), y + 3, 11, GRIS_2, false);
            y += 9;
            for (JsonObject p : l) {
                boolean on = bool(p, "online");
                s.tete(p, ix(), y + 1, 12);
                boolean moi = bool(p, "moi");
                float lw = s.txt(str(p, "nom"), ix() + 17, y + 7, iw() - 80, 14, on ? CREME : GRIS, moi);
                String note = bool(p, "publique") ? "figure publique" : moi ? "toi" : "";
                if (!note.isEmpty()) s.pz.texte(note, ix() + 21 + lw, y + 7, 12, bool(p, "publique") ? OR_CLAIR : GRIS_2, false);
                s.pz.pastille(ix() + iw() - 31, y + 7, 1.5f, on ? OK : GRIS_3);
                s.pz.texte(on ? "en ligne" : "absent", ix() + iw() - 27, y + 7, 12, on ? GRIS : GRIS_2, false);
                y += 15;
                vus++;
            }
            if (!filtre && inc > 0) {
                s.pz.texte(l.isEmpty() ? inc + " que tu ne connais pas" : "+ " + inc + " que tu ne connais pas", l.isEmpty() ? ix() : ix() + 17, y + 4.5f, 12, GRIS_3, false);
                y += 11;
            }
            y += 4;
        }
        if (filtre && vus == 0) y = s.vide(y, "Personne de ce nom parmi ceux que tu connais.", "Les autres membres ne sont jamais nommés : présente-toi d'abord (menu R).");
        // dernière annonce
        JsonArray an = arr(v, "annonces");
        if (!an.isEmpty() && !filtre) {
            y += 3;
            filet(y);
            y += 7;
            JsonObject a = an.get(0).getAsJsonObject();
            caps(y, "Dernière annonce", depuis(lng(a, "le")), GRIS_2);
            y += 11;
            s.pz.icone("cloche", ix(), y + 0.5f, 7, OR);
            List<String> lignes = s.wrap(str(a, "texte"), iw() - 11, 13);
            for (int i = 0; i < Math.min(2, lignes.size()); i++) {
                String l = lignes.get(i);
                if (i == 1 && lignes.size() > 2) l = s.fit(l + "…", iw() - 11, 13, false);
                s.pz.texte(l, ix() + 11, y + 4 + i * 10, 13, CREME, false);
            }
            y += Math.min(2, lignes.size()) * 10;
            String auteur = str(a, "auteur").isEmpty() ? str(a, "titre") : str(a, "auteur") + ", " + str(a, "titre");
            s.txt("— " + auteur, ix() + 11, y + 5, iw() - 50, 12, GRIS, false);
            s.lien("toutes ›", ix() + iw(), y + 5, 12, OR, true, true, () -> s.changerOnglet("annonces"));
            y += 12;
        }
        return y + 4;
    }

    /* =================================================================== Ma section (maquettes 01 et 02) */

    private int section(JsonObject v, int y) {
        JsonObject sec = section(v);
        if (sec == null) return s.vide(y, "Tu n'es dans aucune section.", null);
        boolean chef = bool(sec, "chef");
        boolean chefF = bool(obj(v, "moi"), "chefFaction");
        s.rondInsigne(ix() + 9, y + 10, 9, str(sec, "insigne"), true);
        s.txt(str(sec, "nom"), ix() + 24, y + 5.5f, chef ? iw() - 84 : iw() - 26, 17, CREME, true);
        if (chef) {
            s.txt("Chef de section : toi · " + str(sec, "monGrade"), ix() + 24, y + 15.5f, iw() - 70, 12, OR_CLAIR, false);
            // places fixées par le chef
            s.pz.texteD(ryo(num(sec, "n")) + " pris", ix() + iw(), y + 5.5f, 12, GRIS_2, false);
            float pw = s.pz.largeur(ryo(num(sec, "n")) + " pris", 12, false);
            int fx = Math.round(ix() + iw() - pw - 4 - 20);
            if (!places.isFocused() && !places.getValue().equals(String.valueOf(num(sec, "places")))) places.setValue(String.valueOf(num(sec, "places")));
            s.champ(places, fx, y - 1, 20, 13, null, null);
            s.pz.texteD("Places", fx - 4, y + 5.5f, 12, GRIS, false);
            s.pz.texteD("fixées par toi", ix() + iw(), y + 16, 11, GRIS_3, false);
        } else {
            s.txt("Ton grade de section : " + str(sec, "monGrade"), ix() + 24, y + 15.5f, iw() - 26, 12, GRIS, false);
        }
        y += 27;
        filet(y);
        y += 7;
        JsonArray ms = arr(sec, "membres");
        Set<String> droits = set(arr(sec, "droits"));
        if (chef) caps(y, "Membres · " + ms.size(), null, 0);
        else caps(y, "Membres", String.valueOf(ms.size()), GRIS_2);
        if (droits.contains("inviter") || chef) {
            boolean pleine = num(sec, "n") >= num(sec, "places");
            if (pleine) s.pz.texteD("complet", ix() + iw(), y + 3, 12, GRIS_3, false);
            else s.lien("+ Inviter", ix() + iw(), y + 3, 13, OR, true, true, () -> s.ouvrirSous("inviter"));
        }
        y += 11;
        boolean gere = chef || chefF || droits.contains("monter") || droits.contains("exclure");
        int monRang = num(sec, "monRang");
        for (JsonElement e : ms) {
            JsonObject p = e.getAsJsonObject();
            int h = 15;
            boolean geree = gere && bool(p, "connu") && !bool(p, "moi") && !bool(p, "chef") && (chef || chefF || num(p, "rang") > monRang);
            boolean hov = geree && s.menu == null && s.hover(s.px0() + 1, y - 1, FactionScreen.PW - 2, h) && s.cliquable(y, h);
            if (hov) {
                s.pz.fond(ix() - 4, y - 1, ix() + iw() + 4, y + h - 1, NUIT_3);
                s.pz.fond(ix() - 4, y - 1, ix() - 3, y + h - 1, OR);
            }
            boolean on = bool(p, "online"), connu = bool(p, "connu"), moi = bool(p, "moi");
            s.tete(p, ix(), y + 1, 12);
            float lw = s.txt(str(p, "nom"), ix() + 17, y + 7, iw() - 90, 14, connu && (on || moi) ? CREME : GRIS, moi);
            boolean estChef = bool(p, "chef");
            String g = str(p, "grade") + (estChef && !chef ? " · chef" : "");
            s.txt(g, ix() + 21 + lw, y + 7, iw() - 30 - lw - 14, 12, estChef ? OR_CLAIR : GRIS_2, false);
            if (hov) s.pz.texteD("Gérer ›", ix() + iw(), y + 7, 12, OR, true);
            else if (connu) s.pz.pastille(ix() + iw() - 4, y + 7, 1.5f, on || moi ? OK : GRIS_3);
            if (geree && s.cliquable(y, h)) {
                final int yy = y;
                s.hits.add(new FactionScreen.Hit(s.px0() + 1, y - 1, FactionScreen.PW - 2, h, () -> fiche(v, p, yy), null));
            }
            y += h;
        }
        y += 2;
        filet(y);
        y += 7;
        // tenue de la section
        JsonObject t = obj(sec, "tenue");
        caps(y, "Tenue de la section", null, 0);
        if (chef) s.lien("Changer", ix() + iw(), y + 3, 12, OR, true, true, () -> s.ouvrirSous("tenue"));
        y += 10;
        String tid = str(t, "id");
        s.pz.fond(ix(), y, ix() + 36, y + 48, NUIT_2);
        s.pz.cadre(ix(), y, 36, 48, FILET_2);
        if (!tid.isEmpty()) s.pz.poupee(FactionArt.tenue(tid), ix() + 10, y + 7, 1f);
        float tx = ix() + 43;
        if (tid.isEmpty()) {
            s.pz.texte("Aucune tenue de section", tx, y + 7, 14, GRIS, false);
            s.pz.texte(chef ? "Choisis-en une parmi les tenues" : "Le chef de section peut en", tx, y + 16.5f, 12, GRIS_2, false);
            s.pz.texte(chef ? "existantes : « Changer »." : "choisir une ; elle te sera prêtée.", tx, y + 25.5f, 12, GRIS_2, false);
        } else if (chef) {
            s.txt(str(t, "nom").isEmpty() ? "Tenue de la section" : str(t, "nom"), tx, y + 6, iw() - 43, 14, CREME, false);
            s.txt("tenue " + tid + " · prêtée aux membres", tx, y + 15.5f, iw() - 43, 12, GRIS, false);
            String acc = str(t, "accessoire");
            s.txt(acc.isEmpty() ? "Aucun accessoire" : "Accessoire : " + acc, tx, y + 27, iw() - 43, 12, GRIS, false);
            s.pz.texte("objet Nexo, remis depuis le coffre", tx, y + 36, 12, GRIS_2, false);
        } else {
            s.txt(str(t, "nom").isEmpty() ? "Tenue de la section" : str(t, "nom"), tx, y + 7, iw() - 43, 14, CREME, false);
            s.pz.texte("Prêtée tant que tu es membre :", tx, y + 17, 12, GRIS, false);
            s.pz.texte("sacoche › Cosmétique › Tenue.", tx, y + 26, 12, GRIS, false);
            String qui = bool(sec, "chefFem") ? "la cheffe" : "le chef";
            s.pz.texte("Choisie par " + qui + " de section", tx, y + 39, 12, GRIS_2, false);
        }
        y += 54;
        // caisse
        JsonObject caisse = obj(sec, "caisse");
        if (caisse != null) {
            filet(y);
            y += 7;
            s.pz.capitales("Caisse de la section", ix(), y + 3, 10, GRIS_2);
            s.pz.texteD(ryo(lng(caisse, "solde")) + " ryo", ix() + iw(), y + 3, 14, OR_CLAIR, true);
            y += 11;
            s.pz.texte("Taxe du village : " + num(caisse, "taux") + " % de chaque entrée", ix(), y + 4, 12, GRIS, false);
            y += 12;
            for (JsonElement e : arr(caisse, "lignes")) {
                JsonObject l = e.getAsJsonObject();
                long n = lng(l, "n");
                String lib = str(l, "t");
                boolean taxe = lib.startsWith("Taxe");
                if (!taxe) lib = lib + " · " + date(lng(l, "le"));
                s.txt(lib, ix(), y + 4, iw() - 40, 12, GRIS, false);
                s.pz.texteD((n >= 0 ? "+ " : "− ") + ryo(Math.abs(n)), ix() + iw(), y + 4, 12, taxe ? GRIS : n >= 0 ? OK : ROUGE, true);
                y += 10;
            }
            s.pz.texte("On dépense au guichet du village, avec un motif.", ix(), y + 5, 12, GRIS_2, false);
            y += 15;
        }
        filet(y);
        y += 2;
        JsonArray gr = arr(sec, "grades");
        if (chef || chefF) {
            List<String> noms = new ArrayList<>();
            for (JsonElement e : gr) noms.add(str(e.getAsJsonObject(), "nom"));
            y = replie(y, "drapeau", "Grades de la section", String.valueOf(gr.size()), String.join(" · ", noms), () -> s.ouvrirSous("grades"));
            JsonArray lx = arr(sec, "lieux");
            List<String> ln = new ArrayList<>();
            for (JsonElement e : lx) ln.add(str(e.getAsJsonObject(), "nom"));
            y = replie(y, "verrou", "Lieux de la section", lx.isEmpty() ? "" : String.valueOf(lx.size()),
                    lx.isEmpty() ? "Posés par le staff (portes, coffres)" : String.join(", ", ln), () -> s.ouvrirSous("lieux"));
        } else {
            // tes accès (lieux posés par le staff)
            y += 5;
            caps(y, "Tes accès", null, 0);
            y += 11;
            JsonArray lx = arr(sec, "lieux");
            if (lx.isEmpty()) {
                for (String l : s.wrap("Aucun lieu pour l'instant : les portes et coffres de la section sont posés par le staff.", iw(), 12)) {
                    s.pz.texte(l, ix(), y + 4, 12, GRIS_2, false);
                    y += 9;
                }
                y += 3;
            }
            for (JsonElement e : lx) {
                JsonObject l = e.getAsJsonObject();
                boolean coffre = "coffre".equals(str(l, "type"));
                s.pz.icone(coffre ? "sac" : "verrou", ix(), y, 8, GRIS);
                s.txt(str(l, "nom"), ix() + 13, y + 4, iw() - 80, 13, CREME, false);
                s.pz.texteD(coffre ? "coffre : " + str(l, "acces") : "porte", ix() + iw(), y + 4, 12, GRIS_2, false);
                y += 13;
            }
        }
        JsonArray jo = arr(sec, "journal");
        if (sec.has("journal")) {
            String last = jo.isEmpty() ? "Rien pour l'instant" : str(jo.get(0).getAsJsonObject(), "t") + " · " + date(lng(jo.get(0).getAsJsonObject(), "le"));
            y = replie(y, "journal", "Journal de la section", "", last, () -> { journalFiltre = null; s.ouvrirSous("journal"); });
        }
        if (!chef) {
            y += 4;
            s.lien("Quitter la section", ix(), y + 4, 12, GRIS_2, false, false, () -> form("quitter", null, null, "Quitter la section",
                    "Tu quittes « " + str(sec, "nom") + " ». La tenue prêtée te sera reprise ; ton grade du village ne change pas."));
            y += 10;
        }
        return y + 4;
    }

    /** Fiche d'un membre (maquette 04, encart) : monter, descendre, tenue prêtée, journal, exclure. */
    private void fiche(JsonObject v, JsonObject p, int y) {
        JsonObject sec = section(v);
        boolean chef = bool(sec, "chef"), chefF = bool(obj(v, "moi"), "chefFaction");
        Set<String> droits = set(arr(sec, "droits"));
        JsonArray gr = arr(sec, "grades");
        int monRang = chef ? 0 : num(sec, "monRang"), r = num(p, "rang");
        boolean peutMonter = chef || chefF || droits.contains("monter");
        String id = str(p, "id");
        List<FactionScreen.Item> items = new ArrayList<>();
        int haut = r - 1, bas = r + 1;
        boolean montable = peutMonter && haut >= 1 && (chef || chefF || haut > monRang);
        String nomHaut = haut >= 0 && haut < gr.size() ? "→ " + str(gr.get(haut).getAsJsonObject(), "nom") : "";
        String nomBas = bas < gr.size() ? "→ " + str(gr.get(bas).getAsJsonObject(), "nom") : "dernier grade";
        items.add(new FactionScreen.Item("Monter en grade", montable ? nomHaut : haut <= monRang ? "ton grade" : nomHaut, !montable, false,
                () -> envoyer("monter", "p", id)));
        boolean descendable = peutMonter && bas < Math.max(gr.size(), r + 2) && (gr.isEmpty() || bas < gr.size());
        items.add(new FactionScreen.Item("Descendre en grade", nomBas, !descendable, false, () -> envoyer("descendre", "p", id)));
        items.add(new FactionScreen.Item("Changer la tenue prêtée", chef ? "" : "le chef", !chef, false, () -> s.ouvrirSous("tenue")));
        boolean journal = sec.has("journal");
        items.add(new FactionScreen.Item("Journal du membre", "", !journal, false, () -> { journalFiltre = str(p, "nom"); s.ouvrirSous("journal"); }));
        boolean excl = chef || chefF || droits.contains("exclure");
        items.add(new FactionScreen.Item("Exclure de la section", "motif facultatif", !excl, false,
                () -> form("exclure", id, str(p, "nom"), "Exclure " + str(p, "nom"), "Il ou elle quitte la section ; une ligne lui est envoyée et tout va au journal de la section.")));
        JsonObject tete = p.deepCopy();
        String rv = str(p, "rangVillage");
        tete.addProperty("ligne", (rv.isEmpty() ? "" : rv + " · ") + str(p, "grade") + " · " + (bool(p, "online") ? "en ligne" : "absent"));
        s.ouvrirMenu(items, y, tete, "Le grade du village ne se change pas ici : /grade.", 150);
    }

    /* =================================================================== Inviter (maquette 02, encart) */

    private int inviter(JsonObject v, int y) {
        JsonObject sec = section(v);
        String nomSec = str(sec, "nom");
        y = retour(y, nomSec);
        JsonObject pr = FactionClient.proches();
        s.pz.capitales("Inviter dans " + FactionClient.article(nomSec), ix(), y + 3, 11, OR);
        int n = pr != null && pr.has("n") ? num(pr, "n") : num(sec, "n"), pl = pr != null && pr.has("places") ? num(pr, "places") : num(sec, "places");
        s.pz.texteD(n + " / " + pl + " places", ix() + iw(), y + 3, 12, GRIS_2, false);
        y += 12;
        s.pz.texte("Personnes que tu connais, à 30 blocs", ix(), y + 4, 12, GRIS, false);
        y += 11;
        JsonArray pres = arr(pr, "pres");
        if (pr == null) { s.pz.texte("Recherche des personnes proches…", ix(), y + 6, 12, GRIS_2, false); y += 14; }
        else if (pres.isEmpty()) { s.pz.texte("Personne que tu connais n'est à 30 blocs.", ix(), y + 6, 12, GRIS_2, false); y += 14; }
        for (JsonElement e : pres) y = lignePersonne(e.getAsJsonObject(), y, true);
        y += 3;
        filet(y);
        y += 6;
        s.pz.texte("En ligne, plus loin", ix(), y + 4, 12, GRIS, false);
        y += 11;
        if (pr != null && bool(pr, "distance")) {
            JsonArray loin = arr(pr, "loin");
            if (loin.isEmpty()) { s.pz.texte("Personne d'autre en ligne parmi ceux que tu connais.", ix(), y + 4, 12, GRIS_2, false); y += 12; }
            for (JsonElement e : loin) y = lignePersonne(e.getAsJsonObject(), y, false);
        } else {
            s.pz.icone("verrou", ix(), y, 7, GRIS_3);
            s.pz.texte("Réservé aux grades « inviter à distance »", ix() + 11, y + 3.5f, 12, GRIS_2, false);
            y += 15;
        }
        filet(y);
        y += 6;
        s.pz.texte("Un inconnu n'est pas dans la liste :", ix(), y + 4, 12, GRIS_2, false);
        s.pz.texte("présente-toi d'abord (menu R), ou invite face à face.", ix(), y + 13, 12, GRIS_2, false);
        y += 23;
        if (envoye != null && System.currentTimeMillis() - envoyeA < 30_000) {
            s.pz.pastille(ix() + 2, y + 4, 2, OK);
            s.txt(envoye, ix() + 7, y + 4, iw() - 7, 12, OK, false);
            y += 12;
        }
        return y + 4;
    }

    private int lignePersonne(JsonObject p, int y, boolean pres) {
        String refus = str(p, "refus");
        boolean ok = refus.isEmpty();
        JsonObject t = p.deepCopy();
        t.addProperty("connu", true);
        t.addProperty("online", ok);
        s.tete(t, ix(), y + 1, 12);
        float lw = s.txt(str(p, "nom"), ix() + 17, y + 7, iw() - 100, 14, ok ? CREME : GRIS_2, false);
        s.txt(ok ? str(p, "rang") : refus, ix() + 21 + lw, y + 7, iw() - 60 - lw, 12, GRIS_2, false);
        if (ok) {
            int w = Math.round(s.pz.largeur("Inviter", 12, true)) + 9;
            int bx = ix() + iw() - w, by = y + 1;
            boolean hov = s.hover(bx, by, w, 11) && s.cliquable(by, 11);
            s.pz.fond(bx, by, bx + w, by + 11, hov ? 0xFF5E1824 : LAQUE_SOMBRE);
            s.pz.cadre(bx, by, w, 11, hov ? OR : OR_DOUX);
            s.pz.texteC("Inviter", bx + w / 2f, by + 5.5f, 12, CREME, true);
            if (s.cliquable(by, 11)) s.hits.add(new FactionScreen.Hit(bx, by, w, 11, () -> {
                envoyer("inviter", "p", str(p, "id"));
                envoye = "Invitation envoyée à " + str(p, "nom") + " · 30 s";
                envoyeA = System.currentTimeMillis();
                RebornSounds.confirm();
            }, null));
        } else if (p.has("dist")) s.pz.texteD(num(p, "dist") + " blocs", ix() + iw(), y + 7, 12, GRIS_3, false);
        return y + 16;
    }

    /* =================================================================== Grades de la section (maquette 04) */

    private int grades(JsonObject v, int y) {
        JsonObject sec = section(v);
        y = retour(y, str(sec, "nom"));
        JsonArray gr = arr(sec, "grades");
        s.pz.capitales("Grades de la section", ix(), y + 3, 11, OR);
        s.pz.texteD("ordre = autorité", ix() + iw(), y + 3, 11, GRIS_2, false);
        y += 11;
        if (gradeSel == null || gradeIndex(gr, gradeSel) < 0) {
            gradeSel = gr.size() > 1 ? str(gr.get(1).getAsJsonObject(), "id") : gr.isEmpty() ? null : str(gr.get(0).getAsJsonObject(), "id");
            chargerDroits(gr);
        }
        for (int i = 0; i < gr.size(); i++) {
            JsonObject g = gr.get(i).getAsJsonObject();
            String id = str(g, "id");
            boolean sel = id.equals(gradeSel);
            int h = 21;
            boolean hov = !sel && s.hover(s.px0() + 1, y, FactionScreen.PW - 2, h) && s.cliquable(y, h) && s.menu == null;
            if (sel || hov) s.pz.fond(ix() - 4, y, ix() + iw() + 4, y + h, sel ? NUIT_3 : NUIT_2);
            if (sel) s.pz.fond(ix() - 4, y, ix() - 3, y + h, OR);
            s.pz.texte(String.valueOf(i + 1), ix() + 2, y + 10, 12, GRIS_2, false);
            s.txt(str(g, "nom"), ix() + 12, y + 6.5f, iw() - 30, 14, CREME, sel);
            int n = num(g, "n");
            String sous = i == 0 ? n + " · toi" : pluriel(n, "membre") + (i == gr.size() - 1 ? " · par défaut" : "");
            if (i == gr.size() - 1 && n > 0) sous = n + " · par défaut";
            s.pz.texte(sous, ix() + 12, y + 15, 11, GRIS_2, false);
            if (i == 0) s.pz.icone("verrou", ix() + iw() - 9, y + 6, 7, GRIS_3);
            else {
                for (int k = 0; k < 3; k++) s.pz.pastille(ix() + iw() - 5, y + 7 + k * 3, 0.7f, GRIS_2);
                final int fi = i, yy = y;
                s.hits.add(new FactionScreen.Hit(ix() + iw() - 12, y, 14, h, () -> menuGrade(gr, fi, yy), null));
            }
            if (s.cliquable(y, h)) s.hits.add(new FactionScreen.Hit(s.px0() + 1, y, FactionScreen.PW - 16, h, () -> {
                if (!id.equals(gradeSel)) { gradeSel = id; chargerDroits(gr); RebornSounds.uiClick(); }
            }, null));
            y += h;
        }
        s.lien("+ Nouveau grade", ix() + 12, y + 6, 13, OR, true, false,
                () -> form("grade_nouveau", null, null, "Nouveau grade", "Il se place juste au-dessus du dernier, qui reste celui des nouveaux."));
        y += 16;
        filet(y);
        y += 8;
        int gi = gradeIndex(gr, gradeSel);
        if (gi < 0) return y + 4;
        JsonObject g = gr.get(gi).getAsJsonObject();
        s.txt(str(g, "nom"), ix(), y + 5, iw() - 90, 18, CREME, true);
        if (gi > 0) {
            float xx = ix() + iw();
            xx -= s.lien("Supprimer", xx, y + 5, 12, GRIS, false, true, () -> form("grade_suppr", str(g, "id"), str(g, "nom"), "Supprimer « " + str(g, "nom") + " » ?",
                    "Ses membres passent au dernier grade. Le changement va au journal de la section.")) + 7;
            s.lien("Renommer", xx, y + 5, 12, GRIS, false, true, () -> form("grade_renommer", str(g, "id"), str(g, "nom"), "Renommer « " + str(g, "nom") + " »", null));
        }
        y += 15;
        caps(y, "Droits", null, 0);
        y += 11;
        boolean chef = bool(sec, "chef") || bool(obj(s.vue(), "moi"), "chefFaction");
        Set<String> miens = set(arr(sec, "droits"));
        for (String[] d : DROITS_SECTION) {
            boolean c = gi == 0 || droitsEdit.contains(d[0]);
            boolean eteint = gi == 0 || !chef && !miens.contains(d[0]);
            s.caseCocher(ix(), y + 0.5f, c, eteint);
            s.pz.texte(d[1], ix() + 12, y + 4, 13, eteint ? GRIS_3 : c ? CREME : GRIS, false);
            if (!eteint && s.cliquable(y - 2, 13)) s.hits.add(new FactionScreen.Hit(ix() - 2, y - 2, iw() + 4, 13, () -> {
                if (!droitsEdit.remove(d[0])) droitsEdit.add(d[0]);
                droitsSales = true;
                RebornSounds.uiClick();
            }, null));
            y += 14;
        }
        y += 3;
        s.pz.texte("Tu ne donnes que des droits que tu as toi-même.", ix(), y + 4, 12, GRIS_2, false);
        y += 11;
        s.pz.texte("Jamais de stat : un grade de section donne des droits.", ix(), y + 4, 12, GRIS_2, false);
        y += 16;
        int bw = s.bouton(ix(), y, 85, 15, "Enregistrer · Entrée", this::enregistrerDroits, gi == 0);
        s.lien("Annuler", ix() + bw + 9, y + 7.5f, 13, GRIS, false, false, () -> { chargerDroits(gr); });
        return y + 22;
    }

    private static int gradeIndex(JsonArray gr, String id) {
        for (int i = 0; i < gr.size(); i++) if (str(gr.get(i).getAsJsonObject(), "id").equals(id)) return i;
        return -1;
    }

    private void chargerDroits(JsonArray gr) {
        droitsEdit.clear();
        int i = gradeIndex(gr, gradeSel);
        if (i >= 0) droitsEdit.addAll(set(arr(gr.get(i).getAsJsonObject(), "droits")));
        droitsSales = false;
    }

    private void enregistrerDroits() {
        if (gradeSel == null || !droitsSales) return;
        JsonArray d = new JsonArray();
        for (String x : droitsEdit) d.add(x);
        envoyer("grade_droits", "id", gradeSel, "droits", d);
        droitsSales = false;
        RebornSounds.confirm();
    }

    private void menuGrade(JsonArray gr, int i, int y) {
        JsonObject g = gr.get(i).getAsJsonObject();
        String id = str(g, "id");
        List<FactionScreen.Item> items = new ArrayList<>();
        items.add(new FactionScreen.Item("Monter d'un cran", "", i <= 1, false, () -> envoyer("grade_ordre", "id", id, "sens", -1)));
        items.add(new FactionScreen.Item("Descendre d'un cran", "", i >= gr.size() - 1, false, () -> envoyer("grade_ordre", "id", id, "sens", 1)));
        items.add(null);
        items.add(new FactionScreen.Item("Renommer…", "", false, false, () -> form("grade_renommer", id, str(g, "nom"), "Renommer « " + str(g, "nom") + " »", null)));
        items.add(new FactionScreen.Item("Supprimer…", "", gr.size() <= 2, true, () -> form("grade_suppr", id, str(g, "nom"),
                "Supprimer « " + str(g, "nom") + " » ?", "Ses membres passent au dernier grade.")));
        s.ouvrirMenu(items, y, null, null, 130);
    }

    /* =================================================================== Lieux, journaux */

    private int lieux(JsonObject v, int y) {
        JsonObject sec = section(v);
        y = retour(y, str(sec, "nom"));
        s.pz.capitales("Lieux de la section", ix(), y + 3, 11, OR);
        y += 13;
        JsonArray lx = arr(sec, "lieux");
        if (lx.isEmpty()) {
            for (String l : s.wrap("Aucun lieu pour l'instant. Les portes et les coffres de la section sont posés par le staff (Poste de garde) : on vise le bloc, puis on choisit les grades qui y ont accès.", iw(), 12)) {
                s.pz.texte(l, ix(), y + 4, 12, GRIS_2, false);
                y += 9;
            }
            return y + 6;
        }
        for (JsonElement e : lx) {
            JsonObject l = e.getAsJsonObject();
            boolean coffre = "coffre".equals(str(l, "type"));
            s.pz.icone(coffre ? "sac" : "verrou", ix(), y, 8, GRIS);
            s.txt(str(l, "nom"), ix() + 13, y + 4, iw() - 80, 13, CREME, false);
            s.pz.texteD(coffre ? "coffre" : "porte", ix() + iw(), y + 4, 12, GRIS_2, false);
            y += 13;
        }
        return y + 4;
    }

    private int journal(JsonObject v, int y, boolean faction) {
        JsonArray jo;
        if (faction) {
            y = retour(y, "Gestion");
            jo = arr(obj(v, "gestion"), "journal");
            s.pz.capitales("Journal de la faction", ix(), y + 3, 11, OR);
        } else {
            JsonObject sec = section(v);
            y = retour(y, str(sec, "nom"));
            jo = arr(sec, "journal");
            s.pz.capitales(journalFiltre != null ? "Journal · " + journalFiltre : "Journal de la section", ix(), y + 3, 11, OR);
        }
        s.pz.texteD("en ajout seul", ix() + iw(), y + 3, 11, GRIS_2, false);
        y += 13;
        int n = 0;
        for (JsonElement e : jo) {
            JsonObject l = e.getAsJsonObject();
            String t = str(l, "t");
            if (journalFiltre != null && !sans(t).contains(sans(journalFiltre))) continue;
            n++;
            s.pz.texte(date(lng(l, "le")), ix(), y + 4, 11, GRIS_3, false);
            List<String> ls = s.wrap(t, iw() - 26, 12);
            for (int i = 0; i < ls.size(); i++) s.pz.texte(ls.get(i), ix() + 26, y + 4 + i * 9, 12, GRIS, false);
            y += Math.max(1, ls.size()) * 9 + 4;
            s.pz.filetH(ix() + 26, ix() + iw(), y - 1.5f, SEP);
        }
        if (n == 0) y = s.vide(y, "Rien au journal.", journalFiltre != null ? "Aucune ligne pour " + journalFiltre + "." : null);
        return y + 4;
    }

    /* =================================================================== Tenue de la section (chef) */

    private int tenue(JsonObject v, int y) {
        JsonObject sec = section(v);
        y = retour(y, str(sec, "nom"));
        s.pz.capitales("Tenue de la section", ix(), y + 3, 11, OR);
        y += 12;
        for (String l : s.wrap("Choisis une tenue existante : elle est prêtée à chaque membre tant qu'il l'est (sacoche › Cosmétique › Tenue). La porter n'est pas obligatoire.", iw(), 12)) {
            s.pz.texte(l, ix(), y + 4, 12, GRIS_2, false);
            y += 9;
        }
        y += 4;
        s.pz.texte("Accessoire", ix(), y + 7.5f, 12, GRIS, false);
        s.champ(accessoire, ix() + 46, y, iw() - 46, 15, null, null);
        y += 21;
        int bw = s.bouton(ix(), y, 85, 15, "Enregistrer · Entrée", this::enregistrerTenue, false);
        s.lien("Retirer la tenue", ix() + bw + 9, y + 7.5f, 13, GRIS, false, false, () -> { tenueSel = ""; enregistrerTenue(); });
        y += 22;
        filet(y);
        y += 6;
        List<CharacterCatalog.Asset> l = CharacterCatalog.all("outfit");
        int cols = 4, gap = 3;
        int cw = (iw() - gap * (cols - 1)) / cols, ch = 58;
        for (int i = 0; i < l.size(); i++) {
            CharacterCatalog.Asset a = l.get(i);
            int cx = ix() + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
            boolean sel = a.id.equals(tenueSel);
            boolean visible = cy + ch > s.vy0() && cy < s.vy1();
            boolean hov = visible && s.hover(cx, cy, cw, ch);
            s.pz.fond(cx, cy, cx + cw, cy + ch, sel ? NUIT_3 : NUIT_2);
            s.pz.cadre(cx, cy, cw, ch, sel ? OR : hov ? OR_DOUX : FILET);
            if (visible) s.pz.poupee(FactionArt.tenue(a.id), cx + cw / 2f - 8, cy + 4, 1f);
            s.pz.texteC(s.fit(a.name, cw - 4, 11, false), cx + cw / 2f, cy + 51, 11, sel ? OR_CLAIR : GRIS, false);
            if (visible) s.hits.add(new FactionScreen.Hit(cx, cy, cw, ch, () -> { tenueSel = a.id; RebornSounds.uiClick(); }, null));
        }
        y += ((l.size() + cols - 1) / cols) * (ch + gap);
        return y + 4;
    }

    private void enregistrerTenue() {
        String nomT = "";
        if (tenueSel != null && !tenueSel.isEmpty()) {
            for (CharacterCatalog.Asset a : CharacterCatalog.all("outfit")) if (a.id.equals(tenueSel)) nomT = a.name;
        }
        envoyer("tenue", "id", tenueSel == null ? "" : tenueSel, "nom", nomT, "acc", accessoire.getValue().trim());
        RebornSounds.confirm();
        s.retour();
    }

    /* =================================================================== Sections (maquette 05) */

    private int sections(JsonObject v, int y) {
        JsonObject moi = obj(v, "moi");
        s.txt(str(moi, "nom") + " · " + (bool(moi, "chefFaction") ? str(moi, "titre") : str(moi, "rangNom")) + " · tu vois les sections", ix(), y + 4, iw(), 13, GRIS, false);
        y += 15;
        JsonArray ss = arr(v, "sections");
        if (ss.isEmpty()) y = s.vide(y, "Aucune section pour l'instant.", "Le chef de faction et le staff créent les sections.");
        float w = (iw() - 6) / 2f;
        int hh = 65;
        for (int i = 0; i < ss.size(); i++) {
            JsonObject t = ss.get(i).getAsJsonObject();
            float tx = ix() + (i % 2) * (w + 6);
            int ty = y + (i / 2) * (hh + 6);
            tuile(t, tx, ty, w, hh);
        }
        y += ((ss.size() + 1) / 2) * (hh + 6);
        y += 4;
        filet(y);
        y += 7;
        s.pz.texte("Clic sur une section : son chef, son effectif et les membres", ix(), y + 4, 12, GRIS_2, false);
        s.pz.texte("que tu connais. Pas de caisse, pas de journal.", ix(), y + 13, 12, GRIS_2, false);
        y += 25;
        filet(y);
        y += 2;
        JsonObject sec = section(v);
        String apercu = sec == null ? "aucune · " + str(moi, "rangNom") + " du village" : str(sec, "nom") + " · " + str(sec, "monGrade");
        y = replie(y, "drapeau", "Ta section", "", apercu, sec == null ? null : () -> s.changerOnglet("section"));
        return y + 4;
    }

    private void tuile(JsonObject t, float x, int y, float w, int h) {
        int X = Math.round(x), W = Math.round(w);
        boolean q = bool(t, "q");
        boolean hov = !q && s.hover(X, y, W, h) && s.cliquable(y, h);
        s.pz.fond(X, y, X + W, y + h, hov ? NUIT_3 : NUIT_2);
        s.pz.cadre(X, y, W, h, hov ? OR_DOUX : FILET);
        float cx = x + w / 2f;
        if (q) {                                   // section cachée vue d'un non-membre : un « ? », rien d'autre
            s.rondQ(cx, y + h / 2f, 12);
            return;
        }
        s.rondInsigne(cx, y + 18, 10, str(t, "insigne"), false);
        s.pz.texteC(s.fit(str(t, "nom"), w - 8, 14, true), cx, y + 36, 14, CREME, true);
        JsonObject ch = obj(t, "chef");
        if (ch != null) {
            String cn = str(ch, "nom");
            float tw = s.pz.largeur(s.fit(cn, w - 20, 12, false), 12, false) + 11;
            s.tete(ch, cx - tw / 2f, y + 42.5f, 8);
            s.pz.texte(s.fit(cn, w - 20, 12, false), cx - tw / 2f + 11, y + 46.5f, 12, bool(ch, "connu") ? GRIS : GRIS_2, false);
        }
        String eff = pluriel(num(t, "n"), "membre") + (bool(t, "cachee") ? " · cachée" : "");
        s.pz.texteC(eff, cx, y + 57, 11, GRIS_2, false);
        String id = str(t, "id");
        if (s.cliquable(y, h)) s.hits.add(new FactionScreen.Hit(X, y, W, h, () -> s.ouvrirSous("section:" + id), null));
    }

    private int sectionDetail(JsonObject v, int y, String id) {
        y = retour(y, "Sections");
        JsonObject t = null;
        for (JsonElement e : arr(v, "sections")) if (id.equals(str(e.getAsJsonObject(), "id"))) t = e.getAsJsonObject();
        if (t == null) return s.vide(y, "Cette section n'est plus visible.", null);
        s.rondInsigne(ix() + 9, y + 10, 9, str(t, "insigne"), true);
        s.txt(str(t, "nom"), ix() + 24, y + 5.5f, iw() - 26, 17, CREME, true);
        s.pz.texte(pluriel(num(t, "n"), "membre") + (bool(t, "cachee") ? " · cachée" : ""), ix() + 24, y + 15.5f, 12, GRIS, false);
        y += 27;
        if (t.has("caisse")) {
            s.pz.texte("Caisse", ix(), y + 4, 12, GRIS, false);
            s.pz.texteD(ryo(lng(t, "caisse")) + " ryo", ix() + iw(), y + 4, 13, OR_CLAIR, true);
            y += 12;
        }
        filet(y);
        y += 7;
        caps(y, "Chef", null, 0);
        y += 10;
        JsonObject ch = obj(t, "chef");
        if (ch != null) {
            s.tete(ch, ix(), y + 1, 12);
            s.txt(str(ch, "nom"), ix() + 17, y + 7, iw() - 30, 14, bool(ch, "connu") ? CREME : GRIS, false);
            if (bool(ch, "connu")) s.pz.pastille(ix() + iw() - 4, y + 7, 1.5f, bool(ch, "online") ? OK : GRIS_3);
            y += 16;
        }
        y += 3;
        caps(y, "Membres que tu connais", null, 0);
        y += 10;
        JsonArray c = arr(t, "connus");
        if (c.isEmpty()) { s.pz.texte("Aucun membre que tu connais.", ix(), y + 4, 12, GRIS_2, false); y += 12; }
        for (JsonElement e : c) {
            JsonObject p = e.getAsJsonObject();
            s.tete(p, ix(), y + 1, 12);
            float lw = s.txt(str(p, "nom"), ix() + 17, y + 7, iw() - 80, 14, CREME, false);
            s.txt(str(p, "grade"), ix() + 21 + lw, y + 7, iw() - 30 - lw, 12, GRIS_2, false);
            s.pz.pastille(ix() + iw() - 4, y + 7, 1.5f, bool(p, "online") ? OK : GRIS_3);
            y += 15;
        }
        int autres = num(t, "n") - c.size() - (ch != null ? 1 : 0);
        if (autres > 0) { s.pz.texte("+ " + autres + " que tu ne connais pas", ix() + 17, y + 4.5f, 12, GRIS_3, false); y += 11; }
        return y + 6;
    }

    /* =================================================================== Annonces */

    private int annonces(JsonObject v, int y) {
        JsonObject peut = obj(v, "peut");
        if (bool(peut, "annoncerVillage") || bool(peut, "annoncerSection")) {
            s.lien("+ Publier une annonce", ix(), y + 5, 13, OR, true, false, () -> {
                formChoix = bool(peut, "annoncerVillage") ? "village" : "section";
                form("annonce", null, null, "Publier une annonce", null);
            });
            y += 14;
            filet(y);
            y += 6;
        }
        JsonArray an = arr(v, "annonces");
        if (an.isEmpty()) return s.vide(y, "Aucune annonce pour l'instant.", "Les annonces du village et de ta section arrivent ici.");
        for (JsonElement e : an) {
            JsonObject a = e.getAsJsonObject();
            s.pz.icone("cloche", ix(), y + 1, 7, OR);
            String sec = str(a, "section");
            s.pz.capitales(sec.isEmpty() ? "Au village" : "À la section · " + sec, ix() + 11, y + 4, 10, sec.isEmpty() ? GRIS_2 : OR_DOUX);
            s.pz.texteD(depuis(lng(a, "le")), ix() + iw(), y + 4, 11, GRIS_2, false);
            y += 10;
            for (String l : s.wrap(str(a, "texte"), iw() - 11, 13)) { s.pz.texte(l, ix() + 11, y + 4, 13, CREME, false); y += 10; }
            String auteur = str(a, "auteur").isEmpty() ? str(a, "titre") : str(a, "auteur") + ", " + str(a, "titre");
            s.txt("— " + auteur, ix() + 11, y + 4, iw() - 11, 12, GRIS, false);
            y += 12;
            s.pz.filetH(ix() + 11, ix() + iw(), y + 1.5f, SEP);
            y += 5;
        }
        return y + 4;
    }

    /* =================================================================== Gestion (maquette 03) */

    private int gestion(JsonObject v, int y) {
        JsonObject g = obj(v, "gestion");
        JsonObject moi = obj(v, "moi");
        Set<String> droits = set(arr(g, "droits"));
        boolean chef = bool(g, "chef");
        s.txt(str(moi, "nom") + " · " + str(moi, "titre") + (chef ? " · chef du village" : ""), ix(), y + 4, iw(), 13, GRIS, false);
        y += 15;
        JsonArray ss = arr(g, "sections");
        caps(y, "Sections · " + ss.size(), null, 0);
        if (droits.contains("sections")) s.lien("+ Créer une section", ix() + iw(), y + 3, 13, OR, true, true, () -> s.ouvrirSous("creer"));
        y += 10;
        boolean caisses = droits.contains("caisses");
        if (caisses) s.pz.texteD("caisse", ix() + iw(), y + 3, 11, GRIS_3, false);
        y += 6;
        for (JsonElement e : ss) {
            JsonObject x = e.getAsJsonObject();
            int h = 21;
            boolean actif = droits.contains("sections") || chef;
            boolean hov = actif && s.menu == null && s.hover(s.px0() + 1, y, FactionScreen.PW - 2, h) && s.cliquable(y, h);
            if (hov) s.pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
            s.pz.insigne(str(x, "insigne"), ix(), y + 4.5f, 11, OR);
            float lw = s.txt(str(x, "nom"), ix() + 16, y + 5.5f, iw() - 70, 14, CREME, false);
            if (bool(x, "cachee")) {
                s.pz.icone("verrou", ix() + 19 + lw, y + 2, 6, GRIS_2);
                s.pz.texte("cachée", ix() + 28 + lw, y + 5.5f, 11, GRIS_2, false);
            }
            s.txt((str(x, "chefNom").isEmpty() ? "sans chef" : str(x, "chefNom")) + " · " + num(x, "n") + " / " + num(x, "places") + " places",
                    ix() + 16, y + 14.5f, iw() - 60, 12, GRIS_2, false);
            if (x.has("caisse")) s.pz.texteD(ryo(lng(x, "caisse")), ix() + iw(), y + 5.5f, 13, OR_CLAIR, false);
            if (actif && s.cliquable(y, h)) {
                final int yy = y;
                s.hits.add(new FactionScreen.Hit(s.px0() + 1, y, FactionScreen.PW - 2, h, () -> menuSection(x, yy, chef), null));
            }
            y += h;
        }
        if (ss.isEmpty()) { s.pz.texte("Aucune section : crée la première (modèle ou libre).", ix(), y + 4, 12, GRIS_2, false); y += 12; }
        y += 4;
        filet(y);
        y += 7;
        boolean taxe = droits.contains("taxe");
        caps(y, "Taxe sur les sections", null, 0);
        y += 11;
        if (taxe) {
            if (!taux.isFocused() && !taux.getValue().equals(String.valueOf(num(g, "taux")))) taux.setValue(String.valueOf(num(g, "taux")));
            s.champ(taux, ix(), y, 27, 14, null, "%");
        } else {
            s.pz.fond(ix(), y, ix() + 27, y + 14, NUIT_2);
            s.pz.cadre(ix(), y, 27, 14, FILET);
            s.pz.texte(num(g, "taux") + " %", ix() + 5, y + 7, 13, GRIS, false);
        }
        s.pz.texte("de chaque entrée d'une caisse de section,", ix() + 33, y + 3.5f, 12, GRIS, false);
        s.pz.texte("versés au trésor du village (0 à " + num(g, "plafond") + " %)", ix() + 33, y + 11.5f, 12, GRIS, false);
        y += 20;
        if (g.has("tresor")) {
            s.pz.texte("Trésor du village", ix(), y + 4, 12, GRIS, false);
            s.pz.texteD(ryo(lng(g, "tresor")) + " ryo", ix() + iw(), y + 4, 13, OR_CLAIR, true);
            y += 11;
        }
        if (g.has("taxeSemaine")) {
            s.pz.texte("Taxe perçue cette semaine", ix(), y + 4, 12, GRIS, false);
            s.pz.texteD("+ " + ryo(lng(g, "taxeSemaine")) + " ryo", ix() + iw(), y + 4, 12, OK, true);
            y += 11;
        }
        s.pz.texte("Dotation d'une section : au guichet, depuis le trésor", ix(), y + 4, 12, GRIS_2, false);
        y += 14;
        filet(y);
        y += 7;
        caps(y, "Droits par grade du village", null, 0);
        y += 9;
        s.pz.texte("Les grades restent ceux de /grade ; tu dis à partir duquel", ix(), y + 4, 12, GRIS_2, false);
        y += 13;
        for (JsonElement e : arr(g, "seuils")) {
            JsonObject x = e.getAsJsonObject();
            // 77 de large : « Commandant Jōnin » (seuil « Sonner sans délai ») tient avant le chevron
            s.txt(str(x, "nom"), ix(), y + 6.5f, iw() - 94, 13, CREME, false);
            s.pz.texteD("dès", ix() + iw() - 80, y + 6.5f, 12, GRIS_2, false);
            int bx = ix() + iw() - 77, bw = 77;
            boolean hov = taxe && s.hover(bx, y, bw, 13) && s.cliquable(y, 13) && s.menu == null;
            s.pz.fond(bx, y, bx + bw, y + 13, hov ? NUIT_3 : NUIT_2);
            s.pz.cadre(bx, y, bw, 13, hov ? OR_DOUX : FILET_2);
            s.pz.texte(nomRang(g, str(x, "rang")), bx + 5, y + 6.5f, 13, taxe ? CREME : GRIS, false);
            s.pz.icone("bas", bx + bw - 11, y + 3, 7, GRIS);
            if (taxe && s.cliquable(y, 13)) {
                final int yy = y;
                s.hits.add(new FactionScreen.Hit(bx, y, bw, 13, () -> menuRang(g, str(x, "id"), yy), null));
            }
            y += 16;
        }
        y += 3;
        filet(y);
        y += 2;
        JsonArray ch = arr(g, "charges");
        List<String> ap = new ArrayList<>();
        for (JsonElement e : ch) ap.add(str(e.getAsJsonObject(), "nom") + " (" + arr(e.getAsJsonObject(), "titulaires").size() + ")");
        if (chef) ap.add("+ Créer une charge");
        y = replie(y, "joueurs", "Charges du village", ch.isEmpty() ? "" : String.valueOf(ch.size()), ap.isEmpty() ? "aucune" : String.join(" · ", ap),
                chef ? () -> s.ouvrirSous("charges") : null);
        if (g.has("journal")) {
            JsonArray jo = arr(g, "journal");
            String last = jo.isEmpty() ? "Rien pour l'instant" : str(jo.get(0).getAsJsonObject(), "t") + " · " + date(lng(jo.get(0).getAsJsonObject(), "le"));
            y = replie(y, "journal", "Journal de la faction", "", last, () -> s.ouvrirSous("journal_faction"));
        }
        return y + 4;
    }

    private static String nomRang(JsonObject g, String id) {
        for (JsonElement e : arr(g, "rangs")) if (id.equals(str(e.getAsJsonObject(), "id"))) return str(e.getAsJsonObject(), "nom");
        return id;
    }

    private void menuRang(JsonObject g, String seuil, int y) {
        List<FactionScreen.Item> items = new ArrayList<>();
        for (JsonElement e : arr(g, "rangs")) {
            JsonObject r = e.getAsJsonObject();
            String id = str(r, "id");
            items.add(new FactionScreen.Item(str(r, "nom"), "", false, false, () -> { envoyer("seuil", "id", seuil, "rang", id); RebornSounds.confirm(); }));
        }
        s.ouvrirMenu(items, y, null, null, 120);
    }

    private void menuSection(JsonObject x, int y, boolean chef) {
        String id = str(x, "id"), n = str(x, "nom");
        boolean cach = bool(x, "cachee");
        List<FactionScreen.Item> items = new ArrayList<>();
        items.add(new FactionScreen.Item("Renommer…", "", false, false, () -> form("section_renommer", id, n, "Renommer « " + n + " »", null)));
        items.add(new FactionScreen.Item(cach ? "Ne plus la cacher" : "Cacher (un « ? »)", "", false, false, () -> envoyer("section_cacher", "id", id, "v", !cach)));
        items.add(new FactionScreen.Item("Nommer le chef…", "", false, false, () -> form("section_chef", id, n, "Chef de « " + n + " »",
                "Un membre du village sans section, ou déjà dans celle-ci. L'ancien chef passe au deuxième grade.")));
        items.add(null);
        items.add(new FactionScreen.Item("Dissoudre…", "", !chef, true, () -> form("section_dissoudre", id, n, "Dissoudre « " + n + " » ?",
                "Les membres sont libérés, la caisse est versée au trésor du village. Motif obligatoire.")));
        s.ouvrirMenu(items, y, null, null, 130);
    }

    /* =================================================================== Créer une section (maquette 03, encart) */

    private int creer(JsonObject v, int y) {
        JsonObject g = obj(v, "gestion");
        y = retour(y, "Gestion");
        s.pz.capitales("Créer une section", ix(), y + 3, 11, OR);
        y += 12;
        int lx = ix() + 41;
        s.pz.texte("Modèle", ix(), y + 5.5f, 12, GRIS, false);
        float x = lx;
        x += s.mot("aucun", x, y + 5.5f, 13, modele.isEmpty(), () -> modele = "") + 6;
        String[][] vis = {{"anbu", "ANBU"}, {"police", "Police"}, {"medical", "Médical"}};
        for (String[] m : vis) x += s.mot(m[1], x, y + 5.5f, 13, modele.equals(m[0]), () -> appliquerModele(g, m[0])) + 6;
        boolean autre = !modele.isEmpty() && !modele.equals("anbu") && !modele.equals("police") && !modele.equals("medical");
        String lab = autre ? nomModele(g, modele) : "…";
        s.mot(lab, x, y + 5.5f, 13, autre, () -> {
            List<FactionScreen.Item> items = new ArrayList<>();
            for (JsonElement e : arr(g, "modeles")) {
                JsonObject m = e.getAsJsonObject();
                items.add(new FactionScreen.Item(str(m, "nom"), bool(m, "cachee") ? "cachée" : "", false, false, () -> appliquerModele(g, str(m, "id"))));
            }
            s.ouvrirMenu(items, s.mouseY, null, null, 130);
        });
        y += 16;
        s.pz.texte("Nom", ix(), y + 7.5f, 12, GRIS, false);
        s.champ(nom, lx, y, iw() - 41, 15, null, null);
        y += 20;
        s.pz.texte("Insigne", ix(), y + 9.5f, 12, GRIS, false);
        for (int j = 0; j < 6; j++) {
            String k = INSIGNES[j];
            int bx = lx + j * 23;
            boolean on = k.equals(insigne);
            boolean hov = !on && s.hover(bx, y, 19, 19) && s.cliquable(y, 19);
            s.pz.fond(bx, y, bx + 19, y + 19, on ? NUIT_3 : NUIT_2);
            s.pz.cadre(bx, y, 19, 19, on ? OR : hov ? OR_DOUX : FILET_2);
            s.pz.insigne(k, bx + 3.5f, y + 3.5f, 12, on ? OR_CLAIR : GRIS);
            if (s.cliquable(y, 19)) s.hits.add(new FactionScreen.Hit(bx, y, 19, 19, () -> { insigne = k; RebornSounds.uiClick(); }, null));
        }
        y += 24;
        s.pz.texte("Chef", ix(), y + 7.5f, 12, GRIS, false);
        if (!chefId.isEmpty() && !chefRech.isFocused()) {
            boolean hov = s.hover(lx, y, iw() - 41, 15) && s.cliquable(y, 15);
            s.pz.fond(lx, y, ix() + iw(), y + 15, NUIT_2);
            s.pz.cadre(lx, y, iw() - 41, 15, hov ? OR_DOUX : FILET_2);
            s.txt(chefLabel, lx + 5, y + 7.5f, iw() - 51, 13, CREME, false);
            s.hits.add(new FactionScreen.Hit(lx, y, iw() - 41, 15, () -> { chefRech.setValue(""); s.setFocused(chefRech); chefRech.setFocused(true); }, null));
            y += 20;
        } else {
            s.champ(chefRech, lx, y, iw() - 41, 15, null, null);
            y += 17;
            if (chefRech.isFocused() || chefId.isEmpty()) y = candidats(g, lx, y, iw() - 41, true, (id, label) -> { chefId = id; chefLabel = label; chefRech.setFocused(false); s.setFocused(null); });
            y += 3;
        }
        s.pz.texte("Places", ix(), y + 7.5f, 12, GRIS, false);
        s.champ(placesNew, lx, y, 27, 15, null, null);
        s.pz.texte("le chef de section pourra les changer", lx + 33, y + 7.5f, 12, GRIS_2, false);
        y += 21;
        boolean hovC = s.hover(lx - 2, y - 2, 70, 11) && s.cliquable(y - 2, 11);
        s.caseCocher(lx, y, cachee, false);
        s.pz.texte("Cachée", lx + 12, y + 3.5f, 13, hovC ? OR_CLAIR : CREME, false);
        s.hits.add(new FactionScreen.Hit(lx - 2, y - 2, 70, 11, () -> { cachee = !cachee; RebornSounds.uiClick(); }, null));
        s.pz.texte("comme l'ANBU : un « ? » pour les gradés", lx + 12, y + 13, 12, GRIS_2, false);
        y += 25;
        s.pz.texte("Les lieux (portes, coffres) sont posés par le staff.", lx, y + 4, 12, GRIS_2, false);
        y += 14;
        int bw = s.bouton(lx, y, 75, 15, "Créer · Entrée", this::creerSection, nom.getValue().isBlank() || chefId.isEmpty());
        s.lien("Annuler", lx + bw + 9, y + 7.5f, 13, GRIS, false, false, s::retour);
        return y + 22;
    }

    private static String nomModele(JsonObject g, String id) {
        for (JsonElement e : arr(g, "modeles")) if (id.equals(str(e.getAsJsonObject(), "id"))) return str(e.getAsJsonObject(), "nom");
        return id;
    }

    private void appliquerModele(JsonObject g, String id) {
        modele = id;
        for (JsonElement e : arr(g, "modeles")) {
            JsonObject m = e.getAsJsonObject();
            if (!id.equals(str(m, "id"))) continue;
            nom.setValue(str(m, "nom"));
            insigne = str(m, "insigne");
            cachee = bool(m, "cachee");
        }
    }

    private void creerSection() {
        if (nom.getValue().isBlank() || chefId.isEmpty()) return;
        int pl = 8;
        try { pl = Integer.parseInt(placesNew.getValue().replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) { }
        envoyer("section_creer", "modele", modele, "nom", nom.getValue().trim(), "insigne", insigne, "chef", chefId, "places", pl, "cachee", cachee);
        RebornSounds.confirm();
        s.retour();
    }

    /** Liste des membres du village qui correspondent au champ de recherche (candidats du serveur, chef de faction). */
    private int candidats(JsonObject g, int x, int y, int w, boolean sansSection, java.util.function.BiConsumer<String, String> choisir) {
        String q = sans(chefRech.getValue().trim());
        int n = 0;
        for (JsonElement e : arr(g, "candidats")) {
            JsonObject c = e.getAsJsonObject();
            if (!q.isEmpty() && !sans(str(c, "nom")).contains(q)) continue;
            if (n >= 5) break;
            boolean pris = sansSection && bool(c, "section");
            boolean hov = !pris && s.hover(x, y, w, 12) && s.cliquable(y, 12);
            if (hov) s.pz.fond(x, y, x + w, y + 12, NUIT_3);
            float lw = s.txt(str(c, "nom"), x + 4, y + 6, w - 70, 13, pris ? GRIS_3 : CREME, false);
            s.pz.texte(pris ? "déjà en section" : str(c, "rang"), x + 8 + lw, y + 6, 12, GRIS_2, false);
            if (!pris && s.cliquable(y, 12)) s.hits.add(new FactionScreen.Hit(x, y, w, 12, () -> {
                choisir.accept(str(c, "id"), str(c, "nom") + " · " + str(c, "rang"));
                RebornSounds.uiClick();
            }, null));
            y += 12;
            n++;
        }
        if (n == 0) { s.pz.texte(q.isEmpty() ? "Aucun membre." : "Personne de ce nom.", x + 4, y + 6, 12, GRIS_2, false); y += 12; }
        return y;
    }

    /* =================================================================== Charges */

    private int charges(JsonObject v, int y) {
        JsonObject g = obj(v, "gestion");
        y = retour(y, "Gestion");
        s.pz.capitales("Charges du village", ix(), y + 3, 11, OR);
        s.lien("+ Créer une charge", ix() + iw(), y + 3, 13, OR, true, true, () -> form("charge_nouvelle", null, null, "Créer une charge",
                "Conseiller, Intendant… : des droits en cases, une charge par personnage."));
        y += 13;
        JsonArray ch = arr(g, "charges");
        if (ch.isEmpty()) y = s.vide(y, "Aucune charge.", "Les charges donnent des droits en plus du grade : créer des sections, régler la taxe…");
        for (JsonElement e : ch) {
            JsonObject c = e.getAsJsonObject();
            List<String> t = new ArrayList<>();
            for (JsonElement x : arr(c, "titulaires")) t.add(str(x.getAsJsonObject(), "nom"));
            y = replie(y, "joueurs", str(c, "nom"), String.valueOf(t.size()), t.isEmpty() ? "personne" : String.join(", ", t), () -> {
                chargeId = str(c, "id");
                chargeDroits.clear();
                chargeDroits.addAll(set(arr(c, "droits")));
                chargePublique = bool(c, "publique");
                chargeSale = false;
                s.ouvrirSous("charge:" + chargeId);
            });
        }
        return y + 4;
    }

    private int charge(JsonObject v, int y, String id) {
        JsonObject g = obj(v, "gestion");
        y = retour(y, "Charges");
        JsonObject c = null;
        for (JsonElement e : arr(g, "charges")) if (id.equals(str(e.getAsJsonObject(), "id"))) c = e.getAsJsonObject();
        if (c == null) return s.vide(y, "Cette charge n'existe plus.", null);
        s.txt(str(c, "nom"), ix(), y + 5, iw() - 60, 18, CREME, true);
        final JsonObject fc = c;
        s.lien("Supprimer", ix() + iw(), y + 5, 12, GRIS, false, true, () -> form("charge_suppr", id, str(fc, "nom"), "Supprimer la charge « " + str(fc, "nom") + " » ?", null));
        y += 15;
        caps(y, "Titulaires", null, 0);
        s.lien("+ Nommer", ix() + iw(), y + 3, 12, OR, true, true, () -> form("charge_nommer", id, str(fc, "nom"), "Nommer à « " + str(fc, "nom") + " »", "Un membre du village ; une charge par personnage."));
        y += 11;
        JsonArray t = arr(c, "titulaires");
        if (t.isEmpty()) { s.pz.texte("Personne.", ix(), y + 4, 12, GRIS_2, false); y += 11; }
        for (JsonElement e : t) {
            JsonObject p = e.getAsJsonObject();
            s.txt(str(p, "nom"), ix(), y + 5, iw() - 50, 13, CREME, false);
            s.lien("retirer", ix() + iw(), y + 5, 12, GRIS, false, true, () -> envoyer("charge_titulaire", "id", id, "p", str(p, "id"), "oui", false));
            y += 12;
        }
        y += 4;
        caps(y, "Droits", null, 0);
        y += 11;
        for (String[] d : DROITS_CHARGE) {
            boolean on = chargeDroits.contains(d[0]);
            s.caseCocher(ix(), y + 0.5f, on, false);
            s.txt(d[1], ix() + 12, y + 4, iw() - 12, 13, on ? CREME : GRIS, false);
            s.hits.add(new FactionScreen.Hit(ix() - 2, y - 2, iw() + 4, 13, () -> {
                if (!chargeDroits.remove(d[0])) chargeDroits.add(d[0]);
                chargeSale = true;
                RebornSounds.uiClick();
            }, null));
            y += 14;
        }
        s.caseCocher(ix(), y + 0.5f, chargePublique, false);
        s.pz.texte("Charge publique (nommée pour tous, comme le Kage)", ix() + 12, y + 4, 13, chargePublique ? CREME : GRIS, false);
        s.hits.add(new FactionScreen.Hit(ix() - 2, y - 2, iw() + 4, 13, () -> { chargePublique = !chargePublique; chargeSale = true; RebornSounds.uiClick(); }, null));
        y += 18;
        s.bouton(ix(), y, 85, 15, "Enregistrer · Entrée", this::enregistrerCharge, !chargeSale);
        return y + 22;
    }

    private void enregistrerCharge() {
        if (chargeId == null || !chargeSale) return;
        JsonArray d = new JsonArray();
        for (String x : chargeDroits) d.add(x);
        envoyer("charge_droits", "id", chargeId, "droits", d, "publique", chargePublique);
        chargeSale = false;
        RebornSounds.confirm();
    }

    /* =================================================================== formulaires dans le volet */

    /** Ouvre un formulaire (exclure, quitter, nouveau grade, renommer, chef, dissoudre, annonce, charge…). */
    void form(String type, String id, String cible, String titre, String texte) {
        formType = type;
        formId = id;
        formCible = cible;
        formTitre = titre;
        formTexte = texte;
        formCandidat = "";
        formCandidatLabel = "";
        champForm.setValue(type.endsWith("renommer") && cible != null ? cible : "");
        motif.setValue("");
        chefRech.setValue("");
        s.ouvrirSous("form");
        switch (type) {
            case "grade_nouveau", "grade_renommer", "section_renommer", "annonce", "charge_nouvelle" -> { s.setFocused(champForm); champForm.setFocused(true); }
            case "exclure", "section_dissoudre" -> { s.setFocused(motif); motif.setFocused(true); }
            case "section_chef", "charge_nommer" -> { s.setFocused(chefRech); chefRech.setFocused(true); }
            default -> { }
        }
    }

    private int form(JsonObject v, int y) {
        String back = switch (formType) {
            case "grade_nouveau", "grade_renommer", "grade_suppr" -> "Grades";
            case "section_renommer", "section_chef", "section_dissoudre" -> "Gestion";
            case "charge_nouvelle" -> "Charges";
            case "charge_nommer", "charge_suppr" -> "Charge";
            case "annonce" -> "Annonces";
            default -> str(section(v), "nom");
        };
        y = retour(y, back);
        boolean danger = formType.equals("exclure") || formType.endsWith("suppr") || formType.equals("section_dissoudre") || formType.equals("quitter");
        s.pz.capitales(formTitre == null ? "" : formTitre, ix(), y + 3, 11, danger ? ROUGE : OR);
        y += 12;
        if (formTexte != null) {
            for (String l : s.wrap(formTexte, iw(), 12)) { s.pz.texte(l, ix(), y + 4, 12, GRIS, false); y += 9; }
            y += 4;
        }
        String bouton = "Valider · Entrée";
        boolean pret = true;
        switch (formType) {
            case "grade_nouveau", "grade_renommer", "section_renommer", "charge_nouvelle" -> {
                indiceForm(formType.startsWith("grade") ? "Nom du grade" : formType.startsWith("charge") ? "Nom de la charge" : "Nom de la section");
                s.champ(champForm, ix(), y, iw(), 15, null, null);
                y += 21;
                bouton = formType.endsWith("nouveau") || formType.endsWith("nouvelle") ? "Créer · Entrée" : "Renommer · Entrée";
                pret = !champForm.getValue().isBlank();
            }
            case "annonce" -> {
                JsonObject peut = obj(v, "peut");
                s.pz.texte("À", ix(), y + 5.5f, 12, GRIS, false);
                float x = ix() + 16;
                if (bool(peut, "annoncerVillage")) x += s.mot("tout le village", x, y + 5.5f, 13, "village".equals(formChoix), () -> formChoix = "village") + 8;
                if (bool(peut, "annoncerSection")) s.mot("ma section", x, y + 5.5f, 13, "section".equals(formChoix), () -> formChoix = "section");
                y += 14;
                indiceForm("Une réunion aura lieu ce soir sur la place centrale…");
                s.champ(champForm, ix(), y, iw(), 15, null, null);
                y += 19;
                s.pz.texteD(champForm.getValue().length() + " / 240", ix() + iw(), y + 3, 11, GRIS_2, false);
                y += 9;
                bouton = "Publier · Entrée";
                pret = !champForm.getValue().isBlank();
            }
            case "exclure", "section_dissoudre" -> {
                indice(motif, formType.equals("exclure") ? "Motif (facultatif)" : "Motif (obligatoire)");
                s.champ(motif, ix(), y, iw(), 15, null, null);
                y += 21;
                bouton = formType.equals("exclure") ? "Exclure · Entrée" : "Dissoudre · Entrée";
                pret = formType.equals("exclure") || !motif.getValue().isBlank();
            }
            case "section_chef", "charge_nommer" -> {
                JsonObject g = obj(v, "gestion");
                if (!formCandidat.isEmpty()) {
                    s.pz.texte("Choisi : " + formCandidatLabel, ix(), y + 5, 13, OR_CLAIR, false);
                    y += 12;
                }
                s.champ(chefRech, ix(), y, iw(), 15, "chercher", null);
                y += 17;
                y = candidats(g, ix(), y, iw(), false, (id, label) -> { formCandidat = id; formCandidatLabel = label; });
                y += 5;
                bouton = "Nommer · Entrée";
                pret = !formCandidat.isEmpty();
            }
            case "quitter" -> bouton = "Quitter · Entrée";
            case "grade_suppr", "charge_suppr" -> bouton = "Supprimer · Entrée";
            default -> { }
        }
        int bw = s.bouton(ix(), y, Math.max(75, Math.round(s.pz.largeur(bouton, 13, true)) + 20), 15, bouton, this::validerForm, !pret);
        s.lien("Annuler", ix() + bw + 9, y + 7.5f, 13, GRIS, false, false, s::retour);
        y += 22;
        if (formType.equals("exclure") || formType.equals("section_dissoudre") || formType.startsWith("grade") || formType.startsWith("section"))
            { s.pz.texte("Inscrit au journal, que personne n'efface.", ix(), y + 4, 12, GRIS_2, false); y += 12; }
        return y + 4;
    }

    private void indiceForm(String h) { indice(champForm, h); }

    private void indice(EditBox b, String h) { s.indices.put(b, h); }

    private void validerForm() {
        String val = champForm.getValue().trim();
        switch (formType) {
            case "grade_nouveau" -> { if (val.isEmpty()) return; envoyer("grade_creer", "nom", val); }
            case "grade_renommer" -> { if (val.isEmpty()) return; envoyer("grade_renommer", "id", formId, "nom", val); }
            case "grade_suppr" -> { envoyer("grade_suppr", "id", formId); gradeSel = null; }
            case "section_renommer" -> { if (val.isEmpty()) return; envoyer("section_renommer", "id", formId, "nom", val); }
            case "section_chef" -> { if (formCandidat.isEmpty()) return; envoyer("section_chef", "id", formId, "p", formCandidat); }
            case "section_dissoudre" -> { if (motif.getValue().isBlank()) return; envoyer("section_dissoudre", "id", formId, "motif", motif.getValue().trim()); }
            case "exclure" -> envoyer("exclure", "p", formId, "motif", motif.getValue().trim());
            case "quitter" -> envoyer("quitter");
            case "annonce" -> { if (val.isEmpty()) return; envoyer("annoncer", "section", "section".equals(formChoix), "texte", val); }
            case "charge_nouvelle" -> { if (val.isEmpty()) return; envoyer("charge_creer", "nom", val); }
            case "charge_nommer" -> { if (formCandidat.isEmpty()) return; envoyer("charge_titulaire", "id", formId, "p", formCandidat, "oui", true); }
            case "charge_suppr" -> envoyer("charge_suppr", "id", formId);
            default -> { }
        }
        RebornSounds.confirm();
        String apres = formType;
        s.retour();
        if (apres.startsWith("charge_") && !apres.equals("charge_nouvelle") && !apres.equals("charge_suppr")) s.sous = "charge:" + formId;
        else if (apres.startsWith("grade_")) s.sous = "grades";
        else if (apres.equals("charge_nouvelle") || apres.equals("charge_suppr")) s.sous = "charges";
    }

    /* =================================================================== Entrée */

    boolean entree() {
        if (s.sous == null) {
            if ("section".equals(s.tab) && places.isFocused()) {
                try { envoyer("places", "n", Integer.parseInt(places.getValue().replaceAll("[^0-9]", ""))); } catch (NumberFormatException ignored) { }
                places.setFocused(false); s.setFocused(null);
                return true;
            }
            if ("gestion".equals(s.tab) && taux.isFocused()) {
                try { envoyer("taux", "n", Integer.parseInt(taux.getValue().replaceAll("[^0-9]", ""))); } catch (NumberFormatException ignored) { }
                taux.setFocused(false); s.setFocused(null);
                return true;
            }
            return false;
        }
        switch (s.sous) {
            case "grades" -> { enregistrerDroits(); return true; }
            case "creer" -> { creerSection(); return true; }
            case "tenue" -> { enregistrerTenue(); return true; }
            case "form" -> { validerForm(); return true; }
            default -> {
                if (s.sous.startsWith("charge:")) { enregistrerCharge(); return true; }
                return false;
            }
        }
    }

    /* =================================================================== banc */

    void debugForm(String type, String id, String cible, String titre, String texte, String valeur) {
        form(type, id, cible, titre, texte);
        if (valeur != null) { champForm.setValue(valeur); motif.setValue(valeur); }
    }

    void debugCreer(String nomS, String insigneS, String chefIdS, String chefLabelS, String placesS) {
        nom.setValue(nomS);
        insigne = insigneS;
        chefId = chefIdS;
        chefLabel = chefLabelS;
        placesNew.setValue(placesS);
        nom.setFocused(true);
        s.setFocused(nom);
    }

    void debugRecherche(String q) { recherche.setValue(q); }

    void debugGrade(String id) { gradeSel = id; chargerDroits(arr(section(s.vue()), "grades")); }

    void debugEnvoye(String t) { envoye = t; envoyeA = System.currentTimeMillis(); }

    void debugFiche(int i, int y) {
        JsonObject v = s.vue();
        JsonArray ms = arr(section(v), "membres");
        if (i < ms.size()) fiche(v, ms.get(i).getAsJsonObject(), y);
    }
}
