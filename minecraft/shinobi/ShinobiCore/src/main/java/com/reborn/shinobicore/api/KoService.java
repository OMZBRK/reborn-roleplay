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

    /**
     * True while the player's active character is under ATA (wounded,
     * awaiting care) or in pain (HP under the pain threshold): no sprint,
     * no mobility kit, <b>no technique at all</b> (only bare-hand M1, halved,
     * under ATA; nothing in pain). Default false for older cores.
     */
    default boolean isImpaired(UUID playerId) { return false; }

    /**
     * True while the player is KO <b>and still in the downed (agony) phase</b> — à terre, crawling, before falling
     * unconscious. {@code isKo && !isDowned} = unconscious. Default false for older cores.
     */
    default boolean isDowned(UUID playerId) { return false; }

    /**
     * Why the player may not cast a technique right now, or {@code null} when nothing forbids it: consented duel
     * (countdown running, or techniques disabled by {@code duel.techniques}), staff mode, and since the urgent-fix
     * batch (ETATS_JOUEUR §9 step 0) frozen by staff, RP death scene, cinematic, carried / held, <b>stunned</b>,
     * <b>guarding</b>. KO and ATA / pain are read separately ({@link #isKo}, {@link #isImpaired}). Default null for
     * older cores.
     */
    default String techniqueDenial(UUID playerId) { return null; }

    /**
     * True while the player is <b>out of play</b>: a staff member in staff mode ({@code /modestaff}) may use no RP
     * mechanic at all (no hit, technique, interaction menu, duel, pickup, item use, RP block, mount, RP chat) and
     * nobody may target them. Addons gate their own entry points with it. Default false for older cores.
     */
    default boolean isOutOfPlay(UUID playerId) { return false; }

    /**
     * Why the player may not use the mobility kit right now (dash, double jump, wall jump, climb, Naruto run, Flux
     * path, rides), or {@code null}: KO, pain / ATA, frozen, RP death, cinematic, carried / held / holding, staff
     * mode, stunned, guarding, carrying a body. Default null for older cores.
     */
    default String mobilityDenial(UUID playerId) { return null; }

    /**
     * Why a player-requested teleport (training parkour start…) is refused right now, or {@code null}: everything
     * that blocks a character switch (KO, pain, frozen, carried, duel, <b>in combat</b> — 15 s after the last hit…)
     * plus ATA, stun and staff mode. Default null for older cores.
     */
    default String teleportDenial(UUID playerId) { return null; }
}
