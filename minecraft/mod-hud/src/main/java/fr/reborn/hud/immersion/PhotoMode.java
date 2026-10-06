package fr.reborn.hud.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.Vec3;

/**
 * Mode Photo : caméra libre (free-cam) pendant que le joueur reste figé. On vole
 * avec ZQSD + Espace/Shift (Sprint = ×2.5). L'UI (curseur, look, bouton
 * Capturer) est gérée par {@link fr.reborn.hud.ui.PhotoModeScreen} ; la caméra
 * est repositionnée par {@code CameraPhotoMixin}.
 *
 * <p>On force la <b>3e personne</b> pendant le mode (sinon en 1ère personne on
 * ne voit pas le perso et la main apparaît) et on bloque le changement de vue.
 */
public final class PhotoMode {

    public static final PhotoMode INSTANCE = new PhotoMode();

    private static final float SENS = 0.15f;

    /** Demi-étendue de la zone autorisée (100×100×100 autour du point de départ). */
    private static final double HALF = 50.0;

    private boolean active = false;
    /** Le chat est ouvert par-dessus la freecam : le déplacement est figé et le
     *  panneau photo est rouvert quand le chat se ferme. */
    private boolean suspendedForChat = false;
    private double x, y, z;
    private double anchorX, anchorY, anchorZ;
    private float yaw, pitch;
    private boolean pendingCapture = false;
    private CameraType savedPerspective = CameraType.FIRST_PERSON;
    /** Vitesse de déplacement de la caméra (réglable dans le panneau). */
    private float cameraSpeed = 0.35f;

    // ── Réglages de prise de vue (panneau) ──────────────────────────────
    /** Filtres (post effect reborn-hud:photo_&lt;id&gt;_&lt;flou&gt;). */
    public static final String[] FILTERS = { "naturel", "encre", "sepia", "nuit" };
    public static final String[] FILTER_LABELS = { "Naturel", "Encre", "Sepia", "Nuit" };
    /** Cadres appliqués au fichier après la capture. */
    public static final String[] FRAMES = { "Aucun", "Makimono", "Polaroid" };
    public static final float FOV_MIN = 30f, FOV_MAX = 110f, ROLL_MAX = 30f;
    public static final int BLUR_LEVELS = 4;

    private float fov = 70f;
    private float roll = 0f;
    private int blur = 0;
    private int filter = 0;
    private int frame = 0;
    private net.minecraft.resources.Identifier appliedFilter = null;

    public float fov() { return fov; }
    public void setFov(float v) { fov = Math.max(FOV_MIN, Math.min(FOV_MAX, v)); }
    public float roll() { return roll; }
    public void setRoll(float v) { roll = Math.max(-ROLL_MAX, Math.min(ROLL_MAX, v)); }
    public int blur() { return blur; }
    public void setBlur(int v) { blur = Math.max(0, Math.min(BLUR_LEVELS - 1, v)); syncFilter(Minecraft.getInstance()); }
    public int filter() { return filter; }
    public void setFilter(int v) { filter = Math.floorMod(v, FILTERS.length); syncFilter(Minecraft.getInstance()); }
    public int frame() { return frame; }
    public void cycleFrame() { frame = (frame + 1) % FRAMES.length; }

    /** Filtre voulu (null = aucun : naturel et net). */
    private net.minecraft.resources.Identifier wantedFilter() {
        if (filter == 0 && blur == 0) return null;
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "photo_" + FILTERS[filter] + "_" + blur);
    }

    /** Pose / retire le filtre plein écran du mode photo (n'écrase pas le Byakugan). */
    private void syncFilter(Minecraft mc) {
        if (mc.gameRenderer == null) return;
        var cur = mc.gameRenderer.currentPostEffect();
        var want = active ? wantedFilter() : null;
        if (want != null && !fr.reborn.hud.byakugan.ByakuganClient.active()) {
            if (!want.equals(cur)) ((fr.reborn.hud.mixin.GameRendererByakuganAccessor) mc.gameRenderer).reborn$setPostEffect(want);
            appliedFilter = want;
        } else if (appliedFilter != null) {
            if (appliedFilter.equals(cur)) mc.gameRenderer.clearPostEffect();
            appliedFilter = null;
        }
    }

    public float getCameraSpeed() { return cameraSpeed; }
    public void setCameraSpeed(float s) { this.cameraSpeed = Math.max(0.05f, Math.min(2.0f, s)); }
    public void addCameraSpeed(float d) { setCameraSpeed(cameraSpeed + d); }

    /** Replace la caméra sur le joueur (position + regard actuels). */
    public void resetPosition(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        yaw = mc.player.getYRot();
        pitch = Math.max(-30f, Math.min(45f, mc.player.getXRot()));
        double yr = Math.toRadians(yaw);
        double bx = Math.sin(yr), bz = -Math.cos(yr);
        placeBehind(mc, eye, bx, bz);
    }

    /** true si une touche de déplacement est physiquement enfoncée. */
    public boolean anyMoveKeyDown(Minecraft mc) {
        if (mc.options == null) return false;
        com.mojang.blaze3d.platform.Window w = mc.getWindow();
        return down(mc, w, mc.options.keyUp) || down(mc, w, mc.options.keyDown)
            || down(mc, w, mc.options.keyLeft) || down(mc, w, mc.options.keyRight)
            || down(mc, w, mc.options.keyJump) || down(mc, w, mc.options.keyShift);
    }

    private PhotoMode() {}

    public boolean isActive() { return active; }
    public boolean isSuspendedForChat() { return suspendedForChat; }

    /** Ouvre le chat par-dessus la freecam (la caméra reste active, on écrit). */
    public void openChatOverlay(Minecraft mc, boolean command) {
        suspendedForChat = true;
        mc.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen(command ? "/" : "", true));
    }

    /**
     * Démarre le mode : caméra placée <b>derrière</b> le perso (≈ 3 blocs, un peu au-dessus), en
     * reculant moins si un bloc gêne — avant, elle partait des yeux et l'on voyait l'intérieur de
     * la tête. Sans effet si le mode est déjà actif (retour du chat, redimensionnement).
     */
    public void begin(Minecraft mc) {
        if (mc.player == null || active) return;
        Vec3 eye = mc.player.getEyePosition();
        yaw = mc.player.getYRot();
        pitch = Math.max(-30f, Math.min(45f, mc.player.getXRot()));
        double yr = Math.toRadians(yaw);
        double bx = Math.sin(yr), bz = -Math.cos(yr);          // vers l'arrière du regard
        placeBehind(mc, eye, bx, bz);
        anchorX = x; anchorY = y; anchorZ = z;
        savedPerspective = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        fov = mc.options.fov().get();
        roll = 0f;
        active = true;
    }

    public void end(Minecraft mc) {
        active = false;
        suspendedForChat = false;
        syncFilter(mc);
        if (mc.options != null) mc.options.setCameraType(savedPerspective);
    }

    public Vec3 pos() { return new Vec3(x, y, z); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** Rotation depuis un delta souris (drag). */
    public void rotate(double dx, double dy) {
        yaw += (float) (dx * SENS);
        pitch = (float) Math.max(-90, Math.min(90, pitch + dy * SENS));
    }

    /** Déplacement free-cam + maintien de la 3e personne. Appelé chaque tick. */
    public void tickMovement(Minecraft mc) {
        if (!active || mc.options == null) return;
        // Chat ouvert par-dessus la freecam : on FIGE le déplacement (les touches
        // servent à écrire, pas à voler) et on garde la caméra où elle est.
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            return;
        }
        // Le chat s'est fermé → on rouvre le panneau photo (on reste en freecam).
        if (suspendedForChat) {
            suspendedForChat = false;
            mc.setScreenAndShow(new fr.reborn.hud.ui.PhotoModeScreen());
            return;
        }
        syncFilter(mc);
        // Bloque le changement de vue (F5) pendant le mode.
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        com.mojang.blaze3d.platform.Window win = mc.getWindow();
        float f = 0, s = 0, up = 0;
        if (down(mc, win, mc.options.keyUp)) f += 1;
        if (down(mc, win, mc.options.keyDown)) f -= 1;
        if (down(mc, win, mc.options.keyRight)) s += 1;
        if (down(mc, win, mc.options.keyLeft)) s -= 1;
        if (down(mc, win, mc.options.keyJump)) up += 1;
        if (down(mc, win, mc.options.keyShift)) up -= 1;
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);
        double spd = cameraSpeed * (down(mc, win, mc.options.keySprint) ? 2.5 : 1.0);
        double dx = (fx * f + rx * s) * spd;
        double dz = (fz * f + rz * s) * spd;
        double dy = up * spd;
        // Zone autorisée (anti-fuite) + pas de traversée de blocs (anti-xray),
        // testé par axe pour permettre de glisser le long des murs.
        double nx = clamp(x + dx, anchorX - HALF, anchorX + HALF);
        if (!solid(mc, nx, y, z)) x = nx;
        double nz = clamp(z + dz, anchorZ - HALF, anchorZ + HALF);
        if (!solid(mc, x, y, nz)) z = nz;
        double ny = clamp(y + dy, anchorY - HALF, anchorY + HALF);
        if (!solid(mc, x, ny, z)) y = ny;
    }

    /**
     * Place la caméra derrière la tête : on avance pas à pas depuis les yeux jusqu'au premier
     * obstacle (max 3 blocs). S'il y a moins de 1,5 bloc de libre (feuillage, mur…), on monte
     * plutôt au-dessus du perso en regardant vers le bas.
     */
    private void placeBehind(Minecraft mc, Vec3 eye, double bx, double bz) {
        double d = 0;
        for (double t = 0.4; t <= 3.0; t += 0.2) {
            if (solid(mc, eye.x + bx * t, eye.y + 0.3, eye.z + bz * t)) break;
            d = t;
        }
        if (d >= 1.5) {
            x = eye.x + bx * (d - 0.2); y = eye.y + 0.3; z = eye.z + bz * (d - 0.2);
            return;
        }
        double up = 0;
        for (double t = 0.4; t <= 3.0; t += 0.2) {
            if (solid(mc, eye.x + bx * 0.8, eye.y + t, eye.z + bz * 0.8)) break;
            up = t;
        }
        x = eye.x + bx * 0.8; y = eye.y + Math.max(0.5, up - 0.2); z = eye.z + bz * 0.8;
        pitch = Math.max(pitch, 35f);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean solid(Minecraft mc, double x, double y, double z) {
        if (mc.level == null) return false;
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        return !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    /** État physique d'une touche (marche même avec un écran ouvert). */
    private static boolean down(Minecraft mc, com.mojang.blaze3d.platform.Window win, KeyMapping kb) {
        try {
            // 26.1 : plus de getBoundKeyTranslationKey()/fromTranslationKey() ; on
            // récupère la touche actuellement liée via saveString() → getKey(String).
            InputConstants.Key key = InputConstants.getKey(kb.saveString());
            return InputConstants.isKeyDown(win, key.getValue());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void requestCapture() { pendingCapture = true; }

    public boolean consumeCapture() {
        if (!pendingCapture) return false;
        pendingCapture = false;
        return true;
    }
}
