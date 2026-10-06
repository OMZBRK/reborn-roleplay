package fr.reborn.hud.meteo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:meteo} (S2C, ShinobiCore → client) : météo vue par le joueur local.
 * Format (big-endian, {@code DataOutputStream} côté Paper) : {@code byte meteo} (0 clair, 1 pluie, 2 sable, 3 brume),
 * {@code float intensité} (0..1).
 */
public record MeteoPayload(byte meteo, float intensity) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "meteo");
    public static final CustomPacketPayload.Type<MeteoPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, MeteoPayload> CODEC = new StreamCodec<>() {
        @Override
        public MeteoPayload decode(FriendlyByteBuf buf) {
            return new MeteoPayload(buf.readByte(), buf.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buf, MeteoPayload v) {
            buf.writeByte(v.meteo);
            buf.writeFloat(v.intensity);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
