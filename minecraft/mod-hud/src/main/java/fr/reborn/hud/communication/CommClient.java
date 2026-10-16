package fr.reborn.hud.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.interaction.InteractionItem;
import fr.reborn.hud.menu.RebornSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Client de la cloche du village et des volières (docs/gameplay/CLOCHE_ET_MISSIVES.md) : canal {@link CommPayload},
 * points proches (cloches que l'on peut sonner, volières) pour le <b>menu R</b>, ouverture des écrans
 * ({@link ClocheScreen}, {@link VoliereScreen}, {@link LettreScreen}), bande du haut ({@link BandeHud}), brouillons
 * gardés quand on ferme. Le serveur décide de tout ; le client affiche et demande. Aucune touche dédiée.
 */
public final class CommClient {

    /** Un point d'interaction proche : {@code k} = « cloche » ou « voliere ». */
    record Point(String k, String id, int x, int y, int z, String nom) { }

    /** Un destinataire en puce : un contact ({@code ref} = « c:uuid ») ou une adresse écrite à la main. */
    record Dest(String ref, String nom) {
        boolean adresse() { return ref == null || ref.isEmpty(); }
    }

    private static volatile List<Point> points = List.of();

    // brouillons (gardés si l'on ferme l'écran)
    static final Editeur annonce = new Editeur(200);
    static String annonceType = "normale";
    static final Editeur lettre = new Editeur(600);
    static final List<Dest> dests = new ArrayList<>();
    static String vers = "";
    static boolean signee;

    /** Dernier message court du serveur (pied des écrans). */
    static volatile String toast;
    static volatile boolean toastRefus;
    static volatile long toastLe;

    /** Banc d'essai : les demandes ne partent pas (voir {@link CommDebug}). */
    static boolean demo;

    private CommClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CommPayload.ID, CommPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CommPayload.ID, CommPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CommPayload.ID, (payload, ctx) -> ctx.client().execute(() -> recevoir(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> effacer());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "communication_bande"),
                (ctx, tick) -> BandeHud.render(ctx));
        CommDebug.init();
    }

    static void effacer() {
        points = List.of();
        BandeHud.effacer();
        toast = null;
    }

    static String str(JsonObject o, String k) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    static long num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsLong() : 0; } catch (RuntimeException e) { return 0; }
    }

    static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        Minecraft mc = Minecraft.getInstance();
        Screen ecran = mc.gui.screen();
        switch (str(o, "t")) {
            case "points" -> {
                List<Point> l = new ArrayList<>();
                for (JsonElement e : arr(o, "l")) {
                    JsonObject p = e.getAsJsonObject();
                    l.add(new Point(str(p, "k"), str(p, "id"), (int) num(p, "x"), (int) num(p, "y"), (int) num(p, "z"), str(p, "nom")));
                }
                points = l;
            }
            case "cloche" -> {
                if (ecran instanceof ClocheScreen c && c.id().equals(str(o, "id"))) c.maj(o);
                else if (bool(o, "ouvrir") && (ecran == null || ecran instanceof VoliereScreen || ecran instanceof LettreScreen))
                    mc.setScreenAndShow(new ClocheScreen(o));
            }
            case "compte" -> { if (ecran instanceof ClocheScreen c) c.compte((int) num(o, "zone"), (int) num(o, "concernes")); }
            case "annonce" -> BandeHud.annonce(str(o, "type"), str(o, "titre"), CommArt.segs(o.get("texte")), str(o, "signe"),
                    (int) Math.max(3, num(o, "s")));
            case "annonce_fin" -> BandeHud.finAnnonce();
            case "voliere" -> {
                if (ecran instanceof VoliereScreen v && v.id().equals(str(o, "id"))) v.maj(o);
                else if (bool(o, "ouvrir") && (ecran == null || ecran instanceof ClocheScreen || ecran instanceof LettreScreen))
                    mc.setScreenAndShow(new VoliereScreen(o));
            }
            case "envoye" -> {
                if (!bool(o, "transfert")) { lettre.effacer(); dests.clear(); }
                if (ecran instanceof VoliereScreen v) v.envoye(bool(o, "transfert"));
            }
            case "lettre" -> {
                if (ecran == null || ecran instanceof VoliereScreen || ecran instanceof LettreScreen) mc.setScreenAndShow(new LettreScreen(o, ecran instanceof VoliereScreen v ? v : null));
            }
            case "alerte" -> BandeHud.alerte(str(o, "k"), str(o, "lieu"), (int) Math.max(3, num(o, "s")));
            case "toast" -> toast(str(o, "m"), bool(o, "refus"));
            case "ferme" -> {
                if (ecran instanceof ClocheScreen || ecran instanceof VoliereScreen || ecran instanceof LettreScreen) ecran.onClose();
            }
            default -> { }
        }
    }

    static void toast(String m, boolean refus) {
        if (m == null || m.isBlank()) return;
        Screen s = Minecraft.getInstance().gui.screen();
        toast = m;
        toastRefus = refus;
        toastLe = System.currentTimeMillis();
        if (refus) RebornSounds.deny();
        if (!(s instanceof ClocheScreen || s instanceof VoliereScreen || s instanceof LettreScreen))
            fr.reborn.hud.notif.NotifHud.message(refus ? "refus" : "info", null, null, m);
    }

    static String toastActif() { return toast != null && System.currentTimeMillis() - toastLe < 4500 ? toast : null; }

    /* ================================================================ envois */

    static void send(JsonObject o) {
        if (demo) { CommDebug.demande(o); return; }
        if (ClientPlayNetworking.canSend(CommPayload.ID)) ClientPlayNetworking.send(new CommPayload(o.toString()));
    }

    static JsonObject a(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        return o;
    }

    /* ================================================================ menu R */

    private static Point proche(double x, double y, double z) {
        Point best = null;
        double d = 2.6 * 2.6;
        for (Point p : points) {
            double dx = p.x() + 0.5 - x, dy = p.y() + 0.5 - y, dz = p.z() + 0.5 - z;
            if (dy > -1.6 && dy < 2.8) dy = 0;
            double q = dx * dx + dy * dy + dz * dz;
            if (q <= d) { d = q; best = p; }
        }
        return best;
    }

    /**
     * Entrées du menu R visé sur un bloc ou une entité (le meuble d'une cloche ou d'une volière) : « Sonner la cloche »
     * si l'on en a le droit (sinon le serveur ne nous l'a pas envoyée), « Écrire une missive » et « Missives ici » sur
     * un perchoir. Liste vide sinon.
     */
    public static List<InteractionItem> menuR(double x, double y, double z) {
        List<InteractionItem> l = new ArrayList<>();
        Point p = proche(x, y, z);
        if (p == null) return l;
        if (p.k().equals("cloche")) {
            l.add(InteractionItem.action("Sonner la cloche", () -> ouvrir(p.id(), false)));
        } else {
            l.add(InteractionItem.action("Écrire une missive", () -> ouvrir(p.id(), false)));
            l.add(InteractionItem.action("Missives ici", () -> ouvrir(p.id(), true)));
        }
        return l;
    }

    /** Titre du menu R sur une cloche ou un perchoir (« Cloche du village », « Grande volière de Konoha »), sinon null. */
    public static String titreR(double x, double y, double z) {
        Point p = proche(x, y, z);
        if (p == null) return null;
        return p.k().equals("cloche") ? "Cloche du village" : p.nom();
    }

    static void ouvrir(String id, boolean missives) {
        JsonObject o = a("ouvrir");
        o.addProperty("id", id);
        if (missives) o.addProperty("missives", true);
        send(o);
    }

    /* ================================================================ banc */

    static void debugPoints(List<Point> l) { points = l; }
}
