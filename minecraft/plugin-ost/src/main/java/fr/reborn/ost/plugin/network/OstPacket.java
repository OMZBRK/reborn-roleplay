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
 *       radius:float trackId:string volume:float</li>
 *   <li>{@code 0x02} STOP_BROADCAST   : aucun arg</li>
 *   <li>{@code 0x03} PLAY_GLOBAL      : trackId:string volume:float</li>
 * </ul>
 */
public final class OstPacket {

    public static final byte TYPE_PLAY_AT_POSITION = 0x01;
    public static final byte TYPE_STOP_BROADCAST   = 0x02;
    public static final byte TYPE_PLAY_GLOBAL      = 0x03;

    private OstPacket() {}

    public static byte[] playAtPosition(double x, double y, double z, float radius,
                                        String trackId, float volume) {
        return OstWireFormat.writer()
            .writeByte(TYPE_PLAY_AT_POSITION)
            .writeDouble(x)
            .writeDouble(y)
            .writeDouble(z)
            .writeFloat(radius)
            .writeString(trackId)
            .writeFloat(volume)
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
