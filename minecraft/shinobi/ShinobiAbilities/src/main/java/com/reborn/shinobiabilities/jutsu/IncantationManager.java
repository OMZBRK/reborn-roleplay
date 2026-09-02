package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobicore.technique.Ability;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The pre-fire incantation phase for picker casts.
 *
 * <ul>
 *   <li>Step count = explicit mudra list size, else rank fallback
 *       (E/D 2, C/B 4, A/HIDEN 6).</li>
 *   <li>Cadence: one step every {@code jutsu.incantation.step-ticks}
 *       (default 5 ticks = 0.25 s); sounds at 0 s … (N-1)·0.25 s.</li>
 *   <li>The fire callback runs at (N-1)·0.25 s, simultaneous with the
 *       last sound.</li>
 *   <li>Per-type sound, pitch ramping 1.0 → 1.4 across the steps.</li>
 *   <li>Blue PROGRESS boss bar; mudra-based casts show
 *       {@code <Mudra>  ·  <Jutsu>} (gold/aqua), others just the name.</li>
 * </ul>
 *
 * <p>One incantation per player. The <b>gate runs before us</b> — never
 * start an incantation for a cast that can't pay (see
 * {@code JutsuExecutionManager.gateOrWarn}).
 */
public final class IncantationManager {

    private record Cast(BukkitTask task, BossBar bar, String abilityId) {}

    private final JavaPlugin plugin;
    private final Map<UUID, Cast> active = new HashMap<>();

    public IncantationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isCasting(Player p) {
        return p != null && active.containsKey(p.getUniqueId());
    }

    /**
     * Start the incantation; {@code onFire} runs on the last step.
     * Any in-flight incantation for this player is cancelled first.
     *
     * <p>Step labels come from {@link Ability#incantationLabels()} —
     * mudra names for ninjutsu, free-form movements for the other arts,
     * or a plain-name display when neither is declared.
     */
    public void start(Player p, JutsuItemType type, Ability ability, Runnable onFire) {
        cancel(p);

        final int steps = Math.max(1, ability.mudraCount());
        final List<String> labels = ability.incantationLabels();
        final long stepTicks = Math.max(1L,
                plugin.getConfig().getLong("jutsu.incantation.step-ticks", 5L));

        final BossBar bar = BossBar.bossBar(
                title(ability, labels, 0), 0f,
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);

        final UUID id = p.getUniqueId();
        final int[] step = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { cancelById(id); return; }
            int i = step[0];

            float pitch = steps == 1 ? 1.2f : 1.0f + 0.4f * i / (float) (steps - 1);
            p.playSound(p.getLocation(), type.incantationSound(), 0.7f, pitch);
            bar.name(title(ability, labels, i));
            bar.progress(Math.min(1f, (i + 1) / (float) steps));

            if (i >= steps - 1) {
                // Last step: fire simultaneously with the final sound,
                // then tear down. Remove BEFORE running the callback so
                // an effect that re-opens UI can't see a ghost cast.
                Cast c = active.remove(id);
                if (c != null) c.task().cancel();
                p.hideBossBar(bar);
                onFire.run();
                return;
            }
            step[0] = i + 1;
        }, 0L, stepTicks);

        active.put(id, new Cast(task, bar, ability.id()));
    }

    private Component title(Ability ability, List<String> labels, int step) {
        Component name = Component.text(ability.name(), NamedTextColor.AQUA);
        if (labels == null || labels.isEmpty()) return name;
        String label = labels.get(Math.min(step, labels.size() - 1));
        return Component.text(label, NamedTextColor.GOLD)
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(name);
    }

    /** Cancel the in-flight incantation, if any. Safe on null/offline. */
    public void cancel(Player p) {
        if (p == null) return;
        Cast c = active.remove(p.getUniqueId());
        if (c == null) return;
        c.task().cancel();
        p.hideBossBar(c.bar());
    }

    private void cancelById(UUID id) {
        Cast c = active.remove(id);
        if (c != null) c.task().cancel();
        Player p = plugin.getServer().getPlayer(id);
        if (p != null && c != null) p.hideBossBar(c.bar());
    }

    /** Cancel everything (plugin disable). */
    public void cancelAll() {
        for (UUID id : active.keySet().toArray(new UUID[0])) cancelById(id);
    }
}
