package com.reborn.shinobicore.api;

import java.util.UUID;

/**
 * Étourdissement de combat (hitstun, garde brisée, parade subie), tenu par ShinobiCombat et publié dans le
 * ServicesManager. Service optionnel : sans ShinobiCombat, personne n'est étourdi.
 *
 * <p>Depuis le 17/10 (ETATS_JOUEUR étape 4), ShinobiCombat pose aussi la garde et l'étourdi dans la couche Contrôle
 * d'{@link EtatService} : lire de préférence {@code EtatService#etat(...).controle()} ou une capacité.
 */
@Stable
public interface StunService {

    /** Le joueur est-il étourdi en ce moment ? */
    boolean isStunned(UUID playerId);

    /**
     * Le joueur est-il en garde (touche de parade maintenue) ? Garde = immobile, sans technique (décision du 14/10).
     * Défaut {@code false} pour un ShinobiCombat plus ancien.
     */
    default boolean isGuarding(UUID playerId) { return false; }
}
