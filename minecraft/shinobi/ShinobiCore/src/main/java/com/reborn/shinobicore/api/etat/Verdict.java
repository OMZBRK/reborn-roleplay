package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/**
 * Réponse à « peut-il X ? » : permis ({@code 2}), limité ({@code 1}) ou refusé ({@code 0}), avec la <b>raison</b>
 * lisible (tutoiement, phrase courte : « Tu es à terre. », « On te porte. ») et la ligne de la matrice qui a décidé
 * ({@code "vital.A_TERRE"}, {@code "mod.ata"}).
 */
@Stable
public record Verdict(int niveau, String raison, String ligne) {

    public static final Verdict PERMIS = new Verdict(2, null, null);

    public boolean ok() { return niveau > 0; }

    public boolean plein() { return niveau >= 2; }

    public boolean limite() { return niveau == 1; }

    /** La raison du refus, ou null si permis (même limité). */
    public String refus() { return niveau > 0 ? null : raison; }
}
