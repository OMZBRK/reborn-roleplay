package com.reborn.shinobicore.backpack;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Snapshot of a backpack that has been placed in the world. Carries
 * the persistent backpack id, the size (so we can rebuild the
 * matching item on pickup), the location, and the UUIDs of the two
 * Bukkit entities used to render + receive clicks (an
 * {@code ItemDisplay} for visuals, an {@code Interaction} for the
 * click hitbox).
 *
 * <p>Persisted by {@link BackpackEntityManager} into
 * {@code placed-backpacks.yml} so a server restart can re-attach the
 * pickup/open behaviour to entities that survived the shutdown.
 */
public record PlacedBackpack(
        UUID backpackId,
        BackpackSize size,
        Location location,
        UUID displayEntityId,
        UUID interactionEntityId) {
}
