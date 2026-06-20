package fr.reborn.integrity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entree client du mod Reborn Integrity — attestation uniquement.
 *
 * <p>Boot :
 * <ol>
 *   <li>Lit la sysprop {@code reborn.playTokenPath} ecrite par le launcher
 *       (cf {@code apps/launcher/src-tauri/src/launcher/game.rs}).</li>
 *   <li>Charge le contenu en memoire (le fichier disparait ensuite avec
 *       le game dir, peu importe).</li>
 *   <li>Enregistre le payload {@code reborn:auth} sur le canal C2S.</li>
 *   <li>Souscrit a {@link ClientPlayConnectionEvents#JOIN} : envoie le
 *       payload des que le client est connecte au serveur.</li>
 * </ol>
 *
 * <p>Si la sysprop est absente ou la lecture echoue, on log et on
 * continue : le client est exploitable hors-Reborn (vanilla servers, LAN,
 * etc.) ; c'est juste qu'il ne s'attestera pas et se fera kick par notre
 * serveur si jamais il s'y connecte.
 */
public final class RebornIntegrityClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-integrity");
    private static final String TOKEN_PATH_PROPERTY = "reborn.playTokenPath";

    private volatile String playToken;

    @Override
    public void onInitializeClient() {
        loadPlayToken();
        // Enregistre le type de payload cote C2S (client → server). Sans
        // ca, ClientPlayNetworking.send refuse de transmettre par securite.
        PayloadTypeRegistry.playC2S().register(AuthPayload.ID, AuthPayload.CODEC);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (playToken == null || playToken.isBlank()) {
                LOGGER.warn(
                    "play-token absent : pas d'attestation envoyee, le plugin Guardian va kick a T+8s."
                );
                return;
            }
            try {
                ClientPlayNetworking.send(new AuthPayload(playToken));
                LOGGER.info("attestation envoyee au serveur ({} bytes)", playToken.length());
            } catch (Exception e) {
                LOGGER.error("envoi de l'attestation echoue", e);
            }
        });
    }

    private void loadPlayToken() {
        String pathStr = System.getProperty(TOKEN_PATH_PROPERTY);
        if (pathStr == null || pathStr.isBlank()) {
            LOGGER.info(
                "sysprop {} absente — mode standalone, aucun token a envoyer.",
                TOKEN_PATH_PROPERTY
            );
            return;
        }
        try {
            String content = Files.readString(Path.of(pathStr)).trim();
            if (content.isEmpty()) {
                LOGGER.warn("fichier play-token vide : {}", pathStr);
                return;
            }
            this.playToken = content;
            LOGGER.info("play-token charge depuis {} ({} chars)", pathStr, content.length());
        } catch (IOException e) {
            LOGGER.error("impossible de lire le play-token a {}", pathStr, e);
        }
    }
}
