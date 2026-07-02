package fr.reborn.ost.plugin.broadcast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry des broadcasts OST positionnels actifs sur le serveur. Permet
 * à un joueur qui entre dans le rayon APRES le {@code /ost play} de recevoir
 * un packet PLAY avec le bon {@code secOffset} pour rejoindre la lecture
 * en cours, et pas attendre le prochain broadcast.
 *
 * <p>Sans ce mécanisme, l'ancien comportement "fire-and-forget" était :
 * <ul>
 *   <li>{@code /ost play <track> 16} envoie un packet aux joueurs dans 16
 *       blocs au moment T.</li>
 *   <li>Un joueur qui entre dans la zone à T+5s ne reçoit jamais rien.</li>
 * </ul>
 *
 * <p>Politique de subscription : un joueur n'est <strong>subscribed</strong>
 * à une zone qu'au moment où il y entre. Une fois subscribed, il l'est pour
 * toute la durée de vie de la zone — même s'il sort puis revient, on ne
 * lui renvoie pas un PLAY (la source OpenAL continue de tourner côté client,
 * c'est l'atténuation manuelle qui le silence quand il est hors rayon).
 *
 * <p>Thread-safety : {@link ConcurrentHashMap} pour permettre au broadcaster
 * (thread main scheduler) et aux listeners (potentiellement async selon
 * l'event) de muter en concurrence. Les opérations bulk
 * ({@link #removeZonesNear}, {@link #clearAll}) sont atomiques par zone
 * mais pas en bloc — acceptable pour Phase 2 (pas de garantie de visibilité
 * snapshot croisée nécessaire).
 */
public final class OstZoneRegistry {

    public record ZoneRecord(
        UUID id,
        UUID worldId,
        double x, double y, double z,
        float radius,
        String trackId,
        float volume,
        long startedAtMs,
        /** Joueur qui a déclenché ce broadcast (null = commande /ost serveur).
         *  Seul lui peut le stopper / mettre en pause pour la zone. */
        UUID owner
    ) {
        /**
         * Distance² (skip {@code sqrt}) entre la zone et un point monde.
         * Renvoie {@link Double#POSITIVE_INFINITY} si le monde diffère —
         * une zone d'overworld n'atteint pas un joueur en nether même
         * coords.
         */
        public double distanceSquaredFrom(UUID otherWorldId, double px, double py, double pz) {
            if (otherWorldId == null || !otherWorldId.equals(worldId)) {
                return Double.POSITIVE_INFINITY;
            }
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final Map<UUID, ZoneRecord> zones = new ConcurrentHashMap<>();
    /** playerUuid → set des zoneIds auxquels il est déjà subscribed. */
    private final Map<UUID, Set<UUID>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Enregistre une nouvelle zone et renvoie le record (avec son UUID).
     * {@code startedAtMs} est snapshotté à l'appel ; pour un broadcast
     * déclenché par {@code /ost play}, c'est le temps zéro de la track.
     */
    public ZoneRecord addZone(UUID worldId, double x, double y, double z,
                              float radius, String trackId, float volume) {
        return addZone(worldId, x, y, z, radius, trackId, volume, null);
    }

    /** Variante avec propriétaire (broadcast déclenché par un joueur). */
    public ZoneRecord addZone(UUID worldId, double x, double y, double z,
                              float radius, String trackId, float volume, UUID owner) {
        Objects.requireNonNull(worldId, "worldId");
        ZoneRecord r = new ZoneRecord(
            UUID.randomUUID(),
            worldId,
            x, y, z,
            radius,
            trackId,
            volume,
            System.currentTimeMillis(),
            owner
        );
        zones.put(r.id(), r);
        return r;
    }

    /** Zones détenues par ce joueur (broadcasts qu'il a déclenchés). */
    public List<ZoneRecord> zonesOwnedBy(UUID owner) {
        List<ZoneRecord> out = new ArrayList<>();
        if (owner == null) return out;
        for (ZoneRecord z : zones.values()) {
            if (owner.equals(z.owner())) out.add(z);
        }
        return out;
    }

    /** Marque un joueur comme subscribed à une zone. Idempotent. */
    public void markSubscribed(UUID playerId, UUID zoneId) {
        subscriptions
            .computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(zoneId);
    }

    public boolean isSubscribed(UUID playerId, UUID zoneId) {
        Set<UUID> set = subscriptions.get(playerId);
        return set != null && set.contains(zoneId);
    }

    public Collection<ZoneRecord> allZones() {
        return zones.values();
    }

    /** Supprime toutes les zones dans {@code radius} blocs autour du point. */
    public List<ZoneRecord> removeZonesNear(UUID worldId, double x, double y, double z, float radius) {
        double r2 = (double) radius * (double) radius;
        List<ZoneRecord> removed = new ArrayList<>();
        zones.values().removeIf(z2 -> {
            if (!z2.worldId().equals(worldId)) return false;
            double dx = z2.x() - x;
            double dy = z2.y() - y;
            double dz = z2.z() - z;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                removed.add(z2);
                return true;
            }
            return false;
        });
        purgeOrphanSubscriptions();
        return removed;
    }

    /** Supprime une zone précise (par id). Renvoie le record ou null. */
    public ZoneRecord remove(UUID zoneId) {
        ZoneRecord r = zones.remove(zoneId);
        if (r != null) purgeOrphanSubscriptions();
        return r;
    }

    /** Supprime toutes les zones, peu importe le monde ou la position. */
    public List<ZoneRecord> clearAll() {
        List<ZoneRecord> removed = new ArrayList<>(zones.values());
        zones.clear();
        subscriptions.clear();
        return removed;
    }

    /** À appeler sur {@code PlayerQuitEvent} pour ne pas leaker des subs. */
    public void onPlayerQuit(UUID playerId) {
        subscriptions.remove(playerId);
    }

    /**
     * Nettoie les références à des zoneIds qui n'existent plus, sinon elles
     * restent dans {@link #subscriptions} jusqu'au quit du joueur et bloquent
     * un futur PLAY si on recrée une zone avec le même UUID (impossible
     * en pratique car UUID.randomUUID, mais hygiène).
     */
    private void purgeOrphanSubscriptions() {
        Set<UUID> live = zones.keySet();
        for (Set<UUID> subs : subscriptions.values()) {
            subs.removeIf(id -> !live.contains(id));
        }
    }
}
