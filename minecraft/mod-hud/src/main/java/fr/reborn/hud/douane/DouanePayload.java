package fr.reborn.hud.douane;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:douane}, dans les deux sens : JSON UTF-8 brut, sans préfixe de longueur (la charge utile d'un
 * paquet personnalisé porte déjà sa taille). Protocole v1 : PLUGINS_REFERENCE §4 decies.4 à .6.
 */
public record DouanePayload(String json) implements CustomPacketPayload {

    public static final Type<DouanePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "douane"));

    /** Au-delà, le serveur ignore un message C → S (§4 decies.6). */
    public static final int TAILLE_MAX = 32 * 1024;

    public static final StreamCodec<FriendlyByteBuf, DouanePayload> CODEC = new StreamCodec<>() {
        @Override
        public DouanePayload decode(FriendlyByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new DouanePayload(new String(b, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, DouanePayload v) {
            buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
