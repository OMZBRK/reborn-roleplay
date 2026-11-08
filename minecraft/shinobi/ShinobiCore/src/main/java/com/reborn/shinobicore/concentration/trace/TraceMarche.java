package com.reborn.shinobicore.concentration.trace;

/**
 * La marche d'un joueur entre deux relevés (toutes les 4 ticks), sans dépendance au serveur : où tombent les pas le long
 * du segment parcouru, à l'espacement voulu, et quel pied. Une téléportation (changement de monde, plus de {@value #SAUT}
 * blocs en un relevé au sol) ne trace rien. Un bond ne coupe pas la marche : rien en l'air, un pas à l'atterrissage.
 */
final class TraceMarche {

    /** Au-delà, ce n'est plus une marche (blocs parcourus en un relevé). */
    static final double SAUT = 6.0;
    /** Pas posés en un relevé au plus. */
    static final int MAX_PAR_RELEVE = 4;

    private String monde;
    private double x, z;
    private boolean amorce;
    /** Distance parcourue depuis le dernier pas. */
    private double parcouru;
    private boolean droit;
    private int rang;
    /** Pieds en l'air depuis le dernier relevé au sol. */
    private boolean enLair;
    private double capVu;
    /* Vitesse réelle, mesurée sur au moins FENETRE_TICKS ticks : un paquet de position en retard la fausserait moins. */
    private static final int FENETRE_TICKS = 8;
    private boolean mesureAmorcee, airFenetre, airMesure;
    private int tickMesure;
    private double mx, mz, vitesse;

    /** Exclu du marquage : le prochain relevé repart de là où il est. */
    void couper() {
        amorce = false;
        enLair = false;
        mesureAmorcee = false;
        vitesse = 0;
    }

    /** Pieds en l'air : la marche continue, le prochain relevé au sol est un atterrissage. */
    void enLair() { if (amorce) enLair = true; }

    /** Relevé de position : met à jour la vitesse mesurée ({@link #vitesse()}, blocs/s). */
    void mesurer(int tick, double nx, double nz, boolean auSol) {
        if (!mesureAmorcee) {
            mesureAmorcee = true;
            tickMesure = tick;
            mx = nx;
            mz = nz;
            airFenetre = !auSol;
            return;
        }
        airFenetre |= !auSol;
        int dt = tick - tickMesure;
        if (dt < FENETRE_TICKS) return;
        vitesse = Math.hypot(nx - mx, nz - mz) * 20.0 / dt;
        airMesure = airFenetre;
        tickMesure = tick;
        mx = nx;
        mz = nz;
        airFenetre = !auSol;
    }

    double vitesse() { return vitesse; }

    /** La dernière vitesse a-t-elle été mesurée sur un saut ? */
    boolean mesureEnSaut() { return airMesure; }

    /**
     * Le joueur est maintenant en {@code (nx, nz)}.
     *
     * @param sortie reçoit {x, z, cap} par pas (au moins {@code 3 * MAX_PAR_RELEVE} cases)
     * @return le nombre de pas posés
     */
    int avancer(String nmonde, double nx, double nz, double espacement, double[] sortie) {
        if (!amorce || !nmonde.equals(monde)) {
            monde = nmonde;
            x = nx;
            z = nz;
            amorce = true;
            enLair = false;
            return 0;
        }
        double dx = nx - x, dz = nz - z, d = Math.sqrt(dx * dx + dz * dz);
        if (enLair) return atterrir(nx, nz, dx, dz, d, espacement, sortie);
        if (d > SAUT) {
            x = nx;
            z = nz;
            parcouru = 0;
            return 0;
        }
        if (d < 1e-4) return 0;
        double cap = Math.toDegrees(Math.atan2(dx, dz));
        capVu = cap;
        int n = 0;
        double fait = 0;
        while (n < MAX_PAR_RELEVE && parcouru + (d - fait) >= espacement) {
            fait += espacement - parcouru;
            parcouru = 0;
            double u = fait / d;
            sortie[n * 3] = x + dx * u;
            sortie[n * 3 + 1] = z + dz * u;
            sortie[n * 3 + 2] = cap;
            n++;
        }
        parcouru = n == MAX_PAR_RELEVE ? 0 : parcouru + (d - fait);
        x = nx;
        z = nz;
        return n;
    }

    /**
     * Premier relevé au sol après un bond : un seul pas, là où il retombe, quelle que soit la longueur du bond. Un petit saut
     * sur place compte comme de la marche : sauter sans avancer ne pose rien (et ne peut donc pas vider l'anneau du tronçon).
     */
    private int atterrir(double nx, double nz, double dx, double dz, double d, double espacement, double[] sortie) {
        enLair = false;
        x = nx;
        z = nz;
        if (parcouru + d < espacement) {
            parcouru += d;
            return 0;
        }
        if (d >= 1e-4) capVu = Math.toDegrees(Math.atan2(dx, dz));
        parcouru = 0;
        sortie[0] = nx;
        sortie[1] = nz;
        sortie[2] = capVu;
        return 1;
    }

    /** Le pied du prochain pas (ils alternent) : vrai = droit. */
    boolean piedSuivant() {
        droit = !droit;
        rang++;
        return droit;
    }

    int rang() { return rang; }
}
