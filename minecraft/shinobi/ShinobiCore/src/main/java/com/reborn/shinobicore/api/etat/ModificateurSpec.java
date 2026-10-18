package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/**
 * Demande de pose d'un modificateur (§8) : type du registre ({@code modificateurs.types.<id>}), intensité, durée
 * ({@code ≤ 0} = celle du type).
 */
@Stable
public record ModificateurSpec(String id, double intensite, long dureeMs) {

    public static ModificateurSpec of(String id) { return new ModificateurSpec(id, 1.0, 0); }
}
