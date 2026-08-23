package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * <b>Test de la feuille</b> — l'entraînement de concentration de la feuille
 * (陣印術, réf. animé/manga). On canalise son chakra dans une feuille sensible :
 * <b>Katon</b> l'enflamme, <b>Suiton</b> la mouille, <b>Raiton</b> la froisse,
 * <b>Fûton</b> la fend, <b>Doton</b> l'effrite. La réaction révèle la nature.
 *
 * <p>Rendu <b>subtil + in-world</b> (fidèle à Zenkai / à l'animé), pas de gros
 * GUI plein écran : le décor reste visible, le perso passe en 3ᵉ personne de
 * face, la réaction se joue en <b>particules vanilla</b> (GPU-batchées, donc
 * fluides), puis un <b>petit popup propre</b> annonce le résultat (kanji +
 * nature) — comme l'écran « Vous avez obtenu… » de référence.
 *
 * <p>Tirage = <b>1/5</b> (5 natures), <b>aucune rareté</b> : juste une
 * <b>pondération par clan</b> (Uchiha → +Katon, Hōzuki → +Suiton…). Côté client
 * c'est un prototype de FEEL (roll local) ; l'attribution réelle + le clan
 * viendront du serveur (ShinobiCore).
 */
public class TirageScreen extends Screen {

    // Durées (ms).
    private static final long CONCENTRATION_MS = 1700;
    private static final long REACTION_MS = 1300;

    private enum Phase { CONCENTRATION, REACTION, RESULT }

    private Phase phase = Phase.CONCENTRATION;
    private long phaseStart = now();
    private Nature nature;
    private boolean reactionSoundPlayed;

    private final Random rng = new Random();

    // Caméra / HUD sauvegardés et restaurés à la fermeture.
    private CameraType prevCam;
    private RebornCamera.Mode prevMode;
    private double prevCamYaw, prevCamPitch, prevRight, prevDistance;
    private boolean prevHudHidden;
    private boolean captured = false;

    /** Angle de la caméra autour du perso : ¾ profil (montre le côté + les
     *  particules devant lui, contrairement à la vue de dos qui les cache). */
    private static final float SIDE_YAW = 75f;

    /** Clan du perso (pilote la pondération). null = uniforme (démo client). */
    private final String clan;

    public TirageScreen() { this(null); }

    public TirageScreen(String clan) {
        super(Component.literal("Test de la feuille"));
        this.clan = clan;
    }

    private static long now() { return System.currentTimeMillis(); }
    private void setPhase(Phase p) { phase = p; phaseStart = now(); }
    private float pt(long dur) { return Math.min(1f, (now() - phaseStart) / (float) dur); }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!captured && mc.options != null && mc.player != null) {
            RebornCamera cam = RebornCamera.INSTANCE;
            prevCam = mc.options.getCameraType();
            prevMode = cam.mode();
            prevCamYaw = cam.camYaw();
            prevCamPitch = cam.camPitch();
            prevRight = cam.rightMagnitude();
            prevDistance = cam.distance();
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            captured = true;
        }
        applyProfileCamera(mc);
        startTest();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (captured && mc.options != null) {
            RebornCamera cam = RebornCamera.INSTANCE;
            cam.setMode(prevMode);
            cam.setRight(prevRight);
            cam.setDistance(prevDistance);
            // Réaligne la caméra derrière le joueur pour une sortie sans à-coup.
            if (mc.player != null) cam.initOrientation(mc.player.getYRot(), mc.player.getXRot());
            else cam.initOrientation((float) prevCamYaw, (float) prevCamPitch);
            mc.options.setCameraType(prevCam);
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            captured = false;
        }
        super.removed();
    }

    /** Place la caméra en vue de profil ¾ autour du perso (mode épaule + orbite
     *  Reborn). Ré-appelée chaque tick pour tenir contre toute réinitialisation. */
    private void applyProfileCamera(Minecraft mc) {
        if (mc.player == null || mc.options == null) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        cam.setMode(RebornCamera.Mode.SHOULDER);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        cam.initOrientation(mc.player.getYRot() + SIDE_YAW, 6f);
        cam.setRight(0);
        cam.setDistance(3.4);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void startTest() {
        nature = rollNature(clan);
        reactionSoundPlayed = false;
        setPhase(Phase.CONCENTRATION);
        playSound(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.6f);
    }

    // ─────────── Tirage pondéré par clan ───────────
    /**
     * Roll 1/5 pondéré par le clan. Base = 20 % chacune ; un clan pousse son
     * affinité (Uchiha → Katon, Hōzuki → Suiton…) au détriment des autres.
     * Table volontairement lisible/tunable — le serveur appellera la même logique
     * avec le vrai clan. Réf. animé : entraînement de concentration de la feuille.
     */
    static Nature rollNature(String clan) {
        double[] w = {20, 20, 20, 20, 20}; // Katon, Suiton, Raiton, Fûton, Doton
        int fav = clanFavored(clan);
        if (fav >= 0) {
            // +30 pts sur l'affinité du clan, répartis en retrait sur les 4 autres.
            for (int i = 0; i < w.length; i++) w[i] -= 7.5;
            w[fav] += 30 + 7.5;
        }
        double total = 0; for (double v : w) total += Math.max(0, v);
        double x = new Random().nextDouble() * total, acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += Math.max(0, w[i]);
            if (x < acc) return Nature.values()[i];
        }
        return Nature.KATON;
    }

    /** Index de nature favorisée par le clan (-1 = aucun). Tunable / extensible. */
    private static int clanFavored(String clan) {
        if (clan == null) return -1;
        String c = clan.toLowerCase();
        if (c.contains("uchiha") || c.contains("sarutobi")) return 0;   // Katon
        if (c.contains("hozuki") || c.contains("hōzuki") || c.contains("senju")) return 1; // Suiton
        if (c.contains("yotsuki") || c.contains("kaminari")) return 2;  // Raiton
        if (c.contains("kaguya") || c.contains("kazekage") || c.contains("sabaku")) return 3; // Fûton
        if (c.contains("kamizuru") || c.contains("iwa")) return 4;      // Doton
        return -1;
    }

    // ─────────── Boucle temporelle ───────────
    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        applyProfileCamera(mc); // tient la vue de profil contre RebornCamera.tickView
        Vec3 focus = focusPoint(p);

        switch (phase) {
            case CONCENTRATION -> {
                spawnConcentration(mc, focus, pt(CONCENTRATION_MS));
                if (pt(CONCENTRATION_MS) >= 1f) setPhase(Phase.REACTION);
            }
            case REACTION -> {
                if (!reactionSoundPlayed) {
                    reactionSoundPlayed = true;
                    playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.9f, 0.5f);
                }
                spawnReaction(mc, focus, pt(REACTION_MS));
                if (pt(REACTION_MS) >= 1f) {
                    setPhase(Phase.RESULT);
                    playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
                }
            }
            default -> { }
        }
    }

    /** Point focal = devant le torse du perso (visible en 3ᵉ pers de face). */
    private Vec3 focusPoint(LocalPlayer p) {
        Vec3 look = p.getLookAngle();
        double hx = look.x, hz = look.z;
        double n = Math.sqrt(hx * hx + hz * hz);
        if (n > 1e-4) { hx /= n; hz /= n; }
        return new Vec3(p.getX() + hx * 0.85, p.getY() + 1.2, p.getZ() + hz * 0.85);
    }

    // ─────────── Particules in-world (vanilla, GPU) ───────────
    private void spawnConcentration(Minecraft mc, Vec3 f, float t) {
        // Chakra qui converge : quelques particules par tick sur une sphère,
        // vélocité vers le centre (effet de concentration). Léger.
        int count = 4 + (int) (t * 5);
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2, e = (rng.nextDouble() - 0.5) * Math.PI;
            double r = 0.55 + rng.nextDouble() * 0.25;
            double px = f.x + Math.cos(a) * Math.cos(e) * r;
            double py = f.y + Math.sin(e) * r;
            double pz = f.z + Math.sin(a) * Math.cos(e) * r;
            double sp = 0.06 + t * 0.05;
            mc.level.addParticle(t > 0.7f ? ParticleTypes.END_ROD : ParticleTypes.GLOW,
                px, py, pz, (f.x - px) * sp, (f.y - py) * sp, (f.z - pz) * sp);
        }
    }

    private void spawnReaction(Minecraft mc, Vec3 f, float t) {
        ParticleOptions[] set = nature.particles;
        int count = 6;
        for (int i = 0; i < count; i++) {
            ParticleOptions po = set[rng.nextInt(set.length)];
            double sx = (rng.nextDouble() - 0.5) * 0.5;
            double sy = (rng.nextDouble() - 0.5) * 0.5;
            double sz = (rng.nextDouble() - 0.5) * 0.5;
            double vx = nature.vx * (rng.nextDouble() - 0.5) * 2;
            double vy = nature.vy + (rng.nextDouble() - 0.3) * 0.05;
            double vz = nature.vz * (rng.nextDouble() - 0.5) * 2;
            mc.level.addParticle(po, f.x + sx, f.y + sy, f.z + sz, vx, vy, vz);
        }
    }

    private void playSound(SoundEvent ev, float pitch, float vol) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && ev != null) mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
    }

    // ─────────── Rendu (léger : le monde reste visible) ───────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (phase == Phase.RESULT) {
            // Fond assombri progressif SEULEMENT au résultat (monde flouté à l'œil).
            float a = 0.62f;
            ctx.fillGradient(0, 0, this.width, this.height,
                Colors.withAlpha(0xFF000000, a * 0.7f), Colors.withAlpha(0xFF000000, a));
        } else {
            // Concentration/réaction : monde visible, juste un léger vignettage bas.
            ctx.fillGradient(0, this.height - 90, this.width, this.height,
                0x00000000, Colors.withAlpha(0xFF000000, 0.35f));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        int cx = this.width / 2;

        if (phase == Phase.CONCENTRATION || phase == Phase.REACTION) {
            // Texte discret, centré bas.
            String s = phase == Phase.CONCENTRATION ? "Concentration du chakra" : "La feuille réagit…";
            int dots = (int) ((now() / 350) % 4);
            Component t = RebornFont.arcade(s + "...".substring(0, phase == Phase.CONCENTRATION ? dots : 3));
            ctx.text(f, t, cx - f.width(t) / 2, this.height - 54, Colors.FOREGROUND, false);
            Component sub = RebornFont.arcade("Test de la feuille");
            ctx.text(f, sub, cx - f.width(sub) / 2, this.height - 40, Colors.withAlpha(Colors.GOLD, 0.7f), false);
        } else {
            drawResult(ctx, f, cx, mouseX, mouseY);
        }
    }

    private void drawResult(GuiGraphicsExtractor ctx, Font f, int cx, int mouseX, int mouseY) {
        Nature n = nature;
        int col = n.color;
        int cy = (int) (this.height * 0.46f);

        // Panneau central sobre.
        int pw = 300, ph = 250;
        int px = cx - pw / 2, py = cy - ph / 2;
        DrawHelpers.roundedOutlinedRect(ctx, px, py, pw, ph, 6,
            Colors.withAlpha(0xFF0B0709, 0.96f), Colors.withAlpha(col, 0.55f));
        // Liseré supérieur teinté nature.
        ctx.fill(px + 1, py + 1, px + pw - 1, py + 4, Colors.withAlpha(col, 0.8f));

        // « Vous avez obtenu… »
        Component top = RebornFont.arcade("VOUS AVEZ OBTENU");
        ctx.text(f, top, cx - f.width(top) / 2, py + 22, Colors.FOREGROUND_SUBTLE, false);

        // Kanji : icône custom si présente, sinon glyphe CJK (police vanilla).
        int kanjiY = py + 46;
        int kanjiBox = 108;
        if (n.kanjiTexExists()) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, n.kanjiTex, cx - kanjiBox / 2, kanjiY,
                0f, 0f, kanjiBox, kanjiBox, 256, 256, 256, 256, Colors.withAlpha(col, 1f));
        } else {
            // Glyphe kanji en gros via la police vanilla (supporte le CJK).
            drawBigGlyph(ctx, f, n.kanji, cx, kanjiY + kanjiBox / 2, 6.5f, col);
        }

        // Nom + élément.
        Component name = RebornFont.arcade(n.display + "  " + n.kanji);
        drawScaled(ctx, f, name, cx, py + 170, 1.7f, Colors.WHITE_PURE);
        Component elem = RebornFont.arcade(n.element);
        ctx.text(f, elem, cx - f.width(elem) / 2, py + 190, col, false);

        // Bouton Valider.
        int bw = 150, bh = 30;
        int bx = cx - bw / 2, by = py + ph - 40;
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, bw, bh, 6,
            hover ? Colors.withAlpha(Colors.SUCCESS, 0.28f) : Colors.withAlpha(0xFF000000, 0.5f),
            hover ? Colors.SUCCESS : Colors.withAlpha(Colors.FOREGROUND, 0.4f));
        Component v = RebornFont.arcade("VALIDER");
        ctx.text(f, v, cx - f.width(v) / 2, by + (bh - 8) / 2, Colors.WHITE_PURE, false);

        // Hint discret : refaire un test (dev).
        Component hint = RebornFont.arcade("F : refaire un test");
        ctx.text(f, hint, cx - f.width(hint) / 2, by + bh + 8, Colors.FOREGROUND_MUTED, false);
    }

    private int btnY() { return (int) (this.height * 0.46f) - 125 + 250 - 40; }

    private void drawBigGlyph(GuiGraphicsExtractor ctx, Font f, String glyph, int cx, int cy, float scale, int color) {
        Component c = Component.literal(glyph);
        int w = f.width(c);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(scale, scale);
        // Halo léger derrière (2 copies décalées, alpha bas) — cheap.
        ctx.text(f, c, -w / 2, -5, Colors.withAlpha(color, 0.25f), false);
        ctx.text(f, c, -w / 2, -4, color, false);
        ctx.pose().popMatrix();
    }

    private void drawScaled(GuiGraphicsExtractor ctx, Font f, Component c, int cx, int y, float scale, int color) {
        int w = f.width(c);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, y);
        ctx.pose().scale(scale, scale);
        ctx.text(f, c, -w / 2, 0, color, false);
        ctx.pose().popMatrix();
    }

    // ─────────── Interactions ───────────
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (phase == Phase.RESULT) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { onClose(); return true; }
            if (k == GLFW.GLFW_KEY_F) { RebornSounds.uiClick(); startTest(); return true; }
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (phase == Phase.RESULT) { onClose(); return true; }
            return true; // avale l'ESC pendant l'animation
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0 && phase == Phase.RESULT) {
            int mx = (int) e.x(), my = (int) e.y();
            int bw = 150, bh = 30, bx = this.width / 2 - bw / 2, by = btnY();
            if (mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
                RebornSounds.confirm();
                onClose();
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    // ─────────── Données natures ───────────
    enum Nature {
        KATON("Katon", "火", "Feu", 0xFFF97316,
            new ParticleOptions[]{ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME, ParticleTypes.LAVA, ParticleTypes.SMOKE},
            0.02, 0.08, 0.02),
        SUITON("Suiton", "水", "Eau", 0xFF38BDF8,
            new ParticleOptions[]{ParticleTypes.SPLASH, ParticleTypes.DRIPPING_WATER, ParticleTypes.BUBBLE},
            0.04, 0.02, 0.04),
        RAITON("Raiton", "雷", "Foudre", 0xFFFACC15,
            new ParticleOptions[]{ParticleTypes.ELECTRIC_SPARK, ParticleTypes.CRIT, ParticleTypes.WAX_OFF},
            0.10, 0.03, 0.10),
        FUTON("Fûton", "風", "Vent", 0xFF4ADE80,
            new ParticleOptions[]{ParticleTypes.CLOUD, ParticleTypes.SWEEP_ATTACK, ParticleTypes.GUST},
            0.12, 0.01, 0.12),
        DOTON("Doton", "土", "Terre", 0xFFB98A46,
            new ParticleOptions[]{ParticleTypes.LARGE_SMOKE, ParticleTypes.ASH, ParticleTypes.SMOKE},
            0.03, -0.02, 0.03);

        final String display, kanji, element;
        final int color;
        final ParticleOptions[] particles;
        final double vx, vy, vz;
        final Identifier kanjiTex;
        private Boolean texOk;

        Nature(String display, String kanji, String element, int color,
               ParticleOptions[] particles, double vx, double vy, double vz) {
            this.display = display; this.kanji = kanji; this.element = element; this.color = color;
            this.particles = particles; this.vx = vx; this.vy = vy; this.vz = vz;
            this.kanjiTex = Identifier.fromNamespaceAndPath("reborn",
                "textures/gacha/kanji/" + name().toLowerCase() + ".png");
        }

        boolean kanjiTexExists() {
            if (texOk != null) return texOk;
            Minecraft mc = Minecraft.getInstance();
            texOk = mc != null && mc.getResourceManager().getResource(kanjiTex).isPresent();
            return texOk;
        }
    }
}
