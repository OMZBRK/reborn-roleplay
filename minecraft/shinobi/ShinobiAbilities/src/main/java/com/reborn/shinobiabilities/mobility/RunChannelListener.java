package com.reborn.shinobiabilities.mobility;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Canal entrant {@code reborn:run} (C2S) : le mod client Reborn demande, sur
 * pression de la touche naruto-run, l'état de la course chakraïque. Tous les
 * garde-fous (config-enabled, perso actif, voie/slot, chakra minimum, verrou
 * post-interruption) restent DANS {@link NarutoRun} — un paquet forgé depuis un
 * client bidouillé ne peut rien faire de plus que basculer sa propre course déjà
 * méritée.
 *
 * <p><b>Protocole (1 octet).</b> {@code 1} = démarrer, {@code 0} = arrêter, toute
 * autre valeur = bascule. Le client envoie l'état <i>souhaité</i> (et pas un
 * simple toggle) : ça rend la synchro idempotente, donc un paquet perdu ou
 * ré-émis ne laisse plus client et serveur en désaccord. Le plugin répond
 * systématiquement avec l'état autoritaire (cf {@link NarutoRun#syncToClient}).
 *
 * <p>{@link #CHANNEL_LEGACY} ({@code reborn:naruto}) est accepté en alias : les
 * builds du mod publiés avant ce correctif émettaient sur ce nom, qui n'était
 * écouté nulle part — la touche ne faisait donc rien en jeu.
 *
 * <p>Les plugin-messages arrivent sur le main thread → appel Bukkit direct sûr,
 * pas besoin de scheduler. Miroir de {@code AuthChannelListener} (plugin-guardian).
 *
 * <p>Enregistrement dans {@code ShinobiAbilities.onEnable} :
 * <pre>{@code
 * RunChannelListener run = new RunChannelListener(mobility.narutoRun());
 * for (String ch : RunChannelListener.CHANNELS) {
 *     getServer().getMessenger().registerIncomingPluginChannel(this, ch, run);
 * }
 * getServer().getMessenger().registerOutgoingPluginChannel(this, RunChannelListener.CHANNEL);
 * }</pre>
 */
public final class RunChannelListener implements PluginMessageListener {

    public static final String CHANNEL = "reborn:run";

    /** Ancien nom émis par les mods publiés avant le correctif de canal. */
    public static final String CHANNEL_LEGACY = "reborn:naruto";

    /** Canaux entrants à enregistrer (le S2C ne part que sur {@link #CHANNEL}). */
    public static final String[] CHANNELS = { CHANNEL, CHANNEL_LEGACY };

    private static final byte OP_STOP = 0;
    private static final byte OP_START = 1;

    private final NarutoRun narutoRun;

    public RunChannelListener(NarutoRun narutoRun) {
        this.narutoRun = narutoRun;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel) && !CHANNEL_LEGACY.equals(channel)) return;
        byte op = (message != null && message.length > 0) ? message[0] : OP_START;
        switch (op) {
            case OP_START -> narutoRun.requestStart(player);
            case OP_STOP -> narutoRun.requestStop(player);
            default -> narutoRun.toggle(player);
        }
    }
}
