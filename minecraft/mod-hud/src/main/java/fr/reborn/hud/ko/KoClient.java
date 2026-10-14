package fr.reborn.hud.ko;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * État client du KO / de l'ATA (alimenté par {@link KoPayload} et {@link KoEventPayload}, lot KO-6).
 *
 * <p>Trois couches :
 * <ul>
 *   <li>le filtre plein écran {@code reborn-hud:ko} ({@code shaders/post/ko.fsh}), posé par
 *       {@link fr.reborn.hud.effets.FiltresEcran} et prioritaire sur tous les autres : à terre (agonie), le monde se
 *       désature et une vignette <b>sombre</b> bat au rythme du cœur ; inconscient, noir et blanc, flou, paupières
 *       lourdes ; en <b>douleur</b> (PV sous 5 %), légère désaturation, bords assombris et vision qui tremble à
 *       peine ; en ATA, une pulsation légère. Plus de voile rouge sur les bords (rejeté, DA §3, 14/10) ; épuisement
 *       de chakra = bords d'un bleu nuit. Ses paramètres passent par une petite texture dynamique ({@link #PARAMS},
 *       4x1), comme la météo ;</li>
 *   <li>l'affichage dans la DA Reborn ({@link KoHud}) qui remplace titres, barre d'action et barre de boss vanilla ;</li>
 *   <li>les sons : battement de cœur calé sur la vignette, souffle haché à terre.</li>
 * </ul>
 */
public final class KoClient {

    public static final Identifier FILTRE = Identifier.fromNamespaceAndPath("reborn-hud", "ko");
    public static final Identifier PARAMS = Identifier.fromNamespaceAndPath("reborn-hud", "textures/effect/ko_params.png");

    public static final byte EV_A_TERRE = 1, EV_KO = 2, EV_REVEIL = 3, EV_HOPITAL = 4, EV_RETABLI = 5, EV_DEFAITE = 6, EV_RELEVE = 7,
            EV_DOULEUR = 8;

    /* ------------------------------------------------------------ état reçu */
    private static volatile KoPayload state;
    private static volatile long stateAt;
    private static volatile byte event;
    private static volatile long eventAt;

    /* ------------------------------------------------------------ rendu */
    private static int visMode;          // 0 rien, 1 à terre, 2 inconscient, 3 ATA, 4 douleur
    private static float strength;
    private static double beat;          // phase cardiaque cumulée (1 = un battement)
    private static long last;
    private static int breathTicks;
    private static DynamicTexture params;

    private KoClient() {}

    public static void update(KoPayload p) {
        state = p;
        stateAt = System.currentTimeMillis();
    }

    public static void event(byte kind) {
        event = kind;
        eventAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        switch (kind) {
            case EV_A_TERRE -> play(mc, SoundEvents.PLAYER_HURT, 0.9f, 0.55f);
            case EV_KO -> play(mc, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.6f);
            case EV_REVEIL, EV_HOPITAL -> play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 0.8f);
            case EV_RETABLI, EV_RELEVE -> play(mc, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
            case EV_DEFAITE -> play(mc, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 0.7f);
            case EV_DOULEUR -> play(mc, SoundEvents.PLAYER_HURT, 0.6f, 0.7f);
            default -> { }
        }
    }

    public static void clear() {
        state = null;
        event = 0;
        strength = 0f;
        visMode = 0;
    }

    public static KoPayload state() { return state; }

    /** Secondes écoulées depuis le dernier état reçu (pour décompter entre deux paquets). */
    public static float sinceState() { return (System.currentTimeMillis() - stateAt) / 1000f; }

    public static byte lastEvent() { return event; }

    public static float sinceEvent() { return event == 0 ? 99f : (System.currentTimeMillis() - eventAt) / 1000f; }

    /** Battement de cœur : 1 sur le « boum », retombe aussitôt (forme lub-dub). */
    public static float pulse() {
        double p = beat - Math.floor(beat);
        double a = Math.exp(-Math.pow(p / 0.07, 2)) + 0.55 * Math.exp(-Math.pow((p - 0.2) / 0.07, 2));
        return (float) Math.min(1.0, a);
    }

    public static float strength() { return strength; }

    public static int mode() { return visMode; }

    /** Le filtre KO doit-il être affiché ? */
    public static boolean actif() { return strength > 0.01f && visMode != 0; }

    private static int targetMode(KoPayload s) {
        if (s == null) return 0;
        if (s.phase() == 1) return 1;
        if (s.phase() == 2) return 2;
        if (CorpsClient.douleur()) return 4;
        if (s.ata() > 0) return 3;
        return 0;
    }

    /** À chaque image : fondus, rythme cardiaque, texture de paramètres. */
    public static void frame() {
        long now = System.currentTimeMillis();
        float dt = last == 0L ? 0f : Math.min(0.25f, (now - last) / 1000f);
        last = now;
        KoPayload s = state;
        int want = s == null && CorpsClient.douleur() ? 4 : targetMode(s);
        float target = switch (want) {
            case 1, 2, 4 -> 1f;
            case 3 -> s.ata() == 2 ? 0.45f : 0.3f;
            default -> 0f;
        };
        if (want != 0) visMode = want;
        if (strength < target) strength = Math.min(target, strength + dt * 1.6f);
        else strength = Math.max(target, strength - dt * (want == 0 ? 0.6f : 1.2f));
        if (strength <= 0f && want == 0) visMode = 0;

        float bpm = switch (visMode) {
            case 1 -> 112f;
            case 2 -> 46f;
            case 3 -> 84f;
            case 4 -> 100f;
            default -> 70f;
        };
        double before = beat;
        beat += dt * bpm / 60.0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && strength > 0.2f && Math.floor(beat) > Math.floor(before)) {
            play(mc, SoundEvents.WARDEN_HEARTBEAT, 0.25f + 0.55f * strength * (visMode == 3 ? 0.5f : 1f),
                    visMode == 2 ? 0.75f : 1.0f);
        }
        camera(dt, mc);
        ecrireParams(now, mc);
    }

    /* ------------------------------------------------------------ caméra, sons (ressenti) */

    private static float camYaw, camPitch;
    private static double reptation;

    /** Réglage d'accessibilité vanilla « Effets de distorsion » (0..1). */
    public static float distorsion() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1f : (float) (double) mc.options.screenEffectScale().get();
    }

    /**
     * Décalage de la caméra {lacet, tangage} en degrés, ou null : en douleur, la vue vacille doucement et le cœur
     * la fait tressaillir ; à terre, chaque poussée de reptation fait tanguer la vue (au sol, on avance par à-coups).
     * Coupé par « Effets de distorsion » à 0.
     */
    public static float[] cameraDecalage() {
        if (camYaw == 0f && camPitch == 0f) return null;
        return new float[]{camYaw, camPitch};
    }

    private static void camera(float dt, Minecraft mc) {
        float k = distorsion() * strength;
        camYaw = 0f;
        camPitch = 0f;
        if (mc.player == null || k < 0.01f) return;
        double t = System.currentTimeMillis() / 1000.0;
        float pulse = pulse();
        if (visMode == 4) {
            camYaw = (float) ((Math.sin(t * 0.7) * 0.55 + Math.sin(t * 1.9 + 0.4) * 0.18) * k);
            camPitch = (float) ((Math.sin(t * 0.9 + 1.0) * 0.35 + pulse * 0.45) * k);
        } else if (visMode == 1) {
            var v = mc.player.getDeltaMovement();
            double vit = Math.sqrt(v.x * v.x + v.z * v.z);
            float bouge = (float) Math.min(1.0, vit / 0.03);
            reptation += dt * (1.5 + 4.0 * bouge);
            camPitch = (float) ((Math.sin(reptation * 2.0) * 1.6 * bouge + Math.sin(t * 1.3) * 0.3 + pulse * 0.3) * k);
            camYaw = (float) ((Math.sin(reptation) * 1.2 * bouge) * k);
        }
    }

    /**
     * Facteur de volume des sons du monde : en douleur et à terre ils s'étouffent (le cœur et le souffle, eux, restent
     * nets), inconscient encore plus. Appelé par {@code SoundEngineKoMixin}.
     */
    public static float volumeSon(net.minecraft.client.resources.sounds.SoundInstance son) {
        if (strength < 0.2f || son == null) return 1f;
        float f = switch (visMode) {
            case 4 -> 0.5f;
            case 1 -> 0.4f;
            case 2 -> 0.3f;
            default -> 1f;
        };
        if (f >= 1f) return 1f;
        var id = son.getIdentifier();
        if (id != null && (id.equals(SoundEvents.WARDEN_HEARTBEAT.location()) || id.equals(SoundEvents.PLAYER_BREATH.location()))) {
            return 1f;
        }
        return 1f - (1f - f) * strength;
    }

    private static int montureTicks;

    /** À chaque tick client : souffle haché à terre ; porté (douleur, KO) : pas de « Maj pour descendre » vanilla. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // Porté de force ou à terre : on ne descend pas soi-même (le serveur l'empêche) — on efface l'invite vanilla.
        if (mc.player != null && mc.player.isPassenger()
                && (CorpsClient.douleur() || CorpsClient.porte() || state != null && state.phase() > 0)) {
            if (montureTicks++ < 10) mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
        } else {
            montureTicks = 0;
        }

        if (mc.player == null || mc.level == null || (visMode != 1 && visMode != 4) || strength < 0.3f) { breathTicks = 0; return; }
        // respiration lourde : plus lente et plus grave en douleur
        if (++breathTicks % (visMode == 4 ? 80 : 70) == 0) play(mc, SoundEvents.PLAYER_BREATH, visMode == 4 ? 0.55f : 0.6f,
                visMode == 4 ? 0.62f : 0.75f);
    }

    private static void play(Minecraft mc, SoundEvent ev, float vol, float pitch) {
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ev, SoundSource.PLAYERS,
                vol, pitch, false);
    }

    private static int octet(float v) { return Math.max(0, Math.min(255, Math.round(v * 255f))); }

    private static void ecrireParams(long now, Minecraft mc) {
        if (mc.getTextureManager() == null) return;
        if (params == null) {
            params = new DynamicTexture(() -> "reborn-hud ko params", 4, 1, false);
            mc.getTextureManager().register(PARAMS, params);
        }
        KoPayload s = state;
        long t60 = (now / 1000L * 60L + (now % 1000L) * 60L / 1000L) & 0xFFFFFFL;
        float hp = 1f;
        if (mc.player != null && mc.player.getMaxHealth() > 0) hp = mc.player.getHealth() / mc.player.getMaxHealth();
        boolean chakra = s != null && s.cause() == 1 && s.phase() > 0;
        float left = 1f;
        if (s != null && s.phase() > 0 && s.total() > 0) {
            left = Math.max(0f, (s.left() - sinceState()) / s.total());
        }
        float beatPhase = (float) (beat - Math.floor(beat));
        NativeImage px = params.getPixels();
        // pixel 0 : R mode, G force, B/A temps (bits 0-15) ; pixel 1 : R temps (bits 16-23), G chakra, B phase cardiaque, A PV
        px.setPixel(0, 0, argb((int) ((t60 >> 8) & 0xFF), visMode, octet(strength), (int) (t60 & 0xFF)));
        px.setPixel(1, 0, argb(octet(hp), (int) ((t60 >> 16) & 0xFF), chakra ? 255 : 0, octet(beatPhase)));
        // pixel 2 : R temps restant de la phase (fraction), G réglage « Effets de distorsion »
        px.setPixel(2, 0, argb(255, octet(left), octet(distorsion()), 0));
        params.upload();
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
