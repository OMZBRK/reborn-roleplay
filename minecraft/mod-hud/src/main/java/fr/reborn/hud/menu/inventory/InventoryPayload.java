package fr.reborn.hud.menu.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:inventory} (bidirectionnel), même forme que
 * {@code TiragePayload}/{@code CharacterPayload} : octets UTF-8 bruts, la taille
 * est portée par le packet custom payload de MC (pas de préfixe de longueur).
 *
 * <p>S2C (ShinobiCore → client) = un JSON décrivant le sac (bag + slots +
 * cosmétiques équipés). C2S (client → serveur) = une action textuelle :
 * {@code open}, {@code equip:<id>:<SLOT>}, {@code unequip:<SLOT>},
 * {@code use:<id>}, {@code drop:<id>}.
 */
public record InventoryPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "inventory");
    public static final CustomPacketPayload.Type<InventoryPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, InventoryPayload> CODEC = new StreamCodec<>() {
        @Override
        public InventoryPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new InventoryPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, InventoryPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
