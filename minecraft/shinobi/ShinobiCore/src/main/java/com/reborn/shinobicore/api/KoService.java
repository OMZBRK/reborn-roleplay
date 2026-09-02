package com.reborn.shinobicore.api;

import java.util.UUID;

/**
 * The unconscious-state machine (KO). World-agnostic: any world has a
 * collapse state; what causes it (chakra debt, cursed-energy burnout)
 * is content.
 */
@Stable
public interface KoService {

    /** True while the player is KO. */
    boolean isKo(UUID playerId);

    /**
     * Wake a KO'd player with the given HP and primary-resource
     * amounts (absolute values, not percentages).
     */
    void revive(UUID playerId, double hp, double resource);
}
