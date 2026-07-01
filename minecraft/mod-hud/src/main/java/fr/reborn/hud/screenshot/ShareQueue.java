package fr.reborn.hud.screenshot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * File d'attente de partages « social feed ». Le mod n'a PAS le JWT Reborn
 * (il vit dans le keyring du launcher), donc il ne peut pas uploader lui-même.
 * Il empile juste les demandes dans {@code <gamedir>/reborn/pending-shares.json}
 * (tableau JSON de {@code {file, caption}}) ; le launcher (qui détient l'auth)
 * lit cette file, POST /v1/shots pour chaque entrée, puis la vide.
 *
 * <p>Même dossier de jeu que le launcher (game_dir partagé), même logique que
 * {@link ScreenshotLibrary} pour les favoris.
 */
public final class ShareQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/share");
    private static final Gson GSON = new Gson();

    private ShareQueue() {}

    private static Path queueFile() {
        return FabricLoader.getInstance().getGameDir().resolve("reborn").resolve("pending-shares.json");
    }

    /** Une demande de partage en attente de traitement par le launcher. */
    public record Pending(String file, String caption) {}

    private static List<Pending> load() {
        List<Pending> out = new ArrayList<>();
        Path f = queueFile();
        if (!Files.exists(f)) return out;
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray();
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                String file = o.has("file") ? o.get("file").getAsString() : null;
                if (file == null || file.isBlank()) continue;
                String caption = o.has("caption") && !o.get("caption").isJsonNull()
                    ? o.get("caption").getAsString() : "";
                out.add(new Pending(file, caption));
            }
        } catch (Exception e) {
            LOGGER.warn("file de partage illisible : {}", e.getMessage());
        }
        return out;
    }

    private static void save(List<Pending> queue) {
        try {
            Path f = queueFile();
            Files.createDirectories(f.getParent());
            JsonArray arr = new JsonArray();
            for (Pending p : queue) {
                JsonObject o = new JsonObject();
                o.addProperty("file", p.file());
                o.addProperty("caption", p.caption() == null ? "" : p.caption());
                arr.add(o);
            }
            Files.writeString(f, GSON.toJson(arr), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("écriture file de partage échec : {}", e.getMessage());
        }
    }

    /** Vrai si ce fichier est déjà en attente de partage. */
    public static boolean isPending(String fileName) {
        for (Pending p : load()) if (p.file().equals(fileName)) return true;
        return false;
    }

    /**
     * Empile une demande de partage (déduplique par nom de fichier : re-partager
     * met simplement à jour la légende). Le launcher la traitera à sa prochaine
     * ouverture / visite du feed.
     */
    public static void enqueue(String fileName, String caption) {
        List<Pending> queue = load();
        queue.removeIf(p -> p.file().equals(fileName));
        queue.add(new Pending(fileName, caption == null ? "" : caption.trim()));
        save(queue);
        LOGGER.info("partage en file : {} ({} en attente)", fileName, queue.size());
    }
}
