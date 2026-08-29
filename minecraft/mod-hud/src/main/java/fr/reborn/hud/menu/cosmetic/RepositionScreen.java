package fr.reborn.hud.menu.cosmetic;

import fr.reborn.hud.cosmetic.CosmeticPresets;
import fr.reborn.hud.cosmetic.CosmeticTransform;
import fr.reborn.hud.cosmetic.RepositionMode;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * <b>Menu de repositionnement cosmétique in-world</b> (façon Zenkai / nanos-world).
 * Le joueur reste debout dans le VRAI monde, rendu en 3e personne
 * ({@link RepositionMode} + {@code CameraRepositionMixin}) : on orbite autour de
 * lui (glisser sur le monde), on zoome (molette), et on manipule le cosmétique
 * via un <b>gizmo 3D projeté sur le modèle</b> (flèches X/Y/Z en Déplacer, anneaux
 * en Tourner). Le rendu est direct : muter {@link RepositionMode#target()} se voit
 * immédiatement sur le perso.
 *
 * <p><b>Panneau CONFIGURATION</b> (haut-gauche) : dropdown de preset (charge),
 * champ nom, dropdown d'ancrage, rangée [Sauvegarder][Supprimer], puis [Appliquer]
 * (persiste le placement actif). <b>Barre du bas</b> : [Déplacer] [Tourner]
 * [Masquer le gizmo].
 *
 * <p><b>Gizmo (projection).</b> Le point d'ancrage MONDE du cosmétique est projeté
 * à l'écran (basis caméra + FOV, sans matrices MC), avec l'extrémité de chaque axe
 * (repère du joueur : X=droite, Y=haut, Z=avant). Le drag projette le déplacement
 * souris sur la direction écran de l'axe → delta appliqué au transform (translate
 * ~0.006/px, rotate ~0.5°/px ; Maj = fin, Ctrl/Alt = accroche). Le placement du
 * point d'ancrage est une approximation joueur-relative (v1).
 */
public class RepositionScreen extends Screen {

    private static final int ACC = Colors.ACCENT;
    private static final int ACC_HOVER = Colors.ACCENT_HOVER;
    private static final int PANEL_FILL = Colors.withAlpha(0xFF17110F, 0.72f);
    private static final int PANEL_BORDER = Colors.withAlpha(ACC, 0.44f);
    private static final int INK = Colors.withAlpha(0xFFFFFFFF, 0.88f);
    private static final int INK_DIM = Colors.withAlpha(0xFFFFFFFF, 0.44f);

    // X rouge, Y vert, Z bleu.
    private static final int[] AXIS_COLOR = { 0xFFE0453B, 0xFF54D65B, 0xFF4C8DF0 };
    private static final float AXIS_LEN_3D = 0.45f;   // longueur d'un axe en blocs

    private enum GizmoMode { TRANSLATE, ROTATE, SCALE }

    private CosmeticTransform t;
    private final String cosmeticId;
    private final String cosmeticLabel;
    private final CosmeticTransform.Anchor defaultAnchor;

    private EditBox nameBox;
    private final long openedAt = System.currentTimeMillis();

    private GizmoMode gizmoMode = GizmoMode.TRANSLATE;
    private boolean gizmoVisible = true;
    private int grabbedAxis = -1;
    private boolean orbiting = false;

    private boolean presetOpen = false, anchorOpen = false;
    private String selectedPreset = null;

    // ─── Projection caméra (recalculée chaque frame) ───
    private Vec3 camPos = Vec3.ZERO, camLook = new Vec3(0, 0, 1), camRight = new Vec3(1, 0, 0), camUp = new Vec3(0, 1, 0);
    private double tanHalf = 0.7, aspect = 1.0;

    // ─── Gizmo écran (recalculé chaque frame) ───
    private boolean gizOn = false;
    private float gizCx, gizCy;
    private final float[] axEndX = new float[3], axEndY = new float[3];
    private final float[] axDirX = new float[3], axDirY = new float[3];
    private final boolean[] axVis = new boolean[3];
    // Base du joueur (mise à jour chaque frame) pour les anneaux de rotation.
    private Vec3 pRight = new Vec3(1, 0, 0), pUp = new Vec3(0, 1, 0), pFwd = new Vec3(0, 0, 1);
    // Anneaux de rotation projetés (un par axe), en coords écran. NaN = point derrière caméra.
    // Rendus en PERLES (1 fill/point) et non en thickLine (des centaines de fills/segment) :
    // le pipeline retained 26.2 facture chaque ctx.fill, la version filaire tuait le FPS en Tourner.
    private static final int RING_N = 48;
    private final float[][] ringX = new float[3][RING_N], ringY = new float[3][RING_N];
    private final boolean[] ringVis = new boolean[3];

    // Échelles de texte (menu compact) : petites capitales & valeurs réduites.
    private static final float LBL_SCALE = 0.82f;   // labels de section (PRESET, ANCRAGE, titre)
    private static final float VAL_SCALE = 0.9f;    // valeurs de dropdown + libellés de bouton

    // ─── Layout panneau (recalculé) — compact + plus d'air entre les sections ───
    private final int panelX = 14, panelY = 14, panelW = 178, pad = 9, rowH = 14, fieldH = 13;
    private final int labelH = 10, sectionGap = 12;
    private int closeX, closeY, closeSize = 13;
    private int presetY, nameY, anchorY, actionsY, applyY, panelH;
    private int saveX, saveW, delX, delW;
    // Barre du bas (boutons resserrés).
    private int barY, barH = 24;
    private final int[] barX = new int[4];
    private final int barBtnW = 92;

    public RepositionScreen(String cosmeticId, String cosmeticLabel, CosmeticTransform.Anchor defaultAnchor) {
        super(Component.literal("Repositionnement cosmétique"));
        this.cosmeticId = cosmeticId != null ? cosmeticId : "cosmetic";
        this.cosmeticLabel = cosmeticLabel != null ? cosmeticLabel : "Cosmétique";
        this.defaultAnchor = defaultAnchor != null ? defaultAnchor : CosmeticTransform.Anchor.HEAD;
        CosmeticPresets.ensureLoaded();
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!RepositionMode.INSTANCE.isActive()) {
            RepositionMode.INSTANCE.begin(mc, cosmeticId, cosmeticLabel, defaultAnchor);
        }
        // Cible la MÊME instance de transform que le renderer (édition live).
        this.t = RepositionMode.INSTANCE.target();
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        }
        layout();
        nameBox = new EditBox(this.font, panelX + pad, nameY, panelW - pad * 2, 14, Component.literal("preset"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.literal("Nom du preset…"));
        nameBox.setBordered(false);
        this.addRenderableWidget(nameBox);
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        RepositionMode.INSTANCE.end(mc);
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────────────── Layout ───────────────────
    private void layout() {
        closeSize = 13;
        closeX = panelX + panelW - pad - closeSize;
        closeY = panelY + 6;

        // Chaque section : un label (labelH au-dessus) puis son contrôle ; sectionGap
        // sépare le bas d'un contrôle du label de la section suivante → plus d'air.
        presetY = panelY + 24 + labelH;
        nameY = presetY + rowH + sectionGap;                 // champ nom (sans label)
        anchorY = nameY + fieldH + sectionGap + labelH;
        actionsY = anchorY + rowH + sectionGap;              // rangée [Sauver][Suppr]
        applyY = actionsY + rowH + sectionGap - 4;           // [Appliquer]
        panelH = (applyY + rowH + pad) - panelY;

        int half = (panelW - pad * 2 - 5) / 2;
        saveX = panelX + pad; saveW = half;
        delX = saveX + half + 5; delW = panelW - pad - delX;
        if (nameBox != null) { nameBox.setX(panelX + pad); nameBox.setY(nameY); nameBox.setWidth(panelW - pad * 2); }

        barY = this.height - barH - 14;
        int totalW = barBtnW * 4 + 7 * 3;
        int bx = (this.width - totalW) / 2;
        for (int i = 0; i < 4; i++) { barX[i] = bx; bx += barBtnW + 7; }
    }

    // ─────────────────── Projection ───────────────────
    private void computeCamera(Minecraft mc) {
        RepositionMode m = RepositionMode.INSTANCE;
        camPos = m.cameraPosition(mc);
        camLook = m.look();
        Vec3 r = camLook.cross(new Vec3(0, 1, 0));
        camRight = r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
        camUp = camRight.cross(camLook).normalize();
        int fov = 70;
        try { fov = mc.options.fov().get(); } catch (RuntimeException ignored) { }
        tanHalf = Math.tan(Math.toRadians(Math.max(30, Math.min(110, fov))) / 2.0);
        aspect = (double) this.width / Math.max(1, this.height);
    }

    /** Projette un point monde en coords GUI, ou {@code null} si derrière la caméra. */
    private float[] project(Vec3 p) {
        Vec3 rel = p.subtract(camPos);
        double dl = rel.dot(camLook);
        if (dl <= 0.05) return null;
        double drx = rel.dot(camRight);
        double dup = rel.dot(camUp);
        double ndcX = (drx / dl) / (tanHalf * aspect);
        double ndcY = (dup / dl) / tanHalf;
        return new float[] {
            (float) ((ndcX * 0.5 + 0.5) * this.width),
            (float) ((0.5 - ndcY * 0.5) * this.height)
        };
    }

    private void computeGizmo(Minecraft mc) {
        gizOn = false;
        if (mc.player == null) return;
        RepositionMode m = RepositionMode.INSTANCE;
        Vec3 anchor = m.anchorWorld(mc);
        float[] c = project(anchor);
        if (c == null) return;
        gizCx = c[0]; gizCy = c[1];
        gizOn = true;
        pRight = m.playerRight(mc);
        pUp = new Vec3(0, 1, 0);
        pFwd = m.playerForward(mc);
        Vec3[] dirs = { pRight, pUp, pFwd };
        for (int i = 0; i < 3; i++) {
            float[] tip = project(anchor.add(dirs[i].scale(AXIS_LEN_3D)));
            if (tip == null) { axVis[i] = false; continue; }
            float dx = tip[0] - gizCx, dy = tip[1] - gizCy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 4f) { // trop court à l'écran : direction instable
                axVis[i] = false; continue;
            }
            axVis[i] = true;
            axEndX[i] = tip[0]; axEndY[i] = tip[1];
            axDirX[i] = dx / len; axDirY[i] = dy / len;
        }

        // Anneaux de rotation : cercle dans le plan perpendiculaire à chaque axe
        // (X→plan Y-Z, Y→plan Z-X, Z→plan X-Y), projeté à l'écran → ellipse.
        if (gizmoMode == GizmoMode.ROTATE) {
            Vec3[][] plane = { { pUp, pFwd }, { pFwd, pRight }, { pRight, pUp } };
            for (int i = 0; i < 3; i++) {
                int seen = 0;
                for (int s = 0; s < RING_N; s++) {
                    double a = 2 * Math.PI * s / RING_N;
                    Vec3 pw = anchor
                        .add(plane[i][0].scale(Math.cos(a) * AXIS_LEN_3D))
                        .add(plane[i][1].scale(Math.sin(a) * AXIS_LEN_3D));
                    float[] pr = project(pw);
                    if (pr == null) { ringX[i][s] = Float.NaN; continue; }
                    ringX[i][s] = pr[0]; ringY[i][s] = pr[1]; seen++;
                }
                ringVis[i] = seen >= RING_N / 2;
            }
        }
    }

    // ─────────────────── Rendu ───────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Monde NET derrière (pas de flou) — léger vignettage seulement.
        ctx.fillGradient(0, 0, this.width, this.height,
            Colors.withAlpha(0xFF0A0709, 0.10f), Colors.withAlpha(0xFF0A0709, 0.22f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        Minecraft mc = Minecraft.getInstance();
        Font f = this.font;

        computeCamera(mc);
        computeGizmo(mc);
        if (gizmoVisible) drawGizmo(ctx, mouseX, mouseY);

        drawPanel(ctx, f, mouseX, mouseY);
        drawBottomBar(ctx, f, mouseX, mouseY);

        // Listes déroulantes AU-DESSUS.
        if (anchorOpen) drawAnchorList(ctx, f, mouseX, mouseY);
        else if (presetOpen) drawPresetList(ctx, f, mouseX, mouseY);

        Component hint = RebornFont.arcade(
            "GLISSER : orbiter   •   MOLETTE : zoom   •   GIZMO : glisser un axe   •   MAJ : fin   •   CTRL : accroche");
        drawScaledCentered(ctx, f, hint, this.width / 2f, barY - 11, INK_DIM, LBL_SCALE);
    }

    private void drawGizmo(GuiGraphicsExtractor ctx, int mx, int my) {
        if (!gizOn) return;
        int hovered = grabbedAxis >= 0 ? grabbedAxis : hitAxis(mx, my);
        int cx = Math.round(gizCx), cy = Math.round(gizCy);

        if (gizmoMode == GizmoMode.ROTATE) {
            // Anneaux (cercles de rotation) en PERLES : on voit directement le plan de
            // chaque axe. L'anneau survolé est dessiné EN DERNIER (par-dessus) avec des
            // perles plus grosses + un point de repère de sens de rotation.
            for (int i = 0; i < 3; i++) {
                if (!ringVis[i] || hovered == i) continue;
                drawRing(ctx, i, 1, Colors.withAlpha(AXIS_COLOR[i], 0.85f));
            }
            if (hovered >= 0 && ringVis[hovered]) {
                int col = Colors.lerp(AXIS_COLOR[hovered], 0xFFFFFFFF, 0.5f);
                drawRing(ctx, hovered, 2, col);
                drawRotIndicator(ctx, hovered, mx, my);
            }
            // Petit repère : les 3 axes en trait fin pour l'orientation.
            for (int i = 0; i < 3; i++) {
                if (!axVis[i]) continue;
                DrawHelpers.line(ctx, cx, cy, Math.round(axEndX[i]), Math.round(axEndY[i]),
                    Colors.withAlpha(AXIS_COLOR[i], 0.35f));
            }
            ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, Colors.withAlpha(0xFFFFFFFF, 0.8f));
            return;
        }

        // TRANSLATE / SCALE : flèches d'axe.
        Integer[] order = { 0, 1, 2 };
        java.util.Arrays.sort(order, (a, b) -> Float.compare(axLen(a), axLen(b)));
        for (int idx : order) {
            if (!axVis[idx]) continue;
            int base = AXIS_COLOR[idx];
            boolean hot = hovered == idx;
            int col = hot ? Colors.lerp(base, 0xFFFFFFFF, 0.45f) : base;
            int ex = Math.round(axEndX[idx]), ey = Math.round(axEndY[idx]);
            DrawHelpers.thickLine(ctx, cx, cy, ex, ey, hot ? 3 : 2, col);
            if (gizmoMode == GizmoMode.TRANSLATE) {
                DrawHelpers.disc(ctx, ex, ey, hot ? 5 : 4, col);
            } else { // SCALE : petit carré plein
                int s = hot ? 5 : 4;
                ctx.fill(ex - s, ey - s, ex + s, ey + s, col);
            }
        }
        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, Colors.withAlpha(0xFFFFFFFF, 0.8f));
    }

    /**
     * Trace l'anneau de rotation de l'axe en <b>perles</b> — 1 {@code ctx.fill} par
     * point échantillonné (carré {@code (2*half+1)²}). Bien moins coûteux que la version
     * filaire (thickLine = des centaines de fills par segment), d'où un mode Tourner fluide.
     */
    private void drawRing(GuiGraphicsExtractor ctx, int axis, int half, int col) {
        for (int s = 0; s < RING_N; s++) {
            float ax = ringX[axis][s], ay = ringY[axis][s];
            if (Float.isNaN(ax)) continue;
            int cx = Math.round(ax), cy = Math.round(ay);
            ctx.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, col);
        }
    }

    /**
     * Repère de sens de rotation sur l'anneau survolé : une perle blanche au point le
     * plus proche du curseur + une courte flèche tangentielle (montre dans quel sens le
     * drag fera tourner). Rend le geste circulaire explicite.
     */
    private void drawRotIndicator(GuiGraphicsExtractor ctx, int axis, int mx, int my) {
        // Point de l'anneau le plus proche du curseur.
        int bestS = -1;
        float bestD = Float.MAX_VALUE;
        for (int s = 0; s < RING_N; s++) {
            float ax = ringX[axis][s], ay = ringY[axis][s];
            if (Float.isNaN(ax)) continue;
            float dx = mx - ax, dy = my - ay, d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; bestS = s; }
        }
        if (bestS < 0) return;
        int px = Math.round(ringX[axis][bestS]), py = Math.round(ringY[axis][bestS]);
        ctx.fill(px - 2, py - 2, px + 3, py + 3, Colors.WHITE_PURE);
        float[] tan = ringTangent(axis, px, py);
        if (tan != null) {
            int ex = px + Math.round(tan[0] * 12f), ey = py + Math.round(tan[1] * 12f);
            DrawHelpers.thickLine(ctx, px, py, ex, ey, 2, Colors.WHITE_PURE);
            DrawHelpers.disc(ctx, ex, ey, 2, Colors.WHITE_PURE);
        }
    }

    private float axLen(int i) {
        if (!axVis[i]) return -1f;
        float dx = axEndX[i] - gizCx, dy = axEndY[i] - gizCy;
        return dx * dx + dy * dy;
    }

    private int hitAxis(int mx, int my) {
        if (!gizmoVisible || !gizOn) return -1;
        int best = -1;
        float bestD = 7f;
        if (gizmoMode == GizmoMode.ROTATE) {
            for (int i = 0; i < 3; i++) {
                if (!ringVis[i]) continue;
                float d = distToRing(i, mx, my);
                if (d < bestD) { bestD = d; best = i; }
            }
            return best;
        }
        for (int i = 0; i < 3; i++) {
            if (!axVis[i]) continue;
            float d = distToSegment(mx, my, gizCx, gizCy, axEndX[i], axEndY[i]);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Distance écran min. du curseur à l'anneau de rotation de l'axe. */
    private float distToRing(int axis, float mx, float my) {
        float best = Float.MAX_VALUE;
        for (int s = 0; s < RING_N; s++) {
            int n = (s + 1) % RING_N;
            float ax = ringX[axis][s], ay = ringY[axis][s];
            float bx = ringX[axis][n], by = ringY[axis][n];
            if (Float.isNaN(ax) || Float.isNaN(bx)) continue;
            float d = distToSegment(mx, my, ax, ay, bx, by);
            if (d < best) best = d;
        }
        return best;
    }

    /** Tangente écran (normalisée) de l'anneau, au point le plus proche du curseur. */
    private float[] ringTangent(int axis, float mx, float my) {
        int bestS = -1;
        float bestD = Float.MAX_VALUE;
        for (int s = 0; s < RING_N; s++) {
            float ax = ringX[axis][s], ay = ringY[axis][s];
            if (Float.isNaN(ax)) continue;
            float dx = mx - ax, dy = my - ay, d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; bestS = s; }
        }
        if (bestS < 0) return null;
        int prev = (bestS - 1 + RING_N) % RING_N, next = (bestS + 1) % RING_N;
        if (Float.isNaN(ringX[axis][prev])) prev = bestS;
        if (Float.isNaN(ringX[axis][next])) next = bestS;
        float tx = ringX[axis][next] - ringX[axis][prev];
        float ty = ringY[axis][next] - ringY[axis][prev];
        float len = (float) Math.sqrt(tx * tx + ty * ty);
        if (len < 1e-3f) return null;
        return new float[] { tx / len, ty / len };
    }

    private void applyGizmoDrag(int axis, double mx, double my, double dmx, double dmy) {
        if (axis < 0 || axis > 2) return;
        float sens = shiftDown() ? 0.25f : 1f;
        boolean snap = ctrlOrAltDown();
        if (gizmoMode == GizmoMode.TRANSLATE) {
            float proj = (float) dmx * axDirX[axis] + (float) dmy * axDirY[axis];
            float d = proj * 0.006f * sens;
            // X (droite/gauche) et Z (avant/arrière) sont inversés par rapport au repère
            // du modèle → on soustrait ; Y (haut/bas) est correct.
            switch (axis) {
                case 0 -> t.posX = clamp(t.posX - d, -4f, 4f);
                case 1 -> t.posY = clamp(t.posY - d, -4f, 4f);
                case 2 -> t.posZ = clamp(t.posZ - d, -4f, 4f);
                default -> { }
            }
            if (snap) snapPos(axis);
        } else if (gizmoMode == GizmoMode.ROTATE) {
            // Drag TANGENTIEL à l'anneau : on suit le geste circulaire (intuitif).
            float[] tan = ringTangent(axis, (float) mx, (float) my);
            float proj = tan != null
                ? (float) dmx * tan[0] + (float) dmy * tan[1]
                : (float) dmx * axDirX[axis] + (float) dmy * axDirY[axis];
            float d = proj * 0.55f * sens;
            switch (axis) {
                case 0 -> t.rotX = wrap180(t.rotX + d);
                case 1 -> t.rotY = wrap180(t.rotY + d);
                case 2 -> t.rotZ = wrap180(t.rotZ + d);
                default -> { }
            }
            if (snap) snapRot(axis);
        } else { // SCALE — uniforme, glisser un axe vers l'extérieur agrandit
            float proj = (float) dmx * axDirX[axis] + (float) dmy * axDirY[axis];
            float d = proj * 0.004f * sens;
            t.scale = clamp(t.scale + d, 0.05f, 5f);
            if (snap) t.scale = Math.round(t.scale / 0.05f) * 0.05f;
        }
    }

    private void snapPos(int axis) {
        switch (axis) {
            case 0 -> t.posX = Math.round(t.posX / 0.05f) * 0.05f;
            case 1 -> t.posY = Math.round(t.posY / 0.05f) * 0.05f;
            case 2 -> t.posZ = Math.round(t.posZ / 0.05f) * 0.05f;
            default -> { }
        }
    }

    private void snapRot(int axis) {
        switch (axis) {
            case 0 -> t.rotX = Math.round(t.rotX / 15f) * 15f;
            case 1 -> t.rotY = Math.round(t.rotY / 15f) * 15f;
            case 2 -> t.rotZ = Math.round(t.rotZ / 15f) * 15f;
            default -> { }
        }
    }

    // ─────────────────── Texte à l'échelle (menu compact) ───────────────────

    /** Texte (Component) mis à l'échelle, coin haut-gauche à (x, y). */
    private void drawScaled(GuiGraphicsExtractor ctx, Font f, Component c, float x, float y, int color, float scale) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.text(f, c, 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    /** Texte (Component) mis à l'échelle, centré horizontalement autour de cx. */
    private void drawScaledCentered(GuiGraphicsExtractor ctx, Font f, Component c, float cx, float y, int color, float scale) {
        drawScaled(ctx, f, c, cx - (f.width(c) * scale) / 2f, y, color, scale);
    }

    /** Label de section (petites capitales arcade, échelle réduite). */
    private void sectionLabel(GuiGraphicsExtractor ctx, Font f, String s, int x, int y, int color) {
        drawScaled(ctx, f, RebornFont.arcade(s), x, y, color, LBL_SCALE);
    }

    /** Ordonnée pour centrer verticalement un texte d'échelle {@code scale} dans une boîte. */
    private static float centerY(int boxY, int h, float scale) {
        return boxY + (h - 8f * scale) / 2f;
    }

    // ─────────────────── Panneau CONFIGURATION ───────────────────
    private void drawPanel(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        DrawHelpers.roundedOutlinedRectFull(ctx, panelX, panelY, panelW, panelH, 7, PANEL_FILL, PANEL_BORDER);
        ctx.fill(panelX + 8, panelY + 1, panelX + panelW - 8, panelY + 2, Colors.withAlpha(ACC, 0.28f));

        // Titre + pastille + cosmétique ciblé + croix.
        DrawHelpers.ring(ctx, panelX + pad + 3, panelY + 11, 3, 2, Colors.withAlpha(ACC_HOVER, 0.9f));
        drawScaled(ctx, f, RebornFont.arcade("CONFIGURATION"), panelX + pad + 11, panelY + 8, INK, LBL_SCALE);
        // Cosmétique en cours d'édition, à droite (juste avant la croix).
        Component tgt = Component.literal(cosmeticLabel);
        float tgtW = f.width(tgt) * LBL_SCALE;
        drawScaled(ctx, f, tgt, closeX - 5 - tgtW, panelY + 8, Colors.withAlpha(ACC_HOVER, 0.85f), LBL_SCALE);
        boolean ch = inside(mx, my, closeX, closeY, closeSize, closeSize);
        drawScaledCentered(ctx, f, Component.literal("✕"), closeX + closeSize / 2f, closeY + 3,
            ch ? Colors.WHITE_PURE : INK_DIM, VAL_SCALE);

        // Dropdown preset.
        sectionLabel(ctx, f, "PRESET", panelX + pad, presetY - labelH, INK_DIM);
        String presetLbl = selectedPreset != null ? selectedPreset : "Choisir un preset…";
        drawDropdownBox(ctx, f, panelX + pad, presetY, panelW - pad * 2, rowH, presetLbl,
            selectedPreset != null, presetOpen, mx, my);

        // Champ nom.
        nameBox.extractRenderState(ctx, mx, my, 0f);
        ctx.fill(nameBox.getX() - 2, nameY + fieldH, nameBox.getX() + nameBox.getWidth(), nameY + fieldH + 1,
            Colors.withAlpha(0xFFFFFFFF, 0.18f));

        // Dropdown ancrage.
        sectionLabel(ctx, f, "ANCRAGE", panelX + pad, anchorY - labelH, INK_DIM);
        drawDropdownBox(ctx, f, panelX + pad, anchorY, panelW - pad * 2, rowH, t.anchor.label,
            true, anchorOpen, mx, my);

        // Rangée [Sauvegarder] [Supprimer].
        drawButton(ctx, f, saveX, actionsY, saveW, rowH, "Sauver", Colors.SUCCESS, mx, my);
        drawButton(ctx, f, delX, actionsY, delW, rowH, "Supprimer", Colors.DANGER, mx, my);

        // [Appliquer] pleine largeur.
        drawButton(ctx, f, panelX + pad, applyY, panelW - pad * 2, rowH, "✓ Appliquer", ACC_HOVER, mx, my);
    }

    private void drawDropdownBox(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h,
                                 String label, boolean strong, boolean open, int mx, int my) {
        boolean hov = inside(mx, my, x, y, w, h);
        int fill = open || hov ? Colors.withAlpha(ACC_HOVER, 0.22f) : Colors.withAlpha(0xFF000000, 0.40f);
        int border = open ? ACC_HOVER : hov ? ACC_HOVER : Colors.withAlpha(ACC, 0.40f);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 3, fill, border);
        drawScaled(ctx, f, Component.literal(trim(f, label, (int) ((w - 16) / VAL_SCALE))), x + 5,
            centerY(y, h, VAL_SCALE), strong ? INK : INK_DIM, VAL_SCALE);
        // Chevron.
        int ax = x + w - 10, ay = y + h / 2 - 1;
        DrawHelpers.line(ctx, ax, ay, ax + 3, ay + 3, INK);
        DrawHelpers.line(ctx, ax + 3, ay + 3, ax + 6, ay, INK);
    }

    private void drawButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h,
                            String label, int accent, int mx, int my) {
        boolean hov = inside(mx, my, x, y, w, h);
        int fill = hov ? Colors.withAlpha(accent, 0.28f) : Colors.withAlpha(0xFF000000, 0.40f);
        int border = hov ? accent : Colors.withAlpha(accent, 0.55f);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 3, fill, border);
        String lbl = trim(f, label, (int) ((w - 6) / VAL_SCALE));
        drawScaledCentered(ctx, f, Component.literal(lbl), x + w / 2f, centerY(y, h, VAL_SCALE),
            hov ? Colors.WHITE_PURE : INK, VAL_SCALE);
    }

    private static final int ITEM_H = 13;

    private void drawPresetList(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        List<String> names = CosmeticPresets.list(cosmeticId);
        int x = panelX + pad, w = panelW - pad * 2, y = presetY + rowH + 2;
        int h = Math.max(ITEM_H, names.size() * ITEM_H) + 4;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 3,
            Colors.withAlpha(0xFF0C0A0D, 0.97f), Colors.withAlpha(ACC, 0.6f));
        if (names.isEmpty()) {
            drawScaled(ctx, f, Component.literal("Aucun preset enregistré."), x + 5, y + 4, INK_DIM, VAL_SCALE);
            return;
        }
        int iy = y + 2;
        for (String name : names) {
            boolean hov = inside(mx, my, x + 2, iy, w - 4, ITEM_H);
            if (hov) ctx.fill(x + 2, iy, x + w - 2, iy + ITEM_H, Colors.withAlpha(ACC_HOVER, 0.24f));
            drawScaled(ctx, f, Component.literal(trim(f, name, (int) ((w - 10) / VAL_SCALE))), x + 5,
                centerY(iy, ITEM_H, VAL_SCALE), hov ? Colors.WHITE_PURE : INK, VAL_SCALE);
            iy += ITEM_H;
        }
    }

    private void drawAnchorList(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        CosmeticTransform.Anchor[] vals = CosmeticTransform.Anchor.values();
        int x = panelX + pad, w = panelW - pad * 2, y = anchorY + rowH + 2;
        int h = vals.length * ITEM_H + 4;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 3,
            Colors.withAlpha(0xFF0C0A0D, 0.97f), Colors.withAlpha(ACC, 0.6f));
        int iy = y + 2;
        for (CosmeticTransform.Anchor a : vals) {
            boolean hov = inside(mx, my, x + 2, iy, w - 4, ITEM_H);
            boolean sel = a == t.anchor;
            if (hov) ctx.fill(x + 2, iy, x + w - 2, iy + ITEM_H, Colors.withAlpha(ACC_HOVER, 0.24f));
            drawScaled(ctx, f, Component.literal(a.label), x + 5, centerY(iy, ITEM_H, VAL_SCALE),
                sel ? ACC_HOVER : hov ? Colors.WHITE_PURE : INK, VAL_SCALE);
            iy += ITEM_H;
        }
    }

    // ─────────────────── Barre du bas ───────────────────
    private void drawBottomBar(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        String[] labels = { "Déplacer", "Tourner", "Échelle", "Masquer" };
        for (int i = 0; i < 4; i++) {
            int x = barX[i];
            boolean hov = inside(mx, my, x, barY, barBtnW, barH);
            boolean sel = (i == 0 && gizmoMode == GizmoMode.TRANSLATE)
                || (i == 1 && gizmoMode == GizmoMode.ROTATE)
                || (i == 2 && gizmoMode == GizmoMode.SCALE)
                || (i == 3 && !gizmoVisible);
            int fill = sel ? Colors.withAlpha(ACC_HOVER, 0.34f)
                : hov ? Colors.withAlpha(ACC_HOVER, 0.18f) : PANEL_FILL;
            int border = sel ? ACC_HOVER : hov ? ACC_HOVER : PANEL_BORDER;
            DrawHelpers.roundedOutlinedRectFull(ctx, x, barY, barBtnW, barH, 6, fill, border);
            int gcx = x + 15, gcy = barY + barH / 2;
            int gcol = sel || hov ? Colors.WHITE_PURE : INK;
            drawBarGlyph(ctx, i, gcx, gcy, gcol);
            drawScaled(ctx, f, Component.literal(labels[i]), x + 27, centerY(barY, barH, VAL_SCALE),
                sel || hov ? Colors.WHITE_PURE : INK, VAL_SCALE);
        }
    }

    private void drawBarGlyph(GuiGraphicsExtractor ctx, int type, int cx, int cy, int col) {
        switch (type) {
            case 0 -> { // Déplacer : croix 4 directions
                DrawHelpers.thickLine(ctx, cx - 6, cy, cx + 6, cy, 2, col);
                DrawHelpers.thickLine(ctx, cx, cy - 6, cx, cy + 6, 2, col);
                DrawHelpers.disc(ctx, cx + 6, cy, 2, col);
                DrawHelpers.disc(ctx, cx - 6, cy, 2, col);
                DrawHelpers.disc(ctx, cx, cy + 6, 2, col);
                DrawHelpers.disc(ctx, cx, cy - 6, 2, col);
            }
            case 1 -> DrawHelpers.ring(ctx, cx, cy, 6, 2, col); // Tourner : anneau
            case 2 -> { // Échelle : carré + flèches diagonales
                DrawHelpers.thickLine(ctx, cx - 5, cy - 5, cx + 5, cy - 5, 2, col);
                DrawHelpers.thickLine(ctx, cx - 5, cy + 5, cx + 5, cy + 5, 2, col);
                DrawHelpers.thickLine(ctx, cx - 5, cy - 5, cx - 5, cy + 5, 2, col);
                DrawHelpers.thickLine(ctx, cx + 5, cy - 5, cx + 5, cy + 5, 2, col);
                DrawHelpers.thickLine(ctx, cx - 3, cy - 3, cx + 3, cy + 3, 2, col);
            }
            case 3 -> { // Masquer : anneau barré
                DrawHelpers.ring(ctx, cx, cy, 6, 2, col);
                DrawHelpers.thickLine(ctx, cx - 5, cy - 5, cx + 5, cy + 5, 2, col);
            }
            default -> { }
        }
    }

    // ─────────────────── Interactions ───────────────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        layout();
        int mx = (int) e.x(), my = (int) e.y();
        if (e.button() != 0) return super.mouseClicked(e, dbl);

        // Listes déroulantes ouvertes : priorité absolue.
        if (anchorOpen) { handleAnchorList(mx, my); return true; }
        if (presetOpen) { handlePresetList(mx, my); return true; }

        // Croix de fermeture.
        if (inside(mx, my, closeX, closeY, closeSize, closeSize)) { RebornSounds.uiClick(); onClose(); return true; }

        // Gizmo : attraper un axe (hors panneau/barre).
        if (gizmoVisible && !overPanel(mx, my) && !overBar(mx, my)) {
            int axis = hitAxis(mx, my);
            if (axis >= 0) { grabbedAxis = axis; RebornSounds.uiClick(); return true; }
        }

        // Dropdown preset / ancrage.
        if (inside(mx, my, panelX + pad, presetY, panelW - pad * 2, rowH)) {
            presetOpen = !presetOpen; anchorOpen = false; RebornSounds.uiClick(); return true;
        }
        if (inside(mx, my, panelX + pad, anchorY, panelW - pad * 2, rowH)) {
            anchorOpen = !anchorOpen; presetOpen = false; RebornSounds.uiClick(); return true;
        }
        // Boutons.
        if (inside(mx, my, saveX, actionsY, saveW, rowH)) { doSave(); return true; }
        if (inside(mx, my, delX, actionsY, delW, rowH)) { doDelete(); return true; }
        if (inside(mx, my, panelX + pad, applyY, panelW - pad * 2, rowH)) { doApply(); return true; }

        // Champ nom (poser le focus au niveau de l'écran, sinon charTyped ne reçoit rien).
        if (inside(mx, my, nameBox.getX(), nameBox.getY(), nameBox.getWidth(), fieldH + 2)) {
            nameBox.mouseClicked(e, dbl);
            setFocused(nameBox);
            nameBox.setFocused(true);
            return true;
        }

        // Barre du bas.
        for (int i = 0; i < 4; i++) {
            if (inside(mx, my, barX[i], barY, barBtnW, barH)) {
                if (i == 0) gizmoMode = GizmoMode.TRANSLATE;
                else if (i == 1) gizmoMode = GizmoMode.ROTATE;
                else if (i == 2) gizmoMode = GizmoMode.SCALE;
                else gizmoVisible = !gizmoVisible;
                RebornSounds.charNav();
                return true;
            }
        }

        // Sinon : orbite (glisser sur le monde).
        if (!overPanel(mx, my) && !overBar(mx, my)) {
            orbiting = true;
            if (nameBox.isFocused()) { nameBox.setFocused(false); setFocused(null); }
            return true;
        }
        if (nameBox.isFocused()) nameBox.setFocused(false);
        return super.mouseClicked(e, dbl);
    }

    private void handlePresetList(int mx, int my) {
        List<String> names = CosmeticPresets.list(cosmeticId);
        int x = panelX + pad, w = panelW - pad * 2, y = presetY + rowH + 2 + 2;
        for (String name : names) {
            if (inside(mx, my, x + 2, y, w - 4, ITEM_H)) {
                CosmeticTransform loaded = CosmeticPresets.load(cosmeticId, name);
                if (loaded != null) {
                    t.copyFrom(loaded);
                    selectedPreset = name;
                    nameBox.setValue(name);
                    RebornSounds.confirm();
                }
                presetOpen = false;
                return;
            }
            y += ITEM_H;
        }
        presetOpen = false;
    }

    private void handleAnchorList(int mx, int my) {
        CosmeticTransform.Anchor[] vals = CosmeticTransform.Anchor.values();
        int x = panelX + pad, w = panelW - pad * 2, y = anchorY + rowH + 2 + 2;
        for (CosmeticTransform.Anchor a : vals) {
            if (inside(mx, my, x + 2, y, w - 4, ITEM_H)) {
                t.anchor = a; RebornSounds.charNav(); anchorOpen = false; return;
            }
            y += ITEM_H;
        }
        anchorOpen = false;
    }

    private void doSave() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty()) {
            CosmeticPresets.save(cosmeticId, name, t.copy());
            selectedPreset = name;
            RebornSounds.confirm();
        } else {
            RebornSounds.uiClick();
        }
    }

    private void doDelete() {
        String name = selectedPreset != null ? selectedPreset : nameBox.getValue().trim();
        if (name != null && !name.isEmpty()) {
            CosmeticPresets.delete(cosmeticId, name);
            if (name.equals(selectedPreset)) selectedPreset = null;
            RebornSounds.uiClick();
        }
    }

    private void doApply() {
        CosmeticPresets.saveApplied(cosmeticId, t.copy());
        // Synchronise le placement au serveur → rediffusé aux AUTRES joueurs sur
        // reborn:cosmetics (sinon ils voyaient le placement par défaut). Format
        // \n-délimité : l'id de modèle contient un ':' qui casserait le split ':'
        // côté serveur.
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                fr.reborn.hud.menu.inventory.InventoryPayload.ID)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new fr.reborn.hud.menu.inventory.InventoryPayload(
                    "cos:tf\n" + cosmeticId + "\n" + t.serialize()));
        }
        RebornSounds.confirm();
        onClose();
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dragX, double dragY) {
        if (grabbedAxis >= 0) { applyGizmoDrag(grabbedAxis, e.x(), e.y(), dragX, dragY); return true; }
        if (orbiting) { RepositionMode.INSTANCE.orbit(dragX, dragY); return true; }
        return super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        boolean consumed = grabbedAxis >= 0 || orbiting;
        grabbedAxis = -1;
        orbiting = false;
        return consumed || super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        layout();
        if (!overPanel((int) mouseX, (int) mouseY)) {
            RepositionMode.INSTANCE.zoom(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (anchorOpen || presetOpen) { anchorOpen = false; presetOpen = false; return true; }
            if (nameBox.isFocused()) { nameBox.setFocused(false); return true; }
            onClose();
            return true;
        }
        // Saisie dans le champ nom (retour arrière, flèches, entrée…).
        if (nameBox != null && nameBox.isFocused() && nameBox.keyPressed(e)) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        // Sans ce forward, les lettres ne s'écrivaient jamais dans le champ nom.
        if (nameBox != null && nameBox.isFocused() && nameBox.charTyped(event)) return true;
        return super.charTyped(event);
    }

    private boolean overPanel(int mx, int my) {
        return inside(mx, my, panelX, panelY, panelW, panelH);
    }

    private boolean overBar(int mx, int my) {
        return my >= barY && my < barY + barH
            && mx >= barX[0] && mx < barX[3] + barBtnW;
    }

    // ─────────────────── Utils ───────────────────
    private static boolean shiftDown() {
        com.mojang.blaze3d.platform.Window w = Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean ctrlOrAltDown() {
        com.mojang.blaze3d.platform.Window w = Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static String trim(Font f, String s, int maxW) {
        if (f.width(s) <= maxW) return s;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (f.width(sb.toString() + c + "…") > maxW) break;
            sb.append(c);
        }
        return sb + "…";
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float wrap180(float deg) {
        return ((deg + 180f) % 360f + 360f) % 360f - 180f;
    }

    private static float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float len2 = dx * dx + dy * dy;
        if (len2 < 1e-4f) { float ex = px - ax, ey = py - ay; return (float) Math.sqrt(ex * ex + ey * ey); }
        float tt = ((px - ax) * dx + (py - ay) * dy) / len2;
        tt = Math.max(0f, Math.min(1f, tt));
        float cx = ax + tt * dx, cy = ay + tt * dy;
        float ex = px - cx, ey = py - cy;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }
}
