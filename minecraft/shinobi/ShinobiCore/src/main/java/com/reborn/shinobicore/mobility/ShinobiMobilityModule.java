package com.reborn.shinobicore.mobility;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.chakra.ChakraManager;
import com.reborn.shinobicore.mobility.ability.GrappleAbility;
import com.reborn.shinobicore.mobility.ability.MobilityAbility;
import com.reborn.shinobicore.mobility.ability.ZiplineAbility;
import com.reborn.shinobicore.mobility.cooldown.CooldownManager;

import java.util.List;

/**
 * Minimal mobility container — owns just the kept utility items
 * (Grappin and Zipline) plus the shared {@link CooldownManager}.
 *
 * <p>The legacy module hosted the full ability suite (Naruto Run,
 * Double Jump, Wall Jump, Floor Shockwave, Shinobi Dash, Climb,
 * Trail). Those moved out to the standalone ShinobiAbilities plugin
 * — see {@code ShinobiAbilities_RECREATION_PROMPT.md} for the spec.
 * What remains here is the bare minimum to keep the Grappin and
 * Zipline RP items working as standalone utilities that ShinobiCore
 * ships out of the box.
 */
public class ShinobiMobilityModule implements com.reborn.shinobicore.api.MobilityService {

    private final ShinobiCore plugin;

    private CooldownManager cooldowns;
    private GrappleAbility grapple;
    private ZiplineAbility zipline;

    public ShinobiMobilityModule(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void enable(ChakraManager chakra) {
        this.cooldowns = new CooldownManager();
        this.grapple = new GrappleAbility(plugin, chakra, cooldowns);
        this.zipline = new ZiplineAbility(plugin, chakra, cooldowns);
    }

    public void disable() {
        // No-op for now; abilities are stateless beyond per-player
        // cooldowns which die with the process.
    }

    /* ------------------------------------------------------- getters */

    public CooldownManager cooldowns() { return cooldowns; }
    public GrappleAbility  grapple()   { return grapple; }
    public ZiplineAbility  zipline()   { return zipline; }

    /** Every ability the module exposes — kept for any HUD or
     *  reflection-style iteration. With the legacy abilities removed
     *  this is just the two utility items. */
    public List<MobilityAbility> allAbilities() {
        return List.of(grapple, zipline);
    }
}
