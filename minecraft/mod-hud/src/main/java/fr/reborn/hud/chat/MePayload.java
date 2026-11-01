package fr.reborn.hud.chat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@code reborn:me} (S → C, 31/10) : une action {@code /me} à afficher au-dessus de la tête de son auteur
 * ({@link fr.reborn.hud.voice.MeBulles}). Un message par spectateur (le nom est résolu pour chacun) :
 * <pre>{@code
 * {"v":1,"t":"me","id":4182,"u":"<uuid auteur>","nom":"Kazuki Uchiha","inconnu":false,"ton":"C0392B",
 *  "texte":"serre le poing et fixe l'horizon.","ms":6500,"r":16,"n":17}
 * }</pre>
 * {@code id} = identifiant réseau de l'auteur (repli sur l'UUID {@code u}), {@code ms} = durée d'affichage,
 * {@code r} = portée en blocs, {@code n} = numéro croissant par auteur ; {@code {"v":1,"t":"me_fin","id":…}} efface
 * ses bulles (déconnexion, mort RP).
 *
 * <p>Codec = octets UTF-8 bruts, sans préfixe de longueur (comme {@code TypingPayload}). Le type doit être enregistré
 * à l'initialisation du client : sinon le canal n'est pas annoncé et le serveur ne le voit pas dans
 * {@code getListeningPluginChannels()}.
 */
public record MePayload(String json) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath("reborn", "me");
    public static final CustomPacketPayload.Type<MePayload> ID = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<FriendlyByteBuf, MePayload> CODEC = new StreamCodec<>() {
        @Override
        public MePayload decode(FriendlyByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new MePayload(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void encode(FriendlyByteBuf buf, MePayload value) {
            buf.writeBytes(value.json.getBytes(StandardCharsets.UTF_8));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
