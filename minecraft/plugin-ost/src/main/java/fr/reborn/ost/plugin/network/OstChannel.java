package fr.reborn.ost.plugin.network;

/**
 * Constantes du canal {@code reborn:ost}.
 *
 * <p>NB : pour cette première version, le serveur est l'unique émetteur
 * sur ce canal (registerOutgoingPluginChannel). Le mod client ne push
 * jamais — pas de listener incoming installé.
 *
 * <p>TODO (futur — auto-DJ partagé) : si on veut permettre au client
 * de REQUÊTER une lecture (genre "vote pour la prochaine track"), il
 * faudra :
 *   1. Enregistrer en plus un canal entrant {@code reborn:ost-request}
 *   2. Signer les requêtes HMAC-SHA256 avec le secret partagé (cf
 *      pattern {@code REBORN_PLAY_TOKEN_SECRET} dans CLAUDE.md
 *      "Integrity loop").
 *   3. Côté mod, signer avec un secret stocké dans la JVM (déposé par
 *      le launcher au boot, jamais en clair côté disque).
 *   4. Sinon n'importe quel client tampon peut spammer un son chez
 *      tous ses voisins.
 */
public final class OstChannel {

    /** Nom complet du canal Bukkit/Paper (serveur → client). */
    public static final String NAME = "reborn:ost";

    /** Canal entrant (client → serveur) : requêtes de broadcast de zone d'un
     *  joueur (play/stop/pause). Cf {@code OstRequestListener}. */
    public static final String REQUEST_NAME = "reborn:ost_request";

    private OstChannel() {}
}
