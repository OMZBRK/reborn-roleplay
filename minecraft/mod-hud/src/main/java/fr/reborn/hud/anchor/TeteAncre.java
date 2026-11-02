package fr.reborn.hud.anchor;

import fr.reborn.hud.ui.DaKit;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * <b>Ancre de tête</b> (31/10, socle des nametags, bulles, /me et nametags staff) : le centre de la tête
 * <b>réellement rendue</b> d'un joueur, émotes, ramper, KO à terre, inconscient et {@code /allonger} compris. Les
 * anciennes ancres (« œil + k ») tombaient 0,64 bloc derrière la tête d'un joueur qui rampe et 1,64 bloc à côté de
 * celle d'un joueur allongé sans lit (le nametag flottait au-dessus de ses pieds).
 *
 * <p><b>Pourquoi un décalage capturé.</b> En 26.2, le HUD d'une image est extrait <i>avant</i> que le monde de cette
 * image soit soumis et animé. {@link fr.reborn.hud.mixin.LivingEntityRendererTeteMixin} capte donc la tête au rendu
 * de l'image N (juste après {@code setupAnim}, pose PlayerAnimationLib comprise) et ne garde que le <b>décalage tête −
 * racine</b> ; le HUD de l'image N+1 le recolle sur la racine et le lacet de corps de l'image N+1 (états indexés à
 * {@code LevelExtractionEvents.END_EXTRACTION}) : le déplacement n'a aucun retard, seule la pose garde une image.
 * Pas d'extrapolation (biaisée). Le passage accroupi ↔ debout est corrigé de la descente connue de la tête.
 *
 * <p><b>Point visé</b> : pivot du cou, puis centre du cube de tête <b>sans</b> la rotation propre de la tête — le nametag
 * ne danse pas quand le joueur regarde en haut, en bas ({@code RebornCamera.lookDown} force 89°) ou pendant une émote
 * qui fait tourner la tête. Seule exception : l'inclinaison que la pose vanilla impose quel que soit le regard (−45° à
 * terre / en nage, en élytre) ; sans elle, le centre d'un joueur qui rampe tombait 0,17 bloc sous sa tête visible
 * (constaté au banc). Avec le centre, on garde les trois <b>demi-axes de la boîte de tête</b> (chapeau compris), pris
 * dans la même pose.
 *
 * <p><b>Sommet à l'écran</b> ({@link #ecran}, correctifs du 31/10) : on monte le long de l'axe « haut » de la caméra de
 * la distance d'appui de cette boîte ({@code |hx·haut| + |hy·haut| + |hz·haut|}), puis de la marge ; de près, le coin le
 * plus haut à l'écran (perspective) l'emporte s'il dépasse. Avant, on montait de {@link #HAUT_TETE} vers le haut du
 * monde : juste pour une tête droite vue à hauteur d'œil, mais la pile mordait sur une tête basculée (à terre : demi-
 * hauteur réelle 0,373) ou vue en plongée (le bord arrière du chapeau monte à l'écran). Une tête droite vue de loin à
 * hauteur d'œil ne bouge pas ; de près, l'ancre se cale sur le bord du chapeau qui monte le plus haut à l'écran.
 *
 * <p><b>Fraîcheur</b> : capture de 6 images et 300 ms au plus (le critère de temps couvre le retour d'un menu opaque,
 * où le compteur d'images s'arrête) ; au-delà, ou si la pose a changé de famille, formule de la pose vanilla
 * ({@link Qualite#ESTIMEE}). Un fondu de 80 ms couvre chaque changement de source.
 *
 * <p><b>Ce qui cache une tête</b> : un bloc opaque plein ({@link #visible}, pour tout ce qui flotte au-dessus) et, pour la
 * bulle et le /me seulement, un autre corps plus proche de la caméra ({@link #cacheeParUnCorps}) — un auteur caché
 * derrière un joueur ne fait pas parler celui de devant. Même hystérésis de 150 ms, entre images consécutives
 * seulement (une tête évaluée après une pause repart de ce qu'on voit, jamais d'une valeur périmée).
 *
 * <p>Tout se passe sur le thread de rendu ; API à appeler pendant l'extraction du HUD. Aucune allocation par image :
 * une fiche {@link Suivi} par entité (créée à la première rencontre, purgée après 10 s), matrices et vecteurs
 * réutilisés. Réservé aux avatars ({@code AvatarRenderState} : joueurs, mannequins, corps au sol) ; une autre
 * entité vivante retombe sur sa hauteur des yeux.
 */
public final class TeteAncre {

    private TeteAncre() {}

    /** D'où vient le centre rendu par {@link #centre}. */
    public enum Qualite {
        /** Pose captée au rendu de l'image précédente : le cas normal d'un joueur à l'écran. */
        CAPTUREE,
        /** Capture de 2 à 6 images (300 ms au plus), recalée sur la racine et le lacet de l'image courante. */
        RECALEE,
        /** Pas de capture fraîche (hors champ, caché par EntityCulling, retour de menu opaque) : formule de la pose. */
        ESTIMEE
    }

    /** Du centre de la tête au sommet du calque de chapeau (4,5 px de modèle × 15/16), en blocs, à l'échelle 1. */
    public static final float HAUT_TETE = 0.265f;

    /**
     * Croix de débogage sur chaque avatar à 32 blocs : croix au centre de tête (vert capturée, or recalée, rouge
     * estimée, pâle si masquée) avec sa lettre C / R / E, trait au sommet ({@code ecran(e, 0)}), et en gris l'ancien
     * ancrage des nametags (œil + 0,62). Activée par {@code -Dreborn.teteAncreDebug=true} ou
     * {@code REBORN_TETE_ANCRE_DEBUG=1} ({@code =banc} : scène de mannequins de {@link TeteAncreBanc}), ou posée par un
     * banc.
     */
    public static boolean debugCroix;

    private static final int FRAICHEUR_IMAGES = 6;
    private static final long FRAICHEUR_NS = 300_000_000L;
    private static final long FONDU_NS = 80_000_000L;
    /** Occlusion : un changement doit tenir 150 ms avant d'être rendu (pas de clignotement derrière un poteau). */
    private static final long HYSTERESE_NS = 150_000_000L;
    /** Sans nouvelle demande depuis 500 ms, ni fondu ni hystérésis : on repart de la valeur brute. */
    private static final long REPRISE_NS = 500_000_000L;
    private static final long OUBLI_NS = 10_000_000_000L;
    /** Descente de la tête à l'accroupi : décalage de rendu (2/16) + pivot du cou (4,2 px × 15/16), à l'échelle 1. */
    private static final float DY_ACCROUPI = 0.125f + 4.2f / 16f * 0.9375f;
    /** Garde-fou : une tête à plus de 4 blocs × échelle de la racine vient d'une passe parasite. */
    private static final float DECALAGE_MAX = 4f;
    /** Tête basculée de −45° par la pose (élytre, nage, ramper) : {@code HumanoidModel#setupAnim}, −π/4. */
    private static final float BASCULE_POSE = (float) (-Math.PI / 4.0);
    /** Demi-côté de la boîte de tête chapeau compris, en unités du modèle (4 px + 0,5 de calque, ÷ 16) : × 15/16 = 0,264. */
    private static final float DEMI_CHAPEAU = 4.5f / 16f;
    /** Corps qui cache une tête : boîte de collision réduite de 0,1 bloc par face (jamais un faux « caché » au ras). */
    private static final double RETRAIT_CORPS = 0.1;
    /** … ou boîte de sa tête rendue, de ce demi-côté × échelle (le cube sans chapeau en fait 0,234). */
    private static final double DEMI_TETE_CORPS = 0.22;
    private static final byte MODE_DEBOUT = 0, MODE_COUCHE = 1, MODE_LIT = 2;

    /** Fiche par entité : état extrait de l'image, dernière capture, sortie (cache de l'image et fondu), occlusions. */
    private static final class Suivi {
        AvatarRenderState etat;
        long etatImage = -1;

        float capX, capY, capZ, capCorps, capEchelle;
        /** Demi-axes monde de la boîte de tête à la capture (x, y, z de chacun des trois axes). */
        final float[] capH = new float[9];
        byte capMode;
        Pose capPose;
        long capImage = -1, capNanos;

        long sortieImage = -1, sortieNanos;
        double sortieX, sortieY, sortieZ;
        float sortieOx, sortieOy, sortieOz, sortieEch = 1f;
        final float[] sortieH = new float[9];
        Qualite sortieQualite = Qualite.ESTIMEE;
        boolean sortieCapture;
        boolean fondu;
        float fonduX, fonduY, fonduZ;
        final float[] fonduH = new float[9];
        long fonduDebut;

        /** Sommet à l'écran (px de la fenêtre) pour une marge, mis en cache pour l'image. */
        long ecranImage = -1;
        float ecranMarge, ecranX, ecranY, ecranZ;
        boolean ecranOk;

        long occlImage = -1, occlNanos, occlDepuis;
        boolean occlBrut, occlStable;

        long corpsImage = -1, corpsDepuis;
        boolean corpsBrut, corpsStable;

        long vuNanos;
    }

    private static final Int2ObjectOpenHashMap<Suivi> SUIVIS = new Int2ObjectOpenHashMap<>();
    /** Avatars rendus à cette image (états extraits, id > 0) : les corps qui peuvent cacher une tête. */
    private static AvatarRenderState[] rendus = new AvatarRenderState[64];
    private static int nCorps;
    /** Compteur d'images (avance à chaque fin d'extraction du monde) et heure de cette fin. */
    private static long image, imageNanos;
    /** Vrai pendant {@code LevelRenderer#submitEntities} (posé par {@code LevelRendererTeteMixin}). */
    private static boolean passeEntites;

    private static final Matrix4f M = new Matrix4f();
    private static final Matrix4f EST = new Matrix4f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final Vector3f O = new Vector3f();
    private static final Vector3f HAUT = new Vector3f();
    private static final Vector3f COIN = new Vector3f();
    /** Demi-axes de la boîte de tête en cours de calcul. */
    private static final float[] HT = new float[9];
    private static final Vector3d CE = new Vector3d();
    private static final Vector3d CV = new Vector3d();
    private static final Vector3d CO = new Vector3d();
    private static final Vector3d DBG = new Vector3d();
    private static final Vector3f DP = new Vector3f();

    public static void init() {
        debugCroix = Boolean.getBoolean("reborn.teteAncreDebug") || "1".equals(System.getenv("REBORN_TETE_ANCRE_DEBUG"));
        LevelExtractionEvents.END_EXTRACTION.register(TeteAncre::finExtraction);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> vider());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "tete-ancre-debug"),
            (ctx, tickCounter) -> dessinerDebug(ctx));
        TeteAncreBanc.init();   // REBORN_TETE_ANCRE_DEBUG=banc (dev)
    }

    // ── API ─────────────────────────────────────────────────────

    /**
     * Centre de la tête rendue de {@code e} pour l'image courante, en coordonnées monde, dans {@code out}. Calculé au
     * plus une fois par image et par entité (les appels suivants relisent le résultat).
     */
    public static Qualite centre(Entity e, Vector3d out) {
        Suivi s = suivi(e.getId());
        if (s.sortieImage != image) calculer(e, s);
        out.set(s.sortieX, s.sortieY, s.sortieZ);
        return s.sortieQualite;
    }

    /** Échelle de rendu de l'entité (attribut {@code scale}) : celle de l'image courante si elle a été extraite. */
    public static float echelle(Entity e) {
        Suivi s = SUIVIS.get(e.getId());
        if (s != null && s.sortieImage == image) return s.sortieEch;
        if (s != null && s.etatImage == image && s.etat != null) return s.etat.scale;
        return e instanceof LivingEntity le ? le.getScale() : 1f;
    }

    /**
     * Vrai si la tête a été rendue récemment (capture de 6 images et 300 ms au plus : dans le champ, pas cachée par
     * EntityCulling) <b>et</b> qu'aucun bloc opaque plein ne s'interpose entre la caméra et son centre (hystérésis de
     * 150 ms, entre images consécutives : une tête qu'on n'a pas demandée à l'image précédente repart de la valeur
     * brute, jamais d'une visibilité périmée). Le rayon n'est lancé que pour les entités demandées, une fois par image.
     * Ne regarde pas l'invisibilité (potion) : au consommateur de la traiter.
     */
    public static boolean visible(Entity e) {
        Suivi s = SUIVIS.get(e.getId());
        if (s == null || s.capImage < 0) return false;
        if (image - s.capImage > FRAICHEUR_IMAGES || imageNanos - s.capNanos > FRAICHEUR_NS) return false;
        if (s.occlImage != image) {
            centre(e, CV);
            Minecraft mc = Minecraft.getInstance();
            CameraRenderState cs = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
            boolean brut = cs != null && cs.pos != null
                && OcclusionBlocs.masque(mc.level, cs.pos.x, cs.pos.y, cs.pos.z, CV.x, CV.y, CV.z);
            if (s.occlImage < 0 || s.occlImage != image - 1 || imageNanos - s.occlNanos > REPRISE_NS) {
                s.occlBrut = brut;
                s.occlStable = brut;
                s.occlDepuis = imageNanos;
            } else {
                if (brut != s.occlBrut) { s.occlBrut = brut; s.occlDepuis = imageNanos; }
                if (s.occlStable != brut && imageNanos - s.occlDepuis >= HYSTERESE_NS) s.occlStable = brut;
            }
            s.occlImage = image;
            s.occlNanos = imageNanos;
        }
        return !s.occlStable;
    }

    /**
     * Le centre de la tête de {@code e} est-il caché à la caméra par un <b>autre corps</b> plus proche (joueur, mannequin,
     * soi-même en 3e personne) : sa boîte de collision réduite de 0,1 bloc, ou la boîte de sa tête rendue ? Pour la bulle
     * et le /me seulement (le nom au regard n'allume que le plus proche) : caché comme derrière un bloc, même hystérésis
     * de 150 ms entre images consécutives. Une fois par image et par entité demandée ; les corps testés sont les avatars
     * rendus à cette image (aucune allocation). Jamais le joueur local en 1re personne, ni un corps invisible ou
     * spectateur ; un corps qui contient la caméra ou le centre visé ne compte pas (trop près pour dire qui est devant).
     */
    public static boolean cacheeParUnCorps(Entity e) {
        Suivi s = suivi(e.getId());
        if (s.corpsImage == image) return s.corpsStable;
        Minecraft mc = Minecraft.getInstance();
        CameraRenderState cs = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        boolean brut = false;
        if (cs != null && cs.pos != null && mc.level != null) {
            centre(e, CV);
            brut = corpsEntre(mc, e.getId(), cs.pos.x, cs.pos.y, cs.pos.z, CV.x, CV.y, CV.z);
        }
        if (s.corpsImage < 0 || s.corpsImage != image - 1) {
            s.corpsBrut = brut;
            s.corpsStable = brut;
            s.corpsDepuis = imageNanos;
        } else {
            if (brut != s.corpsBrut) { s.corpsBrut = brut; s.corpsDepuis = imageNanos; }
            if (s.corpsStable != brut && imageNanos - s.corpsDepuis >= HYSTERESE_NS) s.corpsStable = brut;
        }
        s.corpsImage = image;
        return s.corpsStable;
    }

    /**
     * Ancre à l'écran, en coordonnées GUI calées sur le pixel physique : sommet de la tête rendue vu par la caméra
     * (centre + haut caméra × (appui de la boîte de tête + {@code margeBlocs}), coin le plus haut si la perspective le
     * fait dépasser), projeté comme le monde. {@code outGui.z} = distance caméra → ancre (blocs). {@code false} : derrière
     * la caméra ou hors écran large. Ne teste pas {@link #visible}.
     */
    public static boolean ecran(Entity e, float margeBlocs, Vector3f outGui) {
        if (!ecranPx(e, margeBlocs, outGui)) return false;
        float gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        outGui.x /= gs;
        outGui.y /= gs;
        return true;
    }

    /** {@link #ecran} en pixels physiques de la fenêtre (entiers), pour ce qui se dessine en « unités menu ». */
    public static boolean ecranPx(Entity e, float margeBlocs, Vector3f outPx) {
        Suivi s = suivi(e.getId());
        if (s.sortieImage != image) calculer(e, s);
        if (s.ecranImage != image || s.ecranMarge != margeBlocs) {
            s.ecranImage = image;
            s.ecranMarge = margeBlocs;
            s.ecranOk = sommet(s, margeBlocs, outPx);
            s.ecranX = outPx.x;
            s.ecranY = outPx.y;
            s.ecranZ = outPx.z;
        }
        if (!s.ecranOk) return false;
        outPx.set(s.ecranX, s.ecranY, s.ecranZ);
        return true;
    }

    /** Oublie tout (déconnexion). */
    public static void vider() {
        SUIVIS.clear();
        Arrays.fill(rendus, null);
        nCorps = 0;
        passeEntites = false;
    }

    // ── réservé aux mixins ──────────────────────────────────────

    /** {@code LevelRendererTeteMixin}, HEAD de {@code submitEntities}. */
    public static void debutEntites() { passeEntites = true; }

    /** {@code LevelRendererTeteMixin}, RETURN de {@code submitEntities}. */
    public static void finEntites() { passeEntites = false; }

    /**
     * Passe du monde : pendant {@code submitEntities} et avec la caméra du monde (les aperçus GUI — inventaire,
     * créateur, menu Emotecraft — soumettent avec un {@code new CameraRenderState()}).
     */
    public static boolean passeMonde(CameraRenderState cam) {
        return passeEntites && cam == Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
    }

    /**
     * {@code LivingEntityRendererTeteMixin}, juste après {@code setupAnim} : le modèle partagé est dans la pose de
     * {@code s}, la pile ({@code pose}) est relative à la caméra, en axes monde.
     */
    public static void capturer(AvatarRenderState s, CameraRenderState cam, Matrix4fc pose, ModelPart racine, ModelPart tete) {
        Camera c = Minecraft.getInstance().gameRenderer.mainCamera();
        Entity vue = c.entity();
        // 1re personne : passe « corps » de PlayerAnimationLib (tête masquée, pose propre) — à ignorer.
        if (vue != null && !c.isDetached() && s.id == vue.getId()) return;
        M.set(pose);
        partie(M, racine);
        M.translate(tete.x / 16f, tete.y / 16f, tete.z / 16f);   // pivot du cou ; rotation de regard exclue
        float bascule = basculeTete(s.isFallFlying, s.swimAmount);  // seule la bascule de pose est gardée
        if (bascule != 0f) M.rotateX(bascule);
        M.transformPosition(0f, -0.25f, 0f, V);                   // centre du cube de tête (−4 px)
        float ox = V.x - (float) (s.x - cam.pos.x), oy = V.y - (float) (s.y - cam.pos.y), oz = V.z - (float) (s.z - cam.pos.z);
        float lim = DECALAGE_MAX * s.scale;
        if (ox * ox + oy * oy + oz * oz > lim * lim) return;
        Suivi su = suivi(s.id);
        Pose p = s.pose != null ? s.pose : Pose.STANDING;
        su.capX = ox;
        su.capY = oy;
        su.capZ = oz;
        axes(M, su.capH);   // boîte de tête dans la même pose (échelle de l'entité comprise)
        su.capCorps = s.bodyRot;
        su.capEchelle = s.scale;
        su.capPose = p;
        su.capMode = mode(p, s.bedOrientation);
        su.capImage = image;
        su.capNanos = imageNanos;
        su.vuNanos = imageNanos;
    }

    /** Compteur d'images (clé de cache de {@link ProjectionMonde}). */
    static long image() { return image; }

    // ── interne ─────────────────────────────────────────────────

    private static void finExtraction(LevelExtractionContext ctx) {
        image++;
        imageNanos = System.nanoTime();
        passeEntites = false;
        int avant = nCorps;
        nCorps = 0;
        List<EntityRenderState> etats = ctx.levelState().entityRenderStates;
        for (int i = 0, n = etats.size(); i < n; i++) {
            // id 0 : faux état créé par EntityCulling pour afficher un nom à travers un mur
            if (etats.get(i) instanceof AvatarRenderState a && a.id > 0) {
                Suivi s = suivi(a.id);
                s.etat = a;
                s.etatImage = image;
                s.vuNanos = imageNanos;
                if (nCorps == rendus.length) rendus = Arrays.copyOf(rendus, nCorps * 2);   // rare : la foule grandit
                rendus[nCorps++] = a;
            }
        }
        for (int i = nCorps; i < avant; i++) rendus[i] = null;   // pas d'anciens états de rendu retenus
        if ((image & 255) == 0) purger();
    }

    private static Suivi suivi(int id) {
        Suivi s = SUIVIS.get(id);
        if (s == null) {
            s = new Suivi();
            s.vuNanos = imageNanos;
            SUIVIS.put(id, s);
        }
        return s;
    }

    private static void purger() {
        long limite = imageNanos - OUBLI_NS;
        ObjectIterator<Int2ObjectMap.Entry<Suivi>> it = Int2ObjectMaps.fastIterator(SUIVIS);
        while (it.hasNext()) {
            Suivi s = it.next().getValue();
            if (s.vuNanos < limite) it.remove();
            else if (s.etatImage != image) s.etat = null;   // ne pas retenir d'anciens états de rendu
        }
    }

    /**
     * Sommet de la tête à l'écran (px de la fenêtre) : centre + haut caméra × (appui de la boîte + marge) ; puis, de près,
     * le coin de la boîte (+ marge) le plus haut à l'écran s'il dépasse — le bord arrière du chapeau d'une tête vue d'en
     * haut monte plus que le centre (perspective). Neuf projections, aucune allocation.
     */
    private static boolean sommet(Suivi s, float marge, Vector3f out) {
        if (!ProjectionMonde.haut(HAUT)) return false;
        float[] h = s.sortieH;
        float ux = HAUT.x, uy = HAUT.y, uz = HAUT.z;
        float appui = Math.abs(h[0] * ux + h[1] * uy + h[2] * uz) + Math.abs(h[3] * ux + h[4] * uy + h[5] * uz)
            + Math.abs(h[6] * ux + h[7] * uy + h[8] * uz);
        double cx = s.sortieX, cy = s.sortieY, cz = s.sortieZ;
        float k = appui + marge;
        if (!ProjectionMonde.versPx(cx + ux * k, cy + uy * k, cz + uz * k, out)) return false;
        float y = out.y;
        double mx = ux * marge, my = uy * marge, mz = uz * marge;
        for (int i = 0; i < 8; i++) {
            float a = (i & 1) == 0 ? -1f : 1f, b = (i & 2) == 0 ? -1f : 1f, c = (i & 4) == 0 ? -1f : 1f;
            double px = cx + a * h[0] + b * h[3] + c * h[6] + mx;
            double py = cy + a * h[1] + b * h[4] + c * h[7] + my;
            double pz = cz + a * h[2] + b * h[5] + c * h[8] + mz;
            if (ProjectionMonde.versPx(px, py, pz, COIN) && COIN.y < y) y = COIN.y;
        }
        out.y = y;
        return true;
    }

    /**
     * Un avatar rendu à cette image (autre que la cible, que le joueur local en 1re personne, qu'un invisible ou un
     * spectateur) coupe-t-il le segment caméra → centre de tête ? Boîte de collision réduite, puis boîte de sa tête.
     */
    private static boolean corpsEntre(Minecraft mc, int cible, double x0, double y0, double z0, double x1, double y1, double z1) {
        Camera c = mc.gameRenderer.mainCamera();
        Entity vue = c.entity();
        int dedans = vue != null && !c.isDetached() ? vue.getId() : Integer.MIN_VALUE;   // la caméra est dans ce corps
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        for (int i = 0; i < nCorps; i++) {
            AvatarRenderState a = rendus[i];
            if (a == null || a.id == cible || a.id == dedans || a.isInvisible || a.isSpectator) continue;
            double dh = a.boundingBoxWidth / 2.0 - RETRAIT_CORPS;
            if (dh > 0.0 && a.boundingBoxHeight > 2.0 * RETRAIT_CORPS
                && coupe(x0, y0, z0, dx, dy, dz, a.x - dh, a.y + RETRAIT_CORPS, a.z - dh,
                    a.x + dh, a.y + a.boundingBoxHeight - RETRAIT_CORPS, a.z + dh)) return true;
            Entity o = mc.level.getEntity(a.id);
            if (o == null) continue;
            centre(o, CO);
            double r = DEMI_TETE_CORPS * a.scale;
            if (coupe(x0, y0, z0, dx, dy, dz, CO.x - r, CO.y - r, CO.z - r, CO.x + r, CO.y + r, CO.z + r)) return true;
        }
        return false;
    }

    /**
     * Le segment {@code (x0, y0, z0) + t × (dx, dy, dz)} traverse-t-il la boîte strictement entre ses deux bouts (méthode
     * des dalles) ? Faux si la boîte contient la caméra ({@code t} d'entrée ≤ 0) ou le point visé (sortie ≥ 1).
     */
    private static boolean coupe(double x0, double y0, double z0, double dx, double dy, double dz,
                                 double ax, double ay, double az, double bx, double by, double bz) {
        double tmin = Double.NEGATIVE_INFINITY, tmax = Double.POSITIVE_INFINITY;
        if (Math.abs(dx) < 1e-9) {
            if (x0 < ax || x0 > bx) return false;
        } else {
            double t1 = (ax - x0) / dx, t2 = (bx - x0) / dx;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(dy) < 1e-9) {
            if (y0 < ay || y0 > by) return false;
        } else {
            double t1 = (ay - y0) / dy, t2 = (by - y0) / dy;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(dz) < 1e-9) {
            if (z0 < az || z0 > bz) return false;
        } else {
            double t1 = (az - z0) / dz, t2 = (bz - z0) / dz;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        return tmin <= tmax && tmin > 0.0 && tmax < 1.0;
    }

    /** Racine de l'image courante + décalage capturé recalé, ou estimé ; fondu de 80 ms au changement de source. */
    private static void calculer(Entity e, Suivi s) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(!e.level().tickRateManager().isEntityFrozen(e));
        AvatarRenderState a = s.etatImage == image ? s.etat : null;
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        double rx, ry, rz;
        float corps, ech;
        Pose pose;
        Direction lit;
        if (a != null) {
            rx = a.x;
            ry = a.y;
            rz = a.z;
            corps = a.bodyRot;
            ech = a.scale;
            pose = a.pose;
            lit = a.bedOrientation;
        } else {
            // mêmes formules que l'extraction (xOld, pas xo : ils diffèrent après absSnapTo)
            rx = Mth.lerp(pt, e.xOld, e.getX());
            ry = Mth.lerp(pt, e.yOld, e.getY());
            rz = Mth.lerp(pt, e.zOld, e.getZ());
            corps = le != null ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot(pt);
            ech = le != null ? le.getScale() : 1f;
            pose = e.getPose();
            lit = le != null && pose == Pose.SLEEPING ? le.getBedOrientation() : null;
        }
        if (pose == null) pose = Pose.STANDING;
        byte mode = mode(pose, lit);

        Qualite q;
        long age = image - s.capImage;
        boolean capOk = s.capImage >= 0 && age <= FRAICHEUR_IMAGES && imageNanos - s.capNanos <= FRAICHEUR_NS
            && s.capMode == mode && (s.capPose == pose || (debout(s.capPose) && debout(pose)));
        if (capOk) {
            O.set(s.capX, s.capY, s.capZ);
            System.arraycopy(s.capH, 0, HT, 0, 9);
            if (mode != MODE_LIT) {
                // Lacet de corps : YP(180 − corps) debout, YP(corps) couché sans lit (signes vérifiés au calcul).
                float d = Mth.wrapDegrees(corps - s.capCorps) * Mth.DEG_TO_RAD;
                if (d != 0f) {
                    float r = mode == MODE_DEBOUT ? -d : d;
                    O.rotateY(r);
                    for (int i = 0; i < 9; i += 3) {   // la boîte de tête tourne avec le corps
                        V.set(HT[i], HT[i + 1], HT[i + 2]).rotateY(r);
                        HT[i] = V.x;
                        HT[i + 1] = V.y;
                        HT[i + 2] = V.z;
                    }
                }
            }
            if (s.capPose != pose) O.y += (s.capPose == Pose.CROUCHING ? DY_ACCROUPI : -DY_ACCROUPI) * ech;
            if (s.capEchelle > 0f && Math.abs(ech - s.capEchelle) > 1e-4f) {
                float k = ech / s.capEchelle;
                O.mul(k);
                for (int i = 0; i < 9; i++) HT[i] *= k;
            }
            q = age <= 1 ? Qualite.CAPTUREE : Qualite.RECALEE;
        } else {
            if (a != null) estimer(a, O);
            else if (le != null && e instanceof Avatar) estimer(le, pt, pose, lit, corps, ech, O);
            else {
                O.set(0f, e.getEyeHeight(), 0f);
                float h = HAUT_TETE * ech;   // autre entité : une boîte droite autour des yeux
                Arrays.fill(HT, 0f);
                HT[0] = h;
                HT[4] = h;
                HT[8] = h;
            }
            q = Qualite.ESTIMEE;
        }

        boolean capture = q != Qualite.ESTIMEE;
        boolean recent = s.sortieImage >= 0 && imageNanos - s.sortieNanos <= REPRISE_NS;
        if (!recent) s.fondu = false;
        else if (capture != s.sortieCapture) {
            s.fondu = true;
            s.fonduDebut = imageNanos;
            s.fonduX = s.sortieOx;
            s.fonduY = s.sortieOy;
            s.fonduZ = s.sortieOz;
            System.arraycopy(s.sortieH, 0, s.fonduH, 0, 9);
        }
        if (s.fondu) {
            float k = (imageNanos - s.fonduDebut) / (float) FONDU_NS;
            if (k >= 1f) s.fondu = false;
            else {
                k = k * k * (3f - 2f * k);
                O.set(Mth.lerp(k, s.fonduX, O.x), Mth.lerp(k, s.fonduY, O.y), Mth.lerp(k, s.fonduZ, O.z));
                for (int i = 0; i < 9; i++) HT[i] = Mth.lerp(k, s.fonduH[i], HT[i]);
            }
        }
        s.sortieOx = O.x;
        s.sortieOy = O.y;
        s.sortieOz = O.z;
        System.arraycopy(HT, 0, s.sortieH, 0, 9);
        s.sortieX = rx + O.x;
        s.sortieY = ry + O.y;
        s.sortieZ = rz + O.z;
        s.sortieEch = ech;
        s.sortieQualite = q;
        s.sortieCapture = capture;
        s.sortieImage = image;
        s.sortieNanos = imageNanos;
        s.vuNanos = imageNanos;
    }

    private static byte mode(Pose pose, Direction lit) {
        return pose != Pose.SLEEPING ? MODE_DEBOUT : lit == null ? MODE_COUCHE : MODE_LIT;
    }

    private static boolean debout(Pose p) { return p == Pose.STANDING || p == Pose.CROUCHING; }

    /**
     * Inclinaison de la tête imposée par la pose ({@code HumanoidModel#setupAnim}) : −45° en élytre, {@code rotLerp}
     * vers −45° selon la nage (ramper, à terre) ; la part « regard » du joueur n'y entre pas.
     */
    private static float basculeTete(boolean plane, float nage) {
        return plane ? BASCULE_POSE : nage > 0f ? BASCULE_POSE * Math.min(nage, 1f) : 0f;
    }

    /** {@code ModelPart#translateAndRotate} sans {@code PoseStack} (rotation par quaternion réutilisé). */
    private static void partie(Matrix4f m, ModelPart p) {
        m.translate(p.x / 16f, p.y / 16f, p.z / 16f);
        if (p.xRot != 0f || p.yRot != 0f || p.zRot != 0f) m.rotate(Q.rotationZYX(p.zRot, p.yRot, p.xRot));
        if (p.xScale != 1f || p.yScale != 1f || p.zScale != 1f) m.scale(p.xScale, p.yScale, p.zScale);
    }

    /** Demi-axes monde de la boîte de tête (chapeau compris) dans la pose de {@code m} : x, y, z de chaque axe. */
    private static void axes(Matrix4f m, float[] out) {
        m.transformDirection(DEMI_CHAPEAU, 0f, 0f, V);
        out[0] = V.x;
        out[1] = V.y;
        out[2] = V.z;
        m.transformDirection(0f, DEMI_CHAPEAU, 0f, V);
        out[3] = V.x;
        out[4] = V.y;
        out[5] = V.z;
        m.transformDirection(0f, 0f, DEMI_CHAPEAU, V);
        out[6] = V.x;
        out[7] = V.y;
        out[8] = V.z;
    }

    // ── estimation (pose vanilla, sans animation) ───────────────

    private static void estimer(AvatarRenderState a, Vector3f out) {
        chaine(a.pose, a.bedOrientation, a.eyeHeight, a.swimAmount, a.isInWater, a.xRot, a.isFallFlying, a.fallFlyingScale(),
            a.isAutoSpinAttack, a.isCrouching, a.isVisuallySwimming, a.bodyRot, a.scale, out);
    }

    private static void estimer(LivingEntity le, float pt, Pose pose, Direction lit, float corps, float ech, Vector3f out) {
        float yeux = lit != null ? le.getEyeHeight(Pose.STANDING) : le.getEyeHeight();
        float vol = le.getFallFlyingTicks() + pt;
        chaine(pose, lit, yeux, le.getSwimAmount(pt), le.isInWater(), le.getXRot(pt), le.isFallFlying(),
            Mth.clamp(vol * vol / 100f, 0f, 1f), le.isAutoSpinAttack(), le.isCrouching(), le.isVisuallySwimming(), corps, ech, out);
    }

    /**
     * Chaîne de {@code LivingEntityRenderer#submit} pour un avatar sans animation (lit, échelle, {@code setupRotations}
     * d'{@code AvatarRenderer} puis de {@code LivingEntityRenderer}, retournement, 15/16, −1,501, pivot du cou accroupi,
     * bascule de pose, décalage de rendu accroupi) : debout 1,642 ; accroupi 1,270 ; ramper au sol avant × 0,574 + haut
     * × 0,466 ; couché sans lit 1,642 sur le côté au ras du sol ; élytre et trident selon le tangage (vérification a6).
     * Les demi-axes de la boîte de tête vont dans {@link #HT}. Ignorés : tremblement du gel, rotation de mort,
     * retournement « Dinnerbone ».
     */
    private static void chaine(Pose pose, Direction lit, float yeux, float nage, boolean dansEau, float tangage,
                               boolean plane, float echPlane, boolean vrille, boolean accroupi, boolean nageVisuelle,
                               float corps, float ech, Vector3f out) {
        Matrix4f m = EST.identity();
        boolean couche = pose == Pose.SLEEPING;
        if (couche && lit != null) {
            float k = yeux - 0.1f;
            m.translate(-lit.getStepX() * k, 0f, -lit.getStepZ() * k);
        }
        m.scale(ech);
        if (plane) {
            rotationsVivant(m, couche, lit, vrille, tangage, corps);
            // YP(flyingYRot) tourne autour de l'axe du corps : sans effet sur le centre de la tête
            if (!vrille) m.rotateX(echPlane * (-90f - tangage) * Mth.DEG_TO_RAD);
        } else if (nage > 0f) {
            rotationsVivant(m, couche, lit, vrille, tangage, corps);
            m.rotateX(Mth.lerp(nage, 0f, dansEau ? -90f - tangage : -90f) * Mth.DEG_TO_RAD);
            if (nageVisuelle) m.translate(0f, -1f, 0.3f);
        } else {
            rotationsVivant(m, couche, lit, vrille, tangage, corps);
        }
        m.scale(-1f, -1f, 1f).scale(0.9375f).translate(0f, -1.501f, 0f);
        if (accroupi) m.translate(0f, 4.2f / 16f, 0f);
        float bascule = basculeTete(plane, nage);
        if (bascule != 0f) m.rotateX(bascule);
        axes(m, HT);
        m.transformPosition(0f, -0.25f, 0f, out);
        if (accroupi) out.y -= 0.125f * ech;
    }

    /** {@code LivingEntityRenderer#setupRotations} (vrille du trident prioritaire sur le sommeil, comme vanilla). */
    private static void rotationsVivant(Matrix4f m, boolean couche, Direction lit, boolean vrille, float tangage, float corps) {
        if (!couche) m.rotateY((180f - corps) * Mth.DEG_TO_RAD);
        if (vrille) {
            m.rotateX((-90f - tangage) * Mth.DEG_TO_RAD);   // YP(âge × −75) : autour de l'axe du corps
        } else if (couche) {
            m.rotateY((lit != null ? rotationLit(lit) : corps) * Mth.DEG_TO_RAD);
            m.rotateZ(90f * Mth.DEG_TO_RAD);
            m.rotateY(270f * Mth.DEG_TO_RAD);
        }
    }

    /** {@code LivingEntityRenderer#sleepDirectionToRotation}. */
    private static float rotationLit(Direction d) {
        return switch (d) {
            case SOUTH -> 90f;
            case NORTH -> 270f;
            case EAST -> 180f;
            default -> 0f;
        };
    }

    // ── croix de débogage ───────────────────────────────────────

    private static final int VERT = 0xFF3FE05A, ROUGE = 0xFFDC3B3B, GRIS = 0xFF8C8C8C;
    private static final Component[] LETTRES = {Component.literal("C"), Component.literal("R"), Component.literal("E")};

    private static void dessinerDebug(GuiGraphicsExtractor ctx) {
        if (!debugCroix) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;
        Entity vue = cam.entity();
        int gs = DaKit.echelleGui();
        DaKit.debutPx(ctx);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Avatar)) continue;
            if (e == vue && !cam.isDetached()) continue;
            if (e.distanceToSqr(cam.position()) > 32 * 32) continue;
            Qualite q = centre(e, DBG);
            boolean vu = visible(e);
            int col = q == Qualite.CAPTUREE ? VERT : q == Qualite.RECALEE ? DaKit.OR : ROUGE;
            if (!vu) col = DaKit.alpha(col, 0.45f);
            // ancien ancrage des nametags : œil interpolé + 0,62
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            if (ProjectionMonde.versPx(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getEyeHeight() + 0.62,
                    Mth.lerp(pt, e.zo, e.getZ()), DP)) croix(ctx, (int) DP.x, (int) DP.y, 3 * gs, GRIS);
            if (ecranPx(e, 0f, DP)) ctx.fill((int) DP.x - 3 * gs, (int) DP.y, (int) DP.x + 3 * gs + 1, (int) DP.y + 1, col);
            if (ProjectionMonde.versPx(DBG.x, DBG.y, DBG.z, DP)) {
                croix(ctx, (int) DP.x, (int) DP.y, 4 * gs, col);
                ctx.pose().pushMatrix();
                ctx.pose().translate(DP.x + 5 * gs, DP.y - 9 * gs);
                ctx.pose().scale(gs, gs);
                ctx.text(mc.font, LETTRES[q.ordinal()], 0, 0, vu ? 0xFFFFFFFF : 0xFF9A9A9A, true);
                ctx.pose().popMatrix();
            }
        }
        DaKit.finPx(ctx);
    }

    /** Croix d'un pixel physique d'épaisseur, de demi-côté {@code r} pixels. */
    private static void croix(GuiGraphicsExtractor ctx, int x, int y, int r, int col) {
        ctx.fill(x - r, y, x + r + 1, y + 1, col);
        ctx.fill(x, y - r, x + 1, y + r + 1, col);
    }
}
