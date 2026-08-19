package com.reborn.shinobiabilities.mobility;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shared movement math — pitch-blended velocity anchors and wall
 * detection.
 *
 * <p>Pitch blending: three anchor pairs (forward / up / down) are
 * interpolated by the camera pitch. Looking up favours the vertical
 * anchor, looking down the dive anchor, level the forward anchor.
 */
public final class MobilityMath {

    private MobilityMath() {}

    /** Horizontal+vertical magnitudes blended by {@code pitch} (degrees,
     *  -90 = up, +90 = down). Returns {h, v}. */
    public static double[] pitchBlend(float pitch,
                                      double hForward, double vForward,
                                      double hUp, double vUp,
                                      double hDown, double vDown) {
        double t;
        double h, v;
        if (pitch <= 0) {           // level → up
            t = Math.min(1.0, -pitch / 90.0);
            h = lerp(hForward, hUp, t);
            v = lerp(vForward, vUp, t);
        } else {                    // level → down
            t = Math.min(1.0, pitch / 90.0);
            h = lerp(hForward, hDown, t);
            v = lerp(vForward, vDown, t);
        }
        return new double[]{h, v};
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Velocity along the look XZ with the blended magnitudes. */
    public static Vector blendedVelocity(Player p, double[] hv) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        return dir.multiply(hv[0]).setY(hv[1]);
    }

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    /** True when a solid block touches the player horizontally (feet or
     *  chest height). */
    public static boolean nearWall(Player p) {
        return wallFace(p) != null;
    }

    /** The face of the player's block column that touches a wall, or
     *  null. (The returned face points FROM the player TOWARD the wall.) */
    public static BlockFace wallFace(Player p) {
        Location feet = p.getLocation();
        for (int dy = 0; dy <= 1; dy++) {
            Block at = feet.clone().add(0, dy, 0).getBlock();
            for (BlockFace f : HORIZONTAL) {
                Block side = at.getRelative(f);
                if (side.getType().isSolid()) return f;
            }
        }
        return null;
    }

    /** Unit vector pushing AWAY from the wall the player touches;
     *  null when no wall. */
    public static Vector awayFromWall(Player p) {
        BlockFace f = wallFace(p);
        if (f == null) return null;
        return new Vector(-f.getModX(), 0, -f.getModZ()).normalize();
    }

    /* ----------------------------------------------- fluidity rework (§3) */

    /** Vertical handling for {@link #applyImpulse}: REPLACE sets Y to the
     *  push (leaps — discards speculative lift); ADD keeps current Y (dashes). */
    public enum VMode { REPLACE, ADD }

    /** Horizontal intent direction — the camera look nudged toward where the
     *  player is already travelling, so turns keep momentum instead of
     *  snapping. {@code lookWeight} 1.0 = pure look (old behaviour); ~0.7 is
     *  natural. */
    public static Vector intentDir(Player p, double lookWeight) {
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0e-4) look = new Vector(0, 0, 1);
        look.normalize();
        Vector vel = p.getVelocity().clone().setY(0);
        if (vel.lengthSquared() < 1.0e-3) return look;
        Vector travel = vel.normalize();
        Vector dir = look.multiply(lookWeight).add(travel.multiply(1.0 - lookWeight));
        return dir.lengthSquared() < 1.0e-4 ? look : dir.normalize();
    }

    /**
     * Apply a mobility impulse that PRESERVES part of the player's current
     * horizontal momentum instead of overwriting it, redirected toward
     * {@code dirH} and soft-capped — the heart of the fluidity rework (§3.2).
     *
     * @param dirH         horizontal push direction (usually {@link #intentDir})
     * @param pushH        horizontal push magnitude (the old hv[0])
     * @param pushV        vertical component (the old hv[1])
     * @param preserveH    0..1 fraction of existing horizontal speed carried over
     * @param softCapH     soft cap on resulting horizontal speed (≤0 = none)
     * @param verticalMode REPLACE (leaps — discards speculative lift) or ADD (dashes)
     */
    public static void applyImpulse(Player p, Vector dirH, double pushH, double pushV,
                                    double preserveH, double softCapH, VMode verticalMode) {
        Vector v = p.getVelocity();
        Vector h = new Vector(v.getX(), 0, v.getZ()).multiply(preserveH);
        Vector push = new Vector(dirH.getX(), 0, dirH.getZ());
        if (push.lengthSquared() > 1.0e-6) {
            h.add(push.normalize().multiply(pushH));
        }
        if (softCapH > 0 && h.length() > softCapH) {
            h = h.normalize().multiply(softCapH);
        }
        double newY = (verticalMode == VMode.REPLACE) ? pushV : v.getY() + pushV;
        p.setVelocity(new Vector(h.getX(), newY, h.getZ()));
    }
}
