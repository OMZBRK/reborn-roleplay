package fr.reborn.hud.parchemin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:parchemin} (les deux sens) : JSON UTF-8 brut, sans préfixe de longueur — contrat miroir de
 * ShinobiAbilities {@code LibraryService}. Le client envoie {@code {"a": …}}, le serveur répond {@code {"t": …}}.
 */
public record ParcheminPayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "parchemin");
    public static final CustomPacketPayload.Type<ParcheminPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, ParcheminPayload> CODEC = new StreamCodec<>() {
        @Override
        public ParcheminPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new ParcheminPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, ParcheminPayload v) {
            buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
