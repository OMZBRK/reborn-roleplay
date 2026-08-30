package fr.reborn.hud.camera;

import fr.reborn.hud.menu.settings.RebornPrefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;

/**
 * État de la caméra épaule Reborn (style Zenkai / anime-RP).
 *
 * <p>Singleton process-wide. Quand {@link #isEnabled()} est vrai et que le
 * joueur est en 3e personne arrière, {@code CameraThirdPersonMixin} décale la
 * caméra en over-the-shoulder selon {@link #distance()},
 * {@link #rightOffset()} et {@link #upOffset()} (avec clip anti-mur).
 *
 * <p>Le cadrage vient d'un {@link CameraPreset}, ajustable ensuite dans une
 * plage bornée (le joueur peut positionner sa caméra mais dans des limites
 * raisonnables). Le côté de l'épaule est un simple signe (swap).
 */
public final class RebornCamera {

    public static final RebornCamera INSTANCE = new RebornCamera();

    // Bornes du positionnement libre.
    static final double DIST_MIN = 2.0, DIST_MAX = 6.5;
    static final double RIGHT_MAX = 1.2;
    static final double UP_MIN = -0.6, UP_MAX = 1.2;

    /** Vue épaule (3e pers. RP par défaut), vraie 1ère personne, ou caméra
     *  Minecraft vanilla (F5 libre — le mod ne touche plus à la caméra). */
    public enum Mode { SHOULDER, FIRST, VANILLA }

    /** ÉPAULE par défaut : c'est la vue permanente RP (F5 neutralisé). */
    private Mode mode = Mode.SHOULDER;
    private CameraPreset preset = CameraPreset.DEFAUT;
    /** +1 = épaule droite, -1 = épaule gauche. */
    private int side = 1;

    // Valeurs live (initialisées depuis le preset, ajustables + clampées).
    private double distance = preset.distance;
    private double right = preset.right;
    private double up = preset.up;

    /** Orientation de la caméra en orbite (découplée du regard du joueur).
     *  La souris fait tourner {@code camYaw}/{@code camPitch}, PAS le perso. */
    private double camYaw = 0.0;
    private double camPitch = 0.0;

    /** Free-look : maintien ALT → la caméra orbite sans réorienter le corps. */
    private boolean freeLook = false;

    /** Impact d'atterrissage : petit dip vertical de la caméra (optionnel). */
    private boolean impactEnabled = false;
    private double landImpact = 0.0;   // décalage vertical courant (≤ 0)
    private boolean wasOnGround = true;
    private double prevYSpeed = 0.0;

    private RebornCamera() {}

    /** Le découplage/OTS n'est actif qu'en vue ÉPAULE. */
    public boolean isEnabled() { return mode == Mode.SHOULDER; }
    public Mode mode() { return mode; }
    /** Force le mode (sans effet de bord ; utilisé par les écrans cinématiques
     *  qui prennent temporairement le contrôle de la caméra, ex. test de la feuille). */
    public void setMode(Mode m) { this.mode = m; }
    public CameraPreset preset() { return preset; }
    public int side() { return side; }

    public double distance() { return distance; }
    /** Décalage latéral signé (négatif = épaule gauche). */
    public double rightOffset() { return right * side; }
    public double upOffset() { return up; }

    public double camYaw() { return camYaw; }
    public double camPitch() { return camPitch; }

    public boolean freeLook() { return freeLook; }
    public void setFreeLook(boolean v) { this.freeLook = v; }

    public boolean impactEnabled() { return impactEnabled; }
    public void setImpactEnabled(boolean v) { this.impactEnabled = v; saveToPrefs(); }
    /** Décalage vertical d'impact courant (0 si désactivé). */
    public double landImpact() { return impactEnabled ? landImpact : 0.0; }

    /** Initialise l'orbite caméra depuis l'orientation actuelle du joueur. */
    public void initOrientation(float yaw, float pitch) {
        this.camYaw = yaw;
        this.camPitch = pitch;
    }

    /** Applique un delta souris (déjà à l'échelle vanilla) à l'orbite caméra. */
    public void rotateCamera(double dx, double dy) {
        this.camYaw += dx * 0.15;
        this.camPitch = clamp(this.camPitch + dy * 0.15, -89.0, 89.0);
    }

    public void applyPreset(CameraPreset p) {
        this.preset = p;
        this.distance = clamp(p.distance, DIST_MIN, DIST_MAX);
        this.right = clamp(p.right, 0, RIGHT_MAX);
        this.up = clamp(p.up, UP_MIN, UP_MAX);
    }

    public void cyclePreset() { applyPreset(preset.next()); saveToPrefs(); }
    public void swapShoulder() { side = -side; saveToPrefs(); }
    public void adjustDistance(double d) { distance = clamp(distance + d, DIST_MIN, DIST_MAX); }
    public void adjustRight(double d) { right = clamp(right + d, 0, RIGHT_MAX); }
    public void adjustUp(double d) { up = clamp(up + d, UP_MIN, UP_MAX); }

    // ── Setters directs (menu de repositionnement) ──
    public void setDistance(double v) { distance = clamp(v, DIST_MIN, DIST_MAX); }
    public void setRight(double v) { right = clamp(v, 0, RIGHT_MAX); }
    public void setUp(double v) { up = clamp(v, UP_MIN, UP_MAX); }
    public void setSide(int s) { side = s < 0 ? -1 : 1; }
    public void setPreset(CameraPreset p) { applyPreset(p); }

    /** Vitesse de rotation du corps vers la direction de déplacement (0.1..1). */
    private double turnSpeed = 0.5;
    public double turnSpeed() { return turnSpeed; }
    public void setTurnSpeed(double v) { turnSpeed = clamp(v, 0.1, 1.0); }

    public double rightMagnitude() { return right; }

    /** Charge la config caméra depuis les prefs persistées. */
    public void loadFromPrefs() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.ensureLoaded();
        distance = clamp(p.camDistance, DIST_MIN, DIST_MAX);
        right = clamp(p.camRight, 0, RIGHT_MAX);
        up = clamp(p.camUp, UP_MIN, UP_MAX);
        side = p.camSide < 0 ? -1 : 1;
        turnSpeed = clamp(p.camTurnSpeed, 0.1, 1.0);
        impactEnabled = p.camImpact;
        CameraPreset[] presets = CameraPreset.values();
        preset = presets[Math.max(0, Math.min(presets.length - 1, p.camPreset))];
    }

    /** Persiste la config caméra courante. */
    public void saveToPrefs() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.camDistance = distance;
        p.camRight = right;
        p.camUp = up;
        p.camSide = side;
        p.camTurnSpeed = turnSpeed;
        p.camPreset = preset.ordinal();
        p.camImpact = impactEnabled;
        p.save();
    }

    /**
     * Force la perspective selon le mode et neutralise le F5 vanilla. À
     * appeler chaque client tick. En ÉPAULE : verrouille la 3e pers. arrière
     * (et ré-initialise l'orbite quand on (re)passe en 3e pers). En 1ère
     * personne : verrouille la 1ère personne.
     */
    public void tickView(Minecraft mc) {
        if (mc.player == null || mc.options == null) return;
        switch (mode) {
            case SHOULDER -> {
                if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    initOrientation(mc.player.getYRot(), mc.player.getXRot());
                }
            }
            case FIRST -> {
                if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
            }
            // VANILLA : on ne force RIEN → F5/double-F5 vanilla libre, le mod ne
            // touche plus à la caméra (mixin OTS off via isEnabled()==false) → chunks
            // rendus normalement. Sert de repli + de test A/B contre la caméra Reborn.
            case VANILLA -> { }
        }
    }

    public boolean isVanilla() { return mode == Mode.VANILLA; }

    /**
     * Bascule entre la caméra Reborn (épaule) et la caméra <b>vanilla Minecraft</b>
     * (F5/double-F5 libre). En mode vanilla, le mod ne repositionne plus la caméra
     * (isEnabled()==false) → le rendu des chunks redevient celui de Minecraft.
     */
    public void toggleVanilla(Minecraft mc) {
        if (mode == Mode.VANILLA) {
            mode = Mode.SHOULDER;
        } else {
            mode = Mode.VANILLA;
            if (mc != null && mc.options != null) {
                // Démarre en 3e personne vanilla pour voir la différence tout de suite.
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
        tickView(mc);
    }

    /**
     * Amortissement d'atterrissage : quand le joueur retouche le sol après une
     * chute, la caméra plonge brièvement puis remonte (decay exponentiel). À
     * appeler chaque client tick avec le joueur local. No-op si désactivé (on
     * garde juste le suivi sol/vitesse pour ne pas déclencher un dip au ré-activé).
     */
    public void tickImpact(Entity e) {
        if (e == null) { landImpact = 0.0; return; }
        boolean onGround = e.onGround();
        if (impactEnabled && onGround && !wasOnGround) {
            double fall = -prevYSpeed;          // vitesse descendante à l'impact
            if (fall > 0.25) {
                landImpact = -Math.min(0.5, fall * 0.45);
            }
        }
        landImpact *= 0.70;                      // remonte progressivement
        if (Math.abs(landImpact) < 1.0e-3) landImpact = 0.0;
        wasOnGround = onGround;
        prevYSpeed = e.getDeltaMovement().y;
    }

    /** Bascule vue ÉPAULE ↔ vraie 1ère personne (touche dédiée). */
    public void toggleFirstPerson(Minecraft mc) {
        mode = (mode == Mode.SHOULDER) ? Mode.FIRST : Mode.SHOULDER;
        tickView(mc); // applique tout de suite la perspective + init orbite.
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
