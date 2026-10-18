package fr.reborn.hud.meditation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:meditation} (ShinobiCore {@code character/MeditationManager}), octets bruts, serveur → client :
 * {@code 0} liste des joueurs proches qui méditent (le destinataire compris) : nombre (int), puis pour chacun
 * l'identifiant réseau de l'entité (int) et la couleur du halo (int RGB). La liste remplace la précédente (vide = plus
 * personne). Lu par {@link MeditationClient#recevoir}.
 */
public record MeditationPayload(byte[] data) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "meditation");
    public static final CustomPacketPayload.Type<MeditationPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, MeditationPayload> CODEC = new StreamCodec<>() {
        @Override
        public MeditationPayload decode(FriendlyByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new MeditationPayload(b);
        }

        @Override
        public void encode(FriendlyByteBuf buf, MeditationPayload value) {
            buf.writeBytes(value.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
