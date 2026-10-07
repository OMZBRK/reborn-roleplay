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

/**
 * Client du Poste de garde (panel staff) : canal {@link StaffPayload}, touche F7, dernier état reçu.
 * Le serveur décide de tout (grade, actions possibles) ; le client affiche et envoie des demandes.
 */
public final class StaffClient {

    public record Toast(String text, long at) { }

    private static volatile JsonObject snapshot;
    private static volatile JsonObject profile;
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static KeyMapping key;

    private StaffClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(StaffPayload.ID, StaffPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StaffPayload.ID, StaffPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(StaffPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> receive(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { snapshot = null; profile = null; });
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.reborn-hud.staff_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, RebornKeyCategory.REBORN));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                if (client.gui.screen() == null) send("open");
            }
        });
    }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        switch (t) {
            case "open" -> {
                if (!(mc.gui.screen() instanceof StaffScreen)) mc.setScreenAndShow(new StaffScreen());
            }
            case "snap" -> snapshot = o;
            case "profile" -> profile = o;
            case "toast" -> toast(o.get("m").getAsString());
            default -> { }
        }
    }

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

    public static void clearProfile() { profile = null; }

    public static void send(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        send(o);
    }

    public static void send(JsonObject o) {
        if (ClientPlayNetworking.canSend(StaffPayload.ID)) ClientPlayNetworking.send(new StaffPayload(o.toString()));
    }
}
