package fr.reborn.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Préférences utilisateur du mod HUD — persiste dans
 * {@code config/reborn-hud.json}.
 *
 * <p>Stocke un {@link HudElementState} par {@link HudElement} (position +
 * scale + visible). Les clés du JSON sont les {@link HudElement#id()}
 * stables (string keys) pour résister à un renommage interne de l'enum.
 */
public final class HudConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/config");
    private static final String FILE_NAME = "reborn-hud.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, HudElementState> states = new HashMap<>();

    public HudElementState stateOf(HudElement element) {
        if (states == null) return HudElementState.DEFAULT;
        return states.getOrDefault(element.id(), HudElementState.DEFAULT);
    }

    public void setState(HudElement element, HudElementState state) {
        if (states == null) states = new HashMap<>();
        states.put(element.id(), state);
    }

    public void resetAll() {
        if (states != null) states.clear();
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static HudConfig load() {
        Path path = defaultPath();
        if (!Files.exists(path)) {
            HudConfig fresh = new HudConfig();
            fresh.save();
            return fresh;
        }
        try {
            String json = Files.readString(path);
            HudConfig parsed = GSON.fromJson(json, HudConfig.class);
            if (parsed == null) parsed = new HudConfig();
            if (parsed.states == null) parsed.states = new HashMap<>();
            return parsed;
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("config invalide ({}), reset to defaults : {}", path, e.getMessage());
            return new HudConfig();
        }
    }

    public synchronized void save() {
        Path path = defaultPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("config save echec ({}) : {}", path, e.getMessage());
        }
    }
}
