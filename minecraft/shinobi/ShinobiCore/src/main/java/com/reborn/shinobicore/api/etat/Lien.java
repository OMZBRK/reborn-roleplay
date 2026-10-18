package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.UUID;

/**
 * Le <b>lien</b> d'une valeur de couche (§5.2 règle 2) : la source vivante dont elle dépend (porteur, lanceur d'une
 * entrave, contrôleur). Quand la source part, change de personnage, tombe à terre ou s'éloigne au-delà de
 * {@code porteeBlocs}, le lien tombe et la valeur avec.
 *
 * @param type        nature du lien ({@code "PORTE"}, {@code "GORGE"}, {@code "EXECUTION"}, {@code "OMBRE"}…)
 * @param source      compte Minecraft de la source (null = pas de source vivante : staff, objet)
 * @param nom         nom affiché de la source (personnage), jamais l'UUID
 * @param porteeBlocs portée au-delà de laquelle le lien rompt ({@code ≤ 0} = sans limite)
 */
@Stable
public record Lien(String type, UUID source, String nom, double porteeBlocs) {

    public static Lien de(String type, UUID source, String nom) { return new Lien(type, source, nom, 0); }
}
