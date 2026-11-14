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
    /** Dash « Keriox » (pitch-aware) : {@code a,b,c} = vélocité MONDE. */
    public static final byte KIND_KERIOX_DASH = 6;
    /** Esquive latérale/arrière : {@code a,b,c} = vélocité MONDE. */
    public static final byte KIND_DODGE = 7;
    /** Saut chargé (lot 5) : début de charge, aucun corps. Le serveur note son heure. */
    public static final byte KIND_SAUT_DEBUT = 8;
    /** Saut chargé : relâche, {@code a} = lacet, {@code b} = tangage de la visée. Le serveur répond GO ou NON. */
    public static final byte KIND_SAUT_RELACHE = 9;
    /** Saut chargé : annulation (quitté le sol, garde, écran), aucun corps. */
    public static final byte KIND_SAUT_ANNULE = 10;
    /** Saut chargé : un {@code GO} est arrivé hors de sa fenêtre et n'a pas été joué, aucun corps. Le serveur rembourse
     *  le bond tant que le joueur n'a pas décollé. */
    public static final byte KIND_SAUT_GO_IGNORE = 11;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "combatin");
    public static final CustomPacketPayload.Type<CombatInputPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static CombatInputPayload of(byte kind) { return new CombatInputPayload(kind, 0f, 0f, 0f); }
    public static CombatInputPayload dash(float dx, float dz) { return new CombatInputPayload(KIND_DASH, dx, dz, 0f); }
    public static CombatInputPayload chakraJump(float vx, float vy, float vz) {
        return new CombatInputPayload(KIND_CHAKRA_JUMP, vx, vy, vz);
    }
    public static CombatInputPayload kerioxDash(float vx, float vy, float vz) {
        return new CombatInputPayload(KIND_KERIOX_DASH, vx, vy, vz);
    }
    public static CombatInputPayload sautRelache(float lacet, float tangage) {
        return new CombatInputPayload(KIND_SAUT_RELACHE, lacet, tangage, 0f);
    }
    public static CombatInputPayload dodge(float vx, float vy, float vz) {
        return new CombatInputPayload(KIND_DODGE, vx, vy, vz);
    }

    private static boolean isVec3Kind(byte k) {
        return k == KIND_CHAKRA_JUMP || k == KIND_KERIOX_DASH || k == KIND_DODGE;
    }

    public static final StreamCodec<FriendlyByteBuf, CombatInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public CombatInputPayload decode(FriendlyByteBuf buf) {
            byte k = buf.readByte();
            if (k == KIND_DASH || k == KIND_SAUT_RELACHE) {
                return new CombatInputPayload(k, buf.readFloat(), buf.readFloat(), 0f);
            } else if (isVec3Kind(k)) {
                return new CombatInputPayload(k, buf.readFloat(), buf.readFloat(), buf.readFloat());
            }
            return new CombatInputPayload(k, 0f, 0f, 0f);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CombatInputPayload v) {
            buf.writeByte(v.kind);
            if (v.kind == KIND_DASH || v.kind == KIND_SAUT_RELACHE) {
                buf.writeFloat(v.a); buf.writeFloat(v.b);
            } else if (isVec3Kind(v.kind)) {
                buf.writeFloat(v.a); buf.writeFloat(v.b); buf.writeFloat(v.c);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
