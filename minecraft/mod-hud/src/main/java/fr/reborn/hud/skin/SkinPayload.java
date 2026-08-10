package fr.reborn.hud.skin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:skins} (S2C uniquement) : ShinobiCore diffuse l'apparence RP
 * active de chaque joueur en ligne à TOUS les clients, pour que chacun voie le skin
 * composé des autres.
 *
 * <p>Contenu = {@code <uuid>\n<queue SkinSpec.serialize()>}. Une apparence vide
 * ({@code <uuid>\n}) = retrait de l'override (skin Minecraft normal / déconnexion).
 * Codec = octets UTF-8 bruts, comme {@code CharacterPayload}.
 */
public record SkinPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "skins");
    public static final CustomPacketPayload.Type<SkinPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, SkinPayload> CODEC = new StreamCodec<>() {
        @Override
        public SkinPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new SkinPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, SkinPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
