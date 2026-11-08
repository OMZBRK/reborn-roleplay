package com.reborn.shinobicore.concentration.trace;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les pas en mémoire, sans dépendance au serveur : un anneau par tronçon (le plus vieux part quand il est plein) et un
 * plafond global (le plus vieux du serveur part). Perdues au redémarrage, à dessein. Fil principal seulement.
 *
 * <p>Avec des pas qui vivent des heures, rien ne parcourt tout : l'ordre d'arrivée est une liste chaînée dans les pas (ôter
 * un pas : O(1)), et {@link #balayer} n'examine qu'un nombre borné de pas à chaque appel, en reprenant où il s'était arrêté.
 * Un pas expiré mais pas encore balayé n'est jamais envoyé ({@link #autour} le saute).
 */
final class TraceStore {

    private final int maxTroncon, maxGlobal;
    private final Map<String, Map<Long, ArrayDeque<Trace>>> mondes = new HashMap<>();
    /** Ordre d'arrivée, pour le plafond global : le plus vieux en tête ; {@link #curseur} : où le balayage reprend. */
    private Trace premier, dernier, curseur;
    private int taille, prochainId = 1;

    TraceStore(int maxTroncon, int maxGlobal) {
        this.maxTroncon = Math.max(1, maxTroncon);
        this.maxGlobal = Math.max(1, maxGlobal);
    }

    int taille() { return taille; }

    private static long troncon(double x, double z) {
        return troncon((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    private static long troncon(int cx, int cz) { return (long) cx << 32 | cz & 0xFFFFFFFFL; }

    Trace ajouter(UUID marcheur, String monde, double x, double y, double z, int angle, int drapeaux, int k, long nee, int vie) {
        while (taille >= maxGlobal && premier != null) retirer(premier);
        Trace t = new Trace(prochainId++, marcheur, monde, x, y, z, angle, drapeaux, k, nee, vie);
        ArrayDeque<Trace> anneau = mondes.computeIfAbsent(monde, m -> new HashMap<>())
            .computeIfAbsent(troncon(x, z), c -> new ArrayDeque<>());
        if (anneau.size() >= maxTroncon) {
            Trace vieux = anneau.pollFirst();
            if (vieux != null) delier(vieux);
        }
        anneau.addLast(t);
        t.avant = dernier;
        if (dernier != null) dernier.apres = t;
        else premier = t;
        dernier = t;
        taille++;
        return t;
    }

    /** Ôte un pas de l'ordre d'arrivée (pas de son anneau). */
    private void delier(Trace t) {
        t.retiree = true;
        taille--;
        if (curseur == t) curseur = t.apres;
        if (t.avant != null) t.avant.apres = t.apres;
        else premier = t.apres;
        if (t.apres != null) t.apres.avant = t.avant;
        else dernier = t.avant;
        t.avant = null;
        t.apres = null;
    }

    private void retirer(Trace t) {
        delier(t);
        Map<Long, ArrayDeque<Trace>> m = mondes.get(t.monde);
        if (m == null) return;
        long c = troncon(t.x, t.z);
        ArrayDeque<Trace> anneau = m.get(c);
        if (anneau == null) return;
        anneau.remove(t);
        if (anneau.isEmpty()) m.remove(c);
    }

    /** Examine {@code max} pas au plus, à la suite du dernier appel : oublie les expirés et les tronçons vidés. */
    void balayer(long now, int max) {
        if (curseur == null) curseur = premier;
        for (int i = 0; i < max && curseur != null; i++) {
            Trace t = curseur;
            curseur = t.apres;
            if (t.expiree(now)) retirer(t);
        }
    }

    /** Oublie tous les pas expirés d'un coup. */
    void purger(long now) {
        curseur = premier;
        balayer(now, taille);
    }

    /**
     * Les pas encore vivants à {@code rayon} blocs, sauf ceux de {@code exclu} (on ne voit pas les siens), les {@code max}
     * plus proches ; {@code sortie} est vidée d'abord.
     */
    void autour(String monde, double x, double z, double rayon, UUID exclu, long now, int max, List<Trace> sortie) {
        sortie.clear();
        Map<Long, ArrayDeque<Trace>> m = mondes.get(monde);
        if (m == null) return;
        int c0x = (int) Math.floor(x - rayon) >> 4, c1x = (int) Math.floor(x + rayon) >> 4;
        int c0z = (int) Math.floor(z - rayon) >> 4, c1z = (int) Math.floor(z + rayon) >> 4;
        double r2 = rayon * rayon;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                ArrayDeque<Trace> anneau = m.get(troncon(cx, cz));
                if (anneau == null) continue;
                for (Trace t : anneau) {
                    if (t.expiree(now) || t.marcheur.equals(exclu)) continue;
                    double dx = t.x - x, dz = t.z - z;
                    if (dx * dx + dz * dz <= r2) sortie.add(t);
                }
            }
        }
        if (sortie.size() > max) {
            sortie.sort((a, b) -> Double.compare(d2(a, x, z), d2(b, x, z)));
            sortie.subList(max, sortie.size()).clear();
        }
    }

    private static double d2(Trace t, double x, double z) {
        double dx = t.x - x, dz = t.z - z;
        return dx * dx + dz * dz;
    }
}
