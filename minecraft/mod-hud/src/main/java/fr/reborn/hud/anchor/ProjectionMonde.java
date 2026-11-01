package fr.reborn.hud.anchor;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.reborn.hud.mixin.GameRendererTeteAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Projection d'un point du monde sur l'écran <b>exactement comme le monde est dessiné</b> (31/10, socle des plaques,
 * bulles et /me) : {@code projection × balancement (coup reçu, marche) × spirale (nausée, portail) × rotation de vue},
 * la chaîne de {@code GameRenderer#renderLevel}. {@code Camera#getViewRotationProjectionMatrix}, qu'utilisaient les
 * plaques, ignore le balancement : la plaque pompait de 10 à 20 px en marchant (option « Balancement de la vue ») et
 * décrochait quand on était touché.
 *
 * <p>La matrice est construite <b>une fois par image</b>, à la première demande, à partir de ce que le jeu a déjà
 * extrait avant le HUD ({@code CameraRenderState}, {@code OptionsRenderState}) : {@code bobHurt} / {@code bobView}
 * (privés, appelés par {@link GameRendererTeteAccessor}) ne lisent que ces états, donc le HUD et le monde de la même
 * image voient la même caméra. Non reproduit : la projection Panini de Sodium Extra (post-effet, désactivé par défaut).
 *
 * <p>Sorties en <b>pixels physiques</b> (fenêtre) arrondis à l'entier, ou en coordonnées GUI calées sur ces pixels
 * ({@code round(x × gs) / gs}) : une plaque translatée sur cette ancre puis dessinée en coordonnées entières ne
 * scintille plus (le contour du style « Détouré » changeait avec la position sous-pixel). {@code z} = distance
 * caméra → point, en blocs. Thread de rendu uniquement, aucune allocation par appel.
 */
public final class ProjectionMonde {

    private ProjectionMonde() {}

    /** Au-delà (coordonnées normalisées), le point est « hors écran large » : rien à dessiner. */
    public static final float NDC_LARGE = 1.25f;
    /** Plan proche du test « derrière la caméra » (même seuil que les anciennes plaques). */
    private static final float W_MIN = 0.05f;

    private static final Matrix4f CLIP = new Matrix4f();
    private static final PoseStack BOB = new PoseStack();
    private static final Vector4f P = new Vector4f();
    /** Axe « haut » de la caméra en coordonnées monde (sans le balancement), pour le sommet des têtes ({@link TeteAncre}). */
    private static final Vector3f HAUT = new Vector3f();
    /** Axe de la spirale de nausée / portail ({@code renderLevel}). */
    private static final Vector3f AXE_SPIRALE = new Vector3f(0f, Mth.SQRT_OF_TWO / 2f, Mth.SQRT_OF_TWO / 2f);

    private static long image = Long.MIN_VALUE;
    private static boolean pret;
    private static double camX, camY, camZ;

    /** Point du monde → pixels physiques de la fenêtre (entiers). {@code false} : derrière la caméra ou hors écran large. */
    public static boolean versPx(double x, double y, double z, Vector3f out) {
        if (!preparer()) return false;
        float rx = (float) (x - camX), ry = (float) (y - camY), rz = (float) (z - camZ);
        CLIP.transform(P.set(rx, ry, rz, 1f));
        if (P.w <= W_MIN) return false;
        float nx = P.x / P.w, ny = P.y / P.w;
        if (nx < -NDC_LARGE || nx > NDC_LARGE || ny < -NDC_LARGE || ny > NDC_LARGE) return false;
        Window w = Minecraft.getInstance().getWindow();
        out.set(Math.round((nx * 0.5f + 0.5f) * w.getWidth()), Math.round((0.5f - ny * 0.5f) * w.getHeight()),
            (float) Math.sqrt(rx * rx + ry * ry + rz * rz));
        return true;
    }

    /** Point du monde → coordonnées GUI calées sur le pixel physique. Mêmes refus que {@link #versPx}. */
    public static boolean versGui(double x, double y, double z, Vector3f out) {
        if (!versPx(x, y, z, out)) return false;
        float gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        out.x /= gs;
        out.y /= gs;
        return true;
    }

    /** Axe « haut » de la caméra (monde, unitaire) de l'image courante dans {@code out} ; {@code false} si pas de caméra. */
    static boolean haut(Vector3f out) {
        if (!preparer()) return false;
        out.set(HAUT);
        return true;
    }

    /** Matrice de l'image courante (clé : le compteur d'images de {@link TeteAncre}). */
    private static boolean preparer() {
        long img = TeteAncre.image();
        if (img == image) return pret;
        image = img;
        Minecraft mc = Minecraft.getInstance();
        GameRenderState gs = mc.gameRenderer.gameRenderState();
        CameraRenderState cs = gs.levelRenderState.cameraRenderState;
        pret = cs != null && cs.initialized && cs.pos != null;
        if (!pret) return false;
        GameRendererTeteAccessor acc = (GameRendererTeteAccessor) mc.gameRenderer;
        BOB.setIdentity();
        acc.reborn$bobHurt(cs, BOB);
        if (gs.optionsRenderState.bobView) acc.reborn$bobView(cs, BOB);
        CLIP.set(cs.projectionMatrix).mul(BOB.last().pose());
        spirale(mc, gs, acc);
        CLIP.mul(cs.viewRotationMatrix);
        cs.viewRotationMatrix.positiveY(HAUT);   // monde → vue : son +Y d'avant la rotation = le haut de la caméra
        camX = cs.pos.x;
        camY = cs.pos.y;
        camZ = cs.pos.z;
        return true;
    }

    /** Déformation nausée / portail, copie de {@code renderLevel} (même tick partiel, mêmes constantes). */
    private static void spirale(Minecraft mc, GameRenderState gs, GameRendererTeteAccessor acc) {
        LocalPlayer j = mc.player;
        if (j == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float ecran = gs.optionsRenderState.screenEffectScale;
        float f = Math.max(Mth.lerp(pt, j.oPortalEffectIntensity, j.portalEffectIntensity),
            j.getEffectBlendFactor(MobEffects.NAUSEA, pt)) * ecran * ecran;
        if (f <= 0f) return;
        float g = 5f / (f * f + 5f) - f * 0.04f;
        g *= g;
        float a = (acc.reborn$spiraleTemps() + pt * acc.reborn$spiraleVitesse()) * Mth.DEG_TO_RAD;
        CLIP.rotate(a, AXE_SPIRALE).scale(1f / g, 1f, 1f).rotate(-a, AXE_SPIRALE);
    }
}
