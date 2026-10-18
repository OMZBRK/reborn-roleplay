package fr.reborn.hud.meditation;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Rendu du <b>halo de méditation</b> ({@link MeditationClient}) — l'effet Photon « meditation1 » refait sans Photon.
 *
 * <h2>Ce que fait l'effet d'origine (décodé du .fx, 18/10)</h2>
 * Un émetteur de particules : 0,5 particule par tick, vie 30 à 40 ticks, taille 0,05 bloc (×1 jusqu'à 10 % de la vie
 * puis ×0,61 à 90 %), vitesse 1 vers le haut dans un cône de 10° de rayon 0,5 (aplati ×0,6), vitesse radiale
 * 0,3 à 1 (elles s'écartent en montant), couleur {@code #6F9FF8} avec traînée blanche, plein éclairage ; et chaque
 * particule <b>émet de la lumière</b> (portée 1, intensité 1, couleur de la particule). Le « disque vert » de la vidéo
 * est cette lumière qui éclaircit l'herbe sous le joueur, assombrie au centre par l'ombre du joueur.
 *
 * <h2>Technique (même place que les plis de l'eau, {@code eau/EauRendu})</h2>
 * Dessiné après le terrain translucide ({@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN}), sans écrire la
 * profondeur, en deux passes sur la même géométrie :
 * <ol>
 *   <li><b>lumière</b> : mélange {@code DST_COLOR, ONE} (le sol × (1 + halo)) — l'herbe devient plus vive et plus
 *       verte, la pierre plus claire, comme sous une vraie lumière ;</li>
 *   <li><b>lueur</b> : additif {@code ONE, ONE}, teinte du halo, plus forte quand le lieu est sombre (la nuit, le sol
 *       ne renvoie presque rien : c'est la lueur qui se voit) ; les petites lueurs qui montent passent ici.</li>
 * </ol>
 * Le disque épouse le sol : un quad par bloc couvert, posé sur le dessus de la forme de collision (dalle, pente en
 * escalier…), UV continus d'un bloc à l'autre ; décalage de couche de l'ombre du jeu ({@code VIEW_OFFSET_Z_LAYERING})
 * contre le z-fighting. Texture pixel art 40×40 en aplats concentriques ({@code effet/meditation.png}, 1 texel =
 * 1/16 de bloc, filtrage NEAREST). Respiration lente (3,2 s), fondu d'apparition et de disparition.
 * Les lueurs : traînées verticales de 1 × 4 texels tournées vers la caméra (au plus {@value #PARTICULES}).
 *
 * <p>Performance : tableaux et tampons préalloués et réutilisés, aucune allocation par image hormis les en-têtes
 * {@code BufferBuilder}/{@code MeshData} de l'API ; rien quand personne ne médite ; au-delà de 40 blocs, rien.
 */
final class MeditationRendu {

    private static final Identifier TEX = Identifier.fromNamespaceAndPath("reborn", "textures/effet/meditation.png");
    private static final float TW = 48f, TH = 40f;
    /** Force de la passe « lumière » (part ajoutée à la couleur du sol au centre du halo). */
    static float LUMIERE = 1.0f;
    /** Force de la lueur additive en plein jour / dans le noir. */
    static float LUEUR_JOUR = 0.15f, LUEUR_NUIT = 0.32f;
    /** Particules : taux par seconde et par méditant, intensité. */
    static float TAUX = 6f, ECLAT = 0.7f;
    private static final double SOUFFLE = 3.2;
    private static final int LUMIERE_PLEINE = 0xF000F0;

    static final int PARTICULES = 256;
    private static final float[] PX = new float[PARTICULES], PY = new float[PARTICULES], PZ = new float[PARTICULES],
            VX = new float[PARTICULES], VY = new float[PARTICULES], VZ = new float[PARTICULES],
            AGE = new float[PARTICULES], VIE = new float[PARTICULES];
    private static final int[] PROP = new int[PARTICULES];
    private static final boolean[] PVIVE = new boolean[PARTICULES];
    private static final float[] EMIS = new float[MeditationClient.CAPACITE];
    private static final Random ALEA = new Random();
    private static int vivantes;
    private static double derniere = Double.NaN;

    private static RenderType rtLumiere, rtLueur;
    private static ByteBufferBuilder octets;
    private static final GpuBuffer[] TAMPONS = new GpuBuffer[4];
    private static int image;
    private static boolean enPanne;

    static long mesures, cumul, pire, quads;

    private MeditationRendu() {}

    static void viderParticules() {
        for (int k = 0; k < PARTICULES; k++) PVIVE[k] = false;
        vivantes = 0;
        derniere = Double.NaN;
    }

    static void apresTranslucide(LevelRenderContext ctx) {
        if (enPanne || (MeditationClient.aucun() && vivantes == 0)) { derniere = Double.NaN; return; }
        long t0 = MeditationClient.debug ? System.nanoTime() : 0L;
        try {
            dessiner();
        } catch (RuntimeException | ReflectiveOperationException | LinkageError e) {
            enPanne = true;
            org.slf4j.LoggerFactory.getLogger("reborn-hud").warn("Méditation : rendu du halo coupé ({})", e.toString());
        }
        if (MeditationClient.debug && Double.isNaN(MeditationClient.horlogeFilm)) {   // hors film (captures à chaque image)
            long d = System.nanoTime() - t0;
            mesures++;
            cumul += d;
            pire = Math.max(pire, d);
        }
    }

    /** Les deux types de rendu (pipelines du mod sur le shader d'entité du jeu, émissif, sans écriture de profondeur). */
    private static void preparer() throws ReflectiveOperationException {
        if (rtLumiere != null) return;
        DepthStencilState profondeur = new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false);
        RenderPipeline lumiere = pipeline("meditation_lumiere", new BlendFunction(
                BlendFactor.DST_COLOR, BlendFactor.ONE, BlendOp.ADD, BlendFactor.ZERO, BlendFactor.ONE, BlendOp.ADD), profondeur);
        RenderPipeline lueur = pipeline("meditation_lueur", new BlendFunction(
                BlendFactor.ONE, BlendFactor.ONE, BlendOp.ADD, BlendFactor.ZERO, BlendFactor.ONE, BlendOp.ADD), profondeur);
        Method creer = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
        creer.setAccessible(true);
        rtLumiere = (RenderType) creer.invoke(null, "reborn_meditation_lumiere", reglage(lumiere));
        rtLueur = (RenderType) creer.invoke(null, "reborn_meditation_lueur", reglage(lueur));
    }

    private static RenderPipeline pipeline(String nom, BlendFunction melange, DepthStencilState profondeur) {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withLocation(Identifier.fromNamespaceAndPath("reborn", "pipeline/" + nom))
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(melange))
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(profondeur)
                .withCull(false)
                .build();
    }

    private static RenderSetup reglage(RenderPipeline p) {
        return RenderSetup.builder(p)
                .withTexture("Sampler0", TEX, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .createRenderSetup();
    }

    private static void dessiner() throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam.getFluidInCamera() != FogType.NONE && cam.getFluidInCamera() != FogType.ATMOSPHERIC) return;
        preparer();
        Vec3 c = cam.position();
        double now = MeditationClient.temps();
        float dt = Double.isNaN(derniere) ? 0f : (float) Math.max(0, Math.min(0.1, now - derniere));
        derniere = now;
        animerParticules(now, dt);
        if (octets == null) octets = new ByteBufferBuilder(32 * 1024);

        // passe 1 : lumière (le sol × (1 + halo))
        BufferBuilder bb = null;
        int n = 0;
        for (int i = 0; i < MeditationClient.CAPACITE; i++) {
            float f = force(i, now, c);
            if (f <= 0.004f) continue;
            int rgb = MeditationClient.RGB[i];
            if (bb == null) bb = new BufferBuilder(octets, rtLumiere.primitiveTopology(), rtLumiere.format());
            n += disque(bb, i, c, couleur(rgb, f * LUMIERE * (0.45f + 0.55f * MeditationClient.LUM[i])));
        }
        if (bb != null) tirer(rtLumiere, bb);

        // passe 2 : lueur additive + particules
        bb = null;
        for (int i = 0; i < MeditationClient.CAPACITE; i++) {
            float f = force(i, now, c);
            if (f <= 0.004f) continue;
            float nuit = 1f - MeditationClient.LUM[i];
            if (bb == null) bb = new BufferBuilder(octets, rtLueur.primitiveTopology(), rtLueur.format());
            n += disque(bb, i, c, couleur(MeditationClient.RGB[i], f * (LUEUR_JOUR + (LUEUR_NUIT - LUEUR_JOUR) * nuit)));
        }
        if (vivantes > 0) {
            if (bb == null) bb = new BufferBuilder(octets, rtLueur.primitiveTopology(), rtLueur.format());
            n += particules(bb, c);
        }
        if (bb != null) tirer(rtLueur, bb);
        quads += n;
    }

    /** Intensité 0..1 du halo {@code i} : fondus, respiration, distance. 0 = rien à dessiner. */
    private static float force(int i, double now, Vec3 c) {
        if (!MeditationClient.ACTIF[i] || !MeditationClient.VISIBLE[i] || MeditationClient.NQ[i] == 0) return 0f;
        double f = MeditationClient.fondu(i, now);
        if (f <= 0) return 0f;
        double dx = MeditationClient.CX[i] - c.x, dz = MeditationClient.CZ[i] - c.z, dy = MeditationClient.CY[i] - c.y;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > MeditationClient.PORTEE) return 0f;
        if (d > MeditationClient.PORTEE_FONDU) {
            f *= 1 - (d - MeditationClient.PORTEE_FONDU) / (MeditationClient.PORTEE - MeditationClient.PORTEE_FONDU);
        }
        double phase = (MeditationClient.ID[i] * 0.618) % 1.0;
        double souffle = 0.80 + 0.20 * Math.sin(2 * Math.PI * (now / SOUFFLE + phase));
        return (float) (f * souffle);
    }

    /** Couleur de sommet : teinte × force (canaux 0..255, alpha plein). */
    private static int couleur(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * k)),
                b = Math.min(255, Math.round((rgb & 255) * k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Les quads de sol du halo {@code i} (UV du disque 40×40 continus d'un bloc à l'autre). */
    private static int disque(BufferBuilder bb, int i, Vec3 c, int argb) {
        double cx = MeditationClient.CX[i], cz = MeditationClient.CZ[i];
        float r2 = 2 * MeditationClient.RAYON, u = 40f / TW, v = 40f / TH;
        int n = MeditationClient.NQ[i];
        for (int k = 0; k < n; k++) {
            int q = i * MeditationClient.COLONNES + k;
            float x0 = MeditationClient.QX0[q], x1 = MeditationClient.QX1[q], z0 = MeditationClient.QZ0[q], z1 = MeditationClient.QZ1[q];
            float y = (float) (MeditationClient.QY[q] + 0.002 - c.y);
            float u0 = (float) ((x0 - (cx - MeditationClient.RAYON)) / r2) * u, u1 = (float) ((x1 - (cx - MeditationClient.RAYON)) / r2) * u;
            float v0 = (float) ((z0 - (cz - MeditationClient.RAYON)) / r2) * v, v1 = (float) ((z1 - (cz - MeditationClient.RAYON)) / r2) * v;
            float ax = (float) (x0 - c.x), bx = (float) (x1 - c.x), az = (float) (z0 - c.z), bz = (float) (z1 - c.z);
            sommet(bb, ax, y, az, argb, u0, v0);
            sommet(bb, ax, y, bz, argb, u0, v1);
            sommet(bb, bx, y, bz, argb, u1, v1);
            sommet(bb, bx, y, az, argb, u1, v0);
        }
        return n;
    }

    private static void sommet(BufferBuilder bb, float x, float y, float z, int argb, float u, float v) {
        bb.addVertex(x, y, z, argb, u, v, OverlayTexture.NO_OVERLAY, LUMIERE_PLEINE, 0f, 1f, 0f);
    }

    /* ------------------------------------------------------------------ particules */

    /** Émission (≈ {@link #TAUX} par seconde et par méditant, au prorata du fondu) puis mouvement. */
    private static void animerParticules(double now, float dt) {
        if (dt <= 0) return;
        for (int i = 0; i < MeditationClient.CAPACITE; i++) {
            if (!MeditationClient.ACTIF[i] || !MeditationClient.VISIBLE[i]) { EMIS[i] = 0; continue; }
            double f = MeditationClient.fondu(i, now);
            if (!Double.isNaN(MeditationClient.FIN[i])) f = 0;           // on se relève : plus de nouvelles lueurs
            EMIS[i] += (float) (dt * TAUX * f);
            while (EMIS[i] >= 1f) {
                EMIS[i] -= 1f;
                emettre(i);
            }
        }
        if (vivantes == 0) return;
        for (int k = 0; k < PARTICULES; k++) {
            if (!PVIVE[k]) continue;
            AGE[k] += dt;
            if (AGE[k] >= VIE[k]) { PVIVE[k] = false; vivantes--; continue; }
            PX[k] += VX[k] * dt;
            PY[k] += VY[k] * dt;
            PZ[k] += VZ[k] * dt;
        }
    }

    /** Une lueur : sur un disque de rayon 0,5 au pied du méditant, monte (≈ 0,8 bloc/s) en s'écartant un peu. */
    private static void emettre(int i) {
        int k = -1;
        for (int j = 0; j < PARTICULES; j++) if (!PVIVE[j]) { k = j; break; }
        if (k < 0) return;
        double a = ALEA.nextDouble() * 2 * Math.PI, r = 0.5 * Math.sqrt(ALEA.nextDouble());
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        PX[k] = (float) (MeditationClient.CX[i] + ca * r);
        PZ[k] = (float) (MeditationClient.CZ[i] + sa * r);
        PY[k] = (float) (MeditationClient.CY[i] + 0.05 + ALEA.nextDouble() * 0.3);
        float ecart = 0.08f + ALEA.nextFloat() * 0.22f, monte = 0.55f + ALEA.nextFloat() * 0.45f;
        VX[k] = ca * ecart;
        VZ[k] = sa * ecart;
        VY[k] = monte;
        AGE[k] = 0;
        VIE[k] = 1.5f + ALEA.nextFloat() * 0.5f;                       // 30 à 40 ticks
        PROP[k] = i;
        PVIVE[k] = true;
        vivantes++;
    }

    /** Traînées 1 × 4 texels tournées vers la caméra autour de l'axe vertical, tête en haut. */
    private static int particules(BufferBuilder bb, Vec3 c) {
        int n = 0;
        float u0 = 44f / TW, u1 = 45f / TW, v1 = 4f / TH;
        for (int k = 0; k < PARTICULES; k++) {
            if (!PVIVE[k]) continue;
            float t = AGE[k] / VIE[k];
            // taille : ×1 jusqu'à 10 % de la vie, puis vers ×0,61 à 90 % (courbe du .fx)
            float taille = t < 0.1f ? 1f : t > 0.9f ? 0.61f : 1f - 0.39f * (t - 0.1f) / 0.8f;
            float alpha = Math.min(1f, t / 0.12f) * Math.min(1f, (1f - t) / 0.35f);
            int i = PROP[k];
            int rgb = MeditationClient.RGB[i];
            // teinte éclaircie (cœur presque blanc), comme la particule bleue à traînée blanche de la référence
            int tr = (((rgb >> 16) & 255) + 255) / 2, tg = (((rgb >> 8) & 255) + 255) / 2, tb = ((rgb & 255) + 255) / 2;
            int argb = couleur(tr << 16 | tg << 8 | tb, alpha * ECLAT);
            float x = (float) (PX[k] - c.x), y = (float) (PY[k] - c.y), z = (float) (PZ[k] - c.z);
            float l = (float) Math.sqrt(x * x + z * z);
            if (l < 1e-3f) continue;
            float demi = 0.5f / 16f * taille, haut = 4f / 16f * taille;
            float rx = z / l * demi, rz = -x / l * demi;
            sommet(bb, x - rx, y, z - rz, argb, u0, 0f);
            sommet(bb, x - rx, y - haut, z - rz, argb, u0, v1);
            sommet(bb, x + rx, y - haut, z + rz, argb, u1, v1);
            sommet(bb, x + rx, y, z + rz, argb, u1, 0f);
            n++;
        }
        return n;
    }

    /* ------------------------------------------------------------------ envoi */

    /** Envoie le maillage dans un tampon GPU réutilisé (rotation) et le dessine tout de suite. */
    private static void tirer(RenderType rt, BufferBuilder bb) {
        MeshData m = bb.build();
        if (m == null) return;
        try (m) {
            GpuDevice dev = RenderSystem.getDevice();
            ByteBuffer data = m.vertexBuffer();
            int taille = data.remaining();
            if (taille <= 0) return;
            image = (image + 1) % TAMPONS.length;
            GpuBuffer tampon = TAMPONS[image];
            if (tampon == null || tampon.isClosed() || tampon.size() < taille) {
                if (tampon != null && !tampon.isClosed()) tampon.close();
                long cap = Math.max(16 * 1024, Long.highestOneBit(taille) << 1);
                TAMPONS[image] = tampon = dev.createBuffer(() -> "reborn meditation", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, cap);
            }
            dev.createCommandEncoder().writeToBuffer(tampon.slice(0, taille), data);
            var seq = RenderSystem.getSequentialBuffer(rt.primitiveTopology());
            int n = m.drawState().indexCount();
            rt.prepare().drawFromBuffer(tampon, seq.getBuffer(n), seq.type(), 0, 0, n);
        }
    }
}
