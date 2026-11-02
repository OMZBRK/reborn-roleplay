package fr.reborn.hud.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * « Un bloc opaque plein se trouve-t-il entre ces deux points ? » — parcours des cellules traversées par le segment
 * (Amanatides et Woo), sans {@code ClipContext} ni allocation. Seul {@code BlockState#isSolidRender()} bloque :
 * {@code canOcclude} et forme d'occlusion pleine (valeur mise en cache par le jeu), donc le verre, les feuilles, les
 * barrières, les dalles, les escaliers et les clôtures ne cachent pas un nametag.
 *
 * <p>Les cellules de départ (caméra) et d'arrivée (centre de tête) ne sont pas testées : une tête allongée posée au ras
 * du sol ou une caméra frôlant un mur ne se masquent pas elles-mêmes.
 */
final class OcclusionBlocs {

    private OcclusionBlocs() {}

    /** Au-delà, pas de test (le nametag ne s'affiche pas si loin ; EntityCulling couvre l'occlusion lointaine). */
    static final double PORTEE_MAX = 64.0;
    private static final int CELLULES_MAX = 3 * 64 + 3;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    static boolean masque(Level level, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (level == null) return false;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double l2 = dx * dx + dy * dy + dz * dz;
        if (l2 < 1e-8 || l2 > PORTEE_MAX * PORTEE_MAX) return false;
        int x = (int) Math.floor(x0), y = (int) Math.floor(y0), z = (int) Math.floor(z0);
        int xe = (int) Math.floor(x1), ye = (int) Math.floor(y1), ze = (int) Math.floor(z1);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        // t ∈ [0, 1] le long du segment : pas d'une cellule (tdX…) et prochaine frontière franchie (tmX…).
        double tdx = sx != 0 ? 1.0 / Math.abs(dx) : Double.MAX_VALUE;
        double tdy = sy != 0 ? 1.0 / Math.abs(dy) : Double.MAX_VALUE;
        double tdz = sz != 0 ? 1.0 / Math.abs(dz) : Double.MAX_VALUE;
        double tmx = sx > 0 ? (x + 1 - x0) * tdx : sx < 0 ? (x0 - x) * tdx : Double.MAX_VALUE;
        double tmy = sy > 0 ? (y + 1 - y0) * tdy : sy < 0 ? (y0 - y) * tdy : Double.MAX_VALUE;
        double tmz = sz > 0 ? (z + 1 - z0) * tdz : sz < 0 ? (z0 - z) * tdz : Double.MAX_VALUE;
        for (int i = 0; i < CELLULES_MAX; i++) {
            if (tmx <= tmy && tmx <= tmz) {
                if (tmx > 1.0) return false;
                x += sx;
                tmx += tdx;
            } else if (tmy <= tmz) {
                if (tmy > 1.0) return false;
                y += sy;
                tmy += tdy;
            } else {
                if (tmz > 1.0) return false;
                z += sz;
                tmz += tdz;
            }
            if (x == xe && y == ye && z == ze) return false;
            if (level.getBlockState(POS.set(x, y, z)).isSolidRender()) return true;
        }
        return false;
    }
}
