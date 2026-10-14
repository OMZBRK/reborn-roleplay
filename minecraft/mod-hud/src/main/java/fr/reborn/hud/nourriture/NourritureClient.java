package fr.reborn.hud.nourriture;

import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Nourriture côté client (ShinobiCore {@code nourriture/}, docs/technique/PLUGINS_REFERENCE.md §4 ter). Deux canaux
 * S2C, JSON UTF-8 brut sans préfixe, champs optionnels :
 * <ul>
 *   <li>{@code reborn:nourriture} : {@code prep} / {@code prep_fin} (préparer au fourneau), {@code repas} /
 *       {@code repas_fin} (manger), {@code offre} / {@code offre_fin} (on me tend quelque chose), {@code attente} /
 *       {@code attente_fin} (ce que je tends), {@code main} (plat tenu : chaud / froid). Déclarer ce canal vaut « je lis
 *       les vitals étendus » (satiété dans {@code reborn:vitals}) ;</li>
 *   <li>{@code reborn:modifs} : {@code {"mods":[{"id","nom","ic","b","val","reste","duree","pause"}]}} — les effets
 *       actifs visibles, décomptés ici entre deux paquets.</li>
 * </ul>
 * Les réponses du joueur repartent en commandes ({@code /tendre prendre|refuser}) : le serveur revérifie tout.
 */
public final class NourritureClient {

    /** Charge utile JSON brute (un type par canal). */
    public record Payload(Type<Payload> kind, String json) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return kind; }
    }

    static final CustomPacketPayload.Type<Payload> NOURRITURE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "nourriture"));
    static final CustomPacketPayload.Type<Payload> MODIFS =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "modifs"));

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

    /** Une ligne + un filet au-dessus de la barre d'action (préparer ou manger). */
    public static final class Action {
        public final boolean repas;
        public final String nom, icone;
        public final float duree;
        public float fait;
        public int vitesse;
        public long pauseJusqua;
        public boolean froid;
        public long maj;
        Action(boolean repas, String nom, String icone, float duree) {
            this.repas = repas;
            this.nom = nom;
            this.icone = icone;
            this.duree = Math.max(0.1f, duree);
        }
        /** Avancement estimé (0..1), extrapolé depuis le dernier paquet. */
        public float avance(long now) {
            float f = fait;
            if (now >= pauseJusqua && vitesse > 0) f += (now - Math.max(maj, pauseJusqua)) / 1000f * vitesse;
            return Math.max(0f, Math.min(1f, f / duree));
        }
        public int secondesRestantes(long now) {
            float r = duree * (1 - avance(now)) / Math.max(1, vitesse);
            return (int) Math.ceil(r);
        }
        public int pause(long now) { return (int) Math.ceil(Math.max(0, pauseJusqua - now) / 1000.0); }
    }

    /** On me tend quelque chose. */
    public record Offre(String de, String quoi, String etat, String mat, String model, String ic, long a, int secs) {
        public float reste(long now) { return Math.max(0f, secs - (now - a) / 1000f); }
    }

    /** Ce que je tends. */
    public record Attente(String a, String quoi, long t, int secs) {
        public float reste(long now) { return Math.max(0f, secs - (now - t) / 1000f); }
    }

    /** Plat tenu en main : chaud (secondes restantes à la réception) ou froid. */
    public record Main(String nom, String ic, int chaud, long recu) {
        public int chaudRestant(long now) { return Math.max(0, chaud - (int) ((now - recu) / 1000)); }
    }

    /** Un effet actif (modificateur). */
    public record Effet(String id, String nom, String ic, boolean bonus, String val, int reste, int duree, boolean pause,
                        long recu, long apparu) {
        public int resteMaintenant(long now) { return Math.max(0, reste - (int) ((now - recu) / 1000)); }
    }

    static Action action;
    static Offre offre;
    static Attente attente;
    static Main main;
    static final List<Effet> EFFETS = new ArrayList<>();

    private NourritureClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(NOURRITURE, codec(NOURRITURE));
        PayloadTypeRegistry.clientboundPlay().register(MODIFS, codec(MODIFS));
        ClientPlayNetworking.registerGlobalReceiver(NOURRITURE, (p, ctx) -> ctx.client().execute(() -> recevoir(p.json())));
        ClientPlayNetworking.registerGlobalReceiver(MODIFS, (p, ctx) -> ctx.client().execute(() -> recevoirModifs(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(NourritureClient::clear));
    }

    public static void clear() {
        action = null;
        offre = null;
        attente = null;
        main = null;
        synchronized (EFFETS) { EFFETS.clear(); }
    }

    /** Banc d'essai : rejoue un message {@code reborn:nourriture}. */
    public static void debugReceive(String raw) { recevoir(raw); }

    /** Banc d'essai : rejoue un message {@code reborn:modifs}. */
    public static void debugModifs(String raw) { recevoirModifs(raw); }

    private static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        long now = System.currentTimeMillis();
        switch (str(o, "t", "")) {
            case "prep", "repas" -> {
                boolean repas = "repas".equals(str(o, "t", ""));
                Action a = action != null && action.repas == repas && action.nom.equals(str(o, "nom", "")) ? action
                    : new Action(repas, str(o, "nom", "?"), str(o, "ic", repas ? "repas" : "chaud"), (float) dbl(o, "dur", 30));
                a.fait = (float) dbl(o, "fait", 0);
                a.vitesse = (int) dbl(o, "vit", 1);
                a.pauseJusqua = now + (long) (dbl(o, "pause", 0) * 1000);
                a.froid = bool(o, "froid");
                a.maj = now;
                action = a;
            }
            case "prep_fin", "repas_fin" -> action = null;
            case "offre" -> {
                offre = new Offre(str(o, "de", "?"), str(o, "quoi", "?"), str(o, "etat", null), str(o, "mat", null),
                    str(o, "model", null), str(o, "ic", null), now, (int) dbl(o, "s", 10));
                fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 0.9f, 0.5f);
            }
            case "offre_fin" -> offre = null;
            case "attente" -> attente = new Attente(str(o, "a", "?"), str(o, "quoi", "?"), now, (int) dbl(o, "s", 10));
            case "attente_fin" -> attente = null;
            case "main" -> main = o.has("nom") ? new Main(str(o, "nom", ""), str(o, "ic", "repas"), (int) dbl(o, "chaud", 0), now) : null;
            default -> { }
        }
    }

    private static void recevoirModifs(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        long now = System.currentTimeMillis();
        List<Effet> l = new ArrayList<>();
        if (o.has("mods") && o.get("mods").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("mods")) {
                if (!e.isJsonObject()) continue;
                JsonObject m = e.getAsJsonObject();
                String id = str(m, "id", "");
                long apparu = now;
                synchronized (EFFETS) {
                    for (Effet x : EFFETS) if (x.id().equals(id)) { apparu = x.apparu(); break; }
                }
                l.add(new Effet(id, str(m, "nom", id), str(m, "ic", "repas"), dbl(m, "b", 1) > 0, str(m, "val", ""),
                    (int) dbl(m, "reste", 0), (int) Math.max(1, dbl(m, "duree", 1)), dbl(m, "pause", 0) > 0, now, apparu));
            }
        }
        synchronized (EFFETS) {
            EFFETS.clear();
            EFFETS.addAll(l);
        }
    }

    /* ================================================================ actions du joueur */

    public static boolean aOffre() {
        if (offre != null && offre.reste(System.currentTimeMillis()) <= 0) offre = null;
        return offre != null;
    }

    public static void prendre() {
        if (!aOffre()) return;
        envoyer("tendre prendre");
        offre = null;
    }

    public static void refuser() {
        if (!aOffre()) return;
        envoyer("tendre refuser");
        offre = null;
    }

    /** Une ligne d'action (préparer / manger) occupe la place du filet de sprint. */
    public static boolean filetActif() { return action != null; }

    private static void envoyer(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
        fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 1.1f, 0.4f);
    }

    static List<Effet> effets() {
        synchronized (EFFETS) { return new ArrayList<>(EFFETS); }
    }

    /* ================================================================ JSON */

    private static String str(JsonObject o, String k, String d) {
        try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : d; } catch (RuntimeException e) { return d; }
    }

    private static double dbl(JsonObject o, String k, double d) {
        try { return o.has(k) ? o.get(k).getAsDouble() : d; } catch (RuntimeException e) { return d; }
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }
}
