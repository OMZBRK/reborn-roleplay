package fr.reborn.hud.animation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload <b>bidirectionnel</b> {@code reborn:run} de la course chakraïque
 * (« Naruto run »). 1 octet = état on/off.
 *
 * <ul>
 *   <li><b>C2S</b> — le client <i>demande</i> l'état ({@code true} = démarrer,
 *       {@code false} = arrêter). Le plugin (ShinobiAbilities) garde tous ses
 *       garde-fous : chakra minimum, voie/slot débloqué, verrou post-interruption.</li>
 *   <li><b>S2C</b> — le plugin renvoie l'état <i>autoritaire</i>. C'est ce qui
 *       permet au client de sortir du mode quand le serveur coupe la course
 *       (coup reçu, chakra épuisé, KO, refus au démarrage) : sans ce retour, le
 *       client restait bloqué en « naruto run » côté animation/mouvement.</li>
 * </ul>
 *
 * <p>⚠️ L'identifiant est {@code reborn:run} — <b>pas</b> {@code reborn:naruto}.
 * C'est le canal que {@code RunChannelListener} écoute côté plugin ; l'ancien
 * nom ne correspondait à rien côté serveur, donc la touche ne faisait rien en
 * jeu. Le plugin accepte toujours {@code reborn:naruto} en alias pour les
 * clients déjà publiés.
 *
 * <p>Miroir de {@code TablistPayload} / {@code AuthPayload}. Inerte tant que le
 * plugin n'a pas enregistré le canal (l'envoi est gardé par
 * {@code ClientPlayNetworking.canSend} côté {@link NarutoRun}).
 */
public record NarutoRunPayload(boolean active) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "run");
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
