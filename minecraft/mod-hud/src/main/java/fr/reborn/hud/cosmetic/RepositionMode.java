package fr.reborn.hud.cosmetic;

import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Mode de repositionnement cosmétique in-world</b> — état partagé (singleton,
 * comme {@link fr.reborn.hud.immersion.PhotoMode}) piloté par
 * {@code RepositionScreen}. Le joueur reste debout dans le VRAI monde, rendu en
 * 3e personne : {@code CameraRepositionMixin} lit cet état chaque frame pour
 * placer la caméra en orbite autour du perso ; le cosmétique (rendu par
 * {@link CosmeticFeatureRenderer}) est modifié EN DIRECT via le gizmo.
 *
 * <p>La caméra tourne autour d'un point de focus (centre du corps), à une
 * distance réglable (molette). Le yaw/pitch de l'orbite se règlent en glissant
 * sur le monde. Ce mode ne stocke PAS la transform : il édite l'instance live
 * du cosmétique ciblé, récupérée via {@link CosmeticFeatureRenderer#live} pour
 * l'id passé à {@link #begin} — l'éditeur mute la même instance que le renderer.
 */
public final class RepositionMode {

    public static final RepositionMode INSTANCE = new RepositionMode();

    private static final double DIST_MIN = 1.8, DIST_MAX = 7.0;

    private boolean active = false;
    private String cosmeticId = "cosmetic";
    private String cosmeticLabel = "Cosmétique";
    /** Transform live du cosmétique ciblé (instance partagée avec le renderer). */
    private CosmeticTransform liveTransform = new CosmeticTransform();

    private float camYaw = 0f;
    private float camPitch = 6f;
    private double distance = 3.2;

    // Sauvegarde de l'état caméra pour restauration à la sortie.
    private CameraType savedPerspective = CameraType.FIRST_PERSON;
    private RebornCamera.Mode savedCamMode = RebornCamera.Mode.SHOULDER;

    private RepositionMode() {}

    public boolean isActive() { return active; }
    public String cosmeticId() { return cosmeticId; }
    public String cosmeticLabel() { return cosmeticLabel; }

    /** Transform live édité par le gizmo (instance partagée avec le renderer pour l'id ciblé). */
    public CosmeticTransform target() { return liveTransform; }

    public float camYaw() { return camYaw; }
    public float camPitch() { return camPitch; }
    public double distance() { return distance; }

    /** Démarre le mode : force la 3e personne arrière + neutralise la caméra épaule. */
    public void begin(Minecraft mc, String cosmeticId, String cosmeticLabel, CosmeticTransform.Anchor defaultAnchor) {
        this.cosmeticId = cosmeticId != null ? cosmeticId : "cosmetic";
        this.cosmeticLabel = cosmeticLabel != null ? cosmeticLabel : "Cosmétique";
        // Cible la MÊME instance de transform que le renderer pour cet id → édition live.
        this.liveTransform = CosmeticFeatureRenderer.live(this.cosmeticId,
            defaultAnchor != null ? defaultAnchor : CosmeticTransform.Anchor.HEAD);
        if (mc.player != null) {
            // Démarre face au perso (on voit son avant → main droite/katana visibles).
            this.camYaw = mc.player.getYRot() + 180f;
            this.camPitch = 6f;
        }
        this.distance = 3.2;
        if (mc.options != null) {
            savedPerspective = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        // Neutralise la caméra épaule Reborn le temps du mode (sinon tickView la
        // reforce et CameraThirdPersonMixin la décale en over-the-shoulder).
        savedCamMode = RebornCamera.INSTANCE.mode();
        RebornCamera.INSTANCE.setMode(RebornCamera.Mode.SHOULDER);
        active = true;
    }

    public void end(Minecraft mc) {
        active = false;
        if (mc.options != null) mc.options.setCameraType(savedPerspective);
        RebornCamera.INSTANCE.setMode(savedCamMode);
    }

    // ─────────────────── Contrôles orbite ───────────────────
    public void orbit(double dx, double dy) {
        camYaw = (float) ((camYaw + dx * 0.5) % 360.0);
        camPitch = (float) clamp(camPitch - dy * 0.5, -85.0, 85.0);
    }

    public void zoom(double scrollY) {
        distance = clamp(distance - scrollY * 0.3, DIST_MIN, DIST_MAX);
    }

    // ─────────────────── Géométrie caméra / ancre ───────────────────

    /** Direction de visée de la caméra (unité) depuis yaw/pitch (repère vanilla). */
    public Vec3 look() {
        double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch);
        return new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
    }

    /** Point de focus (centre du corps du joueur). */
    public Vec3 focus(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return Vec3.ZERO;
        return new Vec3(p.getX(), p.getY() + 1.0, p.getZ());
    }

    /** Position caméra = focus reculé de {@code distance} le long de la visée. */
    public Vec3 cameraPosition(Minecraft mc) {
        return focus(mc).subtract(look().scale(distance));
    }

    /** Vecteur « droite » du joueur (monde), depuis son yaw corporel. */
    public Vec3 playerRight(Minecraft mc) {
        Player p = mc.player;
        double yr = Math.toRadians(p != null ? p.getYRot() : 0f);
        return new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
    }

    /** Vecteur « avant » horizontal du joueur (monde). */
    public Vec3 playerForward(Minecraft mc) {
        Player p = mc.player;
        double yr = Math.toRadians(p != null ? p.getYRot() : 0f);
        return new Vec3(-Math.sin(yr), 0, Math.cos(yr));
    }

    /**
     * Point MONDE approximatif où le cosmétique se trouve (là où le gizmo est
     * dessiné), selon l'ancrage. Approximation joueur-relative (v1) : suffisant
     * pour cadrer le gizmo sur/près du modèle ; l'utilisateur ajuste ensuite.
     */
    public Vec3 anchorWorld(Minecraft mc) {
        Player p = mc.player;
        if (p == null) return Vec3.ZERO;
        Vec3 feet = new Vec3(p.getX(), p.getY(), p.getZ());
        Vec3 right = playerRight(mc);
        Vec3 fwd = playerForward(mc);
        double up;
        double sideR = 0, sideF = 0;
        switch (target().anchor) {
            case HEAD      -> up = 1.62;
            case NECK      -> up = 1.45;
            case TORSO     -> up = 1.20;
            case PELVIS    -> up = 0.95;
            case RIGHT_ARM -> { up = 1.35; sideR = 0.32; }
            case LEFT_ARM  -> { up = 1.35; sideR = -0.32; }
            case RIGHT_HAND-> { up = 1.00; sideR = 0.35; sideF = 0.10; }
            case LEFT_HAND -> { up = 1.00; sideR = -0.35; sideF = 0.10; }
            default        -> up = 1.20;
        }
        return feet.add(0, up, 0).add(right.scale(sideR)).add(fwd.scale(sideF));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
