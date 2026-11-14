package fr.reborn.hud.combat;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.reborn.hud.byakugan.ByakuganClient;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * <b>Trajectoire en poussière</b> du saut chargé (lot 5, étape A, 6e tour du 10/11). Le rendu que le propriétaire aimait
 * (0.4.64 à 0.4.68 : poussière de redstone violet chakra le long de la parabole, anneau de poussière à l'arrivée), repris
 * sur l'<b>arc exact</b> ({@link SautArc} : la nouvelle loi, la collision, le bornage, jusqu'à ~92 blocs). Les aperçus
 * suivants (zone bleue, quatre marqueurs sobres, ruban crème façon Ketto avec anneau fin et barre dans le monde) sont
 * refusés et retirés ; la barre de charge est à l'écran, près du réticule ({@link SautHud}).
 *
 * <p><b>Des grains, pas des particules.</b> Le sprite du jeu ({@code generic_7} à {@code generic_3} de l'atlas des
 * particules : le grain épars de la poussière de redstone), teinté de {@link #VIOLET}, est dessiné ici à chaque image, face
 * à la caméra, en pixels nets. De vraies particules restaient à l'ancienne visée quand on tourne la tête (traînées), leur
 * nombre n'était pas borné sur 90 blocs et elles écrivent la profondeur (le Byakugan, qui trace son encre aux sauts de
 * profondeur, les cernait de bruit blanc). Ici : grains toujours sur l'arc du moment, au plus {@value #MAX_LIGNE} sur la
 * ligne et {@value #ANNEAU_N} sur l'anneau, aucune écriture de profondeur.
 *
 * <p><b>Vivant</b> comme la poussière du jeu, sans hasard par image : chaque grain a sa période (0,55 à 1,35 s) et sa
 * phase ; à chaque cycle il « renaît » avec un petit écart et une luminosité tirés d'un hachage (grain, cycle), puis son
 * sprite décroît (gros grain → petit) en dérivant à peine. Mêmes écarts de teinte que la poussière vanilla.
 *
 * <p><b>Un fil, pas une colonne</b> : un grain fait {@value #TAILLE} bloc (la perspective vraie : gros près, fin au
 * loin), jamais moins de {@value #PX_MIN} px ni plus de {@value #PX_MAX_1P} px en 1re personne, {@value #PX_MAX_3P} en 3e
 * (en 1080 p) ; il ne s'écarte de l'arc que de {@value #ECART} fois sa taille ; l'espacement est réglé à l'écran (un
 * pointillé régulier de {@value #PAS} fois la taille du grain, même là où l'arc fuit droit devant), élargi si la ligne
 * dépasserait {@value #MAX_LIGNE} grains. <b>1re personne</b> : un arc vu de dos se projette en ligne verticale, la montée
 * proche par-dessus la descente lointaine ; la ligne n'y naît donc que sur les {@value #LEVEE_1P} blocs qui précèdent le
 * sommet (jamais avant {@value #NAIT_1P_0} blocs d'arc : rien au bas de l'écran) et s'efface autour du réticule (de
 * {@value #RETICULE_0} à {@value #RETICULE_1} px). L'anneau : {@value #ANNEAU_N} grains posés au sol, au moins
 * {@value #ANNEAU_PX_MIN} px de diamètre ; une seconde passe le montre à travers le décor ({@value #TRAVERS_A}
 * d'opacité). Sous le Byakugan, qui ne garde que la luminance, la poussière passe en lavande presque blanche
 * ({@link #PALE}) : elle se lit comme son encre au lieu de virer au gris sale.
 *
 * <p>Fondus en temps réel : rien sous le seuil (rien ne partirait), apparition en {@value #SEUIL_MS} ms au seuil,
 * effacement en {@value #FIN_MS} ms à la relâche ou à l'annulation. Aucune allocation par image : tableaux fixes,
 * émetteurs sans capture.
 */
public final class SautApercu {

    /** Violet chakra de la poussière (celui de 0.4.64) ; la barre de charge le reprend. */
    static final int VIOLET = 0x9B7FE0;

    /* grains : bornes, taille (bloc), taille vue (px en 1080 p : au moins, au plus en 1re et en 3e personne), pas à l'écran
       (part de la taille), écart à la naissance (part de la taille), hauteur au-dessus des pieds */
    private static final int MAX_LIGNE = 300, ANNEAU_N = 48, MAX = MAX_LIGNE + ANNEAU_N;
    private static final float TAILLE = 0.2f, PX_MIN = 5f, PX_MAX_1P = 9f, PX_MAX_3P = 12f, PAS = 0.75f, TRAVERS_MIN = 0.3f,
        ECART = 0.18f, HAUT = 0.4f, HAUT_DESCENTE = 2f;
    /* naissance le long de l'arc (blocs depuis le départ). 1re personne : la montée, sous et devant les yeux, se superpose à
       l'écran à la descente lointaine (un arc vu de dos est une ligne verticale) : la ligne naît sur les LEVEE_1P blocs qui
       précèdent le sommet, jamais avant NAIT_1P_0 ni pleine avant NAIT_1P_1 (saut à plat ou vers le bas). 3e : hors de la tête */
    private static final float NAIT_1P_0 = 1.8f, NAIT_1P_1 = 6f, LEVEE_1P = 2.5f, NAIT_3P_0 = 0.8f, NAIT_3P_1 = 2.2f;
    /** Longueur d'arc, avant l'arrivée, sur laquelle la ligne s'efface dans l'anneau. */
    private static final float FIN_LIGNE = 1.2f;
    /** Rayon autour du réticule où la poussière s'efface (px en 1080 p : nulle dedans, pleine au-delà du second). */
    private static final float RETICULE_0 = 22f, RETICULE_1 = 50f;
    /* anneau : rayon de près (bloc), diamètre vu au moins (px en 1080 p), rayon au plus, regard tombant pour l'allonge */
    private static final float ANNEAU_R = 0.55f, ANNEAU_PX_MIN = 90f, ANNEAU_MAX = 4f, SIN_VUE = 0.45f, ALLONGE_MAX = 8f,
        ANNEAU_PX = 5f, TRAVERS_A = 0.35f, ANNEAU_TOUR_S = 14f;
    /** Sous le Byakugan (qui ne garde que la luminance) : lavande presque blanche, claire comme son encre. */
    private static final int PALE = 0xF0EAFF;
    private static final float[] COS = new float[ANNEAU_N], SIN = new float[ANNEAU_N];

    static {
        for (int i = 0; i < ANNEAU_N; i++) {
            double t = 2 * Math.PI * i / ANNEAU_N;
            COS[i] = (float) Math.cos(t);
            SIN[i] = (float) Math.sin(t);
        }
    }

    /* fondus (temps réel) */
    private static final long APPARITION_MS = 100L, SEUIL_MS = 150L, FIN_MS = 150L;
    private static long chargeVue = -1, seuilVu = -1, finVue = -1;
    private static float aBarre, aLigne, aBarreFin, aLigneFin, cBarre;
    /** Teinte des grains à cette image : {@link #VIOLET}, vers {@link #PALE} sous le Byakugan. */
    private static int teinte = VIOLET;

    /* grains préparés à la collecte (relatifs à la caméra) : centre, demi-taille, couleur, sprite ; ligne puis anneau */
    private static final int LUMIERE = LightCoordsUtil.FULL_BRIGHT;
    private static final float[] GX = new float[MAX], GY = new float[MAX], GZ = new float[MAX], GH = new float[MAX];
    private static final int[] GC = new int[MAX], GS = new int[MAX];
    private static int nLigne, nGrains;
    /* base de la caméra : droite, haut, avant */
    private static float rx, rz, ux, uy, uz, fx, fy, fz;
    /* sprites generic_0 à 7 de l'atlas des particules, relus à chaque image (un rechargement de ressources les déplace) */
    private static final Identifier[] SPRITES = new Identifier[8];
    private static final float[] U0 = new float[8], U1 = new float[8], V0 = new float[8], V1 = new float[8];

    static {
        for (int i = 0; i < 8; i++) SPRITES[i] = Identifier.withDefaultNamespace("generic_" + i);
    }

    /** Types de rendu (créés une fois) : testé contre le décor, et à travers lui ; aucun n'écrit la profondeur. */
    private static RenderType rtVu, rtTravers;
    private static boolean rtEchec;

    private SautApercu() {}

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(SautApercu::collecter);
    }

    /* ================================================================ fondus */

    /** Met les fondus à jour (sans effet de bord si on l'appelle deux fois dans la même image). */
    static void maj() {
        long now = Util.getMillis();
        ChakraJump j = ChakraJump.INSTANCE;
        if (j.phase() == ChakraJump.Phase.CHARGE) {
            if (chargeVue < 0) { chargeVue = now; seuilVu = -1; finVue = -1; }
            double c = j.charge();
            cBarre = (float) c;
            if (seuilVu < 0 && SautChargeLoi.suffisante(c, j.regles())) seuilVu = now;
            aBarre = Math.min(1f, (now - chargeVue) / (float) APPARITION_MS);
            aLigne = seuilVu < 0 ? 0f : Math.min(1f, (now - seuilVu) / (float) SEUIL_MS);
            return;
        }
        if (chargeVue >= 0) {
            chargeVue = -1;
            finVue = now;
            aBarreFin = aBarre;
            aLigneFin = aLigne;
        }
        float f = finVue < 0 ? 0f : 1f - (now - finVue) / (float) FIN_MS;
        if (f <= 0f) { finVue = -1; f = 0f; }
        aBarre = aBarreFin * f;
        aLigne = aLigneFin * f;
    }

    /** Opacité de la barre de charge maintenant (0 = rien). */
    static float barre() { return aBarre; }

    /** Charge montrée par la barre (celle de la relâche pendant le fondu de sortie). */
    static float chargeBarre() { return cBarre; }

    /** Opacité de la trajectoire et de l'anneau maintenant (0 = rien). Lue aussi par le banc. */
    public static float ligne() {
        maj();
        return montrable() ? aLigne : 0f;
    }

    /** Grains préparés à la dernière image : sur la ligne, en tout (anneau compris). Lu par le banc. */
    public static int grainsLigne() { return nLigne; }
    public static int grains() { return nGrains; }

    /** Arrivée connue : serveur du lot 5 (sinon l'ancien saut, sans aperçu), arc trouvé, pas d'arrivée inventée. */
    private static boolean montrable() {
        ChakraJump j = ChakraJump.INSTANCE;
        SautArc a = j.arc();
        return j.serveurConnu() && a.valide() && !a.inconnu() && a.points() >= 2;
    }

    /* ================================================================ collecte */

    private static void collecter(LevelRenderContext ctx) {
        maj();
        nLigne = 0;
        nGrains = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        if (aLigne <= 0.01f || !montrable() || !types() || !sprites(mc)) return;
        var cs = ctx.levelState().cameraRenderState;
        Vec3 cam = cs.pos;
        if (cam == null) return;
        double yr = Math.toRadians(cs.yRot), xr = Math.toRadians(cs.xRot);
        fx = (float) (-Math.sin(yr) * Math.cos(xr));
        fy = (float) -Math.sin(xr);
        fz = (float) (Math.cos(yr) * Math.cos(xr));
        rx = (float) -Math.cos(yr);
        rz = (float) -Math.sin(yr);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        // taille d'un pixel de l'écran, en blocs par bloc de distance ; tailles en px données pour 1080 p
        double fov = Math.toRadians(mc.options.fov().get());
        float hauteur = Math.max(1, mc.getWindow().getHeight());
        float pxMonde = (float) (2 * Math.tan(fov / 2) / hauteur), k1080 = hauteur / 1080f;
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        teinte = ByakuganClient.filtreAffiche() ? melange(VIOLET, PALE, ByakuganClient.niveau()) : VIOLET;
        boolean premiere = mc.options.getCameraType().isFirstPerson();
        float pxMax = premiere ? PX_MAX_1P : PX_MAX_3P;
        SautArc arc = ChakraJump.INSTANCE.arc();
        preparerAnneau(arc, cam, pxMonde * k1080, pxMax, t);
        preparerLigne(arc, cam, pxMonde * k1080, pxMax, t, premiere);
        poserAnneau(t);
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), rtTravers, TRAVERS_GEO);
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), rtVu, VU_GEO);
    }

    private static int melange(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
        return r << 16 | g << 8 | Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
    }

    /** Coordonnées des huit sprites de grain dans l'atlas des particules (faux si l'atlas manque). */
    private static boolean sprites(Minecraft mc) {
        if (!(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES) instanceof TextureAtlas at)) return false;
        for (int i = 0; i < 8; i++) {
            TextureAtlasSprite s = at.getSprite(SPRITES[i]);
            U0[i] = s.getU0();
            U1[i] = s.getU1();
            V0[i] = s.getV0();
            V1[i] = s.getV1();
        }
        return true;
    }

    /**
     * La ligne : de grain en grain le long du chemin des pieds (relevé de {@value #HAUT} bloc, qui redescend sur les
     * {@value #HAUT_DESCENTE} derniers blocs jusqu'au centre de l'anneau). Deux passes : on compte, puis, si la ligne
     * dépasse {@value #MAX_LIGNE} grains, on élargit l'espacement d'autant et on écrit. En 1re personne, la ligne naît
     * juste avant le sommet (seule la descente se voit : une ligne fine, pas une colonne).
     */
    private static void preparerLigne(SautArc arc, Vec3 cam, float px1080, float pxMax, float t, boolean premiere) {
        int n = arc.points();
        double fin = 0, sommet = 0, haut = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < n; i++) {
            fin += segment(arc, i);
            if (arc.cheminY(i) > haut) { haut = arc.cheminY(i); sommet = fin; }
        }
        if (fin <= 0.5) return;
        float nait0 = NAIT_3P_0, nait1 = NAIT_3P_1;
        if (premiere) {
            nait1 = Math.max(NAIT_1P_1, (float) sommet);
            nait0 = Math.max(NAIT_1P_0, Math.min(nait1 - LEVEE_1P, (float) sommet - LEVEE_1P));
        }
        int compte = poser(arc, cam, px1080, pxMax, t, fin, 1f, nait0, nait1, false);
        float etirer = compte > MAX_LIGNE - 4 ? compte / (float) (MAX_LIGNE - 4) : 1f;
        poser(arc, cam, px1080, pxMax, t, fin, etirer, nait0, nait1, true);
    }

    private static double segment(SautArc a, int i) {
        double dx = a.cheminX(i) - a.cheminX(i - 1), dy = a.cheminY(i) - a.cheminY(i - 1), dz = a.cheminZ(i) - a.cheminZ(i - 1);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Pose (ou seulement compte) les grains de la ligne, abscisse curviligne {@code s} de 0 à {@code fin}. L'espacement est
     * réglé <b>à l'écran</b> : deux grains voisins s'y écartent de {@value #PAS} fois leur taille vue, quelle que soit la
     * fuite de l'arc (un arc qui s'éloigne droit devant ne s'entasse plus en colonne) : pas = taille × {@value #PAS} / part
     * de la tangente en travers du regard (au moins {@value #TRAVERS_MIN}), × {@code etirer}.
     */
    private static int poser(SautArc a, Vec3 cam, float px1080, float pxMax, float t, double fin, float etirer,
                             float nait0, float nait1, boolean ecrire) {
        int n = a.points(), j = 1, compte = 0, poses = 0;
        double debutSeg = 0, lSeg = segment(a, 1), s = 0;
        float minPx = PX_MIN * px1080, maxPx = pxMax * px1080;
        float ret0 = RETICULE_0 * px1080, ret1 = RETICULE_1 * px1080;
        while (s <= fin && compte < MAX_LIGNE * 8) {
            while (s > debutSeg + lSeg && j + 1 < n) {
                debutSeg += lSeg;
                j++;
                lSeg = segment(a, j);
            }
            double u = lSeg < 1e-6 ? 0 : Math.min(1, (s - debutSeg) / lSeg);
            double sx = a.cheminX(j) - a.cheminX(j - 1), sy = a.cheminY(j) - a.cheminY(j - 1), sz = a.cheminZ(j) - a.cheminZ(j - 1);
            float h = HAUT * (float) Math.min(1.0, (fin - s) / HAUT_DESCENTE);
            float x = (float) (a.cheminX(j - 1) + sx * u - cam.x), z = (float) (a.cheminZ(j - 1) + sz * u - cam.z);
            float y = (float) (a.cheminY(j - 1) + sy * u - cam.y) + h;
            float dc = (float) Math.max(1e-3, Math.sqrt(x * x + y * y + z * z));
            float w = Math.max(minPx * dc, Math.min(maxPx * dc, TAILLE));
            if (ecrire && poses < MAX_LIGNE) {
                // naissance le long de l'arc, fin juste avant l'anneau (son centre reste net), réticule dégagé (tangente de
                // l'écart au regard, contre la taille d'un px)
                float a0 = lisse(((float) s - nait0) / (nait1 - nait0)) * lisse((float) (fin - s) / FIN_LIGNE);
                float av = (x * fx + y * fy + z * fz) / dc;
                if (av > 0f) a0 *= lisse(((float) Math.sqrt(Math.max(0f, 1f - av * av)) / av - ret0) / (ret1 - ret0));
                if (a0 > 0.02f) grain(poses++, compte, x, y, z, w, a0 * aLigne, t);
            }
            compte++;
            // part de la tangente en travers du regard
            double ts = lSeg < 1e-6 ? 0 : (sx * x + sy * y + sz * z) / (lSeg * dc);
            float travers = (float) Math.max(TRAVERS_MIN, Math.sqrt(Math.max(0, 1 - ts * ts)));
            s += w * PAS * etirer / travers;
        }
        if (ecrire) nLigne = nGrains = poses;
        return compte;
    }

    private static float lisse(float v) {
        v = v < 0f ? 0f : v > 1f ? 1f : v;
        return v * v * (3f - 2f * v);
    }

    /* anneau : ses grains, préparés avant la ligne et copiés après elle */
    private static final float[] AX = new float[ANNEAU_N], AY = new float[ANNEAU_N], AZ = new float[ANNEAU_N],
        AH = new float[ANNEAU_N];

    /**
     * L'anneau autour de l'arrivée : {@value #ANNEAU_N} grains sur une ellipse au sol, un bloc de diamètre de près, jamais
     * moins de {@value #ANNEAU_PX_MIN} px au loin, allongée dans l'axe du regard quand on la voit en rasant (au plus ×
     * {@value #ALLONGE_MAX}), qui tourne d'un tour en {@value #ANNEAU_TOUR_S} s. Grains d'au moins {@value #ANNEAU_PX} px,
     * pleins et clairs : un cercle net qui se lit à 60 blocs, pas une tache.
     */
    private static void preparerAnneau(SautArc a, Vec3 cam, float px1080, float pxMax, float t) {
        float cx = (float) (a.x() - cam.x), cy = (float) (a.y() - cam.y), cz = (float) (a.z() - cam.z);
        float d = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float hl = (float) Math.sqrt(cx * cx + cz * cz), ahx, ahz;
        if (hl < 1e-3f) { ahx = 0f; ahz = 1f; } else { ahx = cx / hl; ahz = cz / hl; }
        float sinE = d < 1e-3f ? 1f : -cy / d;
        float r = Math.min(ANNEAU_MAX, Math.max(ANNEAU_R, ANNEAU_PX_MIN / 2f * px1080 * d));
        float rf = r * (sinE > 0.002f ? Math.max(1f, Math.min(ALLONGE_MAX, SIN_VUE / sinE)) : ALLONGE_MAX);
        float minPx = ANNEAU_PX * px1080, maxPx = pxMax * px1080;
        float tour = (float) (2 * Math.PI * (t / ANNEAU_TOUR_S % 1f)), ct = (float) Math.cos(tour), st = (float) Math.sin(tour);
        for (int i = 0; i < ANNEAU_N; i++) {
            float c = COS[i] * ct - SIN[i] * st, s = SIN[i] * ct + COS[i] * st;
            float x = cx - ahz * r * c + ahx * rf * s, z = cz + ahx * r * c + ahz * rf * s;
            float dc = (float) Math.sqrt(x * x + cy * cy + z * z);
            float w = Math.max(minPx * dc, Math.min(maxPx * dc, TAILLE));
            AX[i] = x;
            AY[i] = cy + w * 0.45f + 0.02f;
            AZ[i] = z;
            AH[i] = w;
        }
    }

    /** Copie les grains de l'anneau à la suite de la ligne (après {@link #preparerLigne}). */
    private static void poserAnneau(float t) {
        for (int i = 0; i < ANNEAU_N; i++) grain(nGrains + i, 100_000 + i, AX[i], AY[i], AZ[i], AH[i], aLigne, t);
        nGrains += ANNEAU_N;
    }

    /**
     * Le grain {@code graine} (son rang sur la ligne ou l'anneau : sa vie propre) écrit à la case {@code i} : position
     * écartée selon son cycle (au plus {@value #ECART} × sa taille, ×1,5 en fin de vie : le grain reste sur l'arc),
     * demi-taille, sprite qui décroît du gros grain au petit, luminosité (0,8 à 1, puis 0,85 à 1 par canal : les écarts
     * de la poussière vanilla, resserrés pour qu'aucun grain ne se délave sur le ciel), alpha {@code a}.
     */
    private static void grain(int i, int graine, float x, float y, float z, float w, float a, float t) {
        int h = hache(graine * 0x9E3779B1 + 0x632BE5AB);
        float periode = 0.55f + 0.8f * unite(h), phase = unite(h >>> 8);
        float cycle = t / periode + phase;
        int k = (int) Math.floor(cycle);
        float age = cycle - k;
        int hk = hache(h ^ (k * 0x85EBCA6B));
        float taille = w * (0.85f + 0.3f * unite(h >>> 16)), e = ECART * taille * (1f + 0.5f * age);
        // petit écart à la naissance, dérive lente pendant la vie (dans le plan de l'écran)
        float ex = (unite(hk) - 0.5f) * e, ey = (unite(hk >>> 8) - 0.5f) * e;
        GX[i] = x + rx * ex + ux * ey;
        GY[i] = y + uy * ey;
        GZ[i] = z + rz * ex + uz * ey;
        GH[i] = taille / 2f;
        GS[i] = 7 - Math.min(4, (int) (age * 5f));
        float lum = 0.8f + 0.2f * unite(hk >>> 16);
        int r = canal(teinte >> 16, lum, hk >>> 4), g = canal(teinte >> 8, lum, hk >>> 12), b = canal(teinte, lum, hk >>> 20);
        GC[i] = Math.round(255 * Math.max(0f, Math.min(1f, a))) << 24 | r << 16 | g << 8 | b;
    }

    private static int canal(int v, float lum, int h) {
        return Math.min(255, Math.round((v & 0xFF) * lum * (0.85f + 0.15f * unite(h))));
    }

    private static int hache(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return x;
    }

    /** 8 bits du hachage en [0, 1). */
    private static float unite(int h) { return (h & 0xFF) / 256f; }

    /* ================================================================ types de rendu */

    /**
     * Le shader d'entité du jeu (émissif, sans voile ni éclairage directionnel), texture : l'atlas des particules en
     * échantillonnage net, mélange translucide, sans écriture de profondeur (le filtre du Byakugan ne cerne pas les grains).
     */
    private static boolean types() {
        if (rtVu != null) return true;
        if (rtEchec) return false;
        try {
            Method creer = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            creer.setAccessible(true);
            rtVu = (RenderType) creer.invoke(null, "reborn_saut_poussiere",
                reglage(pipeline("saut_poussiere", DepthStencilState.DEFAULT.depthTest())));
            rtTravers = (RenderType) creer.invoke(null, "reborn_saut_poussiere_travers",
                reglage(pipeline("saut_poussiere_travers", CompareOp.ALWAYS_PASS)));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            rtEchec = true;
            rtVu = null;
            org.slf4j.LoggerFactory.getLogger("reborn-hud").warn("Saut : type de rendu de la poussière indisponible ({})",
                e.toString());
            return false;
        }
    }

    private static RenderPipeline pipeline(String nom, CompareOp test) {
        return RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withLocation(Identifier.fromNamespaceAndPath("reborn", "pipeline/" + nom))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withShaderDefine("EMISSIVE")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(test, false))
            .withCull(false)
            .build();
    }

    private static RenderSetup reglage(RenderPipeline p) {
        return RenderSetup.builder(p)
            .withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            .createRenderSetup();
    }

    /* ================================================================ émission */

    /* émetteurs sans capture (aucune allocation par image) */
    private static final SubmitNodeCollector.CustomGeometryRenderer VU_GEO = (pose, vc) -> emettre(pose, vc, 0, nGrains, 1f),
        TRAVERS_GEO = (pose, vc) -> emettre(pose, vc, nLigne, nGrains, TRAVERS_A);

    private static void emettre(PoseStack.Pose pose, VertexConsumer vc, int de, int a, float opacite) {
        for (int i = de; i < a; i++) {
            int c = opacite >= 1f ? GC[i] : Math.round(((GC[i] >>> 24) & 0xFF) * opacite) << 24 | (GC[i] & 0xFFFFFF);
            float h = GH[i];
            float ax = (rx + ux) * h, ay = uy * h, az = (rz + uz) * h;    // droite + haut
            float bx = (rx - ux) * h, by = -uy * h, bz = (rz - uz) * h;   // droite − haut
            int s = GS[i];
            sommet(pose, vc, GX[i] - ax, GY[i] - ay, GZ[i] - az, c, U0[s], V1[s]);
            sommet(pose, vc, GX[i] + bx, GY[i] + by, GZ[i] + bz, c, U1[s], V1[s]);
            sommet(pose, vc, GX[i] + ax, GY[i] + ay, GZ[i] + az, c, U1[s], V0[s]);
            sommet(pose, vc, GX[i] - bx, GY[i] - by, GZ[i] - bz, c, U0[s], V0[s]);
        }
    }

    private static void sommet(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, int argb, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LUMIERE)
            .setNormal(pose, 0f, 1f, 0f);
    }
}
