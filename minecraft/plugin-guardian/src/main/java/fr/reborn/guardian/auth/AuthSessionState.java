package fr.reborn.guardian.auth;

import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Etat en-memoire des sessions d'attestation.
 *
 * <p>Pour chaque UUID joueur, on memorise :
 * <ul>
 *   <li>Le {@link BukkitTask kick differe} planifie au JOIN. Si l'attestation
 *       arrive en temps, on l'annule. Sinon, le task kick et est libere par
 *       Bukkit lui-meme.</li>
 *   <li>La {@link Scope portee} admise une fois l'attestation validee : elle
 *       distingue "attestation valide recue" de "kick deja parti ou joueur
 *       deja deconnecte", et elle ne change plus jusqu'a la deconnexion
 *       (une seconde attestation est ignoree).</li>
 * </ul>
 *
 * <p>Toutes les operations sont thread-safe : un plugin message peut arriver
 * async (Paper) alors que le PlayerJoinEvent est sur le main thread.
 */
public final class AuthSessionState {

    private final ConcurrentHashMap<UUID, PendingAuth> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Scope> authenticated = new ConcurrentHashMap<>();

    /** Memorise le kick task planifie pour ce UUID. */
    public void trackPendingKick(UUID playerUuid, BukkitTask kickTask) {
        authenticated.remove(playerUuid);
        PendingAuth previous = pending.put(playerUuid, new PendingAuth(kickTask));
        if (previous != null) previous.kickTask.cancel();
    }

    /**
     * Marque le joueur comme authentifie avec cette portee et annule le kick
     * s'il est encore planifie. Retourne {@code true} si on a effectivement
     * annule un kick (= attestation arrivee a temps), {@code false} si le
     * joueur n'avait deja plus de kick pendant (deja kick, deconnecte, ou deja
     * authentifie).
     */
    public boolean markAuthenticated(UUID playerUuid, Scope scope) {
        PendingAuth previous = pending.remove(playerUuid);
        if (previous == null) return false;
        previous.kickTask.cancel();
        authenticated.put(playerUuid, scope);
        return true;
    }

    /** Portee admise pour ce joueur, vide tant que l'attestation n'est pas validee. */
    public Optional<Scope> scopeOf(UUID playerUuid) {
        return Optional.ofNullable(authenticated.get(playerUuid));
    }

    /** Cleanup au quit / kick. Evite les fuites memoire sur churn de joueurs. */
    public void forget(UUID playerUuid) {
        authenticated.remove(playerUuid);
        PendingAuth previous = pending.remove(playerUuid);
        if (previous != null) {
            previous.kickTask.cancel();
        }
    }

    public boolean isPending(UUID playerUuid) {
        return pending.containsKey(playerUuid);
    }

    private record PendingAuth(BukkitTask kickTask) {}
}
