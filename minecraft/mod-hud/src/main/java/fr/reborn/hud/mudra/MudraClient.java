package fr.reborn.hud.mudra;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.tirage.TirageAnimations;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * <b>Mudras au clavier</b> (14/11, demande du propriétaire : « le principe de mudra […] que ce soit pour ouvrir les portes
 * ou encore pour les apprentissages de techniques »). Une barre de douze signes, composés sur la <b>rangée des
 * chiffres</b> (1 à 9, 0 et les deux touches à sa droite), en temps compté.
 *
 * <p><b>Le serveur décide de tout</b> (ShinobiCore {@code mudra/}, canal {@code reborn:mudra}) : qui peut ouvrir la barre,
 * combien de temps elle dure, si la suite ouvre une porte scellée. Le mod affiche, capte les touches et envoie chaque
 * signe ; il ne sait jamais s'il y a une porte près de lui ni si un signe est le bon (mode libre). En mode guidé
 * (apprentissage d'une technique), le serveur donne la suite : le signe attendu est mis en avant.
 *
 * <p>La touche (U par défaut) demande l'ouverture ; un second appui ou Échap referme. Tant que la barre est ouverte et
 * qu'aucun écran ne l'est, la rangée des chiffres ne change plus l'objet tenu ({@code mixin/KeyboardMudraMixin}). Chaque
 * signe joue un claquement de mains et joint les mains du personnage ({@code animations/mudra_signe.json}, bras seuls,
 * pose provisoire, la même pour les douze) ; les
 * proches le voient et l'entendent (message {@code geste}, son joué par le serveur). Le système est livré éteint côté
 * serveur : sans règles reçues, la touche ne fait rien.
 */
public final class MudraClient {

    /** Les douze signes, dans l'ordre du zodiaque = ordre de la barre = ordinal de l'enum {@code Mudra} du serveur. */
    public static final String[] NOMS = {"Rat", "Buffle", "Tigre", "Lièvre", "Dragon", "Serpent", "Cheval", "Bélier", "Singe",
        "Coq", "Chien", "Sanglier"};
    /** Rangée des chiffres, par position (AZERTY compris : GLFW nomme les touches par leur place). */
    public static final int[] TOUCHES = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
        GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS,
        GLFW.GLFW_KEY_EQUAL};
    public static final int N = 12;

    /**
     * Écart minimal entre deux signes : celui du serveur ({@code signe-min-ms}, 120 ms, compté en moyenne avec 500 ms de
     * marge et relevé à l'arrivée du message). Plus strict (200 ms au 14/11), le mod avalait un signe tapé vite que le
     * serveur aurait accepté, et le suivant était jugé au mauvais rang (relecture du 15/11).
     */
    static final long ECART_MS = 120L;
    /** Une demande sans réponse est oubliée après ce délai. */
    private static final long ATTENTE_MAX_MS = 2000L;
    /** Sans {@code fin} du serveur, la barre se referme seule ce délai après l'échéance. */
    private static final long SECOURS_MS = 1200L;
    /** Les mains restent jointes ce temps après le dernier signe. */
    private static final long POSE_MS = 650L;
    static final long MESSAGE_MS = 1800L;

    /** Horloge (le banc la fige). */
    public static volatile LongSupplier horloge = Util::getMillis;

    private static boolean systeme;
    private static boolean attend;
    private static long demandeA;

    /* session en cours */
    private static boolean ouverte, guide;
    private static int session, max, fait;
    private static long temps, gain, ouverteA, echeance, dernierSigne;
    private static int[] suite = new int[0];
    private static String titre = "";
    private static final long[] APPUI = new long[N];

    /* dernière fin (fondu de sortie) et message court */
    private static long finA = Long.MIN_VALUE;
    private static boolean finOk;
    private static String message = "";
    private static long messageA = Long.MIN_VALUE;
    private static boolean messageRefus;

    /* mains jointes : soi et les proches (identifiant d'entité → fin de la pose) */
    private static long poseSoi = Long.MIN_VALUE;
    private static final Map<Integer, Long> POSES = new HashMap<>();

    private MudraClient() {}

    public static void init() {
        MudraCanal.init();
        ClientTickEvents.END_CLIENT_TICK.register(MudraClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(MudraClient::clear));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "mudras"),
            (ctx, tickCounter) -> MudraHud.render(ctx));
    }

    /* ================================================================ lecture (barre, mixin, banc) */

    public static boolean systeme() { return systeme; }
    public static boolean ouverte() { return ouverte; }
    static boolean guide() { return guide; }
    static int fait() { return fait; }
    static int[] suite() { return suite; }
    static String titre() { return titre; }
    static long ouverteA() { return ouverteA; }
    static long finA() { return finA; }
    static boolean finOk() { return finOk; }
    static long appui(int i) { return APPUI[i]; }
    static String message() { return message; }
    static long messageA() { return messageA; }
    static boolean messageRefus() { return messageRefus; }

    /** Le signe attendu en mode guidé, ou −1. */
    static int attendu() { return ouverte && guide && fait < suite.length ? suite[fait] : -1; }

    /** Part de temps restante (0 à 1) à l'instant donné. */
    static float reste(long now) {
        if (temps <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (echeance - now) / (float) temps));
    }

    /** La rangée des chiffres est-elle prise par la barre ? (ouverte, aucun écran) */
    public static boolean capte() {
        Minecraft mc = Minecraft.getInstance();
        return ouverte && mc.player != null && mc.gui.screen() == null;
    }

    /** Rang (0 à 11) d'une touche de la rangée des chiffres, ou −1. */
    public static int indexTouche(int glfw) {
        for (int i = 0; i < N; i++) if (TOUCHES[i] == glfw) return i;
        return -1;
    }

    /* ================================================================ gestes du joueur */

    /** La touche des mudras : demande l'ouverture, ou referme. */
    public static void basculer() {
        if (ouverte) { fermer(); return; }
        long now = horloge.getAsLong();
        if (!systeme || attend || !MudraCanal.disponible()) return;
        attend = true;
        demandeA = now;
        MudraCanal.ouvrir();
    }

    /** Un signe (rang 0 à 11) : montré et joué tout de suite, envoyé au serveur, qui tranche. */
    public static void signe(int s) {
        if (!ouverte || s < 0 || s >= N) return;
        long now = horloge.getAsLong();
        if (now - dernierSigne < ECART_MS || now > echeance || fait >= max) return;
        dernierSigne = now;
        APPUI[s] = now;
        if (guide) {
            // le bon signe avance la suite et rend tout le temps ; un mauvais : le serveur referme (fin « rate »)
            if (fait < suite.length && suite[fait] == s) {
                fait++;
                echeance = now + temps;
            }
        } else {
            fait++;
            echeance = Math.min(now + temps, echeance + gain);
        }
        MudraCanal.signe(session, s);
        RebornSounds.playReborn("mudra.signe", 0.96f + 0.02f * (s % 5), 0.85f);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            TirageAnimations.INSTANCE.playSigne(mc.player);
            poseSoi = now + POSE_MS;
        }
    }

    /** Referme la barre (touche, Échap). */
    public static void fermer() {
        if (!ouverte) return;
        MudraCanal.fermer(session);
        finir(false, "");
    }

    /* ================================================================ messages du serveur */

    static void recevoir(JsonObject o) {
        String t = o.has("t") ? o.get("t").getAsString() : "";
        long now = horloge.getAsLong();
        try {
            switch (t) {
                case "regles" -> {
                    systeme = o.has("actif") && o.get("actif").getAsBoolean();
                    if (systeme) MudraHud.preparer();
                    if (!systeme && ouverte) finir(false, "");
                }
                case "ouvre" -> {
                    attend = false;
                    session = o.get("n").getAsInt();
                    guide = o.has("mode") && "guide".equals(o.get("mode").getAsString());
                    temps = Math.max(500L, o.get("temps").getAsLong());
                    gain = o.has("gain") ? Math.max(0L, o.get("gain").getAsLong()) : 0L;
                    max = o.has("max") ? Math.max(1, Math.min(N, o.get("max").getAsInt())) : N;
                    int[] s = new int[0];
                    if (guide && o.has("suite") && o.get("suite").isJsonArray()) {
                        JsonArray a = o.getAsJsonArray("suite");
                        s = new int[Math.min(N, a.size())];
                        for (int i = 0; i < s.length; i++) s[i] = Math.max(0, Math.min(N - 1, a.get(i).getAsInt()));
                    }
                    suite = s;
                    if (guide) max = Math.max(1, s.length);
                    titre = o.has("titre") ? o.get("titre").getAsString() : "";
                    fait = 0;
                    ouverte = true;
                    ouverteA = now;
                    echeance = now + temps;
                    dernierSigne = Long.MIN_VALUE / 2;
                    finA = Long.MIN_VALUE;
                    java.util.Arrays.fill(APPUI, Long.MIN_VALUE / 2);
                    RebornSounds.playReborn("mudra.barre", 1f, 0.6f);
                }
                case "fin" -> {
                    if (!ouverte || (o.has("n") && o.get("n").getAsInt() != session)) return;
                    String res = o.has("res") ? o.get("res").getAsString() : "";
                    boolean ok = "ok".equals(res);
                    finir(ok, o.has("m") ? o.get("m").getAsString() : "");
                    // une suite libre qui n'ouvre rien se dissipe en silence ; l'échec ne sonne qu'à l'apprentissage
                    boolean guidee = guide;
                    if (ok) RebornSounds.playReborn("mudra.reussite", 1f, 0.8f);
                    else if (guidee && ("rate".equals(res) || "temps".equals(res))) RebornSounds.playReborn("mudra.echec", 1f, 0.6f);
                }
                case "refus" -> {
                    attend = false;
                    String m = o.has("m") ? o.get("m").getAsString() : "";
                    if (!m.isEmpty()) {
                        dire(m, true, now);
                        RebornSounds.playReborn("mudra.echec", 1f, 0.5f);
                    }
                }
                case "geste" -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null) return;
                    int id = o.get("e").getAsInt();
                    Entity e = mc.level.getEntity(id);
                    if (e instanceof AbstractClientPlayer p && p != mc.player) {
                        TirageAnimations.INSTANCE.playSigne(p);
                        POSES.put(id, now + POSE_MS);
                    }
                }
                default -> { }
            }
        } catch (RuntimeException e) {
            // message mal formé : ignoré
        }
    }

    private static void finir(boolean ok, String m) {
        long now = horloge.getAsLong();
        ouverte = false;
        attend = false;
        finA = now;
        finOk = ok;
        if (m != null && !m.isEmpty()) dire(m, false, now);
    }

    private static void dire(String m, boolean refus, long now) {
        message = m.length() > 64 ? m.substring(0, 64) : m;
        messageA = now;
        messageRefus = refus;
    }

    /* ================================================================ tick */

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        long now = horloge.getAsLong();
        if (HudKeybinds.MUDRAS != null && mc.gui.screen() == null) {
            while (HudKeybinds.MUDRAS.consumeClick()) basculer();
        }
        if (attend && now - demandeA >= ATTENTE_MAX_MS) attend = false;
        // le serveur finit la session ; s'il se tait, la barre ne reste pas ouverte
        if (ouverte && now > echeance + SECOURS_MS) finir(false, "");
        if (poseSoi != Long.MIN_VALUE && now >= poseSoi) {
            poseSoi = Long.MIN_VALUE;
            TirageAnimations.INSTANCE.stopSigne(mc.player);
        }
        if (!POSES.isEmpty()) {
            Iterator<Map.Entry<Integer, Long>> it = POSES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> e = it.next();
                if (now < e.getValue()) continue;
                it.remove();
                if (mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer p) TirageAnimations.INSTANCE.stopSigne(p);
            }
        }
    }

    /** Déconnexion : tout est oublié (les règles reviennent à la connexion suivante). */
    public static void clear() {
        systeme = false;
        attend = false;
        ouverte = false;
        finA = Long.MIN_VALUE;
        messageA = Long.MIN_VALUE;
        message = "";
        poseSoi = Long.MIN_VALUE;
        POSES.clear();
    }
}
