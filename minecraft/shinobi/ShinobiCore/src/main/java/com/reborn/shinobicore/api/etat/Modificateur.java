package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/**
 * Vue immuable d'un modificateur actif dans un {@link Etat} (§4.4) : posé (registre des modificateurs) ou <b>dérivé</b>
 * (ATA, surcharge, en combat, gel du staff… calculés par leur système, jamais stockés deux fois).
 *
 * @param id        identifiant ({@code ata}, {@code surcharge}, {@code en_combat}, {@code poison}…)
 * @param nom       libellé affiché
 * @param intensite niveau (ATA 1 allégée / 2 pleine, surcharge 1-3…)
 * @param resteMs   temps restant, 0 = sans fin connue
 * @param source    qui l'a posé
 * @param derive    vrai pour un état dérivé d'un autre système (pas dans le registre)
 */
@Stable
public record Modificateur(String id, String nom, double intensite, long resteMs, String source, boolean derive) {}
