package com.reborn.shinobisense;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.skill.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chakra sensing — the MVP of the Perception Web (see
 * {@code ShinobiSense_InfoWar_DESIGN.md}). An active "pulse" reports nearby
 * chakra <em>fuzzily</em> (presence, direction, rough distance, magnitude),
 * never a coordinate. Range and stealth scale with the Perception skill from
 * ShinobiCore. Sensing someone usually lets them feel a gaze — surveillance
 * carries the risk of being caught.
 *
 * <p>MVP simplifications, flagged for later: suppression is a free toggle (the
 * design's output/regen cost is not yet enforced); identity recognition reuses
 * the contact book as a stand-in for the chakra-signature familiarity log; and
 * transformed-jinchūriki magnitude (ShinobiTail's auraRange) isn't folded in.
 */
public final class SenseService {

    private static final String[] DIRS =
            {"Nord", "Nord-Est", "Est", "Sud-Est", "Sud", "Sud-Ouest", "Ouest", "Nord-Ouest"};

    private final ShinobiSense plugin;
    /** Players currently masking their chakra (transient — clears on relog). */
    private final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();

    public SenseService(ShinobiSense plugin) { this.plugin = plugin; }

    public boolean isSuppressed(UUID id) { return suppressed.contains(id); }

    /** Toggle suppression; returns the new state (true = now suppressed). */
    public boolean toggleSuppress(UUID id) {
        if (suppressed.add(id)) return true;
        suppressed.remove(id);
        return false;
    }

    public void clear(UUID id) { suppressed.remove(id); }

    /** Resolve the online player currently playing the named character (or an
     *  exact online username as a fallback); {@code null} if not present. */
    public Player resolveOnlinePlayerByCharacter(String name) {
        CharacterService characters = plugin.characters();
        return characters == null ? null : characters.findOnlinePlayerByCharacter(name);
    }

    /* ------------------------------------------------------------- pulse */

    public void pulse(Player sensor) {
        CharacterService characters = plugin.characters();
        if (characters == null) return;
        ShinobiCharacter sc = characters.getActive(sensor.getUniqueId());
        if (sc == null) {
            sensor.sendMessage(Component.text("Aucun personnage actif.", NamedTextColor.RED));
            return;
        }

        double cost = plugin.getConfig().getDouble("sense.chakra-cost", 5.0);
        if (cost > 0 && !sc.chakra().consume(cost)) {
            sensor.sendActionBar(Component.text(
                    "Chakra insuffisant pour te concentrer.", NamedTextColor.AQUA));
            return;
        }

        int per = plugin.skills().effectiveSkill(sc, Skill.PERCEPTION);
        double range = plugin.getConfig().getDouble("sense.base-range", 20.0)
                + per * plugin.getConfig().getDouble("sense.range-per-skill", 2.0);
        range = Math.min(range, plugin.getConfig().getDouble("sense.max-range", 120.0));
        // A dōjutsu sharpens everything: longer reach, sees through masking,
        // and reads true names.
        boolean dojutsu = plugin.dojutsu() != null
                && plugin.dojutsu().isActive(sensor.getUniqueId());
        if (dojutsu) range *= plugin.getConfig().getDouble("dojutsu.range-multiplier", 2.0);
        int quiet = plugin.getConfig().getInt("sense.quiet-probe-skill", 20);

        Location at = sensor.getLocation();
        List<Component> reads = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(sensor)) continue;
            if (!other.getWorld().equals(at.getWorld())) continue;
            double dist = other.getLocation().distance(at);
            if (dist > range) continue;
            // Masking hides you — unless the sensor's dōjutsu pierces it.
            if (isSuppressed(other.getUniqueId()) && !dojutsu) continue;
            ShinobiCharacter oc = characters.getActive(other.getUniqueId());
            if (oc == null) continue;                           // no character = no chakra to feel

            // A dōjutsu reads true names; otherwise only known contacts.
            String who = dojutsu ? oc.name() : identify(sc, oc);
            reads.add(Component.text("• " + who + " — " + direction(at, other.getLocation())
                    + ", " + distanceBand(dist) + " · chakra " + magnitude(oc),
                    NamedTextColor.AQUA));

            // The anti-spy rule: the target feels a clumsy probe. A master
            // sensor (Perception >= quiet threshold) reads them unnoticed.
            if (per < quiet) {
                other.sendActionBar(Component.text("Tu sens un regard sur toi…", NamedTextColor.GRAY));
            }
        }

        sensor.playSound(sensor.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.6f);
        sensor.sendMessage(Component.text("— Perception · portée " + (int) range + "m —",
                NamedTextColor.GOLD));
        if (reads.isEmpty()) {
            sensor.sendMessage(Component.text("Tu ne perçois aucune présence.", NamedTextColor.GRAY));
        } else {
            reads.forEach(sensor::sendMessage);
        }
    }

    /* ------------------------------------------------------------ reads */

    /** Known contact → their known name; otherwise a fuzzy unknown. High
     *  Perception is needed even to attempt naming an unfamiliar signature. */
    private String identify(ShinobiCharacter sensor, ShinobiCharacter target) {
        var known = sensor.contacts().get(target.id());
        if (known != null && known.display() != null && !known.display().isBlank()) {
            return known.display();
        }
        return "une présence inconnue";
    }

    private static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        if (bearing < 0) bearing += 360.0;
        int idx = (int) Math.round(bearing / 45.0) % 8;
        return DIRS[idx];
    }

    private static String distanceBand(double dist) {
        if (dist < 10) return "très proche";
        if (dist < 25) return "proche";
        if (dist < 50) return "à distance";
        return "au loin";
    }

    private static String magnitude(ShinobiCharacter c) {
        double max = c.chakra().max();
        if (c.chakra().inDeficit()) return "vacillant";
        if (max < 1000) return "faible";
        if (max < 5000) return "modéré";
        if (max < 30000) return "élevé";
        return "monstrueux";
    }
}
