package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.Set;
import java.util.UUID;

/**
 * Le <b>contexte</b> d'un dégât évalué par la porte des dégâts (docs/technique/ETATS_JOUEUR.md §6.3) : étiquettes de
 * l'attaque, technique d'origine, défense appliquée ou contournée. Joint à {@code CharacterDamageEvent}.
 *
 * @param etiquettes     étiquettes de l'attaque (vecteur, nature, propriétés)
 * @param technique      id de la technique (fenêtre d'attribution), ou null
 * @param attaquant      compte de l'attaquant (joueur), ou null
 * @param defense        défense appliquée (« hydratation »), ou null
 * @param contourneePar  étiquette qui a contourné une défense ({@code RAITON}), ou null
 * @param multiplicateur multiplicateur appliqué par les défenses (1 = aucun effet)
 */
@Stable
public record DegatContexte(Set<Etiquette> etiquettes, String technique, UUID attaquant, String defense,
                            Etiquette contourneePar, double multiplicateur) {}
