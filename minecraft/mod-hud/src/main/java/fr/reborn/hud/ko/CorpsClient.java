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

    /** phase 1 à terre (agonie), 2 inconscient, 3 mort, {@link #PHASE_DOULEUR} en douleur (debout). */
    public record Body(int phase, int rpk) {}
    public static final int PHASE_DOULEUR = 4;
    /** Exécution en cours où je suis : rôle 0 exécuteur / 1 victime, ticks écoulés à la réception, durée, instant du
     *  coup, mort RP ou mise hors de combat. */
    public record Exe(int role, int elapsed, int duration, int strike, boolean lethal, long at) {
        /** Avancement 0..1, extrapolé depuis la réception. */
        public float progress() {
            float e = elapsed + (System.currentTimeMillis() - at) / 50f;
            return duration <= 0 ? 1f : Math.max(0f, Math.min(1f, e / duration));
        }
        public float strikeAt() { return duration <= 0 ? 1f : Math.min(1f, strike / (float) duration); }
    }
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
    /** Mon état : en douleur, porté, jauge de lutte (-1 = pas de lutte), exécution en cours. */
    private static volatile boolean douleur, porte;
    private static volatile float lutte = -1f;
    private static volatile Exe exe;
    /** mannequin (corps au sol) → skin Reborn composé. */
    private static final Map<UUID, Skin> corpses = new ConcurrentHashMap<>();
    /** Mannequins qui sont des corps au sol (menu d'interaction : prélever un œil). */
    private static final java.util.Set<UUID> corpseIds = ConcurrentHashMap.newKeySet();

    private CorpsClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (p, ctx) -> ctx.client().execute(() -> receive(p.json())));
        // 14/10 v2 : exécution à la touche Ramasser (F) maintenue ; HUD masqué à terre et inconscient.
        ExecuterClient.init();
        KoMasqueHud.init();
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
                if (o.has("dl")) for (JsonElement e : o.getAsJsonArray("dl")) {
                    try { bodies.put(UUID.fromString(e.getAsJsonObject().get("u").getAsString()), new Body(PHASE_DOULEUR, 0)); }
                    catch (RuntimeException ignored) { }
                }
                readMe(o.has("me") && o.get("me").isJsonObject() ? o.getAsJsonObject("me") : new JsonObject());
                List<Exec> ex = new ArrayList<>();
                JsonArray arr = o.has("ex") ? o.getAsJsonArray("ex") : new JsonArray();
                for (JsonElement e : arr) ex.add(new Exec(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("n").getAsString()));
                execs = List.copyOf(ex);
                if (o.has("cp")) {
                    corpseIds.clear();
                    for (JsonElement e : o.getAsJsonArray("cp")) {
                        JsonObject c = e.getAsJsonObject();
                        try {
                            UUID m = UUID.fromString(c.get("m").getAsString());
                            corpseIds.add(m);
                            dressCorpse(m, c.get("a").getAsString());
                        } catch (RuntimeException ignored) { }
                    }
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

    private static void readMe(JsonObject me) {
        douleur = me.has("d") && me.get("d").getAsInt() == 1;
        porte = me.has("c");
        lutte = me.has("s") ? me.get("s").getAsFloat() : -1f;
        if (me.has("x")) {
            JsonObject x = me.getAsJsonObject("x");
            exe = new Exe(x.get("r").getAsInt(), x.get("e").getAsInt(), x.get("d").getAsInt(), x.get("k").getAsInt(),
                    x.get("l").getAsInt() == 1, System.currentTimeMillis());
        } else {
            exe = null;
        }
    }

    /** Suis-je en douleur (PV sous le seuil, avant le KO) ? */
    public static boolean douleur() { return douleur; }

    /** Suis-je porté (sur les épaules de quelqu'un) ? */
    public static boolean porte() { return porte; }

    /** Jauge de lutte quand je suis porté en douleur (0..1), -1 sinon. */
    public static float lutte() { return lutte; }

    /** Exécution en cours où je suis exécuteur ou victime, ou null. */
    public static Exe exe() { return exe; }

    /** Tous les corps / personnes en douleur connus (étiquettes au-dessus des têtes). */
    public static Map<UUID, Body> bodies() { return bodies; }

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

    /** Ce mannequin est-il un corps au sol (connu du serveur) ? */
    public static boolean isCorpse(UUID mannequin) { return corpseIds.contains(mannequin) || corpses.containsKey(mannequin); }

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
        ExecuterClient.clear();
        douleur = false;
        porte = false;
        lutte = -1f;
        exe = null;
        corpses.clear();
        corpseIds.clear();
    }

    public static Body body(UUID id) { return bodies.get(id); }

    public static List<Exec> executions() { return execs; }

    public static boolean isDead() { return Minecraft.getInstance().gui.screen() instanceof DeathScreen; }
}
