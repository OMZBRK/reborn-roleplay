package fr.reborn.hud.menu.character;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:character} entre ShinobiCore et le mod (bidirectionnel).
 *
 * <ul>
 *   <li><b>S2C</b> : JSON de la liste de personnages du joueur
 *       ({@code {"slotLimit":N,"characters":[...],"candidature":{...}}}) → ouvre
 *       l'écran de sélection.</li>
 *   <li><b>C2S</b> : commande texte simple — {@code select:<uuid>} / {@code create}
 *       / {@code open} / {@code create\n<...>} (wizard).</li>
 * </ul>
 *
 * <p>Codec = octets UTF-8 bruts (le custom payload MC porte déjà la taille),
 * exactement comme {@code TablistPayload}.
 */
public record CharacterPayload(String content) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "character");
    public static final CustomPacketPayload.Type<CharacterPayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, CharacterPayload> CODEC = new StreamCodec<>() {
        @Override
        public CharacterPayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new CharacterPayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, CharacterPayload value) {
            buf.writeBytes(value.content.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
