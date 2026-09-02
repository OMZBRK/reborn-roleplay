package com.reborn.shinobiabilities.mobility;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Canal entrant {@code reborn:run} (C2S) : le mod client Reborn demande, sur
 * pression de la touche naruto-run, de basculer la course chakraïque. On appelle
 * directement {@link NarutoRun#toggle(Player)} — tous les garde-fous
 * (config-enabled, perso actif, voie/slot, chakra minimum) restent DANS
 * {@code toggle()}, donc un paquet forgé depuis un client bidouillé ne peut rien
 * faire de plus que basculer sa propre course déjà méritée.
 *
 * <p>Les plugin-messages arrivent sur le main thread → appel Bukkit direct sûr,
 * pas besoin de scheduler. Miroir de {@code AuthChannelListener} (plugin-guardian).
 *
 * <p>Enregistrement dans {@code ShinobiAbilities.onEnable} :
 * <pre>{@code
 * getServer().getMessenger().registerIncomingPluginChannel(this,
 *     RunChannelListener.CHANNEL,
 *     new RunChannelListener(mobility.narutoRun()));
 * }</pre>
 */
public final class RunChannelListener implements PluginMessageListener {

    public static final String CHANNEL = "reborn:run";

    private static final byte OP_TOGGLE = 1;

    private final NarutoRun narutoRun;

    public RunChannelListener(NarutoRun narutoRun) {
        this.narutoRun = narutoRun;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        // 1 octet d'opcode ; par défaut = toggle. (Réservé pour start/stop explicites.)
        byte op = (message != null && message.length > 0) ? message[0] : OP_TOGGLE;
        if (op == OP_TOGGLE) {
            narutoRun.toggle(player);
        }
    }
}
