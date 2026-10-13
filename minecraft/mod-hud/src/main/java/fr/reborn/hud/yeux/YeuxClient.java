package fr.reborn.hud.yeux;

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
 * Canal {@code reborn:yeux} (ShinobiCore, {@code medic.yeux}) : JSON UTF-8 brut, sans préfixe.
 * <ul>
 *   <li>{@code {"t":"ouvrir","mode":"prelevement"|"greffe","patient":"…","cote":"g"|"d","type":"sharingan","jeton":"…"}}
 *       → ouvre {@link ChirurgieScreen} ; le résultat repart par {@code /yeux resultat <mode> <ok|echec> <score> <jeton>},
 *       que le serveur valide (jeton, distance, droits) avant d'appliquer.</li>
 *   <li>{@code {"t":"cecite","g":bool,"d":bool,"b":bool}} → {@link CeciteHud} (orbite vide ou œil trop abîmé ;
 *       b = bandage).</li>
 * </ul>
 */
public final class YeuxClient {

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "yeux"));
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

    private YeuxClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (p, ctx) -> ctx.client().execute(() -> receive(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> CeciteHud.set(false, false, false)));
    }

    /** Banc d'essai : rejoue un message serveur. */
    public static void debugReceive(String raw) { receive(raw); }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String t = str(o, "t");
        switch (t) {
            case "ouvrir" -> {
                ChirurgieScreen.Mode mode = "greffe".equals(str(o, "mode")) ? ChirurgieScreen.Mode.GREFFE
                        : ChirurgieScreen.Mode.PRELEVEMENT;
                Minecraft.getInstance().setScreenAndShow(new ChirurgieScreen(mode, str(o, "patient"),
                        !"d".equals(str(o, "cote")), str(o, "type"), str(o, "jeton")));
            }
            case "cecite" -> CeciteHud.set(bool(o, "g"), bool(o, "d"), bool(o, "b"));
            default -> { }
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }
}
