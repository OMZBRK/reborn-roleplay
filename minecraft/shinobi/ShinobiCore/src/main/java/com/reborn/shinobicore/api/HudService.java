package com.reborn.shinobicore.api;

import com.reborn.shinobicore.mobility.hud.CooldownHud;

/**
 * The shared sidebar HUD. Dependent plugins contribute sections
 * (register in onEnable, unregister in onDisable); the engine owns
 * the ticker, diff-gating and TPS deference.
 *
 * <p>{@link CooldownHud.HudSection} is referenced from its current
 * home for now; relocating the section type into {@code api} is
 * deferred until the HUD moves in the engine decomposition phase.
 */
@Stable
public interface HudService {

    void registerSection(CooldownHud.HudSection section);

    void unregisterSection(CooldownHud.HudSection section);
}
