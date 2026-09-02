package fr.reborn.hud.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload S2C {@code reborn:combat} — poussé par le plugin {@code ShinobiCombat}
 * (Bukkit Messenger, octets bruts). Un payload = un message :
 * <ul>
 *   <li>{@link #TYPE_HIT} : {@code int victimEntityId}, {@code float damage} →
 *       damage indicator flottant + cumul de combo.</li>
 *   <li>{@link #TYPE_STAMINA} : {@code float current}, {@code float max} →
 *       barre d'endurance + anneau de stamina.</li>
 *   <li>{@link #TYPE_ANIM} : {@code int attackerEntityId}, {@code byte animId} →
 *       joue l'anim de combat (coup/garde) sur l'avatar concerné (voir les autres).</li>
 * </ul>
 * Miroir du format écrit par {@code com.reborn.shinobicombat.net.CombatChannel}.
 */
public record CombatPayload(byte msgType, int victimEntityId, float damage,
                            float staminaCurrent, float staminaMax, int animId)
        implements CustomPacketPayload {

    public static final byte TYPE_HIT = 1;
    public static final byte TYPE_STAMINA = 2;
    public static final byte TYPE_ANIM = 3;
    /** S2C : reset des cooldowns client (commande staff dev). Corps = juste le type. */
    public static final byte TYPE_COOLDOWN_RESET = 4;
    /** S2C : parade timée (Sekiro) — {@code byte role} porté dans {@link #animId}
     *  (0 = a paré / 1 = s'est fait parer). Flash + son côté client. */
    public static final byte TYPE_PARRY = 5;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "combat");
    public static final CustomPacketPayload.Type<CombatPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, CombatPayload> CODEC = new StreamCodec<>() {
        @Override
        public CombatPayload decode(FriendlyByteBuf buf) {
            byte type = buf.readByte();
            if (type == TYPE_HIT) {
                int vid = buf.readInt();
                float dmg = buf.readFloat();
                return new CombatPayload(type, vid, dmg, 0f, 0f, 0);
            } else if (type == TYPE_STAMINA) {
                float cur = buf.readFloat();
                float max = buf.readFloat();
                return new CombatPayload(type, 0, 0f, cur, max, 0);
            } else if (type == TYPE_ANIM) {
                int aid = buf.readInt();
                int anim = buf.readByte();
                return new CombatPayload(type, aid, 0f, 0f, 0f, anim);
            } else if (type == TYPE_PARRY) {
                int role = buf.readByte();   // 0 = paré / 1 = fait parer → dans animId
                return new CombatPayload(type, 0, 0f, 0f, 0f, role);
            }
            return new CombatPayload(type, 0, 0f, 0f, 0f, 0);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CombatPayload v) {
            buf.writeByte(v.msgType);
            if (v.msgType == TYPE_HIT) {
                buf.writeInt(v.victimEntityId);
                buf.writeFloat(v.damage);
            } else if (v.msgType == TYPE_STAMINA) {
                buf.writeFloat(v.staminaCurrent);
                buf.writeFloat(v.staminaMax);
            } else if (v.msgType == TYPE_ANIM) {
                buf.writeInt(v.victimEntityId);
                buf.writeByte(v.animId);
            } else if (v.msgType == TYPE_PARRY) {
                buf.writeByte(v.animId);   // rôle
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
