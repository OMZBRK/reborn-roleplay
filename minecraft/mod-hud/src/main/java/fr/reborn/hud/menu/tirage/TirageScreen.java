package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.NatureIcones;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <b>Test de la Feuille</b> (refait le 14/10) — on canalise son chakra dans une feuille de papier à chakra ; sa
 * réaction révèle la nature (canon) : <b>Katon</b> elle se consume, <b>Fūton</b> elle se fend en deux, <b>Raiton</b>
 * elle se froisse, <b>Doton</b> elle s'effrite, <b>Suiton</b> elle se détrempe. Double nature : la feuille se partage
 * en bandes, une réaction par nature.
 *
 * <p>Déroulé (serveur autoritaire, inchangé) : confirmation → le joueur fait ses signes de mains en vue de profil
 * (animation PAL {@link TirageAnimations}) → concentration (la vue se voile, la feuille apparaît au centre au-dessus de
 * son plateau, lueur de chakra) → réaction image par image → révélation : le <b>symbole canonique</b> de la nature
 * ({@link NatureIcones}, icônes Narutopedia, comme les blasons de clan), son nom, une phrase, « Valider ». Aucun kanji
 * décoratif (DA §3), écran centré et épuré ; pixel art léger (DA §6) : chaque image de la feuille est
 * une case d'atlas ({@code textures/gui/tirage/feuille_<nature>.png}, {@code tools/gen_tirage_textures.py}) posée en
 * un blit, le code ne fait que choisir la case, poser le voile, la lueur et le texte.
 *
 * <p>Canal {@code reborn:tirage} : {@code confirm|<clan>} ouvre la confirmation ; le client répond {@code go} ;
 * le serveur renvoie {@code <NATURE>|<clan>|<AUTRES>} ({@code AUTRES} = natures déjà éveillées, séparées par des
 * virgules, absent sur un ancien serveur) ou {@code deny}.
 */
public class TirageScreen extends Screen {

    // Durées (ms).
    private static final long MUDRA_MS = 1500;
    private static final long CONC_MS = 1500;
    private static final long FRAME_MS = 110;
    private static final int FRAMES = 16;
    private static final long REACTION_MS = FRAMES * FRAME_MS;
    private static final long TENUE_MS = 500;           // dernière image tenue avant la révélation
    private static final long MONTEE_MS = 420;          // la feuille remonte, la révélation apparaît
    private static final long ATTENTE_MAX_MS = 6000;    // attente du résultat serveur (sinon tirage local)

    private enum Phase { CONFIRM, MUDRA, CONCENTRATION, REACTION, RESULT }

    private Phase phase = Phase.MUDRA;
    private long phaseStart = now();
    private Nature nature;
    /** Natures déjà éveillées (double nature) : la feuille se partage en bandes, la nouvelle à droite. */
    private final List<Nature> autres = new ArrayList<>();
    private boolean reactionSoundPlayed;

    private final Random rng = new Random();

    // Caméra / HUD sauvegardés et restaurés à la fermeture.
    // Partagés entre écrans du test : un écran du test peut en remplacer un autre (confirmation → résultat serveur,
    // bancs) et le nouvel init peut passer avant l'ancien removed ; compter les détenteurs évite de « sauvegarder » la
    // caméra de profil ou le HUD masqué comme état d'origine.
    private static CameraType prevCam;
    private static RebornCamera.Mode prevMode;
    private static double prevCamYaw, prevCamPitch, prevRight, prevDistance;
    private static boolean prevHudHidden;
    private static int hudRefs, camRefs;
    private boolean hudCaptured = false;
    private boolean camCaptured = false;

    /** Angle de la caméra autour du perso : ¾ profil (montre le côté et les particules devant lui). */
    private static final float SIDE_YAW = 75f;

    /** Clan du perso (pilote la pondération du tirage local de démo). */
    private final String clan;
    /** Nature imposée par le serveur (autoritaire). null = roll local (démo). */
    private final String forcedNatureName;
    private boolean confirmMode = false;
    /** En attente du résultat serveur (après Confirmer) : le test tourne déjà, la nature arrive pendant les signes. */
    private boolean awaitingResult = false;
    /** Test piloté par le serveur (résultat forcé ou confirmé) → pas de reroll F. */
    private boolean serverDriven = false;
    /** Banc de captures : étape à afficher d'emblée (−1 = déroulé normal). */
    private int bancEtape = -1;
    private boolean initialise;

    /** Le serveur a répondu : fixe la nature de l'écran en cours. {@code clanEtAutres} = « clan|AUTRES » (facultatif). */
    public void applyServerResult(String natureName) { applyServerResult(natureName, null); }

    public void applyServerResult(String natureName, String clanEtAutres) {
        nature = Nature.fromName(natureName);
        lireAutres(clanEtAutres);
        awaitingResult = false;
    }

    public boolean isAwaiting() { return awaitingResult; }

    /** Démo client : roll local, va direct au test. */
    public TirageScreen() { this(null, null); }

    /** Déclenché par le serveur : nature déjà tirée ; {@code clan} peut porter « clan|AUTRES ». */
    public TirageScreen(String forcedNatureName, String clan) {
        super(Component.literal("Test de la feuille"));
        String c = clan;
        if (c != null && c.contains("|")) {
            lireAutres(c);
            c = c.substring(0, c.indexOf('|'));
        }
        this.forcedNatureName = forcedNatureName;
        this.clan = c == null || c.isBlank() ? null : c;
        this.serverDriven = forcedNatureName != null;
    }

    /** Popup « Faire le test ? » (Confirmer / Refuser) avant le test — item / commande. */
    public static TirageScreen confirm(String clan) {
        TirageScreen s = new TirageScreen(null, clan);
        s.confirmMode = true;
        return s;
    }

    /**
     * Banc de captures : la nature (et les autres, « SUITON,RAITON »), à l'étape 0 signes, 1 concentration,
     * 2 réaction (milieu), 3 résultat.
     */
    public static TirageScreen banc(String natureName, String autres, int etape) {
        TirageScreen s = new TirageScreen(natureName, autres == null ? null : "|" + autres);
        s.bancEtape = etape;
        return s;
    }

    private void lireAutres(String clanEtAutres) {
        if (clanEtAutres == null) return;
        int i = clanEtAutres.indexOf('|');
        if (i < 0) return;
        autres.clear();
        for (String p : clanEtAutres.substring(i + 1).split(",")) {
            Nature n = Nature.parse(p);
            if (n != null && !autres.contains(n) && autres.size() < 2) autres.add(n);
        }
    }

    private static long now() { return System.currentTimeMillis(); }
    private void setPhase(Phase p) { phase = p; phaseStart = now(); }
    private long ecoule() { return now() - phaseStart; }
    private float pt(long dur) { return Math.min(1f, ecoule() / (float) dur); }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!hudCaptured && mc.gui != null) {
            if (hudRefs++ == 0) prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            hudCaptured = true;
        }
        if (initialise) return;                 // redimensionnement : on garde l'état
        initialise = true;
        // La confirmation ne touche pas la caméra ; le profil se prend au démarrage du test.
        if (confirmMode) setPhase(Phase.CONFIRM);
        else if (nature == null) startTest();
        if (bancEtape >= 0) {
            awaitingResult = false;
            switch (bancEtape) {
                case 1 -> { setPhase(Phase.CONCENTRATION); phaseStart -= CONC_MS / 6; }
                case 2 -> { setPhase(Phase.REACTION); phaseStart -= FRAME_MS * 2; reactionSoundPlayed = true; }
                case 3 -> { setPhase(Phase.RESULT); phaseStart -= 5000; }
                default -> { }
            }
            bancEtape = -1;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) TirageAnimations.INSTANCE.stop(mc.player);
        if (camCaptured && --camRefs > 0) camCaptured = false;   // un autre écran du test garde la caméra
        if (camCaptured && mc.options != null) {
            RebornCamera cam = RebornCamera.INSTANCE;
            cam.setMode(prevMode);
            cam.setRight(prevRight);
            cam.setDistance(prevDistance);
            if (mc.player != null) cam.initOrientation(mc.player.getYRot(), mc.player.getXRot());
            else cam.initOrientation((float) prevCamYaw, (float) prevCamPitch);
            mc.options.setCameraType(prevCam);
            camCaptured = false;
        }
        if (hudCaptured && --hudRefs > 0) hudCaptured = false;
        if (hudCaptured && mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            hudCaptured = false;
        }
        super.removed();
    }

    /** Bascule en caméra de profil ¾ au démarrage du test (jamais pendant la confirmation). */
    private void enterCinematicCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (camCaptured || mc.options == null || mc.player == null) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        if (camRefs++ == 0) {
            prevCam = mc.options.getCameraType();
            prevMode = cam.mode();
            prevCamYaw = cam.camYaw();
            prevCamPitch = cam.camPitch();
            prevRight = cam.rightMagnitude();
            prevDistance = cam.distance();
        }
        camCaptured = true;
        applyProfileCamera(mc);
    }

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
        enterCinematicCamera();
        nature = forcedNatureName != null ? Nature.fromName(forcedNatureName) : rollNature(clan);
        reactionSoundPlayed = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) TirageAnimations.INSTANCE.playMudra(mc.player);
        setPhase(Phase.MUDRA);
        playSound(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.6f);
    }

    // ─────────── Tirage local (démo hors serveur), pondéré par clan ───────────
    static Nature rollNature(String clan) {
        Nature[] ordre = {Nature.KATON, Nature.SUITON, Nature.RAITON, Nature.FUTON, Nature.DOTON};
        double[] w = {20, 20, 20, 20, 20};
        int fav = clanFavored(clan);
        if (fav >= 0) {
            for (int i = 0; i < w.length; i++) w[i] -= 7.5;
            w[fav] += 30 + 7.5;
        }
        double total = 0;
        for (double v : w) total += Math.max(0, v);
        double x = new Random().nextDouble() * total, acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += Math.max(0, w[i]);
            if (x < acc) return ordre[i];
        }
        return Nature.KATON;
    }

    private static int clanFavored(String clan) {
        if (clan == null) return -1;
        String c = clan.toLowerCase();
        if (c.contains("uchiha") || c.contains("sarutobi")) return 0;
        if (c.contains("hozuki") || c.contains("hōzuki") || c.contains("senju")) return 1;
        if (c.contains("yotsuki") || c.contains("kaminari")) return 2;
        if (c.contains("kaguya") || c.contains("kazekage") || c.contains("sabaku")) return 3;
        if (c.contains("kamizuru") || c.contains("iwa")) return 4;
        return -1;
    }

    // ─────────── Boucle temporelle ───────────
    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (camCaptured) applyProfileCamera(mc);
        Vec3 focus = focusPoint(p);
        switch (phase) {
            case MUDRA -> { if (pt(MUDRA_MS) >= 1f) setPhase(Phase.CONCENTRATION); }
            case CONCENTRATION -> {
                spawnConcentration(mc, focus, pt(CONC_MS));
                // on attend la nature du serveur avant de faire réagir la feuille (sinon tirage local)
                boolean attendre = awaitingResult && ecoule() < ATTENTE_MAX_MS;
                if (pt(CONC_MS) >= 1f && !attendre) setPhase(Phase.REACTION);
            }
            case REACTION -> {
                if (!reactionSoundPlayed) {
                    reactionSoundPlayed = true;
                    playSound(nature.son, 1.0f, 0.8f);
                }
                if (ecoule() < REACTION_MS) spawnReaction(mc, focus);
                if (ecoule() >= REACTION_MS + TENUE_MS) {
                    setPhase(Phase.RESULT);
                    TirageAnimations.INSTANCE.stop(p);
                    playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f);
                }
            }
            default -> { }
        }
    }

    private Vec3 focusPoint(LocalPlayer p) {
        Vec3 look = p.getLookAngle();
        double hx = look.x, hz = look.z;
        double n = Math.sqrt(hx * hx + hz * hz);
        if (n > 1e-4) { hx /= n; hz /= n; }
        return new Vec3(p.getX() + hx * 0.85, p.getY() + 1.2, p.getZ() + hz * 0.85);
    }

    // ─────────── Particules in-world (vanilla, GPU) ───────────
    private void spawnConcentration(Minecraft mc, Vec3 f, float t) {
        int count = 3 + (int) (t * 4);
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

    private void spawnReaction(Minecraft mc, Vec3 f) {
        ParticleOptions[] set = nature.particles;
        for (int i = 0; i < 2; i++) {
            ParticleOptions po = set[rng.nextInt(set.length)];
            double sx = (rng.nextDouble() - 0.5) * 0.5, sy = (rng.nextDouble() - 0.5) * 0.5, sz = (rng.nextDouble() - 0.5) * 0.5;
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

    // ─────────── Rendu ───────────
    private static Identifier tex(String n) { return DaKit.tex("tirage", n); }

    private static final Identifier FEUILLE = tex("feuille"), HALO = tex("halo"), BOUTON = tex("bouton");
    private static final int CW = 96, CH = 112, ATLAS_W = 768, ATLAS_H = 224;
    private static final int NUIT = 0xFF0A0608;
    private static final int BW = 96, BH = 16;

    /** Opacité du voile de nuit selon la phase (le monde reste visible derrière). */
    private float voile() {
        return switch (phase) {
            case CONFIRM -> 0.5f;
            case MUDRA -> 0f;
            case CONCENTRATION -> 0.62f * DaKit_lisse(pt(500));
            default -> 0.62f;
        };
    }

    private static float DaKit_lisse(float x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float v = voile();
        if (v <= 0.001f) {
            // signes de mains : monde visible, léger assombrissement en bas pour la légende
            ctx.fillGradient(0, this.height - 70, this.width, this.height, 0x00000000, DaKit.alpha(NUIT, 0.35f));
            return;
        }
        ctx.fill(0, 0, this.width, this.height, DaKit.alpha(NUIT, v));
        // mise au point : ombre douce derrière la colonne (texture lisse, un seul blit)
        int r = Math.min(this.height, 420);
        DaKit.etire(ctx, HALO, this.width / 2 - r * 3 / 4, this.height / 2 - r / 2 - 10, r * 3 / 2, r, 64, 64,
            DaKit.alpha(NUIT, Math.min(1f, v * 0.9f)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        int cx = this.width / 2;
        switch (phase) {
            case CONFIRM -> drawConfirm(ctx, f, cx, mouseX, mouseY);
            case MUDRA -> legendeBas(ctx, f, cx, "Signes de mains");
            case CONCENTRATION -> drawTest(ctx, f, cx);
            case REACTION -> drawTest(ctx, f, cx);
            case RESULT -> drawResult(ctx, f, cx, mouseX, mouseY);
        }
    }

    /** Légende discrète en bas (règle 9) pendant les signes de mains. */
    private void legendeBas(GuiGraphicsExtractor ctx, Font f, int cx, String t) {
        int d = (int) ((now() / 350) % 4);
        Component c = DaKit.inter(t);
        DaKit.inter9o(ctx, f, DaKit.inter(t + "...".substring(0, d)), cx - f.width(c) / 2f, this.height - 40, DaKit.CREME, 0);
        DaKit.arcFin(ctx, f, DaKit.arcade("Test de la Feuille"), cx, this.height - 30, DaKit.alpha(DaKit.OR, 0.8f), 1, true);
    }

    /** Haut de la case de la feuille pendant le test (centrée, un peu au-dessus du milieu). */
    private int feuilleYTest() { return this.height / 2 - CH / 2 - 14; }

    // ── confirmation ──
    private int confirmTop() { return Math.max(8, this.height / 2 - 128); }
    private int boutonY() { return confirmTop() + CH + 62; }
    private int confirmerX() { return this.width / 2 - BW - 6; }
    private int refuserX() { return this.width / 2 + 18; }
    private int refuserW() { return this.font.width(DaKit.inter("Refuser")) + 8; }

    private void drawConfirm(GuiGraphicsExtractor ctx, Font f, int cx, int mouseX, int mouseY) {
        int top = confirmTop();
        lueur(ctx, cx, top + 56, 150, 0x8FC8FF, 0.16f + 0.05f * (float) Math.sin(now() / 420.0));
        DaKit.blit(ctx, FEUILLE, cx - CW / 2, top + flotte(), 0, 0, CW, CH, CW, CH);
        int y = top + CH + 10;
        DaKit.arcX1(ctx, f, DaKit.arcade("Test de la Feuille"), cx, y, DaKit.OR_CLAIR, 1, true);
        ReglagesArt.filet(ctx, cx - 70, cx + 70, y + 12);
        DaKit.inter9o(ctx, f, DaKit.inter("Canalise ton chakra dans la feuille pour révéler ta nature."), cx, y + 26,
            DaKit.CREME_VAL, 1);
        int by = boutonY();
        boolean h1 = DaKit.dans(mouseX, mouseY, confirmerX(), by, BW, BH);
        boolean h2 = DaKit.dans(mouseX, mouseY, refuserX() - 4, by, refuserW(), BH);
        plaque(ctx, f, confirmerX(), by, "Confirmer", h1);
        int rw = DaKit.inter9o(ctx, f, DaKit.inter("Refuser"), refuserX(), by + BH / 2f, h2 ? DaKit.CREME : DaKit.DOUX, 0);
        if (h2) ctx.fill(refuserX(), by + BH / 2 + 6, refuserX() + rw, by + BH / 2 + 7, DaKit.OR);
        DaKit.inter7o(ctx, f, DaKit.inter("Entrée : confirmer  ·  Échap : refuser"), cx, by + BH + 12,
            DaKit.alpha(DaKit.DOUX, 0.75f), 1);
    }

    /** Plaque laquée fine (repos / survol) + libellé centré. */
    private void plaque(GuiGraphicsExtractor ctx, Font f, int x, int y, String txt, boolean survol) {
        DaKit.blit(ctx, BOUTON, x - 1, y - 1, 0, survol ? 20 : 0, BW + 4, 20, 100, 40);
        DaKit.inter9(ctx, f, DaKit.inter(txt), x + BW / 2f, y + BH / 2f, survol ? DaKit.OR_CLAIR : DaKit.CREME, 1);
    }

    /** Légère lévitation de la feuille (1 GUI). */
    private int flotte() { return (int) Math.round(Math.sin(now() / 600.0)); }

    /** Lueur douce teintée (halo.png étiré : une texture lisse, un blit). */
    private void lueur(GuiGraphicsExtractor ctx, int cx, int cy, int r, int rgb, float a) {
        if (a <= 0.003f) return;
        DaKit.etire(ctx, HALO, cx - r / 2, cy - r / 2, r, r, 64, 64, DaKit.argb(rgb, Math.min(1f, a)));
    }

    // ── la feuille (une case d'atlas par nature, en bandes si plusieurs natures) ──
    private void feuille(GuiGraphicsExtractor ctx, int x, int y, int image) {
        if (image < 0) {
            DaKit.blit(ctx, FEUILLE, x, y, 0, 0, CW, CH, CW, CH);
            return;
        }
        int u = (image % 8) * CW, v = (image / 8) * CH;
        int n = 1 + autres.size();
        for (int i = 0; i < n; i++) {
            Nature nat = i < autres.size() ? autres.get(i) : nature;
            int x0 = CW * i / n, x1 = CW * (i + 1) / n;
            DaKit.blit(ctx, nat.atlas, x + x0, y, u + x0, v, x1 - x0, CH, ATLAS_W, ATLAS_H);
        }
    }

    private void drawTest(GuiGraphicsExtractor ctx, Font f, int cx) {
        int y = feuilleYTest();
        if (phase == Phase.CONCENTRATION) {
            float a = DaKit_lisse(pt(600));
            float t = pt(CONC_MS);
            lueur(ctx, cx, y + 56, 120 + (int) (30 * t), 0x8FC8FF, a * (0.18f + 0.12f * t + 0.06f * (float) Math.sin(now() / 140.0)));
            int dy = Math.round((1 - a) * 8);
            int tremble = t > 0.5f ? (int) ((now() / 60) % 3) - 1 : 0;
            DaKit.blit(ctx, FEUILLE, cx - CW / 2 + tremble, y + dy + flotte(), 0, 0, CW, CH, CW, CH, DaKit.blanc(a));
            legende(ctx, f, cx, y + CH + 14, awaitingResult && t >= 1f ? "La feuille attend ton chakra" : "Concentration du chakra", a);
        } else {
            int img = (int) Math.min(FRAMES - 1, ecoule() / FRAME_MS);
            float e = Math.min(1f, ecoule() / (float) REACTION_MS);
            lueur(ctx, cx, y + 56, 170, nature.couleur & 0xFFFFFF, 0.34f * (1 - e * 0.6f));
            for (Nature o : autres) lueur(ctx, cx, y + 56, 130, o.couleur & 0xFFFFFF, 0.12f * (1 - e * 0.6f));
            feuille(ctx, cx - CW / 2, y, img);
            legende(ctx, f, cx, y + CH + 14, "La feuille réagit", 1f);
        }
    }

    private void legende(GuiGraphicsExtractor ctx, Font f, int cx, int y, String t, float a) {
        int d = (int) ((now() / 350) % 4);
        Component c = DaKit.inter(t);
        DaKit.inter8(ctx, f, DaKit.inter(t + "...".substring(0, d)), cx - DaKit.largeur8(f, c) / 2f, y,
            DaKit.alpha(DaKit.DOUX, a), 0);
    }

    // ── révélation ──
    private static final int ICONE = 64;

    /** Haut de la colonne du résultat (feuille, icône, nom, phrase, bouton), centrée. */
    private int resultTop() {
        int h = CH + 4 + ICONE + 10 + 14 + 6 + 9 + 10 + 10 + (autres.isEmpty() ? 0 : 18) + 16 + BH + 18;
        return Math.max(4, (this.height - h) / 2);
    }

    private int btnY() {
        return resultTop() + CH + 4 + ICONE + 10 + 14 + 6 + 9 + 10 + 10 + (autres.isEmpty() ? 0 : 18) + 16;
    }

    private void drawResult(GuiGraphicsExtractor ctx, Font f, int cx, int mouseX, int mouseY) {
        Nature n = nature;
        float m = DaKit_lisse(pt(MONTEE_MS));
        int top = resultTop();
        int fy = Math.round(feuilleYTest() + (top - feuilleYTest()) * m);
        feuille(ctx, cx - CW / 2, fy, FRAMES - 1);
        // révélation en fondu, de haut en bas
        float a1 = DaKit_lisse((ecoule() - 150) / 350f), a2 = DaKit_lisse((ecoule() - 320) / 350f),
            a3 = DaKit_lisse((ecoule() - 480) / 350f);
        int y = top + CH + 4;
        lueur(ctx, cx, y + ICONE / 2, 150, n.couleur & 0xFFFFFF, 0.26f * a1);
        if (a1 > 0.01f) NatureIcones.dessiner(ctx, n.icone, cx - ICONE / 2, y + Math.round((1 - a1) * 4), ICONE,
            DaKit.blanc(a1), now());
        y += ICONE + 10;
        if (a2 > 0.01f) {
            Component nom = DaKit.arcade(n.icone.nom);
            float w = f.width(nom) * 2f - 2;
            DaKit.texteEchelle(ctx, f, nom, cx - w / 2f + 1, y + 1, DaKit.alpha(0xFF0A0406, a2), 2f);
            DaKit.texteEchelle(ctx, f, nom, cx - w / 2f, y, DaKit.alpha(DaKit.CREME, a2), 2f);
            y += 14 + 6;
            DaKit.inter9o(ctx, f, DaKit.inter(n.icone.element), cx, y + 4, DaKit.alpha(0xFF000000 | n.icone.couleur, a2), 1);
            y += 9 + 10;
        } else {
            y += 14 + 6 + 9 + 10;
        }
        if (a3 > 0.01f) {
            DaKit.inter8(ctx, f, DaKit.inter(n.phrase), cx - DaKit.largeur8(f, DaKit.inter(n.phrase)) / 2f, y + 4,
                DaKit.alpha(DaKit.CREME_VAL, a3), 0);
            if (!autres.isEmpty()) {
                // les natures déjà éveillées : petites icônes + noms, sur une ligne
                y += 18;
                Component lib = DaKit.inter(autres.size() > 1 ? "Ton chakra porte aussi :" : "Ton chakra porte aussi le");
                float lw = DaKit.largeur7(f, lib);
                float total = lw + 4;
                for (Nature o : autres) total += 14 + DaKit.largeur7(f, DaKit.inter(o.icone.nom)) + 8;
                float x = cx - total / 2f;
                DaKit.inter7o(ctx, f, lib, x, y + 4, DaKit.alpha(DaKit.DOUX, a3), 0);
                x += lw + 4;
                for (Nature o : autres) {
                    NatureIcones.dessiner(ctx, o.icone, Math.round(x), y - 2, 12, DaKit.blanc(a3), now());
                    x += 14;
                    x += DaKit.inter7o(ctx, f, DaKit.inter(o.icone.nom), x, y + 4, DaKit.alpha(0xFF000000 | o.icone.couleur, a3), 0) + 8;
                }
            }
            int by = btnY();
            int bx = cx - BW / 2;
            boolean hover = DaKit.dans(mouseX, mouseY, bx, by, BW, BH);
            plaque(ctx, f, bx, by, "Valider", hover);
            DaKit.inter7o(ctx, f, DaKit.inter(serverDriven ? "Entrée : valider" : "Entrée : valider  ·  F : refaire"), cx,
                by + BH + 10, DaKit.alpha(DaKit.DOUX, 0.7f * a3), 1);
        }
    }

    // ─────────── Interactions ───────────
    private void confirmAccept() {
        RebornSounds.confirm();
        confirmMode = false;
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(TiragePayload.ID)) {
            // On enchaîne sans fermer l'écran (la caméra reste en profil) ; la nature du serveur arrive pendant les
            // signes de mains (applyServerResult) et la feuille l'attend avant de réagir.
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new TiragePayload("go"));
            awaitingResult = true;
            serverDriven = true;
        }
        startTest();
    }

    private void confirmRefuse() {
        RebornSounds.uiClick();
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (phase == Phase.CONFIRM) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { confirmAccept(); return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { confirmRefuse(); return true; }
            return true;
        }
        if (phase == Phase.RESULT) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER || k == GLFW.GLFW_KEY_ESCAPE) {
                RebornSounds.confirm();
                onClose();
                return true;
            }
            if (k == GLFW.GLFW_KEY_F && !serverDriven) { RebornSounds.uiClick(); startTest(); return true; }
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) return true;   // avale l'Échap pendant l'animation
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            double mx = e.x(), my = e.y();
            if (phase == Phase.CONFIRM) {
                if (DaKit.dans(mx, my, confirmerX(), boutonY(), BW, BH)) { confirmAccept(); return true; }
                if (DaKit.dans(mx, my, refuserX() - 4, boutonY(), refuserW(), BH)) { confirmRefuse(); return true; }
                return true;
            }
            if (phase == Phase.RESULT && ecoule() > 480) {
                if (DaKit.dans(mx, my, this.width / 2 - BW / 2, btnY(), BW, BH)) {
                    RebornSounds.confirm();
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    // ─────────── Natures ───────────
    enum Nature {
        KATON(NatureIcones.Nature.KATON, "La feuille s'est consumée : ton chakra brûle comme une flamme.",
            SoundEvents.FIRECHARGE_USE,
            new ParticleOptions[]{ParticleTypes.FLAME, ParticleTypes.SMALL_FLAME, ParticleTypes.SMOKE},
            0.02, 0.08, 0.02),
        SUITON(NatureIcones.Nature.SUITON, "La feuille s'est détrempée : ton chakra coule comme l'eau.",
            SoundEvents.BUCKET_EMPTY,
            new ParticleOptions[]{ParticleTypes.SPLASH, ParticleTypes.DRIPPING_WATER, ParticleTypes.BUBBLE},
            0.04, 0.02, 0.04),
        RAITON(NatureIcones.Nature.RAITON, "La feuille s'est froissée : ton chakra crépite comme la foudre.",
            SoundEvents.FIREWORK_ROCKET_TWINKLE,
            new ParticleOptions[]{ParticleTypes.ELECTRIC_SPARK, ParticleTypes.CRIT, ParticleTypes.WAX_OFF},
            0.10, 0.03, 0.10),
        FUTON(NatureIcones.Nature.FUTON, "La feuille s'est fendue en deux : ton chakra tranche comme le vent.",
            SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
            new ParticleOptions[]{ParticleTypes.WHITE_ASH},
            0.12, 0.01, 0.12),
        DOTON(NatureIcones.Nature.DOTON, "La feuille s'est effritée : ton chakra est solide comme la terre.",
            SoundEvents.GRAVEL_BREAK,
            new ParticleOptions[]{ParticleTypes.ASH, ParticleTypes.SMOKE},
            0.03, -0.02, 0.03);

        final NatureIcones.Nature icone;
        final String phrase;
        final SoundEvent son;
        final ParticleOptions[] particles;
        final double vx, vy, vz;
        final Identifier atlas;
        final int couleur;

        Nature(NatureIcones.Nature icone, String phrase, SoundEvent son, ParticleOptions[] particles,
               double vx, double vy, double vz) {
            this.icone = icone;
            this.phrase = phrase;
            this.son = son;
            this.particles = particles;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.atlas = tex("feuille_" + icone.slug);
            this.couleur = icone.couleur;
        }

        static Nature parse(String s) {
            NatureIcones.Nature n = NatureIcones.parse(s);
            return n == null ? null : valueOf(n.name());
        }

        /** KATON / SUITON / RAITON / FUTON / DOTON (casse et accents indifférents), Katon par défaut. */
        static Nature fromName(String s) {
            Nature n = parse(s);
            return n == null ? KATON : n;
        }
    }
}
