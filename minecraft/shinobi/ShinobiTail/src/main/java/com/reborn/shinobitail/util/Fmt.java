package com.reborn.shinobitail.util;

import com.reborn.shinobitail.beast.BeastDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Small text helpers: legacy '&amp;' codes from config → Components,
 * the beast-styled message prefix, and unicode progress bars.
 */
public final class Fmt {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Fmt() { }

    /** Parse a config string with '&amp;' colour codes. */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /** "【Kyūbi】 message" — the voice of the beast, GM or automated. */
    public static Component beastSpeak(Plugin plugin, BeastDefinition beast,
                                       String message) {
        String prefix = plugin.getConfig().getString(
                        "messages.beast-prefix", "&8【&c{beast}&8】&7 ")
                .replace("{beast}", beast.beastName());
        return LEGACY.deserialize(prefix + message);
    }

    public static void beastWhisper(Plugin plugin, Player to,
                                    BeastDefinition beast, String message) {
        if (message == null || message.isBlank()) return;
        to.sendMessage(beastSpeak(plugin, beast, message));
    }

    /** ▮▮▮▮▮▯▯▯▯▯ style bar, value in 0..100. */
    public static Component bar(double percent, int width, TextColor filled) {
        int on = (int) Math.round(Math.max(0, Math.min(100, percent)) / 100.0 * width);
        Component c = Component.empty();
        for (int i = 0; i < width; i++) {
            c = c.append(Component.text("▮")
                    .color(i < on ? filled : NamedTextColor.DARK_GRAY));
        }
        return c;
    }

    public static String pct(double v) {
        return String.format("%.0f%%", v);
    }

    public static String pct1(double v) {
        return String.format("%.1f%%", v);
    }
}
