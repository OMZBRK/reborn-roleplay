package fr.reborn.hud.monnaie;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Canaux {@code reborn:monnaie} (bourse, « Combien ? », fouille d'un corps) et {@code reborn:banque} (guichet) —
 * protocole : docs/technique/PLUGINS_REFERENCE.md (Monnaie et banque). Octets UTF-8 bruts dans les deux sens.
 */
public final class MonnaieClient {

    public record Payload(Type<Payload> kind, String json) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return kind; }
    }

    public static final CustomPacketPayload.Type<Payload> MONNAIE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "monnaie"));
    public static final CustomPacketPayload.Type<Payload> BANQUE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "banque"));

    private static StreamCodec<FriendlyByteBuf, Payload> codec(CustomPacketPayload.Type<Payload> t) {
        return new StreamCodec<>() {
            @Override public Payload decode(FriendlyByteBuf buf) {
                byte[] b = new byte[buf.readableBytes()];
                buf.readBytes(b);
                return new Payload(t, new String(b, StandardCharsets.UTF_8));
            }
            @Override public void encode(FriendlyByteBuf buf, Payload p) { buf.writeBytes(p.json().getBytes(StandardCharsets.UTF_8)); }
        };
    }

    /** Montant de la bourse poussé par le serveur (−1 = inconnu). */
    private static long bourse = -1;
    /** Guichetier en service (menu R : « Ouvrir son compte »). */
    private static boolean guichetier;

    private MonnaieClient() {}

    public static void init() {
        for (CustomPacketPayload.Type<Payload> t : java.util.List.of(MONNAIE, BANQUE)) {
            PayloadTypeRegistry.clientboundPlay().register(t, codec(t));
            PayloadTypeRegistry.serverboundPlay().register(t, codec(t));
        }
        ClientPlayNetworking.registerGlobalReceiver(MONNAIE, (p, ctx) -> ctx.client().execute(() -> recevoir(p.json())));
        ClientPlayNetworking.registerGlobalReceiver(BANQUE, (p, ctx) -> ctx.client().execute(() -> recevoirBanque(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(MonnaieClient::clear));
    }

    public static void clear() {
        bourse = -1;
        guichetier = false;
        FouilleHud.fin();
        GuichetData.clear();
    }

    /* ================================================================ envoi */

    public static boolean serveur() { return ClientPlayNetworking.canSend(MONNAIE); }

    public static void envoyer(String msg) {
        if (debugEnvoi != null) { debugEnvoi.accept("monnaie:" + msg); return; }
        if (ClientPlayNetworking.canSend(MONNAIE)) ClientPlayNetworking.send(new Payload(MONNAIE, msg));
    }

    public static void envoyerBanque(String msg) {
        if (debugEnvoi != null) { debugEnvoi.accept("banque:" + msg); return; }
        if (ClientPlayNetworking.canSend(BANQUE)) ClientPlayNetworking.send(new Payload(BANQUE, msg));
    }

    /** Banc d'essai : intercepte les envois (pas de serveur). */
    public static java.util.function.Consumer<String> debugEnvoi;

    /* ================================================================ état */

    /** Ce que le joueur porte (bourse) : valeur du serveur, sinon lue dans la barre et le sac ; −1 inconnu. */
    public static long bourse() {
        if (bourse >= 0) return bourse;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        long t = -1;
        for (int i = 0; i < 9; i++) {
            long n = BourseClient.montant(mc.player.getInventory().getItem(i));
            if (n >= 0) t = Math.max(0, t) + n;
        }
        var snap = fr.reborn.hud.menu.inventory.InventoryData.get();
        if (fr.reborn.hud.menu.inventory.InventoryData.fromServer() && snap.bag() != null) {
            for (var it : snap.bag()) if (it != null && it.ryo >= 0) t = Math.max(0, t) + it.ryo;
        }
        return t;
    }

    public static boolean guichetier() { return guichetier; }

    /* ================================================================ réception */

    public static void recevoir(String json) {
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        Minecraft mc = Minecraft.getInstance();
        String t = str(o, "t");
        switch (t) {
            case "bourse" -> bourse = num(o, "n");
            case "combien" -> {
                Screen parent = mc.gui.screen();
                String mode = str(o, "mode");
                mc.setScreenAndShow(new CombienScreen(parent, mode, str(o, "titre"), str(o, "question"), num(o, "max"),
                    str(o, "action").isEmpty() ? "Valider" : str(o, "action"), null,
                    n -> envoyer(mode + ":" + n)));
            }
            case "fouille_prog" -> FouilleHud.debut(str(o, "nom"), (int) num(o, "s"));
            case "fouille_prog_fin" -> FouilleHud.fin();
            case "fouille" -> {
                FouilleHud.fin();
                FouilleVue v = FouilleVue.lire(o);
                if (mc.gui.screen() instanceof fr.reborn.hud.menu.inventory.InventoryScreen s && s.enFouille()) s.majFouille(v);
                else if (mc.gui.screen() instanceof CombienScreen c && c.parent() instanceof fr.reborn.hud.menu.inventory.InventoryScreen s
                    && s.enFouille()) s.majFouille(v);
                else mc.setScreenAndShow(new fr.reborn.hud.menu.inventory.InventoryScreen(v));
            }
            case "fouille_ferme" -> {
                Screen s = mc.gui.screen();
                if (s instanceof CombienScreen c) s = c.parent();
                if (s instanceof fr.reborn.hud.menu.inventory.InventoryScreen i && i.enFouille()) mc.setScreenAndShow(null);
            }
            default -> { }
        }
    }

    public static void recevoirBanque(String json) {
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        Minecraft mc = Minecraft.getInstance();
        switch (str(o, "t")) {
            case "guichet" -> {
                GuichetData.update(o);
                if (o.has("bourse")) bourse = num(o, "bourse");
                Screen s = mc.gui.screen();
                if (s instanceof GuichetScreen g) g.maj();
                else if (s instanceof CombienScreen c && c.parent() instanceof GuichetScreen g) g.maj();
                else if (o.has("ouvrir") && o.get("ouvrir").getAsBoolean()) mc.setScreenAndShow(new GuichetScreen());
            }
            case "ferme" -> {
                if (mc.gui.screen() instanceof GuichetScreen) mc.setScreenAndShow(null);
            }
            case "guichetier" -> guichetier = o.has("actif") && o.get("actif").getAsBoolean();
            case "sceau" -> fr.reborn.hud.notif.NotifHud.message("info", null, "Échange prêt à sceller",
                str(o, "a") + " ↔ " + str(o, "b") + " · /banque sceller", null);
            default -> { }
        }
    }

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    static long num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsLong() : 0; } catch (RuntimeException e) { return 0; }
    }
}
