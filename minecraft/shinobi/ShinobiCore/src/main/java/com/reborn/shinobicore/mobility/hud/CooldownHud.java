package com.reborn.shinobicore.mobility.hud;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-player sidebar scoreboard. After the jutsu/abilities cleanup
 * this is now <b>section-pluggable</b>: any plugin can call
 * {@link #registerSection(HudSection)} to push its own header + row
 * list into every viewer's HUD.
 *
 * <h2>What ships out of the box</h2>
 * Just the title and the skeleton — no built-in sections. The
 * ShinobiAbilities plugin (when it loads) registers its own
 * <b>Passifs</b> / <b>Cooldowns</b> / <b>Jutsu</b> sections via
 * the API and the rendered scoreboard fills out from there.
 *
 * <h2>Rendering</h2>
 * Same ticker / skip-when-idle / TPS-aware behaviour as before:
 * <ul>
 *   <li>Re-renders every {@code display.cooldown-hud.refresh-ticks}
 *       ticks (default 5 = 0.25 s).</li>
 *   <li>Skips rebuild when the line list is bit-identical to the
 *       last frame.</li>
 *   <li>Defers via {@link Tps#shouldDefer()} when the server is
 *       lagging.</li>
 * </ul>
 *
 * <h2>Uniqueness trick</h2>
 * Scoreboard entry strings MUST be unique per line. Each row is
 * prefixed with a pair of invisible colour codes ({@code §f§<i>}) so
 * the renderer sees distinct strings even when the human-readable
 * tail collides between sections.
 */
public class CooldownHud implements com.reborn.shinobicore.api.HudService {

    /** Plugin-registered section. Implementations live in the consumer
     *  plugin (ShinobiAbilities, or any future addon). */
    public interface HudSection {
        /** Header label rendered above the section's rows. */
        String label();
        /** Colour of the header label. Default light gray. */
        default NamedTextColor color() { return NamedTextColor.GRAY; }
        /** The rows to render for {@code viewer} this tick. Return
         *  empty list to skip the section entirely (header hidden too).
         *  Called every render — cache internally if needed. */
        List<HudRow> rows(Player viewer);
    }

    /** A single row in a HUD section. {@code value} renders in
     *  {@code valueColor}; {@code label} renders in white. */
    public record HudRow(String label, String value, NamedTextColor valueColor) {}

    private final ShinobiCore plugin;

    /** Per-player owned scoreboard so we never clobber other plugins. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Per-player snapshot of the last rendered line list. Skip the
     *  whole tear-down / rebuild when the new line list would be
     *  identical. */
    private final Map<UUID, List<String>> lastRender = new HashMap<>();
    /** Registered sections, ordered by insertion. CopyOnWriteArrayList
     *  so plugins can register mid-tick without ConcurrentModification. */
    private final List<HudSection> sections = new CopyOnWriteArrayList<>();

    private BukkitTask ticker;
    private boolean enabled;
    private String titleRaw;

    public CooldownHud(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadConfig();
    }

    public void reloadConfig() {
        this.enabled = plugin.getConfig()
                .getBoolean("display.cooldown-hud.enabled", true);
        this.titleRaw = plugin.getConfig()
                .getString("display.cooldown-hud.title", "&6Tableau Chakraique");
        long refresh = Math.max(1L, plugin.getConfig()
                .getLong("display.cooldown-hud.refresh-ticks", 5L));

        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (!enabled) {
            clearAll();
            return;
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, refresh);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        clearAll();
    }

    public void remove(UUID id) {
        boards.remove(id);
        lastRender.remove(id);
        Player p = plugin.getServer().getPlayer(id);
        if (p != null && p.isOnline()) {
            p.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
        }
    }

    /* -------------------------------------------------- section API */

    /** Register a HUD section. Renders on next tick. Call from another
     *  plugin's onEnable. */
    public void registerSection(HudSection section) {
        if (section == null) return;
        sections.add(section);
    }

    /** Unregister a previously-registered section. Call on plugin disable. */
    public void unregisterSection(HudSection section) {
        if (section == null) return;
        sections.remove(section);
    }

    /* --------------------------------------------------------------- tick */

    private void tick() {
        if (Tps.shouldDefer()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID pid = p.getUniqueId();

            // Build the line list by walking every registered section.
            // An empty section's header is omitted entirely so plugins
            // can self-hide their group simply by returning rows().isEmpty().
            List<String> lines = new ArrayList<>();
            for (HudSection section : sections) {
                List<HudRow> rows = section.rows(p);
                if (rows == null || rows.isEmpty()) continue;
                lines.add(header(section.label(), section.color()));
                for (HudRow row : rows) {
                    lines.add(formatRow(row));
                }
                lines.add(spacer(lines.size()));
            }

            // Optimization: skip rebuild if line list is identical to
            // the last rendered frame. Idle players cost ~1 map lookup
            // per HUD tick.
            List<String> previous = lastRender.get(pid);
            if (lines.equals(previous)) continue;
            lastRender.put(pid, lines);

            Scoreboard sb = boards.computeIfAbsent(pid, k -> buildBoardFor(p));
            Objective obj = sb.getObjective("shinobi_cd");
            if (obj == null) continue;

            for (String entry : sb.getEntries()) sb.resetScores(entry);

            int score = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                String unique = "§f§" + hex(i) + lines.get(i);
                if (unique.length() > 40) unique = unique.substring(0, 40);
                obj.getScore(unique).setScore(score--);
            }

            p.setScoreboard(sb);
        }
    }

    /* ------------------------------------------------------------- formatting */

    /** {@code "Name: 5.3s"} — label white, value in the row's colour. */
    private static String formatRow(HudRow row) {
        ChatColor valueColor = legacy(row.valueColor());
        return ChatColor.WHITE + row.label() + ": " + valueColor + row.value();
    }

    /** {@code "— Section —"} — header banner in the section's colour. */
    private static String header(String text, NamedTextColor color) {
        return legacy(color).toString() + ChatColor.ITALIC + "— " + text + " —";
    }

    /** Blank-looking row used to separate sections. Uses {@code i+1}
     *  spaces so each spacer is bit-distinct (the uniqueness-prefix
     *  alone isn't enough when multiple spacers share an index hex). */
    private static String spacer(int seed) {
        return ChatColor.RESET.toString() + " ".repeat(Math.max(1, (seed % 4) + 1));
    }

    /** 0-15 → '0'-'f'. Lets us uniqueness-prefix up to 16 lines. */
    private static char hex(int i) {
        return "0123456789abcdef".charAt(Math.max(0, Math.min(15, i)));
    }

    private static ChatColor legacy(NamedTextColor c) {
        if (c == null) return ChatColor.GRAY;
        if (c == NamedTextColor.AQUA)         return ChatColor.AQUA;
        if (c == NamedTextColor.BLUE)         return ChatColor.BLUE;
        if (c == NamedTextColor.GOLD)         return ChatColor.GOLD;
        if (c == NamedTextColor.GREEN)        return ChatColor.GREEN;
        if (c == NamedTextColor.LIGHT_PURPLE) return ChatColor.LIGHT_PURPLE;
        if (c == NamedTextColor.RED)          return ChatColor.RED;
        if (c == NamedTextColor.WHITE)        return ChatColor.WHITE;
        if (c == NamedTextColor.YELLOW)       return ChatColor.YELLOW;
        if (c == NamedTextColor.DARK_GRAY)    return ChatColor.DARK_GRAY;
        return ChatColor.GRAY;
    }

    /* ------------------------------------------------------- scoreboard */

    private Scoreboard buildBoardFor(Player p) {
        Scoreboard sb = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Component titleCmp = LegacyComponentSerializer.legacyAmpersand().deserialize(titleRaw);
        Objective obj = sb.registerNewObjective("shinobi_cd", Criteria.DUMMY, titleCmp);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        return sb;
    }

    private void clearAll() {
        Scoreboard main = plugin.getServer().getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(p.getUniqueId())) {
                p.setScoreboard(main);
            }
        }
        boards.clear();
        lastRender.clear();
    }
}
