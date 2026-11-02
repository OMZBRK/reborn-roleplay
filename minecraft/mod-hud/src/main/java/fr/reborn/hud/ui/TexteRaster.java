package fr.reborn.hud.ui;

import org.lwjgl.CLongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_BBox;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_BitmapGlyph;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Matrix;
import org.lwjgl.util.freetype.TT_OS2;
import org.lwjgl.util.freetype.TT_Postscript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.util.freetype.FreeType.*;

/**
 * « TexteRaster v1.1 » : le moteur de texte du lot 2 (« Reprises Ketto », Game Plan n° 17), en Java, identique à la
 * recette de référence des maquettes ({@code Maquettes/gameplan/17_lot2_ketto/_texte_raster.py} : brief §6 et §7.1,
 * LISEZMOI v2 « Recette TexteRaster v1.1 »). Chaque texte est calculé <b>une fois</b> en une couche RGBA, sur le fil
 * {@code Reborn-Texte} ({@link TexteCache}), puis posé à 1:1 sur des pixels entiers en un seul blit.
 *
 * <ol>
 *   <li><b>Typographie française</b> (brief §2.2) : ' → ’, ... → …, "…" → « … », espaces multiples réduites, pas
 *       d'espace avant , . … ), espace fine insécable avant ; : ! ? » et après «, « ~ » = espace insécable, espaces
 *       insécables automatiques après un mot d'une ou deux lettres et entre deux noms propres. Caractère → glyphe par
 *       la cmap ; ni ligatures ni substitutions.</li>
 *   <li><b>em</b> = C × UPM / sCapHeight (OS/2, contrôlée sur le H) ; hauteur d'x calée sur un pixel entier si
 *       l'écart reste dans ±6 %.</li>
 *   <li><b>Avances linéaires</b> non arrondies : avance hmtx (lue sans mise à l'échelle) × em / UPM, à l'em exact —
 *       {@code linearHoriAdvance} serait calculé à l'em arrondi au 1/64 px de {@code FT_Set_Char_Size} et dériverait
 *       d'un dixième de pixel sur une ligne. <b>Crénage</b> par paires (fichier {@code .kern} de
 *       {@code tools/gen_kern.py}, mêmes paires que le Python), recherche dichotomique sans allocation. Espace-mot =
 *       max(avance native, 0,25 em), espace fine = ½ espace-mot.</li>
 *   <li><b>Mise en page</b> : coupe aux espaces sécables ; équilibrage seulement si la dernière ligne fait moins de
 *       45 % de L, jamais sous 94 % de L dès 4 lignes ; origine x flottante, lignes de base entières
 *       {@code haut + arrondi((P + C)/2) + i × P}. Un mot plus large que L (bloc seulement : cri sans espace, adresse)
 *       est coupé entre deux caractères, au plus près de L (contrat de la narration, PLUGINS_REFERENCE §4 bis.3 bis) —
 *       le Python ne coupe pas, mais aucun texte des maquettes n'a de mot plus large que la ligne.</li>
 *   <li><b>Remplissage</b> : couverture à l'origine sous-pixel ({@code FT_Outline_Translate} au 1/16 px, puis
 *       {@code FT_Glyph_To_Bitmap} NORMAL) ; le contour du glyphe est chargé comme dans la recette des maquettes
 *       ({@link #CHARGEMENT}), chaque glyphe est rendu seul à ×{@value #SS} ({@link #SS}) puis réduit à 1× avant le
 *       cumul (somme bornée).</li>
 *   <li><b>Contour</b> rond de rayon r = f px ({@code FT_Glyph_StrokeBorder}, bord extérieur, joints et bouts ronds),
 *       rendu de même à ×{@value #SS} (boucles suréchantillonnées, {@link #CHEVAUCHEMENT}), cumulé glyphe par glyphe
 *       par le maximum, réuni au remplissage.</li>
 *   <li><b>Composition</b> en alpha prémultiplié, stockée en alpha droit : ombre (le contour décalé de
 *       d = max(1, arrondi(f))) à l'encre × opacité d'ombre, contour à l'encre × opacité de contour, puis remplissage à
 *       la couleur du segment, alpha = couverture^γ. Couche = boîte du remplissage + r + d + 2 px.</li>
 * </ol>
 *
 * <p>Les formes du HUD (puce, barres, vumètre, filet, losange, pause, case) passent par {@link #forme}, avec le même
 * contour rond et la même ombre ({@code contour_rond} / {@code poser_forme} du Python).
 *
 * <p><b>Fils.</b> Tout ce qui touche FreeType ({@link #rendre}, {@link #forme} n'en a pas besoin, {@link #face},
 * {@link #fermer}) tourne sur le seul fil {@code Reborn-Texte}, avec la bibliothèque FreeType propre au mod. Une police
 * chargée est immuable : sa mise en page ({@link #mettreEnPage}, {@link #largeur}, {@link #tronquer}) peut se faire
 * depuis n'importe quel fil.
 *
 * <p><b>Contrôle</b> : le banc {@code REBORN_TEXTE_BANC} (cible du brief §6 : écart d'alpha moyen &lt; 1/255, max
 * &lt; 16/255) mesure au 01/11 un écart moyen ≤ 0,07/255 et max ≤ 2/255 sur ses 24 textes, 0 sur ses 8 formes ; mise en
 * page identique (lignes, lignes de base, largeurs, em). Voyelles à macron composées comme le Python (lettre de base +
 * U+00AF ; depuis le 02/11, pour Philosopher Bold qui ne les a pas — Alegreya Sans et Inter ont les vrais glyphes) ; le
 * crénage couvre le répertoire de {@code gen_kern.py} (latin, latin-1, latin étendu A, ponctuation générale).
 */
public final class TexteRaster {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/texte");

    private TexteRaster() {}

    /* ================================================================ réglages de la recette (identiques au Python) */

    /** Espace-mot = max(avance native, 0,25 em). */
    public static final double ESPACE_MIN_EM = 0.25;
    /** Espace fine (U+202F) = ½ espace-mot. */
    public static final double FINE_SUR_ESPACE = 0.5;
    /** Calage de la hauteur d'x sur un pixel entier si l'écart est ≤ 6 %. */
    public static final double TOLERANCE_CALAGE = 0.06;
    /** Équilibrage seulement si la dernière ligne fait moins de 45 % de L… */
    public static final double SEUIL_DERNIERE = 0.45;
    /** … et jamais sous 94 % de L à partir de 4 lignes. */
    public static final double PLANCHER_EQ = 0.94;

    /** Largeur « sans limite » d'une ligne ou d'un bloc aux coupes figées (seuls les retours à la ligne coupent). */
    public static final double SANS_LIMITE = 1e9;

    /*
     * Rastérisation : trois réglages tranchés au banc du 01/11 (REBORN_TEXTE_BANC + tools/texte_banc_compare.py), là où
     * le brief prévoyait « FT_LOAD_NO_HINTING, origine au 1/64 px, rendu direct à 1× ». Écart avec les maquettes :
     *   rendu direct à 1×, sans hinting, origine 1/64   → jusqu'à 68/255 (contours qui se chevauchent) ;
     *   ×4 + boucles suréchantillonnées                 → 17/255 (Inter seulement) ;
     *   + origine au 1/16 px                            → 16/255 (Inter Medium, capitales 10 px) ;
     *   + contours chargés comme la recette (×16)       → 2/255 au plus, moyen ≤ 0,07/255.
     * Le placement exact des avances (mise en page) n'en dépend pas : il reste linéaire et identique au Python.
     */

    /**
     * Suréchantillonnage local d'un glyphe : chaque remplissage et chaque contour est rendu seul à ×SS (contour de
     * rayon SS × r), puis réduit à 1× (moyenne SS × SS) avant le cumul, comme le ×16 du Python. Rendu directement à 1×,
     * FreeType additionne les couvertures des contours qui se chevauchent au lieu d'en prendre l'union : Inter en est
     * plein (1 027 glyphes marqués OVERLAP_SIMPLE), et le bord extérieur d'un contour rond fait des boucles aux coins
     * rentrants (jusqu'à 58/255 d'écart sur un bord).
     */
    static final int SS = 4;

    /**
     * Le contour rond est rendu avec {@code FT_OUTLINE_OVERLAP} : FreeType suréchantillonne encore ×4 ses boucles
     * (×16 en tout), comme il le fait déjà pour les glyphes marqués OVERLAP_SIMPLE.
     */
    static final boolean CHEVAUCHEMENT = true;

    /** Pas de l'origine sous-pixel des glyphes : 1/16 px, celui de la recette des maquettes. Invisible à l'œil. */
    static final int PAS_ORIGINE = 16;

    /**
     * Les contours sont chargés comme dans la recette des maquettes : FreeType, chargement par défaut, à 16 × em (taille
     * tronquée au 1/64 px comme Pillow), puis ramenés à ×{@link #SS} par la transformation de la face. À cette taille,
     * Alegreya Sans coupe elle-même ses instructions (au-delà de 200 px/em) ; Inter, sans instructions de glyphes (son
     * {@code prep} de 7 octets ne règle que les pertes de contour), passe par l'auto-hinter de FreeType, qui cale ses
     * hauteurs et ses fûts sur la grille du ×16 : au plus 1/32 px à 1×, mais 16/255 sur un bord si Java ne fait pas
     * pareil.
     */
    static final int CHARGEMENT = 16;
    static final int CHARGEMENT_FT = FT_LOAD_DEFAULT;

    static final int ESP = ' ', NBSP = 0x00A0, NNBSP = 0x202F, RETOUR = '\n', POINTS = 0x2026, GUIL_O = 0x00AB,
        GUIL_F = 0x00BB, APOSTROPHE = 0x2019;

    /** Arrondi des deux recettes : floor(v + 0,5) des deux côtés (le round de Python arrondit au pair). */
    public static int arrondi(double v) { return (int) Math.floor(v + 0.5); }

    /* ================================================================ couleurs et préréglages (brief §1.2, corrigés) */

    /** Encre du contour et de l'ombre (MeBulles.OMBRE). */
    public static final int ENCRE = 0x0A0608;

    /** Clés de couleur (balises {@code <rouge>…</>} et couleur par défaut), dans l'ordre de {@code COULEURS}. */
    private static final String[] CLES = {"", "blanc", "rouge", "or", "ombre", "ombre_neutre", "ombre_bleute", "gris",
        "hud", "doux", "inconnu"};
    private static final int[] RGB = {0xFFFFFF, 0xFFFFFF, 0xE5483C, 0xF2D08A, 0x8F8B8E, 0x8F8B8E, 0x8FA3C4, 0xC2B59A,
        0xF3F0EA, 0xC2B59A, 0xA8A0A0};

    static int indexCle(String cle) {
        for (int i = 0; i < CLES.length; i++) if (CLES[i].equals(cle)) return i;
        return -1;
    }

    /**
     * Couleur RGB d'une clé ({@code ""} = blanc de la narration, {@code "hud"} = #F3F0EA…), ou d'une couleur écrite
     * {@code "#RRGGBB"} : couleur par défaut d'une ligne seulement (accents et raretés des notifications du bas-droite),
     * pas de balise {@code <#…>}.
     */
    public static int couleur(String cle) {
        int i = indexCle(cle);
        if (i >= 0) return RGB[i];
        int h = hexa(cle);
        return h >= 0 ? h : 0xFFFFFF;
    }

    /** {@code "#RRGGBB"} → RGB ; -1 si ce n'est pas une couleur écrite. */
    static int hexa(String cle) {
        if (cle == null || cle.length() != 7 || cle.charAt(0) != '#') return -1;
        int v = 0;
        for (int i = 1; i < 7; i++) {
            int c = Character.digit(cle.charAt(i), 16);
            if (c < 0) return -1;
            v = v << 4 | c;
        }
        return v;
    }

    /** Clé de couleur écrite d'un RGB ({@code "#RRGGBB"}), à composer une fois (à la réception d'une notification). */
    public static String cleRgb(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /**
     * Préréglage de lisibilité (critiques du 01/11) : opacité du contour, de l'ombre, et gamma du remplissage. Les trois
     * du lot sont ici ; un client peut poser le sien là où il règle le reste de son allure (narration du staff en plein
     * écran : {@code recit/Gabarit}, décision du 02/11). Une seule instance par préréglage : comparé par référence.
     */
    public static final class Preset {
        /** Stèles, plaques et objets lus en concentration (lot 4), maquettes a1 à a8 : contour 100 %, ombre 45 %. */
        public static final Preset RECIT = new Preset("RECIT", 1.00f, 0.45f, 0.85f);
        /** Titre de l'OST, libellés, touches, noms, notifications : contour 80 %, ombre 50 %. */
        public static final Preset HUD = new Preset("HUD", 0.80f, 0.50f, 0.85f);
        /** Formes du HUD (puce, barres, filet…) : comme HUD, remplissage sans gamma. */
        public static final Preset FORME = new Preset("FORME", 0.80f, 0.50f, 1.0f);

        public final String nom;
        public final float contour, ombre, gamma;

        public Preset(String nom, float contour, float ombre, float gamma) {
            this.nom = nom;
            this.contour = contour;
            this.ombre = ombre;
            this.gamma = gamma;
        }

        /** Nom du préréglage (journaux des bancs). */
        public String name() { return nom; }

        @Override
        public String toString() { return nom; }
    }

    public enum Ancre { GAUCHE, CENTRE, DROITE }

    public enum Equilibrage { AUTO, TOUJOURS, JAMAIS }

    /* ================================================================ polices */

    /** Les polices du lot : TTF officiels non modifiés (OFL), chacun avec son {@code .kern}. */
    public enum Police {
        /** Récit : narration, plaques (lot 4), nom de qui parle. */
        RECIT("recit/alegreyasans-bolditalic", "Alegreya Sans Bold Italic"),
        /** Titre de l'OST, libellés, touches, notifications (l'Inter Bold du mod). */
        INTER_GRAS("inter-bold", "Inter Bold"),
        /** Détail des notifications. */
        INTER_MEDIUM("inter-medium", "Inter Medium"),
        /**
         * Narration du staff en plein écran : Philosopher Bold (choix du propriétaire du 02/11, à la place de Marcellus
         * Regular, rejetée : « apparence qui ne corrèle pas avec l'univers »). Sans voyelles à macron précomposées : elles
         * sont composées (lettre de base + U+00AF), comme le moteur des maquettes.
         */
        PHILOSOPHER("narration/philosopher-bold", "Philosopher Bold");

        final String fichier;
        public final String libelle;
        volatile Face face;

        Police(String fichier, String libelle) {
            this.fichier = fichier;
            this.libelle = libelle;
        }

        /** Police chargée (cmap, avances, crénage : lecture libre depuis tout fil). */
        public boolean chargee() { return face != null; }
    }

    /** Données d'une police ouverte. Tout est immuable après le chargement, sauf {@link #bornes} (fil du texte). */
    static final class Face {
        final Police police;
        long ft;                       // FT_Face
        ByteBuffer ttf;                // octets du TTF : vivants tant que la face est ouverte
        int upm;
        double cap, xh;                // unités de police (OS/2, ou bornes du H / du x si l'OS/2 est faux)
        boolean metriquesOs2 = true;   // faux : capitales ou hauteur d'x prises sur le H / le x (OS/2 absent ou faux)
        int[] codes, glyphes;          // cmap triée : point de code -> glyphe
        int[] avances;                 // avance hmtx de chaque glyphe (unités de police)
        int[] kernCles = new int[0];   // (g1 << 16) | g2, triées
        short[] kernValeurs = new short[0];
        String sourceCrenage = "aucun";
        int gEspace = -1, gPoint = -1;
        boolean ellipseCjk;
        /**
         * Voyelles à macron composées (police qui ne les a pas, Philosopher) : glyphe U+00AF (-1 : pas de composition),
         * ses bornes (xMin, yMin, xMax, yMax, unités), l'écart des accents au-dessus de la hauteur d'x et des capitales
         * (mesuré sur é / É) et la pente de l'italique (-tan(italicAngle)), comme {@code Police} du Python.
         */
        int gMacron = -1;
        int[] macronBornes;
        double ecartBas, ecartHaut, pente;
        int[] bornes;                  // yMin, yMax exacts par glyphe (unités), lus à la demande ; MIN_VALUE = à lire

        Face(Police police) { this.police = police; }

        /** Glyphe d'un point de code, ou -1 s'il manque. */
        int glyphe(int cp) {
            int i = Arrays.binarySearch(codes, cp);
            return i >= 0 ? glyphes[i] : -1;
        }

        /** Crénage de la paire, en unités de police (0 sans paire). */
        int crenage(int g1, int g2) {
            if (g1 < 0 || g2 < 0 || g1 > 0x7FFF || g2 > 0xFFFF) return 0;
            int i = Arrays.binarySearch(kernCles, (g1 << 16) | g2);
            return i >= 0 ? kernValeurs[i] : 0;
        }

        int avance(int g) { return g >= 0 && g < avances.length ? avances[g] : 0; }
    }

    /* ================================================================ état natif (fil Reborn-Texte seulement) */

    private static long bibliotheque;   // FT_Library propre au mod (jamais celle de Minecraft)
    private static long traceur;        // FT_Stroker

    /** Compteurs d'objets natifs et de rendus (banc) : un seul écrivain, le fil du texte. */
    public static final class Compteurs {
        public volatile long bibliotheques, bibliothequesFermees, faces, facesFermees, traceurs, traceursFermes,
            glyphes, glyphesLiberes, rendus, formes, erreursFt;

        @Override
        public String toString() {
            return "bibliothèques " + bibliotheques + "/" + bibliothequesFermees + " · faces " + faces + "/" + facesFermees
                + " · traceurs " + traceurs + "/" + traceursFermes + " · glyphes " + glyphes + "/" + glyphesLiberes
                + " · rendus " + rendus + " · formes " + formes + " · erreurs FreeType " + erreursFt;
        }
    }

    public static final Compteurs COMPTEURS = new Compteurs();

    static void verifierFil() {
        if (!TexteCache.surFil()) throw new IllegalStateException("TexteRaster : FreeType hors du fil Reborn-Texte");
    }

    private static void verifier(int err, String quoi) {
        if (err != 0) {
            COMPTEURS.erreursFt++;
            String s = FT_Error_String(err);
            throw new IllegalStateException(quoi + " : erreur FreeType " + err + (s != null ? " (" + s + ")" : ""));
        }
    }

    private static void initialiser() {
        if (bibliotheque != 0) return;
        try (MemoryStack s = MemoryStack.stackPush()) {
            PointerBuffer p = s.mallocPointer(1);
            verifier(FT_Init_FreeType(p), "FT_Init_FreeType");
            bibliotheque = p.get(0);
            COMPTEURS.bibliotheques++;
            int err = FT_Stroker_New(bibliotheque, p);
            if (err != 0) {
                FT_Done_FreeType(bibliotheque);
                bibliotheque = 0;
                COMPTEURS.bibliothequesFermees++;
                verifier(err, "FT_Stroker_New");
            }
            traceur = p.get(0);
            COMPTEURS.traceurs++;
        }
        LOGGER.info("TexteRaster : FreeType {} (bibliothèque propre au mod, fil {})", versionFreeType(),
            Thread.currentThread().getName());
    }

    /** Version de FreeType chargée (« 2.14.1 »), ou « ? » avant le premier rendu (fil du texte). */
    public static String versionFreeType() {
        if (bibliotheque == 0) return "?";
        try (MemoryStack s = MemoryStack.stackPush()) {
            IntBuffer v = s.mallocInt(3);
            FT_Library_Version(bibliotheque, v.slice(0, 1), v.slice(1, 1), v.slice(2, 1));
            return v.get(0) + "." + v.get(1) + "." + v.get(2);
        }
    }

    /**
     * Ferme tout (fil du texte) : faces (puis libération de leurs octets), traceur, bibliothèque, chacun dans un
     * {@code finally}. Un rendu suivant rouvrira ce qu'il faut.
     */
    public static void fermer() {
        verifierFil();
        try {
            for (Police p : Police.values()) {
                Face f = p.face;
                if (f == null) continue;
                p.face = null;
                try {
                    FT_Done_Face(FT_Face.create(f.ft));
                    COMPTEURS.facesFermees++;
                } finally {
                    MemoryUtil.memFree(f.ttf);
                    f.ttf = null;
                    f.ft = 0;
                }
            }
        } finally {
            try {
                if (traceur != 0) {
                    FT_Stroker_Done(traceur);
                    traceur = 0;
                    COMPTEURS.traceursFermes++;
                }
            } finally {
                if (bibliotheque != 0) {
                    FT_Done_FreeType(bibliotheque);
                    bibliotheque = 0;
                    COMPTEURS.bibliothequesFermees++;
                }
            }
        }
    }

    /** Police ouverte (chargée au premier appel, fil du texte). */
    static Face face(Police p) {
        Face f = p.face;
        if (f != null) return f;
        verifierFil();
        initialiser();
        f = charger(p);
        p.face = f;
        return f;
    }

    private static Face charger(Police p) {
        String base = "/assets/reborn/font/" + p.fichier;
        byte[] octets;
        try (InputStream in = TexteRaster.class.getResourceAsStream(base + ".ttf")) {
            if (in == null) throw new IllegalStateException("police absente : " + base + ".ttf");
            octets = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("lecture de " + base + ".ttf", e);
        }
        Face f = new Face(p);
        ByteBuffer ttf = MemoryUtil.memAlloc(octets.length);
        ttf.put(octets).flip();
        try (MemoryStack s = MemoryStack.stackPush()) {
            PointerBuffer pf = s.mallocPointer(1);
            int err = FT_New_Memory_Face(bibliotheque, ttf, 0, pf);
            if (err != 0) {
                MemoryUtil.memFree(ttf);
                verifier(err, "FT_New_Memory_Face " + p.fichier);
            }
            f.ft = pf.get(0);
            f.ttf = ttf;
            COMPTEURS.faces++;
        }
        try {
            FT_Face face = FT_Face.create(f.ft);
            FT_Select_Charmap(face, FT_ENCODING_UNICODE);
            f.upm = FT_Face.nunits_per_EM(f.ft) & 0xFFFF;
            int n = (int) FT_Face.nnum_glyphs(f.ft);
            // cmap (points de code croissants)
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer gi = s.mallocInt(1);
                int[] c = new int[1024], g = new int[1024];
                int k = 0;
                long code = FT_Get_First_Char(face, gi);
                while (gi.get(0) != 0) {
                    if (k == c.length) {
                        c = Arrays.copyOf(c, k * 2);
                        g = Arrays.copyOf(g, k * 2);
                    }
                    c[k] = (int) code;
                    g[k] = gi.get(0);
                    k++;
                    code = FT_Get_Next_Char(face, code, gi);
                }
                f.codes = Arrays.copyOf(c, k);
                f.glyphes = Arrays.copyOf(g, k);
            }
            // avances hmtx, en unités de police
            CLongBuffer av = MemoryUtil.memAllocCLong(n);
            try {
                verifier(FT_Get_Advances(face, 0, FT_LOAD_NO_SCALE, av), "FT_Get_Advances");
                f.avances = new int[n];
                for (int i = 0; i < n; i++) f.avances[i] = (int) av.get(i);
            } finally {
                MemoryUtil.memFree(av);
            }
            f.bornes = new int[2 * n];
            Arrays.fill(f.bornes, Integer.MIN_VALUE);
            f.gEspace = f.glyphe(ESP);
            f.gPoint = f.glyphe('.');
            // capitales et hauteur d'x : OS/2, contrôlées sur les bornes du H et du x (Sorts Mill Goudy avait 356 pour 704)
            long os2 = nFT_Get_Sfnt_Table(f.ft, FT_SFNT_OS2);
            int cap = 0, xh = 0;
            if (os2 != 0 && TT_OS2.nversion(os2) >= 2) {
                cap = TT_OS2.nsCapHeight(os2);
                xh = TT_OS2.nsxHeight(os2);
            }
            int hHaut = hautGlyphe(f, f.glyphe('H')), xHaut = hautGlyphe(f, f.glyphe('x'));
            if (cap == 0 || Math.abs(cap - hHaut) > 0.10 * hHaut) {
                cap = hHaut;
                f.metriquesOs2 = false;
            }
            if (xh == 0 || Math.abs(xh - xHaut) > 0.10 * xHaut) {
                xh = xHaut;
                f.metriquesOs2 = false;
            }
            f.cap = cap;
            f.xh = xh;
            // « … » centré verticalement (polices japonaises) : le Python le compose de trois points
            int ge = f.glyphe(POINTS);
            if (ge >= 0) {
                lireBornes(f, ge);
                f.ellipseCjk = f.bornes[2 * ge] > 0.2 * f.xh;
            }
            // voyelles à macron composables (_texte_raster : MACRONS, _macron) : U+00AF, écarts des accents, pente
            f.gMacron = f.glyphe(0xAF);
            if (f.gMacron >= 0) f.macronBornes = boiteGlyphe(f, f.gMacron);
            f.ecartBas = ecartAccent(f, 0xE9, f.xh);
            f.ecartHaut = ecartAccent(f, 0xC9, f.cap);
            long post = nFT_Get_Sfnt_Table(f.ft, FT_SFNT_POST);
            if (post != 0) f.pente = -Math.tan(Math.toRadians(TT_Postscript.nitalicAngle(post) / 65536.0));
            lireCrenage(f, base + ".kern");
            LOGGER.info("TexteRaster : {} chargée (UPM {}, capitales {}, x {}{}, {} glyphes, {} paires de crénage {})",
                p.libelle, f.upm, f.cap, f.xh, f.metriquesOs2 ? " (OS/2)" : " (bornes du H / du x)", n,
                f.kernCles.length, f.sourceCrenage);
        } catch (RuntimeException e) {
            try {
                FT_Done_Face(FT_Face.create(f.ft));
                COMPTEURS.facesFermees++;
            } finally {
                MemoryUtil.memFree(ttf);
            }
            throw e;
        }
        return f;
    }

    private static int hautGlyphe(Face f, int g) {
        if (g < 0) return 0;
        lireBornes(f, g);
        return f.bornes[2 * g + 1];
    }

    /** Bornes verticales exactes du glyphe (unités de police, courbes comprises, comme le BoundsPen de fontTools). */
    private static void lireBornes(Face f, int g) {
        if (f.bornes[2 * g] != Integer.MIN_VALUE) return;
        int yMin = 0, yMax = 0;
        if (nFT_Load_Glyph(f.ft, g, FT_LOAD_NO_SCALE | FT_LOAD_IGNORE_TRANSFORM) == 0) {
            long slot = MemoryUtil.memGetAddress(f.ft + FT_Face.GLYPH);
            long outline = slot + FT_GlyphSlot.OUTLINE;
            if (MemoryUtil.memGetShort(outline + org.lwjgl.util.freetype.FT_Outline.N_POINTS) > 0) {
                try (MemoryStack s = MemoryStack.stackPush()) {
                    FT_BBox b = FT_BBox.calloc(s);
                    if (nFT_Outline_Get_BBox(outline, b.address()) == 0) {
                        yMin = (int) b.yMin();
                        yMax = (int) b.yMax();
                    }
                }
            }
        }
        f.bornes[2 * g] = yMin;
        f.bornes[2 * g + 1] = yMax;
    }

    /** Boîte exacte du glyphe (xMin, yMin, xMax, yMax, unités de police), {0, 0, 0, 0} sans contour. */
    private static int[] boiteGlyphe(Face f, int g) {
        int[] b4 = new int[4];
        if (nFT_Load_Glyph(f.ft, g, FT_LOAD_NO_SCALE | FT_LOAD_IGNORE_TRANSFORM) != 0) return b4;
        long outline = MemoryUtil.memGetAddress(f.ft + FT_Face.GLYPH) + FT_GlyphSlot.OUTLINE;
        if (MemoryUtil.memGetShort(outline + org.lwjgl.util.freetype.FT_Outline.N_POINTS) == 0) return b4;
        try (MemoryStack s = MemoryStack.stackPush()) {
            FT_BBox b = FT_BBox.calloc(s);
            if (nFT_Outline_Get_BBox(outline, b.address()) == 0) {
                b4[0] = (int) b.xMin();
                b4[1] = (int) b.yMin();
                b4[2] = (int) b.xMax();
                b4[3] = (int) b.yMax();
            }
        }
        return b4;
    }

    /**
     * Écart entre {@code hauteur} (x ou capitale) et le bas de l'accent du caractère {@code cp} (é / É) : bas le plus
     * bas des contours dont le bas dépasse 1,02 × hauteur, moins la hauteur ; 10 % de l'em sans accent mesurable
     * ({@code _ecart_accent} du Python : bornes exactes de chaque contour, courbes comprises).
     */
    private static double ecartAccent(Face f, int cp, double hauteur) {
        int g = f.glyphe(cp);
        if (g < 0 || nFT_Load_Glyph(f.ft, g, FT_LOAD_NO_SCALE | FT_LOAD_IGNORE_TRANSFORM) != 0) return 0.10 * f.upm;
        long outline = MemoryUtil.memGetAddress(f.ft + FT_Face.GLYPH) + FT_GlyphSlot.OUTLINE;
        int nc = org.lwjgl.util.freetype.FT_Outline.nn_contours(outline) & 0xFFFF;
        int np = org.lwjgl.util.freetype.FT_Outline.nn_points(outline) & 0xFFFF;
        if (nc == 0 || np == 0) return 0.10 * f.upm;
        var pts = org.lwjgl.util.freetype.FT_Outline.npoints(outline);
        java.nio.ByteBuffer tags = org.lwjgl.util.freetype.FT_Outline.ntags(outline);
        java.nio.ShortBuffer fins = org.lwjgl.util.freetype.FT_Outline.ncontours(outline);
        double bas = Double.POSITIVE_INFINITY;
        int debut = 0;
        for (int c = 0; c < nc; c++) {
            int fin = fins.get(c) & 0xFFFF;
            int n = fin - debut + 1;
            if (n > 0) {
                double[] y = new double[n];
                boolean[] on = new boolean[n];
                for (int i = 0; i < n; i++) {
                    y[i] = pts.get(debut + i).y();
                    on[i] = (tags.get(debut + i) & 1) != 0;
                }
                double ymin = yMinContour(y, on);
                if (ymin > hauteur * 1.02) bas = Math.min(bas, ymin);
            }
            debut = fin + 1;
        }
        return bas == Double.POSITIVE_INFINITY ? 0.10 * f.upm : bas - hauteur;
    }

    /**
     * Bas exact d'un contour TrueType : points sur la courbe, milieux implicites entre deux contrôles, extrêmes des
     * quadratiques (comme le BoundsPen de fontTools).
     */
    private static double yMinContour(double[] y, boolean[] on) {
        int n = y.length;
        // suite cyclique des points, en commençant sur un point de la courbe (ou au milieu des deux premiers contrôles)
        int s = -1;
        for (int i = 0; i < n; i++) if (on[i]) { s = i; break; }
        double y0 = s >= 0 ? y[s] : (y[0] + y[n > 1 ? 1 : 0]) / 2;
        int k0 = s >= 0 ? s + 1 : 1, total = s >= 0 ? n - 1 : n;
        double cy = y0, qy = 0, ymin = y0;
        boolean ctrl = false;
        for (int k = 0; k <= total; k++) {
            boolean fermeture = k == total;
            double py = fermeture ? y0 : y[(k0 + k) % n];
            boolean pon = fermeture || on[(k0 + k) % n];
            if (pon) {
                ymin = Math.min(ymin, ctrl ? quadMin(cy, qy, py) : Math.min(cy, py));
                cy = py;
                ctrl = false;
            } else if (ctrl) {
                double m = (qy + py) / 2;
                ymin = Math.min(ymin, quadMin(cy, qy, m));
                cy = m;
                qy = py;
            } else {
                qy = py;
                ctrl = true;
            }
        }
        return ymin;
    }

    /** Minimum en y d'une quadratique (y0, contrôle y1, y2). */
    private static double quadMin(double y0, double y1, double y2) {
        double m = Math.min(y0, y2), d = y0 - 2 * y1 + y2;
        if (d != 0) {
            double t = (y0 - y1) / d;
            if (t > 0 && t < 1) m = Math.min(m, (1 - t) * (1 - t) * y0 + 2 * t * (1 - t) * y1 + t * t * y2);
        }
        return m;
    }

    private static void lireCrenage(Face f, String chemin) {
        try (InputStream in = TexteRaster.class.getResourceAsStream(chemin)) {
            if (in == null) {
                LOGGER.warn("TexteRaster : {} absent, police sans crénage", chemin);
                return;
            }
            DataInputStream d = new DataInputStream(new BufferedInputStream(in));
            byte[] m = new byte[4];
            d.readFully(m);
            if (m[0] != 'R' || m[1] != 'K' || m[2] != 'R' || m[3] != 'N') throw new IOException("en-tête inattendu");
            int version = d.readUnsignedShort(), upm = d.readUnsignedShort();
            int n = d.readInt();
            if (version != 1 || upm != f.upm || n < 0) throw new IOException("version " + version + ", UPM " + upm);
            int[] cles = new int[n];
            short[] val = new short[n];
            int avant = -1;
            for (int i = 0; i < n; i++) {
                int g1 = d.readUnsignedShort(), g2 = d.readUnsignedShort();
                if (g1 > 0x7FFF) throw new IOException("glyphe " + g1 + " hors de la clé 32 bits");
                cles[i] = (g1 << 16) | g2;
                val[i] = d.readShort();
                if (cles[i] <= avant) throw new IOException("paires non triées");
                avant = cles[i];
            }
            f.kernCles = cles;
            f.kernValeurs = val;
            f.sourceCrenage = ".kern";
        } catch (IOException e) {
            LOGGER.error("TexteRaster : {} illisible ({}), police sans crénage", chemin, e.getMessage());
        }
    }

    /* ================================================================ demande */

    /**
     * Ce qu'il faut rendre, en pixels écran. Deux formes, comme {@code rendre} et {@code rendre_ligne} du Python :
     * <ul>
     *   <li>{@link #bloc} : un bloc de lignes centré sur (cx, cy) (narration, plaque) ;</li>
     *   <li>{@link #ligne} : une ligne de HUD posée sur x selon l'ancre, centrée verticalement sur yCentre (milieu des
     *       capitales, ou de l'encre pour une ponctuation seule dans une case).</li>
     * </ul>
     * {@code f} règle le contour (rayon f px) et l'ombre (décalage max(1, arrondi(f))) ; C, L et P sont déjà à l'échelle
     * de la fenêtre. {@code couleurDefaut} est une clé de couleur ({@code ""} blanc, {@code "hud"}, {@code "doux"}…).
     * {@code ombre} : null = l'ombre dure de la recette (le contour décalé de d en diagonale) ; sinon une ombre douce
     * ({@link Ombre}).
     */
    public record Demande(String texte, Police police, Preset preset, double cap, double largeurMax, int pas, double f,
                          double cx, double cy, Ancre ancre, double interlettre, String couleurDefaut, boolean typo,
                          boolean ligne, boolean centrageEncre, Equilibrage equilibrage, Ombre ombre) {

        public Demande {
            if (texte == null) texte = "";
            if (couleurDefaut == null) couleurDefaut = "";
            if (indexCle(couleurDefaut) < 0 && hexa(couleurDefaut) < 0)
                throw new IllegalArgumentException("couleur inconnue : " + couleurDefaut);
        }

        /** Bloc centré (narration, plaque) : alignement centré, équilibrage automatique, typographie, ombre dure. */
        public static Demande bloc(String texte, Police police, Preset preset, double cap, double largeurMax, int pas,
                                   double f, double cx, double cy) {
            return bloc(texte, police, preset, cap, largeurMax, pas, f, cx, cy, 0.0, Equilibrage.AUTO, null);
        }

        /**
         * Bloc centré, tous réglages : approche (px entre deux lettres), équilibrage, ombre (null = ombre dure). Pour des
         * coupes de ligne figées, donner le texte avec ses retours à la ligne, {@code largeurMax} = sans limite
         * ({@code 1e9}) et {@link Equilibrage#JAMAIS}.
         */
        public static Demande bloc(String texte, Police police, Preset preset, double cap, double largeurMax, int pas,
                                   double f, double cx, double cy, double interlettre, Equilibrage equilibrage,
                                   Ombre ombre) {
            return new Demande(texte, police, preset, cap, largeurMax, pas, f, cx, cy, Ancre.CENTRE, interlettre, "", true,
                false, false, equilibrage, ombre);
        }

        /** Ligne de HUD ({@code rendre_ligne}). */
        public static Demande ligne(String texte, Police police, Preset preset, double cap, double x, double yCentre,
                                    Ancre ancre, double f, double interlettre, String couleurDefaut, boolean typo,
                                    boolean centrageEncre) {
            return new Demande(texte, police, preset, cap, SANS_LIMITE, 0, f, x, yCentre, ancre, interlettre,
                couleurDefaut, typo, true, centrageEncre, Equilibrage.JAMAIS, null);
        }

        /** Même demande, ancre déplacée (la couche se déplace d'autant si le déplacement est entier). */
        public Demande ailleurs(double x, double y) {
            return new Demande(texte, police, preset, cap, largeurMax, pas, f, x, y, ancre, interlettre, couleurDefaut,
                typo, ligne, centrageEncre, equilibrage, ombre);
        }
    }

    /**
     * Ombre <b>douce</b> d'un bloc (narration plein écran, planche d1 du 02/11 : recette de
     * {@code _maquettes_narration_d.rasteriser_narration}) : le contour réuni au glyphe, décalé de ({@code dx},
     * {@code dy}) px, quantifié sur 8 bits puis flouté par le flou gaussien de Pillow (σ = {@code flou} px : trois passes
     * de flou de boîte à rayon fractionnaire, {@link #flouPillow}), posé sous le contour à l'opacité d'ombre du
     * préréglage. La couche est alors recadrée sur ses pixels non transparents. Valeurs en pixels écran, déjà à
     * l'échelle de la fenêtre.
     */
    public record Ombre(int dx, int dy, double flou) { }

    /* ================================================================ texte : balises et typographie française (§2.2) */

    /** Suite de (caractère, clé) : points de code et index de clé de couleur. */
    static final class Suite {
        int[] c = new int[64], k = new int[64];
        int n;

        void add(int cp, int cle) {
            if (n == c.length) {
                c = Arrays.copyOf(c, n * 2);
                k = Arrays.copyOf(k, n * 2);
            }
            c[n] = cp;
            k[n] = cle;
            n++;
        }

        int dernier() { return c[n - 1]; }

        String texte(int a, int b) {
            StringBuilder s = new StringBuilder(b - a);
            for (int i = a; i < b; i++) s.appendCodePoint(c[i]);
            return s.toString();
        }
    }

    private static final Pattern BALISE = Pattern.compile("<(/?)([a-z_]*)>");

    /**
     * « < » littéral dans un texte balisé : un caractère d'usage privé (U+E000) qui ne peut pas ouvrir de balise et que
     * la lecture rend en « < ». Pour les textes reçus déjà découpés en segments (narration, plaques : le serveur a lu
     * les balises), un « <rouge> » ou un « </> » écrit par un joueur reste du texte.
     */
    public static final char INFERIEUR_LITTERAL = '';

    /** Protège les « < » d'un texte brut avant de le poser entre des balises ({@link #INFERIEUR_LITTERAL}). */
    public static String litteral(String texte) {
        return texte == null ? "" : texte.indexOf('<') < 0 ? texte : texte.replace('<', INFERIEUR_LITTERAL);
    }

    /** {@code "Les <ombre>ombres</> s'étirent"} → suite de (caractère, clé). Balise inconnue = texte littéral. */
    static Suite segments(String texte, int cleDefaut) {
        Suite s = new Suite();
        int cle = cleDefaut, pos = 0;
        Matcher m = BALISE.matcher(texte);
        while (m.find()) {
            if (m.start() > pos) ajouter(s, texte, pos, m.start(), cle);
            if (!m.group(1).isEmpty()) cle = cleDefaut;
            else {
                int i = indexCle(m.group(2));
                if (i >= 0) cle = i;
                else ajouter(s, texte, m.start(), m.end(), cle);
            }
            pos = m.end();
        }
        if (pos < texte.length()) ajouter(s, texte, pos, texte.length(), cle);
        return s;
    }

    private static void ajouter(Suite s, String t, int a, int b, int cle) {
        for (int i = a; i < b; ) {
            int cp = t.codePointAt(i);
            s.add(cp == INFERIEUR_LITTERAL ? '<' : cp, cle);
            i += Character.charCount(cp);
        }
    }

    static boolean estEsp(int c) { return c == ESP || c == NBSP || c == NNBSP; }

    /** {@code str.isdigit} de Python : chiffres décimaux, et exposants / indices (Numeric_Type = Digit). */
    static boolean estChiffre(int c) {
        return Character.isDigit(c) || c == 0xB2 || c == 0xB3 || c == 0xB9 || c == 0x2070
            || (c >= 0x2074 && c <= 0x2079) || (c >= 0x2080 && c <= 0x2089);
    }

    private static final String PONCT_FIN = ",.;:!?…»)’'\"-–—";

    private static Suite reduire(Suite in) {
        Suite out = new Suite();
        for (int i = 0; i < in.n; i++) {
            int c = in.c[i];
            if (estEsp(c) && out.n > 0 && estEsp(out.dernier())) {
                int p = out.dernier();
                out.c[out.n - 1] = (c == NNBSP || p == NNBSP) ? NNBSP : (c == NBSP || p == NBSP) ? NBSP : ESP;
            } else {
                out.add(c, in.k[i]);
            }
        }
        return out;
    }

    /**
     * Typographie française automatique (brief §2.2 et LISEZMOI v2), pas à pas comme {@code typographie} du Python.
     * {@code liees[0]} reçoit le nombre d'espaces insécables automatiques.
     */
    static Suite typographie(Suite in, boolean autoInsecables, int[] liees) {
        // 1. remplacements simples
        Suite a = new Suite();
        boolean ouvert = false;
        int i = 0;
        while (i < in.n) {
            int c = in.c[i], k = in.k[i];
            if (c == '.' && i + 2 < in.n && in.c[i + 1] == '.' && in.c[i + 2] == '.') {
                a.add(POINTS, k);
                i += 3;
                continue;
            }
            if (c == '\'') c = APOSTROPHE;
            else if (c == '"') {
                c = ouvert ? GUIL_F : GUIL_O;
                ouvert = !ouvert;
            } else if (c == '~') c = NBSP;
            else if (c == '\t' || c == '\r') c = ESP;
            a.add(c, k);
            i++;
        }
        a = reduire(a);
        // 2. pas d'espace avant , . … )
        Suite b = new Suite();
        for (i = 0; i < a.n; i++) {
            int c = a.c[i];
            if ((c == ',' || c == '.' || c == POINTS || c == ')') && b.n > 0 && estEsp(b.dernier())) b.n--;
            b.add(c, a.k[i]);
        }
        // 3. espace fine avant ; : ! ? » et après « (ajoutée si elle manque)
        Suite d = new Suite();
        for (i = 0; i < b.n; i++) {
            int c = b.c[i], k = b.k[i];
            if ((c == ';' || c == ':' || c == '!' || c == '?' || c == GUIL_F) && d.n > 0) {
                int p = d.dernier();
                int suiv = i + 1 < b.n ? b.c[i + 1] : -1;
                if (estEsp(p)) d.c[d.n - 1] = NNBSP;
                else if (p != ';' && p != ':' && p != '!' && p != '?' && p != RETOUR
                    && !(c == ':' && estChiffre(p) && suiv >= 0 && estChiffre(suiv))) d.add(NNBSP, k);
            }
            d.add(c, k);
            if (c == GUIL_O) {
                if (i + 1 < b.n && estEsp(b.c[i + 1])) b.c[i + 1] = NNBSP;
                else if (i + 1 < b.n) d.add(NNBSP, k);
            }
        }
        d = reduire(d);
        int deb = 0, fin = d.n;
        while (deb < fin && estEsp(d.c[deb])) deb++;
        while (fin > deb && estEsp(d.c[fin - 1])) fin--;
        if (deb > 0 || fin < d.n) {
            Suite t = new Suite();
            for (i = deb; i < fin; i++) t.add(d.c[i], d.k[i]);
            d = t;
        }
        // 4. espaces insécables automatiques
        int l = autoInsecables ? insecablesAuto(d) : 0;
        if (liees != null) liees[0] = l;
        return d;
    }

    /**
     * Espace insécable automatique (U+00A0) après un mot d'une ou deux lettres qui ne finit pas par une ponctuation
     * (« à l’aube », « Ce soir ») et entre deux mots à majuscule qui se suivent (« Ren Hyūga »). Sur place.
     */
    private static int insecablesAuto(Suite d) {
        int n = d.n;
        int[] mots = new int[16];
        int nm = 0, i = 0;
        while (i < n) {
            if (estEsp(d.c[i]) || d.c[i] == RETOUR) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && !estEsp(d.c[j]) && d.c[j] != RETOUR) j++;
            if (2 * nm + 2 > mots.length) mots = Arrays.copyOf(mots, mots.length * 2);
            mots[2 * nm] = i;
            mots[2 * nm + 1] = j;
            nm++;
            i = j;
        }
        int liees = 0;
        for (int m = 0; m + 1 < nm; m++) {
            int a0 = mots[2 * m], a1 = mots[2 * m + 1], b0 = mots[2 * m + 2];
            if (b0 - a1 != 1 || d.c[a1] != ESP) continue;
            int lettres = 0;
            for (int q = a0; q < a1; q++) if (Character.isLetter(d.c[q])) lettres++;
            int dernier = d.c[a1 - 1];
            boolean court = lettres >= 1 && lettres <= 2 && PONCT_FIN.indexOf(dernier) < 0;
            boolean noms = Character.isUpperCase(d.c[a0]) && Character.isUpperCase(d.c[b0]) && Character.isLetter(dernier);
            if (court || noms) {
                d.c[a1] = NBSP;
                liees++;
            }
        }
        return liees;
    }

    /* ================================================================ mise en page */

    /** Résultat de la mise en page (sans rastérisation) : lignes, positions et métriques, comme {@code Mise}. */
    public static final class Mise {
        public final Police police;
        public final double em, k, cap, interlettre;
        public final int pas;
        public final String calage;
        /** Capitales et hauteur d'x obtenues (px). */
        public final double capitales, hauteurX;
        public final int nbLignes;
        /** Haut du bloc (n × P), ou {@link Integer#MIN_VALUE} quand la ligne de base est imposée (ligne de HUD). */
        public final int haut;
        public final int[] debut, fin, bases;
        public final double[] largeurs, x0;
        public final double largeurBloc, largeurMax, largeurEquilibree;
        public final String equilibrage;
        public final String[] lignes;
        public final double espaceMot, espaceNative, espaceFine;
        public final int insecablesAuto, pairesCrenees, nbCaracteres;
        public final String texte, manquants;
        // items (rastérisation)
        final int n;
        final int[] cp, cle, g;
        final double[] adv, pos;
        final boolean[] dessine;
        final byte[] macron;

        Mise(Police police, double em, double k, double cap, double interlettre, int pas, String calage, double capitales,
             double hauteurX, int haut, int[] debut, int[] fin, int[] bases, double[] largeurs, double[] x0,
             double largeurMax, double largeurEquilibree, String equilibrage, String[] lignes, double espaceMot,
             double espaceNative, int insecablesAuto, int pairesCrenees, String texte, String manquants, Items it,
             double[] pos) {
            this.police = police;
            this.em = em;
            this.k = k;
            this.cap = cap;
            this.interlettre = interlettre;
            this.pas = pas;
            this.calage = calage;
            this.capitales = capitales;
            this.hauteurX = hauteurX;
            this.nbLignes = debut.length;
            this.haut = haut;
            this.debut = debut;
            this.fin = fin;
            this.bases = bases;
            this.largeurs = largeurs;
            this.x0 = x0;
            double lb = 0;
            for (double w : largeurs) lb = Math.max(lb, w);
            this.largeurBloc = lb;
            this.largeurMax = largeurMax;
            this.largeurEquilibree = largeurEquilibree;
            this.equilibrage = equilibrage;
            this.lignes = lignes;
            this.espaceMot = espaceMot;
            this.espaceNative = espaceNative;
            this.espaceFine = FINE_SUR_ESPACE * espaceMot;
            this.insecablesAuto = insecablesAuto;
            this.pairesCrenees = pairesCrenees;
            this.texte = texte;
            this.nbCaracteres = it.n;
            this.manquants = manquants;
            this.n = it.n;
            this.cp = it.cp;
            this.cle = it.cle;
            this.g = it.g;
            this.adv = it.adv;
            this.pos = pos;
            this.dessine = it.dessine;
            this.macron = it.macron;
        }

        /**
         * Texte balisé des lignes [a, b) (séparateurs intérieurs compris : espaces, retours à la ligne), à repasser tel
         * quel à {@link Demande#bloc} : couleurs en balises, « < » protégés ({@link #INFERIEUR_LITTERAL}). Sert à
         * découper un long bloc en pages (texte au centre : 12 lignes par page). La typographie y est déjà faite : la
         * refaire ne change rien. Chaîne vide si l'intervalle ne contient aucune ligne.
         */
        public String texteBalise(int a, int b) {
            a = Math.max(0, a);
            b = Math.min(nbLignes, b);
            if (a >= b) return "";
            int i0 = debut[a], i1 = fin[b - 1];
            StringBuilder s = new StringBuilder(i1 - i0 + 16);
            int ouverte = 0;   // clé ouverte (0 = la couleur par défaut du bloc, sans balise)
            for (int i = i0; i < i1; i++) {
                int k = cle[i];
                if (k != ouverte) {
                    if (ouverte != 0) s.append("</>");
                    if (k != 0) s.append('<').append(CLES[k]).append('>');
                    ouverte = k;
                }
                int c = cp[i];
                if (c == '<') s.append(INFERIEUR_LITTERAL);
                else s.appendCodePoint(c);
            }
            if (ouverte != 0) s.append("</>");
            return s.toString();
        }
    }

    /** em (px) pour des capitales de {@code capPx} ; calage sur une hauteur d'x entière (« x ») ou capitales exactes. */
    static double emPour(Face f, double capPx, boolean calageX, StringBuilder info) {
        double em = capPx * f.upm / f.cap;
        if (!calageX) {
            if (info != null) info.append("capitales exactes");
            return em;
        }
        double x = em * f.xh / f.upm;
        int xr = arrondi(x);
        double ecart = 100 * (xr / x - 1);
        if (xr > 0 && Math.abs(xr / x - 1) <= TOLERANCE_CALAGE) {
            if (info != null) info.append(String.format(java.util.Locale.ROOT, "calé (x %+.1f %%)", ecart));
            return em * xr / x;
        }
        if (info != null) info.append(String.format(java.util.Locale.ROOT, "non calé (x %+.1f %%)", ecart));
        return em;
    }

    /** Avance de l'espace-mot (px) : max(avance native, 0,25 em) ; [1] = avance native. */
    static double[] espaceMot(Face f, double em) {
        double nat = f.gEspace >= 0 ? f.avance(f.gEspace) * em / f.upm : 0.0;
        return new double[] {Math.max(nat, ESPACE_MIN_EM * em), nat};
    }

    static final class Items {
        int n;
        int[] cp = new int[64], cle = new int[64], g = new int[64];
        double[] adv = new double[64];
        boolean[] dessine = new boolean[64], secable = new boolean[64], force = new boolean[64];
        /** Voyelle à macron composée : 0 non, 1 minuscule, 2 capitale (macron posé sur le glyphe de base). */
        byte[] macron = new byte[64];

        int add(int c, int k, int gl, double a, boolean des) {
            if (n == cp.length) {
                int m = n * 2;
                cp = Arrays.copyOf(cp, m);
                cle = Arrays.copyOf(cle, m);
                g = Arrays.copyOf(g, m);
                adv = Arrays.copyOf(adv, m);
                dessine = Arrays.copyOf(dessine, m);
                secable = Arrays.copyOf(secable, m);
                force = Arrays.copyOf(force, m);
                macron = Arrays.copyOf(macron, m);
            }
            cp[n] = c;
            cle[n] = k;
            g[n] = gl;
            adv[n] = a;
            dessine[n] = des;
            secable[n] = false;
            force[n] = false;
            macron[n] = 0;
            return n++;
        }
    }

    private static Items items(Face f, Suite chars, double em, StringBuilder manquants) {
        double k = em / f.upm;
        Items it = new Items();
        double wEsp = espaceMot(f, em)[0];
        double wFine = FINE_SUR_ESPACE * wEsp;
        for (int i = 0; i < chars.n; i++) {
            int ch = chars.c[i], cle = chars.k[i];
            if (ch == POINTS && f.ellipseCjk && f.gPoint >= 0) {
                for (int q = 0; q < 3; q++) it.add('.', cle, f.gPoint, f.avance(f.gPoint) * k, true);
                continue;
            }
            if (ch == RETOUR) {
                int j = it.add(ch, cle, -1, 0.0, false);
                it.force[j] = true;
            } else if (ch == ESP) {
                int j = it.add(ch, cle, f.gEspace, wEsp, false);
                it.secable[j] = true;
            } else if (ch == NBSP) {
                it.add(ch, cle, f.gEspace, wEsp, false);
            } else if (ch == NNBSP) {
                it.add(ch, cle, -1, wFine, false);   // ½ espace-mot, sans glyphe : pas de crénage de part et d'autre
            } else {
                int g = f.glyphe(ch);
                int base = g >= 0 || f.gMacron < 0 ? -1 : baseMacron(ch);
                if (base == 0x131 && f.glyphe(base) < 0) base = 'i';
                int gb = base >= 0 ? f.glyphe(base) : -1;
                if (g >= 0) it.add(ch, cle, g, f.avance(g) * k, true);
                else if (gb >= 0) {   // voyelle à macron composée : lettre de base + U+00AF (posé au rendu)
                    int j = it.add(ch, cle, gb, f.avance(gb) * k, true);
                    it.macron[j] = (byte) (Character.isUpperCase(ch) ? 2 : 1);
                } else {
                    it.add(ch, cle, 0, f.avance(0) * k, true);   // .notdef
                    if (manquants.indexOf(new String(Character.toChars(ch))) < 0) manquants.appendCodePoint(ch);
                }
            }
        }
        return it;
    }

    /** Lettre de base d'une voyelle à macron composable ({@code MACRONS} du Python ; ī → ı), sinon -1. */
    private static int baseMacron(int ch) {
        return switch (ch) {
            case 0x101 -> 'a';
            case 0x113 -> 'e';
            case 0x12B -> 0x131;
            case 0x14D -> 'o';
            case 0x16B -> 'u';
            case 0x100 -> 'A';
            case 0x112 -> 'E';
            case 0x12A -> 'I';
            case 0x14C -> 'O';
            case 0x16A -> 'U';
            default -> -1;
        };
    }

    private static double[] positions(Face f, Items it, double k, double interlettre) {
        double[] pos = new double[it.n];
        double x = 0.0;
        for (int i = 0; i < it.n; i++) {
            pos[i] = x;
            if (i + 1 < it.n) {
                x += it.adv[i] + interlettre;
                if (it.g[i] >= 0 && it.g[i + 1] >= 0) x += f.crenage(it.g[i], it.g[i + 1]) * k;
            }
        }
        return pos;
    }

    /** Jetons : {type (0 mot, 1 retour forcé), début, fin}. */
    private static int[] jetons(Items it) {
        int[] j = new int[48];
        int nj = 0, i = 0;
        while (i < it.n) {
            if (3 * nj + 3 > j.length) j = Arrays.copyOf(j, j.length * 2);
            if (it.force[i]) {
                j[3 * nj] = 1;
                j[3 * nj + 1] = i;
                j[3 * nj + 2] = i + 1;
                nj++;
                i++;
            } else if (it.secable[i]) {
                i++;
            } else {
                int q = i;
                while (q < it.n && !it.secable[q] && !it.force[q]) q++;
                j[3 * nj] = 0;
                j[3 * nj + 1] = i;
                j[3 * nj + 2] = q;
                nj++;
                i = q;
            }
        }
        return Arrays.copyOf(j, 3 * nj);
    }

    private static double largeurLigne(Items it, double[] pos, int a, int b) {
        return b > a ? pos[b - 1] + it.adv[b - 1] - pos[a] : 0.0;
    }

    /**
     * Mots plus larges que {@code largeur} coupés entre deux caractères, chaque morceau au plus près de la largeur (un
     * caractère au moins) : le reste du mot continue la ligne suivante. Aucun mot trop large : le même tableau (mise en
     * page identique au Python, qui ne coupe pas).
     */
    private static int[] couperMotsLongs(Items it, double[] pos, int[] jet, double largeur) {
        boolean trop = false;
        for (int q = 0; q < jet.length && !trop; q += 3) {
            trop = jet[q] == 0 && largeurLigne(it, pos, jet[q + 1], jet[q + 2]) > largeur + 1e-9;
        }
        if (!trop) return jet;
        int[] j = new int[jet.length + 48];
        int nj = 0;
        for (int q = 0; q < jet.length; q += 3) {
            int typ = jet[q], a = jet[q + 1], b = jet[q + 2];
            int deb = a;
            if (typ == 0 && largeurLigne(it, pos, a, b) > largeur + 1e-9) {
                for (int i = a + 1; i < b; i++) {
                    if (largeurLigne(it, pos, deb, i + 1) <= largeur + 1e-9) continue;
                    if (3 * nj + 6 > j.length) j = Arrays.copyOf(j, j.length * 2);
                    j[3 * nj] = 0;
                    j[3 * nj + 1] = deb;
                    j[3 * nj + 2] = i;
                    nj++;
                    deb = i;
                }
            }
            if (3 * nj + 3 > j.length) j = Arrays.copyOf(j, j.length * 2);
            j[3 * nj] = typ;
            j[3 * nj + 1] = deb;
            j[3 * nj + 2] = b;
            nj++;
        }
        return Arrays.copyOf(j, 3 * nj);
    }

    /** Coupe gloutonne aux espaces sécables : {début, fin} par ligne. */
    private static int[] couper(Items it, double[] pos, int[] jet, double largeur) {
        int[] l = new int[32];
        int nl = 0;
        int deb = -1, fin = -1;
        for (int q = 0; q < jet.length; q += 3) {
            int typ = jet[q], a = jet[q + 1], b = jet[q + 2];
            if (2 * nl + 4 > l.length) l = Arrays.copyOf(l, l.length * 2);
            if (typ == 1) {
                l[2 * nl] = deb >= 0 ? deb : a;
                l[2 * nl + 1] = deb >= 0 ? fin : a;
                nl++;
                deb = fin = -1;
                continue;
            }
            if (deb < 0) {
                deb = a;
                fin = b;
            } else if (pos[b - 1] + it.adv[b - 1] - pos[deb] <= largeur + 1e-9) {
                fin = b;
            } else {
                l[2 * nl] = deb;
                l[2 * nl + 1] = fin;
                nl++;
                deb = a;
                fin = b;
            }
        }
        if (deb >= 0) {
            if (2 * nl + 2 > l.length) l = Arrays.copyOf(l, l.length + 2);
            l[2 * nl] = deb;
            l[2 * nl + 1] = fin;
            nl++;
        }
        return Arrays.copyOf(l, 2 * nl);
    }

    /**
     * Mise en page d'une demande, sans rastériser. Police déjà chargée : depuis tout fil, sauf le centrage sur l'encre
     * d'un glyphe pas encore mesuré (ses bornes se lisent sur le fil du texte).
     */
    public static Mise mettreEnPage(Demande d) {
        Face f = d.police().face;
        if (f == null) throw new IllegalStateException("police non chargée : " + d.police());
        return mettreEnPage(d, f, d.ligne() ? baseLigne(d, f) : Integer.MIN_VALUE);
    }

    /** Ligne de base d'une ligne de HUD : milieu des capitales, ou de l'encre ({@code rendre_ligne}). */
    private static int baseLigne(Demande d, Face f) {
        if (d.centrageEncre()) {
            boolean minus = false;
            String t = d.texte();
            for (int i = 0; i < t.length(); ) {
                int cp = t.codePointAt(i);
                if (Character.isLowerCase(cp)) minus = true;
                i += Character.charCount(cp);
            }
            double em = emPour(f, d.cap(), minus, null);
            return arrondi(d.cy() + centreEncre(f, t, em));
        }
        return arrondi(d.cy() + d.cap() / 2);
    }

    /** Hauteur (px, au-dessus de la ligne de base) du milieu de l'encre du texte brut ({@code centre_encre}). */
    private static double centreEncre(Face f, String texte, double em) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        boolean vu = false;
        for (int i = 0; i < texte.length(); ) {
            int cp = texte.codePointAt(i);
            i += Character.charCount(cp);
            int g = f.glyphe(cp);
            if (g < 0) continue;
            if (f.bornes[2 * g] == Integer.MIN_VALUE) {
                verifierFil();
                lireBornes(f, g);
            }
            int y0 = f.bornes[2 * g], y1 = f.bornes[2 * g + 1];
            if (y0 == 0 && y1 == 0) continue;   // glyphe vide
            lo = Math.min(lo, y0);
            hi = Math.max(hi, y1);
            vu = true;
        }
        if (!vu) return em * f.cap / f.upm / 2;
        return (lo + hi) / 2.0 * em / f.upm;
    }

    private static Mise mettreEnPage(Demande d, Face f, int baseImposee) {
        boolean ligneUnique = d.ligne();
        double capPx = d.cap(), largeurMax = d.largeurMax();
        int pas = d.pas();
        // couleur écrite « #RRGGBB » : les caractères gardent la clé "" (= la couleur par défaut de la demande)
        Suite chars = segments(d.texte(), Math.max(0, indexCle(d.couleurDefaut())));
        int[] liees = {0};
        if (d.typo()) chars = typographie(chars, !ligneUnique, liees);
        boolean calageX = false;
        for (int i = 0; i < chars.n; i++) if (Character.isLowerCase(chars.c[i])) { calageX = true; break; }
        StringBuilder infoCalage = new StringBuilder();
        double em = emPour(f, capPx, calageX, infoCalage);
        double k = em / f.upm;
        StringBuilder manquants = new StringBuilder();
        Items it = items(f, chars, em, manquants);
        double[] pos = positions(f, it, k, d.interlettre());
        int[] jet = jetons(it);
        if (!ligneUnique) jet = couperMotsLongs(it, pos, jet, largeurMax);
        int[] lignes;
        double wEq = Double.NaN;
        String infoEq;
        if (ligneUnique) {
            lignes = jet.length > 0 ? new int[] {jet[1], jet[jet.length - 1]} : new int[0];
            infoEq = "une ligne";
        } else {
            lignes = couper(it, pos, jet, largeurMax);
            wEq = largeurMax;
            Equilibrage eq = d.equilibrage();
            if (eq != Equilibrage.JAMAIS && lignes.length / 2 > 1) {
                int n0 = lignes.length / 2;
                double der = largeurLigne(it, pos, lignes[2 * n0 - 2], lignes[2 * n0 - 1]);
                if (eq == Equilibrage.TOUJOURS || der < SEUIL_DERNIERE * largeurMax) {
                    double maxMot = -1;
                    for (int q = 0; q < jet.length; q += 3) {
                        if (jet[q] != 0) continue;
                        maxMot = Math.max(maxMot, pos[jet[q + 2] - 1] + it.adv[jet[q + 2] - 1] - pos[jet[q + 1]]);
                    }
                    if (maxMot >= 0) {
                        long lo = (long) Math.ceil(maxMot * 4), hi = (long) Math.floor(largeurMax * 4);
                        if (lo <= hi) {
                            while (lo < hi) {
                                long mid = (lo + hi) / 2;
                                if (couper(it, pos, jet, mid / 4.0).length / 2 == n0) hi = mid;
                                else lo = mid + 1;
                            }
                            wEq = lo / 4.0;
                            if (eq == Equilibrage.AUTO && n0 >= 4) wEq = Math.max(wEq, PLANCHER_EQ * largeurMax);
                            lignes = couper(it, pos, jet, wEq);
                        }
                    }
                    infoEq = String.format(java.util.Locale.ROOT, "équilibré à %.2f px (dernière ligne à %d %% de L)",
                        wEq, Math.round(100 * der / largeurMax));
                } else {
                    infoEq = String.format(java.util.Locale.ROOT, "non équilibré (dernière ligne à %d %% de L)",
                        Math.round(100 * der / largeurMax));
                }
            } else {
                infoEq = lignes.length / 2 <= 1 ? "une ligne" : "non équilibré";
            }
        }
        int n = lignes.length / 2;
        int haut;
        int[] bases = new int[n];
        if (baseImposee != Integer.MIN_VALUE) {
            haut = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) bases[i] = baseImposee + i * pas;
        } else {
            haut = arrondi(d.cy() - n * pas / 2.0);
            int dBase = arrondi((pas + capPx) / 2);
            for (int i = 0; i < n; i++) bases[i] = haut + dBase + i * pas;
        }
        int[] deb = new int[n], fin = new int[n];
        double[] larg = new double[n], x0 = new double[n];
        String[] txt = new String[n];
        for (int i = 0; i < n; i++) {
            int a = lignes[2 * i], b = lignes[2 * i + 1];
            deb[i] = a;
            fin[i] = b;
            double w = largeurLigne(it, pos, a, b);
            larg[i] = w;
            x0[i] = d.ancre() == Ancre.CENTRE ? d.cx() - w / 2 : d.ancre() == Ancre.GAUCHE ? d.cx() : d.cx() - w;
            StringBuilder s = new StringBuilder();
            for (int j = a; j < b; j++) s.appendCodePoint(it.cp[j]);
            txt[i] = s.toString();
        }
        int paires = 0;
        for (int i = 0; i + 1 < it.n; i++) {
            if (it.g[i] >= 0 && it.g[i + 1] >= 0 && f.crenage(it.g[i], it.g[i + 1]) != 0) paires++;
        }
        double[] esp = espaceMot(f, em);
        return new Mise(d.police(), em, k, capPx, d.interlettre(), pas, infoCalage.toString(), em * f.cap / f.upm,
            em * f.xh / f.upm, haut, deb, fin, bases, larg, x0, largeurMax, wEq, infoEq, txt, esp[0], esp[1], liees[0],
            paires, chars.texte(0, chars.n), manquants.toString(), it, pos);
    }

    /**
     * Largeur d'avance d'une ligne (px), mesurée sans rastériser (hmtx + crénage), comme {@code largeur} des maquettes.
     * NaN tant que la police n'est pas chargée ({@link TexteCache#prechauffer}).
     */
    public static double largeur(String texte, Police police, double cap, double interlettre, boolean typo) {
        if (police.face == null) return Double.NaN;
        Mise m = mettreEnPage(new Demande(texte, police, Preset.HUD, cap, SANS_LIMITE, 0, 1.0, 0.0, 0.0, Ancre.GAUCHE,
            interlettre, "", typo, true, false, Equilibrage.JAMAIS, null), police.face, 0);
        return m.nbLignes > 0 ? m.largeurs[0] : 0.0;
    }

    /**
     * Coupe par « … » pour tenir dans {@code wmax} px ({@code tronquer} des maquettes) : au dernier mot entier qui
     * tient si le reste garde au moins la moitié de la largeur, sinon à la lettre. Le texte tel quel s'il tient, ou si
     * la police n'est pas chargée.
     */
    public static String tronquer(String texte, Police police, double cap, double wmax) {
        double w = largeur(texte, police, cap, 0.0, true);
        if (Double.isNaN(w) || w <= wmax) return texte;
        String[] mots = texte.split(" ", -1);
        for (int n = mots.length - 1; n > 0; n--) {
            String t = sansFin(String.join(" ", Arrays.copyOf(mots, n))) + "…";
            w = largeur(t, police, cap, 0.0, true);
            if (w <= wmax) {
                if (w >= 0.5 * wmax) return t;
                break;
            }
        }
        String e = texte;
        while (e.codePointCount(0, e.length()) > 1) {
            e = e.substring(0, e.offsetByCodePoints(e.length(), -1));
            String t = sansFin(e) + "…";
            if (largeur(t, police, cap, 0.0, true) <= wmax) return t;
        }
        return texte;
    }

    private static String sansFin(String s) {
        int fin = s.length();
        while (fin > 0 && " ,;:".indexOf(s.charAt(fin - 1)) >= 0) fin--;
        return s.substring(0, fin);
    }

    /* ================================================================ rastérisation */

    /** Couche RGBA calculée : position en pixels écran (coin haut gauche, entier), pixels ARGB en alpha droit. */
    public static final class Rendu {
        public final int x, y, w, h;
        public final int[] argb;
        /** Mise en page (null pour une forme). */
        public final Mise mise;

        Rendu(int x, int y, int w, int h, int[] argb, Mise mise) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.argb = argb;
            this.mise = mise;
        }
    }

    /** Rendu complet d'une demande (fil du texte) : mise en page, rastérisation, composition. */
    public static Rendu rendre(Demande d) {
        verifierFil();
        Face f = face(d.police());
        Mise m = mettreEnPage(d, f, d.ligne() ? baseLigne(d, f) : Integer.MIN_VALUE);
        Rendu r = rasteriser(m, f, d.preset(), d.f(), d.couleurDefaut(), d.ombre());
        COMPTEURS.rendus++;
        return r;
    }

    private static Rendu rasteriser(Mise m, Face f, Preset preset, double facteur, String couleurDefaut, Ombre ombre) {
        double r = facteur;
        int d = Math.max(1, arrondi(facteur));
        double marge = r + d + 2;
        if (m.nbLignes == 0) return new Rendu(0, 0, 1, 1, new int[1], m);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        int minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
        for (int i = 0; i < m.nbLignes; i++) {
            minX = Math.min(minX, Math.min(m.x0[i], m.x0[i] + m.largeurs[i]));
            maxX = Math.max(maxX, Math.max(m.x0[i], m.x0[i] + m.largeurs[i]));
            minB = Math.min(minB, m.bases[i]);
            maxB = Math.max(maxB, m.bases[i]);
        }
        double em = m.em;
        int X0, Y0, W, H;
        if (ombre == null) {   // contour et ombre dure (calcul inchangé depuis l'étape 1, bit pour bit)
            double pad = em * 1.2 + r + d + 4 + em * 0.0;
            X0 = (int) Math.floor(minX - pad);
            Y0 = (int) Math.floor(minB - em * 1.3 - r - d - 4);
            W = (int) Math.ceil(maxX + pad) - X0;
            H = (int) Math.ceil(maxB + em * 0.7 + r + d + 4) - Y0;
        } else {               // ombre douce : contour, décalage et étendue du flou (rasteriser_narration)
            double ext = r + Math.abs(ombre.dx()) + Math.abs(ombre.dy()) + 3.0 * ombre.flou() + 4;
            double pad = em * 1.2 + ext;
            X0 = (int) Math.floor(minX - pad);
            Y0 = (int) Math.floor(minB - em * 1.3 - ext);
            W = (int) Math.ceil(maxX + pad) - X0;
            H = (int) Math.ceil(maxB + em * 0.7 + ext) - Y0;
        }
        float[] trait = new float[W * H];
        List<float[]> remp = new ArrayList<>(4);
        int[] clesRemp = new int[CLES.length];
        int nRemp = 0;

        try (MemoryStack s = MemoryStack.stackPush()) {
            // contours chargés à ×CHARGEMENT (taille tronquée au 1/64 px, comme Pillow), ramenés à ×SS par la face
            long q = (long) Math.floor(em * CHARGEMENT * 64);
            verifier(nFT_Set_Char_Size(f.ft, 0, q, 72, 72), "FT_Set_Char_Size");
            long echelle = 65536L * SS / CHARGEMENT;
            FT_Matrix mat = FT_Matrix.calloc(s);
            mat.xx(echelle).yy(echelle);
            nFT_Set_Transform(f.ft, mat.address(), 0L);
            FT_Stroker_Set(traceur, arrondi(r * SS * 64), FT_STROKER_LINECAP_ROUND, FT_STROKER_LINEJOIN_ROUND, 0);
            PointerBuffer pg = s.mallocPointer(1);
            for (int li = 0; li < m.nbLignes; li++) {
                int a = m.debut[li], b = m.fin[li];
                for (int j = a; j < b; j++) {
                    if (!m.dessine[j]) continue;
                    double x = m.x0[li] + m.pos[j] - m.pos[a];
                    double X = x - X0, Y = m.bases[li] - Y0;
                    int c = m.cle[j], ic = -1;
                    for (int t = 0; t < nRemp; t++) if (clesRemp[t] == c) { ic = t; break; }
                    if (ic < 0) {
                        ic = nRemp;
                        clesRemp[nRemp++] = c;
                        remp.add(new float[W * H]);
                    }
                    glyphe(f, m.g[j], X, Y, remp.get(ic), trait, W, H, pg);
                    if (m.macron[j] != 0) {   // _macron du Python : centré sur l'avance, bas à x (ou capitale) + écart
                        int[] mb = f.macronBornes;
                        double h = m.macron[j] == 2 ? f.cap : f.xh;
                        double bas = h + (m.macron[j] == 2 ? f.ecartHaut : f.ecartBas);
                        double yc = bas + (mb[3] - mb[1]) / 2.0;
                        double xc = f.avance(m.g[j]) / 2.0 + (yc - h / 2) * f.pente;
                        double ddx = xc - (mb[0] + mb[2]) / 2.0, ddy = bas - mb[1], sc = em / f.upm;
                        glyphe(f, f.gMacron, X + ddx * sc, Y - ddy * sc, remp.get(ic), trait, W, H, pg);
                    }
                }
            }
        } finally {
            nFT_Set_Transform(f.ft, 0L, 0L);
        }

        // couverture totale (max des couleurs, chacune bornée), boîte de l'encre
        int[] rgbRemp = new int[nRemp];
        String defaut = couleurDefaut;
        for (int t = 0; t < nRemp; t++) {
            String cle = CLES[clesRemp[t]];
            rgbRemp[t] = couleur(cle.isEmpty() ? defaut : cle);
        }
        if (ombre != null) return ombreDouce(m, remp.toArray(new float[0][]), rgbRemp, trait, W, H, X0, Y0, ombre, preset);
        float seuil = 0.5f / 255f;
        int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bx1 = -1, by1 = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int p = y * W + x;
                float tot = 0f;
                for (int t = 0; t < nRemp; t++) tot = Math.max(tot, Math.min(1f, remp.get(t)[p]));
                if (tot > seuil) {
                    if (x < bx0) bx0 = x;
                    if (x > bx1) bx1 = x;
                    if (y < by0) by0 = y;
                    if (y > by1) by1 = y;
                }
            }
        }
        if (bx1 < 0) return new Rendu(X0, Y0, 1, 1, new int[1], m);
        int mm = (int) Math.ceil(marge);
        int cx0 = Math.max(0, bx0 - mm), cy0 = Math.max(0, by0 - mm);
        int cx1 = Math.min(W, bx1 + 1 + mm), cy1 = Math.min(H, by1 + 1 + mm);
        int w = cx1 - cx0, h = cy1 - cy0;
        int[] out = new int[w * h];
        float[][] rp = remp.toArray(new float[0][]);
        composer(rp, rgbRemp, trait, null, W, H, cx0, cy0, w, h, d, preset.contour, preset.ombre, preset.gamma, out);
        return new Rendu(X0 + cx0, Y0 + cy0, w, h, out, m);
    }

    /**
     * Fin de la rastérisation avec une ombre douce (recette de {@code rasteriser_narration}, planche d1) : masque S =
     * max(contour, remplissage) décalé de (dx, dy), quantifié sur 8 bits, flouté ({@link #flouPillow}) ; composition de
     * tout le canevas (ombre × opacité d'ombre, contour, remplissages), puis la couche recadrée sur ses pixels non
     * transparents.
     */
    private static Rendu ombreDouce(Mise m, float[][] remp, int[] rgb, float[] trait, int W, int H, int X0, int Y0,
                                    Ombre ombre, Preset preset) {
        int n = W * H, dx = ombre.dx(), dy = ombre.dy();
        float[] floue = new float[n];
        int[] q = new int[n];
        for (int y = 0; y < H; y++) {
            int ys = y - dy;
            if (ys < 0 || ys >= H) continue;
            for (int x = 0; x < W; x++) {
                int xs = x - dx;
                if (xs < 0 || xs >= W) continue;
                float s = masque(remp, trait, ys * W + xs);
                if (ombre.flou() > 0) q[y * W + x] = octet(s);
                else floue[y * W + x] = s;   // sans flou : le masque décalé tel quel (pas de quantification)
            }
        }
        if (ombre.flou() > 0) {
            flouPillow(q, W, H, ombre.flou());
            for (int p = 0; p < n; p++) floue[p] = q[p] / 255f;
        }
        int[] tout = new int[n];
        composer(remp, rgb, trait, floue, W, H, 0, 0, W, H, 0, preset.contour, preset.ombre, preset.gamma, tout);
        int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bx1 = -1, by1 = -1;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (tout[y * W + x] >>> 24 == 0) continue;
                if (x < bx0) bx0 = x;
                if (x > bx1) bx1 = x;
                if (y < by0) by0 = y;
                if (y > by1) by1 = y;
            }
        }
        if (bx1 < 0) return new Rendu(X0, Y0, 1, 1, new int[1], m);
        int w = bx1 - bx0 + 1, h = by1 - by0 + 1;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(tout, (by0 + y) * W + bx0, out, y * w, w);
        return new Rendu(X0 + bx0, Y0 + by0, w, h, out, m);
    }

    /**
     * Flou gaussien de Pillow ({@code ImageFilter.GaussianBlur(σ)} sur une image « L ») porté à l'identique, sur des
     * octets (0..255) en place : trois passes horizontales puis trois verticales d'un flou de boîte à rayon fractionnaire
     * ({@code BoxBlur.c} : rayon de boîte de Gwosdek et al., poids en 8.24, bords répétés, arrondi à chaque passe).
     * Contrôlé bit à bit contre Pillow 12.3 (σ de 0,5 à 4, tailles de 5 à 90 px).
     */
    static void flouPillow(int[] v, int W, int H, double sigma) {
        float r = rayonBoite((float) sigma, 3);
        if (r == 0f) return;
        int nMax = Math.max(W, H);
        int[] lin = new int[nMax], sortie = new int[nMax];
        for (int passe = 0; passe < 3; passe++) {
            for (int y = 0; y < H; y++) {
                System.arraycopy(v, y * W, lin, 0, W);
                ligneBoite(lin, sortie, W, r);
                System.arraycopy(sortie, 0, v, y * W, W);
            }
        }
        for (int passe = 0; passe < 3; passe++) {
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) lin[y] = v[y * W + x];
                ligneBoite(lin, sortie, H, r);
                for (int y = 0; y < H; y++) v[y * W + x] = sortie[y];
            }
        }
    }

    /** Rayon de la boîte équivalente à σ en {@code passes} passes ({@code _gaussian_blur_radius} de Pillow, en float). */
    static float rayonBoite(float sigma, int passes) {
        float sigma2 = sigma * sigma / passes;
        float L = (float) Math.sqrt(12.0 * sigma2 + 1.0);
        float l = (float) Math.floor((L - 1.0) / 2.0);
        float a = (2 * l + 1) * (l * (l + 1) - 3 * sigma2);
        a /= 6 * (sigma2 - (l + 1) * (l + 1));
        return l + a;
    }

    /** Une ligne du flou de boîte de Pillow ({@code ImagingLineBoxBlur8}) : {@code in} → {@code out}, {@code n} octets. */
    private static void ligneBoite(int[] in, int[] out, int n, float rayon) {
        int last = n - 1, radius = (int) rayon;
        long ww = (long) (16777216f / (rayon * 2f + 1f));
        long fw = ((1L << 24) - (radius * 2L + 1) * ww) / 2;
        int edgeA = Math.min(radius + 1, n), edgeB = Math.max(n - radius - 1, 0);
        long acc = (long) in[0] * (radius + 1);
        for (int x = 0; x < edgeA - 1; x++) acc += in[x];
        acc += (long) in[last] * (radius - edgeA + 1);
        if (edgeA <= edgeB) {
            for (int x = 0; x < edgeA; x++) {
                acc += in[x + radius] - in[0];
                out[x] = boite(acc * ww + (long) (in[0] + in[x + radius + 1]) * fw);
            }
            for (int x = edgeA; x < edgeB; x++) {
                acc += in[x + radius] - in[x - radius - 1];
                out[x] = boite(acc * ww + (long) (in[x - radius - 1] + in[x + radius + 1]) * fw);
            }
            for (int x = edgeB; x <= last; x++) {
                acc += in[last] - in[x - radius - 1];
                out[x] = boite(acc * ww + (long) (in[x - radius - 1] + in[last]) * fw);
            }
        } else {
            for (int x = 0; x < edgeB; x++) {
                acc += in[x + radius] - in[0];
                out[x] = boite(acc * ww + (long) (in[0] + in[x + radius + 1]) * fw);
            }
            for (int x = edgeB; x < edgeA; x++) {
                acc += in[last] - in[0];
                out[x] = boite(acc * ww + (long) (in[0] + in[last]) * fw);
            }
            for (int x = edgeA; x <= last; x++) {
                acc += in[last] - in[x - radius - 1];
                out[x] = boite(acc * ww + (long) (in[x - radius - 1] + in[last]) * fw);
            }
        }
    }

    /** Octet d'une somme pondérée en 8.24 (arrondi au plus proche, comme le {@code SAVE} de Pillow). */
    private static int boite(long bulk) { return (int) ((bulk + (1L << 23)) >> 24) & 0xFF; }

    /** Remplissage (somme) et contour (maximum) d'un glyphe, rendus à ×SS puis posés à 1× dans le canevas. */
    private static void glyphe(Face f, int g, double X, double Y, float[] remp, float[] trait, int W, int H,
                               PointerBuffer pg) {
        int ix = (int) Math.floor(X), iy = (int) Math.floor(Y);
        long dx = (long) arrondi((X - ix) * PAS_ORIGINE) * (64 / PAS_ORIGINE);   // origine au 1/16 px, en 1/64 px
        long dy = (long) arrondi((Y - iy) * PAS_ORIGINE) * (64 / PAS_ORIGINE);
        int err = nFT_Load_Glyph(f.ft, g, CHARGEMENT_FT | FT_LOAD_NO_BITMAP);
        if (err != 0) {
            COMPTEURS.erreursFt++;
            return;
        }
        long slot = MemoryUtil.memGetAddress(f.ft + FT_Face.GLYPH);
        nFT_Outline_Translate(slot + FT_GlyphSlot.OUTLINE, dx * SS, -dy * SS);   // y FreeType vers le haut
        cumuler(slot, pg, false, ix, iy, remp, W, H);
        cumuler(slot, pg, true, ix, iy, trait, W, H);
    }

    /** Couverture 1× d'un glyphe (somme des sous-pixels), réutilisée d'un glyphe à l'autre (fil du texte). */
    private static int[] reduit = new int[1024];

    private static void cumuler(long slot, PointerBuffer pg, boolean contour, int ix, int iy, float[] dst, int W, int H) {
        long pga = pg.address();
        pg.put(0, 0L);
        if (nFT_Get_Glyph(slot, pga) != 0) {
            COMPTEURS.erreursFt++;
            return;
        }
        COMPTEURS.glyphes++;
        try {
            if (contour) {
                if (nFT_Glyph_StrokeBorder(pga, traceur, false, true) != 0) {
                    COMPTEURS.erreursFt++;
                    return;
                }
                if (CHEVAUCHEMENT) {
                    long o = pg.get(0) + org.lwjgl.util.freetype.FT_OutlineGlyph.OUTLINE
                        + org.lwjgl.util.freetype.FT_Outline.FLAGS;
                    MemoryUtil.memPutInt(o, MemoryUtil.memGetInt(o) | FT_OUTLINE_OVERLAP);
                }
            }
            if (nFT_Glyph_To_Bitmap(pga, FT_RENDER_MODE_NORMAL, 0L, true) != 0) {
                COMPTEURS.erreursFt++;
                return;
            }
            long bg = pg.get(0);
            long bm = bg + FT_BitmapGlyph.BITMAP;
            int rows = FT_Bitmap.nrows(bm), width = FT_Bitmap.nwidth(bm), pitch = FT_Bitmap.npitch(bm);
            long buf = MemoryUtil.memGetAddress(bm + FT_Bitmap.BUFFER);
            if (rows <= 0 || width <= 0 || buf == 0 || FT_Bitmap.npixel_mode(bm) != FT_PIXEL_MODE_GRAY) return;
            // sous-pixels (×SS) relatifs à l'origine entière du glyphe -> pixels 1×
            int left = FT_BitmapGlyph.nleft(bg), haut = -FT_BitmapGlyph.ntop(bg);
            int gx0 = Math.floorDiv(left, SS), gy0 = Math.floorDiv(haut, SS);
            int gw = Math.floorDiv(left + width - 1, SS) - gx0 + 1, gh = Math.floorDiv(haut + rows - 1, SS) - gy0 + 1;
            if (reduit.length < gw * gh) reduit = new int[Math.max(gw * gh, reduit.length * 2)];
            int[] red = reduit;
            Arrays.fill(red, 0, gw * gh, 0);
            for (int r = 0; r < rows; r++) {
                long ligne = pitch >= 0 ? buf + (long) r * pitch : buf + (long) (rows - 1 - r) * -pitch;
                int l = (Math.floorDiv(haut + r, SS) - gy0) * gw - gx0;
                for (int c = 0; c < width; c++) {
                    int v = MemoryUtil.memGetByte(ligne + c) & 0xFF;
                    if (v != 0) red[l + Math.floorDiv(left + c, SS)] += v;
                }
            }
            float k = 1f / (255f * SS * SS);
            for (int yy = 0; yy < gh; yy++) {
                int y = iy + gy0 + yy;
                if (y < 0 || y >= H) continue;
                for (int xx = 0; xx < gw; xx++) {
                    int x = ix + gx0 + xx;
                    int v = red[yy * gw + xx];
                    if (v == 0 || x < 0 || x >= W) continue;
                    float a = v * k;
                    int p = y * W + x;
                    if (contour) {
                        if (a > dst[p]) dst[p] = a;
                    } else {
                        dst[p] += a;
                    }
                }
            }
        } finally {
            long gl = pg.get(0);
            if (gl != 0) {
                nFT_Done_Glyph(gl);
                COMPTEURS.glyphesLiberes++;
            }
        }
    }

    private static final float INK_R = ((ENCRE >> 16) & 0xFF) / 255f, INK_G = ((ENCRE >> 8) & 0xFF) / 255f,
        INK_B = (ENCRE & 0xFF) / 255f;

    /**
     * Composition (en flottants simples, comme le numpy float32 du Python), de bas en haut : ombre (masque du contour
     * décalé de d, ou {@code floue} : l'ombre douce déjà décalée et floutée, canevas entier), contour, remplissages dans
     * l'ordre d'apparition de leurs couleurs. S = max(contour, remplissage).
     */
    private static void composer(float[][] remp, int[] rgb, float[] trait, float[] floue, int W, int H, int cx0, int cy0,
                                 int w, int h, int d, float contour, float ombre, float gamma, int[] out) {
        int n = remp.length;
        float[] fr = new float[n], fg = new float[n], fb = new float[n];
        for (int t = 0; t < n; t++) {
            fr[t] = ((rgb[t] >> 16) & 0xFF) / 255f;
            fg[t] = ((rgb[t] >> 8) & 0xFF) / 255f;
            fb[t] = (rgb[t] & 0xFF) / 255f;
        }
        for (int yy = 0; yy < h; yy++) {
            int y = cy0 + yy;
            for (int xx = 0; xx < w; xx++) {
                int x = cx0 + xx;
                int p = y * W + x;
                float sd = floue != null ? floue[p] : (x >= d && y >= d) ? masque(remp, trait, p - d * W - d) : 0f;
                float S = masque(remp, trait, p);
                float A = 0f, cr = 0f, cg = 0f, cb = 0f;
                float a = sd * ombre;
                cr = INK_R * a + cr * (1f - a);
                cg = INK_G * a + cg * (1f - a);
                cb = INK_B * a + cb * (1f - a);
                A = a + A * (1f - a);
                a = S * contour;
                cr = INK_R * a + cr * (1f - a);
                cg = INK_G * a + cg * (1f - a);
                cb = INK_B * a + cb * (1f - a);
                A = a + A * (1f - a);
                for (int t = 0; t < n; t++) {
                    float F = Math.min(1f, Math.max(0f, remp[t][p]));
                    a = gamma == 1f ? F : (float) Math.pow(F, gamma);
                    cr = fr[t] * a + cr * (1f - a);
                    cg = fg[t] * a + cg * (1f - a);
                    cb = fb[t] * a + cb * (1f - a);
                    A = a + A * (1f - a);
                }
                out[yy * w + xx] = pixel(A, cr, cg, cb);
            }
        }
    }

    private static float masque(float[][] remp, float[] trait, int p) {
        float tot = 0f;
        for (float[] r : remp) tot = Math.max(tot, Math.min(1f, Math.max(0f, r[p])));
        return Math.max(Math.min(1f, Math.max(0f, trait[p])), tot);
    }

    /** Prémultiplié → alpha droit, octets floor(v × 255 + 0,5). */
    private static int pixel(float A, float cr, float cg, float cb) {
        float r = 0f, g = 0f, b = 0f;
        if (A > 1e-6f) {
            float div = Math.max(A, 1e-6f);
            r = cr / div;
            g = cg / div;
            b = cb / div;
        }
        return octet(A) << 24 | octet(r) << 16 | octet(g) << 8 | octet(b);
    }

    private static int octet(float v) {
        int o = (int) Math.floor(v * 255f + 0.5f);
        return o < 0 ? 0 : Math.min(255, o);
    }

    /* ================================================================ formes du HUD (contour rond + ombre) */

    /** Masque de forme pixel art : couverture 0..1, ligne par ligne. */
    public record Masque(int w, int h, float[] v) {

        /** Puce n×n aux coins coupés. */
        public static Masque puce(int n) {
            float[] v = new float[n * n];
            Arrays.fill(v, 1f);
            if (n >= 4) v[0] = v[n - 1] = v[(n - 1) * n] = v[n * n - 1] = 0f;
            return new Masque(n, n, v);
        }

        /** Barres alignées en bas (vumètre : 5 barres de 1 px au pas de 4 ; OST : 3 barres de 2 px au pas de 4). */
        public static Masque barres(int[] hauteurs, int largeur, int pas) {
            int hmax = 1;
            for (int hh : hauteurs) hmax = Math.max(hmax, hh);
            int w = pas * (hauteurs.length - 1) + largeur;
            float[] v = new float[hmax * w];
            for (int i = 0; i < hauteurs.length; i++) {
                for (int y = hmax - hauteurs[i]; y < hmax; y++) {
                    for (int x = i * pas; x < i * pas + largeur; x++) v[y * w + x] = 1f;
                }
            }
            return new Masque(w, hmax, v);
        }

        /** Losange de demi-diagonale r (2r + 1 px). */
        public static Masque losange(int r) {
            int n = 2 * r + 1;
            float[] v = new float[n * n];
            for (int dy = -r; dy <= r; dy++) {
                int hw = r - Math.abs(dy);
                for (int x = r - hw; x <= r + hw; x++) v[(dy + r) * n + x] = 1f;
            }
            return new Masque(n, n, v);
        }

        /** Filet de L px sur e px ; fondu sur le quart de chaque extrémité (boussole : min(1, (1 − u²) × 1,35)). */
        public static Masque filet(int longueur, int epaisseur, boolean fondu) {
            float[] v = new float[longueur * epaisseur];
            for (int i = 0; i < longueur; i++) {
                float a = 1f;
                if (fondu) {
                    float u = Math.abs((i + 0.5f - longueur / 2.0f) / (longueur / 2.0f));
                    a = Math.min(1f, Math.max(0f, (1f - u * u) * 1.35f));
                }
                for (int y = 0; y < epaisseur; y++) v[y * longueur + i] = a;
            }
            return new Masque(longueur, epaisseur, v);
        }

        /** « ‖ » : deux traits de t × h séparés de t. */
        public static Masque pause(int t, int h) {
            int w = 3 * t;
            float[] v = new float[h * w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < t; x++) {
                    v[y * w + x] = 1f;
                    v[y * w + 2 * t + x] = 1f;
                }
            }
            return new Masque(w, h, v);
        }

        /** Case de touche w × h aux coins de 2 px. */
        public static Masque caseTouche(int w, int h) {
            float[] v = new float[h * w];
            Arrays.fill(v, 1f);
            int[][] coins = {{0, 0}, {0, 1}, {1, 0}};
            for (int[] c : coins) {
                int yy = c[0], xx = c[1];
                v[yy * w + xx] = 0f;
                v[yy * w + w - 1 - xx] = 0f;
                v[(h - 1 - yy) * w + xx] = 0f;
                v[(h - 1 - yy) * w + w - 1 - xx] = 0f;
            }
            return new Masque(w, h, v);
        }
    }

    /**
     * Contour rond de rayon r px d'un masque (dilatation par un disque à ×{@code ss}, puis moyenne ss × ss), réuni au
     * masque ; agrandi de m = ceil(r) px de chaque côté ({@code contour_rond}). [0] = m.
     */
    static float[] contourRond(Masque masque, double r, int ss, int[] mSortie) {
        int h = masque.h(), w = masque.w();
        int m = (int) Math.ceil(r);
        double R = r * ss;
        int Ri = (int) Math.ceil(R);
        int H4 = (h + 2 * m) * ss, W4 = (w + 2 * m) * ss;
        float[] big = new float[H4 * W4];
        int o = m * ss;
        for (int dy = -Ri; dy <= Ri; dy++) {
            for (int dx = -Ri; dx <= Ri; dx++) {
                if (dx * dx + dy * dy > R * R + 1e-9) continue;
                for (int yy = 0; yy < h * ss; yy++) {
                    int ligne = (o + dy + yy) * W4 + o + dx;
                    int src = (yy / ss) * w;
                    for (int xx = 0; xx < w * ss; xx++) {
                        float v = masque.v()[src + xx / ss];
                        if (v > big[ligne + xx]) big[ligne + xx] = v;
                    }
                }
            }
        }
        int hh = h + 2 * m, ww = w + 2 * m;
        float[] red = new float[hh * ww];
        float inv = 1f / (ss * ss);
        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < ww; x++) {
                float sum = 0f;
                for (int a = 0; a < ss; a++) {
                    int l = (y * ss + a) * W4 + x * ss;
                    for (int b = 0; b < ss; b++) sum += big[l + b];
                }
                red[y * ww + x] = sum * inv;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = (y + m) * ww + x + m;
                red[p] = Math.max(red[p], masque.v()[y * w + x]);
            }
        }
        mSortie[0] = m;
        return red;
    }

    /**
     * Forme pixel art posée à 1:1 ({@code poser_forme}) : contour rond de rayon r à {@code contour} d'encre (0 = sans
     * contour, ombre seule, comme une case de touche) et ombre (le contour décalé de (d, d)) à {@code ombre}, couleur
     * RGB × {@code alpha}. Couche relative au coin haut gauche du masque : {@code x = y = -m}. Calcul pur Java,
     * depuis tout fil. Préréglage par défaut : {@link Preset#FORME} (80 % / 50 %), r = f, d = max(1, arrondi(f)).
     */
    public static Rendu forme(Masque masque, int rgb, double r, int d, float contour, float ombre, float alpha) {
        int h = masque.h(), w = masque.w();
        float[] S;
        int m;
        if (contour > 0) {
            int[] mo = new int[1];
            S = contourRond(masque, r, 4, mo);
            m = mo[0];
        } else {
            m = 1;
            S = new float[(h + 2) * (w + 2)];
            for (int y = 0; y < h; y++) System.arraycopy(masque.v(), y * w, S, (y + 1) * (w + 2) + 1, w);
        }
        int Hh = h + 2 * m, Ww = w + 2 * m;
        int HH = Hh + d, WW = Ww + d;
        float cR = ((rgb >> 16) & 0xFF) / 255f, cG = ((rgb >> 8) & 0xFF) / 255f, cB = (rgb & 0xFF) / 255f;
        int[] out = new int[HH * WW];
        for (int y = 0; y < HH; y++) {
            for (int x = 0; x < WW; x++) {
                float s2 = (y < Hh && x < Ww) ? S[y * Ww + x] : 0f;
                int ys = y - d, xs = x - d;
                float sd = (ys >= 0 && xs >= 0 && ys < Hh && xs < Ww) ? S[ys * Ww + xs] : 0f;
                int yf = y - m, xf = x - m;
                float F = (yf >= 0 && xf >= 0 && yf < h && xf < w) ? masque.v()[yf * w + xf] : 0f;
                float A = 0f, cr = 0f, cg = 0f, cb = 0f;
                float a = sd * ombre * alpha;
                cr = INK_R * a + cr * (1f - a);
                cg = INK_G * a + cg * (1f - a);
                cb = INK_B * a + cb * (1f - a);
                A = a + A * (1f - a);
                if (contour > 0) {
                    a = s2 * contour * alpha;
                    cr = INK_R * a + cr * (1f - a);
                    cg = INK_G * a + cg * (1f - a);
                    cb = INK_B * a + cb * (1f - a);
                    A = a + A * (1f - a);
                }
                a = F * alpha;
                cr = cR * a + cr * (1f - a);
                cg = cG * a + cg * (1f - a);
                cb = cB * a + cb * (1f - a);
                A = a + A * (1f - a);
                out[y * WW + x] = pixel(A, cr, cg, cb);
            }
        }
        COMPTEURS.formes++;
        return new Rendu(-m, -m, WW, HH, out, null);
    }

    /** Forme au préréglage {@link Preset#FORME} pour le facteur f (r = f, d = max(1, arrondi(f))). */
    public static Rendu forme(Masque masque, int rgb, double f) {
        return forme(masque, rgb, f, Math.max(1, arrondi(f)), Preset.FORME.contour, Preset.FORME.ombre, 1f);
    }

    /* ================================================================ échelles du lot (brief §0.1, §1.1) */

    /** kHud = max(2, échelle menu) : OST et bas-droite (k = kHud / 2 multiplie les distances mesurées en 1080p). */
    public static int kHud() { return Math.max(2, DaKit.echelleMenu()); }

    /** f du récit = max(kHud / 2, H / 1080). */
    public static double facteurRecit(int kHud, int hauteurFenetre) {
        return Math.max(kHud / 2.0, hauteurFenetre / 1080.0);
    }

    /** Capitales du récit (12 px en 1080p). */
    public static double capRecit(double f) { return 12.0 * f; }

    /** Pas de ligne du récit (21 px en 1080p, entier). */
    public static int pasRecit(double f) { return arrondi(21.0 * f); }

    /** Largeur maximale du bloc de récit (340 px en 1080p). */
    public static double largeurRecit(double f) { return 340.0 * f; }
}
