package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.anchor.ProjectionMonde;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.mudra.MudraClient;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Outil <b>« Portes »</b> du Poste de garde (F7 › Outils › Portes scellées ; 16/11, maquettes {@code f7_*}) : les dalles
 * scellées par une suite de mudras, jusque-là gérées par {@code /porte}. Protocole {@code portes}, {@code porte_verifier},
 * {@code porte_creer}, {@code porte_signes}, {@code porte_nom}, {@code porte_leve}, {@code porte_ouvrir},
 * {@code porte_fermer}, {@code porte_retirer}, {@code porte_tp}, {@code porte_systeme}, {@code porte_debloquer} sur
 * {@code reborn:staff} ; réponses {@code toast} puis {@code portes} ({@code dernier} = l'id touché), et {@code porte_verif}.
 * Le serveur relit le grade et la dalle à chaque message : le volet ne fait que proposer ce que les droits permettent.
 *
 * <ul>
 *   <li><b>Liste</b> : système allumé ou éteint (Admin, confirmation avant d'éteindre), recherche sur le nom ou l'id,
 *       filtres Proches · Toutes, état de chaque porte, redemandée toutes les 2 s ; dans le monde, les portes à moins de
 *       64 blocs sont cernées, pour le staff seul ({@link MondePlaques}).</li>
 *   <li><b>Sélecteur de dalle</b> ({@link SelecteurPlaque}, blocs seulement) : le volet se replie, clic = premier coin,
 *       Maj + clic = second coin ; limites vérifiées ici (9 par côté, 3 d'épaisseur, 162 blocs) puis par le serveur
 *       ({@code porte_verifier} numéroté, une demande toutes les 250 ms au plus, réponse périmée ignorée).</li>
 *   <li><b>Nouvelle porte</b> : dalle, nom (staff seulement), suite composée sur la <b>grille 3A</b> des douze signes
 *       (dessins de la barre de mudras, rang en pastille d'or), levée bornée à la hauteur de la dalle.</li>
 *   <li><b>Fiche</b> : état, dalle, suite, levée, auteurs, joueurs bloqués (essais en série) et « Lever » ; ouvrir pour
 *       essai / fermer, changer la suite, renommer, changer la levée, aller devant, retirer le sceau (confirmation).</li>
 * </ul>
 * Droits : Helper voit et se téléporte ; Modérateur gère ; Admin allume et éteint le système. <b>La suite n'est montrée
 * qu'à un modérateur</b> (le serveur ne l'envoie pas à un Helper, et le volet ne l'affiche jamais sans
 * {@code peutGerer}) ; elle ne part ni dans un toast, ni dans le presse-papiers, ni dans un journal.
 */
final class GardePortes {

    /* limites du serveur (MudraRegles), revérifiées là-bas */
    static final int SUITE_MIN = 2, SUITE_MAX = 12, NOM_MAX = 32, COTE_MAX = 9, EPAISSEUR_MAX = 3, VOLUME_MAX = 162;
    /** Rayon des « proches » demandé au serveur, seuil de l'intertitre « Plus loin ». */
    private static final int RAYON = 64, PROCHES = 24;
    /** Liste redemandée toutes les 2 s, outil ouvert. */
    private static final int RAFRAICHIR_TICKS = 40;
    /** Écart minimal entre deux {@code porte_verifier}. */
    static final long VERIF_ECART_MS = 250;
    /** Envoi en attente de sa confirmation : un second envoi est ignoré pendant ce délai. */
    private static final long ATTENTE_MS = 3000;
    /** Repères posés au plus dans le monde ({@link MondePlaques} garde 48 boîtes). */
    private static final int REPERES_MAX = 44;
    /** État de la dalle du sélecteur. */
    static final int RIEN = 0, REFUS_LOCAL = 1, ATTENTE = 2, VALABLE = 3, REFUS_SERVEUR = 4;

    private static final String[] NOMS = MudraClient.NOMS;
    /** Dessins des douze signes (atlas de la barre de mudras : 48 px par main, ordre de la barre). */
    private static final Identifier MAINS = DaKit.tex("mudras", "mains_petit");
    private static final int MAIN = 48;
    private static final String SEP_SUITE = "  ›  ", SEP_DEBUT = "›  ";
    private static final String MODO = "Réservé aux modérateurs.", ILLISIBLE = "mudras.yml illisible : aucune modification possible.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final Vector3f V = new Vector3f();
    private static final double[] B = new double[6], B2 = new double[6];

    /** Horloge (ms) de l'écart entre deux vérifications ; le banc la fige. */
    static LongSupplier horloge = System::currentTimeMillis;
    /** Numéro de la dernière demande {@code porte_verifier} (ne repart jamais de zéro : une vieille réponse ne passe pas). */
    private static int verifN;

    private final StaffScreen s;
    final SelecteurPlaque sel = new SelecteurPlaque();
    private EditBox rech, nomBox;
    private int ticks;

    /** Vue : « liste », « fiche », « nouvelle », ou un sous-éditeur de la fiche (« suite », « nom », « leve »). */
    String vue = "liste";
    String filtre = "proches";
    String ficheId;
    /** Rangée survolée de la liste (cernée plus fort dans le monde), relue à chaque image. */
    private String survolId;

    /* nouvelle porte et sous-éditeurs */
    /** Dalle validée au sélecteur : x, y, z du coin bas puis dx, dy, dz. */
    final int[] dalle = new int[6];
    boolean dalleOk;
    private String dalleLib = "", dalleDetail = "";
    /** Suite en cours (ordinaux 0 à 11), jamais écrite ailleurs que dans le message {@code porte_creer / porte_signes}. */
    final List<Integer> suite = new ArrayList<>();
    int leve;
    private final float[] suiteX = new float[SUITE_MAX];
    private final int[] suiteL = new int[SUITE_MAX];

    /* vérification de la dalle du sélecteur */
    int verifEtat;
    String verifRaison = "";
    private int verifAttendue = -1;
    private boolean verifDue;
    private long verifEnvoyeeA = Long.MIN_VALUE / 4;
    /** Yeux du staff à la dernière demande {@code porte_verifier} : s'il se déplace, le serveur rejugerait la distance. */
    private double verifX, verifY, verifZ;
    private String selDims = "", selLigne = "", selCoin1 = "", selCoin2 = "", selLevee = "";
    private final List<String> selRaison = new ArrayList<>();
    private String[] touchesDepl = new String[0];
    /** Faces cliquées des deux coins : le numéro de chaque coin se pose dessus. */
    private Direction face1, face2;
    /* « Visé » du bandeau : refait au changement de cible */
    private long viseCle = Long.MIN_VALUE;
    private String viseLib = "";

    /* envoi en attente de sa confirmation (« dernier » de la liste) */
    private long envoiA;
    private String attendu;

    /* fiche : textes refaits à l'arrivée d'une liste */
    private long ficheA = -1;
    private String ficheDe;
    private String fSous = "", fDalle = "", fLevee = "", fPosee = "", fModif = "";
    private final List<String> fSuite = new ArrayList<>();

    GardePortes(StaffScreen s) {
        this.s = s;
        sel.blocsSeuls = true;
    }

    void init() {
        rech = s.box(rech, "Nom ou id (D3)", 40);
        nomBox = s.box(nomBox, "Archives du clan", NOM_MAX);
    }

    /** Ouverture de l'outil : liste demandée. */
    void ouvrir() {
        attendu = null;
        vue = "liste";
        sel.fermer();
        demander();
        ticks = 0;
    }

    private void demander() {
        JsonObject o = base("portes", null);
        o.addProperty("r", RAYON);
        StaffClient.send(o);
    }

    void tick() {
        if (sel.actif) {
            if (horsVolet(s.mouseX, s.mouseY)) sel.survoler(Minecraft.getInstance(), s.mouseX, s.mouseY);
            else sel.viseK = 0;
            // le serveur juge la distance depuis les yeux (8 + 1 blocs) : après un demi-bloc de déplacement, une dalle
            // déjà jugée (valable, ou refusée « trop loin ») est redemandée
            var joueur = Minecraft.getInstance().player;
            if (joueur != null && sel.choisi() && (verifEtat == VALABLE || verifEtat == REFUS_SERVEUR) && !verifDue
                    && joueur.getEyePosition().distanceToSqr(verifX, verifY, verifZ) > 0.25) {
                verifEtat = ATTENTE;
                verifRaison = "";
                selRaison.clear();
                verifDue = true;
            }
            verifier();
        }
        if (++ticks % RAFRAICHIR_TICKS == 0) demander();
        // création ou retouche confirmée par le serveur (« dernier » = l'id) : retour à la fiche
        // « dernier » est gardé à part par StaffClient : la liste du rafraîchissement, arrivée dans le même tick, ne l'efface pas
        if (attendu == null || donnees() == null || StaffClient.portesDernierA() < envoiA) return;
        String dernier = StaffClient.portesDernier();
        if (dernier.isEmpty() || !"creer".equals(attendu) && !dernier.equals(attendu)) return;
        if ("creer".equals(attendu)) effacer();
        attendu = null;
        ficheId = dernier;
        vue = "fiche";
        s.setFocused(null);
        s.scroll = 0;
    }

    /** Outil quitté, volet fermé : sélecteur fermé, touches de déplacement relâchées, curseur rendu. */
    void liberer() {
        if (sel.actif) GardePlaques.relacherTouches();
        sel.fermer();
        montrerCurseur();
        survolId = null;
    }

    /* ================================================================ données */

    private static JsonObject donnees() { return StaffClient.portes(); }

    private static boolean drapeau(String k) { return StaffScreen.bool(donnees(), k); }

    /** Le staff peut modifier : modérateur, et {@code mudras.yml} lisible. */
    private static boolean gerer() { return drapeau("peutGerer") && !drapeau("bloque"); }

    JsonObject porte(String id) {
        if (id == null) return null;
        for (JsonElement e : StaffScreen.arr(donnees(), "l"))
            if (id.equals(StaffScreen.str(e.getAsJsonObject(), "id"))) return e.getAsJsonObject();
        return null;
    }

    private static double dbl(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsDouble() : 0; } catch (RuntimeException e) { return 0; }
    }

    /** Boîte d'une porte de la liste (coin bas + taille). */
    private static void boite(JsonObject p, double[] out) {
        int x = StaffScreen.num(p, "x"), y = StaffScreen.num(p, "y"), z = StaffScreen.num(p, "z");
        out[0] = x; out[1] = y; out[2] = z;
        out[3] = x + Math.max(1, StaffScreen.num(p, "dx"));
        out[4] = y + Math.max(1, StaffScreen.num(p, "dy"));
        out[5] = z + Math.max(1, StaffScreen.num(p, "dz"));
    }

    private static String dims(int dx, int dy, int dz) { return dx + " × " + dy + " × " + dz; }

    private static String dims(JsonObject p) {
        return dims(StaffScreen.num(p, "dx"), StaffScreen.num(p, "dy"), StaffScreen.num(p, "dz"));
    }

    private static String blocs(int n) { return n + (n > 1 ? " blocs" : " bloc"); }

    private static String nom(JsonObject p) {
        String n = StaffScreen.str(p, "nom");
        return n.isBlank() ? StaffScreen.str(p, "id") : n;
    }

    /** « à 6 blocs », ou le nom du monde quand la porte est ailleurs. */
    private static String distance(JsonObject p) {
        double d = dbl(p, "dist");
        return d < 0 ? StaffScreen.str(p, "monde") : "à " + blocs((int) Math.round(d));
    }

    /** Nombre de signes : {@code n}, seul champ qu'un Helper reçoit. */
    private static int nombreSignes(JsonObject p) {
        int n = StaffScreen.num(p, "n");
        return n > 0 ? n : StaffScreen.arr(p, "signes").size();
    }

    private static String signes(int n) { return n + (n > 1 ? " signes" : " signe"); }

    /** Ordinaux de la suite d'une porte (vide pour un Helper : le serveur ne l'envoie pas). */
    private static List<Integer> ordinaux(JsonObject p) {
        List<Integer> l = new ArrayList<>();
        for (JsonElement e : StaffScreen.arr(p, "signes")) {
            try {
                int i = e.getAsInt();
                if (i >= 0 && i < NOMS.length) l.add(i);
            } catch (RuntimeException ignored) { }
        }
        return l;
    }

    private static String etatLib(String e) {
        return switch (e) {
            case "leve" -> "Se lève";
            case "levee" -> "Levée";
            case "redescend" -> "Redescend";
            case "remettre" -> "À remettre";
            default -> "Fermée";
        };
    }

    private static int etatCouleur(String e, int repos) {
        return switch (e) {
            case "leve", "levee", "redescend" -> TERRE;
            case "remettre" -> ROUGE;
            default -> repos;
        };
    }

    /** Secondes restantes d'un {@code reste} de la liste, décomptées depuis son arrivée (−1 : inconnu). */
    private static int reste(int r) {
        if (r < 0) return -1;
        return (int) Math.max(0, r - (System.currentTimeMillis() - StaffClient.portesA()) / 1000);
    }

    /** « 45 s », « 2 min », « 1 min 20 ». */
    static String duree(int sec) {
        if (sec < 60) return sec + " s";
        int r = sec % 60;
        return sec / 60 + " min" + (r == 0 ? "" : r < 10 ? " 0" + r : " " + r);
    }

    /**
     * Ligne « Suite » de la fiche, en lignes de {@code larg} au plus : les noms des signes pour un modérateur, sinon le
     * nombre seul, même si un serveur fautif avait envoyé la suite à un Helper.
     */
    List<String> suiteAffichee(JsonObject p, boolean gerer, float larg) {
        List<String> out = new ArrayList<>();
        List<Integer> l = gerer ? ordinaux(p) : List.of();
        if (l.isEmpty()) {
            out.add(signes(nombreSignes(p)) + (gerer ? "" : "  ·  réservée aux modérateurs"));
            return out;
        }
        StringBuilder ligne = new StringBuilder();
        for (int i : l) {
            String suivant = ligne.isEmpty() ? NOMS[i] : ligne + SEP_SUITE + NOMS[i];
            if (!ligne.isEmpty() && s.pz.largeur(suivant, 12, false) > larg) {
                out.add(ligne.toString());
                ligne = new StringBuilder(SEP_DEBUT).append(NOMS[i]);
            } else ligne = new StringBuilder(suivant);
        }
        out.add(ligne.toString());
        return out;
    }

    /** Compteur et aperçu de la ligne de l'outil : portes à moins de 64 blocs. */
    Object[] resume() {
        int n = 0;
        for (JsonElement e : StaffScreen.arr(donnees(), "l")) {
            double d = dbl(e.getAsJsonObject(), "dist");
            if (d >= 0 && d <= RAYON) n++;
        }
        return new Object[]{n, n > 0 ? n + " autour de toi · dalles scellées par des signes" : "Dalles scellées par une suite de signes"};
    }

    /* ================================================================ fil (zone fixe) */

    private boolean sousEditeur() { return "suite".equals(vue) || "nom".equals(vue) || "leve".equals(vue); }

    /** « < Outils · Portes » ; « < Portes · D3 » ; « < Portes · Nouvelle · Dalle » (sélecteur) ; « < D3 · Suite ». */
    void fil(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        String retour = sel.actif ? "Portes" : "liste".equals(vue) ? "Outils" : sousEditeur() && ficheId != null ? ficheId : "Portes";
        int rw = Math.round(pz.largeur(retour, 13, false)) + 12;
        boolean hov = s.hover(ix - 3, y - 2, rw, 12);
        pz.icone("gauche", ix - 2, y + 0.5f, 8, hov ? CREME : GRIS);
        float w = pz.texte(retour, ix + 7, y + 4.5f, 13, hov ? CREME : GRIS, false);
        s.hits.add(new StaffScreen.Hit(ix - 3, y - 2, rw, 12, this::retour, null, null));
        float x = ix + 11 + w;
        String m1, m2 = null;
        if (sel.actif) { m1 = "Nouvelle"; m2 = "Dalle"; }
        else m1 = switch (vue) {
            case "fiche" -> ficheId == null ? "Fiche" : ficheId;
            case "nouvelle" -> "Nouvelle";
            case "suite" -> "Suite";
            case "nom" -> "Nom";
            case "leve" -> "Levée";
            default -> "Portes";
        };
        pz.texte("·", x, y + 4.5f, 13, GRIS_2, false);
        x += 6;
        x += pz.texte(m1, x, y + 4.5f, 13, m2 == null ? CREME : GRIS, m2 == null) + 5;
        if (m2 != null) {
            pz.texte("·", x, y + 4.5f, 13, GRIS_2, false);
            pz.texte(m2, x + 6, y + 4.5f, 13, CREME, true);
            pz.texteD("sélection", ix + iw, y + 4.5f, 11, OR_CLAIR, false);
        }
        pz.filetH(ix, ix + iw, y + 15.5f, FILET);
    }

    private void retour() {
        RebornSounds.uiClick();
        s.formulaire.fermer();
        if (reculer()) return;
        s.outil = null;
        s.scroll = 0;
    }

    /**
     * Un cran en arrière : sélecteur → nouvelle porte (ou la liste s'il n'y a pas encore de dalle) ; sous-éditeur → fiche ;
     * fiche ou nouvelle porte → liste. Faux : la liste rend la main aux outils.
     */
    private boolean reculer() {
        if (sel.actif) {
            fermerSelecteur();
            if (!dalleOk) versListe();
            return true;
        }
        if (sousEditeur()) { versFiche(); return true; }
        if (!"liste".equals(vue)) { versListe(); return true; }
        return false;
    }

    private void versListe() {
        // vue quittée : une confirmation encore attendue (refus du serveur, réponse perdue) est soldée, sinon le premier
        // « dernier » venu (retrait, essai, déblocage) ferait sauter le volet
        attendu = null;
        vue = "liste";
        s.setFocused(null);
        s.scroll = 0;
    }

    private void versFiche() {
        attendu = null;
        vue = "fiche";
        s.setFocused(null);
        s.scroll = 0;
    }

    String pied() {
        if (sel.actif) return null;
        return switch (vue) {
            case "nouvelle" -> "Ctrl + Entrée : sceller  ·  Échap : retour à la liste";
            case "fiche" -> "Échap : retour à la liste";
            case "suite", "leve" -> "Entrée : enregistrer  ·  Échap : retour à la fiche";
            case "nom" -> "Entrée : renommer  ·  Échap : retour à la fiche";
            default -> "Clic : fiche  ·  F7 : fermer";
        };
    }

    boolean pleineHauteur() { return !sel.actif; }

    /* ================================================================ corps */

    int corps(int y) {
        if (sel.actif) return bandeau(y + 4);
        return switch (vue) {
            case "fiche" -> fiche(y + 4);
            case "nouvelle" -> nouvelleVue(y + 2);
            case "suite" -> suiteVue(y + 4);
            case "nom" -> nomVue(y + 4);
            case "leve" -> leveVue(y + 4);
            default -> liste(y + 2);
        };
    }

    /* ---------------------------------------------------------------- liste */

    private int liste(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        JsonObject d = donnees();
        String survolAvant = survolId;
        survolId = null;
        if (d == null) return s.vide(y, "Lecture des portes…", "Le serveur ne répond pas encore (ShinobiCore à jour ?).");
        if (StaffScreen.bool(d, "bloque")) {
            pz.texte("mudras.yml illisible : aucune modification possible", ix, y + 3, 12, ROUGE, false);
            y += 11;
        }
        for (String l : s.wrap("Dalles scellées par une suite de signes. Outil ouvert : les portes proches sont cernées dans le monde, pour toi seul.", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 4;
        ligneSysteme(d, y);
        y += 15;
        s.champ(rech, ix, y, iw, "chercher");
        y += 23;
        String q = rech.getValue().trim().toLowerCase(Locale.ROOT);
        JsonArray l = StaffScreen.arr(d, "l");
        int rayon = Math.max(PROCHES + 1, StaffScreen.num(d, "r") > 0 ? StaffScreen.num(d, "r") : RAYON);
        int nProches = 0;
        for (JsonElement e : l) {
            double di = dbl(e.getAsJsonObject(), "dist");
            if (di >= 0 && di <= PROCHES) nProches++;
        }
        boolean proches = "proches".equals(filtre);
        float ox = ix;
        ox += s.mot("Proches " + nProches, ox, y + 4, 13, proches, () -> { filtre = "proches"; s.scroll = 0; }) + 10;
        s.mot("Toutes " + l.size(), ox, y + 4, 13, !proches, () -> { filtre = "toutes"; s.scroll = 0; });
        y += 15;
        int n = 0;
        boolean loin = false;
        for (JsonElement e : l) {
            JsonObject p = e.getAsJsonObject();
            double di = dbl(p, "dist");
            if (proches && (di < 0 || di > rayon)) continue;
            if (!q.isEmpty() && !(StaffScreen.str(p, "id") + " " + StaffScreen.str(p, "nom")).toLowerCase(Locale.ROOT).contains(q)) continue;
            if (proches && !loin && di > PROCHES) {
                loin = true;
                pz.capitales("Plus loin · " + PROCHES + " à " + rayon + " blocs", ix, y + 6, 11, GRIS_2);
                y += 14;
            }
            y = ligne(p, y, survolAvant);
            n++;
        }
        if (n == 0) y = s.vide(y, !q.isEmpty() ? "Aucune porte ne correspond à « " + q + " »." : proches ? "Aucune porte scellée à " + rayon + " blocs." : "Aucune porte scellée.",
            !q.isEmpty() ? null : l.isEmpty() ? "« Nouvelle porte » : deux coins dans le monde, un nom, une suite de signes." : "« Toutes » montre aussi les portes lointaines.");
        y += 3;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 6;
        String lab = "Nouvelle porte";
        int bw = s.largeurBouton(lab);
        s.refusVerrou = StaffScreen.bool(d, "bloque") ? ILLISIBLE : MODO;
        s.bouton(ix + iw - bw, y, bw, lab, true, this::nouvelle, !gerer());
        s.refusVerrou = null;
        int yy = y;
        for (String li : s.wrap("Deux coins à choisir dans le monde, à 8 blocs au plus.", iw - bw - 8, 11)) {
            pz.texte(li, ix, yy + 4.5f, 11, GRIS_2, false);
            yy += 8;
        }
        return y + 22;
    }

    /** « Système  Allumé · Éteint » : cliquable pour un admin, sinon éteint avec la raison au clic. */
    private void ligneSysteme(JsonObject d, int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        boolean actif = StaffScreen.bool(d, "actif"), admin = StaffScreen.bool(d, "peutSysteme");
        pz.texte("Système", ix, y + 4, 13, GRIS, false);
        float ox = ix + 46;
        for (int i = 0; i < 2; i++) {
            boolean on = i == 0;
            String lab = on ? "Allumé" : "Éteint";
            if (admin) ox += s.mot(lab, ox, y + 4, 13, actif == on, () -> systeme(on)) + 10;
            else {
                // pas admin : l'état se lit, le mot ne répond pas
                float w = pz.texte(lab, ox, y + 4, 13, actif == on ? CREME : GRIS_3, actif == on);
                if (actif == on) pz.filetH(ox, ox + w, y + 9.5f, OR);
                if (s.cliquable(y - 2, 12)) s.hits.add(new StaffScreen.Hit(Math.round(ox) - 1, y - 2, Math.round(w) + 2, 12, null, null, "Réservé aux admins."));
                ox += w + 10;
            }
        }
        pz.texteD("admin", ix + iw, y + 4, 11, GRIS_2, false);
    }

    private int ligne(JsonObject p, int y, String survolAvant) {
        int h = 26;
        if (!s.cliquable(y, h)) return y + h;   // hors de la partie visible : rien à dessiner
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        String id = StaffScreen.str(p, "id");
        boolean hov = s.hover(s.px0() + 1, y, s.pw() - 2, h);
        if (hov) survolId = id;
        // la rangée survolée, sinon celle de la dernière fiche ouverte (comme le repère du monde)
        boolean choisie = hov || id.equals(ficheId) && survolAvant == null;
        if (choisie) {
            pz.fond(ix - 3, y, ix + iw + 3, y + h - 1, NUIT_3);
            pz.fond(ix - 3, y, ix - 2, y + h - 1, OR);
        }
        pz.icone("porte", ix + 1, y + 4.5f, 8, choisie ? OR_CLAIR : GRIS);
        String etat = StaffScreen.str(p, "etat");
        int col = etatCouleur(etat, GRIS);
        float ew = pz.texteD(etatLib(etat), ix + iw, y + 8, 11, col, false);
        pz.pastille(ix + iw - ew - 3.5f, y + 8, 1.25f, col);
        s.txt(nom(p), ix + 13, y + 8, iw - 13 - ew - 10, 14, CREME, false);
        int r = "levee".equals(etat) ? reste(StaffScreen.num(p, "reste")) : -1;
        int nb = StaffScreen.arr(p, "blocages").size();
        String droite = r >= 0 ? "redescend dans " + duree(r)
            : nb > 0 ? nb + (nb > 1 ? " joueurs bloqués" : " joueur bloqué") : "levée " + StaffScreen.num(p, "leve");
        float dw = pz.texteD(droite, ix + iw, y + 17.5f, 11, GRIS_2, false);
        s.txt(id + " · dalle " + dims(p) + " · " + distance(p) + " · " + signes(nombreSignes(p)), ix + 13, y + 17.5f, iw - 13 - dw - 6, 11, GRIS, false);
        s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, () -> {
            ficheId = id;
            versFiche();
            RebornSounds.uiClick();
        }, null, null));
        return y + h;
    }

    /**
     * Allume ou éteint le système (Admin) ; éteindre demande une confirmation dans le volet : les portes levées se
     * referment d'un coup.
     */
    void systeme(boolean on) {
        JsonObject d = donnees();
        if (!StaffScreen.bool(d, "peutSysteme")) { StaffClient.toast("Réservé aux admins."); RebornSounds.deny(); return; }
        if (StaffScreen.bool(d, "actif") == on) return;
        if (on) { envoyerSysteme(true); return; }
        s.formulaire.ouvrir("porte", "Éteindre les portes scellées ?",
            "Les portes levées se referment d'un coup et plus aucune ne répond aux signes, pour tout le serveur.", "Éteindre", true, null,
            List.of(), List.of("« Allumé » les remet en service. Inscrit au journal Staff."), v -> envoyerSysteme(false));
    }

    private void envoyerSysteme(boolean on) {
        JsonObject o = base("porte_systeme", null);
        o.addProperty("on", on);
        StaffClient.send(o);
        if (on) RebornSounds.confirm();
    }

    /* ---------------------------------------------------------------- fiche */

    private int fiche(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        JsonObject p = porte(ficheId);
        if (p == null) return s.vide(y, "Porte introuvable.", "Son sceau a pu être retiré par un autre membre du staff.");
        JsonObject d = donnees();
        boolean modo = StaffScreen.bool(d, "peutGerer"), bloque = StaffScreen.bool(d, "bloque"), actif = StaffScreen.bool(d, "actif");
        String id = ficheId, etat = StaffScreen.str(p, "etat");
        boolean fermee = "fermee".equals(etat);
        preparerFiche(p, modo);
        pz.icone("porte", ix, y + 1, 8, OR_CLAIR);
        s.txt(nom(p), ix + 13, y + 5, iw - 13, 15, CREME, true);
        s.txt(fSous, ix + 13, y + 16, iw - 13, 11, GRIS_2, false);
        y += 25;
        int r = "levee".equals(etat) ? reste(StaffScreen.num(p, "reste")) : -1;
        String e = "remettre".equals(etat) ? "Dalle à remettre (monde absent)" : etatLib(etat);
        if (r >= 0) e += "  ·  redescend dans " + duree(r);
        if (!actif) e += "  ·  système éteint";
        y = info("État", e, etatCouleur(etat, CREME), y);
        y = info("Dalle", fDalle, CREME, y);
        boolean suiteLue = modo && !StaffScreen.arr(p, "signes").isEmpty();
        for (int i = 0; i < fSuite.size(); i++) y = info(i == 0 ? "Suite" : "", fSuite.get(i), suiteLue ? OR_CLAIR : GRIS, y);
        y = info("Levée", fLevee, CREME, y);
        y = info("Posée", fPosee, CREME, y);
        if (!fModif.isEmpty()) y = info("Modifiée", fModif, CREME, y);
        JsonArray bl = StaffScreen.arr(p, "blocages");
        for (int i = 0; i < bl.size(); i++) {
            JsonObject b = bl.get(i).getAsJsonObject();
            String u = StaffScreen.str(b, "u");
            float lw = 0;
            if (modo) {
                lw = pz.largeur("Lever", 12, false);
                s.motCouleur("Lever", ix + iw - lw, y + 4, 12, OR_CLAIR, () -> debloquer(id, u));
                lw += 6;
            }
            pz.texte(i == 0 ? "Blocages" : "", ix, y + 4, 12, GRIS, false);
            s.txt(StaffScreen.str(b, "nom") + "  ·  encore " + duree(Math.max(0, reste(StaffScreen.num(b, "reste")))), ix + 46, y + 4, iw - 46 - lw, 12, TERRE, false);
            y += 10;
        }
        if (!bl.isEmpty()) {
            y += 2;
            y = note("Bloqué : cinq essais manqués devant cette porte en une minute. Elle ne lui répond plus, sans rien lui dire.", GRIS_2, y);
            y += 8;
        } else y += 6;
        pz.filetH(ix, ix + iw, y, FILET);
        y += 4;
        // actions
        String sansDroit = !modo ? MODO : bloque ? ILLISIBLE : null;
        if (fermee) y = action("Ouvrir pour essai", "porte", sansDroit != null ? sansDroit : actif ? null : "Système éteint : les portes restent fermées.",
            !modo, () -> envoyer("porte_ouvrir", id), y, OR_CLAIR);
        else {
            // le serveur refuse toujours porte_fermer en plein mouvement (PorteService.essaiFermer)
            boolean bouge = "leve".equals(etat) || "redescend".equals(etat);
            y = action("remettre".equals(etat) ? "Remettre la dalle" : "Fermer", "porte",
                sansDroit != null ? sansDroit : bouge ? "Attends la fin du mouvement." : null, !modo, () -> envoyer("porte_fermer", id), y, OR_CLAIR);
        }
        String ouverte = sansDroit != null ? sansDroit : fermee ? null : "La porte doit d'abord être fermée.";
        y = action("Changer la suite", "noter", sansDroit, !modo, () -> ouvrirSuite(p), y, CREME);
        y = action("Renommer", "noter", sansDroit, !modo, () -> ouvrirNom(p), y, CREME);
        y = action("Changer la levée", "haut", ouverte, !modo, () -> ouvrirLeve(p), y, CREME);
        y = action("Aller devant", "aller", null, false, () -> envoyer("porte_tp", id), y, CREME);
        y = action("Copier l'id", "copier", null, false, this::copierId, y, CREME);
        y = action("Retirer le sceau…", "suppr", ouverte, !modo, () -> retirer(id), y, ROUGE);
        y += 5;
        y = note("Retirer le sceau laisse les blocs en place. Il n'y a pas de corbeille : la porte et sa suite sont perdues.", GRIS_2, y);
        return y + 4;
    }

    /** Textes fixes de la fiche, refaits à l'arrivée d'une liste (pas à chaque image). */
    private void preparerFiche(JsonObject p, boolean modo) {
        if (StaffClient.portesA() == ficheA && ficheId.equals(ficheDe)) return;
        ficheA = StaffClient.portesA();
        ficheDe = ficheId;
        fSous = StaffScreen.str(p, "id") + "  ·  dalle " + dims(p) + "  ·  " + StaffScreen.str(p, "monde");
        fDalle = StaffScreen.num(p, "x") + " " + StaffScreen.num(p, "y") + " " + StaffScreen.num(p, "z") + "  ·  " + distance(p);
        int l = StaffScreen.num(p, "leve"), dy = StaffScreen.num(p, "dy");
        fLevee = blocs(l) + (l >= dy ? "  ·  toute la hauteur de la dalle" : "  ·  la dalle fait " + dy + " de haut");
        fPosee = auteur(StaffScreen.obj(p, "cree"));
        fModif = auteur(StaffScreen.obj(p, "modif"));
        fSuite.clear();
        fSuite.addAll(suiteAffichee(p, modo, s.iw() - 46));
    }

    /** « par Kaede le 15/11 18:40 », vide sans auteur. */
    private static String auteur(JsonObject o) {
        String par = StaffScreen.str(o, "par");
        long le = StaffScreen.lng(o, "le");
        if (par.isEmpty() && le <= 0) return "";
        return (par.isEmpty() ? "" : "par " + par + " ") + (le > 0 ? "le " + DATE.format(Instant.ofEpochMilli(le).atZone(ZoneId.systemDefault())) : "");
    }

    private int info(String label, String val, int col, int y) {
        s.pz.texte(label, s.ix(), y + 4, 12, GRIS, false);
        s.txt(val, s.ix() + 46, y + 4, s.iw() - 46, 12, col, false);
        return y + 10;
    }

    private int note(String t, int col, int y) {
        for (String l : s.wrap(t, s.iw(), 11)) {
            s.pz.texte(l, s.ix(), y + 4, 11, col, false);
            y += 8;
        }
        return y + 3;
    }

    /**
     * Ligne d'action (icône, libellé). {@code refus} non nul : éteinte, la raison au clic ; {@code grade} : la mention
     * « modérateur » à droite (action hors de portée du grade).
     */
    private int action(String lab, String ic, String refus, boolean grade, Runnable r, int y, int col) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw(), h = 16;
        boolean ok = refus == null;
        boolean hov = ok && s.hover(s.px0() + 1, y, s.pw() - 2, h) && s.cliquable(y, h);
        if (hov) pz.fond(s.px0() + 1, y, s.px1() - 1, y + h, NUIT_2);
        pz.icone(ic, ix, y + 4.5f, 7, !ok ? GRIS_3 : col != CREME ? col : hov ? OR : GRIS);
        pz.texte(lab, ix + 13, y + 8, 13, !ok ? GRIS_3 : hov && col == CREME ? CREME : col, false);
        if (!ok && grade) pz.texteD("modérateur", ix + iw, y + 8, 11, GRIS_3, false);
        if (s.cliquable(y, h)) s.hits.add(new StaffScreen.Hit(s.px0() + 1, y, s.pw() - 2, h, ok ? () -> { r.run(); RebornSounds.uiClick(); } : null, null, refus));
        return y + h;
    }

    private void copierId() {
        Minecraft.getInstance().keyboardHandler.setClipboard(ficheId);
        StaffClient.toast(ficheId + " · id copié");
    }

    /** Lève le blocage « essais en série » de ce joueur devant cette porte. */
    void debloquer(String id, String joueur) {
        // lever un blocage ne touche pas à mudras.yml : permis même fichier illisible (le serveur revérifie le grade)
        if (!drapeau("peutGerer")) return;
        JsonObject o = base("porte_debloquer", id);
        o.addProperty("joueur", joueur);
        StaffClient.send(o);
        RebornSounds.confirm();
    }

    /** Confirmation dans le volet : pas de corbeille, la porte et sa suite sont perdues. */
    void retirer(String id) {
        if (!gerer()) return;
        s.formulaire.ouvrir("suppr", "Retirer le sceau de " + id + " ?", "Les blocs restent, la porte et sa suite sont perdues.", "Retirer", true, null,
            List.of(), List.of("Définitif : il n'y a pas de corbeille. Inscrit au journal Staff."), v -> {
                StaffClient.send(base("porte_retirer", id));
                ficheId = null;
                versListe();
            });
    }

    private static JsonObject base(String a, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", a);
        if (id != null) o.addProperty("id", id);
        return o;
    }

    /** Action simple sur une porte ({@code porte_ouvrir}, {@code porte_fermer}, {@code porte_tp}). */
    void envoyer(String a, String id) {
        StaffClient.send(base(a, id));
        RebornSounds.confirm();
    }

    /* ---------------------------------------------------------------- nouvelle porte */

    /** Bouton « Nouvelle porte » : brouillon remis à zéro, sélecteur de dalle ouvert. */
    void nouvelle() {
        if (!gerer()) return;
        effacer();
        vue = "nouvelle";
        ouvrirSelecteur();
        RebornSounds.uiClick();
    }

    /** Tout remettre à zéro : dalle, nom, suite, levée. */
    void effacer() {
        dalleOk = false;
        dalleLib = dalleDetail = "";
        nomBox.setValue("");
        suite.clear();
        leve = 0;
        sel.effacer();
        face1 = face2 = null;
        choixChange();
    }

    private boolean brouillon() { return dalleOk || !nomBox.getValue().isEmpty() || !suite.isEmpty(); }

    private int nouvelleVue(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        for (String l : s.wrap("La dalle est la boîte de blocs choisie au sélecteur : elle coulissera vers le haut quand la suite sera composée devant elle.", iw, 12)) {
            pz.texte(l, ix, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        y += 5;
        // ------------------------------------------------------------------ dalle
        pz.texte("Dalle", ix, y + 4, 13, GRIS, false);
        y += 11;
        int bh = 25;
        pz.fond(ix, y, ix + iw, y + bh, NUIT_2);
        pz.cadre(ix, y, iw, bh, dalleOk ? OR_DOUX : FILET_2);
        pz.icone("verrou", ix + 6, y + 5, 7, dalleOk ? OR_CLAIR : GRIS_2);
        String lien = dalleOk ? "Changer" : "Choisir";
        float lw = pz.largeur(lien, 13, false);
        s.txt(dalleOk ? dalleLib : "Aucune dalle", ix + 18, y + 8, iw - 30 - lw, 14, dalleOk ? CREME : GRIS, false);
        s.txt(dalleOk ? dalleDetail : "deux coins à choisir dans le monde, à 8 blocs au plus", ix + 18, y + 17.5f, iw - 24, 11, GRIS, false);
        s.motCouleur(lien, ix + iw - 6 - lw, y + 8, 13, OR_CLAIR, () -> { ouvrirSelecteur(); RebornSounds.uiClick(); });
        y += bh;
        pz.texte("9 blocs par côté, 3 d'épaisseur, blocs simples seulement.", ix, y + 6, 11, GRIS_2, false);
        y += 15;
        // ------------------------------------------------------------------ nom
        pz.texte("Nom", ix, y + 4, 13, GRIS, false);
        pz.texteD("staff seulement, jamais montré", ix + iw, y + 4, 11, GRIS_2, false);
        y += 11;
        s.champ(nomBox, ix, y, iw, null);
        y += 23;
        // ------------------------------------------------------------------ suite, levée
        y = blocSuite(y);
        y += 2;
        y = rangeeLevee(y, leve, dalleOk ? dalle[4] : 0, v -> leve = v);
        // ------------------------------------------------------------------ sceller
        String raison = raisonSceller();
        String lab = "Sceller la porte";
        int bw = s.largeurBouton(lab);
        s.refusVerrou = raison;
        s.bouton(ix + iw - bw, y, bw, lab, true, this::sceller, raison != null);
        s.refusVerrou = null;
        if (brouillon()) s.motCouleur("Effacer", ix, y + 8.5f, 13, GRIS_2, () -> { effacer(); RebornSounds.uiClick(); });
        y += 30;
        return aides(y, true) + 4;
    }

    /** Pourquoi « Sceller la porte » est éteint (null : tout est bon). */
    String raisonSceller() {
        if (!drapeau("peutGerer")) return MODO;
        if (drapeau("bloque")) return ILLISIBLE;
        if (!dalleOk) return "Choisis d'abord la dalle (sélecteur).";
        if (nomBox.getValue().isBlank()) return "Donne un nom à la porte.";
        if (suite.size() < SUITE_MIN) return "La suite compte " + SUITE_MIN + " signes au moins.";
        return null;
    }

    private boolean envoiEnCours() { return attendu != null && System.currentTimeMillis() - envoiA < ATTENTE_MS; }

    private void attendre(String quoi) {
        envoiA = System.currentTimeMillis();
        attendu = quoi;
        RebornSounds.confirm();
    }

    private JsonArray suiteJson() {
        JsonArray a = new JsonArray();
        for (int i : suite) a.add(i);
        return a;
    }

    /** Crée la porte : la liste qui revient avec {@code dernier} ouvre sa fiche. */
    void sceller() {
        String raison = raisonSceller();
        if (raison != null) { StaffClient.toast(raison); RebornSounds.deny(); return; }
        if (envoiEnCours()) return;
        JsonObject o = base("porte_creer", null);
        JsonObject b = new JsonObject();
        b.addProperty("x", dalle[0]);
        b.addProperty("y", dalle[1]);
        b.addProperty("z", dalle[2]);
        b.addProperty("dx", dalle[3]);
        b.addProperty("dy", dalle[4]);
        b.addProperty("dz", dalle[5]);
        o.add("boite", b);
        o.addProperty("nom", nomBox.getValue().trim());
        o.add("signes", suiteJson());
        o.addProperty("leve", leve);
        StaffClient.send(o);
        attendre("creer");
    }

    /* ---------------------------------------------------------------- suite : ligne composée et grille 3A */

    /** Un clic sur une case de la grille : le signe s'ajoute à la fin (un signe peut revenir). */
    void ajouterSigne(int i) {
        if (i < 0 || i >= NOMS.length) return;
        if (suite.size() >= SUITE_MAX) { StaffClient.toast(SUITE_MAX + " signes au plus."); RebornSounds.deny(); return; }
        suite.add(i);
        RebornSounds.uiClick();
    }

    void effacerDernier() {
        if (!suite.isEmpty()) suite.remove(suite.size() - 1);
    }

    /**
     * « Suite » : compteur, ligne composée (« Tigre › Serpent › Chien », « Effacer le dernier ») puis la grille des douze
     * signes. Renvoie le bas de la grille.
     */
    private int blocSuite(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw(), n = suite.size();
        pz.texte("Suite", ix, y + 4, 13, GRIS, false);
        pz.texteD(n + " / " + SUITE_MAX + " signes", ix + iw, y + 4, 11, GRIS_2, false);
        y += 11;
        String eff = "Effacer le dernier";
        float ew = pz.largeur(eff, 11, false), larg = iw - 10 - ew - 8;
        float sep = pz.largeur(SEP_SUITE, 13, false), debut = pz.largeur(SEP_DEBUT, 13, false);
        // mise en page : à la ligne quand le nom suivant ne tient plus (« › » en tête de ligne)
        int lignes = 1;
        float x = 0;
        for (int k = 0; k < n; k++) {
            float w = pz.largeur(NOMS[suite.get(k)], 14, false);
            if (x > 0 && x + sep + w > larg) { lignes++; x = debut; }
            else if (x > 0) x += sep;
            suiteX[k] = x;
            suiteL[k] = lignes - 1;
            x += w;
        }
        int h = 17 + 10 * (lignes - 1);
        pz.fond(ix, y, ix + iw, y + h, NUIT_2);
        pz.cadre(ix, y, iw, h, OR_DOUX);
        float tx = ix + 5;
        for (int k = 0; k < n; k++) {
            float cy = y + 8.5f + 10 * suiteL[k];
            if (k > 0) {
                boolean tete = suiteL[k] != suiteL[k - 1];
                pz.texte(tete ? SEP_DEBUT : SEP_SUITE, tx + suiteX[k] - (tete ? debut : sep), cy, 13, GRIS_2, false);
            }
            pz.texte(NOMS[suite.get(k)], tx + suiteX[k], cy, 14, OR_CLAIR, false);
        }
        float cyFin = y + 8.5f + 10 * (lignes - 1);
        pz.px(tx + x + 1.5f, cyFin - 4, tx + x + 1.5f + 1f / GardeArt.echelle(), cyFin + 4, CREME);
        if (n == 0) pz.texte("clique les signes, dans l'ordre", tx + 6, cyFin, 13, GRIS_2, false);
        if (n > 0) s.motCouleur(eff, ix + iw - 5 - ew, y + 8.5f, 11, GRIS, () -> { effacerDernier(); RebornSounds.uiClick(); });
        else pz.texte(eff, ix + iw - 5 - ew, y + 8.5f, 11, GRIS_3, false);
        return grille(y + h + 6);
    }

    /**
     * Grille 3A : douze cases (2 rangées de 6), le dessin du signe et son nom ; une case déjà dans la suite porte un
     * liseré d'or doux et une pastille d'or avec son ou ses rangs (« 1 », « 1 · 4 »).
     */
    private int grille(int y) {
        GardeArt.Pinceau pz = s.pz;
        GuiGraphicsExtractor ctx = s.ctxCourant;
        int ix = s.ix(), w = 28, h = 29;
        float pas = s.iw() / 6f;
        boolean pleine = suite.size() >= SUITE_MAX;
        for (int i = 0; i < NOMS.length; i++) {
            int x0 = ix + Math.round((i % 6) * pas), y0 = y + (i / 6) * 33;
            boolean on = suite.contains(i), hov = !pleine && s.hover(x0, y0, w, h) && s.cliquable(y0, h);
            pz.fond(x0, y0, x0 + w, y0 + h, on || hov ? NUIT_3 : NUIT_2);
            pz.cadre(x0, y0, w, h, on || hov ? OR_DOUX : FILET_2);
        }
        // les mains par-dessus les cases, sous les noms et les rangs : le pinceau est vidé avant et après elles
        pz.vider(ctx);
        int k = GardeArt.echelle(), cote = 17 * k;
        DaKit.debutPx(ctx);
        LotBlit lot = LotBlit.ouvrir(ctx, MAINS, MAIN * NOMS.length, MAIN);
        for (int i = 0; i < NOMS.length; i++) {
            int x0 = ix + Math.round((i % 6) * pas), y0 = y + (i / 6) * 33;
            // lue un demi-texel en retrait : la main voisine ne bave pas (texture filtrée)
            lot.etire(Math.round((x0 + 5.5f) * k), (y0 + 2) * k, cote, cote, i * MAIN + 0.5f, 0, MAIN - 1, MAIN,
                pleine && !suite.contains(i) ? 0x66FFFFFF : 0xFFFFFFFF);
        }
        lot.fermer();
        DaKit.finPx(ctx);
        for (int i = 0; i < NOMS.length; i++) {
            int x0 = ix + Math.round((i % 6) * pas), y0 = y + (i / 6) * 33;
            boolean on = suite.contains(i);
            pz.texteC(NOMS[i], x0 + w / 2f, y0 + 24, 11, on ? CREME : GRIS, on);
            if (on) {
                String rangs = rangs(i, w - 6);
                float lw = pz.largeur(rangs, 11, true) + 4;
                pz.px(x0 + w - 1 - lw, y0 + 1, x0 + w - 1, y0 + 8, OR);
                pz.texteC(rangs, x0 + w - 1 - lw / 2f, y0 + 4.5f, 11, NUIT, true);
            }
            final int signe = i;
            if (s.cliquable(y0, h)) s.hits.add(new StaffScreen.Hit(x0, y0, w, h, () -> ajouterSigne(signe), null, null));
        }
        return y + 66;
    }

    /** Rangs du signe {@code i} dans la suite : « 1 · 4 », resserré puis compté (« ×5 ») quand la case est trop étroite. */
    private String rangs(int i, float larg) {
        StringBuilder large = new StringBuilder(), serre = new StringBuilder();
        int n = 0;
        for (int k = 0; k < suite.size(); k++) {
            if (suite.get(k) != i) continue;
            if (n++ > 0) { large.append(" · "); serre.append('·'); }
            large.append(k + 1);
            serre.append(k + 1);
        }
        if (s.pz.largeur(large.toString(), 11, true) + 4 <= larg) return large.toString();
        if (s.pz.largeur(serre.toString(), 11, true) + 4 <= larg) return serre.toString();
        return "×" + n;
    }

    /** « Levée  − 2 + » bornée de 1 à {@code haut} (la hauteur de la dalle ; 0 : pas encore de dalle). */
    private int rangeeLevee(int y, int valeur, int haut, IntConsumer poser) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        float cy = y + 8;
        boolean ok = haut > 0;
        pz.texte("Levée", ix, cy, 13, GRIS, false);
        petit(ix + 46, y + 2, "−", ok && valeur > 1, () -> poser.accept(valeur - 1));
        pz.fond(ix + 61, y, ix + 81, y + 16, NUIT_2);
        pz.cadre(ix + 61, y, 20, 16, FILET_2);
        pz.texteC(ok ? String.valueOf(valeur) : "–", ix + 71, cy, 14, ok ? CREME : GRIS_2, false);
        petit(ix + 84, y + 2, "+", ok && valeur < haut, () -> poser.accept(valeur + 1));
        s.txt(ok ? "blocs (la dalle fait " + haut + " de haut)" : "choisis d'abord la dalle", ix + 103, cy, iw - 103, 11, GRIS_2, false);
        return y + 24;
    }

    private void petit(int x, int y, String label, boolean ok, Runnable r) {
        GardeArt.Pinceau pz = s.pz;
        int c = 12;
        boolean hov = ok && s.hover(x, y, c, c) && s.cliquable(y, c);
        pz.fond(x, y, x + c, y + c, hov ? NUIT_3 : NUIT_2);
        pz.cadre(x, y, c, c, hov ? OR_DOUX : FILET_2);
        pz.texteC(label, x + c / 2f, y + c / 2f, 13, ok ? CREME : GRIS_3, false);
        if (ok && s.cliquable(y, c)) s.hits.add(new StaffScreen.Hit(x, y, c, c, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
    }

    /** Aides du bas : « Suite », et « Sélecteur » pour une nouvelle porte. */
    private int aides(int y, boolean selecteur) {
        GardeArt.Pinceau pz = s.pz;
        pz.capitales("Suite", s.ix(), y + 4, 11, GRIS_2);
        y += 10;
        y = note("Clique les signes dans l'ordre : 2 à 12, un même signe peut revenir. La suite ne s'affiche nulle part en jeu, elle se transmet en RP. Les Helpers ne la voient pas ; elle n'est jamais écrite au journal.", GRIS_2, y);
        if (!selecteur) return y;
        y += 2;
        pz.capitales("Sélecteur", s.ix(), y + 4, 11, GRIS_2);
        y += 10;
        return note("Changer : le volet se replie, la souris choisit dans le monde (clic : premier coin ; Maj + clic : second coin). Entrée valide, Échap revient ici sans rien changer.", GRIS_2, y);
    }

    /* ---------------------------------------------------------------- sous-éditeurs de la fiche */

    /** « Changer la suite » : la grille 3A préremplie. */
    void ouvrirSuite(JsonObject p) {
        if (!gerer()) return;
        suite.clear();
        suite.addAll(ordinaux(p));
        vue = "suite";
        s.setFocused(null);
        s.scroll = 0;
    }

    void ouvrirNom(JsonObject p) {
        if (!gerer()) return;
        nomBox.setValue(StaffScreen.str(p, "nom"));
        vue = "nom";
        s.scroll = 0;
        s.setFocused(nomBox);
        nomBox.setFocused(true);
    }

    void ouvrirLeve(JsonObject p) {
        if (!gerer()) return;
        leve = Math.max(1, StaffScreen.num(p, "leve"));
        vue = "leve";
        s.setFocused(null);
        s.scroll = 0;
    }

    private int titre(String ic, String t, int y) {
        s.pz.icone(ic, s.ix(), y + 1, 8, OR_CLAIR);
        s.txt(t, s.ix() + 13, y + 5, s.iw() - 13, 15, CREME, true);
        return y + 15;
    }

    /** « Annuler » en mot, l'action en plaque laquée à droite ; renvoie le bas. */
    private int valider(String lab, String refus, Runnable r, int y) {
        int ix = s.ix(), iw = s.iw(), bw = s.largeurBouton(lab), bx = ix + iw - bw;
        s.refusVerrou = refus;
        s.bouton(bx, y, bw, lab, true, r, refus != null);
        s.refusVerrou = null;
        s.motCouleur("Annuler", bx - 10 - s.pz.largeur("Annuler", 13, false), y + 8.5f, 13, GRIS, () -> { versFiche(); RebornSounds.uiClick(); });
        return y + 17;
    }

    private int suiteVue(int y) {
        JsonObject p = porte(ficheId);
        if (p == null) return s.vide(y, "Porte introuvable.", "Son sceau a pu être retiré par un autre membre du staff.");
        y = titre("noter", "Changer la suite de " + ficheId, y);
        y = note("Elle remplace l'ancienne dès l'enregistrement : préviens en RP ceux qui la connaissent.", GRIS_2, y);
        y += 3;
        y = blocSuite(y);
        y += 4;
        y = valider("Enregistrer la suite", raisonSuite(), this::enregistrerSuite, y);
        y += 13;
        return aides(y, false) + 4;
    }

    private String raisonSuite() {
        if (!drapeau("peutGerer")) return MODO;
        if (drapeau("bloque")) return ILLISIBLE;
        return suite.size() < SUITE_MIN ? "La suite compte " + SUITE_MIN + " signes au moins." : null;
    }

    void enregistrerSuite() {
        JsonObject p = porte(ficheId);
        String raison = p == null ? "Porte introuvable." : raisonSuite();
        if (raison != null) { StaffClient.toast(raison); RebornSounds.deny(); return; }
        if (envoiEnCours()) return;
        if (suite.equals(ordinaux(p))) { versFiche(); return; }
        JsonObject o = base("porte_signes", ficheId);
        o.add("signes", suiteJson());
        StaffClient.send(o);
        attendre(ficheId);
    }

    private int nomVue(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        if (porte(ficheId) == null) return s.vide(y, "Porte introuvable.", "Son sceau a pu être retiré par un autre membre du staff.");
        y = titre("noter", "Renommer " + ficheId, y);
        y += 3;
        pz.texte("Nom", ix, y + 4, 13, GRIS, false);
        pz.texteD("staff seulement, jamais montré", ix + iw, y + 4, 11, GRIS_2, false);
        y += 11;
        s.champ(nomBox, ix, y, iw, null);
        y += 24;
        return valider("Renommer", raisonNom(), this::enregistrerNom, y) + 6;
    }

    private String raisonNom() {
        if (!drapeau("peutGerer")) return MODO;
        if (drapeau("bloque")) return ILLISIBLE;
        return nomBox.getValue().isBlank() ? "Donne un nom à la porte." : null;
    }

    void enregistrerNom() {
        JsonObject p = porte(ficheId);
        String raison = p == null ? "Porte introuvable." : raisonNom();
        if (raison != null) { StaffClient.toast(raison); RebornSounds.deny(); return; }
        if (envoiEnCours()) return;
        String n = nomBox.getValue().trim();
        if (n.equals(StaffScreen.str(p, "nom"))) { versFiche(); return; }
        JsonObject o = base("porte_nom", ficheId);
        o.addProperty("nom", n);
        StaffClient.send(o);
        attendre(ficheId);
    }

    private int leveVue(int y) {
        JsonObject p = porte(ficheId);
        if (p == null) return s.vide(y, "Porte introuvable.", "Son sceau a pu être retiré par un autre membre du staff.");
        y = titre("haut", "Changer la levée de " + ficheId, y);
        y = note("De combien de blocs la dalle monte : de 1 à sa hauteur. La flèche dans le monde montre jusqu'où.", GRIS_2, y);
        y += 3;
        y = rangeeLevee(y, leve, Math.max(1, StaffScreen.num(p, "dy")), v -> leve = v);
        return valider("Enregistrer", raisonLeve(p), this::enregistrerLeve, y) + 6;
    }

    private String raisonLeve(JsonObject p) {
        if (!drapeau("peutGerer")) return MODO;
        if (drapeau("bloque")) return ILLISIBLE;
        return "fermee".equals(StaffScreen.str(p, "etat")) ? null : "La porte doit d'abord être fermée.";
    }

    void enregistrerLeve() {
        JsonObject p = porte(ficheId);
        String raison = p == null ? "Porte introuvable." : raisonLeve(p);
        if (raison != null) { StaffClient.toast(raison); RebornSounds.deny(); return; }
        if (envoiEnCours()) return;
        if (leve == StaffScreen.num(p, "leve")) { versFiche(); return; }
        JsonObject o = base("porte_leve", ficheId);
        o.addProperty("leve", leve);
        StaffClient.send(o);
        attendre(ficheId);
    }

    /* ---------------------------------------------------------------- sélecteur de dalle : bandeau (volet replié) */

    /** Ouvre le sélecteur ; une dalle déjà choisie est gardée et revérifiée (le staff a pu bouger). */
    void ouvrirSelecteur() {
        sel.ouvrir("creer");
        s.setFocused(null);
        s.scroll = 0;
        var o = Minecraft.getInstance().options;
        var touches = new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyLeft, o.keyDown, o.keyRight};
        touchesDepl = new String[touches.length];
        for (int i = 0; i < touches.length; i++) touchesDepl[i] = touches[i].getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        choixChange();
    }

    private void fermerSelecteur() {
        GardePlaques.relacherTouches();
        sel.fermer();
        montrerCurseur();
    }

    /** Raison locale du refus d'une dalle (les limites de {@code MudraRegles.validerBoite}), ou null. */
    static String refusLocal(int dx, int dy, int dz) {
        if (dx > COTE_MAX || dy > COTE_MAX || dz > COTE_MAX) return "Trop grande : " + COTE_MAX + " blocs par côté au plus.";
        if (Math.min(dx, dz) > EPAISSEUR_MAX) return "Trop épaisse : " + EPAISSEUR_MAX + " blocs d'épaisseur au plus.";
        if (dx * dy * dz > VOLUME_MAX) return "Trop grosse : " + VOLUME_MAX + " blocs au plus.";
        return null;
    }

    /**
     * La dalle du sélecteur a changé (clic, ouverture) : limites vérifiées ici, puis une demande {@code porte_verifier}
     * est due. La réponse d'une demande plus ancienne ne compte plus.
     */
    private void choixChange() {
        verifAttendue = -1;
        verifDue = false;
        selRaison.clear();
        selCoin1 = sel.c1 == null ? "" : sel.c1.getX() + " " + sel.c1.getY() + " " + sel.c1.getZ();
        selCoin2 = sel.c2 == null ? "" : sel.c2.getX() + " " + sel.c2.getY() + " " + sel.c2.getZ();
        if (!sel.choisi()) { verifEtat = RIEN; verifRaison = ""; return; }
        int dx = sel.dx(), dy = sel.dy(), dz = sel.dz();
        selDims = "Dalle " + dims(dx, dy, dz) + "  ·  " + blocs(dx * dy * dz);
        selLigne = dims(dx, dy, dz) + "  ·  " + blocs(dx * dy * dz);
        selLevee = "lèvera de " + blocs(dy);
        String refus = refusLocal(dx, dy, dz);
        if (refus != null) { refuser(REFUS_LOCAL, refus); return; }
        verifEtat = ATTENTE;
        verifRaison = "";
        verifDue = true;
    }

    private void refuser(int etat, String raison) {
        verifEtat = etat;
        verifRaison = raison == null || raison.isBlank() ? "Dalle refusée par le serveur." : raison;
        selRaison.clear();
        List<String> l = s.wrap(verifRaison, s.iw() - 46, 12);
        for (int i = 0; i < Math.min(2, l.size()); i++) selRaison.add(l.get(i));
    }

    /** Demande {@code porte_verifier} due (250 ms d'écart au moins), réponse attendue lue. */
    private void verifier() {
        long now = horloge.getAsLong();
        if (verifDue && now - verifEnvoyeeA >= VERIF_ECART_MS) {
            verifDue = false;
            verifEnvoyeeA = now;
            verifAttendue = ++verifN;
            var joueur = Minecraft.getInstance().player;
            if (joueur != null) {
                var yeux = joueur.getEyePosition();
                verifX = yeux.x;
                verifY = yeux.y;
                verifZ = yeux.z;
            }
            JsonObject o = base("porte_verifier", null);
            o.addProperty("n", verifAttendue);
            JsonObject b = new JsonObject();
            b.addProperty("x", (int) Math.floor(sel.choixBoite[0]));
            b.addProperty("y", (int) Math.floor(sel.choixBoite[1]));
            b.addProperty("z", (int) Math.floor(sel.choixBoite[2]));
            b.addProperty("dx", sel.dx());
            b.addProperty("dy", sel.dy());
            b.addProperty("dz", sel.dz());
            o.add("boite", b);
            StaffClient.send(o);
        }
        JsonObject r = StaffClient.porteVerif();
        // seule la réponse à la dernière demande compte : une plus ancienne (dalle d'avant) est ignorée
        if (r == null || verifEtat != ATTENTE || verifAttendue < 0 || StaffScreen.num(r, "n") != verifAttendue) return;
        verifAttendue = -1;
        if (StaffScreen.bool(r, "ok")) verifEtat = VALABLE;
        else refuser(REFUS_SERVEUR, StaffScreen.str(r, "m"));
    }

    private boolean refusee() { return verifEtat == REFUS_LOCAL || verifEtat == REFUS_SERVEUR; }

    /** Pourquoi « Valider la dalle » est éteint. */
    private String raisonValider() {
        return switch (verifEtat) {
            case VALABLE -> null;
            case ATTENTE -> "Vérification de la dalle en cours…";
            case RIEN -> "Clique d'abord un bloc dans le monde (Maj + clic : second coin).";
            default -> verifRaison;
        };
    }

    /** Le point GUI ({@code x, y}) est hors du volet : à sa gauche, ou sous le bandeau replié du sélecteur. */
    private boolean horsVolet(double x, double y) {
        return x < s.px0() - 2 || y > s.py1() + 2;
    }

    private void ligneBandeau(String label, String val, int col, float cy) {
        s.pz.texte(label, s.ix(), cy, 12, GRIS, false);
        s.txt(val, s.ix() + 46, cy, s.iw() - 46, 12, col, false);
    }

    private int bandeau(int y) {
        GardeArt.Pinceau pz = s.pz;
        int ix = s.ix(), iw = s.iw();
        // visé
        if (sel.viseK == 0) ligneBandeau("Visé", "rien (vise un bloc)", GRIS_2, y + 5);
        else {
            long cle = sel.viseBloc.asLong() ^ (sel.viseLoin ? Math.round(sel.viseDist * 10) << 52 | 1L << 62 : 0);
            if (cle != viseCle) {
                viseCle = cle;
                viseLib = sel.viseLoin
                    ? SelecteurPlaque.coin(sel.viseBoite) + "  ·  trop loin (" + SelecteurPlaque.un(sel.viseDist) + " blocs, " + (int) SelecteurPlaque.PORTEE + " au plus)"
                    : "Bloc  ·  " + SelecteurPlaque.coin(sel.viseBoite);
            }
            ligneBandeau("Visé", viseLib, sel.viseLoin ? ROUGE : GRIS, y + 5);
        }
        // coins, dalle
        ligneBandeau("Coin 1", sel.c1 == null ? "clique un bloc" : selCoin1, sel.c1 == null ? GRIS_2 : CREME, y + 16);
        ligneBandeau("Coin 2", sel.c2 != null ? selCoin2 : sel.c1 != null ? "Maj + clic sur un autre bloc" : "–", sel.c2 != null ? CREME : GRIS_2, y + 27);
        switch (verifEtat) {
            case RIEN -> ligneBandeau("Dalle", "aucune", GRIS_2, y + 38);
            case VALABLE -> ligneBandeau("Dalle", selLigne + "  ·  valable", OK, y + 38);
            case ATTENTE -> ligneBandeau("Dalle", selLigne + "  ·  vérification…", GRIS, y + 38);
            default -> {
                for (int i = 0; i < selRaison.size(); i++) ligneBandeau(i == 0 ? "Dalle" : "", selRaison.get(i), ROUGE, y + 38 + 8 * i);
            }
        }
        // touches
        float half = ix + iw / 2f, cy = y + 56;
        float x = ix;
        x += capsule("Clic", x, cy) + 4;
        pz.texte("premier coin", x, cy, 11, GRIS, false);
        x = half;
        x += capsule("Maj", x, cy) + 2.5f;
        x += pz.texte("+", x, cy, 11, GRIS_2, false) + 5;
        x += capsule("Clic", x, cy) + 4;
        pz.texte("second coin", x, cy, 11, GRIS, false);
        cy = y + 70;
        x = ix;
        x += capsule("Clic droit", x, cy) + 4;
        pz.texte("tourner", x, cy, 11, GRIS, false);
        x = half;
        for (String t : touchesDepl) x += capsule(t, x, cy) + 1.5f;
        pz.texte("se déplacer", x + 3, cy, 11, GRIS, false);
        pz.filetH(ix, ix + iw, y + 80, FILET);
        cy = y + 94.5f;
        x = ix;
        x += capsule("Échap", x, cy) + 4;
        pz.texte("annuler", x, cy, 11, GRIS, false);
        String lab = "Valider la dalle";
        int bw = s.largeurBouton(lab);
        boolean ok = verifEtat == VALABLE;
        if (ok) capsule("Entrée", ix + iw - bw - 29, cy);
        s.refusVerrou = raisonValider();
        s.bouton(ix + iw - bw, y + 86, bw, lab, true, this::validerSelecteur, !ok);
        s.refusVerrou = null;
        return y + 128;
    }

    /** Touche en petite capsule, milieu en {@code cy} ; renvoie sa largeur. */
    private float capsule(String t, float x, float cy) {
        GardeArt.Pinceau pz = s.pz;
        float w = Math.max(9.5f, pz.largeur(t, 11, false) + 5);
        pz.px(x, cy - 4.5f, x + w, cy + 5, NUIT_3);
        pz.cadre(x, cy - 4.5f, w, 9.5f, FILET_2);
        pz.texteC(t, x + w / 2f, cy, 11, GRIS, false);
        return w;
    }

    /** Entrée ou « Valider la dalle » : la dalle valable devient celle de la nouvelle porte. */
    void validerSelecteur() {
        if (verifEtat != VALABLE) { StaffClient.toast(raisonValider()); RebornSounds.deny(); return; }
        int dy = sel.dy();
        // la levée repart de toute la hauteur quand la dalle change de hauteur
        if (!dalleOk || dalle[4] != dy || leve < 1 || leve > dy) leve = dy;
        dalle[0] = (int) Math.floor(sel.choixBoite[0]);
        dalle[1] = (int) Math.floor(sel.choixBoite[1]);
        dalle[2] = (int) Math.floor(sel.choixBoite[2]);
        dalle[3] = sel.dx();
        dalle[4] = dy;
        dalle[5] = sel.dz();
        dalleOk = true;
        dalleLib = "Boîte " + dims(dalle[3], dalle[4], dalle[5]) + "  ·  " + blocs(dalle[3] * dalle[4] * dalle[5]);
        dalleDetail = "choisie au sélecteur  ·  " + dalle[0] + " " + dalle[1] + " " + dalle[2];
        fermerSelecteur();
        RebornSounds.confirm();
    }

    /* ================================================================ monde : repères */

    /** À chaque image, outil ouvert (avant le volet) : repères dans le monde ({@link MondePlaques}) et étiquettes. */
    void monde(GuiGraphicsExtractor ctx) {
        MondePlaques.debut();
        GardeArt.Pinceau pz = s.pz;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (sel.actif) {
            if (sel.choisi()) {
                // dalle tendue entre les deux coins : voile léger, arêtes et coins pleins, d'or ou rouge si elle est refusée
                boolean refus = refusee();
                MondePlaques.voile(sel.choixBoite, null, refus ? MondePlaques.VOILE_REFUS : MondePlaques.VOILE_CHOIX);
                MondePlaques.boite(sel.choixBoite, refus ? MondePlaques.ROUGE_PLEIN : MondePlaques.OR_PLEIN);
                etiquette(sel.choixBoite, selDims, refus ? "refusée" : selLevee, true, refus ? ROUGE : OR_CLAIR);
                if (!refus) fleche(sel.choixBoite, sel.dy());
                if (sel.c1 != null) numeroCoin(sel.c1, face1, "1");
                if (sel.c2 != null) numeroCoin(sel.c2, face2, "2");
            }
            if (sel.viseK != 0 && horsVolet(s.mouseX, s.mouseY) && !rotation) {
                MondePlaques.boite(sel.viseBoite, sel.viseLoin ? MondePlaques.HORS : MondePlaques.FIL);
                if (!sel.viseLoin && sel.viseFace != null) MondePlaques.voile(sel.viseBoite, sel.viseFace, MondePlaques.VOILE_FACE);
                // Maj enfoncée : la dalle jusqu'au bloc survolé (or à 55 %, 1 px), avant le clic
                if (GardePlaques.majEnfoncee(mc) && sel.apercuBoite(B2)) MondePlaques.boite(B2, MondePlaques.APERCU);
            }
            pz.vider(ctx);
            return;
        }
        // portes à moins de 64 blocs : coins et id ; celle de la fiche (ou la rangée survolée) cernée plus fort
        String forte = "liste".equals(vue) ? (survolId != null ? survolId : ficheId) : "nouvelle".equals(vue) ? null : ficheId;
        int poses = 0;
        for (JsonElement e : StaffScreen.arr(donnees(), "l")) {
            JsonObject p = e.getAsJsonObject();
            double di = dbl(p, "dist");
            if (di < 0 || di > RAYON) continue;
            if (poses++ >= REPERES_MAX) break;
            boite(p, B);
            String id = StaffScreen.str(p, "id");
            if (id.equals(forte)) {
                MondePlaques.boite(B, MondePlaques.OR_PLEIN);
                etiquette(B, id + " · " + nom(p), etatLib(StaffScreen.str(p, "etat")).toLowerCase(Locale.ROOT) + " · " + signes(nombreSignes(p)), true, OR_CLAIR);
                if ("leve".equals(vue)) fleche(B, leve);
            } else {
                MondePlaques.boite(B, MondePlaques.COINS);
                etiquette(B, id, null, false, 0);
            }
        }
        if ("nouvelle".equals(vue) && dalleOk) {
            B[0] = dalle[0]; B[1] = dalle[1]; B[2] = dalle[2];
            B[3] = dalle[0] + dalle[3]; B[4] = dalle[1] + dalle[4]; B[5] = dalle[2] + dalle[5];
            MondePlaques.voile(B, null, MondePlaques.VOILE_CHOIX);
            MondePlaques.boite(B, MondePlaques.OR_PLEIN);
            etiquette(B, "Dalle " + dims(dalle[3], dalle[4], dalle[5]) + "  ·  " + blocs(dalle[3] * dalle[4] * dalle[5]), "lèvera de " + blocs(leve), true, OR_CLAIR);
            fleche(B, leve);
        }
        pz.vider(ctx);
    }

    /** Étiquette sans fond au-dessus de la boîte (ombre du HUD) : titre, seconde ligne en {@code col2} ; GUI. */
    private void etiquette(double[] b, String l1, String l2, boolean fort, int col2) {
        if (!ProjectionMonde.versGui((b[0] + b[3]) / 2, b[4] + 0.15, (b[2] + b[5]) / 2, V)) return;
        GardeArt.Pinceau pz = s.pz;
        int taille = fort ? 12 : 10;
        float w = pz.largeur(l1, taille, fort);
        float x = V.x - w / 2f, y = V.y - (l2 == null ? 6 : 12);
        pz.texte(l1, x + 0.5f, y + 0.5f, taille, 0xC0000000, fort);
        pz.texte(l1, x, y, taille, fort ? CREME : 0xE0EEE4D2, fort);
        if (l2 == null) return;
        float w2 = pz.largeur(l2, 10, false);
        pz.texte(l2, V.x - w2 / 2f + 0.5f, y + 8.5f, 10, 0xC0000000, false);
        pz.texte(l2, V.x - w2 / 2f, y + 8, 10, col2, false);
    }

    /** « 1 » ou « 2 » sur la face cliquée du bloc d'un coin de la dalle. */
    private void numeroCoin(BlockPos b, Direction f, String n) {
        double x = b.getX() + 0.5, y = b.getY() + 0.5, z = b.getZ() + 0.5;
        if (f != null) { x += f.getStepX() * 0.5; y += f.getStepY() * 0.5; z += f.getStepZ() * 0.5; }
        if (!ProjectionMonde.versGui(x, y, z, V)) return;
        GardeArt.Pinceau pz = s.pz;
        float w = pz.largeur(n, 12, true);
        pz.texte(n, V.x - w / 2f + 0.5f, V.y + 0.5f, 12, 0xC0000000, true);
        pz.texte(n, V.x - w / 2f, V.y, 12, CREME, true);
    }

    /**
     * Flèche de levée, à droite de la dalle : du pied de la dalle jusqu'où montera son sommet ({@code leve} blocs plus
     * haut), à l'échelle du monde.
     */
    private void fleche(double[] b, int leve) {
        if (leve <= 0) return;
        float xa = Float.NEGATIVE_INFINITY, bas = 0, haut = 0;
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? b[0] : b[3], z = (i & 2) == 0 ? b[2] : b[5];
            if (!ProjectionMonde.versGui(x, b[1], z, V)) continue;
            float px = V.x, py = V.y;
            if (!ProjectionMonde.versGui(x, b[4] + leve, z, V)) continue;
            if (Math.max(px, V.x) > xa) { xa = Math.max(px, V.x); bas = py; haut = V.y; }
        }
        if (xa == Float.NEGATIVE_INFINITY || bas - haut < 8) return;
        GardeArt.Pinceau pz = s.pz;
        float e = 1f / GardeArt.echelle();
        int col = 0xD9E8C27A;
        xa += 8;
        pz.px(xa, haut, xa + 2 * e, bas, col);
        for (int k = 1; k < 9; k++) pz.px(xa - k * e, haut + k * e, xa + (2 + k) * e, haut + (k + 1) * e, col);
    }

    /* ================================================================ entrées (routées par l'écran) */

    boolean actif() { return "outils".equals(s.tab) && "portes".equals(s.outil); }

    /** Clic gauche dans le monde (hors du volet), sélecteur ouvert : pose un coin de la dalle. */
    boolean clicMonde(double x, double y, boolean maj) {
        if (!actif() || !sel.actif || !horsVolet(x, y)) return false;
        Minecraft mc = Minecraft.getInstance();
        sel.survoler(mc, x, y);
        String err = sel.clic(mc, maj);
        if (err != null) { StaffClient.toast(err); RebornSounds.deny(); }
        else if (sel.viseK != 0) {
            if (sel.c2 != null) face2 = sel.viseFace;
            else { face1 = sel.viseFace; face2 = null; }
            choixChange();
            RebornSounds.uiClick();
        }
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

    void relacher() { montrerCurseur(); }

    /** Échap : un cran en arrière. Faux : la liste rend la main aux outils. */
    boolean echap() {
        return actif() && reculer();
    }

    /**
     * Touches de l'outil : Entrée valide la dalle du sélecteur, le nom ou la levée ; Ctrl + Entrée scelle la porte ou
     * enregistre la suite ; Retour arrière efface le dernier signe quand aucun champ n'a la main.
     */
    boolean touche(int k, boolean ctrl) {
        if (!actif() || s.formulaire.actif()) return false;
        boolean entree = k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER;
        if (sel.actif) {
            if (entree) validerSelecteur();
            return entree;
        }
        boolean champ = s.getFocused() instanceof EditBox b && b.isFocused();
        if (entree) {
            switch (vue) {
                case "nouvelle" -> { if (ctrl) { sceller(); return true; } }
                case "suite" -> { enregistrerSuite(); return true; }
                case "nom" -> { enregistrerNom(); return true; }
                case "leve" -> { enregistrerLeve(); return true; }
                default -> { }
            }
            return false;
        }
        if (k == GLFW.GLFW_KEY_BACKSPACE && !champ && ("nouvelle".equals(vue) || "suite".equals(vue)) && !suite.isEmpty()) {
            effacerDernier();
            RebornSounds.uiClick();
            return true;
        }
        return false;
    }

    /** Sélecteur ouvert : les touches de déplacement passent au personnage (le volet est replié). */
    boolean deplacement(KeyEvent e, boolean appui) {
        if (!actif() || !sel.actif) return false;
        var o = Minecraft.getInstance().options;
        // Maj ne passe pas au personnage : c'est le modificateur du second coin
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump}) {
            if (k.matches(e)) { k.setDown(appui); return true; }
        }
        return false;
    }

    /* ================================================================ banc */

    void debugVue(String v, String id) {
        vue = v;
        ficheId = id;
        s.scroll = 0;
    }

    void debugNom(String n) { nomBox.setValue(n); }

    /** Banc : l'état de l'outil en une ligne, sans jamais la suite (son nombre de signes seulement). */
    String debugBilan() {
        return "vue " + vue + (ficheId == null ? "" : " " + ficheId) + ", sélecteur " + sel.actif + ", visé " + sel.viseK + ", choisi " + sel.choixK
            + ", dalle du sélecteur " + (sel.choisi() ? dims(sel.dx(), sel.dy(), sel.dz()) : "aucune") + " état " + verifEtat
            + ", dalle " + (dalleOk ? dims(dalle[3], dalle[4], dalle[5]) : "aucune") + ", suite de " + suite.size() + ", levée " + leve
            + ", repères " + MondePlaques.nombre();
    }
}
