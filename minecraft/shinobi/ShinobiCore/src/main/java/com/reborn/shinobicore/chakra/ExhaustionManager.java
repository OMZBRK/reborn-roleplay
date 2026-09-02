package com.reborn.shinobicore.chakra;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chakra exhaustion — the back half of the "no hard gates" economy. A character
 * may {@link ChakraPool#overdraw overdraw} their pool (cast something they
 * can't afford), pushing it into debt. While in debt the host suffers an
 * escalating debuff ladder and, if they don't claw the pool back above zero,
 * collapses (KO) after a deficit-scaled fuse. Deeper overdraw → harsher
 * debuffs and a shorter fuse.
 *
 * <p>The whole point: a Genin CAN unleash an A-rank — and pays for it.
 */
public final class ExhaustionManager {

    private final ShinobiCore plugin;
    private BukkitTask task;

    /** Per-player seconds spent continuously in chakra deficit. */
    private final Map<UUID, Integer> deficitSeconds = new HashMap<>();

    public ExhaustionManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        deficitSeconds.clear();
    }

    /** True while this player's active character is in chakra deficit. */
    public boolean isExhausted(UUID playerId) {
        return deficitSeconds.containsKey(playerId);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("chakra.overdraw.enabled", true)) return;
        if (Tps.shouldDefer()) return;
        if (plugin.characters() == null) return;

        int baseKo = plugin.getConfig().getInt("chakra.overdraw.ko-seconds", 15);
        int minKo  = plugin.getConfig().getInt("chakra.overdraw.min-ko-seconds", 6);

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            ShinobiCharacter c = plugin.characters().getActive(id);
            if (c == null) { deficitSeconds.remove(id); continue; }

            ChakraPool pool = c.chakra();
            if (pool == null || pool == ChakraPool.EMPTY || !pool.inDeficit()) {
                if (deficitSeconds.remove(id) != null) {
                    p.sendActionBar(Component.text("Ton chakra se stabilise.", NamedTextColor.AQUA));
                }
                continue;
            }
            if (plugin.ko() != null && plugin.ko().isKo(id)) {
                deficitSeconds.remove(id);
                continue; // already down — let the KO system run
            }

            // Severity 0..1 from how deep the debt runs relative to the pool.
            double ratio = pool.max() > 0 ? Math.min(1.0, pool.debt() / pool.max()) : 1.0;
            int secs = deficitSeconds.getOrDefault(id, 0) + 1;
            deficitSeconds.put(id, secs);

            applyLadder(p, ratio);

            // Deeper debt → shorter fuse.
            int koAt = (int) Math.round(baseKo - (baseKo - minKo) * ratio);
            if (koAt < minKo) koAt = minKo;

            if (secs >= koAt) {
                // The collapse pays the debt — they wake spent, not in a re-KO loop.
                double debt = pool.debt(); // Rasengan - capture before zeroing
                c.chakra().setCurrent(0.0);
                deficitSeconds.remove(id);
                // Rasengan - signal chakra depletion before the KO fires.
                org.bukkit.Bukkit.getPluginManager().callEvent(
                        new com.reborn.shinobicore.event.ChakraDepletedEvent(p, c.id(), debt));
                if (plugin.ko() != null) {
                    plugin.ko().enterKo(p, c.id(), KoState.Cause.CHAKRA);
                }
                p.showTitle(Title.title(
                        Component.text("Épuisement", NamedTextColor.DARK_RED),
                        Component.text("Ton chakra s'effondre…", NamedTextColor.GRAY)));
            } else {
                int left = koAt - secs;
                p.sendActionBar(Component.text(
                        "⚠ Surcharge du chakra — effondrement dans " + left + "s",
                        NamedTextColor.RED));
                if (secs == 1) {
                    p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 0.6f);
                }
            }
        }
    }

    /**
     * Stacks debuffs that worsen with the debt ratio. Effects are re-applied
     * each second with a short duration so they fade on recovery without us
     * tracking them.
     */
    private void applyLadder(Player p, double ratio) {
        int dur = 40; // 2 s, refreshed every 1 s tick
        int amp = ratio >= 0.66 ? 2 : ratio >= 0.33 ? 1 : 0;
        addEffect(p, PotionEffectType.SLOWNESS, dur, amp);
        addEffect(p, PotionEffectType.WEAKNESS, dur, amp);
        addEffect(p, PotionEffectType.MINING_FATIGUE, dur, Math.min(2, amp + 1));
        if (ratio >= 0.66) addEffect(p, PotionEffectType.NAUSEA, dur, 0);
        if (ratio >= 0.85) addEffect(p, PotionEffectType.BLINDNESS, dur, 0);
    }

    private void addEffect(Player p, PotionEffectType type, int durationTicks, int amplifier) {
        if (type == null) return;
        p.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false, true));
    }

    /* ------------------------------------------------------- /sc overdraw */

    /**
     * Staff / GM tool: force a character to overdraw by {@code montant} chakra,
     * pushing the pool into deficit so the exhaustion ladder kicks in. Also the
     * way to demo the economy before jutsu casting is wired to call
     * {@link ChakraPool#overdraw}.
     */
    public static boolean handleOverdraw(ShinobiCore plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("shinobicore.staff")) {
            sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage : /sc overdraw <personnage> <montant>", NamedTextColor.GRAY));
            return true;
        }
        ShinobiCharacter c = findCharacter(plugin, args[1]);
        if (c == null) {
            sender.sendMessage(Component.text("Aucun personnage trouvé pour : " + args[1],
                    NamedTextColor.RED));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2].trim());
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Montant invalide : " + args[2], NamedTextColor.RED));
            return true;
        }
        double incurred = c.chakra().overdraw(amount);
        plugin.characters().save(c);
        sender.sendMessage(Component.text(c.name() + " — chakra "
                + (int) c.chakra().current() + "/" + (int) c.chakra().max()
                + (c.chakra().inDeficit()
                    ? " · dette " + (int) c.chakra().debt() + " (épuisement en cours)"
                    : " (aucune dette)"),
                incurred > 0 ? NamedTextColor.RED : NamedTextColor.GREEN));
        return true;
    }

    private static ShinobiCharacter findCharacter(ShinobiCore plugin, String name) {
        return plugin.characters().resolveByName(name);
    }
}
