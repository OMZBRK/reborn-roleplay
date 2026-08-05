package fr.reborn.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerInfo;
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

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/ui");

    /**
     * Default serveur Reborn (mode dev). En prod le launcher passe
     * {@code -Dreborn.server.host} / {@code .port} en sysprop, et
     * le mod les lit ici.
     */
    private static final String DEFAULT_HOST = "play.reborn-rp.com";
    private static final int DEFAULT_PORT = 27106;

    /** Cible de connexion : serveur principal (BUILD) ou serveur de DEV. */
    public enum ServerTarget { BUILD, DEV }

    /** Cible sélectionnée (session). Par défaut BUILD. Basculée par le toggle
     *  du menu, visible uniquement pour les staffs. */
    private static ServerTarget target = ServerTarget.BUILD;

    private RebornBranding() {}

    /** Vrai si le launcher a marqué ce compte comme staff (grade HELPER+). */
    public static boolean isStaff() {
        return Boolean.getBoolean("reborn.staff");
    }

    /** Vrai si un serveur de dev est configuré (sysprop du launcher). */
    public static boolean hasDevServer() {
        String h = System.getProperty("reborn.server.dev.host");
        return h != null && !h.isBlank();
    }

    /** Le toggle Build/Dev n'apparaît que pour un staff avec un serveur dev. */
    public static boolean serverToggleAvailable() {
        return isStaff() && hasDevServer();
    }

    public static ServerTarget target() { return target; }

    public static void setTarget(ServerTarget t) {
        if (t == ServerTarget.DEV && !hasDevServer()) return; // garde-fou
        target = t;
    }

    public static void toggleTarget() {
        setTarget(target == ServerTarget.BUILD ? ServerTarget.DEV : ServerTarget.BUILD);
    }

    private static String buildHost() { return System.getProperty("reborn.server.host", DEFAULT_HOST); }
    private static int buildPort() { return parsePort(System.getProperty("reborn.server.port")); }
    private static String devHost() { return System.getProperty("reborn.server.dev.host", DEFAULT_HOST); }
    private static int devPort() { return parsePort(System.getProperty("reborn.server.dev.port")); }

    /**
     * URL du site Reborn pour le bouton "Site web" du title screen.
     */
    public static final String SITE_URL = "https://reborn-rp.com";
    /**
     * Invite Discord publique.
     */
    public static final String DISCORD_URL = "https://discord.gg/reborn";

    /** Bouton "JOUER" → ConnectScreen direct vers la cible sélectionnée
     *  (BUILD par défaut ; DEV seulement si staff + serveur dev configuré). */
    public static void connectToReborn(Minecraft client, Screen parent) {
        boolean dev = target == ServerTarget.DEV && hasDevServer();
        String host = dev ? devHost() : buildHost();
        int port = dev ? devPort() : buildPort();
        String label = dev ? "Reborn Roleplay (DEV)" : "Reborn Roleplay";
        ServerInfo info = new ServerInfo(
            label,
            host + (port == 25565 ? "" : ":" + port),
            ServerInfo.ServerType.OTHER
        );
        ServerAddress address = new ServerAddress(host, port);
        LOGGER.info("connexion directe a {}:{} (cible={})", host, port, target);
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
