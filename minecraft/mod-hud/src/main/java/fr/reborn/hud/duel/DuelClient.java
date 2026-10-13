package fr.reborn.hud.duel;

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
 * Duel consenti (ShinobiCore {@code duel/DuelService}) — canal {@code reborn:duel}, JSON UTF-8 brut sans préfixe, tous
 * les champs optionnels :
 * <ul>
 *   <li>{@code {"t":"invit","de":pseudo,"nom":nom affiché,"s":secondes}} / {@code {"t":"invit_fin","de":pseudo}} :
 *       invitation reçue / tombée (acceptée, refusée, expirée) ;</li>
 *   <li>{@code {"t":"attente","a":pseudo,"nom":…,"s":…}} / {@code {"t":"attente_fin"}} : ma proposition attend ;</li>
 *   <li>{@code {"t":"debut","adv":nom,"advId":pseudo,"cr":3,"duree":300,"tech":bool}} : compte à rebours puis combat ;</li>
 *   <li>{@code {"t":"etat","d":distance,"max":…,"reste":secondes}} : chaque seconde pendant le duel ;</li>
 *   <li>{@code {"t":"fin","issue":"victoire"|"defaite"|"nul"|"annule","adv":nom,"raison":texte}}.</li>
 * </ul>
 * Les réponses repartent par commandes ({@code /duel accepter|refuser|abandonner}) : le serveur revérifie tout.
 */
public final class DuelClient {

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "duel"));
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

    /** Invitation reçue. */
    public record Invite(String fromId, String fromName, long at, int secs) {
        public float left(long now) { return Math.max(0f, secs - (now - at) / 1000f); }
    }

    /** Ma proposition en attente. */
    public record Waiting(String toId, String toName, long at, int secs) {
        public float left(long now) { return Math.max(0f, secs - (now - at) / 1000f); }
    }

    /** Duel en cours (compte à rebours compris). */
    public static final class Duel {
        public final String advName, advId;
        public final long at;
        public final int countdown, duration;
        public final boolean techniques;
        public double dist, max = 30;
        public long remaining = -1, stateAt;
        Duel(String advName, String advId, long at, int countdown, int duration, boolean techniques) {
            this.advName = advName; this.advId = advId; this.at = at;
            this.countdown = countdown; this.duration = duration; this.techniques = techniques;
        }
        /** Millisecondes depuis le signal (négatif pendant le compte à rebours). */
        public long sinceFight(long now) { return now - at - countdown * 1000L; }
        public long secondsLeft(long now) {
            if (remaining >= 0) return Math.max(0, remaining - (now - stateAt) / 1000L);
            return Math.max(0, duration - Math.max(0, sinceFight(now)) / 1000L);
        }
    }

    /** Fin du duel (plaque affichée quelques secondes). */
    public record End(String issue, String advName, String reason, long at) { }

    static Invite invite;
    static Waiting waiting;
    static Duel duel;
    static End end;

    private DuelClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (p, ctx) -> ctx.client().execute(() -> receive(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(DuelClient::clear));
    }

    public static void clear() {
        invite = null;
        waiting = null;
        duel = null;
        end = null;
    }

    /** Banc d'essai : rejoue un message serveur. */
    public static void debugReceive(String raw) { receive(raw); }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        long now = System.currentTimeMillis();
        switch (str(o, "t", "")) {
            case "invit" -> {
                invite = new Invite(str(o, "de", ""), str(o, "nom", str(o, "de", "?")), now, num(o, "s", 30));
                fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 0.8f, 0.5f);
            }
            case "invit_fin" -> {
                String de = str(o, "de", "");
                if (invite != null && (de.isEmpty() || de.equalsIgnoreCase(invite.fromId()))) invite = null;
            }
            case "attente" -> waiting = new Waiting(str(o, "a", ""), str(o, "nom", str(o, "a", "?")), now, num(o, "s", 30));
            case "attente_fin" -> waiting = null;
            case "debut" -> {
                invite = null;
                waiting = null;
                end = null;
                duel = new Duel(str(o, "adv", "?"), str(o, "advId", ""), now, num(o, "cr", 3), num(o, "duree", 300),
                        !o.has("tech") || bool(o, "tech"));
            }
            case "etat" -> {
                if (duel == null) return;
                duel.dist = dbl(o, "d", duel.dist);
                duel.max = dbl(o, "max", duel.max);
                if (o.has("reste")) { duel.remaining = num(o, "reste", 0); duel.stateAt = now; }
            }
            case "fin" -> {
                duel = null;
                end = new End(str(o, "issue", "annule"), str(o, "adv", ""), str(o, "raison", ""), now);
            }
            default -> { }
        }
    }

    /* ------------------------------------------------------------------ actions du joueur */

    public static boolean hasInvite() {
        if (invite != null && invite.left(System.currentTimeMillis()) <= 0) invite = null;
        return invite != null;
    }

    public static void accept() {
        if (!hasInvite()) return;
        send("duel accepter " + invite.fromId());
        invite = null;
    }

    public static void refuse() {
        if (!hasInvite()) return;
        send("duel refuser " + invite.fromId());
        invite = null;
    }

    public static void forfeit() {
        if (duel != null) send("duel abandonner");
    }

    /** Suis-je en duel contre ce joueur (pseudo Minecraft) ? */
    public static boolean inDuelWith(String player) {
        return duel != null && duel.advId.equalsIgnoreCase(player);
    }

    public static boolean inDuel() { return duel != null; }

    /** Invitation en attente venant de ce joueur ? */
    public static boolean invitedBy(String player) {
        return hasInvite() && invite.fromId().equalsIgnoreCase(player);
    }

    private static void send(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
        fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 1.1f, 0.4f);
    }

    /* ------------------------------------------------------------------ JSON */

    private static String str(JsonObject o, String k, String d) {
        try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : d; } catch (RuntimeException e) { return d; }
    }

    private static int num(JsonObject o, String k, int d) {
        try { return o.has(k) ? o.get(k).getAsInt() : d; } catch (RuntimeException e) { return d; }
    }

    private static double dbl(JsonObject o, String k, double d) {
        try { return o.has(k) ? o.get(k).getAsDouble() : d; } catch (RuntimeException e) { return d; }
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }
}
