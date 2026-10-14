package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Onglet Journal (maquette A_03) : le <b>journal universel</b> du serveur (tout est journalisé, quel que soit le grade ;
 * PLUGINS_REFERENCE §4 bis.1-2). Champ de texte libre, <b>jetons</b> des filtres posés (#N° en or, catégories à
 * pastille, période à horloge, chacun avec sa croix), « + Filtre » (menu des catégories et périodes) et le compte ;
 * entrées <b>groupées par jour</b> (AUJOURD'HUI, HIER, date), une ligne par entrée : heure, pastille, action, N° à
 * droite, « acteur → cible · détail ». Clic = déplier l'entrée (lieu, « Y aller », fiche, « Copier ») ; Ctrl + clic
 * = filtrer ce N°. Les demandes partent au serveur ({@code a: journal}) 0,4 s après la dernière frappe ; pages de 50.
 */
final class GardeJournal {

    static final Object[][] CATS = {
            {"JOUEUR", "Joueur"}, {"INVENTAIRE", "Inventaire"}, {"OBJET", "Objet"}, {"COMBAT", "Combat"}, {"KO", "KO / mort"},
            {"DUEL", "Duel"}, {"ECONOMIE", "Économie"}, {"INTERACTION", "Interaction"}, {"BIBLIOTHEQUE", "Bibliothèque"},
            {"STAFF", "Staff"}, {"COMMANDE", "Commande"}, {"CHAT", "Tchat"}, {"SYSTEME", "Système"}};
    static final String[][] PERIODES = {{"1h", "1 h"}, {"24h", "24 h"}, {"7j", "7 j"}, {"tout", "Tout"}};
    private static final int PAR = 50;

    private final StaffScreen s;
    private EditBox texte;
    final Set<String> cats = new LinkedHashSet<>();
    String periode = "tout";
    int page;
    private int req;
    private long modifAt = -1;
    private boolean attente;
    String pidFiltre, nomFiltre;
    /** Entrée dépliée (id). */
    private String deplie;
    /** Zones des N° cliquables à Ctrl + clic : {x, y, w, h} → N°. */
    private final List<Object[]> zonesPid = new ArrayList<>();

    GardeJournal(StaffScreen s) { this.s = s; }

    void init() {
        texte = s.box(texte, "Texte libre : action, objet, commande…", 60);
        texte.setResponder(v -> { modifAt = System.currentTimeMillis(); page = 0; s.scroll = 0; });
    }

    void ouvrir() { demander(); s.setFocused(texte); }

    void tick() {
        if (modifAt > 0 && System.currentTimeMillis() - modifAt > 400) demander();
    }

    /** Filtre posé depuis la fiche (« Journal du joueur ») : toutes les entrées de ce N°. */
    void filtrerPid(String pid, String nom) {
        pidFiltre = pid == null || pid.isEmpty() ? null : pid;
        nomFiltre = nom;
        cats.clear();
        periode = "tout";
        page = 0;
        deplie = null;
        modifAt = -1;
    }

    /** Envoie la demande au serveur avec les filtres en cours. */
    void demander() {
        modifAt = -1;
        JsonObject o = new JsonObject();
        o.addProperty("a", "journal");
        o.addProperty("req", ++req);
        JsonArray c = new JsonArray();
        for (String id : cats) c.add(id);
        o.add("cat", c);
        String t = texte.getValue().trim();
        // « #00042 » tapé dans le texte devient le filtre N°
        if (t.startsWith("#") && t.length() > 1 && t.substring(1).chars().allMatch(Character::isDigit)) {
            pidFiltre = t.substring(1);
            nomFiltre = null;
            texte.setValue("");
            t = "";
        }
        if (pidFiltre != null) o.addProperty("pid", pidFiltre);
        long du = depuis();
        if (du > 0) o.addProperty("du", du);
        if (!t.isEmpty()) o.addProperty("q", t);
        o.addProperty("page", page);
        o.addProperty("par", PAR);
        attente = true;
        StaffClient.send(o);
    }

    private long depuis() {
        long now = System.currentTimeMillis();
        return switch (periode) {
            case "1h" -> now - 3_600_000L;
            case "24h" -> now - 86_400_000L;
            case "7j" -> now - 7 * 86_400_000L;
            default -> 0L;
        };
    }

    private JsonObject reponse(JsonObject snap) {
        JsonObject r = StaffClient.journal();
        if (r != null) {
            if (StaffScreen.num(r, "req") >= req) attente = false;
            return r;
        }
        attente = false;
        return ancien(snap);
    }

    /* ================================================================ outils fixes : champ, jetons, compte */

    private int rangsJetons;

    int hauteurOutils() { return 17 + 5 + Math.max(1, rangsJetons) * 15 + 12 + 6; }

    void outils(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        s.champ(texte, ix, y, iw, "chercher");
        y += 22;
        // jetons des filtres posés
        List<Object[]> jetons = new ArrayList<>();   // {libellé, type, id}
        if (pidFiltre != null) jetons.add(new Object[]{"#" + pidFiltre, "pid", pidFiltre});
        for (String c : cats) jetons.add(new Object[]{label(c), "cat", c});
        if (!"tout".equals(periode)) for (String[] p : PERIODES) if (p[0].equals(periode)) jetons.add(new Object[]{p[1], "per", p[0]});
        float x = ix;
        int rangs = 1, jy = y;
        if (jetons.isEmpty()) pz.texte("Aucun filtre : tout le journal.", ix, y + 6, 12, GRIS_2, false);
        for (Object[] j : jetons) {
            String lab = (String) j[0], type = (String) j[1], id = (String) j[2];
            boolean pid = "pid".equals(type);
            float w = pz.largeur(lab, 12, pid) + (pid ? 15 : 19);
            if (x + w > ix + iw) { x = ix; jy += 15; rangs++; }
            int X = Math.round(x);
            pz.fond(X, jy, X + Math.round(w), jy + 12, NUIT_2);
            pz.cadre(X, jy, Math.round(w), 12, FILET_2);
            if (pid) pz.texte(lab, x + 4, jy + 6, 12, OR, true);
            else {
                if ("per".equals(type)) pz.icone("horloge", x + 2.5f, jy + 3, 6, GRIS);
                else pz.pastille(x + 5, jy + 6, 1.5f, GardeArt.categorie(id));
                pz.texte(lab, x + 9, jy + 6, 12, CREME, false);
            }
            boolean hovX = s.hover(Math.round(x + w - 9), jy, 9, 12);
            pz.icone("croix", x + w - 8, jy + 3, 6, hovX ? CREME : GRIS_2);
            s.hits.add(new StaffScreen.Hit(Math.round(x + w - 10), jy, 10, 12, () -> retirer(type, id), null, null));
            if (pid && nomFiltre != null && !nomFiltre.isEmpty()) s.tip(X, jy, Math.round(w), 12, "N° " + id + " · " + nomFiltre);
            x += w + 3;
        }
        rangsJetons = rangs;
        y = jy + 15 + 2;
        // + Filtre, compte
        float fw = s.motAction("+ Filtre", ix, y + 3.5f, true, this::menuFiltre);
        JsonObject r = reponse(snap);
        int t = StaffScreen.num(r, "total");
        int pages = Math.max(1, StaffScreen.num(r, "pages")), pg = Math.min(StaffScreen.num(r, "page"), pages - 1);
        String m = attente ? "recherche…" : t + (t > 1 ? " entrées" : " entrée") + " · récentes d'abord"
                + ("disque".equals(StaffScreen.str(r, "source")) ? " · archives" : "");
        float mx = ix + iw;
        if (pages > 1) {
            boolean nxt = pg < pages - 1, prv = pg > 0;
            int bx = Math.round(mx - 8);
            pz.icone("droite", bx, y, 7, nxt ? (s.hover(bx - 1, y - 2, 9, 11) ? CREME : GRIS) : GRIS_3);
            if (nxt) s.hits.add(new StaffScreen.Hit(bx - 1, y - 2, 9, 11, () -> aller(page + 1), null, null));
            String pt = (pg + 1) + "/" + pages;
            float pw = pz.texteD(pt, bx - 2, y + 3.5f, 12, GRIS, false);
            int ax = Math.round(bx - 2 - pw - 9);
            pz.icone("gauche", ax, y, 7, prv ? (s.hover(ax - 1, y - 2, 9, 11) ? CREME : GRIS) : GRIS_3);
            if (prv) s.hits.add(new StaffScreen.Hit(ax - 1, y - 2, 9, 11, () -> aller(page - 1), null, null));
            mx = ax - 5;
        }
        s.txt(m, mx - Math.min(mx - (ix + fw + 6), pz.largeur(m, 12, false)), y + 3.5f, mx - (ix + fw + 6), 12, GRIS_2, false);
        y += 11;
        pz.filetH(ix, ix + iw, y + 0.5f, FILET);
    }

    private void retirer(String type, String id) {
        switch (type) {
            case "pid" -> { pidFiltre = null; nomFiltre = null; }
            case "cat" -> cats.remove(id);
            default -> periode = "tout";
        }
        page = 0;
        s.scroll = 0;
        RebornSounds.uiClick();
        demander();
    }

    private void menuFiltre() {
        List<StaffScreen.Item> items = new ArrayList<>();
        for (Object[] c : CATS) {
            String id = (String) c[0];
            boolean on = cats.contains(id);
            items.add(new StaffScreen.Item(on ? "croix" : "point", (on ? "Retirer · " : "") + c[1], (char) 0, null, false, () -> {
                if (!cats.remove(id)) cats.add(id);
                page = 0;
                demander();
            }));
        }
        items.add(null);
        for (String[] p : PERIODES) {
            items.add(new StaffScreen.Item("horloge", p[0].equals("tout") ? "Toute la période" : "Dernières " + p[1], (char) 0, null, false, () -> {
                periode = p[0];
                page = 0;
                demander();
            }));
        }
        items.add(null);
        items.add(new StaffScreen.Item("filtre", "Un N° : tape #00042 dans le champ", (char) 0, null, false, () -> { texte.setValue("#"); s.setFocused(texte); texte.setFocused(true); }));
        s.ouvrirMenu(items, s.px0() - 4, s.top() + 40, this);
    }

    private void aller(int p) {
        page = Math.max(0, p);
        s.scroll = 0;
        RebornSounds.uiClick();
        demander();
    }

    /* ================================================================ corps */

    int corps(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        zonesPid.clear();
        JsonObject r = reponse(snap);
        JsonArray es = StaffScreen.arr(r, "entries");
        if (es.isEmpty()) {
            boolean filtre = !cats.isEmpty() || !"tout".equals(periode) || !texte.getValue().isBlank() || pidFiltre != null;
            return s.vide(y, attente ? "Recherche dans le journal…" : filtre ? "Aucune entrée pour ces filtres." : "Le journal est vide.",
                    filtre ? "Élargis la période ou retire des jetons." : null);
        }
        String jour = null;
        y += 1;
        for (JsonElement el : es) {
            JsonObject e = el.getAsJsonObject();
            String j = jour(e);
            if (!j.equals(jour)) {
                jour = j;
                y += 2;
                pz.capitales(j, ix, y + 4, 11, GRIS_2);
                y += 11;
            }
            String id = StaffScreen.str(e, "id").isEmpty() ? StaffScreen.str(e, "date") + StaffScreen.str(e, "action") : StaffScreen.str(e, "id");
            boolean ouvert = id.equals(deplie);
            int h = ouvert ? 46 : 25;
            if (y + h < s.vy0() - 4 || y > s.vy1() + 4) { y += h; continue; }   // hors de la vue : rien à dessiner
            boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
            if (ouvert) {
                pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_3);
                pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR);
            } else if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
            String cat = StaffScreen.str(e, "cat");
            pz.texte(heure(e), ix, y + 7, 12, GRIS_2, false);
            pz.pastille(ix + 25, y + 7, 1.5f, GardeArt.categorie(cat));
            JsonObject ac = StaffScreen.obj(e, "acteur"), ci = StaffScreen.obj(e, "cible");
            String pid = ac != null ? StaffScreen.str(ac, "pid") : "";
            float pw = 0;
            if (!pid.isEmpty()) {
                pw = pz.texteD(pid, ix + iw, y + 7, 12, OR_DOUX, false);
                zonesPid.add(new Object[]{Math.round(ix + iw - pw - 1), y + 1, Math.round(pw) + 2, 12, pid,
                        ac != null ? StaffScreen.str(ac, "perso") : ""});
            }
            s.txt(action(StaffScreen.str(e, "action")), ix + 30, y + 7, iw - 30 - pw - 6, 14, CREME, false);
            s.txt(detail(ac, ci, StaffScreen.str(e, "detail")), ix + 30, y + 16.5f, iw - 30, 12, GRIS, false);
            final String fid = id;
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, ouvert ? 22 : h,
                    () -> { deplie = fid.equals(deplie) ? null : fid; RebornSounds.uiClick(); }, null, null));
            if (ouvert) {
                String lieu = StaffScreen.str(e, "lieu");
                s.txt((lieu.isEmpty() ? "Lieu inconnu" : "Lieu : " + lieu) + " · " + label(cat), ix + 30, y + 26, iw - 30, 12, GRIS_2, false);
                float x = ix + 30;
                float cy = y + 36.5f;
                x += s.motAction("Y aller", x, cy, true, () -> yAller(e)) + 9;
                JsonObject cf = ci != null && !StaffScreen.str(ci, "uuid").isEmpty() ? ci : ac;
                if (cf != null && !StaffScreen.str(cf, "uuid").isEmpty()) {
                    String n = StaffScreen.str(cf, "perso").isEmpty() ? StaffScreen.str(cf, "pseudo") : StaffScreen.str(cf, "perso").split(" ")[0];
                    String u = StaffScreen.str(cf, "uuid");
                    x += s.motAction(s.fit("Fiche de " + n, 70, 13, false), x, cy, false, () -> s.openProfile(u)) + 9;
                }
                s.motAction("Copier", x, cy, false, () -> copier(e));
            }
            y += h;
        }
        return y + 2;
    }

    private static String detail(JsonObject ac, JsonObject ci, String det) {
        String a = nom(ac), c = nom(ci);
        String t = a.isEmpty() ? "" : a;
        if (!c.isEmpty()) t = t.isEmpty() ? "→ " + c : t + " → " + c;
        if (!det.isEmpty()) t = t.isEmpty() ? det : t + " · " + det;
        return t;
    }

    private static String nom(JsonObject p) {
        if (p == null) return "";
        return StaffScreen.str(p, "perso").isEmpty() ? StaffScreen.str(p, "pseudo") : StaffScreen.str(p, "perso");
    }

    private void yAller(JsonObject e) {
        String lieu = StaffScreen.str(e, "lieu");
        String[] t = lieu.split(" ");
        var mc = Minecraft.getInstance();
        if (t.length == 4 && mc.player != null && !StaffClient.demo) {
            mc.player.connection.sendCommand("tp " + t[1] + " " + t[2] + " " + t[3]);
            return;
        }
        JsonObject ac = StaffScreen.obj(e, "acteur");
        if (ac != null && !StaffScreen.str(ac, "uuid").isEmpty()) GardeActions.envoyer(s, "tp", ac, java.util.Map.of());
        else StaffClient.toast("Pas de lieu pour cette entrée.");
    }

    private void copier(JsonObject e) {
        JsonObject ac = StaffScreen.obj(e, "acteur"), ci = StaffScreen.obj(e, "cible");
        Minecraft.getInstance().keyboardHandler.setClipboard(StaffScreen.str(e, "date") + " [" + StaffScreen.str(e, "cat") + "] "
                + StaffScreen.str(e, "action") + " · " + detail(ac, ci, StaffScreen.str(e, "detail"))
                + (StaffScreen.str(e, "lieu").isEmpty() ? "" : " · " + StaffScreen.str(e, "lieu")));
        StaffClient.toast("Entrée copiée.");
    }

    /** Ctrl + clic sur un N° : filtrer ce joueur. */
    boolean ctrlClic(double x, double y) {
        if (!"journal".equals(s.tab)) return false;
        for (Object[] z : zonesPid) {
            int zx = (int) z[0], zy = (int) z[1], zw = (int) z[2], zh = (int) z[3];
            if (x >= zx && x < zx + zw && y >= zy && y < zy + zh) {
                filtrerPid((String) z[4], (String) z[5]);
                demander();
                RebornSounds.uiClick();
                return true;
            }
        }
        return false;
    }

    boolean echap() {
        if (deplie != null) { deplie = null; return true; }
        return false;
    }

    boolean entree() {
        if ("journal".equals(s.tab) && texte.isFocused()) { demander(); return true; }
        return false;
    }

    /* ================================================================ dates et libellés */

    /** « AUJOURD'HUI », « HIER » ou la date, d'après {@code at} (sinon la date affichée). */
    private static String jour(JsonObject e) {
        long at = StaffScreen.lng(e, "at");
        if (at > 0) {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.FRANCE);
            String d = f.format(new Date(at)), auj = f.format(new Date()), hier = f.format(new Date(System.currentTimeMillis() - 86_400_000L));
            if (d.equals(auj)) return "Aujourd'hui";
            if (d.equals(hier)) return "Hier";
            return new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date(at));
        }
        String d = StaffScreen.str(e, "date");
        return d.length() >= 5 ? d.substring(0, 5) : "Récemment";
    }

    private static String heure(JsonObject e) {
        String d = StaffScreen.str(e, "date");
        String[] t = d.split(" ");
        String h = t.length >= 2 ? t[1] : d;
        return h.length() >= 5 ? h.substring(0, 5) : h;
    }

    /** « DEGATS » → « Dégâts », « A_TERRE » → « À terre » (codes du serveur, sans accents). */
    static String action(String a) {
        if (a == null || a.isEmpty()) return "";
        String t = switch (a) {
            case "DEGATS" -> "Dégâts";
            case "A_TERRE" -> "À terre";
            case "REVEIL" -> "Réveil";
            case "RPK_REFUSE" -> "RPK refusée";
            case "RPK_VALIDE" -> "RPK validée";
            case "RPK_DEMANDE" -> "Demande de RPK";
            case "AGRESSION", "AGRESSION_HORS_ZONE" -> "Agression";
            case "DECONNEXION" -> "Déconnexion";
            case "LACHE" -> "Lâché";
            case "RAMASSE" -> "Ramassé";
            case "EQUIPE" -> "Équipé";
            case "GEL" -> "Gel";
            case "DEGEL" -> "Dégel";
            case "ECRAN_NOIR" -> "Écran noir";
            case "MODE_STAFF" -> "Mode staff";
            case "MODE_ANIMATION" -> "Mode animation";
            case "ANIMATION" -> "Animation";
            case "TP_VERS", "TP" -> "Téléportation";
            case "RAMENE" -> "Ramené";
            case "OBTENU" -> "Obtenu";
            case "DEPENSE" -> "Dépense";
            case "ACHAT" -> "Achat";
            default -> null;
        };
        if (t != null) return t;
        String x = a.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }

    static String label(String cat) {
        for (JsonObject src : new JsonObject[]{StaffClient.journal(), StaffClient.snapshot()}) {
            for (JsonElement e : StaffScreen.arr(src, src == StaffClient.snapshot() ? "journalCats" : "cats")) {
                JsonObject c = e.getAsJsonObject();
                if (cat.equals(StaffScreen.str(c, "id")) && !StaffScreen.str(c, "label").isEmpty()) return StaffScreen.str(c, "label");
            }
        }
        for (Object[] c : CATS) if (c[0].equals(cat)) return (String) c[1];
        return cat.isEmpty() ? "—" : cat.charAt(0) + cat.substring(1).toLowerCase(Locale.ROOT);
    }

    /* ================================================================ banc */

    void debugDeplier(int i) {
        JsonArray es = StaffScreen.arr(StaffClient.journal(), "entries");
        if (i < es.size()) {
            JsonObject e = es.get(i).getAsJsonObject();
            deplie = StaffScreen.str(e, "id");
        }
    }

    void debugMenu() { menuFiltre(); }

    void debugTexte(String t) { texte.setValue(t); demander(); }

    /* ================================================================ repli : ancien journal du Poste de garde */

    private JsonObject ancien(JsonObject snap) {
        JsonObject r = new JsonObject();
        JsonArray out = new JsonArray();
        String q = texte.getValue().trim().toLowerCase(Locale.ROOT);
        for (JsonElement el : StaffScreen.arr(snap, "journal")) {
            JsonObject o = el.getAsJsonObject();
            String cat = switch (StaffScreen.str(o, "kind")) {
                case "soin" -> "INTERACTION";
                case "ko" -> "KO";
                case "monde" -> "SYSTEME";
                case "objet" -> "OBJET";
                case "duel" -> "DUEL";
                default -> "STAFF";
            };
            if (!cats.isEmpty() && !cats.contains(cat)) continue;
            String txt = (StaffScreen.str(o, "type") + " " + StaffScreen.str(o, "detail")).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !txt.contains(q)) continue;
            JsonObject e = new JsonObject();
            e.addProperty("date", StaffScreen.str(o, "time"));
            e.addProperty("cat", cat);
            e.addProperty("action", StaffScreen.str(o, "type").toUpperCase(Locale.ROOT).replace(' ', '_'));
            JsonObject a = new JsonObject();
            a.addProperty("pseudo", StaffScreen.str(o, "name"));
            a.addProperty("grade", StaffScreen.num(o, "grade"));
            e.add("acteur", a);
            e.addProperty("detail", StaffScreen.str(o, "detail"));
            out.add(e);
        }
        r.addProperty("total", out.size());
        r.addProperty("page", 0);
        r.addProperty("pages", 1);
        r.addProperty("source", "ancien");
        r.add("entries", out);
        return r;
    }
}
