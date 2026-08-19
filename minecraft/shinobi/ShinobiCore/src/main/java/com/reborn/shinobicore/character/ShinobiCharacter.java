package com.reborn.shinobicore.character;

import com.reborn.shinobicore.chakra.ChakraPool;
import com.reborn.shinobicore.ko.injury.Injury;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.reborn.shinobicore.skill.Skill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A single playable character. A Minecraft account (owner UUID) can own
 * multiple of these; exactly one can be <em>active</em> at any time.
 *
 * <p>All persisted fields are mutable through setters so the admin edit GUI
 * can rewrite them. Whenever {@code level} or {@code affinity} changes,
 * {@link #recomputeStats()} is called to update the chakra pool's max.
 *
 * <p>Current HP / current chakra / last position are persisted so that a
 * player coming back from a previous session resumes the character's state.
 */
public class ShinobiCharacter implements com.reborn.shinobicore.data.CharacterData {

    public static final int MAX_LEAF_TEST_ROLLS = 5;
    public static final int MAX_CHAKRA_AFFINITIES = 3;

    /* ---------------------------------------------------- dirty tracking */
    /** Unsaved-changes flag, set by every mutator (value-compared on the
     *  autosave-hot paths so an unchanged snapshot stays clean). Fresh
     *  characters start dirty; the repository marks clean after load. */
    private transient boolean dirty = true;
    /** LearnedState / ChakraPool mutation counters recorded at the last
     *  {@link #markClean()} — the sub-objects track their own writes. */
    private transient long cleanLearnedMutations;
    private transient long cleanChakraMutations;

    /** Base per-skill cap; the effective cap is this + level (see {@link #skillCap()}). */
    public static final int SKILL_CAP_BASE = 10;

    /* ------------------------------------------------------------- identity */
    private final UUID id;
    private final UUID ownerId;

    /* -------------------------------------------------------- editable data */
    private String name;
    private String clan;
    private String village = "";     // Village RP (Konoha, Suna, ...) — "" si aucun
    private String sexe = "Homme";   // "Homme" / "Femme" — cosmétique (modèle Steve/Alex)
    private String appearance = "";  // apparence RP composée (queue SkinSpec.serialize()) — "" = skin par défaut
    private int age;
    private double size;            // Attribute.SCALE multiplier (e.g. 0.8 – 1.4)
    private NinjaArt ninjaArt;
    private Affinity affinity;
    private Rank rank;              // Village grade (Genin, Chunin, Jonin, ...)

    /** Up to {@value #MAX_CHAKRA_AFFINITIES} elemental chakra affinities.
     *  Order preserved (first-rolled first). */
    private final Set<ChakraAffinity> chakraAffinities = new LinkedHashSet<>();

    private int leafTestRollsUsed;

    /* ----------------------------------------------- progression & runtime */
    private int level;
    private double experience;

    /* ------------------------------------------- learned state (unified) */
    /** Everything learned — ability mastery (known == entry present),
     *  skills, skill points. One source of truth; see {@link LearnedState}. */
    private final LearnedState learned = new LearnedState();
    private double currentHp;
    private final ChakraPool chakra;

    /* ------------------------------------------------------- last position */
    private String lastWorldName;
    private double lastX, lastY, lastZ;
    private float lastYaw, lastPitch;
    private boolean hasLastPosition;

    /* ------------------------------------------------ per-character inventory */
    /**
     * Frozen snapshot of the player's inventory while this character is
     * <em>not</em> active. Layout: 36 main-inventory slots (0..35), 4
     * armor slots (36..39) in helm/chest/legs/boots order, 1 offhand
     * slot (40). {@code null} entries are empty. Persisted through the
     * repository as a serialised {@code Map<String,Object>} per slot
     * (standard Bukkit ItemStack YAML serialisation).
     *
     * <p>Characters created before the per-character-inventory feature
     * landed start with {@link #hasSavedInventory} {@code false}, which
     * tells the swap logic to <em>inherit</em> whatever the player is
     * currently holding rather than wiping them. Once that first
     * capture runs, the flag flips to {@code true} permanently.
     */
    private final ItemStack[] inventory = new ItemStack[INVENTORY_SIZE];
    private boolean hasSavedInventory;

    public static final int MAIN_INVENTORY_SLOTS = 36;
    public static final int ARMOR_INVENTORY_SLOTS = 4;
    public static final int OFFHAND_INVENTORY_SLOTS = 1;
    public static final int INVENTORY_SIZE =
            MAIN_INVENTORY_SLOTS + ARMOR_INVENTORY_SLOTS + OFFHAND_INVENTORY_SLOTS;
    public static final int OFFHAND_SLOT_INDEX = MAIN_INVENTORY_SLOTS + ARMOR_INVENTORY_SLOTS;

    /* ---------------------------------------------- identity / contacts */
    /**
     * People this character has been <em>introduced to</em>. Key is the
     * other character's {@link #id()} (the subject); value is how THIS
     * character knows the subject — either their real name (stored as
     * "Name Clan") or an arbitrary nickname. Rendering lives in
     * {@link CharacterDisplay#styledNameFor} and keys off the {@code isNickname}
     * flag to pick a colour (clan colour for real names, neutral gray for
     * nicknames so the viewer can't reverse-engineer the clan).
     */
    private final Map<UUID, KnownName> contacts = new HashMap<>();

    /**
     * Pool of nicknames this character has saved for quick re-use when
     * giving their own identity to someone new. Populated via the chat
     * "save nickname?" prompt after every nickname-reveal. Order is
     * preserved (most-recently-saved appended last).
     */
    private final List<String> savedNicknames = new ArrayList<>();

    /**
     * Character ids of <em>friends</em> — a mutual relationship built via
     * {@code /amitier}. Distinct from {@link #contacts}: contacts just
     * means "you've been introduced" (they no longer show as ???? in
     * chat), friendship is a stronger opt-in that also gates tab-list
     * visibility. Maintained as a symmetric graph by
     * {@link FriendshipManager}. Cleared on character reset.
     */
    private final Set<UUID> friends = new LinkedHashSet<>();

    /**
     * Per-character toggle for the welcome / reminder message. The
     * message fires on every {@code setActive} call AND on a 15-minute
     * recurring ticker — players keep being reminded of the few
     * commands we surface ({@code /amitier}, {@code /techniques},
     * {@code /me}, {@code /rencontrer}) until they click the
     * "[Ne plus afficher]" toggle in the message, which sets this to
     * {@code true} and silences future reminders for THIS character.
     * Defaults to {@code false} so a fresh character sees the message.
     */
    private boolean welcomeDisabled;

    /**
     * Whether this character has already seen its first-play intro
     * cinematic. Defaults to {@code false} so a brand-new character runs the
     * designated "bienvenue" cinematic the first time it is set active;
     * flipped to {@code true} on completion so it never replays.
     */
    private boolean introSeen;

    /*
     * Per-JutsuItem-type bindings used to live here as an EnumMap.
     * They've been removed along with the jutsu system; the new
     * ShinobiAbilities plugin owns its own per-character binding
     * storage (likely a small YAML file beside this one, keyed by
     * character UUID).
     */
    // (jutsuBindings field removed — see comment block above)

    /**
     * Whether the character has been ruled <em>dead</em> by a validated
     * Tuer request. Dead characters cannot be re-selected from
     * {@code /character}; they are kept on the roster (greyed out, with
     * a "Mort" tag) so staff can revive them via {@code /character edit}
     * after a clarification or RP rewind. Independent from KO state —
     * a player can be KO without being dead, and a character can be
     * dead without anyone being currently logged in as them.
     */
    private boolean dead;

    /**
     * Active injuries on this character. Each {@link Injury} carries a
     * body part, type (Hématome/Brûlure/etc.), origin (Taijutsu/Katon/…)
     * and severity. Persisted across logout / character switch so a
     * Faible bruise still ticks down while you're playing another
     * character. Cleared by the {@code InjuryHealer} (time-based) or
     * by an Iryō technique. Injuries survive the death + revive cycle
     * because staff revives are explicitly meant to undo a killing
     * blow — a clean wipe would force the medic role to re-treat
     * unrelated wounds.
     */
    private final List<Injury> injuries = new ArrayList<>();

    /** Name the viewer knows the subject by — real ("Name Clan") or
     *  nickname. The {@code isNickname} flag is what the renderer uses
     *  to pick a colour. */
    public record KnownName(String display, boolean isNickname) {}

    /* disabledMobilitySlots removed alongside the mobility system. */

    /** Constructor for newly-created characters. */
    public ShinobiCharacter(UUID ownerId, String name) {
        this(UUID.randomUUID(), ownerId, name,
                "None", 12, 1.0,
                NinjaArt.TAIJUTSU, Affinity.STRENGTH, EnumSet.noneOf(ChakraAffinity.class),
                Rank.GENIN,
                0, 1, 0.0,
                /* currentHp */ -1.0, /* currentChakra */ -1.0,
                null, 0, 0, 0, 0, 0);
    }

    /** Full constructor (used by the repository when loading). */
    public ShinobiCharacter(UUID id, UUID ownerId, String name, String clan,
                            int age, double size,
                            NinjaArt ninjaArt, Affinity affinity,
                            Set<ChakraAffinity> chakraAffinities,
                            Rank rank, int leafTestRollsUsed,
                            int level, double experience,
                            double currentHp, double currentChakra,
                            String lastWorldName,
                            double lastX, double lastY, double lastZ,
                            float lastYaw, float lastPitch) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.clan = clan;
        this.age = age;
        this.size = size;
        this.ninjaArt = ninjaArt;
        this.affinity = affinity;
        this.rank = rank == null ? Rank.GENIN : rank;
        if (chakraAffinities != null) {
            for (ChakraAffinity a : chakraAffinities) {
                if (a != null && a != ChakraAffinity.NONE
                        && this.chakraAffinities.size() < MAX_CHAKRA_AFFINITIES) {
                    this.chakraAffinities.add(a);
                }
            }
        }
        this.leafTestRollsUsed = leafTestRollsUsed;
        this.level = LevelTable.clampLevel(level);
        this.experience = experience;
        this.chakra = new ChakraPool(maxChakra());
        this.currentHp = currentHp < 0 ? maxHp() : Math.min(currentHp, maxHp());
        this.chakra.setCurrent(currentChakra < 0 ? chakra.max() : Math.min(currentChakra, chakra.max()));
        if (lastWorldName != null && !lastWorldName.isEmpty()) {
            this.lastWorldName = lastWorldName;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastZ = lastZ;
            this.lastYaw = lastYaw;
            this.lastPitch = lastPitch;
            this.hasLastPosition = true;
        }
        // disabledMobilitySlots defaults to empty (all slots enabled). The
        // repository populates it afterwards from the saved YAML if the
        // player has disabled any slots in the /toggle GUI.
    }

    /* -------------------------------------------------------------- getters */

    public UUID id()               { return id; }
    public UUID ownerId()          { return ownerId; }
    public String name()           { return name; }
    public String clan()           { return clan; }
    public String village()        { return village == null ? "" : village; }
    public String sexe()           { return sexe == null ? "Homme" : sexe; }
    public String appearance()     { return appearance == null ? "" : appearance; }
    public int age()               { return age; }
    public double size()           { return size; }
    public NinjaArt ninjaArt()     { return ninjaArt; }
    public Affinity affinity()     { return affinity; }
    public Rank rank()             { return rank == null ? Rank.GENIN : rank; }

    /** Unmodifiable view of this character's up-to-3 elemental affinities. */
    public Set<ChakraAffinity> chakraAffinities() {
        return Collections.unmodifiableSet(chakraAffinities);
    }

    /**
     * Returns the primary (first-rolled) affinity, or {@link ChakraAffinity#NONE}
     * if the character has no affinities yet. Kept for legacy callers.
     */
    public ChakraAffinity primaryChakraAffinity() {
        return chakraAffinities.isEmpty() ? ChakraAffinity.NONE : chakraAffinities.iterator().next();
    }

    public int leafTestRollsUsed() { return leafTestRollsUsed; }
    public int leafTestRollsRemaining() { return Math.max(0, MAX_LEAF_TEST_ROLLS - leafTestRollsUsed); }
    public int chakraAffinitiesRemaining() {
        return Math.max(0, MAX_CHAKRA_AFFINITIES - chakraAffinities.size());
    }
    public int level()             { return level; }
    public double experience()     { return experience; }
    public double currentHp()      { return currentHp; }
    public ChakraPool chakra()     { return chakra; }

    public boolean hasLastPosition() { return hasLastPosition; }
    public String lastWorldName()    { return lastWorldName; }
    public double lastX()            { return lastX; }
    public double lastY()            { return lastY; }
    public double lastZ()            { return lastZ; }
    public float lastYaw()           { return lastYaw; }
    public float lastPitch()         { return lastPitch; }

    /** Returns the last {@link Location}, resolving the world through the
     *  server. Returns {@code null} if no position has been saved yet, or if
     *  the saved world is no longer loaded. */
    public Location lastLocation() {
        if (!hasLastPosition || lastWorldName == null) return null;
        World w = Bukkit.getWorld(lastWorldName);
        if (w == null) return null;
        return new Location(w, lastX, lastY, lastZ, lastYaw, lastPitch);
    }

    public void setLastLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String world = loc.getWorld().getName();
        // Diff-gated: the 60s autosave calls this for every online player;
        // an unmoved player must not dirty the record.
        if (hasLastPosition && world.equals(lastWorldName)
                && lastX == loc.getX() && lastY == loc.getY() && lastZ == loc.getZ()
                && lastYaw == loc.getYaw() && lastPitch == loc.getPitch()) {
            return;
        }
        this.lastWorldName = world;
        this.lastX = loc.getX();
        this.lastY = loc.getY();
        this.lastZ = loc.getZ();
        this.lastYaw = loc.getYaw();
        this.lastPitch = loc.getPitch();
        this.hasLastPosition = true;
        touch();
    }

    public void clearLastLocation() {
        this.hasLastPosition = false;
        this.lastWorldName = null;
        touch();
    }

    /* -------------------------------------------------------------- setters */

    public void setName(String name)   { this.name = name; touch(); }
    public void setClan(String clan)   { this.clan = clan; touch(); }
    public void setVillage(String v)   { this.village = v == null ? "" : v; touch(); }
    public void setSexe(String s)      { this.sexe = (s == null || s.isBlank()) ? "Homme" : s; touch(); }
    public void setAppearance(String a){ this.appearance = a == null ? "" : a; touch(); }
    public void setAge(int age)        { this.age = Math.max(0, age); touch(); }
    public void setSize(double size)   { this.size = Math.max(0.25, Math.min(4.0, size)); touch(); }
    public void setNinjaArt(NinjaArt a){ this.ninjaArt = a; touch(); }

    public void setAffinity(Affinity a) {
        this.affinity = a;
        recomputeStats();
        touch();
    }

    public void setRank(Rank r) { this.rank = r == null ? Rank.GENIN : r; touch(); }

    public void setLevel(int level) {
        this.level = LevelTable.clampLevel(level);
        recomputeStats();
        touch();
    }

    public void setExperience(double xp) { this.experience = Math.max(0.0, xp); touch(); }

    /** Diff-gated: the 60s autosave writes the live HP for every online
     *  player; an unchanged value must not dirty the record. */
    public void setCurrentHp(double hp) {
        double clamped = Math.max(0.0, Math.min(hp, maxHp()));
        if (clamped == this.currentHp) return;
        this.currentHp = clamped;
        touch();
    }

    /* ---------------------------------------------------- affinity mutation */

    /** Add an affinity slot (admin override). Returns true if added, false if
     *  already present or slots full. */
    public boolean addChakraAffinity(ChakraAffinity a) {
        if (a == null || a == ChakraAffinity.NONE) return false;
        if (chakraAffinities.contains(a)) return false;
        if (chakraAffinities.size() >= MAX_CHAKRA_AFFINITIES) return false;
        chakraAffinities.add(a);
        touch();
        return true;
    }

    /** Remove an affinity slot (admin override). */
    public boolean removeChakraAffinity(ChakraAffinity a) {
        boolean removed = chakraAffinities.remove(a);
        if (removed) touch();
        return removed;
    }

    public void clearChakraAffinities() { chakraAffinities.clear(); touch(); }

    public boolean hasChakraAffinity(ChakraAffinity a) {
        return chakraAffinities.contains(a);
    }

    /**
     * Result of a leaf-test roll.
     *
     * @param rolled      the element the leaf fell towards
     * @param outcome     what happened (added / duplicate / no rolls / max reached)
     */
    public record LeafTestResult(ChakraAffinity rolled, Outcome outcome) {
        public enum Outcome { ADDED, DUPLICATE, NO_ROLLS_LEFT, SLOTS_FULL }
    }

    /** Consume one roll and attempt to add a random element. */
    public LeafTestResult rollLeafTest() {
        if (leafTestRollsUsed >= MAX_LEAF_TEST_ROLLS) {
            return new LeafTestResult(ChakraAffinity.NONE, LeafTestResult.Outcome.NO_ROLLS_LEFT);
        }
        if (chakraAffinities.size() >= MAX_CHAKRA_AFFINITIES) {
            return new LeafTestResult(ChakraAffinity.NONE, LeafTestResult.Outcome.SLOTS_FULL);
        }
        leafTestRollsUsed++;
        touch();
        ChakraAffinity rolled = ChakraAffinity.roll();
        if (chakraAffinities.contains(rolled)) {
            return new LeafTestResult(rolled, LeafTestResult.Outcome.DUPLICATE);
        }
        chakraAffinities.add(rolled);
        return new LeafTestResult(rolled, LeafTestResult.Outcome.ADDED);
    }

    /* -------------------------------------------------------------- derived */

    /** Base * (Strength multiplier if STRENGTH-affinity, else 1.0). */
    public double maxHp() {
        double base = LevelTable.baseHp(level);
        double mult = affinity == Affinity.STRENGTH
                ? AffinityMultipliers.strengthMultiplier(level) : 1.0;
        return base * mult;
    }

    /** Base * (Intelligence multiplier if INTELLIGENCE-affinity, else 1.0). */
    public double maxChakra() {
        double base = LevelTable.baseChakra(level);
        double mult = affinity == Affinity.INTELLIGENCE
                ? AffinityMultipliers.intelligenceMultiplier(level) : 1.0;
        return base * mult;
    }

    /** Multiplier applied to the player's base movement speed. */
    public double speedMultiplier() {
        return affinity == Affinity.AGILITY
                ? AffinityMultipliers.agilityMultiplier(level) : 1.0;
    }

    /** Re-sync derived values (chakra max, HP cap) after editing level/affinity. */
    public void recomputeStats() {
        chakra.setMax(maxChakra());
        if (currentHp > maxHp()) currentHp = maxHp();
    }

    /* mobility-slot toggle API removed alongside the mobility system. */

    /* ---------------------------------------------- contact book API */

    /** True if this character knows {@code otherCharId} — i.e. someone
     *  has /rencontrer'd with them since their character was created. */
    public boolean knows(UUID otherCharId) {
        return contacts.containsKey(otherCharId);
    }

    /** How this character knows {@code otherCharId} — returns {@code null}
     *  when they haven't been introduced yet. */
    public KnownName knownNameOf(UUID otherCharId) {
        return contacts.get(otherCharId);
    }

    /** Remember {@code other} under {@code display}. Overwrites any
     *  existing entry (useful when someone reveals their real name after
     *  having been known only by a nickname). */
    public void remember(UUID otherCharId, String display, boolean isNickname) {
        if (otherCharId == null || display == null || display.isBlank()) return;
        contacts.put(otherCharId, new KnownName(display.trim(), isNickname));
        touch();
    }

    /** Unmodifiable snapshot of the contact book — ID → how we know them. */
    public Map<UUID, KnownName> contacts() {
        return Collections.unmodifiableMap(contacts);
    }

    /** Forget a specific contact (admin / reset tooling). */
    public void forget(UUID otherCharId) {
        if (contacts.remove(otherCharId) != null) touch();
    }

    /* ---------------------------------------------- friendship API */

    /** True if {@code otherCharId} is on this character's friend list.
     *  The graph is symmetric — {@link FriendshipManager} always writes
     *  both sides in the same operation. */
    public boolean isFriend(UUID otherCharId) {
        return otherCharId != null && friends.contains(otherCharId);
    }

    /** Add a friend edge on this side. Callers should use
     *  {@link FriendshipManager#addMutual} so both sides land atomically
     *  and both character YAMLs are persisted. */
    public void addFriend(UUID otherCharId) {
        if (otherCharId != null && !otherCharId.equals(id) && friends.add(otherCharId)) touch();
    }

    /** Remove a friend edge on this side. As with {@link #addFriend},
     *  prefer {@link FriendshipManager#removeMutual}. */
    public void removeFriend(UUID otherCharId) {
        if (otherCharId != null && friends.remove(otherCharId)) touch();
    }

    /** Unmodifiable snapshot of this character's friend ids. */
    public Set<UUID> friends() {
        return Collections.unmodifiableSet(friends);
    }

    /* ---------------------------------------------- welcome flow */

    public boolean welcomeDisabled()         { return welcomeDisabled; }
    public void setWelcomeDisabled(boolean v) { this.welcomeDisabled = v; touch(); }

    /* ---------------------------------------------- first-play intro */

    public boolean introSeen()              { return introSeen; }
    public void setIntroSeen(boolean v)      { this.introSeen = v; touch(); }

    /* ---------------------------------------------- death flag */

    public boolean dead()         { return dead; }
    public void    setDead(boolean v) { this.dead = v; touch(); }

    /* ---------------------------------------------- jutsu bindings */

    /* jutsuBindings methods removed alongside the jutsu system. */

    /* ---------------------------------------------- injuries */

    /** Live mutable view — the KO + Iryō systems read and modify this
     *  list directly. The repository iterates it on save. */
    public List<Injury> injuries() { return injuries; }

    /** Append an injury (no-op when null). */
    public void addInjury(Injury i) {
        if (i != null) { injuries.add(i); touch(); }
    }

    /** Remove the injury with this id. Returns true on hit. */
    public boolean removeInjury(UUID injuryId) {
        if (injuryId == null) return false;
        boolean removed = injuries.removeIf(x -> injuryId.equals(x.id()));
        if (removed) touch();
        return removed;
    }

    /** Clear every injury (used by {@code /character edit reset}). */
    public void clearInjuries() { injuries.clear(); touch(); }

    /* ---------------------------------------------- inventory snapshot API */

    /** True once the character has had its inventory captured at least
     *  once (on switch-out or auto-save). Characters that pre-date this
     *  feature start {@code false} so the first activation inherits the
     *  player's current items instead of wiping them. */
    public boolean hasSavedInventory() { return hasSavedInventory; }

    /** Read-only access to the stored inventory array. {@link #INVENTORY_SIZE}
     *  slots, {@code null} for empties. The repository uses this for
     *  serialisation; callers that want to apply it to a player should
     *  use {@link #applyInventoryTo(Player)} instead. */
    public ItemStack[] inventorySnapshot() { return inventory; }

    /** Replace the stored inventory array. Used by the repository on
     *  load. The argument must be exactly {@link #INVENTORY_SIZE} long. */
    public void setInventorySnapshot(ItemStack[] src) {
        if (src == null || src.length != INVENTORY_SIZE) return;
        System.arraycopy(src, 0, inventory, 0, INVENTORY_SIZE);
        hasSavedInventory = true;
        touch();
    }

    /** Pull the player's current inventory into this character's
     *  snapshot. Called before a character swap and on the auto-save
     *  ticker. Safe when the player is null / offline (no-op). */
    public void captureInventoryFrom(Player p) {
        if (p == null || !p.isOnline()) return;
        PlayerInventory inv = p.getInventory();
        // Build the candidate snapshot first, then diff-assign — the 60s
        // autosave calls this for every online player and an unchanged
        // inventory must not dirty the record. The per-slot equals() is
        // far cheaper than the YAML re-serialize it saves.
        ItemStack[] next = new ItemStack[INVENTORY_SIZE];
        // Main inventory (0..35) via getContents — Paper returns a 36-slot
        // array covering hotbar + main.
        ItemStack[] main = inv.getStorageContents();
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
            next[i] = (i < main.length && main[i] != null && !main[i].getType().isAir())
                    ? main[i].clone() : null;
        }
        // Armor slots (36..39) — boots, legs, chest, helm ordering matches
        // getArmorContents() which returns them bottom-up. We store them
        // top-down (helm/chest/legs/boots) for human-readability in YAML.
        ItemStack[] armor = inv.getArmorContents();
        // getArmorContents(): [boots, legs, chestplate, helmet] — flip to
        // helmet, chest, legs, boots at indices 36..39.
        next[36] = armor.length > 3 ? copyOrNull(armor[3]) : null; // helm
        next[37] = armor.length > 2 ? copyOrNull(armor[2]) : null; // chest
        next[38] = armor.length > 1 ? copyOrNull(armor[1]) : null; // legs
        next[39] = armor.length > 0 ? copyOrNull(armor[0]) : null; // boots
        // Offhand (40).
        next[OFFHAND_SLOT_INDEX] = copyOrNull(inv.getItemInOffHand());

        boolean changed = !hasSavedInventory;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (!java.util.Objects.equals(inventory[i], next[i])) changed = true;
            inventory[i] = next[i];
        }
        hasSavedInventory = true;
        if (changed) touch();
    }

    /** Apply this character's stored inventory onto {@code p}. Should
     *  be called <em>after</em> the outgoing character's inventory has
     *  been captured — otherwise the outgoing data is lost. No-op when
     *  the player is null/offline or this character has never had its
     *  inventory saved (the caller should handle that case explicitly
     *  by capturing the player's current state instead). */
    public void applyInventoryTo(Player p) {
        if (p == null || !p.isOnline()) return;
        if (!hasSavedInventory) return;
        PlayerInventory inv = p.getInventory();
        // Main slots.
        ItemStack[] main = new ItemStack[MAIN_INVENTORY_SLOTS];
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) main[i] = copyOrNull(inventory[i]);
        inv.setStorageContents(main);
        // Armor — getArmorContents expects [boots, legs, chest, helm].
        ItemStack[] armor = new ItemStack[ARMOR_INVENTORY_SLOTS];
        armor[0] = copyOrNull(inventory[39]); // boots
        armor[1] = copyOrNull(inventory[38]); // legs
        armor[2] = copyOrNull(inventory[37]); // chest
        armor[3] = copyOrNull(inventory[36]); // helm
        inv.setArmorContents(armor);
        // Offhand.
        inv.setItemInOffHand(copyOrNull(inventory[OFFHAND_SLOT_INDEX]));
    }

    /** Wipe the inventory snapshot — used when a character is reset.
     *  The flag stays true so the next switch doesn't inherit. */
    public void clearInventorySnapshot() {
        for (int i = 0; i < INVENTORY_SIZE; i++) inventory[i] = null;
        hasSavedInventory = true;
        touch();
    }

    private static ItemStack copyOrNull(ItemStack src) {
        if (src == null || src.getType() == Material.AIR) return null;
        return src.clone();
    }

    /** Save a nickname to this character's pool for later re-use in the
     *  /rencontrer GUI. No-op on blank input or duplicates. */
    public void saveNickname(String nickname) {
        if (nickname == null) return;
        String trimmed = nickname.trim();
        if (trimmed.isEmpty()) return;
        if (!savedNicknames.contains(trimmed)) { savedNicknames.add(trimmed); touch(); }
    }

    /** Remove a saved nickname from the pool. */
    public void removeSavedNickname(String nickname) {
        if (nickname == null) return;
        if (savedNicknames.remove(nickname.trim())) touch();
    }

    /** Unmodifiable snapshot of this character's saved nickname pool. */
    public List<String> savedNicknames() {
        return Collections.unmodifiableList(savedNicknames);
    }

    /* ---------------------------------------------- dirty tracking api */

    /** Mark this record as having unsaved changes. */
    private void touch() { dirty = true; }

    /** {@link com.reborn.shinobicore.data.CharacterData} identity. */
    @Override
    public UUID characterId() { return id; }

    /** True when the record has unsaved changes — own fields, learned
     *  state, or the chakra pool (compared via mutation counters). */
    @Override
    public boolean dirty() {
        return dirty
                || learned.mutationCount() != cleanLearnedMutations
                || chakra.mutationCount() != cleanChakraMutations;
    }

    /** Clear the dirty state; called after a successful save (and after
     *  the repository finishes loading). */
    @Override
    public void markClean() {
        dirty = false;
        cleanLearnedMutations = learned.mutationCount();
        cleanChakraMutations = chakra.mutationCount();
    }

    /* --------------------------------------- learned state (delegates) */

    /** The unified learned state — abilities/mastery, skills, points. */
    public LearnedState learned() { return learned; }

    /** True if this character has learned (or been granted) the given
     *  ability id. */
    public boolean knowsAbility(String abilityId) {
        return learned.knows(abilityId);
    }

    /** Add an ability id. Returns true if it wasn't already known. */
    public boolean learnAbility(String abilityId) {
        return learned.learn(abilityId);
    }

    /** Remove an ability id. Returns true if it was known. */
    public boolean forgetAbility(String abilityId) {
        return learned.forget(abilityId);
    }

    /** Unmodifiable view of the known-ability ids. */
    public Set<String> knownAbilities() {
        return learned.knownView();
    }

    /** Invested rating in a skill (0 when never trained). */
    public int skill(Skill s) {
        return learned.skill(s);
    }

    /** Set the invested rating of a skill (clamped to >= 0). */
    public void setSkill(Skill s, int value) {
        learned.setSkill(s, value);
    }

    /** Unmodifiable snapshot of invested skill ratings. */
    public Map<Skill, Integer> skills() {
        return learned.skillsView();
    }

    /** Unspent skill points available to allocate. */
    public int skillPoints() { return learned.skillPoints(); }

    public void setSkillPoints(int points) { learned.setSkillPoints(points); }

    public void addSkillPoints(int delta) { learned.addSkillPoints(delta); }

    /** Per-skill allocation cap, scaling with level (a Genin can be good but
     *  not legendary yet). */
    public int skillCap() { return SKILL_CAP_BASE + level; }

    /** Mastery 0..100 in a specific ability (0 when untrained). */
    public int abilityMastery(String abilityId) {
        return learned.mastery(abilityId);
    }

    /** Set mastery for an ability (clamped 0..100); establishes it as known. */
    public void setAbilityMastery(String abilityId, int value) {
        learned.setMastery(abilityId, value);
    }

    public void addAbilityMastery(String abilityId, int delta) {
        learned.addMastery(abilityId, delta);
    }

    /** Unmodifiable snapshot of per-ability mastery. */
    public Map<String, Integer> abilityMasteryView() {
        return learned.masteryView();
    }
}
