package fr.reborn.hud.radio;

import com.google.gson.JsonArray;
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
import net.minecraft.sounds.SoundEvents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La radio en escouades côté mod (docs/gameplay/RADIO.md v3) — canal {@code reborn:radio} (S → C, JSON UTF-8 brut,
 * champs optionnels), servi par le plugin ShinobiRadio :
 * <ul>
 *   <li>{@code {"t":"etat","ore":"aucune|neuve|ok|coupee|exclue","num":12,"volee":true,"proprio":"Ren Hyūga",
 *       "proprioId":uuid,"canal":"Kagerō","h":bool,"muet":bool,"staff":bool,"coupe":bool,
 *       "esc":{"id","nom","n","max","createur":bool,"membres":[{"id":compte,"perso","nom","court",
 *       "etat":"en_ligne|a_terre|inconscient|mort|hors_ligne","ore":"ok|coupee|sans|prise|","createur","moi"}]}}} ;</li>
 *   <li>{@code {"t":"parle","src":uuid,"id":uuid de la tête,"nom":"Sakura H.","h":bool}} / {@code {"t":"tait","src"}} ;</li>
 *   <li>{@code {"t":"invit","de":"Aki U.","esc":"Kagerō","creation":bool,"s":10}} / {@code {"t":"invit_fin"}} ;</li>
 *   <li>{@code {"t":"ecoute","esc":"Kagerō","reste":600}} / {@code {"t":"ecoute_fin"}} (staff seulement).</li>
 * </ul>
 * Les actions repartent par commandes {@code /radio …} (menu R, écran N, touches Entrée / Retour) : le serveur
 * revérifie tout. Tout est lu sur le thread client ; {@link #hache(UUID)} est lu par le thread audio (map concurrente).
 */
public final class RadioClient {

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "radio"));
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

    /** Un membre de mon escouade. */
    public record Membre(UUID id, String nom, String court, String etat, String ore, boolean createur, boolean moi) {
        public boolean enLigne() { return !"hors_ligne".equals(etat); }
    }

    /** Mon escouade. */
    public record Escouade(String id, String nom, int n, int max, boolean createur, List<Membre> membres) { }

    /** Mon état radio. */
    public record Etat(String ore, int num, boolean volee, String proprio, UUID proprioId, String canal, boolean hache,
                       boolean muet, boolean staff, boolean coupe, Escouade esc) {
        static final Etat AUCUN = new Etat("aucune", -1, false, "", null, "", false, false, false, false, null);

        /** Peut-on émettre (B) ? Sinon la raison, courte. */
        public String refus() {
            if (staff) return "mode staff";
            if (muet) return "inconscient";
            if (coupe) return "coupée par le staff";
            return switch (ore) {
                case "ok" -> null;
                case "coupee" -> "oreillette coupée";
                case "neuve" -> "aucune escouade";
                case "exclue" -> "oreillette exclue";
                default -> "pas d'oreillette";
            };
        }
    }

    /** Quelqu'un parle (ou vient de se taire : effacé {@link #EFFACE_MS} après la fin). */
    public static final class Parole {
        public final String src;
        public final UUID tete;
        public final String nom;
        public final boolean hache;
        public final long debut;
        public long fin = -1;

        Parole(String src, UUID tete, String nom, boolean hache, long debut) {
            this.src = src;
            this.tete = tete;
            this.nom = nom;
            this.hache = hache;
            this.debut = debut;
        }
    }

    public record Invit(String de, String esc, boolean creation, long at, int secs) {
        public float reste(long now) { return Math.max(0f, secs - (now - at) / 1000f); }
    }

    public record Ecoute(String esc, long fin) { }

    public static final long EFFACE_MS = 1500;

    static Etat etat = Etat.AUCUN;
    static boolean serveur;
    static final Map<String, Parole> paroles = new LinkedHashMap<>();
    /** Sources Plasmo Voice de la ligne radio marquées « hachées » (lu par le thread audio). */
    private static final Map<UUID, Boolean> HACHEES = new java.util.concurrent.ConcurrentHashMap<>();
    static Invit invit;
    static Ecoute ecoute;
    /** Dernier refus de B (« pas d'oreillette »…), affiché brièvement dans l'élément Escouade. */
    static String refusB;
    static long refusBAt;

    private RadioClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (p, ctx) -> ctx.client().execute(() -> receive(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(RadioClient::clear));
    }

    public static void clear() {
        etat = Etat.AUCUN;
        serveur = false;
        paroles.clear();
        HACHEES.clear();
        invit = null;
        ecoute = null;
        refusB = null;
    }

    /** Banc d'essai : efface les lignes « parle » restantes. */
    public static void debugEffacerParoles() { paroles.clear(); }

    /** Banc d'essai : rejoue un message serveur. */
    public static void debugReceive(String raw) { receive(raw); }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        serveur = true;
        long now = System.currentTimeMillis();
        switch (str(o, "t", "")) {
            case "etat" -> etat = lireEtat(o);
            case "parle" -> {
                String src = str(o, "src", "");
                Parole p = new Parole(src, uuid(str(o, "id", "")), str(o, "nom", "?"), bool(o, "h"), now);
                paroles.remove(src);
                paroles.put(src, p);
                UUID s = uuid(src);
                if (s != null) HACHEES.put(s, p.hache);
                clic(1.6f);
            }
            case "tait" -> {
                Parole p = paroles.get(str(o, "src", ""));
                if (p != null && p.fin < 0) { p.fin = now; clic(1.25f); }
            }
            case "invit" -> {
                invit = new Invit(str(o, "de", "?"), str(o, "esc", "?"), bool(o, "creation"), now, num(o, "s", 10));
                fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 0.8f, 0.5f);
            }
            case "invit_fin" -> invit = null;
            case "ecoute" -> ecoute = new Ecoute(str(o, "esc", "?"), now + num(o, "reste", 600) * 1000L);
            case "ecoute_fin" -> ecoute = null;
            default -> { }
        }
    }

    private static Etat lireEtat(JsonObject o) {
        Escouade esc = null;
        if (o.has("esc") && o.get("esc").isJsonObject()) {
            JsonObject e = o.getAsJsonObject("esc");
            List<Membre> l = new ArrayList<>();
            if (e.has("membres") && e.get("membres").isJsonArray()) {
                JsonArray a = e.getAsJsonArray("membres");
                for (JsonElement x : a) {
                    if (!x.isJsonObject()) continue;
                    JsonObject m = x.getAsJsonObject();
                    l.add(new Membre(uuid(str(m, "id", "")), str(m, "nom", "?"), str(m, "court", str(m, "nom", "?")),
                            str(m, "etat", "hors_ligne"), str(m, "ore", ""), bool(m, "createur"), bool(m, "moi")));
                }
            }
            esc = new Escouade(str(e, "id", ""), str(e, "nom", "?"), num(e, "n", l.size()), num(e, "max", 8),
                    bool(e, "createur"), List.copyOf(l));
        }
        return new Etat(str(o, "ore", "aucune"), num(o, "num", -1), bool(o, "volee"), str(o, "proprio", ""),
                uuid(str(o, "proprioId", "")), str(o, "canal", ""), bool(o, "h"), bool(o, "muet"), bool(o, "staff"),
                bool(o, "coupe"), esc);
    }

    /* ------------------------------------------------------------------ lecture */

    public static Etat etat() { return etat; }

    public static Escouade escouade() { return etat.esc(); }

    /** Le serveur a-t-il la radio (un message reçu depuis la connexion) ? */
    public static boolean serveur() { return serveur; }

    public static Invit invit() {
        if (invit != null && invit.reste(System.currentTimeMillis()) <= 0) invit = null;
        return invit;
    }

    public static Ecoute ecoute() {
        if (ecoute != null && ecoute.fin() < System.currentTimeMillis()) ecoute = null;
        return ecoute;
    }

    /** Paroles à afficher (en cours, ou terminées depuis moins de {@link #EFFACE_MS}), plus anciennes d'abord. */
    public static List<Parole> paroles() {
        long now = System.currentTimeMillis();
        paroles.values().removeIf(p -> p.fin >= 0 && now - p.fin > EFFACE_MS);
        return List.copyOf(paroles.values());
    }

    /** Thread audio : la source radio {@code src} doit-elle être hachée ? (ou moi-même à terre) */
    public static boolean hache(UUID src) {
        if (etat.hache()) return true;
        Boolean b = src == null ? null : HACHEES.get(src);
        return b != null && b;
    }

    /** Touche B pressée : rappel discret si l'on ne peut pas émettre. */
    public static void appuiB() {
        if (!serveur) return;
        String r = etat.refus();
        if (r != null) { refusB = r; refusBAt = System.currentTimeMillis(); }
    }

    /* ------------------------------------------------------------------ actions */

    public static boolean accepter() {
        if (invit() == null) return false;
        send("radio accepter");
        invit = null;
        return true;
    }

    public static boolean refuser() {
        if (invit() == null) return false;
        send("radio refuser");
        invit = null;
        return true;
    }

    static void send(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
    }

    /** Petit clic de squelch au début et à la fin d'une prise de parole (non spatialisé, très bas). */
    private static void clic(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.08f));
        } catch (RuntimeException ignored) { }
    }

    /* ------------------------------------------------------------------ JSON */

    static String str(JsonObject o, String k, String d) {
        try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : d; } catch (RuntimeException e) { return d; }
    }

    static int num(JsonObject o, String k, int d) {
        try { return o.has(k) ? o.get(k).getAsInt() : d; } catch (RuntimeException e) { return d; }
    }

    static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    static UUID uuid(String s) {
        try { return s == null || s.isEmpty() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
