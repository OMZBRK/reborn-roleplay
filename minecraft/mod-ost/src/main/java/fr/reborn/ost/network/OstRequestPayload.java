package fr.reborn.ost.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Custom payload <b>client → serveur</b> {@code reborn:ost_request} — le joueur
 * demande au plugin de diffuser / stopper / mettre en pause un son de zone.
 * C'est le pendant C2S du canal {@link OstPayload} (S2C).
 *
 * <p>Format wire (compatible {@code PacketByteBuf} / {@code OstRequestReader}
 * côté plugin) :
 * <pre>
 *   REQUEST_PLAY  (0x01) : type:byte trackId:string radius:float volume:float
 *   REQUEST_STOP  (0x02) : type:byte
 *   REQUEST_PAUSE (0x03) : type:byte paused:boolean
 * </pre>
 *
 * <p>Anti-abus : le plugin applique un cooldown par joueur + un cap de rayon,
 * et n'autorise stop/pause qu'au <b>propriétaire</b> du broadcast en cours.
 */
public sealed interface OstRequestPayload extends CustomPayload
    permits OstRequestPayload.RequestPlay, OstRequestPayload.RequestStop,
            OstRequestPayload.RequestPause {

    Identifier IDENTIFIER = Identifier.of("reborn", "ost_request");
    CustomPayload.Id<OstRequestPayload> ID = new CustomPayload.Id<>(IDENTIFIER);

    byte TYPE_PLAY  = 0x01;
    byte TYPE_STOP  = 0x02;
    byte TYPE_PAUSE = 0x03;

    PacketCodec<PacketByteBuf, OstRequestPayload> CODEC = new PacketCodec<>() {
        @Override
        public OstRequestPayload decode(PacketByteBuf buf) {
            byte type = buf.readByte();
            return switch (type) {
                case TYPE_PLAY -> new RequestPlay(buf.readString(), buf.readFloat(), buf.readFloat());
                case TYPE_STOP -> new RequestStop();
                case TYPE_PAUSE -> new RequestPause(buf.readBoolean());
                default -> throw new IllegalArgumentException("type ost-request inconnu: " + type);
            };
        }

        @Override
        public void encode(PacketByteBuf buf, OstRequestPayload value) {
            switch (value) {
                case RequestPlay p -> {
                    buf.writeByte(TYPE_PLAY);
                    buf.writeString(p.trackId());
                    buf.writeFloat(p.radius());
                    buf.writeFloat(p.volume());
                }
                case RequestStop ignored -> buf.writeByte(TYPE_STOP);
                case RequestPause p -> {
                    buf.writeByte(TYPE_PAUSE);
                    buf.writeBoolean(p.paused());
                }
            }
        }
    };

    @Override
    default Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Demande de diffuser {@code trackId} autour du joueur (rayon {@code radius}). */
    record RequestPlay(String trackId, float radius, float volume) implements OstRequestPayload {}

    /** Demande de stopper le broadcast dont le joueur est propriétaire. */
    record RequestStop() implements OstRequestPayload {}

    /** Demande de pause/reprise du broadcast dont le joueur est propriétaire. */
    record RequestPause(boolean paused) implements OstRequestPayload {}
}
