package fr.reborn.hud.chat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:typing} (bidirectionnel) pour l'indicateur de frappe.
 *
 * <ul>
 *   <li><b>C2S</b> : {@code "1"} quand le joueur ouvre le chat (il tape),
 *       {@code "0"} quand il le ferme.</li>
 *   <li><b>S2C</b> : JSON {@code {"typing":["uuid",...]}} des joueurs proches en
 *       train d'écrire → le client dessine 3 points au-dessus de leur tête.</li>
 * </ul>
 *
 * <p>Codec = octets UTF-8 bruts (comme {@code TablistPayload}).
 */
public record TypingPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "typing");
    public static final CustomPacketPayload.Type<TypingPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, TypingPayload> CODEC = new StreamCodec<>() {
        @Override
        public TypingPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new TypingPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, TypingPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
