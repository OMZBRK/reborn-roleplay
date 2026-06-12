package fr.reborn.ost.network;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.config.OstConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Bootstrap du canal {@code reborn:ost} côté client + handler des
 * payloads reçus depuis le plugin serveur.
 *
 * <p>Filtrage : si {@link OstConfig#isSoloMode()} est true, on log
 * et on ignore tous les broadcasts — l'utilisateur a choisi son
 * propre playlist.
 */
public final class OstNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/net");

    private OstNetworking() {}

    public static void registerClient(OstLibrary library, OstAudioEngine engine, OstConfig config) {
        // Canal S2C (serveur → client) — le client ne push jamais ici
        // dans cette version. Si plus tard on veut un auto-DJ partagé
        // (le client request une lecture), il faudra ajouter une
        // signature HMAC pour éviter qu'un client malveillant déclenche
        // un son chez tous les voisins — TODO documenté dans le plugin.
        PayloadTypeRegistry.playS2C().register(OstPayload.ID, OstPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(OstPayload.ID, (payload, ctx) -> {
            LOGGER.info("payload recu : {}", payload.summary());
            try {
                handle(payload, library, engine, config, ctx.client());
            } catch (Exception e) {
                LOGGER.warn("handling OST payload {} failed: {}", payload.summary(), e.getMessage());
            }
        });

        LOGGER.info("OST channel registered (reborn:ost S2C)");
    }

    private static void handle(OstPayload payload, OstLibrary library, OstAudioEngine engine,
                               OstConfig config, MinecraftClient client) {
        if (config.isSoloMode()) {
            LOGGER.info("solo mode actif — payload IGNORE : {}", payload.summary());
            return;
        }
        switch (payload) {
            case OstPayload.StopBroadcast ignored -> client.execute(engine::stop);
            case OstPayload.PlayGlobal p -> resolveAndPlay(p.trackId(), p.volume(), null, 0f, 0f, library, engine, client);
            case OstPayload.PlayAtPosition p -> resolveAndPlay(
                p.trackId(),
                p.volume(),
                new float[]{(float) p.x(), (float) p.y(), (float) p.z()},
                p.radius(),
                p.secOffset(),
                library, engine, client
            );
        }
    }

    private static void resolveAndPlay(String trackId, float volume, float[] worldPos,
                                       float radius, float secOffset, OstLibrary library,
                                       OstAudioEngine engine, MinecraftClient client) {
        Optional<OstTrack> resolved = library.resolve(trackId);
        if (resolved.isEmpty()) {
            LOGGER.warn("broadcast reçu pour trackId inconnu : '{}'. Drop ses fichiers .ogg dans le dossier OST local et /ost reload.", trackId);
            return;
        }
        OstTrack track = resolved.get();
        // L'audio engine doit être appelé depuis le main thread MC (OpenAL
        // single-thread). client.execute déferre sur la prochaine tick.
        client.execute(() -> engine.play(track, volume, worldPos, radius, secOffset));
    }
}
