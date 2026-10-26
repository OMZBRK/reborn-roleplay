package fr.reborn.guardian.auth;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * Marque transitoire {@code reborn.scope} posée sur le joueur après une attestation valide.
 *
 * <p>Pourquoi une métadonnée Bukkit et pas une permission : une permission non déclarée est
 * accordée aux op, et le joker {@code *} de LuckPerms l'accorde aussi ; un membre du staff se
 * retrouverait avec une portée qu'il n'a pas. Une métadonnée n'est posée que par ce plugin, après
 * vérification, et se lit d'un plugin à l'autre sans dépendance de classes (ShinobiCore ne peut pas
 * importer Guardian).
 *
 * <p>Contrat pour le lecteur (ShinobiCore, lot D) :
 * <ul>
 *   <li>lire {@code player.getMetadata("reborn.scope")} et ne garder que la valeur dont
 *       {@code getOwningPlugin().getName()} vaut {@code "RebornGuardian"} ;</li>
 *   <li>valeur {@code asString()} : {@code "play"} ou {@code "douane"} ;</li>
 *   <li>absente = aucune preuve (attestation pas encore reçue, ou Guardian en mode fermé) :
 *       jamais l'équivalent de {@code "play"}.</li>
 * </ul>
 *
 * <p>Les métadonnées de joueur Bukkit survivent à la déconnexion : la marque est donc effacée au
 * JOIN (avant tout autre plugin) et au QUIT, sinon une ancienne valeur reviendrait à la reconnexion.
 * À toucher sur le fil principal seulement.
 *
 * <p>Paper 26.x marque l'API des métadonnées dépréciée, sans retrait annoncé. Elle reste le seul
 * stockage transitoire lisible d'un plugin à l'autre : le {@code PersistentDataContainer} du
 * joueur est écrit dans ses données de jeu et survivrait au redémarrage, ce qui est exclu ici.
 */
@SuppressWarnings("deprecation")
public final class ScopeMetadata {

    /** Clé de la métadonnée. */
    public static final String KEY = "reborn.scope";

    private ScopeMetadata() {}

    /** Pose la portée vérifiée. Fil principal. */
    public static void set(Plugin plugin, Player player, Scope scope) {
        player.setMetadata(KEY, new FixedMetadataValue(plugin, scope.claim()));
    }

    /** Efface la marque posée par ce plugin. Fil principal. */
    public static void clear(Plugin plugin, Player player) {
        player.removeMetadata(KEY, plugin);
    }
}
