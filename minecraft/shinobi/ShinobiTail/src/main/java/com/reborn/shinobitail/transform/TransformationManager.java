package com.reborn.shinobitail.transform;

import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.beast.BeastStage;
import com.reborn.shinobitail.data.JinchurikiData;
import com.reborn.shinobitail.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TransformationManager {

    public enum StopReason {
        RESIST_SUCCESS, GM, KO, QUIT, SWITCH, SHUTDOWN, UNBIND, SEALED, UNION_END, RESET
    }

    private static final String MOD_DAMAGE = "damage_boost";
    private static final String MOD_SPEED  = "speed_boost";
    private static final String MOD_KB     = "knockback_resistance";
    private static final String MOD_SCALE  = "scale_boost";

    private final ShinobiTail plugin;
    private final Map<UUID, ActiveTransformation> active = new ConcurrentHashMap<>();
    private BukkitTask logicTask;
    private BukkitTask particleTask;

    public TransformationManager(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    public void start() {
        logicTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLogic, 20L, 20L);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAura, 5L, 5L);
    }

    public void stop() {
        if (logicTask != null) logicTask.cancel();
        if (particleTask != null) particleTask.cancel();
        for (UUID id : List.copyOf(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stop(p, StopReason.SHUTDOWN);
            else active.remove(id);
        }
    }

    public boolean isTransformed(UUID playerId) { return active.containsKey(playerId); }

    /**
     * Aura range (blocks) of this player's CURRENT stage — 0 when not
     * transformed. PUBLIC API for sensory systems: characters with
     * advanced sensing can feel the bijū chakra within this radius
     * (the tracking arrow itself lives in the sensory plugin).
     */
    public double auraRange(UUID playerId) {
        ActiveTransformation t = active.get(playerId);
        return t == null ? 0.0 : t.beast().stage(t.stage()).auraRange();
    }

    /** GM narration freeze — pauses rage growth and control windows. */
    public void setPaused(Player player, boolean paused) {
        ActiveTransformation t = active.get(player.getUniqueId());
        if (t != null) t.setPaused(paused);
    }

    /**
     * Mode Union — the seal is broken in harmony: full final-stage power,
     * golden aura, ZERO rage, no corruption, no control windows, and the
     * point-of-no-return does NOT apply. This is mastery, not loss.
     */
    public void startUnion(Player player, JinchurikiData data,
                           BeastDefinition beast) {
        ActiveTransformation t = active.get(player.getUniqueId());
        if (t == null) {
            t = new ActiveTransformation(
                    player.getUniqueId(), data, beast, beast.tails());
            armControlWindow(t);
            active.put(player.getUniqueId(), t);
            data.recordTransformation();
        } else {
            clearStageEffects(player, t);
            t.setStage(beast.tails());
        }
        t.setUnion(true);
        t.setFiredCorruptionTier(-1);
        data.setRage(0);
        if (beast.tails() > data.highestStageReached()) {
            data.setHighestStageReached(beast.tails());
        }
        applyStageEffects(player, t);

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.7f);
        player.showTitle(Title.title(
                Component.text("UNION", NamedTextColor.GOLD),
                Component.text(beast.beastName()
                                + " et toi ne faites plus qu'un.",
                        NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300),
                        Duration.ofSeconds(4), Duration.ofSeconds(1))));
        var resolved = plugin.jinchuriki().resolveById(data.characterId());
        String charName = resolved != null
                ? resolved.character().name() : player.getName();
        String raw = plugin.getConfig().getString("union.broadcast",
                "&6&l{character} et {beast} ne font plus qu'un !");
        Bukkit.broadcast(Fmt.legacy(raw.replace("{character}", charName)
                .replace("{beast}", beast.beastName())));
        plugin.jinchuriki().save(data);
    }
    public ActiveTransformation get(UUID playerId) { return active.get(playerId); }
    public Collection<ActiveTransformation> all() { return active.values(); }

    public void begin(Player player, JinchurikiData data, BeastDefinition beast,
                      int stage, ActiveTransformation.Cause cause) {
        if (active.containsKey(player.getUniqueId())) return;
        stage = Math.max(1, Math.min(beast.tails(), stage));

        ActiveTransformation t = new ActiveTransformation(
                player.getUniqueId(), data, beast, stage);
        armControlWindow(t);
        active.put(player.getUniqueId(), t);

        data.recordTransformation();
        applyStageEffects(player, t);
        noteStageReached(player, t);
        announceEntry(player, t, cause);
        plugin.jinchuriki().save(data);
    }

    public void escalate(Player player) {
        ActiveTransformation t = active.get(player.getUniqueId());
        if (t == null) return;
        BeastDefinition beast = t.beast();
        int next = Math.min(beast.tails(), t.stage() + 1);
        boolean grew = next != t.stage();

        clearStageEffects(player, t);
        t.setStage(next);
        t.data().setRage(plugin.getConfig().getDouble("rage.after-escalation", 25.0));
        t.setFiredCorruptionTier(-1);
        armControlWindow(t);
        applyStageEffects(player, t);
        noteStageReached(player, t);

        BeastStage st = beast.stage(next);
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL,
                1.0f, grew ? 0.6f : 0.4f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0),
                3, 0.4, 0.6, 0.4, 0.0);
        player.showTitle(Title.title(
                Component.text(grew
                                ? beast.beastName() + " — " + st.displayName()
                                : beast.beastName() + " — Paroxysme",
                        NamedTextColor.DARK_RED),
                Component.text(grew
                                ? "Étape " + next + " / " + beast.tails()
                                : "Le démon est à son apogée…",
                        NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(300),
                        Duration.ofSeconds(3), Duration.ofMillis(800))));
        plugin.jinchuriki().save(t.data());
    }

    public void setStage(Player player, JinchurikiData data,
                         BeastDefinition beast, int stage) {
        ActiveTransformation t = active.get(player.getUniqueId());
        if (t == null) {
            begin(player, data, beast, stage, ActiveTransformation.Cause.GM);
            return;
        }
        clearStageEffects(player, t);
        t.setStage(Math.max(1, Math.min(beast.tails(), stage)));
        t.setFiredCorruptionTier(-1);
        applyStageEffects(player, t);
        noteStageReached(player, t);
    }

    public void stop(Player player, StopReason reason) {
        ActiveTransformation t = active.remove(player.getUniqueId());
        if (t == null) return;

        clearStageEffects(player, t);
        if (t.bossBar() != null) t.bossBar().removeAll();

        JinchurikiData data = t.data();
        long lived = (System.currentTimeMillis() - t.startedAtMillis()) / 1000L;
        data.addSecondsTransformed(lived);
        switch (reason) {
            case UNBIND, UNION_END, RESET -> data.setRage(0);
            case SHUTDOWN -> { }
            default -> data.setRage(Math.min(data.rage(),
                    plugin.getConfig().getDouble("rage.after-stop", 20.0)));
        }
        // Point of no return: once the final stage was entered, ending the
        // transformation (whatever the cause) leaves no host to come back
        // to. The character is ruled dead — ShinobiCore staff tools remain
        // the only resurrection path. A GM reset is the deliberate exception:
        // it wipes the slate clean instead of killing the character.
        if (t.finalRelease() && reason != StopReason.SHUTDOWN
                && reason != StopReason.RESET
                && plugin.getConfig().getBoolean("final-stage.kill-character", true)) {
            markCharacterDead(data);
        }
        plugin.jinchuriki().save(data);

        if (player.isOnline()) {
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.CLOUD,
                    loc.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
            player.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
            if (reason == StopReason.UNION_END) {
                player.showTitle(Title.title(
                        Component.text("Tu relâches le pouvoir", NamedTextColor.GOLD),
                        Component.text("Le chakra du démon se retire, apaisé.",
                                NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(300),
                                Duration.ofSeconds(3), Duration.ofSeconds(1))));
            } else if (reason == StopReason.SEALED) {
                player.showTitle(Title.title(
                        Component.text("Scellé", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Le sceau arrache le chakra du démon…",
                                NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300),
                                Duration.ofSeconds(3), Duration.ofSeconds(1))));
            } else if (reason == StopReason.RESIST_SUCCESS) {
                player.showTitle(Title.title(
                        Component.text("Tu reprends le contrôle", NamedTextColor.AQUA),
                        Component.text("Le chakra du démon reflue…", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300),
                                Duration.ofSeconds(3), Duration.ofSeconds(1))));
            } else if (reason == StopReason.GM || reason == StopReason.KO) {
                player.showTitle(Title.title(
                        Component.text("Le chakra reflue", NamedTextColor.GRAY),
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(200),
                                Duration.ofSeconds(2), Duration.ofMillis(600))));
            }
        }
    }

    public void onDamageDealt(Player player, double damage) {
        ActiveTransformation t = active.get(player.getUniqueId());
        if (t == null || t.union()) return;
        t.data().addRage((damage / 2.0)
                * plugin.getConfig().getDouble("rage.damage-dealt-per-heart", 0.8)
                * harmonyFactor(t.data()));
    }

    public void onDamageTaken(Player player, double damage) {
        ActiveTransformation t = active.get(player.getUniqueId());
        if (t == null || t.union()) return;
        t.data().addRage((damage / 2.0)
                * plugin.getConfig().getDouble("rage.damage-taken-per-heart", 1.2)
                * harmonyFactor(t.data()));
    }

    private void tickLogic() {
        if (active.isEmpty()) return;
        ConfigurationSection rageCfg =
                plugin.getConfig().getConfigurationSection("rage");
        for (ActiveTransformation t : active.values()) {
            Player p = Bukkit.getPlayer(t.playerId());
            if (p == null || !p.isOnline()) continue;
            if (plugin.innerWorld().inSession(p.getUniqueId())) continue;

            JinchurikiData data = t.data();
            BeastStage st = t.beast().stage(t.stage());

            // Health / chakra flows belong to the stage itself — they keep
            // running even while a GM pauses the rage engine.
            if (st.healthRegenPercent() > 0 && p.getHealth() > 0) {
                double max = p.getMaxHealth();
                p.setHealth(Math.min(max,
                        p.getHealth() + max * st.healthRegenPercent() / 100.0));
            }
            if (plugin.resources() != null && st.chakraRegenPercent() > 0) {
                var pool = plugin.resources().pool(p);
                if (pool.max() > 0) {
                    pool.regen(pool.max() * st.chakraRegenPercent() / 100.0);
                }
            }

            updateBossBar(p, t);
            // Union pairs feel no rage at all; pause is the GM freeze.
            if (t.paused() || t.union()) continue;

            double base = rageCfg != null ? rageCfg.getDouble("base-per-second", 0.08) : 0.08;
            double perPow = rageCfg != null ? rageCfg.getDouble("per-power", 0.04) : 0.04;
            data.addRage((base + perPow * st.power())
                    * t.beast().rageMultiplier()
                    * harmonyFactor(data)
                    * timeAcceleration(rageCfg, t.secondsInStage()));

            double perMin = plugin.getConfig()
                    .getDouble("inner-world.mastery-gain.per-minute-transformed", 0.2);
            if (perMin > 0 && data.masteryTargetStage(t.beast().tails()) == t.stage()) {
                data.addMastery(t.stage(), perMin / 60.0);
            }

            tickCorruption(p, t);
            tickWhisper(p, t);

            long now = System.currentTimeMillis();
            if (!t.finalRelease()
                    && (data.rage() >= 100.0 || now >= t.nextWindowAtMillis())) {
                armControlWindow(t);
                plugin.innerWorld().beginConfrontation(p,
                        data.rage() >= 100.0 ? "rage" : "fenêtre de contrôle");
            }
        }
    }

    /** Trust + cooperation tame the beast's fire: at 100/100 the rage
     *  engine runs at (1 − harmony-reduction) of its normal speed. */
    private double harmonyFactor(JinchurikiData data) {
        double reduction = plugin.getConfig()
                .getDouble("rage.harmony-reduction", 0.7);
        double harmony = Math.max(0.0, Math.min(1.0,
                (data.trust() + data.cooperation()) / 200.0));
        return Math.max(0.0, 1.0 - reduction * harmony);
    }

    private double timeAcceleration(ConfigurationSection cfg, long secondsInStage) {
        if (cfg == null) return 1.0;
        long after = cfg.getLong("time-acceleration-after-seconds", 60);
        long maxAt = Math.max(after + 1,
                cfg.getLong("time-acceleration-max-seconds", 300));
        double maxMult = cfg.getDouble("time-acceleration-max-multiplier", 3.0);
        if (secondsInStage <= after) return 1.0;
        double f = Math.min(1.0, (secondsInStage - after) / (double) (maxAt - after));
        return 1.0 + f * (maxMult - 1.0);
    }

    private void tickCorruption(Player p, ActiveTransformation t) {
        List<Map<?, ?>> tiers = plugin.getConfig().getMapList("corruption.tiers");
        for (Map<?, ?> tier : tiers) {
            double at = toDouble(tier.get("at"), -1);
            if (at < 0 || t.data().rage() < at || t.firedCorruptionTier() >= at) continue;
            t.setFiredCorruptionTier(at);

            Object msg = tier.get("message");
            if (msg != null) p.sendMessage(Fmt.legacy(String.valueOf(msg)));
            Object snd = tier.get("sound");
            if (snd != null) {
                try {
                    p.playSound(p.getLocation(),
                            Sound.valueOf(String.valueOf(snd).toUpperCase()), 1.0f, 0.8f);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("corruption tier: unknown sound " + snd);
                }
            }
            Object fx = tier.get("effects");
            if (fx instanceof List<?> list) {
                for (Object o : list) applyConfigEffect(p, t, String.valueOf(o));
            }
        }
    }

    private void tickWhisper(Player p, ActiveTransformation t) {
        long interval = 1000L * plugin.getConfig()
                .getLong("corruption.whisper-interval-seconds", 45);
        if (interval <= 0) return;
        // A trusted host hears its beast less often — familiarity, not rage.
        double harmony = (t.data().trust() + t.data().cooperation()) / 200.0;
        interval = (long) (interval * (1.0 + 2.0 * harmony));
        long now = System.currentTimeMillis();
        if (now - t.lastWhisperAtMillis() < interval) return;
        t.setLastWhisperAt(now + ThreadLocalRandom.current().nextLong(15_000));
        String line = t.beast().randomWhisper();
        if (line != null) Fmt.beastWhisper(plugin, p, t.beast(), line);
    }

    private void updateBossBar(Player p, ActiveTransformation t) {
        if (!plugin.getConfig().getBoolean("display.bossbar", true)) return;
        BossBar bar = t.bossBar();
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
            bar.addPlayer(p);
            t.setBossBar(bar);
        }
        if (t.union()) {
            bar.setProgress(1.0);
            bar.setColor(BarColor.YELLOW);
            bar.setTitle("§6§l" + t.beast().beastName()
                    + " §e⚭ UNION §6— contrôle total");
            return;
        }
        double rage = t.data().rage();
        bar.setProgress(Math.max(0.0, Math.min(1.0, rage / 100.0)));
        bar.setColor(rage >= 70 ? BarColor.RED
                : rage >= 35 ? BarColor.YELLOW : BarColor.BLUE);
        BeastStage st = t.beast().stage(t.stage());
        bar.setTitle("§c" + t.beast().beastName()
                + " §8— §6" + st.displayName()
                + " §8(§e" + t.stage() + "§8/§e" + t.beast().tails() + "§8) — §4Rage "
                + String.format("%.0f%%", rage));
    }

    /**
     * The cloak. Scales hard with stage power: twin counter-rotating
     * helixes, a ground pressure ring, a rising flame column, soul fire
     * past power 6 and a periodic pressure pulse near the top — golden
     * and serene in Union, where the strongest aura of all belongs to
     * the HOST. {@code aura.intensity} in config tunes density.
     */
    private void tickAura() {
        if (com.reborn.shinobicore.util.Tps.shouldDefer()) return;
        double intensity = plugin.getConfig().getDouble("aura.intensity", 1.0);
        if (intensity <= 0) return;
        long now = System.currentTimeMillis();
        for (ActiveTransformation t : active.values()) {
            Player p = Bukkit.getPlayer(t.playerId());
            if (p == null || !p.isOnline()) continue;
            BeastStage st = t.beast().stage(t.stage());
            double power = st.power();
            boolean union = t.union();
            Location base = p.getLocation();
            var world = p.getWorld();

            org.bukkit.Color color = union
                    ? unionColor() : t.beast().auraColor();
            float size = (float) Math.min(4.0, 1.5 + 0.25 * power);
            Particle.DustOptions dust = new Particle.DustOptions(color, size);

            double radius = (1.0 + 0.35 * power) * Math.min(1.5, intensity);
            int points = (int) ((14 + 3 * power) * Math.min(2.0, intensity));
            double spin = (now % 1600) / 1600.0 * Math.PI * 2;

            // Twin counter-rotating helixes — the cloak itself.
            for (int i = 0; i < points; i++) {
                double a = spin + (Math.PI * 2 / points) * i;
                double y = 0.1 + (i % 5) * 0.5;
                world.spawnParticle(Particle.DUST, base.clone()
                        .add(Math.cos(a) * radius, y, Math.sin(a) * radius),
                        1, 0, 0, 0, 0, dust);
                world.spawnParticle(Particle.DUST, base.clone()
                        .add(Math.cos(-a) * radius * 0.6, y + 0.25,
                                Math.sin(-a) * radius * 0.6),
                        1, 0, 0, 0, 0, dust);
            }
            // Ground pressure ring.
            int ground = Math.max(8, points / 2);
            for (int i = 0; i < ground; i++) {
                double a = -spin + (Math.PI * 2 / ground) * i;
                world.spawnParticle(Particle.DUST, base.clone()
                        .add(Math.cos(a) * radius * 1.5, 0.05,
                                Math.sin(a) * radius * 1.5),
                        1, 0.02, 0, 0.02, 0, dust);
            }
            // Rising column, fiercer with power.
            if (power >= 3) {
                world.spawnParticle(union ? Particle.END_ROD : Particle.FLAME,
                        base.clone().add(0, 1.1, 0),
                        (int) (2 + power / 2), 0.25, 0.8, 0.25, 0.02);
            }
            if (power >= 6 && !union) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                        base.clone().add(0, 0.6, 0),
                        (int) (power / 2), 0.35, 0.5, 0.35, 0.015);
            }
            // Union: golden sparkles — mastery, not menace.
            if (union) {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                        base.clone().add(0, 1.4, 0), 4, 0.4, 0.7, 0.4, 0.05);
            }
            // Periodic pressure pulse near the top of the curve.
            if ((union || power >= 7) && now % 2500 < 130) {
                world.spawnParticle(Particle.EXPLOSION,
                        base.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);
                world.playSound(base, union
                        ? Sound.BLOCK_BEACON_AMBIENT
                        : Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.6f);
            }
            // Rage flames on top of everything.
            if (!union && t.data().rage() >= 70) {
                world.spawnParticle(Particle.FLAME,
                        base.clone().add(0, 1.0, 0), 4, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    private org.bukkit.Color unionColor() {
        String hex = plugin.getConfig().getString("union.aura-color", "#FFD75A");
        try {
            return org.bukkit.Color.fromRGB(
                    Integer.parseInt(hex.replace("#", "").trim(), 16));
        } catch (NumberFormatException ex) {
            return org.bukkit.Color.fromRGB(0xFFD75A);
        }
    }

    private void applyStageEffects(Player p, ActiveTransformation t) {
        BeastStage st = t.beast().stage(t.stage());

        applyModifier(p, Attribute.ATTACK_DAMAGE, MOD_DAMAGE,
                st.damageMultiplier() - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyModifier(p, Attribute.MOVEMENT_SPEED, MOD_SPEED,
                st.bonusSpeedPercent() / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyModifier(p, Attribute.KNOCKBACK_RESISTANCE, MOD_KB,
                st.knockbackResistance(),
                AttributeModifier.Operation.ADD_NUMBER);
        applyModifier(p, Attribute.SCALE, MOD_SCALE,
                st.extraScale() - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);

        for (String spec : st.extraEffects()) applyConfigEffect(p, t, spec);

        if (st.instantHealPercent() > 0) {
            double max = p.getMaxHealth();
            p.setHealth(Math.min(max,
                    p.getHealth() + max * st.instantHealPercent() / 100.0));
        }
        if (st.instantChakraPercent() > 0 && plugin.resources() != null) {
            var pool = plugin.resources().pool(p);
            if (pool.max() > 0) {
                pool.regen(pool.max() * st.instantChakraPercent() / 100.0);
            }
        }
    }

    private void clearStageEffects(Player p, ActiveTransformation t) {
        clearModifier(p, Attribute.ATTACK_DAMAGE, MOD_DAMAGE);
        clearModifier(p, Attribute.MOVEMENT_SPEED, MOD_SPEED);
        clearModifier(p, Attribute.KNOCKBACK_RESISTANCE, MOD_KB);
        clearModifier(p, Attribute.SCALE, MOD_SCALE);
        for (PotionEffectType type : t.appliedEffects()) {
            p.removePotionEffect(type);
        }
        t.appliedEffects().clear();
    }

    private void applyConfigEffect(Player p, ActiveTransformation t, String spec) {
        try {
            String[] parts = spec.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].trim());
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect: " + spec);
                return;
            }
            int amp = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            p.addPotionEffect(new PotionEffect(type,
                    PotionEffect.INFINITE_DURATION, amp, true, false, true));
            if (!t.appliedEffects().contains(type)) t.appliedEffects().add(type);
        } catch (Exception ex) {
            plugin.getLogger().warning("Bad effect spec '" + spec + "': " + ex.getMessage());
        }
    }

    private void applyModifier(Player p, Attribute attribute, String name,
                               double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null || amount == 0.0) return;
        clearModifier(p, attribute, name);
        inst.addTransientModifier(new AttributeModifier(
                new NamespacedKey(plugin, name), amount, op));
    }

    public void clearModifier(Player p, Attribute attribute, String name) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, name);
        for (AttributeModifier mod : List.copyOf(inst.getModifiers())) {
            if (key.equals(mod.getKey())) inst.removeModifier(mod);
        }
    }

    public void clearAllModifiers(Player p) {
        clearModifier(p, Attribute.ATTACK_DAMAGE, MOD_DAMAGE);
        clearModifier(p, Attribute.MOVEMENT_SPEED, MOD_SPEED);
        clearModifier(p, Attribute.KNOCKBACK_RESISTANCE, MOD_KB);
        clearModifier(p, Attribute.SCALE, MOD_SCALE);
    }

    /** Progression bookkeeping + the final-stage point of no return. */
    private void noteStageReached(Player p, ActiveTransformation t) {
        JinchurikiData data = t.data();
        if (t.stage() > data.highestStageReached()) {
            data.setHighestStageReached(t.stage());
        }
        boolean isFinal = t.stage() >= t.beast().tails();
        if (isFinal && !t.union() && !t.finalRelease()
                && plugin.getConfig().getBoolean("final-stage.point-of-no-return", true)) {
            t.setFinalRelease(true);
            data.setFinalReleaseReached(true);
            var resolved = plugin.jinchuriki().resolveById(data.characterId());
            String charName = resolved != null
                    ? resolved.character().name() : p.getName();
            String raw = plugin.getConfig().getString("final-stage.broadcast",
                    "&4&l{character} est englouti par {beast} — libération totale !");
            Bukkit.broadcast(Fmt.legacy(raw
                    .replace("{character}", charName)
                    .replace("{beast}", t.beast().beastName())));
            p.getWorld().playSound(p.getLocation(),
                    Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 0.5f);
        }
    }

    /** The host is gone: rule the character dead (ShinobiCore staff
     *  tools remain the only resurrection path). */
    private void markCharacterDead(JinchurikiData data) {
        var resolved = plugin.jinchuriki().resolveById(data.characterId());
        if (resolved == null || plugin.characters() == null) return;
        resolved.character().setDead(true);
        plugin.characters().save(resolved.character());
    }

    private void armControlWindow(ActiveTransformation t) {
        long interval = plugin.getConfig().getLong("control-window.interval-seconds", 180);
        long jitter = plugin.getConfig().getLong("control-window.jitter-seconds", 60);
        long delta = interval + (jitter > 0
                ? ThreadLocalRandom.current().nextLong(-jitter, jitter + 1) : 0);
        t.setNextWindowAt(System.currentTimeMillis() + Math.max(20, delta) * 1000L);
    }

    private void announceEntry(Player p, ActiveTransformation t,
                               ActiveTransformation.Cause cause) {
        BeastDefinition beast = t.beast();
        BeastStage st = beast.stage(t.stage());
        Location loc = p.getLocation();
        p.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
        p.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.6f);
        p.getWorld().spawnParticle(Particle.EXPLOSION,
                loc.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.0);

        String sub = switch (cause) {
            case HELP -> "Le démon te prête sa puissance…";
            case TAKEOVER -> "Le démon force le sceau !";
            case KO_SAVE -> "Le démon refuse de te laisser tomber ici.";
            case GM, ESCALATION -> st.displayName();
        };
        p.showTitle(Title.title(
                Component.text(beast.beastName(), NamedTextColor.DARK_RED),
                Component.text(sub, NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(300),
                        Duration.ofSeconds(3), Duration.ofMillis(800))));
        String line = switch (cause) {
            case HELP -> "Relève-toi. Je ne tombe pas avec les faibles.";
            case TAKEOVER -> "À MOI ce corps !";
            case KO_SAVE -> "Pas encore. Tu ne meurs pas tant que JE vis ici.";
            default -> null;
        };
        if (line != null) Fmt.beastWhisper(plugin, p, beast, line);
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o != null ? Double.parseDouble(String.valueOf(o)) : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
