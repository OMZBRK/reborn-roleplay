package fr.reborn.hud.mudra;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.staff.CanalJson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Canal {@code reborn:mudra} côté mod (mudras au clavier et portes scellées, 14/11 ; ShinobiCore {@code mudra/MudraService},
 * PLUGINS_REFERENCE) : JSON UTF-8 brut dans les deux sens, champ {@code v} = {@value #VERSION}.
 * C → S : {@code ouvrir}, {@code signe} (n, s), {@code fermer} (n) ; S → C : {@code regles}, {@code ouvre}, {@code fin},
 * {@code refus}, {@code geste}, passés à {@link MudraClient} sur le fil client.
 */
public final class MudraCanal {

    public static final int VERSION = 1;
    public static final CustomPacketPayload.Type<CanalJson> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "mudra"));

    /** Banc : les demandes partent vers ce serveur simulé au lieu du vrai (null = réseau). */
    public static volatile Consumer<JsonObject> debugServeur;

    private MudraCanal() {}

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CanalJson.codec(TYPE));
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CanalJson.codec(TYPE));
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (p, c) -> c.client().execute(() -> recevoir(p.json())));
    }

    /** Un message du serveur (fil client ; le banc y passe des messages simulés). */
    public static void recevoir(String raw) {
        JsonObject o;
        try {
            o = JsonParser.parseString(raw).getAsJsonObject();
            if (!o.has("v") || o.get("v").getAsInt() != VERSION) return;
        } catch (RuntimeException e) { return; }
        MudraClient.recevoir(o);
    }

    /** Le serveur (ou le banc) peut-il recevoir une demande ? */
    static boolean disponible() {
        return debugServeur != null || ClientPlayNetworking.canSend(TYPE);
    }

    static void ouvrir() { envoyer(base("ouvrir")); }

    static void signe(int session, int signe) {
        JsonObject o = base("signe");
        o.addProperty("n", session);
        o.addProperty("s", signe);
        envoyer(o);
    }

    static void fermer(int session) {
        JsonObject o = base("fermer");
        o.addProperty("n", session);
        envoyer(o);
    }

    private static JsonObject base(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("a", action);
        return o;
    }

    private static void envoyer(JsonObject o) {
        Consumer<JsonObject> d = debugServeur;
        if (d != null) { d.accept(o); return; }
        if (ClientPlayNetworking.canSend(TYPE)) ClientPlayNetworking.send(new CanalJson(TYPE, o.toString()));
    }
}
