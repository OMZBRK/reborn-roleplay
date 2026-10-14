package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Fiche compacte d'un joueur dans le volet (maquette A_02) : « < Joueurs », visage, nom RP, « N° · grade · pseudo »,
 * étiquettes ; état (pastille) et distance ; PV et chakra en barres fines ; ping, temps de jeu, ryo ; <b>huit actions
 * éclair</b> (icône + mot, grille 4 × 2, la huitième ouvre le menu « Plus » à gauche du volet) ; puis les <b>sections
 * repliées</b> Notes · Sanctions · Journal du joueur · Radio · Comptes liés (aperçu d'une ligne, clic = déplier).
 * Raccourcis d'une lettre (fiche ouverte, aucun champ actif) : A aller, R ramener, O observer, G geler, S sacoche,
 * N écran noir, T noter, E écouter la radio, M annonce perso, B revenir, X sanctionner, Tab = « Plus ».
 */
final class GardeFiche {

    private final StaffScreen s;
    /** Section dépliée (null = toutes repliées). */
    String section;
    /** Joueur visé par le formulaire en cours (repli tant que la fiche n'est pas arrivée). */
    JsonObject cible;
    private String notesDemandees;
    private float plusY;
    /** Infraction choisie (confirmation de sanction dans le volet) et preuves cochées. */
    private JsonObject infraction;
    private final Set<Integer> preuves = new HashSet<>();
    private net.minecraft.client.gui.components.EditBox noteSanction;

    GardeFiche(StaffScreen s) { this.s = s; }

    void init() {
        noteSanction = s.box(noteSanction, "Note pour le casier (facultatif)", 160);
    }

    void ouvrir() {
        section = null;
        notesDemandees = null;
        infraction = null;
        cible = null;
    }

    void ouvrirSection(String id) {
        section = id;
        infraction = null;
        s.scroll = 0;
    }

    /** Profil reçu pour la fiche ouverte, sinon la cible connue (ligne de la liste). */
    private JsonObject profil() {
        JsonObject p = StaffClient.profile();
        if (p != null && s.profileUuid != null && s.profileUuid.equals(StaffScreen.str(p, "uuid"))) return p;
        if (cible != null && s.profileUuid != null && s.profileUuid.equals(StaffScreen.str(cible, "uuid"))) return cible;
        JsonObject snap = StaffClient.snapshot();
        for (JsonElement e : StaffScreen.arr(snap, "players"))
            if (s.profileUuid != null && s.profileUuid.equals(StaffScreen.str(e.getAsJsonObject(), "uuid"))) return e.getAsJsonObject();
        return null;
    }

    /* ================================================================ corps */

    int corps(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        // « < Joueurs »
        boolean hovB = s.hover(ix - 3, y - 2, 46, 12);
        pz.icone("gauche", ix - 2, y + 0.5f, 8, hovB ? CREME : GRIS);
        pz.texte("Joueurs", ix + 7, y + 4.5f, 13, hovB ? CREME : GRIS, false);
        if (s.cliquable(y - 2, 12)) s.hits.add(new StaffScreen.Hit(ix - 3, y - 2, 46, 12, s::retourListe, null, null));
        y += 15;
        JsonObject p = profil();
        if (p == null) return s.vide(y, "Chargement de la fiche…", null);
        boolean complet = StaffClient.profile() != null && s.profileUuid.equals(StaffScreen.str(StaffClient.profile(), "uuid"));
        int g = StaffScreen.grade(snap);

        // identité
        String uuid = StaffScreen.str(p, "uuid");
        pz.visage(uuid, ix, y, 24);
        float tx = ix + 31;
        s.txt(StaffScreen.name(p), tx, y + 5, ix + iw - tx, 18, CREME, true);
        String pid = StaffScreen.str(p, "pid");
        List<String> sous = new ArrayList<>();
        if (!pid.isEmpty()) sous.add("N° " + pid);
        if (!StaffScreen.str(p, "rank").isEmpty()) sous.add(StaffScreen.str(p, "rank"));
        sous.add(StaffScreen.str(p, "name"));
        s.txt(String.join(" · ", sous), tx, y + 16, ix + iw - tx, 13, GRIS, false);
        float ex = tx;
        for (String[] e : etiquettes(p)) {
            int col = Integer.parseUnsignedInt(e[1]);
            float w = pz.largeur(e[0], 11, false) + 6;
            if (ex + w > ix + iw) break;
            pz.cadre(ex, y + 23, w, 9, GardeArt.sombre(col, 0.55f));
            pz.texteC(e[0], ex + w / 2f, y + 27.5f, 11, col, false);
            s.tip(Math.round(ex), y + 23, Math.round(w), 9, e[2]);
            ex += w + 3;
        }
        y += 39;

        // état, distance
        Object[] et = StaffScreen.etatCourt(p);
        String st = StaffScreen.str(p, "state");
        String etat = st.isEmpty() ? (String) et[0] : st;
        if (StaffScreen.bool(p, "modeStaff") || StaffScreen.bool(p, "modeAnim")) etat = (String) et[0];
        boolean enLigne = StaffScreen.bool(p, "online") || p.has("ping");
        int col = enLigne ? (int) et[1] : GRIS_2;
        pz.pastille(ix + 2, y + 4.5f, 2, col);
        String dist = distance(uuid);
        float dw = dist.isEmpty() ? 0 : pz.texteD(dist, ix + iw, y + 4.5f, 12, GRIS_2, false);
        s.txt(enLigne ? etat : "Hors ligne", ix + 7, y + 4.5f, iw - 7 - dw - 6, 14, col, false);
        y += 13;
        // PV, chakra
        y = barre(pz, "PV", p, "hp", "hpMax", ROUGE, y);
        y = barre(pz, "Chakra", p, "chakra", "chakraMax", GLACE, y);
        y += 2;
        List<String> faits = new ArrayList<>();
        if (p.has("ping")) faits.add("Ping " + StaffScreen.num(p, "ping") + " ms");
        if (p.has("playtime")) faits.add((StaffScreen.num(p, "playtime") / 60) + " h de jeu");
        if (p.has("ryo")) faits.add(milliers(StaffScreen.num(p, "ryo")) + " ryo");
        if (!faits.isEmpty()) s.txt(String.join("   ·   ", faits), ix, y + 3.5f, iw, 12, GRIS_2, false);
        y += 12;
        // Nourriture (14/10) : entreprise(s) et satiété, une ligne.
        if (p.has("entreprise") || p.has("satiete")) {
            String ent = StaffScreen.str(p, "entreprise");
            String l = (ent.isEmpty() ? "" : "Entreprise : " + ent) + (p.has("satiete")
                ? (ent.isEmpty() ? "" : "   ·   ") + "Satiété " + StaffScreen.num(p, "satiete") + " %" : "");
            s.txt(l, ix, y + 3.5f, iw, 12, GRIS_2, false);
            y += 12;
        }
        pz.filetH(ix, ix + iw, y, FILET);
        y += 6;

        // huit actions éclair
        float cw = iw / 4f;
        GardeActions.Action[] grille = GardeActions.GRILLE;
        for (int i = 0; i < 8; i++) {
            float cx = ix + (i % 4) * cw;
            int cy = y + (i / 4) * 29;
            boolean plus = i == 7;
            GardeActions.Action a = plus ? null : grille[i];
            String refus = a == null ? null : GardeActions.refus(a, g, p);
            boolean menuOuvert = plus && s.menu != null && s.menu.source() == this;
            int X = Math.round(cx + 1), W = Math.round(cw - 2);
            boolean hov = s.hover(X, cy, W, 27) && s.cliquable(cy, 27);
            if (menuOuvert) {
                pz.fond(X, cy, X + W, cy + 27, NUIT_3);
                pz.cadre(X, cy, W, 27, OR_DOUX);
            } else if (hov) pz.fond(X, cy, X + W, cy + 27, NUIT_2);
            String ic = plus ? "plus" : a.icone();
            String lab = plus ? "Plus" : a.libelle(p).replace("…", "");
            if (lab.length() > 12) lab = switch (a.id()) { case "blind" -> "Lever le noir"; case "watch" -> "Fin d'obs."; default -> lab; };
            int icol = refus != null ? GRIS_3 : menuOuvert || !plus && "tp".equals(a.id()) ? OR : CREME;
            pz.icone(ic, cx + cw / 2f - 5, cy + 4, 10, icol);
            pz.texteC(s.fit(lab, cw - 4, 12, false), cx + cw / 2f, cy + 20.5f, 12, refus != null ? GRIS_3 : menuOuvert || hov ? CREME : GRIS, false);
            if (plus) {
                plusY = cy;
                s.hits.add(new StaffScreen.Hit(X, cy, W, 27, this::ouvrirPlus, null, null));
                s.tip(X, cy, W, 27, "Toutes les autres actions (Tab)");
            } else {
                final GardeActions.Action fa = a;
                s.hits.add(new StaffScreen.Hit(X, cy, W, 27, refus == null ? () -> GardeActions.lancer(s, fa, p) : null, null, refus));
                s.tip(X, cy, W, 27, a.libelle(p).replace("…", "") + "  (" + a.touche() + ")" + (refus != null ? " — " + refus : ""));
            }
        }
        y += 58 + 3;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 1;

        // formulaire de l'action choisie, dans le volet
        if (s.formulaire.actif()) return s.formulaire.dessiner(pz, ix, y + 7, iw) + 2;
        if (infraction != null) return confirmationSanction(snap, p, y + 7);

        // sections repliées
        y = sec(pz, "notes", "noter", "Notes", compteNotes(p), apercuNotes(p), y, yy -> notes(snap, p, yy));
        y = sec(pz, "sanctions", "sanction", "Sanctions", StaffScreen.num(p, "recordTotal"), apercuSanctions(p, complet), y, yy -> sanctions(snap, p, yy));
        y = sec(pz, "journal", "journal", "Journal du joueur", 0, pid.isEmpty() ? "Pas de N° : aucun personnage actif"
                : "Toutes les entrées du N° " + pid + " ›", y, null);
        y = sec(pz, "radio", "radio", "Radio", 0, GardeFormulaire.radioDisponible() ? "Écouter son escouade (motif, durée)"
                : "Écoute staff prête · la radio arrive bientôt", y, null);
        y = sec(pz, "comptes", "lien", "Comptes liés", StaffScreen.arr(p, "alts").size(), apercuComptes(p, complet), y, yy -> comptes(p, yy));
        // Monnaie et banque (15/10, maquette 06) : une section « Économie », pas un écran.
        if (p.has("eco")) y = sec(pz, "economie", "cercle", "Économie", 0, GardeEconomie.apercu(p), y, yy -> GardeEconomie.contenu(s, p, yy));
        return y + 2;
    }

    private static String milliers(int v) { return String.format("%,d", v).replace(',', ' ').replace(' ', ' '); }

    /** Distance au joueur s'il est chargé autour du staff. */
    private static String distance(String uuid) {
        try {
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return "";
            Player o = mc.level.getPlayerByUUID(UUID.fromString(uuid));
            if (o == null || o == mc.player) return "";
            return "à " + Math.round(o.distanceTo(mc.player)) + " blocs";
        } catch (RuntimeException e) { return ""; }
    }

    private int barre(GardeArt.Pinceau pz, String lab, JsonObject p, String k, String kMax, int col, int y) {
        int ix = s.ix(), iw = s.iw();
        pz.texte(lab, ix, y + 3, 12, GRIS, false);
        String val = p.has(k) ? milliers(StaffScreen.num(p, k)) + " / " + milliers(StaffScreen.num(p, kMax)) : "—";
        pz.texteD(val, ix + iw, y + 3, 12, GRIS, false);
        float bx = ix + 28, bw = iw - 28 - 50;
        float frac = p.has(k) && StaffScreen.num(p, kMax) > 0 ? Math.min(1f, StaffScreen.num(p, k) / (float) StaffScreen.num(p, kMax)) : 0f;
        pz.px(bx, y + 2.5f, bx + bw, y + 4f, 0xFF2C2024);
        if (frac > 0) pz.px(bx, y + 2.5f, bx + bw * frac, y + 4f, col);
        return y + 9;
    }

    /** Étiquettes : gelé, écran noir, mode staff / animation, étiquettes des notes. {texte, couleur, aide}. */
    private List<String[]> etiquettes(JsonObject p) {
        List<String[]> m = new ArrayList<>();
        if (GardeActions.gele(p)) {
            String par = StaffScreen.str(p, "gelePar"), motif = StaffScreen.str(p, "geleMotif");
            m.add(new String[]{"gelé", Integer.toUnsignedString(GLACE), "Gelé" + (par.isEmpty() ? "" : " par " + par) + (motif.isEmpty() ? "" : " : " + motif)});
        }
        if (StaffScreen.bool(p, "noir")) m.add(new String[]{"écran noir", Integer.toUnsignedString(NOIR_V), "Écran noir en cours"});
        if (StaffScreen.bool(p, "watching")) m.add(new String[]{"observé", Integer.toUnsignedString(OK), "Tu l'observes"});
        JsonArray et = StaffScreen.arr(p, "etiquettes");
        JsonObject n = StaffClient.notes(StaffScreen.str(p, "pid"));
        if (n != null) {
            et = new JsonArray();
            for (JsonElement e : StaffScreen.arr(n, "entries")) {
                String t = StaffScreen.str(e.getAsJsonObject(), "etiquette");
                if (!t.isEmpty() && !et.contains(e.getAsJsonObject().get("etiquette"))) et.add(t);
            }
        }
        for (JsonElement e : et) {
            String t = e.getAsString();
            int col = t.startsWith("surveill") ? TERRE : t.startsWith("avert") ? ROUGE : t.equals("event") ? ANIM : GRIS;
            m.add(new String[]{t, Integer.toUnsignedString(col), "Étiquette staff : " + t});
        }
        return m;
    }

    /* ---------------------------------------------------------------- sections */

    private interface Section { int dessiner(int y); }

    int sec(GardeArt.Pinceau pz, String id, String icone, String titre, int n, String apercu, int y, Section contenu) {
        int ix = s.ix(), iw = s.iw();
        boolean ouvert = id.equals(section);
        int h = 25;
        boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
        pz.icone(icone, ix, y + 7, 8, ouvert ? OR : GRIS);
        float lw = pz.texte(titre, ix + 13, y + 7.5f, 14, CREME, false);
        if (n > 0) pz.texte(String.valueOf(n), ix + 16 + lw, y + 7.5f, 12, OR, true);
        s.txt(apercu, ix + 13, y + 17, iw - 26, 12, GRIS_2, false);
        pz.icone(ouvert ? "bas" : "droite", ix + iw - 7, y + 8.5f, 7, GRIS_2);
        Runnable clic = switch (id) {
            case "journal" -> () -> {
                JsonObject p = profil();
                if (p == null || StaffScreen.str(p, "pid").isEmpty()) { StaffClient.toast("Pas de N° : aucun personnage actif."); return; }
                s.journalVue.filtrerPid(StaffScreen.str(p, "pid"), StaffScreen.name(p));
                s.switchTab("journal");
            };
            case "radio" -> () -> { JsonObject p = profil(); if (p != null) GardeActions.lancer(s, GardeActions.RADIO, p); };
            default -> () -> { section = ouvert ? null : id; infraction = null; RebornSounds.uiClick(); };
        };
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, clic, null, null));
        y += h;
        pz.filetH(ix + 13, ix + iw, y - 0.5f, SEP);
        if (ouvert && contenu != null) y = contenu.dessiner(y + 4) + 4;
        return y;
    }

    /* ---------------------------------------------------------------- aperçus */

    private int compteNotes(JsonObject p) {
        JsonObject n = StaffClient.notes(StaffScreen.str(p, "pid"));
        return n != null ? StaffScreen.arr(n, "entries").size() : StaffScreen.num(p, "notes");
    }

    private String apercuNotes(JsonObject p) {
        if (StaffScreen.str(p, "pid").isEmpty()) return "Pas de N° : aucun personnage actif";
        demanderNotes(StaffScreen.str(p, "pid"));
        JsonObject n = StaffClient.notes(StaffScreen.str(p, "pid"));
        JsonArray es = StaffScreen.arr(n, "entries");
        if (es.isEmpty()) return compteNotes(p) > 0 ? "Chargement…" : "Aucune note · clic pour en ajouter";
        JsonObject t = es.get(0).getAsJsonObject();
        return "« " + StaffScreen.str(t, "texte") + " » — " + StaffScreen.str(t, "auteur");
    }

    private static String apercuSanctions(JsonObject p, boolean complet) {
        JsonArray rec = StaffScreen.arr(p, "record");
        if (rec.isEmpty()) return complet ? "Casier vierge · clic pour sanctionner" : "…";
        JsonObject r = rec.get(0).getAsJsonObject();
        return StaffScreen.str(r, "text") + " · " + StaffScreen.str(r, "when");
    }

    private static String apercuComptes(JsonObject p, boolean complet) {
        JsonArray a = StaffScreen.arr(p, "alts");
        if (a.isEmpty()) return complet ? "Aucun autre compte vu avec la même IP" : "…";
        List<String> n = new ArrayList<>();
        for (JsonElement e : a) n.add(StaffScreen.str(e.getAsJsonObject(), "name"));
        return String.join(", ", n) + " · même IP";
    }

    private void demanderNotes(String pid) {
        if (pid.isEmpty() || pid.equals(notesDemandees)) return;
        notesDemandees = pid;
        JsonObject o = new JsonObject();
        o.addProperty("a", "notes");
        o.addProperty("pid", pid);
        StaffClient.send(o);
    }

    /* ---------------------------------------------------------------- contenus dépliés */

    private int notes(JsonObject snap, JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int x = s.ix() + 13, w = s.iw() - 13;
        String pid = StaffScreen.str(p, "pid");
        if (pid.isEmpty()) return y;
        s.motAction("+ Ajouter une note", x, y + 4, true, () -> GardeActions.lancer(s, GardeActions.NOTER, p));
        y += 12;
        JsonObject n = StaffClient.notes(pid);
        if (n == null) { pz.texte("Chargement des notes…", x, y + 4, 12, GRIS_2, false); return y + 10; }
        JsonArray es = StaffScreen.arr(n, "entries");
        if (es.isEmpty()) { pz.texte("Aucune note sur ce N°.", x, y + 4, 12, GRIS_2, false); return y + 10; }
        for (JsonElement e : es) {
            JsonObject t = e.getAsJsonObject();
            float cx = x;
            cx += pz.texte(StaffScreen.str(t, "date"), cx, y + 4, 12, GRIS_2, false) + 5;
            cx += pz.texte(StaffScreen.str(t, "auteur"), cx, y + 4, 12, StaffScreen.gradeColor(StaffScreen.num(t, "auteurGrade")), true) + 5;
            String etq = StaffScreen.str(t, "etiquette");
            if (!etq.isEmpty()) {
                float ew = pz.largeur(etq, 11, false) + 6;
                int col = etq.startsWith("surveill") ? TERRE : etq.startsWith("avert") ? ROUGE : GRIS;
                pz.cadre(cx, y, ew, 9, GardeArt.sombre(col, 0.55f));
                pz.texteC(etq, cx + ew / 2f, y + 4.5f, 11, col, false);
            }
            if (StaffScreen.bool(t, "suppr")) {
                String id = StaffScreen.str(t, "id");
                float sw = pz.largeur("Supprimer", 12, false);
                s.motCouleur("Supprimer", x + w - sw, y + 4, 12, GRIS_2, () -> s.formulaire.ouvrir("suppr", "Supprimer la note ?",
                        "« " + StaffScreen.str(t, "texte") + " »", "Supprimer", true, null, List.of(), List.of(), v -> supprimerNote(pid, id)));
            }
            y += 10;
            for (String l : s.wrap(StaffScreen.str(t, "texte"), w, 12)) { pz.texte(l, x, y + 3, 12, CREME, false); y += 9; }
            y += 4;
            pz.filetH(x, x + w, y - 1.5f, SEP);
        }
        return y;
    }

    void ajouterNote(String pid, String texte, String etiquette) {
        if (pid == null || pid.isEmpty() || texte == null || texte.isBlank()) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "note_add");
        o.addProperty("pid", pid);
        o.addProperty("texte", texte.trim());
        if (etiquette != null && !etiquette.isEmpty()) o.addProperty("etiquette", etiquette);
        StaffClient.send(o);
        section = "notes";
    }

    private void supprimerNote(String pid, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "note_del");
        o.addProperty("pid", pid);
        o.addProperty("id", id);
        StaffClient.send(o);
    }

    private int sanctions(JsonObject snap, JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int x = s.ix() + 13, w = s.iw() - 13;
        JsonArray rec = StaffScreen.arr(p, "record");
        pz.texte(StaffScreen.num(p, "recordTotal") + " infraction(s) · " + StaffScreen.num(p, "recordActive") + " active(s)", x, y + 4, 12, GRIS, false);
        y += 11;
        for (JsonElement e : rec) {
            JsonObject r = e.getAsJsonObject();
            boolean act = StaffScreen.bool(r, "active");
            pz.pastille(x + 1.5f, y + 4, 1.2f, act ? ROUGE : GRIS_3);
            float stw = pz.texteD(StaffScreen.str(r, "status"), x + w, y + 4, 11, act ? ROUGE : GRIS_2, false);
            s.txt(StaffScreen.str(r, "when") + " · " + StaffScreen.str(r, "text"), x + 6, y + 4, w - 6 - stw - 4, 12, CREME, false);
            y += 10;
        }
        JsonArray msgs = StaffScreen.arr(p, "msgs");
        if (!msgs.isEmpty()) {
            y += 3;
            pz.capitales("Messages récents", x, y + 3, 11, GRIS_2);
            y += 9;
            for (int i = 0; i < Math.min(4, msgs.size()); i++) {
                JsonObject m = msgs.get(i).getAsJsonObject();
                s.txt(StaffScreen.str(m, "time") + " · " + StaffScreen.str(m, "text"), x, y + 3.5f, w, 12, GRIS, false);
                y += 9;
            }
        }
        y += 4;
        pz.capitales("Sanctionner : choisis l'infraction", x, y + 3, 11, GRIS_2);
        y += 10;
        JsonArray offs = StaffScreen.arr(p, "offenses");
        if (offs.isEmpty()) { pz.texte("Chargement…", x, y + 4, 12, GRIS_2, false); return y + 10; }
        for (JsonElement e : offs) {
            JsonObject o = e.getAsJsonObject();
            boolean locked = StaffScreen.bool(o, "locked");
            int tone = locked ? GRIS_3 : switch (StaffScreen.str(o, "tone")) { case "warn" -> TERRE; case "mute" -> 0xFFE0884C; default -> ROUGE; };
            int h = 15;
            boolean hov = !locked && s.hover(x - 2, y, w + 2, h) && s.cliquable(y, h);
            if (hov) s.pz.fond(x - 2, y, x + w, y + h, NUIT_3);
            int st = StaffScreen.num(o, "strikes");
            String next = StaffScreen.str(o, "next");
            float nw = pz.largeur(next, 11, false) + 6;
            pz.cadre(x + w - nw, y + 3, nw, 9, GardeArt.sombre(tone, 0.6f));
            pz.texteC(next, x + w - nw / 2f, y + 7.5f, 11, tone, false);
            float lw = s.txt(StaffScreen.str(o, "name"), x, y + 7.5f, w - nw - 50, 13, locked ? GRIS_3 : CREME, false);
            pz.texte(st == 0 ? "" : st + " strike" + (st > 1 ? "s" : ""), x + lw + 5, y + 7.5f, 11, GRIS_2, false);
            if (locked) pz.icone("verrou", x + w - nw - 9, y + 4.5f, 6, GRIS_3);
            final JsonObject off = o;
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(x - 2, y, w + 2, h, locked ? null : () -> {
                infraction = off;
                preuves.clear();
                noteSanction.setValue("");
                RebornSounds.uiClick();
            }, null, "Réservé au grade " + StaffScreen.str(o, "lockGrade") + "."));
            y += h;
        }
        return y;
    }

    /** Confirmation d'une sanction dans le volet : motif, sanction prévue, preuves à cocher, note. */
    private int confirmationSanction(JsonObject snap, JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int x = s.ix(), w = s.iw();
        pz.icone("sanction", x, y + 1, 8, ROUGE);
        s.txt("Sanctionner " + StaffScreen.name(p), x + 14, y + 5, w - 14, 15, CREME, true);
        y += 13;
        pz.texte("Motif", x, y + 4, 12, GRIS, false);
        s.txt(StaffScreen.str(infraction, "name"), x + 52, y + 4, w - 52, 13, CREME, false);
        y += 10;
        pz.texte("Sanction", x, y + 4, 12, GRIS, false);
        s.txt(StaffScreen.str(infraction, "next") + " · strike " + (StaffScreen.num(infraction, "strikes") + 1), x + 52, y + 4, w - 52, 13, ROUGE, false);
        y += 14;
        JsonArray msgs = StaffScreen.arr(p, "msgs");
        pz.texte("Preuves : coche les messages qui servent (" + preuves.size() + ")", x, y + 3, 12, GRIS, false);
        y += 9;
        if (msgs.isEmpty()) { pz.texte("Aucun message récent.", x, y + 4, 12, GRIS_2, false); y += 10; }
        for (int i = 0; i < Math.min(4, msgs.size()); i++) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            boolean on = preuves.contains(i);
            int h = 14;
            boolean hov = s.hover(x - 2, y, w + 2, h) && s.cliquable(y, h);
            if (hov) pz.fond(x - 2, y, x + w, y + h, NUIT_2);
            pz.fond(x, y + 3, x + 8, y + 11, on ? LAQUE : NUIT_2);
            pz.cadre(x, y + 3, 8, 8, on ? LAQUE_HAUT : FILET_2);
            if (on) pz.icone("croix", x + 1, y + 4, 6, CREME);
            s.txt(StaffScreen.str(m, "time") + " · " + StaffScreen.str(m, "text"), x + 12, y + 7, w - 12, 12, on ? CREME : GRIS, false);
            final int idx = i;
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(x - 2, y, w + 2, h, () -> { if (!preuves.remove(idx)) preuves.add(idx); RebornSounds.uiClick(); }, null, null));
            y += h;
        }
        y += 4;
        pz.texte("Note pour le casier", x, y + 3, 12, GRIS, false);
        y += 8;
        s.champ(noteSanction, x, y, w, null);
        y += 24;
        int bw = s.largeurBouton("Sanctionner  ·  Entrée");
        s.bouton(x + w - bw, y, bw, "Sanctionner  ·  Entrée", true, () -> envoyerSanction(p, false), false);
        float aw = pz.largeur("Avertir seulement", 13, false);
        s.motCouleur("Avertir seulement", x + w - bw - 10 - aw, y + 8.5f, 13, CREME, () -> envoyerSanction(p, true));
        s.motCouleur("Annuler", x, y + 8.5f, 13, GRIS, () -> infraction = null);
        return y + 23;
    }

    private void envoyerSanction(JsonObject p, boolean avertir) {
        if (infraction == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "sanction");
        o.addProperty("t", StaffScreen.str(p, "uuid"));
        o.addProperty("o", StaffScreen.str(infraction, "id"));
        o.addProperty("warnOnly", avertir);
        o.addProperty("note", noteSanction.getValue());
        JsonArray ev = new JsonArray();
        JsonArray msgs = StaffScreen.arr(p, "msgs");
        for (int i : preuves) if (i < msgs.size()) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            ev.add(StaffScreen.str(m, "time") + " — " + StaffScreen.str(m, "text"));
        }
        o.add("ev", ev);
        StaffClient.send(o);
        infraction = null;
        RebornSounds.confirm();
    }

    private int comptes(JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int x = s.ix() + 13, w = s.iw() - 13;
        JsonArray alts = StaffScreen.arr(p, "alts");
        if (alts.isEmpty()) { pz.texte("Aucun autre compte vu avec la même IP.", x, y + 4, 12, GRIS_2, false); return y + 10; }
        for (JsonElement e : alts) {
            JsonObject a = e.getAsJsonObject();
            float nw = pz.texte(StaffScreen.str(a, "name"), x, y + 4, 13, CREME, false);
            pz.texte("IP partagée · vu " + StaffScreen.str(a, "seen"), x + nw + 6, y + 4, 12, GRIS_2, false);
            float fw = pz.largeur("Fiche", 13, false);
            String id = StaffScreen.str(a, "uuid");
            s.motAction("Fiche", x + w - fw, y + 4, false, () -> s.openProfile(id));
            y += 12;
        }
        return y;
    }

    /* ================================================================ menu « Plus », raccourcis */

    void ouvrirPlus() {
        JsonObject p = profil();
        if (p == null) return;
        if (s.menu != null && s.menu.source() == this) { s.menu = null; return; }
        int g = StaffScreen.grade(StaffClient.snapshot());
        List<StaffScreen.Item> items = new ArrayList<>();
        for (GardeActions.Action a : GardeActions.PLUS) items.add(a == null ? null : s.item(a, p, g));
        s.ouvrirMenu(items, s.px0() - 4, Math.round(plusY) - 20, this);
    }

    boolean raccourci(int key) {
        JsonObject p = profil();
        if (p == null) return false;
        char c = (char) key;
        if (key < GLFW.GLFW_KEY_A || key > GLFW.GLFW_KEY_Z) return false;
        for (GardeActions.Action a : GardeActions.TOUTES) {
            if (a.touche() == c) {
                GardeActions.lancer(s, a, p);
                return true;
            }
        }
        return false;
    }

    boolean entree() {
        if (infraction != null && s.profileUuid != null) {
            JsonObject p = profil();
            if (p != null) { envoyerSanction(p, false); return true; }
        }
        return false;
    }

    void debugInfraction(int i) {
        JsonObject p = StaffClient.profile();
        if (p == null) return;
        JsonArray offs = StaffScreen.arr(p, "offenses");
        if (i < offs.size()) { infraction = offs.get(i).getAsJsonObject(); preuves.clear(); preuves.add(0); preuves.add(1); }
    }
}
