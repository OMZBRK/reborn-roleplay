package fr.reborn.hud.menu.fiche;

/**
 * Repères de mise en page (maquette finale tour 4), en px GUI. Référence 960×540 (MenuScale : échelle 2 en 1080p) ;
 * ce qui est ancré en bas suit d = H − 540, les planches suivent les bords. Calculé dans {@code init()} /
 * {@code refresh()} seulement.
 *
 * <p>Mode compact (W &lt; 840 ou H &lt; 470 : 800×450, 683×384) : personnage à 4 px/texel, planches collées aux bords
 * et raccourcies par coupe, lanternes des Attributs resserrées.
 */
final class FicheLayout {

    final int w, h, cx, d;
    final boolean compact;

    /* ancrés en bas */
    final int ySol, piedsY, cartoucheX, cartoucheY, aidesY, aidesDroite, hitoY;

    /* en-tête */
    final int enteteX;
    final int[] tabX = new int[4];
    final int fermerX, fermerY = 12;

    /* planches */
    final int panY = 84;
    final int panG, panD, listeX;
    /** Bas maximal d'une planche gauche (au-dessus du cartouche) et droite (au-dessus des aides). */
    final int basMaxG, basMaxD;

    /* attributs */
    final int[] lanX = new int[6];
    final int matG, matD;
    final int barreX, barreY;
    /** Sommet d'une lanterne Novice ; un palier = 40 GUI. */
    final int yTop0;
    /** Un palier = 40 GUI (moins en compact, pour que la Légendaire reste sous les onglets). */
    final int pasPalier;
    final int yPiquet, yEma;

    /* personnage */
    final float echelle;
    final int px;            // x du personnage dans l'onglet Techniques

    FicheLayout(int w, int h) {
        this.w = w;
        this.h = h;
        this.cx = w / 2;
        this.d = h - 540;
        this.compact = w < 840 || h < 470;

        ySol = 404 + d;
        piedsY = 452 + d;
        cartoucheX = compact ? 8 : 24;
        cartoucheY = 488 + d;
        aidesY = 509 + d;
        aidesDroite = w - (compact ? 10 : 24);
        hitoY = 450 + d;

        enteteX = cx - 160;
        int[] dx = {-120, -40, 40, 120};
        for (int i = 0; i < 4; i++) tabX[i] = cx + dx[i];
        fermerX = w - 40;

        panG = compact ? 8 : 24;
        panD = compact ? w - 8 - FicheArt.PW : w - 24 - FicheArt.PW;
        listeX = panD;
        basMaxG = cartoucheY - 8;
        basMaxD = aidesY - 14;

        int[] gauche = compact ? new int[]{50, 146, 242} : new int[]{86, 184, 282};
        for (int i = 0; i < 3; i++) {
            lanX[i] = gauche[i];
            lanX[5 - i] = w - gauche[i];
        }
        matG = 20;
        matD = w - 22;
        barreX = cx - 180;
        barreY = 466 + d;
        yTop0 = 290 + d;
        pasPalier = Math.max(8, Math.min(40, (yTop0 - 88) / 5));
        yPiquet = 362 + d;
        yEma = 372 + d;

        echelle = compact ? FichePortrait.ECHELLE_COMPACTE : FichePortrait.ECHELLE;
        px = cx;
    }

    /** Sommet d'une lanterne des Attributs : une seule échelle pour les six, par PALIER (la valeur reste cachée). */
    int yTop(int palier) {
        return yTop0 - pasPalier * palier;
    }

    /** Hauteur utile d'une planche (coupée si l'écran est trop bas). */
    int hauteurPanneau(int hNominale, boolean droite) {
        int max = (droite ? basMaxD : basMaxG) - panY;
        return Math.max(110, Math.min(hNominale, max));
    }

    /**
     * Couronne (Identité, Techniques, État) : x = cx + (i − 2,5)·42, y = 120 + 52·((x − cx)/105)² + osc ; en compact,
     * resserrée entre les planches et au-dessus de la tête.
     */
    int[][] arc() {
        int[] osc = {1, -1, 0, 1, -1, 0};
        int[][] out = new int[6][2];
        double pas = compact ? Math.max(24, Math.min(42, (panD - panG - FicheArt.PW - 36) / 5.0)) : 42;
        double haut = compact ? 98 : 120, fleche = compact ? 24 : 52;
        for (int i = 0; i < 6; i++) {
            double x = cx + (i - 2.5) * pas;
            double t = (x - cx) / (pas * 2.5);
            out[i][0] = (int) Math.round(x);
            out[i][1] = (int) Math.round(haut + fleche * t * t + osc[i]);
        }
        return out;
    }
}
