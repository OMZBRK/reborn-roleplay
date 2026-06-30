package fr.reborn.ost.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Préférences utilisateur du mod OST — persiste dans
 * {@code config/reborn-ost.json}.
 *
 * <p>Encoding : Gson (déjà bundlé via Fabric Loader, pas de nouvelle
 * dep). On reste sur un objet plat sérialisable pour faciliter le diff
 * manuel.
 */
public final class OstConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/config");
    private static final String FILE_NAME = "reborn-ost.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Volume global 0..1. */
    private float volume = 0.5f;
    /** Radius par défaut pour les broadcasts manuels (utilisé par les slider UI). */
    private float broadcastDistance = 32f;
    /** Mode Solo : ignore les broadcasts serveur. OFF par défaut — l'UX
     *  attendue est que les joueurs entendent les broadcasts RP, et qu'ils
     *  opt-out explicitement via le toggle du menu OST si besoin. */
    private boolean soloMode = false;
    /** TrackIds favoris (preserve insertion order pour l'affichage). */
    private LinkedHashSet<String> favorites = new LinkedHashSet<>();
    /** Dernier trackId joué — restauré au prochain lancement si présent. */
    private String lastTrackId = null;
    /** Lecture aléatoire. */
    private boolean shuffle = false;
    /** Mode répétition : 0 = off, 1 = répète la piste, 2 = répète la liste. */
    private int repeatMode = 0;

    public static final int REPEAT_OFF = 0, REPEAT_ONE = 1, REPEAT_ALL = 2;

    public boolean isShuffle() { return shuffle; }
    public void setShuffle(boolean s) { this.shuffle = s; }

    public int getRepeatMode() { return repeatMode; }
    public void setRepeatMode(int m) { this.repeatMode = ((m % 3) + 3) % 3; }
    public void cycleRepeat() { setRepeatMode(repeatMode + 1); }

    public float getVolume() { return volume; }
    public void setVolume(float v) { this.volume = v; }

    public float getBroadcastDistance() { return broadcastDistance; }
    public void setBroadcastDistance(float d) { this.broadcastDistance = d; }

    public boolean isSoloMode() { return soloMode; }
    public void setSoloMode(boolean s) { this.soloMode = s; }

    public Set<String> getFavorites() {
        if (favorites == null) favorites = new LinkedHashSet<>();
        return favorites;
    }

    public boolean isFavorite(String trackId) {
        return favorites != null && favorites.contains(trackId);
    }

    public void toggleFavorite(String trackId) {
        if (favorites == null) favorites = new LinkedHashSet<>();
        if (!favorites.remove(trackId)) favorites.add(trackId);
    }

    public String getLastTrackId() { return lastTrackId; }
    public void setLastTrackId(String id) { this.lastTrackId = id; }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static OstConfig load() {
        Path path = defaultPath();
        if (!Files.exists(path)) {
            OstConfig fresh = new OstConfig();
            fresh.save();
            return fresh;
        }
        try {
            String json = Files.readString(path);
            OstConfig parsed = GSON.fromJson(json, OstConfig.class);
            if (parsed == null) parsed = new OstConfig();
            if (parsed.favorites == null) parsed.favorites = new LinkedHashSet<>();
            return parsed;
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("config invalide ({}), reset to defaults : {}", path, e.getMessage());
            return new OstConfig();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OstConfig that)) return false;
        return Float.compare(volume, that.volume) == 0
            && Float.compare(broadcastDistance, that.broadcastDistance) == 0
            && soloMode == that.soloMode
            && Objects.equals(favorites, that.favorites)
            && Objects.equals(lastTrackId, that.lastTrackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(volume, broadcastDistance, soloMode, favorites, lastTrackId);
    }
}
