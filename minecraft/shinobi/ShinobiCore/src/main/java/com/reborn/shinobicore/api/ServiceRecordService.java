package com.reborn.shinobicore.api;

import java.util.UUID;

/**
 * États de service d'un personnage (fiche shinobi, onglet Identité) — journal
 * <b>append-only</b> tenu par ShinobiCore : enregistrement, promotions,
 * missions, distinctions. Une entrée n'est jamais supprimée, seulement masquée
 * par le staff.
 *
 * <p>Les addons (ShinobiLearning à la sortie de l'Académie, un futur système de
 * missions…) écrivent par {@link #append}.
 */
@Stable
public interface ServiceRecordService {

    /** Nature d'une entrée — choisit le sceau affiché par le mod. */
    enum Type { ENREGISTREMENT, PROMOTION, RETROGRADATION, MISSION, DISTINCTION, AUTRE }

    /**
     * Ajoute une entrée datée de maintenant.
     *
     * @param charId personnage visé
     * @param type   nature de l'entrée
     * @param text   texte affiché tel quel (figé : c'est un historique)
     * @param rang   lettre de rang d'une mission (D/C/B/A/S), sinon {@code null}
     * @param by     auteur (pseudo du staff, nom de plugin…), peut être {@code null}
     */
    void append(UUID charId, Type type, String text, String rang, String by);
}
