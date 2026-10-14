package com.reborn.shinobiradio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une escouade radio (docs/gameplay/RADIO.md §4) : un nom, un créateur, au plus 8 membres <b>personnages</b> (un compte
 * peut en avoir plusieurs), et les numéros d'oreillettes exclues. Persistante : survit aux déconnexions et aux
 * redémarrages ({@code escouades.yml}). Modifiée sur le thread principal seulement.
 */
final class Escouade {

    /** Un membre : le personnage, son compte, son nom RP, qui l'a ajouté, depuis quand, l'oreillette liée. */
    static final class Membre {
        final UUID perso;
        final UUID compte;
        String nom;
        final UUID ajoutePar;
        final long depuis;
        /** Numéro de l'oreillette liée à ce membre (-1 : aucune). */
        int oreillette;
        /** Son oreillette lui a été prise (vol) : l'écran N propose au créateur de la couper. */
        boolean prise;

        Membre(UUID perso, UUID compte, String nom, UUID ajoutePar, long depuis, int oreillette) {
            this.perso = perso;
            this.compte = compte;
            this.nom = nom;
            this.ajoutePar = ajoutePar;
            this.depuis = depuis;
            this.oreillette = oreillette;
        }
    }

    final UUID id;
    String nom;
    UUID createur;
    final long creeLe;
    long derniereConnexion;
    final Map<UUID, Membre> membres = new LinkedHashMap<>();
    final Set<Integer> exclues = new HashSet<>();

    Escouade(UUID id, String nom, UUID createur, long creeLe) {
        this.id = id;
        this.nom = nom;
        this.createur = createur;
        this.creeLe = creeLe;
        this.derniereConnexion = creeLe;
    }

    Membre membre(UUID perso) { return perso == null ? null : membres.get(perso); }

    boolean estCreateur(UUID perso) { return perso != null && perso.equals(createur); }

    /** Membres, le créateur d'abord puis par ancienneté. */
    List<Membre> ordonnes() {
        List<Membre> l = new ArrayList<>(membres.values());
        l.sort(Comparator.<Membre>comparingInt(m -> estCreateur(m.perso) ? 0 : 1).thenComparingLong(m -> m.depuis));
        return l;
    }

    /** Le plus ancien membre hors {@code sauf}, ou null. */
    Membre plusAncien(UUID sauf) {
        return membres.values().stream().filter(m -> !m.perso.equals(sauf))
                .min(Comparator.comparingLong(m -> m.depuis)).orElse(null);
    }
}
