package fr.reborn.hud.camera;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Menu de la caméra épaule, au niveau de la fiche shinobi (DA §4.1, §6), version épurée. Signature : le <b>miroir de
 * bronze</b> (dōkyō) en tête du panneau, dans lequel se reflète en direct la vue de dessus (joueur, caméra, champ de
 * vision ; cercles gravés à 2, 4 et 6 blocs). Panneau laqué étroit à droite : le jeu reste visible et la caméra bouge
 * en temps réel. Contrôles des Paramètres ({@link ReglagesArt}) : style, préréglages, quatre réglettes, épaule,
 * impact d'atterrissage, Réinitialiser / Fermer. Réglages persistés dans {@code RebornPrefs} comme avant.
 *
 * <p>Décor fixe : {@code textures/gui/camera/*} ({@code tools/gen_camera_textures.py}).
 */
public class CameraScreen extends Screen {

    private static Identifier tex(String n) { return DaKit.tex("camera", n); }

    private static final Identifier PANNEAU = tex("panneau"), ENTETE = tex("entete"), PIECES = tex("pieces");

    private static final int PW = 200, PH = 336, PAD = 14, IW = PW - 2 * PAD;
    private static final int MIR_Y = 60, STYLE_Y = 120, PRESET_Y = 142, RULER_Y = 166, RULER_STEP = 24, SIDE_Y = 264,
        IMPACT_Y = 286, BTN_Y = 308, SEG_W = 50, BTN_W = 84;
    /** GUI par bloc dans le miroir. */
    private static final float ECH = 5.5f;

    private static final String[] RULERS = {"Distance", "Décalage d'épaule", "Hauteur", "Vitesse de rotation"};

    private final Screen parent;
    private int px, py;
    private int dragging = -1;
    private int lastHover = -1;

    public CameraScreen(Screen parent) {
        super(Component.literal("Caméra"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = this.width - PW - 16;
        py = Math.max(22, (this.height - PH) / 2);
        RebornSounds.playReborn("esc.open", 1.15f, 0.35f);
    }

    // ── valeurs ──────────────────────────────────────────────────
    private static float frac(double v, double a, double b) { return (float) ((v - a) / (b - a)); }

    private static float rulerValue(int i) {
        RebornCamera c = RebornCamera.INSTANCE;
        return switch (i) {
            case 0 -> frac(c.distance(), RebornCamera.DIST_MIN, RebornCamera.DIST_MAX);
            case 1 -> frac(c.rightMagnitude(), 0, RebornCamera.RIGHT_MAX);
            case 2 -> frac(c.upOffset(), RebornCamera.UP_MIN, RebornCamera.UP_MAX);
            default -> frac(c.turnSpeed(), 0.1, 1.0);
        };
    }

    private static void setRuler(int i, double t) {
        RebornCamera c = RebornCamera.INSTANCE;
        t = Math.max(0, Math.min(1, t));
        switch (i) {
            case 0 -> c.setDistance(Math.round((RebornCamera.DIST_MIN + t * (RebornCamera.DIST_MAX - RebornCamera.DIST_MIN)) * 10) / 10.0);
            case 1 -> c.setRight(Math.round(t * RebornCamera.RIGHT_MAX * 100) / 100.0);
            case 2 -> c.setUp(Math.round((RebornCamera.UP_MIN + t * (RebornCamera.UP_MAX - RebornCamera.UP_MIN)) * 100) / 100.0);
            default -> c.setTurnSpeed(Math.round((0.1 + t * 0.9) * 100) / 100.0);
        }
    }

    private static String rulerText(int i) {
        RebornCamera c = RebornCamera.INSTANCE;
        return switch (i) {
            case 0 -> String.format(Locale.FRANCE, "%.1f blocs", c.distance());
            case 1 -> String.format(Locale.FRANCE, "%.2f bloc", c.rightMagnitude());
            case 2 -> String.format(Locale.FRANCE, "%+.2f bloc", c.upOffset());
            default -> Math.round(c.turnSpeed() * 100) + " %";
        };
    }

    // ── rendu ────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // le jeu reste visible (réglage en direct) : léger voile seulement derrière le panneau
        g.fill(px - 24, 0, this.width, this.height, 0x30080408);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Font f = this.font;
        RebornCamera cam = RebornCamera.INSTANCE;
        boolean reborn = !cam.isVanilla();
        int mx = mouseX - px, my = mouseY - py;

        DaKit.blit(g, PANNEAU, px - 2, py - 2, 0, 0, 208, 344, 208, 344);
        int ex = px + PW / 2 - 80, ey = py - 20;
        DaKit.blit(g, ENTETE, ex, ey, 0, 0, 160, 44, 160, 44);
        DaKit.arcX1(g, f, DaKit.arcade("Caméra"), ex + 80, ey + 16, DaKit.OR_CLAIR, 1, true);

        miroir(g, f, cam, px + PW / 2, py + MIR_Y);

        // style
        DaKit.inter8(g, f, DaKit.inter("Style"), px + PAD, py + STYLE_Y + 8, DaKit.CREME_VAL, 0);
        seg2(g, f, STYLE_Y, "Reborn", "Vanilla", reborn ? 0 : 1, mx, my, true);
        // préréglages
        CameraPreset[] ps = CameraPreset.values();
        int pw = (IW + 2) / ps.length;
        for (int i = 0; i < ps.length; i++) {
            int x = PAD + i * pw;
            boolean actif = reborn && cam.preset() == ps[i], hov = reborn && DaKit.dans(mx, my, x, PRESET_Y, pw - 2, 16);
            ReglagesArt.segment(g, px + x, py + PRESET_Y, pw - 2, actif ? 2 : hov ? 1 : 0);
            DaKit.inter8(g, f, DaKit.inter(ps[i].label), px + x + (pw - 2) / 2f, py + PRESET_Y + 8,
                !reborn ? DaKit.INACTIF : actif ? DaKit.CREME : hov ? DaKit.CREME_VAL : DaKit.NEUTRE_TXT, 1);
        }
        // réglettes
        for (int i = 0; i < 4; i++) {
            int y = RULER_Y + i * RULER_STEP;
            boolean hot = reborn && (dragging == i || DaKit.dans(mx, my, PAD - 4, y + 6, IW + 8, 16));
            DaKit.inter8(g, f, DaKit.inter(RULERS[i]), px + PAD, py + y + 4, reborn ? DaKit.CREME_VAL : DaKit.INACTIF, 0);
            DaKit.inter8(g, f, DaKit.inter(rulerText(i)), px + PW - PAD, py + y + 4, reborn ? DaKit.OR_CLAIR : DaKit.INACTIF, 2);
            ReglagesArt.reglette(g, px + PAD, py + y + 10, IW, rulerValue(i), hot);
        }
        // épaule, impact
        DaKit.inter8(g, f, DaKit.inter("Épaule"), px + PAD, py + SIDE_Y + 8, reborn ? DaKit.CREME_VAL : DaKit.INACTIF, 0);
        seg2(g, f, SIDE_Y, "Gauche", "Droite", cam.side() > 0 ? 1 : 0, mx, my, reborn);
        DaKit.inter8(g, f, DaKit.inter("Impact au sol"), px + PAD, py + IMPACT_Y + 8, reborn ? DaKit.CREME_VAL : DaKit.INACTIF, 0);
        seg2(g, f, IMPACT_Y, "Non", "Oui", cam.impactEnabled() ? 1 : 0, mx, my, reborn);
        // boutons
        boolean h1 = DaKit.dans(mx, my, PAD, BTN_Y, BTN_W, 20), h2 = DaKit.dans(mx, my, PW - PAD - BTN_W, BTN_Y, BTN_W, 20);
        ReglagesArt.bouton(g, px + PAD, py + BTN_Y, BTN_W, h1 ? 1 : 0);
        ReglagesArt.bouton(g, px + PW - PAD - BTN_W, py + BTN_Y, BTN_W, 3);
        DaKit.inter8(g, f, DaKit.inter("Réinitialiser"), px + PAD + BTN_W / 2f, py + BTN_Y + 10, h1 ? DaKit.CREME : DaKit.CREME_VAL, 1);
        DaKit.inter8(g, f, DaKit.inter("Fermer"), px + PW - PAD - BTN_W / 2f, py + BTN_Y + 10, h2 ? DaKit.CREME : DaKit.CREME_VAL, 1);

        int hov = hoverId(mx, my);
        if (hov != lastHover) { if (hov >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hov; }

        if (py + PH + 14 < this.height) {
            DaKit.aides(g, f, ReglagesArt.PIECES, 0, 0, 120, 40,
                new String[][]{{"Glisser", "régler"}, {"Molette", "affiner"}, {"Échap", "fermer"}}, px + PW, py + PH + 10, 1f);
        }
    }

    /** Vue de dessus dans le miroir : joueur au centre, caméra derrière (vers le bas), cône de vision vers le haut. */
    private void miroir(GuiGraphicsExtractor g, Font f, RebornCamera cam, int cx, int cy) {
        if (cam.isVanilla()) {
            DaKit.arcFin(g, f, DaKit.arcade("Vanilla (F5)"), cx, cy + 22, DaKit.OR_ETIQ, 1, true);
            DaKit.blit(g, PIECES, cx - 2, cy - 2, 8, 0, 5, 5, 32, 16);
            return;
        }
        int ccx = cx + Math.round((float) cam.rightOffset() * 12f), ccy = cy + Math.round((float) cam.distance() * ECH);
        LotGui l = LotGui.ouvrir(g);
        // cône (lignes horizontales bornées au disque) : un seul élément GUI
        float r2 = 39f * 39f;
        for (int t = 2; t <= 76; t++) {
            int yy = ccy - t, half = t / 2;
            float dy = yy - cy;
            if (dy * dy > r2) break;
            int lim = (int) Math.floor(Math.sqrt(r2 - dy * dy));
            int a = Math.max(ccx - half, cx - lim), b = Math.min(ccx + half, cx + lim);
            if (a > b) continue;
            l.fill(a, yy, b + 1, yy + 1, 0x14F2D08A);
            if (ccx - half >= cx - lim) l.fill(ccx - half, yy, ccx - half + 1, yy + 1, 0x80D9A95E);
            if (ccx + half <= cx + lim) l.fill(ccx + half, yy, ccx + half + 1, yy + 1, 0x80D9A95E);
        }
        // liaison joueur → caméra (pointillé)
        int steps = Math.max(Math.abs(ccx - cx), Math.abs(ccy - cy));
        for (int i = 0; i <= steps; i += 2) {
            int lx = cx + (ccx - cx) * i / Math.max(1, steps), lyy = cy + (ccy - cy) * i / Math.max(1, steps);
            l.fill(lx, lyy, lx + 1, lyy + 1, 0xFFC8A05A);
        }
        l.fermer();
        LotBlit b = LotBlit.ouvrir(g, PIECES, 32, 16);
        b.blit(ccx - 3, ccy - 3, 0, 0, 7, 7);
        b.blit(cx - 2, cy - 2, 8, 0, 5, 5);
        b.fermer();
    }

    /** Deux plaques segmentées alignées à droite (choix binaire). */
    private void seg2(GuiGraphicsExtractor g, Font f, int y, String a, String b, int sel, int mx, int my, boolean enabled) {
        for (int k = 0; k < 2; k++) {
            int x = PW - PAD - (2 - k) * SEG_W;
            boolean actif = enabled && sel == k, hov = enabled && DaKit.dans(mx, my, x, y, SEG_W - 2, 16);
            ReglagesArt.segment(g, px + x, py + y, SEG_W - 2, actif ? 2 : hov ? 1 : 0);
            DaKit.inter8(g, f, DaKit.inter(k == 0 ? a : b), px + x + (SEG_W - 2) / 2f, py + y + 8,
                !enabled ? DaKit.INACTIF : actif ? DaKit.CREME : hov ? DaKit.CREME_VAL : DaKit.NEUTRE_TXT, 1);
        }
    }

    private static boolean seg(int mx, int my, int y, int k) {
        return DaKit.dans(mx, my, PW - PAD - (2 - k) * SEG_W, y, SEG_W - 2, 16);
    }

    private int hoverId(int mx, int my) {
        for (int k = 0; k < 2; k++) {
            if (seg(mx, my, STYLE_Y, k)) return 1 + k;
            if (seg(mx, my, SIDE_Y, k)) return 10 + k;
            if (seg(mx, my, IMPACT_Y, k)) return 12 + k;
        }
        if (DaKit.dans(mx, my, PAD, PRESET_Y, IW, 16)) return 3 + (mx - PAD) / ((IW + 2) / 4);
        if (DaKit.dans(mx, my, PAD, BTN_Y, BTN_W, 20)) return 20;
        if (DaKit.dans(mx, my, PW - PAD - BTN_W, BTN_Y, BTN_W, 20)) return 21;
        return -1;
    }

    // ── interactions ─────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mx = (int) Math.floor(event.x()) - px, my = (int) Math.floor(event.y()) - py;
        RebornCamera cam = RebornCamera.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        boolean reborn = !cam.isVanilla();

        if (seg(mx, my, STYLE_Y, 0)) { cam.setVanilla(false, mc); click(); return true; }
        if (seg(mx, my, STYLE_Y, 1)) { cam.setVanilla(true, mc); click(); return true; }
        if (DaKit.dans(mx, my, PAD, BTN_Y, BTN_W, 20)) {
            cam.setPreset(CameraPreset.DEFAUT); cam.setSide(1); cam.setTurnSpeed(0.5); cam.saveToPrefs(); click(); return true;
        }
        if (DaKit.dans(mx, my, PW - PAD - BTN_W, BTN_Y, BTN_W, 20)) { onClose(); return true; }
        if (!reborn) return super.mouseClicked(event, doubleClick);

        CameraPreset[] ps = CameraPreset.values();
        int pw = (IW + 2) / ps.length;
        for (int i = 0; i < ps.length; i++) {
            if (DaKit.dans(mx, my, PAD + i * pw, PRESET_Y, pw - 2, 16)) { cam.setPreset(ps[i]); cam.saveToPrefs(); click(); return true; }
        }
        for (int i = 0; i < 4; i++) {
            int y = RULER_Y + i * RULER_STEP;
            if (DaKit.dans(mx, my, PAD - 4, y + 6, IW + 8, 16)) { dragging = i; setRuler(i, (mx - PAD - 2) / (double) (IW - 4)); return true; }
        }
        if (seg(mx, my, SIDE_Y, 0)) { cam.setSide(-1); cam.saveToPrefs(); click(); return true; }
        if (seg(mx, my, SIDE_Y, 1)) { cam.setSide(1); cam.saveToPrefs(); click(); return true; }
        if (seg(mx, my, IMPACT_Y, 0)) { cam.setImpactEnabled(false); click(); return true; }
        if (seg(mx, my, IMPACT_Y, 1)) { cam.setImpactEnabled(true); click(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging >= 0) { setRuler(dragging, (event.x() - px - PAD - 2) / (IW - 4)); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging >= 0) { dragging = -1; RebornCamera.INSTANCE.saveToPrefs(); return true; }
        return super.mouseReleased(event);
    }

    /** Molette sur une réglette : réglage fin d'un cran. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (RebornCamera.INSTANCE.isVanilla() || v == 0) return false;
        double mx = mouseX - px, my = mouseY - py;
        for (int i = 0; i < 4; i++) {
            int y = RULER_Y + i * RULER_STEP;
            if (DaKit.dans(mx, my, PAD - 4, y - 2, IW + 8, RULER_STEP)) {
                double step = i == 0 ? 0.1 / (RebornCamera.DIST_MAX - RebornCamera.DIST_MIN)
                    : i == 1 ? 0.01 / RebornCamera.RIGHT_MAX : i == 2 ? 0.01 / (RebornCamera.UP_MAX - RebornCamera.UP_MIN) : 0.01 / 0.9;
                setRuler(i, rulerValue(i) + Math.signum(v) * step + 1e-6);
                RebornCamera.INSTANCE.saveToPrefs();
                return true;
            }
        }
        return false;
    }

    private static void click() { RebornSounds.playReborn("esc.select", 1.1f, 0.4f); }

    @Override
    public boolean isPauseScreen() {
        return false; // live : la caméra bouge en temps réel derrière le menu.
    }

    @Override
    public void onClose() {
        RebornCamera.INSTANCE.saveToPrefs();
        RebornSounds.playReborn("esc.close", 1.1f, 0.35f);
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
