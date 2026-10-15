package fr.reborn.hud.meteo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Météo vue par le joueur local (alimentée par {@link MeteoPayload}) : pluie, vent de sable, brume, blizzard et
 * tempête de sable.
 *
 * <p>Pluie : la pluie de Minecraft telle quelle (LevelRainMeteoMixin, ClientLevelMeteoMixin), avec ses sons, plus
 * l'ambiance du filtre (voiles de pluie, bruine, brouillard gris-bleu) et des embruns au sol ; en orage, éclairs du
 * ciel du jeu et tonnerre. Sable et brume : un filtre plein écran ({@code reborn-hud:meteo}, voir
 * {@code shaders/post/meteo.fsh}) qui lit ses paramètres dans une petite texture dynamique ({@link #PARAMS}, 4x1) mise
 * à jour à chaque image (type, intensité, temps), plus le brouillard en distance du jeu ({@code FogRendererMeteoMixin}) ;
 * poussière 3D et vent dans le sable ({@link #tick()}).
 *
 * <p><b>Blizzard</b> (15/10) : mur de brouillard blanc-bleuté (visibilité {@link #visibilite}, 6 à 12 blocs en tempête),
 * neige du jeu qui tombe partout ({@code ClientLevelMeteoMixin}), flocons rapides en diagonale et rafales de poudreuse au
 * ras du sol portés par le vent, bise ; voile froid très léger sur l'écran. <b>Tempête de sable</b> : même principe en
 * ocre — mur de sable, grains et volutes chassés par le vent, souffle sourd, voile chaud. Le brouillard suit le jour et
 * la nuit (il s'assombrit avec le ciel).
 *
 * <p>Changement de météo : l'intensité redescend à 0 puis la nouvelle monte (fondu ~2 s, ~3 s pour les tempêtes : on
 * « entre » dans le mur). L'intensité « vit » un peu toute seule (rafales lentes ±15 %).
 */
public final class MeteoClient {

    public static final byte CLAIR = 0, PLUIE = 1, SABLE = 2, BRUME = 3, BLIZZARD = 4, TEMPETE = 5;
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
        cibleType = (t >= CLAIR && t <= TEMPETE) ? t : CLAIR;
        cibleIntensite = Math.max(0f, Math.min(1f, i));
        if (cibleType == CLAIR) cibleIntensite = 0f;
    }

    public static void clear() { update(CLAIR, 0f); }

    public static byte type() { return type; }

    /** Tempête dense (blizzard ou tempête de sable) en cours ? */
    public static boolean tempete() { return (type == BLIZZARD || type == TEMPETE) && intensite > 0.002f; }

    /**
     * Visibilité (blocs) du blizzard et de la tempête de sable selon l'intensité — <b>même courbe que le plugin</b>
     * ({@code MeteoCommand.visibilite}) : 100 % → 6 blocs, 90 % → ~8, 75 % → 12, 55 % → ~21, 25 % → ~48.
     */
    public static float visibilite(float i) {
        return (float) (6.0 * Math.exp(4.0 * Math.log(2.0) * (1.0 - Math.max(0f, Math.min(1f, i)))));
    }

    /** Brume : 1 au ras du sol, diminue quand le joueur s'élève au-dessus du sol (lissé). */
    private static float facteurHauteur = 1f;

    /** Intensité effective (après fondus et variations ; la brume se dissipe en altitude), 0..1. */
    public static float intensite() {
        if (intensite <= 0f) return 0f;
        double t = System.currentTimeMillis() / 1000.0;
        float vie = (float) (0.88 + 0.08 * Math.sin(t * 0.37) + 0.05 * Math.sin(t * 1.13 + 1.7));
        float h = type == BRUME ? facteurHauteur : 1f;
        return Math.min(1f, intensite * vie * h);
    }

    /** Direction du vent (radians, plan horizontal) : tourne lentement autour d'une direction moyenne. */
    private static double angleVent() {
        double t = System.currentTimeMillis() / 1000.0;
        return 0.6 + 0.35 * Math.sin(t * 0.05) + 0.12 * Math.sin(t * 0.31);
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
        if (type == SABLE) { poussiere(mc, i, rnd); volutes(mc, i, rnd, true, 0.86f, 0.70f, 0.50f, 0.0); }
        if (type == BRUME) volutes(mc, i, rnd, false, 0.88f, 0.90f, 0.92f, 0.0);
        if (type == PLUIE) embruns(mc, i, rnd);
        if (type == SABLE && sonTicks % 50 == 0) {
            mc.level.playLocalSound(x, y + 1, z, net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
                    net.minecraft.sounds.SoundSource.WEATHER, 0.08f + 0.22f * i, 0.55f + rnd.nextFloat() * 0.15f, false);
        }
        if (type == BLIZZARD) {
            flocons(mc, i, rnd, false);
            volutes(mc, i * 0.8f, rnd, false, 0.93f, 0.96f, 1.0f, 0.22 + 0.5 * i);
            if (sonTicks % 45 == 0) {                                   // bise : souffle plus aigu que le sable
                mc.level.playLocalSound(x, y + 1, z, net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
                        net.minecraft.sounds.SoundSource.WEATHER, 0.10f + 0.26f * i, 0.95f + rnd.nextFloat() * 0.2f, false);
            }
        }
        if (type == TEMPETE) {
            flocons(mc, i, rnd, true);
            volutes(mc, i, rnd, true, 0.86f, 0.70f, 0.50f, 0.35 + 0.6 * i);
            if (sonTicks % 40 == 0) {                                   // souffle sourd et grains
                mc.level.playLocalSound(x, y + 1, z, net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
                        net.minecraft.sounds.SoundSource.WEATHER, 0.14f + 0.3f * i, 0.5f + rnd.nextFloat() * 0.12f, false);
            }
        }
    }

    /**
     * Blizzard et tempête de sable : flocons (ou grains) qui filent en diagonale avec le vent autour de la caméra, nés
     * en amont pour traverser le champ de vision. Particules du jeu (neige de la poudreuse, cendre teintée), sans
     * gravité ni freinage ; nombre borné (≤ ~45 par tick à 100 %).
     */
    private static void flocons(Minecraft mc, float i, net.minecraft.util.RandomSource rnd, boolean sable) {
        if (mc.particleEngine == null) return;
        double ang = angleVent();
        double vx = Math.cos(ang), vz = Math.sin(ang);
        int n = Math.round(4 + 40 * (float) Math.pow(i, 1.3));
        var cam = mc.gameRenderer.mainCamera().position();
        for (int k = 0; k < n; k++) {
            double ox = (rnd.nextDouble() - 0.5) * 28, oz = (rnd.nextDouble() - 0.5) * 28;
            double px = cam.x + ox - vx * 9, pz = cam.z + oz - vz * 9;
            double py = cam.y - 3 + rnd.nextDouble() * 10;
            var p = mc.particleEngine.createParticle(sable ? net.minecraft.core.particles.ParticleTypes.WHITE_ASH
                    : net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, px, py, pz, 0, 0, 0);
            if (p == null) continue;
            var acc = (fr.reborn.hud.mixin.ParticleMeteoAccessor) p;
            acc.reborn$setGravity(0f);
            acc.reborn$setFriction(1f);
            acc.reborn$setHasPhysics(false);
            double v = (sable ? 0.45 : 0.35) + 0.75 * i + rnd.nextDouble() * 0.25;
            double chute = sable ? (rnd.nextDouble() - 0.5) * 0.05 : -(0.06 + 0.1 * rnd.nextDouble());
            p.setParticleSpeed(vx * v, chute, vz * v);
            p.setLifetime(18 + rnd.nextInt(18));
            p.scale(sable ? 0.8f + rnd.nextFloat() * 0.9f : 0.6f + rnd.nextFloat() * 0.7f);
            if (p instanceof net.minecraft.client.particle.SingleQuadParticle q) {
                float c = 0.88f + rnd.nextFloat() * 0.18f;
                if (sable) q.setColor(0.84f * c, 0.66f * c, 0.42f * c);
                else q.setColor(Math.min(1f, 0.97f * c), Math.min(1f, 0.98f * c), 1.0f);
            }
        }
    }

    /**
     * Volume du sable et de la brume, dans le monde et non sur l'écran : grosses volutes de fumée du jeu, très
     * transparentes et teintées, portées par le vent (vite pour le sable, à peine pour la brume). Elles naissent à
     * distance, dans le brouillard, pour ne pas apparaître sous les yeux ; la brume reste collée au sol.
     */
    private static void volutes(Minecraft mc, float i, net.minecraft.util.RandomSource rnd, boolean sable,
                                float cr, float cg, float cb, double vent) {
        if (mc.particleEngine == null) return;
        float moyenne = sable ? 0.4f + 2.6f * i * i : 0.3f + 1.2f * i;          // volutes par tick, en moyenne
        if (vent > 0) moyenne += 1.5f * i;                                      // tempête : plus de rafales
        int n = (int) moyenne + (rnd.nextFloat() < moyenne - (int) moyenne ? 1 : 0);
        double ang = angleVent();
        double vx = Math.cos(ang), vz = Math.sin(ang);
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 8 + rnd.nextDouble() * 32;
            double px = mc.player.getX() + Math.cos(a) * r, pz = mc.player.getZ() + Math.sin(a) * r;
            if (sable) { px -= vx * 14; pz -= vz * 14; }                          // plutôt en amont du vent
            int sol = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    net.minecraft.util.Mth.floor(px), net.minecraft.util.Mth.floor(pz));
            if (Math.abs(sol - mc.player.getY()) > 16) continue;
            double haut = sable ? 0.3 + rnd.nextDouble() * rnd.nextDouble() * (1.5 + 10 * i * i)
                                : rnd.nextDouble() * rnd.nextDouble() * (1.5 + 2.5 * i);
            var p = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    px, sol + haut, pz, 0, 0, 0);
            if (p == null) continue;
            var acc = (fr.reborn.hud.mixin.ParticleMeteoAccessor) p;
            acc.reborn$setGravity(0f);
            acc.reborn$setHasPhysics(false);
            double v = vent > 0 ? vent + rnd.nextDouble() * 0.1
                    : sable ? 0.08 + 0.35 * i + rnd.nextDouble() * 0.08 : 0.01 + 0.02 * rnd.nextDouble();
            p.setParticleSpeed(vx * v, (rnd.nextDouble() - 0.5) * 0.004, vz * v);
            p.setLifetime(sable ? 90 + rnd.nextInt(60) : 200 + rnd.nextInt(120));
            p.scale(sable ? 3f + rnd.nextFloat() * 4f : 4f + rnd.nextFloat() * 5f);
            if (p instanceof net.minecraft.client.particle.SingleQuadParticle q) {
                float c = 0.92f + rnd.nextFloat() * 0.12f;
                q.setColor(Math.min(1f, cr * c), Math.min(1f, cg * c), Math.min(1f, cb * c));
                ((fr.reborn.hud.mixin.SingleQuadParticleMeteoAccessor) q)
                        .reborn$setAlpha(sable ? 0.05f + 0.14f * i : 0.05f + 0.12f * i);
            }
        }
    }

    /**
     * Tempête de sable : poussière en 3D portée par le vent autour du joueur (particules de cendre teintées sable,
     * sans gravité ni freinage). Le vent tourne lentement autour d'une direction moyenne.
     */
    private static void poussiere(Minecraft mc, float i, net.minecraft.util.RandomSource rnd) {
        if (mc.particleEngine == null) return;
        double ang = angleVent();
        double vx = Math.cos(ang), vz = Math.sin(ang);
        int n = Math.round(3 + 30 * (float) Math.pow(i, 1.5));
        double hauteur = 1.2 + 8.0 * i * i;                 // petit vent : au ras du sol ; tempête : partout
        for (int k = 0; k < n; k++) {
            double ox = (rnd.nextDouble() - 0.5) * 36, oz = (rnd.nextDouble() - 0.5) * 36;
            double px = mc.player.getX() + ox - vx * 10, pz = mc.player.getZ() + oz - vz * 10;
            double py = mc.player.getY() - 1.5 + rnd.nextDouble() * rnd.nextDouble() * hauteur;  // plus dense près du sol
            var p = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, px, py, pz, 0, 0, 0);
            if (p == null) continue;
            var acc = (fr.reborn.hud.mixin.ParticleMeteoAccessor) p;
            acc.reborn$setGravity(0f);
            acc.reborn$setFriction(1f);
            acc.reborn$setHasPhysics(false);
            double v = 0.15 + 0.6 * i + rnd.nextDouble() * 0.2;
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

    /** Blizzard en cours : le jeu fait tomber sa neige partout (LevelRainMeteoMixin, ClientLevelMeteoMixin). */
    public static boolean neige3D() { return type == BLIZZARD && intensite > 0.002f; }

    /** 1 = le joueur est sous le ciel, 0 = à l'abri (lissé) : les gouttelettes sur l'objectif n'apparaissent que dehors. */
    private static float exposition = 1f;

    /**
     * Orientation de la caméra pour le filtre (nappes, voiles et volutes accrochés au monde) : lacet « déroulé »
     * (cumul des rotations, ramené sur 0..3600° pour ne sauter que tous les 10 tours) et tangage.
     */
    private static float lacet = 0f, dernierLacet = Float.NaN, tangage = 0f;

    /** Le filtre météo doit-il être affiché ? */
    public static boolean actif() { return type != CLAIR && intensite > 0.002f; }

    /** Appelé à chaque image : fondus, puis mise à jour de la texture de paramètres. */
    public static void frame() {
        long now = System.currentTimeMillis();
        float dt = dernier == 0L ? 0f : Math.min(0.25f, (now - dernier) / 1000f);
        dernier = now;
        // ~2 s pour aller de 0 à 1 ; ~3 s pour entrer dans une tempête ou en sortir
        float vitesse = ((type == BLIZZARD || type == TEMPETE || cibleType == BLIZZARD || cibleType == TEMPETE)
                ? 0.33f : 0.5f) * dt;
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
            // brume : se dissipe quand on s'élève au-dessus du sol (rien au saut, presque plus à ~20 blocs)
            int sol = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    mc.player.getBlockX(), mc.player.getBlockZ());
            float h = (float) (mc.player.getEyeY() - sol);
            float u = Math.max(0f, Math.min(1f, (h - 2.5f) / 18f));
            float fh = 1f - 0.85f * u * u * (3f - 2f * u);
            facteurHauteur += (fh - facteurHauteur) * Math.min(1f, dt * 2f);
            soleil(mc);
            var cam = mc.gameRenderer.mainCamera();
            float y = cam.yRot();
            if (!Float.isNaN(dernierLacet)) {
                lacet += net.minecraft.util.Mth.wrapDegrees(y - dernierLacet);
                lacet = ((lacet % 3600f) + 3600f) % 3600f;
            }
            dernierLacet = y;
            tangage = Math.max(-90f, Math.min(90f, cam.xRot()));
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
        // pixel 0 : R = type, G = intensité, B/A = temps (bits 0-15) ; pixel 1 : R = temps (bits 16-23), G = champ de vision
        px.setPixel(0, 0, argb((int) ((t60 >> 8) & 0xFF), type, i255, (int) (t60 & 0xFF)));
        int fov = mc.options == null ? 70 : mc.options.fov().get();
        px.setPixel(1, 0, argb(255, (int) ((t60 >> 16) & 0xFF), octet(fov / 180f), 0));
        // pixel 2 : R = exposition au ciel, G/B = soleil à l'écran (uv, codé sur [-0,5 ; 1,5]), A = visibilité du soleil
        px.setPixel(2, 0, argb(octet(soleilVis), octet(exposition), octet((soleilX + 0.5f) / 2f), octet((soleilY + 0.5f) / 2f)));
        // pixel 3 : R/G = lacet déroulé (0..3600° sur 16 bits), B/A = tangage (-90..90° sur 16 bits)
        int l16 = Math.round(lacet / 3600f * 65535f), t16 = Math.round((tangage + 90f) / 180f * 65535f);
        px.setPixel(3, 0, argb(t16 & 0xFF, (l16 >> 8) & 0xFF, l16 & 0xFF, (t16 >> 8) & 0xFF));
        params.upload();
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
