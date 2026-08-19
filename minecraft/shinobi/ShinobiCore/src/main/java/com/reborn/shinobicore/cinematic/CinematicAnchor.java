package com.reborn.shinobicore.cinematic;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * One viewpoint in a {@link Cinematic}: a fixed camera location + locked
 * view direction, an optional on-screen title, up to three optional chat
 * lines (each with its own colour), and how long to hold before advancing.
 *
 * <p>The location is stored decomposed (world name + coords + yaw/pitch) so a
 * missing or unloaded world degrades gracefully instead of throwing. Durations
 * are kept in ticks internally; the UI works in seconds (×20).
 *
 * <p>{@link #sound()} is reserved for future voice-over and ignored for now.
 */
public final class CinematicAnchor {

    private String worldName;
    private double x, y, z;
    private float yaw, pitch;

    private String title;                            // nullable -> no title
    private String text1, text2, text3;              // nullable -> nothing
    private NamedTextColor titleColor;               // nullable -> GOLD
    private NamedTextColor color1, color2, color3;   // nullable -> default
    private int durationTicks = 180;                 // default 9 s (fits the 4 s title delay + staggered texts)
    private String sound;                            // reserved (nullable)

    public CinematicAnchor() {}

    /* ------------------------------------------------------------- camera */

    /** Capture a camera viewpoint from a live location (incl. yaw/pitch). */
    public void setCamera(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.worldName = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    /** Resolve to a Bukkit {@link Location}, or {@code null} when the world
     *  isn't loaded (the engine skips such anchors). */
    public Location toLocation() {
        if (worldName == null) return null;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public boolean hasCamera() { return worldName != null; }

    /* ---------------------------------------------------------- accessors */

    public String worldName() { return worldName; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public void setWorldName(String w) { this.worldName = w; }
    public void setX(double v) { this.x = v; }
    public void setY(double v) { this.y = v; }
    public void setZ(double v) { this.z = v; }
    public void setYaw(float v) { this.yaw = v; }
    public void setPitch(float v) { this.pitch = v; }

    public String title() { return title; }
    public void setTitle(String t) { this.title = blankToNull(t); }
    public String text1() { return text1; }
    public void setText1(String t) { this.text1 = blankToNull(t); }
    public String text2() { return text2; }
    public void setText2(String t) { this.text2 = blankToNull(t); }
    public String text3() { return text3; }
    public void setText3(String t) { this.text3 = blankToNull(t); }

    public NamedTextColor titleColor() { return titleColor; }
    public void setTitleColor(NamedTextColor c) { this.titleColor = c; }
    public NamedTextColor color1() { return color1; }
    public void setColor1(NamedTextColor c) { this.color1 = c; }
    public NamedTextColor color2() { return color2; }
    public void setColor2(NamedTextColor c) { this.color2 = c; }
    public NamedTextColor color3() { return color3; }
    public void setColor3(NamedTextColor c) { this.color3 = c; }

    public int durationTicks() { return durationTicks; }
    public void setDurationTicks(int t) { this.durationTicks = Math.max(1, t); }
    public int durationSeconds() { return Math.max(1, Math.round(durationTicks / 20f)); }
    public void setDurationSeconds(int s) { this.durationTicks = Math.max(1, s) * 20; }

    public String sound() { return sound; }
    public void setSound(String s) { this.sound = blankToNull(s); }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
