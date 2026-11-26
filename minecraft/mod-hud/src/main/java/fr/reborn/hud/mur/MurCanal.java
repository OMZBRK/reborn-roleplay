package fr.reborn.hud.mur;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.staff.CanalJson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Canal {@code reborn:mur} côté mod (marche sur les murs, lot 5, essai C0 puis étape C1a ; ShinobiAbilities
 * {@code mobility/MurTest}) : JSON UTF-8 brut dans les deux sens, champ {@code v} = {@value #VERSION}, champs inconnus
 * ignorés.
 * <ul>
 *   <li>S → C : {@code permis} (on), {@code etat} (e = identifiant d'entité, f = face ou « - », n = numéro de la demande
 *       à laquelle il répond, seulement dans l'exemplaire du joueur lui-même), {@code refus} (m, a = action refusée,
 *       n), passés à {@link MurClient} sur le fil client ;</li>
 *   <li>C → S : {@code accroche} (f, n), {@code face} (f, n : changement de face à un coin ; l'ancienne face annule),
 *       {@code lache} (c = corrections de position reçues pendant la marche). {@code n} est un numéro croissant par
 *       session, tenu par {@link MurClient}.</li>
 * </ul>
 * Sans plugin (canal jamais annoncé par le serveur), rien n'est reçu ni envoyé : la marche sur les murs reste éteinte.
 */
public final class MurCanal {

    public static final int VERSION = 1;
    public static final CustomPacketPayload.Type<CanalJson> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("reborn", "mur"));

    /** Banc : les demandes partent vers ce serveur simulé au lieu du vrai (null = réseau). */
    public static volatile Consumer<JsonObject> debugServeur;

    private MurCanal() {}

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CanalJson.codec(TYPE));
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CanalJson.codec(TYPE));
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (p, c) -> c.client().execute(() -> recevoir(p.json())));
    }

    /** Un message du serveur (fil client ; le banc y passe des messages simulés). Un message mal formé est ignoré. */
    public static void recevoir(String raw) {
        try {
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            if (!o.has("v") || o.get("v").getAsInt() != VERSION) return;
            MurClient.recevoir(o);
        } catch (RuntimeException e) {
            // message tronqué ou d'un autre format : ignoré
        }
    }

    /** Le serveur (ou le banc) peut-il recevoir une demande ? */
    static boolean disponible() {
        return debugServeur != null || ClientPlayNetworking.canSend(TYPE);
    }

    static void accroche(MurFace face, int n) {
        JsonObject o = base("accroche");
        o.addProperty("f", face.code);
        o.addProperty("n", n);
        envoyer(o);
    }

    /** Changement de face à un coin ; renvoyer l'ancienne face annule la demande précédente. */
    static void face(MurFace face, int n) {
        JsonObject o = base("face");
        o.addProperty("f", face.code);
        o.addProperty("n", n);
        envoyer(o);
    }

    static void lache(int corrections) {
        JsonObject o = base("lache");
        o.addProperty("c", corrections);
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
