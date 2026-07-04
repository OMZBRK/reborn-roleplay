package fr.reborn.hud.menu.tablist;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Payload S2C {@code reborn:tablist} : le JSON du tablist personnalisé poussé
 * par ShinobiCore ({@code TabListManager#pushClientFeed}). Bytes UTF-8 bruts
 * (le packet custom payload porte déjà la taille), pas de préfixe de longueur —
 * miroir de {@code AuthPayload} côté mod-integrity.
 */
public record TablistPayload(String json) implements CustomPayload {

    public static final Identifier IDENTIFIER = Identifier.of("reborn", "tablist");
    public static final CustomPayload.Id<TablistPayload> ID = new CustomPayload.Id<>(IDENTIFIER);

    public static final PacketCodec<PacketByteBuf, TablistPayload> CODEC = new PacketCodec<>() {
        @Override
        public TablistPayload decode(PacketByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new TablistPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(PacketByteBuf buf, TablistPayload value) {
            buf.writeBytes(value.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
