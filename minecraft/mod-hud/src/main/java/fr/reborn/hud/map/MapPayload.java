package fr.reborn.hud.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:map} (bidirectionnel), même forme que {@code StatsPayload} :
 * octets UTF-8 bruts, sans préfixe de longueur.
 *
 * <p>S2C (ShinobiCore {@code MapChannel}) = le JSON de la carte et de ses lieux.
 * C2S = {@code open} ou {@code tp:<placeId>} (staff, revérifié côté serveur).
 */
public record MapPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "map");
    public static final CustomPacketPayload.Type<MapPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, MapPayload> CODEC = new StreamCodec<>() {
        @Override
        public MapPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new MapPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, MapPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
