package fr.reborn.ost.audio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Métadonnées des pistes OST (titre réel, cover, durée), chargées depuis
 * {@code assets/reborn-ost/tracks.json}. Mapping {@code trackId → {title, cover,
 * duration}}. Non-destructif : les .ogg gardent leur nom ; ce fichier donne juste
 * le joli titre + la pochette à afficher.
 *
 * <p>Fallback : si une piste n'est pas listée (ou champ vide), on retombe sur le
 * nom de fichier prettifié et un cover par défaut.
 */
public final class OstTrackMeta {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/meta");
    private static final String RESOURCE = "/assets/reborn-ost/tracks.json";

    public record Entry(String title, String cover, int duration) {}

    private static final Map<String, Entry> META = load();

    private OstTrackMeta() {}

    private static Map<String, Entry> load() {
        Map<String, Entry> out = new HashMap<>();
        try (InputStream in = OstTrackMeta.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return out;
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : root.keySet()) {
                JsonObject o = root.getAsJsonObject(key);
                String title = o.has("title") ? o.get("title").getAsString() : "";
                String cover = o.has("cover") ? o.get("cover").getAsString() : "";
                int duration = o.has("duration") ? o.get("duration").getAsInt() : 0;
                out.put(key, new Entry(title, cover, duration));
            }
            LOGGER.info("OST meta : {} entrées", out.size());
        } catch (Exception e) {
            LOGGER.warn("tracks.json illisible : {}", e.getMessage());
        }
        return out;
    }

    /** Titre réel, ou le fallback (nom de fichier prettifié) si absent. */
    public static String title(String trackId, String fallback) {
        Entry e = META.get(trackId);
        return (e != null && e.title != null && !e.title.isBlank()) ? e.title : fallback;
    }

    /** Durée en secondes (0 = inconnue → on n'affiche rien). */
    public static int duration(String trackId) {
        Entry e = META.get(trackId);
        return e != null ? e.duration : 0;
    }

    /**
     * Identifier de la cover : {@code reborn-ost:textures/cover/<cover>.png}.
     * Utilise le champ {@code cover} s'il existe, sinon le nom de fichier de la
     * piste. {@code null} si pas de cover définissable.
     */
    public static Identifier coverTexture(OstTrack track) {
        Entry e = META.get(track.trackId());
        String name = (e != null && e.cover != null && !e.cover.isBlank())
            ? e.cover : track.fileName();
        if (name == null || name.isBlank()) return null;
        return Identifier.fromNamespaceAndPath("reborn-ost", "textures/cover/" + name + ".png");
    }

    /** Formate une durée en mm:ss, ou chaîne vide si 0. */
    public static String formatDuration(int seconds) {
        if (seconds <= 0) return "";
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
