package fr.reborn.hud.parchemin;

import com.google.gson.JsonObject;
import fr.reborn.hud.combat.BrushRing;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Concentration du chakra (ShinobiAbilities, message {@code {"t":"conc"}} du canal parchemin), à l'encre, sans
 * plaque — dans l'esprit de l'ensō d'endurance du HUD de combat.
 * <ul>
 *   <li><b>Ensō</b> (style par défaut) : autour du viseur, le cercle au pinceau se referme en or à mesure que la
 *       concentration tient ; dedans, un trait d'encre horizontal porte la zone d'or et la perle de chakra.</li>
 *   <li><b>Trait</b> : un coup de pinceau horizontal sous le viseur, zone d'or, perle, et un fil d'or de progression.</li>
 * </ul>
 * La stabilité : gouttes d'encre sous le tracé, qui s'éteignent. Un à-coup (pas, saut, coup) fait trembler l'encre.
 */
public final class ConcentrationHud {

    private static final int CHAKRA = 0xFF6FD3FF, LOST = 0xFFE0505A;
    private static final int INK = 0xD0120A0C, INK_SOFT = 0x700A0608;
    private static final long END_SHOW = 1400, FADE = 300, HINT = 3500;

    /** Style choisi : {@code REBORN_CONC_STYLE=trait} pour le trait de pinceau, sinon l'ensō. */
    public static String style = "trait".equalsIgnoreCase(System.getenv("REBORN_CONC_STYLE")) ? "trait" : "enso";

    private static boolean active;
    private static String state = "run", rank = "D";
    private static double zone, center, prevCenter, pos, prevPos, held, secs = 1, stab = 1;
    private static boolean in = true, helped;
    private static long lastUpdate, startedAt, endedAt, joltAt;
    private static final double[] trail = new double[8];
    private static int trailN;

    private ConcentrationHud() {}

    public static void update(JsonObject o) {
        long now = System.currentTimeMillis();
        String st = o.has("st") ? o.get("st").getAsString() : "run";
        if (st.equals("stop")) { active = false; return; }
        if (!active || (!state.equals("run") && st.equals("run"))) {
            startedAt = now;
            trailN = 0;
            prevPos = pos = num(o, "pos", 0);
            prevCenter = center = num(o, "c", 0);
        }
        active = true;
        prevPos = displayPos(now);
        prevCenter = center;
        pos = num(o, "pos", pos);
        center = num(o, "c", center);
        zone = num(o, "zone", zone);
        held = num(o, "held", held);
        secs = Math.max(0.1, num(o, "secs", secs));
        stab = num(o, "stab", stab);
        in = !o.has("in") || o.get("in").getAsBoolean();
        helped = o.has("helped") && o.get("helped").getAsBoolean();
        if (o.has("jolt") && o.get("jolt").getAsBoolean()) joltAt = now;
        if (o.has("rank")) rank = o.get("rank").getAsString();
        if (!st.equals(state) && !st.equals("run")) endedAt = now;
        state = st;
        lastUpdate = now;
    }

    public static void clear() { active = false; }

    private static double num(JsonObject o, String k, double d) { return o.has(k) ? o.get(k).getAsDouble() : d; }

    private static double displayPos(long now) {
        double f = Math.min(1, (now - lastUpdate) / 50.0);
        return prevPos + (pos - prevPos) * f;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        long now = System.currentTimeMillis();
        boolean running = state.equals("run");
        if (running && now - lastUpdate > 1500) { active = false; return; }
        if (!running && now - endedAt > END_SHOW + FADE) { active = false; return; }

        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float alpha = Math.min(1f, (now - startedAt) / (float) FADE);
        if (!running) alpha *= 1f - Math.max(0f, (now - endedAt - END_SHOW) / (float) FADE);
        if (alpha <= 0.01f) return;

        // tremblement de l'encre après un à-coup
        float shake = Math.max(0f, 1f - (now - joltAt) / 350f);
        int sx = shake > 0 ? Math.round((float) Math.sin(now / 18.0) * 3 * shake) : 0;
        int sy = shake > 0 ? Math.round((float) Math.cos(now / 23.0) * 2 * shake) : 0;

        double f = Math.min(1, (now - lastUpdate) / 50.0);
        View v = new View(displayPos(now), prevCenter + (center - prevCenter) * f, alpha, now);
        if (style.equals("trait")) trait(g, font, w / 2 + sx, h / 2 + 34 + sy, v);
        else enso(g, font, w / 2 + sx, h / 2 + sy, v);

        // consigne / alerte, discrètes, sous le tracé
        int ty = style.equals("trait") ? h / 2 + 66 : h / 2 + 52;
        long since = now - startedAt;
        if (running && since < HINT) {
            float a = alpha * (since > HINT - 700 ? (HINT - since) / 700f : 1f);
            Component hint = RebornFont.body("Reste immobile · tourne la tête pour garder le chakra dans l'or");
            g.text(font, hint, w / 2 - font.width(hint) / 2, ty, a(Colors.FOREGROUND_SUBTLE, a * 0.9f), true);
        } else if (running && stab <= 0.3) {
            float pulse = (float) (0.55 + 0.45 * Math.sin(now / 120.0));
            Component warn = RebornFont.body("Ton chakra se disperse");
            g.text(font, warn, w / 2 - font.width(warn) / 2, ty, a(Colors.ACCENT_HOVER, alpha * pulse), true);
        } else if (!running) {
            boolean ok = state.equals("ok");
            Component t = RebornFont.body(ok ? "Chakra concentré" : "Chakra dispersé — la séance ne compte pas");
            g.text(font, t, w / 2 - font.width(t) / 2, ty, a(ok ? Colors.GOLD : Colors.ACCENT_HOVER, alpha), true);
        }
    }

    private record View(double pos, double center, float alpha, long now) {}

    /* ------------------------------------------------------------------ ensō */

    private static void enso(GuiGraphicsExtractor g, Font font, int cx, int cy, View v) {
        boolean running = state.equals("run"), ok = state.equals("ok");
        int r = 34;
        float prog = ok ? 1f : (float) Math.min(1, held / secs);
        int fill = ok ? Colors.GOLD : state.equals("ko") ? a(LOST, 0.8f) : 0xFFD9A95E;
        BrushRing.enso(g, cx, cy, r, prog, prog, a(fill, v.alpha), a(fill, v.alpha), a(0x55F5E9D0, v.alpha));

        if (!running) {
            // 集 au centre, en or ou en laque
            Component k = Component.literal("集");
            float sc = 2f;
            g.pose().pushMatrix();
            g.pose().translate(cx - font.width(k) * sc / 2f, cy - 4 * sc);
            g.pose().scale(sc, sc);
            g.text(font, k, 0, 0, a(ok ? Colors.GOLD : Colors.ACCENT_HOVER, v.alpha), false);
            g.pose().popMatrix();
            return;
        }
        // trait d'encre horizontal (diamètre), zone d'or, perle
        int half = r - 10, y = cy;
        brush(g, cx - half, cx + half, y, 3, a(INK, v.alpha), 7);
        zoneAndBead(g, cx - half, cx + half, y, v);
        drops(g, cx, cy + r + 8, v);
        // rang discret au-dessus de l'ouverture du cercle
        Component rk = RebornFont.body(rank + (helped ? " · maître" : ""));
        g.text(font, rk, cx - font.width(rk) / 2, cy - r - 12, a(Colors.FOREGROUND_MUTED, v.alpha * 0.9f), true);
    }

    /* ----------------------------------------------------------------- trait */

    private static void trait(GuiGraphicsExtractor g, Font font, int cx, int cy, View v) {
        boolean running = state.equals("run"), ok = state.equals("ok");
        int half = 70;
        // coup de pinceau : large, effilé aux deux bouts
        brush(g, cx - half, cx + half, cy, 5, a(INK, v.alpha), 3);
        // fil d'or de progression juste au-dessus
        float prog = ok ? 1f : (float) Math.min(1, held / secs);
        g.fill(cx - half + 6, cy - 6, cx + half - 6, cy - 5, a(0x40D9A95E, v.alpha));
        g.fill(cx - half + 6, cy - 6, cx - half + 6 + Math.round((2 * half - 12) * prog), cy - 5, a(Colors.GOLD, v.alpha));
        // 集 à gauche, rang à droite
        Component k = Component.literal("集");
        g.text(font, k, cx - half - 14, cy - 4, a(ok ? Colors.GOLD : running ? Colors.FOREGROUND_SUBTLE : Colors.ACCENT_HOVER, v.alpha), true);
        Component rk = RebornFont.body(rank + (helped ? " · maître" : ""));
        g.text(font, rk, cx + half + 6, cy - 4, a(Colors.FOREGROUND_MUTED, v.alpha * 0.9f), true);
        if (!running) return;
        zoneAndBead(g, cx - half + 4, cx + half - 4, cy, v);
        drops(g, cx, cy + 10, v);
    }

    /* ---------------------------------------------------------------- communs */

    /** Zone d'or (lavis + deux traits) et perle de chakra avec sa traînée. */
    private static void zoneAndBead(GuiGraphicsExtractor g, int x0, int x1, int y, View v) {
        int span = x1 - x0;
        int zl = Math.max(x0, x0 + (int) Math.round((v.center - zone + 1) / 2 * span));
        int zr = Math.min(x1, x0 + (int) Math.round((v.center + zone + 1) / 2 * span));
        g.fill(zl, y - 2, zr, y + 3, a(0x70D9A95E, v.alpha));
        g.fill(zl, y - 4, zl + 1, y + 5, a(Colors.GOLD, v.alpha));
        g.fill(zr - 1, y - 4, zr, y + 5, a(Colors.GOLD, v.alpha));

        int bx = x0 + (int) Math.round((v.pos + 1) / 2 * span);
        int col = in ? CHAKRA : LOST;
        trail[trailN % trail.length] = bx;
        trailN++;
        int n = Math.min(trailN, trail.length);
        for (int k = 1; k < n; k++) {
            int tx = (int) trail[(trailN - 1 - k) % trail.length];
            g.fill(tx - 1, y - 1, tx + 1, y + 2, a(col, v.alpha * Math.max(0f, 0.45f - k * 0.06f)));
        }
        float glow = (float) (0.5 + 0.5 * Math.sin(v.now / 160.0));
        g.fill(bx - 3, y - 3, bx + 4, y + 4, a(col, v.alpha * (0.18f + 0.12f * glow)));
        g.fill(bx - 2, y - 2, bx + 3, y + 3, a(col, v.alpha * 0.55f));
        g.fill(bx - 1, y - 1, bx + 2, y + 2, a(0xFFFFFFFF, v.alpha * 0.9f));
    }

    /** Stabilité : dix gouttes d'encre, qui s'éteignent (or → laque quand ça devient critique). */
    private static void drops(GuiGraphicsExtractor g, int cx, int y, View v) {
        int n = 10, gap = 5, left = cx - (n - 1) * gap / 2;
        int on = (int) Math.ceil(Math.max(0, stab) * n);
        int col = stab > 0.3 ? Colors.GOLD : Colors.ACCENT_HOVER;
        for (int i = 0; i < n; i++) {
            int x = left + i * gap;
            if (i < on) {
                g.fill(x - 1, y, x + 1, y + 2, a(col, v.alpha * 0.95f));
                g.fill(x, y - 1, x + 1, y + 3, a(col, v.alpha * 0.6f));
            } else {
                g.fill(x, y, x + 1, y + 1, a(INK_SOFT, v.alpha));
            }
        }
    }

    /**
     * Coup de pinceau horizontal de {@code x0} à {@code x1} : épaisseur max {@code thick}, effilé aux bouts, bord
     * irrégulier (bruit déterministe par colonne) pour l'effet de poils.
     */
    private static void brush(GuiGraphicsExtractor g, int x0, int x1, int y, int thick, int color, int seed) {
        int len = Math.max(1, x1 - x0);
        for (int x = x0; x < x1; x++) {
            float u = (x - x0) / (float) len;
            float taper = (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, u * 1.08f - 0.02f)));
            int hash = (x * 73856093) ^ (seed * 19349663);
            float rough = ((hash >>> 8) & 0xFF) / 255f;
            int t = Math.max(1, Math.round(thick * (0.55f + 0.45f * taper) - rough * 0.8f));
            int top = y - t / 2 - ((hash & 3) == 0 ? 1 : 0);
            g.fill(x, top, x + 1, top + t, color);
        }
    }

    private static int a(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }
}
