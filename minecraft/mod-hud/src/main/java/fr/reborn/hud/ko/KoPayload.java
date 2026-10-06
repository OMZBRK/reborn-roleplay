package fr.reborn.hud.ko;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:ko} (S2C, ShinobiCore {@code KoHudSync} → client) : état du KO et de l'ATA du joueur local.
 * Format (big-endian, {@code DataOutputStream} côté Paper) : {@code byte phase} (0 aucune, 1 à terre, 2 inconscient),
 * {@code byte cause} (0 PV, 1 chakra), {@code short restant}, {@code short total} (s), {@code boolean hôpital},
 * {@code byte ata} (0, 1 allégée, 2 pleine), {@code float repos} (0..1), {@code short repos min},
 * {@code short repos requis min}, {@code boolean au repos}, {@code boolean peur}.
 */
public record KoPayload(byte phase, byte cause, short left, short total, boolean hospital,
                        byte ata, float rest, short restMin, short restRequired, boolean resting,
                        boolean fear) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "ko");
    public static final CustomPacketPayload.Type<KoPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, KoPayload> CODEC = new StreamCodec<>() {
        @Override
        public KoPayload decode(FriendlyByteBuf buf) {
            return new KoPayload(buf.readByte(), buf.readByte(), buf.readShort(), buf.readShort(), buf.readBoolean(),
                    buf.readByte(), buf.readFloat(), buf.readShort(), buf.readShort(), buf.readBoolean(),
                    buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, KoPayload v) {
            buf.writeByte(v.phase);
            buf.writeByte(v.cause);
            buf.writeShort(v.left);
            buf.writeShort(v.total);
            buf.writeBoolean(v.hospital);
            buf.writeByte(v.ata);
            buf.writeFloat(v.rest);
            buf.writeShort(v.restMin);
            buf.writeShort(v.restRequired);
            buf.writeBoolean(v.resting);
            buf.writeBoolean(v.fear);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
