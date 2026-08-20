package fr.reborn.hud.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload C2S {@code reborn:combatin} : inputs de combat vers {@code ShinobiCombat}.
 * <ul>
 *   <li>{@link #KIND_BLOCK_ON} / {@link #KIND_BLOCK_OFF} : garde M2 (aucun float).</li>
 *   <li>{@link #KIND_DASH} : dash — {@code a,b} = direction MONDE horizontale (dx,dz).</li>
 *   <li>{@link #KIND_CHAKRA_JUMP} : saut de chakra — {@code a,b,c} = vélocité MONDE
 *       (vx,vy,vz) calculée client (charge + direction) ; le serveur l'applique.</li>
 * </ul>
 */
public record CombatInputPayload(byte kind, float a, float b, float c) implements CustomPacketPayload {

    public static final byte KIND_BLOCK_ON = 2;
    public static final byte KIND_BLOCK_OFF = 3;
    public static final byte KIND_DASH = 4;
    public static final byte KIND_CHAKRA_JUMP = 5;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "combatin");
    public static final CustomPacketPayload.Type<CombatInputPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static CombatInputPayload of(byte kind) { return new CombatInputPayload(kind, 0f, 0f, 0f); }
    public static CombatInputPayload dash(float dx, float dz) { return new CombatInputPayload(KIND_DASH, dx, dz, 0f); }
    public static CombatInputPayload chakraJump(float vx, float vy, float vz) {
        return new CombatInputPayload(KIND_CHAKRA_JUMP, vx, vy, vz);
    }

    public static final StreamCodec<FriendlyByteBuf, CombatInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public CombatInputPayload decode(FriendlyByteBuf buf) {
            byte k = buf.readByte();
            if (k == KIND_DASH) {
                return new CombatInputPayload(k, buf.readFloat(), buf.readFloat(), 0f);
            } else if (k == KIND_CHAKRA_JUMP) {
                return new CombatInputPayload(k, buf.readFloat(), buf.readFloat(), buf.readFloat());
            }
            return new CombatInputPayload(k, 0f, 0f, 0f);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CombatInputPayload v) {
            buf.writeByte(v.kind);
            if (v.kind == KIND_DASH) {
                buf.writeFloat(v.a); buf.writeFloat(v.b);
            } else if (v.kind == KIND_CHAKRA_JUMP) {
                buf.writeFloat(v.a); buf.writeFloat(v.b); buf.writeFloat(v.c);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
