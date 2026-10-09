package fr.reborn.hud.parchemin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client des parchemins : canal {@link ParcheminPayload}. Le serveur décide de tout (tirage, prise, réglages) ; le
 * client affiche la bibliothèque et ses réglages, et envoie les demandes.
 */
public final class ParcheminClient {

    private ParcheminClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ParcheminPayload.ID, ParcheminPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ParcheminPayload.ID, ParcheminPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ParcheminPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> receive(payload.json())));
    }

    /** Banc d'essai : message serveur simulé. */
    public static void debugReceive(String raw) { receive(raw); }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        switch (t) {
            case "lib" -> {
                if (mc.gui.screen() instanceof BibliothequeScreen b && o.get("id").getAsString().equals(b.libId())) b.update(o);
                else mc.setScreenAndShow(BibliothequeScreen.fromJson(o));
            }
            case "lib_cfg" -> {
                if (mc.gui.screen() instanceof BibliothequeStaffScreen s && o.get("id").getAsString().equals(s.libId())) s.update(o);
                else mc.setScreenAndShow(BibliothequeStaffScreen.fromJson(o));
            }
            case "read" -> {
                String id = o.getAsJsonObject("tech").get("id").getAsString();
                if (mc.gui.screen() instanceof LectureScreen l && id.equals(l.techId())) l.update(o);
                else mc.setScreenAndShow(LectureScreen.fromJson(o));
            }
            case "toast" -> {
                String msg = o.get("msg").getAsString();
                if (mc.gui.screen() instanceof BibliothequeScreen b) b.toast(msg);
                else if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Parchemins] §e" + msg));
            }
            default -> { }
        }
    }

    /** Demande au serveur sans bibliothèque : {@code {"a": action, …extra}}. */
    public static void sendAction(String action, JsonObject extra) {
        if (!ClientPlayNetworking.canSend(ParcheminPayload.ID)) return;
        JsonObject o = extra == null ? new JsonObject() : extra.deepCopy();
        o.addProperty("a", action);
        ClientPlayNetworking.send(new ParcheminPayload(o.toString()));
    }

    /** Demande au serveur : {@code {"a": action, "id": bibliothèque, …extra}}. */
    public static void send(String action, String libId, JsonObject extra) {
        if (libId == null || !ClientPlayNetworking.canSend(ParcheminPayload.ID)) return;
        JsonObject o = extra == null ? new JsonObject() : extra.deepCopy();
        o.addProperty("a", action);
        o.addProperty("id", libId);
        ClientPlayNetworking.send(new ParcheminPayload(o.toString()));
    }
}
