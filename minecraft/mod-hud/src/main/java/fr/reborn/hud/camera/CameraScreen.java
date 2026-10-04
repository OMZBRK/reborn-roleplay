package fr.reborn.hud.camera;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.Da;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Menu de repositionnement de la caméra épaule — DA Reborn. Panneau laqué <b>live</b> à droite
 * (le jeu reste visible, la caméra bouge en temps réel) : vue de dessus de la position caméra,
 * style Reborn/Vanilla, préréglages, quatre réglettes graduées en vraies unités, épaule, impact
 * d'atterrissage, réinitialiser / fermer. Mêmes réglages qu'avant, persistés dans
 * {@link fr.reborn.hud.menu.settings.RebornPrefs}.
 *
 * <p>Le panneau est dessiné dans un repère local de 200×{@value #PH} et réduit si la fenêtre est
 * trop basse (plus de contrôles hors écran).
 */
public class CameraScreen extends Screen {

    private static final int PW = 200, PH = 290, PAD = 10;
    private static final int TOP_Y = 10, TOP_H = 64, STYLE_Y = 82, PRESET_Y = 100, RULER_Y = 124, RULER_STEP = 24,
        SIDE_Y = 224, IMPACT_Y = 242, BTN_Y = 266;

    private final Screen parent;
    private int px, py;
    private float sc = 1f;
    private int dragging = -1;
    private int lastHover = -1;

    public CameraScreen(Screen parent) {
        super(Component.literal("Caméra"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        RebornSounds.playReborn("esc.open", 1.15f, 0.35f);
    }

    private void layout() {
        int avail = this.height - 50 - 22;
        sc = Math.min(1f, avail / (float) PH);
        px = this.width - Math.round(PW * sc) - 12;
        py = 50;
    }

    // ── conversions ──────────────────────────────────────────────
    private double lx(double mx) { return (mx - px) / sc; }
    private double ly(double my) { return (my - py) / sc; }

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
            case 0 -> String.format(Locale.ROOT, "%.1f BLOCS", c.distance());
            case 1 -> String.format(Locale.ROOT, "%.2f BLOC", c.rightMagnitude());
            case 2 -> String.format(Locale.ROOT, "%+.2f BLOC", c.upOffset());
            default -> Math.round(c.turnSpeed() * 100) + " %";
        };
    }

    private static final String[] RULERS = { "Distance", "Décalage épaule", "Hauteur", "Vitesse de rotation" };

    // ── rendu ────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Le jeu reste visible (réglage en direct) : léger voile seulement derrière le panneau.
        g.fillGradient(px - 60, 0, this.width, this.height, 0x00000000, 0x00000000);
        g.fill(px - 30, 0, this.width, this.height, 0x30080408);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();
        Font f = this.font;
        RebornCamera cam = RebornCamera.INSTANCE;
        boolean reborn = !cam.isVanilla();
        double mx = lx(mouseX), my = ly(mouseY);

        Da.header(g, f, px + Math.round(PW * sc / 2f), 14, "Caméra", "Réglages de la vue");

        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(sc, sc);
        Da.panel(g, 0, 0, PW, PH);

        drawTopView(g, f, cam);

        float s = Da.small();
        int iw = PW - 2 * PAD;
        // Style
        Da.text(g, f, "Style", PAD, STYLE_Y + 3, s, Da.MUTED, 0);
        plate2(g, f, STYLE_Y, "Reborn", "Vanilla", reborn ? 0 : 1, mx, my, true);
        // Préréglages
        CameraPreset[] ps = CameraPreset.values();
        int pw = (iw - 3 * 4) / 4;
        for (int i = 0; i < ps.length; i++) {
            int x = PAD + i * (pw + 4);
            Da.plate(g, f, x, PRESET_Y, pw, 13, ps[i].label, reborn && cam.preset() == ps[i], Da.in(mx, my, x, PRESET_Y, pw, 13), reborn);
        }
        // Réglettes
        for (int i = 0; i < 4; i++) {
            int y = RULER_Y + i * RULER_STEP;
            boolean hot = dragging == i || Da.in(mx, my, PAD - 4, y + 4, iw + 8, 14);
            Da.ruler(g, f, PAD, y, iw, rulerValue(i), RULERS[i], rulerText(i), hot, reborn);
        }
        // Épaule / impact
        Da.text(g, f, "Épaule", PAD, SIDE_Y + 3, s, Da.MUTED, 0);
        plate2(g, f, SIDE_Y, "Gauche", "Droite", cam.side() > 0 ? 1 : 0, mx, my, reborn);
        Da.text(g, f, "Impact atterrissage", PAD, IMPACT_Y + 3, s, Da.MUTED, 0);
        plate2(g, f, IMPACT_Y, "Non", "Oui", cam.impactEnabled() ? 1 : 0, mx, my, reborn);
        // Boutons
        int bw = (iw - 6) / 2;
        Da.plate(g, f, PAD, BTN_Y, bw, 15, "Réinitialiser", false, Da.in(mx, my, PAD, BTN_Y, bw, 15), true);
        Da.plate(g, f, PAD + bw + 6, BTN_Y, iw - bw - 6, 15, "Fermer", true, Da.in(mx, my, PAD + bw + 6, BTN_Y, iw - bw - 6, 15), true);
        g.pose().popMatrix();

        int hov = hoverId(mx, my);
        if (hov != lastHover) { if (hov >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hov; }

        Da.hint(g, f, this.width, this.height, "Glisser : régler     Molette : affiner     O / Échap : fermer");
    }

    /** Deux plaques côte à côte, alignées à droite (choix binaire). */
    private void plate2(GuiGraphicsExtractor g, Font f, int y, String a, String b, int sel, double mx, double my, boolean enabled) {
        int w = 46, x1 = PW - PAD - w, x0 = x1 - w - 4;
        Da.plate(g, f, x0, y, w, 13, a, enabled && sel == 0, Da.in(mx, my, x0, y, w, 13), enabled);
        Da.plate(g, f, x1, y, w, 13, b, enabled && sel == 1, Da.in(mx, my, x1, y, w, 13), enabled);
    }

    /** Vue de dessus en direct : joueur, position de la caméra (distance + épaule), champ de vision. */
    private void drawTopView(GuiGraphicsExtractor g, Font f, RebornCamera cam) {
        int x = PAD, y = TOP_Y, w = PW - 2 * PAD, h = TOP_H;
        g.fill(x, y, x + w, y + h, 0xFF140E10);
        Da.outline(g, x, y, w, h, Da.GOLD_D);
        for (int k = 1; k < 9; k++) g.fill(x + k * w / 9, y + 1, x + k * w / 9 + 1, y + h - 1, 0xFF221A1C);
        for (int k = 1; k < 4; k++) g.fill(x + 1, y + k * h / 4, x + w - 1, y + k * h / 4 + 1, 0xFF221A1C);
        int pcx = x + w / 2, pcy = y + 14;
        if (!cam.isVanilla()) {
            int ccx = pcx + (int) Math.round(cam.rightOffset() * 14), ccy = pcy + (int) Math.round(cam.distance() * 6.5);
            // cône de vision (lignes en escalier vers le haut)
            for (int t = 0; t <= ccy - y - 3; t++) {
                int yy = ccy - t, half = t / 2;
                g.fill(ccx - half, yy, ccx + half + 1, yy + 1, 0x18F6CC78);
                g.fill(ccx - half, yy, ccx - half + 1, yy + 1, 0x90F6CC78);
                g.fill(ccx + half, yy, ccx + half + 1, yy + 1, 0x90F6CC78);
            }
            // liaison joueur → caméra
            int steps = Math.max(Math.abs(ccx - pcx), Math.abs(ccy - pcy));
            for (int i = 0; i <= steps; i += 2) {
                int lx = pcx + (ccx - pcx) * i / Math.max(1, steps), lyy = pcy + (ccy - pcy) * i / Math.max(1, steps);
                g.fill(lx, lyy, lx + 1, lyy + 1, 0xFFC8A05A);
            }
            g.fill(ccx - 4, ccy - 3, ccx + 5, ccy + 4, Da.RED);
            Da.outline(g, ccx - 4, ccy - 3, 9, 7, Da.GOLD);
        }
        g.fill(pcx - 2, pcy - 2, pcx + 3, pcy + 3, Da.CREAM);
        float s = Da.small();
        Da.text(g, f, "Vue de dessus", x + 4, y + h - 9, s, Da.MUTED, 0);
        Da.text(g, f, cam.isVanilla() ? "Vanilla (F5)" : String.format(Locale.ROOT, "%.1f blocs", cam.distance()), x + w - 4, y + h - 9, s, Da.GOLD, 2);
    }

    private int hoverId(double mx, double my) {
        int iw = PW - 2 * PAD;
        if (Da.in(mx, my, PW - PAD - 96, STYLE_Y, 96, 13)) return 1;
        if (Da.in(mx, my, PAD, PRESET_Y, iw, 13)) return 2 + (int) ((mx - PAD) / (iw / 4.0));
        if (Da.in(mx, my, PW - PAD - 96, SIDE_Y, 96, 13)) return 10;
        if (Da.in(mx, my, PW - PAD - 96, IMPACT_Y, 96, 13)) return 11;
        if (Da.in(mx, my, PAD, BTN_Y, iw, 15)) return mx < PAD + iw / 2.0 ? 12 : 13;
        return -1;
    }

    // ── interactions ─────────────────────────────────────────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        layout();
        double mx = lx(event.x()), my = ly(event.y());
        RebornCamera cam = RebornCamera.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        boolean reborn = !cam.isVanilla();
        int iw = PW - 2 * PAD;
        int w = 46, x1 = PW - PAD - w, x0 = x1 - w - 4;

        if (Da.in(mx, my, x0, STYLE_Y, w, 13)) { cam.setVanilla(false, mc); click(); return true; }
        if (Da.in(mx, my, x1, STYLE_Y, w, 13)) { cam.setVanilla(true, mc); click(); return true; }
        if (Da.in(mx, my, PAD, BTN_Y, (iw - 6) / 2, 15)) {
            cam.setPreset(CameraPreset.DEFAUT); cam.setSide(1); cam.setTurnSpeed(0.5); cam.saveToPrefs(); click(); return true;
        }
        if (Da.in(mx, my, PAD + (iw - 6) / 2 + 6, BTN_Y, iw - (iw - 6) / 2 - 6, 15)) { onClose(); return true; }
        if (!reborn) return true;

        int pw = (iw - 12) / 4;
        CameraPreset[] ps = CameraPreset.values();
        for (int i = 0; i < ps.length; i++) {
            if (Da.in(mx, my, PAD + i * (pw + 4), PRESET_Y, pw, 13)) { cam.setPreset(ps[i]); cam.saveToPrefs(); click(); return true; }
        }
        for (int i = 0; i < 4; i++) {
            int y = RULER_Y + i * RULER_STEP;
            if (Da.in(mx, my, PAD - 4, y + 4, iw + 8, 14)) { dragging = i; setRuler(i, (mx - PAD) / (iw - 1)); return true; }
        }
        if (Da.in(mx, my, x0, SIDE_Y, w, 13)) { cam.setSide(-1); cam.saveToPrefs(); click(); return true; }
        if (Da.in(mx, my, x1, SIDE_Y, w, 13)) { cam.setSide(1); cam.saveToPrefs(); click(); return true; }
        if (Da.in(mx, my, x0, IMPACT_Y, w, 13)) { cam.setImpactEnabled(false); click(); return true; }
        if (Da.in(mx, my, x1, IMPACT_Y, w, 13)) { cam.setImpactEnabled(true); click(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging >= 0) { setRuler(dragging, (lx(event.x()) - PAD) / (PW - 2 * PAD - 1)); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging >= 0) { dragging = -1; RebornCamera.INSTANCE.saveToPrefs(); return true; }
        return super.mouseReleased(event);
    }

    /** Molette sur une réglette : réglage fin d'un cran. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (RebornCamera.INSTANCE.isVanilla() || v == 0) return false;
        double mx = lx(mouseX), my = ly(mouseY);
        for (int i = 0; i < 4; i++) {
            int y = RULER_Y + i * RULER_STEP;
            if (Da.in(mx, my, PAD - 4, y - 2, PW - 2 * PAD + 8, 20)) {
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
