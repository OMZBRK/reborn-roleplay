package com.reborn.shinobicore.api;

import java.util.UUID;

/**
 * Étourdissement de combat (hitstun, garde brisée, parade subie), tenu par ShinobiCombat et publié dans le
 * ServicesManager. Service optionnel : sans ShinobiCombat, personne n'est étourdi.
 */
@Stable
public interface StunService {

    /** Le joueur est-il étourdi en ce moment ? */
    boolean isStunned(UUID playerId);
}
