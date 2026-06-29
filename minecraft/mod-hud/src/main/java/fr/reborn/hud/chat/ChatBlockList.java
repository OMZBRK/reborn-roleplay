package fr.reborn.hud.chat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Liste de joueurs bloqués côté client : leurs messages sont masqués dans le
 * chat. Persistée en JSON dans {@code <config>/reborn_chat_blocklist.json}.
 * Comparaison insensible à la casse. Géré via les commandes client
 * {@code /rblock}, {@code /runblock}, {@code /rblocklist}.
 */
public final class ChatBlockList {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/chat-block");
    public static final ChatBlockList INSTANCE = new ChatBlockList();
    private static final Gson GSON = new Gson();
    private static final String FILENAME = "reborn_chat_blocklist.json";

    private final Set<String> blocked = new LinkedHashSet<>(); // stocké en minuscules
    private boolean loaded = false;

    private ChatBlockList() {}

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = path();
        if (path == null || !Files.exists(path)) return;
        try {
            Type t = new TypeToken<Set<String>>() {}.getType();
            Set<String> data = GSON.fromJson(Files.readString(path), t);
            if (data != null) {
                for (String s : data) {
                    if (s != null) blocked.add(s.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("failed to load blocklist: {}", e.getMessage());
        }
    }

    public boolean isBlocked(String name) {
        ensureLoaded();
        return name != null && blocked.contains(name.toLowerCase(Locale.ROOT));
    }

    /** @return true si ajouté (false si déjà bloqué ou nom vide). */
    public boolean block(String name) {
        ensureLoaded();
        if (name == null || name.isBlank()) return false;
        boolean added = blocked.add(name.toLowerCase(Locale.ROOT));
        if (added) save();
        return added;
    }

    /** @return true si retiré. */
    public boolean unblock(String name) {
        ensureLoaded();
        if (name == null) return false;
        boolean removed = blocked.remove(name.toLowerCase(Locale.ROOT));
        if (removed) save();
        return removed;
    }

    public Set<String> names() {
        ensureLoaded();
        return new LinkedHashSet<>(blocked);
    }

    private void save() {
        Path path = path();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(blocked));
        } catch (IOException e) {
            LOG.warn("failed to save blocklist: {}", e.getMessage());
        }
    }

    private Path path() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
        } catch (Exception e) {
            return null;
        }
    }
}
