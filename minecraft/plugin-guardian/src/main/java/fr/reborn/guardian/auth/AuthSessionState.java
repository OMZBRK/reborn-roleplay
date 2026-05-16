package fr.reborn.guardian.auth;

import org.bukkit.scheduler.BukkitTask;

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
 *   <li>Le marqueur {@code authenticated} qui distingue "attestation valide
 *       recue" de "kick deja annule mais joueur deja parti".</li>
 * </ul>
 *
 * <p>Toutes les operations sont thread-safe : un plugin message peut arriver
 * async (Paper) alors que le PlayerJoinEvent est sur le main thread.
 */
public final class AuthSessionState {

    private final ConcurrentHashMap<UUID, PendingAuth> pending = new ConcurrentHashMap<>();

    /** Memorise le kick task planifie pour ce UUID. */
    public void trackPendingKick(UUID playerUuid, BukkitTask kickTask) {
        pending.put(playerUuid, new PendingAuth(kickTask));
    }

    /**
     * Marque le joueur comme authentifie et annule le kick s'il est encore
     * planifie. Retourne {@code true} si on a effectivement annule un kick
     * (= attestation arrivee a temps), {@code false} si le joueur n'avait
     * deja plus de kick pendant (deja kick ou deconnecte).
     */
    public boolean markAuthenticated(UUID playerUuid) {
        PendingAuth previous = pending.remove(playerUuid);
        if (previous == null) return false;
        previous.kickTask.cancel();
        return true;
    }

    /** Cleanup au quit / kick. Evite les fuites memoire sur churn de joueurs. */
    public void forget(UUID playerUuid) {
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
