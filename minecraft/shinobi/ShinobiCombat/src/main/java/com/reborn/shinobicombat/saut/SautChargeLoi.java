package com.reborn.shinobicombat.saut;

/**
 * Loi du <b>saut chargé</b> (lot 5, étape A, 09/11), sans serveur ni monde : charge, vitesse de départ, coût, recharge,
 * vol à l'air libre, portée à plat, bornage de la portée et de la visée. <b>Le même code vit dans le mod</b>
 * ({@code fr.reborn.hud.combat.SautChargeLoi}) : le cercle d'arrivée du joueur et la validation du serveur ne peuvent
 * pas diverger. Toute retouche se fait des deux côtés, et les deux suites de tests vérifient le même tableau.
 *
 * <p><b>Vol</b> : la vitesse est posée au départ, puis chaque tick, dans cet ordre (celui du jeu dans
 * {@code LivingEntity.travelInAir}) : déplacement, gravité de 0,08 retirée, puis vertical × 0,98 (vanilla) et horizontal
 * × {@link Regles#trainee()} (0,98 au lieu du 0,91 vanilla). <b>Aucune poussée en l'air</b> : la trajectoire ne dépend
 * que du départ, donc le cercle dit vrai.
 *
 * <p><b>Loi</b> (demande du 09/11 : « faut aller loin », sommet gardé à 40, une charge courte ne va jamais moins loin
 * qu'avant au même temps de maintien) : {@code s = 1,5 + 1,65 × c} blocs par tick, élan vertical {@code 0,6 + 0,35 × c},
 * visée bornée à 0° vers le bas, {@code vy ≤ 3,0}. Pleine charge (1,2 s) : 60 blocs à plat, 92 au mieux (29°),
 * sommet 40. Coût {@code 0,3 + 2,7 × c²} % de la réserve, recharge {@code 5 + 25 × c} s.
 */
public final class SautChargeLoi {

    /** Gravité du joueur (attribut {@code GRAVITY} vanilla), retirée chaque tick. */
    public static final double GRAVITE = 0.08;
    /** Frottement vertical vanilla, inchangé. */
    public static final double TRAINEE_V = 0.98;
    /** Ticks simulés au plus (20 s) : au-delà, le vol est considéré sans fin. */
    public static final int TICKS_MAX = 400;

    /**
     * Réglages, tous en config côté serveur ({@code mobility.saut-charge}) et poussés au mod à la connexion.
     *
     * @param chargeMs       durée d'une charge pleine
     * @param seuil          part de charge sous laquelle rien ne part (0,1125 × 1,2 s = 135 ms, le seuil d'avant)
     * @param vitesseMin     vitesse de départ à charge nulle, blocs par tick
     * @param vitessePente   vitesse ajoutée à pleine charge
     * @param elanMin        élan vertical à charge nulle, ajouté même à plat
     * @param elanPente      élan ajouté à pleine charge
     * @param vyMax          vitesse verticale de départ au plus (3,0 : sommet de 40 blocs)
     * @param trainee        frottement horizontal par tick en vol
     * @param coutMinPct     coût à charge nulle, en % de la réserve
     * @param coutMaxPct     coût à pleine charge, en % de la réserve (croît avec c²)
     * @param rechargeMinMs  recharge à charge nulle
     * @param rechargeMaxMs  recharge à pleine charge (croît avec c)
     * @param porteeMax      portée à plat au plus, en blocs (96 : sous la distance de suivi des meubles)
     * @param viseeMaxDeg    écart au plus entre la visée envoyée et le regard connu du serveur
     */
    public record Regles(long chargeMs, double seuil, double vitesseMin, double vitessePente, double elanMin,
                         double elanPente, double vyMax, double trainee, double coutMinPct, double coutMaxPct,
                         long rechargeMinMs, long rechargeMaxMs, double porteeMax, double viseeMaxDeg) {

        public static final Regles DEFAUT = new Regles(1200L, 0.1125, 1.5, 1.65, 0.6, 0.35, 3.0, 0.98,
                0.3, 3.0, 5000L, 30000L, 96.0, 25.0);

        /** Temps de maintien minimal (ms) pour que le saut parte. */
        public long seuilMs() { return Math.round(seuil * chargeMs); }
    }

    private SautChargeLoi() {}

    /* ============================================================ charge, coût, recharge */

    /** Part de charge (0 à 1) pour un temps de maintien en ms. */
    public static double charge(long tenuMs, Regles r) {
        if (tenuMs <= 0 || r.chargeMs() <= 0) return 0;
        return Math.min(1.0, tenuMs / (double) r.chargeMs());
    }

    /** La charge suffit-elle à faire partir le saut ? (marge d'un millième pour les arrondis de l'horloge) */
    public static boolean suffisante(double c, Regles r) { return c + 1e-9 >= r.seuil(); }

    /** Coût en % de la réserve de chakra : {@code min + (max − min) × c²}. */
    public static double coutPct(double c, Regles r) {
        double k = clamp01(c);
        return r.coutMinPct() + (r.coutMaxPct() - r.coutMinPct()) * k * k;
    }

    /** La plus forte charge que {@code dispoPct} % de réserve peut payer (0 si même la charge nulle est trop chère). */
    public static double chargePayable(double dispoPct, Regles r) {
        if (dispoPct < r.coutMinPct()) return 0;
        double span = r.coutMaxPct() - r.coutMinPct();
        if (span <= 0) return 1;
        return clamp01(Math.sqrt((dispoPct - r.coutMinPct()) / span));
    }

    /** Recharge en ms : {@code min + (max − min) × c}. */
    public static long rechargeMs(double c, Regles r) {
        return r.rechargeMinMs() + Math.round((r.rechargeMaxMs() - r.rechargeMinMs()) * clamp01(c));
    }

    /* ============================================================ vitesse de départ */

    /** Vitesse de départ (blocs par tick) le long de la visée. */
    public static double vitesse(double c, Regles r) { return r.vitesseMin() + r.vitessePente() * clamp01(c); }

    /** Élan vertical ajouté, même à plat. */
    public static double elan(double c, Regles r) { return r.elanMin() + r.elanPente() * clamp01(c); }

    /**
     * Vitesse de départ monde {@code {vx, vy, vz}} pour une charge et une visée (convention du jeu : lacet en degrés,
     * {@code tangage > 0} = regard vers le bas). Viser vers le bas revient à viser à plat.
     */
    public static double[] vitesseDepart(double c, double lacetDeg, double tangageDeg, Regles r) {
        double s = vitesse(c, r);
        double elev = Math.toRadians(Math.max(0.0, Math.min(90.0, -tangageDeg)));
        double vh = s * Math.cos(elev);
        double vy = Math.min(r.vyMax(), s * Math.sin(elev) + elan(c, r));
        double yr = Math.toRadians(lacetDeg);
        return new double[] {-Math.sin(yr) * vh, vy, Math.cos(yr) * vh};
    }

    /* ============================================================ vol à l'air libre */

    /** Un tick de vol à l'air libre : {@code pos += v}, puis gravité et frottements. Tableaux de 3, modifiés sur place. */
    public static void pas(double[] pos, double[] v, Regles r) {
        pos[0] += v[0];
        pos[1] += v[1];
        pos[2] += v[2];
        avancerVitesse(v, r);
    }

    /** Gravité puis frottements, après le déplacement du tick (même ordre que le jeu). */
    public static void avancerVitesse(double[] v, Regles r) {
        v[1] = (v[1] - GRAVITE) * TRAINEE_V;
        v[0] *= r.trainee();
        v[2] *= r.trainee();
    }

    /**
     * Portée horizontale sur un sol plat (arrivée à la hauteur du départ), en blocs. Comme dans le jeu, le tick de la
     * réception avance en entier à l'horizontale (la collision coupe la chute, pas l'avance) : pas d'interpolation, la
     * portée est celle que le corps atteint vraiment. Proportionnelle à {@code vh} : la durée ne dépend que de {@code vy}.
     */
    public static double porteeAPlat(double vh, double vy, Regles r) {
        double x = 0, y = 0, h = vh, v = vy;
        for (int t = 0; t < TICKS_MAX; t++) {
            double nx = x + h, ny = y + v;
            if (t > 0 && ny <= 0) return nx;
            x = nx;
            y = ny;
            v = (v - GRAVITE) * TRAINEE_V;
            h *= r.trainee();
        }
        return x;
    }

    /** Durée du vol sur sol plat, en ticks (arrondie au tick d'arrivée). */
    public static int dureeAPlat(double vy, Regles r) {
        double y = 0, v = vy;
        for (int t = 0; t < TICKS_MAX; t++) {
            double ny = y + v;
            if (t > 0 && ny <= 0) return t + 1;
            y = ny;
            v = (v - GRAVITE) * TRAINEE_V;
        }
        return TICKS_MAX;
    }

    /** Hauteur du sommet au-dessus du départ, en blocs. */
    public static double sommet(double vy) {
        double y = 0, v = vy, max = 0;
        for (int t = 0; t < TICKS_MAX && v > 0; t++) {
            y += v;
            max = Math.max(max, y);
            v = (v - GRAVITE) * TRAINEE_V;
        }
        return max;
    }

    /** Distance horizontale parcourue après {@code ticks} ticks de vol sans obstacle. */
    public static double distanceAuTick(double vh, int ticks, Regles r) {
        if (ticks <= 0) return 0;
        double d = r.trainee();
        if (Math.abs(1 - d) < 1e-12) return vh * ticks;
        return vh * (1 - Math.pow(d, ticks)) / (1 - d);
    }

    /** Portée à plat d'une vitesse monde {@code {vx, vy, vz}}. */
    public static double portee(double[] v, Regles r) {
        return porteeAPlat(Math.hypot(v[0], v[2]), v[1], r);
    }

    /** Hauteur du sol sous un point, relative au départ, en blocs ({@code -∞} = sol inconnu, le vol continue). */
    @FunctionalInterface
    public interface Sol {
        double hauteur(double dx, double dz);
    }

    /**
     * Distance horizontale de la <b>vraie</b> arrivée sur le relief : le vol à l'air libre, arrêté au premier tick où les
     * pieds passent sous le sol (pente montante et mur compris). Partir d'un point haut porte plus loin qu'à plat.
     *
     * @param limite au-delà, on arrête de simuler
     * @return la distance d'arrivée, ou {@link Double#POSITIVE_INFINITY} si elle dépasse {@code limite}
     */
    public static double arriveeSurSol(double[] v, Regles r, Sol sol, double limite) {
        double x = 0, y = 0, z = 0, vx = v[0], vy = v[1], vz = v[2];
        for (int t = 0; t < TICKS_MAX; t++) {
            x += vx;
            y += vy;
            z += vz;
            double d = Math.hypot(x, z);
            if (d > limite) return Double.POSITIVE_INFINITY;
            if (t > 0 && y <= sol.hauteur(x, z)) return d;
            vy = (vy - GRAVITE) * TRAINEE_V;
            vx *= r.trainee();
            vz *= r.trainee();
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Borne la <b>vraie</b> arrivée (sur le relief, voir {@link #arriveeSurSol}) à {@code borne} blocs, en ne réduisant que
     * la vitesse horizontale (recherche par dichotomie, 12 pas : à 1/4096 près). Pas la portée à plat : depuis une
     * falaise ou le mont des Hokage, elle ment (60 blocs de chute portent la pleine charge de 92 à 111 blocs). Le serveur
     * et le mod lisent le même relief (carte des hauteurs {@code MOTION_BLOCKING}, la plus haute surface solide, feuilles
     * et eau comprises) : un toit ou une branche sous l'arc arrête le corps plus tôt, jamais plus loin. Modifie {@code v}.
     *
     * @return vrai si la vitesse a été réduite
     */
    public static boolean bornerArrivee(double[] v, double borne, Regles r, Sol sol) {
        if (borne <= 0 || arriveeSurSol(v, r, sol, borne) <= borne) return false;
        double vx = v[0], vz = v[2], lo = 0, hi = 1;
        double[] essai = new double[3];
        essai[1] = v[1];
        for (int i = 0; i < 12; i++) {
            double k = (lo + hi) / 2;
            essai[0] = vx * k;
            essai[2] = vz * k;
            if (arriveeSurSol(essai, r, sol, borne) <= borne) lo = k; else hi = k;
        }
        v[0] = vx * lo;
        v[2] = vz * lo;
        return true;
    }

    /**
     * Portée au plus permise par une distance de vue en chunks : l'arrivée doit rester dans un chunk chargé, avec une
     * marge d'un chunk ({@code (distance − 1) × 16}), et jamais au-delà de {@link Regles#porteeMax()}.
     */
    public static double porteeVue(int distanceChunks, Regles r) {
        if (distanceChunks <= 0) return r.porteeMax();
        return Math.min(r.porteeMax(), Math.max(16.0, (distanceChunks - 1) * 16.0));
    }

    /* ============================================================ contrôle du décollage (serveur) */

    /** Hauteur la plus haute atteinte au-dessus du départ pendant les {@code ticks} premiers ticks de vol libre. */
    public static double monteeAuTick(double vy, int ticks) {
        double y = 0, v = vy, max = 0;
        for (int t = 0; t < ticks && t < TICKS_MAX; t++) {
            y += v;
            max = Math.max(max, y);
            v = (v - GRAVITE) * TRAINEE_V;
        }
        return max;
    }

    /**
     * Le corps a-t-il vraiment pris le bond donné ? Après {@code ticks} ticks depuis le décollage vu par le serveur, il
     * doit avoir parcouru au moins 30 % de l'avance attendue à l'horizontale et être monté d'au moins 40 % de la montée
     * attendue (marges : paquets de mouvement groupés, arrondis). Se laisser tomber d'une falaise ou faire un saut vanilla
     * à la place du bond ne passe pas ; heurter un mur dans les premiers blocs non plus (la chute redevient normale).
     */
    public static boolean decollageConforme(double vh, double vy, int ticks, double parcouru, double montee, Regles r) {
        double h = 0.30 * distanceAuTick(vh, ticks, r) - 0.5;
        double m = 0.40 * monteeAuTick(vy, ticks) - 0.25;
        return parcouru >= h && montee >= m;
    }

    /* ============================================================ visée */

    /**
     * Ramène la visée {@code (lacet, tangage)} dans un cône de {@code maxDeg} autour du regard connu du serveur.
     *
     * @return {@code {lacet, tangage}} en degrés, inchangés si l'écart tient dans le cône
     */
    public static double[] viseeBornee(double lacetCli, double tangageCli, double lacetSrv, double tangageSrv,
                                       double maxDeg) {
        double[] a = direction(lacetCli, tangageCli);
        double[] b = direction(lacetSrv, tangageSrv);
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double max = Math.toRadians(maxDeg);
        double ang = Math.acos(Math.max(-1, Math.min(1, dot)));
        if (ang <= max + 1e-9) return new double[] {lacetCli, tangageCli};
        // composante de a orthogonale à b, normée ; puis on tourne b de max vers a
        double px = a[0] - b[0] * dot, py = a[1] - b[1] * dot, pz = a[2] - b[2] * dot;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1e-9) return new double[] {lacetSrv, tangageSrv};
        px /= pl; py /= pl; pz /= pl;
        double cx = b[0] * Math.cos(max) + px * Math.sin(max);
        double cy = b[1] * Math.cos(max) + py * Math.sin(max);
        double cz = b[2] * Math.cos(max) + pz * Math.sin(max);
        double tangage = Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, cy))));
        double lacet = Math.toDegrees(Math.atan2(-cx, cz));
        return new double[] {lacet, tangage};
    }

    /** Vecteur unitaire du regard (convention du jeu). */
    public static double[] direction(double lacetDeg, double tangageDeg) {
        double yr = Math.toRadians(lacetDeg), pr = Math.toRadians(tangageDeg);
        return new double[] {-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)};
    }

    private static double clamp01(double c) { return c < 0 ? 0 : c > 1 ? 1 : c; }
}
