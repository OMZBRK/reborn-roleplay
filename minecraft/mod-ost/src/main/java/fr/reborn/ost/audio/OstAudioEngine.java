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
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Moteur audio OST — lit des fichiers Ogg Vorbis <b>en flux</b> via STBVorbis (LWJGL, déjà bundlé par Minecraft) et
 * les joue via OpenAL sur le contexte AL partagé avec le SoundEngine vanilla. Pas de dépendance externe.
 *
 * <p><b>Lecture en flux</b> (0.2.6, revue du 02/11) : le .ogg (quelques Mo) est gardé en mémoire et décodé par
 * tranches de {@value #TRANCHE} images (≈ 0,19 s à 44,1 kHz) dans {@value #TAMPONS} tampons OpenAL mis en file sur la
 * source ({@code alSourceQueueBuffers} / {@code alSourceUnqueueBuffers}, ≈ 2,2 s d'avance). Au lancement, seules
 * {@value #TAMPONS_DEPART} tranches sont décodées sur le fil qui appelle (≈ 0,75 s de son, quelques ms) ; le fil
 * {@code Reborn-OST} remplit le reste toutes les {@value #POMPE_MS} ms. Avant, toute la piste était décodée d'un coup
 * sur le fil principal : 109 Mo de PCM et plusieurs secondes de gel pour une piste de 10 min. Un saut
 * ({@link #seekMs}, rattrapage d'une diffusion) passe par {@code stb_vorbis_seek}. Une source arrêtée faute de son
 * (le jeu a gelé plus de 2 s) repart toute seule ; seule la fin du fichier est une fin de piste.
 *
 * <p>Une seule piste joue à la fois (musique de fond, pas un mixer). Un nouvel appel à
 * {@link #play(OstTrack, float, float[], float, float)} stoppe la précédente et libère ses ressources.
 *
 * <p>Positional vs. global :
 * <ul>
 *   <li>{@code worldPos == null} → source non-positionnelle, gain constant.</li>
 *   <li>{@code worldPos != null} → gain calculé à la main selon la distance ({@link #tickPositional}) : OpenAL
 *       n'atténue pas les tampons stéréo.</li>
 * </ul>
 *
 * <p>Fils : les méthodes publiques sont appelées sur le fil client ; la pompe tourne sur {@code Reborn-OST}. Tout passe
 * par le verrou de l'instance (OpenAL Soft accepte les appels de plusieurs fils sur le contexte du processus, comme le
 * fil du SoundEngine vanilla).
 */
public final class OstAudioEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/audio");

    /** États renvoyés par {@link #etat()} (et publiés dans {@code reborn-ost:lecture}). */
    public static final int ETAT_RIEN = 0, ETAT_LECTURE = 1, ETAT_PAUSE = 2;

    /** Images (par canal) d'une tranche décodée, tampons en file, tampons remplis au lancement, pas de la pompe (ms). */
    static final int TRANCHE = 8192, TAMPONS = 12, TAMPONS_DEPART = 4;
    static final long POMPE_MS = 40;

    private int currentSource = -1;
    /** Tampons OpenAL de la piste ; libres = pas encore en file. */
    private int[] tampons;
    private final ArrayDeque<Integer> libres = new ArrayDeque<>(TAMPONS);
    /** Images de chaque tampon en file, dans l'ordre de la file (pour la position de lecture). */
    private final ArrayDeque<int[]> enFile = new ArrayDeque<>(TAMPONS);
    /** Décodeur, fichier en mémoire (il doit vivre autant que le décodeur), tranche PCM réutilisée. */
    private long decodeur = MemoryUtil.NULL;
    private ByteBuffer fichier;
    private ShortBuffer tranche;
    private int canaux, frequence, format;
    /** Images au total, première image du premier tampon en file, fin du fichier atteinte. */
    private long totalImages, imageBase;
    private boolean finFichier;
    /** Pause voulue (la source peut être arrêtée faute de son pendant une pause de la pompe). */
    private boolean pause;

    private long currentStartedAtMs = 0L;
    /** Durée totale en ms. */
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
    /** Facteur de distance du dernier {@link #tickPositional} (1 pour une source globale). */
    private float currentDistanceFactor = 1f;

    /** Fil de la pompe, créé à la première lecture. */
    private ScheduledExecutorService pompe;

    public OstAudioEngine() {}

    public synchronized void setGlobalVolume(float volume) {
        this.globalVolume = clamp(volume, 0f, 1f);
        if (currentSource != -1 && currentWorldPos == null) {
            // Sources globales : on applique directement. Pour les sources positionnelles, tickPositional réapplique le
            // gain avec le facteur distance à chaque tick.
            AL10.alSourcef(currentSource, AL10.AL_GAIN, clamp(globalVolume * currentVolumeMultiplier, 0f, 2f));
        }
    }

    public float globalVolume() { return globalVolume; }

    /**
     * Joue une piste. Si une autre joue déjà, elle est stoppée avant.
     *
     * @param worldPos         null = son global, sinon coordonnées monde
     * @param radius           ignoré si worldPos == null, sinon distance fade-to-0
     * @param secOffsetSeconds saut dans la piste, en secondes. 0 = depuis le début ; > 0 quand le serveur nous fait
     *                         rejoindre une diffusion déjà entamée. Au-delà de la fin : rien ne joue.
     */
    public synchronized void play(OstTrack track, float volumeMultiplier,
                                  float[] worldPos, float radius,
                                  float secOffsetSeconds) {
        long t0 = System.nanoTime();
        stop();
        try {
            ouvrir(track.filePath());
        } catch (IOException e) {
            LOGGER.warn("lecture .ogg impossible '{}' : {}", track.trackId(), e.getMessage());
            fermerDecodeur();
            return;
        } catch (RuntimeException e) {
            LOGGER.warn("STBVorbis exception sur '{}' : {}", track.trackId(), e.getMessage());
            fermerDecodeur();
            return;
        }

        try {
            float clampedOffset = Math.max(0f, secOffsetSeconds);
            long depart = (long) (clampedOffset * (double) frequence);
            if (depart >= totalImages) {   // diffusion déjà finie pour ce fichier
                LOGGER.info("OST '{}' : saut +{}s au-delà de la fin, rien ne joue", track.trackId(), clampedOffset);
                fermerDecodeur();
                return;
            }
            if (depart > 0 && !STBVorbis.stb_vorbis_seek(decodeur, (int) depart)) {
                LOGGER.warn("OST '{}' : saut +{}s impossible, lecture depuis le début", track.trackId(), clampedOffset);
                STBVorbis.stb_vorbis_seek_start(decodeur);
                depart = 0;
            }
            imageBase = depart;

            // Consomme toute erreur OpenAL sticky d'une op précédente (SoundEngine vanilla, source mal libérée…).
            AL10.alGetError();
            tampons = new int[TAMPONS];
            AL10.alGenBuffers(tampons);
            int genErr = AL10.alGetError();
            if (genErr != AL10.AL_NO_ERROR) {
                LOGGER.warn("alGenBuffers failed (err=0x{})", Integer.toHexString(genErr));
                tampons = null;
                fermerDecodeur();
                return;
            }
            for (int b : tampons) libres.add(b);

            int source = AL10.alGenSources();
            this.currentSource = source;
            // Toujours relative au listener : atténuation à la main (OpenAL refuse d'atténuer la stéréo).
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);

            this.currentVolumeMultiplier = volumeMultiplier;
            // global : audible tout de suite ; positionnel : inaudible jusqu'au premier tick (gain 0 ci-dessous)
            this.currentDistanceFactor = worldPos != null ? 0f : 1f;
            if (worldPos != null) {
                this.currentWorldPos = new float[]{worldPos[0], worldPos[1], worldPos[2]};
                this.currentMaxDistance = Math.max(1f, radius);
                // refDistance = 25 % du rayon : un cœur à plein volume, puis fondu jusqu'au bord
                this.currentReferenceDistance = Math.max(1f, currentMaxDistance * 0.25f);
                AL10.alSourcef(source, AL10.AL_GAIN, 0f);
            } else {
                this.currentWorldPos = null;
                this.currentMaxDistance = 0f;
                this.currentReferenceDistance = 1f;
                AL10.alSourcef(source, AL10.AL_GAIN, clamp(globalVolume * volumeMultiplier, 0f, 2f));
            }

            remplir(TAMPONS_DEPART);
            AL10.alSourcePlay(source);
            this.currentTrack = track;
            this.currentStartedAtMs = System.currentTimeMillis() - (long) (clampedOffset * 1000f);
            this.currentDurationMs = totalImages * 1000L / frequence;
            demarrerPompe();
            LOGGER.info("OST play '{}' ({}ch @ {}Hz, dur {}ms, flux{}{}) en {} ms", track.trackId(), canaux, frequence,
                currentDurationMs, worldPos != null ? ", positional r=" + currentMaxDistance : ", global",
                clampedOffset > 0f ? ", seek +" + clampedOffset + "s" : "", (System.nanoTime() - t0) / 1_000_000L);
        } catch (RuntimeException e) {
            LOGGER.warn("OpenAL setup failed for '{}' : {}", track.trackId(), e.getMessage());
            stop();
        }
    }

    /**
     * Recalcule le gain d'une source positionnelle en fonction de la distance entre le listener et le point d'origine
     * du broadcast. No-op pour les sources globales (et quand aucune source n'est active). Appelé à chaque tick client.
     */
    public synchronized void tickPositional(double listenerX, double listenerY, double listenerZ) {
        if (currentSource == -1 || currentWorldPos == null) return;
        double dx = listenerX - currentWorldPos[0];
        double dy = listenerY - currentWorldPos[1];
        double dz = listenerZ - currentWorldPos[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float factor = distanceFactor(distance, currentReferenceDistance, currentMaxDistance);
        currentDistanceFactor = factor;
        float gain = clamp(globalVolume * currentVolumeMultiplier * factor, 0f, 2f);
        AL10.alSourcef(currentSource, AL10.AL_GAIN, gain);
    }

    /**
     * Courbe d'atténuation smoothstep clamped : 1 jusqu'à refDistance, 0 au-delà de maxDistance, smoothstep entre les
     * deux (pas de « pop » aux bornes). Static pour les tests unitaires sans contexte OpenAL.
     */
    public static float distanceFactor(double distance, float refDistance, float maxDistance) {
        if (maxDistance <= refDistance) return distance <= maxDistance ? 1.0f : 0.0f;
        if (distance <= refDistance) return 1.0f;
        if (distance >= maxDistance) return 0.0f;
        double t = (distance - refDistance) / (maxDistance - refDistance);
        double smooth = t * t * (3.0 - 2.0 * t);
        return (float) (1.0 - smooth);
    }

    public synchronized void stop() {
        if (currentSource != -1) {
            AL10.alSourceStop(currentSource);
            AL10.alSourcei(currentSource, AL10.AL_BUFFER, 0);   // vide la file
            AL10.alDeleteSources(currentSource);
            currentSource = -1;
        }
        if (tampons != null) {
            AL10.alDeleteBuffers(tampons);
            tampons = null;
        }
        libres.clear();
        enFile.clear();
        fermerDecodeur();
        pause = false;
        currentTrack = null;
        currentDurationMs = 0L;
        currentStartedAtMs = 0L;
        currentWorldPos = null;
        currentMaxDistance = 0f;
        currentReferenceDistance = 1f;
        currentVolumeMultiplier = 1f;
        currentDistanceFactor = 1f;
    }

    /** Fermeture du client : piste arrêtée, pompe coupée. */
    public void fermer() {
        ScheduledExecutorService p;
        synchronized (this) {
            stop();
            p = pompe;
            pompe = null;
        }
        if (p != null) p.shutdownNow();
    }

    public synchronized boolean isPlaying() {
        if (currentSource == -1 || pause) return false;
        int state = AL10.alGetSourcei(currentSource, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING || state == AL10.AL_STOPPED && !finie();
    }

    /**
     * Renvoie la piste en cours, ou empty. <strong>Effet de bord :</strong> une piste jouée jusqu'au bout (fin du
     * fichier, file vide) est libérée — sinon la HUD et le bouton ▶ du menu afficheraient une piste fantôme.
     */
    public synchronized Optional<OstTrack> currentTrack() {
        if (currentTrack != null && currentSource != -1 && finie()) stop();
        return Optional.ofNullable(currentTrack);
    }

    /**
     * État de la lecture pour {@link fr.reborn.ost.OstLecture} (une fois par tick, sans allocation) :
     * {@link #ETAT_RIEN}, {@link #ETAT_LECTURE} ou {@link #ETAT_PAUSE}. Même effet de bord que {@link #currentTrack()}.
     */
    public synchronized int etat() {
        if (currentTrack == null || currentSource == -1) return ETAT_RIEN;
        if (finie()) {
            stop();
            return ETAT_RIEN;
        }
        return pause ? ETAT_PAUSE : ETAT_LECTURE;
    }

    /** Piste chargée (lecture ou pause), ou null — sans appel OpenAL ni {@code Optional}. */
    public synchronized OstTrack pisteOuNull() { return currentTrack; }

    /**
     * La piste s'entend : gain effectif non nul (volume du joueur × volume du paquet × facteur de distance du dernier
     * tick). Faux hors de la zone d'une diffusion positionnelle, où la source tourne à gain 0.
     */
    public synchronized boolean audible() {
        return currentSource != -1 && globalVolume * currentVolumeMultiplier * currentDistanceFactor > 0f;
    }

    /** Position de lecture (ms) : images des tampons déjà joués + position dans le tampon en cours. */
    public synchronized long elapsedMs() {
        if (currentSource == -1 || frequence <= 0) return 0L;
        long img = imageBase + Math.max(0, AL10.alGetSourcei(currentSource, AL11.AL_SAMPLE_OFFSET));
        return Math.min(currentDurationMs, img * 1000L / frequence);
    }

    public synchronized long durationMs() { return currentDurationMs; }

    public synchronized boolean isPaused() { return currentSource != -1 && pause; }

    /** Bascule lecture/pause de la source courante. */
    public synchronized void togglePause() {
        if (currentSource == -1) return;
        if (pause) {
            pause = false;
            pomper();   // remplit et relance
            if (AL10.alGetSourcei(currentSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(currentSource);
        } else {
            pause = true;
            AL10.alSourcePause(currentSource);
        }
    }

    /** Saute à une position (ms) dans la piste courante (lecture ou pause gardée). */
    public synchronized void seekMs(long ms) {
        if (currentSource == -1 || decodeur == MemoryUtil.NULL) return;
        long img = Math.max(0, Math.min(totalImages - 1, ms * frequence / 1000L));
        AL10.alSourceStop(currentSource);
        AL10.alSourcei(currentSource, AL10.AL_BUFFER, 0);   // vide la file
        enFile.clear();
        libres.clear();
        for (int b : tampons) libres.add(b);
        if (!STBVorbis.stb_vorbis_seek(decodeur, (int) img)) {
            STBVorbis.stb_vorbis_seek_start(decodeur);
            img = 0;
        }
        imageBase = img;
        finFichier = false;
        remplir(TAMPONS_DEPART);
        if (!pause) AL10.alSourcePlay(currentSource);
    }

    /* ================================================================ flux */

    /** Fin de la piste : fichier fini et plus rien en file (la source s'est arrêtée d'elle-même). */
    private boolean finie() {
        if (!finFichier) return false;
        int state = AL10.alGetSourcei(currentSource, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_STOPPED && AL10.alGetSourcei(currentSource, AL10.AL_BUFFERS_PROCESSED)
            >= AL10.alGetSourcei(currentSource, AL10.AL_BUFFERS_QUEUED);
    }

    private void demarrerPompe() {
        if (pompe != null) return;
        pompe = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Reborn-OST");
            t.setDaemon(true);
            return t;
        });
        pompe.scheduleWithFixedDelay(() -> {
            try {
                pomper();
            } catch (Throwable t) {
                LOGGER.warn("OST : pompe du flux en échec ({})", t.toString());
            }
        }, POMPE_MS, POMPE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Pompe (fil {@code Reborn-OST}, toutes les {@value #POMPE_MS} ms) : tampons joués retirés de la file, remplis de la
     * suite et remis en file ; une source arrêtée faute de son (gel du jeu) repart.
     */
    synchronized void pomper() {
        if (currentSource == -1 || tampons == null) return;
        if (!AL10.alIsSource(currentSource)) {   // contexte OpenAL refait (périphérique audio changé) : piste perdue
            LOGGER.warn("OST : source OpenAL perdue (contexte refait), piste arrêtée");
            currentSource = -1;
            tampons = null;
            stop();
            return;
        }
        int faits = AL10.alGetSourcei(currentSource, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < faits; i++) {
            int b = AL10.alSourceUnqueueBuffers(currentSource);
            int[] e = enFile.pollFirst();
            if (e != null) imageBase += e[1];
            libres.add(b);
        }
        if (pause) return;
        remplir(TAMPONS);
        int state = AL10.alGetSourcei(currentSource, AL10.AL_SOURCE_STATE);
        if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED && !enFile.isEmpty()) AL10.alSourcePlay(currentSource);
    }

    /** Décode et met en file jusqu'à {@code max} tampons en file (ou la fin du fichier). */
    private void remplir(int max) {
        while (!finFichier && !libres.isEmpty() && enFile.size() < max) {
            tranche.clear();
            int n = STBVorbis.stb_vorbis_get_samples_short_interleaved(decodeur, canaux, tranche);
            if (n <= 0) {
                finFichier = true;
                break;
            }
            tranche.limit(n * canaux);
            int b = libres.pollFirst();
            AL10.alBufferData(b, format, tranche, frequence);
            AL10.alSourceQueueBuffers(currentSource, b);
            enFile.addLast(new int[] {b, n});
        }
    }

    /** Ouvre le fichier en mémoire et le décodeur ; la tranche PCM est allouée une fois par piste. */
    private void ouvrir(Path file) throws IOException {
        long size;
        try (SeekableByteChannel ch = Files.newByteChannel(file)) {
            size = ch.size();
            if (size <= 0 || size > Integer.MAX_VALUE) throw new IOException("fichier vide ou trop gros : " + size);
            fichier = MemoryUtil.memAlloc((int) size);
            while (fichier.hasRemaining()) if (ch.read(fichier) < 0) break;
            fichier.flip();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);
            decodeur = STBVorbis.stb_vorbis_open_memory(fichier, err, null);
            if (decodeur == MemoryUtil.NULL) throw new IOException("stb_vorbis_open_memory failed (err=" + err.get(0) + ")");
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decodeur, info);
            canaux = info.channels();
            frequence = info.sample_rate();
            if (canaux < 1 || canaux > 2 || frequence <= 0) throw new IOException("format non pris : " + canaux + " canaux");
            totalImages = STBVorbis.stb_vorbis_stream_length_in_samples(decodeur);
            if (totalImages <= 0) throw new IOException("stream vide");
            format = canaux == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            tranche = MemoryUtil.memAllocShort(TRANCHE * canaux);
            finFichier = false;
        }
    }

    private void fermerDecodeur() {
        if (decodeur != MemoryUtil.NULL) {
            STBVorbis.stb_vorbis_close(decodeur);
            decodeur = MemoryUtil.NULL;
        }
        if (fichier != null) {
            MemoryUtil.memFree(fichier);
            fichier = null;
        }
        if (tranche != null) {
            MemoryUtil.memFree(tranche);
            tranche = null;
        }
        finFichier = false;
        totalImages = 0;
        imageBase = 0;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
