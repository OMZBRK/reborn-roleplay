package fr.reborn.ost.audio;

import fr.reborn.ost.config.OstConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrôleur de lecture (file d'attente) au-dessus de {@link OstAudioEngine} :
 * mémorise la playlist en cours (catégorie ou recherche) + l'index, et gère
 * <b>lecture continue</b> (auto-next à la fin d'une piste), <b>shuffle</b> et
 * <b>repeat</b> (off / une / liste). L'état shuffle/repeat vit dans
 * {@link OstConfig}.
 *
 * <p>À ticker chaque tick client ({@link #tick}) pour détecter la fin d'une
 * piste et enchaîner.
 */
public final class OstPlayback {

    public static final OstPlayback INSTANCE = new OstPlayback();

    private List<OstTrack> playlist = new ArrayList<>();
    private int index = -1;
    /** Piste attendue en lecture ; null = rien / stop explicite (pas d'auto-next). */
    private OstTrack expected = null;
    /** Dernière position/durée observées pendant la lecture — sert à distinguer
     *  une fin NATURELLE (elapsed ≈ durée) d'un stop EXTERNE (StopBroadcast,
     *  remplacement de broadcast…). Sans ça, l'auto-next s'enchaînait à tort. */
    private long lastElapsedMs = 0;
    private long lastDurationMs = 0;

    private OstPlayback() {}

    /** Joue une piste dans le contexte d'une playlist (pour le prev/next/auto). */
    public void play(OstAudioEngine engine, OstConfig cfg, List<OstTrack> list, int idx) {
        if (list == null || list.isEmpty() || idx < 0 || idx >= list.size()) return;
        playlist = new ArrayList<>(list);
        index = idx;
        OstTrack t = playlist.get(idx);
        expected = t;
        engine.play(t, 1f, null, 0f, 0f);
        cfg.setLastTrackId(t.trackId());
        cfg.save();
    }

    public void stop(OstAudioEngine engine) {
        expected = null;
        engine.stop();
    }

    public void next(OstAudioEngine engine, OstConfig cfg) { step(engine, cfg, +1, true); }
    public void prev(OstAudioEngine engine, OstConfig cfg) { step(engine, cfg, -1, true); }

    /** Appelé chaque tick : enchaîne UNIQUEMENT quand la piste courante s'est
     *  terminée NATURELLEMENT (jouée jusqu'au bout). Un arrêt externe
     *  (StopBroadcast reçu, remplacement d'un broadcast, coupure) NE doit PAS
     *  déclencher d'auto-next — sinon le lanceur sautait à la piste suivante. */
    public void tick(OstAudioEngine engine, OstConfig cfg) {
        if (expected == null) return;
        // Mémorise la position TANT QUE la source joue (avant que currentTrack()
        // ne la libère à la fin). En pause on ne touche pas (elapsed gelé).
        if (engine.isPlaying()) {
            lastElapsedMs = engine.elapsedMs();
            lastDurationMs = engine.durationMs();
        }
        // currentTrack() a un effet de bord : libère la source si AL_STOPPED.
        if (engine.currentTrack().isPresent() || engine.isPaused()) return;

        // La source a disparu. Fin naturelle = on était proche de la fin.
        boolean naturalEnd = lastDurationMs > 0 && lastElapsedMs >= lastDurationMs - 1500;
        lastElapsedMs = 0;
        lastDurationMs = 0;
        if (!naturalEnd) {
            expected = null; // stop externe → pas d'enchaînement
            return;
        }
        if (cfg.getRepeatMode() == OstConfig.REPEAT_ONE) {
            engine.play(expected, 1f, null, 0f, 0f);
        } else {
            step(engine, cfg, +1, false);
        }
    }

    private void step(OstAudioEngine engine, OstConfig cfg, int dir, boolean manual) {
        if (playlist.isEmpty()) { expected = null; return; }
        int n = playlist.size();
        int next;
        if (cfg.isShuffle() && n > 1) {
            int r = (int) (Math.random() * (n - 1));
            next = r >= index ? r + 1 : r; // un autre que l'actuel
        } else {
            next = index + dir;
        }
        if (next < 0 || next >= n) {
            if (manual || cfg.getRepeatMode() == OstConfig.REPEAT_ALL) {
                next = ((next % n) + n) % n; // boucle
            } else {
                expected = null; engine.stop(); return; // fin de liste, repeat off
            }
        }
        play(engine, cfg, playlist, next);
    }

    public boolean isActive() { return expected != null; }
}
