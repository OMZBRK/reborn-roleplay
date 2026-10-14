package fr.reborn.hud.staff;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Recherche universelle du Poste de garde (fonctionnalité de la direction B, maquette B_01, posée dans le champ du
 * volet A) : un seul champ trouve <b>nom RP, pseudo, #N°</b> — y compris <b>hors ligne</b> (index des N° du serveur,
 * demande {@code cherche}) — et <b>des actions</b> : taper « gel » propose « Geler… » sur le joueur choisi ; « gel
 * kazu » cherche Kazu et propose Geler. Résultats surlignés, flèches pour choisir, Entrée pour ouvrir la fiche ou lancer
 * l'action.
 */
final class GardeRecherche {

    private final StaffScreen s;
    private String q = "";
    private int sel;
    private long modifAt = -1;
    private int req;
    /** Dernier joueur choisi (cible des actions trouvées quand la recherche ne nomme personne). */
    private JsonObject dernier;

    /** Éléments affichés, dans l'ordre du clavier : un joueur (JsonObject) ou une action. */
    private final List<Object> elements = new ArrayList<>();
    private JsonObject cibleActions;

    GardeRecherche(StaffScreen s) { this.s = s; }

    boolean active() { return !q.isBlank(); }

    void modifie(String v) {
        q = v == null ? "" : v;
        sel = 0;
        modifAt = System.currentTimeMillis();
    }

    void tick() {
        if (modifAt > 0 && System.currentTimeMillis() - modifAt > 300) {
            modifAt = -1;
            String t = termeJoueur();
            if (t.length() >= 2) {
                JsonObject o = new JsonObject();
                o.addProperty("a", "cherche");
                o.addProperty("q", t);
                o.addProperty("req", ++req);
                StaffClient.send(o);
            }
        }
    }

    /** Mots de la requête qui ne désignent pas une action. */
    private String termeJoueur() {
        List<String> m = new ArrayList<>();
        for (String w : q.trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (!w.startsWith("#") && !GardeActions.correspondant(w).isEmpty() && !joueurConnu(w)) continue;
            m.add(w);
        }
        return String.join(" ", m);
    }

    private List<GardeActions.Action> actionsDemandees() {
        Set<GardeActions.Action> out = new java.util.LinkedHashSet<>();
        for (String w : q.trim().split("\\s+")) {
            if (w.startsWith("#") || joueurConnu(w)) continue;
            out.addAll(GardeActions.correspondant(w));
        }
        return new ArrayList<>(out);
    }

    /** Un mot désigne-t-il (aussi) un joueur en ligne ? (« sora » n'est pas « sacoche ».) */
    private boolean joueurConnu(String w) {
        if (w.length() < 3) return false;
        for (JsonElement e : StaffScreen.arr(StaffClient.snapshot(), "players"))
            if (correspond(e.getAsJsonObject(), w)) return true;
        return false;
    }

    /** {@code #00042} / {@code 42} → N° (zéros ignorés) ; sinon pseudo ou nom RP, sans casse ni accents. */
    static boolean correspond(JsonObject p, String t) {
        if (t == null || t.isBlank()) return true;
        String x = GardeActions.sans(t);
        String d = x.startsWith("#") ? x.substring(1) : x;
        String pid = StaffScreen.str(p, "pid");
        if (!d.isEmpty() && d.chars().allMatch(Character::isDigit)) {
            if (pid.isEmpty()) return false;
            String a = d.replaceFirst("^0+(?=.)", ""), b = pid.replaceFirst("^0+(?=.)", "");
            return x.startsWith("#") ? a.equals(b) : pid.contains(d) || a.equals(b);
        }
        return GardeActions.sans(StaffScreen.str(p, "name")).contains(x) || GardeActions.sans(StaffScreen.str(p, "perso")).contains(x);
    }

    /** Résultats : joueurs en ligne qui correspondent, puis ceux du serveur hors ligne (réponse {@code cherche}). */
    private List<JsonObject> joueurs() {
        String t = termeJoueur();
        List<JsonObject> out = new ArrayList<>();
        if (t.isBlank()) return out;
        Set<String> vus = new HashSet<>();
        for (JsonElement e : StaffScreen.arr(StaffClient.snapshot(), "players")) {
            JsonObject p = e.getAsJsonObject();
            boolean ok = true;
            for (String w : t.split("\\s+")) if (!correspond(p, w)) { ok = false; break; }
            if (ok) { out.add(p); vus.add(StaffScreen.str(p, "pid") + "|" + StaffScreen.str(p, "uuid")); }
        }
        JsonObject r = StaffClient.cherche();
        if (r != null) for (JsonElement e : StaffScreen.arr(r, "joueurs")) {
            JsonObject p = e.getAsJsonObject();
            if (StaffScreen.bool(p, "online")) continue;
            boolean ok = true;
            for (String w : t.split("\\s+")) if (!correspond(p, w)) { ok = false; break; }
            if (ok && vus.add(StaffScreen.str(p, "pid") + "|" + StaffScreen.str(p, "uuid"))) out.add(p);
        }
        return out;
    }

    /* ================================================================ dessin */

    int corps(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        elements.clear();
        List<JsonObject> js = joueurs();
        List<GardeActions.Action> demandees = actionsDemandees();
        String terme = termeJoueur();
        int total = StaffScreen.arr(snap, "players").size();
        int nbHors = 0;
        for (JsonObject p : js) if (p.has("online") && !StaffScreen.bool(p, "online")) nbHors++;
        sel = Math.max(0, sel);

        if (!js.isEmpty() || !terme.isBlank()) {
            pz.capitales("Joueurs", ix, y + 4, 11, GRIS_2);
            pz.texteD(js.size() + " sur " + total + (nbHors > 0 ? " · hors ligne compris" : " en ligne"), ix + iw, y + 4, 12, GRIS_2, false);
            y += 10;
            if (js.isEmpty()) {
                pz.texte("Aucun joueur ne correspond à « " + terme + " ».", ix, y + 6, 12, GRIS, false);
                y += 14;
            }
            for (JsonObject p : js) {
                int i = elements.size();
                elements.add(p);
                boolean choisi = i == selIndex();
                int y0 = y;
                y = s.ligneJoueur(p, y, choisi, terme.split("\\s+")[0]);
                if (choisi) {
                    cibleActions = p;
                    dernier = p;
                    if (s.hover(s.px0() + 1, y0, s.pw() - 2, 23)) continue;
                }
            }
        }
        // actions : celles demandées (« gel »), sinon les principales du joueur choisi
        JsonObject cible = cibleActions != null && js.contains(cibleActions) ? cibleActions : !js.isEmpty() ? js.get(0) : dernier;
        if (cible != null && !(cible.has("online") && !StaffScreen.bool(cible, "online"))) {
            List<GardeActions.Action> acts = demandees.isEmpty()
                    ? List.of(GardeActions.ALLER, GardeActions.RAMENER, GardeActions.OBSERVER, GardeActions.GELER)
                    : demandees;
            y += 5;
            pz.capitales("Actions · " + StaffScreen.name(cible), ix, y + 4, 11, GRIS_2);
            y += 10;
            int g = StaffScreen.grade(snap);
            for (GardeActions.Action a : acts) {
                int i = elements.size();
                elements.add(a);
                boolean choisi = i == selIndex();
                String refus = GardeActions.refus(a, g, cible);
                int h = 17;
                boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
                if (choisi || hov) {
                    pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, choisi ? NUIT_3 : NUIT_2);
                    if (choisi) pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR);
                }
                pz.icone(a.icone(), ix, y + 4.5f, 8, refus != null ? GRIS_3 : "tp".equals(a.id()) ? OR : GRIS);
                String lab = a.libelle(cible);
                if (!demandees.isEmpty()) {
                    float lw = pz.largeur(lab, 14, false);
                    s.surligner(lab, q.trim().split("\\s+")[0], ix + 13, y + 8.5f, iw - 40, 14, refus != null ? GRIS_3 : CREME);
                    pz.texte(" · " + StaffScreen.name(cible), ix + 13 + lw, y + 8.5f, 12, GRIS_2, false);
                } else pz.texte(lab, ix + 13, y + 8.5f, 14, refus != null ? GRIS_3 : CREME, false);
                if (refus != null) pz.icone("verrou", ix + iw - 8, y + 5, 7, GRIS_3);
                else if (a.touche() != 0) s.touche(String.valueOf(a.touche()), ix + iw, y + 4);
                final JsonObject c = cible;
                if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h,
                        refus == null ? () -> GardeActions.lancer(s, a, c) : null, null, refus));
                y += h;
            }
            if (demandees.isEmpty()) {
                int i = elements.size();
                elements.add("plus");
                boolean choisi = i == selIndex();
                int h = 17;
                boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
                if (choisi || hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, choisi ? NUIT_3 : NUIT_2);
                pz.icone("plus", ix, y + 4.5f, 8, GRIS);
                pz.texte("Toutes les actions", ix + 13, y + 8.5f, 14, CREME, false);
                final JsonObject c = cible;
                final int yy = y;
                if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> s.ouvrirMenuJoueur(c, s.px0() - 4, yy + 17), null, null));
                y += h;
            }
        } else if (!demandees.isEmpty()) {
            y += 5;
            pz.capitales("Actions", ix, y + 4, 11, GRIS_2);
            y += 10;
            for (GardeActions.Action a : demandees) {
                pz.icone(a.icone(), ix, y + 4.5f, 8, GRIS_3);
                pz.texte(a.label(), ix + 13, y + 8.5f, 14, GRIS_3, false);
                y += 17;
            }
            pz.texte("Tape aussi un nom ou un #N° pour choisir le joueur.", ix, y + 5, 12, GRIS_2, false);
            y += 12;
        }
        return y + 4;
    }

    private int selIndex() { return elements.isEmpty() ? -1 : Math.min(sel, Math.max(0, elementsAttendus() - 1)); }

    private int nbPrec = 0;

    /** Nombre d'éléments de l'image précédente (la sélection reste dans les bornes). */
    private int elementsAttendus() { return Math.max(nbPrec, elements.size()); }

    void deplacer(int d) {
        nbPrec = elements.size();
        if (nbPrec == 0) return;
        sel = Math.floorMod(sel + d, nbPrec);
        if (elements.get(sel) instanceof JsonObject p) cibleActions = p;
        RebornSounds.uiClick();
    }

    boolean entree() {
        if (elements.isEmpty()) return false;
        Object o = elements.get(Math.min(sel, elements.size() - 1));
        JsonObject cible = cibleActions != null ? cibleActions : dernier;
        if (o instanceof JsonObject p) { s.openProfile(StaffScreen.str(p, "uuid")); return true; }
        if (o instanceof GardeActions.Action a && cible != null) { GardeActions.lancer(s, a, cible); return true; }
        if ("plus".equals(o) && cible != null) { s.ouvrirMenuJoueur(cible, s.px0() - 4, s.vy0() + 40); return true; }
        return false;
    }
}
