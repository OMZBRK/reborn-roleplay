package com.reborn.shinobiradio;

import com.reborn.shinobicore.api.EtatService;
import com.reborn.shinobicore.api.KoService;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Etat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.PlayerActivationInfo;
import su.plo.voice.api.server.config.ServerConfig;
import su.plo.voice.api.server.event.audio.source.ServerSourceAudioPacketEvent;
import su.plo.voice.api.server.event.config.VoiceServerConfigReloadedEvent;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Chuchoté forcé à terre</b> (décision du 31/10 : « à terre = chuchoté seulement »). Les portées restent celles de
 * Plasmo Voice ; on ne touche qu'à la voix de <b>proximité</b> d'un joueur dont la capacité {@code VOCAL} est limitée
 * par {@code etats.yml} (ShinobiCore) :
 * <ul>
 *   <li>{@code VOCAL == 1} (à terre, inconscient) : chaque trame est ramenée au rayon chuchoté — la distance de
 *       l'événement (qui choisit les auditeurs) <b>et</b> celle du paquet (atténuation côté client). Rayon :
 *       {@code voix.a-terre-blocs}, sinon la plus petite portée de proximité de Plasmo, sinon {@value #REPLI}.</li>
 *   <li>{@code VOCAL == 0} (mort RP, corps contrôlé ou vide, cinématique) : la trame ne part pas (événement annulé).</li>
 * </ul>
 * Abonné {@code ServerSourceAudioPacketEvent} en priorité {@code HIGHEST} (appelé en dernier), filtré sur l'activation
 * de proximité ({@code VoiceActivation.PROXIMITY_ID}) : le chuchoté de la radio ({@link VoixRadio}) et les autres
 * activations ne sont pas touchés. Les paquets de fin de parole non plus (ils ne portent pas de distance ; brider leurs
 * destinataires laisserait des sources ouvertes chez ceux qui entendaient avant la chute).
 *
 * <p>L'écouteur tourne sur le thread UDP de Plasmo : il ne lit que {@link #limites}, tenu sur le thread principal (à
 * chaque {@code EtatChangedEvent}, à la déconnexion, et chaque seconde en filet de sécurité).
 */
final class VoixATerre {

    /** Rayon chuchoté quand ni la config ni Plasmo n'en donnent un (blocs). */
    static final int REPLI = 8;

    private final ShinobiRadio plugin;
    private volatile PlasmoVoiceServer voix;
    private volatile RadioAddon addon;
    /** Niveau {@code VOCAL} des joueurs limités (1 chuchoté, 0 muet) ; absent = voix normale. */
    private final Map<UUID, Integer> limites = new ConcurrentHashMap<>();
    /** {@code voix.a-terre-blocs} (0 = portée de Plasmo), posé sur le thread principal. */
    private volatile int configure;
    /** Plus petite portée de proximité de Plasmo, relue à chaque (re)chargement de sa config. */
    private volatile int plusPetite = REPLI;

    VoixATerre(ShinobiRadio plugin) {
        this.plugin = plugin;
    }

    /** Plasmo Voice prêt (addon initialisé, à chaque rechargement de Plasmo). */
    void brancher(PlasmoVoiceServer v, RadioAddon a) {
        debrancher();
        voix = v;
        addon = a;
        plusPetite = plusPetite(v.getConfig());
        v.getEventBus().register(a, this);
    }

    void debrancher() {
        PlasmoVoiceServer v = voix;
        RadioAddon a = addon;
        voix = null;
        if (v != null && a != null) {
            try {
                v.getEventBus().unregister(a, this);
            } catch (RuntimeException ignored) { }
        }
    }

    void regler(int aTerreBlocs) { configure = Math.max(0, aTerreBlocs); }

    /** Rayon chuchoté en vigueur (blocs). */
    int rayon() {
        int c = configure;
        return c > 0 ? c : plusPetite;
    }

    /* ================================================================== instantané (thread principal) */

    /** Après un changement d'état (ShinobiCore, au plus une fois par tick et par joueur). */
    void etat(Player p, Etat apres) {
        noter(p.getUniqueId(), apres.capacites().niveau(Capacite.VOCAL));
    }

    void oublier(UUID id) { limites.remove(id); }

    /** Tous les joueurs en ligne : au démarrage, puis chaque seconde (filet de sécurité si un événement manque). */
    void rafraichir() {
        EtatService etats = EtatService.get();
        KoService ko = etats == null ? Bukkit.getServicesManager().load(KoService.class) : null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            int n;
            if (etats != null) n = etats.etat(id).capacites().niveau(Capacite.VOCAL);
            else n = ko != null && ko.isKo(id) ? 1 : 2;   // repli d'un ShinobiCore sans service des états
            noter(id, n);
        }
        limites.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void noter(UUID id, int niveau) {
        if (niveau >= 2) limites.remove(id);
        else limites.put(id, niveau);
    }

    /* ================================================================== Plasmo Voice (thread UDP) */

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onSourceAudio(ServerSourceAudioPacketEvent event) {
        if (limites.isEmpty()) return;
        PlayerActivationInfo info = event.getActivationInfo();
        if (info == null) return;
        PlayerAudioPacket paquet = info.getAudioPacket();
        if (paquet == null || !VoiceActivation.PROXIMITY_ID.equals(paquet.getActivationId())) return;
        Integer niveau = limites.get(info.getPlayer().getInstance().getUuid());
        if (niveau == null) return;
        if (niveau <= 0) {
            event.setCancelled(true);
            return;
        }
        short d = (short) rayon();
        if (event.getDistance() > d) {
            event.setDistance(d);
            event.getPacket().setDistance(d);
        }
    }

    @EventSubscribe
    public void onConfigRechargee(VoiceServerConfigReloadedEvent event) {
        plusPetite = plusPetite(event.getConfig());
    }

    /**
     * Plus petite portée de {@code [voice.proximity] distances}. Mode plage de Plasmo ({@code [-1, N]} : toute valeur de
     * 1 à N) : pas de « plus petite » réglée, on prend {@value #REPLI} (N au plus).
     */
    private static int plusPetite(ServerConfig config) {
        try {
            int min = Integer.MAX_VALUE, max = 0;
            boolean plage = false;
            for (Integer d : config.voice().proximity().distances()) {
                if (d == null) continue;
                if (d <= 0) { plage = true; continue; }
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            if (plage) return max > 0 ? Math.min(REPLI, max) : REPLI;
            return min == Integer.MAX_VALUE ? REPLI : min;
        } catch (RuntimeException e) {   // config pas encore chargée
            return REPLI;
        }
    }
}
