package fr.reborn.hud.combat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload C2S {@code reborn:combatin} : le client informe {@code ShinobiCombat}
 * d'un input de combat. Pour l'instant, seul le BLOCAGE (M2) est envoyé —
 * {@code kind = 2} (garde ON) / {@code 3} (garde OFF). 1 octet.
 *
 * <p>Le M1 (combo) ne passe PAS par ici : c'est de la mêlée vanilla, gérée par
 * {@code EntityDamageByEntityEvent} côté serveur. Inerte tant que le plugin n'a pas
 * enregistré le canal (envoi gardé par {@code ClientPlayNetworking.canSend}).
 */
public record CombatInputPayload(byte kind) implements CustomPacketPayload {

    public static final byte KIND_BLOCK_ON = 2;
    public static final byte KIND_BLOCK_OFF = 3;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "combatin");
    public static final CustomPacketPayload.Type<CombatInputPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, CombatInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public CombatInputPayload decode(FriendlyByteBuf buf) {
            return new CombatInputPayload(buf.readByte());
        }

        @Override
        public void encode(FriendlyByteBuf buf, CombatInputPayload value) {
            buf.writeByte(value.kind);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
