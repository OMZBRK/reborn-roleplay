package fr.reborn.hud.byakugan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.staff.CanalJson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Canal {@code reborn:dojutsu} côté mod (lot 5, étape B ; PLUGINS_REFERENCE §4 duodecies), ShinobiCore
 * {@code dojutsu/ByakuganService} : JSON UTF-8 brut dans les deux sens, champ {@code v} = {@value #VERSION}.
 * C → S : {@code {"a":"byakugan","on":bool}} ; S → C : {@code regles}, {@code byakugan}, {@code signes}, passés à
 * {@link ByakuganClient} sur le fil client. L'ancien {@code reborn:byakugan} de ShinobiSense ({@link ByakuganPayload}) reste
 * écouté, en repli seulement.
 */
public final class DojutsuCanal {

    public static final int VERSION = 1;
    public static final CustomPacketPayload.Type<CanalJson> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "dojutsu"));

    /** Banc : les demandes partent vers ce serveur simulé au lieu du vrai (null = réseau). */
    public static volatile Consumer<JsonObject> debugServeur;

    private DojutsuCanal() {}

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
        ByakuganClient.recevoir(o);
    }

    static void envoyer(boolean on) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("a", "byakugan");
        o.addProperty("on", on);
        Consumer<JsonObject> d = debugServeur;
        if (d != null) { d.accept(o); return; }
        if (ClientPlayNetworking.canSend(TYPE)) ClientPlayNetworking.send(new CanalJson(TYPE, o.toString()));
    }
}
