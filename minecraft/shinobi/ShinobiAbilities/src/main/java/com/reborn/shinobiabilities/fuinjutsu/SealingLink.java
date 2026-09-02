package com.reborn.shinobiabilities.fuinjutsu;

import com.reborn.shinobiabilities.ShinobiAbilities;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.transform.ActiveTransformation;
import com.reborn.shinobitail.transform.TransformationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fuinjutsu — <b>Sceau de Contention</b>.
 *
 * <p>A sealing CHANNEL, not a blast: sneak + right-click a transformed
 * jinchūriki to bind your chakra to the beast's. While the link holds,
 * every {@code tick-seconds} the target loses {@code rage-per-tick}%
 * of rage and the sealer pays {@code chakra-percent-per-tick}% of max
 * chakra. When rage reaches 0%, the transformation is SEALED shut.
 *
 * <p>Free to start, {@code cooldown-seconds} between attempts. The link
 * snaps if the sealer takes damage (configurable), runs out of chakra,
 * leaves range, or if either side disconnects. A fully released beast
 * (final stage) is beyond saving — the seal refuses.
 *
 * <p>Registered only when the ShinobiTail plugin is present.
 */
public final class SealingLink implements Listener {

    private static final String COOLDOWN_ID = "fuinjutsu_seal";

    private record Session(UUID casterId, UUID targetId, BukkitTask task) {}

    private final ShinobiAbilities plugin;
    private final CoreServices core;
    private final CooldownTracker cooldowns;
    private final Map<UUID, Session> byCaster = new ConcurrentHashMap<>();

    public SealingLink(ShinobiAbilities plugin, CoreServices core,
                       CooldownTracker cooldowns) {
        this.plugin = plugin;
        this.core = core;
        this.cooldowns = cooldowns;
    }

    private ConfigurationSection cfg() {
        return plugin.getConfig().getConfigurationSection("fuinjutsu.seal");
    }

    private static ShinobiTail tail() {
        return Bukkit.getPluginManager().getPlugin("ShinobiTail") instanceof ShinobiTail st
                && st.isEnabled() ? st : null;
    }

    /* -------------------------------------------------------------- start */

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        Player caster = ev.getPlayer();
        if (!caster.isSneaking()) return;
        if (!(ev.getRightClicked() instanceof Player target)) return;

        ConfigurationSection cfg = cfg();
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;
        ShinobiTail tail = tail();
        if (tail == null) return;
        ActiveTransformation t = tail.transformations().get(target.getUniqueId());
        if (t == null) return; // not transformed — ignore silently

        ev.setCancelled(true);
        if (caster.getUniqueId().equals(target.getUniqueId())) return;
        if (byCaster.containsKey(caster.getUniqueId())) return;

        if (!caster.hasPermission("shinobiabilities.fuinjutsu")) {
            caster.sendMessage(Component.text(
                    "Tu ne connais pas les arts du Fuinjutsu.", NamedTextColor.RED));
            return;
        }
        String abilityId = cfg.getString("ability-id", "");
        if (abilityId != null && !abilityId.isBlank()) {
            ShinobiCharacter active = core.characters()
                    .getActive(caster.getUniqueId());
            if (active == null || !active.knownAbilities().contains(abilityId)) {
                caster.sendMessage(Component.text(
                        "Ton personnage ne connaît pas ce sceau ("
                                + abilityId + ").", NamedTextColor.RED));
                return;
            }
        }
        if (cooldowns.isOnCooldown(caster.getUniqueId(), COOLDOWN_ID)) {
            long left = cooldowns.remainingMillis(
                    caster.getUniqueId(), COOLDOWN_ID) / 1000L + 1;
            caster.sendMessage(Component.text(
                    "Sceau de Contention — recharge " + left + "s.",
                    NamedTextColor.RED));
            return;
        }
        if (t.finalRelease()) {
            caster.sendMessage(Component.text(
                    "Le démon est entièrement libéré… plus aucun sceau ne peut le contenir.",
                    NamedTextColor.DARK_RED));
            return;
        }
        if (t.union()) {
            caster.sendMessage(Component.text(
                    "Leur union est harmonieuse — il n'y a rien à sceller.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (tail.innerWorld().inSession(target.getUniqueId())) {
            caster.sendMessage(Component.text(
                    "Son esprit est ailleurs — le lien ne trouve pas prise.",
                    NamedTextColor.RED));
            return;
        }

        start(caster, target, tail, cfg);
    }

    private void start(Player caster, Player target, ShinobiTail tail,
                       ConfigurationSection cfg) {
        cooldowns.set(caster.getUniqueId(), COOLDOWN_ID,
                1000L * cfg.getLong("cooldown-seconds", 25));

        int tickSeconds = Math.max(1, cfg.getInt("tick-seconds", 5));
        double ragePerTick = cfg.getDouble("rage-per-tick", 5.0);
        double chakraPct = cfg.getDouble("chakra-percent-per-tick", 5.0);
        double maxDist = cfg.getDouble("max-distance", 10.0);

        caster.sendMessage(Component.text(
                "Sceau de Contention — maintiens le lien ! (interrompu si tu subis des dégâts)",
                NamedTextColor.LIGHT_PURPLE));
        target.sendMessage(Component.text(
                "Des chaînes de chakra s'enroulent autour du démon…",
                NamedTextColor.LIGHT_PURPLE));
        caster.getWorld().playSound(caster.getLocation(),
                Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.7f);

        final int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ShinobiTail st = tail();
            if (st == null || !caster.isOnline() || !target.isOnline()) {
                interrupt(caster, null);
                return;
            }
            ActiveTransformation t = st.transformations().get(target.getUniqueId());
            if (t == null) { end(caster, true, target); return; }
            if (st.innerWorld().inSession(target.getUniqueId())) {
                interrupt(caster, "Son esprit a été happé par le Monde Intérieur.");
                return;
            }
            if (!caster.getWorld().equals(target.getWorld())
                    || caster.getLocation().distance(target.getLocation()) > maxDist) {
                interrupt(caster, "Trop loin — le lien se rompt.");
                return;
            }

            if (!com.reborn.shinobicore.util.Tps.shouldDefer()) drawLink(caster, target);

            if (++ticks[0] % tickSeconds == 0) {
                var pool = core.chakra().pool(caster);
                double cost = pool.max() * chakraPct / 100.0;
                if (!pool.has(cost)) {
                    interrupt(caster, "Plus assez de chakra — le sceau se dissout.");
                    return;
                }
                pool.consume(cost);
                t.data().addRage(-ragePerTick);
                caster.playSound(caster.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
                target.playSound(target.getLocation(),
                        Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.6f);
                if (t.data().rage() <= 0.0) {
                    st.transformations().stop(target,
                            TransformationManager.StopReason.SEALED);
                    end(caster, true, target);
                }
            }
        }, 10L, 20L);

        byCaster.put(caster.getUniqueId(),
                new Session(caster.getUniqueId(), target.getUniqueId(), task));
    }

    /* ------------------------------------------------------------- visuals */

    private void drawLink(Player caster, Player target) {
        Location from = caster.getEyeLocation().subtract(0, 0.3, 0);
        Location to = target.getLocation().add(0, 1.2, 0);
        Vector step = to.toVector().subtract(from.toVector());
        int points = (int) Math.max(8, from.distance(to) * 4);
        Particle.DustOptions dust = new Particle.DustOptions(
                Color.fromRGB(0xD8C9FF), 1.1f);
        for (int i = 0; i <= points; i++) {
            Location l = from.clone().add(step.clone().multiply(i / (double) points));
            caster.getWorld().spawnParticle(Particle.DUST, l, 1, 0.03, 0.03, 0.03, 0, dust);
            if (i % 4 == 0) {
                caster.getWorld().spawnParticle(Particle.ENCHANT, l, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    /* ----------------------------------------------------------- end paths */

    private void interrupt(Player caster, String reason) {
        Session s = byCaster.remove(caster.getUniqueId());
        if (s == null) return;
        s.task().cancel();
        if (reason != null && caster.isOnline()) {
            caster.sendMessage(Component.text(reason, NamedTextColor.RED));
            caster.playSound(caster.getLocation(),
                    Sound.ENTITY_ITEM_BREAK, 0.9f, 0.7f);
        }
    }

    private void end(Player caster, boolean success, Player target) {
        Session s = byCaster.remove(caster.getUniqueId());
        if (s != null) s.task().cancel();
        if (success && caster.isOnline()) {
            caster.sendMessage(Component.text(
                    "Le sceau se referme — le démon est contenu.",
                    NamedTextColor.GREEN));
            caster.getWorld().playSound(caster.getLocation(),
                    Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
        }
        if (success && target != null && target.isOnline()) {
            target.sendMessage(Component.text(
                    "Le lien de scellement apaise la rage du démon.",
                    NamedTextColor.LIGHT_PURPLE));
        }
    }

    /* -------------------------------------------------------------- events */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCasterDamaged(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        if (!byCaster.containsKey(p.getUniqueId())) return;
        ConfigurationSection cfg = cfg();
        if (cfg != null && !cfg.getBoolean("interrupt-on-damage", true)) return;
        if (ev.getFinalDamage() <= 0) return;
        interrupt(p, "Touché ! Le sceau vole en éclats.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        interrupt(ev.getPlayer(), null);
        // If the leaver was a TARGET, snap every link pointing at them.
        for (Session s : Map.copyOf(byCaster).values()) {
            if (s.targetId().equals(id)) {
                Player caster = Bukkit.getPlayer(s.casterId());
                if (caster != null) {
                    interrupt(caster, "Le lien s'évanouit — la cible a disparu.");
                }
            }
        }
    }
}
