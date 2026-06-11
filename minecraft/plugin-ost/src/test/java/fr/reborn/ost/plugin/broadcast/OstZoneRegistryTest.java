package fr.reborn.ost.plugin.broadcast;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests purs (sans Bukkit) du {@link OstZoneRegistry}. La logique de
 * tracking des subscriptions et de filtrage par monde/distance doit
 * fonctionner sans démarrer un serveur Paper.
 */
class OstZoneRegistryTest {

    private final UUID overworld = UUID.randomUUID();
    private final UUID nether = UUID.randomUUID();

    @Test
    void addZoneAssignsUniqueIdAndPreservesFields() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var z = reg.addZone(overworld, 10, 64, 20, 16f, "apaisant/track-01", 0.8f);

        assertNotNull(z.id());
        assertEquals(overworld, z.worldId());
        assertEquals(10.0, z.x());
        assertEquals(64.0, z.y());
        assertEquals(20.0, z.z());
        assertEquals(16f, z.radius());
        assertEquals("apaisant/track-01", z.trackId());
        assertEquals(0.8f, z.volume());
        assertTrue(z.startedAtMs() > 0);

        assertEquals(1, reg.allZones().size());
    }

    @Test
    void distanceSquaredFromReturnsInfinityCrossWorld() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var z = reg.addZone(overworld, 0, 0, 0, 16f, "t", 1f);
        // Même coords mais autre monde → INF (zone overworld n'atteint pas le nether)
        assertEquals(Double.POSITIVE_INFINITY, z.distanceSquaredFrom(nether, 0, 0, 0));
        // Et null world id pareil — joueur dans un monde "indéterminé".
        assertEquals(Double.POSITIVE_INFINITY, z.distanceSquaredFrom(null, 0, 0, 0));
    }

    @Test
    void distanceSquaredFromComputesEuclideanWhenSameWorld() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var z = reg.addZone(overworld, 0, 0, 0, 16f, "t", 1f);
        // (3,4,12) : 9 + 16 + 144 = 169
        assertEquals(169.0, z.distanceSquaredFrom(overworld, 3, 4, 12), 1e-6);
    }

    @Test
    void subscriptionTracking() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var z = reg.addZone(overworld, 0, 0, 0, 16f, "t", 1f);
        UUID alice = UUID.randomUUID();

        assertFalse(reg.isSubscribed(alice, z.id()));
        reg.markSubscribed(alice, z.id());
        assertTrue(reg.isSubscribed(alice, z.id()));

        // Idempotent : pas d'effet de bord à re-marquer
        reg.markSubscribed(alice, z.id());
        assertTrue(reg.isSubscribed(alice, z.id()));
    }

    @Test
    void onPlayerQuitClearsSubs() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var z = reg.addZone(overworld, 0, 0, 0, 16f, "t", 1f);
        UUID alice = UUID.randomUUID();
        reg.markSubscribed(alice, z.id());

        reg.onPlayerQuit(alice);
        // Après quit, le joueur n'est plus subscribed → un futur tick lui
        // renverrait un PLAY s'il revient sur le serveur (logique attendue).
        assertFalse(reg.isSubscribed(alice, z.id()));
    }

    @Test
    void removeZonesNearOnlyTouchesMatchingWorldAndRadius() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var inRange = reg.addZone(overworld, 10, 0, 0, 16f, "a", 1f);
        var outOfRange = reg.addZone(overworld, 100, 0, 0, 16f, "b", 1f);
        var wrongWorld = reg.addZone(nether, 10, 0, 0, 16f, "c", 1f);

        // Stop 20 blocs autour de (0,0,0) en overworld → ne tape que inRange.
        List<OstZoneRegistry.ZoneRecord> removed = reg.removeZonesNear(overworld, 0, 0, 0, 20f);
        assertEquals(1, removed.size());
        assertEquals(inRange.id(), removed.get(0).id());

        // Les deux autres restent.
        assertEquals(2, reg.allZones().size());
        assertTrue(reg.allZones().stream().anyMatch(z -> z.id().equals(outOfRange.id())));
        assertTrue(reg.allZones().stream().anyMatch(z -> z.id().equals(wrongWorld.id())));
    }

    @Test
    void clearAllRemovesEverything() {
        OstZoneRegistry reg = new OstZoneRegistry();
        reg.addZone(overworld, 0, 0, 0, 16f, "a", 1f);
        reg.addZone(nether, 0, 0, 0, 16f, "b", 1f);
        UUID alice = UUID.randomUUID();
        reg.markSubscribed(alice, reg.allZones().iterator().next().id());

        List<OstZoneRegistry.ZoneRecord> removed = reg.clearAll();
        assertEquals(2, removed.size());
        assertTrue(reg.allZones().isEmpty());
        // Subscriptions purgées aussi : un joueur ne reste pas "subscribed"
        // à une zone qui n'existe plus.
        assertFalse(reg.isSubscribed(alice, removed.get(0).id()));
    }

    @Test
    void removeZonesNearPurgesOrphanSubscriptions() {
        OstZoneRegistry reg = new OstZoneRegistry();
        var z = reg.addZone(overworld, 0, 0, 0, 16f, "t", 1f);
        UUID alice = UUID.randomUUID();
        reg.markSubscribed(alice, z.id());
        assertTrue(reg.isSubscribed(alice, z.id()));

        reg.removeZonesNear(overworld, 0, 0, 0, 1f);
        // La sub doit avoir été nettoyée : la zone supprimée n'est plus
        // dans le registry, donc isSubscribed renvoie false même si on
        // n'a pas explicitement appelé onPlayerQuit.
        assertFalse(reg.isSubscribed(alice, z.id()));
    }
}
