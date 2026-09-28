package fr.reborn.hud.menu.stats;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Fiche shinobi</b> — l'écran des six stats (SPEC_STATS_SERVICE, option B).
 *
 * <p>Deux panneaux translucides sur le monde, dans la charte crimson/or du mod :
 * <ul>
 *   <li><b>Gauche — PROFIL</b> : radar hexagonal des six stats (une par sommet),
 *       anneau doré au seuil du soft cap, polygone courant en crimson et
 *       allocation en attente en or ; rang, prochain palier, natures.</li>
 *   <li><b>Droite — ATTRIBUTS</b> : une rangée par stat (10 pastilles, cran doré au
 *       soft cap, pastilles au-delà atténuées = rendement réduit), boutons −/+,
 *       puis six tuiles de valeurs dérivées avec leur variation en direct.</li>
 *   <li><b>Droite — TECHNIQUES</b> : chaque technique connue avec ses lettres de
 *       scaling, son multiplicateur de puissance, son coût et le nombre de
 *       lancers à réserve pleine — l'effet d'un point se lit avant de le poser.</li>
 * </ul>
 *
 * <p>L'allocation reste <b>locale</b> jusqu'à VALIDER ; les valeurs affichées
 * sont recalculées par {@link StatsMath} avec les leviers du serveur, qui reste
 * seul juge ({@code alloc:} refusé → la fiche se resynchronise).
 *
 * <p>Clavier : 1-6 = +1 sur la stat (Maj = −1), Entrée = valider, Tab = onglet.
 */
public class StatsScreen extends Screen {

    private static final int TAB_ATTR = 0, TAB_TECH = 1;
    private static int lastTab = TAB_ATTR;

    private static final int ACC = Colors.ACCENT;
    private static final int GOLD = Colors.GOLD;
    private static final int PANEL_FILL = Colors.withAlpha(0xFF120C0D, 0.62f);
    private static final int PANEL_BORDER = Colors.withAlpha(ACC, 0.40f);
    private static final int INK = Colors.withAlpha(Colors.FOREGROUND, 0.92f);
    private static final int INK_SOFT = Colors.withAlpha(Colors.FOREGROUND_SUBTLE, 0.85f);
    private static final int INK_DIM = Colors.withAlpha(Colors.FOREGROUND, 0.42f);

    private static final int[] TIER_COLOR = {0xFF9CA3AF, 0xFFE5E7EB, 0xFF4ADE80, 0xFF38BDF8, 0xFFD9A95E, 0xFFC084FC};
    private static final String[] TIER_LETTER = {"E", "D", "C", "B", "A", "S"};

    private StatsData.Snapshot snap;
    private int seenVersion = -1;
    private final int[] pending = new int[StatDef.values().length];
    private int tab = lastTab;
    private final long openedAt = System.currentTimeMillis();
    private long respecArmedAt = 0L;
    private float techScroll = 0f;
    private int techContentH = 0;
    /** Valeurs affichées par le radar — interpolées vers la cible (ouverture / +1). */
    private final float[] shown = new float[StatDef.values().length];

    // Layout
    private int margin, top, bottom, contentY, panelH;
    private int leftX, leftW, rightX, rightW;
    private int closeX, closeY;
    private final int closeS = 14;
    private int tabY, tabAttrX0, tabAttrX1, tabTechX0, tabTechX1;
    private int rowsY, rowH, rowX0, rowX1;
    private int tilesY, tileH;
    private int footY;
    private final List<Btn> buttons = new ArrayList<>();

    private record Btn(int x, int y, int w, int h, Runnable act) {
        boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Infobulle à dessiner en dernier (au-dessus de tout). */
    private List<Component> tooltip = null;
    private int tooltipColor = GOLD;

    public StatsScreen() {
        super(Component.literal("Fiche shinobi"));
        resync();
    }

    /* ------------------------------------------------------------ données */

    private void resync() {
        this.snap = StatsData.get();
        this.seenVersion = StatsData.version();
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
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        lastTab = tab;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /* --------------------------------------------------------------- layout */

    private void layout() {
        margin = Math.max(10, (int) (this.width * 0.04f));
        top = Math.max(8, (int) (this.height * 0.05f));
        bottom = this.height - Math.max(12, (int) (this.height * 0.06f));
        contentY = top + 30;
        panelH = bottom - contentY;

        int avail = this.width - margin * 2;
        leftW = clampI((int) (avail * 0.38f), 150, 250);
        leftX = margin;
        rightX = leftX + leftW + 10;
        rightW = this.width - margin - rightX;

        closeX = this.width - margin - closeS;
        closeY = top;

        tabY = contentY + 8;
        rowX0 = rightX + 10;
        rowX1 = rightX + rightW - 10;
        rowsY = tabY + 20;
        footY = bottom - 22;
        tileH = 24;
        int tilesBlock = tileH * 2 + 4;
        int rowsAvail = footY - 8 - tilesBlock - 8 - rowsY;
        rowH = clampI(rowsAvail / 6, 16, 24);
        tilesY = rowsY + rowH * 6 + 8;
    }

    /* ---------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float appear = appear();
        ctx.fillGradient(0, 0, this.width, this.height,
            Colors.withAlpha(0xFF0A0608, 0.55f * appear + 0.05f),
            Colors.withAlpha(0xFF050304, 0.78f * appear + 0.05f));
        // Grand « 忍 » (shinobi) en filigrane, derrière le profil.
        Component kanji = Component.literal("忍");
        float scale = Math.max(6f, this.height / 22f);
        ctx.pose().pushMatrix();
        ctx.pose().translate(this.width * 0.5f - this.font.width(kanji) * scale / 2f, this.height * 0.5f - 4.5f * scale);
        ctx.pose().scale(scale, scale);
        ctx.text(this.font, kanji, 0, 0, Colors.withAlpha(ACC, 0.06f * appear), false);
        ctx.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (StatsData.version() != seenVersion) resync();
        layout();
        buttons.clear();
        tooltip = null;
        Font f = this.font;
        int[] prev = previewStats();

        animateRadar(prev);
        drawHeader(ctx, f, mouseX, mouseY);

        panel(ctx, leftX, contentY, leftW, panelH);
        panel(ctx, rightX, contentY, rightW, panelH);

        drawProfile(ctx, f, prev, mouseX, mouseY);
        drawTabs(ctx, f, mouseX, mouseY);
        drawPointsBadge(ctx, f);
        if (tab == TAB_ATTR) {
            drawRows(ctx, f, prev, mouseX, mouseY);
            drawTiles(ctx, f, prev, mouseX, mouseY);
        } else {
            drawTechniques(ctx, f, prev, mouseX, mouseY);
        }
        drawFooter(ctx, f, mouseX, mouseY);

        Component hint = RebornFont.arcade(tab == TAB_ATTR
            ? "CLIC + / -   MAJ : TOUT   1-6 : RACCOURCIS   ENTREE : VALIDER   ECHAP : FERMER"
            : "MOLETTE : DEFILER   TAB : ONGLET   ECHAP : FERMER");
        drawScaledCentered(ctx, f, hint, this.width / 2f, bottom + 5, INK_DIM, 0.7f);

        if (tooltip != null) drawTooltip(ctx, f, tooltip, mouseX, mouseY, tooltipColor);
    }

    private float appear() {
        return Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
    }

    private void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        float a = appear();
        int dy = (int) ((1f - a) * 8);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y + dy, w, h, 7, PANEL_FILL, PANEL_BORDER);
        ctx.fill(x + 8, y + dy + 1, x + w - 8, y + dy + 2, Colors.withAlpha(ACC, 0.30f));
    }

    /* ---- en-tête ---- */

    private void drawHeader(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        ctx.text(f, RebornFont.arcade("FICHE SHINOBI"), margin, top + 2, GOLD, false);
        String sub = snap.name()
            + (snap.clan().isBlank() || snap.clan().equalsIgnoreCase("None") ? "" : " " + snap.clan())
            + "  ·  " + snap.rank()
            + (snap.village().isBlank() ? "" : "  ·  " + snap.village());
        ctx.text(f, Component.literal(sub), margin, top + 14, INK_SOFT, false);
        if (!StatsData.fromServer()) {
            Component demo = RebornFont.arcade("APERCU HORS LIGNE");
            ctx.text(f, demo, closeX - 10 - f.width(demo), top + 4, Colors.withAlpha(Colors.WARNING, 0.8f), false);
        }
        boolean hov = mx >= closeX && mx < closeX + closeS && my >= closeY && my < closeY + closeS;
        DrawHelpers.roundedOutlinedRectFull(ctx, closeX, closeY, closeS, closeS, 3,
            hov ? Colors.withAlpha(ACC, 0.5f) : Colors.withAlpha(0xFF000000, 0.35f),
            Colors.withAlpha(hov ? Colors.ACCENT_HOVER : ACC, 0.7f));
        Component x = Component.literal("✕");
        ctx.text(f, x, closeX + (closeS - f.width(x)) / 2 + 1, closeY + 3, INK, false);
        buttons.add(new Btn(closeX, closeY, closeS, closeS, () -> { RebornSounds.uiClick(); onClose(); }));
        // Filet crimson sous l'en-tête.
        int ly = contentY - 6;
        DrawHelpers.horizontalGradient(ctx, margin, ly, this.width - margin * 2, 1,
            Colors.withAlpha(ACC, 0.0f), Colors.withAlpha(ACC, 0.55f));
    }

    /* ---- profil / radar ---- */

    private void animateRadar(int[] target) {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 450f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        for (int i = 0; i < shown.length; i++) {
            float goal = target[i] * ease;
            shown[i] += (goal - shown[i]) * 0.25f;
            if (Math.abs(goal - shown[i]) < 0.01f) shown[i] = goal;
        }
    }

    private void drawProfile(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        ctx.text(f, RebornFont.arcade("PROFIL"), leftX + 10, contentY + 8, INK_DIM, false);

        int infoH = 58;
        int radius = clampI(Math.min(leftW / 2 - 30, (panelH - infoH - 44) / 2), 28, 90);
        int cx = leftX + leftW / 2;
        int cy = contentY + 22 + radius + 10;
        int max = snap.max();

        // Anneaux (tous les 2 points) — l'anneau du soft cap en or.
        double soft = snap.levers().softCap();
        for (int ring = 2; ring <= max; ring += 2) {
            polygonOutline(ctx, cx, cy, radius / (float) max, fill6(ring),
                1, Colors.withAlpha(0xFFFFFFFF, ring == max ? 0.22f : 0.08f));
        }
        polygonOutline(ctx, cx, cy, radius / (float) max, fill6((float) soft),
            1, Colors.withAlpha(GOLD, 0.45f));
        // Rayons.
        for (int i = 0; i < 6; i++) {
            float[] v = vertex(cx, cy, radius, i, 1f);
            line(ctx, cx, cy, v[0], v[1], 1, Colors.withAlpha(0xFFFFFFFF, 0.10f));
        }

        // Polygone courant (serveur) puis aperçu (en attente, or).
        float[] cur = new float[6];
        float[] shownCur = new float[6];
        for (int i = 0; i < 6; i++) {
            cur[i] = snap.stats()[i];
            shownCur[i] = Math.min(shown[i], cur[i]);
        }
        boolean hasPending = pendingTotal() > 0;
        if (hasPending) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 220.0);
            polygonFill(ctx, cx, cy, radius / (float) max, shown, Colors.withAlpha(GOLD, 0.16f + 0.10f * pulse));
            polygonOutline(ctx, cx, cy, radius / (float) max, shown, 1, Colors.withAlpha(GOLD, 0.85f));
        }
        polygonFill(ctx, cx, cy, radius / (float) max, shownCur, Colors.withAlpha(ACC, 0.40f));
        polygonOutline(ctx, cx, cy, radius / (float) max, shownCur, 2, Colors.withAlpha(Colors.ACCENT_HOVER, 0.95f));

        // Sommets + étiquettes.
        for (StatDef d : StatDef.values()) {
            int i = d.ordinal();
            float[] p = vertex(cx, cy, radius / (float) max, i, shown[i]);
            DrawHelpers.disc(ctx, Math.round(p[0]), Math.round(p[1]), 2, d.color);
            float[] lab = vertex(cx, cy, radius + 13, i, 1f);
            Component name = RebornFont.arcade(d.shortLabel);
            String val = String.valueOf(prev[i]);
            Component vc = Component.literal(val);
            int w = f.width(name) + 3 + f.width(vc);
            int lx = Math.round(lab[0]) - w / 2;
            int ly = Math.round(lab[1]) - 4;
            ctx.text(f, name, lx, ly, d.color, false);
            ctx.text(f, vc, lx + f.width(name) + 3, ly, pending[i] > 0 ? GOLD : INK, false);
            if (mx >= lx - 2 && mx < lx + w + 2 && my >= ly - 2 && my < ly + 10) statTooltip(d, prev);
        }

        // Rang / prochain palier / natures.
        int y = cy + radius + 22;
        int x = leftX + 12;
        int w = leftW - 24;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, 20, 4,
            Colors.withAlpha(0xFF000000, 0.35f), Colors.withAlpha(GOLD, 0.35f));
        ctx.text(f, RebornFont.arcade("RANG"), x + 6, y + 6, INK_DIM, false);
        Component rank = Component.literal(snap.rank());
        ctx.text(f, rank, x + 34, y + 6, GOLD, false);
        if (!snap.nextRank().isBlank()) {
            Component next = Component.literal("→ " + snap.nextRank() + "  +" + snap.levers().pointsPerRank() + " pts");
            ctx.text(f, next, x + w - 6 - f.width(next), y + 6, INK_DIM, false);
        }
        int ny = y + 26;
        if (ny + 14 <= contentY + panelH - 4) {
            if (snap.natures().isEmpty()) {
                ctx.text(f, Component.literal("Nature : test de la feuille à passer"), x, ny + 3, INK_DIM, false);
            } else {
                int nx = x;
                for (String n : snap.natures()) {
                    Component c = RebornFont.arcade(n);
                    int cw = f.width(c) + 10;
                    int col = natureColor(n);
                    DrawHelpers.roundedOutlinedRectFull(ctx, nx, ny, cw, 14, 3,
                        Colors.withAlpha(col, 0.18f), Colors.withAlpha(col, 0.7f));
                    ctx.text(f, c, nx + 5, ny + 3, col, false);
                    nx += cw + 4;
                }
            }
        }
    }

    private static float[] fill6(float v) {
        float[] a = new float[6];
        java.util.Arrays.fill(a, v);
        return a;
    }

    /** Sommet i (0 = haut, sens horaire) à {@code value × unit} du centre. */
    private static float[] vertex(float cx, float cy, float unit, int i, float value) {
        double ang = -Math.PI / 2 + i * Math.PI / 3;
        return new float[]{(float) (cx + Math.cos(ang) * unit * value), (float) (cy + Math.sin(ang) * unit * value)};
    }

    private void polygonOutline(GuiGraphicsExtractor ctx, float cx, float cy, float unit, float[] values,
                                int thick, int color) {
        for (int i = 0; i < 6; i++) {
            float[] a = vertex(cx, cy, unit, i, values[i]);
            float[] b = vertex(cx, cy, unit, (i + 1) % 6, values[(i + 1) % 6]);
            line(ctx, a[0], a[1], b[0], b[1], thick, color);
        }
    }

    /** Remplissage par balayage (pair-impair) — une ligne horizontale par pixel. */
    private void polygonFill(GuiGraphicsExtractor ctx, float cx, float cy, float unit, float[] values, int color) {
        float[] xs = new float[6], ys = new float[6];
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 6; i++) {
            float[] p = vertex(cx, cy, unit, i, values[i]);
            xs[i] = p[0];
            ys[i] = p[1];
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        float[] hits = new float[6];
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            float sy = y + 0.5f;
            int n = 0;
            for (int i = 0; i < 6; i++) {
                int j = (i + 1) % 6;
                float y0 = ys[i], y1 = ys[j];
                if ((y0 <= sy && y1 > sy) || (y1 <= sy && y0 > sy)) {
                    hits[n++] = xs[i] + (sy - y0) / (y1 - y0) * (xs[j] - xs[i]);
                }
            }
            java.util.Arrays.sort(hits, 0, n);
            for (int k = 0; k + 1 < n; k += 2) {
                int x0 = Math.round(hits[k]), x1 = Math.round(hits[k + 1]);
                if (x1 > x0) ctx.fill(x0, y, x1, y + 1, color);
            }
        }
    }

    /** Segment épais : un seul rectangle tourné (pose 2D), pas un pixel par pas. */
    private static void line(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1, int thick, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x0, y0);
        ctx.pose().rotate((float) Math.atan2(dy, dx));
        ctx.pose().translate(0f, -thick / 2f);
        ctx.fill(0, 0, Math.round(len), thick, color);
        ctx.pose().popMatrix();
    }

    /* ---- onglets + badge ---- */

    private void drawTabs(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        Component a = RebornFont.arcade("ATTRIBUTS");
        Component t = RebornFont.arcade("TECHNIQUES (" + snap.techniques().size() + ")");
        tabAttrX0 = rightX + 12;
        tabAttrX1 = tabAttrX0 + f.width(a);
        tabTechX0 = tabAttrX1 + 16;
        tabTechX1 = tabTechX0 + f.width(t);
        tab(ctx, f, a, tabAttrX0, tab == TAB_ATTR, mx, my);
        tab(ctx, f, t, tabTechX0, tab == TAB_TECH, mx, my);
        buttons.add(new Btn(tabAttrX0 - 2, tabY - 3, tabAttrX1 - tabAttrX0 + 4, 14, () -> switchTab(TAB_ATTR)));
        buttons.add(new Btn(tabTechX0 - 2, tabY - 3, tabTechX1 - tabTechX0 + 4, 14, () -> switchTab(TAB_TECH)));
    }

    private void tab(GuiGraphicsExtractor ctx, Font f, Component c, int x, boolean active, int mx, int my) {
        boolean hov = mx >= x - 2 && mx < x + f.width(c) + 2 && my >= tabY - 3 && my < tabY + 11;
        ctx.text(f, c, x, tabY, active ? INK : hov ? INK_SOFT : INK_DIM, false);
        if (active) ctx.fill(x, tabY + 10, x + f.width(c), tabY + 11, ACC);
    }

    private void switchTab(int t) {
        if (tab != t) { tab = t; RebornSounds.charNav(); }
    }

    private void drawPointsBadge(GuiGraphicsExtractor ctx, Font f) {
        int rem = remaining();
        int unspent = snap.unspent();
        Component label = RebornFont.arcade(unspent < 0 ? "SUR-ALLOUE" : "POINTS");
        String num = unspent < 0 ? String.valueOf(unspent) : String.valueOf(rem);
        int r = 9;
        int cx = rowX1 - r;
        int cy = tabY + 3;
        boolean glow = rem > 0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        if (glow) DrawHelpers.disc(ctx, cx, cy, r + 3, Colors.withAlpha(GOLD, 0.10f + 0.12f * pulse));
        DrawHelpers.disc(ctx, cx, cy, r, glow ? Colors.withAlpha(GOLD, 0.95f)
            : unspent < 0 ? Colors.withAlpha(Colors.DANGER, 0.9f) : Colors.withAlpha(0xFF3A2C14, 0.9f));
        Component n = Component.literal(num);
        ctx.text(f, n, cx - f.width(n) / 2 + 1, cy - 3, glow ? 0xFF1A1208 : INK, false);
        ctx.text(f, label, cx - r - 6 - f.width(label), cy - 3, glow ? GOLD : INK_DIM, false);
    }

    /* ---- attributs ---- */

    private void drawRows(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        StatsData.Levers lv = snap.levers();
        int max = snap.max();
        int nameW = 58;
        int btn = Math.min(14, rowH - 4);
        for (StatDef d : StatDef.values()) {
            int i = d.ordinal();
            int y = rowsY + i * rowH;
            int midY = y + rowH / 2;
            boolean hovRow = mx >= rowX0 && mx < rowX1 && my >= y && my < y + rowH;
            if (hovRow) ctx.fill(rowX0 - 4, y, rowX1 + 4, y + rowH, Colors.withAlpha(0xFFFFFFFF, 0.04f));
            ctx.fill(rowX0, y + 3, rowX0 + 2, y + rowH - 3, d.color);
            ctx.text(f, RebornFont.arcade(d.arcadeLabel), rowX0 + 6, midY - 4, INK_SOFT, false);

            // Valeur (+ en attente).
            int vx = rowX0 + 6 + nameW;
            Component v = Component.literal(String.valueOf(snap.get(d)));
            ctx.text(f, v, vx, midY - 4, INK, false);
            if (pending[i] > 0) {
                ctx.text(f, Component.literal("+" + pending[i]), vx + f.width(v) + 2, midY - 4, GOLD, false);
            }

            // Pastilles.
            int px0 = vx + 26;
            int px1 = rowX1 - btn * 2 - 8;
            int gap = 2;
            int pw = Math.max(4, (px1 - px0 - gap * (max - 1)) / max);
            int ph = Math.min(8, rowH - 8);
            int py = midY - ph / 2;
            float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 200.0);
            for (int k = 1; k <= max; k++) {
                int x = px0 + (k - 1) * (pw + gap);
                boolean over = k > lv.softCap();
                int fill;
                if (k <= snap.get(d)) fill = Colors.withAlpha(d.color, over ? 0.62f : 0.95f);
                else if (k <= prev[i]) fill = Colors.withAlpha(GOLD, 0.45f + 0.45f * pulse);
                else fill = Colors.withAlpha(0xFF000000, 0.45f);
                ctx.fill(x, py, x + pw, py + ph, fill);
                if (k > snap.get(d)) ctx.fill(x, py + ph - 1, x + pw, py + ph, Colors.withAlpha(0xFFFFFFFF, 0.08f));
                if (k == (int) lv.softCap() && k < max) {
                    int tx = x + pw + gap / 2;
                    ctx.fill(tx, py - 3, tx + 1, py + ph + 3, Colors.withAlpha(GOLD, 0.8f));
                }
            }
            if (mx >= px0 && mx < px1 && my >= y && my < y + rowH) statTooltip(d, prev);
            if (mx >= rowX0 && mx < vx + 20 && my >= y && my < y + rowH) statTooltip(d, prev);

            // − / +
            int bx = rowX1 - btn * 2 - 3;
            int by = midY - btn / 2;
            boolean canMinus = pending[i] > 0;
            boolean canPlus = remaining() > 0 && prev[i] < max;
            stepButton(ctx, f, bx, by, btn, "−", canMinus, mx, my, false);
            stepButton(ctx, f, bx + btn + 3, by, btn, "+", canPlus, mx, my, true);
            final int idx = i;
            buttons.add(new Btn(bx, by, btn, btn, () -> step(idx, -1, false)));
            buttons.add(new Btn(bx + btn + 3, by, btn, btn, () -> step(idx, +1, false)));
        }
    }

    private void stepButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int s, String label,
                            boolean enabled, int mx, int my, boolean primary) {
        boolean hov = enabled && mx >= x && mx < x + s && my >= y && my < y + s;
        int fill = !enabled ? Colors.withAlpha(0xFF000000, 0.25f)
            : hov ? Colors.withAlpha(primary ? GOLD : ACC, 0.55f)
            : Colors.withAlpha(primary ? Colors.GOLD_SOFT : Colors.ACCENT_SOFT, 0.85f);
        int border = enabled ? Colors.withAlpha(primary ? GOLD : ACC, 0.8f) : Colors.withAlpha(0xFFFFFFFF, 0.08f);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, s, s, 3, fill, border);
        Component c = Component.literal(label);
        ctx.text(f, c, x + (s - f.width(c)) / 2 + 1, y + (s - 8) / 2, enabled ? INK : INK_DIM, false);
    }

    /** +1 / −1 (Maj : tout ce qui est possible). */
    private void step(int i, int dir, boolean all) {
        int before = pending[i];
        if (dir > 0) {
            int room = Math.min(remaining(), snap.max() - snap.stats()[i] - pending[i]);
            pending[i] += all ? Math.max(0, room) : Math.min(1, Math.max(0, room));
        } else {
            pending[i] = all ? 0 : Math.max(0, pending[i] - 1);
        }
        if (pending[i] != before) RebornSounds.uiClick();
    }

    private void statTooltip(StatDef d, int[] prev) {
        StatsData.Levers lv = snap.levers();
        List<Component> lines = new ArrayList<>();
        lines.add(RebornFont.arcade(d.arcadeLabel));
        for (String h : d.help) lines.add(Component.literal(h));
        int raw = prev[d.ordinal()];
        double eff = StatsMath.eff(lv, raw);
        lines.add(Component.literal(" "));
        lines.add(Component.literal("Valeur " + raw + " / " + snap.max()
            + (raw > lv.softCap() ? "  →  effective " + fmt1(eff) : "")));
        lines.add(Component.literal("Plein rendement jusqu'à " + fmt1(lv.softCap())
            + ", puis ×" + fmt1(lv.overFactor()) + " par point."));
        tooltip = lines;
        tooltipColor = d.color;
    }

    /* ---- tuiles dérivées ---- */

    private void drawTiles(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        StatsData.Levers lv = snap.levers();
        int[] cur = snap.stats();
        record Tile(String label, double now, double next, String unit, boolean pct, int color, String help) {}
        List<Tile> tiles = List.of(
            new Tile("PV MAX", StatsMath.maxHp(lv, cur), StatsMath.maxHp(lv, prev), "", false,
                StatDef.VIGUEUR.color, "Vigueur"),
            new Tile("CHAKRA", StatsMath.maxChakra(lv, cur), StatsMath.maxChakra(lv, prev), "", false,
                StatDef.CHAKRA.color, "Chakra — réserve des techniques"),
            new Tile("ENDURANCE", StatsMath.maxStamina(lv, cur), StatsMath.maxStamina(lv, prev), "", false,
                StatDef.TAIJUTSU.color, "Vigueur + Taïjutsu — M1, dash, techniques du corps"),
            new Tile("REGEN / 10S", StatsMath.regenPer10s(lv, cur), StatsMath.regenPer10s(lv, prev), "", false,
                StatDef.CHAKRA.color, "Chakra + Contrôle — méditation"),
            new Tile("COUTS", -StatsMath.costReduction(lv, cur) * 100, -StatsMath.costReduction(lv, prev) * 100,
                "%", true, StatDef.CONTROLE.color, "Contrôle — réduit toutes les techniques"),
            new Tile("CRITIQUE", StatsMath.crit(lv, cur) * 100, StatsMath.crit(lv, prev) * 100, "%", true,
                StatDef.CONTROLE.color, lv.critEnabled() ? "Contrôle — techniques seulement" : "Désactivé"));
        int cols = 3;
        int gap = 4;
        int tw = (rowX1 - rowX0 - gap * (cols - 1)) / cols;
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int x = rowX0 + (i % cols) * (tw + gap);
            int y = tilesY + (i / cols) * (tileH + gap);
            DrawHelpers.roundedOutlinedRectFull(ctx, x, y, tw, tileH, 3,
                Colors.withAlpha(0xFF000000, 0.35f), Colors.withAlpha(t.color(), 0.30f));
            ctx.fill(x + 1, y + 3, x + 2, y + tileH - 3, t.color());
            drawScaled(ctx, f, RebornFont.arcade(t.label()), x + 6, y + 4, INK_DIM, 0.75f);
            String now = t.pct() ? fmtSigned(t.now()) + t.unit() : fmtInt(t.now());
            Component nc = Component.literal(now);
            ctx.text(f, nc, x + 6, y + 13, INK, false);
            double diff = t.next() - t.now();
            if (Math.abs(diff) > 0.05) {
                boolean good = !t.label().equals("COUTS") ? diff > 0 : diff < 0;
                String ds = (diff > 0 ? "+" : "") + (t.pct() ? fmt1(diff) + t.unit() : fmtInt(diff));
                Component dc = Component.literal(ds);
                ctx.text(f, dc, x + tw - 5 - f.width(dc), y + 13, good ? Colors.SUCCESS : Colors.DANGER, false);
            }
            if (mx >= x && mx < x + tw && my >= y && my < y + tileH) {
                tooltip = List.of(RebornFont.arcade(t.label()), Component.literal(t.help()));
                tooltipColor = t.color();
            }
        }
    }

    /* ---- techniques ---- */

    private void drawTechniques(GuiGraphicsExtractor ctx, Font f, int[] prev, int mx, int my) {
        StatsData.Levers lv = snap.levers();
        int[] cur = snap.stats();
        int y0 = rowsY;
        int y1 = footY - 6;
        List<StatsData.Tech> list = snap.techniques();
        if (list.isEmpty()) {
            Component e1 = Component.literal("Aucune technique apprise.");
            Component e2 = Component.literal("Les parchemins et les maîtres t'attendent.");
            int cx = rightX + rightW / 2;
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
            ctx.fill(rowX1 + 2, thumbY, rowX1 + 5, thumbY + thumbH, Colors.withAlpha(ACC, 0.8f));
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
            lines.add(Component.literal("Rang " + t.rank() + " · " + t.category()
                + ("NONE".equals(t.nature()) ? "" : " · " + t.nature())));
            lines.add(Component.literal("Puissance ×" + String.format(Locale.ROOT, "%.2f", pNow)
                + (pNext > pNow + 1e-6 ? " → ×" + String.format(Locale.ROOT, "%.2f", pNext) : "")
                + "  (1 + Σ poids × stat)"));
            if (t.castable()) {
                lines.add(Component.literal("Coût " + fmtInt(StatsMath.cost(lv, cur, t))
                    + (t.stamina() ? " endurance" : " chakra") + " (hors maîtrise) · "
                    + StatsMath.casts(lv, cur, t) + " lancers à réserve pleine"));
            }
            lines.add(Component.literal("Maîtrise " + t.mastery() + " %"
                + (t.explicit() ? "" : " · lettres déduites de la catégorie")));
            tooltip = lines;
            tooltipColor = tc;
        }
    }

    /* ---- pied de page ---- */

    private void drawFooter(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        int h = 16;
        int y = footY;
        int pend = pendingTotal();
        // VALIDER (droite)
        String vl = pend > 0 ? "VALIDER (" + pend + ")" : "VALIDER";
        int vw = f.width(RebornFont.arcade(vl)) + 18;
        int vx = rowX1 - vw;
        actionButton(ctx, f, vx, y, vw, h, vl, pend > 0, true, mx, my);
        buttons.add(new Btn(vx, y, vw, h, this::validate));
        // ANNULER
        int cw = f.width(RebornFont.arcade("ANNULER")) + 14;
        int cx = vx - 6 - cw;
        actionButton(ctx, f, cx, y, cw, h, "ANNULER", pend > 0, false, mx, my);
        buttons.add(new Btn(cx, y, cw, h, () -> {
            if (pendingTotal() > 0) { java.util.Arrays.fill(pending, 0); RebornSounds.uiClick(); }
        }));
        // RÉINITIALISER (gauche) — double clic de confirmation.
        if (snap.canRespec()) {
            boolean armed = System.currentTimeMillis() - respecArmedAt < 3000L;
            String rl = armed ? "CONFIRMER ?" : "REINITIALISER";
            int rw = f.width(RebornFont.arcade(rl)) + 14;
            int rx = rowX0;
            boolean hov = mx >= rx && mx < rx + rw && my >= y && my < y + h;
            DrawHelpers.roundedOutlinedRectFull(ctx, rx, y, rw, h, 3,
                armed ? Colors.withAlpha(Colors.DANGER, 0.35f) : Colors.withAlpha(0xFF000000, hov ? 0.4f : 0.25f),
                Colors.withAlpha(armed ? Colors.DANGER : 0xFFFFFFFF, armed ? 0.9f : 0.18f));
            ctx.text(f, RebornFont.arcade(rl), rx + 7, y + 4, armed ? INK : INK_SOFT, false);
            buttons.add(new Btn(rx, y, rw, h, this::respec));
            if (hov) {
                tooltip = List.of(RebornFont.arcade("REINITIALISER"),
                    Component.literal("Toutes les stats reviennent à " + snap.min() + ","),
                    Component.literal("tous les points sont rendus."));
                tooltipColor = Colors.DANGER;
            }
        }
    }

    private void actionButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h, String label,
                              boolean enabled, boolean primary, int mx, int my) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = !enabled ? Colors.withAlpha(0xFF000000, 0.25f)
            : primary ? (hov ? Colors.ACCENT_HOVER : ACC)
            : Colors.withAlpha(0xFF000000, hov ? 0.45f : 0.3f);
        int border = !enabled ? Colors.withAlpha(0xFFFFFFFF, 0.08f)
            : primary ? Colors.withAlpha(GOLD, hov ? 0.9f : 0.5f) : Colors.withAlpha(0xFFFFFFFF, 0.25f);
        if (enabled && primary && hov) DrawHelpers.glowRect(ctx, x, y, w, h, Colors.ACCENT_GLOW, 3);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 3, fill, border);
        Component c = RebornFont.arcade(label);
        ctx.text(f, c, x + (w - f.width(c)) / 2, y + 4, enabled ? INK : INK_DIM, false);
    }

    /* ----------------------------------------------------------- actions */

    private void validate() {
        if (pendingTotal() <= 0) return;
        StringBuilder sb = new StringBuilder("alloc:");
        boolean first = true;
        for (StatDef d : StatDef.values()) {
            int p = pending[d.ordinal()];
            if (p <= 0) continue;
            if (!first) sb.append(',');
            sb.append(d.key()).append('=').append(p);
            first = false;
        }
        if (ClientPlayNetworking.canSend(StatsPayload.ID)) {
            ClientPlayNetworking.send(new StatsPayload(sb.toString()));
        }
        java.util.Arrays.fill(pending, 0);
        RebornSounds.confirm();
    }

    private void respec() {
        long now = System.currentTimeMillis();
        if (now - respecArmedAt > 3000L) {
            respecArmedAt = now;
            RebornSounds.uiClick();
            return;
        }
        respecArmedAt = 0L;
        java.util.Arrays.fill(pending, 0);
        if (ClientPlayNetworking.canSend(StatsPayload.ID)) ClientPlayNetworking.send(new StatsPayload("respec"));
        RebornSounds.confirm();
    }

    /* ------------------------------------------------------------ entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        int mx = (int) e.x(), my = (int) e.y();
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        // Maj-clic sur −/+ = tout / rien : on refait le hit-test des boutons de rangée.
        if (shift && tab == TAB_ATTR) {
            int btn = Math.min(14, rowH - 4);
            for (StatDef d : StatDef.values()) {
                int y = rowsY + d.ordinal() * rowH + rowH / 2 - btn / 2;
                int bx = rowX1 - btn * 2 - 3;
                if (my >= y && my < y + btn) {
                    if (mx >= bx && mx < bx + btn) { step(d.ordinal(), -1, true); return true; }
                    if (mx >= bx + btn + 3 && mx < bx + btn * 2 + 3) { step(d.ordinal(), +1, true); return true; }
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
            Colors.withAlpha(0xFF0A0608, 0.94f), Colors.withAlpha(accent, 0.7f));
        ctx.fill(x + 1, y + 3, x + 3, y + h - 3, accent);
        int ly = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            ctx.text(f, lines.get(i), x + 8, ly, i == 0 ? accent : INK_SOFT, false);
            ly += 10;
        }
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
