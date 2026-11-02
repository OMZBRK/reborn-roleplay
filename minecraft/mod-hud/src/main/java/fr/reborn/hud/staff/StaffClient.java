package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.keybind.RebornKeyCategory;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Client du Poste de garde (panel staff) : canal {@link StaffPayload}, touche F7, dernier état reçu (instantané,
 * fiche, Infirmerie, réponse du journal universel, notes par N°). Les effets en jeu (annonces, écran noir, séisme,
 * nametags staff) sont dans {@link EffetsStaff} et {@link NametagsStaff}. Le serveur décide de tout (grade, actions
 * possibles) ; le client affiche et envoie des demandes. Protocole : {@code docs/technique/PLUGINS_REFERENCE.md} §4 bis.
 */
public final class StaffClient {

    public record Toast(String text, long at) { }

    private static volatile JsonObject snapshot;
    private static volatile JsonObject profile;
    private static volatile JsonObject koSnapshot;
    private static volatile JsonObject journal;
    private static volatile JsonObject cherche;
    /** Dernière réponse {@code persos} (fiche › Personnages, 15/10). */
    private static volatile JsonObject persos;
    /** Dernier état {@code narrations} (outil « Narration », lot 2) et son heure d'arrivée (le « reste » en part). */
    private static volatile JsonObject narrations;
    private static volatile long narrationsA;
    /** Son des nouvelles alertes quand le Poste de garde est ouvert (cloche de l'onglet Alertes). */
    static boolean cloche = true;
    /** Modes du joueur local (message {@code mode} du serveur) : indicateur discret en jeu. */
    static volatile boolean modeStaff, modeAnim;
    private static final java.util.Set<String> ALERTES_VUES = new java.util.HashSet<>();
    private static final Map<String, JsonObject> NOTES = new HashMap<>();
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static KeyMapping key;

    /** Banc d'essai : les demandes partent vers {@link StaffDemo} (serveur simulé conforme au protocole). */
    static boolean demo;

    private StaffClient() {}

    /** Mode staff ou mode animation actif (rappel « Poste de garde [F7] » du bas-droite, lot 2). */
    public static boolean modeActif() { return modeStaff || modeAnim; }

    /** Touche du Poste de garde (F7 par défaut), null avant l'initialisation. */
    public static KeyMapping touche() { return key; }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(StaffPayload.ID, StaffPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StaffPayload.ID, StaffPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(StaffPayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> receive(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            snapshot = null; profile = null; journal = null; cherche = null; narrations = null;
            modeStaff = false; modeAnim = false;
            ficheDemandee = null; actionDemandee = null; actionEnAttente = null; horsGarde = 0;
            synchronized (ALERTES_VUES) { ALERTES_VUES.clear(); }
            synchronized (NOTES) { NOTES.clear(); }
        });
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.reborn-hud.staff_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, RebornKeyCategory.REBORN));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                if (client.gui.screen() == null) send("open");
            }
        });
        EffetsStaff.init();
        NametagsStaff.init();
    }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        switch (t) {
            case "open" -> {
                GardeScreen g;
                if (mc.gui.screen() instanceof GardeScreen cur) { g = cur; if (g.onKo()) g.show("alerts"); }
                else mc.setScreenAndShow(g = new GardeScreen("alerts"));
                String fiche = ficheDemandee, action = actionDemandee;
                ficheDemandee = null;
                actionDemandee = null;
                // l'action attend la fiche complète de ce joueur (réponse « profile ») ; une ouverture sans fiche l'oublie
                actionEnAttente = fiche != null ? action : null;
                actionPour = fiche;
                if (fiche != null) g.openProfile(fiche);
            }
            case "snap" -> {
                nouvellesAlertes(o, mc);
                snapshot = o;
                if (o.has("modeStaff")) modeStaff = o.get("modeStaff").getAsBoolean();
                if (o.has("modeAnim")) modeAnim = o.get("modeAnim").getAsBoolean();
            }
            case "cherche" -> cherche = o;
            case "mode" -> {
                modeStaff = o.has("staff") && o.get("staff").getAsBoolean();
                modeAnim = o.has("anim") && o.get("anim").getAsBoolean();
            }
            case "open_ko" -> {
                if (mc.gui.screen() instanceof GardeScreen g) g.show("ko");
                else mc.setScreenAndShow(new GardeScreen("ko"));
            }
            case "ko_snap" -> koSnapshot = o;
            case "close" -> {
                if (mc.gui.screen() instanceof StaffScreen) mc.setScreenAndShow(null);
            }
            case "profile" -> {
                profile = o;
                lancerEnAttente(o, mc);
            }
            case "persos" -> persos = o;
            case "narrations" -> { narrations = o; narrationsA = System.currentTimeMillis(); }
            case "journal" -> journal = o;
            case "notes" -> {
                String pid = o.has("pid") ? o.get("pid").getAsString() : "";
                synchronized (NOTES) { NOTES.put(normPid(pid), o); }
            }
            case "toast" -> {
                String m = o.get("m").getAsString();
                toast(m);
                // réponse à une action du menu R, Poste de garde fermé : dans la pile de notifications, en bas à droite
                if (!(mc.gui.screen() instanceof StaffScreen) && System.currentTimeMillis() - horsGarde < 5000)
                    fr.reborn.hud.notif.NotifHud.message("info", null, null, m);
            }
            default -> { }
        }
    }

    /** Lance l'action demandée avec la fiche (menu R) quand la fiche de ce joueur arrive, une seule fois. */
    private static void lancerEnAttente(JsonObject p, Minecraft mc) {
        String a = actionEnAttente;
        if (a == null || !StaffScreen.str(p, "uuid").equals(actionPour)) return;
        actionEnAttente = null;
        // le staff a pu quitter la fiche avant la réponse : rien n'est lancé
        if (mc.gui.screen() instanceof StaffScreen s && "players".equals(s.tab) && actionPour.equals(s.profileUuid))
            GardeActions.lancerDemandee(s, a, p);
    }

    /** Petit son quand une alerte nouvelle arrive alors que le Poste de garde est ouvert. */
    private static void nouvellesAlertes(JsonObject o, Minecraft mc) {
        boolean neuve = false;
        synchronized (ALERTES_VUES) {
            boolean premier = snapshot == null;
            if (o.has("alerts") && o.get("alerts").isJsonArray()) for (var e : o.getAsJsonArray("alerts")) {
                try {
                    String id = e.getAsJsonObject().get("id").getAsString();
                    if (ALERTES_VUES.add(id) && !premier) neuve = true;
                } catch (RuntimeException ignored) { }
            }
        }
        if (neuve && cloche && !demo && mc.gui.screen() instanceof StaffScreen) fr.reborn.hud.menu.RebornSounds.playReborn("stats.light", 1.3f, 0.5f);
    }

    /** Dernière réponse {@code cherche} (recherche universelle, hors ligne compris). */
    public static JsonObject cherche() { return cherche; }

    /** Banc d'essai : injecte un message comme s'il venait du serveur. */
    static void debugReceive(String raw) { receive(raw); }

    public static void toast(String text) {
        synchronized (TOASTS) {
            TOASTS.addLast(new Toast(text, System.currentTimeMillis()));
            while (TOASTS.size() > 3) TOASTS.pollFirst();
        }
    }

    static void debugEffacerToasts() { synchronized (TOASTS) { TOASTS.clear(); } }

    public static Toast[] toasts() {
        synchronized (TOASTS) {
            long now = System.currentTimeMillis();
            TOASTS.removeIf(t -> now - t.at() > 3500);
            return TOASTS.toArray(new Toast[0]);
        }
    }

    public static JsonObject snapshot() { return snapshot; }

    public static JsonObject profile() { return profile; }

    /** Personnages du compte {@code uuid} (null tant que la réponse {@code persos} n'est pas arrivée). */
    public static JsonObject persos(String uuid) {
        JsonObject p = persos;
        return p != null && uuid != null && p.has("uuid") && uuid.equals(p.get("uuid").getAsString()) ? p : null;
    }

    public static JsonObject koSnapshot() { return koSnapshot; }

    /**
     * Dernier état {@code narrations} (outil « Narration » : actives, la plus récente d'abord ; PLUGINS_REFERENCE
     * §4 bis.2), null tant qu'il n'est pas arrivé.
     */
    static JsonObject narrations() { return narrations; }

    /** Heure d'arrivée (ms, horloge murale) du dernier état {@code narrations}. */
    static long narrationsA() { return narrationsA; }

    /** Dernière réponse {@code journal} reçue (null si le serveur ne la gère pas encore). */
    public static JsonObject journal() { return journal; }

    /** Notes staff reçues pour ce N° (null tant que la réponse {@code notes} n'est pas arrivée). */
    public static JsonObject notes(String pid) {
        synchronized (NOTES) { return NOTES.get(normPid(pid)); }
    }

    public static void clearProfile() { profile = null; }

    /** « #00042 », « 42 », « 00042 » → « 42 » (clé des notes). */
    static String normPid(String pid) {
        if (pid == null) return "";
        String s = pid.trim();
        if (s.startsWith("#")) s = s.substring(1);
        s = s.replaceFirst("^0+(?=.)", "");
        return s;
    }

    /** Fiche demandée depuis le menu R (« Staff › Fiche »), ouverte dès que le Poste de garde s'ouvre. */
    private static volatile String ficheDemandee;
    /**
     * Action demandée avec la fiche (menu R › Staff › « Couper la voix… », 31/10) : id du catalogue de
     * {@link GardeActions}, mise en attente à l'ouverture puis lancée à l'arrivée de la fiche de {@code actionPour}.
     */
    private static volatile String actionDemandee, actionEnAttente, actionPour;
    /** Dernière action envoyée du menu R (Poste de garde fermé) : sa réponse {@code toast} reste visible en jeu. */
    private static volatile long horsGarde;

    /** Ouvre le Poste de garde sur la fiche de ce joueur (le serveur revérifie le grade à l'ouverture). */
    public static void ouvrirFiche(java.util.UUID id) { ouvrirFiche(id, null); }

    /**
     * Idem, puis lance l'action {@code action} (id du catalogue, ex. {@code "voix"} : le formulaire « Couper la voix »)
     * dès que sa fiche arrive ; null = la fiche seule.
     */
    public static void ouvrirFiche(java.util.UUID id, String action) {
        ficheDemandee = id.toString();
        actionDemandee = action;
        send("open");
    }

    /**
     * Action directe sur un joueur depuis le menu R (« Staff › Rétablir la voix ») : {@code act} par UUID, sans ouvrir
     * le Poste de garde ; le serveur revérifie le grade et répond d'un mot repris dans la pile de notifications.
     */
    public static void agir(java.util.UUID id, String op) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "act");
        o.addProperty("op", op);
        o.addProperty("t", id.toString());
        horsGarde = System.currentTimeMillis();
        send(o);
    }

    /**
     * La voix de ce joueur est-elle coupée par le staff (menu R › Staff) ? Plasmo Voice d'abord, diffusé en direct à
     * tous les clients ({@link VoiceState#MICRO_MUET}) ; sans réponse franche de Plasmo (pas de voix chez soi, joueur
     * sans Plasmo), la dernière fiche ou liste reçue du Poste de garde, si la coupure n'est pas échue.
     */
    public static boolean voixCoupee(java.util.UUID id) {
        int m = VoiceState.micro(id);
        if (m == VoiceState.MICRO_MUET) return true;
        if (m == VoiceState.MICRO_ACTIF || m == VoiceState.MICRO_COUPE) return false;
        JsonObject p = connu(id.toString());
        if (!GardeActions.voixCoupee(p)) return false;
        long fin = StaffScreen.lng(p, "voixFin");
        return fin <= 0 || fin > System.currentTimeMillis();
    }

    /** Dernière fiche, sinon ligne de la liste, de ce joueur (null si le Poste de garde ne l'a pas vu). */
    private static JsonObject connu(String uuid) {
        JsonObject p = profile;
        if (p != null && uuid.equals(StaffScreen.str(p, "uuid"))) return p;
        for (var e : StaffScreen.arr(snapshot, "players")) {
            if (e.isJsonObject() && uuid.equals(StaffScreen.str(e.getAsJsonObject(), "uuid"))) return e.getAsJsonObject();
        }
        return null;
    }

    public static void send(String action) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        send(o);
    }

    public static void send(JsonObject o) {
        if (demo) { StaffDemo.handle(o); return; }
        if (ClientPlayNetworking.canSend(StaffPayload.ID)) ClientPlayNetworking.send(new StaffPayload(o.toString()));
    }
}
