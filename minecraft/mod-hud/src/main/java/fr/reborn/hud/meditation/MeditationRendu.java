package fr.reborn.hud.meditation;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Rendu du <b>halo de méditation</b> ({@link MeditationClient}) — v2 du 18/10, refaite sur la vidéo de référence (la
 * v1, « tache claire + traînées blanches qui montent », ne lui ressemblait pas). Formes et couleurs : {@link Aura}.
 *
 * <h2>Quatre passes, après le terrain translucide, sans écrire la profondeur</h2>
 * <ol>
 *   <li><b>disque</b> : voile {@link Aura#voile} à 50 % (mélange translucide), <b>éclairé par la lumière du lieu</b>
 *       (lightmap) comme le sol qu'il recouvre — net et rond, sombre au jour comme à la nuit ;</li>
 *   <li><b>couronne</b>, deux passes sur le profil {@link Aura#couronne} (le plus vif au bord du disque, éteint à
 *       {@link Aura#R_HALO}) : le sol prend la teinte {@link Aura#lumiere} (part {@link Aura#TEINTE}), puis la même
 *       couleur s'ajoute en lumière ({@link Aura#LUMIERE}) ; émissives (la nuit, elle luit sur le sol sombre) ;
 *       respiration lente (± 10 %, 3,2 s) ;</li>
 *   <li><b>étincelles</b> : points lumineux additifs face à la caméra (halo + cœur plus blanc), qui scintillent et
 *       dérivent lentement autour du corps, ≈ 8 à la fois par méditant.</li>
 * </ol>
 * Le disque et la couronne épousent le sol : un quad par bloc couvert, posé sur le dessus de la forme de collision
 * (dalle, pente en escalier…), UV continus d'un bloc à l'autre ; décalage de couche de l'ombre du jeu
 * ({@code VIEW_OFFSET_Z_LAYERING}) contre le z-fighting. Textures fabriquées au premier usage (pas de fichier) en
 * haute définition et filtrées en linéaire : disque 512² (≈ 230 texels par bloc, bord anti-crénelé d'un texel),
 * couronne 256², étincelle 32² ; aucun escalier.
 *
 * <p>Performance : tableaux et tampons préalloués et réutilisés, aucune allocation par image hormis les en-têtes
 * {@code BufferBuilder}/{@code MeshData} de l'API ; rien quand personne ne médite ; au-delà de 40 blocs, rien.
 */
final class MeditationRendu {

    private static final Identifier TEX_DISQUE = Identifier.fromNamespaceAndPath("reborn", "meditation/disque"),
            TEX_COURONNE = Identifier.fromNamespaceAndPath("reborn", "meditation/couronne"),
            TEX_ETINCELLE = Identifier.fromNamespaceAndPath("reborn", "meditation/etincelle");
    private static final int T_DISQUE = 512, T_COURONNE = 256, T_ETINCELLE = 32;
    /** Respiration de la couronne : période (s) et amplitude. */
    private static final double SOUFFLE = 3.2, SOUFFLE_AMPL = 0.10;
    private static final int LUMIERE_PLEINE = 0xF000F0;

    /* étincelles */
    /** Par seconde et par méditant ; vie 0,9 à 1,6 s : ≈ 8 visibles à la fois. */
    static float TAUX = 6.5f;
    /** Taille du halo et du cœur (blocs), force. */
    static float TAILLE_HALO = 0.085f, TAILLE_COEUR = 0.03f, ECLAT = 0.95f;
    static final int PARTICULES = 192;
    private static final float[] PX = new float[PARTICULES], PY = new float[PARTICULES], PZ = new float[PARTICULES],
            VX = new float[PARTICULES], VY = new float[PARTICULES], VZ = new float[PARTICULES],
            AGE = new float[PARTICULES], VIE = new float[PARTICULES], SCINT = new float[PARTICULES],
            PHASE = new float[PARTICULES], ECHELLE = new float[PARTICULES];
    private static final int[] PROP = new int[PARTICULES];
    private static final boolean[] PVIVE = new boolean[PARTICULES];
    private static final float[] EMIS = new float[MeditationClient.CAPACITE];
    private static final Random ALEA = new Random();
    private static int vivantes;
    private static double derniere = Double.NaN;

    private static RenderType rtDisque, rtTeinte, rtCouronne, rtEtincelle;
    private static ByteBufferBuilder octets;
    private static final GpuBuffer[] TAMPONS = new GpuBuffer[6];
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

    /* ------------------------------------------------------------------ préparation */

    /** Textures (une fois) et les trois types de rendu (shader d'entité du jeu, sans écriture de profondeur). */
    private static void preparer() throws ReflectiveOperationException {
        if (rtDisque != null) return;
        textures();
        DepthStencilState profondeur = new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false);
        BlendFunction additif = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendOp.ADD,
                BlendFactor.ZERO, BlendFactor.ONE, BlendOp.ADD);
        BlendFunction additifAlpha = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendOp.ADD,
                BlendFactor.ZERO, BlendFactor.ONE, BlendOp.ADD);
        RenderPipeline disque = pipeline("meditation_disque", BlendFunction.TRANSLUCENT, profondeur, true);
        RenderPipeline lueur = pipeline("meditation_etincelle", additif, profondeur, false);
        RenderPipeline teinte = pipeline("meditation_teinte", BlendFunction.TRANSLUCENT, profondeur, false);
        RenderPipeline lumiere = pipeline("meditation_couronne", additifAlpha, profondeur, false);
        Method creer = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
        creer.setAccessible(true);
        rtDisque = (RenderType) creer.invoke(null, "reborn_meditation_disque", reglage(disque, TEX_DISQUE, true));
        rtTeinte = (RenderType) creer.invoke(null, "reborn_meditation_teinte", reglage(teinte, TEX_COURONNE, false));
        rtCouronne = (RenderType) creer.invoke(null, "reborn_meditation_couronne", reglage(lumiere, TEX_COURONNE, false));
        rtEtincelle = (RenderType) creer.invoke(null, "reborn_meditation_etincelle", reglage(lueur, TEX_ETINCELLE, false));
    }

    private static RenderPipeline pipeline(String nom, BlendFunction melange, DepthStencilState profondeur, boolean eclaire) {
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withLocation(Identifier.fromNamespaceAndPath("reborn", "pipeline/" + nom))
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING");
        BindGroupLayout echant = BindGroupLayouts.SAMPLER0;
        if (eclaire) echant = BindGroupLayouts.SAMPLER0_SAMPLER2;      // la lumière du lieu (lightmap)
        else b.withShaderDefine("EMISSIVE");
        return b.withBindGroupLayout(echant)
                .withColorTargetState(new ColorTargetState(melange))
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(profondeur)
                .withCull(false)
                .build();
    }

    private static RenderSetup reglage(RenderPipeline p, Identifier tex, boolean eclaire) {
        RenderSetup.RenderSetupBuilder b = RenderSetup.builder(p)
                .withTexture("Sampler0", tex, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        if (eclaire) b.useLightmap();
        return b.createRenderSetup();
    }

    /**
     * Les trois textures, calculées une fois : le carré [−R_HALO, R_HALO]² pour le disque (blanc, alpha = couverture,
     * bord d'un texel) et la couronne (gris = profil), et la petite étincelle (halo doux + cœur).
     */
    private static boolean texturesPretes;

    /** Fabrique les textures à l'avance (à la connexion) : pas d'à-coup à la première méditation vue. */
    static void prechauffer() {
        try {
            textures();
        } catch (RuntimeException | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("reborn-hud").warn("Méditation : textures du halo ({})", e.toString());
        }
    }

    private static void textures() {
        if (texturesPretes) return;
        texturesPretes = true;
        var tm = Minecraft.getInstance().getTextureManager();
        NativeImage d = new NativeImage(T_DISQUE, T_DISQUE, false);
        float pas = 2 * Aura.R_HALO / T_DISQUE;
        for (int j = 0; j < T_DISQUE; j++) {
            for (int i = 0; i < T_DISQUE; i++) {
                float x = (i + 0.5f) * pas - Aura.R_HALO, z = (j + 0.5f) * pas - Aura.R_HALO;
                int a = Math.round(255 * Aura.disque((float) Math.sqrt(x * x + z * z), pas));
                d.setPixel(i, j, a << 24 | 0xFFFFFF);
            }
        }
        tm.register(TEX_DISQUE, new DynamicTexture(() -> "reborn meditation disque", d));
        NativeImage c = new NativeImage(T_COURONNE, T_COURONNE, false);
        pas = 2 * Aura.R_HALO / T_COURONNE;
        for (int j = 0; j < T_COURONNE; j++) {
            for (int i = 0; i < T_COURONNE; i++) {
                float x = (i + 0.5f) * pas - Aura.R_HALO, z = (j + 0.5f) * pas - Aura.R_HALO;
                float r = (float) Math.sqrt(x * x + z * z);
                // au bord intérieur, la couronne s'allume sur un texel (comme le disque) : pas de liseré noir
                float v = Aura.couronne(Math.max(r, Aura.R_DISQUE)) * (1 - Aura.disque(r, pas));
                int a = Math.round(255 * Math.min(1f, v));
                c.setPixel(i, j, a << 24 | 0xFFFFFF);                  // blanc, alpha = profil
            }
        }
        tm.register(TEX_COURONNE, new DynamicTexture(() -> "reborn meditation couronne", c));
        NativeImage e = new NativeImage(T_ETINCELLE, T_ETINCELLE, false);
        for (int j = 0; j < T_ETINCELLE; j++) {
            for (int i = 0; i < T_ETINCELLE; i++) {
                float x = (i + 0.5f) / T_ETINCELLE * 2 - 1, y = (j + 0.5f) / T_ETINCELLE * 2 - 1;
                float r = (float) Math.sqrt(x * x + y * y);
                float v = r >= 1 ? 0 : (float) (Math.exp(-r * r * 9) * 0.75 + Math.exp(-r * r * 60) * 0.25);
                v *= Math.max(0, 1 - r);
                int g = Math.round(255 * Math.min(1f, v));
                e.setPixel(i, j, 0xFF000000 | g << 16 | g << 8 | g);
            }
        }
        tm.register(TEX_ETINCELLE, new DynamicTexture(() -> "reborn meditation etincelle", e));
    }

    /* ------------------------------------------------------------------ image */

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
        int n = 0;

        // passe 1 : le disque sombre (voile éclairé par le lieu)
        BufferBuilder bb = null;
        for (int i = 0; i < MeditationClient.CAPACITE; i++) {
            float f = force(i, now, c);
            if (f <= 0.004f) continue;
            if (bb == null) bb = new BufferBuilder(octets, rtDisque.primitiveTopology(), rtDisque.format());
            int argb = Math.round(255 * Aura.VOILE * f) << 24 | Aura.voile(MeditationClient.RGB[i]);
            n += sol(bb, i, c, argb, MeditationClient.LUMIERE[i]);
        }
        if (bb != null) tirer(rtDisque, bb);

        // passes 2 et 3 : la couronne (respire) — le sol prend la teinte, puis la lumière s'ajoute
        for (int passe = 0; passe < 2; passe++) {
            RenderType rt = passe == 0 ? rtTeinte : rtCouronne;
            bb = null;
            for (int i = 0; i < MeditationClient.CAPACITE; i++) {
                float f = force(i, now, c);
                if (f <= 0.004f) continue;
                double phase = (MeditationClient.ID[i] * 0.618) % 1.0;
                float souffle = (float) (1 - SOUFFLE_AMPL + SOUFFLE_AMPL * Math.sin(2 * Math.PI * (now / SOUFFLE + phase)));
                int lum = Aura.lumiere(MeditationClient.RGB[i]);
                int argb = passe == 0 ? Math.round(255 * Math.min(1f, Aura.TEINTE * f * souffle)) << 24 | lum
                        : couleur(lum, f * souffle * Aura.LUMIERE);
                if (bb == null) bb = new BufferBuilder(octets, rt.primitiveTopology(), rt.format());
                n += sol(bb, i, c, argb, LUMIERE_PLEINE);
            }
            if (bb != null) tirer(rt, bb);
        }

        // passe 4 : les étincelles
        if (vivantes > 0) {
            bb = new BufferBuilder(octets, rtEtincelle.primitiveTopology(), rtEtincelle.format());
            n += etincelles(bb, cam, c, now);
            tirer(rtEtincelle, bb);
        }
        quads += n;
    }

    /** Intensité 0..1 du halo {@code i} : fondus et distance. 0 = rien à dessiner. */
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
        return (float) f;
    }

    /** Couleur de sommet : teinte × force (canaux 0..255, alpha plein). */
    private static int couleur(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * k)),
                b = Math.min(255, Math.round((rgb & 255) * k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Les quads de sol du halo {@code i} (UV du carré [−R, R]² continus d'un bloc à l'autre). */
    private static int sol(BufferBuilder bb, int i, Vec3 c, int argb, int lumiere) {
        double cx = MeditationClient.CX[i], cz = MeditationClient.CZ[i];
        float r = MeditationClient.RAYON, r2 = 2 * r;
        int n = MeditationClient.NQ[i];
        for (int k = 0; k < n; k++) {
            int q = i * MeditationClient.COLONNES + k;
            float x0 = MeditationClient.QX0[q], x1 = MeditationClient.QX1[q], z0 = MeditationClient.QZ0[q], z1 = MeditationClient.QZ1[q];
            float y = (float) (MeditationClient.QY[q] + 0.002 - c.y);
            float u0 = (float) ((x0 - (cx - r)) / r2), u1 = (float) ((x1 - (cx - r)) / r2);
            float v0 = (float) ((z0 - (cz - r)) / r2), v1 = (float) ((z1 - (cz - r)) / r2);
            float ax = (float) (x0 - c.x), bx = (float) (x1 - c.x), az = (float) (z0 - c.z), bz = (float) (z1 - c.z);
            sommet(bb, ax, y, az, argb, u0, v0, lumiere);
            sommet(bb, ax, y, bz, argb, u0, v1, lumiere);
            sommet(bb, bx, y, bz, argb, u1, v1, lumiere);
            sommet(bb, bx, y, az, argb, u1, v0, lumiere);
        }
        return n;
    }

    private static void sommet(BufferBuilder bb, float x, float y, float z, int argb, float u, float v, int lumiere) {
        bb.addVertex(x, y, z, argb, u, v, OverlayTexture.NO_OVERLAY, lumiere, 0f, 1f, 0f);
    }

    /* ------------------------------------------------------------------ étincelles */

    /** Émission (≈ {@link #TAUX} par seconde et par méditant, au prorata du fondu) puis mouvement. */
    private static void animerParticules(double now, float dt) {
        if (dt <= 0) return;
        for (int i = 0; i < MeditationClient.CAPACITE; i++) {
            if (!MeditationClient.ACTIF[i] || !MeditationClient.VISIBLE[i]) { EMIS[i] = 0; continue; }
            double f = MeditationClient.fondu(i, now);
            if (!Double.isNaN(MeditationClient.FIN[i])) f = 0;           // on se relève : plus de nouvelles étincelles
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

    /**
     * Une étincelle : dans un manchon autour du corps (rayon 0,18 à 0,5 du centre, du sol à 1,35 bloc au-dessus de
     * l'assise), dérive lente (monte de 0,05 à 0,25 bloc/s, s'écarte un peu), scintillement propre.
     */
    private static void emettre(int i) {
        int k = -1;
        for (int j = 0; j < PARTICULES; j++) if (!PVIVE[j]) { k = j; break; }
        if (k < 0) return;
        double a = ALEA.nextDouble() * 2 * Math.PI, r = 0.18 + 0.32 * Math.sqrt(ALEA.nextDouble());
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        PX[k] = (float) (MeditationClient.CX[i] + ca * r);
        PZ[k] = (float) (MeditationClient.CZ[i] + sa * r);
        PY[k] = (float) (MeditationClient.CY[i] + 0.04 + ALEA.nextDouble() * 1.3);
        float ecart = 0.02f + ALEA.nextFloat() * 0.06f;
        VX[k] = ca * ecart - sa * 0.04f;                                  // légère rotation autour du corps
        VZ[k] = sa * ecart + ca * 0.04f;
        VY[k] = 0.05f + ALEA.nextFloat() * 0.2f;
        AGE[k] = 0;
        VIE[k] = 0.9f + ALEA.nextFloat() * 0.7f;
        SCINT[k] = 9f + ALEA.nextFloat() * 10f;                          // rad/s
        PHASE[k] = ALEA.nextFloat() * 6.2832f;
        ECHELLE[k] = 0.7f + ALEA.nextFloat() * 0.6f;
        PROP[k] = i;
        PVIVE[k] = true;
        vivantes++;
    }

    /** Halo + cœur face à la caméra pour chaque étincelle (additif). */
    private static int etincelles(BufferBuilder bb, Camera cam, Vec3 c, double now) {
        Vector3fc g = cam.leftVector(), h = cam.upVector();
        float gx = g.x(), gy = g.y(), gz = g.z(), hx = h.x(), hy = h.y(), hz = h.z();
        int n = 0;
        for (int k = 0; k < PARTICULES; k++) {
            if (!PVIVE[k]) continue;
            float t = AGE[k] / VIE[k];
            float env = Math.min(1f, t / 0.15f) * Math.min(1f, (1f - t) / 0.3f);
            float sc = 0.5f + 0.5f * (float) Math.sin(PHASE[k] + AGE[k] * SCINT[k]);
            float a = env * (0.35f + 0.65f * sc * sc) * ECLAT;
            int i = PROP[k];
            if (MeditationClient.ACTIF[i]) a *= (float) MeditationClient.fondu(i, now);
            if (a <= 0.01f) continue;
            int teinte = Aura.etincelle(MeditationClient.RGB[i]);
            int coeur = melangeBlanc(teinte, 0.6f);
            float x = (float) (PX[k] - c.x), y = (float) (PY[k] - c.y), z = (float) (PZ[k] - c.z);
            float s = TAILLE_HALO * ECHELLE[k] * 0.5f;
            quadFace(bb, x, y, z, s, gx, gy, gz, hx, hy, hz, couleur(teinte, a * 0.75f));
            s = TAILLE_COEUR * ECHELLE[k] * 0.5f * (0.8f + 0.4f * sc);
            quadFace(bb, x, y, z, s, gx, gy, gz, hx, hy, hz, couleur(coeur, a));
            n += 2;
        }
        return n;
    }

    private static void quadFace(BufferBuilder bb, float x, float y, float z, float s, float gx, float gy, float gz,
                                 float hx, float hy, float hz, int argb) {
        float lx = gx * s, ly = gy * s, lz = gz * s, ux = hx * s, uy = hy * s, uz = hz * s;
        sommet(bb, x + lx + ux, y + ly + uy, z + lz + uz, argb, 0f, 0f, LUMIERE_PLEINE);
        sommet(bb, x + lx - ux, y + ly - uy, z + lz - uz, argb, 0f, 1f, LUMIERE_PLEINE);
        sommet(bb, x - lx - ux, y - ly - uy, z - lz - uz, argb, 1f, 1f, LUMIERE_PLEINE);
        sommet(bb, x - lx + ux, y - ly + uy, z - lz + uz, argb, 1f, 0f, LUMIERE_PLEINE);
    }

    private static int melangeBlanc(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return Aura.rgbClamp(r + (255 - r) * k, g + (255 - g) * k, b + (255 - b) * k);
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
