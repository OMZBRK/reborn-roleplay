package fr.reborn.hud.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.proto.data.audio.source.EntitySourceInfo;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pont vers PlasmoVoice côté client : qui parle (bulle) + état voix perso
 * (micro/casque du panneau vitals).
 *
 * <p>On récupère le client via l'<b>instance statique</b>
 * {@code ModVoiceClient.INSTANCE} plutôt que l'injection {@code @InjectPlasmoVoice}
 * (qui n'alimente pas un champ Java de façon fiable). Tout est défensif : sans
 * PlasmoVoice (classe absente / non initialisé) chaque accès retombe sur une
 * valeur neutre, jamais de crash.
 */
public final class VoiceState {

    private VoiceState() {}

    private static PlasmoVoiceClient client() {
        try {
            return su.plo.voice.client.ModVoiceClient.INSTANCE;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean available() { return client() != null; }

    // ── État voix perso (testable en solo) ─────────────────────────
    /** true = le joueur local <b>transmet</b> en ce moment. On lit l'activation
     *  ({@code ClientActivation.isActive()}), PAS {@code AudioCapture.isActive()}
     *  qui signifie juste « micro ouvert » (toujours vrai → faux positif). */
    public static boolean selfSpeaking() {
        PlasmoVoiceClient c = client();
        try {
            if (c == null) return false;
            return c.getActivationManager().getParentActivation()
                .map(a -> a.isActive() && !a.isDisabled())
                .orElse(false);
        } catch (Throwable t) { return false; }
    }

    /** true = micro coupé (bouton mute local) ou mute serveur. */
    public static boolean selfMuted() {
        PlasmoVoiceClient c = client();
        try {
            if (c == null) return false;
            if (c.getAudioCapture().isServerMuted()) return true;
            return Boolean.TRUE.equals(c.getConfig().getVoice().getMicrophoneDisabled().value());
        } catch (Throwable t) { return false; }
    }

    /** true = voix désactivée / assourdi (casque coupé). */
    public static boolean selfDeafened() {
        PlasmoVoiceClient c = client();
        try {
            return c != null && Boolean.TRUE.equals(c.getConfig().getVoice().getDisabled().value());
        } catch (Throwable t) { return false; }
    }

    // ── Volume d'un joueur (menu R, GAMEPLAN §14, 24/10) ───────────
    /** Volume de la voix d'un joueur chez moi seulement : 1 = normal ; on baisse, on ne coupe jamais. */
    public static double playerVolume(UUID id) {
        Double d = DEBUG_VOLUMES.get(id);
        if (d != null) return d;
        PlasmoVoiceClient c = client();
        try {
            if (c == null) return 1.0;
            Object v = c.getConfig().getVoice().getVolumes().getPlayerVolume(id).value();
            return v instanceof Number n ? n.doubleValue() : 1.0;
        } catch (Throwable t) { return 1.0; }
    }

    /** Règle le volume (borné à 25 %… 100 %, jamais 0) et enregistre la config de Plasmo Voice. */
    public static void setPlayerVolume(UUID id, double volume) {
        double v = Math.max(0.25, Math.min(1.0, volume));
        PlasmoVoiceClient c = client();
        try {
            if (c == null) return;
            c.getConfig().getVoice().getVolumes().setPlayerVolume(id, v);
            Object cfg = c.getConfig();
            cfg.getClass().getMethod("save", boolean.class).invoke(cfg, true);   // hors API, comme RadioVoix
        } catch (Throwable ignored) { }
    }

    /** Banc d'essai : volumes imposés (aucune écriture dans la config de Plasmo Voice). */
    public static final java.util.Map<UUID, Double> DEBUG_VOLUMES = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Micro d'un autre joueur (onglet Douane du Poste de garde, 25/10) ─
    public static final int MICRO_INCONNU = -1, MICRO_SANS_VOIX = 0, MICRO_ACTIF = 1, MICRO_COUPE = 2, MICRO_MUET = 3;

    /**
     * État du micro d'un joueur, tel que le serveur Plasmo Voice le diffuse : {@link #MICRO_ACTIF}, {@link #MICRO_COUPE}
     * (il a coupé son micro), {@link #MICRO_MUET} (rendu muet par le staff), {@link #MICRO_SANS_VOIX} (pas de Plasmo
     * Voice, ou voix désactivée) ; {@link #MICRO_INCONNU} sans Plasmo Voice chez soi ou hors serveur vocal.
     */
    public static int micro(UUID id) {
        Integer d = DEBUG_MICROS.get(id);
        if (d != null) return d;
        PlasmoVoiceClient c = client();
        try {
            if (c == null || id == null) return MICRO_INCONNU;
            var conn = c.getServerConnection();
            if (conn.isEmpty()) return MICRO_INCONNU;
            var info = conn.get().getPlayerById(id);
            if (info.isEmpty() || info.get().isVoiceDisabled()) return MICRO_SANS_VOIX;
            if (info.get().isMuted()) return MICRO_MUET;
            return info.get().isMicrophoneMuted() ? MICRO_COUPE : MICRO_ACTIF;
        } catch (Throwable t) { return MICRO_INCONNU; }
    }

    /** Banc d'essai : états de micro imposés. */
    public static final java.util.Map<UUID, Integer> DEBUG_MICROS = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Qui parle autour (bulle de parole) ─────────────────────────
    /**
     * Entity ids des joueurs/entités dont une source audio est active (= parlent).
     * Vide si PlasmoVoice absent. Ne contient jamais le joueur local (on ne
     * s'entend pas soi-même → pas de source).
     */
    public static Set<Integer> speakingEntityIds() {
        Set<Integer> ids = new HashSet<>();
        PlasmoVoiceClient c = client();
        if (c == null) return ids;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ids;
        try {
            for (ClientAudioSource<?> source : c.getSourceManager().getSources()) {
                if (source == null || !source.isActivated()) continue;
                SourceInfo info = source.getSourceInfo();
                if (info instanceof EntitySourceInfo e) {
                    ids.add(e.getEntityId());
                } else if (info instanceof PlayerSourceInfo p && p.getPlayerInfo() != null) {
                    UUID uuid = p.getPlayerInfo().getPlayerId();
                    Player pl = uuid != null ? mc.level.getPlayerByUUID(uuid) : null;
                    if (pl != null) ids.add(pl.getId());
                }
            }
        } catch (Throwable ignored) {
            // API indisponible / en cours d'init → pas de bulle ce frame.
        }
        return ids;
    }
}
