package fr.reborn.hud.ko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KO-7, côté client — canal {@code reborn:corps} (ShinobiCore {@code CorpsService} / {@code DeathScene}) :
 * <ul>
 *   <li>les corps proches (à terre, inconscient, mort) et le statut de RPK du joueur sur chacun : le menu
 *       d'interaction s'en sert pour proposer gorge, porter, RPK, achever, exécutions ;</li>
 *   <li>les corps laissés au sol (mannequins) et leur apparence Reborn, pour les habiller ;</li>
 *   <li>la scène de mort ({@link DeathScreen}).</li>
 * </ul>
 */
public final class CorpsClient {

    public record Body(int phase, int rpk) {}
    public record Exec(String id, String name) {}
    public record Skin(Identifier texture, boolean slim, String look) {}

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "corps"));
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

    private static final Map<UUID, Body> bodies = new HashMap<>();
    private static List<Exec> execs = List.of();
    /** mannequin (corps au sol) → skin Reborn composé. */
    private static final Map<UUID, Skin> corpses = new ConcurrentHashMap<>();

    private CorpsClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (p, ctx) -> ctx.client().execute(() -> receive(p.json())));
    }

    /** Banc d'essai : rejoue un message serveur. */
    public static void debugReceive(String raw) { receive(raw); }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        switch (t) {
            case "bodies" -> {
                bodies.clear();
                for (JsonElement e : o.getAsJsonArray("b")) {
                    JsonObject b = e.getAsJsonObject();
                    try { bodies.put(UUID.fromString(b.get("u").getAsString()), new Body(b.get("ph").getAsInt(), b.get("rpk").getAsInt())); }
                    catch (IllegalArgumentException ignored) { }
                }
                List<Exec> ex = new ArrayList<>();
                JsonArray arr = o.has("ex") ? o.getAsJsonArray("ex") : new JsonArray();
                for (JsonElement e : arr) ex.add(new Exec(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("n").getAsString()));
                execs = List.copyOf(ex);
                if (o.has("cp")) for (JsonElement e : o.getAsJsonArray("cp")) {
                    JsonObject c = e.getAsJsonObject();
                    try { dressCorpse(UUID.fromString(c.get("m").getAsString()), c.get("a").getAsString()); }
                    catch (RuntimeException ignored) { }
                }
            }
            case "mort" -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.5f, 1f));
                mc.setScreenAndShow(new DeathScreen(o.has("nom") ? o.get("nom").getAsString() : "",
                        o.has("min") ? o.get("min").getAsInt() : 60));
            }
            case "fin" -> endScene();
            default -> { }
        }
    }

    /** Compose une fois le skin Reborn d'un corps au sol (apparence du personnage mort). */
    private static void dressCorpse(UUID mannequin, String look) {
        Skin cur = corpses.get(mannequin);
        if (cur != null && cur.look().equals(look)) return;
        SkinSpec spec = SkinSpec.deserialize(look);
        if (spec == null || spec.useOwnSkin) return;
        UUID key = UUID.nameUUIDFromBytes(("reborn-corps:" + mannequin).getBytes(StandardCharsets.UTF_8));
        RebornSkins.applySpec(key, spec);
        Identifier tex = RebornSkins.overrideFor(key);
        if (tex != null) corpses.put(mannequin, new Skin(tex, spec.slim, look));
    }

    /** Skin Reborn d'un corps au sol, ou null (voir {@code ClientMannequinSkinMixin}). */
    public static Skin corpseSkin(UUID mannequin) { return corpses.get(mannequin); }

    private static void endScene() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof DeathScreen d) {
            d.restore();
            mc.setScreenAndShow(null);
        }
    }

    public static void clear() {
        endScene();
        bodies.clear();
        corpses.clear();
    }

    public static Body body(UUID id) { return bodies.get(id); }

    public static List<Exec> executions() { return execs; }

    public static boolean isDead() { return Minecraft.getInstance().gui.screen() instanceof DeathScreen; }
}
