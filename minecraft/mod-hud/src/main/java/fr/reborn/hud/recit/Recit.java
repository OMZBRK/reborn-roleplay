package fr.reborn.hud.recit;

import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Mise;
import fr.reborn.hud.ui.TexteRaster.Police;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Texte au centre</b> (lot 2 « Reprises Ketto », Game Plan n° 17, étapes 2c et 2d) : un texte posé au milieu de
 * l'écran, sans fond, mots colorés. Ici le <b>service</b> : file, minuteurs, pages. Le dessin, la place à l'écran et les
 * règles de visibilité sont dans {@link RecitHud} ; l'allure de chaque texte, dans son {@link Gabarit}.
 *
 * <p><b>Deux allures, une seule place au centre</b> (décision du 02/11) :
 * <ul>
 *   <li>la <b>narration du staff</b> ({@code /narration}, canal {@code reborn:narration}, {@link NarrationClient}) :
 *       {@link Gabarit#NARRATION}, plein écran, blanc pur, plus grande ;</li>
 *   <li>les <b>stèles, plaques, pancartes et objets</b> lus en concentration en les visant (lot 4, {@link #lire}) :
 *       {@link Gabarit#STELE}, le rendu des maquettes a1 à a8 (Alegreya Sans Bold Italic, 340 px, taille Ketto).</li>
 * </ul>
 * Le paquet porte le nom de ce qui s'affiche (un texte de récit, dans la police {@link Police#RECIT}), pas celui de qui
 * l'envoie : il servira aussi aux cinématiques, à la douane et aux quêtes, qui choisiront leur gabarit.
 *
 * <p><b>API</b> (fil de rendu seulement : réception par {@code client.execute}, banc par le tick) :
 * <ul>
 *   <li>{@link #montrer(String, List, int, Mode, int, long, Gabarit)} : un message {@code narration}, champ pour champ,
 *       aux règles du contrat (PLUGINS_REFERENCE §4 bis.3 bis ; receveur du canal : {@link NarrationClient}, qui passe
 *       {@link Gabarit#NARRATION}) — {@code id} ; segments {@code [texte, clé]} ({@code ""} blanc, {@code rouge}
 *       #E5483C, {@code or} #F2D08A, {@code ombre} gris neutre #8F8B8E, {@code gris} #C2B59A ; clé inconnue = blanc) ;
 *       {@code s} ({@link #AUTO} = clamp(1,3 s + n / 30 ; 4 s ; 60 s), n = points de code du texte <b>tel que reçu</b>,
 *       plus 1,2 s (deux fondus de page) par page au-delà de la première ; 2
 *       à 180 s ; {@link #TENIR} = jusqu'à {@link #cacher}, garde-fou de 10 min ; toute autre valeur = {@link #AUTO}) ;
 *       {@code mode} ; {@code pos} (0 à 100 = centre du bloc en % de la hauteur ; autre valeur ou {@link #POS_DEFAUT} =
 *       le centre du gabarit et les règles du KO et de l'écran noir, {@link RecitHud}) ; {@code ecoule} (rattrapage :
 *       affichée comme si elle avait commencé {@code ecoule} ms plus tôt — il reste D − ecoule, la page de départ est
 *       celle dont la tranche contient {@code ecoule}, fondu d'entrée normal ; ignorée si {@code ecoule} ≥ D). La durée
 *       court du début du fondu d'entrée à la fin du fondu de sortie et continue quand le texte est masqué (F1, mode
 *       photo, écran ouvert).</li>
 *   <li>{@link Mode#FILE} : après la narration en cours, 400 ms entre deux, 8 en attente au plus (une 9e fait sortir la
 *       plus ancienne en attente) ; {@link Mode#REMPLACE} : coupe la narration en cours (fondu de coupe du gabarit,
 *       150 ms), <b>vide la file</b> et montre la nouvelle juste après, sans la pause de 400 ms (sa durée part du début
 *       de son fondu d'entrée). Un {@code id} déjà à l'écran ou en file est un doublon : ignoré.</li>
 *   <li>{@link #cacher(String)} : affichée → fondu de sortie du gabarit (narration : 700 ms), puis la file reprend (400 ms plus tard) ; en
 *       attente → retirée ; inconnue → rien. {@code id} vide, ou {@link #toutCacher()} : écran et file vidés (la lecture
 *       d'une plaque continue : elle suit la concentration du joueur). Déconnexion : tout est oublié ({@link #vider()}).</li>
 *   <li>{@link #lire(String, List)} (lot 4) : lecture d'une stèle ou d'une plaque ({@link Gabarit#STELE}), tenue jusqu'à
 *       {@link #cacher} (fin de la concentration ou de la visée) ; une autre plaque visée = {@code lire} avec son
 *       {@code id} : remplacement rapide (fondu de 150 ms), sans toucher à la file des narrations ; même {@code id} =
 *       déjà lue, rien ne change. <b>Aucune minuterie</b>, pages comprises : une plaque de plus d'une page passe à la
 *       suivante par {@link #lireSuite} (une action du joueur, à brancher au lot 4). Une narration passe <b>devant</b> la
 *       lecture (et devant la file des narrations qui attendent) : le texte de la plaque s'efface et revient, à la page
 *       où il était, quand plus aucune narration n'est à l'écran ni en file, si la plaque est toujours lue — un
 *       événement du serveur ne se manque pas, une plaque se relit. Rien ne branche encore cette lecture sur la
 *       concentration.</li>
 * </ul>
 * Limites : 1 000 caractères (points de code, {@code \n} compris ; {@code \n} = retour à la ligne, {@code \n\n} =
 * paragraphe), caractères de contrôle retirés. Au-delà de {@link Gabarit#lignesMax} lignes (3 pour la narration, 12
 * pour la stèle ; moins si la fenêtre ne les tient pas, {@link Gabarit#lignesParPage}), des <b>pages</b> (découpées à
 * la réception, au gabarit et à l'échelle de la fenêtre, ou à la référence 1080p si les coupes sont figées) : un
 * paragraphe commence toujours une page (la ligne vide ne compte pas dans les lignes de la page) ; dans un paragraphe,
 * le moins de pages possible (une de plus si cela évite une coupe au milieu d'une phrase), pleines de préférence,
 * coupées de préférence après une fin de phrase, sans page finale d'une ligne de un ou deux mots ni page qui commence
 * par une ponctuation fermante. Durée : chaque page a ses fondus (entrée ou fondu de page, sortie ou fondu de page) et
 * une part du reste au prorata de ses caractères ; texte tenu : les premières pages au rythme de la durée automatique,
 * la dernière tenue.
 *
 * <p>Exemple, lot 4 (plaque visée en concentration) :
 * <pre>{@code
 * String id = "plaque:" + pos.asLong();
 * Recit.concentration(true);  // la concentration commence : bords assombris (Gabarit.BORDS_CONCENTRATION)
 * Recit.lire(id, segments);    // la visée commence (concentration active)
 * Recit.lireSuite(id);         // le joueur demande la page suivante (plaque de plus d'une page)
 * Recit.cacher(id);            // la visée ou la concentration finit
 * Recit.concentration(false);  // fin de la concentration : les bords reviennent en 150 ms
 * }</pre>
 */
public final class Recit {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/recit");

    /** {@code s} = 0 : durée automatique, clamp(1,3 s + caractères / 30 ; 4 s ; 60 s) ({@link #dureeAuto}). */
    public static final int AUTO = 0;
    /** {@code s} = -1 : tenu jusqu'à {@link #cacher} (garde-fou client : 10 min pour une narration). */
    public static final int TENIR = -1;
    /** Durée imposée : de 2 à 180 s. */
    public static final int DUREE_MIN = 2, DUREE_MAX = 180;
    /** {@code pos} absent : le centre du gabarit, et les règles du KO et de l'écran noir ({@link RecitHud}). */
    public static final int POS_DEFAUT = -1;
    public static final int MAX_CARACTERES = 1000, MAX_FILE = 8, LIGNES_PAR_PAGE = 12;

    /* temps de la file (brief §2.3, contrat de la narration, ms) ; les fondus sont ceux du gabarit de chaque texte */
    static final long ECART_MS = 400;
    static final long AUTO_BASE_MS = 1_300, AUTO_MIN_MS = 4_000, AUTO_MAX_MS = 60_000, TENIR_MAX_MS = 600_000;
    /** Durée automatique : caractères lus par seconde de tenue (3 lignes pleines ≈ 210 caractères ≈ 7 s). */
    static final int AUTO_CARACTERES_S = 30;

    /** {@code file} : après la narration en cours ; {@code remplace} : coupe la narration en cours et vide la file. */
    public enum Mode { FILE, REMPLACE }

    /** Un morceau de texte et sa clé de couleur ({@code ""}, {@code rouge}, {@code or}, {@code ombre}, {@code gris}). */
    public record Segment(String texte, String couleur) {
        public Segment {
            if (texte == null) texte = "";
            if (couleur == null) couleur = "";
        }
    }

    /** Horloge des minuteurs et des fondus (ms, monotone) ; le banc la fige pour prendre les fondus image par image. */
    public static LongSupplier horloge = Util::getMillis;

    private Recit() {}

    /* ================================================================ une entrée */

    /** Un texte reçu : allure, pages prêtes à poser, durée, place ; minuteur lancé quand il passe à l'écran. */
    static final class Entree {
        final String id;
        final boolean plaque;
        /** Allure : police, tailles, centre, fondus ({@link Gabarit#NARRATION} ou {@link Gabarit#STELE}). */
        final Gabarit gabarit;
        /** Pages aux coupes de ligne figées (retours à la ligne de la référence 1080p, {@link Gabarit#coupesFigees}). */
        final boolean figee;
        /** Durée (ms), ou -1 = tenue. */
        final long duree;
        final int pos, caracteres;
        /** Rattrapage : ms déjà passées (le minuteur part d'autant plus tôt) ; 0 sinon. */
        final long ecoule;
        /** Texte balisé de chaque page (« < » protégés) : la même chaîne à chaque image. */
        final String[] pages;
        /** Caractères de chaque page (prorata de la durée). */
        final int[] parts;
        /** Lignes de chaque page, et la plus haute page ({@link RecitHud} : haut du bloc fixe d'une page à l'autre). */
        final int[] lignes;
        final int lignesMax;
        final long arrivee;
        /**
         * Fin de chaque page depuis le début du minuteur (ms : fondus de la page + part du reste au prorata des caractères ;
         * la dernière = durée ou tenue). Plaque : aucune minuterie ({@link Long#MAX_VALUE}).
         */
        final long[] tranches;
        /** Page qui finit un paragraphe (banc : une page courte n'est permise qu'à la fin d'un paragraphe). */
        boolean[] finParagraphe;
        /** Plaque : page lue ({@link #lireSuite}) et instant où elle l'est devenue. */
        int pageLue;
        long pageLueA = Long.MIN_VALUE / 4;
        /** Première page montrée : celle dont la tranche contient {@link #ecoule} (0 sans rattrapage). */
        final int premiere;
        /** Fin de chaque page (absolue ; la dernière = {@link #fin}). */
        final long[] finPages;
        /** Début du minuteur ({@code depart - ecoule}), passage à l'écran (-1 : pas encore), fin. */
        long debut = -1, depart = -1, fin = Long.MAX_VALUE;
        /** Coupée (remplacée, cachée) : fondu de sortie de {@code coupeMs} depuis l'opacité qu'elle avait à {@code coupeA}. */
        long coupeA, coupeMs;

        /**
         * {@code rythme} : durée qui règle les pages (la durée, ou pour un texte tenu la durée automatique, fondus de
         * page compris).
         */
        Entree(String id, boolean plaque, Gabarit gabarit, boolean figee, long duree, long rythme, int pos, int caracteres,
               long ecoule, String[] pages, int[] parts, int[] lignes, long arrivee) {
            this.id = id;
            this.plaque = plaque;
            this.gabarit = gabarit;
            this.figee = figee;
            this.duree = duree;
            this.pos = pos;
            this.caracteres = caracteres;
            this.ecoule = ecoule;
            this.pages = pages;
            this.parts = parts;
            this.lignes = lignes;
            int lm = 1;
            for (int l : lignes) lm = Math.max(lm, l);
            this.lignesMax = lm;
            this.arrivee = arrivee;
            int n = pages.length;
            this.finPages = new long[n];
            this.tranches = new long[n];
            if (plaque) {   // lecture d'une plaque : aucune minuterie, pages comprises (lireSuite)
                java.util.Arrays.fill(tranches, Long.MAX_VALUE);
                this.premiere = 0;
                return;
            }
            // chaque page : ses fondus (entrée ou fondu de page ; sortie ou fondu de page), puis le reste au prorata
            long fondus = 0;
            for (int k = 0; k < n; k++) fondus += fondus(k);
            double lecture = Math.max(0, rythme - fondus);
            int total = 0;
            for (int p : parts) total += p;
            double cumul = 0;
            for (int k = 0; k < n - 1; k++) {
                cumul += fondus(k) + lecture * parts[k] / Math.max(1, total);
                tranches[k] = Math.round(cumul);
            }
            tranches[n - 1] = duree > 0 ? duree : TENIR_MAX_MS;
            int p = 0;
            while (p < n - 1 && ecoule >= tranches[p]) p++;
            this.premiere = p;
        }

        /** Fondus de la page k : entrée (première) ou fondu de page, puis sortie (dernière) ou fondu de page. */
        private long fondus(int k) {
            return (k == 0 ? gabarit.entreeMs() : gabarit.pageMs()) + (k == pages.length - 1 ? gabarit.sortieMs() : gabarit.pageMs());
        }

        /**
         * Passage à l'écran en {@code t} : minuteur lancé comme s'il était parti {@link #ecoule} ms plus tôt (fin, et fin
         * de chaque page au prorata des caractères).
         */
        void demarrer(long t) {
            depart = t;
            debut = t - ecoule;
            coupeMs = 0;
            int n = pages.length;
            fin = tranches[n - 1] == Long.MAX_VALUE ? Long.MAX_VALUE : debut + tranches[n - 1];
            for (int k = 0; k < n - 1; k++) finPages[k] = tranches[k] == Long.MAX_VALUE ? Long.MAX_VALUE : debut + tranches[k];
            finPages[n - 1] = fin;
        }

        /** Page à l'écran en {@code t} (jusqu'au passage à l'écran : la première montrée). */
        int page(long t) {
            if (plaque) return pageLue;
            if (depart < 0 || t <= depart) return premiere;
            for (int k = premiere; k < finPages.length - 1; k++) if (t < Math.min(finPages[k], fin)) return k;
            return finPages.length - 1;
        }

        long debutPage(int k) {
            if (plaque) return Math.max(debut, pageLueA);
            return k == 0 ? debut : Math.min(finPages[k - 1], fin);
        }

        long finPage(int k) { return Math.min(finPages[k], fin); }

        boolean coupee() { return coupeMs > 0; }
    }

    /* ================================================================ état (fil de rendu) */

    private static final ArrayDeque<Entree> FILE = new ArrayDeque<>(MAX_FILE);
    /** Narration à l'écran (minuteur lancé), y compris pendant son fondu de sortie. */
    private static Entree courante;
    /** Plaque lue (lot 4) : à l'écran quand aucune narration ne l'est. */
    private static Entree lecture;
    private static boolean lectureMontree;
    /** Fin de la dernière narration : la suivante attend 400 ms. */
    private static long finPrecedente = Long.MIN_VALUE / 4;
    /** Concentration (lot 4) : active, instant du dernier changement, opacité des bords à cet instant. */
    private static boolean concentre;
    private static long concentreA = Long.MIN_VALUE / 4;
    private static float bordsA0;

    /* ================================================================ API */

    /**
     * {@link #montrer(String, List, int, Mode, int, long, Gabarit)} au centre par défaut du gabarit ({@link #POS_DEFAUT}),
     * sans rattrapage.
     */
    public static boolean montrer(String id, List<Segment> segments, int secondes, Mode mode, Gabarit gabarit) {
        return montrer(id, segments, secondes, mode, POS_DEFAUT, 0, gabarit);
    }

    /**
     * Montre (ou met en file) un texte, aux règles du message {@code narration}, dans l'allure {@code gabarit} (la
     * narration du staff : {@link Gabarit#NARRATION} ; aucune allure par défaut, l'appelant choisit) : {@code secondes}
     * = {@link #AUTO}, 2 à 180 ou {@link #TENIR} (toute autre valeur : {@link #AUTO}) ; {@code pos} = 0 à 100 (% de la
     * hauteur ; toute autre valeur : {@link #POS_DEFAUT}) ; {@code ecoule} = ms déjà passées (rattrapage), 0 sinon. Faux
     * si rien n'est visible dans le texte, si {@code id} est déjà à l'écran ou en file (doublon) ou si {@code ecoule} ≥ D
     * (10 min pour une tenue) : ignoré.
     */
    public static boolean montrer(String id, List<Segment> segments, int secondes, Mode mode, int pos, long ecoule,
                                  Gabarit gabarit) {
        if (id == null) id = "";
        if (gabarit == null) throw new IllegalArgumentException("texte au centre sans gabarit : " + id);
        long now = horloge.getAsLong();
        avancer(now);
        if (connue(id)) return false;   // doublon
        boolean auto = secondes != TENIR && (secondes < DUREE_MIN || secondes > DUREE_MAX);
        long duree = secondes == TENIR ? -1 : auto ? 0 : secondes * 1000L;   // 0 : automatique, pages comptées
        ecoule = Math.max(0, ecoule);
        Entree e = creer(id, segments, gabarit, duree, pos >= 0 && pos <= 100 ? pos : POS_DEFAUT, false, ecoule, now,
            caracteresRecus(segments));
        if (e == null) return false;
        if (ecoule >= (e.duree > 0 ? e.duree : TENIR_MAX_MS)) return false;
        if (mode == Mode.REMPLACE) {
            // ce qui est à l'écran s'efface (fondu de coupe de son gabarit, 150 ms) : la durée de la nouvelle part du
            // début de son fondu d'entrée
            Entree ecran = courante != null ? courante : lectureMontree ? lecture : null;
            long t = ecran != null ? now + ecran.gabarit.coupeMs() : now;
            if (courante != null) couper(courante, courante.gabarit.coupeMs(), now);
            FILE.clear();
            demarrer(e, t);
            return true;
        }
        if (FILE.size() >= MAX_FILE) {
            Entree sortie = FILE.pollFirst();
            LOGGER.warn("texte au centre : file pleine ({} en attente), « {} » retiré pour « {} »", MAX_FILE, sortie.id, e.id);
        }
        FILE.addLast(e);
        avancer(now);
        return true;
    }

    /**
     * Fin d'un texte : affiché → fondu de sortie de son gabarit (narration 700 ms, stèle 350 ms) ; en attente → retiré ;
     * inconnu → rien ; id vide
     * → {@link #toutCacher}.
     */
    public static void cacher(String id) {
        if (id == null || id.isEmpty()) {
            toutCacher();
            return;
        }
        long now = horloge.getAsLong();
        avancer(now);
        if (lecture != null && lecture.id.equals(id)) {
            couper(lecture, lecture.gabarit.sortieMs(), now);
            lecture = null;
            lectureMontree = false;
        }
        if (courante != null && courante.id.equals(id)) couper(courante, courante.gabarit.sortieMs(), now);
        for (Iterator<Entree> it = FILE.iterator(); it.hasNext(); ) if (it.next().id.equals(id)) it.remove();
    }

    /** Écran et file des narrations vidés (fondu de sortie du gabarit, 700 ms) ; la lecture d'une plaque continue. */
    public static void toutCacher() {
        long now = horloge.getAsLong();
        avancer(now);
        if (courante != null) couper(courante, courante.gabarit.sortieMs(), now);
        FILE.clear();
    }

    /**
     * Lecture d'une stèle ou d'une plaque (lot 4, {@link Gabarit#STELE}) : tenue jusqu'à {@link #cacher}{@code (id)}.
     * Une autre plaque déjà lue est remplacée (fondu de coupe, 150 ms) ; même {@code id} : rien ne change. Entrée en
     * 250 ms, sortie en 350 ms ({@link Gabarit#STELE}, vidéos de Ketto), aucune minuterie : le texte reste tant que la
     * concentration dure ; masqué sous une narration. Faux si le texte est vide.
     */
    public static boolean lire(String id, List<Segment> segments) { return lire(id, segments, false); }

    /**
     * Comme {@link #lire(String, List)} ; {@code remplacer} : même {@code id} déjà lu, le texte est quand même remplacé
     * (une révision neuve de la plaque, corrigée par le staff pendant qu'on la lit : fondu de coupe, retour en page 1).
     */
    public static boolean lire(String id, List<Segment> segments, boolean remplacer) {
        if (id == null) id = "";
        if (lecture != null && lecture.id.equals(id) && !remplacer) return true;
        long now = horloge.getAsLong();
        avancer(now);
        Entree e = creer(id, segments, Gabarit.STELE, -1, POS_DEFAUT, true, 0, now, -1);
        if (e == null) return false;
        if (lecture != null) couper(lecture, lecture.gabarit.coupeMs(), now);
        lecture = e;
        lectureMontree = false;
        avancer(now);
        return true;
    }

    /**
     * Plaque lue {@code id} (lot 4) : page suivante, puis retour à la première après la dernière (fondu de page du
     * gabarit). Aucune minuterie ne tourne les pages d'une plaque : c'est le joueur qui lit. Faux si cette plaque n'est
     * pas lue, n'a qu'une page ou est masquée sous une narration.
     */
    public static boolean lireSuite(String id) {
        if (lecture == null || id == null || !lecture.id.equals(id) || lecture.pages.length < 2) return false;
        // masquée sous une narration du staff : le clic ne tourne rien d'invisible (la plaque revient à sa page)
        avancer(horloge.getAsLong());
        if (!lectureMontree) return false;
        lecture.pageLue = (lecture.pageLue + 1) % lecture.pages.length;
        lecture.pageLueA = horloge.getAsLong();
        return true;
    }

    /**
     * Concentration (lot 4, pas encore branchée) : {@code true} au début, {@code false} à la fin. Tant qu'elle dure, les
     * bords de l'écran s'assombrissent légèrement ({@link Gabarit#BORDS_CONCENTRATION}, fondus de 250 ms à l'entrée et
     * de 150 ms à la sortie, repartant de l'opacité du moment) ; indépendant du texte lu ({@link #lire}), qui suit la
     * visée. Sans effet si l'état ne change pas.
     */
    public static void concentration(boolean active) {
        if (active == concentre) return;
        long now = horloge.getAsLong();
        bordsA0 = bords(now);
        concentre = active;
        concentreA = now;
    }

    /** Concentration en cours ({@link #concentration}). */
    public static boolean concentre() { return concentre; }

    /**
     * Opacité de l'assombrissement des bords en {@code t} (0 à 1 ; × {@link Gabarit#BORDS_CONCENTRATION} au dessin) :
     * rampe linéaire depuis l'opacité du dernier changement.
     */
    static float bords(long t) {
        long d = Math.max(0, t - concentreA);
        if (concentre) return Math.min(1f, bordsA0 + (1f - bordsA0) * d / (float) Gabarit.BORDS_ENTREE_MS);
        return Math.max(0f, bordsA0 - bordsA0 * d / (float) Gabarit.BORDS_SORTIE_MS);
    }

    /** Déconnexion : tout oublié tout de suite (sans fondu), textures libérées. */
    public static void vider() {
        FILE.clear();
        courante = null;
        lecture = null;
        lectureMontree = false;
        concentre = false;
        bordsA0 = 0f;
        concentreA = Long.MIN_VALUE / 4;
        finPrecedente = Long.MIN_VALUE / 4;
        RecitHud.oublier();
    }

    /** Un texte est à l'écran, en fondu, en attente ou lu. */
    public static boolean actif() { return courante != null || lecture != null || !FILE.isEmpty(); }

    /** Narrations en attente. */
    public static int enAttente() { return FILE.size(); }

    /** {@code id} à l'écran (fondu de sortie compris) ou en file. */
    private static boolean connue(String id) {
        if (courante != null && courante.id.equals(id)) return true;
        for (Entree x : FILE) if (x.id.equals(id)) return true;
        return false;
    }

    /**
     * Banc : l'état en une ligne — narration à l'écran (durée, temps restant, page, coupée), file d'attente, plaque lue.
     * Alloue (journal seulement).
     */
    public static String bilan() {
        long now = horloge.getAsLong();
        StringBuilder s = new StringBuilder(128);
        Entree c = courante;
        if (c == null) s.append("écran libre");
        else {
            s.append("à l'écran « ").append(c.id).append(" » (").append(c.gabarit.nom()).append(") : ")
                .append(c.duree < 0 ? "tenue" : c.duree + " ms");
            if (c.ecoule > 0) s.append(", rattrapage ").append(c.ecoule).append(" ms");
            s.append(", reste ").append(c.fin == Long.MAX_VALUE ? "∞" : (c.fin - now) + " ms").append(", page ")
                .append(c.page(now) + 1).append('/').append(c.pages.length).append(", ").append(c.caracteres)
                .append(" caractères").append(c.pos >= 0 ? ", pos " + c.pos : "").append(c.coupee() ? ", coupée" : "");
        }
        s.append(" ; file [");
        int i = 0;
        for (Entree x : FILE) s.append(i++ > 0 ? ", " : "").append(x.id);
        s.append(']');
        if (lecture != null) s.append(" ; plaque « ").append(lecture.id).append(lectureMontree ? " » montrée" : " » derrière");
        return s.toString();
    }

    /** Banc : durée de l'entrée {@code id} (à l'écran ou en file) en ms, -1 = tenue ; {@code Long.MIN_VALUE} si inconnue. */
    public static long debugDuree(String id) {
        if (courante != null && courante.id.equals(id)) return courante.duree;
        for (Entree x : FILE) if (x.id.equals(id)) return x.duree;
        return Long.MIN_VALUE;
    }

    /** Banc : {@code pos} de l'entrée {@code id} ({@link #POS_DEFAUT} : place par défaut) ; {@code Integer.MIN_VALUE} si inconnue. */
    public static int debugPos(String id) {
        if (courante != null && courante.id.equals(id)) return courante.pos;
        for (Entree x : FILE) if (x.id.equals(id)) return x.pos;
        return Integer.MIN_VALUE;
    }

    /** Banc : gabarit de l'entrée {@code id} (à l'écran, en file ou plaque lue), sinon null. */
    public static Gabarit debugGabarit(String id) {
        if (courante != null && courante.id.equals(id)) return courante.gabarit;
        for (Entree x : FILE) if (x.id.equals(id)) return x.gabarit;
        return lecture != null && lecture.id.equals(id) ? lecture.gabarit : null;
    }

    /** Banc : nombre de pages de l'entrée {@code id} (à l'écran ou en file), 0 si inconnue. */
    public static int debugPages(String id) {
        if (courante != null && courante.id.equals(id)) return courante.pages.length;
        for (Entree x : FILE) if (x.id.equals(id)) return x.pages.length;
        return 0;
    }

    /**
     * Banc : fin de chaque page depuis le début du minuteur (ms, prorata des caractères ; la dernière = la durée) de
     * l'entrée {@code id} à l'écran ou en file, et sa première page montrée en dernier élément ; null si inconnue.
     */
    public static long[] debugTranches(String id) {
        Entree e = courante != null && courante.id.equals(id) ? courante : null;
        if (e == null) for (Entree x : FILE) if (x.id.equals(id)) e = x;
        if (e == null) return null;
        long[] t = java.util.Arrays.copyOf(e.tranches, e.tranches.length + 1);
        t[e.tranches.length] = e.premiere;
        return t;
    }

    /** Banc : page {@code k} de l'entrée {@code id} (texte balisé tel que posé), null si inconnue. */
    public static String debugPage(String id, int k) {
        Entree e = courante != null && courante.id.equals(id) ? courante : null;
        if (e == null) for (Entree x : FILE) if (x.id.equals(id)) e = x;
        return e == null || k < 0 || k >= e.pages.length ? null : e.pages[k];
    }

    /** Banc : la page {@code k} de l'entrée {@code id} finit un paragraphe (ou le texte). */
    public static boolean debugFinParagraphe(String id, int k) {
        Entree e = courante != null && courante.id.equals(id) ? courante : null;
        if (e == null) for (Entree x : FILE) if (x.id.equals(id)) e = x;
        return e != null && k >= 0 && k < e.finParagraphe.length && e.finParagraphe[k];
    }

    /** Banc : l'id à l'écran (fondu de sortie compris), sinon "". */
    public static String debugEcran() { return courante == null ? "" : courante.id; }

    /** Banc : ids en file, séparés par des virgules. */
    public static String debugFile() {
        StringBuilder s = new StringBuilder();
        for (Entree x : FILE) s.append(s.length() > 0 ? "," : "").append(x.id);
        return s.toString();
    }

    /** Banc : temps restant (ms) de la narration à l'écran ; {@code Long.MIN_VALUE} si aucune. */
    public static long debugReste() {
        return courante == null ? Long.MIN_VALUE : courante.fin == Long.MAX_VALUE ? Long.MAX_VALUE
            : courante.fin - horloge.getAsLong();
    }

    /** Banc : page à l'écran (0 = la première) de la narration en cours ; -1 si aucune. */
    public static int debugPage() { return courante == null ? -1 : courante.page(horloge.getAsLong()); }

    /* ================================================================ minuteurs */

    /** Fait avancer les minuteurs jusqu'à {@code now} (tick du client et chaque image : même masqué, le temps court). */
    static void avancer(long now) {
        Entree c = courante;
        if (c != null && now >= c.fin) {
            finPrecedente = c.fin;
            courante = null;
        }
        if (courante == null && !FILE.isEmpty()) {
            long depart = Math.max(finPrecedente + ECART_MS, FILE.peekFirst().arrivee);
            if (now >= depart) demarrer(FILE.pollFirst(), depart);
        }
        // la plaque attend que plus aucune narration ne soit à l'écran ni en file (pas de retour de 400 ms entre deux)
        boolean montree = courante == null && FILE.isEmpty() && lecture != null;
        if (montree && !lectureMontree) lecture.demarrer(now);   // la plaque revient, à la page où elle était
        lectureMontree = montree;
    }

    /** Ce qui doit être à l'écran : la narration en cours (même en fondu de sortie), sinon la plaque lue. */
    static Entree voulue() { return courante != null ? courante : lecture; }

    /** Ce qui viendra après la narration en cours : la file, puis la plaque lue (page préparée d'avance). */
    static Entree suivante() {
        Entree e = FILE.peekFirst();
        if (e != null) return e;
        return courante != null ? lecture : null;
    }

    private static void demarrer(Entree e, long t) {
        courante = e;
        e.demarrer(t);
    }

    /**
     * Coupe : la fin avance à {@code now + ms} (fondu de sortie ou de coupe du gabarit de l'entrée), le fondu part de
     * l'opacité du moment ({@link RecitHud}).
     */
    private static void couper(Entree e, long ms, long now) {
        if (e == null) return;
        if (!e.coupee() || now + ms < e.fin) {
            e.coupeA = now;
            e.coupeMs = ms;
        }
        e.fin = Math.min(e.fin, now + ms);
    }

    /**
     * Durée automatique de base (ms) d'un texte <b>tel qu'il partira</b>, sans les fondus de page : n = points de code
     * des segments, {@code \n} compris (le serveur fait le même calcul, {@code Narrations.dureeAutoMs}).
     */
    public static long dureeAutoMs(List<Segment> segments) { return dureeAuto(caracteresRecus(segments)); }

    private static List<Segment> dureeDe;
    private static Gabarit dureeG;
    private static long dureeVal;

    /**
     * Durée automatique réelle (ms) d'un texte à l'allure {@code gabarit} : la base, plus 1,2 s par page au-delà de la
     * première (deux fondus de page) — ce que le joueur verra (l'outil « Narration » du Poste de garde l'affiche à côté
     * de « Auto »). La mise en page est refaite seulement quand {@code segments} change (comparé par référence).
     */
    public static long dureeAutoMs(List<Segment> segments, Gabarit gabarit) {
        if (segments == dureeDe && gabarit == dureeG) return dureeVal;
        Entree e = segments == null || segments.isEmpty() ? null : apercu(segments, gabarit);
        dureeDe = segments;
        dureeG = gabarit;
        dureeVal = e != null ? e.duree : dureeAutoMs(segments);
        return dureeVal;
    }

    /**
     * Aperçu (outil « Narration » du Poste de garde, {@link RecitHud.Apercu}) : l'entrée que le joueur recevrait, mêmes
     * pages et mêmes coupes, sans passer à l'écran ni dans la file. Null si rien n'est visible.
     */
    static Entree apercu(List<Segment> segments, Gabarit gabarit) {
        return creer("apercu", segments, gabarit, 0, POS_DEFAUT, false, 0, horloge.getAsLong(), caracteresRecus(segments));
    }

    /**
     * Allongement de la durée automatique d'un texte de {@code pages} pages : deux fondus de page par page au-delà de la
     * première (la règle « 1,3 s + n / 30 » ne compte qu'une entrée et une sortie), pour que chaque page garde sa tenue.
     */
    static long fondusDePages(Gabarit g, int pages) { return Math.max(0, pages - 1) * 2L * g.pageMs(); }

    /**
     * Durée automatique (ms) : clamp(1 300 + n × 1 000 / 30 ; 4 000 ; 60 000), division entière, comme le serveur
     * ({@code Narrations.dureeAutoMs}). Règle du 02/11, calée sur les vidéos de Ketto : 3 lignes pleines de la
     * narration (≈ 210 caractères en Philosopher Bold, 1080p) tenues ≈ 7 s, plus les fondus d'entrée (0,6 s) et de
     * sortie (0,7 s) — 30 caractères par seconde de tenue (Ketto : ≈ 240 caractères en 7,5 s).
     */
    static long dureeAuto(int caracteres) {
        return Math.max(AUTO_MIN_MS, Math.min(AUTO_MAX_MS, AUTO_BASE_MS + caracteres * 1000L / AUTO_CARACTERES_S));
    }

    /** n de la durée automatique : points de code des segments <b>tels que reçus</b> ({@code \n} compris, avant la typographie). */
    static int caracteresRecus(List<Segment> segments) {
        long n = 0;
        if (segments != null) {
            for (Segment s : segments) if (s != null) n += s.texte().codePointCount(0, s.texte().length());
        }
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    /* ================================================================ texte : segments, limite, pages */

    /** Clé du contrat ({@code rouge}, {@code or}, {@code ombre}, {@code gris}) ; toute autre clé = blanc ({@code ""}). */
    static String cle(String c) {
        return switch (c) {
            case "rouge", "or", "ombre", "gris" -> c;
            default -> "";
        };
    }

    /**
     * Entrée prête : segments nettoyés et bornés à 1 000 caractères, posés en texte balisé (« < » protégés), découpés en
     * pages de {@link Gabarit#lignesMax} lignes au plus ({@link #paginer}) au gabarit et à l'échelle de la fenêtre (ou à
     * la référence 1080p). {@code duree} : ms, -1 = tenue, 0 = automatique ({@code nAuto} : caractères reçus, sinon ceux
     * du texte nettoyé ; plus les fondus des pages en plus, {@link #fondusDePages}). Null si rien n'est visible.
     */
    private static Entree creer(String id, List<Segment> segments, Gabarit gabarit, long duree, int pos, boolean plaque,
                                long ecoule, long now, int nAuto) {
        StringBuilder balise = new StringBuilder(256);
        int reste = MAX_CARACTERES;
        boolean visible = false;
        if (segments != null) {
            for (Segment s : segments) {
                if (s == null || reste <= 0) continue;
                String t = nettoyer(s.texte());
                int n = t.codePointCount(0, t.length());
                if (n > reste) {
                    t = t.substring(0, t.offsetByCodePoints(0, reste));
                    n = reste;
                }
                if (n == 0) continue;
                reste -= n;
                visible |= !blanc(t);
                String c = cle(s.couleur());
                String lit = TexteRaster.litteral(t);
                if (c.isEmpty()) balise.append(lit);
                else balise.append('<').append(c).append('>').append(lit).append("</>");
            }
        }
        if (!visible) return null;
        int caracteres = MAX_CARACTERES - reste;
        String texte = balise.toString();
        String[] pages = {texte};
        int[] parts = {caracteres};
        // coupes figées : mise en page une fois à la référence 1920 × 1080 (f = 1), quelle que soit la fenêtre
        boolean fige = gabarit.coupesFigees();
        var win = Minecraft.getInstance().getWindow();
        int W = fige ? Gabarit.REF_W : Math.max(1, win.getWidth()), H = fige ? Gabarit.REF_H : Math.max(1, win.getHeight());
        double f = fige ? 1.0 : gabarit.facteur(W, H);
        int parPage = gabarit.lignesParPage(H, gabarit.pas(f));
        Mise m = miseEnPage(texte, gabarit, f, W);
        if (fige && gabarit.coupesDeSens() && m != null) {
            Mise sens = coupesDeSens(texte, gabarit, f, W, m.nbLignes);
            if (sens != null) m = sens;
        }
        boolean figee = fige && m != null;
        int[] lignes = {m != null ? m.nbLignes : 1};
        boolean[] finPar = {true};
        List<Mise> parSens = m != null && m.nbLignes > parPage && figee && gabarit.coupesDeSens()
            ? pagesDeSens(texte, gabarit, f, W, parPage) : null;
        if (parSens != null) {
            // narration : pages refaites phrase par phrase (chaque page a ses propres coupes de sens)
            int np = parSens.size() / 2;
            pages = new String[np];
            parts = new int[np];
            lignes = new int[np];
            finPar = new boolean[np];
            for (int k = 0; k < np; k++) {
                Mise mp = parSens.get(2 * k);
                pages[k] = lignesFigees(mp, 0, mp.nbLignes);
                parts[k] = Math.max(1, mp.nbCaracteres);
                lignes[k] = mp.nbLignes;
                finPar[k] = parSens.get(2 * k + 1) != null;
            }
        } else if (m != null && m.nbLignes > parPage) {
            List<int[]> bornes = paginer(m, parPage);
            ArrayList<String> ps = new ArrayList<>(bornes.size());
            ArrayList<Integer> cs = new ArrayList<>(bornes.size());
            ArrayList<Integer> ls = new ArrayList<>(bornes.size());
            boolean[] fp = new boolean[bornes.size()];
            int ip = 0;
            for (int[] p : bornes) {
                int a = p[0], b = p[1];
                fp[ip++] = b >= m.nbLignes || vide(m, b);
                ps.add(figee ? lignesFigees(m, a, b) : m.texteBalise(a, b));
                cs.add(Math.max(1, m.fin[b - 1] - m.debut[a]));
                ls.add(b - a);
            }
            if (!ps.isEmpty()) {
                pages = ps.toArray(new String[0]);
                parts = new int[cs.size()];
                lignes = new int[ls.size()];
                finPar = fp;
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = cs.get(i);
                    lignes[i] = ls.get(i);
                }
            }
        } else if (figee) {
            pages = new String[] {lignesFigees(m, 0, m.nbLignes)};
        }
        // durée automatique : base sur le texte reçu (nAuto, comme le serveur), plus les fondus des pages en plus
        long auto = dureeAuto(nAuto >= 0 ? nAuto : caracteres) + fondusDePages(gabarit, pages.length);
        long d = duree == 0 ? auto : duree;
        Entree e = new Entree(id == null ? "" : id, plaque, gabarit, figee, d, d > 0 ? d : auto, pos, caracteres, ecoule,
            pages, parts, lignes, now);
        e.finParagraphe = finPar;
        return e;
    }

    /** Fins de sens : une ligne se coupe de préférence après l'un d'eux suivi d'une espace (planche d1). */
    private static final String FINS_DE_SENS = ".!?:;…";

    /**
     * Coupes « de sens » (planche d1, {@code coupes_de_sens}), à la référence : chaque paragraphe est découpé après
     * . ! ? : ; … suivis d'une espace, les segments voisins sont regroupés tant que la ligne tient dans L, un segment
     * plus large que L est coupé par le moteur (coupe et équilibrage habituels). Mise en page des lignes obtenues (sans
     * limite de largeur), ou null si elles ajoutent plus d'une ligne à la coupe ordinaire ({@code ordinaires}).
     */
    private static Mise coupesDeSens(String balise, Gabarit g, double f, int W, int ordinaires) {
        double L = g.largeur(f, W);
        StringBuilder sortie = new StringBuilder(balise.length() + 32);
        int nLignes = 0;
        try {
            for (String par : balise.split("\n", -1)) {
                if (nLignes > 0) sortie.append('\n');
                if (par.isEmpty()) {   // ligne vide (paragraphe)
                    nLignes++;
                    continue;
                }
                List<String> lignes = new ArrayList<>();
                String cour = null;
                for (String seg : segmentsDeSens(par)) {
                    if (cour != null && largeurSeule(cour + " " + seg, g, f) <= L + 1e-9) {
                        cour = cour + " " + seg;
                        continue;
                    }
                    if (cour != null) lignes.add(cour);
                    cour = seg;
                }
                if (cour != null) lignes.add(cour);
                boolean premiere = true;
                for (String l : lignes) {
                    if (largeurSeule(l, g, f) <= L + 1e-9) {
                        if (!premiere) sortie.append('\n');
                        sortie.append(l);
                        nLignes++;
                    } else {   // segment plus large que L : coupe ordinaire du moteur
                        Mise ml = TexteRaster.mettreEnPage(g.bloc(l, f, W, 0.0, 0.0));
                        for (int i = 0; i < ml.nbLignes; i++) {
                            if (!premiere || i > 0) sortie.append('\n');
                            sortie.append(ml.texteBalise(i, i + 1));
                            nLignes++;
                        }
                    }
                    premiere = false;
                }
            }
            if (nLignes > ordinaires + 1) return null;
            return TexteRaster.mettreEnPage(TexteRaster.Demande.bloc(sortie.toString(), g.police(), g.preset(), g.cap(f),
                TexteRaster.SANS_LIMITE, g.pas(f), g.contour(f), 0.0, 0.0, g.approche(f), TexteRaster.Equilibrage.JAMAIS,
                null));
        } catch (RuntimeException ex) {
            LOGGER.warn("texte au centre : coupes de sens impossibles ({}), coupe ordinaire", ex.toString());
            return null;
        }
    }

    /** Largeur d'une seule ligne (sans coupe), au gabarit et au facteur f. */
    private static double largeurSeule(String balise, Gabarit g, double f) {
        Mise m = TexteRaster.mettreEnPage(TexteRaster.Demande.bloc(balise, g.police(), g.preset(), g.cap(f),
            TexteRaster.SANS_LIMITE, g.pas(f), g.contour(f), 0.0, 0.0, g.approche(f), TexteRaster.Equilibrage.JAMAIS, null));
        return m.largeurBloc;
    }

    /**
     * Un paragraphe balisé découpé après chaque fin de sens suivie d'une espace (l'espace saute) ; une couleur ouverte
     * est refermée en fin de segment et rouverte au début du suivant.
     */
    private static List<String> segmentsDeSens(String par) {
        List<String> segs = new ArrayList<>();
        StringBuilder cour = new StringBuilder();
        String ouverte = null;   // clé de couleur en cours
        for (int i = 0; i < par.length(); i++) {
            char c = par.charAt(i);
            if (c == '<') {   // balise du texte (un « < » littéral est protégé : TexteRaster.INFERIEUR_LITTERAL)
                int fin = par.indexOf('>', i);
                if (fin > i) {
                    String b = par.substring(i, fin + 1);
                    ouverte = b.startsWith("</") ? null : b.substring(1, b.length() - 1);
                    cour.append(b);
                    i = fin;
                    continue;
                }
            }
            cour.append(c);
            if (FINS_DE_SENS.indexOf(c) >= 0 && i + 1 < par.length() && par.charAt(i + 1) == ' ') {
                if (ouverte != null) cour.append("</>");
                String s = cour.toString().strip();
                if (!s.isEmpty()) segs.add(s);
                cour.setLength(0);
                if (ouverte != null) cour.append('<').append(ouverte).append('>');
                i++;   // l'espace qui suit
            }
        }
        String s = cour.toString().strip();
        if (!s.isEmpty() && !s.equals(ouverte == null ? "" : "<" + ouverte + ">")) segs.add(s);
        return segs;
    }

    /**
     * Lignes [a, b) de la mise en page, chacune balisée, jointes par des retours à la ligne : posées sans limite de
     * largeur ni équilibrage, elles gardent exactement ces coupes à toute taille de fenêtre (planche d3).
     */
    private static String lignesFigees(Mise m, int a, int b) {
        StringBuilder s = new StringBuilder(256);
        for (int i = a; i < b; i++) {
            if (i > a) s.append('\n');
            s.append(m.texteBalise(i, i + 1));
        }
        return s.toString();
    }

    /**
     * Pages d'une narration aux coupes de sens (gabarit à coupes figées et coupes de sens), <b>phrase par phrase</b> :
     * chaque paragraphe ({@code \n\n}) commence une page ; ses phrases (coupées après . ! ? : ; … suivis d'une espace)
     * sont regroupées en pages dont la mise en page (coupes de sens refaites pour la page seule) tient en
     * {@code parPage} lignes, au moindre coût (programmation dynamique : {@link #PAGE} par page, {@link #MANQUE} par
     * ligne manquante hors dernière page du paragraphe, coupe après : ou ; {@link #COUPE_SENS}, dernière page d'une
     * ligne de un ou deux mots {@link #ORPHELINE}, page qui commence par une ponctuation fermante {@link #FERMANTE}).
     * Une phrase qui à elle seule dépasse {@code parPage} lignes est d'abord partagée par lignes
     * ({@link #paginerParagraphe}) : là seulement, une page peut finir en pleine phrase. Résultat : pour chaque page, sa
     * mise en page puis un marqueur (non null = la page finit un paragraphe) ; null si rien n'a pu être mis en page.
     */
    private static List<Mise> pagesDeSens(String texte, Gabarit g, double f, int W, int parPage) {
        try {
            List<Mise> out = new ArrayList<>();
            for (String par : paragraphes(texte)) {
                // phrases, une phrase trop longue partagée en morceaux de parPage lignes au plus
                List<String> ph = new ArrayList<>();
                for (String x : segmentsDeSens(par)) {
                    Mise mx = miseDeSens(x, g, f, W);
                    if (mx.nbLignes <= parPage) {
                        ph.add(x);
                        continue;
                    }
                    List<int[]> morceaux = new ArrayList<>();
                    paginerParagraphe(mx, 0, mx.nbLignes, parPage, morceaux);
                    for (int[] mo : morceaux) ph.add(mx.texteBalise(mo[0], mo[1]).replace('\n', ' '));
                }
                int S = ph.size();
                if (S == 0) continue;
                long inf = Long.MAX_VALUE / 4;
                long[] cout = new long[S + 1];
                int[] prec = new int[S + 1];
                Mise[] choix = new Mise[S + 1];
                java.util.Arrays.fill(cout, inf);
                cout[0] = 0;
                for (int j = 1; j <= S; j++) {
                    for (int i = j - 1; i >= 0; i--) {
                        Mise mg = miseDeSens(joindre(ph, i, j), g, f, W);
                        if (mg.nbLignes > parPage && j - i > 1) break;   // le groupe ne tient plus : plus long encore non plus
                        if (cout[i] >= inf) continue;
                        long c = cout[i] + PAGE;
                        int n = mg.nbLignes;
                        if (j < S) {
                            c += (long) MANQUE * Math.max(0, parPage - n);
                            c += coutCoupe(mg.lignes[n - 1]);
                        } else if (n == 1 && i > 0 && mots(mg.lignes[0]) <= 2) c += ORPHELINE;
                        if (i > 0 && commenceParFermante(mg.lignes[0])) c += FERMANTE;
                        if (c < cout[j]) {
                            cout[j] = c;
                            prec[j] = i;
                            choix[j] = mg;
                        }
                    }
                }
                if (cout[S] >= inf) return null;
                ArrayList<Mise> pagesPar = new ArrayList<>();
                for (int j = S; j > 0; j = prec[j]) pagesPar.add(0, choix[j]);
                for (int k = 0; k < pagesPar.size(); k++) {
                    out.add(pagesPar.get(k));
                    out.add(k == pagesPar.size() - 1 ? pagesPar.get(k) : null);   // marqueur : fin du paragraphe
                }
            }
            return out.isEmpty() ? null : out;
        } catch (RuntimeException ex) {
            LOGGER.warn("texte au centre : pages par phrases impossibles ({}), pages par lignes", ex.toString());
            return null;
        }
    }

    /** Mise en page d'un morceau aux coupes de sens (comme le texte entier : la coupe ordinaire si elles échouent). */
    private static Mise miseDeSens(String balise, Gabarit g, double f, int W) {
        Mise m = TexteRaster.mettreEnPage(g.bloc(balise, f, W, 0.0, 0.0));
        Mise sens = coupesDeSens(balise, g, f, W, m.nbLignes);
        return sens != null ? sens : m;
    }

    private static String joindre(List<String> ph, int i, int j) {
        StringBuilder b = new StringBuilder(256);
        for (int k = i; k < j; k++) b.append(k > i ? " " : "").append(ph.get(k));
        return b.toString();
    }

    /**
     * Paragraphes d'un texte balisé (séparés par une ligne vide, {@code \n\n}), non vides ; une couleur ouverte est
     * refermée en fin de paragraphe et rouverte au début du suivant.
     */
    private static List<String> paragraphes(String balise) {
        List<String> out = new ArrayList<>();
        StringBuilder cour = new StringBuilder();
        String ouverte = null;
        for (int i = 0; i < balise.length(); i++) {
            char c = balise.charAt(i);
            if (c == '<') {
                int fin = balise.indexOf('>', i);
                if (fin > i) {
                    String b = balise.substring(i, fin + 1);
                    ouverte = b.startsWith("</") ? null : b.substring(1, b.length() - 1);
                    cour.append(b);
                    i = fin;
                    continue;
                }
            }
            if (c == '\n' && i + 1 < balise.length() && balise.charAt(i + 1) == '\n') {
                if (ouverte != null) cour.append("</>");
                String x = cour.toString().strip();
                if (!x.isEmpty() && !x.equals("<" + ouverte + "></>")) out.add(x);
                cour.setLength(0);
                if (ouverte != null) cour.append('<').append(ouverte).append('>');
                while (i + 1 < balise.length() && balise.charAt(i + 1) == '\n') i++;
                continue;
            }
            cour.append(c);
        }
        String x = cour.toString().strip();
        if (!x.isEmpty() && !x.equals(ouverte == null ? "" : "<" + ouverte + ">")) out.add(x);
        return out;
    }

    /* coûts de la pagination (une page de plus coûte PAGE : on n'en ajoute que pour éviter une coupe en pleine phrase) */
    private static final int PAGE = 5, MANQUE = 3, COUPE_SENS = 2, COUPE_VIRGULE = 6, COUPE_PHRASE = 12, ORPHELINE = 10,
        FERMANTE = 8;

    /**
     * Pages d'un texte mis en page (lignes [a, b) de chaque page, sans ligne vide) : un paragraphe (lignes séparées par
     * une ligne vide) commence toujours une page et la ligne vide ne compte pas ; un paragraphe de plus de
     * {@code parPage} lignes est partagé au moindre coût ({@link #paginerParagraphe}).
     */
    static List<int[]> paginer(Mise m, int parPage) {
        List<int[]> out = new ArrayList<>();
        int n = m.nbLignes, i = 0;
        while (i < n) {
            while (i < n && vide(m, i)) i++;
            if (i >= n) break;
            int s = i;
            while (i < n && !vide(m, i)) i++;
            paginerParagraphe(m, s, i, Math.max(1, parPage), out);
        }
        return out;
    }

    /**
     * Un paragraphe [s, e) en pages de {@code parPage} lignes au plus, programmation dynamique sur le nombre de pages
     * (le minimum, ou une de plus) : chaque page coûte {@link #PAGE}, plus {@link #MANQUE} par ligne manquante si ce
     * n'est pas la dernière du paragraphe ; une coupe coûte 0 après une fin de phrase (. ! ? …), {@link #COUPE_SENS}
     * après : ou ;, {@link #COUPE_VIRGULE} après une virgule, {@link #COUPE_PHRASE} en pleine phrase ; une dernière page
     * d'une seule ligne de un ou deux mots coûte {@link #ORPHELINE}, une page qui commence par une ponctuation fermante
     * (» ) , . ; : ! ? …) {@link #FERMANTE}. À coût égal, le moins de pages.
     */
    private static void paginerParagraphe(Mise m, int s, int e, int parPage, List<int[]> out) {
        int t = e - s;
        if (t <= parPage) {
            out.add(new int[] {s, e});
            return;
        }
        int minP = (t + parPage - 1) / parPage, maxP = Math.min(t, minP + 1);
        long inf = Long.MAX_VALUE / 4;
        long[][] cout = new long[maxP + 1][t + 1];
        int[][] prec = new int[maxP + 1][t + 1];
        for (long[] l : cout) java.util.Arrays.fill(l, inf);
        cout[0][0] = 0;
        for (int j = 1; j <= maxP; j++) {
            for (int i = 1; i <= t; i++) {
                for (int a = Math.max(0, i - parPage); a < i; a++) {
                    if (cout[j - 1][a] >= inf) continue;
                    long c = cout[j - 1][a] + coutPage(m, s + a, s + i, e, t, parPage);
                    if (c < cout[j][i]) {
                        cout[j][i] = c;
                        prec[j][i] = a;
                    }
                }
            }
        }
        int best = -1;
        for (int j = minP; j <= maxP; j++) if (cout[j][t] < inf && (best < 0 || cout[j][t] < cout[best][t])) best = j;
        if (best < 0) {   // impossible en pratique : pages pleines
            for (int a = s; a < e; a += parPage) out.add(new int[] {a, Math.min(e, a + parPage)});
            return;
        }
        int[][] pages = new int[best][];
        for (int j = best, i = t; j > 0; j--) {
            int a = prec[j][i];
            pages[j - 1] = new int[] {s + a, s + i};
            i = a;
        }
        for (int[] p : pages) out.add(p);
    }

    /** Coût d'une page [a, b) d'un paragraphe qui finit en e (t lignes). */
    private static long coutPage(Mise m, int a, int b, int e, int t, int parPage) {
        long c = PAGE;
        if (b < e) {
            c += (long) MANQUE * (parPage - (b - a));
            c += coutCoupe(m.lignes[b - 1]);
        } else if (b - a == 1 && t > 1 && mots(m.lignes[a]) <= 2) {
            c += ORPHELINE;
        }
        if (commenceParFermante(m.lignes[a]) && a > 0) c += FERMANTE;
        return c;
    }

    private static int coutCoupe(String ligne) {
        int i = ligne.length() - 1;
        while (i >= 0 && (ligne.charAt(i) == '»' || ligne.charAt(i) == ')' || ligne.charAt(i) == '"'
            || Character.isSpaceChar(ligne.charAt(i)))) i--;
        if (i < 0) return 0;
        char c = ligne.charAt(i);
        if (".!?…".indexOf(c) >= 0) return 0;
        if (":;".indexOf(c) >= 0) return COUPE_SENS;
        if (c == ',') return COUPE_VIRGULE;
        return COUPE_PHRASE;
    }

    private static boolean commenceParFermante(String ligne) {
        int i = 0;
        while (i < ligne.length() && Character.isSpaceChar(ligne.charAt(i))) i++;
        return i < ligne.length() && "»),.;:!?…".indexOf(ligne.charAt(i)) >= 0;
    }

    private static int mots(String ligne) {
        int n = 0;
        boolean dans = false;
        for (int i = 0; i < ligne.length(); i++) {
            boolean blanc = Character.isWhitespace(ligne.charAt(i)) || Character.isSpaceChar(ligne.charAt(i));
            if (!blanc && !dans) n++;
            dans = !blanc;
        }
        return n;
    }

    private static boolean vide(Mise m, int i) { return i >= 0 && i < m.nbLignes && m.debut[i] == m.fin[i]; }

    /**
     * Mise en page du texte entier au gabarit, à l'échelle f d'un écran de {@code W} px de large (null tant que la
     * police n'est pas ouverte).
     */
    private static Mise miseEnPage(String texte, Gabarit gabarit, double f, int W) {
        if (!gabarit.police().chargee()) return null;
        try {
            return TexteRaster.mettreEnPage(gabarit.bloc(texte, f, W, 0.0, 0.0));
        } catch (RuntimeException ex) {
            LOGGER.warn("texte au centre : mise en page impossible ({}), une seule page", ex.toString());
            return null;
        }
    }

    /** {@code \r\n} et {@code \r} → {@code \n} ; caractères de contrôle retirés (sauf {@code \n} et la tabulation). */
    static String nettoyer(String t) {
        if (t == null || t.isEmpty()) return "";
        StringBuilder s = null;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            char r = c;
            boolean garder = !Character.isISOControl(c) || c == '\n' || c == '\t';
            if (c == '\r') {
                r = '\n';
                garder = i + 1 >= t.length() || t.charAt(i + 1) != '\n';
            }
            if (c == TexteRaster.INFERIEUR_LITTERAL) garder = false;   // réservé au « < » littéral
            if (garder && r == c && s == null) continue;
            if (s == null) s = new StringBuilder(t.length()).append(t, 0, i);
            if (garder) s.append(r);
        }
        return s == null ? t : s.toString();
    }

    private static boolean blanc(String t) {
        for (int i = 0; i < t.length(); ) {
            int c = t.codePointAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return false;
            i += Character.charCount(c);
        }
        return true;
    }

    private static final Pattern BALISE = Pattern.compile("<(/?)([a-z_]*)>");

    /**
     * Texte écrit avec des balises ({@code <rouge>mort</>} ou {@code </rouge>}, {@code <or>}, {@code <ombre>},
     * {@code <gris>}) → segments ; balise inconnue = texte. Pour les textes écrits côté client (bancs, aperçus, plaques) :
     * la narration arrive déjà découpée par le serveur.
     */
    public static List<Segment> segments(String balise) {
        ArrayList<Segment> out = new ArrayList<>();
        if (balise == null || balise.isEmpty()) return out;
        String c = "";
        int pos = 0;
        StringBuilder t = new StringBuilder();
        Matcher m = BALISE.matcher(balise);
        while (m.find()) {
            t.append(balise, pos, m.start());
            String k = m.group(2);
            boolean connue = !m.group(1).isEmpty() || !cle(k).isEmpty();
            if (!connue) t.append(m.group(0));
            else {
                if (t.length() > 0) out.add(new Segment(t.toString(), c));
                t.setLength(0);
                c = m.group(1).isEmpty() ? k : "";
            }
            pos = m.end();
        }
        t.append(balise, pos, balise.length());
        if (t.length() > 0) out.add(new Segment(t.toString(), c));
        return out;
    }
}
