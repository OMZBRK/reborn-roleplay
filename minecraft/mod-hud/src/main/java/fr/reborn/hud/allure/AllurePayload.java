package fr.reborn.hud.allure;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Canal {@code reborn:allure} (ShinobiAbilities {@code mobility/Allure}), octets bruts dans les deux sens :
 * <ul>
 *   <li>C2S, 2 octets : {@code 2} = je sprinte / {@code 0} = j'arrête, puis le numéro de la demande ;</li>
 *   <li>S2C {@code 0} réglages : actif (booléen), marche, trot, sprint (float) ;</li>
 *   <li>S2C {@code 1} état : allure accordée (0/1/2), endurance 0..1 (float), drapeaux (1 neutre, 2 essoufflé),
 *       numéro de la dernière demande reçue.</li>
 * </ul>
 * Lu par {@link Allure#recevoir}. Inerte tant que le plugin n'a pas enregistré le canal ({@code canSend}).
 */
public record AllurePayload(byte[] data) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "allure");
    public static final CustomPacketPayload.Type<AllurePayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, AllurePayload> CODEC = new StreamCodec<>() {
        @Override
        public AllurePayload decode(FriendlyByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new AllurePayload(b);
        }

        @Override
        public void encode(FriendlyByteBuf buf, AllurePayload value) {
            buf.writeBytes(value.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
