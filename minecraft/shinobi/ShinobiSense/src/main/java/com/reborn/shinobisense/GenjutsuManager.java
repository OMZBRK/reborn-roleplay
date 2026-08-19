package com.reborn.shinobisense;

import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Genjutsu — the illusion half of the Perception Web. A caster traps a target
 * in a chakra illusion that floods their senses with false input (disorienting
 * potion effects + phantom whispers). The victim breaks free with <b>Kai</b>
 * (spending chakra) or waits out the duration. Server-side only — the nastier
 * client-rendered illusions (phantom players, false HUD) wait for the mod.
 */
public final class GenjutsuManager {

    private static final PotionEffectType[] CLEARED = {
            PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS
    };

    private static final String[] WHISPERS = {
            "Tu entends des murmures sans source…",
            "Une silhouette bouge à la lisière de ta vision.",
            "Le sol semble onduler sous tes pieds.",
            "Des chuchotements répètent ton nom.",
            "Les couleurs se brouillent un instant.",
            "Quelque chose t'observe — mais il n'y a personne."
    };

    private final ShinobiSense plugin;
    private BukkitTask task;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    private record Active(String casterName, long endMillis) {}

    public GenjutsuManager(ShinobiSense plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (task != null) task.cancel();
        active.clear();
    }

    public boolean isUnder(UUID id) { return active.containsKey(id); }

    /** Trap a target in a genjutsu for {@code seconds}. */
    public void cast(Player caster, Player target, int seconds) {
        active.put(target.getUniqueId(),
                new Active(caster.getName(), System.currentTimeMillis() + seconds * 1000L));
        target.showTitle(Title.title(
                Component.text("Genjutsu", NamedTextColor.DARK_PURPLE),
                Component.text("La réalité vacille…", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofSeconds(1))));
        target.playSound(target.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 0.5f);
        caster.sendActionBar(Component.text("Illusion lancée sur " + target.getName() + ".",
                NamedTextColor.DARK_PURPLE));
    }

    /** The victim spends chakra to shatter the illusion. Returns true if broken. */
    public boolean kai(Player victim) {
        if (!active.containsKey(victim.getUniqueId())) {
            victim.sendActionBar(Component.text("Aucune illusion à briser.", NamedTextColor.GRAY));
            return false;
        }
        ShinobiCharacter c = plugin.characters() != null
                ? plugin.characters().getActive(victim.getUniqueId()) : null;
        double cost = plugin.getConfig().getDouble("genjutsu.kai-chakra-cost", 15.0);
        if (c != null && cost > 0) c.chakra().overdraw(cost);  // Kai costs even on empty
        active.remove(victim.getUniqueId());
        for (PotionEffectType t : CLEARED) victim.removePotionEffect(t);
        victim.sendMessage(Component.text("『 Kai ! 』 Tu brises l'illusion.", NamedTextColor.AQUA));
        victim.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        return true;
    }

    private void tick() {
        if (active.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (UUID id : Map.copyOf(active).keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { active.remove(id); continue; }
            Active a = active.get(id);
            if (a == null) continue;
            if (now >= a.endMillis()) {
                active.remove(id);
                p.sendActionBar(Component.text("L'illusion se dissipe.", NamedTextColor.GRAY));
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 70, 0, true, false, false));
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            if (rng.nextInt(100) < 30) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false, false));
            }
            if (rng.nextInt(100) < 25) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, true, false, false));
            }
            if (rng.nextInt(100) < 45) {
                p.sendMessage(Component.text(WHISPERS[rng.nextInt(WHISPERS.length)],
                        NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
            }
        }
    }
}
