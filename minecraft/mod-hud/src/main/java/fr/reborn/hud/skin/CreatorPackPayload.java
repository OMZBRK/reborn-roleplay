package fr.reborn.hud.skin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:creatorpack} (bidirectionnel) — distribution des assets du
 * character creator (tenues/cheveux/yeux/accessoires…) déposés par les devs dans
 * {@code plugins/ShinobiCore/creator-assets/} vers TOUS les clients, SANS republier
 * le mod. Calqué sur {@code reborn:emotepack}.
 *
 * <p><b>Découpé en morceaux</b> : un PNG + son masque dépassent la limite d'un
 * plugin-message Bukkit → chunks {@code idx}/{@code total} réassemblés côté client.
 *
 * <ul>
 *   <li><b>C2S (requête)</b> : nom vide, {@code total=0} → « pousse-moi le pack ».</li>
 *   <li><b>S2C (chunk)</b> : {@code name} (clé « catégorie/id »), {@code idx},
 *       {@code total}, octets du morceau. Une fois réassemblé, le corps décrit
 *       l'asset : {@code UTF folder, UTF id, UTF metaJson, int pngLen, png,
 *       int maskLen, mask}.</li>
 * </ul>
 *
 * <p>Format d'enveloppe : {@code int nameLen}, {@code name} (UTF-8), {@code int idx},
 * {@code int total}, puis les octets du chunk (reste du buffer).
 */
public record CreatorPackPayload(String name, int idx, int total, byte[] data)
        implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "creatorpack");
    public static final CustomPacketPayload.Type<CreatorPackPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    /** Requête client → serveur (« pousse-moi le pack »). */
    public static CreatorPackPayload request() {
        return new CreatorPackPayload("", 0, 0, new byte[0]);
    }

    public static final StreamCodec<FriendlyByteBuf, CreatorPackPayload> CODEC = new StreamCodec<>() {
        @Override
        public CreatorPackPayload decode(FriendlyByteBuf buf) {
            int nameLen = buf.readInt();
            byte[] nameBytes = new byte[nameLen];
            buf.readBytes(nameBytes);
            int idx = buf.readInt();
            int total = buf.readInt();
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new CreatorPackPayload(new String(nameBytes, StandardCharsets.UTF_8), idx, total, data);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CreatorPackPayload value) {
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
