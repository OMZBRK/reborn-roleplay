package fr.reborn.ost.plugin.network;

/**
 * Encodeurs des trois types de packets {@code reborn:ost} dans le wire
 * format attendu par le mod client.
 *
 * <p>Le mod décode via {@code OstPayload.CODEC} (codec Fabric typé). Les
 * deux sides doivent produire / consommer exactement les mêmes bytes —
 * tout drift se traduit par un warning silencieux côté mod.
 *
 * <ul>
 *   <li>{@code 0x01} PLAY_AT_POSITION : x:double y:double z:double
 *       radius:float trackId:string volume:float secOffset:float</li>
 *   <li>{@code 0x02} STOP_BROADCAST   : aucun arg</li>
 *   <li>{@code 0x03} PLAY_GLOBAL      : trackId:string volume:float</li>
 * </ul>
 *
 * <p>{@code secOffset} (PLAY_AT_POSITION) : nombre de secondes depuis le
 * début de la track. Utilisé quand un joueur rejoint une zone broadcast
 * déjà active — il attaque la lecture au timestamp courant via
 * {@code AL_SEC_OFFSET} côté mod. 0 = lecture depuis le début.
 */
public final class OstPacket {

    public static final byte TYPE_PLAY_AT_POSITION = 0x01;
    public static final byte TYPE_STOP_BROADCAST   = 0x02;
    public static final byte TYPE_PLAY_GLOBAL      = 0x03;
    public static final byte TYPE_PAUSE_BROADCAST  = 0x04;

    private OstPacket() {}

    /** Met en pause ({@code true}) ou reprend ({@code false}) le son en cours. */
    public static byte[] pauseBroadcast(boolean paused) {
        return OstWireFormat.writer()
            .writeByte(TYPE_PAUSE_BROADCAST)
            .writeByte(paused ? 1 : 0)
            .toByteArray();
    }

    public static byte[] playAtPosition(double x, double y, double z, float radius,
                                        String trackId, float volume, float secOffset) {
        return OstWireFormat.writer()
            .writeByte(TYPE_PLAY_AT_POSITION)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeFloat(radius)
            .writeString(trackId)
            .writeFloat(volume)
            .writeFloat(secOffset)
            .toByteArray();
    }

    public static byte[] stopBroadcast() {
        return OstWireFormat.writer()
            .writeByte(TYPE_STOP_BROADCAST)
            .toByteArray();
    }

    public static byte[] playGlobal(String trackId, float volume) {
        return OstWireFormat.writer()
            .writeByte(TYPE_PLAY_GLOBAL)
            .writeString(trackId)
            .writeFloat(volume)
            .toByteArray();
    }
}
