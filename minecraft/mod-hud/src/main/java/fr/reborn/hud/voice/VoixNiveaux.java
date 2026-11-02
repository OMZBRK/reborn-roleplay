package fr.reborn.hud.voice;

import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.audio.source.AudioSourceWriteEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.util.AudioUtil;
import su.plo.voice.proto.data.audio.source.SourceInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Niveau réel des voix pour le vumètre de qui parle (lot 2, brief §4.2, bas-droite §2.5) — chargée par
 * {@link RebornVoiceAddon} seulement si Plasmo Voice est là, comme {@link RadioVoix} (elle nomme des types de Plasmo
 * dans ses signatures). Lue par {@code VoiceState.Pv} une fois par tick.
 * <ul>
 *   <li><b>les autres</b> : {@link AudioSourceWriteEvent} (fil audio, chaque trame de 20 ms de chaque source, avant
 *       OpenAL ; jamais annulé) → crête en dBFS ({@code AudioUtil.calculateHighestAudioLevel}) → 0..1
 *       ({@code audioLevelToDoubleRange} : −60 dB → 0, 0 dB → 1), par identifiant de source ;</li>
 *   <li><b>soi</b> : {@link AudioCaptureProcessedEvent} (fil de capture, trames traitées du micro).</li>
 * </ul>
 * Aucune allocation par trame (un {@link Niveau} par source, créé une fois ; au-delà de 64 sources, on repart de zéro
 * comme le filtre de {@link RadioVoix}).
 */
public final class VoixNiveaux {

    static final VoixNiveaux INSTANCE = new VoixNiveaux();

    /** Sans trame depuis ce délai, la source se tait (niveau 0). */
    private static final long FRAIS_MS = 250;

    /** Écoute branchée sur Plasmo Voice (sinon les niveaux sont inconnus et le vumètre prend l'égaliseur factice). */
    volatile boolean inscrit;

    private static final class Niveau {
        volatile float crete;
        volatile long t;
    }

    private final ConcurrentHashMap<UUID, Niveau> sources = new ConcurrentHashMap<>();
    private volatile float soi;
    private volatile long soiT;

    private VoixNiveaux() {}

    @EventSubscribe
    public void onWrite(AudioSourceWriteEvent event) {
        SourceInfo info = event.getSource().getSourceInfo();
        short[] s = event.getSamples();
        if (info == null || s == null) return;
        UUID id = info.getId();
        Niveau n = sources.get(id);
        if (n == null) {
            if (sources.size() > 64) sources.clear();
            n = new Niveau();
            Niveau deja = sources.putIfAbsent(id, n);
            if (deja != null) n = deja;
        }
        n.crete = (float) AudioUtil.audioLevelToDoubleRange(AudioUtil.calculateHighestAudioLevel(s));
        n.t = System.currentTimeMillis();
    }

    @EventSubscribe
    public void onCapture(AudioCaptureProcessedEvent event) {
        var traite = event.getProcessed();
        short[] m = traite == null ? null : traite.getMono();
        if (m == null) return;
        soi = (float) AudioUtil.audioLevelToDoubleRange(AudioUtil.calculateHighestAudioLevel(m));
        soiT = System.currentTimeMillis();
    }

    /** Crête 0..1 de la source (0 si elle se tait), -1 si l'écoute n'est pas branchée. */
    float niveau(UUID source, long now) {
        if (!inscrit) return -1f;
        Niveau n = source == null ? null : sources.get(source);
        return n == null || now - n.t > FRAIS_MS ? 0f : n.crete;
    }

    /** Crête 0..1 de son propre micro, -1 si l'écoute n'est pas branchée. */
    float soi(long now) {
        if (!inscrit) return -1f;
        return now - soiT > FRAIS_MS ? 0f : soi;
    }

    /** Déconnexion : les identifiants de source changent. */
    void oublier() {
        sources.clear();
        soi = 0f;
    }
}
