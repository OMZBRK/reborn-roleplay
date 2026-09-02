package com.reborn.shinobicore.api;

import com.reborn.shinobicore.mobility.ability.GrappleAbility;
import com.reborn.shinobicore.mobility.ability.ZiplineAbility;

/**
 * Handles to the engine-owned mobility utilities (grapple + zipline).
 * A feature-module surface, not a core engine concept — expect this
 * to move behind the Module lifecycle in the decomposition phase.
 * The concrete ability types are exposed as-is for now.
 */
@Stable
public interface MobilityService {

    GrappleAbility grapple();

    ZiplineAbility zipline();
}
