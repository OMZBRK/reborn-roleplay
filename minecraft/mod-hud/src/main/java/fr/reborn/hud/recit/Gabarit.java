package fr.reborn.hud.recit;

import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Demande;
import fr.reborn.hud.ui.TexteRaster.Equilibrage;
import fr.reborn.hud.ui.TexteRaster.Police;
import fr.reborn.hud.ui.TexteRaster.Preset;

/**
 * <b>Allure d'un texte au centre</b>, tous ses réglages visuels en un seul endroit : police, préréglage de lisibilité
 * (contour, ombre, gamma), capitales et interligne en 1080p, largeur du bloc, centre vertical, contour, ombre douce,
 * approche, coupes de ligne, voile plein écran, fondus, règle d'échelle. Deux allures distinctes (décision du 02/11 :
 * « tu ne fais aucunement la distinction entre narration et [le] principe de stèle ») :
 * <ul>
 *   <li>{@link #NARRATION} : la narration du staff ({@code /narration}, events, canal {@code reborn:narration}), en
 *       <b>plein écran, blanc pur, plus grande</b> : mise en page de la variante <b>V2 « Titre droit »</b> de la planche
 *       {@code Maquettes/gameplan/17_lot2_ketto/d1_narration_plein_ecran.png} (choisie le 02/11), en <b>Philosopher
 *       Bold</b> (choix du 02/11 au soir, à la place de Marcellus) ; des narrations <b>courtes</b> (3 lignes par page au
 *       plus : « pas trop long ») ; fondus d'opacité seuls mesurés sur les vidéos de Ketto (entrée 0,6 s, sortie 0,7 s).
 *       Tout changement d'allure se fait ici, et nulle part ailleurs. V1 reste prête ({@link #NARRATION_V1}).</li>
 *   <li>{@link #STELE} : le texte des stèles, plaques, pancartes et objets, lu <b>seulement en concentration, en visant
 *       l'objet</b> (lot 4) ; c'est le rendu des maquettes a1 à a8 validées le 01/11 (taille Ketto), avec les fondus
 *       mesurés chez Ketto le 02/11 (entrée 0,25 s, sortie 0,35 s). Pendant la concentration, les bords de l'écran
 *       s'assombrissent légèrement ({@link #BORDS_CONCENTRATION}, {@link Recit#concentration}).</li>
 * </ul>
 * Les deux gardent les mots de couleur (blanc par défaut), la typographie française du moteur ({@link TexteRaster}) et
 * les mêmes règles de place et de visibilité ({@link RecitHud} : à terre, inconscient, écran noir, tchat, F1, mode
 * photo, noms à 25 %). Les durées et la file (contrat de la narration) sont dans {@link Recit}, pas ici.
 *
 * <p>Tailles en pixels écran, à l'échelle f de la fenêtre ({@link #echelle}). Rien n'est alloué par ces calculs (image
 * par image), sauf {@link #ombrePx} (à garder d'une image à l'autre).
 *
 * @param nom          nom court (journaux des bancs)
 * @param police       police du texte
 * @param preset       préréglage de lisibilité : opacité du contour, de l'ombre, gamma du remplissage
 * @param capitales    hauteur des capitales en 1080p (px ; le moteur cale l'em sur une hauteur d'x entière)
 * @param interligne   pas des lignes en 1080p (px ; arrondi au pixel entier à l'échelle de la fenêtre)
 * @param largeurPx    largeur maximale du bloc en 1080p (px × f) ; 0 = seulement {@code largeurEcran}
 * @param largeurEcran largeur maximale du bloc en part de la largeur de l'écran (0 à 1) ; avec {@code largeurPx}, la
 *                     plus petite des deux
 * @param centre       centre vertical du bloc par défaut, en part de la hauteur de l'écran ({@code pos} le remplace)
 * @param bas          0 : le bloc est centré sur {@code centre} ; sinon le <b>bas</b> du bloc est ancré à cette part de la
 *                     hauteur (le bloc grandit vers le haut, au-dessus du réticule ; coupes figées seulement)
 * @param rayon        rayon du contour rond en 1080p (px × f)
 * @param rayonMin     0 : rayon × f exact ; sinon le rayon est arrondi au 1/16 px et vaut au moins {@code rayonMin} px
 * @param ombre        ombre douce (décalage, flou en 1080p), ou null : l'ombre dure de la recette (contour décalé de
 *                     max(1, arrondi(rayon)) px en diagonale)
 * @param interlettre  approche en 1080p (px × f ajoutés entre deux lettres)
 * @param coupesFigees vrai : coupes de ligne (et pages) calculées une fois à la référence 1920 × 1080, gardées telles
 *                     quelles à toute taille de fenêtre (tout le monde lit les mêmes lignes, l'aperçu du staff est celui
 *                     des joueurs) ; faux : recoupées à la largeur de la fenêtre
 * @param coupesDeSens avec les coupes figées : lignes coupées de préférence après . ! ? : ; … (segments regroupés tant
 *                     que la ligne tient, un segment trop long coupé par le moteur ; gardé s'il n'ajoute pas plus d'une
 *                     ligne à la coupe ordinaire), recette {@code coupes_de_sens} de la planche d1
 * @param lignesMax    lignes par page au plus (au-delà : des pages) ; la fenêtre peut en imposer moins
 * @param voile        voile plein écran à l'encre sous le texte, opacité (0 = aucun), qui suit le fondu du texte
 * @param vignette     vignette plein écran sous le texte : assombrissement aux coins (0 = aucune ; 0,30 = -30 %), centre
 *                     intact, smoothstep de 30 % à 100 % de la distance au coin (recette {@code vignette} de la planche
 *                     d1), qui suit le fondu du texte
 * @param entreeMs     fondu d'entrée (opacité seule, ease-out) de la première page montrée
 * @param sortieMs     fondu de sortie à la fin (durée écoulée, {@code narration_fin})
 * @param coupeMs      fondu quand le texte est coupé ({@code remplace}, une autre plaque, une narration qui passe devant)
 * @param pageMs       fondu entre deux pages
 * @param echelle      règle d'échelle avec la fenêtre
 */
public record Gabarit(String nom, Police police, Preset preset, double capitales, double interligne, double largeurPx,
                      double largeurEcran, double centre, double bas, double rayon, double rayonMin, OmbreDouce ombre,
                      double interlettre, boolean coupesFigees, boolean coupesDeSens, int lignesMax, float voile,
                      float vignette, long entreeMs, long sortieMs, long coupeMs, long pageMs, Echelle echelle) {

    /** Règle d'échelle avec la fenêtre (W × H pixels écran). */
    public enum Echelle {
        /** Comme le HUD du lot : kHud / 2 (kHud = max(2, échelle menu), selon la taille de la fenêtre). */
        HUD,
        /** Proportionnel à la hauteur de l'écran : H / 1080, sans dépendre de l'échelle d'interface (planche d3). */
        ECRAN,
        /** Comme le HUD, jamais plus petit que la proportion de l'écran : max(kHud / 2 ; H / 1080) (f du brief §1.1). */
        HUD_OU_ECRAN
    }

    /**
     * Ombre douce en 1080p : décalage ({@code dx}, {@code dy}) px et flou gaussien σ = {@code flou} px, à l'opacité d'ombre
     * du préréglage. À l'échelle f (planche d3) : dx = arrondi(dx × f), dy = max(1 ; arrondi(dy × f)), σ = flou × f.
     */
    public record OmbreDouce(double dx, double dy, double flou) { }

    /** Référence des coupes figées : 1920 × 1080, f = 1. */
    public static final int REF_W = 1920, REF_H = 1080;

    /**
     * Narration du staff, décisions du 02/11 : plein écran, blanc pur, plus grande, mise en page de la <b>variante V2
     * « Titre droit »</b> de la planche d1 ({@code _maquettes_narration_d.py}, {@code VARIANTES["V2"]}), en
     * <b>Philosopher Bold</b> (OFL, {@code assets/reborn/font/narration/}) à la place de Marcellus Regular (rejetée :
     * « apparence qui ne corrèle pas avec l'univers ») : blanc pur #FFFFFF sans mot coloré par défaut, capitales gardées
     * (33,6 px demandés, 33,7 obtenus après calage de la hauteur d'x sur 24 px, comme Marcellus), interligne 58 px en
     * 1080p, approche 0 (celle de la planche e1 sur laquelle Philosopher Bold a été choisie ; Marcellus en voulait +1,4), bloc
     * de 1 536 px (80 %) au plus et 90 % de l'écran, <b>bas du bloc à 47 %</b> (le bloc grandit vers le haut : réticule
     * et tête libres), contour rond de 2 px à 100 % (au 1/16 px, 1 px au moins), ombre douce 2 px vers le bas, flou σ 3
     * px, 40 %, vignette très douce (coins -30 %, centre intact) qui suit le fondu ; coupes de sens figées à la référence
     * 1080p ; tout × s = H/1080. <b>3 lignes par page au plus</b> (« pas trop long », mesuré chez Ketto : 3 lignes),
     * des pages au-delà. Fondus mesurés sur les vidéos de Ketto (02/11) : <b>opacité seule</b>, sans glissement ni
     * changement de taille, entrée 600 ms, sortie 700 ms ; entre deux pages, 600 ms de chaque côté ; une coupe
     * ({@code remplace}) reste rapide (150 ms). La tenue automatique est dans {@link Recit#dureeAutoMs(java.util.List, Gabarit)}.
     */
    public static final Gabarit NARRATION = new Gabarit("narration",
        Police.PHILOSOPHER,                              // Philosopher Bold
        new Preset("NARRATION", 1.00f, 0.40f, 0.85f),    // contour, ombre, gamma
        33.6, 58.0,                                      // capitales, interligne (px 1080p)
        1536.0, 0.90,                                    // largeur max : px 1080p, part de l'écran (la plus petite)
        0.40, 0.47,                                      // centre vertical ; bas du bloc ancré à 47 % de H
        2.0, 1.0,                                        // contour : rayon (px 1080p), minimum au 1/16 px
        new OmbreDouce(0.0, 2.0, 3.0),                   // ombre douce : 2 px vers le bas, σ 3 px
        0.0,                                             // approche (px 1080p) : 0, comme la planche e1 validée
        true, true,                                      // coupes figées à la référence 1080p ; coupes de sens
        3,                                               // lignes par page au plus (narrations courtes)
        0f, 0.30f,                                       // voile plein écran ; vignette (coins -30 %)
        600, 700, 150, 600,                              // fondus : entrée, sortie, coupe, page (ms) — vidéos de Ketto
        Echelle.ECRAN);

    /**
     * Planche d1, variante <b>V1 « Grand récit »</b> (recommandée ; révision du 02/11 à 11 h, LISEZMOI et
     * {@code _maquettes_narration_d.py}) : Alegreya Sans Bold Italic, capitales 38 px (37,4 après calage de la hauteur
     * d'x), interligne 64 px, bloc de 1 536 px (80 %) au plus et 90 % de l'écran, <b>bas du bloc à 47 %</b> (au-dessus du
     * réticule), contour rond de 2 px à 100 % (au 1/16 px, 1 px au moins), ombre douce 2 px vers le bas, flou σ 3 px,
     * 40 %, aucun effet d'écran ; tout × H/1080 sans dépendre de l'échelle d'interface ; coupes de sens, figées à la
     * référence 1080p (planche d3) ; 4 lignes par page (proposition de la planche). Non retenue le 02/11 (V2 choisie),
     * gardée pour comparer au banc.
     */
    public static final Gabarit NARRATION_V1 = new Gabarit("narration_v1",
        Police.RECIT,
        new Preset("NARRATION_V1", 1.00f, 0.40f, 0.85f),
        38.0, 64.0,
        1536.0, 0.90,
        0.40, 0.47,
        2.0, 1.0,
        new OmbreDouce(0.0, 2.0, 3.0),
        0.0,
        true, true,
        4,
        0f, 0f,
        600, 700, 150, 600,
        Echelle.ECRAN);

    /**
     * Stèles, plaques, pancartes et objets lus en concentration (lot 4) : le texte au centre des maquettes a1 à a8 (v2
     * du 01/11), à la taille vue chez Ketto — capitales 12 px, interligne 21 px, bloc de 340 px au plus, centre à 54 %,
     * préréglage RÉCIT (contour rond de 1 px à 100 %, ombre dure à 45 %), f = max(kHud/2 ; H/1080). Validé : ne pas
     * changer l'allure. Fondus mesurés chez Ketto le 02/11 : entrée 250 ms, sortie 350 ms (fin de la concentration ou de
     * la visée), le texte reste tant que la concentration dure (aucune minuterie : {@link Recit#lire}), il s'efface sous
     * une narration ; aucun effet d'écran propre (l'assombrissement des bords suit la concentration, pas le texte :
     * {@link #BORDS_CONCENTRATION}).
     */
    public static final Gabarit STELE = new Gabarit("stele",
        Police.RECIT, Preset.RECIT,
        12.0, 21.0,
        340.0, 0.0,
        0.54, 0.0,
        1.0, 0.0,
        null,
        0.0,
        false, false,
        Recit.LIGNES_PAR_PAGE,
        0f, 0f,
        250, 350, 150, 150,
        Echelle.HUD_OU_ECRAN);

    /**
     * Concentration (lot 4, {@link Recit#concentration}) : léger assombrissement des bords de l'écran tant qu'elle dure,
     * vu sur les vidéos de Ketto (02/11 : visible sur les images, sans variation mesurable de la luminance moyenne) —
     * la vignette de la narration (smoothstep de 30 % à 100 % de la distance au coin), coins -18 %, centre intact.
     * Avec une narration, la plus forte des deux vignettes (jamais leur somme).
     */
    public static final float BORDS_CONCENTRATION = 0.18f;
    /**
     * Fondus de l'assombrissement des bords : entrée 250 ms, sortie <b>150 ms</b> (lot 4, CADRAGE §6.2 : les couleurs
     * reviennent avant que le texte d'une plaque ne s'efface, en 350 ms). Repli du filtre de la concentration
     * ({@code ModeConcentration}), qui a les mêmes durées.
     */
    public static final long BORDS_ENTREE_MS = 250, BORDS_SORTIE_MS = 150;

    /** Facteur d'échelle f pour une fenêtre de {@code W} × {@code H} pixels écran. */
    public double facteur(int W, int H) {
        return switch (echelle) {
            case HUD -> TexteRaster.kHud() / 2.0;
            case ECRAN -> Math.max(1, H) / 1080.0;
            case HUD_OU_ECRAN -> TexteRaster.facteurRecit(TexteRaster.kHud(), Math.max(1, H));
        };
    }

    /** Capitales (px écran) au facteur f. */
    public double cap(double f) { return capitales * f; }

    /** Pas des lignes (px écran entiers) au facteur f. */
    public int pas(double f) { return TexteRaster.arrondi(interligne * f); }

    /** Largeur maximale du bloc (px écran) au facteur f, pour un écran de {@code W} px de large. */
    public double largeur(double f, int W) {
        double ecran = largeurEcran * Math.max(1, W);
        if (largeurPx <= 0) return ecran;
        return largeurEcran > 0 ? Math.min(largeurPx * f, ecran) : largeurPx * f;
    }

    /** Rayon du contour (px écran) au facteur f : le {@code f} de la demande au moteur. */
    public double contour(double f) {
        return rayonMin > 0 ? Math.max(rayonMin, Math.rint(rayon * f * 16) / 16.0) : rayon * f;
    }

    /** Approche (px écran) au facteur f. */
    public double approche(double f) { return interlettre * f; }

    /** Ombre douce en px écran au facteur f (alloue : à garder d'une image à l'autre), ou null (ombre dure). */
    public TexteRaster.Ombre ombrePx(double f) {
        if (ombre == null) return null;
        return new TexteRaster.Ombre(TexteRaster.arrondi(ombre.dx() * f), Math.max(1, TexteRaster.arrondi(ombre.dy() * f)),
            ombre.flou() * f);
    }

    /** Largeur donnée au moteur pour poser une page : sans limite si les coupes sont figées (retours à la ligne). */
    public double largeurPose(double f, int W) { return coupesFigees ? TexteRaster.SANS_LIMITE : largeur(f, W); }

    /** Équilibrage pour poser une page : aucun si les coupes sont figées (elles le sont déjà). */
    public Equilibrage equilibragePose() { return coupesFigees ? Equilibrage.JAMAIS : Equilibrage.AUTO; }

    /**
     * Lignes par page : {@link #lignesMax} (12 pour la stèle, contrat du brief §2.3 ; 3 pour la narration courte), moins
     * si la fenêtre ne peut pas les montrer avec une ligne de marge en haut et en bas (petite fenêtre, grande police).
     */
    public int lignesParPage(int H, int pas) {
        return Math.max(1, Math.min(Math.max(1, lignesMax), (Math.max(1, H) - 2 * pas) / Math.max(1, pas)));
    }

    /**
     * Demande de mise en page du texte entier (découpe en lignes et en pages) : bloc centré sur (cx, cy), à l'échelle f
     * d'un écran de {@code W} px de large, largeur du gabarit, équilibrage automatique.
     */
    public Demande bloc(String texte, double f, int W, double cx, double cy) {
        return Demande.bloc(texte, police, preset, cap(f), largeur(f, W), pas(f), contour(f), cx, cy, approche(f),
            Equilibrage.AUTO, null);
    }
}
