package com.reborn.shinobiradio;

import su.plo.slib.api.permission.PermissionDefault;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.PlayerActivationInfo;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerBroadcastSource;
import su.plo.voice.api.server.audio.source.ServerPlayerSource;
import su.plo.voice.api.server.event.connection.UdpClientDisconnectedEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.packets.tcp.clientbound.SourceAudioEndPacket;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La voix de la radio, côté Plasmo Voice (docs/gameplay/RADIO.md §2, §5, §6).
 *
 * <ul>
 *   <li><b>Activation « radio »</b> (touche B côté mod) : sans proximité ({@code setProximity(false)}), non transitive
 *       (la voix de proximité ne part pas en même temps), permise à tous mais soumise à des <b>conditions vérifiées à
 *       chaque paquet</b> ({@link Radio#peutEmettre} : personnage, oreillette portée en état et liée à une escouade
 *       existante, ni inconscient, ni mort, ni mode staff, ni coupé par le staff).</li>
 *   <li><b>Routage</b> : chaque trame de 20 ms est renvoyée, par une <b>source de diffusion</b> par émetteur (ligne
 *       « radio », non spatialisée, icône masquée), aux membres connectés qui portent leur oreillette en état, et au
 *       staff à l'écoute. À terre (ou porté), le serveur saute des trames en rafales (coupures) ; le mod ajoute
 *       l'effet « haché ».</li>
 *   <li><b>Chuchoté à 4 blocs</b> : la même trame part sur une source <b>joueur</b> de la ligne de proximité, distance
 *       {@code voix.chuchote-blocs}, sauf pour ceux qui l'entendent déjà à la radio.</li>
 *   <li><b>Ligne « radio »</b> : un curseur de volume à part dans le menu de Plasmo Voice.</li>
 *   <li><b>Début et fin</b> de chaque prise de parole remontent à {@link Radio} (HUD « parle » et journal de
 *       l'émission, <b>jamais le contenu</b>).</li>
 * </ul>
 * Les écouteurs tournent sur les threads de Plasmo Voice : on ne lit que l'instantané immuable de {@link Radio}.
 */
final class VoixRadio {

    static final String NOM = "radio";

    private final ShinobiRadio plugin;
    private final RadioAddon addon;
    private final PlasmoVoiceServer voix;
    private ServerActivation activation;
    private ServerSourceLine ligne;
    private final Map<UUID, ServerBroadcastSource> diffusions = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> derniersDest = new ConcurrentHashMap<>();
    private final Map<UUID, ServerPlayerSource> chuchotes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> actifs = new ConcurrentHashMap<>();

    VoixRadio(ShinobiRadio plugin, RadioAddon addon, PlasmoVoiceServer voix) {
        this.plugin = plugin;
        this.addon = addon;
        this.voix = voix;
    }

    private Radio radio() { return plugin.radio(); }

    private RadioConfig cfg() { return plugin.cfg(); }

    void enregistrer() {
        retirer();
        RadioConfig cfg = cfg();
        activation = voix.getActivationManager()
                .createBuilder(addon, NOM, "Radio (escouade)", "plasmovoice:textures/icons/microphone_radio.png",
                        "pv.activation.radio", cfg.activationPoids)
                .setProximity(false)
                .setTransitive(false)
                .setStereoSupported(false)
                .setPermissionDefault(PermissionDefault.TRUE)
                .setRequirements(new ServerActivation.Requirements() {
                    @Override
                    public boolean checkRequirements(VoicePlayer player, PlayerAudioPacket packet) {
                        Radio r = radio();
                        return r != null && r.peutEmettre(player.getInstance().getUuid());
                    }

                    @Override
                    public boolean checkRequirements(VoicePlayer player, PlayerAudioEndPacket packet) {
                        return actifs.containsKey(player.getInstance().getUuid());
                    }
                })
                .build();
        activation.onPlayerActivation(this::trame);
        activation.onPlayerActivationEnd(this::fin);
        ligne = voix.getSourceLineManager()
                .createBuilder(addon, NOM, "Radio (escouade)", "plasmovoice:textures/icons/speaker_radio.png", cfg.lignePoids)
                .setDefaultVolume(cfg.volumeDefaut)
                .build();
        voix.getEventBus().register(addon, this);
        plugin.getLogger().info("[radio] Activation et ligne « radio » enregistrées dans Plasmo Voice.");
    }

    void retirer() {
        try {
            voix.getEventBus().unregister(addon, this);
        } catch (RuntimeException ignored) { }
        for (UUID id : new ArrayList<>(actifs.keySet())) {
            Radio r = radio();
            if (r != null) r.finEmission(id);
        }
        actifs.clear();
        diffusions.clear();
        derniersDest.clear();
        chuchotes.clear();
        voix.getActivationManager().unregister(NOM);
        voix.getSourceLineManager().unregister(NOM);
        activation = null;
        ligne = null;
    }

    @EventSubscribe
    public void onClientDisconnected(UdpClientDisconnectedEvent event) {
        UUID id = event.getConnection().getPlayer().getInstance().getUuid();
        oublier(id);
    }

    private void oublier(UUID id) {
        actifs.remove(id);
        derniersDest.remove(id);
        ServerBroadcastSource d = diffusions.remove(id);
        if (d != null) d.remove();
        ServerPlayerSource c = chuchotes.remove(id);
        if (c != null) c.remove();
        Radio r = radio();
        if (r != null) r.finEmission(id);
    }

    /* ------------------------------------------------------------------ trames */

    private ServerActivation.Result trame(VoicePlayer player, PlayerAudioPacket packet) {
        Radio r = radio();
        ServerSourceLine l = ligne;
        if (r == null || l == null || activation == null || !activation.getId().equals(packet.getActivationId())) {
            return ServerActivation.Result.IGNORED;
        }
        UUID id = player.getInstance().getUuid();
        Radio.Emetteur e = r.emetteur(id);
        if (e == null) return ServerActivation.Result.IGNORED;

        ServerBroadcastSource src = diffusions.computeIfAbsent(id, k -> {
            ServerBroadcastSource s = l.createBroadcastSource(false);
            s.setIconVisible(false);
            return s;
        });
        boolean nouveau = actifs.putIfAbsent(id, Boolean.TRUE) == null;
        if (nouveau || !r.enEmission(id)) r.debutEmission(id, src.getId());
        else r.trame(id);

        // Destinataires : ceux qui entendent l'escouade, sauf l'émetteur. Mis à jour seulement s'ils changent.
        List<UUID> dest = r.destinataires(e.escouade());
        if (dest != derniersDest.get(id)) {
            derniersDest.put(id, dest);
            List<VoicePlayer> joueurs = new ArrayList<>(dest.size());
            for (UUID d : dest) {
                if (d.equals(id)) continue;
                voix.getPlayerManager().getPlayerById(d).ifPresent(joueurs::add);
            }
            src.setPlayers(joueurs);
        }

        // À terre : des trames sautent en rafales de 80 ms (coupures). Le mod ajoute le grésillement et les trous.
        boolean saute = e.hache() && coupure(packet.getSequenceNumber(), cfg().hacheTramesPerdues);
        PlayerActivationInfo info = new PlayerActivationInfo(player, packet);
        if (!saute) {
            src.sendAudioPacket(new SourceAudioPacket(packet.getSequenceNumber(), (byte) src.getState(), packet.getData(),
                    src.getId(), (short) 0), info);
        }

        // Autour de soi : chuchoté, voix normale, à quelques blocs (pas pour ceux qui ont déjà la radio).
        int rayon = cfg().chuchoteBlocs;
        if (rayon > 0 && player instanceof VoiceServerPlayer vsp) {
            ServerPlayerSource ch = chuchote(vsp);
            if (ch != null) {
                short d = (short) rayon;
                ch.sendAudioPacket(new SourceAudioPacket(packet.getSequenceNumber(), (byte) ch.getState(), packet.getData(),
                        ch.getId(), d), d, info);
            }
        }
        return ServerActivation.Result.HANDLED;
    }

    private ServerActivation.Result fin(VoicePlayer player, PlayerAudioEndPacket packet) {
        UUID id = player.getInstance().getUuid();
        if (activation == null || !activation.getId().equals(packet.getActivationId())) return ServerActivation.Result.IGNORED;
        if (actifs.remove(id) == null) return ServerActivation.Result.IGNORED;
        ServerBroadcastSource src = diffusions.get(id);
        if (src != null) src.sendPacket(new SourceAudioEndPacket(src.getId(), packet.getSequenceNumber()));
        ServerPlayerSource ch = chuchotes.get(id);
        if (ch != null) ch.sendPacket(new SourceAudioEndPacket(ch.getId(), packet.getSequenceNumber()), (short) Math.max(1, cfg().chuchoteBlocs));
        Radio r = radio();
        if (r != null) r.finEmission(id);
        return ServerActivation.Result.HANDLED;
    }

    private ServerPlayerSource chuchote(VoiceServerPlayer vsp) {
        UUID id = vsp.getInstance().getUuid();
        ServerPlayerSource s = chuchotes.get(id);
        if (s != null) return s;
        Optional<ServerSourceLine> prox = voix.getSourceLineManager().getLineByName("proximity");
        if (prox.isEmpty()) return null;
        ServerPlayerSource n = prox.get().createPlayerSource(vsp, false);
        n.setIconVisible(true);
        // Ceux qui entendent déjà l'escouade à la radio n'ont pas la voix en double.
        n.<VoicePlayer>addFilter(vp -> {
            Radio r = radio();
            Radio.Emetteur e = r == null ? null : r.emetteur(id);
            return e == null || !r.destinataires(e.escouade()).contains(vp.getInstance().getUuid());
        });
        chuchotes.put(id, n);
        return n;
    }

    /** Rafales de 4 trames (80 ms) sautées avec la probabilité {@code part}, de façon stable dans la rafale. */
    static boolean coupure(long seq, double part) {
        if (part <= 0) return false;
        long rafale = seq / 4;
        long h = (rafale * 0x9E3779B97F4A7C15L) ^ (rafale >>> 7);
        h ^= (h >>> 29);
        return (Math.floorMod(h, 1000)) < part * 1000;
    }
}
