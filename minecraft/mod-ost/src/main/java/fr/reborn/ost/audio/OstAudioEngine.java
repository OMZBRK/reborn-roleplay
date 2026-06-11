package fr.reborn.ost.audio;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Moteur audio OST — décode des fichiers Ogg Vorbis via STBVorbis (LWJGL,
 * déjà bundlé par Minecraft) et les joue via OpenAL sur le contexte AL
 * partagé avec le SoundEngine vanilla. Pas de dépendance externe.
 *
 * <p>Une seule piste joue à la fois (musique de fond, pas un mixer). Un
 * nouvel appel à {@link #play(OstTrack, float, float[], float)} stoppe
 * la précédente et libère ses ressources GL.
 *
 * <p>Positional vs. global :
 * <ul>
 *   <li>{@code worldPos == null} → source non-positionnelle, gain
 *       constant.</li>
 *   <li>{@code worldPos != null} → source positionnelle, distance
 *       model {@code AL_LINEAR_DISTANCE_CLAMPED} entre
 *       {@code reference=1} et {@code max=radius}. Suppose un
 *       .ogg mono (stéréo = pas d'attenuation 3D selon spec OpenAL).</li>
 * </ul>
 *
 * <p>Limitation connue : la position du listener est définie par le
 * SoundEngine vanilla MC — on hérite donc de sa logique. Pas besoin
 * de l'updater nous-mêmes.
 *
 * <p>Thread-safety : toutes les méthodes publiques doivent être
 * appelées sur le main client thread (OpenAL n'est pas thread-safe
 * sans context-switch manuel).
 */
public final class OstAudioEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/audio");

    private int currentSource = -1;
    private int currentBuffer = -1;
    private long currentStartedAtMs = 0L;
    /** Durée totale en ms, calculée au décodage. */
    private long currentDurationMs = 0L;
    private OstTrack currentTrack = null;
    /** Volume gain global (0..1) appliqué à la source active. */
    private float globalVolume = 0.5f;
    /** Volume multiplier passé par le caller (broadcast packet ou UI). */
    private float currentVolumeMultiplier = 1f;
    /** Position monde de la source positionnelle, ou null si globale. */
    private float[] currentWorldPos = null;
    /** Distance à laquelle le gain commence à fade (= 1.0 en deçà). */
    private float currentReferenceDistance = 1f;
    /** Distance à laquelle le gain atteint 0. */
    private float currentMaxDistance = 0f;

    public OstAudioEngine() {}

    public synchronized void setGlobalVolume(float volume) {
        this.globalVolume = clamp(volume, 0f, 1f);
        if (currentSource != -1 && currentWorldPos == null) {
            // Sources globales : on applique directement. Pour les sources
            // positionnelles, tickPositional réapplique le gain avec le facteur
            // distance à chaque tick — on ne touche pas ici pour ne pas créer
            // un flash de volume entre la modif du slider et le prochain tick.
            AL10.alSourcef(currentSource, AL10.AL_GAIN,
                clamp(globalVolume * currentVolumeMultiplier, 0f, 2f));
        }
    }

    public float globalVolume() { return globalVolume; }

    /**
     * Joue une piste. Si une autre joue déjà, elle est stoppée avant.
     *
     * @param worldPos        null = son global, sinon coordonnées monde
     * @param radius          ignored si worldPos == null, sinon distance fade-to-0
     * @param secOffsetSeconds skip dans la track, en secondes. 0 = depuis le
     *                        début ; > 0 utilisé quand le serveur nous fait
     *                        rejoindre un broadcast déjà entamé.
     */
    public synchronized void play(OstTrack track, float volumeMultiplier,
                                  float[] worldPos, float radius,
                                  float secOffsetSeconds) {
        stop();

        DecodedOgg decoded;
        try {
            decoded = decode(track.filePath());
        } catch (IOException e) {
            LOGGER.warn("decode .ogg echec '{}' : {}", track.trackId(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.warn("STBVorbis exception sur '{}' : {}", track.trackId(), e.getMessage());
            return;
        }

        try {
            int buffer = AL10.alGenBuffers();
            if (alFailed()) { LOGGER.warn("alGenBuffers failed"); MemoryUtil.memFree(decoded.pcm); return; }
            int format = decoded.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(buffer, format, decoded.pcm, decoded.sampleRate);
            MemoryUtil.memFree(decoded.pcm);
            if (alFailed()) { LOGGER.warn("alBufferData failed"); AL10.alDeleteBuffers(buffer); return; }

            int source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

            // Toujours source-relative au listener : on bypasse complètement
            // le distance model d'OpenAL parce qu'il refuse de fade les
            // buffers stéréo (spec AL). On fait l'attenuation à la main
            // dans tickPositional pour garder le master stéréo du .ogg.
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);

            this.currentVolumeMultiplier = volumeMultiplier;
            if (worldPos != null) {
                this.currentWorldPos = new float[]{worldPos[0], worldPos[1], worldPos[2]};
                this.currentMaxDistance = Math.max(1f, radius);
                // refDistance = 25% du radius — donne un "core" à plein volume
                // proche de la source, puis fade linéaire vers 0 jusqu'au bord.
                // Empirique mais cohérent musicalement (pas de point unique).
                this.currentReferenceDistance = Math.max(1f, currentMaxDistance * 0.25f);
                // Gain initial = on suppose le joueur au point d'origine du
                // broadcast jusqu'au premier tick. Sinon le client entendrait
                // un flash plein volume à t=0 avant que tick réajuste.
                AL10.alSourcef(source, AL10.AL_GAIN, 0f);
            } else {
                this.currentWorldPos = null;
                this.currentMaxDistance = 0f;
                this.currentReferenceDistance = 1f;
                AL10.alSourcef(source, AL10.AL_GAIN,
                    clamp(globalVolume * volumeMultiplier, 0f, 2f));
            }

            // Seek si demandé. AL11.AL_SEC_OFFSET prend des secondes et clamp
            // automatiquement à la durée du buffer — un offset au-delà fait
            // passer la source en AL_STOPPED tout de suite, ce qui est OK
            // (mode "track déjà finie" géré par currentTrack auto-clear).
            float clampedOffset = Math.max(0f, secOffsetSeconds);
            if (clampedOffset > 0f) {
                AL10.alSourcef(source, AL11.AL_SEC_OFFSET, clampedOffset);
            }

            AL10.alSourcePlay(source);
            this.currentBuffer = buffer;
            this.currentSource = source;
            this.currentTrack = track;
            // On recule startedAt par l'offset pour que elapsedMs() renvoie
            // le timestamp courant dans la track et pas "depuis qu'on a
            // appelé play()" — la HUD affiche 0:42 / 2:30 correctement.
            this.currentStartedAtMs = System.currentTimeMillis() - (long) (clampedOffset * 1000f);
            this.currentDurationMs = decoded.durationMs;
            LOGGER.info("OST play '{}' ({}ch @ {}Hz, dur {}ms{}{})", track.trackId(),
                decoded.channels, decoded.sampleRate, decoded.durationMs,
                worldPos != null ? ", positional r=" + currentMaxDistance : ", global",
                clampedOffset > 0f ? ", seek +" + clampedOffset + "s" : "");
        } catch (RuntimeException e) {
            LOGGER.warn("OpenAL setup failed for '{}' : {}", track.trackId(), e.getMessage());
        }
    }

    /**
     * Recalcule le gain d'une source positionnelle en fonction de la
     * distance entre le listener et le point d'origine du broadcast.
     * No-op pour les sources globales (et quand aucune source n'est active).
     *
     * <p>Appel à hooker sur {@code ClientTickEvents.END_CLIENT_TICK}
     * (20 Hz) — l'overhead est négligeable (un sqrt + un alSourcef).
     */
    public synchronized void tickPositional(double listenerX, double listenerY, double listenerZ) {
        if (currentSource == -1 || currentWorldPos == null) return;
        double dx = listenerX - currentWorldPos[0];
        double dy = listenerY - currentWorldPos[1];
        double dz = listenerZ - currentWorldPos[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float factor = distanceFactor(distance, currentReferenceDistance, currentMaxDistance);
        float gain = clamp(globalVolume * currentVolumeMultiplier * factor, 0f, 2f);
        AL10.alSourcef(currentSource, AL10.AL_GAIN, gain);
    }

    /**
     * Courbe d'atténuation linéaire clamped :
     * <ul>
     *   <li>distance ≤ refDistance → 1.0 (full)</li>
     *   <li>distance ≥ maxDistance → 0.0 (silence)</li>
     *   <li>entre → lerp linéaire de 1.0 à 0.0</li>
     * </ul>
     * Static pour permettre les tests unitaires sans contexte OpenAL.
     */
    public static float distanceFactor(double distance, float refDistance, float maxDistance) {
        if (maxDistance <= refDistance) return distance <= maxDistance ? 1.0f : 0.0f;
        if (distance <= refDistance) return 1.0f;
        if (distance >= maxDistance) return 0.0f;
        return (float) ((maxDistance - distance) / (maxDistance - refDistance));
    }

    public synchronized void stop() {
        if (currentSource != -1) {
            AL10.alSourceStop(currentSource);
            AL10.alSourcei(currentSource, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(currentSource);
            currentSource = -1;
        }
        if (currentBuffer != -1) {
            AL10.alDeleteBuffers(currentBuffer);
            currentBuffer = -1;
        }
        currentTrack = null;
        currentDurationMs = 0L;
        currentStartedAtMs = 0L;
        currentWorldPos = null;
        currentMaxDistance = 0f;
        currentReferenceDistance = 1f;
        currentVolumeMultiplier = 1f;
    }

    public synchronized boolean isPlaying() {
        if (currentSource == -1) return false;
        int state = AL10.alGetSourcei(currentSource, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING;
    }

    /**
     * Renvoie la piste en cours, ou empty. <strong>Effet de bord :</strong>
     * si la source OpenAL a fini de jouer naturellement (état AL_STOPPED),
     * on libère les ressources et on remet l'état à zéro — sinon la HUD
     * et le bouton ▶ du menu continueraient d'afficher une piste fantôme
     * jusqu'au prochain {@link #play} ou {@link #stop}.
     */
    public synchronized Optional<OstTrack> currentTrack() {
        if (currentTrack != null && currentSource != -1) {
            int state = AL10.alGetSourcei(currentSource, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_STOPPED) {
                stop();
            }
        }
        return Optional.ofNullable(currentTrack);
    }

    public synchronized long elapsedMs() {
        return currentSource == -1 ? 0L : System.currentTimeMillis() - currentStartedAtMs;
    }

    public synchronized long durationMs() { return currentDurationMs; }

    // ──────────────────────────────────────────────────────────
    // Décodage Ogg Vorbis via STBVorbis (LWJGL, sans dep externe)
    // ──────────────────────────────────────────────────────────

    private record DecodedOgg(ShortBuffer pcm, int channels, int sampleRate, long durationMs) {}

    private static DecodedOgg decode(Path file) throws IOException {
        // 1. Lecture complète en RAM — les .ogg OST tournent typiquement
        //    autour de 1-5 Mo, donc un memAlloc de cette taille est ok.
        ByteBuffer fileBuf;
        long size;
        try (SeekableByteChannel ch = Files.newByteChannel(file)) {
            size = ch.size();
            if (size <= 0 || size > Integer.MAX_VALUE) {
                throw new IOException("fichier vide ou trop gros : " + size);
            }
            fileBuf = MemoryUtil.memAlloc((int) size);
            while (fileBuf.hasRemaining()) {
                if (ch.read(fileBuf) < 0) break;
            }
            fileBuf.flip();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);
            long decoder = STBVorbis.stb_vorbis_open_memory(fileBuf, err, null);
            if (decoder == MemoryUtil.NULL) {
                MemoryUtil.memFree(fileBuf);
                throw new IOException("stb_vorbis_open_memory failed (err=" + err.get(0) + ")");
            }
            try {
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(decoder, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();
                int samples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
                if (samples <= 0) throw new IOException("stream vide");
                long durationMs = (long) (samples * 1000L / sampleRate);

                ShortBuffer pcm = MemoryUtil.memAllocShort(samples * channels);
                STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                pcm.limit(samples * channels);
                return new DecodedOgg(pcm, channels, sampleRate, durationMs);
            } finally {
                STBVorbis.stb_vorbis_close(decoder);
                MemoryUtil.memFree(fileBuf);
            }
        }
    }

    private static boolean alFailed() {
        return AL10.alGetError() != AL10.AL_NO_ERROR;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
