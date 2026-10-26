package com.reborn.shinobicore.douane;

/**
 * Zone autorisée d'un candidat (hall ou bureau) — géométrie pure, sans Bukkit. Le monde est contrôlé à part.
 *
 * <p>Deux formes :
 * <ul>
 *   <li><b>pavé</b> : deux coins (inclus) ;</li>
 *   <li><b>cylindre</b> : un centre et un rayon horizontal, de {@code BAS} blocs sous le centre à {@code HAUT} blocs
 *       au-dessus (le monde est vide : sortir par le bas, c'est tomber dans le vide).</li>
 * </ul>
 */
public record Zone(boolean pave, double x1, double y1, double z1, double x2, double y2, double z2, double rayon) {

    /** Marge sous le centre d'un cylindre. */
    public static final double BAS = 4.0;
    /** Marge au-dessus du centre d'un cylindre. */
    public static final double HAUT = 12.0;

    /** Cylindre de rayon {@code rayon} autour de (x, y, z). */
    public static Zone cylindre(double x, double y, double z, double rayon) {
        return new Zone(false, x, y, z, x, y, z, Math.max(1.0, rayon));
    }

    /** Pavé entre deux coins, dans n'importe quel ordre. */
    public static Zone pave(double ax, double ay, double az, double bx, double by, double bz) {
        return new Zone(true, Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz), 0);
    }

    public boolean contient(double x, double y, double z) {
        if (pave) {
            return x >= x1 && x <= x2 + 1 && y >= y1 && y <= y2 + 1 && z >= z1 && z <= z2 + 1;
        }
        double dx = x - x1, dz = z - z1;
        return dx * dx + dz * dz <= rayon * rayon && y >= y1 - BAS && y <= y1 + HAUT;
    }
}
