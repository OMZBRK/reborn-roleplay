package com.reborn.shinobicore.character.repository;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.Affinity;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.NinjaArt;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One YAML file per owner UUID at {@code plugins/ShinobiCore/characters/<uuid>.yml}.
 * Each character lives under a section keyed by its own UUID:
 *
 * <pre>
 * 4f56...:
 *   name: Kakashi
 *   clan: Hatake
 *   level: 14
 *   chakra-affinities: [KATON, RAITON]
 *   last-position:
 *     world: world
 *     x: 12.5
 *     ...
 * </pre>
 *
 * <p>The loader reads both the new list form {@code chakra-affinities} and the
 * legacy single-value form {@code chakra-affinity} so old save files continue
 * to work.
 */
public class YamlCharacterRepository implements CharacterRepository {

    private final ShinobiCore plugin;
    private final File dir;

    public YamlCharacterRepository(ShinobiCore plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "characters");
    }

    @Override
    public void init() {
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create character data folder: " + dir);
        }
    }

    @Override
    public void close() { /* flushes happen per-save; nothing to do */ }

    /* ---------------------------------------------------------------- load */

    @Override
    public List<ShinobiCharacter> loadAll(UUID owner) {
        File f = fileFor(owner);
        if (!f.isFile()) return new ArrayList<>();

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        backupIfLegacyLearnedShape(f, cfg);
        List<ShinobiCharacter> out = new ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection s = cfg.getConfigurationSection(key);
            if (s == null) continue;
            try {
                out.add(fromSection(owner, UUID.fromString(key), s));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping malformed character '" + key + "' for " + owner + ": " + e.getMessage());
            }
        }
        return out;
    }

    /**
     * One-time safety net for the learned-state unification: if the file
     * still carries the legacy {@code known-abilities}/{@code ability-mastery}
     * shape (pre-unification), copy it to {@code <name>.bak} before the next
     * save rewrites it into the {@code learned:} shape. Idempotent — skipped
     * once the backup exists.
     */
    private void backupIfLegacyLearnedShape(File f, YamlConfiguration cfg) {
        File bak = new File(f.getParentFile(), f.getName() + ".bak");
        if (bak.exists()) return;
        boolean legacy = false;
        for (String key : cfg.getKeys(false)) {
            if (cfg.contains(key + ".known-abilities")
                    || cfg.contains(key + ".ability-mastery")) {
                legacy = true;
                break;
            }
        }
        if (!legacy) return;
        try {
            java.nio.file.Files.copy(f.toPath(), bak.toPath());
            plugin.getLogger().info("Learned-state migration: backed up "
                    + f.getName() + " to " + bak.getName() + ".");
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Could not back up " + f.getName()
                    + " before learned-state migration: " + ex.getMessage());
        }
    }

    private ShinobiCharacter fromSection(UUID owner, UUID id, ConfigurationSection s) {
        // Multi-affinity — read the new list; fall back to the legacy single key.
        Set<ChakraAffinity> affinities = new LinkedHashSet<>();
        List<String> list = s.getStringList("chakra-affinities");
        if (list != null && !list.isEmpty()) {
            for (String a : list) {
                ChakraAffinity parsed = ChakraAffinity.from(a);
                if (parsed != ChakraAffinity.NONE) affinities.add(parsed);
            }
        } else if (s.isString("chakra-affinity")) {
            ChakraAffinity parsed = ChakraAffinity.from(s.getString("chakra-affinity"));
            if (parsed != ChakraAffinity.NONE) affinities.add(parsed);
        }

        String lastWorld = s.getString("last-position.world", null);
        double lx = s.getDouble("last-position.x", 0.0);
        double ly = s.getDouble("last-position.y", 0.0);
        double lz = s.getDouble("last-position.z", 0.0);
        float  lyaw   = (float) s.getDouble("last-position.yaw", 0.0);
        float  lpitch = (float) s.getDouble("last-position.pitch", 0.0);

        ShinobiCharacter c = new ShinobiCharacter(
                id,
                owner,
                s.getString("name", "Shinobi"),
                s.getString("clan", "None"),
                s.getInt("age", 12),
                s.getDouble("size", 1.0),
                NinjaArt.from(s.getString("ninja-art")),
                Affinity.from(s.getString("affinity")),
                affinities,
                Rank.from(s.getString("rank")),
                s.getInt("leaf-test-rolls-used", 0),
                s.getInt("level", 1),
                s.getDouble("experience", 0.0),
                s.getDouble("current-hp", -1.0),
                s.getDouble("current-chakra", -1.0),
                lastWorld, lx, ly, lz, lyaw, lpitch
        );

        // Champs ajoutés après coup (hors constructeur pour ne pas casser sa signature).
        c.setVillage(s.getString("village", ""));
        c.setSexe(s.getString("sexe", "Homme"));
        c.setAppearance(s.getString("appearance", ""));
        c.setRyo(s.getLong("ryo", 0L));
        for (String oid : s.getStringList("owned-outfits")) c.addOwnedOutfit(oid);

        // disabled-mobility-slots block is no longer read — the mobility
        // toggle system was deleted. Legacy keys in saved YAML files are
        // silently ignored.

        // Contact book — who this character knows, and how. Stored as a map
        // of "<subjectCharUuid>": { "display": "...", "nickname": bool }.
        ConfigurationSection contacts = s.getConfigurationSection("contacts");
        if (contacts != null) {
            for (String key : contacts.getKeys(false)) {
                ConfigurationSection row = contacts.getConfigurationSection(key);
                if (row == null) continue;
                String display = row.getString("display");
                boolean isNick = row.getBoolean("nickname", false);
                if (display == null || display.isBlank()) continue;
                try {
                    c.remember(UUID.fromString(key), display, isNick);
                } catch (IllegalArgumentException ignored) { /* bad uuid key */ }
            }
        }

        // Saved nicknames pool — plain list of strings.
        for (String nick : s.getStringList("saved-nicknames")) {
            c.saveNickname(nick);
        }
        // Learned abilities — unified shape: one map, ability id →
        // mastery 0..100, where a key present == known. Falls back to
        // the legacy pair (known-abilities list + ability-mastery
        // section) for pre-migration saves; legacy mastery entries for
        // never-learned abilities become learned (the unified model
        // has no "mastery without knowing" — see LearnedState).
        ConfigurationSection learnedSec = s.getConfigurationSection("learned");
        if (learnedSec != null) {
            for (String aid : learnedSec.getKeys(false)) {
                c.setAbilityMastery(aid, learnedSec.getInt(aid, 0));
            }
        } else {
            for (String aid : s.getStringList("known-abilities")) {
                c.learnAbility(aid);
            }
        }

        // Skills & unspent points (the /sc roll system).
        c.setSkillPoints(s.getInt("skill-points", 0));
        ConfigurationSection skillsSec = s.getConfigurationSection("skills");
        if (skillsSec != null) {
            for (String sk : skillsSec.getKeys(false)) {
                Skill parsed = Skill.from(sk);
                if (parsed != null) c.setSkill(parsed, skillsSec.getInt(sk, 0));
            }
        }

        // Legacy per-ability mastery (0..100) — only when the unified
        // learned: section wasn't present above.
        if (learnedSec == null) {
            ConfigurationSection masterySec = s.getConfigurationSection("ability-mastery");
            if (masterySec != null) {
                for (String aid : masterySec.getKeys(false)) {
                    c.setAbilityMastery(aid, masterySec.getInt(aid, 0));
                }
            }
        }

        // Friends — list of character-UUID strings. Mutual graph, maintained
        // by FriendshipManager; we just load the edges we stored for this side.
        for (String fid : s.getStringList("friends")) {
            try { c.addFriend(UUID.fromString(fid)); }
            catch (IllegalArgumentException ignored) { /* bad uuid */ }
        }

        // Welcome / reminder toggle. Stored under "welcome-disabled".
        // Defaults to false (welcome enabled) so fresh characters see
        // the message on first selection. Old saves with the legacy
        // "welcomed" key are ignored — those characters will simply
        // see the message again on next select, no harm done.
        c.setWelcomeDisabled(s.getBoolean("welcome-disabled", false));

        // First-play cinematic intro — default false so brand-new characters
        // run the designated intro once; flipped true on completion.
        c.setIntroSeen(s.getBoolean("intro-seen", false));

        // Death flag (set by validated /tuer requests; cleared by
        // /character edit). Default false so fresh characters are
        // selectable; older saves without the key behave the same way.
        c.setDead(s.getBoolean("dead", false));

        // jutsu-bindings block is no longer read — the jutsu system was
        // deleted. The new ShinobiAbilities plugin owns its own per-
        // character binding storage. Legacy keys in saved YAML files are
        // silently ignored.

        // Injuries — list of YAML maps, each carrying body part /
        // type / origin / severity / timestamps. Malformed entries are
        // silently skipped so a corrupted single row doesn't lose the
        // rest of the wound history.
        ConfigurationSection injSec = s.getConfigurationSection("injuries");
        if (injSec != null) {
            for (String iKey : injSec.getKeys(false)) {
                ConfigurationSection inj = injSec.getConfigurationSection(iKey);
                if (inj == null) continue;
                com.reborn.shinobicore.ko.injury.Injury parsed =
                        com.reborn.shinobicore.ko.injury.Injury.readFrom(inj);
                if (parsed != null) c.addInjury(parsed);
            }
        }

        // Per-character inventory snapshot. Stored under `inventory.<slot>`
        // as a Bukkit-serialised ItemStack. Absent section = character
        // predates the feature; hasSavedInventory stays false so the
        // first activation seeds from whatever the player currently
        // holds instead of wiping them.
        ConfigurationSection invSec = s.getConfigurationSection("inventory");
        if (invSec != null) {
            ItemStack[] snap = new ItemStack[ShinobiCharacter.INVENTORY_SIZE];
            for (int i = 0; i < snap.length; i++) {
                // ItemStack instances round-trip through YAML via the
                // ConfigurationSerializable interface — YamlConfiguration
                // gives us the rebuilt ItemStack directly.
                Object raw = invSec.get(String.valueOf(i));
                if (raw instanceof ItemStack stack) snap[i] = stack;
            }
            c.setInventorySnapshot(snap);
        }
        // Legacy ability-bindings + disabled-mobility-slots blocks are
        // no longer read — the mobility toggle system was deleted along
        // with the abilities themselves.

        // Loading is not a mutation — start clean so the autosave only
        // writes this character once something actually changes.
        c.markClean();
        return c;
    }

    /* ---------------------------------------------------------------- save */

    @Override
    public void save(ShinobiCharacter c) {
        File f = fileFor(c.ownerId());
        YamlConfiguration cfg = f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();

        String key = c.id().toString();
        cfg.set(key + ".name", c.name());
        cfg.set(key + ".clan", c.clan());
        cfg.set(key + ".village", c.village().isBlank() ? null : c.village());
        cfg.set(key + ".sexe", "Homme".equals(c.sexe()) ? null : c.sexe());
        cfg.set(key + ".appearance", c.appearance().isBlank() ? null : c.appearance());
        cfg.set(key + ".ryo", c.ryo() == 0L ? null : c.ryo());
        cfg.set(key + ".owned-outfits", c.ownedOutfits().isEmpty()
                ? null : new ArrayList<>(c.ownedOutfits()));
        cfg.set(key + ".age", c.age());
        cfg.set(key + ".size", c.size());
        cfg.set(key + ".ninja-art", c.ninjaArt().name());
        cfg.set(key + ".affinity", c.affinity().name());
        cfg.set(key + ".rank", c.rank().name());

        // Multi-affinity: write list form; drop the legacy single-value key so
        // we don't ship stale data from old saves.
        List<String> affNames = new ArrayList<>();
        for (ChakraAffinity a : c.chakraAffinities()) affNames.add(a.name());
        cfg.set(key + ".chakra-affinities", affNames);
        cfg.set(key + ".chakra-affinity", null);

        cfg.set(key + ".leaf-test-rolls-used", c.leafTestRollsUsed());
        cfg.set(key + ".level", c.level());
        cfg.set(key + ".experience", c.experience());
        cfg.set(key + ".current-hp", c.currentHp());
        cfg.set(key + ".current-chakra", c.chakra().current());

        if (c.hasLastPosition()) {
            cfg.set(key + ".last-position.world", c.lastWorldName());
            cfg.set(key + ".last-position.x", c.lastX());
            cfg.set(key + ".last-position.y", c.lastY());
            cfg.set(key + ".last-position.z", c.lastZ());
            cfg.set(key + ".last-position.yaw", c.lastYaw());
            cfg.set(key + ".last-position.pitch", c.lastPitch());
        } else {
            cfg.set(key + ".last-position", null);
        }

        // Mobility toggles: persist only the slots the player has *disabled*.
        // Absent-means-enabled keeps the YAML file small and self-explanatory.
        // We also null out the legacy `ability-bindings` map so old saves are
        // migrated cleanly and we never ship stale binding data.
        // Legacy mobility-slot blocks: clear them out of saved files so
        // upgrades don't carry stale data, but write nothing new.
        cfg.set(key + ".ability-bindings", null);
        cfg.set(key + ".disabled-mobility-slots", null);

        // Contact book — persist as a map so readability stays OK in YAML.
        // Writes each entry under `contacts.<subjectUuid>` with display +
        // nickname flag. Null the whole section first so deletions propagate.
        cfg.set(key + ".contacts", null);
        for (var entry : c.contacts().entrySet()) {
            String sub = key + ".contacts." + entry.getKey();
            cfg.set(sub + ".display", entry.getValue().display());
            cfg.set(sub + ".nickname", entry.getValue().isNickname());
        }

        // Saved nickname pool — just a list of strings.
        List<String> nicks = new ArrayList<>(c.savedNicknames());
        cfg.set(key + ".saved-nicknames", nicks.isEmpty() ? null : nicks);

        // Learned abilities — unified map (id → mastery, 0 included so a
        // known-but-untrained ability survives the round-trip). The legacy
        // known-abilities / ability-mastery keys are purged below.
        cfg.set(key + ".learned", null);
        for (var e : c.learned().masteryView().entrySet()) {
            cfg.set(key + ".learned." + e.getKey(), e.getValue());
        }
        cfg.set(key + ".known-abilities", null);

        // Skills & unspent points (the /sc roll system).
        cfg.set(key + ".skill-points", c.skillPoints() > 0 ? c.skillPoints() : null);
        cfg.set(key + ".skills", null);
        for (var e : c.skills().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                cfg.set(key + ".skills." + e.getKey().name(), e.getValue());
            }
        }

        // Legacy mastery section — purged; lives in learned: now.
        cfg.set(key + ".ability-mastery", null);

        // Friends — character-UUID strings. The graph is symmetric; the
        // other side's YAML carries the mirroring edge.
        List<String> friendIds = new ArrayList<>();
        for (UUID friendId : c.friends()) friendIds.add(friendId.toString());
        cfg.set(key + ".friends", friendIds.isEmpty() ? null : friendIds);

        // Welcome-disabled toggle (only persisted when true so fresh
        // characters' YAML stays clean of false defaults). Also wipe
        // the legacy "welcomed" key from old saves on next write.
        cfg.set(key + ".welcomed", null);
        cfg.set(key + ".welcome-disabled", c.welcomeDisabled() ? true : null);
        cfg.set(key + ".intro-seen", c.introSeen() ? true : null);

        // Death flag — same "only persist when true" pattern.
        cfg.set(key + ".dead", c.dead() ? true : null);

        // Legacy jutsu-bindings block: clear out of saved files. The new
        // ShinobiAbilities plugin owns its own per-character storage.
        cfg.set(key + ".jutsu-bindings", null);

        // Injuries — wipe the section first so removed wounds disappear
        // from disk, then re-write each one keyed by the injury's UUID
        // so editing the YAML by hand stays unambiguous.
        cfg.set(key + ".injuries", null);
        if (!c.injuries().isEmpty()) {
            for (com.reborn.shinobicore.ko.injury.Injury inj : c.injuries()) {
                String iKey = key + ".injuries." + inj.id();
                cfg.createSection(iKey);
                inj.writeTo(cfg.getConfigurationSection(iKey));
            }
        }

        // Inventory snapshot. Blank section out first so removed items
        // don't linger across saves. Only write when the character has
        // ever had their inventory captured — otherwise we'd clobber a
        // valid "inherit on first use" state with an empty section.
        cfg.set(key + ".inventory", null);
        if (c.hasSavedInventory()) {
            ItemStack[] snap = c.inventorySnapshot();
            for (int i = 0; i < snap.length; i++) {
                if (snap[i] != null) cfg.set(key + ".inventory." + i, snap[i]);
            }
        }

        // Async write — serialize on the main thread (where the
        // character data we just touched is consistent) then push the
        // String to disk on a background thread. YamlConfiguration is
        // not thread-safe, so the cfg object never crosses threads;
        // only the produced String + target File do.
        //
        // Ordering: per-file writes are serialised through writeAsync's
        // synchronized lock so two saves for the same character can
        // never race on the file handle. Different characters write in
        // parallel which is exactly what we want.
        String serialized;
        try {
            serialized = cfg.saveToString();
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to serialize character " + c.id(), ex);
            return;
        }
        writeAsync(f, serialized, c.id());
    }

    /** Per-file lock so concurrent saves of the same character serialise.
     *  Different characters lock independently so writes parallelise. */
    private final java.util.concurrent.ConcurrentMap<String, Object> fileLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    private void writeAsync(File f, String serialized, UUID charId) {
        Object lock = fileLocks.computeIfAbsent(f.getAbsolutePath(), k -> new Object());
        // Shutdown safety: BukkitScheduler silently cancels any async
        // task scheduled after onDisable. If the plugin is disabling
        // (or already disabled), fall back to a synchronous write so
        // the server-stop save actually hits disk.
        if (!plugin.isEnabled()) {
            synchronized (lock) {
                writeFile(f, serialized, charId);
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (lock) {
                writeFile(f, serialized, charId);
            }
        });
    }

    private void writeFile(File f, String serialized, UUID charId) {
        try {
            java.nio.file.Files.writeString(f.toPath(), serialized,
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Save failed for character " + charId, ex);
        }
    }

    /* -------------------------------------------------------------- delete */

    @Override
    public void delete(UUID owner, UUID characterId) {
        File f = fileFor(owner);
        if (!f.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        cfg.set(characterId.toString(), null);
        try {
            if (cfg.getKeys(false).isEmpty()) {
                if (!f.delete()) plugin.getLogger().warning("Could not delete empty " + f);
            } else {
                cfg.save(f);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete character " + characterId, ex);
        }
    }

    /* -------------------------------------------------------------- helpers */

    private File fileFor(UUID owner) {
        return new File(dir, owner + ".yml");
    }

    // Silence unused-import lint for EnumSet; it's here if you want to extend.
    @SuppressWarnings("unused")
    private static final Set<ChakraAffinity> KEEP_IMPORT = EnumSet.noneOf(ChakraAffinity.class);
}
