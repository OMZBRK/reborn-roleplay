package fr.reborn.guardian.listener;

import fr.reborn.guardian.auth.AttestationPolicy;
import fr.reborn.guardian.auth.AuthSessionState;
import fr.reborn.guardian.auth.ScopeMetadata;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Recoit les attestations envoyees par le mod Reborn Integrity sur le
 * canal {@code reborn:auth}.
 *
 * <p>Le payload est l'integralite des bytes du play-token en UTF-8 (pas
 * de prefixe de longueur ni de framing — c'est ce que le mod ecrit dans
 * son codec, cf {@code AuthPayload#CODEC}). On reconstitue la string et
 * on la passe a {@link AttestationPolicy}.
 *
 * <p>Decisions de rejet (detail dans {@link AttestationPolicy.Reason}) :
 * signature invalide, jeton expire, UUID d'un autre compte, portee inconnue
 * ou non ouverte sur ce serveur, jeton emis pour un autre serveur.
 *
 * <p>Tout ce qui touche le joueur (kick, annulation du kick differe,
 * metadonnee {@code reborn.scope}) passe par le fil principal : les plugin
 * messages peuvent arriver async selon les versions Paper, et ni
 * {@code Player#kick} ni les metadonnees ne sont thread-safe. Le kick differe
 * de {@link PendingAuthListener} tourne lui aussi sur le fil principal : les
 * deux ne peuvent donc pas se croiser.
 */
public final class AuthChannelListener implements PluginMessageListener {

    public static final String CHANNEL = "reborn:auth";

    private final Plugin plugin;
    private final AttestationPolicy policy;
    private final AuthSessionState state;
    private final Logger logger;

    public AuthChannelListener(Plugin plugin, AttestationPolicy policy, AuthSessionState state) {
        this.plugin = plugin;
        this.policy = policy;
        this.state = state;
        this.logger = plugin.getLogger();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        String token = message == null ? "" : new String(message, StandardCharsets.UTF_8).trim();
        // La verification (HMAC, JSON) ne touche pas Bukkit : elle peut se faire sur ce fil.
        AttestationPolicy.Verdict verdict = policy.evaluate(token, player.getUniqueId());
        onMainThread(() -> apply(player, verdict));
    }

    private void apply(Player player, AttestationPolicy.Verdict verdict) {
        if (!player.isOnline()) {
            state.forget(player.getUniqueId());
            return;
        }

        // Une seule attestation par connexion : une seconde (renvoi du mod,
        // reconfiguration) ne change jamais la portee deja admise.
        if (state.scopeOf(player.getUniqueId()).isPresent()) {
            logger.fine(String.format("[guardian] attestation supplémentaire ignorée pour %s", player.getName()));
            return;
        }

        switch (verdict) {
            case AttestationPolicy.Refused refused -> kick(player, refused.reason());
            case AttestationPolicy.Accepted accepted -> admit(player, accepted);
        }
    }

    private void admit(Player player, AttestationPolicy.Accepted accepted) {
        if (state.markAuthenticated(player.getUniqueId(), accepted.scope())) {
            // Preuve positive : la marque n'existe qu'apres une attestation valide.
            ScopeMetadata.set(plugin, player, accepted.scope());
            String aud = accepted.payload().aud();
            logger.info(String.format(
                "[guardian] attestation OK pour %s (%s) — portée %s, serveur visé %s",
                player.getName(),
                accepted.payload().mcUsername(),
                accepted.scope().claim(),
                aud == null || aud.isBlank() ? "non précisé" : aud
            ));
        } else {
            // Attestation recue mais kick deja parti — peut arriver si
            // le mod envoie tardivement (latence reseau enorme).
            logger.warning(String.format(
                "[guardian] attestation tardive pour %s, déjà expulsé",
                player.getName()
            ));
        }
    }

    private void kick(Player player, AttestationPolicy.Reason reason) {
        logger.warning(String.format(
            "[guardian] kick %s : %s",
            player.getName(),
            reason.logLabel()
        ));
        state.forget(player.getUniqueId());
        ScopeMetadata.clear(plugin, player);
        player.kick(Component.text(reason.playerMessage()));
    }

    private void onMainThread(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}
