package fr.reborn.hud.ko;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:volonte} (les deux sens) : JSON UTF-8 brut — contrat miroir de ShinobiCore
 * {@code VolonteQte}. Le client envoie {@code {"a": "start" | "result"}}, le serveur répond {@code {"t": …}}.
 */
public record VolontePayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "volonte");
    public static final CustomPacketPayload.Type<VolontePayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, VolontePayload> CODEC = new StreamCodec<>() {
        @Override
        public VolontePayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new VolontePayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, VolontePayload v) {
            buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
