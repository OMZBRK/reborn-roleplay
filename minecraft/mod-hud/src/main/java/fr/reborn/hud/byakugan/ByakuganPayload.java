package fr.reborn.hud.byakugan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:byakugan} (S2C, ShinobiSense → client) : état du Byakugan du joueur local.
 * Format (big-endian, {@code DataOutputStream} côté Paper) : {@code boolean actif, float portée en blocs}.
 */
public record ByakuganPayload(boolean active, float range) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "byakugan");
    public static final CustomPacketPayload.Type<ByakuganPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);
    public static final StreamCodec<FriendlyByteBuf, ByakuganPayload> CODEC = new StreamCodec<>() {
        @Override
        public ByakuganPayload decode(FriendlyByteBuf buf) {
            return new ByakuganPayload(buf.readBoolean(), buf.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buf, ByakuganPayload v) {
            buf.writeBoolean(v.active);
            buf.writeFloat(v.range);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
