package fr.reborn.hud.concentration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.staff.CanalJson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Canal {@code reborn:concentration} côté mod (lot 4, étape B ; PLUGINS_REFERENCE §4 undecies) : JSON UTF-8 brut dans
 * les deux sens, champ {@code v} = {@value #VERSION}. <b>Jamais</b> {@code reborn:plaques} (nametags du mode staff).
 * C → S : {@code on}, {@code off}, {@code lire} ; S → C : {@code etat}, {@code ancres}, {@code texte},
 * {@code texte_refus}, {@code traces} (étape C), reçus sur le fil client et passés à {@link Plaques} et {@link Traces}.
 */
public final class ConcentrationCanal {

    public static final int VERSION = 1;
    public static final CustomPacketPayload.Type<CanalJson> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "concentration"));

    /** Banc : les demandes partent vers ce serveur simulé au lieu du vrai (null = réseau). */
    public static volatile Consumer<JsonObject> debugServeur;

    private ConcentrationCanal() {}

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CanalJson.codec(TYPE));
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CanalJson.codec(TYPE));
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (p, c) -> c.client().execute(() -> recevoir(p.json())));
    }

    /** Un message du serveur (fil client ; le banc y passe des messages simulés). */
    public static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        try {
            if (!o.has("v") || o.get("v").getAsInt() != VERSION) return;
        } catch (RuntimeException e) { return; }
        Plaques.recevoir(o);
    }

    /** Le serveur parle-t-il ce canal (ou le banc le simule-t-il) ? */
    static boolean disponible() {
        return debugServeur != null || ClientPlayNetworking.canSend(TYPE);
    }

    static void envoyer(String a, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("a", a);
        if (id != null) o.addProperty("id", id);
        Consumer<JsonObject> d = debugServeur;
        if (d != null) { d.accept(o); return; }
        if (ClientPlayNetworking.canSend(TYPE)) ClientPlayNetworking.send(new CanalJson(TYPE, o.toString()));
    }
}
