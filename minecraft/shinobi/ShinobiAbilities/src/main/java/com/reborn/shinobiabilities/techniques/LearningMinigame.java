package com.reborn.shinobiabilities.techniques;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.Difficulty;
import com.reborn.shinobicore.technique.Mudra;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The learning minigames.
 *
 * <ul>
 *   <li><b>MUDRA</b> — a title prompts each expected hand seal; the
 *       player confirms with a LEFT click before the per-step timeout
 *       (EASY 3 s / MEDIUM 2 s / HARD 1.5 s). A RIGHT click is a wrong
 *       sign — instant fail.</li>
 *   <li><b>PUSHUP</b> — N sneak-reps (rank-scaled), each within the
 *       difficulty's rep timer. Boss bar tracks progress.</li>
 *   <li><b>SUIVI</b> — staff validation via /sa valider|refuser.</li>
 *   <li><b>NONE</b> — succeeds instantly.</li>
 * </ul>
 */
public final class LearningMinigame implements Listener {

    /** Outcome callback: (player, success). */
    private sealed interface Session permits MudraSession, PushupSession, SuiviSession {}

    private static final class MudraSession implements Session {
        Ability ability;
        Location shelfLoc;
        int slot;
        /** Prompt labels — mudra names, free-form steps, or random. */
        List<String> sequence;
        int index;
        BukkitTask timeout;
    }

    private static final class PushupSession implements Session {
        Ability ability;
        Location shelfLoc;
        int slot;
        int reps;
        int done;
        boolean down;
        BukkitTask timeout;
        BossBar bar;
    }

    private static final class SuiviSession implements Session {
        Ability ability;
        Location shelfLoc;
        int slot;
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Completion sink, wired by TechniquesService:
     *  (player, session data) on success / failure. */
    private BiConsumer<Player, Result> sink;

    /** What the service needs to finalise a run. */
    public record Result(Ability ability, Location shelfLoc, int slot, boolean success) {}

    public LearningMinigame(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void wire(BiConsumer<Player, Result> sink) {
        this.sink = sink;
    }

    public boolean isActive(Player p) {
        return p != null && sessions.containsKey(p.getUniqueId());
    }

    /** True when the player has a pending SUIVI validation. */
    public boolean isAwaitingValidation(Player p) {
        return sessions.get(p.getUniqueId()) instanceof SuiviSession;
    }

    /* ---------------------------------------------------------------- start */

    public void start(Player p, Ability ability, Location shelfLoc, int slot) {
        if (isActive(p)) return;
        switch (ability.minigame()) {
            case NONE -> finish(p, new Result(ability, shelfLoc, slot, true));
            case MUDRA -> startMudra(p, ability, shelfLoc, slot);
            case PUSHUP -> startPushup(p, ability, shelfLoc, slot);
            case SUIVI -> startSuivi(p, ability, shelfLoc, slot);
        }
    }

    /* ---------------------------------------------------------------- mudra */

    private void startMudra(Player p, Ability ability, Location shelfLoc, int slot) {
        MudraSession s = new MudraSession();
        s.ability = ability;
        s.shelfLoc = shelfLoc;
        s.slot = slot;
        // Mudra names → free-form steps → random seals (rank-sized).
        List<String> labels = ability.incantationLabels();
        if (labels == null) {
            List<String> random = new java.util.ArrayList<>();
            for (Mudra m : Mudra.randomSequence(ability.rank().defaultMudraCount())) {
                random.add(m.display());
            }
            labels = random;
        }
        s.sequence = labels;
        s.index = 0;
        sessions.put(p.getUniqueId(), s);
        p.sendMessage(Component.text(
                "Entraînement — reproduis les signes : clic GAUCHE pour confirmer, "
                        + "clic droit = raté.", NamedTextColor.GOLD));
        promptMudra(p, s);
    }

    private void promptMudra(Player p, MudraSession s) {
        String label = s.sequence.get(s.index);
        long timeoutMs = s.ability.difficulty().mudraTimeoutMillis();
        p.showTitle(Title.title(
                Component.text(label, NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Signe " + (s.index + 1) + "/" + s.sequence.size()
                        + " — clic gauche !", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100),
                        Duration.ofMillis(timeoutMs), Duration.ofMillis(200))));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.4f);

        if (s.timeout != null) s.timeout.cancel();
        final int expectIndex = s.index;
        s.timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Session live = sessions.get(p.getUniqueId());
            if (live == s && s.index == expectIndex) {
                fail(p, "Trop lent — le signe t'a échappé.");
            }
        }, Math.max(1L, timeoutMs / 50L));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = event.getPlayer();
        if (!(sessions.get(p.getUniqueId()) instanceof MudraSession s)) return;
        s.index++;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,
                0.8f, 1.0f + 0.08f * s.index);
        if (s.index >= s.sequence.size()) {
            if (s.timeout != null) s.timeout.cancel();
            finish(p, new Result(s.ability, s.shelfLoc, s.slot, true));
        } else {
            promptMudra(p, s);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        if (sessions.get(p.getUniqueId()) instanceof MudraSession) {
            fail(p, "Mauvais signe !");
        }
    }

    /* --------------------------------------------------------------- pushup */

    private void startPushup(Player p, Ability ability, Location shelfLoc, int slot) {
        PushupSession s = new PushupSession();
        s.ability = ability;
        s.shelfLoc = shelfLoc;
        s.slot = slot;
        s.reps = plugin.getConfig().getInt(
                "techniques.pushup-reps." + ability.rank().name(),
                defaultReps(ability.rank().name()));
        s.done = 0;
        s.bar = BossBar.bossBar(pushupTitle(s), 0f,
                BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        sessions.put(p.getUniqueId(), s);
        p.showBossBar(s.bar);
        p.sendMessage(Component.text(
                "Entraînement — fais " + s.reps + " pompes : accroupis-toi puis "
                        + "relève-toi, en rythme !", NamedTextColor.GOLD));
        armPushupTimeout(p, s);
    }

    private static int defaultReps(String rank) {
        return switch (rank) {
            case "E" -> 5;
            case "D" -> 8;
            case "C" -> 12;
            case "B" -> 16;
            case "A" -> 20;
            default -> 25;
        };
    }

    private Component pushupTitle(PushupSession s) {
        return Component.text("Pompes : " + s.done + " / " + s.reps,
                NamedTextColor.YELLOW);
    }

    private void armPushupTimeout(Player p, PushupSession s) {
        if (s.timeout != null) s.timeout.cancel();
        long repMs = s.ability.difficulty().pushupRepMillis();
        final int expectDone = s.done;
        s.timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Session live = sessions.get(p.getUniqueId());
            if (live == s && s.done == expectDone) {
                fail(p, "Rythme perdu — l'entraînement échoue.");
            }
        }, Math.max(1L, repMs / 50L));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (!(sessions.get(p.getUniqueId()) instanceof PushupSession s)) return;
        if (event.isSneaking()) {
            s.down = true;
            return;
        }
        if (!s.down) return;
        s.down = false;
        s.done++;
        s.bar.progress(Math.min(1f, s.done / (float) s.reps));
        s.bar.name(pushupTitle(s));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK,
                0.6f, 0.8f + 0.4f * s.done / s.reps);
        if (s.done >= s.reps) {
            if (s.timeout != null) s.timeout.cancel();
            p.hideBossBar(s.bar);
            finish(p, new Result(s.ability, s.shelfLoc, s.slot, true));
        } else {
            armPushupTimeout(p, s);
        }
    }

    /* ---------------------------------------------------------------- suivi */

    private void startSuivi(Player p, Ability ability, Location shelfLoc, int slot) {
        SuiviSession s = new SuiviSession();
        s.ability = ability;
        s.shelfLoc = shelfLoc;
        s.slot = slot;
        sessions.put(p.getUniqueId(), s);
        p.sendMessage(Component.text(
                "Cette technique demande un suivi : un membre du staff doit "
                        + "valider ton apprentissage.", NamedTextColor.GOLD));
        Component notice = Component.text("[Suivi] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(p.getName() + " veut apprendre « "
                        + ability.name() + " » — ", NamedTextColor.GRAY))
                .append(Component.text("[Valider]", NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent
                                .runCommand("/sa valider " + p.getName())))
                .append(Component.text(" "))
                .append(Component.text("[Refuser]", NamedTextColor.RED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent
                                .runCommand("/sa refuser " + p.getName())))
                .append(Component.text(" "))
                .append(Component.text("[File d'attente]", NamedTextColor.GOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent
                                .runCommand("/menu")));
        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission("shinobiabilities.staff")) {
                staff.sendMessage(notice);
            }
        }
    }

    /** One pending SUIVI request, for the staff validation GUI. */
    public record PendingSuivi(UUID playerId, String playerName,
                               String abilityId, String abilityName) {}

    /** Snapshot of every pending SUIVI validation. */
    public java.util.List<PendingSuivi> pendingValidations() {
        java.util.List<PendingSuivi> out = new java.util.ArrayList<>();
        for (var e : sessions.entrySet()) {
            if (!(e.getValue() instanceof SuiviSession s)) continue;
            Player p = plugin.getServer().getPlayer(e.getKey());
            out.add(new PendingSuivi(e.getKey(),
                    p != null ? p.getName() : e.getKey().toString(),
                    s.ability.id(), s.ability.name()));
        }
        return out;
    }

    /** Staff approval — /sa valider. Returns false when nothing pends. */
    public boolean validate(Player target) {
        if (!(sessions.get(target.getUniqueId()) instanceof SuiviSession s)) return false;
        finish(target, new Result(s.ability, s.shelfLoc, s.slot, true));
        return true;
    }

    /** Staff refusal — /sa refuser. */
    public boolean refuse(Player target) {
        if (!(sessions.get(target.getUniqueId()) instanceof SuiviSession)) return false;
        fail(target, "Le staff a refusé la validation.");
        return true;
    }

    /* ------------------------------------------------------------- plumbing */

    private void finish(Player p, Result result) {
        cleanup(p);
        if (sink != null) sink.accept(p, result);
    }

    private void fail(Player p, String reason) {
        Session s = sessions.get(p.getUniqueId());
        Result result = switch (s) {
            case MudraSession m -> new Result(m.ability, m.shelfLoc, m.slot, false);
            case PushupSession pu -> new Result(pu.ability, pu.shelfLoc, pu.slot, false);
            case SuiviSession su -> new Result(su.ability, su.shelfLoc, su.slot, false);
            case null -> null;
        };
        cleanup(p);
        if (result == null) return;
        p.sendMessage(Component.text(reason, NamedTextColor.RED));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
        if (sink != null) sink.accept(p, result);
    }

    /** Abort silently (quit / KO / switch) — the parchemin stays. */
    public void abort(Player p) {
        cleanup(p);
    }

    private void cleanup(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s instanceof MudraSession m && m.timeout != null) m.timeout.cancel();
        if (s instanceof PushupSession pu) {
            if (pu.timeout != null) pu.timeout.cancel();
            if (pu.bar != null) p.hideBossBar(pu.bar);
        }
    }

    public void abortAll() {
        for (UUID id : sessions.keySet().toArray(new UUID[0])) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) cleanup(p);
            else sessions.remove(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abort(event.getPlayer());
    }
}
