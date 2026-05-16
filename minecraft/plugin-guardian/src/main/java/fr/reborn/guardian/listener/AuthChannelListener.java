package fr.reborn.guardian.listener;

import fr.reborn.guardian.auth.AuthSessionState;
import fr.reborn.guardian.auth.PlayTokenVerifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Recoit les attestations envoyees par le mod Reborn Integrity sur le
 * canal {@code reborn:auth}.
 *
 * <p>Le payload est l'integralite des bytes du play-token en UTF-8 (pas
 * de prefixe de longueur ni de framing — c'est ce que le mod ecrit dans
 * son codec, cf {@code AuthPayload#CODEC}). On reconstitue la string et
 * on la passe au {@link PlayTokenVerifier}.
 *
 * <p>Decisions de rejet :
 * <ul>
 *   <li>Signature HMAC invalide -> kick immediat (le mod n'a pas pu
 *       forger ca tout seul, c'est un client adversarial).</li>
 *   <li>UUID du token != UUID du joueur connecte -> kick (token vole
 *       d'un autre joueur).</li>
 *   <li>Token expire -> kick (le launcher doit re-fetch).</li>
 * </ul>
 */
public final class AuthChannelListener implements PluginMessageListener {

    public static final String CHANNEL = "reborn:auth";

    private final Plugin plugin;
    private final PlayTokenVerifier verifier;
    private final AuthSessionState state;
    private final Logger logger;

    public AuthChannelListener(Plugin plugin, PlayTokenVerifier verifier, AuthSessionState state) {
        this.plugin = plugin;
        this.verifier = verifier;
        this.state = state;
        this.logger = plugin.getLogger();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        String token = new String(message, StandardCharsets.UTF_8).trim();
        if (token.isEmpty()) {
            kick(player, "play-token vide");
            return;
        }

        Optional<PlayTokenVerifier.TokenPayload> parsed = verifier.verify(token);
        if (parsed.isEmpty()) {
            kick(player, "play-token invalide ou expire");
            return;
        }

        PlayTokenVerifier.TokenPayload payload = parsed.get();
        if (payload.asUuid() == null || !payload.asUuid().equals(player.getUniqueId())) {
            // Un client legitime ne devrait jamais arriver ici : le launcher
            // signe avec l'UUID du compte authentifie. Si on voit ca, c'est
            // une tentative de replay d'un token vole d'un autre user.
            kick(player, "play-token : UUID ne correspond pas");
            return;
        }

        if (state.markAuthenticated(player.getUniqueId())) {
            logger.info(String.format(
                "[guardian] attestation OK pour %s (%s)",
                player.getName(),
                payload.mcUsername()
            ));
        } else {
            // Attestation recue mais kick deja parti — peut arriver si
            // le mod envoie tardivement (latence reseau enorme).
            logger.warning(String.format(
                "[guardian] attestation tardive pour %s, deja kick",
                player.getName()
            ));
        }
    }

    private void kick(Player player, String reason) {
        logger.warning(String.format(
            "[guardian] kick %s : %s",
            player.getName(),
            reason
        ));
        // Bukkit#kickPlayer doit etre appele sur le main thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.kick(net.kyori.adventure.text.Component.text(
                    "Reborn : " + reason + ". Relance le launcher."
                ));
            }
            state.forget(player.getUniqueId());
        });
    }
}
