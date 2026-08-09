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
 * État voix côté client, alimenté par {@link RebornVoiceAddon}. Fournit
 * l'ensemble des <b>entités qui parlent</b> (source audio active + audible) pour
 * que le renderer dessine une bulle au-dessus de leur tête.
 *
 * <p>Tout est défensif : sans PlasmoVoice (client null) ou en cas d'erreur API,
 * on renvoie un ensemble vide → aucune bulle, jamais de crash.
 */
public final class VoiceState {

    private VoiceState() {}

    private static volatile PlasmoVoiceClient client;

    static void setClient(PlasmoVoiceClient c) { client = c; }

    public static boolean available() { return client != null; }

    /**
     * Entity ids des joueurs/entités dont une source audio est active (= parlent),
     * à l'instant de l'appel. Vide si PlasmoVoice absent.
     */
    public static Set<Integer> speakingEntityIds() {
        Set<Integer> ids = new HashSet<>();
        PlasmoVoiceClient c = client;
        if (c == null) return ids;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ids;
        try {
            for (ClientAudioSource<?> source : c.getSourceManager().getSources()) {
                if (source == null || !source.isActivated()) continue;
                SourceInfo info = source.getSourceInfo();
                if (info instanceof EntitySourceInfo e) {
                    // Source liée à une entité (proximity voice) : id direct.
                    ids.add(e.getEntityId());
                } else if (info instanceof PlayerSourceInfo p && p.getPlayerInfo() != null) {
                    // Source joueur : UUID → entité joueur du monde → entity id.
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
