package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.anchor.ProjectionMonde;
import fr.reborn.hud.concentration.Plaques;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.recit.Gabarit;
import fr.reborn.hud.recit.Recit;
import fr.reborn.hud.recit.RecitHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Outil <b>« Plaques »</b> du Poste de garde (F7 › Outils ; lot 4, étape B ; planches c3, c5) : les textes posés par le
 * staff, lus <b>seulement en concentration, face à l'objet</b>. Protocole {@code plaques}, {@code plaque_creer},
 * {@code plaque_modifier}, {@code plaque_deplacer}, {@code plaque_suppr}, {@code plaque_restaurer}, {@code plaque_tp} sur
 * {@code reborn:staff} ; réponses {@code toast} puis {@code plaques} (PLUGINS_REFERENCE §4 undecies.3). Le serveur relit
 * le grade et l'ancre à chaque message : le volet ne fait que proposer ce que les droits permettent.
 *
 * <ul>
 *   <li><b>Liste</b> : plaques proches (32 blocs) puis plus loin (64), filtres Proches · Miennes · Temporaires ·
 *       Corbeille (Helper et plus), recherche sur le titre, le texte ou l'id ; dans le monde, quatre coins fins et l'id de
 *       chaque plaque, pour le staff seul ({@link MondePlaques}).</li>
 *   <li><b>Fiche</b> : ancre, portée, état, auteur, lectures, texte ; aperçu devant l'objet (sans être concentré),
 *       modifier, déplacer (sélecteur), aller devant (journalisé), activer / désactiver, supprimer (motif, corbeille 30
 *       jours), restaurer (Admin).</li>
 *   <li><b>Éditeur</b> : ancre choisie au <b>sélecteur façon Axiom</b> ({@link SelecteurPlaque} : le volet se replie en
 *       bandeau, la souris vise un bloc, une boîte de blocs ou une entité) ou l'objet en main ; titre interne ; texte à
 *       couleurs de la narration (700 caractères, avertissement au-delà de 450) ; portée 2 à 16 ; expiration (24 h au
 *       plus et obligatoire pour un animateur) ; <b>couleur du halo</b> (planche c6 : huit préréglages, « Personnalisée »
 *       avec sélecteur en ligne, {@link GardeHalo}) ; <b>aperçu en direct</b> devant l'objet, taille réelle, halo compris
 *       ({@link RecitHud.Apercu#dessinerAncre}).</li>
 * </ul>
 * Droits (décision du 07/11) : Helper voit, prévisualise, se téléporte ; Modérateur crée, modifie, déplace, supprime ;
 * Admin restaure ; animateur en mode animation : ses plaques temporaires seulement.
 */
final class GardePlaques {

    static final int TEXTE_MAX = 700, TEXTE_AVERTI = 450, PORTEE_MIN = 2, PORTEE_MAX = 16, PORTEE_DEFAUT = 6;
    private static final int PROCHES = 32;
    private static final String[][] EXPIRES = {{"0", "Jamais"}, {"60", "1 h"}, {"1440", "24 h"}, {"10080", "7 jours"}};
    private static final String[][] EXPIRES_ANIM = {{"60", "1 h"}, {"1440", "24 h"}};
    private static final long ATTENTE_FRAPPE_MS = 250;

    private final StaffScreen s;
    final SelecteurPlaque sel = new SelecteurPlaque();
    final GardeEditeur editeur = new GardeEditeur(TEXTE_MAX);
    private final RecitHud.Apercu apercu = new RecitHud.Apercu();
    /** Rangée « Halo » de l'éditeur, ligne de la fiche (planche c6). */
    final GardeHalo halo;
    private EditBox rech, titreBox;
    private int ticks;

    /** Vue : « liste », « fiche » ou « editeur ». */
    String vue = "liste";
    String filtre = "proches";
    /* fiche */
    String ficheId;
    boolean ficheCorbeille, apercuFiche;
    private List<Recit.Segment> segsFiche;
    private String segsFicheDe;
    /* éditeur */
    String editId;
    boolean ancreObjet, ancreChangee, apercuVisible = true;
    JsonObject ancre;
    String ancreLib = "", ancreDetail = "";
    final double[] ancreBoite = new double[6];
    boolean ancreBoiteOk, ancreBloc;
    /**
     * Face cliquée du bloc choisi (null : boîte, entité, ou ancre existante sans face) ; {@link #faceSeule} : la plaque ne
     * se lit que de ce côté (« f » dans l'ancre), sinon de tous côtés.
     */
    net.minecraft.core.Direction ancreFace;
    boolean faceSeule = true;
    int portee = PORTEE_DEFAUT, expire, expireInitial;
    private boolean focus, glisse;
    private int ex0, ey0, ex1, ey1;
    private double clicX, clicY;
    private boolean clicMaj;
    private long envoiA;
    private String attendu;
    private List<Recit.Segment> segsVus, segsPoses;
    private long segsVusA;
    private static final Vector3f V = new Vector3f();
    private static final double[] PT = new double[3];

    GardePlaques(StaffScreen s) {
        this.s = s;
        this.halo = new GardeHalo(s);
    }

    void init() {
        rech = s.box(rech, "Titre, texte ou id (P1A)", 40);
        titreBox = s.box(titreBox, "Stèle du premier gardien", 60);
        halo.init();
    }

    /** Ouverture de l'outil : liste demandée. */
    void ouvrir() {
        vue = "liste";
        sel.fermer();
        demander();
        ticks = 0;
    }

    private void demander() {
        JsonObject o = new JsonObject();
        o.addProperty("a", "plaques");
        o.addProperty("r", 64);
        StaffClient.send(o);
    }

    void tick() {
        Minecraft mc = Minecraft.getInstance();
        if ("editeur".equals(vue)) halo.tick();
        if (sel.actif) {
            if (horsVolet(s.mouseX, s.mouseY)) sel.survoler(mc, s.mouseX, s.mouseY);
            else sel.viseK = 0;
        }
        if (++ticks % 100 == 0 && !"editeur".equals(vue) && !sel.actif) demander();
        // création ou enregistrement confirmé par le serveur (« dernier » = l'id) : retour à la fiche
        JsonObject d = StaffClient.plaques();
        if (attendu != null && d != null && StaffClient.plaquesA() >= envoiA && !StaffScreen.str(d, "dernier").isEmpty()) {
            ficheId = StaffScreen.str(d, "dernier");
            ficheCorbeille = false;
            vue = "fiche";
            attendu = null;
            editeur.effacer();
            focus = false;
        }
    }

    /** Outil quitté, volet fermé : aperçu libéré, sélecteur fermé, touches de déplacement relâchées. */
    void liberer() {
        apercu.liberer();
        segsVus = segsPoses = null;
        focus = glisse = false;
        halo.relacher();
        if (sel.actif) relacherTouches();
        sel.fermer();
        montrerCurseur();
        apercuFiche = false;
    }

    /* ================================================================ données */

    private static JsonObject donnees() { return StaffClient.plaques(); }

    private static boolean drapeau(String k) { return StaffScreen.bool(donnees(), k); }

    private JsonObject plaque(String id, boolean corbeille) {
        if (id == null) return null;
        for (JsonElement e : StaffScreen.arr(donnees(), corbeille ? "corbeille" : "l"))
            if (id.equals(StaffScreen.str(e.getAsJsonObject(), "id"))) return e.getAsJsonObject();
        return null;
    }

    /** Boîte de l'ancre d'une ligne de la liste (null pour un objet). */
    private static boolean boite(JsonObject p, double[] out) {
        String k = StaffScreen.str(p, "k");
        if ("bloc".equals(k)) {
            JsonArray b = StaffScreen.arr(p, "b");
            double x = dbl(p, "x"), y = dbl(p, "y"), z = dbl(p, "z");
            int dx = b.size() > 0 ? b.get(0).getAsInt() : 1, dy = b.size() > 1 ? b.get(1).getAsInt() : 1, dz = b.size() > 2 ? b.get(2).getAsInt() : 1;
            out[0] = x; out[1] = y; out[2] = z; out[3] = x + dx; out[4] = y + dy; out[5] = z + dz;
            return true;
        }
        if ("entite".equals(k)) {
            double x = dbl(p, "x"), y = dbl(p, "y"), z = dbl(p, "z");
            double w = Math.max(1, dbl(p, "w")), h = Math.max(1, dbl(p, "h"));
            out[0] = x - w / 2; out[1] = y; out[2] = z - w / 2; out[3] = x + w / 2; out[4] = y + h; out[5] = z + w / 2;
            return true;
        }
        return false;
    }

    private static double dbl(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsDouble() : 0; } catch (RuntimeException e) { return 0; }
    }

    /** « bloc », « bloc 3 × 2 », « meuble », « objet ». */
    private static String typeDe(JsonObject p) {
        String k = StaffScreen.str(p, "k");
        if ("bloc".equals(k)) {
            JsonArray b = StaffScreen.arr(p, "b");
            int dx = b.size() > 0 ? b.get(0).getAsInt() : 1, dy = b.size() > 1 ? b.get(1).getAsInt() : 1, dz = b.size() > 2 ? b.get(2).getAsInt() : 1;
            if (dx * dy * dz <= 1) return "bloc";
            return "bloc " + dx + " × " + dy + (dz > 1 ? " × " + dz : "");
        }
        return "entite".equals(k) ? "meuble" : "objet";
    }

    private static String icone(JsonObject p) {
        return switch (StaffScreen.str(p, "k")) {
            case "entite" -> "lien";
            case "objet" -> "sac";
            default -> "noter";
        };
    }

    private static String distance(JsonObject p) {
        double d = dbl(p, "dist");
        return d < 0 ? "suit son objet" : "à " + Math.round(d) + " bloc" + (Math.round(d) > 1 ? "s" : "");
    }

    /** État court et sa couleur : Absente, Désactivée, Encore 23 h, Active. */
    private static Object[] etat(JsonObject p) {
        if (StaffScreen.bool(p, "absente")) return new Object[]{"Absente", ROUGE};
        if (!StaffScreen.bool(p, "actif")) return new Object[]{"Désactivée", GRIS_2};
        int m = StaffScreen.num(p, "expire");
        if (m > 0) return new Object[]{"Encore " + (m >= 120 ? m / 60 + " h" : m + " min"), TERRE};
        return new Object[]{"Active", OK};
    }

    private static String titre(JsonObject p) {
        String t = StaffScreen.str(p, "titre");
        if (!t.isBlank()) return t;
        String x = StaffScreen.str(p, "texte");
        return x.isBlank() ? "Sans titre" : "« " + x + " »";
    }

    /* ================================================================ fil (zone fixe) */

    /** « < Outils · Plaques » ; « < Plaques · Nouvelle » ; « < Plaques · Nouvelle · Ancre » (sélecteur). */
    void fil(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        boolean hov = s.hover(ix - 3, y - 2, 46, 12);
        pz.icone("gauche", ix - 2, y + 0.5f, 8, hov ? CREME : GRIS);
        String retour = "liste".equals(vue) && !sel.actif ? "Outils" : "Plaques";
        float w = pz.texte(retour, ix + 7, y + 4.5f, 13, hov ? CREME : GRIS, false);
        s.hits.add(new StaffScreen.Hit(ix - 3, y - 2, Math.round(w) + 12, 12, this::retour, null, null));
        float x = ix + 11 + w;
        List<String> morceaux = new ArrayList<>();
        if ("liste".equals(vue) && !sel.actif) morceaux.add("Plaques");
        else if ("fiche".equals(vue) && !sel.actif) morceaux.add(ficheId == null ? "Fiche" : ficheId);
        else {
            morceaux.add(editId == null ? (sel.actif && !"creer".equals(sel.but) ? sel.but : "Nouvelle") : editId);
            if (sel.actif) morceaux.add("Ancre");
        }
        for (int i = 0; i < morceaux.size(); i++) {
            pz.texte("·", x, y + 4.5f, 13, GRIS_2, false);
            x += 6;
            boolean der = i == morceaux.size() - 1;
            x += pz.texte(morceaux.get(i), x, y + 4.5f, 13, der ? CREME : GRIS, der) + 5;
        }
        if (sel.actif) pz.texteD("sélection", ix + iw, y + 4.5f, 11, OR, false);
        pz.filetH(ix, ix + iw, y + 15.5f, FILET);
    }

    /** « < » : sélecteur → éditeur ; éditeur ou fiche → liste ; liste → outils. */
    private void retour() {
        RebornSounds.uiClick();
        if (sel.actif) { fermerSelecteur(); return; }
        if (!"liste".equals(vue)) { vue = "liste"; apercuFiche = false; focus = false; s.scroll = 0; return; }
        s.outil = null;
        s.scroll = 0;
    }

    String pied() {
        if (sel.actif) return null;
        return switch (vue) {
            case "editeur" -> "Ctrl + Entrée : poser  ·  Échap : retour à la liste";
            case "fiche" -> "Échap : retour à la liste";
            default -> "Clic : fiche  ·  F7 : fermer";
        };
    }

    boolean pleineHauteur() { return !sel.actif; }

    /* ================================================================ corps */

    int corps(JsonObject snap, int y) {
        if (sel.actif) return bandeau(y + 4);
        return switch (vue) {
            case "fiche" -> fiche(y + 4);
            case "editeur" -> editeurVue(y + 4);
            default -> liste(y + 2);
        };
    }

    /* ---------------------------------------------------------------- liste */

    private int liste(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        JsonObject d = donnees();
        if (d == null) return s.vide(y, "Lecture des plaques…", "Le serveur ne répond pas encore (ShinobiCore à jour ?).");
        for (String l : s.wrap("Textes lus en concentration, face à l'objet. Outil ouvert : les plaques proches sont marquées dans le monde, pour toi seul.", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 4;
        s.champ(rech, ix, y, iw, "chercher");
        y += 23;
        boolean tout = drapeau("voirTout");
        String q = rech.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        JsonArray l = StaffScreen.arr(d, "l"), c = StaffScreen.arr(d, "corbeille");
        int nProches = 0, nMiennes = 0, nTemp = 0;
        for (JsonElement e : l) {
            JsonObject p = e.getAsJsonObject();
            if (dbl(p, "dist") <= PROCHES) nProches++;
            if (StaffScreen.bool(p, "moi")) nMiennes++;
            if (StaffScreen.bool(p, "temporaire")) nTemp++;
        }
        String[][] filtres = tout
            ? new String[][]{{"proches", "Proches", "" + nProches}, {"miennes", "Miennes", "" + nMiennes}, {"temporaires", "Temporaires", "" + nTemp}, {"corbeille", "Corbeille", "" + c.size()}}
            : new String[][]{{"proches", "Proches", "" + nProches}, {"miennes", "Miennes", "" + nMiennes}};
        if (!tout && "corbeille".equals(filtre)) filtre = "proches";
        float ox = ix;
        for (String[] f : filtres) {
            String lab = f[1] + " " + f[2];
            float w = pz.largeur(lab, 13, f[0].equals(filtre));
            if (ox + w > ix + iw) { ox = ix; y += 13; }
            ox += s.mot(lab, ox, y + 4, 13, f[0].equals(filtre), () -> { filtre = f[0]; s.scroll = 0; }) + 10;
        }
        y += 14;
        if ("corbeille".equals(filtre)) return corbeille(c, q, y + 2);
        int n = 0;
        boolean loin = false;
        for (JsonElement e : l) {
            JsonObject p = e.getAsJsonObject();
            if ("miennes".equals(filtre) && !StaffScreen.bool(p, "moi")) continue;
            if ("temporaires".equals(filtre) && !StaffScreen.bool(p, "temporaire")) continue;
            if (!q.isEmpty() && !(StaffScreen.str(p, "id") + " " + StaffScreen.str(p, "titre") + " " + StaffScreen.str(p, "texte"))
                .toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            double dist = dbl(p, "dist");
            if ("proches".equals(filtre) && !loin && dist > PROCHES) {
                loin = true;
                y += 3;
                pz.capitales("Plus loin · 32 à 64 blocs", ix, y + 4, 10, GRIS_2);
                y += 11;
            }
            y = ligne(p, y);
            n++;
        }
        if (n == 0) y = s.vide(y, q.isEmpty() ? "Aucune plaque ici." : "Aucune plaque ne correspond à « " + q + " ».",
            drapeau("anim") && !tout ? "En mode animation, tu ne vois que tes plaques temporaires." : null);
        y += 6;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 8;
        boolean creer = drapeau("peutCreer");
        String lab = "Nouvelle plaque";
        int bw = s.largeurBouton(lab);
        s.refusVerrou = "Réservé aux modérateurs (ou au mode animation).";
        s.bouton(ix + iw - bw, y, bw, lab, true, this::nouvelle, !creer);
        s.refusVerrou = null;
        for (String li : s.wrap(drapeau("temporaire") ? "Mode animation : plaque temporaire, 24 h au plus." : "Sur ce que tu vises (8 blocs) ou sur l'objet en main.", iw - bw - 8, 11)) {
            pz.texte(li, ix, y + 4, 11, GRIS_2, false);
            y += 8;
        }
        return Math.max(y, y + 0) + 22;
    }

    private int ligne(JsonObject p, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        int h = 26;
        String id = StaffScreen.str(p, "id");
        boolean choisie = id.equals(ficheId);
        boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov || choisie) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
        if (choisie) pz.fond(s.px0() + 1, y, s.px0() + 2, y + h, OR);
        pz.icone(icone(p), ix, y + 6, 8, hov ? OR : GRIS);
        Object[] et = etat(p);
        float ew = pz.texteD("• " + et[0], ix + iw, y + 7.5f, 11, (int) et[1], false);
        s.txt(titre(p), ix + 13, y + 7.5f, iw - 22 - ew, 14, CREME, false);
        String sous = id + " · " + typeDe(p) + " · " + distance(p) + " · " + lu(p);
        String droite = StaffScreen.bool(p, "temporaire") ? "temporaire · " + StaffScreen.str(p, "par") : "portée " + StaffScreen.num(p, "p");
        float dw = pz.texteD(droite, ix + iw, y + 17.5f, 11, GRIS_2, false);
        s.txt(sous, ix + 13, y + 17.5f, iw - 20 - dw, 11, GRIS_2, false);
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> {
            ficheId = id;
            ficheCorbeille = false;
            vue = "fiche";
            apercuFiche = false;
            s.scroll = 0;
            RebornSounds.uiClick();
        }, null, null));
        y += h;
        pz.filetH(ix + 13, ix + iw, y - 0.5f, SEP);
        return y;
    }

    private static String lu(JsonObject p) {
        long n = StaffScreen.lng(p, "lectures");
        return n == 0 ? "jamais lue" : "lue " + n + " fois";
    }

    private int corbeille(JsonArray c, String q, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        int n = 0;
        for (JsonElement e : c) {
            JsonObject p = e.getAsJsonObject();
            String id = StaffScreen.str(p, "id");
            if (!q.isEmpty() && !(id + " " + StaffScreen.str(p, "titre") + " " + StaffScreen.str(p, "texte")).toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            int h = 26;
            boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
            if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
            pz.icone("suppr", ix, y + 6, 8, GRIS_2);
            float rw = pz.texteD("encore " + StaffScreen.num(p, "reste") + " j", ix + iw, y + 7.5f, 11, GRIS, false);
            s.txt(titre(p), ix + 13, y + 7.5f, iw - 20 - rw, 14, GRIS, false);
            s.txt(id + " · par " + StaffScreen.str(p, "par") + " le " + StaffScreen.str(p, "le") + " · « " + StaffScreen.str(p, "motif") + " »",
                ix + 13, y + 17.5f, iw - 13, 11, GRIS_2, false);
            if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> {
                ficheId = id;
                ficheCorbeille = true;
                vue = "fiche";
                s.scroll = 0;
                RebornSounds.uiClick();
            }, null, null));
            y += h;
            pz.filetH(ix + 13, ix + iw, y - 0.5f, SEP);
            n++;
        }
        if (n == 0) y = s.vide(y, "Corbeille vide.", "Une plaque supprimée y reste 30 jours ; un admin peut la restaurer.");
        return y + 4;
    }

    /* ---------------------------------------------------------------- fiche */

    private int fiche(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        JsonObject p = plaque(ficheId, ficheCorbeille);
        if (p == null) return s.vide(y, "Plaque introuvable.", "Elle a pu être supprimée ou sortir des 64 blocs.");
        pz.icone(icone(p), ix, y + 1, 8, OR);
        s.txt(titre(p), ix + 13, y + 5, iw - 13, 15, CREME, true);
        y += 13;
        pz.texte(StaffScreen.str(p, "id") + " · " + typeDe(p) + " · " + StaffScreen.str(p, "monde"), ix + 13, y + 3, 11, GRIS_2, false);
        y += 12;
        if (!ficheCorbeille) {
            Object[] et = etat(p);
            y = info("État", (String) et[0] + (StaffScreen.bool(p, "temporaire") ? " · temporaire" : ""), (int) et[1], y);
            y = info("Ancre", coords(p) + " · " + distance(p), CREME, y);
            y = info("Portée", StaffScreen.num(p, "p") + " blocs de lecture (+ 1,5 au serveur)", CREME, y);
            y = halo.ligneFiche(y, fr.reborn.hud.concentration.HaloPlaque.code(StaffScreen.str(p, "halo")));
            y = info("Posée", "par " + StaffScreen.str(p, "par") + " le " + StaffScreen.str(p, "le"), CREME, y);
            if (!StaffScreen.str(p, "modifPar").isEmpty())
                y = info("Modifiée", "par " + StaffScreen.str(p, "modifPar") + " le " + StaffScreen.str(p, "modifLe") + " · rév. " + StaffScreen.num(p, "rev"), CREME, y);
            y = info("Lectures", lu(p), CREME, y);
            if (StaffScreen.bool(p, "absente")) y = note("Le meuble de cette plaque a disparu : elle n'est plus lue. Déplace-la ou supprime-la.", ROUGE, y);
        } else {
            y = info("Supprimée", "par " + StaffScreen.str(p, "par") + " le " + StaffScreen.str(p, "le"), CREME, y);
            y = info("Motif", StaffScreen.str(p, "motif"), CREME, y);
            y = info("Corbeille", "encore " + StaffScreen.num(p, "reste") + " jour(s)", CREME, y);
        }
        y += 3;
        // texte, à plat (les couleurs se voient dans l'aperçu)
        String texte = ficheCorbeille ? StaffScreen.str(p, "texte") : aPlat(StaffScreen.arr(p, "segs"));
        int th = 0;
        List<String> lignes = s.wrap(texte.replace('\n', ' '), iw - 10, 12);
        th = Math.max(1, lignes.size()) * 9 + 8;
        pz.fond(ix, y, ix + iw, y + th, NUIT_2);
        pz.cadre(ix, y, iw, th, FILET_2);
        int yy = y + 4;
        for (String l : lignes) { pz.texte(l, ix + 5, yy + 4, 12, CREME, false); yy += 9; }
        y += th + 8;
        // actions
        if (ficheCorbeille) {
            boolean ok = drapeau("peutRestaurer");
            s.refusVerrou = "Réservé aux admins.";
            y = action("Restaurer", "revenir", ok, () -> envoyer("plaque_restaurer", ficheId), y, OR);
            s.refusVerrou = null;
            y = action("Copier l'id", "copier", true, this::copierId, y, CREME);
            return y + 4;
        }
        boolean modif = StaffScreen.bool(p, "peutModifier"), objet = "objet".equals(StaffScreen.str(p, "k"));
        y = action(apercuFiche ? "Masquer l'aperçu" : "Aperçu devant l'objet (sans te concentrer)", "lecture", true,
            () -> apercuFiche = !apercuFiche, y, OR);
        if (apercuFiche && apercu.pages() > 1) {
            // le découpage des pages, comme chez le joueur (qui les tourne au clic gauche)
            pageApercu(ix + 18, y + 3);
            y += 14;
        }
        s.refusVerrou = "Réservé aux modérateurs (l'animateur : ses plaques temporaires).";
        y = action("Modifier le texte", "noter", modif, () -> modifier(p), y, CREME);
        s.refusVerrou = objet ? "Une plaque d'objet suit son objet." : "Réservé aux modérateurs (l'animateur : ses plaques temporaires).";
        y = action("Déplacer sur une autre ancre", "aller", modif && !objet, () -> ouvrirSelecteur(ficheId), y, CREME);
        s.refusVerrou = objet ? "Une plaque d'objet suit son objet." : "Réservé au staff (Helper et plus).";
        y = action("Aller devant", "porte", drapeau("voirTout") && !objet, () -> envoyer("plaque_tp", ficheId), y, CREME);
        s.refusVerrou = "Réservé aux modérateurs (l'animateur : ses plaques temporaires).";
        boolean actif = StaffScreen.bool(p, "actif");
        y = action(actif ? "Désactiver" : "Activer", "barriere", modif, () -> {
            JsonObject o = base("plaque_modifier", ficheId);
            o.addProperty("actif", !actif);
            StaffClient.send(o);
        }, y, CREME);
        s.refusVerrou = null;
        y = action("Copier l'id", "copier", true, this::copierId, y, CREME);
        s.refusVerrou = "Réservé aux modérateurs (l'animateur : ses plaques temporaires).";
        y = action("Supprimer…", "suppr", modif, () -> supprimer(p), y, ROUGE);
        s.refusVerrou = null;
        return y + 4;
    }

    private static String coords(JsonObject p) {
        String k = StaffScreen.str(p, "k");
        if ("objet".equals(k)) return "objet porteur (PDC reborn:plaque)";
        return Math.round(Math.floor(dbl(p, "x"))) + " " + Math.round(Math.floor(dbl(p, "y"))) + " " + Math.round(Math.floor(dbl(p, "z")));
    }

    private static String aPlat(JsonArray segs) {
        StringBuilder b = new StringBuilder();
        for (JsonElement e : segs) {
            try { b.append(e.getAsJsonArray().get(0).getAsString()); } catch (RuntimeException ignored) { }
        }
        return b.toString();
    }

    private static List<Recit.Segment> segments(JsonArray segs) {
        List<Recit.Segment> l = new ArrayList<>();
        for (JsonElement e : segs) {
            try {
                JsonArray a = e.getAsJsonArray();
                l.add(new Recit.Segment(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsString() : ""));
            } catch (RuntimeException ignored) { }
        }
        return l;
    }

    private int info(String label, String val, int col, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        pz.texte(label, ix, y + 4, 12, GRIS, false);
        s.txt(val, ix + 46, y + 4, iw - 46, 12, col, false);
        return y + 10;
    }

    private int note(String t, int col, int y) {
        for (String l : s.wrap(t, s.iw(), 11)) {
            s.pz.texte(l, s.ix(), y + 4, 11, col, false);
            y += 8;
        }
        return y + 3;
    }

    /** Ligne d'action (icône, libellé), éteinte (cadenas, raison au clic) si {@code ok} est faux. */
    private int action(String lab, String ic, boolean ok, Runnable r, int y, int col) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw(), h = 16;
        boolean hov = ok && s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
        pz.icone(ic, ix, y + 4, 8, ok ? (hov ? OR : GRIS) : GRIS_3);
        pz.texte(lab, ix + 13, y + 8, 13, ok ? (hov ? CREME : col) : GRIS_3, false);
        if (!ok) pz.icone("verrou", ix + iw - 8, y + 5, 6, GRIS_3);
        String refus = s.refusVerrou;
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, ok ? () -> { r.run(); RebornSounds.uiClick(); } : null, null, refus));
        return y + h;
    }

    private void copierId() {
        Minecraft.getInstance().keyboardHandler.setClipboard(ficheId);
        StaffClient.toast(ficheId + " · id copié");
    }

    private void supprimer(JsonObject p) {
        String id = StaffScreen.str(p, "id");
        s.formulaire.ouvrir("suppr", "Supprimer la plaque " + id + " ?", "« " + StaffScreen.str(p, "texte") + " »", "Supprimer", true, null,
            List.of(GardeFormulaire.Champ.texte("m", "Motif (obligatoire, 3 caractères au moins)", "Doublon, texte faux, event fini…", true)),
            List.of("Corbeille 30 jours : un admin peut la restaurer. Inscrit au journal Staff."), v -> {
                JsonObject o = base("plaque_suppr", id);
                o.addProperty("motif", v.getOrDefault("m", ""));
                StaffClient.send(o);
                vue = "liste";
            });
    }

    private static JsonObject base(String a, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", a);
        if (id != null) o.addProperty("id", id);
        return o;
    }

    private void envoyer(String a, String id) {
        StaffClient.send(base(a, id));
        RebornSounds.confirm();
    }

    /* ---------------------------------------------------------------- éditeur */

    private void nouvelle() {
        editId = null;
        ancre = null;
        ancreObjet = false;
        ancreChangee = false;
        ancreBoiteOk = false;
        ancreLib = ancreDetail = "";
        ancreFace = null;
        faceSeule = true;
        editeur.effacer();
        titreBox.setValue("");
        portee = PORTEE_DEFAUT;
        expire = expireInitial = drapeau("temporaire") ? 1440 : 0;
        halo.effacer();
        apercuVisible = true;
        vue = "editeur";
        focus = true;
        s.scroll = 0;
        sel.effacer();
        RebornSounds.uiClick();
    }

    private void modifier(JsonObject p) {
        editId = StaffScreen.str(p, "id");
        ancre = null;
        ancreChangee = false;
        ancreObjet = "objet".equals(StaffScreen.str(p, "k"));
        ancreBoiteOk = boite(p, ancreBoite);
        ancreBloc = "bloc".equals(StaffScreen.str(p, "k"));
        ancreLib = (ancreObjet ? "Objet porteur" : typeDe(p).substring(0, 1).toUpperCase() + typeDe(p).substring(1)) + " · " + coords(p);
        ancreDetail = distance(p) + " · ancre actuelle";
        // face de l'ancre actuelle (bloc posé sur une face) : l'aperçu se pose sur elle
        String f = StaffScreen.str(p, "f");
        ancreFace = f.isEmpty() ? null : net.minecraft.core.Direction.byName(f);
        faceSeule = true;
        editeur.poser(segments(StaffScreen.arr(p, "segs")));
        titreBox.setValue(StaffScreen.str(p, "titre"));
        portee = Math.max(PORTEE_MIN, Math.min(PORTEE_MAX, StaffScreen.num(p, "p")));
        expire = expireInitial = StaffScreen.num(p, "expire");
        halo.poser(fr.reborn.hud.concentration.HaloPlaque.code(StaffScreen.str(p, "halo")));
        vue = "editeur";
        focus = true;
        s.scroll = 0;
        sel.effacer();
    }

    private int editeurVue(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        boolean temp = drapeau("temporaire") && editId == null;
        for (String l : s.wrap("L'ancre est ce que tu as choisi au sélecteur : un bloc, une boîte de blocs, un meuble ou un objet, 8 blocs au plus.", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 4;
        // ------------------------------------------------------------------ ancre
        pz.texte("Ancre", ix, y + 4, 12, GRIS, false);
        if (editId == null) {
            float ox = ix + 46;
            ox += s.mot("Sélection", ox, y + 4, 13, !ancreObjet, () -> ancreObjet = false) + 10;
            s.mot("Objet en main", ox, y + 4, 13, ancreObjet, () -> ancreObjet = true);
        }
        y += 13;
        int bh = 25;
        pz.fond(ix, y, ix + iw, y + bh, NUIT_2);
        boolean aAncre = ancreObjet || ancre != null || editId != null;
        pz.cadre(ix, y, iw, bh, aAncre ? OR_DOUX : FILET_2);
        pz.icone(ancreObjet ? "sac" : "verrou", ix + 5, y + 8, 8, aAncre ? OR : GRIS_2);
        String lib, det;
        if (ancreObjet && editId == null) {
            lib = "Objet en main";
            det = "l'objet que tu tiens portera la plaque (PDC reborn:plaque)";
        } else if (ancre != null || editId != null) {
            lib = ancreLib;
            det = ancreDetail;
        } else {
            lib = "Aucune ancre";
            det = "choisis un bloc, une boîte ou un meuble dans le monde";
        }
        String lien = ancreObjet ? null : ancre != null || editId != null ? "Changer" : "Choisir";
        float lw = lien == null ? 0 : pz.largeur(lien, 13, false) + 12;
        s.txt(lib, ix + 18, y + 8, iw - 24 - lw, 13, aAncre ? CREME : GRIS, false);
        s.txt(det, ix + 18, y + 18, iw - 24, 11, GRIS_2, false);
        if (lien != null) {
            boolean objetFixe = editId != null && ancreObjet;
            if (!objetFixe) s.motCouleur(lien, ix + iw - lw + 4, y + 8, 13, OR, () -> { ouvrirSelecteur(editId == null ? "creer" : "editeur"); RebornSounds.uiClick(); });
        }
        y += bh + 3;
        // bloc choisi au sélecteur : lisible du côté cliqué seulement (inscription sur la face intérieure d'un mur), ou
        // de tous côtés (stèle au milieu d'une place)
        if (ancre != null && ancreFace != null && "bloc".equals(StaffScreen.str(ancre, "k"))) {
            pz.texte("Lisible", ix, y + 4, 12, GRIS, false);
            float fx = ix + 46;
            fx += s.mot("côté " + SelecteurPlaque.faceFr(ancreFace) + " seulement", fx, y + 4, 13, faceSeule, () -> choisirFace(true)) + 10;
            s.mot("tous côtés", fx, y + 4, 13, !faceSeule, () -> choisirFace(false));
            y += 13;
        }
        y = note("Gardée en or dans le monde tant que l'éditeur est ouvert.", GRIS_3, y);
        // ------------------------------------------------------------------ titre
        pz.texte("Titre", ix, y + 4, 12, GRIS, false);
        pz.texteD("staff seulement, jamais montré", ix + iw, y + 4, 11, GRIS_2, false);
        y += 9;
        s.champ(titreBox, ix, y, iw, null);
        y += 22;
        // ------------------------------------------------------------------ texte
        int n = editeur.caracteres();
        pz.texte("Texte", ix, y + 3, 12, GRIS, false);
        pz.texteD(n + " / " + TEXTE_MAX, ix + iw, y + 3, 11, n >= TEXTE_MAX ? ROUGE : n > TEXTE_AVERTI ? TERRE : GRIS_2, false);
        y += 8;
        y = zoneTexte(y, ix, iw);
        y = pastilles(y + 3, ix);
        if (n > TEXTE_AVERTI) y = note("Long : au-delà de 450 caractères, le joueur tourne les pages (clic gauche).", TERRE, y);
        y += 4;
        // ------------------------------------------------------------------ portée
        pz.texte("Portée", ix, y + 8.5f, 12, GRIS, false);
        int bx = ix + 46;
        bx += petit(bx, y + 3, "−", () -> portee = Math.max(PORTEE_MIN, portee - 1)) + 3;
        pz.fond(bx, y, bx + 22, y + 17, NUIT_2);
        pz.cadre(bx, y, 22, 17, FILET_2);
        pz.texteC(String.valueOf(portee), bx + 11, y + 8.5f, 14, CREME, false);
        bx += 25;
        bx += petit(bx, y + 3, "+", () -> portee = Math.min(PORTEE_MAX, portee + 1)) + 6;
        pz.texte("blocs de lecture (2 à 16)", bx, y + 8.5f, 12, GRIS_2, false);
        y += 22;
        // ------------------------------------------------------------------ expiration
        pz.texte("Expire", ix, y + 4, 12, GRIS, false);
        float ox = ix + 46;
        String[][] ex = temp || editId != null && drapeau("temporaire") ? EXPIRES_ANIM : EXPIRES;
        boolean trouve = false;
        for (String[] e : ex) if (Integer.parseInt(e[0]) == expire) trouve = true;
        for (String[] e : ex) {
            int v = Integer.parseInt(e[0]);
            ox += s.mot(e[1], ox, y + 4, 13, v == expire, () -> expire = v) + 10;
        }
        if (!trouve && expire > 0) pz.texte("(" + (expire >= 120 ? expire / 60 + " h" : expire + " min") + " restantes)", ox, y + 4, 11, GRIS_2, false);
        y += 13;
        if (temp) y = note("Mode animation : la plaque est temporaire (24 h au plus) ; toi seul la modifies.", ANIM, y);
        y += 2;
        // ------------------------------------------------------------------ halo (planche c6)
        y = halo.rangee(y);
        // ------------------------------------------------------------------ aperçu
        pz.texte("Aperçu", ix, y + 4, 12, GRIS, false);
        s.txt(ancreBoiteOk ? "devant l'objet, taille réelle, comme en concentration" : "au centre de l'écran (pas encore d'ancre)", ix + 46, y + 4, iw - 46, 11, GRIS_2, false);
        y += 11;
        float ax = ix + 46;
        ax += s.mot("Afficher", ax, y + 4, 13, apercuVisible, () -> apercuVisible = true) + 10;
        ax += s.mot("Masquer", ax, y + 4, 13, !apercuVisible, () -> apercuVisible = false) + 12;
        // texte de plusieurs pages : le staff voit le découpage avant de poser (« Page 1 / 2 › », un clic : la suivante)
        if (apercuVisible) pageApercu(ax, y + 4);
        y += 18;
        // ------------------------------------------------------------------ poser
        boolean vide = editeur.vide();
        boolean sansAncre = editId == null && !ancreObjet && ancre == null;
        String lab = editId == null ? "Poser la plaque" : "Enregistrer";
        int bw = s.largeurBouton(lab);
        s.refusVerrou = vide ? "Écris d'abord le texte." : sansAncre ? "Choisis d'abord l'ancre (sélecteur)." : "Texte trop long (700 au plus).";
        s.bouton(ix + iw - bw, y, bw, lab, true, this::poser, vide || sansAncre || n > TEXTE_MAX);
        s.refusVerrou = null;
        if (!vide) s.motCouleur("Effacer", ix, y + 8.5f, 12, GRIS_2, () -> { editeur.effacer(); RebornSounds.uiClick(); });
        y += 25;
        pz.filetH(ix, ix + iw, y - 0.5f, FILET);
        y += 6;
        pz.capitales("Sélecteur", ix, y + 4, 10, GRIS_2);
        y += 10;
        y = note("Changer : le volet se replie, la souris choisit dans le monde (clic : un bloc ; Maj + clic : second coin d'une boîte ; un meuble ou un objet se choisit d'un clic). Entrée valide, Échap revient ici sans rien changer.", GRIS_2, y);
        y += 4;
        pz.capitales("Halo", ix, y + 4, 10, GRIS_2);
        y += 10;
        y = note(GardeHalo.ASTUCE + " Le halo naît et meurt avec le texte ; « Aucun halo » laisse le texte seul.", GRIS_2, y);
        return y + 4;
    }

    private int zoneTexte(int y, int ix, int iw) {
        GardeArt.Pinceau pz = s.pz;
        editeur.mettreEnPage(pz, ix + 5, y + 5 + GardeEditeur.PAS / 2f, iw - 10);
        int h = Math.max(4, editeur.nbLignes()) * GardeEditeur.PAS + 10;
        pz.fond(ix, y, ix + iw, y + h, NUIT_2);
        pz.cadre(ix, y, iw, h, focus ? OR_DOUX : FILET_2);
        if (editeur.longueur() == 0) {
            pz.texte("Le texte que lira un joueur concentré face à l'objet.", ix + 5, y + 5 + GardeEditeur.PAS / 2f, 14, GRIS_2, false);
            pz.texte("Entrée : à la ligne · couleurs : sélectionne puis une pastille.", ix + 5, y + 5 + GardeEditeur.PAS * 1.5f, 12, GRIS_3, false);
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

    private int pastilles(int y, int ix) {
        GardeArt.Pinceau pz = s.pz;
        int active = editeur.couleurActive();
        float x = ix;
        for (int i = 0; i < GardeEditeur.CLES.length; i++) {
            final int c = i;
            boolean on = active == i;
            String nom = GardeEditeur.NOMS[i];
            float w = 7 + pz.largeur(nom, 12, on);
            int X = Math.round(x) - 1, Y = y - 1;
            int teinte = i == 0 ? 0xFFFFFFFF : GardeEditeur.teinte(i);
            if (on) pz.pastille(x + 2.5f, y + 4.5f, 3f, OR);
            pz.pastille(x + 2.5f, y + 4.5f, 2.2f, teinte);
            boolean hov = s.hover(X, Y, Math.round(w) + 2, 11) && s.cliquable(Y, 11);
            pz.texte(nom, x + 7, y + 4.5f, 12, on || hov ? CREME : GRIS, on);
            if (s.cliquable(Y, 11)) s.hits.add(new StaffScreen.Hit(X, Y, Math.round(w) + 2, 11, () -> {
                editeur.colorer(c);
                focus = true;
                s.setFocused(null);
                RebornSounds.uiClick();
            }, null, null));
            x += w + 8;
        }
        return y + 12;
    }

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

    /** Pose ou enregistre la plaque de l'éditeur. */
    private void poser() {
        if (editeur.vide() || editeur.caracteres() > TEXTE_MAX) return;
        if (editId == null) {
            if (!ancreObjet && ancre == null) return;
            JsonObject o = base("plaque_creer", null);
            JsonObject an;
            if (ancreObjet) {
                an = new JsonObject();
                an.addProperty("k", "objet");
            } else an = ancre;
            o.add("ancre", an);
            o.add("segs", editeur.json());
            o.addProperty("titre", titreBox.getValue().trim());
            o.addProperty("p", portee);
            o.addProperty("expire", expire);
            // halo : absent = ivoire-or (rien de stocké)
            if (halo.code() != fr.reborn.hud.concentration.HaloPlaque.DEFAUT) o.addProperty("halo", halo.valeur());
            StaffClient.send(o);
        } else {
            JsonObject o = base("plaque_modifier", editId);
            o.add("segs", editeur.json());
            o.addProperty("titre", titreBox.getValue().trim());
            o.addProperty("p", portee);
            if (expire != expireInitial) o.addProperty("expire", expire);
            // seulement s'il change ; vide = retour à l'ivoire-or
            if (halo.change()) o.addProperty("halo", halo.valeur());
            StaffClient.send(o);
            if (ancreChangee && ancre != null) {
                JsonObject d = base("plaque_deplacer", editId);
                d.add("ancre", ancre);
                StaffClient.send(d);
            }
        }
        halo.retenir();
        envoiA = System.currentTimeMillis();
        attendu = editId == null ? "creer" : editId;
        RebornSounds.confirm();
    }

    /* ---------------------------------------------------------------- sélecteur : bandeau (volet replié) */

    /** Ouvre le sélecteur : « creer » (nouvelle), « editeur » (ancre de la plaque éditée) ou l'id à déplacer. */
    void ouvrirSelecteur(String but) {
        sel.ouvrir(but);
        if (!"creer".equals(but) && !"editeur".equals(but)) sel.effacer();
        focus = false;
        s.setFocused(null);
        s.scroll = 0;
    }

    private void fermerSelecteur() {
        relacherTouches();
        sel.fermer();
        montrerCurseur();
    }

    /** Lisible du côté cliqué seulement ({@code seule}) ou de tous côtés : « f » ajouté à l'ancre ou retiré. */
    private void choisirFace(boolean seule) {
        faceSeule = seule;
        if (ancre == null || ancreFace == null) return;
        if (seule) ancre.addProperty("f", ancreFace.getName());
        else ancre.remove("f");
        RebornSounds.uiClick();
    }

    /** « Page 1 / 2 › » de l'aperçu (texte de plusieurs pages) : un clic montre la suivante. Refait au changement seul. */
    private void pageApercu(float x, float y) {
        int n = apercu.pages();
        if (n < 2) return;
        if (apercu.page() != pageVue || n != pagesVues) {
            pageVue = apercu.page();
            pagesVues = n;
            pageLib = "Page " + (pageVue + 1) + " / " + n + "  ›";
        }
        s.motCouleur(pageLib, x, y, 13, OR, () -> { apercu.pageSuivante(); RebornSounds.uiClick(); });
    }

    private int pageVue = -1, pagesVues = -1;
    private String pageLib = "";

    /** Le point GUI ({@code x, y}) est hors du volet : à sa gauche, ou sous le bandeau replié du sélecteur. */
    private boolean horsVolet(double x, double y) {
        return x < s.px0() - 2 || y > s.py1() + 2;
    }

    private int bandeau(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        int t = sel.type();
        pz.texte("Type", ix, y + 4, 11, GRIS, false);
        float ox = ix + 40;
        String[] types = {"Bloc", "Boîte", "Entité"};
        for (int i = 0; i < 3; i++) {
            float w = pz.largeur(types[i], 13, i == t);
            pz.texte(types[i], ox, y + 4, 13, i == t ? CREME : GRIS_2, i == t);
            if (i == t) pz.filetH(ox, ox + w, y + 9.5f, OR);
            ox += w + 9;
        }
        pz.texteD("suit ta sélection", ix + iw, y + 4, 10, GRIS_3, false);
        y += 16;
        // visé
        pz.texte("Visé", ix, y + 4, 11, GRIS, false);
        if (sel.viseK == 0) pz.texte("rien (vise un bloc ou un meuble)", ix + 40, y + 4, 12, GRIS_2, false);
        else {
            s.txt(sel.viseNom + " · " + (sel.viseK == 2 ? "entité" : "bloc"), ix + 40, y + 4, iw - 40, 13, sel.viseLoin ? GRIS : CREME, false);
            String d = SelecteurPlaque.coin(sel.viseBoite) + " · " + SelecteurPlaque.un(sel.viseDist) + " blocs · "
                + (sel.viseLoin ? "hors de portée (8 blocs)" : "en vue");
            pz.texte(d, ix + 40, y + 13, 11, sel.viseLoin ? ROUGE : GRIS_2, false);
        }
        y += 24;
        // choisi
        pz.texte("Choisi", ix, y + 4, 11, GRIS, false);
        if (!sel.choisi()) pz.texte("rien encore", ix + 40, y + 4, 13, GRIS_2, false);
        else {
            pz.icone("verrou", ix + 40, y, 8, OR);
            s.txt(sel.libelleChoix(), ix + 52, y + 4, iw - 52, 13, CREME, false);
            pz.texte(sel.detailChoix(), ix + 52, y + 13, 11, OR, false);
        }
        y += 24;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 6;
        // touches
        Minecraft mc = Minecraft.getInstance();
        float half = ix + iw / 2f;
        float x = ix;
        x += touche("Clic", x, y) + 4;
        pz.texte("choisir", x, y + 5, 11, GRIS, false);
        x = half;
        x += touche("Maj", x, y) + 2;
        pz.texte("+", x, y + 5, 11, GRIS_2, false);
        x += 6;
        x += touche("Clic", x, y) + 4;
        pz.texte("second coin", x, y + 5, 11, GRIS, false);
        y += 14;
        x = ix;
        x += touche("Clic droit", x, y) + 4;
        pz.texte("tourner", x, y + 5, 11, GRIS, false);
        x = half;
        for (var k : new net.minecraft.client.KeyMapping[]{mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight})
            x += touche(k.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT), x, y) + 2;
        pz.texte("se déplacer", x + 2, y + 5, 11, GRIS, false);
        y += 16;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 6;
        x = ix;
        x += touche("Échap", x, y + 3) + 4;
        pz.texte("annuler", x, y + 8, 11, GRIS, false);
        String lab = "Valider l'ancre";
        int bw = s.largeurBouton(lab) + 6;
        boolean ok = sel.choisi();
        if (ok) touche("Entrée", ix + iw - bw - 34, y + 3);
        s.refusVerrou = "Choisis d'abord un bloc, une boîte ou un meuble dans le monde.";
        s.bouton(ix + iw - bw, y, bw, lab, true, this::validerSelecteur, !ok);
        s.refusVerrou = null;
        return y + 22;
    }

    /** Touche en petite capsule ; renvoie sa largeur. */
    private float touche(String t, float x, int y) {
        GardeArt.Pinceau pz = s.pz;
        float w = pz.largeur(t, 11, false) + 6;
        pz.cadre(x, y, w, 10, FILET_2);
        pz.texte(t, x + 3, y + 5, 11, GRIS, false);
        return w;
    }

    private void validerSelecteur() {
        if (!sel.choisi()) return;
        // un bloc seul se lit du côté cliqué (au choix dans l'éditeur) ; une boîte ou une entité, de tous côtés
        boolean face = sel.choixK == 1 && sel.choixFace != null;
        JsonObject a = sel.ancreJson(face);
        String but = sel.but;
        if ("creer".equals(but) || "editeur".equals(but)) {
            ancre = a;
            ancreFace = face ? sel.choixFace : null;
            faceSeule = true;
            ancreChangee = "editeur".equals(but);
            ancreObjet = false;
            ancreLib = sel.libelleChoix();
            ancreDetail = (sel.choixK == 2 ? sel.detailChoix() + " · " : SelecteurPlaque.coin(sel.choixBoite) + " · ")
                + "à " + SelecteurPlaque.un(sel.choixDist) + " blocs";
            System.arraycopy(sel.choixBoite, 0, ancreBoite, 0, 6);
            ancreBoiteOk = true;
            ancreBloc = sel.choixK != 3;
            vue = "editeur";
        } else {
            JsonObject o = base("plaque_deplacer", but);
            o.add("ancre", a);
            StaffClient.send(o);
            ficheId = but;
            vue = "fiche";
        }
        fermerSelecteur();
        RebornSounds.confirm();
    }

    /* ================================================================ monde : repères, aperçu */

    /**
     * À chaque image, outil ouvert (avant le volet) : repères dans le monde ({@link MondePlaques}), étiquettes, aperçu
     * ancré devant l'objet.
     */
    void monde(GuiGraphicsExtractor ctx) {
        MondePlaques.debut();
        GardeArt.Pinceau pz = s.pz;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (sel.actif) {
            if (sel.choisi()) {
                // choisi : voile d'or de 9 % et contour d'or ; boîte : numéro de chaque coin
                MondePlaques.voile(sel.choixBoite, null, MondePlaques.VOILE_CHOIX);
                MondePlaques.boite(sel.choixBoite, MondePlaques.OR_PLEIN);
                etiquette(sel.choixBoite, sel.libelleChoix(), sel.choixK == 2 ? sel.detailChoix() : SelecteurPlaque.coin(sel.choixBoite), true);
                if (sel.choixK == 2 && sel.c1 != null && sel.c2 != null) {
                    numeroCoin(sel.c1, "1");
                    numeroCoin(sel.c2, "2");
                }
            }
            if (sel.viseK != 0 && horsVolet(s.mouseX, s.mouseY) && !rotation) {
                MondePlaques.boite(sel.viseBoite, sel.viseLoin ? MondePlaques.HORS : MondePlaques.FIL);
                // face visée : voile crème de 14 % (bloc en portée)
                if (sel.viseK == 1 && !sel.viseLoin && sel.viseFace != null)
                    MondePlaques.voile(sel.viseBoite, sel.viseFace, MondePlaques.VOILE_FACE);
                // Maj enfoncée : la boîte jusqu'au bloc survolé (or à 55 %, 1 px), avant le clic
                if (majEnfoncee(mc) && sel.apercuBoite(PT_APERCU)) MondePlaques.boite(PT_APERCU, MondePlaques.APERCU);
                infobulle();
            }
            pz.vider(ctx);
            return;
        }
        double[] b = PT_BOITE;
        String choisie = "fiche".equals(vue) ? ficheId : "editeur".equals(vue) ? editId : null;
        for (JsonElement e : StaffScreen.arr(donnees(), "l")) {
            JsonObject p = e.getAsJsonObject();
            if (!boite(p, b) || dbl(p, "dist") > 48) continue;
            String id = StaffScreen.str(p, "id");
            boolean ch = id.equals(choisie) && !("editeur".equals(vue) && ancreChangee);
            MondePlaques.boite(b, ch ? MondePlaques.OR_PLEIN : MondePlaques.COINS);
            // l'ancre de l'éditeur porte sa propre étiquette : pas d'id par-dessus
            boolean sousAncre = "editeur".equals(vue) && ancreBoiteOk && croise(b, ancreBoite);
            if (ch) etiquette(b, id + " · " + titre(p), "portée " + StaffScreen.num(p, "p") + " · " + lu(p), true);
            else if (!sousAncre) etiquette(b, id, null, false);
        }
        if ("editeur".equals(vue) && ancreBoiteOk && (editId == null || ancreChangee)) {
            MondePlaques.boite(ancreBoite, MondePlaques.OR_PLEIN);
            etiquette(ancreBoite, ancreLib, null, true);
        }
        pz.vider(ctx);
        // aperçu ancré
        List<Recit.Segment> segs = null;
        double[] boite = null;
        boolean bloc = true;
        net.minecraft.core.Direction faceA = null;
        int codeHalo = fr.reborn.hud.concentration.HaloPlaque.DEFAUT;
        if ("editeur".equals(vue) && apercuVisible) {
            segs = editeur.segments();
            long now = net.minecraft.util.Util.getMillis();
            if (segs != segsVus) { segsVus = segs; segsVusA = now; }
            if (apercu.pages() == 0 || segs.isEmpty() || now - segsVusA >= ATTENTE_FRAPPE_MS) segsPoses = segs;
            segs = segsPoses;
            if (ancreBoiteOk) { boite = ancreBoite; bloc = ancreBloc; faceA = faceSeule ? ancreFace : null; }
            codeHalo = halo.code();   // la couleur choisie, en direct
        } else if ("fiche".equals(vue) && apercuFiche) {
            JsonObject p = plaque(ficheId, false);
            if (p != null) {
                String cle = ficheId + ":" + StaffScreen.num(p, "rev");
                if (!cle.equals(segsFicheDe)) { segsFicheDe = cle; segsFiche = segments(StaffScreen.arr(p, "segs")); }
                segs = segsFiche;
                codeHalo = fr.reborn.hud.concentration.HaloPlaque.code(StaffScreen.str(p, "halo"));
                if (boite(p, PT_FICHE)) {
                    boite = PT_FICHE;
                    bloc = "bloc".equals(StaffScreen.str(p, "k"));
                    String f = StaffScreen.str(p, "f");
                    faceA = f.isEmpty() ? null : net.minecraft.core.Direction.byName(f);
                }
            }
        }
        apercu.poser(segs == null || segs.isEmpty() ? null : segs, Gabarit.STELE);
        if (apercu.pages() == 0) return;
        if (boite != null && Plaques.pointDevant(boite, bloc, faceA, PT) && apercu.dessinerAncre(ctx, PT[0], PT[1], PT[2], 1f, codeHalo)) return;
        // pas d'ancre (objet en main) ou point derrière la caméra : au centre, à gauche du volet
        var win = mc.getWindow();
        int libre = (s.px0() - StaffScreen.MARGE) * Math.max(1, win.getGuiScale());
        apercu.dessiner(ctx, Math.min(win.getWidth() / 2, libre / 2), 1f, 1.0, codeHalo);
    }

    private static final double[] PT_BOITE = new double[6], PT_FICHE = new double[6], PT_APERCU = new double[6];

    /** Banc : Maj tenue pour enfoncée. */
    static boolean debugMaj;

    /** Maj enfoncée (aperçu de la boîte de deux coins). */
    private static boolean majEnfoncee(Minecraft mc) {
        if (debugMaj) return true;
        long w = mc.getWindow().handle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    /** Numéro d'un coin de la boîte choisie, au-dessus de son bloc. */
    private void numeroCoin(net.minecraft.core.BlockPos b, String n) {
        if (!ProjectionMonde.versGui(b.getX() + 0.5, b.getY() + 1.12, b.getZ() + 0.5, V)) return;
        GardeArt.Pinceau pz = s.pz;
        float w = pz.largeur(n, 12, true);
        pz.fond(Math.round(V.x - w / 2f - 3), Math.round(V.y - 6), Math.round(V.x + w / 2f + 3), Math.round(V.y + 5), 0xC0141018);
        pz.cadre(V.x - w / 2f - 3, V.y - 6, w + 6, 11, OR_DOUX);
        pz.texte(n, V.x - w / 2f, V.y - 0.5f, 12, OR_CLAIR, true);
    }

    /* infobulle près du curseur : reconstruite seulement quand la cible change */
    private int bulleK = -1;
    private long bulleCle = Long.MIN_VALUE;
    private String bulleL1 = "", bulleL2 = "";

    /**
     * Infobulle du survol, près du curseur (planche c5) : nom et coordonnées, puis distance ; hors de portée, « Trop loin ·
     * 9,0 blocs / 8 au plus » en rouge. Les chaînes ne sont refaites qu'au changement de cible ou de dixième de bloc.
     */
    private void infobulle() {
        long cle = ((long) Math.floor(sel.viseBoite[0]) * 73856093L) ^ ((long) Math.floor(sel.viseBoite[1]) * 19349663L)
            ^ ((long) Math.floor(sel.viseBoite[2]) * 83492791L) ^ (Math.round(sel.viseDist * 10) << 40) ^ (sel.viseLoin ? 1L << 62 : 0);
        if (cle != bulleCle || sel.viseK != bulleK) {
            bulleCle = cle;
            bulleK = sel.viseK;
            bulleL1 = sel.viseNom + " · " + SelecteurPlaque.coin(sel.viseBoite);
            bulleL2 = sel.viseLoin
                ? "Trop loin · " + SelecteurPlaque.un(sel.viseDist) + " blocs / " + (int) SelecteurPlaque.PORTEE + " au plus"
                : (sel.viseK == 2 ? "entité · " : "bloc · ") + SelecteurPlaque.un(sel.viseDist) + " blocs";
        }
        GardeArt.Pinceau pz = s.pz;
        float w = Math.max(pz.largeur(bulleL1, 11, false), pz.largeur(bulleL2, 10, false)) + 10;
        float x = (float) s.mouseX + 12, y = (float) s.mouseY + 10;
        // jamais sous le bandeau du volet ni hors de l'écran
        if (x + w > s.px0() - 4 && s.mouseY <= s.py1() + 24) x = (float) s.mouseX - 12 - w;
        if (x + w > s.width - 4) x = s.width - 4 - w;
        if (y + 22 > s.height - 4) y = (float) s.mouseY - 30;
        pz.fond(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + 22), 0xD8141018);
        pz.cadre(x, y, w, 22, sel.viseLoin ? 0x90C0504A : FILET_2);
        pz.texte(bulleL1, x + 5, y + 6, 11, CREME, false);
        pz.texte(bulleL2, x + 5, y + 15, 10, sel.viseLoin ? ROUGE : GRIS_2, false);
    }

    private static boolean croise(double[] a, double[] b) {
        return a[0] < b[3] && b[0] < a[3] && a[1] < b[4] && b[1] < a[4] && a[2] < b[5] && b[2] < a[5];
    }

    /** Étiquette sans fond au-dessus de la boîte (contour et ombre du HUD) : titre, ligne grise ; GUI. */
    private void etiquette(double[] b, String l1, String l2, boolean or) {
        if (!ProjectionMonde.versGui((b[0] + b[3]) / 2, b[4] + 0.15, (b[2] + b[5]) / 2, V)) return;
        GardeArt.Pinceau pz = s.pz;
        int taille = or ? 12 : 10;
        float w = pz.largeur(l1, taille, or);
        float x = V.x - w / 2f, y = V.y - (l2 == null ? 6 : 12);
        pz.texte(l1, x + 0.5f, y + 0.5f, taille, 0xC0000000, or);
        pz.texte(l1, x, y, taille, or ? OR_CLAIR : 0xE0EEE4D2, or);
        if (l2 != null) {
            float w2 = pz.largeur(l2, 10, false);
            pz.texte(l2, V.x - w2 / 2f + 0.5f, y + 8.5f, 10, 0xC0000000, false);
            pz.texte(l2, V.x - w2 / 2f, y + 8, 10, OR, false);
        }
    }

    /* ================================================================ entrées (routées par l'écran) */

    boolean actif() { return "outils".equals(s.tab) && "plaques".equals(s.outil); }

    /** La zone de texte a la main. */
    boolean saisie() { return focus && actif() && "editeur".equals(vue) && !sel.actif && !s.formulaire.actif(); }

    void clic(double x, double y, boolean maj) {
        clicX = x;
        clicY = y;
        clicMaj = maj;
        if (!(x >= ex0 && x < ex1 && y >= ey0 && y < ey1)) focus = false;
    }

    /** Clic gauche dans le monde (hors du volet), sélecteur ouvert : choisit ce qui est survolé. */
    boolean clicMonde(double x, double y, boolean maj) {
        if (!actif() || !sel.actif || !horsVolet(x, y)) return false;
        Minecraft mc = Minecraft.getInstance();
        sel.survoler(mc, x, y);
        String err = sel.clic(mc, maj);
        if (err != null) { StaffClient.toast(err); RebornSounds.deny(); }
        else if (sel.viseK != 0) RebornSounds.uiClick();
        return true;
    }

    /** Clic droit maintenu dans le monde, sélecteur ouvert : la caméra tourne. */
    boolean tourner(double dx, double dy) {
        if (!actif() || !sel.actif) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double k = Math.max(1, mc.getWindow().getGuiScale()) * 2.5;
        if (!rotation) cacherCurseur(mc);
        mc.player.turn(dx * k, dy * k);
        return true;
    }

    /* rotation au clic droit : le curseur se cache, puis revient là où il était au relâcher */
    private boolean rotation;
    private final double[] curX = new double[1], curY = new double[1];

    private void cacherCurseur(Minecraft mc) {
        rotation = true;
        long w = mc.getWindow().handle();
        GLFW.glfwGetCursorPos(w, curX, curY);
        GLFW.glfwSetInputMode(w, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    private void montrerCurseur() {
        if (!rotation) return;
        rotation = false;
        long w = Minecraft.getInstance().getWindow().handle();
        GLFW.glfwSetInputMode(w, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(w, curX[0], curY[0]);
    }

    /** Échap : sélecteur → éditeur ; éditeur ou fiche → liste. Faux : la liste rend la main aux outils. */
    boolean echap() {
        if (!actif()) return false;
        if (sel.actif) { fermerSelecteur(); return true; }
        if ("editeur".equals(vue) && halo.echap()) return true;
        if (!"liste".equals(vue)) { vue = "liste"; focus = false; apercuFiche = false; s.scroll = 0; return true; }
        return false;
    }

    /** Entrée : sélecteur → valider ; Ctrl + Entrée dans l'éditeur → poser. */
    boolean entree(boolean ctrl) {
        if (!actif()) return false;
        if (sel.actif) { validerSelecteur(); return true; }
        if ("editeur".equals(vue) && ctrl) { poser(); return true; }
        return false;
    }

    boolean touche(int k, boolean maj, boolean ctrl) {
        if (!saisie()) return false;
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (ctrl) poser();
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
        if (actif() && "editeur".equals(vue) && halo.glisser(x, y)) return true;
        if (!glisse || !saisie()) return false;
        editeur.cliquer(x, y, true);
        return true;
    }

    void relacher() {
        glisse = false;
        halo.relacher();
        montrerCurseur();
    }

    /** Sélecteur ouvert : les touches de déplacement passent au personnage (le volet est replié). */
    boolean deplacement(KeyEvent e, boolean appui) {
        if (!actif() || !sel.actif) return false;
        var o = Minecraft.getInstance().options;
        // Maj ne passe pas au personnage : c'est le modificateur du second coin (s'accroupir abaisserait la caméra juste
        // avant le clic et changerait le bloc sous la souris)
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump}) {
            if (k.matches(e)) { k.setDown(appui); return true; }
        }
        return false;
    }

    private static void relacherTouches() {
        var o = Minecraft.getInstance().options;
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump}) k.setDown(false);
    }

    /* ================================================================ banc */

    void debugVue(String v, String id, boolean corbeille) {
        vue = v;
        ficheId = id;
        ficheCorbeille = corbeille;
    }

    void debugEditeur(String balise, String titre, int portee, JsonObject ancre, String lib, double[] boite, boolean bloc) {
        nouvelle();
        editeur.poser(Recit.segments(balise));
        titreBox.setValue(titre);
        this.portee = portee;
        if (ancre != null) {
            this.ancre = ancre;
            ancreLib = lib;
            ancreDetail = "choisie au sélecteur";
            System.arraycopy(boite, 0, ancreBoite, 0, 6);
            ancreBoiteOk = true;
            ancreBloc = bloc;
        }
        focus = false;
    }

    void debugApercuFiche(boolean on) { apercuFiche = on; }

    /** Banc : halo de l'éditeur (préréglage, ou « Personnalisée » de la couleur donnée, sélecteur ouvert ou non). */
    void debugHalo(int code, boolean perso, int couleur, boolean ouvert) { halo.debug(code, perso, couleur, ouvert); }

    GardeHalo debugHaloVolet() { return halo; }

    /** Banc : page suivante de l'aperçu (comme le clic sur « Page 1 / 2 › »). */
    void debugPageSuivante() { apercu.pageSuivante(); }

    /** Banc : « page / pages » de l'aperçu, et l'ancre lisible d'un seul côté. */
    String debugApercu() {
        return "aperçu page " + (apercu.page() + 1) + " / " + apercu.pages() + ", face " + (ancreFace == null ? "aucune" : ancreFace.getName())
            + (faceSeule ? " seule" : " tous côtés");
    }
}
