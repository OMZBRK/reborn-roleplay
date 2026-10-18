package fr.reborn.hud.parchemin;

import com.google.gson.JsonObject;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Concentration du chakra (ShinobiAbilities, message {@code {"t":"conc"}} du canal parchemin) — piste « ensō A ».
 *
 * <p>Discret, au-dessus de la barre d'action : un ensō (cercle de pinceau) tracé au pixel réel de l'écran, traversé
 * d'un trait d'encre horizontal. Sur le trait, la zone d'or et la perle de chakra (bleue dans l'or, laque hors de
 * l'or) ; l'ensō se referme en or à mesure que la concentration tient. Sous l'ensō, dix gouttes d'encre disent la
 * stabilité ; un à-coup (pas, saut, coup) fait trembler l'encre. Le rang en sceau au bout du trait.
 *
 * <p>DA §6 : tout le fixe est pré-dessiné ({@code tools/gen_concentration_textures.py}, une série de textures par
 * échelle, 1 texel = 1 pixel écran) ; le code ne pose que la zone, la perle, les gouttes et le texte. La taille est
 * celle des menus (échelle 2 en 1080p) quelle que soit l'échelle d'interface du joueur.
 */
public final class ConcentrationHud {

    private static final long END_SHOW = 1400, FADE = 300, HINT = 3500;
    /** Géométrie (unités menu) : rayon de l'ensō, cellule de l'atlas, trait, course utile de la perle. */
    private static final int R = 13, CELL = 36, TRAIT = 144, COURSE = 66, FRAMES = 40;

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
        boolean running = state.equals("run"), ok = state.equals("ok");
        if (running && now - lastUpdate > 1500) { active = false; return; }
        if (!running && now - endedAt > END_SHOW + FADE) { active = false; return; }

        float alpha = Math.min(1f, (now - startedAt) / (float) FADE);
        if (!running) alpha *= 1f - Math.max(0f, (now - endedAt - END_SHOW) / (float) FADE);
        if (alpha <= 0.01f) return;

        Font font = mc.font;
        var win = mc.getWindow();
        int gs = Math.max(1, win.getGuiScale());
        int km = DaKit.echelleMenu();
        int k = Math.max(1, Math.min(4, km));
        int guiH = win.getGuiScaledHeight();

        // tremblement de l'encre après un à-coup (en pixels)
        float shake = Math.max(0f, 1f - (now - joltAt) / 350f);
        int sx = shake > 0 ? Math.round((float) Math.sin(now / 18.0) * 2 * k * shake) : 0;
        int sy = shake > 0 ? Math.round((float) Math.cos(now / 23.0) * k * shake) : 0;

        // centre de l'ensō : au-dessus des cœurs / de la barre d'action (pixels écran)
        int cx = win.getWidth() / 2 + sx;
        int cy = (guiH - 39) * gs - 10 * k - R * k + sy;

        double f = Math.min(1, (now - lastUpdate) / 50.0);
        double vPos = displayPos(now), vCenter = prevCenter + (center - prevCenter) * f;
        float prog = ok ? 1f : (float) Math.min(1, held / secs);
        int frame = Math.round(prog * (FRAMES - 1));

        DaKit.debutPx(g);
        int bl = DaKit.blanc(alpha);
        DaKit.blit(g, tex("trait", k), cx - TRAIT * k / 2, cy - 5 * k, 0, 0, TRAIT * k, 10 * k, TRAIT * k, 10 * k, bl);
        if (running) zone(g, cx, cy, k, vCenter, alpha);
        int F = CELL * k;
        float ea = state.equals("ko") ? alpha * 0.55f : alpha;
        DaKit.blit(g, tex("enso", k), cx - F / 2, cy - F / 2, (frame % 8) * F, (frame / 8) * F, F, F, 8 * F, 5 * F,
            DaKit.blanc(ea));
        if (running) {
            perle(g, cx, cy, k, vPos, alpha, now);
            gouttes(g, cx, cy + (R + 5) * k, k, alpha);
        }
        DaKit.finPx(g);

        // texte et sceau de rang, en unités menu (plus de kanji au centre de l'ensō depuis le 18/10 : le mot
        // « Chakra concentré / dispersé » au-dessus suffit)
        DaKit.unites(g, km);
        float ucx = cx / (float) km, ucy = cy / (float) km;
        int rx = Math.round(ucx + TRAIT / 2f + 4), ry = Math.round(ucy - 7);
        int col = Math.max(0, "DCBAS".indexOf(rank.isEmpty() ? 'D' : rank.charAt(0)));
        DaKit.blit(g, SCEAUX, rx, ry, col * 16, 0, 16, 16, 80, 16, DaKit.blanc(alpha));
        if (helped) {
            Component m = DaKit.inter("avec un maître");
            DaKit.inter7o(g, font, m, rx + 19, ucy, DaKit.argb(0xE8C27A, 0.9f * alpha), 0);
        }

        // consigne / alerte, discrètes, au-dessus de l'ensō
        float ty = ucy - R - 9;
        long since = now - startedAt;
        if (running && since < HINT) {
            float a = alpha * (since > HINT - 700 ? (HINT - since) / 700f : 1f);
            Component hint = DaKit.inter("Reste immobile · tourne la tête pour garder le chakra dans l'or");
            DaKit.inter7o(g, font, hint, ucx, ty, DaKit.argb(0xE8DCC0, 0.95f * a), 1);
        } else if (running && stab <= 0.3) {
            float pulse = (float) (0.55 + 0.45 * Math.sin(now / 120.0));
            DaKit.inter7o(g, font, DaKit.inter("Ton chakra se disperse"), ucx, ty, DaKit.argb(0xE26474, alpha * pulse), 1);
        } else if (!running) {
            Component t = DaKit.inter(ok ? "Chakra concentré" : "Chakra dispersé — la séance ne compte pas");
            DaKit.inter7o(g, font, t, ucx, ty, DaKit.argb(ok ? 0xF2D08A : 0xE26474, alpha), 1);
        }
        DaKit.finUnites(g);
    }

    /* ---------------------------------------------------------------- textures (pixel réel) */

    private static final Identifier[][] TEX = new Identifier[4][5];
    private static final String[] NOMS = {"enso", "trait", "perle", "goutte"};
    private static final Identifier SCEAUX = DaKit.tex("concentration", "sceaux");

    private static Identifier tex(String nom, int k) {
        int i = java.util.Arrays.asList(NOMS).indexOf(nom);
        if (TEX[i][k] == null) TEX[i][k] = DaKit.tex("concentration", nom + "_" + k);
        return TEX[i][k];
    }

    /** Zone d'or sur le trait : lavis d'or et deux traits nets aux bornes. */
    private static void zone(GuiGraphicsExtractor g, int cx, int cy, int k, double c, float alpha) {
        int x0 = cx - COURSE * k, span = 2 * COURSE * k;
        int zl = Math.max(x0, x0 + (int) Math.round((c - zone + 1) / 2 * span));
        int zr = Math.min(x0 + span, x0 + (int) Math.round((c + zone + 1) / 2 * span));
        g.fill(zl, cy - 2 * k, zr, cy + 2 * k, DaKit.argb(0xD9A95E, 0.50f * alpha));
        g.fill(zl, cy - 2 * k, zr, cy - 2 * k + Math.max(1, k / 2), DaKit.argb(0xF2D59A, 0.55f * alpha));
        int t = Math.max(1, k / 2);
        g.fill(zl, cy - 4 * k, zl + t, cy + 4 * k, DaKit.argb(0xF2D59A, alpha));
        g.fill(zr - t, cy - 4 * k, zr, cy + 4 * k, DaKit.argb(0xF2D59A, alpha));
    }

    /** Perle de chakra et sa traînée. */
    private static void perle(GuiGraphicsExtractor g, int cx, int cy, int k, double p, float alpha, long now) {
        int x0 = cx - COURSE * k, span = 2 * COURSE * k;
        int bx = x0 + (int) Math.round((p + 1) / 2 * span);
        int col = in ? 0x6FD3FF : 0xE0505A;
        trail[trailN % trail.length] = bx;
        trailN++;
        int n = Math.min(trailN, trail.length);
        LotGui l = LotGui.ouvrir(g);                       // traînée : un seul élément GUI
        for (int i = 1; i < n; i++) {
            int tx = (int) trail[(trailN - 1 - i) % trail.length];
            l.fill(tx - k / 2, cy - k / 2, tx + Math.max(1, k / 2) + 1, cy + Math.max(1, k / 2) + 1,
                DaKit.argb(col, alpha * Math.max(0f, 0.45f - i * 0.06f)));
        }
        l.fermer();
        int C = 11 * k;
        float glow = (float) (0.85 + 0.15 * Math.sin(now / 160.0));
        DaKit.blit(g, tex("perle", k), bx - C / 2, cy - C / 2, in ? 0 : C, 0, C, C, 2 * C, C, DaKit.blanc(alpha * glow));
    }

    /** Stabilité : dix gouttes d'encre, qui s'éteignent (or, puis laque quand ça devient critique). */
    private static void gouttes(GuiGraphicsExtractor g, int cx, int y, int k, float alpha) {
        int n = 10, pas = 4 * k, w = 3 * k, left = cx - ((n - 1) * pas) / 2 - w / 2;
        int on = (int) Math.ceil(Math.max(0, stab) * n);
        int cell = stab > 0.3 ? 0 : 1;
        int bl = DaKit.blanc(alpha);
        if ((bl >>> 24) == 0) return;
        LotBlit l = LotBlit.ouvrir(g, tex("goutte", k), 3 * w, 4 * k);   // les dix gouttes : un seul élément GUI
        for (int i = 0; i < n; i++) {
            int c = i < on ? cell : 2;
            l.blit(left + i * pas, y, c * w, 0, w, 4 * k, bl);
        }
        l.fermer();
    }
}
