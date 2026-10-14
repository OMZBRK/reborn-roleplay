package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.keybind.RebornKeyCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Client du Poste de garde (panel staff) : canal {@link StaffPayload}, touche F7, dernier état reçu (instantané,
 * fiche, Infirmerie, réponse du journal universel, notes par N°). Les effets en jeu (annonces, écran noir, séisme,
 * plaques staff) sont dans {@link EffetsStaff} et {@link PlaquesStaff}. Le serveur décide de tout (grade, actions
 * possibles) ; le client affiche et envoie des demandes. Protocole : {@code docs/technique/PLUGINS_REFERENCE.md} §4 bis.
 */
public final class StaffClient {

    public record Toast(String text, long at) { }

    private static volatile JsonObject snapshot;
    private static volatile JsonObject profile;
    private static volatile JsonObject koSnapshot;
    private static volatile JsonObject journal;
    private static final Map<String, JsonObject> NOTES = new HashMap<>();
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static KeyMapping key;

    /** Banc d'essai : les demandes partent vers {@link StaffDemo} (serveur simulé conforme au protocole). */
    static boolean demo;

    private StaffClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(StaffPayload.ID, StaffPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StaffPayload.ID, StaffPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(StaffPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> receive(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            snapshot = null; profile = null; journal = null;
            synchronized (NOTES) { NOTES.clear(); }
        });
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.reborn-hud.staff_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, RebornKeyCategory.REBORN));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                if (client.gui.screen() == null) send("open");
            }
        });
        EffetsStaff.init();
        PlaquesStaff.init();
    }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        switch (t) {
            case "open" -> {
                if (mc.gui.screen() instanceof GardeScreen g) { if (g.onKo()) g.show("alerts"); }
                else mc.setScreenAndShow(new GardeScreen("alerts"));
            }
            case "snap" -> snapshot = o;
            case "open_ko" -> {
                if (mc.gui.screen() instanceof GardeScreen g) g.show("ko");
                else mc.setScreenAndShow(new GardeScreen("ko"));
            }
            case "ko_snap" -> koSnapshot = o;
            case "close" -> {
                if (mc.gui.screen() instanceof StaffScreen) mc.setScreenAndShow(null);
            }
            case "profile" -> profile = o;
            case "journal" -> journal = o;
            case "notes" -> {
                String pid = o.has("pid") ? o.get("pid").getAsString() : "";
                synchronized (NOTES) { NOTES.put(normPid(pid), o); }
            }
            case "toast" -> toast(o.get("m").getAsString());
            default -> { }
        }
    }

    /** Banc d'essai : injecte un message comme s'il venait du serveur. */
    static void debugReceive(String raw) { receive(raw); }

    public static void toast(String text) {
        synchronized (TOASTS) {
            TOASTS.addLast(new Toast(text, System.currentTimeMillis()));
            while (TOASTS.size() > 3) TOASTS.pollFirst();
        }
    }

    public static Toast[] toasts() {
        synchronized (TOASTS) {
            long now = System.currentTimeMillis();
            TOASTS.removeIf(t -> now - t.at() > 3500);
            return TOASTS.toArray(new Toast[0]);
        }
    }

    public static JsonObject snapshot() { return snapshot; }

    public static JsonObject profile() { return profile; }

    public static JsonObject koSnapshot() { return koSnapshot; }

    /** Dernière réponse {@code journal} reçue (null si le serveur ne la gère pas encore). */
    public static JsonObject journal() { return journal; }

    /** Notes staff reçues pour ce N° (null tant que la réponse {@code notes} n'est pas arrivée). */
    public static JsonObject notes(String pid) {
        synchronized (NOTES) { return NOTES.get(normPid(pid)); }
    }

    public static void clearProfile() { profile = null; }

    /** « #00042 », « 42 », « 00042 » → « 42 » (clé des notes). */
    static String normPid(String pid) {
        if (pid == null) return "";
        String s = pid.trim();
        if (s.startsWith("#")) s = s.substring(1);
        s = s.replaceFirst("^0+(?=.)", "");
        return s;
    }

    public static void send(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        send(o);
    }

    public static void send(JsonObject o) {
        if (demo) { StaffDemo.handle(o); return; }
        if (ClientPlayNetworking.canSend(StaffPayload.ID)) ClientPlayNetworking.send(new StaffPayload(o.toString()));
    }
}
