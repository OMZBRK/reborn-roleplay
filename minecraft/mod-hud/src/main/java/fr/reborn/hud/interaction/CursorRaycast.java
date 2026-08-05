package fr.reborn.hud.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/**
 * Raycast depuis un point ÉCRAN (le curseur du menu d'interaction) vers le
 * monde — permet de cliquer librement sur un bloc / une entité avec le curseur
 * (style GTA), pas seulement au centre du crosshair.
 *
 * <p>On reconstitue la direction du rayon à partir de l'offset du curseur par
 * rapport au centre, du FOV et de l'aspect ratio, puis on raycast blocs +
 * entités et on garde le plus proche.
 */
public final class CursorRaycast {

    // Longue portée : on peut viser/ouvrir le menu sur une cible éloignée
    // (style GTA). C'est le SERVEUR qui refusera ensuite les actions RP hors
    // de portée — les actions non-RP (DM…) restent possibles.
    private static final double REACH = 64.0;

    private CursorRaycast() {}

    /** @return un BlockHitResult / EntityHitResult, ou null si rien à portée. */
    public static HitResult raycast(Minecraft mc, double cursorX, double cursorY) {
        if (mc == null || mc.player == null || mc.level == null) return null;

        Camera camera = mc.gameRenderer.getCamera();
        float camYaw = camera.getYRot();
        float camPitch = camera.getXRot();
        // Origine = position de la CAMÉRA (pas l'œil joueur) → fonctionne aussi
        // en 3e personne (on vise depuis le point de vue réel).
        Vec3 eye = camera.position();

        double fovDeg = mc.options.getFov().getValue();
        int w = mc.getWindow().getFramebufferWidth();
        int h = mc.getWindow().getFramebufferHeight();
        if (w <= 0 || h <= 0) return null;
        double sf = mc.getWindow().getGuiScale();
        double px = cursorX * sf;
        double py = cursorY * sf;

        double ndcX = (2.0 * px / w) - 1.0;
        double ndcY = 1.0 - (2.0 * py / h);
        double tanHalf = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double aspect = (double) w / h;
        double vx = ndcX * aspect * tanHalf;
        double vy = ndcY * tanHalf;

        float rayYaw = camYaw + (float) Math.toDegrees(Math.atan(vx));
        float rayPitch = camPitch - (float) Math.toDegrees(Math.atan(vy));

        Vec3 dir = Vec3.fromPolar(rayPitch, rayYaw);
        Vec3 end = eye.add(dir.multiply(REACH));

        // Blocs.
        HitResult block = mc.level.raycast(new ClipContext(eye, end,
            ClipContext.ShapeType.OUTLINE, ClipContext.FluidHandling.NONE, mc.player));
        double blockDistSq = block.getType() == HitResult.Type.MISS
            ? REACH * REACH : block.position().squaredDistanceTo(eye);

        // Entités (plus proches que le bloc) — box couvrant tout le segment.
        AABB box = new AABB(eye, end).expand(1.0);
        EntityHitResult entity = ProjectileUtil.raycast(mc.player, eye, end, box,
            e -> e != mc.player && !e.isSpectator() && e.isAlive(), blockDistSq);
        if (entity != null) return entity;

        return block.getType() == HitResult.Type.MISS ? null : block;
    }
}
