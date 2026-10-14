package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Onglet Journal du Poste de garde : le <b>journal universel</b> du serveur (tout est journalisé, quel que soit le
 * grade ; PLUGINS_REFERENCE §4 bis.1-2). Zone d'outils fixe : texte libre, N° ou joueur, catégories et période en
 * puces ; en-têtes de colonnes. Corps : une ligne par entrée (date, catégorie, acteur, action, cible et détail),
 * couleur de catégorie sobre en liseré ; clic = détail de l'entrée. Pied : pagination. Les demandes partent au
 * serveur ({@code a: journal}) 0,4 s après la dernière frappe.
 *
 * <p>Repli tant que le serveur ne répond pas au nouveau protocole : l'ancien journal du Poste de garde
 * ({@code snap.journal}), converti et filtré ici.
 */
final class GardeJournal {

    /** Catégories (id, libellé, couleur) — l'ordre des puces. */
    static final Object[][] CATS = {
            {"JOUEUR", "Joueur", 0xFF2F6A7A}, {"INVENTAIRE", "Inventaire", 0xFF8A5A0C}, {"OBJET", "Objet", 0xFFA8741A},
            {"COMBAT", "Combat", 0xFFA0182B}, {"KO", "KO / mort", 0xFF5A0A12}, {"DUEL", "Duel", 0xFF7A1322},
            {"ECONOMIE", "Économie", 0xFF2F7A3A}, {"INTERACTION", "Interaction", 0xFF6B4FA0},
            {"BIBLIOTHEQUE", "Bibliothèque", 0xFF4A5A8A}, {"STAFF", "Staff", 0xFF3B2A12}, {"COMMANDE", "Commande", 0xFF5A5048},
            {"CHAT", "Tchat", 0xFF5E6E7A}, {"SYSTEME", "Système", 0xFF7A6E5C}};
    static final String[][] PERIODES = {{"1h", "1 h"}, {"24h", "24 h"}, {"7j", "7 j"}, {"tout", "Tout"}};
    private static final int PAR = 50, ROW = 16;

    private final StaffScreen s;
    private EditBox texte, qui;
    final Set<String> cats = new LinkedHashSet<>();
    String periode = "tout";
    int page;
    private int req;
    private long modifAt = -1;
    private boolean attente;
    String pidFiltre, nomFiltre;
    /* disposition des puces calculée pour une largeur (règle DA §6.5) */
    private int fluxW = -1;
    private int[][] fluxPos;
    private int[] fluxL;
    private String[] fluxIds;

    GardeJournal(StaffScreen s) { this.s = s; }

    void init() {
        texte = s.box(texte, "Texte : action, détail, objet, commande…", 60);
        qui = s.box(qui, "N° ou joueur", 32);
        texte.setResponder(v -> modifie());
        qui.setResponder(v -> modifie());
        fluxW = -1;
    }

    private void modifie() {
        modifAt = System.currentTimeMillis();
        page = 0;
        s.scroll = 0;
    }

    void ouvrir() { demander(); }

    void tick() {
        if (modifAt > 0 && System.currentTimeMillis() - modifAt > 400) demander();
    }

    /** Filtre posé depuis la fiche (« Journal › ») : toutes les entrées de ce N°. */
    void filtrerPid(String pid, String nom) {
        pidFiltre = pid == null || pid.isEmpty() ? null : pid;
        nomFiltre = nom;
        qui.setValue(pidFiltre == null ? "" : pidFiltre);
        cats.clear();
        periode = "tout";
        page = 0;
        modifAt = -1;
    }

    void retourRacine() {
        pidFiltre = null;
        qui.setValue("");
        page = 0;
        s.scroll = 0;
        demander();
        RebornSounds.uiClick();
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
        String w = qui.getValue().trim();
        if (!w.isEmpty()) {
            String d = w.startsWith("#") ? w.substring(1) : w;
            if (d.chars().allMatch(Character::isDigit)) o.addProperty("pid", d);
            else o.addProperty("joueur", w);
        }
        long du = depuis();
        if (du > 0) o.addProperty("du", du);
        if (!texte.getValue().isBlank()) o.addProperty("q", texte.getValue().trim());
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

    /** Réponse à afficher : celle du serveur, sinon l'ancien journal converti. */
    private JsonObject reponse(JsonObject snap) {
        JsonObject r = StaffClient.journal();
        if (r != null) {
            if (StaffScreen.num(r, "req") >= req) attente = false;
            return r;
        }
        attente = false;
        return ancien(snap);
    }

    String[] chemin() {
        if (pidFiltre != null) return new String[]{"Journal", "N° " + pidFiltre + (nomFiltre == null || nomFiltre.isEmpty() ? "" : "  ·  " + nomFiltre)};
        return new String[]{"Journal"};
    }

    String meta() {
        JsonObject r = reponse(StaffClient.snapshot());
        int t = StaffScreen.num(r, "total");
        String m = t + (t > 1 ? " entrées" : " entrée");
        if ("disque".equals(StaffScreen.str(r, "source"))) m += " · archives";
        return attente ? "recherche…" : m;
    }

    /* ================================================================ outils fixes */

    private void calculerFlux(int w) {
        if (w == fluxW) return;
        fluxW = w;
        List<String> ids = new ArrayList<>();
        List<String> labs = new ArrayList<>();
        ids.add("*");
        labs.add("Toutes");
        for (Object[] c : CATS) { ids.add((String) c[0]); labs.add((String) c[1]); }
        ids.add("|");
        labs.add("");
        for (String[] p : PERIODES) { ids.add("p:" + p[0]); labs.add(p[1]); }
        fluxIds = ids.toArray(new String[0]);
        fluxL = new int[fluxIds.length];
        for (int i = 0; i < fluxL.length; i++) fluxL[i] = "|".equals(fluxIds[i]) ? 8 : s.bw(labs.get(i));
        fluxPos = StaffScreen.flux(fluxL, w, 4);
    }

    private int rangsPuces() {
        calculerFlux(s.vx1() - s.vx0());
        return fluxPos[fluxPos.length - 1][1] + 1;
    }

    int hauteurOutils() { return 18 + rangsPuces() * 15 + 2 + 12 + 2; }

    void outils(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int x = s.vx0(), w = s.vx1() - s.vx0();
        int quiW = Math.max(96, Math.min(140, w / 4));
        DaKit.Etiq lab = DaKit.etiq(s.police(), "Chercher");
        DaKit.etiq(ctx, s.police(), lab, x, y + 7, DaKit.ENCRE_ROUGE);
        int fx = x + (int) Math.ceil(lab.w()) + 8;
        s.placer(ctx, texte, fx, y, w - (fx - x) - quiW - StaffScreen.GAP);
        s.placer(ctx, qui, x + w - quiW, y, quiW);
        s.tip(x + w - quiW, y, quiW, StaffScreen.H_CHAMP, "Un N° (#00042) ou un pseudo / nom RP : entrées où il est acteur ou cible.");
        y += 18;
        calculerFlux(w);
        String[] labs = new String[fluxIds.length];
        int k = 0;
        labs[k++] = "Toutes";
        for (Object[] c : CATS) labs[k++] = (String) c[1];
        labs[k++] = "";
        for (String[] p : PERIODES) labs[k++] = p[1];
        for (int i = 0; i < fluxIds.length; i++) {
            String id = fluxIds[i];
            int bx = x + fluxPos[i][0], by = y + fluxPos[i][1] * 15;
            if ("|".equals(id)) { ctx.fill(bx + 3, by + 2, bx + 4, by + 10, 0x50462810); continue; }
            boolean sel = "*".equals(id) ? cats.isEmpty() : id.startsWith("p:") ? periode.equals(id.substring(2)) : cats.contains(id);
            s.button(ctx, bx, by, fluxL[i], labs[i], sel ? "punish" : "plain", () -> puce(id), false);
            if (!"*".equals(id) && !id.startsWith("p:") && !sel) {
                ctx.fill(bx + 2, by + 9, bx + 5, by + 10, couleur(id));   // pastille de couleur de la catégorie
            }
        }
        y += rangsPuces() * 15 + 2;
        int[] c = colonnes();
        String[] tetes = {"Date", "Catégorie", "Acteur", "Action", "Cible · détail"};
        for (int i = 0; i < tetes.length; i++) DaKit.etiq(ctx, s.police(), DaKit.etiq(s.police(), tetes[i]), x + c[i * 2], y + 5, StaffScreen.INK_SOFT);
    }

    private void puce(String id) {
        if ("*".equals(id)) cats.clear();
        else if (id.startsWith("p:")) periode = id.substring(2);
        else if (!cats.remove(id)) cats.add(id);
        page = 0;
        s.scroll = 0;
        RebornSounds.uiClick();
        demander();
    }

    /** Colonnes : {x, largeur} × 5 (date, catégorie, acteur, action, cible + détail), relatives à vx0. */
    private int[] colonnes() {
        int w = s.vx1() - s.vx0();
        int dW = 52, cW = 60, aW = Math.max(84, Math.min(140, w * 22 / 100)), acW = Math.max(60, Math.min(100, w * 15 / 100));
        int x = 6;
        int[] c = new int[10];
        c[0] = x; c[1] = dW; x += dW + StaffScreen.GAP;
        c[2] = x; c[3] = cW; x += cW + StaffScreen.GAP;
        c[4] = x; c[5] = aW; x += aW + StaffScreen.GAP;
        c[6] = x; c[7] = acW; x += acW + StaffScreen.GAP;
        c[8] = x; c[9] = w - x - 4;
        return c;
    }

    /* ================================================================ corps */

    int corps(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        JsonObject r = reponse(snap);
        JsonArray es = StaffScreen.arr(r, "entries");
        if (es.isEmpty()) {
            boolean filtre = !cats.isEmpty() || !"tout".equals(periode) || !texte.getValue().isBlank() || !qui.getValue().isBlank();
            return s.vide(ctx, y, attente ? "Recherche dans le journal…" : filtre ? "Aucune entrée pour ces filtres." : "Le journal est vide.",
                    filtre ? "Élargis la période ou retire des catégories." : null);
        }
        int x = s.vx0(), w = s.vx1() - s.vx0();
        int[] c = colonnes();
        // passe 1 : fonds des lignes (un seul élément GUI)
        LotGui l = LotGui.ouvrir(ctx);
        for (int i = 0; i < es.size(); i++) {
            int ry = y + i * ROW;
            if (ry + ROW < s.vy0() || ry > s.vy1()) continue;
            JsonObject e = es.get(i).getAsJsonObject();
            boolean hov = s.hover(x, ry, w, ROW - 1) && s.mouseY >= s.vy0() && s.mouseY < s.vy1() && s.fenetre == null;
            l.fill(x, ry, x + w, ry + ROW - 1, hov ? StaffScreen.L_SURVOL : i % 2 == 0 ? StaffScreen.L_FOND : StaffScreen.L_ALT);
            l.fill(x, ry + ROW - 1, x + w, ry + ROW, StaffScreen.L_FILET);
            l.fill(x, ry, x + 2, ry + ROW - 1, couleur(StaffScreen.str(e, "cat")));
        }
        l.fermer();
        // passe 2 : textes et clics
        for (int i = 0; i < es.size(); i++) {
            int ry = y + i * ROW;
            if (ry + ROW < s.vy0() || ry > s.vy1()) continue;
            JsonObject e = es.get(i).getAsJsonObject();
            String cat = StaffScreen.str(e, "cat");
            s.txt8(ctx, dateCourte(StaffScreen.str(e, "date")), x + c[0], ry + 5, c[1], StaffScreen.INK_SOFT);
            String cl = label(cat);
            int col = couleur(cat);
            String clf = s.fit(cl, Math.round((c[3] - 6) / DaKit.S7));
            int tw = Math.round(s.police().width(RebornFont.body(clf)) * DaKit.S7) + 6;
            DrawHelpers.outlinedRect(ctx, x + c[2], ry + 3, tw, 10, 0, col);
            DaKit.texteEchelle(ctx, s.police(), RebornFont.body(clf), x + c[2] + 3, ry + 5, col, DaKit.S7);
            personne(ctx, StaffScreen.obj(e, "acteur"), x + c[4], ry + 4, c[5]);
            s.txtG(ctx, action(StaffScreen.str(e, "action")), x + c[6], ry + 4, c[7], StaffScreen.INK);
            String suite = cibleTexte(StaffScreen.obj(e, "cible"));
            String det = StaffScreen.str(e, "detail");
            String tout = suite.isEmpty() ? det : det.isEmpty() ? suite : suite + "  ·  " + det;
            s.txt8(ctx, tout, x + c[8], ry + 5, c[9], StaffScreen.INK);
            final JsonObject entree = e;
            if (s.visible(ry, ROW - 1)) s.hits.add(new StaffScreen.Hit(x, ry, w, ROW - 1, () -> detail(entree)));
        }
        return y + es.size() * ROW + 4;
    }

    private void personne(GuiGraphicsExtractor ctx, JsonObject p, int x, int y, int w) {
        if (p == null) { s.txt8(ctx, "—", x, y + 1, w, StaffScreen.INK_SOFT); return; }
        String pid = StaffScreen.str(p, "pid");
        String nom = !StaffScreen.str(p, "perso").isEmpty() ? StaffScreen.str(p, "perso") : StaffScreen.str(p, "pseudo");
        int cx = x;
        if (!pid.isEmpty()) cx += s.txtG(ctx, pid, cx, y, w, StaffScreen.PID) + 4;
        int g = StaffScreen.num(p, "grade");
        s.txt8(ctx, nom, cx, y + 1, x + w - cx, g > 0 ? StaffScreen.gradeColor(g) : StaffScreen.INK);
        String full = (pid.isEmpty() ? "" : "N° " + pid + " · ") + nom
                + (StaffScreen.str(p, "pseudo").isEmpty() || StaffScreen.str(p, "pseudo").equals(nom) ? "" : " (" + StaffScreen.str(p, "pseudo") + ")")
                + (g > 0 ? " · " + StaffScreen.str(p, "gradeName") : "");
        s.tip(x, y - 1, w, 10, full);
    }

    private static String cibleTexte(JsonObject c) {
        if (c == null) return "";
        String pid = StaffScreen.str(c, "pid");
        String nom = !StaffScreen.str(c, "perso").isEmpty() ? StaffScreen.str(c, "perso") : StaffScreen.str(c, "pseudo");
        return "→ " + (pid.isEmpty() ? "" : pid + " ") + nom;
    }

    /** « 14/10 21:03:12 » → « 14/10 21:03 ». */
    static String dateCourte(String d) {
        return d.length() >= 14 && d.charAt(d.length() - 3) == ':' ? d.substring(0, d.length() - 3) : d;
    }

    /** « DEGATS_RECUS » → « Degats recus » (les codes d'action du serveur sont en capitales sans accents). */
    static String action(String a) {
        if (a == null || a.isEmpty()) return "";
        String t = a.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static String label(String cat) {
        // libellés du serveur (snap.journalCats / réponse.cats) s'il en envoie, sinon ceux du protocole
        for (JsonObject src : new JsonObject[]{StaffClient.journal(), StaffClient.snapshot()}) {
            for (JsonElement e : StaffScreen.arr(src, src == StaffClient.snapshot() ? "journalCats" : "cats")) {
                JsonObject c = e.getAsJsonObject();
                if (cat.equals(StaffScreen.str(c, "id")) && !StaffScreen.str(c, "label").isEmpty()) return StaffScreen.str(c, "label");
            }
        }
        for (Object[] c : CATS) if (c[0].equals(cat)) return (String) c[1];
        return cat.isEmpty() ? "—" : cat.charAt(0) + cat.substring(1).toLowerCase();
    }

    static int couleur(String cat) {
        for (Object[] c : CATS) if (c[0].equals(cat)) return (int) c[2];
        return StaffScreen.INK_SOFT;
    }

    /* ================================================================ pied : pagination */

    void pied(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        JsonObject r = reponse(snap);
        int pages = Math.max(1, StaffScreen.num(r, "pages")), pg = Math.min(StaffScreen.num(r, "page"), pages - 1);
        int x = s.vx0(), w = s.vx1() - s.vx0();
        ctx.fill(x, y - 3, s.vx1(), y - 2, 0x30462810);
        int pw = s.bw("< Précédente"), nw = s.bw("Suivante >");
        s.refusVerrou = "Déjà à la première page.";
        s.button(ctx, x, y + 2, pw, "< Précédente", "plain", () -> aller(page - 1), pg <= 0);
        s.refusVerrou = "Déjà à la dernière page.";
        s.button(ctx, x + w - nw, y + 2, nw, "Suivante >", "plain", () -> aller(page + 1), pg >= pages - 1);
        s.refusVerrou = null;
        int total = StaffScreen.num(r, "total");
        int de = total == 0 ? 0 : pg * PAR + 1, a = Math.min(total, (pg + 1) * PAR);
        String m = "Page " + (pg + 1) + " sur " + pages + "   ·   " + de + "–" + a + " sur " + total;
        Component c = RebornFont.body(m);
        float mw = s.police().width(c) * DaKit.S8;
        DaKit.texteEchelle(ctx, s.police(), c, x + w / 2f - mw / 2f, y + 4, StaffScreen.INK_SOFT, DaKit.S8);
    }

    private void aller(int p) {
        page = Math.max(0, p);
        s.scroll = 0;
        RebornSounds.uiClick();
        demander();
    }

    /* ================================================================ détail d'une entrée */

    private void detail(JsonObject e) {
        RebornSounds.uiClick();
        s.fenetre = ctx -> dessinerDetail(ctx, e);
    }

    private void dessinerDetail(GuiGraphicsExtractor ctx, JsonObject e) {
        var font = s.police();
        int mw = Math.min(380, s.width - 40), x = s.width / 2 - mw / 2, cw = mw - 24, labW = 58;
        JsonObject ac = StaffScreen.obj(e, "acteur"), ci = StaffScreen.obj(e, "cible");
        String det = StaffScreen.str(e, "detail");
        List<String> lignes = det.isEmpty() ? List.of("—") : s.wrap(det, cw - labW);
        String[][] rows = {
                {"Date", StaffScreen.str(e, "date")},
                {"Catégorie", label(StaffScreen.str(e, "cat"))},
                {"Action", action(StaffScreen.str(e, "action"))},
                {"Acteur", personneLongue(ac)},
                {"Cible", ci == null ? "—" : personneLongue(ci)},
                {"Lieu", StaffScreen.str(e, "lieu").isEmpty() ? "—" : StaffScreen.str(e, "lieu")}};
        int h = 12 + 18 + rows.length * 12 + 4 + lignes.size() * 10 + 10 + StaffScreen.H_BTN_G + 14;
        int y = Math.max(10, s.height / 2 - h / 2);
        GardeArt.papierNu(ctx, x, y, mw, h);
        GardeArt.cadre(ctx, x, y, mw, h);
        int cx = x + 12, cy = y + 12;
        int col = couleur(StaffScreen.str(e, "cat"));
        ctx.fill(x + 2, y + 2, x + mw - 2, y + 4, col);
        GardeArt.losange(ctx, cx, cy + 1);
        DaKit.arcX1(ctx, font, DaKit.arcade(s.fitArc(label(StaffScreen.str(e, "cat")) + " · " + action(StaffScreen.str(e, "action")), cw - 12)),
                cx + 9, cy, DaKit.ENCRE_ROUGE, 0, false);
        cy += 18;
        for (String[] r : rows) {
            s.txt8(ctx, r[0], cx, cy + 1, labW - 6, StaffScreen.INK_SOFT);
            s.txt(ctx, r[1], cx + labW, cy, cw - labW, StaffScreen.INK);
            cy += 12;
        }
        s.txt8(ctx, "Détail", cx, cy + 1, labW - 6, StaffScreen.INK_SOFT);
        for (int i = 0; i < lignes.size(); i++) ctx.text(font, RebornFont.body(lignes.get(i)), cx + labW, cy + i * 10, StaffScreen.INK, false);
        cy += lignes.size() * 10 + 4;
        ctx.fill(cx, cy, cx + cw, cy + 1, 0x30462810);
        cy += 8;
        int bx = cx;
        String auid = ac == null ? "" : StaffScreen.str(ac, "uuid"), cuid = ci == null ? "" : StaffScreen.str(ci, "uuid");
        if (!auid.isEmpty()) bx += s.bouton(ctx, bx, cy, "Fiche de l'acteur", "plain", () -> { s.fenetre = null; s.openProfile(auid); }, false) + StaffScreen.GAP;
        if (!cuid.isEmpty()) bx += s.bouton(ctx, bx, cy, "Fiche de la cible", "plain", () -> { s.fenetre = null; s.openProfile(cuid); }, false) + StaffScreen.GAP;
        String apid = ac == null ? "" : StaffScreen.str(ac, "pid");
        if (!apid.isEmpty() && bx + s.bwG("Filtrer ce N°") + s.bwG("Fermer") + StaffScreen.GAP < cx + cw) {
            bx += s.bouton(ctx, bx, cy, "Filtrer ce N°", "plain", () -> {
                s.fenetre = null;
                filtrerPid(apid, ac == null ? "" : StaffScreen.str(ac, "perso"));
                demander();
            }, false) + StaffScreen.GAP;
        }
        int fw = s.bwG("Fermer");
        s.bouton(ctx, cx + cw - fw, cy, fw, "Fermer", "punish", () -> s.fenetre = null, false);
        int copW = s.bw("Copier");
        s.button(ctx, cx + cw - fw - StaffScreen.GAP - copW, cy + 2, copW, "Copier", "plain", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(StaffScreen.str(e, "date") + " [" + StaffScreen.str(e, "cat") + "] "
                    + StaffScreen.str(e, "action") + " · " + personneLongue(ac) + (ci == null ? "" : " → " + personneLongue(ci))
                    + (det.isEmpty() ? "" : " · " + det) + (StaffScreen.str(e, "lieu").isEmpty() ? "" : " · " + StaffScreen.str(e, "lieu")));
            StaffClient.toast("Entrée copiée.");
        }, false);
    }

    private static String personneLongue(JsonObject p) {
        if (p == null) return "—";
        String pid = StaffScreen.str(p, "pid"), perso = StaffScreen.str(p, "perso"), pseudo = StaffScreen.str(p, "pseudo");
        String g = StaffScreen.num(p, "grade") > 0 ? " · " + StaffScreen.str(p, "gradeName") : "";
        return (pid.isEmpty() ? "" : "N° " + pid + " · ") + (perso.isEmpty() ? pseudo : perso + (pseudo.isEmpty() ? "" : " (" + pseudo + ")")) + g;
    }

    /* ================================================================ banc */

    void debugDetail(int i) {
        JsonArray es = StaffScreen.arr(StaffClient.journal(), "entries");
        if (i < es.size()) detail(es.get(i).getAsJsonObject());
    }

    void debugTexte(String t) { texte.setValue(t); demander(); }

    /* ================================================================ repli : ancien journal du Poste de garde */

    private JsonObject ancien(JsonObject snap) {
        JsonObject r = new JsonObject();
        JsonArray out = new JsonArray();
        String q = texte.getValue().trim().toLowerCase(), w = qui.getValue().trim().toLowerCase();
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
            String txt = (StaffScreen.str(o, "type") + " " + StaffScreen.str(o, "detail")).toLowerCase();
            if (!q.isEmpty() && !txt.contains(q)) continue;
            if (!w.isEmpty() && !(StaffScreen.str(o, "name").toLowerCase().contains(w) || StaffScreen.str(o, "detail").toLowerCase().contains(w.replace("#", ""))))
                continue;
            JsonObject e = new JsonObject();
            e.addProperty("date", StaffScreen.str(o, "time"));
            e.addProperty("cat", cat);
            e.addProperty("action", StaffScreen.str(o, "type").toUpperCase().replace(' ', '_'));
            JsonObject a = new JsonObject();
            a.addProperty("pseudo", StaffScreen.str(o, "name"));
            a.addProperty("grade", StaffScreen.num(o, "grade"));
            a.addProperty("gradeName", StaffScreen.gradeName(StaffScreen.num(o, "grade")));
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
