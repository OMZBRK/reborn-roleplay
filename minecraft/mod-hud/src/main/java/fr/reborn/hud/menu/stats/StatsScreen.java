package fr.reborn.hud.menu.stats;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.CloudIntro;
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
 * lanternes jusque-là. Sous l'horizon : nom, valeur et boutons −/+ de chaque stat, puis les
 * valeurs dérivées.
 *
 * <p>Animations : nuages qui s'écartent ({@link CloudIntro}), lanternes qui s'allument une
 * à une puis montent depuis le village, balancement, flamme qui vacille, étoiles qui
 * scintillent, braises, étincelles à chaque point posé. Sons : {@link StatsSounds}.
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
    private static final int GROUND = 0xFF120C16;
    private static final int TRAIL = 0xFF786050;

    private static final int[] TIER_COLOR = {0xFF9CA3AF, 0xFFE5E7EB, 0xFF4ADE80, 0xFF38BDF8, 0xFFD9A95E, 0xFFC084FC};
    private static final String[] TIER_LETTER = {"E", "D", "C", "B", "A", "S"};

    private static final Identifier SKY = tex("sky");
    private static final Identifier VILLAGE = tex("village");
    private static final Identifier GLOW = tex("glow");
    private static final Identifier[] LANTERN = {tex("lantern_0"), tex("lantern_1"), tex("lantern_2"),
        tex("lantern_3"), tex("lantern_4"), tex("lantern_5")};
    private static final int LW = 30, LH = 36;

    private static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/stats/" + n + ".png");
    }

    private StatsData.Snapshot snap;
    private int seenVersion = -1;
    private final int[] pending = new int[StatDef.values().length];
    /** Points validés mais pas encore confirmés par le serveur : la lanterne monte tout de suite. */
    private final int[] optimistic = new int[StatDef.values().length];
    private int tab = lastTab;
    private final long openedAt = System.currentTimeMillis();
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

    private final CloudIntro intro = new CloudIntro(1100, 0x0E0A1C, 0xFF8C80BE);
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private final float[][] stars = new float[48][4]; // x, y (0..1), vitesse, phase

    // Layout
    private boolean compact;
    private int top, subY, tabsY, headerBottom, hintY, footY, derivedY, ground, skyTop, skyBase;
    private int laneX0, laneX1;
    private final int[] laneX = new int[6];
    private int closeX, closeY;
    private final int closeS = 12;
    private int tabAttrX0, tabAttrX1, tabTechX0, tabTechX1;
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

    /** Infobulle à dessiner en dernier (au-dessus de tout). */
    private List<Component> tooltip = null;
    private int tooltipColor = GOLD;

    public StatsScreen() {
        super(Component.literal("Fiche shinobi"));
        resync();
        Random r = new Random(0x57A2L);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat() * 0.6f;
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

    /* --------------------------------------------------------------- layout */

    private void layout() {
        compact = this.height < 400;
        top = compact ? 5 : 10;
        subY = top + (compact ? 12 : 16);
        tabsY = subY + (compact ? 11 : 13);
        headerBottom = tabsY + 12;
        hintY = compact ? -1 : this.height - 12;
        footY = compact ? this.height - 20 : hintY - 22;
        derivedY = footY - (compact ? 24 : 34);
        ground = derivedY - (compact ? 44 : 54);
        skyTop = headerBottom + (compact ? 22 : 34);
        skyBase = ground - (compact ? 34 : 44);

        int span = Math.min(this.width - 100, 720);
        laneX0 = this.width / 2 - span / 2;
        laneX1 = this.width / 2 + span / 2;
        for (int i = 0; i < 6; i++) laneX[i] = laneX0 + span * (2 * i + 1) / 12;

        closeX = this.width - closeS - 10;
        closeY = top;

        rowX0 = laneX0;
        rowX1 = laneX1;
        rowsY = headerBottom + 12;
        techBottom = ground - 26;
    }

    private float yOf(float v) {
        return skyBase - (skyBase - skyTop) * v / Math.max(1, snap.max());
    }

    /* ---------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        // Ciel tramé (pixel-art), étiré sur l'écran.
        ctx.blit(RenderPipelines.GUI_TEXTURED, SKY, 0, 0, 0f, 0f, this.width, this.height, 480, 270, 480, 270);
        // Étoiles qui scintillent.
        for (float[] s : stars) {
            float a = 0.25f + 0.75f * Math.max(0f, (float) Math.sin(time * s[2] + s[3]));
            int x = (int) (s[0] * this.width), y = (int) (s[1] * this.height);
            ctx.fill(x, y, x + 1, y + 1, Colors.withAlpha(0xFFF4F0FF, a));
        }
        // Nuages de nuit qui dérivent lentement.
        float w = this.width + 260;
        CloudIntro.drawCloud(ctx, 2, (time * 3f + 40) % w - 180, skyTop - 10, 2f, 0x668C80BE);
        CloudIntro.drawCloud(ctx, 0, (time * 2f + w * 0.55f) % w - 180, skyTop + 50, 2f, 0x558C80BE);
        CloudIntro.drawCloud(ctx, 1, (time * 4.5f + w * 0.3f) % w - 180, headerBottom - 6, 1.5f, 0x448C80BE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (StatsData.version() != seenVersion) resync();
        layout();
        buttons.clear();
        tooltip = null;
        Font f = this.font;
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        int[] prev = previewStats();

        drawHeader(ctx, f, mouseX, mouseY);

        if (tab == TAB_ATTR) {
            drawScale(ctx, f);
            drawLanterns(ctx, f, prev, mouseX, mouseY, dt);
        } else {
            drawTechniques(ctx, f, prev, mouseX, mouseY);
        }
        drawVillage(ctx);
        updateParticles(ctx, dt);
        if (tab == TAB_ATTR) drawLanes(ctx, f, prev, mouseX, mouseY);
        drawDerived(ctx, f, prev, mouseX, mouseY);
        drawFooter(ctx, f, mouseX, mouseY);

        if (!compact) {
            Component hint = RebornFont.arcade(tab == TAB_ATTR
                ? "CLIC LANTERNE : +1   CLIC DROIT : -1   MAJ : TOUT   1-6 : RACCOURCIS   ENTREE : VALIDER   ECHAP : FERMER"
                : "MOLETTE : DEFILER   TAB : ONGLET   ECHAP : FERMER");
            drawScaledCentered(ctx, f, hint, this.width / 2f, hintY, Colors.withAlpha(INK_DIM, 0.85f), 0.7f);
        }

        // Survol : un tic discret quand on change de cible.
        Object hoverKey = hoveredLantern >= 0 ? (Object) ("L" + hoveredLantern) : (tooltip != null ? tooltip.get(0).getString() : null);
        if (hoverKey != null && !hoverKey.equals(lastHoverKey) && intro.done()) StatsSounds.hover();
        lastHoverKey = hoverKey;

        if (tooltip != null) drawTooltip(ctx, f, tooltip, mouseX, mouseY, tooltipColor);
        intro.draw(ctx, this.width, this.height);
    }

    /* ---- en-tête ---- */

    private void drawHeader(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        float cx = this.width / 2f;
        drawScaledCentered(ctx, f, RebornFont.arcade("FICHE SHINOBI"), cx, top, CREAM, compact ? 1.1f : 1.35f);
        StringBuilder sub = new StringBuilder(snap.name());
        if (!snap.clan().isBlank() && !snap.clan().equalsIgnoreCase("None")) sub.append(' ').append(snap.clan());
        sub.append("  -  ").append(snap.rank());
        if (!snap.village().isBlank()) sub.append("  -  ").append(snap.village());
        if (!snap.natures().isEmpty()) sub.append("  -  ").append(String.join(" / ", snap.natures()));
        if (!snap.nextRank().isBlank()) {
            sub.append("  -  vers ").append(snap.nextRank()).append(" (+").append(snap.levers().pointsPerRank()).append(')');
        }
        Component subC = Component.literal(sub.toString());
        ctx.text(f, subC, (int) (cx - f.width(subC) / 2f), subY, INK_SOFT, false);

        // Onglets : deux petites plaques, l'active éclairée.
        Component a = RebornFont.arcade("ATTRIBUTS");
        Component t = RebornFont.arcade("TECHNIQUES (" + snap.techniques().size() + ")");
        int aw = f.width(a) + 14, tw = f.width(t) + 14;
        tabAttrX0 = (int) cx - aw - 3;
        tabAttrX1 = tabAttrX0 + aw;
        tabTechX0 = (int) cx + 3;
        tabTechX1 = tabTechX0 + tw;
        tabPlate(ctx, f, a, tabAttrX0, aw, tab == TAB_ATTR, mx, my);
        tabPlate(ctx, f, t, tabTechX0, tw, tab == TAB_TECH, mx, my);
        buttons.add(new Btn(tabAttrX0, tabsY, aw, 12, () -> switchTab(TAB_ATTR)));
        buttons.add(new Btn(tabTechX0, tabsY, tw, 12, () -> switchTab(TAB_TECH)));

        if (!StatsData.fromServer()) {
            Component demo = RebornFont.arcade("APERCU HORS LIGNE");
            ctx.text(f, demo, closeX - 8 - f.width(demo), closeY + 2, Colors.withAlpha(Colors.WARNING, 0.8f), false);
        }
        boolean hov = mx >= closeX && mx < closeX + closeS && my >= closeY && my < closeY + closeS;
        DrawHelpers.roundedOutlinedRectFull(ctx, closeX, closeY, closeS, closeS, 2,
            hov ? Colors.withAlpha(ACC, 0.6f) : Colors.withAlpha(0xFF000000, 0.35f),
            Colors.withAlpha(hov ? GOLD : 0xFF6A5A80, 0.8f));
        Component x = Component.literal("✕");
        ctx.text(f, x, closeX + (closeS - f.width(x)) / 2 + 1, closeY + 2, CREAM, false);
        buttons.add(new Btn(closeX, closeY, closeS, closeS, this::onClose));
    }

    private void tabPlate(GuiGraphicsExtractor ctx, Font f, Component c, int x, int w, boolean active, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= tabsY && my < tabsY + 12;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, tabsY, w, 12, 2,
            active ? 0xFF962222 : (hov ? 0xFF2E2440 : 0xFF221C2C),
            active ? GOLD : 0xFF463C54);
        ctx.text(f, c, x + (w - f.width(c)) / 2, tabsY + 3, active ? CREAM : (hov ? INK_SOFT : INK_DIM), false);
    }

    private void switchTab(int t) {
        if (tab != t) { tab = t; StatsSounds.tab(); }
    }

    /* ---- ciel : échelle + lanternes ---- */

    private void drawScale(GuiGraphicsExtractor ctx, Font f) {
        int max = snap.max();
        int x = laneX0 - 14;
        for (int v = 0; v <= max; v += 2) {
            int y = Math.round(yOf(v));
            ctx.fill(x, y, x + 6, y + 1, 0xFF6E5A84);
            Component c = Component.literal(String.valueOf(v));
            drawScaled(ctx, f, c, x - 3 - f.width(c) * 0.75f, y - 3, 0xFF6E5A84, 0.75f);
        }
        int sy = Math.round(yOf((float) snap.levers().softCap()));
        for (int xx = laneX0 - 4; xx < laneX1 + 4; xx += 5) ctx.fill(xx, sy, xx + 2, sy + 1, 0x99B48290);
        drawScaled(ctx, f, RebornFont.arcade("SEUIL"), laneX1 + 8, sy - 3, 0xFFB48290, 0.75f);
    }

    private void drawLanterns(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my, float dt) {
        long now = System.currentTimeMillis();
        float time = (now - openedAt) / 1000f;
        float k = 1f - (float) Math.exp(-dt * 5f);
        hoveredLantern = -1;
        for (StatDef d : StatDef.values()) {
            int i = d.ordinal();
            // Allumage échelonné, puis montée depuis le village.
            long lightAt = openedAt + 650 + i * 140L;
            float ignite = Math.max(0f, Math.min(1f, (now - lightAt) / 260f));
            if (ignite > 0f && !lit[i]) { lit[i] = true; StatsSounds.light(i); }
            float target = lit[i] ? snap.stats()[i] + optimistic[i] : 0f;
            shownVal[i] += (target - shownVal[i]) * k;
            boolean hasGhost = pending[i] > 0;
            ghostVal[i] += ((hasGhost ? prev[i] : shownVal[i]) - ghostVal[i]) * Math.min(1f, k * 1.6f);
            ghostAlpha[i] += ((hasGhost ? 1f : 0f) - ghostAlpha[i]) * Math.min(1f, k * 1.6f);

            float bob = (float) Math.sin(time * 1.3f + i * 1.1f) * 1.5f;
            float sway = (float) Math.sin(time * 0.9f + i) * 1f;
            float cx = laneX[i] + sway;
            float cy = yOf(shownVal[i]) + bob;
            float base = yOf(0) + 6;
            if (!lit[i]) cy = Math.max(cy, base - 4);

            boolean hot = intro.done() && Math.abs(mx - cx) <= 18 && my >= cy - 22 && my <= cy + 22;
            if (hot) hoveredLantern = i;
            hoverGlow[i] += ((hot ? 1f : 0f) - hoverGlow[i]) * Math.min(1f, k * 2f);

            // Fil de lumière jusqu'au village.
            for (int yy = (int) (cy + LH / 2f + 3); yy < ground - 18; yy += 3) {
                float wx = cx + (float) Math.sin(yy / 14f + i + time * 0.6f) * 2f;
                ctx.fill(Math.round(wx), yy, Math.round(wx) + 1, yy + 1, Colors.withAlpha(TRAIL, 0.55f * ignite));
            }
            // Silhouette dorée de la hauteur visée (points en attente).
            if (ghostAlpha[i] > 0.02f) {
                float gy = yOf(ghostVal[i]) + bob;
                float pulse = 0.65f + 0.35f * (float) Math.sin(time * 4f + i);
                ghostOutline(ctx, cx, gy, Colors.withAlpha(GOLD, ghostAlpha[i] * pulse));
                drawScaledCentered(ctx, f, Component.literal("+" + pending[i]), cx + 22, gy - 4,
                    Colors.withAlpha(GOLD, ghostAlpha[i]), 0.75f);
            }
            // Halo : couleur de la stat, grandit avec la valeur ; flamme qui vacille.
            float frac = shownVal[i] / Math.max(1, snap.max());
            float flicker = 0.85f + 0.15f * (float) Math.sin(time * 9f + i * 2.3f) * (float) Math.sin(time * 5.3f + i);
            float gs = 44 + 40 * frac + 10 * hoverGlow[i];
            int glowC = Colors.withAlpha(d.color, (0.22f + 0.38f * frac + 0.2f * hoverGlow[i]) * flicker * ignite);
            blitGlow(ctx, cx, cy + 4, gs, glowC);
            blitGlow(ctx, cx, cy + 10, 26, Colors.withAlpha(0xFFFFBE6E, 0.45f * flicker * ignite));
            // Lanterne (éteinte = sombre, s'allume en fondu).
            int shade = (int) (90 + 165 * ignite);
            int tint = 0xFF000000 | (shade << 16) | (shade << 8) | shade;
            ctx.blit(RenderPipelines.GUI_TEXTURED, LANTERN[i], Math.round(cx - LW / 2f), Math.round(cy - LH / 2f),
                0f, 0f, LW, LH, LW, LH, tint);

            // Envol après validation : traînée d'étincelles dorées.
            if (now - launchAt[i] < 900 && rng.nextFloat() < 0.6f) {
                spawnSpark(cx + (rng.nextFloat() - 0.5f) * 10, cy + LH / 2f, 0, 12 + rng.nextFloat() * 10, GOLD, 0.7f);
            }
            if (hot) statTooltip(d, prev);
        }
    }

    private void blitGlow(GuiGraphicsExtractor ctx, float cx, float cy, float size, int argb) {
        int s = Math.round(size);
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - s / 2f), Math.round(cy - s / 2f),
            0f, 0f, s, s, 64, 64, 64, 64, argb);
    }

    /** Contour d'une lanterne (trapèze) — la place qu'elle prendra une fois validée. */
    private static void ghostOutline(GuiGraphicsExtractor ctx, float cx, float cy, int color) {
        int top = Math.round(cy - LH / 2f), bot = top + 30;
        int x0 = Math.round(cx - 14), x1 = Math.round(cx + 14);
        int b0 = Math.round(cx - 10), b1 = Math.round(cx + 10);
        ctx.fill(x0, top, x1, top + 1, color);
        ctx.fill(b0, bot, b1, bot + 1, color);
        for (int y = top; y <= bot; y++) {
            float t = (y - top) / 30f;
            int l = Math.round(x0 + (b0 - x0) * t), r = Math.round(x1 + (b1 - x1) * t);
            ctx.fill(l, y, l + 1, y + 1, color);
            ctx.fill(r - 1, y, r, y + 1, color);
        }
    }

    /* ---- village, particules ---- */

    private void drawVillage(GuiGraphicsExtractor ctx) {
        int vh = 56;
        int vy = ground - 26;
        ctx.blit(RenderPipelines.GUI_TEXTURED, VILLAGE, 0, vy, 0f, 0f, this.width, vh, 480, 56, 480, 56);
        ctx.fill(0, vy + vh, this.width, this.height, GROUND);
        // Braises qui montent des fenêtres.
        if (rng.nextFloat() < 0.08f) {
            Particle p = new Particle();
            p.x = rng.nextFloat() * this.width;
            p.y = ground - 8 - rng.nextFloat() * 10;
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
        for (int n = 0; n < 12; n++) {
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

    /* ---- sous l'horizon : nom, valeur, −/+ ---- */

    private void drawLanes(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        int btn = compact ? 9 : 11;
        for (StatDef d : StatDef.values()) {
            int i = d.ordinal();
            int cx = laneX[i];
            int y0 = ground + (compact ? 2 : 4);
            Component name = RebornFont.arcade(d.arcadeLabel);
            drawScaledCentered(ctx, f, name, cx, y0, d.color, compact ? 0.75f : 0.85f);
            String v = String.valueOf(snap.get(d));
            Component vc = Component.literal(v);
            int vy = y0 + (compact ? 9 : 11);
            if (pending[i] > 0) {
                Component pc = Component.literal("+" + pending[i]);
                int total = f.width(vc) + 3 + f.width(pc);
                ctx.text(f, vc, cx - total / 2, vy, CREAM, false);
                ctx.text(f, pc, cx - total / 2 + f.width(vc) + 3, vy, GOLD, false);
            } else {
                ctx.text(f, vc, cx - f.width(vc) / 2, vy, CREAM, false);
            }
            if (mx >= cx - 30 && mx < cx + 30 && my >= y0 - 2 && my < vy + 9) statTooltip(d, prev);

            int by = vy + (compact ? 11 : 13);
            int bx = cx - btn - 2;
            boolean canMinus = pending[i] > 0;
            boolean canPlus = remaining() > 0 && prev[i] < snap.max();
            stepButton(ctx, f, bx, by, btn, "−", canMinus, mx, my, false);
            stepButton(ctx, f, bx + btn + 4, by, btn, "+", canPlus, mx, my, true);
            if (my >= by && my < by + btn) {
                if (mx >= bx && mx < bx + btn) {
                    stepTooltip(f, d, canMinus ? "Retirer un point en attente (Maj : tous)."
                        : "Rien en attente ici. Un point déjà validé ne se reprend qu'en réinitialisant.", canMinus);
                } else if (mx >= bx + btn + 4 && mx < bx + btn * 2 + 4) {
                    stepTooltip(f, d, canPlus ? "Ajouter un point (Maj : autant que possible)."
                        : prev[i] >= snap.max() ? "Sommet atteint : cette stat ne peut plus monter."
                        : "Plus de points à répartir.", canPlus);
                }
            }
            final int idx = i;
            buttons.add(new Btn(bx, by, btn, btn, () -> step(idx, -1, false)));
            buttons.add(new Btn(bx + btn + 4, by, btn, btn, () -> step(idx, +1, false)));
        }
    }

    private void stepButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int s, String label,
                            boolean enabled, int mx, int my, boolean primary) {
        boolean hov = enabled && mx >= x && mx < x + s && my >= y && my < y + s;
        int fill = !enabled ? 0x40000000
            : primary ? (hov ? 0xFFC02A30 : 0xFF962222)
            : (hov ? 0xFF3A2E4C : 0xFF261E32);
        int border = enabled ? (primary ? GOLD : 0xFF8A7C9A) : 0x30FFFFFF;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, s, s, 2, fill, border);
        Component c = Component.literal(label);
        ctx.text(f, c, x + (s - f.width(c)) / 2 + 1, y + (s - 8) / 2 + 1, enabled ? CREAM : INK_DIM, false);
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
            StatsSounds.rise(target);
            if (layoutReady()) burst(laneX[i], yOf(target), StatDef.values()[i].color);
        } else if (pending[i] < before) {
            StatsSounds.lower();
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

    /* ---- valeurs dérivées ---- */

    private void drawDerived(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
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
        int span = laneX1 - laneX0;
        float colw = span / 6f;
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            float cx = laneX0 + colw * (i + 0.5f);
            drawScaledCentered(ctx, f, RebornFont.arcade(t.label()), cx, derivedY, INK_DIM, 0.72f);
            String now = t.pct() ? fmtSigned(t.now()) + t.unit() : fmtInt(t.now());
            Component nc = Component.literal(now);
            double diff = t.next() - t.now();
            boolean changed = Math.abs(diff) > 0.05;
            boolean good = !t.label().equals("COUTS") ? diff > 0 : diff < 0;
            int vy = derivedY + (compact ? 8 : 10);
            if (changed) {
                String ds = (diff > 0 ? "+" : "") + (t.pct() ? fmt1(diff) + t.unit() : fmtInt(diff));
                Component dc = Component.literal(ds);
                int total = f.width(nc) + 3 + Math.round(f.width(dc) * 0.8f);
                int x = Math.round(cx - total / 2f);
                ctx.text(f, nc, x, vy, CREAM, false);
                drawScaled(ctx, f, dc, x + f.width(nc) + 3, vy + 1, good ? Colors.SUCCESS : Colors.DANGER, 0.8f);
            } else {
                ctx.text(f, nc, Math.round(cx - f.width(nc) / 2f), vy, CREAM, false);
            }
            if (mx >= cx - colw / 2 && mx < cx + colw / 2 && my >= derivedY - 2 && my < vy + 10) {
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
        hoveredLantern = -1;
        StatsData.Levers lv = snap.levers();
        int[] cur = snap.stats();
        int y0 = rowsY;
        int y1 = techBottom;
        DrawHelpers.roundedOutlinedRectFull(ctx, rowX0 - 8, y0 - 6, rowX1 - rowX0 + 16, y1 - y0 + 12, 5,
            0xB80E0A18, 0x60463C54);
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

    private void drawFooter(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        int h = compact ? 13 : 15;
        int y = footY;
        int cx = this.width / 2;
        int pend = pendingTotal();
        // VALIDER (à droite du centre)
        String vl = pend > 0 ? "VALIDER (" + pend + ")" : "VALIDER";
        int vw = f.width(RebornFont.arcade(vl)) + 18;
        int vx = cx + 4;
        actionButton(ctx, f, vx, y, vw, h, vl, pend > 0, true, mx, my);
        buttons.add(new Btn(vx, y, vw, h, this::validate));
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
        int aw = f.width(RebornFont.arcade("ANNULER")) + 14;
        int ax = cx - 4 - aw;
        actionButton(ctx, f, ax, y, aw, h, "ANNULER", pend > 0, false, mx, my);
        buttons.add(new Btn(ax, y, aw, h, () -> {
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
        // Points à répartir (droite)
        int rem = remaining();
        int unspent = snap.unspent();
        String pts = unspent < 0 ? "SUR-ALLOUE (" + unspent + ")" : rem + (rem > 1 ? " POINTS A REPARTIR" : " POINT A REPARTIR");
        Component pc = RebornFont.arcade(pts);
        float pulse = rem > 0 ? 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 300.0) : 1f;
        int pcol = unspent < 0 ? Colors.DANGER : rem > 0 ? Colors.withAlpha(GOLD, pulse) : INK_DIM;
        int px = laneX1 - f.width(pc);
        ctx.text(f, pc, px, y + (h - 7) / 2, pcol, false);
        if (mx >= px && mx < laneX1 && my >= y && my < y + h) {
            List<Component> lines = new ArrayList<>();
            lines.add(RebornFont.arcade(unspent < 0 ? "SUR-ALLOUE" : "POINTS A REPARTIR"));
            if (unspent < 0) {
                addWrapped(lines, f, "Plus de points posés que ton rang n'en accorde. Un membre du staff doit corriger ta fiche.", 0);
            } else {
                addWrapped(lines, f, "Chaque passage de rang t'en accorde de nouveaux ; le staff peut aussi en offrir.", 0);
                addWrapped(lines, f, rem > 0
                    ? "Pose-les avec + (ou un clic sur une lanterne) puis valide."
                    : pendingTotal() > 0 ? "Tout est placé — il ne reste qu'à valider."
                    : "Tout est réparti. Le prochain rang t'en apportera d'autres.", 0);
            }
            tooltip = lines;
            tooltipColor = unspent < 0 ? Colors.DANGER : GOLD;
        }
        // RÉINITIALISER (gauche) — double clic de confirmation ; un jeton hors staff.
        if (snap.canRespec()) {
            boolean usable = respecUsable();
            boolean armed = usable && System.currentTimeMillis() - respecArmedAt < 3000L;
            String rl = armed ? "CONFIRMER ?" : "REINITIALISER";
            int rw = f.width(RebornFont.arcade(rl)) + 14;
            int rx = laneX0;
            boolean hov = mx >= rx && mx < rx + rw && my >= y && my < y + h;
            DrawHelpers.roundedOutlinedRectFull(ctx, rx, y, rw, h, 2,
                armed ? Colors.withAlpha(Colors.DANGER, 0.35f)
                    : Colors.withAlpha(0xFF000000, usable && hov ? 0.4f : 0.25f),
                Colors.withAlpha(armed ? Colors.DANGER : 0xFFFFFFFF, armed ? 0.9f : usable ? 0.18f : 0.08f));
            ctx.text(f, RebornFont.arcade(rl), rx + 7, y + (h - 7) / 2, armed ? CREAM : usable ? INK_SOFT : INK_DIM, false);
            buttons.add(new Btn(rx, y, rw, h, this::respec));
            if (!snap.respecFree()) {
                int n = snap.respecTokens();
                Component tk = Component.literal(n + " jeton" + (n > 1 ? "s" : ""));
                drawScaled(ctx, f, tk, rx + rw + 5, y + (h - 6) / 2f, n > 0 ? GOLD : INK_DIM, 0.8f);
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
        int fill = !enabled ? 0x40000000
            : primary ? (hov ? 0xFFC02A30 : 0xFFA02228)
            : (hov ? 0xFF2E2440 : 0xC0221C2C);
        int border = !enabled ? 0x20FFFFFF : primary ? GOLD : 0xFF8A7C9A;
        if (enabled && primary && hov) blitGlow(ctx, x + w / 2f, y + h / 2f, w + 20, 0x66FFBE6E);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 2, fill, border);
        Component c = RebornFont.arcade(label);
        ctx.text(f, c, x + (w - f.width(c)) / 2, y + (h - 7) / 2, enabled ? CREAM : INK_DIM, false);
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
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (!intro.done()) return true;
        // Lanternes : clic = +1, clic droit = −1.
        if (tab == TAB_ATTR && hoveredLantern >= 0 && (e.button() == 0 || e.button() == 1)) {
            step(hoveredLantern, e.button() == 0 ? +1 : -1, shift);
            return true;
        }
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        // Maj-clic sur −/+ = tout / rien : on refait le hit-test des boutons de voie.
        if (shift && tab == TAB_ATTR) {
            int btn = compact ? 9 : 11;
            for (StatDef d : StatDef.values()) {
                int i = d.ordinal();
                int y0 = ground + (compact ? 2 : 4);
                int by = y0 + (compact ? 9 : 11) + (compact ? 11 : 13);
                int bx = laneX[i] - btn - 2;
                if (my >= by && my < by + btn) {
                    if (mx >= bx && mx < bx + btn) { step(i, -1, true); return true; }
                    if (mx >= bx + btn + 4 && mx < bx + btn * 2 + 4) { step(i, +1, true); return true; }
                }
            }
        }
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
