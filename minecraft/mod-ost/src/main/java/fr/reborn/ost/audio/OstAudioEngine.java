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

    public OstAudioEngine() {}

    public synchronized void setGlobalVolume(float volume) {
        this.globalVolume = clamp(volume, 0f, 1f);
        if (currentSource != -1) {
            AL10.alSourcef(currentSource, AL10.AL_GAIN, globalVolume);
        }
    }

    public float globalVolume() { return globalVolume; }

    /**
     * Joue une piste. Si une autre joue déjà, elle est stoppée avant.
     *
     * @param worldPos null = son global, sinon coordonnées monde
     * @param radius   ignored si worldPos == null, sinon AL_MAX_DISTANCE
     */
    public synchronized void play(OstTrack track, float volumeMultiplier,
                                  float[] worldPos, float radius) {
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
            AL10.alSourcef(source, AL10.AL_GAIN, clamp(globalVolume * volumeMultiplier, 0f, 2f));

            if (worldPos != null) {
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                AL10.alSource3f(source, AL10.AL_POSITION, worldPos[0], worldPos[1], worldPos[2]);
                AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1.0f);
                AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, Math.max(1f, radius));
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
                AL11.alDistanceModel(AL11.AL_LINEAR_DISTANCE_CLAMPED);
            } else {
                // Son non-positionnel : relative à la tête de l'auditeur,
                // donc gain constant indépendant de la position monde.
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
            }

            AL10.alSourcePlay(source);
            this.currentBuffer = buffer;
            this.currentSource = source;
            this.currentTrack = track;
            this.currentStartedAtMs = System.currentTimeMillis();
            this.currentDurationMs = decoded.durationMs;
            LOGGER.info("OST play '{}' ({}ch @ {}Hz, dur {}ms)", track.trackId(),
                decoded.channels, decoded.sampleRate, decoded.durationMs);
        } catch (RuntimeException e) {
            LOGGER.warn("OpenAL setup failed for '{}' : {}", track.trackId(), e.getMessage());
        }
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
