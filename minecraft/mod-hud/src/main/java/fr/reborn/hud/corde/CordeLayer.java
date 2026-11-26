package fr.reborn.hud.corde;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * <b>Corde de ligotage dessinée par le mod</b> (prototype du 26/11) : de vraies boîtes fines posées sur le modèle du
 * joueur, habillées de la bande torsadée de la v2 ({@code textures/entity/corde/corde.png}, script
 * {@code Maquettes/gameplan/20_stele_corde/v3/corde_v3_texture.py}).
 *
 * <p>À chaque image, la couche relit les membres <b>déjà posés</b> ({@code setupAnim} et la pose de {@link CordePose}
 * sont passés avant les couches) et en déduit la corde :
 * <ul>
 *   <li>chaque tour du torse est l'<b>enveloppe convexe</b> du torse et des deux bras, coupés à la même hauteur dans le
 *       repère du torse : une seule bande continue qui fait le tour du corps et des bras ensemble, droite sur le
 *       ventre, en biais sur l'avant des bras, tendue en travers des avant-bras dans le dos ;</li>
 *   <li>les brins sont des boîtes de 1,5 px de haut et 1 px d'épaisseur, posées à 0,22 px de la peau (sous la couche
 *       extérieure du skin, gonflée de 0,25 : elle passe sous la corde) ; les coins se rejoignent par l'extérieur ;</li>
 *   <li>bretelles devant, croix et noeud dans le dos (comme la v2), et en {@link CordeLigotes#COMPLET} un tour aux
 *       genoux et un aux chevilles autour des deux jambes, reliés par un brin.</li>
 * </ul>
 * La corde suit donc tout ce que font les membres (fondu de la pose, souffle, marche des jambes libres) sans casser.
 *
 * <p>Rien n'est fait pour un avatar qui n'est pas dans {@link CordeLigotes} (un entier lu si la liste est vide).
 * Aucune allocation par image : tableaux de travail fixes (fil de rendu seul) et réserve tournante de
 * {@value #RESERVE} géométries, chacune remplie puis dessinée dans la même image.
 */
public final class CordeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/entity/corde/corde.png");
    private static RenderType rendu;

    /** Gabarit de la texture (texels) : 128 x 32, 4 texels par pixel du jeu. */
    private static final float TW = 128f, TH = 32f, K = 4f;

    /* --- mesures (pixels du jeu) */
    /** Demi-hauteur et demi-épaisseur d'un tour ; d'une bretelle ou d'un brin de la croix (plus mince : passe dessous). */
    private static final float HW = 0.75f, HD = 0.5f, HW2 = 0.7f, HD2 = 0.42f;
    /** Écart entre la peau et la face intérieure de la corde (≤ 0,25 : la couche extérieure du skin passe dessous). */
    private static final float JEU = 0.22f;
    /** Tours du torse, depuis le haut des épaules (v2 : au-dessus des coudes, à la taille). */
    private static final float Y_TOUR_1 = 3.5f, Y_TOUR_2 = 8.5f;
    /** Tours des jambes, en repère du modèle (haut des cuisses à 12) : au-dessus du genou, au-dessus de la cheville. */
    private static final float Y_GENOU = 12f + 4.4f, Y_CHEVILLE = 12f + 10f;

    /* --- réserve de géométries */
    private static final int RESERVE = 32;
    private static final int MAX_BRINS = 64, PAS_BRIN = 18;
    private static final Geometrie[] GEO = new Geometrie[RESERVE];
    private static int suivante;

    static {
        for (int i = 0; i < RESERVE; i++) GEO[i] = new Geometrie();
    }

    public CordeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (CordeLigotes.n == 0) return;
        byte v = CordeLigotes.variante(state.id);
        if (v == CordeLigotes.AUCUNE || state.isInvisible || state.isSpectator) return;
        boolean fin = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
        Geometrie g = GEO[suivante];
        suivante = (suivante + 1) % RESERVE;
        g.n = 0;
        g.light = light;
        construire(g, getParentModel(), fin, v == CordeLigotes.COMPLET);
        if (g.n == 0) return;
        if (rendu == null) rendu = RenderTypes.entityCutout(TEXTURE);
        poseStack.pushPose();
        poseStack.scale(1f / 16f, 1f / 16f, 1f / 16f);
        collector.submitCustomGeometry(poseStack, rendu, g);
        poseStack.popPose();
    }

    /* ================================================================ construction (fil de rendu) */

    /** Repère courant : position et rotation (3 x 3, par lignes) qui ramènent ses coordonnées au repère du modèle. */
    private static final float[] FP = new float[3], FR = new float[9];
    /** Membre coupé : position et rotation. */
    private static final float[] MP = new float[3], MR = new float[9];
    /** Points coupés (x, z dans le repère courant), enveloppe. */
    private static final float[] PX = new float[16], PZ = new float[16];
    private static int np;
    private static final int[] ORD = new int[16], ENV = new int[34];
    private static final float[] HX = new float[18], HZ = new float[18];
    private static int hn;
    /** Profondeur avant et arrière du dernier tour construit (repère courant, peau). */
    private static float zAvant, zArriere;

    private static void construire(Geometrie g, PlayerModel m, boolean fin, boolean complet) {
        // ---- torse et bras, repère du torse
        repere(m.body);
        float bd0 = fin ? -2f : -3f, bd1 = 1f, bg0 = -1f, bg1 = fin ? 2f : 3f;
        float avant1 = -2f, arriere1 = 2f;
        for (int k = 0; k < 2; k++) {
            float h = k == 0 ? Y_TOUR_1 : Y_TOUR_2;
            np = 0;
            couper(m.body, -4f, 4f, 0f, 12f, -2f, 2f, h);
            couper(m.rightArm, bd0, bd1, -2f, 10f, -2f, 2f, h);
            couper(m.leftArm, bg0, bg1, -2f, 10f, -2f, 2f, h);
            if (np < 3) continue;
            enveloppe();
            tour(g, h);
            if (k == 0) { avant1 = zAvant; arriere1 = zArriere; }
        }
        // ---- bretelles devant : du cou (sous le bord de la tête) au premier tour, en s'écartant
        float zb = -2f - JEU - HD2;
        brin(g, -2.1f, -0.3f, zb, -3.0f, Y_TOUR_1, Math.min(zb, avant1 - JEU - HD2), 0f, 0f, -1f, HW2, HD2, 0f, 0.3f, 2f, 0);
        brin(g, 2.1f, -0.3f, zb, 3.0f, Y_TOUR_1, Math.min(zb, avant1 - JEU - HD2), 0f, 0f, -1f, HW2, HD2, 0f, 0.3f, 6f, 0);
        // ---- croix du dos : de chaque épaule au second tour, côté opposé ; elle se croise sous le noeud
        float zc = 2f + JEU + HD2;
        brin(g, -2.9f, -0.3f, zc, 2.3f, Y_TOUR_2, zc, 0f, 0f, 1f, HW2, HD2, 0f, 0f, 1f, 0);
        brin(g, 2.9f, -0.3f, zc, -2.3f, Y_TOUR_2, zc, 0f, 0f, 1f, HW2, HD2, 0f, 0f, 9f, 0);
        // ---- noeud plat au centre du dos, sur le premier tour : deux lobes, celui de droite devant ; bout libre
        float zn = Math.max(arriere1, 2f) + JEU + 2f * HD - 0.25f;
        brin(g, -2.05f, Y_TOUR_1 - 0.1f, zn + 0.55f, 0.15f, Y_TOUR_1 - 0.1f, zn + 0.55f, 0f, 0f, 1f, 1.0f, 0.55f, 0f, 0f, 0f, 1);
        brin(g, -0.25f, Y_TOUR_1 + 0.15f, zn + 0.68f, 1.95f, Y_TOUR_1 + 0.15f, zn + 0.68f, 0f, 0f, 1f, 1.0f, 0.55f, 0f, 0f, 0f, 1);
        brin(g, 0.9f, Y_TOUR_1 + 0.9f, zn + 0.45f, 1.5f, Y_TOUR_1 + 3.6f, zn + 0.2f, 0f, 0f, 1f, 0.55f, 0.4f, 0f, 0.1f, 3f, 0);

        if (!complet) return;
        // ---- jambes : repère du modèle (les jambes ne sont pas des enfants du torse)
        identite();
        float avantG = -2f, avantC = -2f, xG = 0f, xC = 0f;
        for (int k = 0; k < 2; k++) {
            float h = k == 0 ? Y_GENOU : Y_CHEVILLE;
            np = 0;
            couper(m.rightLeg, -2f, 2f, 0f, 12f, -2f, 2f, h);
            couper(m.leftLeg, -2f, 2f, 0f, 12f, -2f, 2f, h);
            if (np < 3) continue;
            float milieu = 0f;
            for (int i = 0; i < np; i++) milieu += PX[i];
            enveloppe();
            tour(g, h);
            if (k == 0) { avantG = zAvant; xG = milieu / np; } else { avantC = zAvant; xC = milieu / np; }
        }
        // brin de liaison devant, entre les deux jambes, du genou à la cheville
        brin(g, xG, Y_GENOU, avantG - JEU - HD2, xC, Y_CHEVILLE, avantC - JEU - HD2, 0f, 0f, -1f, HW2 * 0.8f, HD2, 0.4f, 0.4f, 0f, 0);
    }

    /** Le repère courant devient celui de {@code p} (sa pose animée), en repère du modèle. */
    private static void repere(ModelPart p) {
        FP[0] = p.x; FP[1] = p.y; FP[2] = p.z;
        rotation(p, FR);
    }

    private static void identite() {
        FP[0] = FP[1] = FP[2] = 0f;
        FR[0] = 1f; FR[1] = 0f; FR[2] = 0f;
        FR[3] = 0f; FR[4] = 1f; FR[5] = 0f;
        FR[6] = 0f; FR[7] = 0f; FR[8] = 1f;
    }

    /** R = Rz · Ry · Rx, comme {@code ModelPart.translateAndRotate} ({@code rotationZYX}). */
    private static void rotation(ModelPart p, float[] r) {
        float cx = (float) Math.cos(p.xRot), sx = (float) Math.sin(p.xRot);
        float cy = (float) Math.cos(p.yRot), sy = (float) Math.sin(p.yRot);
        float cz = (float) Math.cos(p.zRot), sz = (float) Math.sin(p.zRot);
        r[0] = cz * cy; r[1] = cz * sy * sx - sz * cx; r[2] = cz * sy * cx + sz * sx;
        r[3] = sz * cy; r[4] = sz * sy * sx + cz * cx; r[5] = sz * sy * cx - cz * sx;
        r[6] = -sy;     r[7] = cy * sx;                r[8] = cy * cx;
    }

    /** Coupe le membre {@code p} (boîte locale) à la hauteur {@code h} du repère courant : ses 4 arêtes verticales. */
    private static void couper(ModelPart p, float x0, float x1, float y0, float y1, float z0, float z1, float h) {
        MP[0] = p.x; MP[1] = p.y; MP[2] = p.z;
        rotation(p, MR);
        float sx = p.xScale, sy = p.yScale, sz = p.zScale;
        // direction de l'arête (axe y du membre) dans le repère courant : FRᵀ · MR · (0, sy, 0)
        float ex = MR[1] * sy, ey = MR[4] * sy, ez = MR[7] * sy;
        float dx = FR[0] * ex + FR[3] * ey + FR[6] * ez;
        float dy = FR[1] * ex + FR[4] * ey + FR[7] * ez;
        float dz = FR[2] * ex + FR[5] * ey + FR[8] * ez;
        if (Math.abs(dy) < 0.25f) return;                       // membre presque à l'horizontale : pas de coupe
        int debut = np;
        for (int c = 0; c < 4; c++) {
            float lx = ((c & 1) == 0 ? x0 : x1) * sx, lz = ((c & 2) == 0 ? z0 : z1) * sz;
            // point d'arête à t = 0, en repère du modèle puis du repère courant
            float rx = MP[0] + MR[0] * lx + MR[2] * lz - FP[0];
            float ry = MP[1] + MR[3] * lx + MR[5] * lz - FP[1];
            float rz = MP[2] + MR[6] * lx + MR[8] * lz - FP[2];
            float qx = FR[0] * rx + FR[3] * ry + FR[6] * rz;
            float qy = FR[1] * rx + FR[4] * ry + FR[7] * rz;
            float qz = FR[2] * rx + FR[5] * ry + FR[8] * rz;
            float t = (h - qy) / dy;
            if (t < y0 - 0.5f || t > y1 + 0.5f) { np = debut; return; }   // le membre n'est pas à cette hauteur
            PX[np] = qx + t * dx;
            PZ[np] = qz + t * dz;
            np++;
        }
    }

    /** Enveloppe convexe des points coupés (chaîne monotone d'Andrew), sens trigonométrique dans le plan (x, z). */
    private static void enveloppe() {
        for (int i = 0; i < np; i++) ORD[i] = i;
        for (int i = 1; i < np; i++) {                           // tri par insertion (12 points au plus)
            int a = ORD[i], j = i - 1;
            while (j >= 0 && (PX[ORD[j]] > PX[a] || (PX[ORD[j]] == PX[a] && PZ[ORD[j]] > PZ[a]))) { ORD[j + 1] = ORD[j]; j--; }
            ORD[j + 1] = a;
        }
        int k = 0;
        for (int i = 0; i < np; i++) {
            while (k >= 2 && vect(ENV[k - 2], ENV[k - 1], ORD[i]) <= 1e-3f) k--;
            ENV[k++] = ORD[i];
        }
        for (int i = np - 2, t = k + 1; i >= 0; i--) {
            while (k >= t && vect(ENV[k - 2], ENV[k - 1], ORD[i]) <= 1e-3f) k--;
            ENV[k++] = ORD[i];
        }
        hn = k - 1;
        for (int i = 0; i < hn; i++) { HX[i] = PX[ENV[i]]; HZ[i] = PZ[ENV[i]]; }
        HX[hn] = HX[0];
        HZ[hn] = HZ[0];
    }

    private static float vect(int o, int a, int b) {
        return (PX[a] - PX[o]) * (PZ[b] - PZ[o]) - (PZ[a] - PZ[o]) * (PX[b] - PX[o]);
    }

    /** Un tour : un brin par côté de l'enveloppe, poussé vers l'extérieur, prolongé jusqu'à rejoindre ses voisins. */
    private static void tour(Geometrie g, float h) {
        float off = JEU + HD, dehors = JEU + 2f * HD;
        zAvant = Float.MAX_VALUE;
        zArriere = -Float.MAX_VALUE;
        float perim = 0f;
        for (int i = 0; i < hn; i++) {
            zAvant = Math.min(zAvant, HZ[i]);
            zArriere = Math.max(zArriere, HZ[i]);
            float dx = HX[i + 1] - HX[i], dz = HZ[i + 1] - HZ[i];
            float l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-3f) continue;
            dx /= l; dz /= l;
            int p = (i + hn - 1) % hn, s = (i + 1) % hn;
            float e0 = dehors * demiTangente(HX[i] - HX[p], HZ[i] - HZ[p], dx, dz) + 0.03f;
            float e1 = dehors * demiTangente(dx, dz, HX[s + 1] - HX[s], HZ[s + 1] - HZ[s]) + 0.03f;
            float nx = dz, nz = -dx;                              // normale extérieure (sens trigonométrique)
            float hw = HW + ((i & 1) == 0 ? 0f : 0.012f);         // les dessus des coins ne se battent pas
            float phase = ((perim - e0) * K) % 16f;
            if (phase < 0f) phase += 16f;
            brin(g, HX[i] + nx * off, h, HZ[i] + nz * off, HX[i + 1] + nx * off, h, HZ[i + 1] + nz * off,
                nx, 0f, nz, hw, HD, e0, e1, phase, 0);
            perim += l;
        }
    }

    /** tan(φ/2) de l'angle φ dont on tourne en passant de la direction a à la direction b (non normées). */
    private static float demiTangente(float ax, float az, float bx, float bz) {
        float la = (float) Math.sqrt(ax * ax + az * az), lb = (float) Math.sqrt(bx * bx + bz * bz);
        if (la < 1e-4f || lb < 1e-4f) return 0f;
        float c = (ax * bx + az * bz) / (la * lb), s = Math.abs(ax * bz - az * bx) / (la * lb);
        return s / (1f + Math.max(c, -0.95f));
    }

    /**
     * Un brin droit de A à B (repère courant), prolongé de {@code e0} et {@code e1}, face extérieure vers {@code n} ;
     * converti en repère du modèle et rangé dans {@code g}. {@code type} 0 : corde ; 1 : lobe de noeud.
     */
    private static void brin(Geometrie g, float ax, float ay, float az, float bx, float by, float bz,
                             float nx, float ny, float nz, float hw, float hd, float e0, float e1, float phase, int type) {
        if (g.n >= MAX_BRINS) return;
        float ux = bx - ax, uy = by - ay, uz = bz - az;
        float l = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (l < 1e-4f) return;
        ux /= l; uy /= l; uz /= l;
        float d = nx * ux + ny * uy + nz * uz;                   // n perpendiculaire au brin
        nx -= d * ux; ny -= d * uy; nz -= d * uz;
        float ln = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (ln < 1e-4f) return;
        nx /= ln; ny /= ln; nz /= ln;
        float wx = uy * nz - uz * ny, wy = uz * nx - ux * nz, wz = ux * ny - uy * nx;
        if (wy > 0f) { wx = -wx; wy = -wy; wz = -wz; }          // w vers le haut (y du modèle vers le bas)
        float sx = ax - ux * e0, sy = ay - uy * e0, sz = az - uz * e0;
        float[] f = g.f;
        int o = g.n * PAS_BRIN;
        f[o] = FP[0] + FR[0] * sx + FR[1] * sy + FR[2] * sz;
        f[o + 1] = FP[1] + FR[3] * sx + FR[4] * sy + FR[5] * sz;
        f[o + 2] = FP[2] + FR[6] * sx + FR[7] * sy + FR[8] * sz;
        tourner(f, o + 3, ux, uy, uz);
        f[o + 6] = l + e0 + e1;
        tourner(f, o + 7, nx, ny, nz);
        tourner(f, o + 10, wx, wy, wz);
        f[o + 13] = hd;
        f[o + 14] = hw;
        f[o + 15] = phase;
        f[o + 16] = type;
        g.n++;
    }

    private static void tourner(float[] f, int o, float x, float y, float z) {
        f[o] = FR[0] * x + FR[1] * y + FR[2] * z;
        f[o + 1] = FR[3] * x + FR[4] * y + FR[5] * z;
        f[o + 2] = FR[6] * x + FR[7] * y + FR[8] * z;
    }

    /* ================================================================ dessin */

    /** Brins d'un avatar pour une image (rempli par la couche, lu au dessin des nœuds, aucune capture). */
    private static final class Geometrie implements SubmitNodeCollector.CustomGeometryRenderer {
        final float[] f = new float[MAX_BRINS * PAS_BRIN];
        int n, light;

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            for (int b = 0; b < n; b++) {
                int o = b * PAS_BRIN;
                float ax = f[o], ay = f[o + 1], az = f[o + 2];
                float ux = f[o + 3], uy = f[o + 4], uz = f[o + 5], l = f[o + 6];
                float nx = f[o + 7], ny = f[o + 8], nz = f[o + 9];
                float wx = f[o + 10], wy = f[o + 11], wz = f[o + 12];
                float hd = f[o + 13], hw = f[o + 14], ph = f[o + 15];
                boolean lobe = f[o + 16] == 1f;
                float bx = ax + ux * l, by = ay + uy * l, bz = az + uz * l;
                float u0 = ph / TW, u1 = (ph + Math.min(l * K, 96f)) / TW;
                // face extérieure (+n) : bande de corde, ou face d'un lobe de noeud
                float fu0 = lobe ? 112f / TW : u0, fu1 = lobe ? 121f / TW : u1, fv1 = (lobe ? 8f : 6f) / TH;
                quad(vc, pose, ax, ay, az, bx, by, bz, wx * hw + nx * hd, wy * hw + ny * hd, wz * hw + nz * hd,
                    -wx * hw + nx * hd, -wy * hw + ny * hd, -wz * hw + nz * hd, fu0, fu1, 0f, fv1, nx, ny, nz);
                // dessus (+w) : clair ; bord extérieur en haut de la bande du dessus
                quad(vc, pose, ax, ay, az, bx, by, bz, wx * hw + nx * hd, wy * hw + ny * hd, wz * hw + nz * hd,
                    wx * hw - nx * hd, wy * hw - ny * hd, wz * hw - nz * hd, u0, u1, 8f / TH, 12f / TH, wx, wy, wz);
                // dessous (−w) et face intérieure (−n) : ombre
                quad(vc, pose, ax, ay, az, bx, by, bz, -wx * hw + nx * hd, -wy * hw + ny * hd, -wz * hw + nz * hd,
                    -wx * hw - nx * hd, -wy * hw - ny * hd, -wz * hw - nz * hd, u0, u1, 16f / TH, 20f / TH, -wx, -wy, -wz);
                quad(vc, pose, ax, ay, az, bx, by, bz, wx * hw - nx * hd, wy * hw - ny * hd, wz * hw - nz * hd,
                    -wx * hw - nx * hd, -wy * hw - ny * hd, -wz * hw - nz * hd, u0, u1, 16f / TH, 20f / TH, -nx, -ny, -nz);
                // bouts coupés
                bout(vc, pose, ax, ay, az, wx * hw, wy * hw, wz * hw, nx * hd, ny * hd, nz * hd, -ux, -uy, -uz);
                bout(vc, pose, bx, by, bz, wx * hw, wy * hw, wz * hw, nx * hd, ny * hd, nz * hd, ux, uy, uz);
            }
        }

        /** Face allongée de A à B, décalée de {@code h} (bord du haut de la texture) puis de {@code k} (bord du bas). */
        private void quad(VertexConsumer vc, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz,
                          float hx, float hy, float hz, float kx, float ky, float kz,
                          float u0, float u1, float v0, float v1, float nx, float ny, float nz) {
            sommet(vc, pose, ax + hx, ay + hy, az + hz, u0, v0, nx, ny, nz);
            sommet(vc, pose, bx + hx, by + hy, bz + hz, u1, v0, nx, ny, nz);
            sommet(vc, pose, bx + kx, by + ky, bz + kz, u1, v1, nx, ny, nz);
            sommet(vc, pose, ax + kx, ay + ky, az + kz, u0, v1, nx, ny, nz);
        }

        private void bout(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz,
                          float wx, float wy, float wz, float nx, float ny, float nz, float ox, float oy, float oz) {
            float u0 = 112f / TW, u1 = 118f / TW, v0 = 24f / TH, v1 = 28f / TH;
            sommet(vc, pose, cx + wx - nx, cy + wy - ny, cz + wz - nz, u0, v0, ox, oy, oz);
            sommet(vc, pose, cx + wx + nx, cy + wy + ny, cz + wz + nz, u1, v0, ox, oy, oz);
            sommet(vc, pose, cx - wx + nx, cy - wy + ny, cz - wz + nz, u1, v1, ox, oy, oz);
            sommet(vc, pose, cx - wx - nx, cy - wy - ny, cz - wz - nz, u0, v1, ox, oy, oz);
        }

        private void sommet(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                            float nx, float ny, float nz) {
            vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
