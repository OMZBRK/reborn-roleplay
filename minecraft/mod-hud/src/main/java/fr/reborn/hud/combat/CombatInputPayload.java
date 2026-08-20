package fr.reborn.hud.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload C2S {@code reborn:combatin} : inputs de combat vers {@code ShinobiCombat}.
 * <ul>
 *   <li>{@link #KIND_BLOCK_ON} / {@link #KIND_BLOCK_OFF} : garde M2 (dx/dz ignorés).</li>
 *   <li>{@link #KIND_DASH} : dash directionnel — {@code dx,dz} = direction horizontale
 *       MONDE normalisée (8 directions selon l'input WASD relatif à la vue).</li>
 * </ul>
 * Le M1 (combo) ne passe pas par ici (mêlée vanilla, serveur).
 */
public record CombatInputPayload(byte kind, float dx, float dz) implements CustomPacketPayload {

    public static final byte KIND_BLOCK_ON = 2;
    public static final byte KIND_BLOCK_OFF = 3;
    public static final byte KIND_DASH = 4;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "combatin");
    public static final CustomPacketPayload.Type<CombatInputPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    /** Raccourci pour les inputs sans direction (garde). */
    public static CombatInputPayload of(byte kind) { return new CombatInputPayload(kind, 0f, 0f); }

    public static final StreamCodec<FriendlyByteBuf, CombatInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public CombatInputPayload decode(FriendlyByteBuf buf) {
            byte k = buf.readByte();
            if (k == KIND_DASH) {
                float dx = buf.readFloat();
                float dz = buf.readFloat();
                return new CombatInputPayload(k, dx, dz);
            }
            return new CombatInputPayload(k, 0f, 0f);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CombatInputPayload v) {
            buf.writeByte(v.kind);
            if (v.kind == KIND_DASH) {
                buf.writeFloat(v.dx);
                buf.writeFloat(v.dz);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
