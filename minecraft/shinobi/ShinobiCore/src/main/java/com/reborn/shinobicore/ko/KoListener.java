package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.injury.BodyPart;
import com.reborn.shinobicore.ko.injury.DamageOrigin;
import com.reborn.shinobicore.ko.injury.Injury;
import com.reborn.shinobicore.ko.injury.InjuryType;
import com.reborn.shinobicore.ko.injury.Severity;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Wires the KO mechanic into Bukkit events.
 *
 * <p>Three jobs:
 * <ol>
 *   <li><b>Damage cap</b> — catches every damage event, prevents the
 *       killing blow from actually killing the player, and instead
 *       routes them into {@link KoManager#enterKo}.</li>
 *   <li><b>Injury auto-tag</b> — at KO entry (and when an already-KO
 *       player keeps taking hits), turn the {@link DamageCause} +
 *       weapon material into a fresh {@link Injury} on a randomly
 *       picked {@link BodyPart} appropriate to the angle/source.</li>
 *   <li><b>Input lockdown</b> — once KO, the player can't move, drop
 *       items, swap hands, place / break blocks, attack, or run
 *       commands (except the welcome toggle). Movement enforcement
 *       lives in {@link KoManager}'s ticker; this class handles the
 *       remaining input gates.</li>
 * </ol>
 *
 * <p>Heal-revive uses {@link EntityRegainHealthEvent} as the trigger
 * — anytime a KO player regains HP from any source, we re-check
 * the heal threshold via {@link KoManager#checkHealRevive}.
 */
public final class KoListener implements Listener {

    private final ShinobiCore plugin;

    public KoListener(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* =================================================== damage cap + KO entry */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;

        // Already KO? Cancel further damage entirely. The body should
        // be treated as a prop until revived; chip damage from
        // surroundings (lava, suffocation) is also blocked.
        if (plugin.ko().isKo(p.getUniqueId())) {
            ev.setCancelled(true);
            return;
        }

        // Would this blow drop them to 0 or below? Cap and KO.
        double after = p.getHealth() - ev.getFinalDamage();
        boolean lethal = after <= 0.5;

        // Rasengan - fire ShinobiDamageEvent for character-players so other
        // systems can react to (or cancel) the hit with RP context.
        if (plugin.characters().getActive(p.getUniqueId()) != null) {
            com.reborn.shinobicore.event.ShinobiDamageEvent sde =
                    new com.reborn.shinobicore.event.ShinobiDamageEvent(
                            p, inferOrigin(p, ev), ev.getCause(), ev.getFinalDamage(), lethal);
            org.bukkit.Bukkit.getPluginManager().callEvent(sde);
            if (sde.isCancelled()) { ev.setCancelled(true); return; }
        }

        if (lethal) {
            ev.setCancelled(true);
            // Pin HP at a sliver so the visual "almost dead" state
            // reads correctly until the title card hits.
            p.setHealth(Math.max(0.5, Math.min(1.0, p.getHealth())));
            triggerKo(p, ev);
        }
    }

    /** Build a KO state + record the injury that pushed them over. */
    private void triggerKo(Player p, EntityDamageEvent ev) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        UUID charId = c != null ? c.id() : null;
        plugin.ko().enterKo(p, charId,
                com.reborn.shinobicore.ko.KoState.Cause.HP);
        if (c != null) {
            Injury inj = diagnose(p, c, ev);
            if (inj != null) {
                // Rasengan - let listeners veto the injury (resistances, armour).
                com.reborn.shinobicore.event.InjuryApplyEvent iae =
                        new com.reborn.shinobicore.event.InjuryApplyEvent(p, inj);
                org.bukkit.Bukkit.getPluginManager().callEvent(iae);
                if (!iae.isCancelled()) {
                    c.addInjury(inj);
                    com.reborn.shinobicore.ko.injury.InjuryMerger.mergeAll(c.injuries());
                    plugin.characterRepository().save(c);
                }
            }
        }
    }

    /** Map a damage event to an {@link Injury}. Picks origin from the
     *  cause + weapon, type from the origin, severity from how much of
     *  the player's max-HP this hit consumed, and a random plausible
     *  body part. */
    private Injury diagnose(Player p, ShinobiCharacter c, EntityDamageEvent ev) {
        DamageOrigin origin = inferOrigin(p, ev);
        InjuryType   type   = typeFor(origin);
        BodyPart     part   = randomBodyPartFor(origin);
        double frac = ev.getFinalDamage() / Math.max(1.0, p.getMaxHealth());
        Severity sev = Severity.fromDamageFraction(frac);
        return Injury.create(part, type, origin, sev);
    }

    /** Best-guess of what kind of attack hit the player. */
    private DamageOrigin inferOrigin(Player p, EntityDamageEvent ev) {
        EntityDamageEvent.DamageCause cause = ev.getCause();
        switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                return DamageOrigin.FEU;
            }
            case FALL, FLY_INTO_WALL -> {
                return DamageOrigin.CHUTE;
            }
            case POISON, MAGIC, WITHER -> {
                return DamageOrigin.POISON;
            }
            default -> { /* fall through */ }
        }
        // Entity-based damage: inspect the weapon/attacker.
        if (ev instanceof EntityDamageByEntityEvent edbe) {
            Entity dmgr = edbe.getDamager();
            if (dmgr instanceof Player attacker) {
                ItemStack weapon = attacker.getInventory().getItemInMainHand();
                Material m = weapon != null ? weapon.getType() : Material.AIR;
                if (m == Material.AIR) return DamageOrigin.TAIJUTSU;
                if (isSword(m) || isAxe(m)) return DamageOrigin.KENJUTSU;
                // TODO: hook into TechniquesService PDC marker on the
                //       weapon for elemental ability tagging once it's
                //       wired (Katon scroll → KATON, etc.)
                return DamageOrigin.TAIJUTSU;
            }
        }
        return DamageOrigin.AUTRE;
    }

    private static boolean isSword(Material m) {
        return m == Material.WOODEN_SWORD || m == Material.STONE_SWORD
                || m == Material.IRON_SWORD || m == Material.GOLDEN_SWORD
                || m == Material.DIAMOND_SWORD || m == Material.NETHERITE_SWORD;
    }
    private static boolean isAxe(Material m) {
        return m == Material.WOODEN_AXE || m == Material.STONE_AXE
                || m == Material.IRON_AXE || m == Material.GOLDEN_AXE
                || m == Material.DIAMOND_AXE || m == Material.NETHERITE_AXE;
    }

    /** Origin → most plausible wound type. */
    private static InjuryType typeFor(DamageOrigin origin) {
        return switch (origin) {
            case FEU, KATON     -> InjuryType.BRULURE;
            case CHUTE, DOTON   -> InjuryType.OS_CASSE;
            case KENJUTSU       -> InjuryType.PLAIE;
            case TAIJUTSU       -> InjuryType.HEMATOME;
            case POISON         -> InjuryType.INFECTION;
            default             -> InjuryType.HEMATOME;
        };
    }

    /** Distribute injuries plausibly: falls hit legs/feet, fire hits
     *  arms/torso, lightning hits head/torso, etc. */
    private static BodyPart randomBodyPartFor(DamageOrigin origin) {
        BodyPart[] pool = switch (origin) {
            case CHUTE -> new BodyPart[] {
                    BodyPart.JAMBE_GAUCHE, BodyPart.JAMBE_DROITE,
                    BodyPart.PIED_GAUCHE,  BodyPart.PIED_DROIT,
                    BodyPart.BUSTE_GAUCHE, BodyPart.BUSTE_DROIT };
            case FEU, KATON -> new BodyPart[] {
                    BodyPart.BRAS_GAUCHE,   BodyPart.BRAS_DROIT,
                    BodyPart.BUSTE_GAUCHE,  BodyPart.BUSTE_DROIT,
                    BodyPart.VENTRE_GAUCHE, BodyPart.VENTRE_DROIT,
                    BodyPart.EPAULE_GAUCHE, BodyPart.EPAULE_DROITE };
            case RAITON -> new BodyPart[] {
                    BodyPart.TETE_GAUCHE, BodyPart.TETE_DROIT,
                    BodyPart.BUSTE_GAUCHE,  BodyPart.BUSTE_DROIT,
                    BodyPart.VENTRE_GAUCHE, BodyPart.VENTRE_DROIT };
            case DOTON -> new BodyPart[] {
                    BodyPart.JAMBE_GAUCHE, BodyPart.JAMBE_DROITE,
                    BodyPart.BRAS_GAUCHE,  BodyPart.BRAS_DROIT };
            case KENJUTSU -> BodyPart.values();        // anywhere
            case TAIJUTSU -> new BodyPart[] {
                    BodyPart.TETE_GAUCHE, BodyPart.TETE_DROIT,
                    BodyPart.BUSTE_GAUCHE,  BodyPart.BUSTE_DROIT,
                    BodyPart.VENTRE_GAUCHE, BodyPart.VENTRE_DROIT,
                    BodyPart.EPAULE_GAUCHE, BodyPart.EPAULE_DROITE };
            default -> BodyPart.values();
        };
        return pool[(int) (Math.random() * pool.length)];
    }

    /* ============================================================ heal-revive */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        if (!plugin.ko().isKo(p.getUniqueId())) return;
        // Allow the heal to land first; we re-check on the next tick
        // so the regen has actually applied.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.ko().checkHealRevive(p));
    }

    /* ============================================================ death route */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent ev) {
        // Defence in depth — if anything bypassed the cap, route
        // through the KO system instead of letting the player die.
        Player p = ev.getEntity();
        if (plugin.ko().isKo(p.getUniqueId())) {
            // Already KO and somehow died: clear KO so revive doesn't
            // try to operate on a dead-player ghost; the actual death
            // path is intentionally unreachable in this design (only
            // /tuer can kill).
            plugin.ko().forceClear(p.getUniqueId());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent ev) {
        // If the player is mid-KO when they reconnect or respawn
        // (e.g. server crashed, ko-state.yml restored), re-arm
        // effects so blindness/slowness latch in immediately.
        Player p = ev.getPlayer();
        if (plugin.ko().isKo(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.ko().applyKoEffects(p), 5L);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        if (plugin.ko().isKo(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.ko().applyKoEffects(p), 5L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        // Drop any carry mount the leaving player was on/holding.
        UUID id = ev.getPlayer().getUniqueId();
        plugin.porter().releaseAllInvolving(id);
    }

    /* ============================================================ input lock */

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent ev) {
        if (!plugin.ko().isKo(ev.getPlayer().getUniqueId())) return;
        // Allow look-only changes; cancel any positional movement.
        if (ev.getFrom().getX() != ev.getTo().getX()
                || ev.getFrom().getY() != ev.getTo().getY()
                || ev.getFrom().getZ() != ev.getTo().getZ()) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent ev) {
        if (plugin.ko().isKo(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent ev) {
        if (plugin.ko().isKo(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent ev) {
        if (plugin.ko().isKo(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent ev) {
        if (plugin.ko().isKo(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent ev) {
        if (plugin.ko().isKo(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    /* ============================================================ KO right-click */

    @EventHandler(ignoreCancelled = true)
    public void onInteractKoBody(PlayerInteractEntityEvent ev) {
        // Filter to main-hand only — PlayerInteractEntityEvent fires
        // twice (main + off hand) on every right-click; without this
        // gate the menu would open and immediately re-open.
        if (ev.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!(ev.getRightClicked() instanceof Player target)) return;
        // Right-clicking your own body or a non-KO player does nothing
        // (lets vanilla behaviour through).
        if (!plugin.ko().isKo(target.getUniqueId())) return;
        ev.setCancelled(true);
        Player viewer = ev.getPlayer();
        // The viewer themselves can't be KO (they wouldn't be able to
        // click anyway, but defensively).
        if (plugin.ko().isKo(viewer.getUniqueId())) return;
        com.reborn.shinobicore.character.ShinobiCharacter targetChar =
                plugin.characters().getActive(target.getUniqueId());
        plugin.coreGui().openKoAction(viewer, target.getUniqueId(), targetChar);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        if (!plugin.ko().isKo(ev.getPlayer().getUniqueId())) return;
        // Soft allowlist: a KO player can still call /sc help to read
        // the welcome reminder, but no gameplay commands.
        String cmd = ev.getMessage().toLowerCase();
        if (cmd.startsWith("/sc help") || cmd.startsWith("/shinobi help")
                || cmd.startsWith("/sc welcome") || cmd.startsWith("/shinobi welcome")) {
            return;
        }
        ev.setCancelled(true);
    }
}
