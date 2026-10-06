package fr.reborn.hud.ko;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:ko_event} (S2C) : un moment fort à mettre en scène — un seul octet :
 * 1 à terre, 2 KO, 3 réveil, 4 hôpital, 5 rétabli, 6 défaite (terrain d'entraînement).
 */
public record KoEventPayload(byte kind) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "ko_event");
    public static final CustomPacketPayload.Type<KoEventPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, KoEventPayload> CODEC = new StreamCodec<>() {
        @Override
        public KoEventPayload decode(FriendlyByteBuf buf) { return new KoEventPayload(buf.readByte()); }

        @Override
        public void encode(FriendlyByteBuf buf, KoEventPayload v) { buf.writeByte(v.kind); }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
