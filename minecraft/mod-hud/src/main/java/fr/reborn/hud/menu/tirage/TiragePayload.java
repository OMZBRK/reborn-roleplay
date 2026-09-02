package fr.reborn.hud.menu.tirage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:tirage} — ShinobiCore → client.
 *
 * <p><b>S2C</b> : ouvre le test de la feuille avec le résultat DÉJÀ tiré par le
 * serveur (autoritaire). Contenu = {@code "<NATURE>|<clan>"} (ex
 * {@code "KATON|Uchiha"}). Le client se contente d'animer + afficher.
 *
 * <p>Codec = octets UTF-8 bruts (le custom payload MC porte déjà la taille),
 * comme {@code CharacterPayload}/{@code TablistPayload}.
 */
public record TiragePayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "tirage");
    public static final CustomPacketPayload.Type<TiragePayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, TiragePayload> CODEC = new StreamCodec<>() {
        @Override
        public TiragePayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new TiragePayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, TiragePayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
