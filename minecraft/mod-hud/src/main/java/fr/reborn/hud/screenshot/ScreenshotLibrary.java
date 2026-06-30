package fr.reborn.hud.screenshot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bibliothèque des screenshots du joueur : scanne {@code <gamedir>/screenshots/}
 * et gère la liste des favoris (persistés dans
 * {@code <gamedir>/reborn/screenshots-fav.json}).
 */
public final class ScreenshotLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/shots");
    private static final Gson GSON = new Gson();

    public record Entry(Path path, String name, long modified) {}

    private static Set<String> favorites;

    private ScreenshotLibrary() {}

    public static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("screenshots");
    }

    /** Liste les .png, plus récents d'abord. {@code onlyFavorites} filtre. */
    public static List<Entry> list(boolean onlyFavorites) {
        List<Entry> out = new ArrayList<>();
        Path dir = dir();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (onlyFavorites && !isFavorite(name)) continue;
                long mod;
                try { mod = Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { mod = 0; }
                out.add(new Entry(p, name, mod));
            }
        } catch (IOException e) {
            LOGGER.warn("scan screenshots échec : {}", e.getMessage());
        }
        out.sort(Comparator.comparingLong(Entry::modified).reversed());
        return out;
    }

    // ─── Favoris ───

    private static Path favFile() {
        return FabricLoader.getInstance().getGameDir().resolve("reborn").resolve("screenshots-fav.json");
    }

    private static Set<String> favs() {
        if (favorites == null) favorites = loadFavorites();
        return favorites;
    }

    public static boolean isFavorite(String name) { return favs().contains(name); }

    public static void toggleFavorite(String name) {
        if (!favs().remove(name)) favs().add(name);
        saveFavorites();
    }

    public static void delete(Entry e) {
        try {
            Files.deleteIfExists(e.path());
            favs().remove(e.name());
            saveFavorites();
        } catch (IOException ex) {
            LOGGER.warn("suppression {} échec : {}", e.name(), ex.getMessage());
        }
    }

    private static Set<String> loadFavorites() {
        Set<String> set = new LinkedHashSet<>();
        Path f = favFile();
        if (!Files.exists(f)) return set;
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray();
            arr.forEach(el -> set.add(el.getAsString()));
        } catch (Exception e) {
            LOGGER.warn("favoris illisibles : {}", e.getMessage());
        }
        return set;
    }

    private static void saveFavorites() {
        try {
            Path f = favFile();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(favs()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("save favoris échec : {}", e.getMessage());
        }
    }
}
