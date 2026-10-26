package fr.reborn.integrity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Entrée client du mod Reborn Integrity — attestation uniquement.
 *
 * <p>Le launcher écrit le jeton de jeu dans un fichier (sysprop
 * {@code reborn.playTokenPath}, cf
 * {@code apps/launcher/src-tauri/src/launcher/game.rs}) et, pour un staff
 * avec un serveur de dev, un second jeton dans
 * {@code reborn.playTokenPath.dev}. Le jeton ne vit que 5 min : le launcher
 * réécrit ces fichiers pendant toute la partie
 * ({@code launcher/play_tokens.rs}), et le mod les relit donc AU MOMENT de
 * la connexion, pas au démarrage du jeu (un joueur resté plus de 5 min au
 * menu présentait sinon un jeton expiré).
 *
 * <p>À chaque connexion ({@link ClientPlayConnectionEvents#JOIN}) :
 * <ol>
 *   <li>choisit le fichier : celui du dev si l'adresse rejointe est celle
 *       de {@code reborn.server.dev.host} / {@code .port} (sélecteur
 *       Build/Dev du mod HUD), sinon le principal ;</li>
 *   <li>lit le jeton frais (illisible : dernier jeton lu de ce fichier ;
 *       jeton dev introuvable : jeton principal, comme avant) ;</li>
 *   <li>l'envoie sur le canal C2S {@code reborn:auth}.</li>
 * </ol>
 *
 * <p>Si la sysprop est absente ou la lecture échoue, on log et on
 * continue : le client est exploitable hors-Reborn (serveurs vanilla, LAN,
 * etc.) ; c'est juste qu'il ne s'attestera pas et se fera kick par notre
 * serveur si jamais il s'y connecte.
 */
public final class RebornIntegrityClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-integrity");
    private static final String TOKEN_PATH_PROPERTY = "reborn.playTokenPath";
    private static final String DEV_TOKEN_PATH_PROPERTY = "reborn.playTokenPath.dev";
    private static final String DEV_HOST_PROPERTY = "reborn.server.dev.host";
    private static final String DEV_PORT_PROPERTY = "reborn.server.dev.port";

    private final TokenFile mainToken = TokenFile.fromProperty(TOKEN_PATH_PROPERTY);
    private final TokenFile devToken = TokenFile.fromProperty(DEV_TOKEN_PATH_PROPERTY);

    @Override
    public void onInitializeClient() {
        if (mainToken == null) {
            LOGGER.info(
                "sysprop {} absente — mode standalone, aucun jeton à envoyer.",
                TOKEN_PATH_PROPERTY
            );
        } else {
            // Diagnostic au démarrage seulement : le jeton envoyé est relu à
            // la connexion.
            mainToken.read();
        }
        // Enregistre le type de payload côté C2S (client → server). Sans
        // ça, ClientPlayNetworking.send refuse de transmettre par sécurité.
        // fabric-networking-api-v1 6.x : playC2S() renommé serverboundPlay().
        PayloadTypeRegistry.serverboundPlay().register(AuthPayload.ID, AuthPayload.CODEC);

        ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> sendAttestation(handler, client)
        );
    }

    /** Lit le jeton de la cible rejointe et l'envoie au serveur. */
    private void sendAttestation(ClientPacketListener handler, Minecraft client) {
        ServerData data = handler.getServerData();
        if (data == null) data = client.getCurrentServer();

        String token = null;
        String source = "principal";
        if (isDevServer(data)) {
            token = devToken != null ? devToken.read() : null;
            if (token != null) {
                source = "dev";
            } else {
                LOGGER.info("serveur de dev rejoint sans jeton dev : envoi du jeton principal.");
            }
        }
        if (token == null && mainToken != null) token = mainToken.read();

        if (token == null) {
            LOGGER.warn(
                "play-token absent : pas d'attestation envoyée, le plugin Guardian va kick à T+8s."
            );
            return;
        }
        try {
            ClientPlayNetworking.send(new AuthPayload(token));
            LOGGER.info("attestation envoyée au serveur (jeton {}, {} octets)", source, token.length());
        } catch (Exception e) {
            LOGGER.error("envoi de l'attestation échoué", e);
        }
    }

    /**
     * Vrai si l'adresse rejointe est celle du serveur de dev transmise par le
     * launcher ({@code reborn.server.dev.host} / {@code .port}). Sans port
     * transmis, l'hôte seul fait foi.
     */
    private static boolean isDevServer(ServerData data) {
        String devHost = System.getProperty(DEV_HOST_PROPERTY);
        if (data == null || data.ip == null || devHost == null || devHost.isBlank()) return false;
        ServerAddress joined = ServerAddress.parseString(data.ip.trim());
        if (!normalizeHost(joined.getHost()).equals(normalizeHost(devHost))) return false;
        String devPort = System.getProperty(DEV_PORT_PROPERTY);
        if (devPort == null || devPort.isBlank()) return true;
        try {
            return joined.getPort() == Integer.parseInt(devPort.trim());
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** Hôte comparable : sans espaces, crochets IPv6 ni point final, en minuscules. */
    private static String normalizeHost(String host) {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    /**
     * Fichier de jeton écrit (et réécrit) par le launcher, avec le dernier
     * jeton lu. Le launcher le remplace par renommage : une lecture ne voit
     * jamais un jeton à moitié écrit.
     */
    private static final class TokenFile {
        private final Path path;
        private volatile String last;

        private TokenFile(Path path) {
            this.path = path;
        }

        /** Fichier pointé par la sysprop, ou null si elle est absente ou invalide. */
        static TokenFile fromProperty(String property) {
            String value = System.getProperty(property);
            if (value == null || value.isBlank()) return null;
            try {
                return new TokenFile(Path.of(value.trim()));
            } catch (InvalidPathException e) {
                LOGGER.error("sysprop {} invalide : {}", property, e.getMessage());
                return null;
            }
        }

        /**
         * Jeton actuel du fichier. Fichier illisible ou vide : le dernier
         * jeton lu (peut-être expiré, Guardian tranche), sinon null.
         */
        String read() {
            try {
                String content = Files.readString(path).trim();
                if (!content.isEmpty()) {
                    last = content;
                    LOGGER.info("play-token lu depuis {} ({} chars)", path, content.length());
                    return content;
                }
                LOGGER.warn("fichier play-token vide : {}", path);
            } catch (NoSuchFileException e) {
                LOGGER.warn("fichier play-token introuvable : {}", path);
            } catch (IOException e) {
                LOGGER.warn("lecture du play-token impossible ({}) : {}", path, e.toString());
            }
            if (last != null) LOGGER.info("dernier play-token lu réutilisé ({})", path);
            return last;
        }
    }
}
