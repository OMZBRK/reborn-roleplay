package fr.reborn.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementOffset;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Préférences utilisateur du mod HUD — persiste dans
 * {@code config/reborn-hud.json}.
 *
 * <p>Stocke un offset (x, y) par {@link HudElement}. {@code (0,0)} = position
 * vanilla. Les clés du JSON sont les {@link HudElement#id()} stables (pas
 * les noms enum) pour résister à un renommage interne.
 */
public final class HudConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/config");
    private static final String FILE_NAME = "reborn-hud.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Map id → {x,y}. Volontairement string keys pour stabilité du JSON. */
    private Map<String, HudElementOffset> offsets = new java.util.HashMap<>();

    public HudElementOffset offsetOf(HudElement element) {
        if (offsets == null) return HudElementOffset.ZERO;
        return offsets.getOrDefault(element.id(), HudElementOffset.ZERO);
    }

    public void setOffset(HudElement element, HudElementOffset offset) {
        if (offsets == null) offsets = new java.util.HashMap<>();
        offsets.put(element.id(), offset);
    }

    public void resetAll() {
        if (offsets != null) offsets.clear();
    }

    public Map<HudElement, HudElementOffset> snapshot() {
        EnumMap<HudElement, HudElementOffset> out = new EnumMap<>(HudElement.class);
        for (HudElement e : HudElement.values()) {
            out.put(e, offsetOf(e));
        }
        return out;
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
            if (parsed.offsets == null) parsed.offsets = new java.util.HashMap<>();
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
