package fr.reborn.hud.eau;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;

/**
 * Rendu des plis de la marche sur l'eau ({@link Ondes}) : <b>l'eau du jeu n'est pas touchée</b> ; autour de chaque
 * pas, on redessine par-dessus un petit patch de <b>la même texture d'eau</b> dont le motif est déplacé le long d'un
 * contour irrégulier — la surface semble se plier, comme une réfraction.
 *
 * <h2>Technique (15/10, v8)</h2>
 * Dessiné <b>après l'eau translucide</b> ({@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN}) avec le pipeline
 * translucide des blocs du jeu ({@code RenderTypes.translucentMovingBlock} : sprite d'eau animé de l'atlas, lumière du
 * bloc, brouillard — les mêmes que l'eau), teinte de l'eau du biome. Le patch est une grille de cellules de 1/16 de
 * bloc alignées sur les texels de l'eau (chaque cellule reste dans un bloc : UV continus). À chaque sommet, un champ
 * de distorsion : bande gaussienne autour d'un contour r(θ) irrégulier (4 harmoniques propres au pas, qui frémissent
 * lentement) ; dans la bande, le point où l'on lit la texture est décalé radialement (dérivée de gaussienne : le motif
 * est tiré vers la crête des deux côtés, d'où le pli) ; l'opacité du patch suit la bande (nulle ailleurs) ; un liseré
 * clair très faible marque la crête. Tout s'atténue jusqu'à zéro en fin de vie.
 * Animation au temps réel de l'image ({@link Ondes#temps()}). Grilles et tampons préalloués et réutilisés, aucune
 * allocation par image hormis les en-têtes {@code BufferBuilder}/{@code MeshData} de l'API ; rien quand il n'y a pas
 * de pli, ni caméra sous l'eau.
 */
final class EauRendu {

    /** Cellules de la grille par bloc (texels de l'eau). */
    private static final int PAR_BLOC = 16;
    /** Taille maximale de la grille d'un pli (cellules par côté). */
    private static final int GRILLE = 64;
    /** Hauteur du patch au-dessus de la surface de l'eau (blocs). */
    private static final double DECALAGE = 0.004;
    /** Résolution angulaire du contour (rayon précalculé par pli et par image). */
    private static final int ANGLES = 128;

    private static final int G1 = GRILLE + 1;
    private static final float[] SX = new float[G1 * G1], SZ = new float[G1 * G1], AL = new float[G1 * G1],
            BL = new float[G1 * G1];
    private static final double[] RTHETA = new double[ANGLES + 1];

    private static ByteBufferBuilder octets;
    private static final GpuBuffer[] TAMPONS = new GpuBuffer[3];
    private static int image;
    private static boolean enPanne;

    static long mesures, cumul, pire, quads;

    private EauRendu() {}

    static void apresEauTranslucide(LevelRenderContext ctx) {
        if (enPanne || Ondes.vide()) return;
        long t0 = MarcheEauClient.debug ? System.nanoTime() : 0L;
        try {
            dessiner();
        } catch (RuntimeException e) {
            enPanne = true;
            org.slf4j.LoggerFactory.getLogger("reborn-hud").warn("Marche sur l'eau : rendu coupé ({})", e.toString());
        }
        if (MarcheEauClient.debug) {                                   // banc : coût mesuré du rendu (CPU)
            long d = System.nanoTime() - t0;
            mesures++;
            cumul += d;
            pire = Math.max(pire, d);
        }
    }

    private static void dessiner() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam.getFluidInCamera() != FogType.NONE && cam.getFluidInCamera() != FogType.ATMOSPHERIC) return;
        double now = Ondes.temps();                                     // temps réel de l'image
        Vec3 c = cam.position();
        RenderType rt = RenderTypes.translucentMovingBlock();
        TextureAtlasSprite sp = mc.getModelManager().getFluidStateModelSet().get(Fluids.WATER.defaultFluidState())
                .stillMaterial().sprite();
        if (octets == null) octets = new ByteBufferBuilder(128 * 1024);
        BufferBuilder bb = null;
        int n = 0;
        for (int i = 0; i < Ondes.CAPACITE; i++) {
            if (!Ondes.ACTIVE[i]) continue;
            double age = now - Ondes.NAISSANCE[i], u = age / Ondes.VIE[i];
            if (age < 0 || u >= 1) continue;
            double ox = Ondes.FX[i] - c.x, oz = Ondes.FZ[i] - c.z;
            if (ox * ox + oz * oz > Ondes.PORTEE * Ondes.PORTEE) continue;
            double fe = Math.min(1, age / Ondes.FONDU_ENTREE);
            double env = Ondes.FORCE[i] * fe * fe * (3 - 2 * fe) * Math.pow(1 - u, 1.5);
            if (env < 0.01) continue;
            if (bb == null) bb = new BufferBuilder(octets, rt.primitiveTopology(), rt.format());
            n += pli(bb, i, age, u, env, c, sp);
        }
        quads += n;
        if (bb == null) return;
        MeshData m = bb.build();
        if (m == null) return;
        try (m) { tirer(rt, m); }
    }

    /** Un pli : champ de distorsion sur la grille, puis les cellules non vides en quads texturés. */
    private static int pli(BufferBuilder bb, int i, double age, double u, double env, Vec3 c, TextureAtlasSprite sp) {
        double e = 1 - (1 - u) * (1 - u);
        double r0 = Ondes.RAYON[i] * (Ondes.ECHELLE_DEBUT + (Ondes.ECHELLE_FIN - Ondes.ECHELLE_DEBUT) * e);
        double w = Ondes.BANDE, fv = Ondes.FREMISSEMENT_VITESSE;
        // contour r(θ) : 4 harmoniques propres au pli, qui glissent lentement (frémissement), + un léger tremblé ample
        double rmax = 0, rmin = 1e9;
        for (int k = 0; k <= ANGLES; k++) {
            double t = 2 * Math.PI * k / ANGLES, s = 1;
            for (int h = 0; h < 4; h++) {
                double sens = (h & 1) == 0 ? 1 : -1;
                s += Ondes.AMP[i * 4 + h] * Math.sin((h + 2) * t + Ondes.PHASE[i * 4 + h] + sens * age * fv * (0.35 + 0.15 * h));
            }
            s += Ondes.FREMISSEMENT * (1 - u) * Math.sin(3 * t + Ondes.PHASE[i * 4] * 0.7 + age * fv);
            double r = r0 * s;
            RTHETA[k] = r;
            rmax = Math.max(rmax, r);
            rmin = Math.min(rmin, r);
        }
        double portee = rmax + 2.6 * w, interieur = Math.max(0, rmin - 2.6 * w);
        // grille alignée sur les texels de l'eau
        double fx = Ondes.FX[i], fz = Ondes.FZ[i];
        int gx0 = (int) Math.floor((fx - portee) * PAR_BLOC), gz0 = (int) Math.floor((fz - portee) * PAR_BLOC);
        int nx = Math.min(GRILLE, (int) Math.ceil((fx + portee) * PAR_BLOC) - gx0);
        int nz = Math.min(GRILLE, (int) Math.ceil((fz + portee) * PAR_BLOC) - gz0);
        double depl = Ondes.DEPLACEMENT / 0.4289;                       // max de s·e^(−s²) ramené à 1
        for (int a = 0; a <= nx; a++) {
            for (int b = 0; b <= nz; b++) {
                int q = a * G1 + b;
                double x = (gx0 + a) / (double) PAR_BLOC, z = (gz0 + b) / (double) PAR_BLOC;
                double dx = x - fx, dz = z - fz, r = Math.sqrt(dx * dx + dz * dz);
                SX[q] = (float) x;
                SZ[q] = (float) z;
                if (r > portee || r < interieur || r < 1e-4) { AL[q] = 0; BL[q] = 0; continue; }
                double t = Math.atan2(dz, dx);
                double kf = (t < 0 ? t + 2 * Math.PI : t) / (2 * Math.PI) * ANGLES;
                int k = (int) kf;
                double rt = RTHETA[k] + (RTHETA[Math.min(ANGLES, k + 1)] - RTHETA[k]) * (kf - k);
                double s = (r - rt) / w;
                double g = Math.exp(-s * s);
                // on lit la texture un peu plus loin du centre à l'intérieur, plus près à l'extérieur : le motif se
                // resserre sur la crête (le pli)
                double d = depl * env * s * g;
                SX[q] = (float) (x + dx / r * d);
                SZ[q] = (float) (z + dz / r * d);
                double bande = Math.sqrt(g);                                  // e^(−s²/2) : la bande, plus large
                double g2 = g * g, g4 = g2 * g2, g8 = g4 * g4;                // e^(−8s²) : la crête, fine
                AL[q] = (float) (Ondes.OPACITE * env * bande);
                BL[q] = (float) (Ondes.LISERE * env * g8);
            }
        }
        int teinte = Ondes.TEINTE[i], lum = Ondes.LUMIERE[i];
        int tr = (teinte >> 16) & 255, tg = (teinte >> 8) & 255, tb = teinte & 255;
        float y = (float) (Ondes.FY[i] + DECALAGE - c.y);
        float u0 = sp.getU0(), du = sp.getU1() - u0, v0 = sp.getV0(), dv = sp.getV1() - v0;
        int quadsPli = 0;
        for (int a = 0; a < nx; a++) {
            for (int b = 0; b < nz; b++) {
                int q00 = a * G1 + b, q10 = (a + 1) * G1 + b, q01 = a * G1 + b + 1, q11 = (a + 1) * G1 + b + 1;
                if (AL[q00] < 0.004f && AL[q10] < 0.004f && AL[q01] < 0.004f && AL[q11] < 0.004f) continue;
                // le bloc de la cellule : UV de la texture d'eau relatifs à ce bloc (comme la face d'eau du jeu)
                int bx = Math.floorDiv(gx0 + a, PAR_BLOC), bz = Math.floorDiv(gz0 + b, PAR_BLOC);
                float x0 = (float) ((gx0 + a) / (double) PAR_BLOC - c.x), x1 = (float) ((gx0 + a + 1) / (double) PAR_BLOC - c.x);
                float z0 = (float) ((gz0 + b) / (double) PAR_BLOC - c.z), z1 = (float) ((gz0 + b + 1) / (double) PAR_BLOC - c.z);
                // (x0,z0) → (x0,z1) → (x1,z1) → (x1,z0) : face tournée vers le ciel
                sommet(bb, x0, y, z0, q00, bx, bz, tr, tg, tb, lum, u0, du, v0, dv);
                sommet(bb, x0, y, z1, q01, bx, bz, tr, tg, tb, lum, u0, du, v0, dv);
                sommet(bb, x1, y, z1, q11, bx, bz, tr, tg, tb, lum, u0, du, v0, dv);
                sommet(bb, x1, y, z0, q10, bx, bz, tr, tg, tb, lum, u0, du, v0, dv);
                quadsPli++;
            }
        }
        return quadsPli;
    }

    private static void sommet(BufferBuilder bb, float x, float y, float z, int q, int bx, int bz, int tr, int tg,
                               int tb, int lum, float u0, float du, float v0, float dv) {
        float lx = Math.max(0f, Math.min(1f, SX[q] - bx)), lz = Math.max(0f, Math.min(1f, SZ[q] - bz));
        float bl = BL[q];
        int r = (int) (tr + (255 - tr) * bl), g = (int) (tg + (255 - tg) * bl), b = (int) (tb + (255 - tb) * bl);
        int al = Math.max(0, Math.min(255, (int) (AL[q] * 255)));
        bb.addVertex(x, y, z)
                .setColor(al << 24 | r << 16 | g << 8 | b)
                .setUv(u0 + du * lx, v0 + dv * lz)
                .setLight(lum)
                .setNormal(0f, 1f, 0f);
    }

    /** Envoie le maillage dans un tampon GPU réutilisé (rotation sur 3 images) et le dessine tout de suite. */
    private static void tirer(RenderType rt, MeshData m) {
        GpuDevice dev = RenderSystem.getDevice();
        ByteBuffer data = m.vertexBuffer();
        int taille = data.remaining();
        if (taille <= 0) return;
        image = (image + 1) % 3;
        GpuBuffer tampon = TAMPONS[image];
        if (tampon == null || tampon.isClosed() || tampon.size() < taille) {
            if (tampon != null && !tampon.isClosed()) tampon.close();
            long cap = Math.max(64 * 1024, Long.highestOneBit(taille) << 1);
            TAMPONS[image] = tampon = dev.createBuffer(() -> "reborn eau plis", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, cap);
        }
        dev.createCommandEncoder().writeToBuffer(tampon.slice(0, taille), data);
        var seq = RenderSystem.getSequentialBuffer(rt.primitiveTopology());
        int n = m.drawState().indexCount();
        rt.prepare().drawFromBuffer(tampon, seq.getBuffer(n), seq.type(), 0, 0, n);
    }
}
