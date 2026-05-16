package fr.reborn.guardian.listener;

/**
 * @deprecated Remplace par {@link PendingAuthListener} (kick differe) +
 * {@link AuthChannelListener} (verif HMAC du play-token). Le fichier reste
 * en place faute de pouvoir le supprimer via l'outillage actuel ; il sera
 * retire au prochain refactor manuel.
 */
@Deprecated(forRemoval = true)
final class PlayerJoinListener {
    private PlayerJoinListener() {}
}
