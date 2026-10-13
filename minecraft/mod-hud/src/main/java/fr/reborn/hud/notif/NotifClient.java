package fr.reborn.hud.notif;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canal générique {@code reborn:notif} (S2C, ShinobiCore {@code inventory.Notifs}) — JSON UTF-8 brut sans préfixe :
 * <ul>
 *   <li>{@code gain} / {@code perte} / {@code msg} : une ligne de la pile en bas à droite ({@link NotifHud}) ;</li>
 *   <li>{@code poids} : état de charge du personnage ({@code cur}, {@code max}, {@code palier} 0-3, {@code libres},
 *       {@code verrou}) — indicateur sous les vitals ({@link SurchargeHud}), sprint coupé, invite de ramassage ;</li>
 *   <li>{@code arme} : l'arme vient d'être dégainée / rengainée.</li>
 * </ul>
 * Recevoir un état {@code poids} prouve que le serveur gère le <b>ramassage volontaire</b> : la touche Ramasser n'est
 * interceptée qu'à partir de là (sinon F garde son rôle vanilla).
 */
public final class NotifClient {

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "notif"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = new StreamCodec<>() {
            @Override public Payload decode(FriendlyByteBuf buf) {
                byte[] b = new byte[buf.readableBytes()];
                buf.readBytes(b);
                return new Payload(new String(b, StandardCharsets.UTF_8));
            }
            @Override public void encode(FriendlyByteBuf buf, Payload p) { buf.writeBytes(p.json.getBytes(StandardCharsets.UTF_8)); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** État de charge courant (null tant que le serveur n'en a pas envoyé). */
    public record Poids(double cur, double max, int palier, int libres, String verrou, long at) {
        public double ratio() { return max > 0 ? cur / max : 0; }
    }

    public static final int NORMAL = 0, CHARGE = 1, SURCHARGE = 2, IMMOBILE = 3;

    private static volatile Poids poids;
    /** Banc d'essai : force le ramassage volontaire hors serveur. */
    public static boolean debugActif;

    private NotifClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID,
            (payload, context) -> context.client().execute(() -> recevoir(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void clear() {
        poids = null;
        NotifHud.clear();
    }

    /** Point d'entrée commun (réseau et bancs d'essai). */
    public static void recevoir(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        String t = str(o, "t", "");
        switch (t) {
            case "poids" -> poids = new Poids(dbl(o, "cur", 0), dbl(o, "max", 12), (int) dbl(o, "palier", 0),
                (int) dbl(o, "libres", 9), str(o, "verrou", null), System.currentTimeMillis());
            case "gain", "perte" -> NotifHud.objet("gain".equals(t), (int) dbl(o, "q", 1), str(o, "nom", "Objet"),
                str(o, "rar", "COMMUN"), str(o, "mat", null), str(o, "model", null), str(o, "suite", null));
            case "msg" -> NotifHud.message(str(o, "ton", "info"), str(o, "kj", null), str(o, "gras", null),
                str(o, "texte", null));
            case "arme" -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(o.has("degainee") && o.get("degainee").getAsBoolean()
                        ? net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value()
                        : net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5f, 1.2f);
                }
            }
            default -> { }
        }
    }

    public static Poids poids() {
        return poids;
    }

    /** Le serveur gère-t-il le ramassage volontaire (état de charge reçu) ? */
    public static boolean ramassageServeur() {
        return debugActif || poids != null;
    }

    public static int palier() {
        Poids p = poids;
        return p == null ? NORMAL : p.palier();
    }

    /** Plus de sprint dès « Chargé ». */
    public static boolean sprintBloque() {
        return palier() >= CHARGE;
    }

    static String str(JsonObject o, String k, String def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    static double dbl(JsonObject o, String k, double def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
