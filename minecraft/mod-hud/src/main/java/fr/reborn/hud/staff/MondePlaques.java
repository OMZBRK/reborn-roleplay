package fr.reborn.hud.staff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Repères dans le monde de l'outil « Plaques » du Poste de garde (lot 4, étape B ; planches c3 et c5) : filets fins
 * dessinés <b>dans le monde</b> (une passe {@code RenderTypes.lines()}, un seul lot), seulement tant que l'outil est
 * ouvert, pour le staff seul — rien n'est envoyé aux joueurs.
 *
 * <ul>
 *   <li>{@link #COINS} : quatre coins fins (plaques proches, leur id en étiquette) ;</li>
 *   <li>{@link #FIL} : fil de fer crème de ce que la souris survole dans le sélecteur (façon Axiom) ;</li>
 *   <li>{@link #OR_PLEIN} : contour d'or plus épais de ce qui est choisi (bloc, boîte, entité, plaque ouverte) ;</li>
 *   <li>{@link #HORS} : gris, survol hors de portée (8 blocs : le serveur refuserait l'ancre) ;</li>
 *   <li>{@link #APERCU} : or à 55 %, 1 px, la boîte jusqu'au bloc survolé tant que Maj est enfoncée (planche c5-b) ;</li>
 *   <li>{@link #voile} : voile plein, crème à 14 % sur la face visée ({@link #VOILE_FACE}), or à 9 % sur ce qui est
 *       choisi ({@link #VOILE_CHOIX}) — une seconde passe {@code RenderTypes.debugQuads()}.</li>
 * </ul>
 * L'écran remplit la liste à chaque image ({@link #debut}, {@link #boite}) ; elle est copiée au moment de la collecte
 * (le dessin différé ne voit jamais une liste à moitié remplie) et oubliée 250 ms après la dernière image du volet.
 * Aucune allocation par image (tableaux fixes, 48 boîtes au plus).
 */
public final class MondePlaques {

    public static final int COINS = 0, FIL = 1, OR_PLEIN = 2, HORS = 3, APERCU = 4;
    /** Voiles : crème à 14 % (face visée), or à 9 % (choisi). */
    public static final int VOILE_FACE = 0x24EEE4D2, VOILE_CHOIX = 0x17E8C27A;
    private static final int MAX = 48, MAX_VOILES = 4;
    private static final int CREME = 0xF0EEE4D2, OR_DOUX = 0xE6C8A05A, OR = 0xFFE8C27A, GRIS = 0xB0807470, OR_55 = 0x8CE8C27A;

    /* liste remplie par l'écran */
    private static final double[][] B = new double[MAX][6];
    private static final int[] S = new int[MAX];
    private static int n;
    private static long vuA = Long.MIN_VALUE / 4;
    /* copie de la collecte */
    private static final double[][] BR = new double[MAX][6];
    private static final int[] SR = new int[MAX];
    private static int nr;
    private static double camX, camY, camZ;
    /* voiles : boîte, face (index de Direction, -1 = les six), couleur ; liste de l'écran puis copie de la collecte */
    private static final double[][] VB = new double[MAX_VOILES][6], VBR = new double[MAX_VOILES][6];
    private static final int[] VF = new int[MAX_VOILES], VC = new int[MAX_VOILES], VFR = new int[MAX_VOILES], VCR = new int[MAX_VOILES];
    private static int nv, nvr;

    private MondePlaques() {}

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(MondePlaques::collecter);
    }

    /** Nouvelle image du volet : la liste repart vide. */
    static void debut() {
        n = 0;
        nv = 0;
        vuA = Util.getMillis();
    }

    /** Voile plein sur la face {@code face} de la boîte {@code b} (null : les six faces), couleur ARGB {@code c}. */
    static void voile(double[] b, net.minecraft.core.Direction face, int c) {
        if (nv >= MAX_VOILES || b == null) return;
        System.arraycopy(b, 0, VB[nv], 0, 6);
        VF[nv] = face == null ? -1 : face.ordinal();
        VC[nv++] = c;
    }

    /** Une boîte (minX, minY, minZ, maxX, maxY, maxZ) dans le style {@code style}. */
    static void boite(double[] b, int style) {
        if (n >= MAX || b == null) return;
        System.arraycopy(b, 0, B[n], 0, 6);
        S[n++] = style;
    }

    static void boite(double x0, double y0, double z0, double x1, double y1, double z1, int style) {
        if (n >= MAX) return;
        double[] d = B[n];
        d[0] = x0; d[1] = y0; d[2] = z0; d[3] = x1; d[4] = y1; d[5] = z1;
        S[n++] = style;
    }

    /** Banc : boîtes posées à la dernière image du volet. */
    static int nombre() { return n; }

    private static void collecter(LevelRenderContext ctx) {
        if (n == 0 && nv == 0 || Util.getMillis() - vuA > 250) return;
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        if (cam == null) return;
        nr = n;
        for (int i = 0; i < n; i++) {
            System.arraycopy(B[i], 0, BR[i], 0, 6);
            SR[i] = S[i];
        }
        nvr = nv;
        for (int i = 0; i < nv; i++) {
            System.arraycopy(VB[i], 0, VBR[i], 0, 6);
            VFR[i] = VF[i];
            VCR[i] = VC[i];
        }
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        PoseStack ps = ctx.poseStack();
        ps.pushPose();
        if (nvr > 0) ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.debugQuads(), MondePlaques::emettreVoiles);
        if (nr > 0) ctx.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.lines(), MondePlaques::emettre);
        ps.popPose();
    }

    private static void emettreVoiles(PoseStack.Pose p, VertexConsumer vc) {
        for (int i = 0; i < nvr; i++) {
            double[] b = VBR[i];
            // à peine plus grand que le bloc : pas de z-fighting contre ses faces
            double e = 0.004;
            float x0 = (float) (b[0] - e - camX), y0 = (float) (b[1] - e - camY), z0 = (float) (b[2] - e - camZ);
            float x1 = (float) (b[3] + e - camX), y1 = (float) (b[4] + e - camY), z1 = (float) (b[5] + e - camZ);
            int c = VCR[i], f = VFR[i];
            // ordre de Direction : DOWN, UP, NORTH, SOUTH, WEST, EAST
            if (f < 0 || f == 0) quad(p, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c);
            if (f < 0 || f == 1) quad(p, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, c);
            if (f < 0 || f == 2) quad(p, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, c);
            if (f < 0 || f == 3) quad(p, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c);
            if (f < 0 || f == 4) quad(p, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c);
            if (f < 0 || f == 5) quad(p, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, c);
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int c) {
        vc.addVertex(p, ax, ay, az).setColor(c);
        vc.addVertex(p, bx, by, bz).setColor(c);
        vc.addVertex(p, cx, cy, cz).setColor(c);
        vc.addVertex(p, dx, dy, dz).setColor(c);
    }

    private static void emettre(PoseStack.Pose pose, VertexConsumer vc) {
        for (int i = 0; i < nr; i++) {
            double[] b = BR[i];
            int st = SR[i];
            // un rien plus grand que la boîte : pas de scintillement contre les faces du bloc
            double e = st == OR_PLEIN ? 0.012 : 0.006;
            float x0 = (float) (b[0] - e - camX), y0 = (float) (b[1] - e - camY), z0 = (float) (b[2] - e - camZ);
            float x1 = (float) (b[3] + e - camX), y1 = (float) (b[4] + e - camY), z1 = (float) (b[5] + e - camZ);
            switch (st) {
                case COINS -> coins(pose, vc, x0, y0, z0, x1, y1, z1, OR_DOUX, 2.5f);
                case FIL -> aretes(pose, vc, x0, y0, z0, x1, y1, z1, CREME, 2.0f);
                case HORS -> aretes(pose, vc, x0, y0, z0, x1, y1, z1, GRIS, 1.5f);
                case APERCU -> aretes(pose, vc, x0, y0, z0, x1, y1, z1, OR_55, 1.0f);
                default -> {
                    aretes(pose, vc, x0, y0, z0, x1, y1, z1, OR, 3.0f);
                    coins(pose, vc, x0, y0, z0, x1, y1, z1, OR, 4.0f);
                }
            }
        }
    }

    /** Les 12 arêtes. */
    private static void aretes(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                               int c, float w) {
        ligne(p, vc, x0, y0, z0, x1, y0, z0, c, w);
        ligne(p, vc, x0, y0, z1, x1, y0, z1, c, w);
        ligne(p, vc, x0, y1, z0, x1, y1, z0, c, w);
        ligne(p, vc, x0, y1, z1, x1, y1, z1, c, w);
        ligne(p, vc, x0, y0, z0, x0, y1, z0, c, w);
        ligne(p, vc, x1, y0, z0, x1, y1, z0, c, w);
        ligne(p, vc, x0, y0, z1, x0, y1, z1, c, w);
        ligne(p, vc, x1, y0, z1, x1, y1, z1, c, w);
        ligne(p, vc, x0, y0, z0, x0, y0, z1, c, w);
        ligne(p, vc, x1, y0, z0, x1, y0, z1, c, w);
        ligne(p, vc, x0, y1, z0, x0, y1, z1, c, w);
        ligne(p, vc, x1, y1, z0, x1, y1, z1, c, w);
    }

    /** Quatre coins hauts et bas : trois petits segments par sommet (un quart de bloc au plus). */
    private static void coins(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                              int c, float w) {
        float lx = Math.min(0.25f, (x1 - x0) / 3f), ly = Math.min(0.25f, (y1 - y0) / 3f), lz = Math.min(0.25f, (z1 - z0) / 3f);
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1, y = (i & 2) == 0 ? y0 : y1, z = (i & 4) == 0 ? z0 : z1;
            float sx = (i & 1) == 0 ? lx : -lx, sy = (i & 2) == 0 ? ly : -ly, sz = (i & 4) == 0 ? lz : -lz;
            ligne(p, vc, x, y, z, x + sx, y, z, c, w);
            ligne(p, vc, x, y, z, x, y + sy, z, c, w);
            ligne(p, vc, x, y, z, x, y, z + sz, c, w);
        }
    }

    private static void ligne(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                              int c, float w) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f) return;
        dx /= l;
        dy /= l;
        dz /= l;
        vc.addVertex(p, ax, ay, az).setNormal(p, dx, dy, dz).setColor(c).setLineWidth(w);
        vc.addVertex(p, bx, by, bz).setNormal(p, dx, dy, dz).setColor(c).setLineWidth(w);
    }
}
