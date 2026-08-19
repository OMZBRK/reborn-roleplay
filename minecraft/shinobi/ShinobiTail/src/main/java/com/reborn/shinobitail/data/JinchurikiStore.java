package com.reborn.shinobitail.data;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.data.YamlCharacterDataStore;
import com.reborn.shinobitail.ShinobiTail;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * YAML persistence for {@link JinchurikiData} — one file per character
 * under {@code data/jinchuriki/<characterUuid>.yml}. The cache + autosave +
 * dirty-flag lifecycle is inherited from {@link YamlCharacterDataStore};
 * this class supplies the (de)serialization and the "who is a jinchūriki
 * right now" lookups that bridge ShinobiCore characters to beast data.
 */
public final class JinchurikiStore extends YamlCharacterDataStore<JinchurikiData> {

    private final ShinobiTail plugin;

    public JinchurikiStore(ShinobiTail plugin) {
        super(plugin, "data/jinchuriki");
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------- lookups */

    /** Data for an online player's ACTIVE character, or {@code null} when
     *  no character is selected or no beast is bound. */
    public JinchurikiData ofActive(Player player) {
        CharacterService characters = plugin.characters();
        if (characters == null) return null;
        ShinobiCharacter active = characters.getActive(player.getUniqueId());
        if (active == null) return null;
        JinchurikiData data = of(active.id());
        return data.beastId() != null ? data : null;
    }

    /** Active character of an online player (ShinobiCore passthrough). */
    public ShinobiCharacter activeCharacter(Player player) {
        CharacterService characters = plugin.characters();
        return characters == null ? null : characters.getActive(player.getUniqueId());
    }

    /**
     * Resolve a character NAME across every loaded roster, exactly like
     * {@code /sc heal} does. Returns {@code null} when nothing matches.
     */
    public ResolvedCharacter resolveCharacter(String name) {
        CharacterService characters = plugin.characters();
        if (characters == null) return null;
        var r = characters.resolveOwned(name);
        return r == null ? null : new ResolvedCharacter(r.ownerId(), r.character());
    }

    public record ResolvedCharacter(UUID ownerId, ShinobiCharacter character) {}

    /** Resolve a loaded character by its UUID (GUI + death marking). */
    public ResolvedCharacter resolveById(UUID characterId) {
        CharacterService characters = plugin.characters();
        if (characters == null) return null;
        var r = characters.resolveOwnedById(characterId);
        return r == null ? null : new ResolvedCharacter(r.ownerId(), r.character());
    }

    /* ----------------------------------------------------- (de)serialization */

    @Override
    protected JinchurikiData create(UUID characterId) {
        return new JinchurikiData(characterId);
    }

    @Override
    protected void read(JinchurikiData data, YamlConfiguration yml) {
        Map<Integer, Double> mastery = new LinkedHashMap<>();
        ConfigurationSection m = yml.getConfigurationSection("mastery");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                try {
                    mastery.put(Integer.parseInt(key), m.getDouble(key));
                } catch (NumberFormatException ignored) { }
            }
        }
        data.loadValues(
                yml.getString("beast", null),
                yml.getDouble("trust", 0),
                yml.getDouble("anger", 0),
                yml.getDouble("cooperation", 0),
                yml.getDouble("influence", 50),
                yml.getDouble("rage", 0),
                mastery,
                yml.getInt("stats.transformations", 0),
                yml.getInt("stats.resist-successes", 0),
                yml.getInt("stats.resist-failures", 0),
                yml.getLong("stats.seconds-transformed", 0),
                yml.getInt("progress.highest-stage-reached", 0),
                yml.getBoolean("progress.final-release", false),
                yml.getBoolean("progress.seal-removed", false));
    }

    @Override
    protected void write(JinchurikiData data, YamlConfiguration yml) {
        yml.set("beast", data.beastId());
        yml.set("trust", data.trust());
        yml.set("anger", data.anger());
        yml.set("cooperation", data.cooperation());
        yml.set("influence", data.influence());
        yml.set("rage", data.rage());
        for (Map.Entry<Integer, Double> e : data.masteryView().entrySet()) {
            yml.set("mastery." + e.getKey(), e.getValue());
        }
        yml.set("stats.transformations", data.transformations());
        yml.set("stats.resist-successes", data.resistSuccesses());
        yml.set("stats.resist-failures", data.resistFailures());
        yml.set("stats.seconds-transformed", data.secondsTransformed());
        yml.set("progress.highest-stage-reached", data.highestStageReached());
        yml.set("progress.final-release", data.finalReleaseReached());
        yml.set("progress.seal-removed", data.sealRemoved());
    }

    @Override
    protected boolean shouldPersist(JinchurikiData data, boolean fileExists) {
        // Unbound + pristine shells are not worth a file.
        return data.beastId() != null || fileExists;
    }

    /* --------------------------------------------------------------- admin */

    /** Deletes the binding and wipes the file — full wipe is explicit GM
     *  intent; mastery history goes with it. */
    public void unbind(UUID characterId) {
        JinchurikiData d = of(characterId);
        d.setBeastId(null);
        File f = fileFor(characterId);
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Could not delete " + f.getName());
        }
        evict(characterId);
    }

    /**
     * Reset a character's jinchūriki progression back to a freshly-sealed
     * state. Unlike {@link #unbind}, the beast stays bound and the file is
     * rewritten rather than deleted — stage, mastery, relationship, rage,
     * progression flags and lifetime stats all return to zero.
     */
    public void resetProgress(UUID characterId) {
        JinchurikiData d = of(characterId);
        if (d.beastId() == null) return;
        d.reset();
        save(d);
    }
}
