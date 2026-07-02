package fr.reborn.ost.network;

/**
 * Discriminator du custom payload {@code reborn:ost}. Le premier
 * byte du wire format est l'ordinal défini ici.
 *
 * <ul>
 *   <li>{@link #PLAY_AT_POSITION} : son positionnel avec attenuation
 *       par distance.</li>
 *   <li>{@link #STOP_BROADCAST}   : stoppe le son broadcast en cours.</li>
 *   <li>{@link #PLAY_GLOBAL}      : son non-positionnel (annonce
 *       globale).</li>
 * </ul>
 */
public enum OstPacketType {
    PLAY_AT_POSITION((byte) 0x01),
    STOP_BROADCAST((byte) 0x02),
    PLAY_GLOBAL((byte) 0x03),
    /** Met en pause / reprend le son broadcast en cours (owner-only côté serveur). */
    PAUSE_BROADCAST((byte) 0x04);

    private final byte code;

    OstPacketType(byte code) {
        this.code = code;
    }

    public byte code() { return code; }

    public static OstPacketType fromCode(byte code) {
        for (OstPacketType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Unknown OST packet type code: 0x"
            + Integer.toHexString(code & 0xFF));
    }
}
