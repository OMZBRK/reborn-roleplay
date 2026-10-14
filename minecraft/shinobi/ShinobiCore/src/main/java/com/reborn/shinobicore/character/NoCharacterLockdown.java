package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the "new player without any character" state: renders the whole
 * player inert (blind, can't move, can't jump, can't break blocks) and shows
 * a red, bold title in the middle of the screen asking them to contact a
 * staff member.
 *
 * <p>The lockdown auto-lifts as soon as {@link #release(Player)} is called,
 * which {@link CharacterManager#applyStats} does when a character is chosen.
 */
public class NoCharacterLockdown {

    /** Amplifier 128+ underflows to negative on the client, preventing jumps. */
    private static final int JUMP_LOCK_AMPLIFIER = 128;
    /** Max-strength slowness (paralyses the player). */
    private static final int SLOWNESS_AMPLIFIER = 6; // Slowness VII
    /** Very long duration so effects don't blink between refreshes. */
    private static final int EFFECT_DURATION_TICKS = 20 * 60 * 60;

    private final ShinobiCore plugin;

    /** Players currently under lockdown. */
    private final Set<UUID> locked = new HashSet<>();

    private BukkitTask refresher;

    public NoCharacterLockdown(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long refresh = Math.max(10L, plugin.getConfig()
                .getLong("no-character-lockdown.refresh-ticks", 40L));
        refresher = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, refresh, refresh);
    }

    public void stop() {
        if (refresher != null) { refresher.cancel(); refresher = null; }
    }

    public void reloadConfig() { start(); }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("no-character-lockdown.enabled", true);
    }

    public boolean isLocked(UUID id) { return locked.contains(id); }

    /** Put the player under lockdown. Safe to call repeatedly. */
    public void apply(Player p) {
        if (p == null || !p.isOnline()) return;
        if (!isEnabled()) return;
        locked.add(p.getUniqueId());
        applyEffects(p);
        showTitle(p);
    }

    /** Remove lockdown effects and stop tracking the player. */
    public void release(Player p) {
        if (p == null) return;
        // Appelé à chaque activation de personnage : on ne retire que NOS effets (verrou réellement posé, et
        // signature du verrou : même niveau, durée longue finie). Avant, le retrait par type levait l'écran noir du
        // staff ou un étourdi (ETATS_JOUEUR §2.4 n° 6).
        if (!locked.remove(p.getUniqueId())) return;
        if (!p.isOnline()) return;
        retirerVerrou(p, PotionEffectType.BLINDNESS, 0);
        retirerVerrou(p, PotionEffectType.SLOWNESS, SLOWNESS_AMPLIFIER);
        retirerVerrou(p, PotionEffectType.JUMP_BOOST, JUMP_LOCK_AMPLIFIER);
        retirerVerrou(p, PotionEffectType.MINING_FATIGUE, 9);
        // Clear any lingering title by overwriting with an empty one that
        // fades quickly.
        p.showTitle(Title.title(
                Component.empty(),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1), Duration.ofMillis(200))
        ));
    }

    /** Retire l'effet s'il porte la signature du verrou (niveau identique, durée finie de plus d'une minute). */
    private static void retirerVerrou(Player p, PotionEffectType type, int amplifier) {
        org.bukkit.potion.PotionEffect pe = p.getPotionEffect(type);
        if (pe != null && !pe.isInfinite() && pe.getAmplifier() == amplifier && pe.getDuration() > 20 * 60) {
            p.removePotionEffect(type);
        }
    }

    private void tick() {
        for (UUID id : locked.toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                locked.remove(id);
                continue;
            }
            applyEffects(p);
            showTitle(p);
        }
    }

    private void applyEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                EFFECT_DURATION_TICKS, 0, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                EFFECT_DURATION_TICKS, SLOWNESS_AMPLIFIER, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                EFFECT_DURATION_TICKS, JUMP_LOCK_AMPLIFIER, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                EFFECT_DURATION_TICKS, 9, true, false, false));
    }

    private void showTitle(Player p) {
        String msg = plugin.getConfig().getString(
                "no-character-lockdown.title",
                "Vous devez contacter un Staff pour creer votre personnage!");
        Component main = Component.text(msg, NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true);
        p.showTitle(Title.title(
                main,
                Component.empty(),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofSeconds(10),
                        Duration.ofMillis(500))
        ));
    }
}
