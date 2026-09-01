package fr.reborn.hud.runtime;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload S2C {@code reborn:vitals} : vie/chakra RP du joueur local poussés en
 * DIRECT (~5×/s) par ShinobiCore, indépendamment du tablist (2 s). Quatre entiers
 * bruts {@code hp, maxHp, chakra, maxChakra} — alimente {@link VitalsFeed} pour un
 * HUD de vitals réellement live. Miroir du contrat serveur {@code VitalsPush}.
 */
public record VitalsPayload(int hp, int maxHp, int chakra, int maxChakra) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "vitals");
    public static final CustomPacketPayload.Type<VitalsPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, VitalsPayload> CODEC = new StreamCodec<>() {
        @Override
        public VitalsPayload decode(FriendlyByteBuf buf) {
            return new VitalsPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, VitalsPayload value) {
            buf.writeInt(value.hp);
            buf.writeInt(value.maxHp);
            buf.writeInt(value.chakra);
            buf.writeInt(value.maxChakra);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
