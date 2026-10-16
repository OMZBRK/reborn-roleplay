package fr.reborn.hud.communication;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:communication} (les deux sens) : JSON UTF-8 brut, sans préfixe de longueur — contrat miroir de
 * ShinobiCore {@code CommCanal} (PLUGINS_REFERENCE, Cloche et volières). Le client envoie {@code {"a": …}}, le serveur répond
 * {@code {"t": …}}.
 */
public record CommPayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "communication");
    public static final CustomPacketPayload.Type<CommPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, CommPayload> CODEC = new StreamCodec<>() {
        @Override
        public CommPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new CommPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, CommPayload v) {
            buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
