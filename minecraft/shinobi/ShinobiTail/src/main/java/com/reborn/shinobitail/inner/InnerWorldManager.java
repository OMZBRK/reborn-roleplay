package com.reborn.shinobitail.inner;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import com.reborn.shinobitail.transform.ActiveTransformation;
import com.reborn.shinobitail.transform.TransformationManager;
import com.reborn.shinobitail.util.Chances;
import com.reborn.shinobitail.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Inner World confrontation sequence:
 *
 * <ol>
 *   <li><b>Freeze</b> — the host is stunned, invulnerable, wrapped in a
 *       rising aura at their current position.</li>
 *   <li><b>Swap</b> — a {@link FakeBody} stays behind; the player is
 *       teleported to the beast's configured Inner World location.</li>
 *   <li><b>Choice</b> — the {@link ConfrontGui} asks: yield (next stage)
 *       or resist (roll against mastery / trust / cooperation vs
 *       influence / rage).</li>
 *   <li><b>Return</b> — the player snaps back into their body and the
 *       outcome is applied.</li>
 * </ol>
 */
public final class InnerWorldManager {

    private final ShinobiTail plugin;
    private final Map<UUID, InnerWorldSession> sessions = new ConcurrentHashMap<>();

    public InnerWorldManager(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    public boolean inSession(UUID playerId) { return sessions.containsKey(playerId); }
    public InnerWorldSession session(UUID playerId) { return sessions.get(playerId); }

    /* -------------------------------------------------------------- begin */

    /**
     * Pulls a TRANSFORMED player into the Inner World. Trigger reasons:
     * control window, max rage, or a GM forcing the scene.
     */
    public void beginConfrontation(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) return;
        ActiveTransformation t = plugin.transformations().get(id);
        if (t == null) return;

        InnerWorldSession session = new InnerWorldSession(
                id, t.data(), t.beast(), t.stage(), player.getLocation().clone());
        sessions.put(id, session);

        // --- phase 1: freeze ------------------------------------------------
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                20 * (freezeSeconds() + 3), 250, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                20 * 2, 0, true, false, false));
        player.getWorld().playSound(player.getLocation(),
                Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 0.5f);
        player.showTitle(Title.title(
                Component.text("…", NamedTextColor.DARK_RED),
                Component.text("Quelque chose t'appelle à l'intérieur ("
                        + reason + ")", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200),
                        Duration.ofSeconds(2), Duration.ofMillis(400))));

        // Rising aura while frozen.
        session.setAuraTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (com.reborn.shinobicore.util.Tps.shouldDefer()) return;
            Location base = player.getLocation();
            Particle.DustOptions dust =
                    new Particle.DustOptions(session.beast().auraColor(), 2.0f);
            double spin = (System.currentTimeMillis() % 1200) / 1200.0 * Math.PI * 2;
            for (int i = 0; i < 12; i++) {
                double a = spin + Math.PI * 2 / 12 * i;
                double y = (i % 4) * 0.6;
                player.getWorld().spawnParticle(Particle.DUST,
                        base.clone().add(Math.cos(a) * 1.1, y, Math.sin(a) * 1.1),
                        2, 0.05, 0.05, 0.05, 0, dust);
            }
            player.getWorld().spawnParticle(Particle.END_ROD,
                    base.clone().add(0, 1.2, 0), 3, 0.3, 0.5, 0.3, 0.02);
        }, 0L, 2L));

        // --- phase 2 after the freeze: swap + GUI ---------------------------
        session.setFreezeTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.setFreezeTask(null);
            if (!player.isOnline()) { abort(player); return; }
            enterInnerWorld(player, session);
        }, 20L * freezeSeconds()));
    }

    private void enterInnerWorld(Player player, InnerWorldSession session) {
        if (session.auraTask() != null) { session.auraTask().cancel(); session.setAuraTask(null); }
        BeastDefinition beast = session.beast();

        Location dest = plugin.beasts().innerWorld(beast.id());
        if (dest != null) {
            if (plugin.getConfig().getBoolean("fake-body.enabled", true)) {
                ShinobiCharacter active = plugin.jinchuriki().activeCharacter(player);
                String name = active != null ? active.name() : player.getName();
                var stand = FakeBody.spawn(plugin, player, name);
                session.setFakeBodyId(stand.getUniqueId());
            }
            player.teleport(dest);
            player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        } else {
            plugin.getLogger().warning("No inner-world location set for beast '"
                    + beast.id() + "' — confrontation happens in place. "
                    + "Use /tail setinnerworld " + beast.id());
        }

        session.setPhase(InnerWorldSession.Phase.CHOOSING);
        player.showTitle(Title.title(
                Component.text("Monde Intérieur", NamedTextColor.DARK_PURPLE),
                Component.text(beast.beastName() + " te fait face…",
                        NamedTextColor.LIGHT_PURPLE),
                Title.Times.times(Duration.ofMillis(400),
                        Duration.ofSeconds(3), Duration.ofSeconds(1))));
        String line = beast.randomConfrontLine();
        if (line != null) Fmt.beastWhisper(plugin, player, beast, line);

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> { if (player.isOnline() && inSession(player.getUniqueId())
                        && session.phase() == InnerWorldSession.Phase.CHOOSING) {
                    plugin.confrontGui().open(player, session);
                } }, 30L);

        long timeout = plugin.getConfig()
                .getLong("inner-world.decision-timeout-seconds", 45);
        session.setTimeoutTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.setTimeoutTask(null);
            boolean asContinue = !"resist".equalsIgnoreCase(plugin.getConfig()
                    .getString("inner-world.timeout-counts-as", "continue"));
            Fmt.beastWhisper(plugin, player, beast,
                    "Ton silence est une réponse.");
            resolve(player, asContinue);
        }, 20L * Math.max(5, timeout)));
    }

    /* ------------------------------------------------------------- resolve */

    /**
     * Applies the player's decision. {@code yield} true → next stage;
     * false → resist roll (success ends the transformation).
     */
    public void resolve(Player player, boolean yield) {
        InnerWorldSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase() == InnerWorldSession.Phase.RESOLVING) return;
        session.setPhase(InnerWorldSession.Phase.RESOLVING);
        session.cancelTasks();
        player.closeInventory();

        JinchurikiData data = session.data();
        BeastDefinition beast = session.beast();
        TransformationManager tm = plugin.transformations();

        // Sequential control growth: EVERY confrontation feeds the lowest
        // unmastered stage the host has already reached. Once a stage sits
        // at 100%, the next one only progresses after the host has yielded
        // into it at least once — refusing the rage forever freezes growth.
        var gain = plugin.getConfig()
                .getConfigurationSection("inner-world.mastery-gain");
        int target = data.masteryTargetStage(beast.tails());
        if (target > 0 && gain != null) {
            data.addMastery(target, gain.getDouble("per-confrontation", 1.0));
        }

        if (yield) {
            returnPlayer(player, session);
            Fmt.beastWhisper(plugin, player, beast,
                    "Oui… ABANDONNE-toi ! Ce pouvoir est NÔTRE !");
            tm.escalate(player);
            plugin.jinchuriki().save(data);
            return;
        }

        double chance = Chances.resistChance(
                plugin.getConfig().getConfigurationSection("inner-world.resist"),
                beast, data, session.stageAtEntry());
        boolean success = Chances.roll(chance);
        data.recordResist(success);

        if (success) {
            if (target > 0) {
                data.addMastery(target,
                        gain != null ? gain.getDouble("on-resist-success", 2.0) : 2.0);
            }
            returnPlayer(player, session);
            Fmt.beastWhisper(plugin, player, beast,
                    "Tch… Profite de ta petite victoire. Je serai encore là.");
            tm.stop(player, TransformationManager.StopReason.RESIST_SUCCESS);
        } else {
            if (target > 0) {
                data.addMastery(target,
                        gain != null ? gain.getDouble("on-resist-fail", 0.5) : 0.5);
            }
            returnPlayer(player, session);
            Fmt.beastWhisper(plugin, player, beast,
                    "Trop faible ! TA volonté ne pèse RIEN face à la mienne !");
            player.showTitle(Title.title(
                    Component.text("Échec", NamedTextColor.DARK_RED),
                    Component.text("La volonté du démon te submerge…",
                            NamedTextColor.RED),
                    Title.Times.times(Duration.ofMillis(200),
                            Duration.ofSeconds(2), Duration.ofMillis(600))));
            tm.escalate(player);
        }
        plugin.jinchuriki().save(data);
    }

    /**
     * Seal-break path: perfect trust + cooperation turn the cage into a
     * partnership. Ends the confrontation straight into Mode Union —
     * the conditions are re-checked here so a stray click can't cheat.
     */
    public void resolveUnion(Player player) {
        InnerWorldSession session = sessions.get(player.getUniqueId());
        if (session == null
                || session.phase() == InnerWorldSession.Phase.RESOLVING) return;
        JinchurikiData data = session.data();
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("union.enabled", true) || data.sealRemoved()
                || data.trust() < cfg.getDouble("union.require-trust", 100)
                || data.cooperation() < cfg.getDouble("union.require-cooperation", 100)) {
            return;
        }
        session.setPhase(InnerWorldSession.Phase.RESOLVING);
        session.cancelTasks();
        player.closeInventory();

        var gain = cfg.getConfigurationSection("inner-world.mastery-gain");
        int target = data.masteryTargetStage(session.beast().tails());
        if (target > 0 && gain != null) {
            data.addMastery(target, gain.getDouble("per-confrontation", 1.0));
        }
        data.setSealRemoved(true);
        returnPlayer(player, session);
        Fmt.beastWhisper(plugin, player, session.beast(),
                "Enfin… tu ne me retiens plus, et je ne te dévore plus. "
                        + "Allons-y, partenaire.");
        plugin.transformations().startUnion(player, data, session.beast());
        plugin.jinchuriki().save(data);
    }

    /** Emergency exit (quit / disable / GM stop) — no outcome applied. */
    public void abort(Player player) {
        InnerWorldSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.setPhase(InnerWorldSession.Phase.RESOLVING);
        session.cancelTasks();
        player.closeInventory();
        returnPlayer(player, session);
    }

    public void abortAll() {
        for (UUID id : Map.copyOf(sessions).keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) abort(p);
            else sessions.remove(id);
        }
    }

    /* -------------------------------------------------------------- return */

    private void returnPlayer(Player player, InnerWorldSession session) {
        sessions.remove(session.playerId());

        Location back = session.returnLocation();
        if (session.fakeBodyId() != null) {
            Entity stand = Bukkit.getEntity(session.fakeBodyId());
            if (stand != null) {
                back = stand.getLocation();
                stand.remove();
            }
        }
        if (player.isOnline()) {
            player.teleport(back);
            player.setInvulnerable(false);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.getWorld().playSound(back, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.8f);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                    back.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        } else {
            player.setInvulnerable(false);
        }
    }

    private int freezeSeconds() {
        return Math.max(1, plugin.getConfig().getInt("inner-world.freeze-seconds", 3));
    }
}
