package fr.reborn.hud.eau;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:eau} (ShinobiAbilities {@code mobility/MarcheEau}), octets bruts, serveur → client :
 * <ul>
 *   <li>{@code 0} réglages : actif (booléen), chakra par seconde immobile, en marche, au trot, au sprint (float) ;</li>
 *   <li>{@code 1} permis : 0/1, puis la raison d'un refus (0 aucune, 1 pas appris, 2 chakra, 3 état, 4 hors jeu,
 *       5 surcharge, 6 désactivé).</li>
 * </ul>
 * Lu par {@link MarcheEauClient#recevoir}. Sans plugin (canal jamais reçu), la marche sur l'eau reste coupée.
 */
public record EauPayload(byte[] data) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "eau");
    public static final CustomPacketPayload.Type<EauPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, EauPayload> CODEC = new StreamCodec<>() {
        @Override
        public EauPayload decode(FriendlyByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new EauPayload(b);
        }

        @Override
        public void encode(FriendlyByteBuf buf, EauPayload value) {
            buf.writeBytes(value.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
