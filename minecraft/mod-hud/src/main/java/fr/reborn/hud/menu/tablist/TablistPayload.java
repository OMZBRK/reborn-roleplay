package fr.reborn.hud.menu.tablist;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Payload S2C {@code reborn:tablist} : le JSON du tablist personnalisé poussé
 * par ShinobiCore ({@code TabListManager#pushClientFeed}). Bytes UTF-8 bruts
 * (le packet custom payload porte déjà la taille), pas de préfixe de longueur —
 * miroir de {@code AuthPayload} côté mod-integrity.
 */
public record TablistPayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "tablist");
    public static final CustomPacketPayload.Type<TablistPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, TablistPayload> CODEC = new StreamCodec<>() {
        @Override
        public TablistPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new TablistPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, TablistPayload value) {
            buf.writeBytes(value.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Id<? extends CustomPacketPayload> getId() {
        return ID;
    }
}
