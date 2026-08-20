package fr.reborn.hud.animation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Payload de synchro des démarches entre joueurs — canal {@code reborn:anim}
 * (bidirectionnel avec le plugin {@code ShinobiAbilities}).
 *
 * <p><b>C2S</b> : le client envoie son état d'animation ({@code state} + style de
 * marche) quand il change, plus un heartbeat pendant qu'il n'est pas immobile.
 * <b>S2C</b> : {@code AnimRelayListener} rediffuse cet état (UUID réécrit avec
 * l'émetteur réel → anti-usurpation) aux joueurs proches (≤ 48 blocs), qui
 * appliquent alors l'animation sur l'avatar correspondant.
 *
 * <p><b>Format binaire</b> (miroir EXACT de {@code AnimRelayListener}, plugin-message
 * Bukkit brut, sans préfixe de longueur) : UUID (2 longs) + {@code state} (1 o) +
 * {@code walk} (1 o) = 18 octets. {@code writeUUID} écrit MSB puis LSB, comme les
 * deux {@code DataOutputStream#writeLong} du relais.
 *
 * <p>Codes {@code state} (contrat client↔client, cf. {@link MovementAnimations}) :
 * 0 = idle, 1 = marche, 2 = course, 3 = naruto-run, 4 = saut.
 */
public record AnimSyncPayload(UUID uuid, byte state, byte walk) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "anim");
    public static final CustomPacketPayload.Type<AnimSyncPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, AnimSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public AnimSyncPayload decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            byte state = buf.readByte();
            byte walk = buf.readByte();
            return new AnimSyncPayload(id, state, walk);
        }

        @Override
        public void encode(FriendlyByteBuf buf, AnimSyncPayload value) {
            buf.writeUUID(value.uuid);
            buf.writeByte(value.state);
            buf.writeByte(value.walk);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
