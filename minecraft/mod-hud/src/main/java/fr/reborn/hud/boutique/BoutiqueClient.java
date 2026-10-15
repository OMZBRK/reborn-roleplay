package fr.reborn.hud.boutique;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.SlotItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Canal {@code reborn:boutique} (boutiques-lieux du 15/10 : tailleur et bazar) — protocole : PLUGINS_REFERENCE §4
 * « Boutiques ». S2C : {@code ouvrir} (ouvre l'écran, avec le point de vue de l'estrade), {@code etat} (rafraîchit),
 * {@code ferme}. C2S : {@code fermer}, {@code etat}, {@code acheter:<id>}, {@code acheter_porter:<id>\n<apparence>},
 * {@code porter:<id>\n<apparence>}, {@code ranger\n<apparence>}. Le serveur fait foi ; l'écran affiche et demande.
 */
public final class BoutiqueClient {

    public record Payload(String json) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final CustomPacketPayload.Type<Payload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "boutique"));

    private static final StreamCodec<FriendlyByteBuf, Payload> CODEC = new StreamCodec<>() {
        @Override public Payload decode(FriendlyByteBuf buf) {
            byte[] b = new byte[buf.readableBytes()];
            buf.readBytes(b);
            return new Payload(new String(b, StandardCharsets.UTF_8));
        }
        @Override public void encode(FriendlyByteBuf buf, Payload p) { buf.writeBytes(p.json().getBytes(StandardCharsets.UTF_8)); }
    };

    /** Un article proposé : {@code genre} tenue, objet, vivre ou carte ; {@code verrou} = condition non remplie. */
    public record Article(String id, String nom, String cat, String genre, long prix, boolean possede, String verrou,
                          String raison, SlotItem item, String slot) {
        public boolean tenue() { return "tenue".equals(genre); }
        public boolean objet() { return "objet".equals(genre); }
    }

    public record Categorie(String id, String nom) {}

    /** Point de vue de l'estrade (pieds du client, direction du regard, distance et hauteur de la caméra). */
    public record Cam(double x, double y, double z, float yaw, double dist, double haut) {}

    // ── dernier état reçu ──
    public static String id = "", nom = "", genre = "tailleur", sous = "", comptoir = "", appearance = "";
    public static long bourse = 0;
    public static List<Categorie> categories = List.of();
    public static List<Article> articles = List.of();
    public static Cam cam;
    public static String toast;
    public static long toastLe;
    /** Envois du mod interceptés par un banc (aucun serveur) ; null en jeu. */
    public static Consumer<String> debugEnvoi;

    private BoutiqueClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (p, ctx) -> ctx.client().execute(() -> recevoir(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(BoutiqueClient::clear));
    }

    public static void clear() {
        articles = List.of();
        categories = List.of();
        cam = null;
        toast = null;
    }

    public static void envoyer(String msg) {
        if (debugEnvoi != null) { debugEnvoi.accept(msg); return; }
        if (ClientPlayNetworking.canSend(TYPE)) ClientPlayNetworking.send(new Payload(msg));
    }

    /** Message du serveur (thread client). */
    public static void recevoir(String json) {
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String t = s(o, "t", "");
        Minecraft mc = Minecraft.getInstance();
        if (t.equals("ferme")) {
            if (mc.gui.screen() instanceof BoutiqueScreen bs) bs.fermerParServeur();
            return;
        }
        if (!t.equals("ouvrir") && !t.equals("etat")) return;
        JsonObject b = o.has("boutique") ? o.getAsJsonObject("boutique") : new JsonObject();
        id = s(b, "id", "");
        nom = s(b, "nom", "Boutique");
        genre = s(b, "genre", "tailleur");
        sous = s(b, "sous", "");
        comptoir = s(o, "comptoir", "");
        appearance = s(o, "appearance", "");
        bourse = o.has("bourse") ? o.get("bourse").getAsLong() : 0;
        List<Categorie> cs = new ArrayList<>();
        if (o.has("categories")) for (JsonElement e : o.getAsJsonArray("categories")) {
            JsonObject c = e.getAsJsonObject();
            cs.add(new Categorie(s(c, "id", ""), s(c, "nom", "")));
        }
        categories = List.copyOf(cs);
        List<Article> as = new ArrayList<>();
        JsonArray arr = o.has("articles") ? o.getAsJsonArray("articles") : new JsonArray();
        for (JsonElement e : arr) {
            JsonObject a = e.getAsJsonObject();
            SlotItem item = null;
            String slot = null;
            if (a.has("item") && a.get("item").isJsonObject()) {
                JsonObject io = a.getAsJsonObject("item");
                try { item = InventoryData.parseItem(io); } catch (RuntimeException ignored) { }
                slot = s(io, "slot", null);
            }
            as.add(new Article(s(a, "id", ""), s(a, "nom", ""), s(a, "cat", ""), s(a, "genre", "tenue"),
                a.has("prix") ? a.get("prix").getAsLong() : 0, a.has("possede") && a.get("possede").getAsBoolean(),
                s(a, "verrou", null), s(a, "raison", null), item, slot));
        }
        articles = List.copyOf(as);
        if (o.has("toast")) { toast = s(o, "toast", null); toastLe = System.currentTimeMillis(); }
        if (t.equals("ouvrir")) {
            cam = null;
            if (o.has("cam") && o.get("cam").isJsonObject()) {
                JsonObject c = o.getAsJsonObject("cam");
                cam = new Cam(c.get("x").getAsDouble(), c.get("y").getAsDouble(), c.get("z").getAsDouble(),
                    c.get("yaw").getAsFloat(), c.get("dist").getAsDouble(), c.get("haut").getAsDouble());
            }
            if (mc.gui.screen() instanceof BoutiqueScreen bs) bs.maj(true);
            else mc.setScreenAndShow(new BoutiqueScreen());
        } else if (mc.gui.screen() instanceof BoutiqueScreen bs) {
            bs.maj(false);
        }
    }

    private static String s(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }
}
