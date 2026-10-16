package fr.reborn.hud.faction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.interaction.InteractionItem;
import fr.reborn.hud.interaction.InteractionMenus;
import fr.reborn.hud.menu.RebornSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client du système Faction (FACTION.md v3) : canal {@link FactionPayload}, dernier état reçu (vue filtrée du volet,
 * personnes proches, vue staff, état court pour le menu R), invitation en attente (Entrée / Retour), toasts. Le
 * serveur décide de tout et ne nous envoie que ce qu'on a le droit de voir ; le client affiche et demande.
 * Touche <b>J</b> ({@code HudKeybinds.FACTION}) : {@link #appuiJ}. Protocole : PLUGINS_REFERENCE §4 septies.
 */
public final class FactionClient {

    public record Toast(String text, boolean refus, long at) { }

    /** Invitation reçue : qui (nom appris, ou « Quelqu'un »), dans quelle section, depuis quand, combien de secondes. */
    public record Invit(String de, String section, String insigne, long at, int secs, boolean panneau) {
        public float reste(long now) { return secs - (now - at) / 1000f; }
    }

    private static volatile JsonObject vue, proches, staff, etat;
    private static volatile Invit invit;
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();

    /** Banc d'essai : les demandes partent vers {@link FactionDemo} (serveur simulé conforme au protocole). */
    static boolean demo;

    private FactionClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(FactionPayload.ID, FactionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FactionPayload.ID, FactionPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FactionPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> recevoir(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> effacer());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "faction_invit"),
                (ctx, tick) -> FactionHud.render(ctx));
        FactionDebug.init();
    }

    static void effacer() {
        vue = null; proches = null; staff = null; etat = null; invit = null;
        synchronized (TOASTS) { TOASTS.clear(); }
    }

    static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        switch (t) {
            case "vue" -> {
                vue = o;
                if (o.has("ouvrir") && o.get("ouvrir").getAsBoolean() && !(mc.gui.screen() instanceof FactionScreen)) {
                    if (mc.gui.screen() == null) mc.setScreenAndShow(new FactionScreen());
                }
            }
            case "proches" -> proches = o;
            case "staff" -> staff = o;
            case "etat" -> etat = o;
            case "toast" -> toast(str(o, "m"), o.has("refus") && o.get("refus").getAsBoolean());
            case "invit" -> {
                invit = new Invit(str(o, "de"), str(o, "section"), str(o, "insigne"), System.currentTimeMillis(),
                        o.has("s") ? o.get("s").getAsInt() : 10, o.has("panneau") && o.get("panneau").getAsBoolean());
                RebornSounds.playReborn("stats.light", 1.2f, 0.45f);
            }
            case "invit_fin" -> invit = null;
            default -> { }
        }
    }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    /* ================================================================ accès */

    public static JsonObject vue() { return vue; }

    public static JsonObject proches() { return proches; }

    /** Vue staff (Poste de garde › Outils › Factions), null tant qu'elle n'est pas arrivée. */
    public static JsonObject staff() { return staff; }

    /** Invitation en attente, ou null (expirée = effacée). */
    public static Invit invit() {
        Invit i = invit;
        if (i != null && i.reste(System.currentTimeMillis()) <= 0) invit = null;
        return invit;
    }

    public static void toast(String text) { toast(text, false); }

    public static void toast(String text, boolean refus) {
        if (text == null || text.isBlank()) return;
        synchronized (TOASTS) {
            TOASTS.addLast(new Toast(text, refus, System.currentTimeMillis()));
            while (TOASTS.size() > 3) TOASTS.pollFirst();
        }
        if (refus) RebornSounds.deny();
    }

    public static Toast[] toasts() {
        synchronized (TOASTS) {
            long now = System.currentTimeMillis();
            TOASTS.removeIf(t -> now - t.at() > 3500);
            return TOASTS.toArray(new Toast[0]);
        }
    }

    static void effacerToasts() { synchronized (TOASTS) { TOASTS.clear(); } }

    /* ================================================================ envois */

    public static boolean serveur() { return demo || ClientPlayNetworking.canSend(FactionPayload.ID); }

    public static void send(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        send(o);
    }

    public static void send(JsonObject o) {
        if (demo) { FactionDemo.handle(o); return; }
        if (ClientPlayNetworking.canSend(FactionPayload.ID)) ClientPlayNetworking.send(new FactionPayload(o.toString()));
    }

    /** Touche J : ouvre le volet (le serveur répond avec la vue) ; la même touche le referme (dans l'écran). */
    public static void appuiJ() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        if (serveur()) send("open");
        else InteractionMenus.info("Le volet Faction s'ouvre sur le serveur Reborn.");
    }

    /** Entrée : accepte l'invitation en attente (true si elle a été prise). */
    public static boolean accepter() {
        if (invit() == null) return false;
        JsonObject o = new JsonObject();
        o.addProperty("a", "repondre");
        o.addProperty("oui", true);
        send(o);
        invit = null;
        RebornSounds.confirm();
        return true;
    }

    /** Retour : refuse l'invitation en attente (true si elle a été prise). */
    public static boolean refuser() {
        if (invit() == null) return false;
        JsonObject o = new JsonObject();
        o.addProperty("a", "repondre");
        o.addProperty("oui", false);
        send(o);
        invit = null;
        RebornSounds.uiClick();
        return true;
    }

    /**
     * Entrée du menu R sur un joueur : « Inviter dans la Police » si notre grade de section le permet (le serveur
     * revérifie : même village, sans section, 3 blocs, places libres) ; grisée avec la raison si la section est pleine ;
     * rien sinon.
     */
    public static InteractionItem menuR(String name) {
        JsonObject e = etat;
        if (e == null || !e.has("section")) return null;
        String sec = str(e, "section");
        String org = str(e, "organisation");
        String lib = "Inviter dans " + (org.isEmpty() ? article(sec) : org);
        boolean peut = e.has("inviter") && e.get("inviter").getAsBoolean();
        if (!peut) {
            String r = str(e, "refus");
            return r.isEmpty() ? null : InteractionItem.disabled(lib, r.toLowerCase(java.util.Locale.ROOT).startsWith("la section est pleine") ? "section pleine" : "impossible");
        }
        return InteractionItem.action(lib, () -> InteractionMenus.sendCommand("faction inviter " + name));
    }

    /** « la Police », « le Corps médical », « l'Académie », « l'ANBU ». */
    static String article(String nom) {
        if (nom == null || nom.isEmpty()) return "la section";
        String n = nom.toLowerCase(java.util.Locale.ROOT);
        if (n.equals("anbu") || "aeiouyéèêàâîïôûh".indexOf(n.charAt(0)) >= 0) return "l'" + nom;
        if (n.startsWith("corps") || n.startsWith("bureau") || n.startsWith("conseil")) return "le " + nom;
        return "la " + nom;
    }

    /* ================================================================ banc */

    static void debugEtat(JsonObject e) { etat = e; }
}
