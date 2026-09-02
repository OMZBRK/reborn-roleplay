package fr.reborn.hud.menu.shop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:shop} (boutique de tenues), bidirectionnel avec ShinobiCore.
 * <ul>
 *   <li><b>C2S</b> : {@code open} / {@code buy:<id>} / {@code equip:<id>\n<blob>}.</li>
 *   <li><b>S2C</b> : JSON d'état {@code {ryo,price,owned[],appearance,toast?}}.</li>
 * </ul>
 * Octets UTF-8 bruts (comme {@code CharacterPayload}).
 */
public record ShopPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "shop");
    public static final CustomPacketPayload.Type<ShopPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, ShopPayload> CODEC = new StreamCodec<>() {
        @Override
        public ShopPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new ShopPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, ShopPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
