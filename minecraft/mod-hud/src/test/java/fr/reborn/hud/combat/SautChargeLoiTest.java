package fr.reborn.hud.combat;

import fr.reborn.hud.combat.SautChargeLoi.Regles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Règles pures du saut chargé (lot 5, étape A). Le même tableau est vérifié par le test jumeau du plugin
 * ({@code com.reborn.shinobicombat.saut.SautChargeLoiTest}) : si l'un bouge sans l'autre, une des deux suites échoue.
 */
class SautChargeLoiTest {

    private static final Regles R = Regles.DEFAUT;

    /** Meilleure portée à plat sur les visées de 0 à 89°, et l'angle qui la donne. */
    private static double[] auMieux(double c) {
        double best = 0, angle = 0;
        for (int a = 0; a < 90; a++) {
            double[] v = SautChargeLoi.vitesseDepart(c, 0, -a, R);
            double p = SautChargeLoi.portee(v, R);
            if (p > best) { best = p; angle = a; }
        }
        return new double[] {best, angle};
    }

    private static double aPlat(double c) {
        return SautChargeLoi.portee(SautChargeLoi.vitesseDepart(c, 0, 0, R), R);
    }

    /* ---------------------------------------------------------------- l'ancienne loi (0.4.201), pour comparer */

    /** Portée de l'ancien saut : vitesse 2,4 + 2,2 × c le long du regard, + 0,5 en y, frottement vanilla × 0,91 ; même
     *  règle de réception que la loi (le tick de la réception avance en entier). */
    private static double ancienne(long tenuMs, double elevDeg) {
        if (tenuMs < 135) return 0;
        double c = Math.min(1, tenuMs / 900.0);
        double sp = 2.4 + 2.2 * c, e = Math.toRadians(elevDeg);
        double h = sp * Math.cos(e), v = Math.max(-0.2, Math.min(3.0, sp * Math.sin(e) + 0.5));
        double x = 0, y = 0;
        for (int t = 0; t < 400; t++) {
            double nx = x + h, ny = y + v;
            if (t > 0 && ny <= 0) return nx;
            x = nx;
            y = ny;
            v = (v - 0.08) * 0.98;
            h *= 0.91;
        }
        return x;
    }

    private static double ancienneAuMieux(long tenuMs) {
        double best = 0;
        for (int a = 0; a < 90; a++) best = Math.max(best, ancienne(tenuMs, a));
        return best;
    }

    /* ---------------------------------------------------------------- tests */

    @Test
    void pleineChargeVaLoin() {
        assertTrue(aPlat(1) >= 58, "à plat, pleine charge : " + aPlat(1));
        double[] m = auMieux(1);
        assertTrue(m[0] >= 90 && m[0] <= 96, "au mieux, pleine charge : " + m[0]);
        assertEquals(28, m[1], 2, "angle du meilleur saut");
    }

    @Test
    void sommetGardeA40() {
        double vy = SautChargeLoi.vitesseDepart(1, 0, -90, R)[1];
        assertEquals(3.0, vy, 1e-9);
        double h = SautChargeLoi.sommet(vy);
        assertTrue(h > 39 && h <= 40.5, "sommet : " + h);
    }

    @Test
    void jamaisMoinsLoinQuAvantAuMemeTempsDeMaintien() {
        for (long t = 135; t <= 1200; t += 15) {
            double c = SautChargeLoi.charge(t, R);
            assertTrue(SautChargeLoi.suffisante(c, R), "seuil à " + t + " ms");
            double nouveauPlat = aPlat(c), ancienPlat = ancienne(t, 0);
            double nouveauMieux = auMieux(c)[0], ancienMieux = ancienneAuMieux(t);
            assertTrue(nouveauPlat >= ancienPlat + 1, t + " ms à plat : " + nouveauPlat + " contre " + ancienPlat);
            assertTrue(nouveauMieux >= ancienMieux + 1, t + " ms au mieux : " + nouveauMieux + " contre " + ancienMieux);
        }
    }

    @Test
    void laChargeCompte() {
        double court = aPlat(SautChargeLoi.charge(135, R)), plein = aPlat(1);
        assertTrue(plein / court >= 2.3, "de " + court + " à " + plein);
        // croissante
        double prec = 0;
        for (int i = 0; i <= 20; i++) {
            double p = aPlat(i / 20.0);
            assertTrue(p > prec);
            prec = p;
        }
    }

    @Test
    void seuilDeCharge() {
        assertEquals(135, R.seuilMs());
        assertFalse(SautChargeLoi.suffisante(SautChargeLoi.charge(134, R), R));
        assertTrue(SautChargeLoi.suffisante(SautChargeLoi.charge(135, R), R));
        assertEquals(0, SautChargeLoi.charge(-5, R));
        assertEquals(1, SautChargeLoi.charge(5000, R));
        assertEquals(0.5, SautChargeLoi.charge(600, R), 1e-12);
    }

    @Test
    void coutEtRecharge() {
        assertEquals(0.3, SautChargeLoi.coutPct(0, R), 1e-12);
        assertEquals(0.36075, SautChargeLoi.coutPct(0.15, R), 1e-9);
        assertEquals(0.975, SautChargeLoi.coutPct(0.5, R), 1e-9);
        assertEquals(3.0, SautChargeLoi.coutPct(1, R), 1e-12);
        assertEquals(8750, SautChargeLoi.rechargeMs(0.15, R));
        assertEquals(17500, SautChargeLoi.rechargeMs(0.5, R));
        assertEquals(30000, SautChargeLoi.rechargeMs(1, R));
        // charge payable : l'inverse du coût
        assertEquals(1.0, SautChargeLoi.chargePayable(5, R), 1e-12);
        assertEquals(0.5, SautChargeLoi.chargePayable(0.975, R), 1e-9);
        assertEquals(0, SautChargeLoi.chargePayable(0.2, R));
    }

    @Test
    void viserVersLeBasRevientAViserAPlat() {
        assertArrayEquals(SautChargeLoi.vitesseDepart(0.7, 30, 0, R), SautChargeLoi.vitesseDepart(0.7, 30, 40, R), 1e-12);
    }

    @Test
    void directionDuLacet() {
        double[] sud = SautChargeLoi.vitesseDepart(1, 0, 0, R);   // lacet 0 = +z
        assertTrue(sud[2] > 3 && Math.abs(sud[0]) < 1e-9);
        double[] ouest = SautChargeLoi.vitesseDepart(1, 90, 0, R);   // lacet 90 = −x
        assertTrue(ouest[0] < -3 && Math.abs(ouest[2]) < 1e-9);
    }

    @Test
    void volAirLibreEgalePorteeAPlat() {
        double[] v = SautChargeLoi.vitesseDepart(0.8, 0, -20, R);
        double attendu = SautChargeLoi.portee(v, R);
        double[] pos = {0, 0, 0};
        double[] w = v.clone();
        for (int t = 0; t < 400; t++) {
            SautChargeLoi.pas(pos, w, R);
            if (t > 0 && pos[1] <= 0) break;
        }
        assertEquals(attendu, pos[2], 1e-9);
        // distance au tick : somme géométrique
        double vh = Math.hypot(v[0], v[2]);
        assertEquals(vh * (1 + 0.98 + 0.98 * 0.98), SautChargeLoi.distanceAuTick(vh, 3, R), 1e-9);
    }

    @Test
    void bornerLArriveeAPlat() {
        SautChargeLoi.Sol plat = (dx, dz) -> 0;
        double[] v = SautChargeLoi.vitesseDepart(1, 0, -28, R);
        assertEquals(SautChargeLoi.portee(v, R), SautChargeLoi.arriveeSurSol(v, R, plat, 1000), 1e-9);
        assertTrue(SautChargeLoi.portee(v, R) > 80);
        assertTrue(SautChargeLoi.bornerArrivee(v, 80, R, plat));
        double p = SautChargeLoi.portee(v, R);
        assertTrue(p <= 80 && p > 79, "bornée à plat : " + p);
        assertFalse(SautChargeLoi.bornerArrivee(v, 90, R, plat));
        assertEquals(96, SautChargeLoi.porteeVue(12, R));
        assertEquals(96, SautChargeLoi.porteeVue(7, R));
        assertEquals(80, SautChargeLoi.porteeVue(6, R));
        assertEquals(64, SautChargeLoi.porteeVue(5, R));
        assertEquals(16, SautChargeLoi.porteeVue(1, R));
    }

    /** Depuis une falaise, la portée à plat ment : le bornage se fait sur la vraie arrivée (critique du 09/11). */
    @Test
    void bornerLArriveeSurLeRelief() {
        double[] v = SautChargeLoi.vitesseDepart(1, 0, -29, R);
        SautChargeLoi.Sol plat = (dx, dz) -> 0, falaise = (dx, dz) -> dz < 4 ? 0 : -60;
        double aPlat = SautChargeLoi.arriveeSurSol(v, R, plat, 1000);
        double depuisHaut = SautChargeLoi.arriveeSurSol(v, R, falaise, 1000);
        assertTrue(depuisHaut > aPlat + 15, "60 de chute : " + depuisHaut + " contre " + aPlat);
        // distance de vue 6 : arrivée réelle tenue sous 80, là où la portée à plat laissait passer ~96
        double[] w = v.clone();
        assertTrue(SautChargeLoi.bornerArrivee(w, 80, R, falaise));
        double reelle = SautChargeLoi.arriveeSurSol(w, R, falaise, 1000);
        assertTrue(reelle <= 80 && reelle > 76, "arrivée bornée : " + reelle);
        assertEquals(v[1], w[1], 1e-12);   // seule l'horizontale baisse
        // sol inconnu (chunk absent) : le vol continue, donc bornage
        double[] u = v.clone();
        assertTrue(SautChargeLoi.bornerArrivee(u, 40, R, (dx, dz) -> Double.NEGATIVE_INFINITY));
        assertTrue(Math.hypot(u[0], u[2]) < Math.hypot(v[0], v[2]));
        // mur montant : arrivée au mur, rien à borner
        assertFalse(SautChargeLoi.bornerArrivee(v.clone(), 80, R, (dx, dz) -> dz > 10 ? 50 : 0));
    }

    /** Contrôle du décollage : prendre le bond passe, se laisser tomber ou un saut vanilla non. */
    @Test
    void decollageConforme() {
        for (double c : new double[] {R.seuil(), 0.5, 1}) {
            for (int a : new int[] {0, 29, 80}) {
                double[] v = SautChargeLoi.vitesseDepart(c, 0, -a, R);
                double vh = Math.hypot(v[0], v[2]);
                double[] pos = {0, 0, 0}, w = v.clone();
                double montee = 0;
                for (int t = 0; t < 8; t++) { SautChargeLoi.pas(pos, w, R); montee = Math.max(montee, pos[1]); }
                assertTrue(SautChargeLoi.decollageConforme(vh, v[1], 8, Math.hypot(pos[0], pos[2]), montee, R),
                        "bond honnête c=" + c + " a=" + a);
                // paquets groupés : la moitié du chemin vu seulement
                assertTrue(SautChargeLoi.decollageConforme(vh, v[1], 8, Math.hypot(pos[0], pos[2]) / 2, montee / 2, R));
                // se laisser tomber d'une falaise (marche 0,2 b/t, aucune montée)
                assertFalse(SautChargeLoi.decollageConforme(vh, v[1], 8, 1.6, 0, R), "chute c=" + c + " a=" + a);
            }
        }
        // saut vanilla en courant (1,25 de haut, ~3 blocs) à la place d'un bond de charge minimale
        double[] v = SautChargeLoi.vitesseDepart(R.seuil(), 0, 0, R);
        assertFalse(SautChargeLoi.decollageConforme(Math.hypot(v[0], v[2]), v[1], 8, 3.0, 1.25, R));
        assertEquals(SautChargeLoi.sommet(3.0), SautChargeLoi.monteeAuTick(3.0, 400), 1e-9);
    }

    @Test
    void viseeDansLeCone() {
        double[] v = SautChargeLoi.viseeBornee(10, -5, 0, 0, 25);
        assertEquals(10, v[0], 1e-9);
        assertEquals(-5, v[1], 1e-9);
        double[] b = SautChargeLoi.viseeBornee(60, 0, 0, 0, 25);
        assertEquals(25, b[0], 1e-6);
        assertEquals(0, b[1], 1e-6);
        double[] h = SautChargeLoi.viseeBornee(0, -80, 0, 0, 25);
        assertEquals(-25, h[1], 1e-6);
        // l'écart final ne dépasse jamais le cône
        double[] d1 = SautChargeLoi.direction(170, 30), d2 = SautChargeLoi.direction(-20, -10);
        double[] r = SautChargeLoi.viseeBornee(170, 30, -20, -10, 25);
        double[] dr = SautChargeLoi.direction(r[0], r[1]);
        double ang = Math.toDegrees(Math.acos(dr[0] * d2[0] + dr[1] * d2[1] + dr[2] * d2[2]));
        assertEquals(25, ang, 1e-6);
        assertTrue(d1 != null);
    }

    /** Le tableau de la doc (DEPLACEMENTS.md, PLUGINS_REFERENCE.md) : tenu, charge, à plat, au mieux, coût, recharge. */
    @Test
    void tableauDeLaDoc() {
        StringBuilder s = new StringBuilder();
        for (long t : new long[] {135, 300, 600, 900, 1200}) {
            double c = SautChargeLoi.charge(t, R);
            double[] m = auMieux(c);
            s.append(String.format(java.util.Locale.ROOT, "%d ms | %.0f %% | %.1f | %.1f (%.0f°) | ancien %.1f / %.1f | %.2f %% | %.1f s%n",
                    t, c * 100, aPlat(c), m[0], m[1], ancienne(t, 0), ancienneAuMieux(t), SautChargeLoi.coutPct(c, R),
                    SautChargeLoi.rechargeMs(c, R) / 1000.0));
        }
        System.out.print(s);
        assertEquals(60.9, aPlat(1), 0.6);
        assertEquals(93.0, auMieux(1)[0], 1.0);
    }
}
