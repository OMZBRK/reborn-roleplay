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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.lwjgl.glfw.GLFW;

/**
 * KO-5 — QTE « Volonté », côté client. À terre, la touche Saut demande au serveur de tenter la relève. Le serveur
 * envoie la séquence (instants, touches, fenêtre) ; un cercle se referme sur chaque sceau, il faut frapper la touche
 * affichée (Espace, clic gauche, clic droit) au moment où il se pose. Les appuis horodatés repartent au serveur,
 * qui tranche. Lecture des touches directement au clavier/souris (indépendante des actions du jeu).
 */
public final class VolonteHud {

    private static final String[] KEY_LABEL = {"ESPACE", "CLIC G", "CLIC D"};
    private static final String[] SEALS = {"子", "丑", "寅", "卯", "辰", "巳", "午"};
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
        int hintY = Math.round(h * 0.60f) - 16;
        if (denying) {
            Component c = RebornFont.body(deny);
            g.text(font, c, w / 2 - font.width(c) / 2, hintY, a(Colors.ACCENT_HOVER, 1f), true);
        } else if (downed && !active && !(ended && now - endAt < 1600)) {
            Component hint = RebornFont.display("[ESPACE]  SE RELEVER" + (left < 2 ? "  ·  " + left + " RESTANTE" + (left > 1 ? "S" : "") : ""));
            float pulse = 0.55f + 0.45f * (float) Math.sin(now / 260.0);
            g.text(font, hint, w / 2 - font.width(hint) / 2, hintY, a(Colors.GOLD, pulse), true);
        }
        if (active) drawQte(g, mc, font, w, h, now);
        if (ended && now - endAt < 700) drawEnd(g, w, h, now);
    }

    private static void drawQte(GuiGraphicsExtractor g, Minecraft mc, Font font, int w, int h, long now) {
        long ms = now - startAt;
        int cx = w / 2, cy = Math.round(h * 0.36f);
        // voile : le monde se fige, plus sombre au centre de l'épreuve
        g.fill(0, 0, w, h, 0x80000000);
        Component title = RebornFont.display("VOLONTÉ");
        g.text(font, title, cx - font.width(title) / 2, cy - 58, Colors.GOLD, true);
        // sceaux de la séquence, sous le cercle
        int n = keys.length;
        for (int i = 0; i < n; i++) {
            int sx = cx + (int) Math.round((i - (n - 1) / 2.0) * 22);
            int sy = cy + 40;
            boolean done = i < step, cur = i == step;
            int col = done ? Colors.GOLD : cur ? Colors.FOREGROUND : 0x806E6050;
            Component k = Component.literal(SEALS[i % SEALS.length]);
            g.text(font, k, sx - font.width(k) / 2, sy, col, true);
            if (cur) g.fill(sx - 4, sy + 11, sx + 4, sy + 12, Colors.GOLD);
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
        // le sceau à frapper au centre, la touche juste dessous
        Component seal = Component.literal(SEALS[step % SEALS.length]);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(2f, 2f);
        g.text(font, seal, -font.width(seal) / 2, -4, p > 0.85f ? 0xFFF6E7B0 : Colors.FOREGROUND, true);
        g.pose().popMatrix();
        Component key = RebornFont.display(KEY_LABEL[keys[step]]);
        g.text(font, key, cx - font.width(key) / 2, cy + 21, Colors.GOLD, true);
    }

    /** Fin d'épreuve : un éclair bref (blanc debout, rouge s'il flanche) ; les cartes du KO disent la suite. */
    private static void drawEnd(GuiGraphicsExtractor g, int w, int h, long now) {
        float f = Math.max(0f, 1f - (now - endAt) / 700f);
        int rgb = success ? 0xFFF6E7 : 0x8A0F14;
        g.fill(0, 0, w, h, Math.round((success ? 150 : 120) * f * f) << 24 | rgb);
    }

    /** Anneau en pixels réels : un carré de la taille du trait par pas d'un demi-pixel. */
    private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float thick, int col) {
        int n = Math.max(48, Math.round(r * 2f * (float) Math.PI * 1.5f));
        int tk = Math.max(1, Math.round(thick));
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            int x = Math.round(cx + (float) Math.cos(ang) * r - tk / 2f);
            int y = Math.round(cy + (float) Math.sin(ang) * r - tk / 2f);
            if (seen.add(((long) x << 32) ^ (y & 0xFFFFFFFFL))) g.fill(x, y, x + tk, y + tk, col);
        }
    }

    private static int a(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }
}
