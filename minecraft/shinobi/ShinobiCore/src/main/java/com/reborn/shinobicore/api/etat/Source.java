package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.UUID;

/**
 * Qui ou quoi pose une transition ou un modificateur — journalisé (§7.4).
 *
 * @param type   {@code JOUEUR}, {@code TECHNIQUE}, {@code OBJET}, {@code SYSTEME}, {@code STAFF}
 * @param nom    libellé lisible (nom du personnage, de la technique, du système, du staff)
 * @param compte compte Minecraft de l'acteur s'il y en a un
 */
@Stable
public record Source(String type, String nom, UUID compte) {

    public static Source systeme(String nom) { return new Source("SYSTEME", nom, null); }

    public static Source staff(String nom, UUID compte) { return new Source("STAFF", nom, compte); }

    public static Source joueur(String nom, UUID compte) { return new Source("JOUEUR", nom, compte); }

    public static Source technique(String nom, UUID lanceur) { return new Source("TECHNIQUE", nom, lanceur); }

    @Override
    public String toString() { return type + ":" + nom; }
}
