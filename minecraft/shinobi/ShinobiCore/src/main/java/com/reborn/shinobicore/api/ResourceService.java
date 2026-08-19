package com.reborn.shinobicore.api;

import org.bukkit.entity.Player;

/**
 * Access to the world's primary depletable resource (chakra in the
 * Naruto world). Deliberately world-neutral: engine-level code must
 * not assume what the resource is called — read {@link #resourceId()}.
 */
@Stable
public interface ResourceService {

    /** Stable identifier of this resource (Naruto pack: {@code "chakra"}). */
    String resourceId();

    /**
     * The pool backing {@code player}'s active character. Never null —
     * implementations return an inert empty pool when no character is
     * active.
     */
    ResourcePool pool(Player player);
}
