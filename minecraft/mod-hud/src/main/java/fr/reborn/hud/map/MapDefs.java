package fr.reborn.hud.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Géoréférencement des textures de carte embarquées :
 * {@code assets/reborn/maps/<id>.json} décrit {@code textures/gui/map/<id>.png}
 * (origine monde du pixel 0,0 et blocs par pixel). Généré par
 * {@code tools/map-render/stylize.py} à côté du PNG.
 */
public final class MapDefs {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/map");

    public record Def(Identifier texture, int originX, int originZ, int blocksPerPixel, int width, int height) {

        public float toPixelX(double worldX) { return (float) ((worldX - originX) / blocksPerPixel); }

        public float toPixelZ(double worldZ) { return (float) ((worldZ - originZ) / blocksPerPixel); }
    }

    private static final Map<String, Def> CACHE = new HashMap<>();

    private MapDefs() {}

    /** La carte {@code id}, ou null si le mod ne l'embarque pas (mod trop ancien). */
    public static Def get(String id) {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        Def d = load(id);
        CACHE.put(id, d);
        return d;
    }

    private static Def load(String id) {
        Identifier meta = Identifier.fromNamespaceAndPath("reborn", "maps/" + id + ".json");
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(meta);
            if (res.isEmpty()) return null;
            try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                return new Def(Identifier.fromNamespaceAndPath("reborn", "textures/gui/map/" + id + ".png"),
                    o.get("originX").getAsInt(), o.get("originZ").getAsInt(),
                    o.get("blocksPerPixel").getAsInt(), o.get("width").getAsInt(), o.get("height").getAsInt());
            }
        } catch (Exception e) {
            LOGGER.warn("carte {} illisible : {}", id, e.getMessage());
            return null;
        }
    }
}
