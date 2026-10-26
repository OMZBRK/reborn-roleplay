package com.reborn.shinobicore.douane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * File d'attente du hall (CANDIDATURE_ET_DOUANE.md §3.6), en mémoire — structure pure, sans Bukkit. Seule la décision
 * est persistée (côté API) : un redémarrage vide la file, les candidats sont replacés à leur reconnexion.
 *
 * <p>Ordre :
 * <ol>
 *   <li>les candidats remis <b>en tête</b> (renvoyés au hall, examinateur déconnecté), dans l'ordre où ils y ont été
 *       remis ;</li>
 *   <li>puis l'heure de convocation (inconnue = en dernier) ;</li>
 *   <li>puis l'heure d'arrivée au hall.</li>
 * </ol>
 * Non synchronisée : à manipuler sur le fil principal.
 */
public final class FileDouane {

    /**
     * Une place dans la file.
     *
     * @param convocationAt heure de convocation (ms epoch), 0 si inconnue
     * @param arriveeAt     arrivée au hall (ms epoch) ; gardée à la reprise après une coupure de moins de 10 min
     * @param teteAt        0, ou l'instant où le candidat a été remis en tête de file
     */
    public record Entree(UUID id, String pseudo, long convocationAt, long arriveeAt, long teteAt,
                         boolean deuxiemePassage, String village) {
        Entree enTete(long quand) {
            return new Entree(id, pseudo, convocationAt, arriveeAt, quand, deuxiemePassage, village);
        }
    }

    /** Ordre de passage (voir l'en-tête). */
    public static final Comparator<Entree> ORDRE = Comparator
            .comparingInt((Entree e) -> e.teteAt() > 0 ? 0 : 1)
            .thenComparingLong(e -> e.teteAt() > 0 ? e.teteAt() : 0L)
            .thenComparingLong(e -> e.convocationAt() > 0 ? e.convocationAt() : Long.MAX_VALUE)
            .thenComparingLong(Entree::arriveeAt)
            .thenComparing(e -> e.id().toString());

    private final Map<UUID, Entree> entrees = new LinkedHashMap<>();

    /** Ajoute ou remplace la place de ce candidat. */
    public void ajouter(Entree e) {
        entrees.put(e.id(), e);
    }

    /** Retire le candidat ; renvoie sa place, ou null s'il n'était pas dans la file. */
    public Entree retirer(UUID id) {
        return entrees.remove(id);
    }

    public boolean contient(UUID id) {
        return entrees.containsKey(id);
    }

    public Entree get(UUID id) {
        return entrees.get(id);
    }

    public int taille() {
        return entrees.size();
    }

    /** Le prochain à appeler, ou null si la file est vide. */
    public Entree premier() {
        Entree best = null;
        for (Entree e : entrees.values()) {
            if (best == null || ORDRE.compare(e, best) < 0) best = e;
        }
        return best;
    }

    /** Toute la file, dans l'ordre de passage. */
    public List<Entree> ordre() {
        List<Entree> l = new ArrayList<>(entrees.values());
        l.sort(ORDRE);
        return l;
    }

    /** Rang (1 = prochain), 0 si absent. */
    public int rang(UUID id) {
        Entree cible = entrees.get(id);
        if (cible == null) return 0;
        int r = 1;
        for (Entree e : entrees.values()) {
            if (!e.id().equals(id) && ORDRE.compare(e, cible) < 0) r++;
        }
        return r;
    }

    /** Remet le candidat en tête de file (renvoyé au hall, examinateur parti). Ajoute s'il n'y est pas. */
    public Entree mettreEnTete(Entree e, long quand) {
        Entree t = e.enTete(quand);
        entrees.put(t.id(), t);
        return t;
    }
}
