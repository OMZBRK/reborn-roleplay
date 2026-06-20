package fr.reborn.hud.menu.widget;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Gestion du dossier cache contenant les assets de Dynamic Animated Player
 * (bbmodel_viewer.html + JS + .glb + textures HDRI). Les assets sont
 * bundlés dans le jar du mod ; pour que MCEF puisse les charger via
 * {@code file://}, on les extrait sur disque au premier lancement (et a
 * chaque bump de {@link #ASSETS_VERSION}) dans
 * {@code <game-dir>/reborn-hud-cache/dynamic-player/}.
 *
 * <p>L'extraction est idempotente : si la version disque match la version
 * code, on skip et on retourne directement le path du HTML viewer.
 */
public final class DynamicPlayerAssets {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/dyn-assets");

    /** Bump quand on modifie un des fichiers bundlés -> force l'extraction. */
    private static final String ASSETS_VERSION = "2";

    /** Liste explicite des assets à extraire. Pas de scan recursif du jar
     *  (lent + casse en runClient ou les resources viennent du dossier dev). */
    private static final List<String> ASSETS = List.of(
        "bbmodel_viewer.html",
        "README.txt",
        "js/config.js",
        "js/lighting_debug.js",
        "js/model.js",
        "js/scene.js",
        "js/skin.js",
        "models/idle_player.bbmodel",
        "models/idle_player.glb",
        "textures/blue_hdri.hdr",
        "textures/cutout.png",
        "textures/hdri.hdr",
        "textures/sunrise1.png",
        "textures/sunrise2.png",
        "textures/sunset1.png",
        "textures/sunset2.png"
    );

    private DynamicPlayerAssets() {}

    /**
     * Extrait les assets si nécessaire et retourne le path absolu du
     * {@code bbmodel_viewer.html} à passer à MCEF.
     *
     * @return path absolu vers le HTML viewer, ou {@code null} si une
     *         extraction a échoué (manquant dans le jar, IO error, etc.).
     */
    public static Path extractIfNeeded() {
        try {
            Path cacheRoot = FabricLoader.getInstance().getGameDir()
                .resolve("reborn-hud-cache")
                .resolve("dynamic-player");
            Path versionFile = cacheRoot.resolve(".version");

            if (Files.exists(versionFile)) {
                String cached = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
                if (ASSETS_VERSION.equals(cached)) {
                    Path html = cacheRoot.resolve("bbmodel_viewer.html");
                    if (Files.exists(html)) {
                        LOG.info("assets deja extraits (v{}) -> {}", cached, cacheRoot);
                        return html;
                    }
                }
                // Version mismatch ou fichier manquant -> wipe et re-extract.
                deleteRecursive(cacheRoot);
            }

            Files.createDirectories(cacheRoot);
            for (String relPath : ASSETS) {
                String resourcePath = "/assets/reborn-hud/dynamic-player/" + relPath;
                try (InputStream in = DynamicPlayerAssets.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        LOG.error("asset manquant dans le jar : {}", resourcePath);
                        return null;
                    }
                    Path target = cacheRoot.resolve(relPath);
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(versionFile, ASSETS_VERSION, StandardCharsets.UTF_8);
            Path html = cacheRoot.resolve("bbmodel_viewer.html");
            LOG.info("assets extraits (v{}) -> {}", ASSETS_VERSION, cacheRoot);
            return html;
        } catch (IOException e) {
            LOG.error("extraction des assets dynamic-player a echoue", e);
            return null;
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
