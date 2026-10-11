package com.reborn.shinobicore.api;

import java.util.UUID;

/**
 * Progression par l'expérience : donne de l'XP au personnage actif d'un joueur en ligne. Les montants de base
 * viennent de la config du core ({@code progression.gains.<source>}), le contenu n'apporte que le multiplicateur.
 */
@Stable
public interface XpService {

    /**
     * Donne au personnage actif l'XP de la source {@code source} (clé de {@code progression.gains}, ou
     * {@code fallback} si absente), multipliée par {@code multiplier}. Renvoie le niveau atteint, ou -1.
     */
    int reward(UUID playerId, String source, double fallback, double multiplier);
}
