package fr.reborn.hud.meditation;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.ByteBuffer;

/**
 * <b>Halo de méditation</b>, côté client (18/10) — reprise native de l'effet Photon « meditation1 » (référence vidéo du
 * 17/10) : sous chaque joueur qui médite, un <b>disque de lumière</b> qui épouse le sol et respire lentement, et de
 * petites lueurs qui montent. Vu de tous les joueurs proches.
 *
 * <p>Qui médite : canal {@code reborn:meditation} (ShinobiCore {@code MeditationManager}) — liste des méditants à
 * moins de 48 blocs, identifiant réseau + couleur (vert de la référence, ou la nature de chakra si le serveur
 * l'active). Ici : jusqu'à {@value #CAPACITE} méditants suivis, fondu d'apparition / de disparition, et à chaque tick
 * le relevé du sol sous chacun (une colonne par bloc couvert : hauteur du dessus de la forme de collision, emprise
 * horizontale ; rien sous l'eau ni à travers un mur). Le rendu est dans {@link MeditationRendu}.
 *
 * <p>Rien n'est calculé ni dessiné quand personne ne médite. Tableaux préalloués, aucune allocation par tick.
 */
public final class MeditationClient {

    static final int CAPACITE = 16;
    /** Colonnes de sol par méditant (rayon 1,25 : au plus 4 × 4 blocs). */
    static final int COLONNES = 16;
    /** Rayon du disque (blocs) : 20 texels de 1/16. */
    static final float RAYON = 1.25f;
    /** Au-delà, ni relevé ni rendu (blocs) ; fondu sur les derniers blocs. */
    static final double PORTEE = 40, PORTEE_FONDU = 30;
    static final double FONDU_ENTREE = 1.2, FONDU_SORTIE = 1.4;
    /** Vert doux de la référence (lumière de chakra) si le serveur n'envoie pas de couleur. */
    static final int VERT = 0x86F59E;

    static final boolean[] ACTIF = new boolean[CAPACITE];
    static final int[] ID = new int[CAPACITE], RGB = new int[CAPACITE];
    static final double[] DEBUT = new double[CAPACITE], FIN = new double[CAPACITE];
    // relevé du dernier tick
    static final boolean[] VISIBLE = new boolean[CAPACITE];
    static final double[] CX = new double[CAPACITE], CY = new double[CAPACITE], CZ = new double[CAPACITE];
    /** Luminosité du lieu 0..1 (ciel ou blocs) : la nuit, le halo se voit davantage. */
    static final float[] LUM = new float[CAPACITE];
    static final int[] NQ = new int[CAPACITE];
    static final float[] QX0 = new float[CAPACITE * COLONNES], QX1 = new float[CAPACITE * COLONNES],
            QZ0 = new float[CAPACITE * COLONNES], QZ1 = new float[CAPACITE * COLONNES], QY = new float[CAPACITE * COLONNES];

    private static int actifs;
    /** Banc de captures ({@code REBORN_MEDITATION_DEBUG}). */
    public static boolean debug;
    /** Horloge imposée par le banc pendant un film (NaN = temps réel). */
    static double horlogeFilm = Double.NaN;

    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    private MeditationClient() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(MeditationPayload.ID, MeditationPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MeditationPayload.ID,
                (payload, context) -> context.client().execute(() -> recevoir(payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> vider());
        ClientTickEvents.END_CLIENT_TICK.register(MeditationClient::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(MeditationRendu::apresTranslucide);
        MeditationDebug.init();
    }

    /** Temps de l'effet (secondes) : temps réel, ou l'horloge du banc pendant un film. */
    static double temps() {
        return Double.isNaN(horlogeFilm) ? System.nanoTime() / 1e9 : horlogeFilm;
    }

    static boolean aucun() { return actifs == 0; }

    static void vider() {
        for (int i = 0; i < CAPACITE; i++) { ACTIF[i] = false; VISIBLE[i] = false; }
        actifs = 0;
        MeditationRendu.viderParticules();
    }

    static void recevoir(byte[] d) {
        if (d == null || d.length < 5) return;
        ByteBuffer b = ByteBuffer.wrap(d);
        if (b.get() != 0) return;
        int n = b.getInt();
        double now = temps();
        // tout ce qui n'est plus dans la liste s'éteint en fondu
        for (int i = 0; i < CAPACITE; i++) if (ACTIF[i] && Double.isNaN(FIN[i])) FIN[i] = now;
        for (int k = 0; k < n && b.remaining() >= 8; k++) {
            int id = b.getInt(), rgb = b.getInt();
            definir(id, rgb == 0 ? VERT : rgb & 0xFFFFFF, true, now);
        }
    }

    /** Ajoute (ou relance) / éteint le halo d'une entité. Utilisé par le canal et par le banc. */
    static void definir(int id, int rgb, boolean on, double now) {
        int libre = -1;
        for (int i = 0; i < CAPACITE; i++) {
            if (ACTIF[i] && ID[i] == id) {
                if (on) {
                    if (!Double.isNaN(FIN[i])) {                       // il se rassoit pendant le fondu de sortie
                        double reste = Math.max(0, 1 - (now - FIN[i]) / FONDU_SORTIE) * fondu(i, FIN[i]);
                        DEBUT[i] = now - reste * FONDU_ENTREE;
                        FIN[i] = Double.NaN;
                    }
                    RGB[i] = rgb;
                } else if (Double.isNaN(FIN[i])) {
                    FIN[i] = now;
                }
                return;
            }
            if (!ACTIF[i] && libre < 0) libre = i;
        }
        if (!on || libre < 0) return;
        ACTIF[libre] = true;
        ID[libre] = id;
        RGB[libre] = rgb;
        DEBUT[libre] = now;
        FIN[libre] = Double.NaN;
        VISIBLE[libre] = false;
        NQ[libre] = 0;
        actifs++;
    }

    /** Opacité 0..1 du halo {@code i} au temps {@code now} (fondus d'entrée et de sortie, lissés). */
    static double fondu(int i, double now) {
        double e = Math.min(1, Math.max(0, (now - DEBUT[i]) / FONDU_ENTREE));
        double v = e * e * (3 - 2 * e);
        if (!Double.isNaN(FIN[i])) {
            double s = Math.min(1, Math.max(0, 1 - (now - FIN[i]) / FONDU_SORTIE));
            v *= s * s * (3 - 2 * s);
        }
        return v;
    }

    private static void tick(Minecraft mc) {
        if (actifs == 0) return;
        ClientLevel level = mc.level;
        if (level == null) { vider(); return; }
        double now = temps();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        for (int i = 0; i < CAPACITE; i++) {
            if (!ACTIF[i]) continue;
            if (!Double.isNaN(FIN[i]) && now - FIN[i] > FONDU_SORTIE) {
                ACTIF[i] = false;
                VISIBLE[i] = false;
                actifs--;
                continue;
            }
            Entity e = level.getEntity(ID[i]);
            if (e == null || e.isRemoved()) { VISIBLE[i] = false; continue; }
            double dx = e.getX() - cam.x, dz = e.getZ() - cam.z, dy = e.getY() - cam.y;
            if (dx * dx + dy * dy + dz * dz > PORTEE * PORTEE) { VISIBLE[i] = false; continue; }
            releverSol(level, i, e);
        }
    }

    /**
     * Le sol sous le méditant : pour chaque bloc couvert par le disque, le dessus de la première forme de collision
     * en descendant depuis une marche au-dessus de l'assise (au plus 3 blocs sous l'assise) ; colonne ignorée si un
     * obstacle dépasse d'une marche (mur) ou s'il y a de l'eau.
     */
    private static void releverSol(ClientLevel level, int i, Entity e) {
        Entity v = e.getVehicle();
        double base = v != null ? v.getY() : e.getY();                // assise (support invisible posé sur le bloc)
        double cx = e.getX(), cz = e.getZ();
        CX[i] = cx;
        CY[i] = base;
        CZ[i] = cz;
        int n = 0;
        int x0 = (int) Math.floor(cx - RAYON), x1 = (int) Math.floor(cx + RAYON);
        int z0 = (int) Math.floor(cz - RAYON), z1 = (int) Math.floor(cz + RAYON);
        int yHaut = (int) Math.floor(base + 1.0);
        for (int bx = x0; bx <= x1 && n < COLONNES; bx++) {
            for (int bz = z0; bz <= z1 && n < COLONNES; bz++) {
                // coin du bloc le plus proche du centre hors du disque : rien à dessiner
                double px = Math.max(bx, Math.min(cx, bx + 1)), pz = Math.max(bz, Math.min(cz, bz + 1));
                if ((px - cx) * (px - cx) + (pz - cz) * (pz - cz) >= RAYON * RAYON) continue;
                for (int y = yHaut; y >= yHaut - 4; y--) {
                    M.set(bx, y, bz);
                    BlockState st = level.getBlockState(M);
                    if (!st.getFluidState().isEmpty()) break;          // eau, lave : pas de halo
                    VoxelShape s = st.getCollisionShape(level, M);
                    if (s.isEmpty()) continue;
                    double top = y + s.max(Direction.Axis.Y);
                    if (top > base + 1.05) break;                       // un mur (plus d'une marche) : colonne ignorée
                    AABB bb = s.bounds();
                    float qx0 = (float) Math.max(bx + bb.minX, cx - RAYON), qx1 = (float) Math.min(bx + bb.maxX, cx + RAYON);
                    float qz0 = (float) Math.max(bz + bb.minZ, cz - RAYON), qz1 = (float) Math.min(bz + bb.maxZ, cz + RAYON);
                    if (qx1 - qx0 > 1e-3 && qz1 - qz0 > 1e-3) {
                        int q = i * COLONNES + n++;
                        QX0[q] = qx0; QX1[q] = qx1; QZ0[q] = qz0; QZ1[q] = qz1; QY[q] = (float) top;
                    }
                    break;
                }
            }
        }
        NQ[i] = n;
        M.set((int) Math.floor(cx), (int) Math.floor(base + 0.5), (int) Math.floor(cz));
        LUM[i] = level.getMaxLocalRawBrightness(M) / 15f;
        VISIBLE[i] = true;
    }
}
