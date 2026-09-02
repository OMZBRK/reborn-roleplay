package fr.reborn.hud.emote;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:emote} (serveur→client) : ShinobiCore ordonne à chaque client
 * proche de jouer (ou d'arrêter) une emote EmoteCraft sur l'avatar d'un joueur donné,
 * exactement comme {@code reborn:combat}/{@code TYPE_ANIM} le fait pour les coups.
 *
 * <p>Format (miroir de {@code EmoteChannel} côté plugin) : {@code int entityId} puis
 * les octets UTF-8 du nom d'emote. Un nom <b>vide</b> = arrêter l'emote en cours sur
 * cet avatar.
 *
 * <p>Le rendu passe par l'API cliente EmoteCraft ({@link EmoteAnimations}) — le serveur
 * n'envoie que le nom résolu (l'animation elle-même est celle qu'EmoteCraft a chargée
 * côté client). Purement visuel : le serveur reste autoritaire sur le reste.
 */
public record EmotePayload(int entityId, String key) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "emote");
    public static final CustomPacketPayload.Type<EmotePayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, EmotePayload> CODEC = new StreamCodec<>() {
        @Override
        public EmotePayload decode(FriendlyByteBuf buf) {
            int entityId = buf.readInt();
            byte[] rest = new byte[buf.readableBytes()];
            buf.readBytes(rest);
            return new EmotePayload(entityId, new String(rest, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, EmotePayload value) {
            buf.writeInt(value.entityId);
            buf.writeBytes(value.key.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
