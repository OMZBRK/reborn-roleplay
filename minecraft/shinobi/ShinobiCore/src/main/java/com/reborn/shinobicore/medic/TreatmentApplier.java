package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.dummy.Dummy;
import com.reborn.shinobicore.ko.injury.Injury;
import com.reborn.shinobicore.ko.injury.Severity;
import com.reborn.shinobicore.medic.gui.SoignerScreen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates a {@code /soigner} treatment and runs the apply-medicine
 * minigame.
 *
 * <h2>Recipe check</h2>
 * The medic's drop-strip in {@link com.reborn.shinobicore.medic.gui.TreatmentScreen}
 * must contain at least one of every {@link Medicine} listed by
 * {@link InjuryRecipe#forType}. Extras are tolerated and refunded.
 *
 * <h2>Minigame</h2>
 * Stand still for {@link #STILL_DURATION_MILLIS} milliseconds while
 * the actionbar shows a French progress message. Moving more than
 * {@link #MOVE_TOLERANCE} blocks aborts the application with
 * "Concentration brisée" and refunds every dropped medicine. On
 * completion, the {@link Injury#severity()} steps down one tier
 * (or the injury is removed if it was already FAIBLE), the medicines
 * are consumed, and a green confirmation lands.
 */
public final class TreatmentApplier {

    /** How long the medic must stand still (ms). */
    public static final long   STILL_DURATION_MILLIS = 3_000L;
    /** Movement budget over the whole still-window (blocks). */
    public static final double MOVE_TOLERANCE        = 0.4;

    private final ShinobiCore plugin;

    /** Player UUIDs currently inside the apply-still-window. We block
     *  re-entry so a medic can't double-validate by spamming. */
    private final Set<UUID> active = new HashSet<>();

    /** Per-medic scheduled tasks so we can cancel cleanly on early
     *  exit. */
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public TreatmentApplier(ShinobiCore plugin) { this.plugin = plugin; }

    /* ----------------------------------------------------------- entry */

    /** Validate the recipe + run the minigame. Returns immediately;
     *  outcome lands via title/actionbar after the still-window. */
    public void run(Player medic, SoignerScreen.Target target, UUID targetId,
                    UUID injuryId, ItemStack[] dropStrip) {
        if (active.contains(medic.getUniqueId())) {
            medic.sendMessage(Component.text(
                    "Tu es déjà en train d'appliquer un soin.",
                    NamedTextColor.RED));
            return;
        }

        Injury injury = locateInjury(target, targetId, injuryId);
        if (injury == null) {
            medic.sendMessage(Component.text(
                    "La blessure a disparu.", NamedTextColor.RED));
            return;
        }

        // Cooldown gate. After a successful heal, the same injury is
        // off-limits to /soigner for a duration tied to the severity
        // that was just treated (URGENT 3h, IMPORTANT 1h30, MOYEN
        // 30min, FAIBLE — no cooldown). Refund whatever the medic
        // dropped so they don't lose items to a misclick.
        if (!injury.isHealable()) {
            long remaining = injury.cooldownRemainingMillis();
            medic.sendMessage(Component.text(
                    "Cette blessure est encore en convalescence. "
                            + "Reviens dans " + formatRemaining(remaining) + ".",
                    NamedTextColor.RED));
            refund(medic, dropStrip);
            return;
        }

        List<Medicine> recipe = InjuryRecipe.forType(injury.type());
        if (recipe.isEmpty()) {
            medic.sendMessage(Component.text(
                    "Aucune recette pour ce type de blessure.",
                    NamedTextColor.RED));
            return;
        }

        // Recipe check: every required medicine present at least once.
        // Extras allowed (we'll refund anything unused).
        Map<Medicine, Integer> dropped = new HashMap<>();
        for (ItemStack s : dropStrip) {
            if (s == null) continue;
            Medicine kind = MedicineItem.typeOf(plugin, s);
            if (kind == null) continue;
            dropped.merge(kind, s.getAmount(), Integer::sum);
        }
        for (Medicine req : recipe) {
            if (dropped.getOrDefault(req, 0) <= 0) {
                medic.sendMessage(Component.text(
                        "Il manque : " + req.displayName(),
                        NamedTextColor.RED));
                refund(medic, dropStrip);
                return;
            }
        }

        startMinigame(medic, target, targetId, injuryId, recipe, dropStrip);
    }

    /* --------------------------------------------------------- minigame */

    private void startMinigame(Player medic, SoignerScreen.Target target,
                               UUID targetId, UUID injuryId,
                               List<Medicine> recipe, ItemStack[] dropStrip) {
        active.add(medic.getUniqueId());
        Location startLoc = medic.getLocation().clone();
        long startMillis = System.currentTimeMillis();

        medic.showTitle(Title.title(
                Component.text("Soin", NamedTextColor.AQUA),
                Component.text("Reste immobile et concentré.",
                        NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200),
                        Duration.ofMillis(800),
                        Duration.ofMillis(300))));
        medic.playSound(medic.getLocation(),
                Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.4f);
        medic.closeInventory();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long elapsed = System.currentTimeMillis() - startMillis;
            if (!medic.isOnline()) {
                fail(medic, dropStrip, /*refundOnline=*/false);
                return;
            }
            if (medic.getLocation().distance(startLoc) > MOVE_TOLERANCE) {
                medic.sendActionBar(Component.text(
                        "Concentration brisée.", NamedTextColor.RED));
                medic.playSound(medic.getLocation(),
                        Sound.BLOCK_GLASS_BREAK, 0.6f, 1.4f);
                fail(medic, dropStrip, /*refundOnline=*/true);
                return;
            }
            if (elapsed >= STILL_DURATION_MILLIS) {
                succeed(medic, target, targetId, injuryId);
                return;
            }
            int filled = (int) (elapsed * 20 / STILL_DURATION_MILLIS);
            String bar = "▰".repeat(filled) + "▱".repeat(20 - filled);
            medic.sendActionBar(Component.text(
                    "Application : " + bar, NamedTextColor.AQUA));
        }, 0L, 2L);
        tasks.put(medic.getUniqueId(), task);
    }

    private void succeed(Player medic, SoignerScreen.Target target,
                         UUID targetId, UUID injuryId) {
        cleanup(medic.getUniqueId());

        Injury injury = locateInjury(target, targetId, injuryId);
        if (injury == null) {
            medic.sendMessage(Component.text(
                    "La blessure a disparu en cours de soin.",
                    NamedTextColor.RED));
            return;
        }
        Severity oldSeverity = injury.severity();
        Severity next = oldSeverity.soignerDowngrade();
        com.reborn.shinobicore.ko.injury.BodyPart healedPart = injury.bodyPart();
        long now = System.currentTimeMillis();

        if (next == null) {
            // FAIBLE → cleared.
            removeInjury(target, targetId, injuryId);
            medic.sendMessage(Component.text(
                    "Soin appliqué — blessure guérie.", NamedTextColor.GREEN));
        } else {
            injury.setSeverity(next);
            injury.setLastTick(now);
            // Lock further /soigner on this injury until the cooldown
            // for the severity we just treated has elapsed. URGENT and
            // IMPORTANT both downgrade to MOYEN but carry different
            // cooldowns — the lock is keyed off the OLD severity, not
            // the new one.
            long cd = oldSeverity.healCooldownMillis();
            injury.setNextHealableMillis(cd > 0 ? now + cd : 0L);
            persistTarget(target, targetId);
            String suffix = cd > 0
                    ? "  (Repos requis : " + humanCooldown(cd) + ")"
                    : "";
            medic.sendMessage(Component.text(
                    "Soin appliqué — blessure réduite à " + next.label() + "."
                            + suffix,
                    NamedTextColor.GREEN));
        }
        medic.playSound(medic.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);

        // Auto /me so onlookers can read what the medic just did —
        // lists every medicine of the recipe + the body part healed
        // + the patient's name. Recipe re-derived from the injury
        // type since the dropStrip is no longer in scope here.
        broadcastHealAction(medic, target, targetId, injury.type(), healedPart);

        // Reveal any hidden injuries on the same body part — a
        // bruise treated may uncover a fracture that was masked
        // beneath it. The medic gets a chat note per revealed
        // wound so they know what just appeared on the silhouette.
        revealHiddenOn(medic, target, targetId, healedPart);

        // Items are NOT refunded on success — they were consumed.
    }

    /** Emit a French /me line listing the medicines applied + the
     *  body part + the patient's name. */
    private void broadcastHealAction(Player medic, SoignerScreen.Target target,
                                     UUID targetId,
                                     com.reborn.shinobicore.ko.injury.InjuryType type,
                                     com.reborn.shinobicore.ko.injury.BodyPart bodyPart) {
        java.util.List<Medicine> recipe = InjuryRecipe.forType(type);
        if (recipe.isEmpty()) return;
        StringBuilder meds = new StringBuilder();
        for (int i = 0; i < recipe.size(); i++) {
            if (i > 0) meds.append(i == recipe.size() - 1 ? " et " : ", ");
            meds.append(recipe.get(i).displayName());
        }
        String patient;
        if (target == SoignerScreen.Target.PLAYER) {
            ShinobiCharacter c = plugin.characters().getActive(targetId);
            Player p = plugin.getServer().getPlayer(targetId);
            patient = c != null ? c.name() : (p != null ? p.getName() : "la cible");
        } else {
            com.reborn.shinobicore.dummy.Dummy d = findDummy(targetId);
            patient = d != null ? d.name() : "la cible";
        }
        com.reborn.shinobicore.character.AutoMe.broadcast(plugin, medic,
                "applique " + meds + " sur " + bodyPart.label().toLowerCase()
                        + " de " + patient + ".");
    }

    /** Walk the target's injury list, flip every hidden wound on
     *  {@code bodyPart} to visible, and notify the medic. */
    private void revealHiddenOn(Player medic, SoignerScreen.Target target,
                                UUID targetId,
                                com.reborn.shinobicore.ko.injury.BodyPart bodyPart) {
        java.util.List<Injury> list;
        if (target == SoignerScreen.Target.PLAYER) {
            ShinobiCharacter c = plugin.characters().getActive(targetId);
            if (c == null) return;
            list = c.injuries();
        } else {
            com.reborn.shinobicore.dummy.Dummy d = findDummy(targetId);
            if (d == null) return;
            list = d.injuries();
        }
        java.util.List<Injury> revealed =
                com.reborn.shinobicore.ko.injury.InjuryMerger.revealHiddenOn(
                        list, bodyPart);
        if (revealed.isEmpty()) return;
        for (Injury inj : revealed) {
            medic.sendMessage(Component.text(
                    "Tu découvres une blessure cachée : "
                            + inj.severity().label() + " "
                            + inj.type().label().toLowerCase()
                            + " sur " + bodyPart.label() + ".",
                    NamedTextColor.LIGHT_PURPLE));
        }
        persistTarget(target, targetId);
    }

    /** Format a cooldown duration as a French "Xh", "Xh30", or "Xmin". */
    private static String humanCooldown(long millis) {
        long mins = Math.max(1, millis / 60_000L);
        if (mins >= 60) {
            long h  = mins / 60;
            long mm = mins % 60;
            return mm == 0 ? h + "h" : h + "h" + String.format("%02d", mm);
        }
        return mins + " min";
    }

    /** Format a remaining-cooldown duration as "Xh", "Xh30 min" or
     *  "Xmin Ys" depending on order of magnitude — keeps the chat
     *  message short for short waits and accurate for long ones. */
    private static String formatRemaining(long millis) {
        long secs = millis / 1000L;
        if (secs >= 3600) {
            long h  = secs / 3600;
            long mm = (secs % 3600) / 60;
            return mm == 0 ? h + " h" : h + " h " + mm + " min";
        }
        if (secs >= 60) {
            long mm = secs / 60;
            long ss = secs % 60;
            return ss == 0 ? mm + " min" : mm + " min " + ss + " s";
        }
        return secs + " s";
    }

    private void fail(Player medic, ItemStack[] dropStrip, boolean refundOnline) {
        cleanup(medic.getUniqueId());
        if (refundOnline && medic.isOnline()) {
            refund(medic, dropStrip);
        }
    }

    private void cleanup(UUID medicId) {
        active.remove(medicId);
        BukkitTask t = tasks.remove(medicId);
        if (t != null) t.cancel();
    }

    /* --------------------------------------------------- target lookup */

    private Injury locateInjury(SoignerScreen.Target target, UUID targetId,
                                UUID injuryId) {
        if (target == SoignerScreen.Target.PLAYER) {
            ShinobiCharacter c = plugin.characters().getActive(targetId);
            if (c == null) return null;
            for (Injury i : c.injuries()) if (i.id().equals(injuryId)) return i;
            return null;
        }
        Dummy d = findDummy(targetId);
        if (d == null) return null;
        for (Injury i : d.injuries()) if (i.id().equals(injuryId)) return i;
        return null;
    }

    private void removeInjury(SoignerScreen.Target target, UUID targetId,
                              UUID injuryId) {
        if (target == SoignerScreen.Target.PLAYER) {
            ShinobiCharacter c = plugin.characters().getActive(targetId);
            if (c == null) return;
            c.removeInjury(injuryId);
            plugin.characterRepository().save(c);
        } else {
            Dummy d = findDummy(targetId);
            if (d == null) return;
            d.injuries().removeIf(i -> i.id().equals(injuryId));
            plugin.dummies().save();
        }
    }

    private void persistTarget(SoignerScreen.Target target, UUID targetId) {
        if (target == SoignerScreen.Target.PLAYER) {
            ShinobiCharacter c = plugin.characters().getActive(targetId);
            if (c != null) plugin.characterRepository().save(c);
        } else {
            plugin.dummies().save();
        }
    }

    private Dummy findDummy(UUID dummyId) {
        for (Dummy d : plugin.dummies().all()) {
            if (d.id().equals(dummyId)) return d;
        }
        return null;
    }

    /* ------------------------------------------------------------- refund */

    private static void refund(Player medic, ItemStack[] strip) {
        for (ItemStack s : strip) {
            if (s == null) continue;
            var leftover = medic.getInventory().addItem(s);
            // If inventory was full, drop on the ground at the medic's
            // feet so nothing vanishes.
            for (ItemStack stuck : leftover.values()) {
                medic.getWorld().dropItemNaturally(medic.getLocation(), stuck);
            }
        }
    }
}
