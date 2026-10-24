package fr.reborn.hud.etat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Canal {@code reborn:etat} (S2C, ShinobiCore {@code etat.EtatSuivi}, docs/technique/ETATS_JOUEUR.md §7.2) — JSON UTF-8
 * brut : l'état du joueur <b>tenu par le serveur</b> (état principal, trois couches, capacités utiles au client).
 *
 * <pre>{"v":1,"seq":4182,"p":"PORTE","c":{"vital":"DOULEUR","ctrl":"PORTE","cadre":"MONDE"},
 *  "cap":{"bou":0,"tro":0,"spr":0,"sau":0,"mob":0,"fra":0,"gar":0,"tec":0,"ram":0,"int":0,"exe":0,"par":2,"voc":2,
 *         "debattre":2,"sac":"L"},"vit":0.6,"saut":1,"lien":{"type":"PORTE","nom":"Kazuki Uchiha"},
 *  "mods":[{"id":"ata","n":2,"fin":0}],"raison":{"tec":"On te porte."}}</pre>
 *
 * Capacités : {@code 2} permis, {@code 1} limité (ramper, chuchoter), {@code 0} refusé ; {@code sac} : {@code M}
 * modifier, {@code L} lecture, {@code 0} fermé. Le mod s'en sert pour <b>bloquer en avance</b> (pas de saut fantôme, pas
 * de menu R à terre, pas de coup en douleur) — jamais pour décider : le serveur refuse de toute façon.
 *
 * <p>Tant qu'aucun paquet n'est arrivé (serveur ancien, solo), {@link #actif()} est faux et tout est permis.
 * Remis à zéro à la déconnexion.
 */
public final class EtatClient {

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(Identifier.fromNamespaceAndPath("reborn", "etat"));
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

    /** Instantané reçu (immuable). */
    public record Etat(String principal, String vital, String ctrl, String cadre, Map<String, Integer> cap, String sac,
                       double vitesse, double saut, Map<String, String> raisons, long seq) {}

    private static volatile Etat etat;

    /**
     * Un modificateur d'état reçu dans {@code mods} (dérivés : ata, faim, affame, prison, en_combat, surcharge… et les
     * états du registre montrés au joueur : menottes). {@code finA} : époque locale de fin, 0 = sans fin connue.
     */
    public record Mod(String id, double n, long finA) {
        /** Secondes restantes (décomptées localement), -1 sans fin connue. */
        public int resteS() { return finA <= 0 ? -1 : (int) Math.max(0, (finA - System.currentTimeMillis() + 999) / 1000); }
    }

    private static volatile java.util.List<Mod> mods = java.util.List.of();

    private EtatClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID,
            (payload, context) -> context.client().execute(() -> recevoir(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void clear() { etat = null; mods = java.util.List.of(); }

    /** Point d'entrée commun (réseau et bancs d'essai). */
    public static void recevoir(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        long seq = o.has("seq") ? o.get("seq").getAsLong() : 0;
        Etat prec = etat;
        if (prec != null && seq > 0 && prec.seq() > seq) return;   // paquet en retard
        Map<String, Integer> cap = new HashMap<>();
        String sac = "M";
        if (o.has("cap") && o.get("cap").isJsonObject()) {
            for (var e : o.getAsJsonObject("cap").entrySet()) {
                if (e.getKey().equals("sac")) { sac = e.getValue().getAsString(); continue; }
                try { cap.put(e.getKey(), e.getValue().getAsInt()); } catch (Exception ignored) { }
            }
        }
        Map<String, String> raisons = new HashMap<>();
        if (o.has("raison") && o.get("raison").isJsonObject()) {
            for (var e : o.getAsJsonObject("raison").entrySet()) raisons.put(e.getKey(), e.getValue().getAsString());
        }
        java.util.List<Mod> ms = new java.util.ArrayList<>();
        if (o.has("mods") && o.get("mods").isJsonArray()) {
            long now = System.currentTimeMillis();
            for (var el : o.getAsJsonArray("mods")) {
                try {
                    JsonObject m = el.getAsJsonObject();
                    long fin = m.has("fin") ? m.get("fin").getAsLong() : 0;
                    ms.add(new Mod(m.get("id").getAsString(), m.has("n") ? m.get("n").getAsDouble() : 1, fin > 0 ? now + fin : 0));
                } catch (Exception ignored) { }
            }
        }
        mods = java.util.List.copyOf(ms);
        JsonObject c = o.has("c") && o.get("c").isJsonObject() ? o.getAsJsonObject("c") : new JsonObject();
        etat = new Etat(str(o, "p", "LIBRE"), str(c, "vital", "VALIDE"), str(c, "ctrl", "LIBRE"), str(c, "cadre", "MONDE"),
                Map.copyOf(cap), sac, dbl(o, "vit", 1), dbl(o, "saut", 1), Map.copyOf(raisons), seq);
    }

    /** Un état serveur a-t-il été reçu (serveur Reborn récent) ? */
    public static boolean actif() { return etat != null; }

    public static Etat etat() { return etat; }

    /** Modificateurs d'état reçus (vide tant qu'aucun paquet). */
    public static java.util.List<Mod> mods() { return mods; }

    /** Le modificateur d'état {@code id}, ou null. */
    public static Mod mod(String id) {
        for (Mod m : mods) if (m.id().equals(id)) return m;
        return null;
    }

    /** Niveau d'une capacité (2 si inconnue ou pas d'état reçu). */
    public static int niveau(String code) {
        Etat e = etat;
        if (e == null) return 2;
        Integer n = e.cap().get(code);
        return n == null ? 2 : n;
    }

    /** La capacité est-elle refusée par le serveur ? Faux tant qu'aucun état n'est reçu. */
    public static boolean refuse(String code) { return niveau(code) <= 0; }

    /** Raison lisible d'un refus (ou null). */
    public static String raison(String code) {
        Etat e = etat;
        return e == null ? null : e.raisons().get(code);
    }

    /** Dans un cadre de jeu (monde, entraînement, duel, prison) — pas en mode staff, construction, hors jeu. */
    public static boolean enJeu() {
        Etat e = etat;
        if (e == null) return false;
        return switch (e.cadre()) {
            case "MONDE", "ENTRAINEMENT", "DUEL", "PRISON" -> true;
            default -> false;
        };
    }

    /** À terre, inconscient ou mort : les voiles imposés (filtre KO, scène de mort) ne se contournent pas. */
    public static boolean voileImpose() {
        Etat e = etat;
        if (e == null) return false;
        return switch (e.vital()) {
            case "A_TERRE", "INCONSCIENT", "MORT" -> true;
            default -> false;
        };
    }

    /**
     * Un voile imposé est-il actif (KO, mort RP, écran noir du staff) ? Le mode photo et F1 (HUD masqué) ne doivent
     * pas le lever (ETATS_JOUEUR §2.3). La cécité des yeux, permanente, n'en fait pas partie (choix prudent).
     */
    public static boolean voileBloqueImmersion() {
        return voileImpose() || fr.reborn.hud.staff.EffetsStaff.noirActif();
    }

    private static String str(JsonObject o, String k, String def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; } catch (Exception e) { return def; }
    }

    private static double dbl(JsonObject o, String k, double def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def; } catch (Exception e) { return def; }
    }
}
