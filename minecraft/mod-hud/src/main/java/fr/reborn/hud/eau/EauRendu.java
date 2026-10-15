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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;

/**
 * Rendu des flaques de la marche sur l'eau ({@link Ondes}) : <b>l'eau du jeu n'est pas touchée</b>, on pose seulement,
 * à plat sur la surface, le contour de chaque flaque.
 *
 * <h2>Technique (15/10, v5–v6)</h2>
 * Un décalque très léger dessiné <b>après l'eau translucide</b> ({@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN},
 * par-dessus l'eau de Sodium comme de celle d'un pack Iris) avec le pipeline de l'<b>ombre des entités</b> du jeu
 * ({@code RenderTypes.entityShadow} : translucide, sans écriture de profondeur, légèrement décalé vers la caméra,
 * brouillard ; aucune ressource de shader). Chaque flaque est un maillage de {@link #SEGMENTS} × 3 quads : un
 * remplissage très faible, puis le trait en deux bandes dont l'opacité monte du bord intérieur au contour et redescend
 * à 0 au bord extérieur (dégradé des sommets : trait fin et doux, lisse à toute distance). Texture : un texel blanc
 * ({@code textures/environment/flaque.png}). Couleur = eau du biome mêlée de blanc, assombrie comme l'eau selon la
 * lumière (le pipeline n'éclaire pas). Tampons préalloués et réutilisés (CPU : {@link ByteBufferBuilder} ; GPU :
 * 3 tampons en rotation), aucune allocation par image hormis les en-têtes {@code BufferBuilder}/{@code MeshData} de
 * l'API ; rien quand il n'y a pas de flaque, ni caméra sous l'eau.
 */
final class EauRendu {

    /** Hauteur du décalque au-dessus de la surface de l'eau (blocs). */
    private static final double DECALAGE = 0.006;
    /** Segments du contour d'une flaque. */
    static final int SEGMENTS = 48;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/environment/flaque.png");
    private static final float[] COS = new float[SEGMENTS + 1], SIN = new float[SEGMENTS + 1];
    static {
        for (int k = 0; k <= SEGMENTS; k++) {
            double t = 2 * Math.PI * k / SEGMENTS;
            COS[k] = (float) Math.cos(t);
            SIN[k] = (float) Math.sin(t);
        }
    }
    /** Contour courant (intérieur, milieu du trait, extérieur), relatif à la caméra : tableaux de travail réutilisés. */
    private static final float[] IX = new float[SEGMENTS + 1], IZ = new float[SEGMENTS + 1], CX = new float[SEGMENTS + 1],
            CZ = new float[SEGMENTS + 1], OX = new float[SEGMENTS + 1], OZ = new float[SEGMENTS + 1];
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();

    private static ByteBufferBuilder octets;
    private static final GpuBuffer[] TAMPONS = new GpuBuffer[3];
    private static int image;
    private static RenderType type;
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
        ClientLevel level = mc.level;
        if (level == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam.getFluidInCamera() != FogType.NONE && cam.getFluidInCamera() != FogType.ATMOSPHERIC) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = Ondes.temps(mc, pt);
        Vec3 c = cam.position();
        if (type == null) type = RenderTypes.entityShadow(TEXTURE);
        if (octets == null) octets = new ByteBufferBuilder(64 * 1024);
        int assombri = level.getSkyDarken();
        BufferBuilder bb = null;
        int n = 0;
        for (int i = 0; i < Ondes.CAPACITE; i++) {
            if (!Ondes.ACTIVE[i]) continue;
            double age = now - Ondes.NAISSANCE[i], u = age / Ondes.VIE[i];
            if (age < 0 || u >= 1) continue;
            double dx = Ondes.FX[i] - c.x, dz = Ondes.FZ[i] - c.z;
            if (dx * dx + dz * dz > Ondes.PORTEE * Ondes.PORTEE) continue;
            // apparition en 60 ms, puis décroissance jusqu'à 0 (plus marquée en fin de vie)
            double f = Ondes.FORCE[i] * Math.min(1, age / 0.06) * Math.pow(1 - u, 1.4);
            if (f * Ondes.OPACITE < 0.01) continue;
            if (bb == null) bb = new BufferBuilder(octets, type.primitiveTopology(), type.format());
            // expansion : rapide à l'impact puis freinée (ease-out cubique sur la première partie de la vie)
            double ue = Math.min(1, u / Ondes.EXPANSION), e = 1 - (1 - ue) * (1 - ue) * (1 - ue);
            e = 0.9 * e + 0.1 * u;                                          // elle continue à peine de grandir
            double sc = Ondes.ECHELLE_DEBUT + (Ondes.ECHELLE_FIN - Ondes.ECHELLE_DEBUT) * e;
            double a = Ondes.DEMI_A[i] * sc, b = Ondes.DEMI_B[i] * sc, fl = Ondes.FLOU * (0.7 + 0.3 * e);
            double ca = Math.cos(Ondes.ANGLE[i]), sa = Math.sin(Ondes.ANGLE[i]);
            // le contour ondoie pendant l'expansion (les harmoniques glissent), puis se calme
            double p1 = Ondes.PHASE1[i] + age * 1.6, p2 = Ondes.PHASE2[i] - age * 2.1, p3 = age * 9.0;
            double ond = Ondes.ONDOIEMENT * (1 - u);
            for (int k = 0; k <= SEGMENTS; k++) {
                int kk = k == SEGMENTS ? 0 : k;                             // contour fermé exactement
                double t = 2 * Math.PI * kk / SEGMENTS;
                double w = 1 + Ondes.CABOSSE * (0.65 * Math.sin(2 * t + p1) + 0.35 * Math.sin(3 * t + p2))
                        + ond * Math.sin(5 * t + p3);
                double lx = a * COS[kk] * w, lz = b * SIN[kk] * w;
                double nx = COS[kk] / a, nz = SIN[kk] / b, nn = Math.sqrt(nx * nx + nz * nz);  // normale de l'ellipse
                nx /= nn; nz /= nn;
                IX[k] = (float) (dx + (lx - nx * fl) * ca - (lz - nz * fl) * sa);
                IZ[k] = (float) (dz + (lx - nx * fl) * sa + (lz - nz * fl) * ca);
                CX[k] = (float) (dx + lx * ca - lz * sa);
                CZ[k] = (float) (dz + lx * sa + lz * ca);
                OX[k] = (float) (dx + (lx + nx * fl) * ca - (lz + nz * fl) * sa);
                OZ[k] = (float) (dz + (lx + nx * fl) * sa + (lz + nz * fl) * ca);
            }
            int rgb = couleur(level, i, assombri);
            int dedans = alpha(Ondes.INTERIEUR * f) | rgb, trait = alpha(Ondes.OPACITE * f) | rgb, dehors = rgb;
            float y = (float) (Ondes.FY[i] + DECALAGE - c.y), cx = (float) dx, cz = (float) dz;
            for (int k = 0; k < SEGMENTS; k++) {
                // faces tournées vers le ciel : centre → bord intérieur, puis intérieur → trait → extérieur
                sommet(bb, cx, y, cz, dedans);
                sommet(bb, cx, y, cz, dedans);
                sommet(bb, IX[k + 1], y, IZ[k + 1], dedans);
                sommet(bb, IX[k], y, IZ[k], dedans);
                sommet(bb, IX[k], y, IZ[k], dedans);
                sommet(bb, IX[k + 1], y, IZ[k + 1], dedans);
                sommet(bb, CX[k + 1], y, CZ[k + 1], trait);
                sommet(bb, CX[k], y, CZ[k], trait);
                sommet(bb, CX[k], y, CZ[k], trait);
                sommet(bb, CX[k + 1], y, CZ[k + 1], trait);
                sommet(bb, OX[k + 1], y, OZ[k + 1], dehors);
                sommet(bb, OX[k], y, OZ[k], dehors);
                n += 3;
            }
        }
        quads += n;
        if (bb == null) return;
        MeshData m = bb.build();
        if (m == null) return;
        try (m) { tirer(m); }
    }

    /**
     * Bleu-blanc pâle : l'eau du biome mêlée de blanc ({@link Ondes#BLANC}), assombrie comme l'eau selon la lumière du
     * lieu (ciel selon l'heure, blocs) — sombre la nuit, jamais phosphorescente. Sans alpha.
     */
    private static int couleur(ClientLevel level, int i, int assombri) {
        P.set(Ondes.FX[i], Ondes.FY[i] + 0.2, Ondes.FZ[i]);
        int lum = LightCoordsUtil.getLightCoords(level, P);
        int l = Math.max(LightCoordsUtil.block(lum), LightCoordsUtil.sky(lum) - assombri);
        double k = 0.10 + 0.90 * Math.pow(Math.max(0, l) / 15.0, 1.4);
        int t = Ondes.TEINTE[i];
        double m = Ondes.BLANC;
        int r = clamp((int) ((((t >> 16) & 255) * (1 - m) + 255 * m) * k));
        int g = clamp((int) ((((t >> 8) & 255) * (1 - m) + 255 * m) * k));
        int bl = clamp((int) (((t & 255) * (1 - m) + 255 * m) * k));
        return r << 16 | g << 8 | bl;
    }

    private static int alpha(double a) { return clamp((int) (a * 255)) << 24; }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static void sommet(BufferBuilder bb, float x, float y, float z, int coul) {
        bb.addVertex(x, y, z)
                .setColor(coul)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0f, 1f, 0f);
    }

    /** Envoie le maillage dans un tampon GPU réutilisé (rotation sur 3 images) et le dessine tout de suite. */
    private static void tirer(MeshData m) {
        GpuDevice dev = RenderSystem.getDevice();
        ByteBuffer data = m.vertexBuffer();
        int taille = data.remaining();
        if (taille <= 0) return;
        image = (image + 1) % 3;
        GpuBuffer tampon = TAMPONS[image];
        if (tampon == null || tampon.isClosed() || tampon.size() < taille) {
            if (tampon != null && !tampon.isClosed()) tampon.close();
            long cap = Math.max(64 * 1024, Long.highestOneBit(taille) << 1);
            TAMPONS[image] = tampon = dev.createBuffer(() -> "reborn eau flaques", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, cap);
        }
        dev.createCommandEncoder().writeToBuffer(tampon.slice(0, taille), data);
        var seq = RenderSystem.getSequentialBuffer(type.primitiveTopology());
        int n = m.drawState().indexCount();
        type.prepare().drawFromBuffer(tampon, seq.getBuffer(n), seq.type(), 0, 0, n);
    }
}
