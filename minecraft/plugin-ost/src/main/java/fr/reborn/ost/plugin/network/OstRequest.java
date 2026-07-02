package fr.reborn.ost.plugin.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Requête <b>client → serveur</b> reçue sur le canal
 * {@code reborn:ost_request} (cf {@code OstChannel#REQUEST_NAME}). Miroir du
 * {@code OstRequestPayload} côté mod : le byte de type puis les args, encodés
 * au format {@code PacketByteBuf} (string = VarInt(len)+UTF-8, float
 * big-endian, boolean = 1 byte).
 */
public sealed interface OstRequest permits OstRequest.Play, OstRequest.Stop, OstRequest.Pause {

    byte TYPE_PLAY  = 0x01;
    byte TYPE_STOP  = 0x02;
    byte TYPE_PAUSE = 0x03;

    /** Le joueur demande de diffuser {@code trackId} autour de lui. */
    record Play(String trackId, float radius, float volume) implements OstRequest {}

    /** Le joueur demande de stopper le broadcast dont il est propriétaire. */
    record Stop() implements OstRequest {}

    /** Le joueur demande pause/reprise de son broadcast. */
    record Pause(boolean paused) implements OstRequest {}

    /** Décode un message plugin brut. Lève {@link IOException} si tronqué /
     *  type inconnu (le listener log et ignore). */
    static OstRequest parse(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte type = in.readByte();
        return switch (type) {
            case TYPE_PLAY -> new Play(readString(in), in.readFloat(), in.readFloat());
            case TYPE_STOP -> new Stop();
            case TYPE_PAUSE -> new Pause(in.readByte() != 0);
            default -> throw new IOException("type ost-request inconnu: 0x"
                + Integer.toHexString(type & 0xFF));
        };
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        if (len < 0 || len > 512) throw new IOException("longueur string invalide: " + len);
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, pos = 0, b;
        do {
            b = in.readByte();
            value |= (b & 0x7F) << pos;
            pos += 7;
            if (pos > 35) throw new IOException("VarInt trop long");
        } while ((b & 0x80) != 0);
        return value;
    }
}
