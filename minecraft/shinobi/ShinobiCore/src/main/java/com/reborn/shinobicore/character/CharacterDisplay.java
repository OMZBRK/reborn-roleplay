package com.reborn.shinobicore.character;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Builds and applies the styled "character nickname" used across chat,
 * tab list, and the overhead nameplate.
 *
 * <h2>Viewer-aware rendering</h2>
 * The server is an RP environment: by default a character is a
 * <em>stranger</em> to everyone else — their nickname in chat / /me
 * appears as "????". Once two characters {@code /rencontrer} each other,
 * the viewer's contact book stores how they know the subject (real
 * "Name Clan" or a nickname), and that's what they see from then on.
 *
 * <p>Resolution rules (in order):
 * <ol>
 *   <li>Subject is {@code null} → empty component (defensive fallback).</li>
 *   <li>Viewer is {@code null} (console, staff, observer) → show real name.</li>
 *   <li>Viewer IS the subject → show real name (you know yourself).</li>
 *   <li>Viewer's contact book lists the subject → render the stored
 *       display text; clan colour if it's the real name, neutral gray
 *       if it's a nickname (so the viewer can't reverse-engineer the
 *       subject's clan from a nickname they invented).</li>
 *   <li>Otherwise → {@code ????} in gray.</li>
 * </ol>
 *
 * <p>The "real name" form is always "GivenName ClanName", same as
 * {@link #styledName(ShinobiCharacter)} — that keeps the chat log and
 * the /me line visually identical whether you've met them or not.
 */
public final class CharacterDisplay {

    /** Placeholder shown when the viewer has never been introduced to the subject. */
    public static final String UNKNOWN_LABEL = "????";

    private CharacterDisplay() {}

    /* ---------------------------------------------------- "real name" build */

    /**
     * Build the display {@link Component} for a character — Given-name +
     * space + Clan-name, both bold, both tinted with the clan's themed
     * colour (custom / unset clans fall back to WHITE). Italics are
     * explicitly disabled so Minecraft doesn't slip its default italic
     * styling in when the component is used as a display-name.
     */
    public static Component styledName(ShinobiCharacter c) {
        if (c == null) return Component.empty();
        NamedTextColor colour = Clan.colourFor(c.clan());
        String name = c.name() == null ? "" : c.name();
        String clan = c.clan() == null || c.clan().isBlank() ? "" : c.clan();

        Component out = Component.text(name, colour)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        if (!clan.isEmpty()) {
            out = out.append(Component.text(" "))
                    .append(Component.text(clan, colour)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }

    /** Plain-text form of the real name — used when saving a contact
     *  entry so the stored string matches what {@link #styledName}
     *  renders on screen. */
    public static String realNameString(ShinobiCharacter c) {
        if (c == null) return "";
        String name = c.name() == null ? "" : c.name();
        String clan = c.clan() == null || c.clan().isBlank() ? "" : c.clan();
        if (clan.isEmpty()) return name;
        return name + " " + clan;
    }

    /* -------------------------------------------- viewer-aware rendering */

    /**
     * Render the name {@code subject} should appear as when shown to
     * {@code viewer}. See the class javadoc for the resolution rules.
     *
     * <p>Both params accept {@code null}: a null subject returns
     * {@link Component#empty()}; a null viewer is treated as "knows
     * everyone" (console / staff narration fallback).
     */
    public static Component styledNameFor(ShinobiCharacter subject, ShinobiCharacter viewer) {
        if (subject == null) return Component.empty();
        if (viewer == null) return styledName(subject);
        if (subject.id().equals(viewer.id())) return styledName(subject);

        ShinobiCharacter.KnownName known = viewer.knownNameOf(subject.id());
        if (known == null) return unknown();

        if (!known.isNickname()) {
            // Real name reveal — use the subject's clan colour so the
            // visual cue matches styledName() exactly.
            NamedTextColor colour = Clan.colourFor(subject.clan());
            return Component.text(known.display(), colour)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
        }

        // Nickname — neutral gray so the viewer can't infer the clan from
        // a nickname they themselves invented, and so two separate
        // nicknames for the same subject still look visually distinct.
        return Component.text(known.display(), NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** The "stranger" label — same weight treatment as a real nickname so
     *  chat width stays stable as soon as someone introduces themselves. */
    public static Component unknown() {
        return Component.text(UNKNOWN_LABEL, NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    /* ------------------------------------------------ nameplate / tab */

    /**
     * Push the subject's own real name onto the player — display-name,
     * tab list, nameplate above head. Because these Paper APIs set a
     * <em>single</em> component rendered the same way for every viewer,
     * the tab/nameplate always show the real name; per-viewer masking
     * is only applied to chat + /me + /me bubble, which we control the
     * rendering for.
     *
     * <p>Safe to call on any thread; the player updates are main-thread
     * Bukkit API so the caller needs to be on the main thread (all our
     * call sites already are).
     */
    public static void apply(Player p, ShinobiCharacter c) {
        if (p == null || !p.isOnline()) return;
        if (c == null) { reset(p); return; }
        Component styled = styledName(c);
        try { p.displayName(styled); } catch (Throwable ignore) { /* Paper-only */ }
        try { p.playerListName(styled); } catch (Throwable ignore) { /* Paper-only */ }
        try {
            p.customName(styled);
            p.setCustomNameVisible(true);
        } catch (Throwable ignore) { /* Paper-only */ }
    }

    /**
     * Revert a player to the vanilla Minecraft-username rendering. Used
     * on quit / no-active-character / clear-active transitions.
     */
    public static void reset(Player p) {
        if (p == null) return;
        try { p.displayName(null); } catch (Throwable ignore) {}
        try { p.playerListName(null); } catch (Throwable ignore) {}
        try {
            p.customName(null);
            p.setCustomNameVisible(false);
        } catch (Throwable ignore) {}
    }
}
