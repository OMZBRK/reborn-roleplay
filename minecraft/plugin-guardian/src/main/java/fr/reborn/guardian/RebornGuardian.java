package fr.reborn.guardian;

import fr.reborn.guardian.auth.AuthSessionState;
import fr.reborn.guardian.auth.PlayTokenVerifier;
import fr.reborn.guardian.listener.AuthChannelListener;
import fr.reborn.guardian.listener.PendingAuthListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reborn Guardian — plugin Paper qui valide les clients connectes au serveur.
 *
 * <p>Boucle d'attestation (cf {@code PLAN_CONCEPTION_LAUNCHER.md §9.5}) :
 * <ol>
 *   <li>Le launcher fetche un play-token aupres de l'API (signe HMAC-SHA256
 *       avec {@code REBORN_PLAY_TOKEN_SECRET}, le meme secret est lu ici).</li>
 *   <li>Le launcher passe le token a la JVM via sysprop, le mod
 *       Reborn Integrity le lit et l'envoie au serveur via le custom payload
 *       {@code reborn:auth} au moment du JOIN.</li>
 *   <li>Ce plugin recoit le payload, verifie la signature HMAC, l'expiration
 *       et que l'UUID du token matche celui du joueur connecte.</li>
 *   <li>Si la verif passe, le joueur reste. Sinon, kick avec message
 *       actionnable. Si rien n'arrive en 8s, kick automatique.</li>
 * </ol>
 *
 * <p>Le secret est lu depuis l'env var {@code REBORN_PLAY_TOKEN_SECRET}.
 * En dev local via {@code ./gradlew runServer}, l'env du Gradle daemon est
 * heritee (relancer le daemon apres modification du shell env si besoin).
 */
public final class RebornGuardian extends JavaPlugin {

    private static final String SECRET_ENV = "REBORN_PLAY_TOKEN_SECRET";
    private static final String SECRET_CONFIG_KEY = "play-token-secret";

    @Override
    public void onEnable() {
        getLogger().info("Reborn Guardian " + getPluginMeta().getVersion() + " demarre.");

        // Cree plugins/RebornGuardian/config.yml depuis les resources si
        // absent. Le secret y est vide par defaut — l'admin serveur le pose
        // manuellement OU exporte la variable d'env (priorite a l'env).
        saveDefaultConfig();

        String secret = System.getenv(SECRET_ENV);
        String source = "env " + SECRET_ENV;
        if (secret == null || secret.isBlank()) {
            // FileConfiguration#getString peut renvoyer null si la cle est
            // presente mais vide (`play-token-secret:`), donc on guarde.
            String fromConfig = getConfig().getString(SECRET_CONFIG_KEY);
            secret = fromConfig != null ? fromConfig.trim() : "";
            source = "config.yml#" + SECRET_CONFIG_KEY;
        }

        if (secret.isBlank()) {
            getLogger().severe(
                "Secret play-token absent. Pose-le dans plugins/RebornGuardian/config.yml " +
                    "(cle '" + SECRET_CONFIG_KEY + "') ou exporte l'env var " + SECRET_ENV +
                    ". Plugin desactive."
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayTokenVerifier verifier;
        try {
            verifier = new PlayTokenVerifier(secret);
        } catch (IllegalArgumentException e) {
            getLogger().severe("Secret play-token (source : " + source + ") invalide : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Secret play-token charge (source : " + source + ").");

        AuthSessionState state = new AuthSessionState();

        // Canal entrant — le mod Reborn Integrity envoie ses paquets ici.
        getServer().getMessenger().registerIncomingPluginChannel(
            this,
            AuthChannelListener.CHANNEL,
            new AuthChannelListener(this, verifier, state)
        );

        getServer().getPluginManager().registerEvents(
            new PendingAuthListener(this, state),
            this
        );

        getLogger().info("[guardian] canal " + AuthChannelListener.CHANNEL + " enregistre, attestation active.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Reborn Guardian arrete.");
    }
}
