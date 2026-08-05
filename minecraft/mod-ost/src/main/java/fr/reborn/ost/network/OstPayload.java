package fr.reborn.ost.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom payload {@code reborn:ost} reçu depuis le plugin serveur.
 *
 * <p>Format wire (big-endian) :
 * <pre>
 *   PLAY_AT_POSITION (0x01) : type:byte x:double y:double z:double
 *                              radius:float trackId:string volume:float
 *                              secOffset:float
 *   STOP_BROADCAST   (0x02) : type:byte
 *   PLAY_GLOBAL      (0x03) : type:byte trackId:string volume:float
 * </pre>
 *
 * <p>{@code secOffset} (secondes depuis le début de la track) permet à un
 * joueur qui rejoint une zone déjà active d'attaquer la lecture au
 * timestamp courant via {@code AL_SEC_OFFSET}, et pas de réentendre depuis 0.
 *
 * <p>{@code string} = {@link FriendlyByteBuf#writeString} = VarInt(len) +
 * UTF-8 bytes. Le plugin produit le même encoding via
 * {@code OstWireFormat#writeVarIntString}.
 *
 * <p>Décodage défensif : si le buf est tronqué ou si le type byte est
 * inconnu, le codec lève une exception qui est attrapée dans
 * {@code OstNetworking}, loggée en WARNING, et n'impacte pas le client.
 */
public sealed interface OstPayload extends CustomPacketPayload
    permits OstPayload.PlayAtPosition, OstPayload.StopBroadcast, OstPayload.PlayGlobal,
            OstPayload.PauseBroadcast {

    Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "ost");
    CustomPacketPayload.Type<OstPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    /** Trace fixe pour les logs / la HUD. */
    String summary();

    /**
     * Codec en lecture pure (S2C) — le client ne renvoie jamais sur ce
     * canal dans cette version. La méthode {@code encode} est
     * symétrique pour permettre les tests unitaires (encode → decode)
     * sans avoir à dupliquer la logique dans le plugin.
     */
    StreamCodec<FriendlyByteBuf, OstPayload> CODEC = new StreamCodec<>() {
        @Override
        public OstPayload decode(FriendlyByteBuf buf) {
            byte typeCode = buf.readByte();
            OstPacketType type = OstPacketType.fromCode(typeCode);
            return switch (type) {
                case PLAY_AT_POSITION -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    float radius = buf.readFloat();
                    String trackId = buf.readUtf();
                    float volume = buf.readFloat();
                    float secOffset = buf.readFloat();
                    yield new PlayAtPosition(x, y, z, radius, trackId, volume, secOffset);
                }
                case STOP_BROADCAST -> new StopBroadcast();
                case PLAY_GLOBAL -> {
                    String trackId = buf.readUtf();
                    float volume = buf.readFloat();
                    yield new PlayGlobal(trackId, volume);
                }
                case PAUSE_BROADCAST -> new PauseBroadcast(buf.readBoolean());
            };
        }

        @Override
        public void encode(FriendlyByteBuf buf, OstPayload value) {
            switch (value) {
                case PlayAtPosition p -> {
                    buf.writeByte(OstPacketType.PLAY_AT_POSITION.code());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeFloat(p.radius());
                    buf.writeUtf(p.trackId());
                    buf.writeFloat(p.volume());
                    buf.writeFloat(p.secOffset());
                }
                case StopBroadcast ignored -> buf.writeByte(OstPacketType.STOP_BROADCAST.code());
                case PlayGlobal p -> {
                    buf.writeByte(OstPacketType.PLAY_GLOBAL.code());
                    buf.writeUtf(p.trackId());
                    buf.writeFloat(p.volume());
                }
                case PauseBroadcast p -> {
                    buf.writeByte(OstPacketType.PAUSE_BROADCAST.code());
                    buf.writeBoolean(p.paused());
                }
            }
        }
    };

    @Override
    default Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** Son positionnel — le mod calcule l'attenuation locale (radius).
     *  {@code secOffset} = secondes depuis le début de la track, pour
     *  permettre à un joueur qui rejoint une zone active d'attaquer au
     *  bon timestamp. 0 = lecture depuis le début. */
    record PlayAtPosition(double x, double y, double z, float radius,
                          String trackId, float volume, float secOffset) implements OstPayload {
        @Override
        public String summary() {
            return "PLAY_AT(" + trackId + " @" + x + "," + y + "," + z
                + " r=" + radius + " off=" + secOffset + "s)";
        }
    }

    /** Stoppe le son broadcast actuellement actif. */
    record StopBroadcast() implements OstPayload {
        @Override
        public String summary() { return "STOP_BROADCAST"; }
    }

    /** Son global (annonce, pas de distance). */
    record PlayGlobal(String trackId, float volume) implements OstPayload {
        @Override
        public String summary() {
            return "PLAY_GLOBAL(" + trackId + " vol=" + volume + ")";
        }
    }

    /** Met en pause ({@code true}) ou reprend ({@code false}) le broadcast en cours. */
    record PauseBroadcast(boolean paused) implements OstPayload {
        @Override
        public String summary() { return "PAUSE_BROADCAST(" + paused + ")"; }
    }
}
