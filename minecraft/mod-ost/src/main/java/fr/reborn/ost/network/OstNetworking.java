package fr.reborn.ost.network;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.config.OstConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
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

    /** Vrai si CE client est le propriétaire du broadcast de zone actif
     *  (il a lancé un son en solo OFF). Seul le propriétaire peut stop/pause
     *  pour tout le monde. Passé à false dès qu'un broadcast tiers arrive ou
     *  qu'un stop survient. */
    private static volatile boolean broadcastOwner = false;

    public static boolean isBroadcastOwner() { return broadcastOwner; }

    private OstNetworking() {}

    public static void registerClient(OstLibrary library, OstAudioEngine engine, OstConfig config) {
        PayloadTypeRegistry.clientboundPlay().register(OstPayload.ID, OstPayload.CODEC);
        // Canal C2S (client → serveur) : le joueur demande un broadcast de zone
        // / stop / pause. Le plugin applique cooldown + cap rayon + owner-only.
        PayloadTypeRegistry.serverboundPlay().register(OstRequestPayload.ID, OstRequestPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(OstPayload.ID, (payload, ctx) -> {
            LOGGER.info("payload recu : {}", payload.summary());
            try {
                handle(payload, library, engine, config, ctx.client());
            } catch (Exception e) {
                LOGGER.warn("handling OST payload {} failed: {}", payload.summary(), e.getMessage());
            }
        });

        LOGGER.info("OST channels registered (reborn:ost S2C + reborn:ost_request C2S)");
    }

    // ── Envois C2S (le menu OST les appelle) ──

    /** Demande au serveur de diffuser {@code trackId} autour du joueur. Marque
     *  ce client comme propriétaire du broadcast. */
    public static void requestPlay(String trackId, float radius, float volume) {
        broadcastOwner = true;
        if (ClientPlayNetworking.canSend(OstRequestPayload.ID)) {
            ClientPlayNetworking.send(new OstRequestPayload.RequestPlay(trackId, radius, volume));
        }
    }

    /** Demande au serveur de stopper le broadcast dont on est propriétaire. */
    public static void requestStop() {
        if (broadcastOwner && ClientPlayNetworking.canSend(OstRequestPayload.ID)) {
            ClientPlayNetworking.send(new OstRequestPayload.RequestStop());
        }
        broadcastOwner = false;
    }

    /** Demande pause/reprise du broadcast dont on est propriétaire. */
    public static void requestPause(boolean paused) {
        if (broadcastOwner && ClientPlayNetworking.canSend(OstRequestPayload.ID)) {
            ClientPlayNetworking.send(new OstRequestPayload.RequestPause(paused));
        }
    }

    private static void handle(OstPayload payload, OstLibrary library, OstAudioEngine engine,
                               OstConfig config, Minecraft client) {
        if (config.isSoloMode()) {
            LOGGER.info("solo mode actif — payload IGNORE : {}", payload.summary());
            return;
        }
        switch (payload) {
            case OstPayload.StopBroadcast ignored -> {
                broadcastOwner = false;
                client.execute(engine::stop);
            }
            case OstPayload.PauseBroadcast p -> client.execute(() -> {
                // Aligne l'état de pause local sur la consigne owner.
                if (engine.isPlaying() && engine.isPaused() != p.paused()) engine.togglePause();
            });
            case OstPayload.PlayGlobal p -> {
                broadcastOwner = false; // broadcast d'un tiers → je ne suis pas owner
                resolveAndPlay(p.trackId(), p.volume(), null, 0f, 0f, library, engine, client);
            }
            case OstPayload.PlayAtPosition p -> {
                broadcastOwner = false;
                resolveAndPlay(
                    p.trackId(),
                    p.volume(),
                    new float[]{(float) p.x(), (float) p.y(), (float) p.z()},
                    p.radius(),
                    p.secOffset(),
                    library, engine, client
                );
            }
        }
    }

    private static void resolveAndPlay(String trackId, float volume, float[] worldPos,
                                       float radius, float secOffset, OstLibrary library,
                                       OstAudioEngine engine, Minecraft client) {
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
