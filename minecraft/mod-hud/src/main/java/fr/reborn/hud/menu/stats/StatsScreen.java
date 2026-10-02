package fr.reborn.hud.menu.stats;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * <b>Fiche shinobi</b> — « lanternes célestes » (SPEC_STATS_SERVICE, option B).
 *
 * <p>Un ciel de nuit au-dessus du village : chaque stat est une lanterne céleste dont la
 * <b>hauteur est la valeur</b> (échelle 0 → max, ligne du seuil = soft cap). Les points en
 * attente dessinent la silhouette dorée de la hauteur visée ; VALIDER fait s'envoler les
 * lanternes jusque-là. Au premier plan, une véranda laquée : une plaque votive (ema) par
 * stat accrochée à la rambarde (sceau, valeur, −/+), les valeurs dérivées gravées sur la
 * poutre, et les points restants en <b>flammes-esprits</b> qui filent vers la lanterne
 * quand on pose un point.
 *
 * <p>Ouverture « tombée de la nuit » : panoramique du village vers le ciel (parallaxe
 * montagnes / village / lune), crépuscule → nuit, lanternes qui s'allument en vague puis
 * montent, puis l'interface glisse en place. Un clic pendant l'ouverture la termine.
 * Sons : {@link StatsSounds}. Textures : {@code tools/ui-art/gen_stats_lanterns.py}.
 *
 * <p>L'allocation reste <b>locale</b> jusqu'à VALIDER ; les valeurs dérivées sont
 * recalculées par {@link StatsMath} avec les leviers du serveur, qui reste seul juge
 * ({@code alloc:} refusé → la fiche se resynchronise).
 *
 * <p>Souris : clic sur une lanterne = +1, clic droit = −1 (Maj : tout).
 * Clavier : 1-6 = +1 (Maj = −1), Entrée = valider, Tab = onglet.
 */
public class StatsScreen extends Screen {

    private static final int TAB_ATTR = 0, TAB_TECH = 1;
    private static int lastTab = TAB_ATTR;

    private static final int ACC = Colors.ACCENT;
    private static final int GOLD = 0xFFF6CC78;
    private static final int CREAM = 0xFFFAEED6;
    private static final int INK = Colors.withAlpha(Colors.FOREGROUND, 0.92f);
    private static final int INK_SOFT = 0xFFC8B8D2;
    private static final int INK_DIM = 0xFF8A7C9A;
    private static final int TRAIL = 0xFF8C6450;
    private static final int LACQ = 0xFF5C1418;
    private static final int LACQ_D = 0xFF360C10;
    private static final int LACQ_LINE = 0xFF421014;
    private static final int WOOD = 0xFF623C24;
    private static final int WOOD_D = 0xFF3C2416;
    private static final int WOOD_L = 0xFF8C5C38;
    private static final int ROPE = 0xFFC8A05A;
    private static final int PLAQUE_INK = 0xFF3C2414;
    private static final int SPIRIT = 0xFF8CC8FF;

    private static final int[] TIER_COLOR = {0xFF9CA3AF, 0xFFE5E7EB, 0xFF4ADE80, 0xFF38BDF8, 0xFFD9A95E, 0xFFC084FC};
    private static final String[] TIER_LETTER = {"E", "D", "C", "B", "A", "S"};

    private static final Identifier SKY_DUSK = tex("sky_dusk"), SKY_NIGHT = tex("sky_night");
    private static final Identifier MOUNTAINS = tex("mountains"), VILLAGE = tex("village"), WINDOWS = tex("windows");
    private static final Identifier MOON = tex("moon"), GLOW = tex("glow"), HITODAMA = tex("hitodama"), EMA = tex("ema");
    private static final Identifier[] LANTERN = new Identifier[6], SEAL = new Identifier[6], DK = new Identifier[6];
    static {
        for (int i = 0; i < 6; i++) {
            LANTERN[i] = tex("lantern_" + i);
            SEAL[i] = tex("seal_" + i);
            DK[i] = tex("dk_" + i);
        }
    }
    private static final int LW = 40, LH = 50;

    private static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/stats/" + n + ".png");
    }

    private static final int INTRO_MS = 2300;

    private StatsData.Snapshot snap;
    private int seenVersion = -1;
    private final int[] pending = new int[StatDef.values().length];
    /** Points validés mais pas encore confirmés par le serveur : la lanterne monte tout de suite. */
    private final int[] optimistic = new int[StatDef.values().length];
    private int tab = lastTab;
    private final long openedAt = System.currentTimeMillis();
    private long introStart = openedAt;
    private long respecArmedAt = 0L;
    private float techScroll = 0f;
    private int techContentH = 0;

    // Animation des lanternes (valeurs affichées, en unités de stat).
    private final float[] shownVal = new float[6];
    private final float[] ghostVal = new float[6];
    private final float[] ghostAlpha = new float[6];
    private final float[] hoverGlow = new float[6];
    private final boolean[] lit = new boolean[6];
    private final long[] launchAt = new long[6];
    private long lastFrame = System.nanoTime();
    private int hoveredLantern = -1;
    private Object lastHoverKey = null;
    private boolean clickShift;
    private long shootAt = System.currentTimeMillis() + 3500;
    private float shootX, shootY;

    private final List<Particle> particles = new ArrayList<>();
    private final List<Flyer> flyers = new ArrayList<>();
    private final Random rng = new Random();
    private final float[][] stars = new float[60][4]; // x, y (0..1), vitesse, phase

    // Intro (recalculés à chaque frame)
    private float pan = 1f, night = 1f, ui = 1f, off = 0f;

    // Layout
    private boolean compact;
    private float ls = 1f;
    private int top, plaqueTop, plaqueH, plaqueW, tabsY, headerBottom, hintY, btnY, btnH, beamY, beamH;
    private int railY, hillY, villageTop, villageH, skyTop, skyBase, emaW, emaH;
    private int laneX0, laneX1;
    private final int[] laneX = new int[6];
    private int closeX, closeY;
    private final int closeS = 12;
    private int rowsY, rowX0, rowX1, techBottom;
    private final List<Btn> buttons = new ArrayList<>();

    private record Btn(int x, int y, int w, int h, Runnable act) {
        boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Particule : étincelle (point posé, envol) ou braise (village). */
    private static final class Particle {
        float x, y, vx, vy, life, max;
        int color;
        boolean ember;
    }

    /** Flamme-esprit en vol : réserve → lanterne (point posé) ou retour (point retiré). */
    private static final class Flyer {
        float x0, y0, x1, y1;
        long start;
        int stat;
        boolean toLantern;
    }

    /** Infobulle à dessiner en dernier (au-dessus de tout). */
    private List<Component> tooltip = null;
    private int tooltipColor = GOLD;

    public StatsScreen() {
        super(Component.literal("Fiche shinobi"));
        resync();
        Random r = new Random(0x57A2L);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat() * 0.62f;
            s[2] = 0.6f + r.nextFloat() * 1.6f;
            s[3] = r.nextFloat() * 6.28f;
        }
    }

    /* ------------------------------------------------------------ données */

    private void resync() {
        boolean fresh = seenVersion != StatsData.version();
        this.snap = StatsData.get();
        this.seenVersion = StatsData.version();
        if (fresh) java.util.Arrays.fill(optimistic, 0);
        // Une allocation en attente qui ne tient plus (points retirés, stat montée) est rognée.
        int budget = Math.max(0, snap.unspent());
        for (StatDef d : StatDef.values()) {
            int room = snap.max() - snap.get(d);
            pending[d.ordinal()] = Math.max(0, Math.min(pending[d.ordinal()], room));
        }
        while (pendingTotal() > budget) {
            for (int i = pending.length - 1; i >= 0; i--) {
                if (pending[i] > 0) { pending[i]--; break; }
            }
        }
    }

    /** Appelé par le receveur réseau quand un nouveau snapshot arrive, écran ouvert. */
    public void refresh() {
        resync();
    }

    private int pendingTotal() {
        int t = 0;
        for (int p : pending) t += p;
        return t;
    }

    private int remaining() {
        return Math.max(0, snap.unspent()) - pendingTotal();
    }

    private int[] previewStats() {
        int[] out = snap.stats().clone();
        for (int i = 0; i < out.length; i++) out[i] += pending[i];
        return out;
    }

    /* ---------------------------------------------------------- cycle de vie */

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        if (System.currentTimeMillis() - openedAt < 200) {
            StatsSounds.open();
            StatsSounds.startAmbience();
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        lastTab = tab;
        StatsSounds.stopAmbience();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /* ---------------------------------------------------------------- intro */

    private float introT() {
        return Math.min(1f, (System.currentTimeMillis() - introStart) / (float) INTRO_MS);
    }

    private boolean interactive() { return introT() >= 0.82f; }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float easeInOut(float t) { return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    private void updateIntro() {
        float t = introT();
        pan = easeInOut(clamp01(t / 0.6f));
        night = easeInOut(clamp01((t - 0.04f) / 0.6f));
        ui = easeOut(clamp01((t - 0.62f) / 0.3f));
        off = (1f - pan) * this.height * 0.31f;
    }

    /** Termine l'ouverture tout de suite (clic pendant l'animation). */
    private void skipIntro() {
        introStart = System.currentTimeMillis() - INTRO_MS;
        for (int i = 0; i < 6; i++) lit[i] = true;
    }

    /* --------------------------------------------------------------- layout */

    private void layout() {
        compact = this.height < 400;
        ls = compact ? (this.height < 300 ? 0.6f : 0.75f) : 1f;
        top = compact ? 3 : 6;
        plaqueTop = top;
        plaqueH = compact ? 28 : 40;
        tabsY = plaqueTop + plaqueH + 4;
        headerBottom = tabsY + 12;

        int span = Math.min(this.width - 140, 760);
        laneX0 = this.width / 2 - span / 2;
        laneX1 = this.width / 2 + span / 2;
        for (int i = 0; i < 6; i++) laneX[i] = laneX0 + span * (2 * i + 1) / 12;
        emaW = Math.min(92, span / 6 - 6);
        emaH = Math.round(56f * emaW / 92f);

        hintY = compact ? -1 : this.height - 10;
        btnH = compact ? 13 : 15;
        btnY = compact ? this.height - 17 : hintY - 21;
        beamH = compact ? 16 : 26;
        beamY = btnY - (compact ? 5 : 8) - beamH;
        railY = beamY - 8 - emaH - 6;
        villageH = compact ? 60 : 80;
        hillY = railY - (compact ? 16 : 24);
        villageTop = hillY - Math.round(villageH * 0.45f);
        skyBase = hillY - Math.round(30 * ls);
        skyTop = headerBottom + Math.round(36 * ls);

        closeX = this.width - closeS - 10;
        closeY = top;

        rowX0 = laneX0;
        rowX1 = laneX1;
        rowsY = headerBottom + 12;
        techBottom = railY - 16;
    }

    private float yOf(float v) {
        return skyBase - (skyBase - skyTop) * v / Math.max(1, snap.max());
    }

    /** Couleur RGB interpolée (alpha opaque). */
    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /* ---------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        updateIntro();
        long now = System.currentTimeMillis();
        float time = (now - openedAt) / 1000f;
        // Ciel : crépuscule recouvert progressivement par la nuit.
        ctx.blit(RenderPipelines.GUI_TEXTURED, SKY_DUSK, 0, 0, 0f, 0f, this.width, this.height, 480, 270, 480, 270);
        int na = Math.round(255 * night);
        if (na > 0) ctx.blit(RenderPipelines.GUI_TEXTURED, SKY_NIGHT, 0, 0, 0f, 0f, this.width, this.height, 480, 270, 480, 270,
            (na << 24) | 0xFFFFFF);
        // Étoiles qui apparaissent avec la nuit et scintillent.
        for (float[] s : stars) {
            float a = night * (0.25f + 0.75f * Math.max(0f, (float) Math.sin(time * s[2] + s[3])));
            if (a < 0.03f) continue;
            int x = (int) (s[0] * this.width), y = (int) (s[1] * this.height + off * 0.2f);
            ctx.fill(x, y, x + 1, y + 1, Colors.withAlpha(0xFFF4F0FF, a));
        }
        // Étoile filante de temps en temps.
        if (now > shootAt) {
            float k = (now - shootAt) / 650f;
            if (k > 1f) {
                shootAt = now + 6000 + rng.nextInt(7000);
                shootX = this.width * (0.1f + rng.nextFloat() * 0.5f);
                shootY = skyTop * 0.6f + rng.nextFloat() * 50;
            } else if (night > 0.9f && shootX > 0) {
                float hx = shootX + k * 90, hy = shootY + k * 34;
                for (int n = 0; n < 14; n++) {
                    float a = (1f - n / 14f) * (1f - k);
                    ctx.fill(Math.round(hx - n * 2.6f), Math.round(hy - n * 1f), Math.round(hx - n * 2.6f) + 1,
                        Math.round(hy - n * 1f) + 1, Colors.withAlpha(0xFFFFFFFF, a));
                }
            }
        }
        // Lune (monte avec la caméra).
        int ms = Math.round(40 * ls);
        float mx = this.width * 0.82f, my = skyTop + 6 + off * 0.35f;
        blitGlow(ctx, mx, my, ms * 2.6f, Colors.withAlpha(0xFFFFECC8, 0.18f + 0.12f * night));
        ctx.blit(RenderPipelines.GUI_TEXTURED, MOON, Math.round(mx - ms / 2f), Math.round(my - ms / 2f), 0f, 0f,
            ms, ms, 40, 40, 40, 40, mix(0xFFFFC88C, 0xFFF8EED2, night));
        // Montagnes lointaines.
        int mh = Math.round(this.height * 0.22f);
        int mtop = Math.round(hillY - mh * 0.62f + off * 0.45f);
        ctx.blit(RenderPipelines.GUI_TEXTURED, MOUNTAINS, 0, mtop, 0f, 0f, this.width, mh, 480, 120, 480, 120,
            mix(0xFF6E466E, 0xFF1E162E, night));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (StatsData.version() != seenVersion) resync();
        layout();
        updateIntro();
        buttons.clear();
        tooltip = null;
        Font f = this.font;
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        int[] prev = previewStats();

        if (tab == TAB_ATTR) {
            if (ui > 0.01f) drawScale(ctx, f);
            drawLanterns(ctx, f, prev, mouseX, mouseY, dt);
        } else {
            hoveredLantern = -1;
        }
        drawVillage(ctx);
        updateParticles(ctx, dt);
        if (tab == TAB_TECH) drawTechniques(ctx, f, prev, mouseX, mouseY);

        // Interface : la véranda monte, la plaque descend.
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, (1f - ui) * this.height * 0.32f);
        drawDeck(ctx, f, prev, mouseX, mouseY);
        ctx.pose().popMatrix();
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, -(1f - ui) * (headerBottom + 20));
        drawHeader(ctx, f, mouseX, mouseY);
        ctx.pose().popMatrix();
        updateFlyers(ctx, dt);

        if (tab == TAB_ATTR && hoveredLantern >= 0) drawHoverLabel(ctx, f, hoveredLantern, prev);

        // Survol : un tic discret quand on change de cible.
        Object hoverKey = hoveredLantern >= 0 ? (Object) ("L" + hoveredLantern) : (tooltip != null ? tooltip.get(0).getString() : null);
        if (hoverKey != null && !hoverKey.equals(lastHoverKey) && interactive()) StatsSounds.hover();
        lastHoverKey = hoverKey;

        if (tooltip != null && interactive()) drawTooltip(ctx, f, tooltip, mouseX, mouseY, tooltipColor);
    }

    /* ---- en-tête : plaque laquée suspendue + onglets ---- */

    private void drawHeader(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        float cx = this.width / 2f;
        Component title = RebornFont.arcade("FICHE SHINOBI");
        float ts = compact ? 1.0f : 1.3f;
        StringBuilder sub = new StringBuilder(snap.name());
        if (!snap.clan().isBlank() && !snap.clan().equalsIgnoreCase("None")) sub.append(' ').append(snap.clan());
        sub.append(" - ").append(snap.rank());
        if (!snap.village().isBlank()) sub.append(" - ").append(snap.village());
        if (!snap.natures().isEmpty()) sub.append(" - ").append(String.join(" / ", snap.natures()));
        if (!snap.nextRank().isBlank()) {
            sub.append(" - vers ").append(snap.nextRank()).append(" (+").append(snap.levers().pointsPerRank()).append(')');
        }
        Component subC = Component.literal(sub.toString());
        float subS = compact ? 0.75f : 0.85f;
        int pw = (int) Math.max(f.width(title) * ts, f.width(subC) * subS) + 30;
        int px = Math.round(cx - pw / 2f);
        // cordelettes
        DrawHelpers.line(ctx, px + 30, 0, px + 40, plaqueTop + 2, ROPE);
        DrawHelpers.line(ctx, px + pw - 30, 0, px + pw - 40, plaqueTop + 2, ROPE);
        ctx.fill(px, plaqueTop, px + pw, plaqueTop + plaqueH, LACQ);
        frame(ctx, px, plaqueTop, pw, plaqueH, GOLD);
        frame(ctx, px + 3, plaqueTop + 3, pw - 6, plaqueH - 6, 0xFF963C32);
        drawScaledCentered(ctx, f, title, cx, plaqueTop + (compact ? 5 : 8), CREAM, ts);
        drawScaledCentered(ctx, f, subC, cx, plaqueTop + (compact ? 17 : 26), 0xFFE6B4A0, subS);

        // Onglets suspendus sous la plaque.
        Component a = RebornFont.arcade("ATTRIBUTS");
        Component t = RebornFont.arcade("TECHNIQUES (" + snap.techniques().size() + ")");
        int aw = f.width(a) + 14, tw = f.width(t) + 14;
        int ax = (int) cx - aw - 3, tx = (int) cx + 3;
        tabTag(ctx, f, a, ax, aw, tab == TAB_ATTR, mx, my);
        tabTag(ctx, f, t, tx, tw, tab == TAB_TECH, mx, my);
        buttons.add(new Btn(ax, tabsY, aw, 12, () -> switchTab(TAB_ATTR)));
        buttons.add(new Btn(tx, tabsY, tw, 12, () -> switchTab(TAB_TECH)));

        if (!StatsData.fromServer()) {
            Component demo = RebornFont.arcade("APERCU HORS LIGNE");
            ctx.text(f, demo, closeX - 8 - f.width(demo), closeY + 2, Colors.withAlpha(Colors.WARNING, 0.8f), false);
        }
        boolean hov = mx >= closeX && mx < closeX + closeS && my >= closeY && my < closeY + closeS;
        DrawHelpers.roundedOutlinedRectFull(ctx, closeX, closeY, closeS, closeS, 2,
            hov ? Colors.withAlpha(ACC, 0.7f) : LACQ, hov ? GOLD : 0xFF963C32);
        Component x = Component.literal("✕");
        ctx.text(f, x, closeX + (closeS - f.width(x)) / 2 + 1, closeY + 2, CREAM, false);
        buttons.add(new Btn(closeX, closeY, closeS, closeS, this::onClose));
    }

    private void tabTag(GuiGraphicsExtractor ctx, Font f, Component c, int x, int w, boolean active, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= tabsY && my < tabsY + 12;
        ctx.fill(x + w / 2, plaqueTop + plaqueH, x + w / 2 + 1, tabsY, ROPE);
        ctx.fill(x, tabsY, x + w, tabsY + 12, active ? 0xFFAA1E22 : (hov ? 0xFF4A242A : 0xFF3C1E24));
        frame(ctx, x, tabsY, w, 12, active ? GOLD : 0xFF6E4646);
        ctx.text(f, c, x + (w - f.width(c)) / 2, tabsY + 3, active ? CREAM : (hov ? 0xFFD2B4B4 : 0xFFAA8C8C), false);
    }

    private void switchTab(int t) {
        if (tab != t) { tab = t; StatsSounds.tab(); }
    }

    /* ---- ciel : échelle + lanternes ---- */

    private void drawScale(GuiGraphicsExtractor ctx, Font f) {
        int max = snap.max();
        int x = laneX0 - 22;
        int col = Colors.withAlpha(0xFF6E5A84, ui);
        for (int v = 0; v <= max; v += 2) {
            int y = Math.round(yOf(v) + off);
            ctx.fill(x, y, x + 6, y + 1, col);
            Component c = Component.literal(String.valueOf(v));
            drawScaled(ctx, f, c, x - 3 - f.width(c) * 0.75f, y - 3, col, 0.75f);
        }
        int sy = Math.round(yOf((float) snap.levers().softCap()) + off);
        for (int xx = laneX0 - 10; xx < laneX1 + 10; xx += 5) ctx.fill(xx, sy, xx + 2, sy + 1, Colors.withAlpha(0xFFB48290, 0.6f * ui));
        drawScaled(ctx, f, RebornFont.arcade("SEUIL"), laneX1 + 12, sy - 3, Colors.withAlpha(0xFFB48290, ui), 0.75f);
    }

    private void drawLanterns(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my, float dt) {
        long now = System.currentTimeMillis();
        float time = (now - openedAt) / 1000f;
        float k = 1f - (float) Math.exp(-dt * 5f);
        hoveredLantern = -1;
        int lw = Math.round(LW * ls), lh = Math.round(LH * ls);
        for (StatDef d : StatDef.values()) {
            int i = d.ordinal();
            // Allumage en vague pendant le panoramique, puis montée depuis le village.
            long lightAt = introStart + 950 + i * 130L;
            float ignite = clamp01((now - lightAt) / 280f);
            if (ignite > 0f && !lit[i]) { lit[i] = true; StatsSounds.light(i); }
            if (lit[i] && ignite <= 0f) ignite = 1f;   // ouverture passée d'un clic
            float target = lit[i] ? snap.stats()[i] + optimistic[i] : 0f;
            shownVal[i] += (target - shownVal[i]) * k;
            boolean hasGhost = pending[i] > 0;
            ghostVal[i] += ((hasGhost ? prev[i] : shownVal[i]) - ghostVal[i]) * Math.min(1f, k * 1.6f);
            ghostAlpha[i] += ((hasGhost ? 1f : 0f) - ghostAlpha[i]) * Math.min(1f, k * 1.6f);

            float bob = (float) Math.sin(time * 1.3f + i * 1.1f) * 1.5f;
            float sway = (float) Math.sin(time * 0.9f + i) * 1f;
            float cx = laneX[i] + sway;
            float cy = yOf(shownVal[i]) + bob + off;

            boolean hot = interactive() && Math.abs(mx - cx) <= lw / 2f + 4 && my >= cy - lh / 2f - 4 && my <= cy + lh / 2f + 6;
            if (hot) hoveredLantern = i;
            hoverGlow[i] += ((hot ? 1f : 0f) - hoverGlow[i]) * Math.min(1f, k * 2f);

            // Fil de lumière jusqu'au village.
            for (int yy = (int) (cy + lh / 2f + 4); yy < hillY + off * 0.75f; yy += 3) {
                float wx = cx + (float) Math.sin(yy / 14f + i + time * 0.6f) * 2f;
                ctx.fill(Math.round(wx), yy, Math.round(wx) + 1, yy + 1, Colors.withAlpha(TRAIL, 0.6f * ignite));
            }
            // Silhouette dorée de la hauteur visée (points en attente).
            if (ghostAlpha[i] > 0.02f) {
                float gy = yOf(ghostVal[i]) + bob + off;
                float pulse = 0.65f + 0.35f * (float) Math.sin(time * 4f + i);
                ghostOutline(ctx, cx, gy, lw, lh, Colors.withAlpha(GOLD, ghostAlpha[i] * pulse));
            }
            // Halo : couleur de la stat, grandit avec la valeur ; flamme qui vacille.
            float frac = shownVal[i] / Math.max(1, snap.max());
            float flicker = 0.85f + 0.15f * (float) Math.sin(time * 9f + i * 2.3f) * (float) Math.sin(time * 5.3f + i);
            float gs = (56 + 46 * frac + 14 * hoverGlow[i]) * ls;
            blitGlow(ctx, cx, cy + 6 * ls, gs, Colors.withAlpha(d.color, (0.24f + 0.36f * frac + 0.2f * hoverGlow[i]) * flicker * ignite));
            blitGlow(ctx, cx, cy + 16 * ls, 26 * ls, Colors.withAlpha(0xFFFFBE6E, 0.5f * flicker * ignite));
            // Lanterne (éteinte = sombre, s'allume en fondu).
            int shade = (int) (70 + 185 * ignite);
            int tint = 0xFF000000 | (shade << 16) | (shade << 8) | Math.min(255, shade + 10);
            ctx.blit(RenderPipelines.GUI_TEXTURED, LANTERN[i], Math.round(cx - lw / 2f), Math.round(cy - lh / 2f),
                0f, 0f, lw, lh, LW, LH, LW, LH, tint);
            if (hot) ctx.fill(Math.round(cx - lw / 2f) - 1, Math.round(cy - lh / 2f) - 2,
                Math.round(cx + lw / 2f) + 1, Math.round(cy - lh / 2f) - 1, GOLD);

            // Envol après validation : traînée d'étincelles dorées.
            if (now - launchAt[i] < 900 && rng.nextFloat() < 0.6f) {
                spawnSpark(cx + (rng.nextFloat() - 0.5f) * 12 * ls, cy + lh / 2f, 0, 12 + rng.nextFloat() * 10, GOLD, 0.7f);
            }
        }
    }

    /** Étiquette flottante au-dessus de la lanterne survolée. */
    private void drawHoverLabel(GuiGraphicsExtractor ctx, Font f, int i, int[] prev) {
        StatDef d = StatDef.values()[i];
        String txt = d.arcadeLabel + "  " + snap.get(d) + (pending[i] > 0 ? " +" + pending[i] : "")
            + "  -  " + levelName(prev[i], snap.max()).toUpperCase(Locale.ROOT);
        Component c = RebornFont.arcade(txt);
        float s = 0.8f;
        int w = Math.round(f.width(c) * s) + 12, h = 12;
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        float cy = yOf(shownVal[i]) + (float) Math.sin(time * 1.3f + i * 1.1f) * 1.5f + off;
        int x = Math.round(laneX[i] - w / 2f), y = Math.round(cy - LH * ls / 2f - 18);
        x = Math.max(4, Math.min(this.width - w - 4, x));
        ctx.fill(x, y, x + w, y + h, 0xF0100C1A);
        frame(ctx, x, y, w, h, d.color);
        drawScaled(ctx, f, c, x + 6, y + 3, CREAM, s);
    }

    /** Contour 1 px (sans remplissage). */
    private static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        DrawHelpers.outlinedRect(ctx, x, y, w, h, 0, color);
    }

    private void blitGlow(GuiGraphicsExtractor ctx, float cx, float cy, float size, int argb) {
        int s = Math.round(size);
        if (s < 2 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - s / 2f), Math.round(cy - s / 2f),
            0f, 0f, s, s, 64, 64, 64, 64, argb);
    }

    /** Contour pointillé d'une lanterne (trapèze) — la place qu'elle prendra une fois validée. */
    private static void ghostOutline(GuiGraphicsExtractor ctx, float cx, float cy, int lw, int lh, int color) {
        int top = Math.round(cy - lh / 2f), bot = top + Math.round(lh * 0.84f);
        int x0 = Math.round(cx - lw * 0.48f), x1 = Math.round(cx + lw * 0.48f);
        int b0 = Math.round(cx - lw * 0.33f), b1 = Math.round(cx + lw * 0.33f);
        for (int x = x0; x < x1; x += 3) ctx.fill(x, top, x + 2, top + 1, color);
        for (int x = b0; x < b1; x += 3) ctx.fill(x, bot, x + 2, bot + 1, color);
        for (int y = top; y <= bot; y += 3) {
            float t = (y - top) / (float) Math.max(1, bot - top);
            int l = Math.round(x0 + (b0 - x0) * t), r = Math.round(x1 + (b1 - x1) * t);
            ctx.fill(l, y, l + 1, y + 2, color);
            ctx.fill(r - 1, y, r, y + 2, color);
        }
    }

    /* ---- village, particules ---- */

    private void drawVillage(GuiGraphicsExtractor ctx) {
        int vy = Math.round(villageTop + off * 0.75f);
        int tint = mix(0xFF462846, 0xFF120C18, night);
        ctx.blit(RenderPipelines.GUI_TEXTURED, VILLAGE, 0, vy, 0f, 0f, this.width, villageH, 480, 80, 480, 80, tint);
        float flick = 0.85f + 0.15f * (float) Math.sin((System.currentTimeMillis() - openedAt) / 230.0);
        int win = mix(0xFFFFC878, 0xFFBE6E38, night);
        ctx.blit(RenderPipelines.GUI_TEXTURED, WINDOWS, 0, vy, 0f, 0f, this.width, villageH, 480, 80, 480, 80,
            Colors.withAlpha(win, flick));
        ctx.fill(0, vy + villageH, this.width, this.height, tint);
        // Braises qui montent des fenêtres.
        if (night > 0.6f && rng.nextFloat() < 0.08f) {
            Particle p = new Particle();
            p.x = rng.nextFloat() * this.width;
            p.y = hillY - 4 - rng.nextFloat() * 8 + off * 0.75f;
            p.vx = (rng.nextFloat() - 0.5f) * 4;
            p.vy = -(6 + rng.nextFloat() * 8);
            p.max = p.life = 2.5f + rng.nextFloat() * 2f;
            p.color = 0xFFFFAA5A;
            p.ember = true;
            particles.add(p);
        }
    }

    private void spawnSpark(float x, float y, float vx, float vy, int color, float life) {
        Particle p = new Particle();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy;
        p.max = p.life = life;
        p.color = color;
        particles.add(p);
    }

    private void burst(float x, float y, int color) {
        for (int n = 0; n < 14; n++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float sp = 15 + rng.nextFloat() * 25;
            spawnSpark(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 10, n % 3 == 0 ? CREAM : color, 0.5f + rng.nextFloat() * 0.3f);
        }
    }

    private void updateParticles(GuiGraphicsExtractor ctx, float dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) { it.remove(); continue; }
            p.x += p.vx * dt + (p.ember ? (float) Math.sin(p.y / 9f) * 0.15f : 0f);
            p.y += p.vy * dt;
            if (!p.ember) p.vy += 30 * dt;
            float a = Math.min(1f, p.life / p.max * 1.4f) * (p.ember ? 0.7f : 1f);
            int s = p.ember ? 1 : (p.life > p.max * 0.5f ? 2 : 1);
            ctx.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x) + s, Math.round(p.y) + s, Colors.withAlpha(p.color, a));
        }
    }

    /* ---- flammes-esprits (points à répartir) ---- */

    /** Position de la j-ième flamme de réserve (à droite du pied de page). */
    private float[] spiritSlot(int j) {
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        float x = laneX1 - 64 - j * 12;
        float y = btnY + btnH / 2f + (float) Math.sin(time * 2.2f + j) * 2f + (1f - ui) * this.height * 0.32f;
        return new float[]{x, y};
    }

    private void drawSpirit(GuiGraphicsExtractor ctx, float x, float y, float alpha) {
        blitGlow(ctx, x, y + 2, 18, Colors.withAlpha(SPIRIT, 0.55f * alpha));
        ctx.blit(RenderPipelines.GUI_TEXTURED, HITODAMA, Math.round(x - 5), Math.round(y - 8), 0f, 0f, 10, 16, 10, 16,
            Colors.withAlpha(0xFFFFFFFF, alpha));
    }

    private void launchSpirit(int stat, boolean toLantern, int targetValue) {
        if (!layoutReady()) return;
        Flyer fl = new Flyer();
        float[] slot = spiritSlot(Math.max(0, remaining() - (toLantern ? 0 : 1)));
        float lx = laneX[stat], ly = yOf(targetValue) + off;
        fl.x0 = toLantern ? slot[0] : lx;
        fl.y0 = toLantern ? slot[1] : ly;
        fl.x1 = toLantern ? lx : slot[0];
        fl.y1 = toLantern ? ly : slot[1];
        fl.start = System.currentTimeMillis();
        fl.stat = stat;
        fl.toLantern = toLantern;
        flyers.add(fl);
        StatsSounds.spirit(toLantern ? 1.1f : 0.85f);
    }

    private void updateFlyers(GuiGraphicsExtractor ctx, float dt) {
        long now = System.currentTimeMillis();
        for (Iterator<Flyer> it = flyers.iterator(); it.hasNext(); ) {
            Flyer fl = it.next();
            float t = clamp01((now - fl.start) / 560f);
            float e = easeInOut(t);
            // courbe : arc qui monte entre départ et arrivée
            float mxp = (fl.x0 + fl.x1) / 2f, myp = Math.min(fl.y0, fl.y1) - 50;
            float x = (1 - e) * (1 - e) * fl.x0 + 2 * (1 - e) * e * mxp + e * e * fl.x1;
            float y = (1 - e) * (1 - e) * fl.y0 + 2 * (1 - e) * e * myp + e * e * fl.y1;
            drawSpirit(ctx, x, y, 1f);
            if (rng.nextFloat() < 0.7f) spawnSpark(x, y + 4, 0, 4, SPIRIT, 0.35f);
            if (t >= 1f) {
                if (fl.toLantern) {
                    burst(fl.x1, fl.y1, StatDef.values()[fl.stat].color);
                    StatsSounds.rise(snap.stats()[fl.stat] + pending[fl.stat]);
                }
                it.remove();
            }
        }
    }

    /* ---- véranda : rambarde, plaques ema, poutre, boutons ---- */

    private void drawDeck(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        int yOff = Math.round((1f - ui) * this.height * 0.32f);
        int mxl = mx, myl = my - yOff;   // souris dans le repère local de la véranda
        // Plancher laqué.
        int floorY = railY + 16;
        ctx.fill(0, floorY, this.width, this.height + 40, LACQ_D);
        for (int y = floorY + 2; y < this.height + 40; y += 7) ctx.fill(0, y, this.width, y + 1, LACQ_LINE);
        ctx.fill(0, floorY, this.width, floorY + 1, 0xFF8C282C);
        // Rambarde + poteaux.
        ctx.fill(0, railY, this.width, railY + 6, WOOD);
        ctx.fill(0, railY, this.width, railY + 1, WOOD_L);
        for (int x = 20; x < this.width; x += 80) ctx.fill(x, railY, x + 5, floorY, WOOD_D);

        if (tab == TAB_ATTR) {
            for (StatDef d : StatDef.values()) drawPlaque(ctx, f, d, prev, mxl, myl, yOff);
        }
        drawBeam(ctx, f, prev, mxl, myl);
        drawFooter(ctx, f, mxl, myl, yOff);
        if (!compact) {
            Component hint = RebornFont.arcade(tab == TAB_ATTR
                ? "CLIC LANTERNE : +1   CLIC DROIT : -1   MAJ : TOUT   1-6 : RACCOURCIS   ENTREE : VALIDER   ECHAP : FERMER"
                : "MOLETTE : DEFILER   TAB : ONGLET   ECHAP : FERMER");
            drawScaledCentered(ctx, f, hint, this.width / 2f, hintY, 0xFF9C6E6E, 0.7f);
        }
    }

    private void drawPlaque(GuiGraphicsExtractor ctx, Font f, StatDef d, int[] prev, int mx, int my, int yOff) {
        int i = d.ordinal();
        int cx = laneX[i];
        int px = cx - emaW / 2, py = railY + 6;
        float s = emaW / 92f;
        // cordelette jusqu'à la rambarde
        DrawHelpers.line(ctx, cx - 10, railY + 3, cx, py + 2, ROPE);
        DrawHelpers.line(ctx, cx + 10, railY + 3, cx, py + 2, ROPE);
        ctx.blit(RenderPipelines.GUI_TEXTURED, EMA, px, py, 0f, 0f, emaW, emaH, 92, 56, 92, 56);
        int pad = Math.round(6 * s);
        int sealS = Math.round(18 * s);
        int sy = py + Math.round(13 * s);
        ctx.blit(RenderPipelines.GUI_TEXTURED, SEAL[i], px + pad, sy, 0f, 0f, sealS, sealS, 18, 18, 18, 18);
        int tx = px + pad + sealS + 4;
        drawScaled(ctx, f, RebornFont.arcade(d.arcadeLabel), tx, sy + 1, PLAQUE_INK, compact ? 0.65f : 0.75f);
        Component vc = Component.literal(String.valueOf(snap.get(d)));
        int vy = sy + (compact ? 8 : 10);
        ctx.text(f, vc, tx, vy, 0xFF28160C, false);
        if (pending[i] > 0) drawScaled(ctx, f, Component.literal("+" + pending[i]), tx + f.width(vc) + 3, vy + 1, 0xFFAA6E14, 0.8f);
        // mini-jauge
        int gy = py + emaH - Math.round(10 * s);
        int segW = Math.max(2, Math.round(4 * s)), gap = Math.max(1, Math.round(1 * s));
        for (int k2 = 0; k2 < snap.max(); k2++) {
            int c = k2 < snap.get(d) ? d.color : (k2 < prev[i] ? GOLD : 0xFFBA9C70);
            int gx = px + pad + k2 * (segW + gap);
            ctx.fill(gx, gy, gx + segW, gy + Math.max(2, Math.round(3 * s)), c);
        }
        if (mx >= px && mx < px + emaW && my >= py && my < py + emaH - 14 * s) statTooltip(d, prev);

        // − / + laqués
        int btn = Math.max(8, Math.round(10 * s));
        int bx = px + emaW - pad - btn * 2 - 3, by = py + emaH - pad - btn;
        boolean canMinus = pending[i] > 0;
        boolean canPlus = remaining() > 0 && prev[i] < snap.max();
        stepButton(ctx, f, bx, by, btn, "−", canMinus, mx, my, false);
        stepButton(ctx, f, bx + btn + 3, by, btn, "+", canPlus, mx, my, true);
        if (my >= by && my < by + btn) {
            if (mx >= bx && mx < bx + btn) {
                stepTooltip(f, d, canMinus ? "Retirer un point en attente (Maj : tous)."
                    : "Rien en attente ici. Un point déjà validé ne se reprend qu'en réinitialisant.", canMinus);
            } else if (mx >= bx + btn + 3 && mx < bx + btn * 2 + 3) {
                stepTooltip(f, d, canPlus ? "Ajouter un point (Maj : autant que possible)."
                    : prev[i] >= snap.max() ? "Sommet atteint : cette stat ne peut plus monter."
                    : "Plus de points à répartir.", canPlus);
            }
        }
        final int idx = i;
        buttons.add(new Btn(bx, by + yOff, btn, btn, () -> step(idx, -1, clickShift)));
        buttons.add(new Btn(bx + btn + 3, by + yOff, btn, btn, () -> step(idx, +1, clickShift)));
    }

    private void stepButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int s, String label,
                            boolean enabled, int mx, int my, boolean primary) {
        boolean hov = enabled && mx >= x && mx < x + s && my >= y && my < y + s;
        int fill = !enabled ? 0xFFB4966E : primary ? (hov ? 0xFFC02A30 : 0xFF961E22) : (hov ? 0xFF7A2A30 : LACQ);
        int border = enabled ? GOLD : 0xFF96784E;
        ctx.fill(x, y, x + s, y + s, fill);
        frame(ctx, x, y, s, s, border);
        Component c = Component.literal(label);
        ctx.text(f, c, x + (s - f.width(c)) / 2 + 1, y + (s - 8) / 2 + 1, enabled ? CREAM : 0xFF785E44, false);
    }

    private void stepTooltip(Font f, StatDef d, String text, boolean enabled) {
        List<Component> lines = new ArrayList<>();
        lines.add(RebornFont.arcade(d.arcadeLabel));
        addWrapped(lines, f, text, enabled ? 0 : INK_DIM);
        tooltip = lines;
        tooltipColor = enabled ? d.color : INK_DIM;
    }

    /** +1 / −1 (Maj : tout ce qui est possible). Rien ne bouge → son de refus. */
    private void step(int i, int dir, boolean all) {
        int before = pending[i];
        if (dir > 0) {
            int room = Math.min(remaining(), snap.max() - snap.stats()[i] - pending[i]);
            pending[i] += all ? Math.max(0, room) : Math.min(1, Math.max(0, room));
        } else {
            pending[i] = all ? 0 : Math.max(0, pending[i] - 1);
        }
        if (pending[i] > before) {
            int target = snap.stats()[i] + pending[i];
            if (layoutReady()) launchSpirit(i, true, target);   // le carillon sonne à l'arrivée
            else StatsSounds.rise(target);
        } else if (pending[i] < before) {
            StatsSounds.lower();
            if (layoutReady()) launchSpirit(i, false, snap.stats()[i] + before);
        } else {
            StatsSounds.deny();
        }
    }

    private boolean layoutReady() { return skyBase > skyTop; }

    private void statTooltip(StatDef d, int[] prev) {
        StatsData.Levers lv = snap.levers();
        Font f = this.font;
        List<Component> lines = new ArrayList<>();
        lines.add(RebornFont.arcade(d.arcadeLabel));
        addWrapped(lines, f, d.motto, INK_DIM);
        lines.add(Component.literal(" "));
        for (String e : d.effects) addWrapped(lines, f, "• " + e, 0);
        lines.add(Component.literal(" "));
        int cur = snap.get(d);
        int raw = prev[d.ordinal()];
        String level = "Niveau : " + levelName(cur, snap.max());
        if (raw != cur) level += "  →  " + levelName(raw, snap.max()) + " une fois validé";
        addWrapped(lines, f, level, GOLD);
        if (raw >= snap.max()) {
            addWrapped(lines, f, "Tu as atteint le sommet de cette voie.", INK_DIM);
        } else if (raw >= lv.softCap()) {
            addWrapped(lines, f, "Au-dessus de la ligne du seuil, l'entraînement paie moins : chaque point rapporte moins que les précédents.", INK_DIM);
        } else {
            addWrapped(lines, f, "Sous la ligne du seuil, chaque point porte pleinement ses fruits.", INK_DIM);
        }
        addWrapped(lines, f, "Clic sur la lanterne : +1  ·  clic droit : −1", INK_DIM);
        tooltip = lines;
        tooltipColor = d.color;
    }

    /** Palier qualitatif d'une valeur de stat — jamais le chiffre dans l'infobulle. */
    private static String levelName(int v, int max) {
        if (v >= max) return "Légendaire";
        if (v >= 8) return "Maître";
        if (v >= 6) return "Expert";
        if (v >= 4) return "Aguerri";
        if (v >= 2) return "Initié";
        return "Novice";
    }

    /* ---- valeurs dérivées : gravées sur la poutre ---- */

    private void drawBeam(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        StatsData.Levers lv = snap.levers();
        int[] cur = snap.stats();
        record Tile(String label, double now, double next, String unit, boolean pct, int color, String help) {}
        List<Tile> tiles = List.of(
            new Tile("PV MAX", StatsMath.maxHp(lv, cur), StatsMath.maxHp(lv, prev), "", false,
                StatDef.VIGUEUR.color, "Ce que ton corps encaisse avant de tomber. Grandit avec la Vigueur."),
            new Tile("CHAKRA", StatsMath.maxChakra(lv, cur), StatsMath.maxChakra(lv, prev), "", false,
                StatDef.CHAKRA.color, "Ta réserve pour lancer des techniques. Grandit avec la stat Chakra."),
            new Tile("ENDURANCE", StatsMath.maxStamina(lv, cur), StatsMath.maxStamina(lv, prev), "", false,
                StatDef.TAIJUTSU.color, "Le souffle des coups, des esquives et des techniques physiques. Porté surtout par la Vigueur, un peu par le Taïjutsu."),
            new Tile("REGEN / 10S", StatsMath.regenPer10s(lv, cur), StatsMath.regenPer10s(lv, prev), "", false,
                StatDef.CHAKRA.color, "Ce que la méditation te rend. Une grande réserve et un bon Contrôle l'accélèrent."),
            new Tile("COUTS", -StatsMath.costReduction(lv, cur) * 100, -StatsMath.costReduction(lv, prev) * 100,
                "%", true, StatDef.CONTROLE.color, "L'allègement du prix de toutes tes techniques, porté par le Contrôle."),
            new Tile("CRITIQUE", StatsMath.crit(lv, cur) * 100, StatsMath.crit(lv, prev) * 100, "%", true,
                StatDef.CONTROLE.color, lv.critEnabled()
                    ? "La chance qu'une technique frappe plus fort qu'attendu. Portée par le Contrôle ; les coups simples n'en profitent pas."
                    : "Les coups critiques sont désactivés pour l'instant."));
        int bx0 = laneX0 - 20, bx1 = laneX1 + 20;
        ctx.fill(bx0, beamY, bx1, beamY + beamH, 0xFF240A0E);
        frame(ctx, bx0, beamY, bx1 - bx0, beamH, 0xFF782828);
        float colw = (bx1 - bx0) / 6f;
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int x = Math.round(bx0 + colw * i + 8);
            int ky = beamY + (beamH - 12) / 2;
            ctx.blit(RenderPipelines.GUI_TEXTURED, DK[i], x, ky, 0f, 0f, 12, 12, 12, 12, 12, 12);
            String now = t.pct() ? fmtSigned(t.now()) + t.unit() : fmtInt(t.now());
            Component nc = Component.literal(now);
            double diff = t.next() - t.now();
            boolean changed = Math.abs(diff) > 0.05;
            boolean good = !t.label().equals("COUTS") ? diff > 0 : diff < 0;
            int tx = x + 16;
            int vy;
            if (compact) {
                vy = beamY + 4;
            } else {
                drawScaled(ctx, f, RebornFont.arcade(t.label()), tx, beamY + 4, 0xFFAA8278, 0.7f);
                vy = beamY + 14;
            }
            ctx.text(f, nc, tx, vy, CREAM, false);
            if (changed) {
                String ds = (diff > 0 ? "+" : "") + (t.pct() ? fmt1(diff) + t.unit() : fmtInt(diff));
                drawScaled(ctx, f, Component.literal(ds), tx + f.width(nc) + 3, vy + 1, good ? Colors.SUCCESS : Colors.DANGER, 0.8f);
            }
            if (mx >= bx0 + colw * i && mx < bx0 + colw * (i + 1) && my >= beamY && my < beamY + beamH) {
                List<Component> lines = new ArrayList<>();
                lines.add(RebornFont.arcade(t.label()));
                addWrapped(lines, f, t.help(), 0);
                if (changed) {
                    addWrapped(lines, f, good ? "Ta répartition en attente l'améliore."
                        : "Ta répartition en attente la dégrade.", good ? Colors.SUCCESS : Colors.DANGER);
                }
                tooltip = lines;
                tooltipColor = t.color();
            }
        }
    }

    /* ---- techniques ---- */

    private void drawTechniques(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        StatsData.Levers lv = snap.levers();
        int[] cur = snap.stats();
        int y0 = rowsY;
        int y1 = techBottom;
        DrawHelpers.roundedOutlinedRectFull(ctx, rowX0 - 8, y0 - 6, rowX1 - rowX0 + 16, y1 - y0 + 12, 5,
            0xC00E0A18, 0x80963C32);
        List<StatsData.Tech> list = snap.techniques();
        if (list.isEmpty()) {
            Component e1 = Component.literal("Aucune technique apprise.");
            Component e2 = Component.literal("Les parchemins et les maîtres t'attendent.");
            int cx = (rowX0 + rowX1) / 2;
            ctx.text(f, e1, cx - f.width(e1) / 2, (y0 + y1) / 2 - 8, INK_SOFT, false);
            ctx.text(f, e2, cx - f.width(e2) / 2, (y0 + y1) / 2 + 4, INK_DIM, false);
            return;
        }
        int cardH = 30;
        int gap = 4;
        techContentH = list.size() * (cardH + gap);
        float maxScroll = Math.max(0, techContentH - (y1 - y0));
        techScroll = Math.max(0, Math.min(maxScroll, techScroll));

        ctx.enableScissor(rowX0 - 4, y0, rowX1 + 4, y1);
        int y = y0 - Math.round(techScroll);
        for (StatsData.Tech t : list) {
            if (y + cardH >= y0 && y <= y1) drawTechCard(ctx, f, t, lv, cur, prev, rowX0, y, rowX1 - rowX0, cardH, mx, my, y0, y1);
            y += cardH + gap;
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int trackH = y1 - y0;
            int thumbH = Math.max(12, (int) (trackH * (trackH / (float) techContentH)));
            int thumbY = y0 + (int) ((trackH - thumbH) * (techScroll / maxScroll));
            ctx.fill(rowX1 + 3, y0, rowX1 + 4, y1, Colors.withAlpha(0xFFFFFFFF, 0.08f));
            ctx.fill(rowX1 + 2, thumbY, rowX1 + 5, thumbY + thumbH, Colors.withAlpha(GOLD, 0.8f));
        }
    }

    private void drawTechCard(GuiGraphicsExtractor ctx, Font f, StatsData.Tech t, StatsData.Levers lv,
                              int[] cur, int[] prev, int x, int y, int w, int h, int mx, int my, int clipY0, int clipY1) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h && my >= clipY0 && my < clipY1;
        int tier = clampI(t.tier(), 0, 5);
        int tc = TIER_COLOR[tier];
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 4,
            Colors.withAlpha(0xFF000000, hov ? 0.45f : 0.32f), Colors.withAlpha(tc, hov ? 0.55f : 0.22f));

        // Pastille de rang.
        int chip = 18;
        int chx = x + 5, chy = y + (h - chip) / 2;
        DrawHelpers.roundedOutlinedRectFull(ctx, chx, chy, chip, chip, 3,
            Colors.withAlpha(tc, 0.18f), Colors.withAlpha(tc, 0.85f));
        Component letter = RebornFont.arcade(TIER_LETTER[tier]);
        ctx.text(f, letter, chx + (chip - f.width(letter)) / 2 + 1, chy + 5, tc, false);

        // Nom (+ nature) et lettres de scaling.
        int tx = chx + chip + 6;
        int rightBlock = 92;
        String name = ellipsize(f, t.name(), w - (tx - x) - rightBlock - 8);
        ctx.text(f, Component.literal(name), tx, y + 5, INK, false);
        if (!"NONE".equals(t.nature())) {
            int nc = natureColor(t.nature());
            DrawHelpers.disc(ctx, tx + f.width(Component.literal(name)) + 6, y + 9, 2, nc);
        }
        int sx = tx;
        for (Map.Entry<StatDef, String> e : t.scaling().entrySet()) {
            Component sc = RebornFont.arcade(e.getKey().shortLabel + " " + e.getValue());
            int sw = f.width(sc) + 6;
            DrawHelpers.roundedRectFull(ctx, sx, y + 17, sw, 10, 2, Colors.withAlpha(e.getKey().color, 0.18f));
            drawScaled(ctx, f, sc, sx + 3, y + 19, e.getKey().color, 0.8f);
            sx += (int) (sw * 0.9f) + 3;
        }
        if (!t.explicit()) {
            drawScaled(ctx, f, Component.literal("déduit"), sx + 2, y + 19, INK_DIM, 0.7f);
        }

        // Puissance et coût, avec variation.
        double pNow = StatsMath.power(lv, cur, t.scaling());
        double pNext = StatsMath.power(lv, prev, t.scaling());
        Component pc = Component.literal("×" + String.format(Locale.ROOT, "%.2f", pNext));
        int rx = x + w - 6;
        ctx.text(f, pc, rx - f.width(pc), y + 5, pNext > pNow + 1e-6 ? GOLD : INK, false);
        if (pNext > pNow + 1e-6) {
            Component up = Component.literal("▲");
            ctx.text(f, up, rx - f.width(pc) - f.width(up) - 2, y + 5, Colors.SUCCESS, false);
        }
        String costTxt;
        if (!t.castable()) {
            costTxt = "passive / apprise";
        } else {
            double c = StatsMath.cost(lv, prev, t);
            int casts = StatsMath.casts(lv, prev, t);
            costTxt = fmtInt(c) + (t.stamina() ? " end." : " ck") + " · " + casts + "×";
        }
        Component cc = Component.literal(costTxt);
        drawScaled(ctx, f, cc, rx - f.width(cc) * 0.8f, y + 18, INK_DIM, 0.8f);

        if (hov) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(t.name()));
            addWrapped(lines, f, "Rang " + t.rank() + " · " + t.category()
                + ("NONE".equals(t.nature()) ? "" : " · " + t.nature()), INK_DIM);
            lines.add(Component.literal(" "));
            if (!t.scaling().isEmpty()) {
                addWrapped(lines, f, "Sa puissance dépend de :", 0);
                for (Map.Entry<StatDef, String> e : t.scaling().entrySet()) {
                    lines.add(Component.literal("  " + e.getKey().displayName + " — " + gradeWords(e.getValue()))
                        .withColor(e.getKey().color & 0xFFFFFF));
                }
                if (!t.explicit()) addWrapped(lines, f, "(profil déduit de sa famille de techniques)", INK_DIM);
            }
            if (pNext > pNow + 1e-6) addWrapped(lines, f, "Ta répartition en attente la renforce.", Colors.SUCCESS);
            if (t.castable()) {
                lines.add(Component.literal(" "));
                addWrapped(lines, f, (t.stamina() ? "Se paie en endurance" : "Se paie en chakra")
                    + " ; le Contrôle et la maîtrise l'allègent.", 0);
                addWrapped(lines, f, "À réserve pleine : " + castWords(StatsMath.casts(lv, prev, t)) + ".", 0);
            }
            addWrapped(lines, f, "Maîtrise : " + masteryWords(t.mastery()) + ".", GOLD);
            tooltip = lines;
            tooltipColor = tc;
        }
    }

    /** Lettre de scaling → intensité, en mots. */
    private static String gradeWords(String g) {
        return switch (g == null ? "" : g.trim().toUpperCase(Locale.ROOT)) {
            case "S" -> "énormément";
            case "A" -> "fortement";
            case "B" -> "nettement";
            case "C" -> "un peu";
            case "D" -> "à peine";
            default -> "légèrement";
        };
    }

    private static String castWords(int casts) {
        if (casts <= 0) return "ta réserve ne suffit pas encore";
        if (casts == 1) return "un seul lancer";
        if (casts <= 4) return "quelques lancers";
        if (casts <= 9) return "de nombreux lancers";
        return "presque à volonté";
    }

    private static String masteryWords(int m) {
        if (m <= 0) return "jamais pratiquée";
        if (m < 25) return "balbutiante";
        if (m < 50) return "en progrès";
        if (m < 75) return "solide";
        if (m < 100) return "presque parfaite";
        return "parfaite";
    }

    /* ---- pied de page ---- */

    private void drawFooter(GuiGraphicsExtractor ctx, Font f, int mx, int my, int yOff) {
        int h = btnH;
        int y = btnY;
        int cx = this.width / 2;
        int pend = pendingTotal();
        // VALIDER (à droite du centre)
        String vl = pend > 0 ? "VALIDER (" + pend + ")" : "VALIDER";
        int vw = f.width(RebornFont.arcade(vl)) + 20;
        int vx = cx + 4;
        actionButton(ctx, f, vx, y, vw, h, vl, pend > 0, true, mx, my);
        buttons.add(new Btn(vx, y + yOff, vw, h, this::validate));
        if (mx >= vx && mx < vx + vw && my >= y && my < y + h) {
            List<Component> lines = new ArrayList<>();
            lines.add(RebornFont.arcade("VALIDER"));
            addWrapped(lines, f, pend > 0
                ? "Les lanternes s'envolent jusqu'à leur nouvelle hauteur. Les points validés restent acquis jusqu'à une réinitialisation."
                : "Pose d'abord des points avec + ou en cliquant une lanterne.", pend > 0 ? 0 : INK_DIM);
            tooltip = lines;
            tooltipColor = pend > 0 ? GOLD : INK_DIM;
        }
        // ANNULER (à gauche du centre)
        int aw = f.width(RebornFont.arcade("ANNULER")) + 16;
        int ax = cx - 4 - aw;
        actionButton(ctx, f, ax, y, aw, h, "ANNULER", pend > 0, false, mx, my);
        buttons.add(new Btn(ax, y + yOff, aw, h, () -> {
            if (pendingTotal() > 0) { java.util.Arrays.fill(pending, 0); StatsSounds.lower(); }
            else StatsSounds.deny();
        }));
        if (pend > 0 && mx >= ax && mx < ax + aw && my >= y && my < y + h) {
            List<Component> lines = new ArrayList<>();
            lines.add(RebornFont.arcade("ANNULER"));
            addWrapped(lines, f, "Retire tous les points en attente. Rien de ce qui est déjà validé ne bouge.", 0);
            tooltip = lines;
            tooltipColor = INK_SOFT;
        }
        // Points à répartir : flammes-esprits en réserve + compteur.
        int rem = remaining();
        int unspent = snap.unspent();
        String pts = unspent < 0 ? "SUR-ALLOUE (" + unspent + ")" : rem + (rem > 1 ? " POINTS" : " POINT");
        Component pc = RebornFont.arcade(pts);
        int px = laneX1 - f.width(pc) + 10;
        ctx.text(f, pc, px, y + (h - 7) / 2, unspent < 0 ? Colors.DANGER : rem > 0 ? SPIRIT : INK_DIM, false);
        int shown = Math.min(rem, 8);
        for (int j = 0; j < shown; j++) {
            float[] s = spiritSlot(j);
            drawSpirit(ctx, s[0], s[1] - yOff, 1f);
        }
        int zoneX0 = Math.round(spiritSlot(Math.max(0, shown - 1))[0]) - 8;
        if (mx >= zoneX0 && mx < laneX1 + 10 && my >= y - 6 && my < y + h + 4) {
            List<Component> lines = new ArrayList<>();
            lines.add(RebornFont.arcade(unspent < 0 ? "SUR-ALLOUE" : "POINTS A REPARTIR"));
            if (unspent < 0) {
                addWrapped(lines, f, "Plus de points posés que ton rang n'en accorde. Un membre du staff doit corriger ta fiche.", 0);
            } else {
                addWrapped(lines, f, "Chaque flamme-esprit est un point à poser : elle file vers la lanterne choisie.", 0);
                addWrapped(lines, f, "Chaque passage de rang t'en accorde de nouvelles ; le staff peut aussi en offrir.", INK_DIM);
            }
            tooltip = lines;
            tooltipColor = unspent < 0 ? Colors.DANGER : SPIRIT;
        }
        // RÉINITIALISER (gauche) — double clic de confirmation ; un jeton hors staff.
        if (snap.canRespec()) {
            boolean usable = respecUsable();
            boolean armed = usable && System.currentTimeMillis() - respecArmedAt < 3000L;
            String rl = armed ? "CONFIRMER ?" : "REINITIALISER";
            int rw = f.width(RebornFont.arcade(rl)) + 14;
            int rx = laneX0 - 20;
            boolean hov = mx >= rx && mx < rx + rw && my >= y && my < y + h;
            ctx.fill(rx, y, rx + rw, y + h, armed ? 0xFF7A1A1E : (usable && hov ? 0xFF4A1A20 : 0xFF2C0C10));
            frame(ctx, rx, y, rw, h, armed ? Colors.DANGER : usable ? 0xFF8C5050 : 0xFF4A2A2A);
            ctx.text(f, RebornFont.arcade(rl), rx + 7, y + (h - 7) / 2, armed ? CREAM : usable ? 0xFFD2AAAA : 0xFF7A5A5A, false);
            buttons.add(new Btn(rx, y + yOff, rw, h, this::respec));
            if (!snap.respecFree()) {
                int n = snap.respecTokens();
                Component tk = Component.literal(n + " jeton" + (n > 1 ? "s" : ""));
                drawScaled(ctx, f, tk, rx + rw + 5, y + (h - 6) / 2f, n > 0 ? GOLD : 0xFF7A5A5A, 0.8f);
            }
            if (hov) {
                List<Component> lines = new ArrayList<>();
                lines.add(RebornFont.arcade("REINITIALISER"));
                addWrapped(lines, f, "Les lanternes redescendent au village : tes stats reviennent à la base et tu répartis à nouveau.", 0);
                if (snap.spent() <= 0) {
                    addWrapped(lines, f, "Rien à reprendre pour l'instant.", INK_DIM);
                } else if (snap.respecFree()) {
                    addWrapped(lines, f, "Gratuit pour toi.", Colors.SUCCESS);
                } else {
                    int n = snap.respecTokens();
                    addWrapped(lines, f, "Consomme un jeton de réinitialisation.", GOLD);
                    addWrapped(lines, f, n > 0
                        ? "Tu en possèdes " + n + "."
                        : "Tu n'en as aucun : les jetons s'obtiennent en boutique.", n > 0 ? 0 : Colors.DANGER);
                }
                if (armed) addWrapped(lines, f, "Clique encore pour confirmer.", Colors.DANGER);
                tooltip = lines;
                tooltipColor = usable ? Colors.DANGER : INK_DIM;
            }
        }
    }

    private void actionButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h, String label,
                              boolean enabled, boolean primary, int mx, int my) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = !enabled ? 0xFF2C0C10 : primary ? (hov ? 0xFFC02A30 : 0xFFAA1E22) : (hov ? 0xFF7A2A30 : LACQ);
        int border = !enabled ? 0xFF4A2A2A : primary ? GOLD : 0xFFA06E50;
        if (enabled && primary && hov) blitGlow(ctx, x + w / 2f, y + h / 2f, w + 24, 0x66FFBE6E);
        ctx.fill(x, y, x + w, y + h, fill);
        frame(ctx, x, y, w, h, border);
        Component c = RebornFont.arcade(label);
        ctx.text(f, c, x + (w - f.width(c)) / 2, y + (h - 7) / 2, enabled ? CREAM : 0xFF7A5A5A, false);
    }

    /* ----------------------------------------------------------- actions */

    private void validate() {
        if (pendingTotal() <= 0) { StatsSounds.deny(); return; }
        StringBuilder sb = new StringBuilder("alloc:");
        boolean first = true;
        long now = System.currentTimeMillis();
        for (StatDef d : StatDef.values()) {
            int p = pending[d.ordinal()];
            if (p <= 0) continue;
            if (!first) sb.append(',');
            sb.append(d.key()).append('=').append(p);
            first = false;
            optimistic[d.ordinal()] += p;   // la lanterne s'envole sans attendre le serveur
            launchAt[d.ordinal()] = now;
        }
        if (ClientPlayNetworking.canSend(StatsPayload.ID)) {
            ClientPlayNetworking.send(new StatsPayload(sb.toString()));
        } else {
            java.util.Arrays.fill(optimistic, 0);   // hors ligne : rien à valider pour de vrai
        }
        java.util.Arrays.fill(pending, 0);
        StatsSounds.validate();
    }

    /** Réinitialisation possible maintenant : quelque chose à reprendre, et gratuit ou un jeton en poche. */
    private boolean respecUsable() {
        return snap.spent() > 0 && (snap.respecFree() || snap.respecTokens() > 0);
    }

    private void respec() {
        if (!respecUsable()) { StatsSounds.deny(); return; }
        long now = System.currentTimeMillis();
        if (now - respecArmedAt > 3000L) {
            respecArmedAt = now;
            StatsSounds.tab();
            return;
        }
        respecArmedAt = 0L;
        java.util.Arrays.fill(pending, 0);
        if (ClientPlayNetworking.canSend(StatsPayload.ID)) ClientPlayNetworking.send(new StatsPayload("respec"));
        StatsSounds.respec();
    }

    /* ------------------------------------------------------------ entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        int mx = (int) e.x(), my = (int) e.y();
        clickShift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (!interactive()) { skipIntro(); return true; }
        // Lanternes : clic = +1, clic droit = −1.
        if (tab == TAB_ATTR && hoveredLantern >= 0 && (e.button() == 0 || e.button() == 1)) {
            step(hoveredLantern, e.button() == 0 ? +1 : -1, clickShift);
            return true;
        }
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        for (Btn b : new ArrayList<>(buttons)) {
            if (b.hit(mx, my)) { b.act().run(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == TAB_TECH) {
            techScroll -= (float) (verticalAmount * 18.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (k == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (!interactive()) { skipIntro(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { validate(); return true; }
        if (k == GLFW.GLFW_KEY_TAB) { switchTab(tab == TAB_ATTR ? TAB_TECH : TAB_ATTR); return true; }
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_6) {
            step(k - GLFW.GLFW_KEY_1, shift ? -1 : +1, false);
            return true;
        }
        if (fr.reborn.hud.keybind.HudKeybinds.STATS != null && fr.reborn.hud.keybind.HudKeybinds.STATS.matches(e)) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    /* ------------------------------------------------------------ helpers */

    private void drawTooltip(GuiGraphicsExtractor ctx, Font f, List<Component> lines, int mx, int my, int accent) {
        int w = 0;
        for (Component c : lines) w = Math.max(w, f.width(c));
        w += 14;
        int h = lines.size() * 10 + 8;
        int x = mx + 12, y = my + 8;
        if (x + w > this.width - 4) x = mx - 12 - w;
        if (y + h > this.height - 4) y = this.height - 4 - h;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 4,
            Colors.withAlpha(0xFF0E0A18, 0.95f), Colors.withAlpha(accent, 0.7f));
        ctx.fill(x + 1, y + 3, x + 3, y + h - 3, accent);
        int ly = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            // La couleur de style d'une ligne (addWrapped) prime sur INK_SOFT.
            ctx.text(f, lines.get(i), x + 8, ly, i == 0 ? accent : INK_SOFT, false);
            ly += 10;
        }
    }

    /** Largeur max du texte d'une infobulle, en pixels GUI. */
    private static final int TOOLTIP_TEXT_W = 190;

    /**
     * Ajoute {@code text} découpé au mot pour tenir dans {@link #TOOLTIP_TEXT_W}.
     * {@code color} = 0 : couleur par défaut de l'infobulle.
     */
    private static void addWrapped(List<Component> out, Font f, String text, int color) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && f.width(Component.literal(candidate)) > TOOLTIP_TEXT_W) {
                out.add(styled(line.toString(), color));
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) out.add(styled(line.toString(), color));
    }

    private static Component styled(String s, int color) {
        return color == 0 ? Component.literal(s) : Component.literal(s).withColor(color & 0xFFFFFF);
    }

    private static void drawScaled(GuiGraphicsExtractor ctx, Font f, Component c, float x, float y, int color, float scale) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.text(f, c, 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    private static void drawScaledCentered(GuiGraphicsExtractor ctx, Font f, Component c, float cx, float y, int color, float scale) {
        drawScaled(ctx, f, c, cx - f.width(c) * scale / 2f, y, color, scale);
    }

    private static String ellipsize(Font f, String s, int maxW) {
        if (maxW <= 8 || f.width(Component.literal(s)) <= maxW) return s;
        String e = s;
        while (e.length() > 1 && f.width(Component.literal(e + "…")) > maxW) e = e.substring(0, e.length() - 1);
        return e + "…";
    }

    private static int natureColor(String n) {
        return switch (n) {
            case "KATON" -> 0xFFFF6B35;
            case "SUITON" -> 0xFF3B82F6;
            case "FUTON" -> 0xFFA7F3D0;
            case "DOTON" -> 0xFFC08A4B;
            case "RAITON" -> 0xFFFDE047;
            default -> 0xFFCBD5E1;
        };
    }

    private static String fmtInt(double v) {
        return String.format(Locale.FRANCE, "%,d", Math.round(v)).replace(' ', ' ').replace(' ', ' ');
    }

    private static String fmtSigned(double v) {
        return (v > 0.05 ? "+" : "") + fmt1(v);
    }

    private static String fmt1(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static int clampI(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
