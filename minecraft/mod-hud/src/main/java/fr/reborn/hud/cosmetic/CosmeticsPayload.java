package fr.reborn.hud.cosmetic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:cosmetics} (S2C) — diffusion des cosmétiques ÉQUIPÉS de
 * chaque joueur à tous les clients, pour que chacun voie les modèles 3D portés
 * par les autres (le canal {@code reborn:inventory} ne pousse que le sac du
 * joueur local ; celui-ci ne renseigne donc que ses propres cosmétiques).
 *
 * <p>Même forme que {@code InventoryPayload}/{@code SkinPayload} : octets UTF-8
 * bruts (pas de préfixe de longueur — la taille est portée par le custom payload
 * de MC). Contenu = {@code <uuid>\n<json>} où {@code <json>} est un objet
 * {@code {"SLOT":{"mat":..,"model":..}, ...}} des cosmétiques équipés. Un corps
 * vide (juste {@code <uuid>\n}) = retrait de tous les cosmétiques de ce joueur.
 */
public record CosmeticsPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "cosmetics");
    public static final CustomPacketPayload.Type<CosmeticsPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, CosmeticsPayload> CODEC = new StreamCodec<>() {
        @Override
        public CosmeticsPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new CosmeticsPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, CosmeticsPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
