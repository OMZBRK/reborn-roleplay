package fr.reborn.hud.animation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload C2S {@code reborn:naruto} : le client informe le plugin serveur
 * (ShinobiCore) que le joueur (dés)active sa course chakraïque (« Naruto run »),
 * via une touche dédiée. Le serveur applique alors les effets IG (vitesse,
 * particules, consommation de chakra…). 1 octet = état on/off.
 *
 * <p>Miroir de {@code TablistPayload} / {@code AuthPayload}. Inerte tant que le
 * plugin n'a pas enregistré le canal (l'envoi est gardé par
 * {@code ClientPlayNetworking.canSend} côté {@link NarutoRun}).
 */
public record NarutoRunPayload(boolean active) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "naruto");
    public static final CustomPacketPayload.Type<NarutoRunPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, NarutoRunPayload> CODEC = new StreamCodec<>() {
        @Override
        public NarutoRunPayload decode(FriendlyByteBuf buf) {
            return new NarutoRunPayload(buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, NarutoRunPayload value) {
            buf.writeBoolean(value.active);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
