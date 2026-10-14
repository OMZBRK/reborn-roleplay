package fr.reborn.hud.staff;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Charge utile JSON UTF-8 brute (sans préfixe de longueur) d'un canal serveur → client : {@code reborn:annonce},
 * {@code reborn:ecran}, {@code reborn:plaques} (protocole : PLUGINS_REFERENCE §4 bis.3 à 4 bis.5). Un seul type de
 * record, un {@link Type} par canal.
 */
public record CanalJson(Type<CanalJson> type, String json) implements CustomPacketPayload {

    public static final Type<CanalJson> ANNONCE = canal("annonce"), ECRAN = canal("ecran"), PLAQUES = canal("plaques");

    private static Type<CanalJson> canal(String nom) {
        return new Type<>(Identifier.fromNamespaceAndPath("reborn", nom));
    }

    public static StreamCodec<FriendlyByteBuf, CanalJson> codec(Type<CanalJson> type) {
        return new StreamCodec<>() {
            @Override
            public CanalJson decode(FriendlyByteBuf buf) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new CanalJson(type, new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void encode(FriendlyByteBuf buf, CanalJson v) {
                buf.writeBytes(v.json.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
