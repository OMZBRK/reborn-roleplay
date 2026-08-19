package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.character.repository.CharacterRepository;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all {@link ShinobiCharacter} instances in memory plus the "which
 * character is this player currently playing" mapping.
 *
 * Persistence is delegated to a {@link CharacterRepository}. The manager
 * loads an owner's roster lazily (on join / first access) and saves on
 * explicit calls + on quit.
 */
public class CharacterManager implements CharacterService {

    /** Keys for every character-applied AttributeModifier — cached final
     *  NamespacedKeys (never {@code new} per apply), removal matches by
     *  key. The key strings double as the legacy modifier names for the
     *  one-release cleanup fallback in {@link #stripKeyed}. */
    private static final org.bukkit.NamespacedKey AGILITY_SPEED_KEY =
            java.util.Objects.requireNonNull(
                    org.bukkit.NamespacedKey.fromString("shinobicore:agility_speed"));
    private static final org.bukkit.NamespacedKey CHARACTER_SCALE_KEY =
            java.util.Objects.requireNonNull(
                    org.bukkit.NamespacedKey.fromString("shinobicore:character_scale"));

    private final ShinobiCore plugin;
    private final CharacterRepository repo;

    /** ownerId → mutable list of that owner's characters. */
    private final Map<UUID, List<ShinobiCharacter>> byOwner = new ConcurrentHashMap<>();

    /** ownerId → currently-active character (may be null). */
    private final Map<UUID, ShinobiCharacter> active = new ConcurrentHashMap<>();

    /**
     * ownerId → last-known active character. Acts as a fallback for
     * <em>read-only</em> async lookups (chat rendering) so a brief
     * window where {@link #active} is empty (e.g. mid-{@link #setActive}
     * before the new character lands, or a millisecond between
     * {@link #clearActive} and re-set) doesn't leak the Mojang username
     * into chat.
     *
     * <p>Populated on every successful {@link #setActive}. Cleared on
     * {@link #unload}, {@link #clearActive}, and {@link #delete} for
     * the affected character. Never used for game logic — only for
     * display fallback where stale-by-a-tick is harmless.
     */
    private final Map<UUID, ShinobiCharacter> lastSeenActive = new ConcurrentHashMap<>();

    public CharacterManager(ShinobiCore plugin, CharacterRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public CharacterRepository repository() { return repo; }

    /* -------------------------------------------------------- loading / IO */

    /** Load (or return cached) characters for an owner. */
    public List<ShinobiCharacter> getAll(UUID owner) {
        return byOwner.computeIfAbsent(owner, repo::loadAll);
    }

    public Optional<ShinobiCharacter> findById(UUID owner, UUID characterId) {
        for (ShinobiCharacter c : getAll(owner)) {
            if (c.id().equals(characterId)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public Optional<ShinobiCharacter> findByName(UUID owner, String name) {
        for (ShinobiCharacter c : getAll(owner)) {
            if (c.name().equalsIgnoreCase(name)) return Optional.of(c);
        }
        return Optional.empty();
    }

    /** Persist a character, then update the in-memory list and clear its
     *  dirty state. (Direct {@code characterRepository().save()} callers
     *  bypass the markClean — harmless: the record just stays dirty and
     *  re-saves on the next autosave tick.) */
    public void save(ShinobiCharacter c) {
        repo.save(c);
        List<ShinobiCharacter> list = byOwner.computeIfAbsent(c.ownerId(), k -> new ArrayList<>());
        if (!list.contains(c)) list.add(c);
        c.markClean();
    }

    /** Full-roster save — the on-quit / on-disable safety path (always
     *  writes, dirty or not). */
    public void saveAll(UUID owner) {
        for (ShinobiCharacter c : getAll(owner)) save(c);
    }

    public void unload(UUID owner) {
        saveAll(owner);
        byOwner.remove(owner);
        active.remove(owner);
        lastSeenActive.remove(owner);
    }

    /* ---------------------------------------------------------- create/delete */

    public ShinobiCharacter create(UUID owner, String name) {
        ShinobiCharacter c = new ShinobiCharacter(owner, name);
        List<ShinobiCharacter> list = byOwner.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(c);
        repo.save(c);
        return c;
    }

    public boolean delete(UUID owner, UUID characterId) {
        List<ShinobiCharacter> list = getAll(owner);
        boolean removed = list.removeIf(c -> c.id().equals(characterId));
        if (removed) {
            repo.delete(owner, characterId);
            ShinobiCharacter cur = active.get(owner);
            if (cur != null && cur.id().equals(characterId)) active.remove(owner);
            // Also drop the chat-fallback cache if it pointed at the
            // deleted character — otherwise async chat could keep
            // rendering a ghost identity for a tick or two.
            ShinobiCharacter cached = lastSeenActive.get(owner);
            if (cached != null && cached.id().equals(characterId)) lastSeenActive.remove(owner);
        }
        return removed;
    }

    /* ------------------------------------------------------- active tracking */

    public ShinobiCharacter getActive(UUID owner) {
        return active.get(owner);
    }

    /**
     * Read-only fallback variant: returns the live active character if
     * there is one, otherwise the most recently seen active character
     * for this owner. Use ONLY for display-side rendering where stale
     * data is harmless (chat prefix, /me bubble) — never for game
     * logic. Solves the async-chat race where {@link #setActive}
     * hasn't quite published the new character yet.
     */
    public ShinobiCharacter getActiveOrLastSeen(UUID owner) {
        ShinobiCharacter live = active.get(owner);
        if (live != null) return live;
        return lastSeenActive.get(owner);
    }

    /**
     * Set the active character for a player and apply its stats (max HP,
     * scale, movement-speed multiplier). Safe to call with {@code player = null}
     * for offline updates.
     *
     * <p>If there was a different active character for this player, that
     * character's last position is captured before the switch, and then this
     * new character is teleported to ITS own last-known position (if one is
     * saved and its world is loaded).
     */
    public void setActive(Player player, ShinobiCharacter character) {
        if (character == null) return;
        boolean online = player != null && player.isOnline();
        ShinobiCharacter previous = active.get(character.ownerId());

        // 1. Announce — BEFORE anything is captured or swapped, so a
        //    hotbar-hijacking picker (ShinobiAbilities) can restore the
        //    real hotbar in time. See CharacterSwitchEvent javadoc.
        //    Fires even on a same-character re-pick (unchanged contract;
        //    subscribers tolerate previous == next).
        if (online) {
            plugin.getServer().getPluginManager().callEvent(
                    new com.reborn.shinobicore.api.event.CharacterSwitchEvent(
                            player, previous, character));
        }

        // 2. Tear down the previous character (skipped on a re-pick of
        //    the same character). Capture order is load-bearing: the
        //    inventory capture MUST happen before the new character's
        //    inventory overwrites the player.
        if (online && previous != null && !previous.id().equals(character.id())) {
            teardownPrevious(player, previous);
        }

        // 3. Publish the new active character (+ the async-chat cache).
        active.put(character.ownerId(), character);
        lastSeenActive.put(character.ownerId(), character);

        // 4. Apply the new character onto the live player.
        if (online) {
            applyNext(player, character);
        }
    }

    /** Teardown half of a switch: persist the previous character's live
     *  state (HP, position, inventory) and strip its attribute modifiers
     *  so nothing leaks into the next apply. */
    private void teardownPrevious(Player player, ShinobiCharacter previous) {
        previous.setCurrentHp(player.getHealth());
        previous.setLastLocation(player.getLocation());
        previous.captureInventoryFrom(player);
        save(previous);
        stripCharacterModifiers(player);
    }

    /** Apply half of a switch: stats, inventory, position, display,
     *  visibility, welcome, baseline save — in that order (the ordering
     *  comments below are paid-for; do not reorder casually). */
    private void applyNext(Player player, ShinobiCharacter character) {
        {
            applyStats(player, character);
            // Per-character inventory: if the new character has ever
            // had their inventory saved, load it onto the player now.
            // Otherwise seed from the current player state so migrating
            // existing characters doesn't wipe anyone's stuff — the
            // first thing a fresh character gets is whatever's in the
            // hotbar when they're selected.
            if (character.hasSavedInventory()) {
                character.applyInventoryTo(player);
            } else {
                character.captureInventoryFrom(player);
            }
            // IMPORTANT: apply nickname AFTER the teleport. Intra-world
            // teleports don't fire PlayerChangedWorldEvent, so the
            // nickname listener's cross-world safety net doesn't run; if
            // we apply before the teleport, any client display state
            // reset during the teleport leaks the Mojang username into
            // tab / nameplate. Apply-after keeps the last write winning.
            teleportToLastPositionIfAny(player, character);
            CharacterDisplay.apply(player, character);
            // Late-binding safety retry — some clients finish syncing the
            // teleport a tick or two after the main-thread call returns,
            // and they can overwrite the tab entry in that window. A
            // second apply on the next tick reliably pins the styled
            // name in place for every viewer.
            final Player capPlayer = player;
            final ShinobiCharacter capChar = character;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!capPlayer.isOnline()) return;
                ShinobiCharacter now = active.get(capPlayer.getUniqueId());
                if (now != null && now.id().equals(capChar.id())) {
                    CharacterDisplay.apply(capPlayer, capChar);
                }
            }, 1L);
            // Friendship-gated visibility — the switch may have changed
            // which online players this character is friends with. Refresh
            // BOTH directions: this player's view of everyone, and every
            // other player's view of this player.
            if (plugin.visibility() != null) {
                plugin.visibility().refresh(player);
                for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                    if (viewer == player) continue;
                    plugin.visibility().refreshPair(viewer, player);
                }
            }
            // Welcome / reminder: fires on every character pick AND
            // on a 15-minute recurring ticker (see startWelcomeTicker).
            // The character can opt out via the [Ne plus afficher]
            // toggle at the end of the message; once
            // welcomeDisabled is true, neither path shows the message
            // until they re-enable it via /sc welcome on.
            if (!character.welcomeDisabled()) {
                showWelcome(player, character);
            }
            // Persist so the new character has a baseline position if it had none.
            character.setLastLocation(player.getLocation());
            save(character);
        }
    }

    /** Reminder flow shown on every character pick AND on the
     *  15-minute recurring ticker (see {@link #startWelcomeTicker}).
     *  Lists the player commands we surface explicitly:
     *  {@code /amitier}, {@code /techniques}, {@code /me},
     *  {@code /rencontrer}. Ends with a clickable
     *  "[Ne plus afficher]" toggle that flips
     *  {@link ShinobiCharacter#welcomeDisabled} for this character
     *  via the hidden {@code /sc welcome off} subcommand.
     *
     *  <p>Skipped silently when {@code character.welcomeDisabled()}
     *  is true (callers also gate, but we double-check). */
    public void showWelcome(Player player, ShinobiCharacter character) {
        if (character.welcomeDisabled()) return;
        // Defer 1 tick (5 ticks for the on-pick path) so the title
        // fires after the teleport / inventory swap so its appearance
        // feels intentional rather than racing the screen-update
        // flicker of a switch.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ShinobiCharacter live = active.get(player.getUniqueId());
            // Re-check welcome-disabled at fire time — the player may
            // have toggled it in the same tick the ticker queued.
            if (live == null || live.welcomeDisabled()) return;

            net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component
                    .text("Bienvenue", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);
            net.kyori.adventure.text.Component subtitle =
                    CharacterDisplay.styledName(character);
            player.showTitle(net.kyori.adventure.title.Title.title(
                    title,
                    subtitle,
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(400),
                            java.time.Duration.ofMillis(2200),
                            java.time.Duration.ofMillis(600))));

            net.kyori.adventure.text.Component blank =
                    net.kyori.adventure.text.Component.text("");
            player.sendMessage(blank);
            player.sendMessage(net.kyori.adventure.text.Component
                    .text("Le monde t'appartient — explore-le.",
                            net.kyori.adventure.text.format.NamedTextColor.GOLD,
                            net.kyori.adventure.text.format.TextDecoration.ITALIC));
            player.sendMessage(welcomeLine(
                    "Pour narrer une action en RP : ",
                    "/me",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                    "/me ",
                    "Cliquer pour pré-remplir /me",
                    /*runDirectly=*/ false));
            player.sendMessage(welcomeLine(
                    "Pour rencontrer un autre personnage : ",
                    "/rencontrer",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN,
                    "/rencontrer",
                    "Cliquer pour ouvrir le menu /rencontrer",
                    /*runDirectly=*/ true));
            player.sendMessage(welcomeLine(
                    "Pour te lier d'amitié (en regardant le joueur) : ",
                    "/amitier",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA,
                    "/amitier",
                    "Cliquer pour pré-remplir /amitier",
                    /*runDirectly=*/ false));
            player.sendMessage(welcomeLine(
                    "Pour consulter le grand livre des techniques : ",
                    "/techniques",
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE,
                    "/techniques",
                    "Cliquer pour ouvrir l'encyclopédie",
                    /*runDirectly=*/ true));
            player.sendMessage(net.kyori.adventure.text.Component
                    .text("Le reste, c'est à toi de le découvrir.",
                            net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY,
                            net.kyori.adventure.text.format.TextDecoration.ITALIC));

            // Disable toggle — clickable, runs the hidden /sc welcome
            // off subcommand which flips welcomeDisabled and confirms.
            player.sendMessage(net.kyori.adventure.text.Component
                    .text("[Ne plus afficher ce message]",
                            net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY,
                            net.kyori.adventure.text.format.TextDecoration.ITALIC)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent
                            .runCommand("/sc welcome off"))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent
                            .showText(net.kyori.adventure.text.Component.text(
                                    "Désactiver les rappels périodiques",
                                    net.kyori.adventure.text.format.NamedTextColor.GRAY))));
            player.sendMessage(blank);
        }, 5L);
    }

    /** One welcome chat line: gray prefix + clickable command in colour.
     *  {@code runDirectly=true} executes the command on click;
     *  {@code false} pre-fills the chat input so the player can append
     *  arguments (e.g. /me &lt;action&gt;). */
    private static net.kyori.adventure.text.Component welcomeLine(
            String prefix, String label,
            net.kyori.adventure.text.format.NamedTextColor colour,
            String commandToFire,
            String hover,
            boolean runDirectly) {
        var click = runDirectly
                ? net.kyori.adventure.text.event.ClickEvent.runCommand(commandToFire)
                : net.kyori.adventure.text.event.ClickEvent.suggestCommand(commandToFire);
        return net.kyori.adventure.text.Component
                .text(prefix, net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component
                        .text(label, colour,
                                net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .clickEvent(click)
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent
                                .showText(net.kyori.adventure.text.Component.text(
                                        hover,
                                        net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))));
    }

    /* -------------------------------------------------------- ticker */

    private org.bukkit.scheduler.BukkitTask welcomeTicker;

    /** Start the 15-minute recurring welcome reminder. Iterates every
     *  online player; for each one with an active character that
     *  hasn't opted out, re-shows the welcome flow. Idempotent —
     *  calling twice cancels the previous task and starts fresh. */
    public void startWelcomeTicker() {
        if (welcomeTicker != null) {
            try { welcomeTicker.cancel(); } catch (Throwable ignore) {}
        }
        long ticks = 15L * 60L * 20L; // 15 minutes → 20 tps × 60 s × 15.
        welcomeTicker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (com.reborn.shinobicore.util.Tps.shouldDefer()) return;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                ShinobiCharacter c = active.get(p.getUniqueId());
                if (c != null && !c.welcomeDisabled()) showWelcome(p, c);
            }
        }, ticks, ticks);
    }

    public void stopWelcomeTicker() {
        if (welcomeTicker != null) {
            try { welcomeTicker.cancel(); } catch (Throwable ignore) {}
            welcomeTicker = null;
        }
    }

    /** Teleports the player to {@code character.lastLocation()} if present.
     *  Private: a sub-step of the switch pipeline ({@link #setActive}). */
    private void teleportToLastPositionIfAny(Player player, ShinobiCharacter character) {
        if (player == null || !player.isOnline() || character == null) return;
        Location loc = character.lastLocation();
        if (loc != null && loc.getWorld() != null) {
            player.teleport(loc);
        }
    }

    public void clearActive(UUID owner) {
        active.remove(owner);
        // Intentional: clearing active means we want the "no active
        // character" path everywhere (chat lockdown / blindness).
        // Drop the cached fallback too so chat doesn't keep rendering
        // the previous identity.
        lastSeenActive.remove(owner);
        Player p = plugin.getServer().getPlayer(owner);
        if (p != null && p.isOnline()) CharacterDisplay.reset(p);
    }

    /* --------------------------------------------------- stat application */

    /**
     * Re-apply the active character's derived stats onto its online owner
     * — the one sanctioned out-of-switch apply, used after a staff edit
     * changes stats live. No-op when the player is offline or the edited
     * character isn't the active one.
     */
    public void reapplyStatsIfActive(ShinobiCharacter c) {
        if (c == null) return;
        ShinobiCharacter current = active.get(c.ownerId());
        if (current == null || !current.id().equals(c.id())) return;
        Player online = plugin.getServer().getPlayer(c.ownerId());
        if (online != null && online.isOnline()) applyStats(online, c);
    }

    /**
     * Push the character's derived stats onto the Bukkit player and remove
     * any no-character-lockdown effects. Private: a sub-step of the switch
     * pipeline; out-of-switch callers go through {@link #reapplyStatsIfActive}.
     */
    private void applyStats(Player p, ShinobiCharacter c) {
        // Max HP
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.max(1.0, c.maxHp()));
            p.setHealth(Math.min(p.getHealth(), hp.getBaseValue()));
            if (c.currentHp() > 0) {
                p.setHealth(Math.min(c.currentHp(), hp.getBaseValue()));
            }
        }

        // Compact HP bar: always render as 10 hearts regardless of Max HP.
        double healthScale = plugin.getConfig().getDouble("display.health-scale", 20.0);
        if (healthScale > 0) {
            p.setHealthScaled(true);
            p.setHealthScale(healthScale);
        } else {
            p.setHealthScaled(false);
        }

        // Invisible permanent saturation so the hunger bar never matters.
        if (plugin.getConfig().getBoolean("display.auto-saturation", true)) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExhaustion(0f);
            int refresh = Math.max(5, plugin.getConfig()
                    .getInt("display.saturation-refresh-seconds", 30));
            p.addPotionEffect(new PotionEffect(
                    PotionEffectType.SATURATION,
                    refresh * 20 * 3,
                    0,
                    true,
                    false,
                    false
            ));
        }

        // Scale (only present on 1.20.5+) — applied as a BASE VALUE, not a
        // modifier; the strip only cleans relics of the older modifier-based
        // version that may still sit on a long-running server.
        AttributeInstance scaleAttr = safeAttribute(p, "GENERIC_SCALE");
        if (scaleAttr != null) {
            stripKeyed(scaleAttr, CHARACTER_SCALE_KEY);
            scaleAttr.setBaseValue(c.size());
        }

        // Agility speed multiplier — keyed MULTIPLY_SCALAR_1 modifier.
        // Strip-then-apply keeps this idempotent (never stacks).
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            stripKeyed(speed, AGILITY_SPEED_KEY);
            double mult = c.speedMultiplier();
            if (mult != 1.0) {
                speed.addModifier(new AttributeModifier(
                        AGILITY_SPEED_KEY,
                        mult - 1.0,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }

        // If the no-character lockdown was active, lift it now.
        if (plugin.noCharacterLockdown() != null) {
            plugin.noCharacterLockdown().release(p);
        }
    }

    private AttributeInstance safeAttribute(Player p, String enumName) {
        try {
            Attribute a = Attribute.valueOf(enumName);
            return p.getAttribute(a);
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }

    /** Remove every modifier on {@code inst} that this plugin added under
     *  {@code key} — matched by key (the modern Paper 1.21 identity),
     *  with a one-release legacy fallback for modifiers added by the
     *  pre-keyed code on a still-running server: those carried
     *  {@code nameUUIDFromBytes(key.toString())} as UUID and the key
     *  string as their name. Name-only matching is NOT sufficient on
     *  Paper 1.21 (modifiers are keyed under the hood; a stale one left
     *  behind makes the next addModifier throw "already applied") —
     *  that invariant is why all three checks stay together. */
    @SuppressWarnings("deprecation") // getUniqueId/getName: legacy-cleanup fallback only
    private void stripKeyed(AttributeInstance inst, org.bukkit.NamespacedKey key) {
        String legacyName = key.toString();
        UUID legacyId = UUID.nameUUIDFromBytes(legacyName.getBytes());
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (key.equals(m.getKey())
                    || legacyId.equals(m.getUniqueId())
                    || legacyName.equals(m.getName())) {
                inst.removeModifier(m);
            }
        }
    }

    /** Strip every character-applied attribute modifier from the player —
     *  the teardown half of a character switch (the apply half is
     *  {@link #applyStats}). Safe to call when nothing is applied. */
    private void stripCharacterModifiers(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) stripKeyed(speed, AGILITY_SPEED_KEY);
        AttributeInstance scaleAttr = safeAttribute(p, "GENERIC_SCALE");
        if (scaleAttr != null) stripKeyed(scaleAttr, CHARACTER_SCALE_KEY);
    }

    /* --------------------------------------------------------------- utils */

    public Map<UUID, ShinobiCharacter> activeView() {
        return Collections.unmodifiableMap(active);
    }

    public Map<UUID, List<ShinobiCharacter>> rosterView() {
        Map<UUID, List<ShinobiCharacter>> out = new HashMap<>();
        byOwner.forEach((k, v) -> out.put(k, Collections.unmodifiableList(v)));
        return out;
    }

    /* --------------------------------------------- name resolution (shared) */

    // ResolvedCharacter now lives on the CharacterService api interface;
    // the simple name below resolves through the implemented interface.

    /** Resolve a loaded character by case-insensitive name, with its owner;
     *  {@code null} when nothing matches. */
    public ResolvedCharacter resolveOwned(String name) {
        if (name == null) return null;
        for (Map.Entry<UUID, List<ShinobiCharacter>> e : byOwner.entrySet()) {
            for (ShinobiCharacter c : e.getValue()) {
                if (c.name().equalsIgnoreCase(name)) {
                    return new ResolvedCharacter(e.getKey(), c);
                }
            }
        }
        return null;
    }

    /** Resolve a loaded character by id, with its owner; {@code null} if unknown. */
    public ResolvedCharacter resolveOwnedById(UUID characterId) {
        if (characterId == null) return null;
        for (Map.Entry<UUID, List<ShinobiCharacter>> e : byOwner.entrySet()) {
            for (ShinobiCharacter c : e.getValue()) {
                if (c.id().equals(characterId)) {
                    return new ResolvedCharacter(e.getKey(), c);
                }
            }
        }
        return null;
    }

    /** Just the character by name (case-insensitive), or {@code null}. */
    public ShinobiCharacter resolveByName(String name) {
        ResolvedCharacter r = resolveOwned(name);
        return r == null ? null : r.character();
    }

    /** The online player currently playing the named character (active char
     *  matches), or an exact online username as fallback; {@code null} otherwise. */
    public Player findOnlinePlayerByCharacter(String name) {
        ResolvedCharacter r = resolveOwned(name);
        if (r != null) {
            Player owner = org.bukkit.Bukkit.getPlayer(r.ownerId());
            if (owner != null && owner.isOnline()) {
                ShinobiCharacter act = getActive(owner.getUniqueId());
                if (act != null && act.id().equals(r.character().id())) return owner;
            }
            return null;
        }
        return name == null ? null : org.bukkit.Bukkit.getPlayerExact(name);
    }

    /** Every loaded character name, sorted case-insensitively (tab-completion). */
    public List<String> allCharacterNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (List<ShinobiCharacter> roster : byOwner.values()) {
            for (ShinobiCharacter c : roster) names.add(c.name());
        }
        return new ArrayList<>(names);
    }
}
