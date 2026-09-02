package com.reborn.shinobicore.lore;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * In-universe ("lore") calendar for the Naruto RP server.
 *
 * <p>The lore date is independent of real-world time. It starts at the
 * values configured in {@code config.yml} under {@code lore.calendar.*}
 * and — if {@code auto-advance} is enabled — ticks forward exactly one
 * lore-day every {@code real-minutes-per-day} real-world minutes.
 *
 * <p>Months are 1-indexed and wrap every 12; days wrap every
 * {@code days-per-month} (default 30). All changes are persisted
 * back into {@code config.yml} so the date survives restarts.
 *
 * <p>Thread-safety: all mutation happens on the main scheduler thread.
 */
public class LoreCalendar {

    private final ShinobiCore plugin;

    private int year;
    private int month;   // 1..12
    private int day;     // 1..daysPerMonth
    private int monthsPerYear;
    private int daysPerMonth;

    private boolean autoAdvance;
    private long realMinutesPerDay;
    private BukkitTask ticker;

    public LoreCalendar(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadConfig();
    }

    public void reloadConfig() {
        this.year          = plugin.getConfig().getInt("lore.calendar.year", 495);
        this.monthsPerYear = Math.max(1, plugin.getConfig().getInt("lore.calendar.months-per-year", 12));
        this.daysPerMonth  = Math.max(1, plugin.getConfig().getInt("lore.calendar.days-per-month", 30));
        this.month = clamp(plugin.getConfig().getInt("lore.calendar.month", 6), 1, monthsPerYear);
        this.day   = clamp(plugin.getConfig().getInt("lore.calendar.day",   25), 1, daysPerMonth);

        this.autoAdvance        = plugin.getConfig().getBoolean("lore.calendar.auto-advance", false);
        this.realMinutesPerDay  = Math.max(1L, plugin.getConfig().getLong("lore.calendar.real-minutes-per-day", 120L));

        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (autoAdvance) {
            long periodTicks = realMinutesPerDay * 60L * 20L;
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::advanceOneDay, periodTicks, periodTicks);
        }
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        save();
    }

    /* --------------------------------------------------------------- read */

    public int year()  { return year; }
    public int month() { return month; }
    public int day()   { return day; }

    /** e.g. "An 495 — Mois 6, Jour 25". */
    public String formatted() {
        return "An " + year + " \u2014 Mois " + month + ", Jour " + day;
    }

    /** e.g. "495-06-25" — compact form for the tab list. */
    public String compact() {
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    /* -------------------------------------------------------------- mutate */

    /** Advance the lore calendar by one day, handling month/year rollover. */
    public void advanceOneDay() {
        day++;
        if (day > daysPerMonth) {
            day = 1;
            month++;
            if (month > monthsPerYear) {
                month = 1;
                year++;
            }
        }
        save();
    }

    public void setDate(int year, int month, int day) {
        this.year  = year;
        this.month = clamp(month, 1, monthsPerYear);
        this.day   = clamp(day,   1, daysPerMonth);
        save();
    }

    /* -------------------------------------------------------------- helpers */

    private void save() {
        plugin.getConfig().set("lore.calendar.year",  year);
        plugin.getConfig().set("lore.calendar.month", month);
        plugin.getConfig().set("lore.calendar.day",   day);
        plugin.saveConfig();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
