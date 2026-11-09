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
import net.minecraft.network.chat.Component;
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
 *       Byakugan, rien (son filtre négatif inverserait le pas ; comme le contour de
 *       {@link ModeConcentration#halo}).</li>
 *   <li><b>Temps écoulé</b>, comme chez Ketto : le curseur sur un pas à {@value #PORTEE_SURVOL} blocs au plus, petite étiquette
 *       « à l'instant » / « il y a 7 min » / « il y a 1 h 20 » à côté, ivoire (sombre sur un sol clair, de jour), <b>sans fond</b>, sans nom,
 *       fondu de {@value #LIBELLE_MS} ms.</li>
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
    public static final long LIBELLE_MS = 120, LIBELLE_SORTIE_MS = 90;
    private static final int COULEUR_LIBELLE = DaKit.CREME, COULEUR_LIBELLE_SOMBRE = 0xFF000000 | SEMELLE_SOMBRE;
    /** Curseur à moins de ces pixels GUI du centre du pas, quelle que soit la distance (viser un petit pas lointain). */
    private static final float SURVOL_MIN_GUI = 5f;

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
    private static int survolId = -1, libelleMinutes;
    private static double survolX, survolY, survolZ;
    private static float survolK = 1f;
    private static long survolA = Long.MIN_VALUE / 4, finSurvolA = Long.MIN_VALUE / 4;
    private static boolean libelleVisible, survolClair;
    /** Le dernier texte construit : aucune allocation tant que la durée affichée ne change pas. */
    private static int libelleConstruit = -1;
    private static Component libelleTexte;
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
            (ctx, tc) -> libelle(ctx));
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
        libelleVisible = false;
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
            if (survolId < 0 && now - finSurvolA > LIBELLE_SORTIE_MS) survolA = now;
            else if (survolId < 0) survolA = now - LIBELLE_MS / 2;   // du pas voisin au suivant : pas de clignotement
            survolId = ID[choisi];
        }
        survolX = XD[choisi];
        survolY = Y[choisi];
        survolZ = ZD[choisi];
        survolK = K[choisi];
        libelleMinutes = minutes((now - NEE[choisi]) / 1000L);
        survolClair = SOL[choisi] == SOL_CLAIR;
        libelleVisible = true;
    }

    /** Âge arrondi à la minute : 0 = « à l'instant ». */
    public static int minutes(long ageSecondes) {
        return (int) Math.max(0L, Math.round(ageSecondes / 60.0));
    }

    /** Le texte de l'étiquette : « à l'instant », « il y a 7 min », « il y a 1 h », « il y a 1 h 20 », « il y a 1 h 05 ». */
    public static String texteDuree(int minutes) {
        if (minutes <= 0) return "à l'instant";
        if (minutes < 60) return "il y a " + minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return "il y a " + h + " h" + (m == 0 ? "" : m < 10 ? " 0" + m : " " + m);
    }

    /** Couche du HUD : l'étiquette, à côté du pas, sans fond. */
    private static void libelle(GuiGraphicsExtractor ctx) {
        if (!libelleVisible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        long now = horloge.getAsLong();
        float a;
        if (survolId >= 0) {
            a = Math.min(1f, (now - survolA) / (float) LIBELLE_MS);
        } else {
            a = 1f - (now - finSurvolA) / (float) LIBELLE_SORTIE_MS;
            if (a <= 0f) {
                libelleVisible = false;
                return;
            }
        }
        a *= ModeConcentration.niveau();
        if (a <= 0.01f || !ProjectionMonde.versGui(survolX, survolY + LEVE, survolZ, V)) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double dx = survolX - cam.x, dy = survolY - cam.y, dz = survolZ - cam.z;
        double tan = Math.tan(Math.toRadians(mc.gameRenderer.mainCamera().getFov() / 2.0));
        float rayon = (float) Math.max(3.0, 0.12 * survolK * mc.getWindow().getGuiScaledHeight()
            / (2.0 * tan * Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz))));
        if (libelleMinutes != libelleConstruit) {
            libelleConstruit = libelleMinutes;
            // à cette taille, une espace simple se lit à peine plus large que l'écart entre deux lettres
            libelleTexte = DaKit.inter(texteDuree(libelleMinutes).replace(" ", "  "));
        }
        float x = V.x + rayon + 5f, y = V.y;
        // sur un sol clair, de jour : sombre, cerné d'un pixel pâle ; la nuit ce sol ne l'est plus : ivoire, comme ailleurs. Jamais de fond
        if (survolClair && contourNuit < 0.5f) {
            float px = DaKit.pxEcran() * Math.max(1, Math.round(DaKit.echelleGui() / 2f));
            int pale = DaKit.alpha(COULEUR_LIBELLE, a * 0.55f);
            DaKit.inter7(ctx, mc.font, libelleTexte, x - px, y, pale, 0);
            DaKit.inter7(ctx, mc.font, libelleTexte, x + px, y, pale, 0);
            DaKit.inter7(ctx, mc.font, libelleTexte, x, y - px, pale, 0);
            DaKit.inter7(ctx, mc.font, libelleTexte, x, y + px, pale, 0);
            DaKit.inter7(ctx, mc.font, libelleTexte, x, y, DaKit.alpha(COULEUR_LIBELLE_SOMBRE, a), 0);
        } else DaKit.inter7o(ctx, mc.font, libelleTexte, x, y, DaKit.alpha(COULEUR_LIBELLE, a), 0);
    }

    /* ================================================================ banc */

    public static int nombre() { return n; }

    public static int dessines() { return nDessin; }

    /** Le contour de nuit, de 0 à 1 (banc). */
    public static float contourNuit() { return contourNuit; }

    /** Id du pas survolé, -1 sinon. */
    public static int survolId() { return survolId; }

    public static int minutesLibelle() { return libelleMinutes; }

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
