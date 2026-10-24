package com.reborn.shinobicore.character;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Joueurs dont le visage est caché (GAMEPLAN §14, 24/10) : un objet dans l'emplacement Masque de la sacoche RP. Tenu à
 * jour par {@code InventoryManager} à chaque diffusion des cosmétiques ; lu par {@link CharacterDisplay} (tchat, /me) et
 * la liste des joueurs (plaques du mod) : un masqué reste « Inconnu », même pour ceux qui le connaissent.
 */
public final class Masques {

    private static final Set<UUID> JOUEURS = ConcurrentHashMap.newKeySet();

    private Masques() {}

    public static void definir(UUID joueur, boolean masque) {
        if (joueur == null) return;
        if (masque) JOUEURS.add(joueur);
        else JOUEURS.remove(joueur);
    }

    /** Ce joueur porte-t-il un masque en ce moment ? */
    public static boolean porte(UUID joueur) {
        return joueur != null && JOUEURS.contains(joueur);
    }
}
