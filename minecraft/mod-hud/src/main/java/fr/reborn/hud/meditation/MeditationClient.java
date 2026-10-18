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
 * <b>Halo de méditation</b>, côté client (v2 du 18/10, refait sur la vidéo de référence du 17/10) : sous chaque joueur
 * qui médite, un <b>disque sombre</b> net entouré d'une <b>couronne de lumière</b> qui éclaire le sol et s'estompe vers
 * l'extérieur (respiration lente), de petites <b>étincelles</b> qui scintillent autour du corps et la <b>pose en
 * tailleur</b> ({@link MeditationAnimation}). Vu de tous les
 * joueurs proches, à la <b>couleur d'aura choisie par le joueur</b> (formes et teintes : {@link Aura}).
 *
 * <p>Canal {@code reborn:meditation} (ShinobiCore {@code MeditationManager}) — serveur → client : type 0, la liste des
 * méditants à moins de 48 blocs (identifiant réseau + couleur d'aura) ; type 1, la couleur d'aura du personnage actif.
 * Client → serveur : 1 demander sa couleur, 2 l'enregistrer (+ RGB), 3 la remettre par défaut. Ici : jusqu'à
 * {@value #CAPACITE} méditants suivis, fondu d'apparition / de disparition, et à chaque tick le relevé du sol sous
 * chacun (une colonne par bloc couvert : hauteur du dessus de la forme de collision, emprise horizontale, lumière du
 * lieu ; rien sous l'eau ni à travers un mur). Le rendu est dans {@link MeditationRendu}.
 *
 * <p>Rien n'est calculé ni dessiné quand personne ne médite. Tableaux préalloués, aucune allocation par tick.
 */
public final class MeditationClient {

    static final int CAPACITE = 16;
    /** Colonnes de sol par méditant (rayon 1,1 : au plus 3 × 3 blocs, 4 × 4 au pire). */
    static final int COLONNES = 16;
    /** Rayon de l'effet au sol (blocs) : disque sombre puis couronne jusqu'à {@link Aura#R_HALO}. */
    static final float RAYON = Aura.R_HALO;
    /** Au-delà, ni relevé ni rendu (blocs) ; fondu sur les derniers blocs. */
    static final double PORTEE = 40, PORTEE_FONDU = 30;
    static final double FONDU_ENTREE = 1.2, FONDU_SORTIE = 1.4;
    /** Menthe de la référence si le serveur n'envoie pas de couleur. */
    static final int VERT = Aura.DEFAUT;

    /** Couleur d'aura du personnage actif (réponse du serveur, type 1) et vrai si le joueur l'a choisie. */
    private static int maCouleur = Aura.DEFAUT;
    private static boolean maCouleurChoisie, maCouleurConnue;

    static final boolean[] ACTIF = new boolean[CAPACITE];
    static final int[] ID = new int[CAPACITE], RGB = new int[CAPACITE];
    static final double[] DEBUT = new double[CAPACITE], FIN = new double[CAPACITE];
    // relevé du dernier tick
    static final boolean[] VISIBLE = new boolean[CAPACITE];
    static final double[] CX = new double[CAPACITE], CY = new double[CAPACITE], CZ = new double[CAPACITE];
    /** Luminosité du lieu 0..1 (ciel ou blocs) : la nuit, le halo se voit davantage. */
    static final float[] LUM = new float[CAPACITE];
    /** Lumière du lieu, coordonnées de lightmap (le disque sombre est éclairé comme le sol). */
    static final int[] LUMIERE = new int[CAPACITE];
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
        PayloadTypeRegistry.serverboundPlay().register(MeditationPayload.ID, MeditationPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MeditationPayload.ID,
                (payload, context) -> context.client().execute(() -> recevoir(payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { vider(); maCouleurConnue = false; });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(MeditationRendu::prechauffer));
        ClientTickEvents.END_CLIENT_TICK.register(MeditationClient::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(MeditationRendu::apresTranslucide);
        MeditationAnimation.register();
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
        MeditationAnimation.vider();
    }

    static void recevoir(byte[] d) {
        if (d == null || d.length < 5) return;
        ByteBuffer b = ByteBuffer.wrap(d);
        byte type = b.get();
        if (type == 1) {                                                // la couleur d'aura du personnage actif
            maCouleur = b.getInt() & 0xFFFFFF;
            maCouleurChoisie = b.remaining() > 0 && b.get() != 0;
            maCouleurConnue = true;
            return;
        }
        if (type != 0) return;
        int n = b.getInt();
        double now = temps();
        // tout ce qui n'est plus dans la liste s'éteint en fondu
        for (int i = 0; i < CAPACITE; i++) if (ACTIF[i] && Double.isNaN(FIN[i])) FIN[i] = now;
        for (int k = 0; k < n && b.remaining() >= 8; k++) {
            int id = b.getInt(), rgb = b.getInt();
            definir(id, rgb == 0 ? VERT : rgb & 0xFFFFFF, true, now);
        }
    }

    /* ------------------------------------------------------------------ couleur d'aura (choix du joueur) */

    /** Couleur d'aura du personnage actif telle que connue (défaut tant que le serveur n'a pas répondu). */
    public static int maCouleur() { return maCouleur; }

    /** Vrai si le serveur a répondu (type 1) depuis la connexion. */
    public static boolean maCouleurConnue() { return maCouleurConnue; }

    /** Vrai si le joueur a choisi sa couleur (sinon : celle du serveur par défaut). */
    public static boolean maCouleurChoisie() { return maCouleurChoisie; }

    /** Vrai si le serveur connaît le canal (ShinobiCore à jour) : sinon le choix ne peut pas être enregistré. */
    public static boolean serveurPret() { return ClientPlayNetworking.canSend(MeditationPayload.ID); }

    /** Demande au serveur la couleur d'aura du personnage actif (réponse : type 1). */
    public static void demanderCouleur() {
        if (serveurPret()) ClientPlayNetworking.send(new MeditationPayload(new byte[]{1}));
    }

    /** Enregistre la couleur d'aura du personnage (serveur : type 2), ou la remet par défaut ({@code rgb < 0}, type 3). */
    public static void choisirCouleur(int rgb) {
        if (!serveurPret()) return;
        byte[] d;
        if (rgb < 0) {
            d = new byte[]{3};
        } else {
            d = ByteBuffer.allocate(5).put((byte) 2).putInt(rgb & 0xFFFFFF).array();
            maCouleur = rgb & 0xFFFFFF;
            maCouleurChoisie = true;
        }
        ClientPlayNetworking.send(new MeditationPayload(d));
    }

    /**
     * Aperçu en direct pendant le choix : si le joueur médite, son halo prend tout de suite la couleur essayée (le
     * serveur renverra la couleur enregistrée à la prochaine liste).
     */
    public static void essayer(int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int id = mc.player.getId();
        for (int i = 0; i < CAPACITE; i++) if (ACTIF[i] && ID[i] == id && Double.isNaN(FIN[i])) RGB[i] = rgb & 0xFFFFFF;
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
        MeditationAnimation.tick(level);                                 // la pose en tailleur, sur chacun
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
        LUMIERE[i] = net.minecraft.util.LightCoordsUtil.getLightCoords(level, M);
        VISIBLE[i] = true;
    }
}
