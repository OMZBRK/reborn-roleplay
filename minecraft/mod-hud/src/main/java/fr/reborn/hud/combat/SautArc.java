package fr.reborn.hud.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Arc du saut chargé dans le vrai monde (lot 5, étape A) : le chemin des pieds tick par tick et le point où l'on
 * arrivera, lus par la trajectoire en poussière et son anneau ({@link SautApercu}).
 *
 * <p>Même déplacement que le vol joué par le mod ({@link ChakraJump#volEtape}) : la boîte du joueur avance tick par tick
 * avec la collision du jeu ({@link Entity#collideBoundingBox}), une composante heurtée tombe à zéro (un mur arrête l'avance
 * sur son axe : on glisse et on tombe à son pied), puis gravité et frottements de {@link SautChargeLoi}. L'arrivée est le
 * premier contact du dessous ; l'eau arrête l'arc à sa surface ; un chunk non chargé l'arrête au dernier point connu
 * ({@link #inconnu()} : aucune arrivée n'est inventée, l'aperçu ne montre rien). Toits, plafonds, arbres : la collision
 * décide, comme pour le corps.
 *
 * <p>Coût : au plus {@value #TICKS} pas de collision par calcul, un calcul tous les 2 ticks au plus et seulement quand la
 * charge, la visée ou la position bougent ; rien hors de la charge et du vol.
 */
public final class SautArc {

    /** 7 s de vol : au-delà de tout saut permis (3,3 s au plus à plat). */
    public static final int TICKS = 140;

    private double x0, z0, ax, ay, az, distance;
    private int ticks;
    private boolean valide, mur, inconnu, eau;
    /** Chemin des pieds : le départ, puis un point par tick jusqu'à l'arrivée comprise ({@link #points()} points). */
    private final double[] chX = new double[TICKS + 1], chY = new double[TICKS + 1], chZ = new double[TICKS + 1];
    private int points;

    /** Le dernier calcul a-t-il trouvé une arrivée (sol, eau) ? */
    public boolean valide() { return valide; }
    /** L'arc touche un mur avant d'arriver (arrivée au pied du mur). */
    public boolean mur() { return mur; }
    /** L'arc entre dans un chunk non chargé : arrivée inconnue, dernier point connu dans {@link #x()}…. */
    public boolean inconnu() { return inconnu; }
    /** Arrivée sur l'eau. */
    public boolean eau() { return eau; }
    public double x() { return ax; }
    public double y() { return ay; }
    public double z() { return az; }
    /** Distance horizontale du départ à l'arrivée. */
    public double distance() { return distance; }
    /** Durée du vol jusqu'à l'arrivée, en ticks. */
    public int ticks() { return ticks; }
    /** Nombre de points du chemin (départ et arrivée compris). */
    public int points() { return points; }
    public double cheminX(int i) { return chX[i]; }
    public double cheminY(int i) { return chY[i]; }
    public double cheminZ(int i) { return chZ[i]; }

    /**
     * Calcule l'arc depuis les pieds {@code (x, y, z)} avec la vitesse de départ {@code v} (non modifiée).
     *
     * @param corps l'entité dont la boîte et le contexte de collision servent (le joueur local)
     */
    public void calculer(Level level, Entity corps, double x, double y, double z, double[] v, SautChargeLoi.Regles r) {
        valide = false;
        mur = false;
        inconnu = false;
        eau = false;
        x0 = x;
        z0 = z;
        ax = x;
        ay = y;
        az = z;
        distance = 0;
        ticks = 0;
        points = 0;
        if (level == null || corps == null) return;
        AABB b = corps.getBoundingBox();
        b = b.move(x - (b.minX + b.maxX) / 2, y - b.minY, z - (b.minZ + b.maxZ) / 2);
        noter(x, y, z);
        double vx = v[0], vy = v[1], vz = v[2];
        double[] w = new double[3];
        for (int t = 0; t < TICKS; t++) {
            Vec3 mv = new Vec3(vx, vy, vz);
            Vec3 adj = Entity.collideBoundingBox(corps, mv, b, level, List.of());
            AABB nb = b.move(adj);
            double cx = (nb.minX + nb.maxX) / 2, cz = (nb.minZ + nb.maxZ) / 2;
            if (!level.hasChunkAt(BlockPos.containing(cx, nb.minY, cz))) {
                inconnu = true;
                poser((b.minX + b.maxX) / 2, b.minY, (b.minZ + b.maxZ) / 2, t);
                return;
            }
            b = nb;
            noter(cx, b.minY, cz);
            boolean hx = Math.abs(adj.x - mv.x) > 1e-7, hy = Math.abs(adj.y - mv.y) > 1e-7, hz = Math.abs(adj.z - mv.z) > 1e-7;
            if (hx || hz) mur = true;
            if (hy && mv.y < 0) {
                poser(cx, b.minY, cz, t + 1);
                valide = true;
                return;
            }
            if (!level.getFluidState(BlockPos.containing(cx, b.minY + 0.05, cz)).isEmpty()) {
                eau = true;
                poser(cx, b.minY, cz, t + 1);
                valide = true;
                return;
            }
            w[0] = hx ? 0 : adj.x;
            w[1] = hy ? 0 : adj.y;
            w[2] = hz ? 0 : adj.z;
            SautChargeLoi.avancerVitesse(w, r);
            vx = w[0];
            vy = w[1];
            vz = w[2];
        }
        // pas d'arrivée en 7 s (vide) : dernier point
        inconnu = true;
        poser((b.minX + b.maxX) / 2, b.minY, (b.minZ + b.maxZ) / 2, TICKS);
    }

    /**
     * Relief sous l'arc pour le bornage de l'arrivée ({@link SautChargeLoi#bornerArrivee}), relatif aux pieds au départ :
     * la carte des hauteurs {@code MOTION_BLOCKING}, la même que celle du serveur. Chunk absent : sol inconnu (le vol
     * continue, la borne joue).
     */
    public static SautChargeLoi.Sol relief(Level level, double x0, double y0, double z0) {
        return (dx, dz) -> {
            int bx = Mth.floor(x0 + dx), bz = Mth.floor(z0 + dz);
            if (level == null || !level.hasChunk(bx >> 4, bz >> 4)) return Double.NEGATIVE_INFINITY;
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - y0;
        };
    }

    private void noter(double x, double y, double z) {
        chX[points] = x;
        chY[points] = y;
        chZ[points] = z;
        points++;
    }

    private void poser(double x, double y, double z, int t) {
        ax = x;
        ay = y;
        az = z;
        ticks = t;
        distance = Math.hypot(x - x0, z - z0);
    }
}
