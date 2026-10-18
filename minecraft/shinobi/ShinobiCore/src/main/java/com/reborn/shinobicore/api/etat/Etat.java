package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.List;

/**
 * Instantané immuable de l'état d'un joueur (§8) : les trois couches, l'état principal résolu, le lien de la couche
 * Contrôle, les modificateurs actifs (posés et dérivés) et le résultat de la matrice. Le serveur seul le calcule.
 */
@Stable
public record Etat(Principal principal, Vital vital, Controle controle, Cadre cadre, Lien lien,
                   List<Modificateur> modificateurs, Capacites capacites) {

    public boolean peut(Capacite c) { return capacites.peut(c); }

    public Verdict verdict(Capacite c) { return capacites.verdict(c); }

    /** Raison du refus de {@code c}, ou null si permis. */
    public String refus(Capacite c) { return capacites.refus(c); }

    /** Ce modificateur (id) est-il actif ? */
    public boolean a(String modificateur) {
        for (Modificateur m : modificateurs) if (m.id().equalsIgnoreCase(modificateur)) return true;
        return false;
    }

    /** KO au sens large : à terre ou inconscient. */
    public boolean ko() { return vital == Vital.A_TERRE || vital == Vital.INCONSCIENT; }
}
