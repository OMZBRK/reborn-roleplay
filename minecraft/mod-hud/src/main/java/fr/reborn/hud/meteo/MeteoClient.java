package fr.reborn.hud.meteo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Météo vue par le joueur local (alimentée par {@link MeteoPayload}) : pluie, tempête de sable, brume.
 *
 * <p>Pluie : la pluie de Minecraft telle quelle (LevelRainMeteoMixin, ClientLevelMeteoMixin), avec ses sons, plus
 * l'ambiance du filtre (voiles de pluie, bruine, brouillard gris-bleu) et des embruns au sol ; en orage, éclairs du
 * ciel du jeu et tonnerre. Sable et brume : un filtre plein écran ({@code reborn-hud:meteo}, voir
 * {@code shaders/post/meteo.fsh}) qui lit ses paramètres dans une petite texture dynamique ({@link #PARAMS}, 4x1) mise
 * à jour à chaque image (type, intensité, temps), plus le brouillard en distance du jeu ({@code FogRendererMeteoMixin}) ;
 * poussière 3D et vent dans le sable ({@link #tick()}).
 *
 * <p>Changement de météo : l'intensité redescend à 0 puis la nouvelle monte (fondu ~2 s). L'intensité « vit » un peu
 * toute seule (rafales lentes ±15 %).
 */
public final class MeteoClient {

    public static final byte CLAIR = 0, PLUIE = 1, SABLE = 2, BRUME = 3;
    public static final Identifier FILTRE = Identifier.fromNamespaceAndPath("reborn-hud", "meteo");
    /** Lue par le filtre (entrée « texture » reborn-hud:meteo_params → textures/effect/meteo_params.png). */
    public static final Identifier PARAMS = Identifier.fromNamespaceAndPath("reborn-hud", "textures/effect/meteo_params.png");

    private static volatile byte cibleType = CLAIR;
    private static volatile float cibleIntensite = 0f;
    private static byte type = CLAIR;
    private static float intensite = 0f;
    private static long dernier = 0L;
    private static DynamicTexture params;

    private MeteoClient() {}

    public static void update(byte t, float i) {
        cibleType = (t >= CLAIR && t <= BRUME) ? t : CLAIR;
        cibleIntensite = Math.max(0f, Math.min(1f, i));
        if (cibleType == CLAIR) cibleIntensite = 0f;
    }

    public static void clear() { update(CLAIR, 0f); }

    public static byte type() { return type; }

    /** Intensité effective (après fondus et variations), 0..1. */
    public static float intensite() {
        if (intensite <= 0f) return 0f;
        double t = System.currentTimeMillis() / 1000.0;
        float vie = (float) (0.88 + 0.08 * Math.sin(t * 0.37) + 0.05 * Math.sin(t * 1.13 + 1.7));
        return Math.min(1f, intensite * vie);
    }

    private static int sonTicks;
    /** Tonnerre programmé après un éclair (ticks restants, -1 = aucun). */
    private static int tonnerre = -1;

    /** Déclenche un éclair (pluie forte) : le ciel du jeu s'illumine, puis tonnerre 1 à 3 s plus tard. */
    public static void eclair() {
        tonnerre = 20 + (int) (Math.random() * 40);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.setSkyFlashTime(2);
    }

    /** À chaque tick client : éclairs et tonnerre (orage), poussière et vent (sable). La pluie a ses sons du jeu. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float i = intensite();
        if (i <= 0.05f) return;
        sonTicks++;
        var rnd = mc.level.getRandom();
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (type == PLUIE && i > 0.55f && rnd.nextInt(500) == 0) eclair();     // ~1 éclair / 25 s en orage
        if (tonnerre > 0 && --tonnerre == 0) {
            mc.level.playLocalSound(x + rnd.nextGaussian() * 30, y + 20, z + rnd.nextGaussian() * 30,
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.WEATHER,
                    1.2f + 0.8f * i, 0.8f + rnd.nextFloat() * 0.2f, false);
        }
        if (type == SABLE) poussiere(mc, i, rnd);
        if (type == PLUIE) embruns(mc, i, rnd);
        if (type == SABLE && sonTicks % 50 == 0) {
            mc.level.playLocalSound(x, y + 1, z, net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
                    net.minecraft.sounds.SoundSource.WEATHER, 0.08f + 0.22f * i, 0.55f + rnd.nextFloat() * 0.15f, false);
        }
    }

    /**
     * Tempête de sable : poussière en 3D portée par le vent autour du joueur (particules de cendre teintées sable,
     * sans gravité ni freinage). Le vent tourne lentement autour d'une direction moyenne.
     */
    private static void poussiere(Minecraft mc, float i, net.minecraft.util.RandomSource rnd) {
        if (mc.particleEngine == null) return;
        double t = System.currentTimeMillis() / 1000.0;
        double ang = 0.6 + 0.35 * Math.sin(t * 0.05) + 0.12 * Math.sin(t * 0.31);
        double vx = Math.cos(ang), vz = Math.sin(ang);
        int n = Math.round(4 + 22 * i);
        for (int k = 0; k < n; k++) {
            double ox = (rnd.nextDouble() - 0.5) * 36, oz = (rnd.nextDouble() - 0.5) * 36;
            double px = mc.player.getX() + ox - vx * 10, pz = mc.player.getZ() + oz - vz * 10;
            double py = mc.player.getY() - 1.5 + rnd.nextDouble() * rnd.nextDouble() * 9;    // plus dense près du sol
            var p = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, px, py, pz, 0, 0, 0);
            if (p == null) continue;
            var acc = (fr.reborn.hud.mixin.ParticleMeteoAccessor) p;
            acc.reborn$setGravity(0f);
            acc.reborn$setFriction(1f);
            acc.reborn$setHasPhysics(false);
            double v = 0.35 + 0.35 * i + rnd.nextDouble() * 0.25;
            p.setParticleSpeed(vx * v, (rnd.nextDouble() - 0.45) * 0.04, vz * v);
            p.setLifetime(50 + rnd.nextInt(40));
            p.scale(1.2f + rnd.nextFloat() * 1.6f);
            if (p instanceof net.minecraft.client.particle.SingleQuadParticle q) {
                float c = 0.85f + rnd.nextFloat() * 0.25f;
                q.setColor(0.86f * c, 0.68f * c, 0.46f * c);
            }
        }
    }

    /**
     * Pluie : embruns en 3D — fines gouttelettes d'éclaboussures qui flottent au ras du sol autour du joueur et dérivent
     * doucement avec le vent (particules de cendre teintées eau, sans gravité). Même principe que la poussière du sable.
     */
    private static void embruns(Minecraft mc, float i, net.minecraft.util.RandomSource rnd) {
        if (mc.particleEngine == null) return;
        double t = System.currentTimeMillis() / 1000.0;
        double ang = 0.6 + 0.35 * Math.sin(t * 0.05) + 0.12 * Math.sin(t * 0.31);
        double vx = Math.cos(ang), vz = Math.sin(ang);
        int n = Math.round(2 + 10 * i);
        for (int k = 0; k < n; k++) {
            double px = mc.player.getX() + (rnd.nextDouble() - 0.5) * 30, pz = mc.player.getZ() + (rnd.nextDouble() - 0.5) * 30;
            var sol = mc.level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    net.minecraft.core.BlockPos.containing(px, 0, pz));
            if (Math.abs(sol.getY() - mc.player.getY()) > 12) continue;
            double py = sol.getY() + 0.05 + rnd.nextDouble() * rnd.nextDouble() * 1.2;     // au ras du sol, sous la pluie
            var p = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, px, py, pz, 0, 0, 0);
            if (p == null) continue;
            var acc = (fr.reborn.hud.mixin.ParticleMeteoAccessor) p;
            acc.reborn$setGravity(0f);
            acc.reborn$setFriction(1f);
            acc.reborn$setHasPhysics(false);
            double v = 0.04 + 0.08 * i * rnd.nextDouble();
            p.setParticleSpeed(vx * v, (rnd.nextDouble() - 0.3) * 0.015, vz * v);
            p.setLifetime(30 + rnd.nextInt(30));
            p.scale(0.8f + rnd.nextFloat() * 0.8f);
            if (p instanceof net.minecraft.client.particle.SingleQuadParticle q) {
                float c = 0.9f + rnd.nextFloat() * 0.1f;
                q.setColor(0.82f * c, 0.87f * c, 0.93f * c);
            }
        }
    }

    /** Position du soleil à l'écran (uv), et sa visibilité 0..1 (devant la caméra et au-dessus de l'horizon). */
    private static float soleilX = 0.5f, soleilY = 0.8f, soleilVis = 0f;

    private static void soleil(Minecraft mc) {
        long ct = mc.level.getOverworldClockTime();
        double f = ((ct % 24000L) / 24000.0) - 0.25;
        f -= Math.floor(f);
        double d = 0.5 - Math.cos(f * Math.PI) / 2.0;
        double a = (f * 2.0 + d) / 3.0 * Math.PI * 2.0;                     // angle céleste (0 = midi)
        float sx = (float) -Math.sin(a), sy = (float) Math.cos(a), sz = 0f;
        var cam = mc.gameRenderer.mainCamera();
        var fw = cam.forwardVector(); var up = cam.upVector(); var le = cam.leftVector();
        float df = sx * fw.x() + sy * fw.y() + sz * fw.z();
        float dl = sx * le.x() + sy * le.y() + sz * le.z();
        float du = sx * up.x() + sy * up.y() + sz * up.z();
        float vis = Math.max(0f, Math.min(1f, (df - 0.05f) * 4f)) * Math.max(0f, Math.min(1f, sy * 5f + 0.2f));
        if (df > 0.05f) {
            double th = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
            double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            soleilX = (float) (0.5 + 0.5 * (-dl / (df * th * aspect)));
            soleilY = (float) (0.5 + 0.5 * (du / (df * th)));
        }
        soleilVis = vis;
    }

    private static int octet(float v) { return Math.max(0, Math.min(255, Math.round(v * 255f))); }

    /** Pluie Reborn en cours : le jeu affiche alors sa propre pluie (LevelRainMeteoMixin, ClientLevelMeteoMixin). */
    public static boolean pluie3D() { return type == PLUIE && intensite > 0.002f; }

    /** 1 = le joueur est sous le ciel, 0 = à l'abri (lissé) : les gouttelettes sur l'objectif n'apparaissent que dehors. */
    private static float exposition = 1f;

    /** Le filtre météo doit-il être affiché ? */
    public static boolean actif() { return type != CLAIR && intensite > 0.002f; }

    /** Appelé à chaque image : fondus, puis mise à jour de la texture de paramètres. */
    public static void frame() {
        long now = System.currentTimeMillis();
        float dt = dernier == 0L ? 0f : Math.min(0.25f, (now - dernier) / 1000f);
        dernier = now;
        float vitesse = 0.5f * dt;                       // ~2 s pour aller de 0 à 1
        if (type != cibleType) {
            intensite = Math.max(0f, intensite - vitesse);
            if (intensite <= 0f) type = cibleType;
        } else if (intensite < cibleIntensite) {
            intensite = Math.min(cibleIntensite, intensite + vitesse);
        } else if (intensite > cibleIntensite) {
            intensite = Math.max(cibleIntensite, intensite - vitesse);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            float cible = mc.level.canSeeSky(net.minecraft.core.BlockPos.containing(mc.player.getEyePosition())) ? 1f : 0f;
            exposition += (cible - exposition) * Math.min(1f, dt * 1.5f);
            soleil(mc);
        }
        ecrireParams(now);
    }

    private static void ecrireParams(long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getTextureManager() == null) return;
        if (params == null) {
            params = new DynamicTexture(() -> "reborn-hud meteo params", 4, 1, false);
            mc.getTextureManager().register(PARAMS, params);
        }
        long t60 = (now / 1000L * 60L + (now % 1000L) * 60L / 1000L) & 0xFFFFFFL;   // temps en 1/60 s, 24 bits
        int i255 = Math.round(intensite() * 255f);
        NativeImage px = params.getPixels();
        // pixel 0 : R = type, G = intensité, B/A = temps (bits 0-15) ; pixel 1 : R = temps (bits 16-23)
        px.setPixel(0, 0, argb((int) ((t60 >> 8) & 0xFF), type, i255, (int) (t60 & 0xFF)));
        px.setPixel(1, 0, argb(255, (int) ((t60 >> 16) & 0xFF), 0, 0));
        // pixel 2 : R = exposition au ciel, G/B = soleil à l'écran (uv, codé sur [-0,5 ; 1,5]), A = visibilité du soleil
        px.setPixel(2, 0, argb(octet(soleilVis), octet(exposition), octet((soleilX + 0.5f) / 2f), octet((soleilY + 0.5f) / 2f)));
        params.upload();
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
