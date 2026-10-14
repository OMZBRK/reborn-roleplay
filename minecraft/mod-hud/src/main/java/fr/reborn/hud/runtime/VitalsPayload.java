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
public record VitalsPayload(int hp, int maxHp, int chakra, int maxChakra, int[] ext) implements CustomPacketPayload {

    /** Sans les champs de la nourriture (ancien serveur). */
    public VitalsPayload(int hp, int maxHp, int chakra, int maxChakra) { this(hp, maxHp, chakra, maxChakra, null); }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "vitals");
    public static final CustomPacketPayload.Type<VitalsPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, VitalsPayload> CODEC = new StreamCodec<>() {
        @Override
        public VitalsPayload decode(FriendlyByteBuf buf) {
            int hp = buf.readInt(), mhp = buf.readInt(), ck = buf.readInt(), mck = buf.readInt();
            // Nourriture (14/10) : satiété ‰, à venir ‰, pause de régénération (s), drapeaux — si le serveur les envoie.
            int[] ext = null;
            if (buf.readableBytes() >= 16) ext = new int[] {buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()};
            buf.skipBytes(buf.readableBytes());
            return new VitalsPayload(hp, mhp, ck, mck, ext);
        }

        @Override
        public void encode(FriendlyByteBuf buf, VitalsPayload value) {
            buf.writeInt(value.hp);
            buf.writeInt(value.maxHp);
            buf.writeInt(value.chakra);
            buf.writeInt(value.maxChakra);
            if (value.ext != null) for (int v : value.ext) buf.writeInt(v);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
