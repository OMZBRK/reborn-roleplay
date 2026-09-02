package com.reborn.shinobicore.ko.action;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Proximity-based staff approval flow for the "Tuer" action.
 *
 * <p>When a player clicks {@code Tuer} on a KO body:
 * <ol>
 *   <li>{@link #request} is called with the requester + KO target.</li>
 *   <li>We scan for online players with the {@code shinobicore.staff}
 *       permission within {@link #PROXIMITY_RADIUS} blocks of the body.</li>
 *   <li>If <b>none</b> are nearby, the requester gets a French
 *       "no staff in range" message and the request is dropped.</li>
 *   <li>If staff are nearby, each receives a clickable
 *       {@code [Valider] [Refuser]} message. The first staff member to
 *       click decides; subsequent clicks are ignored.</li>
 *   <li>On {@code [Valider]}, the staff member is prompted (chat)
 *       for a reason. The reason is logged to {@code kill-log.txt}
 *       with timestamp / requester / target / approver / location.
 *       The target character's {@code dead} flag is then set and the
 *       repository saved; the player KO state is cleared and they're
 *       teleported to spawn.</li>
 *   <li>On {@code [Refuser]}, no character is killed but the refusal
 *       is still logged for staff audit.</li>
 *   <li>Requests time out after {@link #TIMEOUT_MILLIS} milliseconds.
 *       After timeout the requester is told no staff acted in time.</li>
 * </ol>
 */
public final class KillRequestManager {

    public static final int  PROXIMITY_RADIUS = 100;
    public static final long TIMEOUT_MILLIS   = 60_000L;

    private final ShinobiCore plugin;
    private final File logFile;

    /** Counter so request ids stay short + readable in chat. */
    private final AtomicLong sequence = new AtomicLong(1);

    /** Live requests keyed by their id. Pending requests time out
     *  via a scheduled task — see {@link #request}. */
    private final Map<Long, KillRequest> pending = new HashMap<>();

    public KillRequestManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "kill-log.txt");
    }

    /* --------------------------------------------------------------- request */

    public void request(Player requester, Player target) {
        if (requester == null || target == null) return;
        if (!plugin.ko().isKo(target.getUniqueId())) {
            requester.sendMessage(Component.text(
                    "La cible n'est plus KO.", NamedTextColor.RED));
            return;
        }

        // Find nearby staff.
        Location loc = target.getLocation();
        int nearbyCount = 0;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("shinobicore.staff")) continue;
            if (!staff.getWorld().equals(loc.getWorld())) continue;
            if (staff.getLocation().distanceSquared(loc)
                    > (double) PROXIMITY_RADIUS * PROXIMITY_RADIUS) continue;
            if (staff.getUniqueId().equals(requester.getUniqueId())) continue; // can't self-approve
            nearbyCount++;
        }
        if (nearbyCount == 0) {
            requester.sendMessage(Component.text(
                    "Aucun staff dans un rayon de " + PROXIMITY_RADIUS
                            + " blocs. Demande dans le tchat staff.",
                    NamedTextColor.RED));
            return;
        }

        long id = sequence.getAndIncrement();
        ShinobiCharacter requesterChar =
                plugin.characters().getActive(requester.getUniqueId());
        ShinobiCharacter targetChar =
                plugin.characters().getActive(target.getUniqueId());
        KillRequest req = new KillRequest(id,
                requester.getUniqueId(),
                requesterChar != null ? requesterChar.name() : requester.getName(),
                target.getUniqueId(),
                targetChar != null ? targetChar.id() : null,
                targetChar != null ? targetChar.name() : target.getName(),
                target.getLocation().clone(),
                System.currentTimeMillis());
        pending.put(id, req);

        // Notify nearby staff.
        Component msg = buildStaffMessage(req);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("shinobicore.staff")) continue;
            if (!staff.getWorld().equals(loc.getWorld())) continue;
            if (staff.getLocation().distanceSquared(loc)
                    > (double) PROXIMITY_RADIUS * PROXIMITY_RADIUS) continue;
            if (staff.getUniqueId().equals(requester.getUniqueId())) continue;
            staff.sendMessage(msg);
        }

        // Confirm to the requester.
        requester.sendMessage(Component.text(
                "Demande envoyée à " + nearbyCount + " staff dans la zone.",
                NamedTextColor.GRAY));

        // Timeout.
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            KillRequest stale = pending.remove(id);
            if (stale == null) return;
            Player rq = Bukkit.getPlayer(stale.requesterId());
            if (rq != null) {
                rq.sendMessage(Component.text(
                        "Aucun staff n'a répondu à temps. Demande annulée.",
                        NamedTextColor.RED));
            }
        }, TIMEOUT_MILLIS / 50);
        req.setTimeoutTask(timeoutTask);
    }

    /* --------------------------------------------------------------- decide */

    /** Called by the chat handler when a staff member clicks
     *  {@code [Valider]}. Prompts for a reason, then finalises. */
    public void approve(Player staff, long requestId) {
        if (staff == null || !staff.hasPermission("shinobicore.staff")) return;
        KillRequest req = pending.remove(requestId);
        if (req == null) {
            staff.sendMessage(Component.text(
                    "Cette demande n'est plus en attente.", NamedTextColor.RED));
            return;
        }
        if (req.timeoutTask() != null) req.timeoutTask().cancel();

        // Ask for reason in chat. ChatInputManager owns the prompt
        // lifecycle (60s timeout, "cancel" word to abort).
        plugin.chatInputs().prompt(staff,
                "Donne la raison validant la mort (ou 'cancel') :",
                reason -> {
                    String trimmed = reason == null ? "" : reason.trim();
                    if (trimmed.isEmpty() || "cancel".equalsIgnoreCase(trimmed)) {
                        staff.sendMessage(Component.text(
                                "Annulation : raison vide.",
                                NamedTextColor.GRAY));
                        return;
                    }
                    finalizeKill(staff, req, trimmed);
                },
                () -> staff.sendMessage(Component.text(
                        "Approbation annulée (timeout).", NamedTextColor.GRAY)));
    }

    /** Called when a staff member clicks {@code [Refuser]}. */
    public void refuse(Player staff, long requestId) {
        if (staff == null || !staff.hasPermission("shinobicore.staff")) return;
        KillRequest req = pending.remove(requestId);
        if (req == null) {
            staff.sendMessage(Component.text(
                    "Cette demande n'est plus en attente.", NamedTextColor.RED));
            return;
        }
        if (req.timeoutTask() != null) req.timeoutTask().cancel();
        log("REFUSE", req, staff.getName(), "");

        Player rq = Bukkit.getPlayer(req.requesterId());
        if (rq != null) {
            rq.sendMessage(Component.text(
                    "Ta demande de tuer " + req.targetName()
                            + " a été refusée.",
                    NamedTextColor.RED));
        }
        staff.sendMessage(Component.text(
                "Demande refusée et journalisée.", NamedTextColor.GRAY));
    }

    private void finalizeKill(Player staff, KillRequest req, String reason) {
        Player target = Bukkit.getPlayer(req.targetPlayerId());
        if (target == null) {
            staff.sendMessage(Component.text(
                    "La cible n'est plus en ligne.", NamedTextColor.RED));
            log("APPROVE_LOST", req, staff.getName(), reason);
            return;
        }
        // Mark the character dead. Don't delete — staff may need to
        // revive later via /character edit.
        if (req.targetCharacterId() != null) {
            plugin.characters().findById(target.getUniqueId(),
                            req.targetCharacterId())
                    .ifPresent(c -> {
                        c.setDead(true);
                        plugin.characterRepository().save(c);
                    });
        }
        // Clear KO state and teleport the player to world spawn —
        // they'll need to pick a different character.
        plugin.ko().forceClear(target.getUniqueId());
        target.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        target.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        target.setHealth(target.getMaxHealth());
        target.teleport(target.getWorld().getSpawnLocation());
        target.sendMessage(Component.text(
                "Ton personnage est mort. Sélectionne un autre personnage avec /character.",
                NamedTextColor.DARK_RED));

        Player rq = Bukkit.getPlayer(req.requesterId());
        if (rq != null) {
            rq.sendMessage(Component.text(
                    "Ta demande a été validée par " + staff.getName() + ".",
                    NamedTextColor.GRAY));
        }
        staff.sendMessage(Component.text(
                "Mort validée et journalisée.", NamedTextColor.GRAY));
        log("APPROVE", req, staff.getName(), reason);
    }

    /* --------------------------------------------------------------- logging */

    private void log(String verdict, KillRequest req, String staffName, String reason) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String ts = fmt.format(new Date());
        Location l = req.scene();
        String line = String.format(
                "[%s] %s id=%d requester=%s(%s) target=%s(%s) staff=%s loc=%s/%.1f,%.1f,%.1f reason=%s%n",
                ts, verdict, req.id(),
                req.requesterName(), req.requesterId(),
                req.targetName(), req.targetPlayerId(),
                staffName,
                l.getWorld() != null ? l.getWorld().getName() : "?",
                l.getX(), l.getY(), l.getZ(),
                reason);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(logFile, true))) {
            w.write(line);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to write kill-log.txt", ex);
        }
    }

    /* --------------------------------------------------------------- chat UI */

    private Component buildStaffMessage(KillRequest req) {
        Component approve = Component.text("[Valider]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sckillvote approve " + req.id()))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Demande la raison puis valide la mort.",
                        NamedTextColor.GRAY)));
        Component refuse = Component.text("[Refuser]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sckillvote refuse " + req.id()))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Refuse la mort, journalise.", NamedTextColor.GRAY)));

        return Component.text("[Staff] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(req.requesterName(), NamedTextColor.YELLOW))
                .append(Component.text(" demande à tuer ", NamedTextColor.GRAY))
                .append(Component.text(req.targetName(), NamedTextColor.YELLOW))
                .append(Component.text(".  ", NamedTextColor.GRAY))
                .append(approve)
                .append(Component.text("  "))
                .append(refuse);
    }
}
