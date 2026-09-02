package com.reborn.shinobilearning.academy;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.data.YamlCharacterDataStore;
import com.reborn.shinobilearning.ShinobiLearning;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * YAML persistence for {@link AcademyData} — one file per character under
 * {@code data/academy/<characterUuid>.yml}. The cache + autosave + dirty-flag
 * lifecycle is inherited from {@link YamlCharacterDataStore}; this class
 * supplies only the (de)serialization and the character-name lookup.
 */
public final class AcademyStore extends YamlCharacterDataStore<AcademyData> {

    private final ShinobiLearning plugin;

    public AcademyStore(ShinobiLearning plugin) {
        super(plugin, "data/academy");
        this.plugin = plugin;
    }

    /** Resolve a character NAME across every loaded roster (like /tail does). */
    public ResolvedCharacter resolveCharacter(String name) {
        CharacterService characters = plugin.characters();
        if (characters == null) return null;
        var r = characters.resolveOwned(name);
        return r == null ? null : new ResolvedCharacter(r.ownerId(), r.character());
    }

    public record ResolvedCharacter(UUID ownerId, ShinobiCharacter character) {}

    /* ----------------------------------------------------- (de)serialization */

    @Override
    protected AcademyData create(UUID characterId) {
        return new AcademyData(characterId);
    }

    @Override
    protected void read(AcademyData data, YamlConfiguration yml) {
        Set<Lesson> done = EnumSet.noneOf(Lesson.class);
        for (String s : yml.getStringList("completed")) {
            Lesson l = Lesson.from(s);
            if (l != null) done.add(l);
        }
        data.load(yml.getBoolean("enrolled", false),
                yml.getBoolean("graduated", false), done);
    }

    @Override
    protected void write(AcademyData data, YamlConfiguration yml) {
        yml.set("enrolled", data.enrolled());
        yml.set("graduated", data.graduated());
        List<String> done = new ArrayList<>();
        for (Lesson l : data.completed()) done.add(l.name());
        yml.set("completed", done);
    }

    @Override
    protected boolean shouldPersist(AcademyData data, boolean fileExists) {
        // A pristine, never-enrolled shell isn't worth a file.
        return data.enrolled() || data.graduated()
                || !data.completed().isEmpty() || fileExists;
    }
}
