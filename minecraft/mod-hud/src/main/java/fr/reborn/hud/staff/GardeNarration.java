package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.recit.Gabarit;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.recit.RecitHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Outil « Narration » du volet (lot 2) : un texte en grand au centre de l'écran des joueurs, dans l'allure de la
 * narration ({@link Gabarit#NARRATION}, plein écran, blanc pur ; jamais celle des stèles). Protocole : requêtes
 * {@code narration}, {@code narration_fin} et {@code narrations} sur {@code reborn:staff}, réponses {@code toast} et
 * {@code narrations} (PLUGINS_REFERENCE §4 bis.2 et §4 bis.3 bis).
 *
 * <ul>
 *   <li><b>Texte</b> sur plusieurs lignes ({@link GardeEditeur}), compteur sur 1 000 caractères, pastilles de couleur
 *       (blanc, rouge, or, ombre, gris) qui colorent la sélection ; il part en segments, jamais en balises ;</li>
 *   <li><b>Portée</b> : un joueur (N° ou pseudo, comme les annonces), ici (rayon de 1 à 64 blocs, 30 par défaut), ce
 *       monde, tout le serveur (confirmation dans le volet qui montre ce qui partira, {@code confirme:true} seulement
 *       après) ; <b>durée</b> : Auto · 15 · 30 · 60 · 90 s · Tenir, ou une valeur de 2 à 180 s ; <b>mode</b> : à la suite
 *       ou remplacer ; option « Aussi dans le tchat » ;</li>
 *   <li><b>aperçu à taille réelle</b> sur le jeu, à gauche du volet : le même rendu que les joueurs
 *       ({@link RecitHud.Apercu} : mêmes pages, mêmes coupes, même moteur), à sa place verticale ;</li>
 *   <li><b>en cours</b> : les narrations actives (temps restant, portée, auteur — staff seulement), « Arrêter » quand le
 *       serveur le permet, « Tout arrêter » (Modérateur et plus) ou « Arrêter les miennes » ; relu à l'ouverture, après
 *       chaque envoi ou arrêt (le serveur répond par l'état) et toutes les 5 s.</li>
 * </ul>
 * Le volet n'offre que ce que le grade permet ({@link #portees}) : Helper → un joueur ; en mode animation → aussi ici
 * (64 blocs au plus) ; Modérateur et plus → toutes les portées et « Tenir ». Le serveur revérifie tout.
 */
final class GardeNarration {

    private static final String[][] PORTEES = {{"joueur", "Un joueur"}, {"ici", "Ici"}, {"monde", "Ce monde"}, {"tous", "Tout le serveur"}};
    private static final String[][] DUREES = {{"0", "Auto"}, {"15", "15 s"}, {"30", "30 s"}, {"60", "60 s"}, {"90", "90 s"}, {"-1", "Tenir"}};
    private static final String[][] MODES = {{"file", "À la suite"}, {"remplace", "Remplacer"}};
    /** Rayon de « ici » : défaut et bornes du serveur ({@code Narrations.RAYON_*}). */
    static final int RAYON_DEFAUT = 30, RAYON_MAX = 64;
    /** Relecture de l'état {@code narrations} tant que l'outil est ouvert (ticks). */
    private static final int RELIRE = 100;

    private final StaffScreen s;
    final GardeEditeur editeur = new GardeEditeur(Recit.MAX_CARACTERES);
    private final RecitHud.Apercu apercu = new RecitHud.Apercu();
    /** Frappe : texte vu à la dernière image, depuis quand, et texte posé dans l'aperçu (250 ms après la dernière touche). */
    private static final long ATTENTE_FRAPPE_MS = 250;
    private List<Recit.Segment> segsVus, segsPoses;
    private long segsVusA;
    /** Aperçu montré réduit (il ne tient pas à gauche du volet). */
    private boolean reduit;
    private EditBox cible, rayonBox, dureeBox;
    String portee = "joueur", duree = "0", mode = "file";
    private boolean tchat, focus, glisse, apercuVisible = true;
    /** « Plein écran » : l'aperçu seul, à sa vraie place, volet caché ; un clic ou une touche le referme. */
    boolean pleinEcran;
    /** Zone du texte (GUI, à la dernière image) : clics et glisser. */
    private int ex0, ey0, ex1, ey1;
    private double clicX, clicY;
    private boolean clicMaj;
    /** Dernier envoi : le texte s'efface quand l'état {@code narrations} confirme l'envoi (refus : il reste). */
    private long envoiA;
    private int versionEnvoyee = -1;
    private int ticks;

    GardeNarration(StaffScreen s) { this.s = s; }

    void init() {
        cible = s.box(cible, "#00042 ou pseudo", 32);
        rayonBox = s.box(rayonBox, "30", 2);
        rayonBox.setResponder(v -> chiffres(rayonBox, v));
        if (rayonBox.getValue().isEmpty()) rayonBox.setValue(String.valueOf(RAYON_DEFAUT));
        dureeBox = s.box(dureeBox, "libre", 3);
        dureeBox.setResponder(v -> {
            if (chiffres(dureeBox, v)) return;
            if (!v.isEmpty()) duree = "libre";
            else if ("libre".equals(duree)) duree = "0";
        });
    }

    /** Champ numérique : tout ce qui n'est pas un chiffre est retiré (true si la valeur a été corrigée). */
    private static boolean chiffres(EditBox b, String v) {
        String d = v.replaceAll("[^0-9]", "");
        if (d.equals(v)) return false;
        b.setValue(d);
        return true;
    }

    /** Ouverture de l'outil : état demandé, saisie prête. */
    void ouvrir() {
        StaffClient.send("narrations");
        ticks = 0;
        focus = true;
        s.setFocused(null);
    }

    void tick() {
        if (++ticks % RELIRE == 0) StaffClient.send("narrations");
        JsonObject n = StaffClient.narrations();
        // envoi confirmé par le serveur (« dernier » = l'id créé) : le texte envoyé s'efface, sauf s'il a changé depuis
        if (versionEnvoyee >= 0 && n != null && StaffClient.narrationsA() >= envoiA && !StaffScreen.str(n, "dernier").isEmpty()) {
            if (editeur.version() == versionEnvoyee) editeur.effacer();
            versionEnvoyee = -1;
        }
    }

    /** Outil quitté ou volet fermé : aperçu libéré, saisie rendue. */
    void liberer() {
        apercu.liberer();
        segsVus = segsPoses = null;
        reduit = false;
        focus = false;
        glisse = false;
        pleinEcran = false;
    }

    /* ================================================================ droits (miroir du serveur, qui reste juge) */

    private static boolean anim(JsonObject snap) { return StaffScreen.bool(snap, "modeAnim"); }

    /**
     * Portées que le spectateur peut utiliser : Helper → joueur ; Helper en mode animation → aussi ici ; Modérateur →
     * toutes. Le Poste de garde est fermé à un animateur sans grade staff (le serveur refuse de l'ouvrir) : lui passe par
     * {@code /narration}.
     */
    private static boolean permise(String p, JsonObject snap) {
        int g = StaffScreen.grade(snap);
        return switch (p) {
            case "joueur" -> g >= 1 || anim(snap);
            case "ici" -> g >= 2 || anim(snap);
            default -> g >= 2;
        };
    }

    private static String[][] portees(JsonObject snap) {
        int n = 0;
        for (String[] p : PORTEES) if (permise(p[0], snap)) n++;
        String[][] out = new String[n][];
        int i = 0;
        for (String[] p : PORTEES) if (permise(p[0], snap)) out[i++] = p;
        return out;
    }

    private static boolean tenirPermis(JsonObject snap) { return StaffScreen.grade(snap) >= 2; }

    /* ================================================================ valeurs */

    private int rayon() {
        try { return Math.max(1, Math.min(RAYON_MAX, Integer.parseInt(rayonBox.getValue().trim()))); }
        catch (NumberFormatException e) { return RAYON_DEFAUT; }
    }

    private void rayon(int r) { rayonBox.setValue(String.valueOf(Math.max(1, Math.min(RAYON_MAX, r)))); }

    /** Durée envoyée ({@code s}) : 0 auto, -1 tenir, 2 à 180 ; {@code Integer.MIN_VALUE} si la valeur libre est fausse. */
    private int secondes() {
        if (!"libre".equals(duree)) return Integer.parseInt(duree);
        try {
            int v = Integer.parseInt(dureeBox.getValue().trim());
            return v >= 2 && v <= 180 ? v : Integer.MIN_VALUE;
        } catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }

    /** « auto · 14 s », « 30 s », « tenue (10 min au plus) ». */
    private String libelleDuree(int sec) {
        if (sec == 0) return "auto · " + Math.round(Recit.dureeAutoMs(editeur.segments(), Gabarit.NARRATION) / 1000.0) + " s";
        if (sec == -1) return "tenue jusqu'à l'arrêt (10 min au plus)";
        return sec + " s";
    }

    /* ================================================================ corps */

    int corps(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        String[][] pts = portees(snap);
        if (pts.length == 0) return s.vide(y, "Réservé au staff.", "Helper et plus. Un animateur sans grade passe par /narration.");
        if (!permise(portee, snap)) portee = pts[0][0];
        if ("-1".equals(duree) && !tenirPermis(snap)) duree = "0";
        for (String l : s.wrap("Un texte en grand au centre de l'écran des joueurs. Ton nom n'y apparaît pas (il est au journal).", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 5;
        // ------------------------------------------------------------------ texte
        int n = editeur.caracteres();
        pz.texte("Texte", ix, y + 3, 12, GRIS, false);
        pz.texteD(n + " / " + Recit.MAX_CARACTERES, ix + iw, y + 3, 11, n >= Recit.MAX_CARACTERES ? ROUGE : GRIS_2, false);
        y += 8;
        y = zoneTexte(y, ix, iw);
        y = pastilles(y + 3, ix, iw);
        y += 4;
        // ------------------------------------------------------------------ portée, durée, mode, tchat
        y = choix("Portée", pts, portee, v -> portee = v, y);
        if (pts.length == 1) {
            String note = StaffScreen.grade(snap) >= 1 && !anim(snap)
                    ? "Ici, ce monde et tout le serveur : Modérateur et plus (ici aussi en mode animation)."
                    : "Ce monde et tout le serveur : Modérateur et plus.";
            y = note(note, y);
        } else if (anim(snap) && StaffScreen.grade(snap) < 2) {
            y = note("Mode animation : un joueur, ou ici (64 blocs au plus).", y);
        }
        if ("joueur".equals(portee)) {
            pz.texte("Joueur", ix, y + 8.5f, 12, GRIS, false);
            s.champ(cible, ix + 36, y, iw - 36, null);
            y += 22;
        } else if ("ici".equals(portee)) {
            pz.texte("Rayon", ix, y + 8.5f, 12, GRIS, false);
            int bx = ix + 36;
            bx += petit(bx, y + 3, "−", () -> rayon(rayon() - 5)) + 3;
            s.champ(rayonBox, bx, y, 26, null);
            bx += 29;
            bx += petit(bx, y + 3, "+", () -> rayon(rayon() + 5)) + 6;
            pz.texte("blocs (1 à " + RAYON_MAX + ")", bx, y + 8.5f, 12, GRIS_2, false);
            y += 22;
        }
        y = durees(snap, y);
        y = choix("Mode", MODES, mode, v -> mode = v, y);
        String aide = "file".equals(mode) ? "Passe après la narration en cours (8 en attente au plus)."
                : StaffScreen.grade(snap) >= 2 ? "Coupe celle à l'écran et vide la file."
                : "Ne coupe que les tiennes, pour les mêmes joueurs.";
        y = note(aide, y);
        y = caseTchat(y, ix);
        // ------------------------------------------------------------------ aperçu
        y = ligneApercu(y, ix, iw);
        // ------------------------------------------------------------------ envoyer
        int sec = secondes();
        boolean vide = editeur.vide(), sansCible = "joueur".equals(portee) && cible.getValue().isBlank();
        boolean dureeFausse = sec == Integer.MIN_VALUE;
        String lab = switch (portee) {
            case "joueur" -> "Envoyer au joueur";
            case "ici" -> "Envoyer ici";
            case "monde" -> "Envoyer au monde";
            default -> "Envoyer à tous…";
        };
        int bw = s.largeurBouton(lab);
        s.refusVerrou = vide ? "Écris d'abord le texte." : sansCible ? "Choisis d'abord le joueur (N° ou pseudo)."
                : "Durée : un nombre de 2 à 180 secondes.";
        s.bouton(ix + iw - bw, y, bw, lab, true, this::envoyer, vide || sansCible || dureeFausse);
        s.refusVerrou = null;
        if (!vide) s.motCouleur("Effacer", ix, y + 8.5f, 12, GRIS_2, () -> { editeur.effacer(); RebornSounds.uiClick(); });
        y += 25;
        // ------------------------------------------------------------------ en cours
        pz.filetH(ix, ix + iw, y - 0.5f, FILET);
        return enCours(snap, y + 6, ix, iw);
    }

    /** Précision en petit gris sous une ligne de choix (alignée sur les mots). */
    private int note(String t, int y) {
        for (String l : s.wrap(t, s.iw() - 36, 11)) {
            s.pz.texte(l, s.ix() + 36, y + 1, 11, GRIS_2, false);
            y += 8;
        }
        return y + 5;
    }

    /** Ligne de choix en mots (label à gauche, retour à la ligne si besoin), comme l'outil Annonces. */
    private int choix(String label, String[][] ch, String actuel, java.util.function.Consumer<String> set, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        pz.texte(label, ix, y + 3, 12, GRIS, false);
        float ox = ix + 36;
        for (String[] c : ch) {
            float w = pz.largeur(c[1], 13, true);
            if (ox + w > ix + iw) { ox = ix + 36; y += 12; }
            ox += s.mot(c[1], ox, y + 3, 13, c[0].equals(actuel), () -> set.accept(c[0])) + 9;
        }
        return y + 15;
    }

    /** Durées : Auto (avec sa valeur), 15 à 90 s, Tenir (Modérateur), et une valeur libre de 2 à 180 s. */
    private int durees(JsonObject snap, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        pz.texte("Durée", ix, y + 3, 12, GRIS, false);
        float ox = ix + 36;
        for (String[] d : DUREES) {
            if ("-1".equals(d[0]) && !tenirPermis(snap)) continue;
            String lab = "0".equals(d[0]) && !editeur.vide() ? "Auto · " + Math.round(Recit.dureeAutoMs(editeur.segments(), Gabarit.NARRATION) / 1000.0) + " s" : d[1];
            float w = pz.largeur(lab, 13, true);
            if (ox + w > ix + iw) { ox = ix + 36; y += 12; }
            ox += s.mot(lab, ox, y + 3, 13, d[0].equals(duree), () -> { duree = d[0]; dureeBox.setValue(""); duree = d[0]; }) + 9;
        }
        y += 11;
        pz.texte("ou", ix + 36, y + 8.5f, 12, GRIS_2, false);
        s.champ(dureeBox, ix + 50, y, 34, null);
        pz.texte("secondes (2 à 180)", ix + 88, y + 8.5f, 12, "libre".equals(duree) && secondes() == Integer.MIN_VALUE ? ROUGE : GRIS_2, false);
        return y + 23;
    }

    /** Petit bouton carré (11 de haut) ; renvoie sa largeur. */
    private int petit(int x, int y, String label, Runnable r) {
        GardeArt.Pinceau pz = s.pz;
        int w = 11, h = 11;
        boolean hov = s.hover(x, y, w, h) && s.cliquable(y, h);
        pz.fond(x, y, x + w, y + h, hov ? NUIT_3 : NUIT_2);
        pz.cadre(x, y, w, h, hov ? OR_DOUX : FILET_2);
        pz.texteC(label, x + w / 2f, y + 5.5f, 12, CREME, false);
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(x, y, w, h, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
        return w;
    }

    private int caseTchat(int y, int ix) {
        GardeArt.Pinceau pz = s.pz;
        String lab = "Aussi dans le tchat";
        int w = 10 + Math.round(pz.largeur(lab, 13, false));
        boolean hov = s.hover(ix, y - 1, w, 11) && s.cliquable(y - 1, 11);
        pz.cadre(ix, y + 0.5f, 7, 7, hov ? OR_DOUX : FILET_2);
        if (tchat) pz.fond(ix + 2, y + 2, ix + 5, y + 6, OR);
        pz.texte(lab, ix + 11, y + 4, 13, hov || tchat ? CREME : GRIS, false);
        if (s.cliquable(y - 1, 11)) s.hits.add(new StaffScreen.Hit(ix, y - 1, w, 11, () -> { tchat = !tchat; RebornSounds.uiClick(); }, null, null));
        s.tip(ix, y - 1, w, 11, "Les lignes partent aussi dans le tchat des joueurs, en italique (en plus du texte au centre).");
        return y + 15;
    }

    /* ---------------------------------------------------------------- zone de texte */

    private int zoneTexte(int y, int ix, int iw) {
        GardeArt.Pinceau pz = s.pz;
        editeur.mettreEnPage(pz, ix + 5, y + 5 + GardeEditeur.PAS / 2f, iw - 10);
        int h = Math.max(4, editeur.nbLignes()) * GardeEditeur.PAS + 10;
        pz.fond(ix, y, ix + iw, y + h, NUIT_2);
        pz.cadre(ix, y, iw, h, focus ? OR_DOUX : FILET_2);
        if (editeur.longueur() == 0) {
            pz.texte("Le texte que les joueurs liront.", ix + 5, y + 5 + GardeEditeur.PAS / 2f, 14, GRIS_2, false);
            pz.texte("Entrée : à la ligne · deux fois : paragraphe.", ix + 5, y + 5 + GardeEditeur.PAS * 1.5f, 12, GRIS_3, false);
        }
        editeur.peindre(focus);
        ex0 = ix;
        ey0 = y;
        ex1 = ix + iw;
        ey1 = y + h;
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(ix, y, iw, h, () -> {
            focus = true;
            glisse = true;
            s.setFocused(null);
            editeur.cliquer(clicX, clicY, clicMaj);
        }, null, null));
        return y + h;
    }

    /** Pastilles de couleur : colorent la sélection, ou la suite de la frappe sans sélection. */
    private int pastilles(int y, int ix, int iw) {
        GardeArt.Pinceau pz = s.pz;
        int active = editeur.couleurActive();
        float x = ix;
        for (int i = 0; i < GardeEditeur.CLES.length; i++) {
            final int c = i;
            boolean on = active == i;
            String nom = GardeEditeur.NOMS[i];
            float w = 7 + pz.largeur(nom, 12, on);
            int X = Math.round(x) - 1, Y = y - 1;
            boolean hov = s.hover(X, Y, Math.round(w) + 2, 11) && s.cliquable(Y, 11);
            int teinte = i == 0 ? 0xFFFFFFFF : GardeEditeur.teinte(i);
            if (on) pz.pastille(x + 2.5f, y + 4.5f, 3f, OR);
            pz.pastille(x + 2.5f, y + 4.5f, 2.2f, teinte);
            pz.texte(nom, x + 7, y + 4.5f, 12, on || hov ? CREME : GRIS, on);
            if (on) pz.filetH(x + 7, x + w, y + 10, OR);
            if (s.cliquable(Y, 11)) s.hits.add(new StaffScreen.Hit(X, Y, Math.round(w) + 2, 11, () -> {
                editeur.colorer(c);
                focus = true;
                s.setFocused(null);
                RebornSounds.uiClick();
            }, null, null));
            s.tip(X, Y, Math.round(w) + 2, 11, editeur.selection() ? "Colorer la sélection en " + nom.toLowerCase() + "."
                    : "Sélectionne des mots pour les colorer (ou écris la suite en " + nom.toLowerCase() + ").");
            x += w + 8;
        }
        return y + 12;
    }

    /* ---------------------------------------------------------------- aperçu */

    private int ligneApercu(int y, int ix, int iw) {
        GardeArt.Pinceau pz = s.pz;
        pz.texte("Aperçu", ix, y + 3, 12, GRIS, false);
        String info;
        if (editeur.vide()) info = "à gauche du volet, dès le premier mot";
        else if (!apercuVisible) info = "masqué";
        else if (!apercu.pret()) info = "en préparation…";
        else info = (reduit ? "à gauche, réduit · " : "à gauche, taille réelle · ") + apercu.lignes() + " ligne(s)"
                + (apercu.pages() > 1 ? " · page " + (apercu.page() + 1) + "/" + apercu.pages() : "");
        float x = ix + 36;
        x += pz.texte(s.fit(info, iw - 36, 12, false), x, y + 3, 12, GRIS_2, false) + 8;
        y += 11;
        float ax = ix + 36;
        if (!editeur.vide()) {
            if (apercuVisible) ax += s.motCouleur("Plein écran", ax, y + 3, 12, OR, () -> { pleinEcran = true; RebornSounds.uiClick(); }) + 9;
            s.tip(Math.round(ix + 36f), y - 3, 50, 12, "L'écran tel que les joueurs le verront, sans le volet. Un clic ou une touche pour revenir.");
            if (apercuVisible && apercu.pages() > 1)
                ax += s.motCouleur("Page suivante", ax, y + 3, 12, GRIS, () -> { apercu.pageSuivante(); RebornSounds.uiClick(); }) + 9;
            s.motCouleur(apercuVisible ? "Masquer" : "Afficher", ax, y + 3, 12, GRIS, () -> { apercuVisible = !apercuVisible; RebornSounds.uiClick(); });
        }
        return y + (editeur.vide() ? 4 : 14);
    }

    /**
     * Aperçu à taille réelle sur le jeu, avant le volet : le texte tel que les joueurs le verront, à sa place verticale,
     * centré sur l'écran s'il passe à gauche du volet, sinon dans la partie libre. Appelé par l'écran à chaque image tant
     * que l'outil est ouvert (formulaire de confirmation compris).
     */
    void apercuJeu(GuiGraphicsExtractor ctx) {
        List<Recit.Segment> segs = apercuVisible || pleinEcran ? editeur.segments() : null;
        // frappe : l'aperçu (un rendu plein écran sur le fil du texte) suit 250 ms après la dernière touche
        long now = net.minecraft.util.Util.getMillis();
        if (segs != segsVus) {
            segsVus = segs;
            segsVusA = now;
        }
        boolean premier = apercu.pages() == 0 || segs == null || segs.isEmpty();
        if (premier || now - segsVusA >= ATTENTE_FRAPPE_MS) segsPoses = segs;
        apercu.poser(segsPoses == null || segsPoses.isEmpty() ? null : segsPoses, Gabarit.NARRATION);
        if (apercu.pages() == 0) { pleinEcran = false; return; }
        var win = Minecraft.getInstance().getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        int libre = (s.px0() - StaffScreen.MARGE) * gs;
        int marge = (int) Math.ceil(24 * Math.max(1, H) / 1080.0);
        // largeur à taille réelle (la couche posée, ramenée à la réduction de son rendu)
        double wPlein = apercu.pret() ? apercu.w() / apercu.reduction() : 0;
        int cx = W / 2;
        reduit = false;
        double red = 1.0;
        if (!pleinEcran && wPlein > 0) {
            // à gauche du volet avec une marge des deux côtés ; un bloc qui n'y tient pas est montré réduit
            if (wPlein + 2 * marge > libre) {
                red = Math.max(0.25, Math.floor((libre - 2.0 * marge) / wPlein * 32) / 32);
                reduit = red < 1.0;
            }
            // place : selon la couche affichée (pendant le recalcul à la nouvelle réduction, l'ancienne reste)
            double w = apercu.w();
            if (cx + w / 2 > libre - marge) cx = (int) Math.round(Math.max(w / 2 + marge, libre - marge - w / 2));
        }
        apercu.dessiner(ctx, cx, 1f, red);
        if (!apercu.pret()) return;
        if (pleinEcran) {
            // seule surcouche : une ligne d'aide au bas de l'écran
            GardeArt.Pinceau pz = s.pz;
            String t = "Aperçu plein écran" + (apercu.pages() > 1 ? " · page " + (apercu.page() + 1) + "/" + apercu.pages() : "")
                    + " · clic ou touche : revenir au volet";
            float lw = pz.largeur(t, 12, false) + 12;
            int X = Math.round(s.width / 2f - lw / 2f), Y = s.height - 40;
            pz.fond(X, Y, X + Math.round(lw), Y + 13, 0xE0080506);
            pz.cadre(X, Y, Math.round(lw), 13, FILET_2);
            pz.fond(X, Y, X + 1, Y + 13, OR);
            pz.texte(t, X + 6, Y + 6.5f, 12, GRIS, false);
            return;
        }
        // étiquette discrète au-dessus du bloc (staff seulement)
        GardeArt.Pinceau pz = s.pz;
        String t = (reduit ? "Aperçu réduit · Plein écran pour la taille réelle" : "Aperçu · ce que verront les joueurs")
                + (apercu.pages() > 1 ? " · page " + (apercu.page() + 1) + "/" + apercu.pages() : "");
        float lw = pz.largeur(t, 11, false) + 10;
        float lx = Math.max(4, cx / (float) gs - lw / 2f);
        int ly = Math.max(4, apercu.y() / gs - 14);
        int X = Math.round(lx);
        pz.fond(X, ly, X + Math.round(lw), ly + 11, 0xE0080506);
        pz.cadre(X, ly, Math.round(lw), 11, FILET_2);
        pz.fond(X, ly, X + 1, ly + 11, OR);
        pz.texte(t, lx + 5, ly + 5.5f, 11, GRIS, false);
    }

    /* ---------------------------------------------------------------- en cours */

    private int enCours(JsonObject snap, int y, int ix, int iw) {
        GardeArt.Pinceau pz = s.pz;
        JsonObject etat = StaffClient.narrations();
        JsonArray act = StaffScreen.arr(etat, "actives");
        float cw = pz.capitales("En cours", ix, y + 3, 11, GRIS_2);
        if (!act.isEmpty()) pz.texte(String.valueOf(act.size()), ix + cw + 4, y + 3, 12, OR, true);
        boolean modo = StaffScreen.grade(snap) >= 2;
        boolean miennes = false;
        for (JsonElement e : act) if (StaffScreen.bool(e.getAsJsonObject(), "moi")) miennes = true;
        if (modo && !act.isEmpty()) {
            float w = pz.largeur("Tout arrêter", 12, false);
            s.motCouleur("Tout arrêter", ix + iw - w, y + 3, 12, ROUGE, () -> arreter(""));
        } else if (miennes) {
            float w = pz.largeur("Arrêter les miennes", 12, false);
            s.motCouleur("Arrêter les miennes", ix + iw - w, y + 3, 12, ROUGE, () -> arreter(""));
        }
        y += 11;
        if (etat == null) {
            pz.texte("Lecture de l'état…", ix, y + 4, 12, GRIS_2, false);
            return y + 14;
        }
        if (act.isEmpty()) {
            pz.texte("Aucune narration en cours.", ix, y + 4, 12, GRIS_2, false);
            return y + 14;
        }
        long passe = (System.currentTimeMillis() - StaffClient.narrationsA()) / 1000;
        for (JsonElement e : act) {
            JsonObject a = e.getAsJsonObject();
            String id = StaffScreen.str(a, "id");
            int reste = StaffScreen.num(a, "reste");
            String rs = reste < 0 ? "tenue" : "≈ " + Math.max(0, reste - passe) + " s";
            int h = 31;
            boolean dernier = id.equals(StaffScreen.str(etat, "dernier"));
            if (dernier) pz.fond(ix - 3, y, ix - 2, y + h - 3, OR);
            float rw = pz.texteD(rs, ix + iw, y + 4.5f, 12, reste < 0 ? OR : GRIS, reste < 0);
            float iwid = pz.texte(id, ix, y + 4.5f, 11, GRIS_3, false) + 4;
            s.txt(StaffScreen.str(a, "portee"), ix + iwid, y + 4.5f, iw - iwid - rw - 6, 13, CREME, false);
            s.txt("« " + StaffScreen.str(a, "texte") + " »", ix, y + 13.5f, iw, 12, GRIS, false);
            String par = StaffScreen.str(a, "par");
            String meta = StaffScreen.num(a, "joueurs") + " joueur(s) · " + ("remplace".equals(StaffScreen.str(a, "mode")) ? "remplace" : "à la suite")
                    + (par.isEmpty() ? "" : " · par " + (StaffScreen.bool(a, "moi") ? "toi" : par));
            float aw = StaffScreen.bool(a, "arreter") ? pz.largeur("Arrêter", 12, false) : 0;
            s.txt(meta, ix, y + 22, iw - aw - 6, 11, GRIS_2, false);
            if (aw > 0) s.motCouleur("Arrêter", ix + iw - aw, y + 22, 12, ROUGE, () -> arreter(id));
            y += h;
            pz.filetH(ix, ix + iw, y - 2.5f, SEP);
        }
        return y + 2;
    }

    /* ================================================================ envoi */

    private void envoyer() {
        JsonObject snap = StaffClient.snapshot();
        if (editeur.vide() || !permise(portee, snap)) return;
        int sec = secondes();
        if (sec == Integer.MIN_VALUE) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "narration");
        o.addProperty("portee", portee);
        if ("joueur".equals(portee)) {
            String c = cible.getValue().trim();
            if (c.isEmpty()) return;
            String d = c.startsWith("#") ? c.substring(1) : c;
            if (!d.isEmpty() && d.chars().allMatch(Character::isDigit)) o.addProperty("pid", d);
            else {
                String uuid = uuidDe(c);
                if (uuid == null) { StaffClient.toast("Aucun joueur en ligne ne s'appelle « " + c + " »."); RebornSounds.deny(); return; }
                o.addProperty("t", uuid);
            }
        }
        if ("ici".equals(portee)) o.addProperty("r", rayon());
        o.addProperty("s", sec);
        o.addProperty("mode", mode);
        o.addProperty("tchat", tchat);
        o.add("segs", editeur.json());
        if (!"tous".equals(portee)) {
            partir(o);
            return;
        }
        // tout le serveur : second clic explicite, sur un résumé de ce qui partira (confirme:true seulement après)
        int joueurs = StaffScreen.arr(snap, "players").size();
        String texte = editeur.aPlat();
        s.formulaire.ouvrir("annonce", "Narration à tout le serveur ?", "« " + texte + " »", "Envoyer à tous", true, null,
                List.of(), List.of(joueurs + " joueur(s) en ligne · " + libelleDuree(sec) + " · "
                        + ("remplace".equals(mode) ? (StaffScreen.grade(snap) >= 2 ? "remplace l'écran et vide la file" : "remplace les tiennes")
                        : "à la suite") + (tchat ? " · aussi dans le tchat" : ""),
                        "L'aperçu à gauche montre le texte tel qu'il partira. Inscrit au journal."), v -> {
                    o.addProperty("confirme", true);
                    partir(o);
                });
    }

    private void partir(JsonObject o) {
        StaffClient.send(o);
        envoiA = System.currentTimeMillis();
        versionEnvoyee = editeur.version();
        RebornSounds.confirm();
    }

    private void arreter(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "narration_fin");
        if (!id.isEmpty()) o.addProperty("id", id);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    private String uuidDe(String q) {
        for (var e : StaffScreen.arr(StaffClient.snapshot(), "players")) {
            JsonObject p = e.getAsJsonObject();
            if (StaffScreen.str(p, "name").equalsIgnoreCase(q) || StaffScreen.str(p, "perso").equalsIgnoreCase(q)) return StaffScreen.str(p, "uuid");
        }
        return null;
    }

    /* ================================================================ entrées (routées par l'écran) */

    /** La zone de texte a la main (frappe, flèches, F7 sans effet). */
    boolean saisie() { return focus && "outils".equals(s.tab) && "narration".equals(s.outil) && !s.formulaire.actif(); }

    /** Tout clic dans l'écran (avant les zones) : position et Maj retenues ; hors de la zone de texte, elle rend la main. */
    void clic(double x, double y, boolean maj) {
        clicX = x;
        clicY = y;
        clicMaj = maj;
        if (!(x >= ex0 && x < ex1 && y >= ey0 && y < ey1)) focus = false;
    }

    boolean touche(int k, boolean maj, boolean ctrl) {
        if (!saisie()) return false;
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (ctrl) envoyer();
            else editeur.inserer("\n");
            return true;
        }
        return editeur.touche(k, maj, ctrl);
    }

    boolean caractere(String c) {
        if (!saisie()) return false;
        editeur.inserer(c);
        return true;
    }

    boolean glisser(double x, double y) {
        if (!glisse || !saisie()) return false;
        editeur.cliquer(x, y, true);
        return true;
    }

    void relacher() { glisse = false; }

    /* ================================================================ banc */

    void debugRemplir(String balise, String portee, String duree, String mode, boolean tchat, String cible) {
        editeur.poser(Recit.segments(balise));
        this.portee = portee;
        this.duree = duree;
        this.mode = mode;
        this.tchat = tchat;
        dureeBox.setValue("");
        this.duree = duree;
        if (cible != null) this.cible.setValue(cible);
    }

    void debugEnvoyer() { envoyer(); }

    void debugSelection(int a, int b) { editeur.debugSelection(a, b); }

    void debugPleinEcran() { pleinEcran = true; }
}
