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
    /** true = le joueur local parle / transmet en ce moment. */
    public static boolean selfSpeaking() {
        PlasmoVoiceClient c = client();
        try {
            return c != null && c.getAudioCapture().isActive();
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
