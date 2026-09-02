package fr.reborn.hud.emote;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:emotepack} (bidirectionnel) — distribution des emotes déposées
 * par les devs ({@code plugins/ShinobiCore/emotes/}) vers TOUS les clients.
 *
 * <p><b>Découpé en morceaux</b> : un fichier {@code .emotecraft} fait plusieurs dizaines
 * de Ko, au-dessus de la limite des plugin-messages Bukkit → on l'envoie en chunks
 * ({@code idx}/{@code total}) réassemblés côté client. Sans ça, le gros message est
 * silencieusement rejeté (symptôme : emote absente côté client alors que
 * {@code reborn:emote}, petit, marche).
 *
 * <ul>
 *   <li><b>C2S (requête)</b> : nom vide, {@code total=0} → « pousse-moi le pack ».</li>
 *   <li><b>S2C (chunk)</b> : {@code name} + {@code idx} + {@code total} + octets du
 *       morceau. Le client accumule jusqu'à {@code total} puis décode + enregistre.</li>
 * </ul>
 *
 * <p>Format : {@code int nameLen}, {@code name} (UTF-8), {@code int idx}, {@code int
 * total}, puis les octets du chunk (reste du buffer).
 */
public record EmotePackPayload(String name, int idx, int total, byte[] data)
        implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "emotepack");
    public static final CustomPacketPayload.Type<EmotePackPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    /** Requête client → serveur (« pousse-moi le pack »). */
    public static EmotePackPayload request() {
        return new EmotePackPayload("", 0, 0, new byte[0]);
    }

    public static final StreamCodec<FriendlyByteBuf, EmotePackPayload> CODEC = new StreamCodec<>() {
        @Override
        public EmotePackPayload decode(FriendlyByteBuf buf) {
            int nameLen = buf.readInt();
            byte[] nameBytes = new byte[nameLen];
            buf.readBytes(nameBytes);
            int idx = buf.readInt();
            int total = buf.readInt();
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new EmotePackPayload(new String(nameBytes, StandardCharsets.UTF_8), idx, total, data);
        }

        @Override
        public void encode(FriendlyByteBuf buf, EmotePackPayload value) {
            byte[] nameBytes = value.name.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(nameBytes.length);
            buf.writeBytes(nameBytes);
            buf.writeInt(value.idx);
            buf.writeInt(value.total);
            buf.writeBytes(value.data);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
