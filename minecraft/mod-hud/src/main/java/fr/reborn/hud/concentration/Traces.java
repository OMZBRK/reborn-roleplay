package fr.reborn.hud.concentration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.reborn.hud.anchor.ProjectionMonde;
import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * <b>Traces de pas</b> vues en concentration (lot 4, étape C, planche c7, décision du 08/11). Le serveur envoie, aux seuls
 * concentrés, les pas des <b>autres</b> joueurs (message {@code traces}, PLUGINS_REFERENCE §4 undecies) : jamais de nom, une
 * clé de piste, l'âge et la durée de vie de chaque pas ; le mod ne fait que les poser.
 *
 * <ul>
 *   <li><b>Rendu</b> sur le patron d'{@code AuraSol} : un quad par pas, <b>un lot</b> (un second la nuit, pour le contour, voir
 *       « Couleur »), à plat sur le dessus de la collision, texture NEAREST ({@code gen_traces_texture.py}), émissif, test de
 *       profondeur sans écriture, dans le monde (donc sous l'effet d'écran). Au plus {@value #MAX_DESSINES} pas, les plus proches.</li>
 *   <li><b>Opacité</b> = âge × distance × effet : pleine jusqu'à 60 % de la vie puis jusqu'à 25 % (« une trace reste ») ; pleine à
 *       {@value #PORTEE_PLEINE} blocs, rien à {@value #PORTEE_MAX} ; le fondu de l'effet d'écran (entrée 250 ms, sortie
 *       150 ms). Accroupi × 0,55 ; chargé × 1,15 et 8 % plus large ; sprint : l'avant du pied seul.</li>
 *   <li><b>Effritement</b> : une semelle ancienne perd ses grains (colonnes de la texture, ordre fixe, trois ordres selon le
 *       pas) : aucun coût en plus, c'est le même quad.</li>
 *   <li><b>Couleur</b> : réglage du joueur (Paramètres › Interface › Concentration). <b>Automatique par défaut</b>
 *       ({@link RebornPrefs#tracesAuto}, 08/11) : blanc, sauf sur un sol clair ({@link #solClair}) où la semelle est sombre ; le
 *       sol se lit une fois à la réception du pas, jamais par image ; jour ou nuit, c'est le bloc qui décide. La nuit, un sable
 *       n'est plus qu'à 10 % de luminance : un <b>contour</b> pâle d'un texel autour de la semelle sombre la fait lire. De jour il
 *       se verrait et élargirait le pied : il n'existe que <b>la nuit</b> ({@link #contourNuit}, ciel lu une fois par tick ;
 *       {@link #TEX_CONTOUR}, jamais sur la semelle : ni ordre de dessin ni forme changée). Sinon la couleur
 *       choisie ({@link RebornPrefs#tracesCouleur}), la même pour toutes : l'anonymat reste entier. Posée telle quelle : l'effet d'écran
 *       en garde 60 % de l'écart à la luminance (recette K+), assez pour la reconnaître ; une compensation n'aurait presque pas
 *       de marge sur un canal déjà au maximum (bleu chakra, ambre) et devrait baisser sa luminance.</li>
 *   <li><b>Autres filtres</b> : le KO et le Byakugan prennent l'unique emplacement de {@code FiltresEcran}. Sous le
 *       Byakugan, rien (son filtre fondrait le pas dans le sol pâle ; comme le contour de
 *       {@link ModeConcentration#halo}).</li>
 *   <li><b>Petit panneau</b>, comme chez Ketto (retour du 10/11) : le curseur sur un pas à {@value #PORTEE_SURVOL} blocs au
 *       plus ; au-dessus du pas, un cartouche de nuit translucide (filet pâle d'un pixel, clous d'or aux coins), « Trace de
 *       pas » en or (police du récit) et l'âge <b>estimé</b> ({@link TracesAge} : « Laissée il y a environ 10 min »…) en
 *       ivoire, centrés ; un fil vertical bleu chakra pâle jusqu'à un point posé sur le pas (sombre cerné de pâle sur un sol
 *       clair, de jour). Jamais de nom. Gardé à l'écran, jamais sur le réticule ; glisse d'un pas à l'autre en
 *       {@value #GLISSE_MS} ms ; fondu {@value #ENTREE_MS} ms à l'entrée, {@value #SORTIE_MS} ms à la sortie. Textes rendus
 *       une fois par {@link TexteCache} (rien de recalculé tant que le palier ne change pas).</li>
 * </ul>
 * Hors concentration : rien (ni tableau parcouru, ni quad, ni rayon). En concentration : une sélection par tick et par
 * ~{@value #MAX_DESSINES} pas au plus, aucune allocation par image ({@link #GEOMETRIE} ne capture rien).
 */
public final class Traces {

    public static final int MAX_RECUS = 512, MAX_DESSINES = 256;
    public static final float PORTEE_PLEINE = 16f, PORTEE_MAX = 24f;
    /** Le blanc des traces : l'ivoire de la planche c7. */
    public static final int IVOIRE = 0xFAF8F0;
    /** La semelle sur un sol clair (contraste automatique, 08/11). */
    public static final int SEMELLE_SOMBRE = 0x141210;
    /**
     * Luminance (0 à 1) de la couleur de carte du bloc à partir de laquelle un sol est clair. Sable 0,90, neige et béton blanc
     * 1, quartz 0,99, bouleau 0,90, calcite 0,72 : clairs ; herbe 0,62, terre 0,45, pierre 0,44, bois 0,47 : sombres.
     */
    public static final float SEUIL_SOL_CLAIR = 0.70f;
    /** Préréglages du réglage joueur, dans l'ordre : les couleurs du halo, sauf « aucun » (une trace sans couleur n'existe pas). */
    public static final int[] PRESETS = {IVOIRE, 0xD6E2F2, 0x6FB4FF, 0xA06CD2, 0xD9443F, 0x5FD49A, 0xFF9A3C};
    public static final String[] PRESETS_NOMS = {"Ivoire", "Blanc lunaire", "Bleu chakra", "Violet", "Rouge sang", "Vert jade", "Ambre"};
    /** La texture ; cases de 8 × 12 (une semelle de 6 × 10 et sa marge) : {@code gen_traces_texture.py}. */
    public static final Identifier TEX = Identifier.fromNamespaceAndPath("reborn", "textures/concentration/traces.png");
    /** Le contour d'un texel de chaque semelle, mêmes cases : les rangées de {@link #TEX}, puis les mêmes coupées au sprint, fermées au talon ({@code gen_traces_texture.py}). */
    public static final Identifier TEX_CONTOUR = Identifier.fromNamespaceAndPath("reborn", "textures/concentration/traces_contour.png");
    public static final int TEX_W = 48, TEX_H = 48, CASE_W = 8, CASE_H = 12, SEMELLE_W = 6, SEMELLE_H = 10;
    private static final int CONTOUR_H = 96, RANGEES = 4;

    /** Un texel = 1/28 de bloc à la taille de référence : semelle de 0,21 × 0,36 bloc. */
    private static final float TEXEL = 1f / 28f;
    /** Opacité du contour, la nuit. {@link #contourNuit} : rien jusqu'à cet assombrissement du ciel ({@code Level#getSkyDarken}, 0 à midi, 11 la nuit), plein à celui-là. */
    private static final float ALPHA_CONTOUR = 0.38f;
    private static final float NUIT_DEBUT = 4f, NUIT_PLEINE = 9f;
    private static final float ALPHA = 0.92f, ALPHA_ACCROUPI = 0.55f, ALPHA_CHARGE = 1.15f, LARGEUR_CHARGE = 1.08f;
    private static final int RANGS_SPRINT = 6;
    /** Au-dessus du dessus de la collision (blocs). */
    private static final float LEVE = 0.012f;
    /** Fractions de vie où une semelle passe à la colonne suivante (1 à 5) ; chaque pas décale un peu les siennes. */
    private static final float[] EFFRITE = {0.67f, 0.74f, 0.81f, 0.87f, 0.93f};
    /** {@link #opaciteAge} : pleine jusqu'à cette fraction de vie, puis jusqu'à ce plancher : la trace finit pâle, sans s'éteindre tôt. */
    private static final float AGE_PLEIN = 0.60f, OPACITE_FIN = 0.25f;
    /** Ce qu'on sait du sol sous un pas ({@link #SOL}). */
    private static final byte SOL_INCONNU = 0, SOL_SOMBRE = 1, SOL_CLAIR = 2;
    /** Relecture des sols encore inconnus (chunk pas encore là), en ticks. */
    private static final int RELECTURE_TICKS = 20;
    private static final int DROIT = 1, SPRINT = 2, ACCROUPI = 4, CHARGE = 8, SANG = 16;
    private static final int RANGEE_SANG = 3, ORDRES = 3;
    private static final int CHAMPS = 10;
    private static final float PRECISION = 32f;

    public static final float PORTEE_SURVOL = 8f;
    public static final long ENTREE_MS = 120, SORTIE_MS = 150, GLISSE_MS = 90;
    /** Curseur à moins de ces pixels GUI du centre du pas, quelle que soit la distance (viser un petit pas lointain). */
    private static final float SURVOL_MIN_GUI = 5f;

    /* Le panneau, en pixels 1080p (× k = kHud / 2, comme le bas-droite). */
    private static final String TITRE = "Trace de pas";
    private static final int CAP_TITRE = 9, CAP_AGE = 8, MARGE_X = 11, HAUT = 40, Y_TITRE = 13, Y_AGE = 27;
    /** Fil : au moins cette longueur sous le panneau ; au moins ce jour entre le panneau et le bord du pas vu à l'écran. */
    private static final int FIL = 30, FIL_JOUR = 10;
    /** Marge aux bords de l'écran ; demi-côté de la zone du réticule que le panneau ne couvre jamais. */
    private static final int MARGE_ECRAN = 8, RETICULE = 10;
    private static final int NUIT = 0x110B0D, OR_TITRE = 0xE8C27A, CHAKRA_PALE = 0xB4D2F2;
    private static final float ALPHA_FOND = 0.78f, ALPHA_FILET = 0.26f, ALPHA_CLOU = 0.80f, ALPHA_FIL = 0.70f;
    /** Texte sur un fond à nous : ni contour ni ombre (ils l'épaissiraient sans rien gagner). Une instance : comparée par référence. */
    private static final TexteRaster.Preset NET = new TexteRaster.Preset("PANNEAU", 0f, 0f, 0.85f);
    private static final TexteCache.Style STYLE_TITRE = new TexteCache.Style(TexteRaster.Police.RECIT, NET,
        TexteRaster.cleRgb(OR_TITRE), TexteRaster.Ancre.CENTRE, true, false);
    private static final TexteCache.Style STYLE_AGE = new TexteCache.Style(TexteRaster.Police.INTER_MEDIUM, NET, "hud",
        TexteRaster.Ancre.CENTRE, true, false);
    private static final TexteCache.Texte TEXTE_TITRE = new TexteCache.Texte(), TEXTE_AGE = new TexteCache.Texte();

    /** Horloge de l'âge et du libellé (le banc la fige). */
    public static volatile LongSupplier horloge = Util::getMillis;

    /* Les pas reçus, à plat (aucun objet par pas). */
    private static final int[] ID = new int[MAX_RECUS], CLE = new int[MAX_RECUS], FL = new int[MAX_RECUS], VIE = new int[MAX_RECUS];
    private static final float[] X = new float[MAX_RECUS], Y = new float[MAX_RECUS], Z = new float[MAX_RECUS],
        SIN = new float[MAX_RECUS], COS = new float[MAX_RECUS], K = new float[MAX_RECUS];
    private static final double[] XD = new double[MAX_RECUS], ZD = new double[MAX_RECUS];
    private static final long[] NEE = new long[MAX_RECUS];
    private static final byte[] SOL = new byte[MAX_RECUS];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static int n, tickRelecture;
    /** Sélection du dernier tick : les pas à dessiner, du plus loin au plus proche. */
    private static final int[] DESSIN = new int[MAX_DESSINES];
    private static int nDessin;
    private static boolean dessinSurClair;
    /** 0 de jour, 1 la nuit : lu une fois par tick ({@link #selectionner}). */
    private static float contourNuit;
    private static final long[] TRI = new long[MAX_RECUS];
    private static ClientLevel monde;

    /* État de l'image en cours, lu par GEOMETRIE (aucune capture : aucune allocation par image). */
    private static double camX, camY, camZ, joueurX, joueurZ;
    private static long maintenant;
    private static float niveauImage;
    private static int rgbImage;
    private static boolean autoImage;

    /* Survol. */
    private static int survolId = -1, palier;
    private static double survolX, survolY, survolZ;
    private static float survolK = 1f;
    private static long survolA = Long.MIN_VALUE / 4, finSurvolA = Long.MIN_VALUE / 4, glisseA = Long.MIN_VALUE / 4;
    private static boolean panneauVisible, survolClair;
    /** Point du pas où le panneau était à la dernière image, et départ du glissement vers un autre pas. */
    private static double affX, affY, affZ, departX, departY, departZ;
    private static float alphaAffiche;
    /** Largeur du panneau (px) pour ce palier et ce k : recalculée seulement quand l'un change. */
    private static int largeurPanneau, largeurPalier = -1;
    private static double largeurK;
    /** Dernier panneau dessiné (banc) : x, y, largeur, hauteur, puis le point du pas, en pixels de la fenêtre. */
    private static int pnX, pnY, pnW, pnH, pnPointX, pnPointY;
    private static final Vector3f V = new Vector3f();

    private Traces() {}

    /** Appelé par {@link ModeConcentration#init}. */
    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Traces::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(Traces::collecter);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(Traces::oublier));
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
            fr.reborn.hud.recit.RecitHud.ID,
            Identifier.fromNamespaceAndPath("reborn-hud", "traces-temps"),
            (ctx, tc) -> panneau(ctx));
    }

    /* ================================================================ messages */

    /** Message {@code traces} du canal (fil client). */
    static void recevoir(JsonObject o) {
        if (!ModeConcentration.actif()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (o.has("plein") && o.get("plein").getAsBoolean()) n = 0;
            monde = mc.level;
            if (o.has("r") && o.get("r").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("r")) retirer(e.getAsInt());
            }
            JsonArray or = o.getAsJsonArray("o");
            int ox = or.get(0).getAsInt(), oy = or.get(1).getAsInt(), oz = or.get(2).getAsInt();
            JsonArray a = o.getAsJsonArray("a");
            long now = horloge.getAsLong();
            for (int i = 0; i + CHAMPS <= a.size() && n < MAX_RECUS; i += CHAMPS) {
                int j = n++;
                ID[j] = a.get(i).getAsInt();
                CLE[j] = a.get(i + 1).getAsInt();
                XD[j] = ox + a.get(i + 2).getAsInt() / (double) PRECISION;
                Y[j] = oy + a.get(i + 3).getAsInt() / PRECISION;
                ZD[j] = oz + a.get(i + 4).getAsInt() / (double) PRECISION;
                float ang = (float) Math.toRadians(a.get(i + 5).getAsInt());
                SIN[j] = (float) Math.sin(ang);
                COS[j] = (float) Math.cos(ang);
                FL[j] = a.get(i + 6).getAsInt();
                K[j] = a.get(i + 7).getAsInt() / 100f;
                NEE[j] = now - a.get(i + 8).getAsInt() * 1000L;
                VIE[j] = Math.max(1, a.get(i + 9).getAsInt());
                SOL[j] = lireSol(mc.level, j);
            }
        } catch (RuntimeException ex) {
            return;   // message illisible : on garde ce qu'on a
        }
        if (mc.player != null) selectionner(mc);
    }

    private static void retirer(int id) {
        for (int i = 0; i < n; i++) {
            if (ID[i] == id) {
                ote(i);
                return;
            }
        }
    }

    /** Ôte le pas {@code i} (le dernier prend sa place). */
    private static void ote(int i) {
        int d = --n;
        if (i == d) return;
        ID[i] = ID[d];
        CLE[i] = CLE[d];
        FL[i] = FL[d];
        VIE[i] = VIE[d];
        XD[i] = XD[d];
        Y[i] = Y[d];
        ZD[i] = ZD[d];
        SIN[i] = SIN[d];
        COS[i] = COS[d];
        K[i] = K[d];
        NEE[i] = NEE[d];
        SOL[i] = SOL[d];
    }

    /** Le sol sous le pas {@code i} : inconnu tant que le chunk n'est pas là (ou le bloc pas encore posé), relu chaque seconde. */
    private static byte lireSol(ClientLevel lv, int i) {
        if (lv == null) return SOL_INCONNU;
        POS.set(Mth.floor(XD[i]), Mth.floor(Y[i] - 0.01f), Mth.floor(ZD[i]));
        if (!lv.hasChunkAt(POS)) return SOL_INCONNU;
        BlockState s = lv.getBlockState(POS);
        if (s.isAir()) return SOL_INCONNU;
        return solClair(s.getMapColor(lv, POS).col) ? SOL_CLAIR : SOL_SOMBRE;
    }

    /** Une couleur de carte (RVB) est-elle celle d'un sol clair ? Le contraste automatique ne dépend que de ça, ni de l'heure ni de la lumière. */
    public static boolean solClair(int rgb) {
        return luminance(rgb) >= SEUIL_SOL_CLAIR;
    }

    /** Luminance Rec. 709 d'une couleur RVB, de 0 à 1. */
    public static float luminance(int rgb) {
        return (0.2126f * (rgb >> 16 & 255) + 0.7152f * (rgb >> 8 & 255) + 0.0722f * (rgb & 255)) / 255f;
    }

    /* ================================================================ cycle */

    /** Entrée en concentration : rien de l'entrée précédente (le serveur renvoie la liste complète). */
    static void entree() {
        n = 0;
        nDessin = 0;
        couperSurvol();
    }

    /** Sortie : le survol s'arrête ; les pas restent le temps du fondu de l'effet (150 ms), puis sont oubliés. */
    static void sortie() {
        couperSurvol();
    }

    static void oublier() {
        n = 0;
        nDessin = 0;
        survolId = -1;
        panneauVisible = false;
        monde = null;
    }

    private static void couperSurvol() {
        if (survolId >= 0) finSurvolA = horloge.getAsLong();
        survolId = -1;
    }

    private static void tick(Minecraft mc) {
        if (n == 0) {
            monde = null;   // ne pas retenir un monde quitté ; recevoir() le repose à chaque message
            return;
        }
        if (mc.player == null || mc.level == null || (monde != null && mc.level != monde)
            || (!ModeConcentration.actif() && !ModeConcentration.filtreVoulu())) {
            n = 0;
            nDessin = 0;
            return;
        }
        if (++tickRelecture >= RELECTURE_TICKS) {
            tickRelecture = 0;
            for (int i = 0; i < n; i++) if (SOL[i] == SOL_INCONNU) SOL[i] = lireSol(mc.level, i);
        }
        selectionner(mc);
    }

    /** Oublie les pas expirés, garde les {@value #MAX_DESSINES} plus proches à {@value #PORTEE_MAX} blocs. Aucune allocation. */
    private static void selectionner(Minecraft mc) {
        long now = horloge.getAsLong();
        double px = mc.player.getX(), pz = mc.player.getZ(), r2 = PORTEE_MAX * PORTEE_MAX;
        boolean aveugle = fr.reborn.hud.yeux.CeciteHud.active();
        contourNuit = mc.level == null ? 0f : Mth.clamp((mc.level.getSkyDarken() - NUIT_DEBUT) / (NUIT_PLEINE - NUIT_DEBUT), 0f, 1f);
        int m = 0;
        for (int i = n - 1; i >= 0; i--) {
            if (now - NEE[i] >= VIE[i] * 1000L) {
                ote(i);
            }
        }
        if (!aveugle) {
            for (int i = 0; i < n; i++) {
                double dx = XD[i] - px, dz = ZD[i] - pz, d2 = dx * dx + dz * dz;
                if (d2 <= r2) TRI[m++] = (long) (d2 * 256.0) << 16 | i;
            }
        }
        Arrays.sort(TRI, 0, m);
        nDessin = Math.min(m, MAX_DESSINES);
        dessinSurClair = false;
        for (int i = 0; i < nDessin; i++) {
            int j = (int) (TRI[i] & 0xFFFF);
            DESSIN[nDessin - 1 - i] = j;
            dessinSurClair |= SOL[j] == SOL_CLAIR;
        }
    }

    /* ================================================================ rendu */

    private static void collecter(LevelRenderContext ctx) {
        if (nDessin == 0 || ByakuganClient.active()) return;
        float niveau = ModeConcentration.niveau();
        if (niveau <= 0.004f) return;
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        Minecraft mc = Minecraft.getInstance();
        if (cam == null || mc.player == null) return;
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        joueurX = mc.player.getX();
        joueurZ = mc.player.getZ();
        maintenant = horloge.getAsLong();
        niveauImage = niveau;
        autoImage = RebornPrefs.INSTANCE.tracesAuto;
        rgbImage = autoImage ? IVOIRE : RebornPrefs.INSTANCE.tracesCouleur & 0xFFFFFF;
        if (autoImage && dessinSurClair && contourNuit > 0f) {
            ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.entityTranslucentEmissive(TEX_CONTOUR), GEOMETRIE_CONTOUR);
        }
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.entityTranslucentEmissive(TEX), GEOMETRIE);
    }

    /** Dessine la sélection (sans rien capturer : aucune allocation par image). */
    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer GEOMETRIE = Traces::dessiner;

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer GEOMETRIE_CONTOUR = Traces::dessinerContour;

    private static void dessiner(PoseStack.Pose pose, VertexConsumer vc) {
        for (int d = 0; d < nDessin; d++) {
            int i = DESSIN[d];
            if (i < n) pas(pose, vc, i, false);
        }
    }

    private static void dessinerContour(PoseStack.Pose pose, VertexConsumer vc) {
        for (int d = 0; d < nDessin; d++) {
            int i = DESSIN[d];
            if (i < n && SOL[i] == SOL_CLAIR) pas(pose, vc, i, true);
        }
    }

    /** Opacité selon la fraction de vie écoulée : pleine jusqu'à 60 %, descend jusqu'à 25 % : une trace reste lisible presque toute sa vie. */
    static float opaciteAge(float u) {
        if (u >= 1f) return 0f;
        if (u <= AGE_PLEIN) return 1f;
        float t = (u - AGE_PLEIN) / (1f - AGE_PLEIN);
        return 1f - (1f - OPACITE_FIN) * (t * t * (3f - 2f * t));
    }

    /** Opacité selon la distance au joueur : pleine à 16 blocs, rien à 24. */
    static float opaciteDistance(float d) {
        return d <= PORTEE_PLEINE ? 1f : Math.max(0f, 1f - (d - PORTEE_PLEINE) / (PORTEE_MAX - PORTEE_PLEINE));
    }

    /** Colonne de la texture (0 = semelle pleine) à la fraction de vie {@code u}. */
    static int colonne(float u, int id) {
        float decale = (Math.floorMod(id, 5) - 2) * 0.02f;
        int c = 0;
        for (float s : EFFRITE) if (u >= s + decale) c++;
        return c;
    }

    /** Le quad d'un pas ; {@code contour} : un texel de plus de chaque côté, dans {@link #TEX_CONTOUR}. */
    private static void pas(PoseStack.Pose pose, VertexConsumer vc, int i, boolean contour) {
        int fl = FL[i];
        float u = (maintenant - NEE[i]) / (VIE[i] * 1000f);
        float dx = (float) (XD[i] - joueurX), dz = (float) (ZD[i] - joueurZ);
        float a = ALPHA * opaciteAge(u) * opaciteDistance((float) Math.sqrt(dx * dx + dz * dz)) * niveauImage;
        if ((fl & ACCROUPI) != 0) a *= ALPHA_ACCROUPI;
        if ((fl & CHARGE) != 0) a = Math.min(1f, a * ALPHA_CHARGE);
        if (contour) a = a / ALPHA * ALPHA_CONTOUR * contourNuit;
        if (a <= 0.004f) return;
        int argb = (Math.round(a * 255f) & 0xFF) << 24 | (contour ? rgbImage : autoImage && SOL[i] == SOL_CLAIR ? SEMELLE_SOMBRE : rgbImage);

        float k = K[i];
        float w = SEMELLE_W * TEXEL * k * ((fl & CHARGE) != 0 ? LARGEUR_CHARGE : 1f);
        int rangs = (fl & SPRINT) != 0 ? RANGS_SPRINT : SEMELLE_H;
        float l = rangs * TEXEL * k;
        float sin = SIN[i], cos = COS[i];
        float fx = sin, fz = cos;           // avant du pied
        float rx = -cos, rz = sin;          // droite du marcheur (monde Minecraft)
        float cx = (float) (XD[i] - camX), cz = (float) (ZD[i] - camZ), y = (float) (Y[i] - camY) + LEVE;
        // sprint : l'avant du pied reste à sa place, le talon ne touche pas
        float avance = (SEMELLE_H * TEXEL * k - l) / 2f;
        cx += fx * avance;
        cz += fz * avance;
        int marge = contour ? 1 : 0;
        float mw = marge * w / SEMELLE_W, ml = marge * l / rangs;
        // O : coin avant gauche ; A : vers la droite ; B : vers le talon
        float ox = cx + fx * (l / 2f + ml) - rx * (w / 2f + mw), oz = cz + fz * (l / 2f + ml) - rz * (w / 2f + mw);
        float ax = rx * (w + 2f * mw), az = rz * (w + 2f * mw), bx = -fx * (l + 2f * ml), bz = -fz * (l + 2f * ml);

        int col = colonne(u, ID[i]);
        int rang = (fl & SANG) != 0 ? RANGEE_SANG : Math.floorMod(ID[i], ORDRES);
        float u0 = (col * CASE_W + 1 - marge) / (float) TEX_W, u1 = u0 + (SEMELLE_W + 2 * marge) / (float) TEX_W;
        // le contour d'un pas de sprint : ses propres rangées, fermées au talon
        int hauteur = contour ? CONTOUR_H : TEX_H;
        float v0 = ((contour && (fl & SPRINT) != 0 ? rang + RANGEES : rang) * CASE_H + 1 - marge) / (float) hauteur, v1 = v0 + (rangs + 2 * marge) / (float) hauteur;
        if ((fl & DROIT) != 0) {   // le pied droit : la même semelle, en miroir
            float t = u0;
            u0 = u1;
            u1 = t;
        }
        sommet(pose, vc, ox, y, oz, u0, v0, argb);
        sommet(pose, vc, ox + bx, y, oz + bz, u0, v1, argb);
        sommet(pose, vc, ox + ax + bx, y, oz + az + bz, u1, v1, argb);
        sommet(pose, vc, ox + ax, y, oz + az, u1, v0, argb);
    }

    private static void sommet(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 1f, 0f);
    }

    /* ================================================================ temps écoulé au survol */

    /**
     * Une fois par tick ({@link ModeConcentration}) : le pas sous le curseur, à {@value #PORTEE_SURVOL} blocs du joueur au plus,
     * le plus proche du curseur. Un seul rayon, vers le pas retenu : un mur devant le cache.
     */
    static void survoler(Minecraft mc, double curseurX, double curseurY) {
        if (nDessin == 0 || ByakuganClient.active() || mc.gameRenderer.mainCamera() == null) {
            couperSurvol();
            return;
        }
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        float h = mc.getWindow().getGuiScaledHeight();
        double tan = Math.tan(Math.toRadians(mc.gameRenderer.mainCamera().getFov() / 2.0));
        double meilleur = Double.MAX_VALUE;
        int choisi = -1;
        for (int d = 0; d < nDessin; d++) {
            int i = DESSIN[d];
            if (i >= n) continue;
            double jx = XD[i] - mc.player.getX(), jz = ZD[i] - mc.player.getZ();
            if (jx * jx + jz * jz > PORTEE_SURVOL * PORTEE_SURVOL) continue;
            if (!ProjectionMonde.versGui(XD[i], Y[i] + LEVE, ZD[i], V)) continue;
            double ex = V.x - curseurX, ey = V.y - curseurY, e = Math.sqrt(ex * ex + ey * ey);
            double dx = XD[i] - cam.x, dy = Y[i] - cam.y, dz = ZD[i] - cam.z;
            // rayon du pas à l'écran : la moitié de sa longueur, au moins quelques pixels
            double rayon = Math.max(SURVOL_MIN_GUI, 0.19 * K[i] * h / (2.0 * tan * Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz))));
            if (e <= rayon && e < meilleur) {
                meilleur = e;
                choisi = i;
            }
        }
        if (choisi >= 0) {
            Vec3 cible = new Vec3(XD[choisi], Y[choisi] + 0.08, ZD[choisi]);
            HitResult mur = mc.level.clip(new ClipContext(cam, cible, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (mur.getType() != HitResult.Type.MISS && mur.getLocation().distanceToSqr(cible) > 0.5 * 0.5) choisi = -1;
        }
        if (choisi < 0) {
            couperSurvol();
            return;
        }
        long now = horloge.getAsLong();
        if (ID[choisi] != survolId) {
            if (panneauVisible && alphaAffiche > 0f) {
                // d'un pas à l'autre, ou repris pendant le fondu de sortie : le panneau glisse d'où il est, l'opacité continue
                departX = affX;
                departY = affY;
                departZ = affZ;
                glisseA = now;
                if (survolId < 0) survolA = now - Math.round(alphaAffiche * ENTREE_MS);
            } else {
                survolA = now;
                glisseA = Long.MIN_VALUE / 4;
            }
            survolId = ID[choisi];
        }
        survolX = XD[choisi];
        survolY = Y[choisi];
        survolZ = ZD[choisi];
        survolK = K[choisi];
        palier = TracesAge.palier((now - NEE[choisi]) / 1000L);
        survolClair = SOL[choisi] == SOL_CLAIR;
        panneauVisible = true;
    }

    /** Couche du HUD : le petit panneau au-dessus du pas survolé, son fil et son point. Aucune allocation par image. */
    private static void panneau(GuiGraphicsExtractor ctx) {
        if (!panneauVisible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        long now = horloge.getAsLong();
        float a;
        if (survolId >= 0) {
            a = Math.min(1f, (now - survolA) / (float) ENTREE_MS);
        } else {
            a = 1f - (now - finSurvolA) / (float) SORTIE_MS;
            if (a <= 0f) {
                panneauVisible = false;
                alphaAffiche = 0f;
                return;
            }
        }
        alphaAffiche = a;
        float t = Math.min(1f, Math.max(0f, (now - glisseA) / (float) GLISSE_MS)), g = 1f - (1f - t) * (1f - t) * (1f - t);
        affX = departX + (survolX - departX) * g;
        affY = departY + (survolY - departY) * g;
        affZ = departZ + (survolZ - departZ) * g;
        a *= ModeConcentration.niveau();
        if (a <= 0.01f || !ProjectionMonde.versPx(affX, affY + LEVE, affZ, V)) return;

        double k = TexteRaster.kHud() / 2.0;
        int p = Math.max(1, TexteRaster.arrondi(k));
        if (palier != largeurPalier || k != largeurK) {
            double wt = TexteRaster.largeur(TITRE, TexteRaster.Police.RECIT, CAP_TITRE * k, 0, true);
            double wa = TexteRaster.largeur(TracesAge.texte(palier), TexteRaster.Police.INTER_MEDIUM, CAP_AGE * k, 0, true);
            if (Double.isNaN(wt) || Double.isNaN(wa)) return;   // polices pas encore ouvertes
            largeurPanneau = (int) Math.ceil(Math.max(wt, wa)) + 2 * TexteRaster.arrondi(MARGE_X * k);
            largeurPalier = palier;
            largeurK = k;
        }
        int w = largeurPanneau, h = TexteRaster.arrondi(HAUT * k);
        int sw = mc.getWindow().getWidth(), sh = mc.getWindow().getHeight();
        int px = (int) V.x, py = (int) V.y, marge = TexteRaster.arrondi(MARGE_ECRAN * k);
        // demi-longueur du pas à l'écran : le panneau ne le couvre pas, même de près
        double tan = Math.tan(Math.toRadians(mc.gameRenderer.mainCamera().getFov() / 2.0));
        int rayon = (int) Math.ceil(0.19 * survolK * sh / (2.0 * tan * Math.max(0.5, V.z)));
        int fil = Math.max(TexteRaster.arrondi(FIL * k), rayon + TexteRaster.arrondi(FIL_JOUR * k));
        int x = Mth.clamp(px - w / 2, marge, sw - marge - w);
        boolean dessus = py - fil - h >= marge;
        int y = Mth.clamp(dessus ? py - fil - h : py + fil, marge, sh - marge - h);
        // jamais sur le réticule : au-dessus de lui (ou dessous s'il n'y a pas la place)
        int cx = sw / 2, cy = sh / 2, r = TexteRaster.arrondi(RETICULE * k);
        if (x < cx + r && x + w > cx - r && y < cy + r && y + h > cy - r) {
            y = cy - r - h >= marge ? cy - r - h : cy + r;
        }
        pnX = x;
        pnY = y;
        pnW = w;
        pnH = h;
        pnPointX = px;
        pnPointY = py;

        // sur un sol clair, de jour, le point et le fil sont sombres (le contraste automatique des traces) ; sinon bleu chakra pâle
        boolean clairJour = survolClair && contourNuit < 0.5f;
        int coeur = DaKit.argb(clairJour ? SEMELLE_SOMBRE : CHAKRA_PALE, a);
        int anneau = DaKit.argb(clairJour ? IVOIRE : NUIT, (clairJour ? 0.70f : 0.55f) * a);
        int filCol = DaKit.argb(clairJour ? SEMELLE_SOMBRE : CHAKRA_PALE, (clairJour ? 0.60f : ALPHA_FIL) * a);
        int fond = DaKit.argb(NUIT, ALPHA_FOND * a), filet = DaKit.argb(IVOIRE, ALPHA_FILET * a);
        int clou = DaKit.alpha(DaKit.OR_MOYEN, ALPHA_CLOU * a);
        DaKit.debutPx(ctx);
        // le fil, du bord du panneau au point (vertical, calé sur le point tant qu'il reste sous le panneau)
        int fx = Mth.clamp(px, x + 3 * p, x + w - 4 * p);
        if (y + h <= py - 2 * p) ctx.fill(fx, y + h, fx + p, py - 2 * p, filCol);
        else if (y >= py + 3 * p) ctx.fill(fx, py + 3 * p, fx + p, y, filCol);
        // le point : 3 × 3 cerné d'un pixel, coins coupés
        ctx.fill(px - p, py - 2 * p, px + 2 * p, py - p, anneau);
        ctx.fill(px - p, py + 2 * p, px + 2 * p, py + 3 * p, anneau);
        ctx.fill(px - 2 * p, py - p, px - p, py + 2 * p, anneau);
        ctx.fill(px + 2 * p, py - p, px + 3 * p, py + 2 * p, anneau);
        ctx.fill(px - p, py - p, px + 2 * p, py + 2 * p, coeur);
        // le cartouche : fond, filet d'un pixel, un clou d'or de 2 × 2 à chaque coin
        ctx.fill(x + p, y + p, x + w - p, y + h - p, fond);
        ctx.fill(x + 2 * p, y, x + w - 2 * p, y + p, filet);
        ctx.fill(x + 2 * p, y + h - p, x + w - 2 * p, y + h, filet);
        ctx.fill(x, y + 2 * p, x + p, y + h - 2 * p, filet);
        ctx.fill(x + w - p, y + 2 * p, x + w, y + h - 2 * p, filet);
        ctx.fill(x, y, x + 2 * p, y + 2 * p, clou);
        ctx.fill(x + w - 2 * p, y, x + w, y + 2 * p, clou);
        ctx.fill(x, y + h - 2 * p, x + 2 * p, y + h, clou);
        ctx.fill(x + w - 2 * p, y + h - 2 * p, x + w, y + h, clou);
        int mx = x + w / 2;
        TEXTE_TITRE.ligne(TITRE, STYLE_TITRE, CAP_TITRE * k, k, 0, mx, y + TexteRaster.arrondi(Y_TITRE * k)).dessinerPx(ctx, a);
        TEXTE_AGE.ligne(TracesAge.texte(palier), STYLE_AGE, CAP_AGE * k, k, 0, mx, y + TexteRaster.arrondi(Y_AGE * k)).dessinerPx(ctx, a);
        DaKit.finPx(ctx);
    }

    /* ================================================================ banc */

    public static int nombre() { return n; }

    public static int dessines() { return nDessin; }

    /** Le contour de nuit, de 0 à 1 (banc). */
    public static float contourNuit() { return contourNuit; }

    /** Id du pas survolé, -1 sinon. */
    public static int survolId() { return survolId; }

    /** Palier d'âge du pas survolé ({@link TracesAge#palier}). */
    public static int palierPanneau() { return palier; }

    /** Dernier panneau dessiné, en pixels de la fenêtre : x, y, largeur, hauteur, point du pas (x, y). */
    public static int[] panneauBanc() { return new int[] {pnX, pnY, pnW, pnH, pnPointX, pnPointY}; }

    /** Le sol sous le pas {@code id} : 0 inconnu, 1 sombre, 2 clair, -1 s'il n'est pas là. */
    public static int solDe(int id) {
        for (int i = 0; i < n; i++) if (ID[i] == id) return SOL[i];
        return -1;
    }

    /** L'opacité d'âge et la colonne d'effritement à la fraction de vie {@code u} (banc). */
    public static float opaciteAgeBanc(float u) { return opaciteAge(u); }

    public static int colonneBanc(float u, int id) { return colonne(u, id); }

    /** Position du pas {@code id} (banc), null s'il n'est pas là. */
    public static double[] position(int id) {
        for (int i = 0; i < n; i++) if (ID[i] == id) return new double[] {XD[i], Y[i], ZD[i]};
        return null;
    }

    /** Clé de piste du pas {@code id} (banc ; le rendu ne s'en sert pas : même semelle pour tous), -1 s'il n'est pas là. */
    public static int cle(int id) {
        for (int i = 0; i < n; i++) if (ID[i] == id) return CLE[i];
        return -1;
    }
}
