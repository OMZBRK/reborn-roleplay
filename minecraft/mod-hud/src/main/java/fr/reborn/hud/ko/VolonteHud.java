package fr.reborn.hud.ko;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.lwjgl.glfw.GLFW;

/**
 * KO-5 — QTE « Volonté », côté client. À terre, la touche Saut demande au serveur de tenter la relève. Le serveur
 * envoie la séquence (instants, touches, fenêtre) ; un cercle se referme sur chaque repère (losange d'or, plus de
 * signe kanji depuis le 18/10), il faut frapper la touche
 * affichée (Espace, clic gauche, clic droit) au moment où il se pose. Les appuis horodatés repartent au serveur,
 * qui tranche. Lecture des touches directement au clavier/souris (indépendante des actions du jeu).
 */
public final class VolonteHud {

    private static final String[] KEY_LABEL = {"Espace", "Clic gauche", "Clic droit"};
    private static final long APPROACH = 750;

    private static boolean active, ended, success;
    private static int[] keys = new int[0];
    private static long[] times = new long[0];
    private static int window, step, left = 2;
    private static long startAt, endAt, lastAsk;
    private static final JsonArray presses = new JsonArray();
    private static final boolean[] wasDown = new boolean[3];
    private static String deny = "";
    /** Le serveur propose la relève pour cette chute (Volonté facultative : technique apprise). */
    private static boolean available;
    private static long denyAt;

    private VolonteHud() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(VolontePayload.ID, VolontePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VolontePayload.ID, VolontePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(VolontePayload.ID,
                (payload, ctx) -> ctx.client().execute(() -> receive(payload.json())));
    }

    private static void send(JsonObject o) {
        if (ClientPlayNetworking.canSend(VolontePayload.ID)) ClientPlayNetworking.send(new VolontePayload(o.toString()));
    }

    private static void receive(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String t = o.has("t") ? o.get("t").getAsString() : "";
        switch (t) {
            case "start" -> {
                JsonArray ks = o.getAsJsonArray("keys"), ts = o.getAsJsonArray("times");
                keys = new int[ks.size()];
                times = new long[ts.size()];
                for (int i = 0; i < keys.length; i++) { keys[i] = ks.get(i).getAsInt(); times[i] = ts.get(i).getAsLong(); }
                window = o.get("win").getAsInt();
                left = o.has("left") ? o.get("left").getAsInt() : left;
                step = 0;
                presses.asList().clear();
                active = true;
                ended = false;
                startAt = System.currentTimeMillis();
                play(SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
            }
            case "ok" -> { success = true; ended = true; endAt = System.currentTimeMillis(); active = false;
                left = o.has("left") ? o.get("left").getAsInt() : left;
                play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.3f); }
            case "fail" -> { success = false; ended = true; endAt = System.currentTimeMillis(); active = false;
                play(SoundEvents.GLASS_BREAK, 0.8f, 0.6f); }
            case "avail" -> { available = true; left = o.has("left") ? o.get("left").getAsInt() : left; }
            case "deny" -> { deny = o.has("m") ? o.get("m").getAsString() : ""; denyAt = System.currentTimeMillis(); }
            default -> { }
        }
    }

    private static void play(net.minecraft.sounds.SoundEvent e, float vol, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    private static void play(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> e, float vol, float pitch) {
        play(e.value(), vol, pitch);
    }

    /** Touche (0 Espace, 1 clic gauche, 2 clic droit) enfoncée à l'instant. */
    private static boolean down(Minecraft mc, int k) {
        long win = mc.getWindow().handle();
        return switch (k) {
            case 0 -> GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
            case 1 -> GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            default -> GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        };
    }

    /** Lecture des appuis, à chaque image (plus fin que le tick). */
    private static void poll(Minecraft mc) {
        if (mc.gui.screen() != null) return;
        KoPayload s = KoClient.state();
        boolean downed = s != null && s.phase() == 1;
        if (!downed && !active) available = false;
        downed = downed && available;
        long now = System.currentTimeMillis();
        for (int k = 0; k < 3; k++) {
            boolean d = down(mc, k);
            boolean pressed = d && !wasDown[k];
            wasDown[k] = d;
            if (!pressed) continue;
            if (!active) {
                if (downed && k == 0 && now - lastAsk > 1500 && !(ended && now - endAt < 1500)) {
                    lastAsk = now;
                    JsonObject o = new JsonObject();
                    o.addProperty("a", "start");
                    send(o);
                }
                continue;
            }
            if (step >= keys.length) continue;
            long ms = now - startAt;
            JsonArray pr = new JsonArray();
            pr.add(k);
            pr.add(ms);
            presses.add(pr);
            boolean good = k == keys[step] && Math.abs(ms - times[step]) <= window;
            step++;
            play(SoundEvents.NOTE_BLOCK_HAT, 0.7f, good ? 1.6f : 0.5f);
            if (!good || step >= keys.length) finish();
        }
        // sceau manqué : trop tard
        if (active && step < keys.length && now - startAt > times[step] + window + 120) {
            step = keys.length;
            finish();
        }
    }

    private static void finish() {
        JsonObject o = new JsonObject();
        o.addProperty("a", "result");
        o.add("p", presses.deepCopy());
        send(o);
        active = false;
    }

    public static void clear() { active = false; ended = false; available = false; }

    /** La relève est proposée pour la chute en cours. */
    public static boolean available() { return available; }

    /** Banc d'essai (FeaturePreview) : rejoue un message serveur. */
    public static void debugReceive(String raw) { receive(raw); }

    /** Banc d'essai : se place au sceau {@code s}, {@code before} ms avant son instant. */
    public static void debugAt(int s, long before) {
        step = s;
        startAt = System.currentTimeMillis() - (times[s] - before);
    }

    /* ----------------------------------------------------------------- rendu */

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        poll(mc);
        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        KoPayload s = KoClient.state();
        boolean downed = s != null && s.phase() == 1 && available;

        boolean denying = !deny.isEmpty() && now - denyAt < 2600;
        int hintY = (KoHud.dernierYATerre() > 0 ? KoHud.dernierYATerre() : Math.round(h * 0.60f)) - 16;
        if (denying) {
            fr.reborn.hud.ui.DaKit.inter8(g, font, fr.reborn.hud.ui.DaKit.inter(deny), w / 2f, hintY + 4,
                fr.reborn.hud.ui.DaKit.ROUGE_CLAIR, 1);
        } else if (downed && !active && !(ended && now - endAt < 1600)) {
            String t = "Espace — se relever" + (left < 2 ? "  ·  " + left + " restante" + (left > 1 ? "s" : "") : "");
            float pulse = 0.55f + 0.45f * (float) Math.sin(now / 260.0);
            fr.reborn.hud.ui.DaKit.inter9o(g, font, fr.reborn.hud.ui.DaKit.inter(t), w / 2f, hintY + 4,
                a(fr.reborn.hud.ui.DaKit.OR_CLAIR, pulse), 1);
        }
        if (active) drawQte(g, mc, font, w, h, now);
        if (ended && now - endAt < 700) drawEnd(g, w, h, now);
    }

    private static void drawQte(GuiGraphicsExtractor g, Minecraft mc, Font font, int w, int h, long now) {
        long ms = now - startAt;
        int cx = w / 2, cy = Math.round(h * 0.36f);
        // voile léger : le monde se fige
        g.fill(0, 0, w, h, 0x48000000);
        fr.reborn.hud.ui.DaKit.arcX1(g, font, fr.reborn.hud.ui.DaKit.arcade("Volonté"), cx, cy - 52,
            fr.reborn.hud.ui.DaKit.OR, 1, true);
        // repères de la séquence (petits losanges), sous le cercle
        int n = keys.length;
        for (int i = 0; i < n; i++) {
            int sx = cx + (int) Math.round((i - (n - 1) / 2.0) * 18);
            int sy = cy + 38;
            boolean done = i < step, cur = i == step;
            int col = done ? fr.reborn.hud.ui.DaKit.OR : cur ? fr.reborn.hud.ui.DaKit.CREME : 0x806E6050;
            fr.reborn.hud.ui.DaKit.losange(g, sx, sy + 4, 2, col, a(0xFF0A0608, ((col >>> 24) & 0xFF) / 255f * 0.6f));
            if (cur) g.fill(sx - 3, sy + 10, sx + 3, sy + 11, fr.reborn.hud.ui.DaKit.OR);
        }
        if (step >= n) return;
        // cercle qui se referme sur le sceau courant : tracé au pixel réel (lisse à toutes les échelles)
        long t = times[step];
        float p = Math.max(0f, Math.min(1f, (float) (ms - (t - APPROACH)) / APPROACH));
        float target = 15f, r = target + (1 - p) * 26f;
        float px = mc.getWindow().getWidth() / (float) w;
        g.pose().pushMatrix();
        g.pose().scale(1f / px, 1f / px);
        ring(g, cx * px, cy * px, target * px, 1.6f * px, 0x40D9A95E);
        ring(g, cx * px, cy * px, target * px, 0.7f * px, 0xC0D9A95E);
        if (ms >= t - APPROACH) {
            int col = p > 0.85f ? 0xFFF6E7B0 : a(0xFFF5E9D0, 0.35f + 0.6f * p);
            ring(g, cx * px, cy * px, r * px, 1.1f * px, col);
        }
        g.pose().popMatrix();
        // le repère à frapper au centre (losange), la touche juste dessous
        fr.reborn.hud.ui.DaKit.losange(g, cx, cy, 4, p > 0.85f ? 0xFFF6E7B0 : Colors.FOREGROUND, 0x990A0608);
        fr.reborn.hud.ui.DaKit.inter8(g, font, fr.reborn.hud.ui.DaKit.inter(KEY_LABEL[keys[step]]), cx, cy + 24,
            fr.reborn.hud.ui.DaKit.OR_CLAIR, 1);
    }

    /** Fin d'épreuve : un éclair bref (blanc debout, rouge s'il flanche) ; les cartes du KO disent la suite. */
    private static void drawEnd(GuiGraphicsExtractor g, int w, int h, long now) {
        float f = Math.max(0f, 1f - (now - endAt) / 700f);
        int rgb = success ? 0xFFF6E7 : 0x8A0F14;
        g.fill(0, 0, w, h, Math.round((success ? 70 : 60) * f * f) << 24 | rgb);
    }

    /** Anneau en pixels réels : un carré de la taille du trait par pas d'un demi-pixel. */
    private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float thick, int col) {
        int n = Math.max(48, Math.round(r * 2f * (float) Math.PI * 1.5f));
        int tk = Math.max(1, Math.round(thick));
        java.util.Set<Long> seen = new java.util.HashSet<>();
        fr.reborn.hud.ui.LotGui lot = fr.reborn.hud.ui.LotGui.ouvrir(g);   // des centaines de carrés = 1 élément GUI
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            int x = Math.round(cx + (float) Math.cos(ang) * r - tk / 2f);
            int y = Math.round(cy + (float) Math.sin(ang) * r - tk / 2f);
            if (seen.add(((long) x << 32) ^ (y & 0xFFFFFFFFL))) lot.fill(x, y, x + tk, y + tk, col);
        }
        lot.fermer();
    }

    private static int a(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }
}
