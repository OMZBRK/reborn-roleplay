package fr.reborn.integrity.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Helpers cote UI Reborn — actions des boutons custom du TitleScreen.
 *
 * <p>Centralise la connexion au serveur (qui doit se faire via
 * ConnectScreen.startConnecting pour partager le code reseau de
 * vanilla, sinon il y a des edge cases avec resource packs serveur
 * et keep-alive timeout) et l'ouverture d'URLs externes (Discord,
 * site, etc.).
 */
public final class RebornBranding {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-integrity/ui");

    /**
     * Default serveur Reborn (mode dev). En prod le launcher passe
     * {@code -Dreborn.server.host} / {@code .port} en sysprop, et
     * le mod les lit ici.
     */
    private static final String DEFAULT_HOST = "play.reborn-rp.fr";
    private static final int DEFAULT_PORT = 25565;

    private RebornBranding() {}

    /**
     * URL du site Reborn pour le bouton "Site web" du title screen.
     */
    public static final String SITE_URL = "https://reborn-rp.fr";
    /**
     * Invite Discord publique.
     */
    public static final String DISCORD_URL = "https://discord.gg/reborn";

    /** Bouton "Connecter à Reborn" → ConnectScreen direct. */
    public static void connectToReborn(MinecraftClient client, Screen parent) {
        String host = System.getProperty("reborn.server.host", DEFAULT_HOST);
        int port = parsePort(System.getProperty("reborn.server.port"));
        ServerInfo info = new ServerInfo(
            "Reborn Roleplay",
            host + (port == 25565 ? "" : ":" + port),
            ServerInfo.ServerType.OTHER
        );
        ServerAddress address = new ServerAddress(host, port);
        LOGGER.info("connexion directe a {}:{}", host, port);
        ConnectScreen.connect(parent, client, address, info, false, null);
    }

    private static int parsePort(String s) {
        if (s == null || s.isBlank()) return DEFAULT_PORT;
        try {
            int n = Integer.parseInt(s.trim());
            return (n > 0 && n < 65536) ? n : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    /** Bouton "Site web" → ouvre le navigateur systeme via Util.getOperatingSystem(). */
    public static void openSite() {
        openUri(SITE_URL);
    }

    /** Bouton "Discord" → ouvre l'invite Discord. */
    public static void openDiscord() {
        openUri(DISCORD_URL);
    }

    private static void openUri(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Exception e) {
            LOGGER.warn("openUri {} echec", url, e);
        }
    }
}
