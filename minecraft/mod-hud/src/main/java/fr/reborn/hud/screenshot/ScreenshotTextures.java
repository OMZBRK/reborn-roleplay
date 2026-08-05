package fr.reborn.hud.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Charge les PNG de screenshots en textures GL, à la demande, avec cache. Doit
 * être appelé depuis le render thread (création GL). {@link #clear()} libère les
 * textures (à la fermeture de la gallery) pour ne pas garder de gros PNG en RAM.
 */
public final class ScreenshotTextures {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/shots-tex");

    public record Tex(Identifier id, int w, int h) {}

    private static final Map<String, Tex> CACHE = new HashMap<>();

    private ScreenshotTextures() {}

    /** Texture du screenshot (chargée si besoin), ou null si illisible. */
    public static Tex get(Path path) {
        String name = path.getFileName().toString();
        if (CACHE.containsKey(name)) return CACHE.get(name);
        Tex tex = load(path, name);
        CACHE.put(name, tex);
        return tex;
    }

    private static Tex load(Path path, String name) {
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage img = NativeImage.read(in);
            NativeImageBackedTexture t = new NativeImageBackedTexture(img);
            Identifier id = Identifier.fromNamespaceAndPath("reborn-shots", "g/" + sanitize(name));
            Minecraft.getInstance().getTextureManager().registerTexture(id, t);
            return new Tex(id, img.width(), img.getHeight());
        } catch (Exception e) {
            LOGGER.warn("load {} échec : {}", name, e.getMessage());
            return null;
        }
    }

    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Tex t : CACHE.values()) {
            if (t != null) tm.destroyTexture(t.id());
        }
        CACHE.clear();
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }
}
