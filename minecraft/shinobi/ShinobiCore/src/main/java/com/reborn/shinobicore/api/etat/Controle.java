package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/**
 * Couche Contrôle (§4.2) : qui pilote le corps. Plusieurs écrivains, un seul détenteur à la fois ; l'ordre des valeurs
 * est leur priorité quand deux sources se recouvrent (la plus haute gagne).
 */
@Stable
public enum Controle {
    LIBRE,
    /** Garde tenue (C), ShinobiCombat. */
    GARDE,
    /** Plus d'action pendant N ticks (garde brisée, parade subie, coup final, onde au sol). */
    ETOURDI,
    /** L'esprit est ailleurs (lanceur du transfert d'esprit, monde intérieur du jinchūriki). Lien = la cible. */
    CORPS_VIDE,
    /** Immobilisé par quelque chose (ombre, bois, chaînes, exécution en cours). Lien = le lanceur. */
    ENTRAVE,
    /** Un autre pilote le corps (imitation d'ombre, transfert d'esprit, possession du bijū). Lien = le contrôleur. */
    CONTROLE,
    /** Sur les épaules ou tenu à la gorge d'un autre. Lien = le porteur. */
    PORTE;

    /** Priorité dans la couche (plus grand = gagne). */
    public int priorite() { return ordinal(); }
}
