package fr.reborn.hud.menu.tirage;

import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Caméra du <b>test de la Feuille</b> (refaite le 16/10) : un plan fixe posé <b>dans le monde</b>, de ¾ face, côté de
 * la main qui tient la feuille, le personnage cadré dans le tiers gauche (l'écran occupe la droite). Elle glisse depuis
 * la caméra du joueur à l'ouverture et y revient en glissant à la fin, que le test soit validé, annulé, remplacé par un
 * autre écran ou coupé par une déconnexion (retour immédiat dans ce dernier cas).
 *
 * <p><b>Pourquoi un plan fixe</b> (bug corrigé le 16/10) : l'ancien test réécrivait l'orbite de la caméra épaule à
 * chaque tick ({@code camYaw = lacet du joueur + 75°}) alors que la caméra épaule, au repos, aligne le lacet du joueur
 * sur l'orbite ({@code KeyboardInputMixin}) : les deux se poursuivaient, le joueur et la vue tournaient de 75° par tick
 * (1 500°/s, lacet envoyé au serveur, vu des autres) et le lacet grandissait sans fin. Ici la caméra Reborn passe en
 * mode {@link RebornCamera.Mode#VANILLA} le temps du test (plus rien n'oriente le joueur), le plan est calculé une fois
 * et n'est plus jamais réécrit ; orbite, distance, mode, vue, HUD sont sauvegardés une fois et rendus à la fin.
 *
 * <p>Lu chaque image par {@code CameraRepositionMixin} (position, regard), {@code CameraPhotoMixin} (champ) ;
 * {@code CameraThirdPersonMixin} se retire pendant le test. Anti-décor : le plan est choisi parmi plusieurs angles et
 * distances, le premier dont la vue sur le personnage n'est coupée par aucun bloc ; sinon le moins mauvais, raccourci
 * devant l'obstacle.
 */
public final class CameraTirage {

    public static final CameraTirage INSTANCE = new CameraTirage();

    static final long ENTREE_MS = 750, SORTIE_MS = 550;
    /** Champ du plan (portrait) ; on glisse depuis celui du joueur. */
    static final float FOV = 52f;

    private enum Etat { INACTIF, ACTIF, RETOUR }

    private Etat etat = Etat.INACTIF;
    private long depuis;
    private boolean liberer;

    // le plan
    private Vec3 pos = Vec3.ZERO;
    private float yaw, pitch;
    /** Point de la main droite (feuille), pour les particules. */
    private Vec3 main = Vec3.ZERO;
    /** Point visé (poitrine) : les glissements tournent autour de lui, ils ne traversent jamais le personnage. */
    private Vec3 foyer = Vec3.ZERO;
    // départ du glissement d'entrée / du retour
    private Vec3 posDepart = Vec3.ZERO;
    private float yawDepart, pitchDepart;

    // état du joueur rendu à la fin
    private CameraType typeAvant = CameraType.THIRD_PERSON_BACK;
    private RebornCamera.Mode modeAvant = RebornCamera.Mode.SHOULDER;
    private double camYawAvant, camPitchAvant;
    private boolean hudAvant;

    private CameraTirage() {}

    /** Filet de sécurité (écran remplacé, déconnexion) + banc. Appelé une fois au démarrage du mod. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> INSTANCE.couper(mc)));
        TirageDebug.init();
    }

    public boolean active() { return etat != Etat.INACTIF; }

    /** Le plan est posé (entrée finie ou en cours), pas en retour. */
    public boolean enPlan() { return etat == Etat.ACTIF; }

    public Vec3 main() { return main; }

    // ─────────── début / fin ───────────

    /** Prend la caméra (idempotent : un écran du test qui en remplace un autre garde le plan). */
    public void prendre(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options == null) return;
        liberer = false;
        if (etat == Etat.ACTIF) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (etat == Etat.INACTIF) {
            typeAvant = mc.options.getCameraType();
            modeAvant = RebornCamera.INSTANCE.mode();
            camYawAvant = RebornCamera.INSTANCE.camYaw();
            camPitchAvant = RebornCamera.INSTANCE.camPitch();
            hudAvant = mc.gui != null && mc.gui.hud.isHidden();
            // le personnage se tourne franchement vers l'avant (corps, tête, regard droit)
            p.setXRot(0f);
            p.setYBodyRot(p.getYRot());
            p.setYHeadRot(p.getYRot());
            calculerPlan(mc, p);
        }
        // départ : là où est la caméra à cette image (y compris en plein retour : on repart de là)
        posDepart = cam.position();
        yawDepart = cam.yRot();
        pitchDepart = cam.xRot();
        // plus rien n'oriente le joueur ni ne recadre (caméra épaule hors jeu le temps du test)
        RebornCamera.INSTANCE.setMode(RebornCamera.Mode.VANILLA);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        etat = Etat.ACTIF;
        depuis = TirageHorloge.now();
    }

    /** L'écran du test se ferme : on rend la caméra au tick suivant, sauf si un autre écran du test a pris le relais. */
    public void liberer() {
        if (etat == Etat.ACTIF) liberer = true;
    }

    private void tick(Minecraft mc) {
        if (etat == Etat.INACTIF) return;
        if (mc.player == null || mc.level == null) { couper(mc); return; }
        boolean ecranTest = mc.gui != null && mc.gui.screen() instanceof TirageScreen;
        if (etat == Etat.ACTIF && (liberer || !ecranTest)) {
            if (ecranTest) { liberer = false; return; }
            demarrerRetour(mc);
        }
        liberer = false;
        if (etat == Etat.RETOUR && TirageHorloge.now() - depuis >= SORTIE_MS) terminer(mc);
    }

    private void demarrerRetour(Minecraft mc) {
        Camera cam = mc.gameRenderer.mainCamera();
        posDepart = cam.position();
        yawDepart = cam.yRot();
        pitchDepart = cam.xRot();
        // la caméra du joueur reprend la main (orbite d'origine) ; la vue reste en 3e personne pendant le glissement
        RebornCamera rc = RebornCamera.INSTANCE;
        rc.setMode(modeAvant);
        rc.initOrientation((float) camYawAvant, (float) camPitchAvant);
        if (mc.options != null) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(hudAvant);
        etat = Etat.RETOUR;
        depuis = TirageHorloge.now();
    }

    private void terminer(Minecraft mc) {
        etat = Etat.INACTIF;
        if (mc.options != null) mc.options.setCameraType(typeAvant);
    }

    /** Coupure (déconnexion, monde déchargé) : tout est rendu sans glissement. */
    private void couper(Minecraft mc) {
        if (etat == Etat.INACTIF) return;
        etat = Etat.INACTIF;
        liberer = false;
        RebornCamera.INSTANCE.setMode(modeAvant);
        RebornCamera.INSTANCE.initOrientation((float) camYawAvant, (float) camPitchAvant);
        if (mc.options != null) mc.options.setCameraType(typeAvant);
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(hudAvant);
    }

    // ─────────── le plan ───────────

    private static final double[] ANGLES = {32, -32, 55, -55, 14, -14, 80, -80};
    private static final double[] DISTANCES = {2.35, 1.95, 1.6};

    private void calculerPlan(Minecraft mc, LocalPlayer p) {
        double r = Math.toRadians(p.getYRot());
        Vec3 avant = new Vec3(-Math.sin(r), 0, Math.cos(r));
        Vec3 droite = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        Vec3 pieds = p.position();
        // la main droite levée devant le visage (pose de l'animation) ; le plan vise entre la poitrine et la main
        main = pieds.add(0, 1.52, 0).add(avant.scale(0.42)).add(droite.scale(0.16));
        foyer = pieds.add(0, 1.30, 0).add(avant.scale(0.12));
        double meilleur = -1;
        Vec3 choixPos = null, choixDir = null;
        boucle:
        for (double d : DISTANCES) {
            for (double a : ANGLES) {
                double ar = Math.toRadians(a);
                // direction foyer → caméra : l'avant du joueur tourné de a vers sa droite
                Vec3 dir = avant.scale(Math.cos(ar)).add(droite.scale(Math.sin(ar))).add(0, 0.16, 0).normalize();
                Vec3 c = foyer.add(dir.scale(d));
                double libre = degagement(mc, p, foyer, c.add(dir.scale(0.35)));
                double voulu = d + 0.35;
                if (TirageDebug.actif()) System.out.println(String.format(java.util.Locale.ROOT,
                    "[tirage-plan] d=%.2f angle=%.0f libre=%.2f / %.2f", d, a, libre, voulu));
                if (libre >= voulu - 1e-3) { choixPos = c; choixDir = dir; break boucle; }
                if (libre > meilleur) {
                    meilleur = libre;
                    choixPos = foyer.add(dir.scale(Math.max(0.6, libre - 0.4)));
                    choixDir = dir;
                }
            }
        }
        pos = choixPos;
        // le personnage dans le tiers gauche : on vise à la droite du foyer (vu de la caméra)
        Vec3 regard = foyer.subtract(pos).normalize();
        Vec3 droiteCam = regard.cross(new Vec3(0, 1, 0)).normalize();
        double dist = foyer.distanceTo(pos);
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double demiLarg = dist * Math.tan(Math.toRadians(FOV / 2.0)) * aspect;
        Vec3 vise = foyer.add(droiteCam.scale(demiLarg * 0.36)).add(0, -0.04, 0);
        Vec3 v = vise.subtract(pos).normalize();
        yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(v.y, -1, 1)));
        if (choixDir == null) { pos = foyer.add(avant.scale(2)); }
    }

    /** Distance libre du foyer vers la caméra (bloc visuel le plus proche). */
    private static double degagement(Minecraft mc, LocalPlayer p, Vec3 de, Vec3 a) {
        HitResult h = mc.level.clip(new ClipContext(de, a, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        if (h.getType() != HitResult.Type.BLOCK) return de.distanceTo(a);
        return de.distanceTo(h.getLocation());
    }

    // ─────────── lu chaque image ───────────

    private float avance() {
        long dur = etat == Etat.RETOUR ? SORTIE_MS : ENTREE_MS;
        float a = Math.min(1f, (TirageHorloge.now() - depuis) / (float) dur);
        return a * a * a * (a * (a * 6 - 15) + 10);   // smootherstep : départ et arrivée sans à-coup
    }

    /**
     * Pose la caméra de cette image. {@code vanillaPos/Yaw/Pitch} = la caméra que le jeu vient de calculer (vue du
     * joueur), cible du retour ; en vue épaule, la cible est l'orbite Reborn ({@code epaule}).
     */
    public void poser(Minecraft mc, Vec3 vanillaPos, float vanillaYaw, float vanillaPitch, float pt, Pose sortie) {
        TirageHorloge.images++;
        float t = avance();
        if (etat == Etat.ACTIF) {
            sortie.set(orbite(mc, posDepart, pos, t), Mth.rotLerp(t, yawDepart, yaw), Mth.lerp(t, pitchDepart, pitch));
            return;
        }
        // retour : vers la caméra du joueur telle qu'elle serait cette image
        Vec3 cible;
        float cy, cp;
        LocalPlayer p = mc.player;
        if (modeAvant == RebornCamera.Mode.SHOULDER && RebornCamera.INSTANCE.isEnabled() && p != null) {
            RebornCamera rc = RebornCamera.INSTANCE;
            cy = (float) rc.camYaw();
            cp = (float) rc.camPitch();
            cible = epaule(p, rc, pt);
        } else if (typeAvant == CameraType.FIRST_PERSON && p != null) {
            cible = p.getEyePosition(pt);
            cy = p.getViewYRot(pt);
            cp = p.getViewXRot(pt);
        } else {
            cible = vanillaPos;
            cy = vanillaYaw;
            cp = vanillaPitch;
        }
        sortie.set(orbite(mc, pos, cible, t), Mth.rotLerp(t, yaw, cy), Mth.lerp(t, pitch, cp));
    }

    /** Champ de vision de cette image (glisse avec la caméra). */
    public float fov(float joueur) {
        float t = avance();
        return etat == Etat.ACTIF ? Mth.lerp(t, joueur, FOV) : Mth.lerp(t, FOV, joueur);
    }

    /** Même calcul que la caméra épaule ({@code CameraThirdPersonMixin}), avec le même anti-mur. */
    private static Vec3 epaule(LocalPlayer e, RebornCamera cam, float pt) {
        double yr = Math.toRadians(cam.camYaw()), pr = Math.toRadians(cam.camPitch());
        Vec3 viewDir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3 right = viewDir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(viewDir).normalize();
        Vec3 eye = e.getEyePosition(pt);
        Vec3 to = eye.add(viewDir.scale(-cam.distance())).add(right.scale(cam.rightOffset()))
            .add(up.scale(cam.upOffset() + cam.landImpact()));
        HitResult hit = e.level().clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
        if (hit.getType() != HitResult.Type.BLOCK) return to;
        Vec3 dir = to.subtract(eye);
        double len = dir.length();
        return len < 1.0e-4 ? hit.getLocation() : hit.getLocation().subtract(dir.scale(0.2 / len));
    }

    /**
     * Glissement en orbite autour du foyer (angle, rayon et hauteur interpolés) : la caméra contourne le personnage au
     * lieu de le traverser ; tenue hors des blocs par un lancer de rayon depuis le foyer.
     */
    private Vec3 orbite(Minecraft mc, Vec3 a, Vec3 b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        double ax = a.x - foyer.x, az = a.z - foyer.z, bx = b.x - foyer.x, bz = b.z - foyer.z;
        double ha = Math.atan2(az, ax), hb = Math.atan2(bz, bx);
        double dh = hb - ha;
        while (dh > Math.PI) dh -= 2 * Math.PI;
        while (dh < -Math.PI) dh += 2 * Math.PI;
        double h = ha + dh * t;
        double r = Mth.lerp(t, Math.hypot(ax, az), Math.hypot(bx, bz));
        double y = Mth.lerp(t, a.y - foyer.y, b.y - foyer.y);
        Vec3 c = foyer.add(Math.cos(h) * r, y, Math.sin(h) * r);
        if (mc.level == null || mc.player == null) return c;
        HitResult hit = mc.level.clip(new ClipContext(foyer, c, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return c;
        Vec3 d = c.subtract(foyer);
        double len = d.length();
        return len < 1.0e-4 ? hit.getLocation() : hit.getLocation().subtract(d.scale(0.2 / len));
    }

    /** Sortie réutilisée (pas d'objet à part le Vec3 du jeu). */
    public static final class Pose {
        public Vec3 pos = Vec3.ZERO;
        public float yaw, pitch;

        void set(Vec3 p, float y, float x) { pos = p; yaw = y; pitch = x; }
    }
}
