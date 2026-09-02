package com.reborn.shinobicore.dummy;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.injury.BodyPart;
import com.reborn.shinobicore.ko.injury.DamageOrigin;
import com.reborn.shinobicore.ko.injury.Injury;
import com.reborn.shinobicore.ko.injury.InjuryType;
import com.reborn.shinobicore.ko.injury.Severity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Routes damage to {@link Dummy} entities through their virtual HP
 * pool instead of the Villager's Bukkit health.
 *
 * <p>For every {@link EntityDamageEvent} on a Villager carrying our
 * {@link DummyManager#DUMMY_KEY} marker:
 * <ol>
 *   <li>Cancel the actual Bukkit damage so the Villager doesn't die.</li>
 *   <li>Subtract the would-have-been damage from the dummy's
 *       {@link Dummy#currentHp}.</li>
 *   <li>Auto-tag an {@link Injury} with the damage source —
 *       sword=Kenjutsu, fist=Taijutsu, fire/lava=Brûlure, etc. —
 *       same heuristic the player KoListener uses, so the silhouette
 *       in {@code /osculter} reads identical to a real KO.</li>
 *   <li>If the dummy's HP hits 0, mark it KO and refresh visuals.</li>
 * </ol>
 *
 * <p>Existing dummy KO state means subsequent hits do nothing — same
 * as a downed player. {@code /dummy heal} resets the state.
 */
public final class DummyListener implements Listener {

    private final ShinobiCore plugin;

    public DummyListener(ShinobiCore plugin) { this.plugin = plugin; }

    /** Run at LOWEST priority so we see the event before anything
     *  else can cancel it (e.g. a regional protection plugin). We
     *  intentionally do NOT cancel the damage ourselves — letting it
     *  land gives the player the red flash, hurt sound, and knockback
     *  feedback they expect from an attack. The Villager would die
     *  on a fatal hit, so we schedule a 1-tick task to restore its
     *  Bukkit HP back to max. Virtual HP is tracked on the
     *  {@link Dummy} record. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Villager v)) return;
        String raw = v.getPersistentDataContainer().get(
                plugin.dummies().markerKey(), PersistentDataType.STRING);
        if (raw == null) return;
        UUID dummyId;
        try { dummyId = UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { return; }

        Dummy d = plugin.dummies().byEntity(v.getUniqueId());
        if (d == null || !d.id().equals(dummyId)) return;

        // Cap incoming damage so the Villager never actually dies on
        // this tick. We leave at least 1 HP, then restore to max on
        // the next tick. The capped value still produces a visible
        // red-flash hurt animation + hurt sound — that's the whole
        // point of letting the event land instead of cancelling.
        double rawDmg = Math.max(0.0, ev.getFinalDamage());
        double maxAllowed = Math.max(0.0, v.getHealth() - 1.0);
        if (rawDmg > maxAllowed) {
            ev.setDamage(maxAllowed);
        }

        if (d.ko()) {
            // Already KO — virtual HP doesn't move, but the body
            // still gets hurt-flashed so attackers see they hit
            // something. Refresh the floating name in case anything
            // perturbed it.
            scheduleHpRestore(v);
            plugin.dummies().refreshVisual(d);
            return;
        }

        // Will this hit transition the dummy into KO? If so, worsen
        // every pre-existing injury BEFORE the new one is appended,
        // so the freshly-tagged wound for this hit keeps its natural
        // starting severity and the old wounds get bumped one tier.
        boolean willKo = d.currentHp() - rawDmg <= 0.0;
        if (willKo) {
            for (Injury old : d.injuries()) {
                if (old.hidden()) continue;
                Severity worse = old.severity().upgrade();
                if (worse != old.severity()) {
                    old.setSeverity(worse);
                    old.setNextHealableMillis(0L);
                }
            }
        }

        // Virtual HP uses the ORIGINAL pre-cap damage so a sword that
        // would deal 9999 still wipes the dummy's full pool, even
        // though only 19 HP landed visually.
        d.setCurrentHp(d.currentHp() - rawDmg);

        // Auto-tag injury for THIS hit.
        DamageOrigin origin = inferOrigin(ev);
        InjuryType   type   = typeFor(origin);
        BodyPart     part   = randomPartFor(origin);
        Severity     sev    = Severity.fromDamageFraction(rawDmg / Math.max(1.0, d.maxHp()));
        d.addInjury(Injury.create(part, type, origin, sev));

        // Consolidate everything that just changed: the upgrade pass
        // (if KO) plus the freshly-tagged injury can both push a body
        // part across a merge threshold.
        com.reborn.shinobicore.ko.injury.InjuryMerger.mergeAll(d.injuries());

        if (willKo) {
            d.setKo(true);
        }
        scheduleHpRestore(v);
        plugin.dummies().refreshVisual(d);
        plugin.dummies().save();
    }

    /** Bring the Villager's Bukkit HP back to max on the next tick so
     *  the entity never dies. We can't do it in the event handler
     *  itself because Bukkit applies the damage AFTER the event
     *  returns. */
    private void scheduleHpRestore(Villager v) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (v.isValid() && !v.isDead()) {
                v.setHealth(v.getMaxHealth());
            }
        }, 1L);
    }

    /* ============================================================ right-click */

    /** Right-click on a dummy Villager opens the same KO action menu
     *  the player gets on a downed teammate (État / Tuer / Fouiller /
     *  Porter). Cancels the vanilla trade GUI. Fires only on main-hand
     *  to avoid the off-hand re-fire. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractDummy(PlayerInteractEntityEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        if (!(ev.getRightClicked() instanceof Villager v)) return;
        Dummy d = plugin.dummies().byEntity(v.getUniqueId());
        if (d == null) return;
        ev.setCancelled(true);
        plugin.coreGui().openKoActionForDummy(ev.getPlayer(), d);
    }

    /* ----------------------------------------- damage source heuristics */

    private static DamageOrigin inferOrigin(EntityDamageEvent ev) {
        switch (ev.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> { return DamageOrigin.FEU; }
            case FALL, FLY_INTO_WALL              -> { return DamageOrigin.CHUTE; }
            case POISON, MAGIC, WITHER            -> { return DamageOrigin.POISON; }
            default -> { /* fall through */ }
        }
        if (ev instanceof EntityDamageByEntityEvent edbe) {
            Entity dmgr = edbe.getDamager();
            if (dmgr instanceof Player p) {
                ItemStack w = p.getInventory().getItemInMainHand();
                Material m = w != null ? w.getType() : Material.AIR;
                if (m == Material.AIR) return DamageOrigin.TAIJUTSU;
                if (isSword(m) || isAxe(m)) return DamageOrigin.KENJUTSU;
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

    private static BodyPart randomPartFor(DamageOrigin origin) {
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
            default -> BodyPart.values();
        };
        return pool[(int) (Math.random() * pool.length)];
    }
}
