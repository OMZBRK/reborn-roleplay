package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.technique.ExecutionMethod;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cast orchestration. The one invariant that has bitten before, kept
 * front and centre:
 *
 * <pre>
 * fire(player, def):
 *   if not gateOrWarn(player, def): return   // no incantation, no bar, no sound
 *   incantation.start(player, def, () -> doFire(player, def))
 * </pre>
 *
 * The chakra / cooldown / character gate runs <b>before</b> the
 * incantation starts — an on-cooldown jutsu must never flash the boss
 * bar or play a single mudra sound.
 */
public final class JutsuExecutionManager {

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final AbilityRegistry registry;
    private final JutsuBindingStore bindings;
    private final JutsuHotbarManager hotbar;
    private final IncantationManager incantation;
    private final CooldownTracker cooldowns;
    private final JutsuEffectRegistry effects;

    /* trigger state */
    private record ClickSeq(int bindingIndex, int count, long firstAt) {}
    private final Map<UUID, ClickSeq> clickSequences = new HashMap<>();
    private final Map<UUID, Long> sneakStarts = new HashMap<>();
    private final Map<UUID, Long> lastQuickCast = new HashMap<>();

    private static final long CLICK_SEQUENCE_WINDOW_MS = 2500L;
    private static final int CLICK_SEQUENCE_COUNT = 3;
    private static final long HOLD_SNEAK_MIN_MS = 1000L;

    public JutsuExecutionManager(JavaPlugin plugin, CoreServices core,
                                 AbilityRegistry registry,
                                 JutsuBindingStore bindings,
                                 JutsuHotbarManager hotbar,
                                 IncantationManager incantation,
                                 CooldownTracker cooldowns,
                                 JutsuEffectRegistry effects) {
        this.plugin = plugin;
        this.core = core;
        this.registry = registry;
        this.bindings = bindings;
        this.hotbar = hotbar;
        this.incantation = incantation;
        this.cooldowns = cooldowns;
        this.effects = effects;
    }

    public CooldownTracker cooldowns() { return cooldowns; }

    /* ------------------------------------------------------------ triggers */

    /** Arm swing (left click). Routes picker-fire or quick-cast. */
    public void onLeftClick(Player p) {
        if (hotbar.isOpen(p)) {
            int idx = hotbar.selectedBindingIndex(p);
            if (idx < 0) return;
            Ability a = boundAbility(p, idx);
            if (a == null) return;
            ExecutionMethod m = a.jutsu().method();
            if (m.firesOnLeftClick()) {
                castFromPicker(p, a);
            } else if (m == ExecutionMethod.CLICK_SEQUENCE) {
                trackClick(p, idx, a);
            }
            return;
        }
        // Picker closed: quick-cast slot 1 of the held JutsuItem.
        JutsuItemType type = JutsuItems.typeOf(p.getInventory().getItemInMainHand());
        if (type != null) quickCast(p, type);
    }

    /** Right click while holding/inside a picker context. */
    public void onRightClick(Player p) {
        if (!hotbar.isOpen(p)) return;
        int idx = hotbar.selectedBindingIndex(p);
        if (idx < 0) return;
        Ability a = boundAbility(p, idx);
        if (a == null) return;
        ExecutionMethod m = a.jutsu().method();
        if (m == ExecutionMethod.RIGHT_CLICK) {
            castFromPicker(p, a);
        } else if (m == ExecutionMethod.CLICK_SEQUENCE) {
            trackClick(p, idx, a);
        }
    }

    /** Sneak press/release while the picker is open (HOLD_SNEAK). */
    public void onSneak(Player p, boolean sneaking) {
        if (!hotbar.isOpen(p)) { sneakStarts.remove(p.getUniqueId()); return; }
        UUID id = p.getUniqueId();
        if (sneaking) {
            sneakStarts.put(id, System.currentTimeMillis());
            return;
        }
        Long start = sneakStarts.remove(id);
        if (start == null) return;
        if (System.currentTimeMillis() - start < HOLD_SNEAK_MIN_MS) return;
        int idx = hotbar.selectedBindingIndex(p);
        if (idx < 0) return;
        Ability a = boundAbility(p, idx);
        if (a == null || a.jutsu().method() != ExecutionMethod.HOLD_SNEAK) return;
        castFromPicker(p, a);
    }

    /** Hotbar slot change inside the picker — cancels the cast-in-flight
     *  and resets trigger state. */
    public void onSlotChange(Player p) {
        incantation.cancel(p);
        clickSequences.remove(p.getUniqueId());
        sneakStarts.remove(p.getUniqueId());
    }

    /** Clear every transient trigger state (quit / KO / switch). */
    public void clearState(Player p) {
        UUID id = p.getUniqueId();
        clickSequences.remove(id);
        sneakStarts.remove(id);
        lastQuickCast.remove(id);
        incantation.cancel(p);
    }

    private void trackClick(Player p, int idx, Ability a) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        ClickSeq seq = clickSequences.get(id);
        if (seq == null || seq.bindingIndex() != idx
                || now - seq.firstAt() > CLICK_SEQUENCE_WINDOW_MS) {
            clickSequences.put(id, new ClickSeq(idx, 1, now));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
            return;
        }
        int count = seq.count() + 1;
        if (count >= CLICK_SEQUENCE_COUNT) {
            clickSequences.remove(id);
            castFromPicker(p, a);
        } else {
            clickSequences.put(id, new ClickSeq(idx, count, seq.firstAt()));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f + 0.1f * count);
        }
    }

    /* --------------------------------------------------------------- casts */

    /** Picker fire: gate FIRST, then incantation, then doFire. */
    public void castFromPicker(Player p, Ability a) {
        if (incantation.isCasting(p)) return;     // one incantation at a time
        ShinobiCharacter c = gateOrWarn(p, a);
        if (c == null) return;
        JutsuItemType type = hotbar.session(p) != null
                ? hotbar.session(p).type() : a.jutsu().itemType();
        incantation.start(p, type, a, () -> doFire(p, a));
    }

    /**
     * Quick-cast: slot 1 of {@code type}'s bindings, INSTANT — no
     * incantation, no delay, one feedback sound. The panic button.
     */
    public void quickCast(Player p, JutsuItemType type) {
        if (!plugin.getConfig().getBoolean("jutsu.quickcast.enabled", true)) return;
        long debounce = plugin.getConfig().getLong("jutsu.quickcast.debounce-ms", 250L);
        long now = System.currentTimeMillis();
        Long last = lastQuickCast.get(p.getUniqueId());
        if (last != null && now - last < debounce) return;
        lastQuickCast.put(p.getUniqueId(), now);

        ShinobiCharacter c = com.reborn.shinobicore.util.Players.active(core.characters(), p);
        if (c == null) return; // silent — bare item in hand, no spam
        String abilityId = bindings.get(c.id(), type, 0);
        if (abilityId == null) return;
        Ability a = registry.byId(abilityId);
        if (a == null || !a.isCastable()) return;

        if (gateOrWarn(p, a) == null) return;
        // Quick-cast interrupts any picker cast in flight.
        incantation.cancel(p);
        p.playSound(p.getLocation(), type.incantationSound(), 0.8f, 1.2f);
        doFire(p, a);
    }

    /* ---------------------------------------------------------------- gate */

    /**
     * The pre-cast gate. Checks active character, KO state, learned
     * requirement, cooldown and chakra — sends the proper action-bar
     * warning and returns null on any failure. MUST run before any
     * incantation starts.
     */
    public ShinobiCharacter gateOrWarn(Player p, Ability a) {
        ShinobiCharacter c = com.reborn.shinobicore.util.Players.activeOrWarn(core.characters(), p);
        if (c == null) return null;

        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) {
            actionBar(p, "Tu es inconscient…", NamedTextColor.DARK_RED);
            return null;
        }
        if (requireLearned() && !p.hasPermission("shinobiabilities.admin")
                && !c.knowsAbility(a.id())) {
            actionBar(p, "Technique non apprise : " + a.name(), NamedTextColor.RED);
            return null;
        }
        long remaining = cooldowns.remainingMillis(p.getUniqueId(), a.id());
        if (remaining > 0) {
            actionBar(p, "⏳ " + a.name() + " — encore "
                    + String.format("%.1f", remaining / 1000.0) + "s", NamedTextColor.YELLOW);
            return null;
        }
        double cost = effectiveCost(c, a);
        // No hard chakra gate when overdraw is on: a character may always
        // attempt a technique — overreaching pushes them into chakra debt and
        // ShinobiCore's ExhaustionManager makes them pay (debuffs → KO). Set
        // jutsu.overdraw false to restore the old "refuse if unaffordable" gate.
        if (!plugin.getConfig().getBoolean("jutsu.overdraw", true)
                && !c.chakra().has(cost)) {
            actionBar(p, "Chakra insuffisant (" + (int) cost + " requis)",
                    NamedTextColor.AQUA);
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.8f);
            return null;
        }
        return c;
    }

    /**
     * Spend + fire. No re-check of cooldown/chakra beyond the consume
     * call itself — the gate ran before the incantation, and the cancel
     * hooks (KO / quit / switch / slot change) cover the races.
     *
     * <p>Effect routing: the internal registry effect when the jutsu
     * declares one, plus any external commands (MagicSpells bridge) —
     * either side may be absent, never both.
     */
    private void doFire(Player p, Ability a) {
        if (!p.isOnline()) return;
        ShinobiCharacter c = com.reborn.shinobicore.util.Players.active(core.characters(), p);
        if (c == null) return;
        double cost = effectiveCost(c, a);
        if (plugin.getConfig().getBoolean("jutsu.overdraw", true)) {
            // Always spends; any shortfall becomes chakra debt → exhaustion.
            c.chakra().overdraw(cost);
        } else if (!c.chakra().consume(cost)) {
            actionBar(p, "Chakra insuffisant (" + (int) cost + " requis)",
                    NamedTextColor.AQUA);
            return;
        }
        cooldowns.set(p.getUniqueId(), a.id(), effectiveCooldown(c, a));
        if (a.jutsu().effectKey() != null) {
            effects.dispatch(plugin, p, a);
        }
        dispatchCommands(p, c, a);
        // Mastery trickles up with use — the "training / performance" path to
        // the 0-90 efficiency / 90%+ enhanced model (consumers land later).
        int masteryGain = plugin.getConfig().getInt("jutsu.mastery-per-cast", 1);
        if (masteryGain > 0 && c.abilityMastery(a.id()) < 100) {
            c.addAbilityMastery(a.id(), masteryGain);
        }
        actionBar(p, "✦ " + a.name(), NamedTextColor.AQUA);
    }

    /** Run the jutsu's external commands (MagicSpells & co). */
    private void dispatchCommands(Player p, ShinobiCharacter c, Ability a) {
        if (!a.jutsu().hasCommands()) return;
        var loc = p.getLocation();
        for (String raw : a.jutsu().commands()) {
            String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
            cmd = cmd.replace("%player%", p.getName())
                    .replace("%uuid%", p.getUniqueId().toString())
                    .replace("%character%", c.name())
                    .replace("%world%", loc.getWorld().getName())
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            try {
                if (a.jutsu().runAsPlayer()) {
                    org.bukkit.Bukkit.dispatchCommand(p, cmd);
                } else {
                    org.bukkit.Bukkit.dispatchCommand(
                            org.bukkit.Bukkit.getConsoleSender(), cmd);
                }
            } catch (Throwable ex) {
                plugin.getLogger().warning("Commande du jutsu " + a.id()
                        + " en échec (« " + cmd + " ») : " + ex.getMessage());
            }
        }
    }

    private Ability boundAbility(Player p, int bindingIndex) {
        JutsuHotbarManager.Session s = hotbar.session(p);
        if (s == null) return null;
        String id = bindings.get(s.characterId(), s.type(), bindingIndex);
        if (id == null) return null;
        Ability a = registry.byId(id);
        return (a != null && a.isCastable()) ? a : null;
    }

    private boolean requireLearned() {
        return plugin.getConfig().getBoolean("jutsu.require-learned", false);
    }

    /* ---- mastery efficiency: practiced jutsu cost less and cool down faster */

    private double masteryFraction(ShinobiCharacter c, Ability a) {
        return Math.max(0, Math.min(100, c.abilityMastery(a.id()))) / 100.0;
    }

    /** Chakra cost after the mastery discount. */
    public double effectiveCost(ShinobiCharacter c, Ability a) {
        double max = plugin.getConfig().getDouble("jutsu.mastery-max-cost-reduction", 0.5);
        return a.jutsu().chakraCost() * (1.0 - max * masteryFraction(c, a));
    }

    /** Cooldown after the mastery discount. */
    public long effectiveCooldown(ShinobiCharacter c, Ability a) {
        double max = plugin.getConfig().getDouble("jutsu.mastery-max-cooldown-reduction", 0.3);
        return (long) (a.jutsu().cooldownMillis() * (1.0 - max * masteryFraction(c, a)));
    }

    private static void actionBar(Player p, String msg, NamedTextColor color) {
        p.sendActionBar(Component.text(msg, color));
    }
}
