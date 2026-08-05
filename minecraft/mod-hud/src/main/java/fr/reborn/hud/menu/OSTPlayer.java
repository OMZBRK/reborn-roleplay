package fr.reborn.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.PositionedSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.Identifier;

/**
 * Audio state machine du lecteur OST Reborn. Singleton process-wide pour
 * que la musique survive aux transitions de Screen (Title -> Options ->
 * Title) sans repartir du debut.
 *
 * <p>Limitations Minecraft 1.21.1 :
 * <ul>
 *   <li>{@code SoundManager} ne supporte pas le seek ni le pause natif.
 *       "Pause" = stop, "Resume" = relance depuis 0 (sans memoire de
 *       position). Pour MVP c'est acceptable, on ameliorera plus tard
 *       via OpenAL direct.</li>
 *   <li>{@code SoundInstance.getVolume()} fige la valeur au moment du
 *       play. Changer le volume necessite stop + replay.</li>
 *   <li>Pas de callback "track ended" — on poll via
 *       {@code SoundManager.isPlaying(instance)} dans le widget.</li>
 * </ul>
 */
public final class OSTPlayer {

    public static final OSTPlayer INSTANCE = new OSTPlayer();

    /** Nombre de pistes embarquees dans assets/reborn/sounds/ost/. */
    public static final int TOTAL_TRACKS = 43;

    /** Piste actuelle (1..TOTAL_TRACKS). */
    private int currentTrack = 1;

    /** True si une piste est actuellement en lecture. */
    private boolean playing = false;

    /** Volume actuel (0.0 .. 1.0). */
    private float volume = 0.5f;

    /** Reference vers le SoundInstance en cours pour pouvoir le stopper. */
    private SoundInstance currentInstance = null;

    /** Timestamp de debut de la piste actuelle (millis epoch). */
    private long startTimeMs = 0;

    /** True quand le popup volume est ouvert (toggle au click). */
    private boolean volumePopupOpen = false;
    /** True quand l'overlay playlist est ouvert. */
    private boolean playlistOpen = false;

    /** Durées effectives par piste — apprises au fil des lectures via
     *  SoundManager.isPlaying() qui passe à false en fin de piste. Permet
     *  d'avoir une progress bar exacte dès la 2e écoute d'une piste. */
    private final java.util.Map<Integer, Long> learnedDurationsMs =
        new java.util.HashMap<>();

    private OSTPlayer() {}

    public boolean isVolumePopupOpen() { return volumePopupOpen; }
    public boolean isPlaylistOpen() { return playlistOpen; }

    public void toggleVolumePopup() {
        volumePopupOpen = !volumePopupOpen;
        if (volumePopupOpen) playlistOpen = false; // mutually exclusive
    }
    public void togglePlaylist() {
        playlistOpen = !playlistOpen;
        if (playlistOpen) volumePopupOpen = false;
    }
    public void closeOverlays() {
        volumePopupOpen = false;
        playlistOpen = false;
    }

    /** Saut direct à une piste par son index (1-indexed). */
    public void playTrack(int trackIdx) {
        currentTrack = Math.max(1, Math.min(TOTAL_TRACKS, trackIdx));
        play();
    }

    /** Label d'une piste donnée — placeholder "Track NN" tant qu'on n'a
     *  pas de metadata propre par piste. */
    public String getTrackNameAt(int trackIdx) {
        return String.format("Track %02d", trackIdx);
    }

    public int getCurrentTrack() {
        return currentTrack;
    }

    public boolean isPlaying() {
        return playing;
    }

    public float getVolume() {
        return volume;
    }

    /** Temps ecoule depuis le debut de la piste actuelle, en ms. */
    public long getElapsedMs() {
        return playing ? System.currentTimeMillis() - startTimeMs : 0;
    }

    /** Demarre la piste courante. Stop l'ancienne si presente. */
    public void play() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        stop();
        Identifier id = Identifier.fromNamespaceAndPath("reborn", String.format("ost.track%02d", currentTrack));
        SoundEvent event = SoundEvent.of(id);
        currentInstance = PositionedSoundInstance.master(event, 1.0F, volume);
        client.getSoundManager().play(currentInstance);
        playing = true;
        startTimeMs = System.currentTimeMillis();
    }

    /** Stop la piste en cours sans memoire de position. */
    public void stop() {
        if (currentInstance != null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.getSoundManager().stop(currentInstance);
            }
            currentInstance = null;
        }
        playing = false;
    }

    /** Toggle play/pause (= stop si en cours, play si arrete). */
    public void togglePlayPause() {
        if (playing) {
            stop();
        } else {
            play();
        }
    }

    /** Passe a la piste suivante (loop sur TOTAL_TRACKS). */
    public void next() {
        currentTrack = (currentTrack % TOTAL_TRACKS) + 1;
        if (playing) {
            play();
        }
    }

    /** Passe a la piste precedente (loop). */
    public void prev() {
        currentTrack = (currentTrack - 2 + TOTAL_TRACKS) % TOTAL_TRACKS + 1;
        if (playing) {
            play();
        }
    }

    /** Set le volume et restart la piste avec la nouvelle valeur. */
    public void setVolume(float v) {
        this.volume = Math.max(0, Math.min(1, v));
        if (playing) {
            play();
        }
    }

    /** Label de la piste actuelle ("Track 01" ... "Track 43"). */
    public String getCurrentTrackName() {
        return String.format("Track %02d", currentTrack);
    }

    /**
     * Verifie via SoundManager si la piste joue toujours. A appeler depuis
     * le tick / render du widget pour auto-skip a la fin.
     */
    public boolean isStillSoundingInManager() {
        if (currentInstance == null) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        return client.getSoundManager().isPlaying(currentInstance);
    }

    /** Si la piste s'est terminee naturellement, passe a la suivante.
     *  Apprend aussi la durée réelle de la piste pour les futures écoutes. */
    public void tickAutoAdvance() {
        if (playing && !isStillSoundingInManager() && getElapsedMs() > 2000) {
            // 2s grace : SoundManager.isPlaying peut etre false momentanement
            // au tout debut du play (chargement async du fichier).
            // On capture la durée réelle avant de passer à la suivante.
            learnedDurationsMs.put(currentTrack, getElapsedMs());
            next();
        }
    }

    /**
     * Retourne le progress 0..1 de la piste en cours. Utilise la durée
     * apprise si déjà jouée auparavant, sinon estimation à 180s par défaut
     * (donc imprécise sur la 1ère écoute, exacte ensuite).
     */
    public float getProgress() {
        if (!playing) return 0f;
        long durationMs = learnedDurationsMs.getOrDefault(currentTrack, 180_000L);
        return Math.min(1f, getElapsedMs() / (float) durationMs);
    }
}
