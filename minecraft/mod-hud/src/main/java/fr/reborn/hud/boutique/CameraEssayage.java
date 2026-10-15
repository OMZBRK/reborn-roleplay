package fr.reborn.hud.boutique;

import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * La caméra d'essayage du tailleur (BOUTIQUES.md §3.2-§3.4, maquettes 02 et 03) : elle quitte les yeux et se pose
 * <b>dans le monde, en face du personnage</b> (point de vue de l'estrade envoyé par le serveur, sinon calculé là où il
 * se tient), à hauteur de poitrine, champ resserré (55°). Trois cadrages — en pied, buste, visage — avec un glissement
 * de 300 ms ; la caméra reste fixe quand le personnage pivote (il pivote pour de vrai, côté {@link BoutiqueScreen}).
 * Lu chaque image par {@code CameraRepositionMixin} (position, regard) et {@code CameraPhotoMixin} (champ).
 *
 * <p>Repli (§3.4) : si un bloc coupe la ligne entre le personnage et le point de vue, {@link #begin} renvoie
 * {@code false} : l'écran dessine alors le personnage en interface, sur un voile.
 */
public final class CameraEssayage {

    public static final CameraEssayage INSTANCE = new CameraEssayage();

    /** Cadrages : distance devant le personnage (part de la distance du staff) et hauteur visée au-dessus des pieds. */
    private static final double[] RECUL = {1.0, 0.70, 0.50}, VISEE = {0.0, 0.30, 0.62};
    public static final String[] NOMS = {"En pied", "Buste", "Visage"};
    private static final long GLISSE_MS = 300;
    public static final float FOV = 55f;

    private boolean active;
    private Vec3 pied = Vec3.ZERO;
    /** Direction où le personnage regarde (vers la caméra), en degrés vanilla. */
    private float face;
    private double dist = 2.7, haut = 1.0;
    private int cadrage, cadrageAvant;
    private long glisseDepuis;
    private long ouvertLe;
    private float yeuxYaw, yeuxPitch;

    private CameraType savedType = CameraType.FIRST_PERSON;
    private RebornCamera.Mode savedMode = RebornCamera.Mode.SHOULDER;

    private CameraEssayage() {}

    public boolean active() { return active; }

    public float face() { return face; }

    public int cadrage() { return cadrage; }

    /**
     * Pose la caméra : {@code pied} = place du client, {@code face} = direction de son regard, {@code dist} / {@code haut}
     * = point de vue du staff. Renvoie {@code false} (rien n'est changé) si le point de vue est obstrué.
     */
    public boolean begin(Minecraft mc, Vec3 pied, float face, double dist, double haut) {
        if (mc.player == null || mc.level == null || mc.options == null) return false;
        this.pied = pied;
        this.face = face;
        this.dist = Math.max(1.2, Math.min(5.0, dist));
        this.haut = Math.max(0.5, Math.min(1.8, haut));
        Vec3 vise = cible(0), cam = point(0);
        HitResult hit = mc.level.clip(new ClipContext(vise, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) return false;
        if (!active) {
            savedType = mc.options.getCameraType();
            savedMode = RebornCamera.INSTANCE.mode();
        }
        // la caméra épaule Reborn ne doit ni recadrer ni ramener le regard du joueur vers elle
        RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        cadrage = cadrageAvant = 0;
        glisseDepuis = 0;
        ouvertLe = System.currentTimeMillis();
        yeuxYaw = mc.player.getYRot();
        yeuxPitch = mc.player.getXRot();
        active = true;
        return true;
    }

    public void end(Minecraft mc) {
        if (!active) return;
        active = false;
        if (mc.options != null) mc.options.setCameraType(savedType);
        RebornCamera.INSTANCE.setMode(savedMode);
        if (mc.player != null) RebornCamera.INSTANCE.initOrientation(mc.player.getYRot(), mc.player.getXRot());
    }

    /** Repli sur le voile : la caméra reste aux yeux ; seul le cadrage (taille du dessin) sert. */
    public void repli() {
        cadrage = cadrageAvant = 0;
        glisseDepuis = 0;
    }

    /** Change de cadrage (0 en pied, 1 buste, 2 visage) avec un glissement. */
    public void cadrer(int c) {
        c = Math.max(0, Math.min(2, c));
        if (c == cadrage) return;
        cadrageAvant = cadrage;
        cadrage = c;
        glisseDepuis = System.currentTimeMillis();
    }

    private Vec3 dir() {
        double r = Math.toRadians(face);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private Vec3 cible(int c) { return pied.add(0, haut + VISEE[c], 0); }

    private Vec3 point(int c) { return cible(c).add(dir().scale(dist * RECUL[c])); }

    /** Avancement du glissement (0 → 1), adouci. */
    private float t() {
        long now = System.currentTimeMillis();
        float a = glisseDepuis == 0 ? 1f : Math.min(1f, (now - glisseDepuis) / (float) GLISSE_MS);
        return a * a * (3 - 2 * a);
    }

    /** Position de la caméra cette image : glissement des yeux à l'ouverture, puis entre cadrages. */
    public Vec3 position(Vec3 yeux) {
        Vec3 p = point(cadrageAvant).lerp(point(cadrage), t());
        float a = ouverture();
        return a < 1f && yeux != null ? yeux.lerp(p, a) : p;
    }

    /** Avancement du glissement d'ouverture (yeux → point de vue), adouci ; 1 ensuite. */
    private float ouverture() {
        long o = System.currentTimeMillis() - ouvertLe;
        if (o >= GLISSE_MS) return 1f;
        float a = o / (float) GLISSE_MS;
        return a * a * (3 - 2 * a);
    }

    /** La caméra regarde le personnage (elle tourne depuis le regard du joueur pendant l'ouverture). */
    public float yaw() { return net.minecraft.util.Mth.rotLerp(ouverture(), yeuxYaw, face + 180f); }

    /** Regard horizontal (la hauteur visée suit le cadrage). */
    public float pitch() { return net.minecraft.util.Mth.lerp(ouverture(), yeuxPitch, 0f); }
}
