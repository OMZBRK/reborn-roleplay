package com.reborn.shinobicore.douane;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Banque de questions de la douane (CANDIDATURE_ET_DOUANE.md §3.7) — tirage pur, sans Bukkit.
 *
 * <p>Grande banque, surtout des mises en situation, tirée au hasard. Le tirage écarte :
 * <ol>
 *   <li>les questions déjà tirées pour ce candidat (un candidat renvoyé au hall en reçoit d'autres à son retour) ;</li>
 *   <li>les dernières questions tirées pour n'importe qui (les candidats se les racontent au hall).</li>
 * </ol>
 * Quand la banque est épuisée, on relâche d'abord la seconde règle, puis la première.
 *
 * <p>Une question est repérée par son <b>texte</b>, jamais par sa place dans la banque : {@code /sc reload} peut
 * remplacer la banque en pleine séance sans fausser la question affichée ni l'exclusion des questions déjà posées.
 */
public final class TirageQuestions {

    private final List<String> banque;
    private final Deque<String> recentes = new ArrayDeque<>();
    private final int memoire;

    public TirageQuestions(List<String> banque) {
        Set<String> propre = new LinkedHashSet<>();
        if (banque != null) {
            for (String q : banque) {
                if (q != null && !q.isBlank()) propre.add(q.strip());
            }
        }
        this.banque = List.copyOf(propre);
        // Mémoire des tirages récents : un tiers de la banque, 10 au plus.
        this.memoire = Math.min(10, this.banque.size() / 3);
    }

    public int taille() {
        return banque.size();
    }

    /**
     * Rechargement : reprend la mémoire des tirages récents de l'ancienne banque, pour les questions qui y sont
     * encore (les candidats du hall ne réentendent pas tout de suite une question qu'ils viennent d'entendre).
     */
    public void reprendre(TirageQuestions avant) {
        if (avant == null || memoire <= 0) return;
        for (String q : avant.recentes) {
            if (banque.contains(q)) recentes.addLast(q);
        }
        while (recentes.size() > memoire) recentes.pollFirst();
    }

    /** Tire une question ; renvoie son texte, ou null si la banque est vide. Ne modifie pas {@code dejaTirees}. */
    public String tirer(Set<String> dejaTirees, Random hasard) {
        if (banque.isEmpty()) return null;
        List<String> possibles = new ArrayList<>();
        for (String q : banque) {
            if (!dejaTirees.contains(q) && !recentes.contains(q)) possibles.add(q);
        }
        if (possibles.isEmpty()) {
            for (String q : banque) if (!dejaTirees.contains(q)) possibles.add(q);
        }
        if (possibles.isEmpty()) possibles.addAll(banque);
        String choix = possibles.get(hasard.nextInt(possibles.size()));
        if (memoire > 0) {
            recentes.remove(choix);
            recentes.addLast(choix);
            while (recentes.size() > memoire) recentes.pollFirst();
        }
        return choix;
    }

    /** Nombre de questions de la banque encore jamais tirées pour ce candidat. */
    public int reste(Set<String> dejaTirees) {
        int n = 0;
        for (String q : banque) if (!dejaTirees.contains(q)) n++;
        return n;
    }
}
