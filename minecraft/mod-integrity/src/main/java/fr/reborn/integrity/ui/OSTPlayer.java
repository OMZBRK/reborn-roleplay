package fr.reborn.integrity.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

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

    private OSTPlayer() {}

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        stop();
        Identifier id = Identifier.of("reborn", String.format("ost.track%02d", currentTrack));
        SoundEvent event = SoundEvent.of(id);
        currentInstance = PositionedSoundInstance.master(event, 1.0F, volume);
        client.getSoundManager().play(currentInstance);
        playing = true;
        startTimeMs = System.currentTimeMillis();
    }

    /** Stop la piste en cours sans memoire de position. */
    public void stop() {
        if (currentInstance != null) {
            MinecraftClient client = MinecraftClient.getInstance();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        return client.getSoundManager().isPlaying(currentInstance);
    }

    /** Si la piste s'est terminee naturellement, passe a la suivante. */
    public void tickAutoAdvance() {
        if (playing && !isStillSoundingInManager() && getElapsedMs() > 2000) {
            // 2s grace : SoundManager.isPlaying peut etre false momentanement
            // au tout debut du play (chargement async du fichier).
            next();
        }
    }
}
