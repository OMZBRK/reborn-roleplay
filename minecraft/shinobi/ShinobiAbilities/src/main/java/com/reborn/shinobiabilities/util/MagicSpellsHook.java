package com.reborn.shinobiabilities.util;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reflection-only bridge to MagicSpells — no compile-time dependency,
 * so the module builds (and the plugin runs) with or without MS on the
 * server.
 *
 * <p>Reads the loaded spell list through
 * {@code com.nisovin.magicspells.MagicSpells.spells()} (with
 * {@code getSpells()} as fallback) and each spell's
 * {@code getInternalName()} / {@code getName()}.
 */
public final class MagicSpellsHook {

    /** One loaded MagicSpells spell. */
    public record SpellInfo(String internalName, String displayName) {}

    private MagicSpellsHook() {}

    public static boolean isAvailable() {
        var pl = Bukkit.getPluginManager().getPlugin("MagicSpells");
        return pl != null && pl.isEnabled();
    }

    /** Every loaded spell, or empty when MS is absent / API mismatch. */
    public static List<SpellInfo> listSpells() {
        List<SpellInfo> out = new ArrayList<>();
        if (!isAvailable()) return out;
        try {
            Class<?> ms = Class.forName("com.nisovin.magicspells.MagicSpells");
            Object spells = invokeStatic(ms, "spells");
            if (spells == null) spells = invokeStatic(ms, "getSpells");
            if (!(spells instanceof Collection<?> col)) return out;
            for (Object spell : col) {
                String internal = callString(spell, "getInternalName");
                if (internal == null || internal.isBlank()) continue;
                String display = callString(spell, "getName");
                out.add(new SpellInfo(internal.trim(),
                        display == null || display.isBlank()
                                ? internal.trim() : stripColors(display.trim())));
            }
        } catch (Throwable ignore) {
            // Incompatible MS version — caller reports the empty list.
        }
        return out;
    }

    private static Object invokeStatic(Class<?> cls, String method) {
        try { return cls.getMethod(method).invoke(null); }
        catch (Throwable t) { return null; }
    }

    private static String callString(Object target, String method) {
        try {
            Object v = target.getClass().getMethod(method).invoke(target);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Drop §-style colour codes from MS display names. */
    private static String stripColors(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') { i++; continue; }   // skip the code char too
            out.append(c);
        }
        return out.toString();
    }
}
