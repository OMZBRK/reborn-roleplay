package fr.reborn.ost;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstSeedExtractor;
import fr.reborn.ost.config.OstConfig;
import fr.reborn.ost.keybind.OstKeybinds;
import fr.reborn.ost.network.OstNetworking;
import fr.reborn.ost.ui.OstHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Entrée client du mod Reborn OST.
 *
 * <p>Boot :
 * <ol>
 *   <li>Charge la config utilisateur ({@code config/reborn-ost.json}).</li>
 *   <li>Scan le dossier filesystem {@code ~/.minecraft/reborn/ost/} et
 *       indexe les .ogg par catégorie ({@link OstLibrary}).</li>
 *   <li>Instancie le moteur audio OpenAL ({@link OstAudioEngine}).</li>
 *   <li>Enregistre le canal {@code reborn:ost} S2C pour recevoir les
 *       broadcasts serveur.</li>
 *   <li>Enregistre la keybind {@code M} et l'overlay HUD.</li>
 * </ol>
 *
 * <p>Les singletons {@code library()}, {@code audioEngine()}, {@code config()}
 * sont exposés statiquement — ils sont nécessaires depuis le Screen UI
 * qui est instancié par le KeyBinding handler.
 */
public final class RebornOstClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost");

    private static OstLibrary LIBRARY;
    private static OstAudioEngine AUDIO_ENGINE;
    private static OstConfig CONFIG;

    @Override
    public void onInitializeClient() {
        // 1. Config.
        CONFIG = OstConfig.load();
        LOGGER.info("config loaded (solo={}, vol={}, fav={})",
            CONFIG.isSoloMode(), CONFIG.getVolume(), CONFIG.getFavorites().size());

        // 2. Library — racine = run dir du client / reborn/ost/.
        //    .minecraft est `FabricLoader.getGameDir()` en runtime.
        Path root = FabricLoader.getInstance().getGameDir().resolve("reborn").resolve("ost");
        LIBRARY = new OstLibrary(root);
        LIBRARY.ensureLayout();
        LIBRARY.scan(); // premier scan pour decider si on extrait le seed
        int extracted = OstSeedExtractor.extractIfEmpty(LIBRARY);
        if (extracted > 0) {
            LIBRARY.scan(); // re-scan apres extraction
        }
        int count = LIBRARY.allTracks().size();
        LOGGER.info("library ready : {} tracks under {} (seed extracted: {})",
            count, root, extracted);

        // 3. Audio engine.
        AUDIO_ENGINE = new OstAudioEngine();
        AUDIO_ENGINE.setGlobalVolume(CONFIG.getVolume());

        // 4. Networking S2C.
        OstNetworking.registerClient(LIBRARY, AUDIO_ENGINE, CONFIG);

        // 5. Keybind + HUD overlay.
        OstKeybinds.registerClient();
        new OstHudOverlay(AUDIO_ENGINE).registerClient();

        LOGGER.info("Reborn OST mod ready.");
    }

    public static OstLibrary library() {
        if (LIBRARY == null) throw new IllegalStateException("OstLibrary not initialized yet");
        return LIBRARY;
    }

    public static OstAudioEngine audioEngine() {
        if (AUDIO_ENGINE == null) throw new IllegalStateException("OstAudioEngine not initialized yet");
        return AUDIO_ENGINE;
    }

    public static OstConfig config() {
        if (CONFIG == null) throw new IllegalStateException("OstConfig not initialized yet");
        return CONFIG;
    }
}
