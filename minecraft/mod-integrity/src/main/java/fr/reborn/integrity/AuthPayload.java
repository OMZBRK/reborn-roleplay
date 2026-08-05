package fr.reborn.integrity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Custom payload {@code reborn:auth} envoye au plugin Guardian au JOIN.
 *
 * <p>Format wire : bytes UTF-8 bruts du play-token, sans prefixe de
 * longueur — le packet custom payload de MC porte deja la taille totale,
 * donc on lit tout ce qui reste dans le buffer cote plugin.
 *
 * <p>On evite le codec String standard (qui prefixe une VarInt) pour
 * faciliter la lecture cote Paper plugin qui consomme
 * {@code PluginMessageListener.onPluginMessageReceived(...)} avec un
 * {@code byte[]} brut (pas de FriendlyByteBuf).
 *
 * <p>Mappings Mojang (26.1+) : {@code FriendlyByteBuf}, {@code StreamCodec},
 * {@code CustomPacketPayload}, {@code Identifier} (ex-Yarn
 * PacketByteBuf / PacketCodec / CustomPayload / Identifier).
 */
public record AuthPayload(String token) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "auth");
    public static final CustomPacketPayload.Type<AuthPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, AuthPayload> CODEC = new StreamCodec<>() {
        @Override
        public AuthPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new AuthPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, AuthPayload value) {
            buf.writeBytes(value.token.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
