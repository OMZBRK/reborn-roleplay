package com.reborn.shinobicore.character;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Canonical list of the Naruto clans supported by this server.
 *
 * <p>A character's clan is still stored as a plain {@code String} on
 * {@link ShinobiCharacter#clan()} — this enum is only used as a presentation
 * layer. The clan-picker GUI lets players choose one of the canonical clans
 * from a list (so typo-fake clans like "Uchihaa" can't drift in), plus a
 * fall-through "Other..." option for custom clans that stays a raw String.
 *
 * <p>Each canonical clan carries:
 * <ul>
 *   <li>a display name (used everywhere the clan is rendered),</li>
 *   <li>an Adventure colour (used for the chat / tab / /me nickname tint),</li>
 *   <li>a Bukkit {@link Material} icon (used by the picker GUI).</li>
 * </ul>
 *
 * <p>{@link #forName(String)} accepts the stored clan String and returns the
 * matching enum value (case-insensitive, trims whitespace) or {@code null}
 * for non-canonical / custom clans. Callers that need a colour should fall
 * back to {@link NamedTextColor#WHITE} for a null return.
 */
public enum Clan {

    SENJU    ("Senju",     NamedTextColor.DARK_GREEN,  Material.OAK_SAPLING,
              "Wielders of the Wood Release. Hashirama's lineage."),
    UCHIHA   ("Uchiha",    NamedTextColor.RED,         Material.BLAZE_POWDER,
              "Keepers of the Sharingan and Fire Release."),
    HYUGA    ("Hyuga",     NamedTextColor.WHITE,       Material.ENDER_EYE,
              "Masters of the Byakugan and Gentle Fist."),
    NARA     ("Nara",      NamedTextColor.DARK_GRAY,   Material.COAL,
              "Shadow manipulation and tactical brilliance."),
    SARUTOBI ("Sarutobi",  NamedTextColor.GOLD,        Material.GOLDEN_HELMET,
              "Lineage of the Third Hokage. Guardians of the Leaf."),
    UZUMAKI  ("Uzumaki",   NamedTextColor.LIGHT_PURPLE, Material.IRON_CHAIN,
              "Sealing masters. Vast chakra reserves, crimson hair."),
    ABURAME  ("Aburame",   NamedTextColor.DARK_PURPLE, Material.SPIDER_EYE,
              "Hosts of the kikaichu — the destruction bugs."),
    AKIMICHI ("Akimichi",  NamedTextColor.YELLOW,      Material.BAKED_POTATO,
              "Butterfly chakra mode — size begets strength."),
    HATAKE   ("Hatake",    NamedTextColor.GRAY,        Material.IRON_SWORD,
              "The White Fangs — silver-haired copy-ninja line."),
    YAMANAKA ("Yamanaka",  NamedTextColor.AQUA,        Material.POPPY,
              "Mind-transfer jutsu. The flower shop of Konoha."),
    KURAMA   ("Kurama",    NamedTextColor.DARK_RED,    Material.RED_DYE,
              "Genjutsu clan with a shared tailed-fox bond.");

    private final String displayName;
    private final NamedTextColor colour;
    private final Material icon;
    private final String flavour;

    Clan(String displayName, NamedTextColor colour, Material icon, String flavour) {
        this.displayName = displayName;
        this.colour = colour;
        this.icon = icon;
        this.flavour = flavour;
    }

    public String displayName()      { return displayName; }
    public NamedTextColor colour()   { return colour; }
    public Material icon()           { return icon; }
    public String flavour()          { return flavour; }

    /**
     * Look up the canonical clan for a stored String. Returns {@code null}
     * for blank, null, or custom clan names — callers should treat that as
     * "draw with the neutral WHITE colour".
     */
    public static Clan forName(String raw) {
        if (raw == null) return null;
        String needle = raw.trim();
        if (needle.isEmpty()) return null;
        for (Clan c : values()) {
            if (c.displayName.equalsIgnoreCase(needle)) return c;
        }
        return null;
    }

    /**
     * Colour for rendering a clan string — canonical clans get their themed
     * colour; custom / blank clans fall back to WHITE so the nickname still
     * reads cleanly.
     */
    public static NamedTextColor colourFor(String raw) {
        Clan c = forName(raw);
        return c == null ? NamedTextColor.WHITE : c.colour;
    }
}
