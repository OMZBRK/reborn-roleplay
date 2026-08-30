package fr.reborn.hud.emote;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:emotepack} (bidirectionnel) — distribution des emotes déposées
 * par les devs sur le serveur ({@code plugins/ShinobiCore/emotes/}) vers TOUS les
 * clients, pour qu'elles soient visibles par tous sans mise à jour du mod.
 *
 * <ul>
 *   <li><b>C2S (requête)</b> : le client envoie un paquet à nom vide dès qu'il rejoint
 *       (canal enregistré) → demande au serveur de lui pousser le pack.</li>
 *   <li><b>S2C (donnée)</b> : le serveur envoie un paquet par emote : {@code name} +
 *       octets bruts du fichier {@code .emotecraft}. Le client les décode et les indexe
 *       ({@link EmoteAnimations#registerCustom}).</li>
 * </ul>
 *
 * <p>Format : {@code int nameLen}, {@code name} (UTF-8), puis les octets de données
 * (le reste du buffer). Nom vide + données vides = requête.
 */
public record EmotePackPayload(String name, byte[] data) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "emotepack");
    public static final CustomPacketPayload.Type<EmotePackPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    /** Requête client → serveur (« pousse-moi le pack »). */
    public static EmotePackPayload request() {
        return new EmotePackPayload("", new byte[0]);
    }

    public static final StreamCodec<FriendlyByteBuf, EmotePackPayload> CODEC = new StreamCodec<>() {
        @Override
        public EmotePackPayload decode(FriendlyByteBuf buf) {
            int nameLen = buf.readInt();
            byte[] nameBytes = new byte[nameLen];
            buf.readBytes(nameBytes);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new EmotePackPayload(new String(nameBytes, StandardCharsets.UTF_8), data);
        }

        @Override
        public void encode(FriendlyByteBuf buf, EmotePackPayload value) {
            byte[] nameBytes = value.name.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(nameBytes.length);
            buf.writeBytes(nameBytes);
            buf.writeBytes(value.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
