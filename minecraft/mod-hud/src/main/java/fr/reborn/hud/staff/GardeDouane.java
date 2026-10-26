package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.douane.DouaneClient;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.voice.VoiceState;
import net.minecraft.client.gui.components.EditBox;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.reborn.hud.staff.GardeArt.*;
import static fr.reborn.hud.staff.StaffScreen.arr;
import static fr.reborn.hud.staff.StaffScreen.bool;
import static fr.reborn.hud.staff.StaffScreen.num;
import static fr.reborn.hud.staff.StaffScreen.obj;
import static fr.reborn.hud.staff.StaffScreen.str;

/**
 * Onglet <b>Douane</b> du Poste de garde (lot E, maquettes J6 à J8 du 24/10) : visible avec un grade staff Helper+ et
 * la permission d'examinateur, quand la douane est active (le serveur n'envoie {@code etat} qu'à eux).
 *
 * <ul>
 *   <li><b>La file</b> (volet étroit) : hall ouvert ou fermé, bureaux (libres, occupés, le tien), « Appeler le suivant ·
 *       Entrée », une ligne de 23 GUI par candidat (pseudo, 2ᵉ passage, village, convocation, arrivée, attente, état du
 *       micro lu dans Plasmo Voice) ; au survol, Appeler · Dossier · « … » (Retirer de la douane) ;</li>
 *   <li><b>le dossier</b> (volet élargi, 640 GUI au plus) : fiche résumée telle que l'API la renvoie (HRP, RP, note
 *       privée de l'écrit, voix, extraits repliables), « Ouvrir dans le panel », Renvoyer au hall, Retirer ;</li>
 *   <li><b>questions et décision</b> : question tirée au hasard, Montrer / Masquer / Autre question, aperçu de la ligne
 *       chez le candidat, questions déjà posées ; décision Accepter · À revoir · Refuser, motif obligatoire hors
 *       acceptation (2 000 caractères, plusieurs lignes), notes privées (4 000), Ctrl + Entrée.</li>
 * </ul>
 * Protocole : PLUGINS_REFERENCE §4 decies.5 et .6 ({@link DouaneClient}). Le serveur revérifie tout.
 */
final class GardeDouane {

    static final String APPROUVE = "APPROVED", A_REVOIR = "NEEDS_REVISION", REFUSE = "REJECTED";
    static final int MOTIF_MAX = 2000, NOTES_MAX = 4000;
    /**
     * Fuseau de la douane (défaut de {@code douane.fuseau}) : le serveur y formate {@code convocation} ; le dossier,
     * l'arrivée et la date de séance suivent, sinon un PC à une autre heure montrerait deux convocations différentes.
     */
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final StaffScreen s;
    /** Candidat dont le dossier est ouvert (volet élargi) ; null = la file. */
    String dossierU;
    /** Sous-onglet du dossier : {@code dossier} ou {@code decision}. */
    String vue = "dossier";
    /** Formulaire « Retirer de la douane » ouvert pour ce candidat. */
    String retraitU;
    private final Set<String> replies = new HashSet<>();
    private EditBox motifBox, notesBox, retraitBox;
    private String brouillonU;
    private long ouvertAt;
    private final Map<EditBox, Object[]> coupes = new HashMap<>();
    /** Ligne survolée de la file (actions). */
    private String survol;
    /** Banc : haut des lignes de la file et rectangle du bouton d'envoi, relevés au dernier dessin. */
    final List<Integer> lignesY = new ArrayList<>();
    int[] boutonEnvoi;

    GardeDouane(StaffScreen s) { this.s = s; }

    void init() {
        motifBox = s.box(motifBox, "Ce que le candidat lira dans son launcher…", MOTIF_MAX);
        notesBox = s.box(notesBox, "Pour le staff seulement…", NOTES_MAX);
        retraitBox = s.box(retraitBox, "Motif montré au joueur…", MOTIF_MAX);
        motifBox.setResponder(v -> sauver());
        notesBox.setResponder(v -> sauver());
        coupes.clear();
    }

    boolean large() { return dossierU != null; }

    boolean pleineHauteur() { return dossierU == null; }

    String pied() {
        if (retraitU != null) return "Échap : annuler";
        if (dossierU == null) return "Clic : dossier  ·  Entrée : appeler le suivant  ·  F7 : fermer";
        return "decision".equals(vue) ? "Échap : annuler  ·  Ctrl + Entrée : envoyer la décision"
                : "Échap : retour à la file  ·  Clic sur un titre : replier ou déplier";
    }

    /** Ouverture de l'onglet : instantané tout de suite. */
    void ouvrir() {
        DouaneClient.demanderEtat();
    }

    void tick() {
        String u = DouaneClient.dossierAOuvrir();
        if (u != null && "douane".equals(s.tab)) ouvrirDossier(u, false);
        // décision enregistrée pour le candidat ouvert : retour à la file
        JsonObject e = DouaneClient.envoi();
        if (dossierU != null && e != null && "ok".equals(str(e, "etat")) && dossierU.equals(str(e, "u"))
                && e.has("at") && e.get("at").getAsLong() > ouvertAt) {
            retourFile();
        }
    }

    /** Échap : retrait → dossier ; décision → dossier ; dossier → file. */
    boolean echap() {
        if (retraitU != null) { retraitU = null; retraitBox.setFocused(false); return true; }
        if (dossierU == null) return false;
        if ("decision".equals(vue)) { vue = "dossier"; defocus(); s.scroll = 0; return true; }
        retourFile();
        return true;
    }

    /** Entrée : dans la file, appeler le suivant ; Ctrl + Entrée dans la décision : l'envoyer. */
    boolean entree(boolean ctrl) {
        if (retraitU != null) { if (ctrl || !retraitBox.getValue().isBlank()) retirer(); return true; }
        if (dossierU != null) {
            if ("decision".equals(vue) && ctrl) { envoyerDecision(); return true; }
            return false;
        }
        if (focusSaisie()) return false;
        appeler(null);
        return true;
    }

    private boolean focusSaisie() {
        return motifBox.isFocused() || notesBox.isFocused() || retraitBox.isFocused();
    }

    private void defocus() {
        motifBox.setFocused(false);
        notesBox.setFocused(false);
        retraitBox.setFocused(false);
    }

    void fermer() { sauver(); }

    /* =================================================================== corps */

    int corps(JsonObject snap, int y) {
        JsonObject e = DouaneClient.etat();
        if (e == null) return s.vide(y, "Connexion à la douane…", "L'onglet se remplit dès que le serveur répond.");
        if (dossierU != null) return dossier(e, y);
        return file(e, y);
    }

    /* =================================================================== la file (volet étroit) */

    private int file(JsonObject e, int y) {
        int x = s.ix(), w = s.iw();
        float xd = x + w;
        survol = null;
        lignesY.clear();
        JsonArray file = arr(e, "file"), bureaux = arr(e, "bureaux");
        boolean ouvert = bool(e, "ouvert"), mode = DouaneClient.modeExaminateur();
        // ── séance en cours
        s.pz.capitales("Séance en cours", x, y + 4, 11, OR);
        int colHall = ouvert ? OK : GRIS_2;
        float lw = s.pz.texteD(ouvert ? "Hall ouvert" : "Hall fermé", xd, y + 4, 12, colHall, false);
        s.pz.pastille(xd - lw - 4.5f, y + 4, 1.5f, colHall);
        y += 11;
        s.pz.texte(dateSeance(file), x, y + 5, 15, CREME, false);
        y += 13;
        int auBureau = 0;
        List<String> exams = new ArrayList<>();
        String mien = str(e, "monBureau");
        for (JsonElement b : bureaux) {
            JsonObject j = b.getAsJsonObject();
            if (!str(j, "candidat").isEmpty()) auBureau++;
            String ex = str(j, "examinateurPseudo");
            if (!ex.isEmpty()) exams.add(str(j, "id").equals(mien) ? "toi" : ex);
        }
        List<String> stats = new ArrayList<>();
        stats.add(file.size() + " en attente");
        stats.add(auBureau + " au bureau");
        int envois = num(e, "envoisEnAttente");
        if (envois > 0) stats.add(envois + (envois > 1 ? " décisions" : " décision") + " à envoyer");
        s.txt(String.join(" · ", stats), x, y + 3.5f, w, 12, GRIS, false);
        y += 10;
        s.txt(exams.isEmpty() ? "Aucun examinateur à un bureau" : "Examinateurs aux bureaux : " + String.join(", ", exams),
                x, y + 3.5f, w, 12, GRIS_2, false);
        y += 10;
        if (!mode) {
            float mx = x + s.pz.texte("Mode examinateur coupé  ·  ", x, y + 3.5f, 12, TERRE, false);
            s.motAction("Le prendre", mx, y + 3.5f, true, () -> DouaneClient.envoyer("mode"));
            y += 10;
        } else if (!bool(e, "peut")) {
            s.txt("Tes droits d'examinateur ne sont plus valides.", x, y + 3.5f, w, 12, ROUGE, false);
            y += 10;
        }
        y += 3;
        // ── bureaux
        int libres = 0;
        for (JsonElement b : bureaux) if (bool(b.getAsJsonObject(), "libre")) libres++;
        s.pz.capitales("Bureaux · " + bureaux.size(), x, y + 3, 11, GRIS_2);
        s.pz.texteD(libres + (libres > 1 ? " libres" : " libre"), xd, y + 3, 12, GRIS_2, false);
        y += 10;
        String cible = bureauCible(e);
        if (!bureaux.isEmpty()) {
            int n = Math.min(4, bureaux.size());
            float cw = (w - (n - 1) * 4f) / n;
            for (int i = 0; i < n; i++) {
                JsonObject b = bureaux.get(i).getAsJsonObject();
                int bx = Math.round(x + i * (cw + 4)), bw = Math.round(cw);
                String id = str(b, "id");
                boolean moi = id.equals(mien), vise = id.equals(cible);
                s.pz.fond(bx, y, bx + bw, y + 23, NUIT_2);
                s.pz.cadre(bx, y, bw, 23, moi || vise ? OR_DOUX : FILET_2);
                s.pz.icone("porte", bx + 5, y + 3.5f, 7, moi || vise ? OR : GRIS_2);
                s.txt(nomBureau(id), bx + 15, y + 7, bw - 17, 13, CREME, false);
                Object[] et = etatBureau(b, moi);
                s.pz.pastille(bx + 7, y + 16, 1.5f, (int) et[1]);
                s.txt((String) et[0], bx + 12, y + 16, bw - 14, 12, (int) et[1], false);
                s.tip(bx, y, bw, 23, infoBureau(b, moi));
            }
            y += 23 + 7;
        }
        // ── appeler le suivant
        String suivant = file.isEmpty() ? null : str(file.get(0).getAsJsonObject(), "pseudo");
        boolean peut = mode && bool(e, "peut") && suivant != null && cible != null;
        s.refusVerrou = !mode ? "Prends d'abord le mode examinateur." : !bool(e, "peut") ? "Droits d'examinateur requis."
                : suivant == null ? "Personne n'attend au hall." : "Aucun bureau libre.";
        s.bouton(x, y, w, "Appeler le suivant  ·  Entrée", true, () -> appeler(null), !peut);
        s.refusVerrou = null;
        y += 17;
        String aide = suivant == null ? "Personne n'attend au hall pour l'instant."
                : cible == null ? "Aucun bureau libre : termine d'abord un entretien."
                : cible.equals(mien) ? suivant + " ira à ton bureau (" + nomBureau(cible).toLowerCase(Locale.ROOT) + ")."
                : suivant + " ira au " + nomBureau(cible).toLowerCase(Locale.ROOT) + ", le premier libre ; tu y vas aussi.";
        s.pz.texteC(s.fit(aide, w, 12, false), x + w / 2f, y + 7, 12, GRIS_2, false);
        y += 16;
        s.pz.filetH(x, xd, y, FILET);
        y += 6;
        // ── en attente au hall
        s.pz.capitales("En attente au hall · " + file.size(), x, y + 3, 11, GRIS_2);
        s.pz.texteD("par convocation, puis arrivée", xd, y + 3, 11, GRIS_3, false);
        y += 10;
        if (file.isEmpty()) {
            s.pz.texte("Personne en attente : les candidats arrivent ici à leur entrée au hall.", x, y + 6, 12, GRIS_2, false);
            y += 14;
        }
        for (int i = 0; i < file.size(); i++) {
            y = ligneFile(file.get(i).getAsJsonObject(), y, mode && bool(e, "peut"), cible != null);
            if (i < file.size() - 1) s.pz.filetH(x + 18, xd, y - 0.5f, SEP);
        }
        // ── au bureau
        List<JsonObject> occupes = new ArrayList<>();
        for (JsonElement b : bureaux) if (!str(b.getAsJsonObject(), "candidat").isEmpty()) occupes.add(b.getAsJsonObject());
        if (!occupes.isEmpty()) {
            y += 6;
            s.pz.capitales("Au bureau · " + occupes.size(), x, y + 3, 11, GRIS_2);
            y += 10;
            for (JsonObject b : occupes) y = ligneBureau(b, y, str(b, "id").equals(mien));
        }
        // ── décisions
        List<String[]> h = DouaneClient.historique();
        if (!h.isEmpty() || envois > 0) {
            y += 6;
            s.pz.capitales("Décisions", x, y + 3, 11, GRIS_2);
            y += 10;
            for (int i = 0; i < Math.min(4, h.size()); i++) {
                String[] d = h.get(i);
                int col = "erreur".equals(d[0]) ? ROUGE : "attente".equals(d[0]) ? TERRE : GRIS_3;
                s.txt(d[1] + " · " + d[2], x, y + 4, w, 12, col, false);
                y += 10;
            }
            if (envois > 0 && h.stream().noneMatch(d -> "attente".equals(d[0]))) {
                s.txt(envois + (envois > 1 ? " décisions en attente d'envoi" : " décision en attente d'envoi")
                        + " · nouvel essai automatique", x, y + 4, w, 12, TERRE, false);
                y += 10;
            }
        }
        return y + 4;
    }

    /** Une ligne de la file (23 GUI) ; renvoie le bas. */
    private int ligneFile(JsonObject c, int y, boolean peutAppeler, boolean bureauLibre) {
        int x = s.ix(), w = s.iw(), h = 23;
        float xd = x + w;
        String u = str(c, "u"), pseudo = str(c, "pseudo");
        lignesY.add(y);
        boolean hov = s.menu == null && s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov) {
            survol = u;
            s.pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_3);
            s.pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR);
        }
        s.pz.visage(u, x, y + 5.5f, 12);
        float tx = x + 18;
        float pw = s.pz.texte(s.fit(pseudo, 80, 15, false), tx, y + 7.5f, 15, CREME, false);
        float tagX = tx + pw + 4;
        if (bool(c, "deuxiemePassage")) tagX += tag("2ᵉ passage", tagX, y + 3, TERRE) + 3;
        if (bool(c, "enTete")) tag("en tête", tagX, y + 3, GRIS);
        String sous = villageCourt(str(c, "village")) + " · conv. " + (str(c, "convocation").isEmpty() ? "—" : str(c, "convocation"));
        s.pz.texte(s.fit(sous, w - 18 - 66, 12, false), tx, y + 16.5f, 12, GRIS_2, false);
        if (cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> ouvrirDossier(u, true),
                () -> menuCandidat(c, s.mouseX, s.mouseY), null));
        if (hov) {
            // Appeler (principal) · Dossier · « … » (Retirer de la douane)
            String[][] b = {{"plus", "Retirer de la douane…"}, {"dossier", "Dossier"}, {"ramener", "Appeler"}};
            float bx = xd;
            for (int k = 0; k < 3; k++) {
                bx -= 14;
                int X = Math.round(bx), Y = y + 5;
                boolean on = s.hover(X, Y, 13, 13);
                boolean principal = k == 2;
                boolean eteint = principal && !(peutAppeler && bureauLibre);
                s.pz.fond(X, Y, X + 13, Y + 13, on ? NUIT_3 : NUIT_2);
                s.pz.cadre(X, Y, 13, 13, on || principal ? OR_DOUX : FILET_2);
                s.pz.icone(b[k][0], X + 2.5f, Y + 2.5f, 8, eteint ? GRIS_3 : on ? CREME : principal ? OR : GRIS);
                Runnable r = switch (k) {
                    case 0 -> () -> ouvrirRetrait(u);
                    case 1 -> () -> ouvrirDossier(u, true);
                    default -> () -> appeler(u);
                };
                String refus = eteint ? (peutAppeler ? "Aucun bureau libre." : "Prends d'abord le mode examinateur.") : null;
                s.hits.add(new StaffScreen.Hit(X, Y, 13, 13, eteint ? null : r, null, refus));
                if (on) bulle(Math.round(xd - 3 * 14) - 4, Y, b[k][1]);
            }
        } else {
            int att = num(c, "attenteMin");
            String arr = arrivee(c);
            float aw = s.pz.texteD(att + " min", xd, y + 7.5f, 12, att >= 30 ? TERRE : GRIS, false);
            if (!arr.isEmpty()) s.pz.texteD("arrivée " + arr + "  ·  ", xd - aw, y + 7.5f, 12, GRIS_2, false);
            micro(u, xd, y + 16.5f);
        }
        return y + h;
    }

    /** Un candidat au bureau (23 GUI). */
    private int ligneBureau(JsonObject b, int y, boolean moi) {
        int x = s.ix(), w = s.iw(), h = 23;
        float xd = x + w;
        String u = str(b, "candidat");
        boolean hov = s.menu == null && s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov) {
            s.pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_3);
            s.pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR);
        }
        s.pz.visage(u, x, y + 5.5f, 12);
        float tx = x + 18;
        s.pz.texte(s.fit(str(b, "candidatPseudo"), 90, 15, false), tx, y + 7.5f, 15, CREME, false);
        String avec = moi ? "avec toi" : str(b, "examinateurPseudo").isEmpty() ? "sans examinateur" : "avec " + str(b, "examinateurPseudo");
        s.pz.texte(s.fit(nomBureau(str(b, "id")).toLowerCase(Locale.ROOT) + " · " + avec, w - 18 - 70, 12, false), tx, y + 16.5f, 12, GRIS_2, false);
        if (cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> ouvrirDossier(u, true), null, null));
        if (hov && moi) {
            String[][] a = {{"renvoyer", "Renvoyer au hall"}, {"dossier", "Dossier"}};
            float bx = xd;
            for (int k = 0; k < 2; k++) {
                bx -= 14;
                int X = Math.round(bx), Y = y + 5;
                boolean on = s.hover(X, Y, 13, 13);
                s.pz.fond(X, Y, X + 13, Y + 13, on ? NUIT_3 : NUIT_2);
                s.pz.cadre(X, Y, 13, 13, on || k == 1 ? OR_DOUX : FILET_2);
                s.pz.icone(a[k][0], X + 2.5f, Y + 2.5f, 8, on ? CREME : k == 1 ? OR : GRIS);
                Runnable r = k == 0 ? () -> DouaneClient.envoyer("renvoyer") : () -> ouvrirDossier(u, true);
                s.hits.add(new StaffScreen.Hit(X, Y, 13, 13, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
                if (on) bulle(Math.round(xd - 2 * 14) - 4, Y, a[k][1]);
            }
        } else {
            if (bool(b, "absent")) s.pz.texteD("déconnecté", xd, y + 7.5f, 12, TERRE, false);
            else if ("attente".equals(str(b, "envoi"))) s.pz.texteD("décision en attente d'envoi", xd, y + 7.5f, 12, TERRE, false);
            else s.pz.texteD(nomBureau(str(b, "id")).toLowerCase(Locale.ROOT), xd, y + 7.5f, 12, GRIS, false);
            micro(u, xd, y + 16.5f);
        }
        s.pz.filetH(tx, xd, y + h - 0.5f, SEP);
        return y + h;
    }

    private void menuCandidat(JsonObject c, int x, int y) {
        String u = str(c, "u");
        List<StaffScreen.Item> items = new ArrayList<>();
        items.add(new StaffScreen.Item("ramener", "Appeler à mon bureau", (char) 0, null, false, () -> appeler(u)));
        items.add(new StaffScreen.Item("dossier", "Dossier", (char) 0, null, false, () -> ouvrirDossier(u, true)));
        items.add(null);
        items.add(new StaffScreen.Item("suppr", "Retirer de la douane…", (char) 0, null, true, () -> ouvrirRetrait(u)));
        s.ouvrirMenu(items, x, y, c);
    }

    /* =================================================================== le dossier (volet élargi) */

    void ouvrirDossier(String u, boolean demander) {
        if (u == null || u.isEmpty()) return;
        sauver();
        if (!u.equals(dossierU)) { vue = "dossier"; replies.clear(); }
        dossierU = u;
        retraitU = null;
        ouvertAt = System.currentTimeMillis();
        s.scroll = 0;
        s.menu = null;
        chargerBrouillon(u);
        if (demander || DouaneClient.dossier(u) == null) {
            JsonObject o = new JsonObject();
            o.addProperty("a", "dossier");
            o.addProperty("u", u);
            DouaneClient.envoyer(o);
        }
        RebornSounds.uiClick();
    }

    void retourFile() {
        sauver();
        dossierU = null;
        retraitU = null;
        vue = "dossier";
        defocus();
        s.scroll = 0;
        RebornSounds.uiClick();
    }

    private int dossier(JsonObject e, int y) {
        int x = s.ix(), w = s.iw();
        float xd = x + w;
        String u = dossierU;
        JsonObject d = DouaneClient.dossier(u);
        JsonObject place = placeDe(e, u);
        JsonObject bureau = bureauDe(e, u);
        boolean auMien = u.equals(DouaneClient.candidatDeMonBureau());
        // ── retour, contexte
        boolean hovR = s.hover(x - 3, y, 50, 12) && s.cliquable(y, 12);
        s.pz.icone("gauche", x - 2, y + 2, 8, hovR ? CREME : GRIS);
        s.pz.texte("Douane", x + 7, y + 6, 13, hovR ? CREME : GRIS, false);
        s.hits.add(new StaffScreen.Hit(x - 3, y, 50, 12, this::retourFile, null, null));
        List<String> ctx = new ArrayList<>();
        if (bureau != null) ctx.add(nomBureau(str(bureau, "id")));
        else if (place != null) ctx.add(num(place, "rang") + "ᵉ au hall");
        ctx.add(num(e, "enAttente") + " en attente au hall");
        s.pz.texteD(String.join("  ·  ", ctx), xd, y + 6, 12, GRIS_2, false);
        y += 16;
        // ── identité
        JsonObject ca = obj(d, "candidat"), pa = obj(d, "passage");
        String pseudo = !str(ca, "pseudo").isEmpty() ? str(ca, "pseudo") : place != null ? str(place, "pseudo")
                : bureau != null ? str(bureau, "candidatPseudo") : "Candidat";
        s.pz.visage(u, x, y, 24);
        float tx = x + 31;
        s.pz.texte(pseudo, tx, y + 5, 18, CREME, true);
        List<String> l2 = new ArrayList<>();
        // la convocation de la file (formatée par le serveur) d'abord : la même heure partout
        String conv = place != null && !str(place, "convocation").isEmpty() ? str(place, "convocation")
                : pa != null ? heure(str(pa, "convocationAt")) : "";
        String arr = pa != null ? heure(str(pa, "arriveeAt")) : place != null ? arrivee(place) : "";
        if (!conv.isEmpty()) l2.add("convocation " + conv + (arr.isEmpty() ? "" : ", arrivée " + arr));
        if (bureau != null && pa != null && !str(pa, "appelAt").isEmpty()) l2.add("au bureau depuis " + duree(str(pa, "appelAt")));
        else if (place != null) l2.add("au hall depuis " + num(place, "attenteMin") + " min");
        // actions à droite : Ouvrir dans le panel (contour), Renvoyer, Retirer (mots)
        String lien = d == null ? "" : str(d, "lienPanel");
        float rx = xd;
        if (lien.startsWith("https://")) {
            String lab = "Ouvrir dans le panel";
            int bw = Math.round(s.pz.largeur(lab, 13, false)) + 31;
            int bx = Math.round(xd - bw);
            boolean hov = s.hover(bx, y + 2, bw, 17) && s.cliquable(y + 2, 17);
            s.pz.fond(bx, y + 2, bx + bw, y + 19, hov ? NUIT_3 : NUIT_2);
            s.pz.cadre(bx, y + 2, bw, 17, hov ? OR_DOUX : FILET_2);
            float lx = bx + (bw - s.pz.largeur(lab, 13, false) - 11) / 2f;
            s.pz.icone("externe", lx, y + 6.5f, 8, OR);
            s.pz.texte(lab, lx + 11, y + 10.5f, 13, CREME, false);
            s.hits.add(new StaffScreen.Hit(bx, y + 2, bw, 17, () -> ouvrirLien(lien), null, null));
            s.pz.texteD("fiche web complète, dans ton navigateur", xd, y + 26, 11, GRIS_2, false);
            rx = bx - 12;
        }
        rx -= s.motCouleur("Retirer de la douane…", rx - s.pz.largeur("Retirer de la douane…", 13, false), y + 10.5f, 13, GRIS_2,
                () -> ouvrirRetrait(u)) + 11;
        if (auMien) s.motCouleur("Renvoyer au hall", rx - s.pz.largeur("Renvoyer au hall", 13, false), y + 10.5f, 13, CREME,
                () -> { DouaneClient.envoyer("renvoyer"); RebornSounds.uiClick(); });
        float maxL2 = rx - tx - 12;
        if (!l2.isEmpty()) s.txt(String.join("  ·  ", l2), tx, y + 16, maxL2, 13, GRIS, false);
        float gx = tx;
        boolean deux = pa != null ? bool(pa, "deuxiemePassage") : place != null && bool(place, "deuxiemePassage");
        gx += tag(deux ? "2ᵉ passage" : "1ᵉʳ passage", gx, y + 23, deux ? TERRE : GRIS) + 3;
        Object[] m = microEtat(u);
        if (m != null) tag((String) m[0], gx, y + 23, (int) m[1]);
        y += 39;
        // ── sous-onglets
        float ox = x;
        ox += s.mot("Dossier", ox, y + 4.5f, 14, "dossier".equals(vue), () -> { vue = "dossier"; s.scroll = 0; defocus(); }) + 13;
        s.mot("Questions et décision", ox, y + 4.5f, 14, "decision".equals(vue), () -> { vue = "decision"; s.scroll = 0; });
        y += 15;
        s.pz.filetH(x, xd, y, FILET);
        y += 11;
        if (retraitU != null) return retrait(y, pseudo);
        if ("decision".equals(vue)) return decision(e, d, y, pseudo, auMien);
        if (d == null) {
            String err = DouaneClient.erreurDossier(u);
            return s.vide(y, err != null ? err : "Chargement du dossier…", err != null ? "Réessaie dans un instant, ou ouvre la fiche dans le panel." : null);
        }
        return fiche(d, y);
    }

    /** Onglet « Dossier » : fiche résumée en deux colonnes. */
    private int fiche(JsonObject d, int y0) {
        int x = s.ix(), w = s.iw();
        float lw = (w - 24) / 2f, lx = x, rx = x + lw + 24;
        JsonObject ca = obj(d, "candidat"), pe = obj(d, "personnage"), vo = obj(d, "voix"), pa = obj(d, "passage");
        int y = y0;
        // ── colonne gauche : HRP, RP, note privée, voix
        s.pz.capitales("Partie HRP", lx, y + 3, 11, GRIS_2);
        s.pz.texteD("onglet Vous", lx + lw, y + 3, 11, GRIS_2, false);
        y += 10;
        kv(lx, y, lw / 2 - 4, "Âge déclaré", num(ca, "ageDeclare") > 0 ? num(ca, "ageDeclare") + " ans" : "—");
        kv(lx + lw / 2, y, lw / 2, "Roleplay", val(ca, "anneesRp"));
        y += 24;
        kv(lx, y, lw, "Univers Naruto", val(ca, "connaissanceNaruto"));
        y += 28;
        s.pz.capitales("Partie RP", lx, y + 3, 11, GRIS_2);
        s.pz.texteD("onglets Personnage, Village, Histoire, Voix", lx + lw, y + 3, 11, GRIS_2, false);
        y += 10;
        kv(lx, y, lw / 2 - 4, "Type", val(pe, "type"));
        kv(lx + lw / 2, y, lw / 2, "Village", villageLong(str(pe, "village")));
        y += 24;
        kv(lx, y, lw / 2 - 4, "Prénom", val(pe, "prenom"));
        kv(lx + lw / 2, y, lw / 2, "Nom de famille", val(pe, "nomFamille"));
        if (num(d, "formVersion") >= 2 && !str(pe, "nomFamille").isEmpty()) {
            // première whitelist : jamais de clan, le nom de famille est inventé (décision du 24/10)
            float nw = s.pz.largeur(str(pe, "nomFamille"), 14, false);
            s.pz.texte("  ·  sans clan", lx + lw / 2 + nw, y + 13, 14, GRIS, false);
        }
        y += 24;
        kv(lx, y, lw / 2 - 4, "Âge du personnage", num(pe, "age") > 0 ? num(pe, "age") + " ans" : "—");
        kv(lx + lw / 2, y, lw / 2, "Sexe", val(pe, "sexe"));
        y += 29;
        JsonObject note = obj(d, "noteEcrit");
        if (note != null && !str(note, "texte").isBlank()) {
            List<String> ls = s.wrap(str(note, "texte"), lw - 16, 13);
            int hb = 8 + 9 + ls.size() * 10 + (bool(note, "tronquee") ? 10 : 0) + 6;
            s.pz.fond(Math.round(lx), y, Math.round(lx + lw), y + hb, NUIT_2);
            s.pz.fond(Math.round(lx), y, Math.round(lx) + 1, y + hb, OR);
            s.pz.capitales("Note privée du staff sur l'écrit", lx + 8, y + 7, 11, OR);
            s.pz.texteD("jamais montrée au joueur", lx + lw - 7, y + 7, 11, GRIS_2, false);
            int yy = y + 17;
            for (String li : ls) { s.pz.texte(li, lx + 8, yy + 5, 13, CREME, false); yy += 10; }
            if (bool(note, "tronquee")) s.pz.texte("La suite dans le panel.", lx + 8, yy + 5, 12, GRIS_2, false);
            y += hb + 8;
        }
        if (pa != null && bool(pa, "deuxiemePassage") && !str(pa, "motifPrecedent").isBlank()) {
            List<String> ls = s.wrap("« " + str(pa, "motifPrecedent") + " »", lw - 16, 13);
            int hb = 8 + 9 + ls.size() * 10 + 6;
            s.pz.fond(Math.round(lx), y, Math.round(lx + lw), y + hb, NUIT_2);
            s.pz.fond(Math.round(lx), y, Math.round(lx) + 1, y + hb, TERRE);
            s.pz.capitales("Motif du passage précédent", lx + 8, y + 7, 11, TERRE);
            s.pz.texteD("déjà vu par le candidat", lx + lw - 7, y + 7, 11, GRIS_2, false);
            int yy = y + 17;
            for (String li : ls) { s.pz.texte(li, lx + 8, yy + 5, 13, CREME, false); yy += 10; }
            y += hb + 8;
        }
        boolean voix = vo != null && bool(vo, "presente");
        s.pz.icone("lecture", lx, y + 0.5f, 8, voix ? GRIS : GRIS_3);
        float vw = s.pz.texte(voix ? "Voix du personnage" : "Pas de voix enregistrée", lx + 13, y + 4.5f, 14, voix ? CREME : GRIS, false);
        if (voix && num(vo, "dureeSec") > 0) {
            int sec = num(vo, "dureeSec");
            s.pz.texte(sec / 60 + ":" + String.format("%02d", sec % 60), lx + 17 + vw, y + 4.5f, 12, GRIS_2, false);
        }
        String consigne = vo == null ? "" : str(vo, "consigne").replaceFirst("^Voix\\s*:\\s*", "");
        if (voix && !consigne.isEmpty()) s.pz.texteD(consigne, lx + lw, y + 4.5f, 12, GRIS_2, false);
        int finG = y + 11;
        // ── colonne droite : extraits repliables
        y = y0;
        JsonArray ex = arr(d, "extraits");
        s.pz.capitales("Extraits · trois premières lignes", rx, y + 3, 11, GRIS_2);
        boolean toutReplie = !ex.isEmpty() && replies.size() >= ex.size();
        String lab = toutReplie ? "Tout déplier" : "Tout replier";
        s.motCouleur(lab, rx + lw - s.pz.largeur(lab, 12, false), y + 3, 12, GRIS, () -> {
            if (toutReplie) replies.clear();
            else for (JsonElement el : ex) replies.add(str(el.getAsJsonObject(), "champ"));
        });
        y += 11;
        if (ex.isEmpty()) {
            s.pz.texte("Aucun texte long dans cette candidature.", rx, y + 6, 12, GRIS_2, false);
            y += 14;
        }
        for (String partie : new String[]{"HRP", "RP"}) {
            boolean titre = false;
            for (JsonElement el : ex) {
                JsonObject t = el.getAsJsonObject();
                String champ = str(t, "champ");
                if (!partie.equals(partieDe(champ))) continue;
                if (!titre) { s.pz.capitales("Partie " + partie, rx, y + 3.5f, 10, GRIS_3); y += 10; titre = true; }
                boolean ouvert = !replies.contains(champ);
                boolean hov = s.hover(Math.round(rx) - 2, y, Math.round(lw) + 2, 12) && s.cliquable(y, 12);
                s.pz.icone(ouvert ? "bas" : "droite", rx - 1, y + 1, 7, hov ? CREME : ouvert ? GRIS_2 : GRIS);
                s.txt(str(t, "titre"), rx + 9, y + 4.5f, lw - 60, 14, hov ? OR_CLAIR : CREME, false);
                s.pz.texteD(nombre(num(t, "caracteres")) + " car.", rx + lw, y + 4.5f, 12, GRIS_2, false);
                s.hits.add(new StaffScreen.Hit(Math.round(rx) - 2, y, Math.round(lw) + 2, 12, () -> {
                    if (!replies.remove(champ)) replies.add(champ);
                    RebornSounds.uiClick();
                }, null, null));
                y += 12;
                if (ouvert) {
                    String corps = str(t, "texte").replace('\n', ' ') + (bool(t, "tronque") && !str(t, "texte").endsWith("…") ? " …" : "");
                    List<String> ls = s.wrap(corps, lw - 9, 13);
                    if (ls.size() > 5) { ls = new ArrayList<>(ls.subList(0, 5)); ls.set(4, s.fit(ls.get(4) + " …", lw - 9, 13, false)); }
                    float fy = y;
                    for (String li : ls) { s.pz.texte(li, rx + 9, fy + 5, 13, GRIS, false); fy += 9.5f; }
                    y = Math.round(fy) + 3;
                }
                y += 4;
                s.pz.filetH(rx + 9, rx + lw, y - 2, SEP);
            }
        }
        String lien = str(d, "lienPanel");
        if (lien.startsWith("https://")) {
            y += 1;
            s.motAction(s.fit("Le reste dans le panel : textes complets, voix, fil avec le staff ›", lw - 9, 12, true), rx + 9, y + 5, true,
                    () -> ouvrirLien(lien));
            y += 11;
        }
        int fin = Math.max(finG, y + 4);
        s.pz.filetV(lx + lw + 12, y0 - 3, fin, SEP);
        return fin + 6;
    }

    /* =================================================================== questions et décision */

    private int decision(JsonObject e, JsonObject d, int y0, String pseudo, boolean auMien) {
        int x = s.ix(), w = s.iw();
        float lw = (w - 24) / 2f, lx = x, rx = x + lw + 24;
        String u = dossierU;
        int y = y0;
        // ── colonne gauche : la question tirée
        JsonObject t = DouaneClient.tirage();
        String q = null;
        boolean montree = false;
        int reste = -1, n = 0;
        if (t != null && u.equals(str(t, "u")) && !str(t, "texte").isBlank()) {
            q = str(t, "texte");
            montree = bool(t, "montree");
            reste = t.has("reste") ? num(t, "reste") : -1;
            n = num(t, "n");
        }
        JsonObject eq = auMien ? obj(e, "question") : null;
        if (eq != null && !str(eq, "texte").isBlank()) {
            if (q == null || !q.equals(str(eq, "texte"))) { q = str(eq, "texte"); n = Math.max(n, 1); }
            montree = bool(eq, "montree");
        }
        s.pz.capitales("Question tirée au hasard", lx, y + 3, 11, OR);
        if (reste >= 0) s.pz.texteD(reste + (reste > 1 ? " jamais tirées" : " jamais tirée") + " pour " + pseudo, lx + lw, y + 3, 11, GRIS_2, false);
        y += 11;
        List<String> ql = q == null ? List.of() : lignesQuestion(q, lw - 18);
        int hq = q == null ? 26 : 10 + 11 + ql.size() * 12 + 6;
        s.pz.fond(Math.round(lx), y, Math.round(lx + lw), y + hq, NUIT_2);
        s.pz.cadre(lx, y, lw, hq, q == null ? FILET_2 : OR_DOUX);
        if (q == null) {
            s.pz.texte(auMien ? "Aucune question tirée pour l'instant." : "Appelle d'abord " + pseudo + " à ton bureau.", lx + 9, y + 13, 13, GRIS, false);
        } else {
            if (n > 0) s.pz.texteD("tirage n° " + n + " pour " + pseudo, lx + lw - 7, y + 11.5f, 11, GRIS_2, false);
            int yy = y + 28;
            for (String li : ql) { s.pz.texte(li, lx + 9, yy, 16, CREME, false); yy += 12; }
        }
        y += hq + 7;
        String refusQ = auMien ? null : "Appelle d'abord ce candidat à ton bureau.";
        s.refusVerrou = refusQ;
        String labQ = montree ? "Masquer" : "Montrer la question";
        int bw = s.largeurBouton(labQ);
        s.bouton(Math.round(lx), y, bw, labQ, !montree, () -> {
            DouaneClient.envoyer(montree0(t, eq) ? "masquer" : "montrer");
            RebornSounds.uiClick();
        }, !auMien || q == null);
        s.refusVerrou = null;
        float ax = lx + bw + 11;
        s.pz.icone("tirage", ax, y + 4.5f, 8, auMien ? GRIS : GRIS_3);
        if (auMien) s.motAction(q == null ? "Tirer une question" : "Autre question", ax + 12, y + 8.5f, false, () -> DouaneClient.envoyer("question"));
        else s.pz.texte(q == null ? "Tirer une question" : "Autre question", ax + 12, y + 8.5f, 13, GRIS_3, false);
        s.pz.texteD("Réponse à la voix", lx + lw, y + 8.5f, 12, GRIS_2, false);
        y += 17 + 11;
        s.pz.capitales("Chez " + pseudo + ", en haut de l'écran", lx, y + 3, 10, GRIS_2);
        s.pz.texteD("aperçu réduit", lx + lw, y + 3, 11, GRIS_3, false);
        y += 10;
        apercu(lx, y, lw, q, montree);
        y += 26 + 6;
        s.pz.pastille(lx + 2, y + 4, 1.5f, montree ? OK : GRIS_3);
        String et = montree ? "Montrée : elle reste jusqu'à la suivante ; « Masquer » la retire."
                : "Pas encore montrée. Montrée, elle reste jusqu'à la suivante ; « Masquer » la retire.";
        s.txt(et, lx + 7, y + 4, lw - 7, 12, GRIS_2, false);
        y += 16;
        s.pz.filetH(lx, lx + lw, y, FILET);
        y += 7;
        List<String[]> posees = DouaneClient.posees(u);
        s.pz.capitales("Déjà posées à " + pseudo + " · " + posees.size(), lx, y + 3, 11, GRIS_2);
        y += 11;
        for (String[] p : posees) {
            s.pz.icone("coche", lx, y + 0.5f, 7, OK);
            s.txt(p[0], lx + 11, y + 4, lw - 11 - 49, 13, GRIS, false);
            s.pz.texteD("montrée " + p[1], lx + lw, y + 4, 12, GRIS_3, false);
            y += 13;
        }
        if (posees.isEmpty()) { s.pz.texte("Aucune pour l'instant.", lx, y + 4, 12, GRIS_3, false); y += 11; }
        y += 2;
        for (String li : s.wrap("Écartées du tirage, comme toutes celles déjà vues par ce candidat, même après un renvoi au hall.", lw, 12)) {
            s.pz.texte(li, lx, y + 4, 12, GRIS_2, false);
            y += 9;
        }
        int finG = y + 2;
        // ── colonne droite : la décision
        y = y0;
        s.pz.icone("noter", rx, y + 0.5f, 8, OR);
        s.pz.texte("Décision de l'oral", rx + 14, y + 4.5f, 15, CREME, true);
        y += 12;
        for (String li : s.wrap("Envoyée à l'API, qui revérifie ton rôle web et la phase du candidat avant d'écrire.", lw - 14, 12)) {
            s.pz.texte(li, rx + 14, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 4;
        if (!auMien) {
            for (String li : s.wrap("La décision vaut pour le candidat de ton bureau : appelle d'abord " + pseudo
                    + " (« Appeler » dans la file).", lw, 13)) {
                s.pz.texte(li, rx, y + 5, 13, GRIS, false);
                y += 10;
            }
            int fin = Math.max(finG, y + 4);
            s.pz.filetV(lx + lw + 12, y0 - 3, fin, SEP);
            return fin + 6;
        }
        DouaneClient.Brouillon b = DouaneClient.brouillon(u);
        s.pz.texte("Décision", rx, y + 5, 12, GRIS, false);
        float cx = rx + 41;
        String[][] choix = {{APPROUVE, "Accepter"}, {A_REVOIR, "À revoir"}, {REFUSE, "Refuser"}};
        int[] cols = {OK, TERRE, ROUGE};
        for (int i = 0; i < 3; i++) {
            final String id = choix[i][0];
            cx += motChoix(choix[i][1], cx, y + 5, id.equals(b.choix), cols[i], () -> { b.choix = id; sauver(); }) + 11;
        }
        y += 17;
        s.pz.texte(s.fit("Motif · obligatoire pour « À revoir » et « Refuser », visible du joueur", lw, 12, false), rx, y + 3, 12, GRIS, false);
        y += 8;
        y = zone(motifBox, rx, y, lw, 4, MOTIF_MAX) + 7;
        s.pz.texte(s.fit("Notes privées · facultatives, jamais montrées au joueur", lw, 12, false), rx, y + 3, 12, GRIS, false);
        y += 8;
        y = zone(notesBox, rx, y, lw, 3, NOTES_MAX) + 7;
        String expl = switch (b.choix) {
            case APPROUVE -> "« Accepter » : « Bienvenue sur Reborn », puis la création de personnage, pré-remplie par la candidature.";
            case REFUSE -> "« Refuser » : " + pseudo + " pourra envoyer une nouvelle candidature complète dans 24 h.";
            case A_REVOIR -> "« À revoir » : l'écrit est gardé ; " + pseudo + " réserve une nouvelle séance dès demain, dans le launcher.";
            default -> "Choisis Accepter, À revoir ou Refuser.";
        };
        for (String li : s.wrap(expl, lw, 12)) { s.pz.texte(li, rx, y + 3, 12, GRIS_2, false); y += 9; }
        s.pz.texte("Inscrit au journal du staff avec les questions montrées.", rx, y + 3, 12, GRIS_2, false);
        y += 14;
        // envoi
        JsonObject env = DouaneClient.envoi();
        boolean enCours = env != null && u.equals(str(env, "u")) && "en_cours".equals(str(env, "etat"))
                && System.currentTimeMillis() - lngOuZero(env, "at") < 8000;
        String libelle = switch (b.choix) {
            case APPROUVE -> "Envoyer « Accepter »";
            case REFUSE -> "Envoyer « Refuser »";
            case A_REVOIR -> "Envoyer « À revoir »";
            default -> "Envoyer la décision";
        };
        String refus = valide(b) ? null : b.choix.isEmpty() ? "Choisis d'abord une décision." : "Le motif est obligatoire pour « "
                + (REFUSE.equals(b.choix) ? "Refuser" : "À revoir") + " ».";
        if (refus == null && enCours) refus = "Décision en cours d'envoi…";
        int bwE = s.largeurBouton(libelle);
        int bx = Math.round(rx + lw - bwE);
        boutonEnvoi = new int[]{bx, y, bwE, 17};
        s.refusVerrou = refus;
        s.bouton(bx, y, bwE, libelle, true, this::envoyerDecision, refus != null);
        s.refusVerrou = null;
        s.motCouleur("Annuler", bx - 10 - s.pz.largeur("Annuler", 13, false), y + 8.5f, 13, GRIS, () -> { vue = "dossier"; defocus(); });
        y += 17 + 4;
        if (env != null && u.equals(str(env, "u")) && System.currentTimeMillis() - lngOuZero(env, "at") < 60_000L) {
            String ee = str(env, "etat");
            int col = "erreur".equals(ee) ? ROUGE : "attente".equals(ee) ? TERRE : "ok".equals(ee) ? OK : GRIS;
            for (String li : s.wrap(str(env, "texte"), lw, 12)) { s.pz.texte(li, rx, y + 4, 12, col, false); y += 9; }
        }
        y += 7;
        s.pz.filetH(rx, rx + lw, y, FILET);
        y += 7;
        s.pz.capitales("Ensuite", rx, y + 3, 11, GRIS_2);
        y += 11;
        String chez = APPROUVE.equals(b.choix)
                ? "Chez " + pseudo + " : « Bienvenue sur Reborn », puis la création de personnage ; pas besoin de se reconnecter."
                : "Chez " + pseudo + " : « La douane n'est pas validée aujourd'hui. », puis retour au launcher ; ton motif l'y attend.";
        // délais réglés par le serveur (expulsion, renvoi) : pas de chiffre en dur
        for (String p : new String[]{chez, "API muette : « Décision en attente d'envoi », nouvel essai automatique ; jamais de promotion sans réponse."}) {
            for (String li : s.wrap(p, lw, 12)) { s.pz.texte(li, rx, y + 4, 12, GRIS_2, false); y += 9; }
            y += 1;
        }
        int fin = Math.max(finG, y + 4);
        s.pz.filetV(lx + lw + 12, y0 - 3, fin, SEP);
        return fin + 6;
    }

    private static boolean montree0(JsonObject t, JsonObject eq) {
        if (eq != null && !str(eq, "texte").isBlank()) return bool(eq, "montree");
        return t != null && bool(t, "montree");
    }

    private static long lngOuZero(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsLong() : 0L; } catch (RuntimeException e) { return 0L; }
    }

    /** Coupe à la phrase plutôt qu'au mot quand deux phrases tiennent chacune sur une ligne. */
    private List<String> lignesQuestion(String q, float maxW) {
        String[] ph = q.split("(?<=[.!?…])\\s+", 2);
        if (ph.length == 2 && s.pz.largeur(ph[0], 16, false) <= maxW && s.pz.largeur(ph[1], 16, false) <= maxW
                && s.pz.largeur(q, 16, false) > maxW) return List.of(ph[0], ph[1]);
        List<String> out = s.wrap(q, maxW, 16);
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    /** La ligne « Question » telle qu'elle s'affiche chez le candidat, réduite (fond du bureau neutre). */
    private void apercu(float x, int y, float w, String q, boolean montree) {
        int X = Math.round(x), W = Math.round(w);
        s.pz.fond(X, y, X + W, y + 26, 0xFF1C1418);
        s.pz.cadre(x - 0.5f, y - 0.5f, w + 1, 27, FILET_2);
        String t = q == null ? "La question montrée s'affichera ici." : q;
        float tete = 5 + 8 + 3 + s.pz.largeur("QUESTION", 9, true) + 4 + 4;
        float wt = Math.min(w - 12 - tete - 5, s.pz.largeur(t, 11, false));
        float bw = tete + wt + 5, bx = x + (w - bw) / 2f, by = y + 7;
        s.pz.fond(Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by + 11), 0xC80D080A);
        s.pz.icone("barriere", bx + 4, by + 1.5f, 8, montree || q == null ? OR : OR_DOUX);
        float tx = bx + 15;
        tx += s.pz.capitales("QUESTION", tx, by + 5.5f, 8, OR) + 3;
        s.pz.filetV(tx, by + 2.5f, by + 8.5f, 0x40FFF0DC);
        s.pz.texte(s.fit(t, wt, 11, false), tx + 4, by + 5.5f, 11, q == null ? GRIS_2 : CREME, false);
        s.pz.filetH(bx, bx + bw, by + 10.5f, montree ? OR : 0x80D9A95E);
    }

    /* =================================================================== retirer de la douane */

    private void ouvrirRetrait(String u) {
        if (u == null) return;
        if (!u.equals(dossierU)) ouvrirDossier(u, false);
        retraitU = u;
        retraitBox.setValue("");
        s.setFocused(retraitBox);
        retraitBox.setFocused(true);
        RebornSounds.uiClick();
    }

    private int retrait(int y, String pseudo) {
        int x = s.ix(), w = s.iw();
        float fw = Math.min(w, 380);
        s.pz.icone("suppr", x, y + 1, 8, ROUGE);
        s.pz.texte("Retirer " + pseudo + " de la douane ?", x + 14, y + 5, 15, CREME, true);
        y += 13;
        for (String li : s.wrap("Il quitte la douane sans décision et voit le motif ; sa place est annulée et il ne revient pas"
                + " dans cette séance. Une autre réservation reste possible.", fw - 14, 12)) {
            s.pz.texte(li, x + 14, y + 3, 12, ROUGE, false);
            y += 9;
        }
        y += 6;
        s.pz.texte("Motif · obligatoire, montré au joueur", x, y + 3, 12, GRIS, false);
        y += 8;
        y = zone(retraitBox, x, y, fw, 3, MOTIF_MAX) + 9;
        boolean ok = !retraitBox.getValue().isBlank();
        String lab = "Retirer de la douane";
        int bw = s.largeurBouton(lab);
        int bx = Math.round(x + fw - bw);
        s.refusVerrou = "Écris d'abord le motif.";
        s.bouton(bx, y, bw, lab, true, this::retirer, !ok);
        s.refusVerrou = null;
        s.motCouleur("Annuler", bx - 10 - s.pz.largeur("Annuler", 13, false), y + 8.5f, 13, GRIS, () -> retraitU = null);
        y += 17 + 8;
        s.pz.texte("Inscrit au journal du staff.", x, y + 3, 12, GRIS_2, false);
        return y + 12;
    }

    private void retirer() {
        String motif = retraitBox.getValue().trim();
        if (retraitU == null || motif.isEmpty()) { RebornSounds.deny(); return; }
        JsonObject o = new JsonObject();
        o.addProperty("a", "retirer");
        o.addProperty("u", retraitU);
        o.addProperty("motif", motif);
        DouaneClient.envoyer(o);
        RebornSounds.confirm();
        retraitU = null;
        retourFile();
    }

    /* =================================================================== actions */

    private void appeler(String u) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "appeler");
        if (u != null) o.addProperty("u", u);
        DouaneClient.envoyer(o);
        RebornSounds.uiClick();
    }

    private boolean valide(DouaneClient.Brouillon b) {
        if (b.choix.isEmpty()) return false;
        return APPROUVE.equals(b.choix) || !motifBox.getValue().isBlank();
    }

    private void envoyerDecision() {
        if (dossierU == null || !dossierU.equals(DouaneClient.candidatDeMonBureau())) { RebornSounds.deny(); return; }
        DouaneClient.Brouillon b = DouaneClient.brouillon(dossierU);
        if (!valide(b)) { RebornSounds.deny(); return; }
        JsonObject o = new JsonObject();
        o.addProperty("a", "decision");
        o.addProperty("resultat", b.choix);
        String motif = motifBox.getValue().trim(), notes = notesBox.getValue().trim();
        if (!motif.isEmpty()) o.addProperty("motif", motif.length() > MOTIF_MAX ? motif.substring(0, MOTIF_MAX) : motif);
        // toujours envoyées, vides comprises : le formulaire fait foi (sinon le serveur reprendrait des notes de
        // /douane notes que l'onglet ne montre pas)
        o.addProperty("notes", notes.length() > NOTES_MAX ? notes.substring(0, NOTES_MAX) : notes);
        DouaneClient.envoyer(o);
        RebornSounds.confirm();
    }

    private void ouvrirLien(String lien) {
        if (lien == null || !lien.startsWith("https://")) return;
        try { net.minecraft.util.Util.getPlatform().openUri(URI.create(lien)); } catch (RuntimeException ignored) { }
        RebornSounds.uiClick();
    }

    private void chargerBrouillon(String u) {
        DouaneClient.Brouillon b = DouaneClient.brouillon(u);
        brouillonU = null;   // pas d'écriture pendant le chargement
        motifBox.setValue(b.motif);
        notesBox.setValue(b.notes);
        brouillonU = u;
    }

    private void sauver() {
        if (brouillonU == null || motifBox == null) return;
        DouaneClient.Brouillon b = DouaneClient.brouillon(brouillonU);
        b.motif = motifBox.getValue();
        b.notes = notesBox.getValue();
    }

    /* =================================================================== pièces */

    /** Mot de choix : actif en gras crème souligné de sa couleur, sinon gris (souligné gris au survol). */
    private float motChoix(String t, float x, float cy, boolean actif, int col, Runnable r) {
        float w = s.pz.largeur(t, 13, actif);
        int X = Math.round(x), Y = Math.round(cy - 6);
        boolean hov = s.hover(X - 1, Y, Math.round(w) + 2, 12) && s.cliquable(Y, 12);
        s.pz.texte(t, x, cy, 13, actif || hov ? CREME : GRIS, actif);
        if (actif) s.pz.fond(X, Math.round(cy + 5), Math.round(x + w), Math.round(cy + 6), col);
        else if (hov) s.pz.filetH(x, x + w, cy + 5.5f, GRIS);
        if (s.cliquable(Y, 12)) s.hits.add(new StaffScreen.Hit(X - 1, Y, Math.round(w) + 2, 12, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
        return w;
    }

    /** Étiquette (« 2ᵉ passage », « micro actif ») : filet assombri, texte 11 de la couleur. */
    private float tag(String t, float x, float y, int col) {
        float w = s.pz.largeur(t, 11, false) + 6;
        s.pz.cadre(x, y, w, 9, sombre(col, 0.55f));
        s.pz.texteC(t, x + w / 2f, y + 4.5f, 11, col, false);
        return w;
    }

    private void kv(float x, int y, float w, String lab, String val) {
        s.pz.texte(lab, x, y + 3.5f, 12, GRIS, false);
        s.txt(val, x, y + 13, w, 14, CREME, false);
    }

    private static String val(JsonObject o, String k) {
        String v = str(o, k);
        return v.isBlank() ? "—" : v;
    }

    /** Bulle courte à gauche d'un groupe d'icônes (comme la liste Joueurs). */
    private void bulle(int xd, int y, String t) {
        float w = s.pz.largeur(t, 13, false) + 10;
        int X = Math.round(xd - 4 - w);
        s.pz.fond(X, y, X + Math.round(w), y + 13, 0xF5090607);
        s.pz.cadre(X, y, Math.round(w), 13, FILET_2);
        s.pz.texte(t, X + 5, y + 6.5f, 13, CREME, false);
    }

    /** État du micro (Plasmo Voice) aligné à droite sur xd : icône + mot. */
    private void micro(String u, float xd, float cy) {
        Object[] m = microEtat(u);
        if (m == null) return;
        float lw = s.pz.texteD((String) m[0], xd, cy, 12, (int) m[1], false);
        s.pz.icone((String) m[2], xd - lw - 8.5f, cy - 3.5f, 7, (int) m[1]);
    }

    private static Object[] microEtat(String u) {
        int m;
        try { m = VoiceState.micro(UUID.fromString(u)); } catch (RuntimeException e) { return null; }
        return switch (m) {
            case VoiceState.MICRO_ACTIF -> new Object[]{"micro actif", OK, "micro"};
            case VoiceState.MICRO_COUPE -> new Object[]{"micro coupé", TERRE, "micro_off"};
            case VoiceState.MICRO_MUET -> new Object[]{"rendu muet", TERRE, "micro_off"};
            case VoiceState.MICRO_SANS_VOIX -> new Object[]{"sans voix", ROUGE, "micro_off"};
            default -> null;
        };
    }

    /** Zone de saisie de plusieurs lignes (le champ vanilla reste caché ; le texte est coupé et redessiné en Inter). */
    private int zone(EditBox b, float x, int y, float w, int n, int max) {
        boolean focus = b.isFocused();
        float il = 10.5f;
        int h = Math.round(7 + n * il + 9);
        int X = Math.round(x), W = Math.round(w);
        s.pz.fond(X, y, X + W, y + h, NUIT_2);
        s.pz.cadre(X, y, W, h, focus ? OR_DOUX : FILET_2);
        boolean visible = !s.dansCorps || y >= s.vy0() && y + h <= s.vy1();
        b.setVisible(visible);
        b.setX(X + 6);
        b.setY(y + 4);
        b.setWidth(Math.max(10, W - 12));
        String v = b.getValue();
        List<int[]> ls = coupes(b, v, w - 12);
        int cur = Math.max(0, Math.min(b.getCursorPosition(), v.length()));
        int lc = 0;
        for (int i = 0; i < ls.size(); i++) if (cur >= ls.get(i)[0] && (cur < ls.get(i)[1] || i == ls.size() - 1)) { lc = i; break; }
        int premier = Math.max(0, Math.min(lc - n + 1, ls.size() - n));
        if (visible) {
            if (v.isEmpty()) {
                String hint = s.indices.get(b);
                if (hint != null) s.pz.texte(s.fit(hint, w - 12, 14, false), x + 6, y + 3.5f + il / 2f, 14, GRIS_2, false);
            }
            for (int i = premier; i < Math.min(ls.size(), premier + n); i++) {
                int[] c = ls.get(i);
                s.pz.texte(v.substring(c[0], c[1]).stripTrailing(), x + 6, y + 3.5f + il / 2f + (i - premier) * il, 14, CREME, false);
            }
            if (focus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int[] c = ls.get(lc);
                float cxp = x + 6 + s.pz.largeur(v.substring(c[0], Math.max(c[0], cur)), 14, false);
                float cy = y + 3.5f + il / 2f + (lc - premier) * il;
                s.pz.px(cxp, cy - 4.5f, cxp + 1f / echelle(), cy + 4.5f, OR);
            }
            s.pz.texteD(nombre(v.length()) + " / " + nombre(max), x + w - 5, y + h - 5.5f, 11, v.length() >= max ? ROUGE : GRIS_2, false);
            s.hits.add(new StaffScreen.Hit(X, y, W, h, () -> { s.setFocused(b); b.setFocused(true); }, null, null));
        }
        return y + h;
    }

    /** Coupe {@code v} en lignes (indices de début et de fin), mots entiers ; mémorisé tant que le texte ne change pas. */
    private List<int[]> coupes(EditBox b, String v, float maxW) {
        Object[] c = coupes.get(b);
        if (c != null && v.equals(c[0]) && Math.abs((float) c[1] - maxW) < 0.01f) {
            @SuppressWarnings("unchecked") List<int[]> l = (List<int[]>) c[2];
            return l;
        }
        List<int[]> out = new ArrayList<>();
        int n = v.length(), debut = 0;
        while (debut < n) {
            int fin = debut, espace = -1;
            float lw = 0;
            while (fin < n) {
                char ch = v.charAt(fin);
                float cw = s.pz.largeur(String.valueOf(ch), 14, false);
                if (lw + cw > maxW && fin > debut) break;
                lw += cw;
                if (ch == ' ') espace = fin;
                fin++;
            }
            if (fin < n && espace >= debut) fin = espace + 1;
            out.add(new int[]{debut, fin});
            debut = fin;
        }
        if (out.isEmpty()) out.add(new int[]{0, 0});
        coupes.put(b, new Object[]{v, maxW, out});
        return out;
    }

    private boolean cliquable(int y, int h) { return s.cliquable(y, h); }

    /* =================================================================== données */

    private static JsonObject placeDe(JsonObject e, String u) {
        for (JsonElement el : arr(e, "file")) if (u.equals(str(el.getAsJsonObject(), "u"))) return el.getAsJsonObject();
        return null;
    }

    private static JsonObject bureauDe(JsonObject e, String u) {
        for (JsonElement el : arr(e, "bureaux")) if (u.equals(str(el.getAsJsonObject(), "candidat"))) return el.getAsJsonObject();
        return null;
    }

    /** Bureau où irait le prochain appelé : le mien, sinon le premier libre (côté serveur, aussi celui où je me tiens). */
    private static String bureauCible(JsonObject e) {
        String mien = str(e, "monBureau");
        JsonArray bs = arr(e, "bureaux");
        if (!mien.isEmpty()) {
            for (JsonElement el : bs) {
                JsonObject b = el.getAsJsonObject();
                if (mien.equals(str(b, "id"))) return str(b, "candidat").isEmpty() ? mien : null;
            }
        }
        for (JsonElement el : bs) if (bool(el.getAsJsonObject(), "libre")) return str(el.getAsJsonObject(), "id");
        return null;
    }

    private static Object[] etatBureau(JsonObject b, boolean moi) {
        if (bool(b, "libre")) return new Object[]{"libre", OK};
        if (bool(b, "absent")) return new Object[]{"candidat déconnecté", TERRE};
        if ("attente".equals(str(b, "envoi"))) return new Object[]{"envoi en attente", TERRE};
        if (!str(b, "candidat").isEmpty()) return new Object[]{str(b, "candidatPseudo"), TERRE};
        return new Object[]{moi ? "toi, prêt" : str(b, "examinateurPseudo"), GRIS};
    }

    private static String infoBureau(JsonObject b, boolean moi) {
        if (bool(b, "libre")) return nomBureau(str(b, "id")) + " : libre";
        String ex = moi ? "toi" : str(b, "examinateurPseudo");
        String c = str(b, "candidatPseudo");
        return nomBureau(str(b, "id")) + " : " + (ex.isEmpty() ? "sans examinateur" : "examinateur " + ex)
                + (c.isEmpty() ? "" : " · candidat " + c + (bool(b, "absent") ? " (déconnecté)" : ""));
    }

    /** « b2 » → « Bureau 2 ». */
    static String nomBureau(String id) {
        if (id == null || id.isEmpty()) return "Bureau";
        if (id.matches("[bB]\\d+")) return "Bureau " + id.substring(1);
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private static String partieDe(String champ) {
        return switch (champ) {
            case "availability", "motivation", "experience" -> "HRP";
            default -> "RP";
        };
    }

    private static String villageCourt(String v) {
        String k = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        for (String[] p : VILLAGES) if (k.startsWith(p[0])) return p[1];
        return v == null || v.isBlank() ? "—" : Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    private static String villageLong(String v) {
        String k = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        for (String[] p : VILLAGES) if (k.startsWith(p[0])) return p[2];
        return v == null || v.isBlank() ? "—" : v;
    }

    private static final String[][] VILLAGES = {{"konoha", "Konoha", "Konohagakure"}, {"suna", "Suna", "Sunagakure"},
            {"kiri", "Kiri", "Kirigakure"}, {"kumo", "Kumo", "Kumogakure"}, {"iwa", "Iwa", "Iwagakure"},
            {"ame", "Ame", "Amegakure"}};

    /** Heure locale « HH:mm » d'une date ISO 8601 (vide si absente). */
    private static String heure(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try { return DateTimeFormatter.ofPattern("HH:mm").format(Instant.parse(iso).atZone(ZONE)); }
        catch (RuntimeException e) { return ""; }
    }

    private static String duree(String iso) {
        try {
            long m = Math.max(0, Duration.between(Instant.parse(iso), Instant.now()).toMinutes());
            return m + " min";
        } catch (RuntimeException e) { return "—"; }
    }

    /** Heure d'arrivée au hall, déduite de l'attente (le serveur envoie les minutes d'attente). */
    private static String arrivee(JsonObject c) {
        if (!c.has("attenteMin")) return "";
        return DateTimeFormatter.ofPattern("HH:mm").format(Instant.now().minusSeconds(num(c, "attenteMin") * 60L).atZone(ZONE));
    }

    /** « Dimanche 25/10 · convocations 21:30 – 22:00 » (les heures de la file). */
    private static String dateSeance(JsonArray file) {
        LocalDate j = LocalDate.now(ZONE);
        String jour = j.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        String t = Character.toUpperCase(jour.charAt(0)) + jour.substring(1) + " " + j.format(DateTimeFormatter.ofPattern("dd/MM"));
        String min = null, max = null;
        for (JsonElement el : file) {
            String c = str(el.getAsJsonObject(), "convocation");
            if (c.isEmpty()) continue;
            if (min == null || c.compareTo(min) < 0) min = c;
            if (max == null || c.compareTo(max) > 0) max = c;
        }
        if (min == null) return t;
        return t + "  ·  " + (min.equals(max) ? "convocation " + min : "convocations " + min + " – " + max);
    }

    /** 2000 → « 2 000 » (espace insécable). */
    static String nombre(int n) {
        String s = Integer.toString(n);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /* =================================================================== banc */

    void debugChoix(String resultat, String motif, String notes) {
        if (dossierU == null) return;
        DouaneClient.Brouillon b = DouaneClient.brouillon(dossierU);
        b.choix = resultat;
        motifBox.setValue(motif);
        notesBox.setValue(notes);
        motifBox.moveCursorToEnd(false);
        s.setFocused(motifBox);
        motifBox.setFocused(true);
        sauver();
    }

    void debugRetrait(String u, String motif) {
        ouvrirRetrait(u);
        retraitBox.setValue(motif);
    }
}
