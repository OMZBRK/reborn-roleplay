package fr.reborn.integrity;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Custom payload {@code reborn:auth} envoye au plugin Guardian au JOIN.
 *
 * <p>Format wire : bytes UTF-8 bruts du play-token, sans prefixe de
 * longueur — le packet custom payload de MC porte deja la taille totale,
 * donc on lit tout ce qui reste dans le buffer cote plugin.
 *
 * <p>On evite {@link net.minecraft.network.codec.PacketCodecs#STRING}
 * (qui prefixe une VarInt) pour faciliter la lecture cote Paper plugin
 * qui consomme {@code PluginMessageListener.onPluginMessageReceived(...)}
 * avec un {@code byte[]} brut (pas de PacketByteBuf).
 */
public record AuthPayload(String token) implements CustomPayload {

    public static final Identifier IDENTIFIER = Identifier.of("reborn", "auth");
    public static final CustomPayload.Id<AuthPayload> ID = new CustomPayload.Id<>(IDENTIFIER);

    public static final PacketCodec<PacketByteBuf, AuthPayload> CODEC = new PacketCodec<>() {
        @Override
        public AuthPayload decode(PacketByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new AuthPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(PacketByteBuf buf, AuthPayload value) {
            buf.writeBytes(value.token.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
