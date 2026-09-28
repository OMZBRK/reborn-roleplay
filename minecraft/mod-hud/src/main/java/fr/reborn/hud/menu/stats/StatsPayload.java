package fr.reborn.hud.menu.stats;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:stats} (bidirectionnel), même forme que
 * {@code InventoryPayload} : octets UTF-8 bruts, sans préfixe de longueur.
 *
 * <p>S2C (ShinobiCore {@code StatsChannel}) = le JSON de la fiche shinobi.
 * C2S = une action : {@code open}, {@code alloc:taijutsu=1,ninjutsu=2}, {@code respec}.
 */
public record StatsPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "stats");
    public static final CustomPacketPayload.Type<StatsPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, StatsPayload> CODEC = new StreamCodec<>() {
        @Override
        public StatsPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new StatsPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, StatsPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
