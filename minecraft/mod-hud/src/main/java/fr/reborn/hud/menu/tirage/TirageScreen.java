package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
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
 * <p>Confirmation et résultat au niveau de la fiche shinobi (DA §4.1, §6), signature : la <b>feuille de papier à
 * chakra</b> sur son sanbō, puis réagie selon la nature ({@code textures/gui/tirage/*}).
 *
 * <p>Rendu <b>subtil + in-world</b> (fidèle à à l'animé), pas de gros
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
    private static final long MUDRA_MS = 1500;        // signe de mains (anim 1.5 s)
    private static final long CONCENTRATION_MS = 1700;
    private static final long REACTION_MS = 1300;

    private enum Phase { CONFIRM, MUDRA, CONCENTRATION, REACTION, RESULT }

    private Phase phase = Phase.MUDRA;
    private long phaseStart = now();
    private Nature nature;
    private boolean reactionSoundPlayed;

    private final Random rng = new Random();

    // Caméra / HUD sauvegardés et restaurés à la fermeture.
    private CameraType prevCam;
    private RebornCamera.Mode prevMode;
    private double prevCamYaw, prevCamPitch, prevRight, prevDistance;
    private boolean prevHudHidden;
    private boolean hudCaptured = false;
    private boolean camCaptured = false;

    /** Angle de la caméra autour du perso : ¾ profil (montre le côté + les
     *  particules devant lui, contrairement à la vue de dos qui les cache). */
    private static final float SIDE_YAW = 75f;

    /** Clan du perso (pilote la pondération). null = uniforme (démo client). */
    private final String clan;
    /** Nature imposée par le serveur (autoritaire). null = roll local (démo F). */
    private final String forcedNatureName;
    /** Ouvre d'abord le popup de confirmation (déclenché par l'item/commande). */
    private boolean confirmMode = false;
    /** En attente du résultat serveur (après Confirmer) — le test tourne déjà,
     *  la nature définitive est injectée à l'arrivée du message (sans rouvrir
     *  d'écran → aucun aller-retour caméra). */
    private boolean awaitingResult = false;
    /** Test piloté par le serveur (résultat forcé ou confirmé) → pas de reroll F. */
    private boolean serverDriven = false;

    /** Le serveur a répondu : fixe la nature de l'écran en cours. */
    public void applyServerResult(String natureName) {
        nature = Nature.fromName(natureName);
        awaitingResult = false;
    }

    /** L'écran attend-il un résultat serveur (transition depuis Confirmer) ? */
    public boolean isAwaiting() { return awaitingResult; }

    /** Démo client (touche F) : roll local, va direct au test. */
    public TirageScreen() { this(null, null); }

    /** Déclenché par le serveur : nature déjà tirée + clan pour le contexte. */
    public TirageScreen(String forcedNatureName, String clan) {
        super(Component.literal("Test de la feuille"));
        this.forcedNatureName = forcedNatureName;
        this.clan = clan;
        this.serverDriven = forcedNatureName != null;
    }

    /** Popup « Faire le test ? » (Confirmer/Refuser) avant le test — item/commande. */
    public static TirageScreen confirm(String clan) {
        TirageScreen s = new TirageScreen(null, clan);
        s.confirmMode = true;
        return s;
    }

    private static long now() { return System.currentTimeMillis(); }
    private void setPhase(Phase p) { phase = p; phaseStart = now(); }
    private float pt(long dur) { return Math.min(1f, (now() - phaseStart) / (float) dur); }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        // Masque le HUD vanilla dès l'ouverture (confirm ou test).
        if (!hudCaptured && mc.gui != null) {
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            hudCaptured = true;
        }
        // La fenêtre de confirmation NE TOUCHE PAS la caméra (vue normale). Le
        // passage en profil se fait seulement au démarrage du test (comme F).
        if (confirmMode) setPhase(Phase.CONFIRM);
        else startTest();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) TirageAnimations.INSTANCE.stop(mc.player);
        if (camCaptured && mc.options != null) {
            RebornCamera cam = RebornCamera.INSTANCE;
            cam.setMode(prevMode);
            cam.setRight(prevRight);
            cam.setDistance(prevDistance);
            // Réaligne la caméra derrière le joueur pour une sortie sans à-coup.
            if (mc.player != null) cam.initOrientation(mc.player.getYRot(), mc.player.getXRot());
            else cam.initOrientation((float) prevCamYaw, (float) prevCamPitch);
            mc.options.setCameraType(prevCam);
            camCaptured = false;
        }
        if (hudCaptured && mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            hudCaptured = false;
        }
        super.removed();
    }

    /** Bascule en caméra cinématique (profil ¾) — appelé au démarrage du test,
     *  jamais pendant la confirmation. Sauvegarde l'état pour la restauration. */
    private void enterCinematicCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (camCaptured || mc.options == null || mc.player == null) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        prevCam = mc.options.getCameraType();
        prevMode = cam.mode();
        prevCamYaw = cam.camYaw();
        prevCamPitch = cam.camPitch();
        prevRight = cam.rightMagnitude();
        prevDistance = cam.distance();
        camCaptured = true;
        applyProfileCamera(mc);
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
        enterCinematicCamera();  // passage en profil ICI (comme F), pas à la confirmation
        nature = forcedNatureName != null ? Nature.fromName(forcedNatureName) : rollNature(clan);
        reactionSoundPlayed = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) TirageAnimations.INSTANCE.playMudra(mc.player); // signe de mains (hold)
        setPhase(Phase.MUDRA);
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
        if (camCaptured) applyProfileCamera(mc); // tient le profil (pas pendant la confirmation)
        Vec3 focus = focusPoint(p);

        switch (phase) {
            case MUDRA -> { if (pt(MUDRA_MS) >= 1f) setPhase(Phase.CONCENTRATION); }
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
                    TirageAnimations.INSTANCE.stop(p);  // relâche le sceau à la révélation
                    playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
                }
            }
            default -> { }  // CONFIRM, RESULT : pas de logique temporelle
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
            // Fond assombri SEULEMENT au résultat (monde flouté à l'œil).
            float a = 0.62f;
            ctx.fillGradient(0, 0, this.width, this.height,
                Colors.withAlpha(0xFF000000, a * 0.7f), Colors.withAlpha(0xFF000000, a));
        } else if (phase == Phase.CONFIRM) {
            // Modale de confirmation : assombrissement moyen (perso visible derrière).
            ctx.fillGradient(0, 0, this.width, this.height,
                Colors.withAlpha(0xFF000000, 0.32f), Colors.withAlpha(0xFF000000, 0.46f));
        } else {
            // Signes / concentration / réaction : monde visible, léger vignettage bas.
            ctx.fillGradient(0, this.height - 90, this.width, this.height,
                0x00000000, Colors.withAlpha(0xFF000000, 0.35f));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        int cx = this.width / 2;

        if (phase == Phase.CONFIRM) {
            drawConfirm(ctx, f, cx, mouseX, mouseY);
        } else if (phase == Phase.RESULT) {
            drawResult(ctx, f, cx, mouseX, mouseY);
        } else {
            // MUDRA / CONCENTRATION / REACTION : texte discret, centré bas.
            String t = switch (phase) {
                case MUDRA -> "Signes de mains";
                case CONCENTRATION -> "Concentration du chakra";
                default -> "La feuille réagit";
            };
            int d = (int) ((now() / 350) % 4);
            DaKit.inter9o(ctx, f, DaKit.inter(t + "...".substring(0, d)), cx - f.width(DaKit.inter(t)) / 2f, this.height - 46,
                DaKit.CREME, 0);
            DaKit.arcFin(ctx, f, DaKit.arcade("Test de la Feuille"), cx, this.height - 36, DaKit.alpha(DaKit.OR, 0.8f), 1, true);
        }
    }

    // ─────────── Popup de confirmation : la feuille sur son sanbō ───────────
    private static Identifier tex(String n) { return DaKit.tex("tirage", n); }

    private static final Identifier CONFIRMATION = tex("confirmation"), RESULTAT = tex("resultat"), FEUILLES = tex("feuilles");
    private static final int CB_W = 90, CB_H = 20;
    private int confirmPy() { return (int) (this.height * 0.46f) - 60; }
    private int confirmY() { return confirmPy() + 82; }
    private int confirmerX() { return this.width / 2 - 150 + 92; }
    private int refuserX() { return this.width / 2 - 150 + 92 + CB_W + 10; }

    private void drawConfirm(GuiGraphicsExtractor ctx, Font f, int cx, int mouseX, int mouseY) {
        int px = cx - 150, py = confirmPy();
        DaKit.blit(ctx, CONFIRMATION, px - 2, py - 2, 0, 0, 308, 128, 308, 128);
        DaKit.arcX1(ctx, f, DaKit.arcade("Test de la Feuille"), px + 92, py + 20, DaKit.OR_CLAIR, 0, true);
        DaKit.inter8(ctx, f, DaKit.inter("Canalise ton chakra dans la feuille"), px + 92, py + 42, DaKit.CREME_VAL, 0);
        DaKit.inter8(ctx, f, DaKit.inter("pour révéler ta nature ?"), px + 92, py + 54, DaKit.CREME_VAL, 0);
        boolean h1 = DaKit.dans(mouseX, mouseY, confirmerX(), confirmY(), CB_W, CB_H);
        boolean h2 = DaKit.dans(mouseX, mouseY, refuserX(), confirmY(), CB_W, CB_H);
        fr.reborn.hud.menu.settings.ReglagesArt.bouton(ctx, confirmerX(), confirmY(), CB_W, 3);
        fr.reborn.hud.menu.settings.ReglagesArt.bouton(ctx, refuserX(), confirmY(), CB_W, h2 ? 1 : 0);
        DaKit.inter9(ctx, f, DaKit.inter("Confirmer"), confirmerX() + CB_W / 2f, confirmY() + 10, h1 ? DaKit.CREME : DaKit.CREME_VAL, 1);
        DaKit.inter9(ctx, f, DaKit.inter("Refuser"), refuserX() + CB_W / 2f, confirmY() + 10, h2 ? DaKit.CREME : DaKit.CREME_VAL, 1);
    }

    private void confirmAccept() {
        RebornSounds.confirm();
        confirmMode = false;
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(TiragePayload.ID)) {
            // On enchaîne SANS fermer l'écran (la caméra reste en profil) : le mudra
            // + la concentration démarrent tout de suite, et la nature définitive du
            // serveur est injectée via applyServerResult pendant l'animation.
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new TiragePayload("go"));
            awaitingResult = true;
            serverDriven = true;
        }
        startTest();  // hors serveur : roll local (démo)
    }

    private void confirmRefuse() {
        RebornSounds.uiClick();
        onClose();
    }

    private int resultPy() { return (int) (this.height * 0.46f) - 98; }

    private void drawResult(GuiGraphicsExtractor ctx, Font f, int cx, int mouseX, int mouseY) {
        Nature n = nature;
        int col = n.color;
        int px = cx - 116, py = resultPy();
        DaKit.blit(ctx, RESULTAT, px - 2, py - 2, 0, 0, 240, 204, 240, 204);
        DaKit.arcX1(ctx, f, DaKit.arcade("Ta nature"), cx, py + 12, DaKit.OR_ETIQ, 1, false);

        // la feuille a réagi (à gauche) ; le kanji de la nature (à droite)
        DaKit.blit(ctx, FEUILLES, cx - 66, py + 28, n.ordinal() * 48, 0, 44, 56, 240, 56);
        if (n.kanjiTexExists()) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, n.kanjiTex, cx + 6, py + 32, 0f, 0f, 48, 48, 256, 256, 256, 256, col);
        } else {
            drawBigGlyph(ctx, f, n.kanji, cx + 30, py + 60, 4f, col);
        }

        DaKit.arcX1(ctx, f, DaKit.arcade(n.display), cx, py + 98, DaKit.CREME, 1, true);
        DaKit.inter9(ctx, f, DaKit.inter(n.element), cx, py + 118, col, 1);

        int bw = 120, bx = cx - bw / 2, by = btnY();
        boolean hover = DaKit.dans(mouseX, mouseY, bx, by, bw, 20);
        fr.reborn.hud.menu.settings.ReglagesArt.bouton(ctx, bx, by, bw, 3);
        DaKit.inter9(ctx, f, DaKit.inter("Valider"), cx, by + 10, hover ? DaKit.CREME : DaKit.CREME_VAL, 1);
        DaKit.inter7(ctx, f, DaKit.inter(serverDriven ? "Entrée : valider" : "F : refaire un test"), cx, by + 32,
            DaKit.argb(0xC2B59A, 0.7f), 1);
    }

    private int btnY() { return resultPy() + 140; }

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
        if (phase == Phase.CONFIRM) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { confirmAccept(); return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { confirmRefuse(); return true; }
            return true;
        }
        if (phase == Phase.RESULT) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { onClose(); return true; }
            if (k == GLFW.GLFW_KEY_F && !serverDriven) { RebornSounds.uiClick(); startTest(); return true; }
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (phase == Phase.RESULT) { onClose(); return true; }
            return true; // avale l'ESC pendant l'animation
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            int mx = (int) e.x(), my = (int) e.y();
            if (phase == Phase.CONFIRM) {
                if (mx >= confirmerX() && mx < confirmerX() + CB_W && my >= confirmY() && my < confirmY() + CB_H) {
                    confirmAccept(); return true;
                }
                if (mx >= refuserX() && mx < refuserX() + CB_W && my >= confirmY() && my < confirmY() + CB_H) {
                    confirmRefuse(); return true;
                }
                return true;
            }
            if (phase == Phase.RESULT) {
                int bw = 120, bx = this.width / 2 - bw / 2, by = btnY();
                if (mx >= bx && mx < bx + bw && my >= by && my < by + 20) {
                    RebornSounds.confirm();
                    onClose();
                    return true;
                }
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

        /** Mappe un nom (KATON/SUITON/RAITON/FUTON/DOTON, insensible à la casse). */
        static Nature fromName(String s) {
            if (s == null) return KATON;
            try { return Nature.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); }
            catch (Exception e) { return KATON; }
        }
    }
}
