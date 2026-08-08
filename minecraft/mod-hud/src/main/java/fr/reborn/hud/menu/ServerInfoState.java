package fr.reborn.hud.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Etat live du serveur Reborn — utilise par {@link ServerInfoWidget}.
 *
 * <p>Ping le serveur via le protocole Minecraft Server List Ping (SLP)
 * en socket TCP raw. Refresh toutes les 30s en background quand le widget
 * est affiche (poll declenche depuis le render).
 *
 * <p>Volatile fields pour acces thread-safe sans lock (le ping run dans
 * un thread daemon, le widget read depuis le render thread).
 */
public final class ServerInfoState {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/server-info");

    private static final String DEFAULT_HOST = "play.reborn-rp.com";
    private static final int DEFAULT_PORT = 27106;

    /** Serveur principal (BUILD) — host/port du launcher (sysprop) ou défaut. */
    public static final ServerInfoState INSTANCE =
        fromSysprops("reborn.server.host", "reborn.server.port");

    /** Serveur DEV — seulement si le launcher l'a configuré (staff). Sinon null. */
    private static final ServerInfoState DEV_INSTANCE =
        (System.getProperty("reborn.server.dev.host") != null
            && !System.getProperty("reborn.server.dev.host").isBlank())
            ? fromSysprops("reborn.server.dev.host", "reborn.server.dev.port")
            : null;

    /** Le pinger du serveur dev, ou null si non configuré. */
    public static ServerInfoState dev() { return DEV_INSTANCE; }

    /** Pinger de la cible passée (retombe sur BUILD si dev absent). */
    public static ServerInfoState forTarget(RebornBranding.ServerTarget t) {
        return (t == RebornBranding.ServerTarget.DEV && DEV_INSTANCE != null) ? DEV_INSTANCE : INSTANCE;
    }

    /** Protocol version du client courant (utilisé dans le handshake du ping).
     *  Lu depuis {@link net.minecraft.SharedConstants} pour suivre la version MC
     *  (767 = 1.21.1 était périmé après le port 26.1). */
    private static final int PROTOCOL_VERSION = net.minecraft.SharedConstants.getProtocolVersion();

    /** Interval de refresh en millis. */
    private static final long REFRESH_INTERVAL_MS = 30_000;

    private final String host;
    private final int port;

    private volatile boolean online = false;
    private volatile int players = 0;
    private volatile int maxPlayers = 0;
    private volatile String motd = "";
    private volatile long lastPingMs = 0;
    private final AtomicBoolean pingInFlight = new AtomicBoolean(false);

    private ServerInfoState(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Construit un pinger depuis des sysprops host/port (défauts Reborn). */
    private static ServerInfoState fromSysprops(String hostProp, String portProp) {
        String h = System.getProperty(hostProp, DEFAULT_HOST);
        if (h == null || h.isBlank()) h = DEFAULT_HOST;
        int p = DEFAULT_PORT;
        try {
            String raw = System.getProperty(portProp);
            if (raw != null && !raw.isBlank()) {
                int n = Integer.parseInt(raw.trim());
                if (n > 0 && n < 65536) p = n;
            }
        } catch (NumberFormatException ignored) { /* défaut */ }
        return new ServerInfoState(h, p);
    }

    public boolean isOnline() { return online; }
    public int getPlayers() { return players; }
    public int getMaxPlayers() { return maxPlayers; }
    public String getMotd() { return motd; }

    /**
     * A appeler depuis le render thread du widget. Declenche un ping
     * async si plus de REFRESH_INTERVAL_MS depuis le dernier.
     */
    public void maybeRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastPingMs < REFRESH_INTERVAL_MS) return;
        if (!pingInFlight.compareAndSet(false, true)) return;

        Thread t = new Thread(this::runPing, "reborn-server-ping");
        t.setDaemon(true);
        t.start();
    }

    private void runPing() {
        try {
            doPing();
        } catch (IOException | RuntimeException e) {
            online = false;
            LOG.debug("server ping echec : {}", e.getMessage());
        } finally {
            lastPingMs = System.currentTimeMillis();
            pingInFlight.set(false);
        }
    }

    private void doPing() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // ── Handshake packet ──
            // packet ID 0x00, protocol version (VarInt), server address
            // (String), port (UShort), next state (VarInt) = 1 (status)
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, PROTOCOL_VERSION);
            writeString(handshake, host);
            handshake.write((port >> 8) & 0xFF);
            handshake.write(port & 0xFF);
            writeVarInt(handshake, 1);

            writeVarInt(out, handshake.size());
            out.write(handshake.toByteArray());

            // ── Status request packet (empty, ID 0x00) ──
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            // ── Status response ──
            readVarInt(in); // packet length
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                throw new IOException("unexpected packet id " + packetId);
            }
            int jsonLen = readVarInt(in);
            byte[] jsonBytes = new byte[jsonLen];
            in.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("players")) {
                JsonObject p = root.getAsJsonObject("players");
                int onlineN = p.has("online") ? p.get("online").getAsInt() : 0;
                int maxN = p.has("max") ? p.get("max").getAsInt() : 0;
                this.players = onlineN;
                this.maxPlayers = maxN;
            }
            if (root.has("description")) {
                this.motd = extractMotd(root.get("description"));
            }
            this.online = true;
        }
    }

    private static String extractMotd(com.google.gson.JsonElement desc) {
        if (desc.isJsonPrimitive()) return desc.getAsString();
        if (desc.isJsonObject()) {
            JsonObject obj = desc.getAsJsonObject();
            if (obj.has("text")) return obj.get("text").getAsString();
        }
        return "";
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers protocole MC : VarInt + String
    // ──────────────────────────────────────────────────────────────────

    private static void writeVarInt(java.io.OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & 0xFFFFFF80) == 0) {
                out.write(value);
                return;
            }
            out.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, position = 0, currentByte;
        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new IOException("VarInt too big");
        }
        return value;
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, data.length);
        out.write(data);
    }
}
