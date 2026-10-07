package fr.reborn.hud.staff;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:staff} (les deux sens) : JSON UTF-8 brut, sans préfixe de longueur — contrat miroir de
 * ShinobiCore {@code StaffPanel}. Le client envoie {@code {"a": …}}, le serveur répond {@code {"t": …}}.
 */
public record StaffPayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "staff");
    public static final CustomPacketPayload.Type<StaffPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, StaffPayload> CODEC = new StreamCodec<>() {
        @Override
        public StaffPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new StaffPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, StaffPayload v) {
            buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
