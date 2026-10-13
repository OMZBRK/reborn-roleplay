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
 * Mort RP (KO-7, « RPK ») : le staff tranche, la victime n'a rien à valider.
 *
 * <ol>
 *   <li>« Demander une RPK » sur un corps à terre ou KO ({@link #request}) : <b>tout le staff en ligne</b> reçoit un
 *       message au tchat (qui, qui, où ; boutons {@code /sckillvote approve|refuse|goto <id>}) et l'alerte « Mort
 *       RP » arrive au Poste de garde (Valider / Refuser / Y aller). Sans staff en ligne, elle y attend tant que la
 *       victime est KO.</li>
 *   <li>Validation : la RPK est <b>accordée</b> au demandeur, le personnage ne meurt pas encore. Le demandeur joue
 *       sa scène, puis l'achève ou l'exécute ({@link CorpsService}) : c'est cette dernière action qui tue
 *       ({@link #kill}) — personnage marqué mort, {@link com.reborn.shinobicore.event.CharacterDeathEvent} (les
 *       addons libèrent ce qu'il détenait), journaux, puis la scène de mort ({@link DeathScene}).</li>
 *   <li>La demande tombe si la victime n'est plus KO (réveil, hôpital) ou se déconnecte.</li>
 * </ol>
 */
public final class KillRequestManager {

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
            requester.sendMessage(Component.text("La cible n'est plus KO.", NamedTextColor.RED));
            return;
        }
        for (KillRequest r : pending.values()) {
            if (r.targetPlayerId().equals(target.getUniqueId())) {
                requester.sendMessage(Component.text("Une demande de mort RP est déjà en cours pour ce personnage.",
                        NamedTextColor.GRAY));
                return;
            }
        }
        long id = sequence.getAndIncrement();
        ShinobiCharacter requesterChar = plugin.characters().getActive(requester.getUniqueId());
        ShinobiCharacter targetChar = plugin.characters().getActive(target.getUniqueId());
        KillRequest req = new KillRequest(id,
                requester.getUniqueId(),
                requesterChar != null ? requesterChar.name() : requester.getName(),
                target.getUniqueId(),
                targetChar != null ? targetChar.id() : null,
                targetChar != null ? targetChar.name() : target.getName(),
                target.getLocation().clone(),
                System.currentTimeMillis());
        pending.put(id, req);
        log("DEMANDE", req, "-", "");

        // 1) le staff est prévenu au tchat (où qu'il soit) + alerte « Mort RP » au Poste de garde
        Component msg = buildStaffMessage(req);
        int online = 0;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("shinobicore.staff")) continue;
            staff.sendMessage(msg);
            staff.playSound(staff.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 0.6f);
            online++;
        }
        if (plugin.staffPanel() != null) plugin.staffPanel().onKillRequest(req);
        requester.sendMessage(Component.text("Mise à mort RP de " + req.targetName() + " signalée au staff"
                + (online == 0 ? " (aucun staff en ligne : elle attend au Poste de garde tant que la cible est KO)." : "."),
                NamedTextColor.GRAY));

        // 2) la demande tombe si la victime n'est plus KO ou se déconnecte
        BukkitTask watch = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            KillRequest live = pending.get(id);
            if (live == null) return;
            Player t = Bukkit.getPlayer(live.targetPlayerId());
            if (t == null || !plugin.ko().isKo(live.targetPlayerId())) {
                pending.remove(id);
                if (live.timeoutTask() != null) live.timeoutTask().cancel();
                if (plugin.staffPanel() != null) plugin.staffPanel().onKillClosed(live.id());
                log("EXPIRE", live, "-", "victime plus KO");
                notifyStaff(Component.text("[Staff] ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("Mise à mort RP de " + live.targetName() + " tombée (plus KO).",
                                NamedTextColor.GRAY)));
                Player r2 = Bukkit.getPlayer(live.requesterId());
                if (r2 != null) r2.sendMessage(Component.text("La mise à mort RP de " + live.targetName()
                        + " est tombée (plus KO).", NamedTextColor.GRAY));
            }
        }, 40L, 40L);
        req.setTimeoutTask(watch);
    }

    /** Id de la demande en attente du staff pour cette victime, ou -1. */
    public long pendingFor(java.util.UUID target) {
        for (KillRequest r : pending.values()) if (r.targetPlayerId().equals(target)) return r.id();
        return -1;
    }

    private void notifyStaff(Component c) {
        for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("shinobicore.staff")) staff.sendMessage(c);
    }

    /* --------------------------------------------------------------- decide */

    /** {@code [Valider]} au tchat : demande la raison, puis accorde la RPK. */
    public void approve(Player staff, long requestId) {
        if (staff == null || !staff.hasPermission("shinobicore.staff")) return;
        KillRequest req = pending.get(requestId);
        if (req == null || req.validated()) {
            staff.sendMessage(Component.text("Cette demande n'est plus en attente.", NamedTextColor.RED));
            return;
        }
        plugin.chatInputs().prompt(staff,
                "Donne la raison validant la RPK (ou 'cancel') :",
                reason -> {
                    String trimmed = reason == null ? "" : reason.trim();
                    if (trimmed.isEmpty() || "cancel".equalsIgnoreCase(trimmed)) {
                        staff.sendMessage(Component.text("Annulation : raison vide.", NamedTextColor.GRAY));
                        return;
                    }
                    validate(staff, requestId, trimmed);
                },
                () -> staff.sendMessage(Component.text("Validation annulée (timeout).", NamedTextColor.GRAY)));
    }

    /** Validation depuis le Poste de garde : la raison vient de l'écran (pas de saisie au tchat). */
    public void approveWithReason(Player staff, long requestId, String reason) {
        if (staff == null || !staff.hasPermission("shinobicore.staff")) return;
        validate(staff, requestId, reason == null || reason.isBlank() ? "Validée depuis le Poste de garde" : reason);
    }

    /**
     * Accorde la RPK : le personnage <b>ne meurt pas encore</b>. Le demandeur peut désormais l'achever ou
     * l'exécuter (menu d'interaction sur le corps) ; la mort tombe à cette dernière action.
     */
    private void validate(Player staff, long requestId, String reason) {
        KillRequest req = pending.get(requestId);
        if (req == null || req.validated()) {
            staff.sendMessage(Component.text("Cette demande n'est plus en attente.", NamedTextColor.RED));
            return;
        }
        req.validate(staff.getName(), reason);
        log("VALIDE", req, staff.getName(), reason);
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().onKillClosed(req.id());
            plugin.staffPanel().log(staff, "ko", "RPK validée", pidPrefix(req.targetCharacterId()) + req.targetName() + " · " + reason);
            plugin.staffPanel().system("RPK validée : " + req.targetName() + " (par " + staff.getName()
                    + ") — en attente de l'exécution par " + req.requesterName() + ".");
        }
        notifyStaff(Component.text("[Staff] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("RPK de " + req.targetName() + " validée par " + staff.getName()
                        + " · " + reason + " — " + req.requesterName() + " doit maintenant l'exécuter.",
                        NamedTextColor.GRAY)));
        Player rq = Bukkit.getPlayer(req.requesterId());
        if (rq != null) {
            rq.sendMessage(Component.text("☠ RPK validée : ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("tu peux achever ou exécuter " + req.targetName()
                            + " (menu d'interaction sur son corps).", NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false)));
            rq.playSound(rq.getLocation(), org.bukkit.Sound.BLOCK_BELL_RESONATE, 0.6f, 0.5f);
        }
    }

    /** RPK accordée à {@code killer} sur {@code target}, ou null. */
    public KillRequest validatedFor(UUID killer, UUID target) {
        for (KillRequest r : pending.values()) {
            if (r.validated() && r.requesterId().equals(killer) && r.targetPlayerId().equals(target)) return r;
        }
        return null;
    }

    /** Pour le menu d'interaction : 0 rien, 1 demande de {@code viewer} en attente du staff, 2 RPK accordée à viewer. */
    public int statusFor(UUID viewer, UUID target) {
        for (KillRequest r : pending.values()) {
            if (!r.targetPlayerId().equals(target) || !r.requesterId().equals(viewer)) continue;
            return r.validated() ? 2 : 1;
        }
        return 0;
    }

    /** {@code [Y aller]} : téléporte le staff sur la victime (ou la scène si elle est hors ligne). */
    public void goTo(Player staff, long requestId) {
        if (staff == null || !staff.hasPermission("shinobicore.staff")) return;
        KillRequest req = pending.get(requestId);
        if (req == null) { staff.sendMessage(Component.text("Cette demande n'est plus en attente.", NamedTextColor.RED)); return; }
        Player t = Bukkit.getPlayer(req.targetPlayerId());
        staff.teleport(t != null ? t.getLocation() : req.scene());
        if (plugin.staffPanel() != null) plugin.staffPanel().log(staff, "tp", "TP vers", pidPrefix(req.targetCharacterId()) + req.targetName() + " (mort RP)");
    }

    /** {@code [Refuser]} : la demande tombe, le personnage reste en vie. */
    public void refuse(Player staff, long requestId) {
        if (staff == null || !staff.hasPermission("shinobicore.staff")) return;
        KillRequest req = pending.remove(requestId);
        if (req == null) {
            staff.sendMessage(Component.text("Cette demande n'est plus en attente.", NamedTextColor.RED));
            return;
        }
        if (req.timeoutTask() != null) req.timeoutTask().cancel();
        log("REFUSE", req, staff.getName(), "");
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().onKillClosed(req.id());
            plugin.staffPanel().log(staff, "ko", "RPK refusée", pidPrefix(req.targetCharacterId()) + req.targetName() + " (demandée par " + req.requesterName() + ")");
        }
        Player rq = Bukkit.getPlayer(req.requesterId());
        if (rq != null) rq.sendMessage(Component.text("Ta demande de RPK sur " + req.targetName() + " a été refusée.",
                NamedTextColor.RED));
        notifyStaff(Component.text("[Staff] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("RPK de " + req.targetName() + " refusée par " + staff.getName() + ".",
                        NamedTextColor.GRAY)));
    }

    /**
     * La dernière action : le personnage meurt (achever, ou fin d'une exécution). Exige une RPK accordée à
     * {@code killer}. Le joueur reste sur place, dans la scène de mort ({@link DeathScene}).
     */
    public boolean kill(Player killer, Player target, String how) {
        KillRequest req = validatedFor(killer.getUniqueId(), target.getUniqueId());
        if (req == null) {
            killer.sendMessage(Component.text("Aucune RPK validée sur cette personne.", NamedTextColor.RED));
            return false;
        }
        pending.remove(req.id());
        if (req.timeoutTask() != null) req.timeoutTask().cancel();
        ShinobiCharacter dead = null;
        if (req.targetCharacterId() != null) {
            dead = plugin.characters().findById(target.getUniqueId(), req.targetCharacterId()).orElse(null);
        }
        if (dead != null) {
            dead.setDead(true);
            plugin.characterRepository().save(dead);
            Bukkit.getPluginManager().callEvent(new com.reborn.shinobicore.event.CharacterDeathEvent(
                    target.getUniqueId(), dead.id(), dead.name()));
        }
        log("MORT", req, req.validatedBy(), how + " · " + req.reason());
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().system("Mort RP : " + req.targetName() + " (" + how + " par " + req.requesterName() + ").");
        }
        notifyStaff(Component.text("[Staff] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("☠ " + req.targetName() + " est mort (" + how + " par " + req.requesterName() + ").",
                        NamedTextColor.GRAY)));
        if (plugin.deathScene() != null) plugin.deathScene().start(target, req.targetName());
        return true;
    }

    /* --------------------------------------------------------------- logging */

    private void log(String verdict, KillRequest req, String staffName, String reason) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String ts = fmt.format(new Date());
        Location l = req.scene();
        String line = String.format(
                "[%s] %s id=%d requester=%s(%s)%s target=%s(%s)%s staff=%s loc=%s/%.1f,%.1f,%.1f reason=%s%n",
                ts, verdict, req.id(),
                req.requesterName(), req.requesterId(), pidOfPlayer(req.requesterId()),
                req.targetName(), req.targetPlayerId(), pidOfCharacter(req.targetCharacterId()),
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

    /** {@code " #00042"} : N° du personnage actif du demandeur (journal staff), vide sinon. */
    private String pidOfPlayer(java.util.UUID playerId) {
        ShinobiCharacter c = playerId == null ? null : plugin.characters().getActive(playerId);
        String t = com.reborn.shinobicore.character.PlayerIds.tag(c);
        return t.isEmpty() ? "" : " " + t;
    }

    /** {@code "#00042 "} devant un nom (journal staff), vide si le personnage n'a pas de N°. */
    private String pidPrefix(java.util.UUID characterId) {
        String s = pidOfCharacter(characterId).trim();
        return s.isEmpty() ? "" : s + " ";
    }

    /** {@code " #00042"} : N° du personnage visé (via l'index, même hors ligne), vide sinon. */
    private String pidOfCharacter(java.util.UUID characterId) {
        if (characterId == null || plugin.playerIds() == null) return "";
        int n = plugin.playerIds().numberOf(characterId);
        return n < 0 ? "" : " #" + com.reborn.shinobicore.character.PlayerIds.format(n);
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
        Component go = Component.text("[Y aller]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/sckillvote goto " + req.id()))
                .hoverEvent(HoverEvent.showText(Component.text("Téléporte sur la scène.", NamedTextColor.GRAY)));
        Location l = req.scene();
        Player rqp = Bukkit.getPlayer(req.requesterId());
        Player tp = Bukkit.getPlayer(req.targetPlayerId());

        return Component.text("☠ [Staff] RPK : ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text(req.requesterName(), NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(rqp != null ? " (" + rqp.getName() + ")" : "", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(" veut mettre à mort ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(req.targetName(), NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(tp != null ? " (" + tp.getName() + ")" : "", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(" · " + (l.getWorld() != null ? l.getWorld().getName() : "?") + " "
                        + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "  ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(approve)
                .append(Component.text("  "))
                .append(refuse)
                .append(Component.text("  "))
                .append(go);
    }
}
