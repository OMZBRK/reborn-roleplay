package fr.reborn.hud.faction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:faction} (les deux sens) : JSON UTF-8 brut, sans préfixe de longueur — contrat miroir de
 * ShinobiCore {@code FactionCanal} (PLUGINS_REFERENCE §4 septies). Le client envoie {@code {"a": …}}, le serveur répond
 * {@code {"t": …}}.
 */
public record FactionPayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "faction");
    public static final CustomPacketPayload.Type<FactionPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, FactionPayload> CODEC = new StreamCodec<>() {
        @Override
        public FactionPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new FactionPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, FactionPayload v) {
            buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
