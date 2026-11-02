package fr.reborn.ost;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.config.OstConfig;
import fr.reborn.ost.keybind.OstKeybinds;
import fr.reborn.ost.network.OstNetworking;
import fr.reborn.ost.ui.OstHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
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
 *   <li>Enregistre la keybind {@code N}, publie l'état de la lecture
 *       ({@link OstLecture}, lu par le lecteur de reborn-hud) et le
 *       bandeau de secours (seulement sans reborn-hud ≥ lot 2).</li>
 * </ol>
 *
 * <p>Les pistes arrivent par le manifest du launcher
 * ({@code reborn/ost/<catégorie>/*.ogg}, fichiers obligatoires téléchargés
 * avant le lancement du jeu) : l'ancien seed de 43 pistes embarqué dans le
 * jar (71 Mo) n'était plus jamais extrait et a été retiré en 0.2.6.
 *
 * <p>Les singletons {@code library()}, {@code audioEngine()}, {@code config()}
 * sont exposés statiquement — ils sont nécessaires depuis le Screen UI
 * qui est instancié par le KeyMapping handler.
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
        LIBRARY.scan();
        int count = LIBRARY.allTracks().size();
        LOGGER.info("library ready : {} tracks under {}", count, root);

        // 3. Audio engine.
        AUDIO_ENGINE = new OstAudioEngine();
        AUDIO_ENGINE.setGlobalVolume(CONFIG.getVolume());

        // 4. Networking S2C.
        OstNetworking.registerClient(LIBRARY, AUDIO_ENGINE, CONFIG);

        // 5. Keybind, état publié pour le HUD (reborn-ost:lecture) et bandeau de
        //    secours (dessiné seulement si reborn-hud ne dessine pas le lecteur).
        OstKeybinds.registerClient();
        OstLecture.publier();
        OstHudOverlay overlay = new OstHudOverlay(AUDIO_ENGINE);
        overlay.registerClient();

        // 6. Tick handler — recalcule le gain par distance pour les broadcasts
        //    positionnels. OpenAL n'attenue pas les buffers stéréo (spec AL),
        //    on bricole donc à la main : 20Hz suffit largement pour suivre
        //    le déplacement du joueur sans artefact audible. Puis relevé de
        //    l'état publié (piste, lecture / pause, audible).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Lecture continue (auto-next / repeat) — indépendant du joueur.
            fr.reborn.ost.audio.OstPlayback.INSTANCE.tick(AUDIO_ENGINE, CONFIG);
            if (client.player != null) {
                Vec3 pos = client.player.getEyePosition();
                AUDIO_ENGINE.tickPositional(pos.x, pos.y, pos.z);
            }
            OstLecture.maj(AUDIO_ENGINE);
        });
        OstBanc.init(LIBRARY, AUDIO_ENGINE, CONFIG, overlay);   // banc REBORN_OST_BANC (dev)
        // fermeture : piste arrêtée et fil de la pompe du flux coupé avant que le SoundEngine ne ferme OpenAL
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c -> AUDIO_ENGINE.fermer());

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
