package fr.reborn.hud.mixin;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.mur.MurCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caméra épaule Reborn (over-the-shoulder, style anime-RP).
 *
 * <p>Après l'{@code update} vanilla (qui a placé la caméra derrière le joueur
 * en 3e personne arrière), on repositionne la caméra à la distance voulue puis
 * on la décale latéralement/verticalement en over-the-shoulder, avec un clip
 * anti-mur (raycast). N'agit QUE si {@link RebornCamera#isEnabled()} et 3e
 * personne arrière. Le mode photo garde la priorité sur la caméra.
 *
 * <p>Marche sur les murs (lot 5, étape C1a) : le temps du mur et du retour, {@link MurCamera} donne le pivot et
 * l'<b>orientation complète</b> de la caméra, roulis compris (« la paroi devient le sol »). Distance, épaule et hauteur
 * restent celles du joueur, appliquées dans le repère tourné ; l'anti-obstacle est le même. {@code setRotation} ne sait
 * poser que lacet et tangage : le quaternion est écrit directement, avec ce que {@code setRotation} en dérive
 * ({@code forwards}, {@code up}, {@code left}, matrices à refaire). Hors du mur : une lecture de booléen et rien d'autre.
 *
 * <p>26.1 : {@code Camera#update} a pour signature {@code update(DeltaTracker)}
 * et ne passe plus (area, focusedEntity, thirdPerson, inverseView, tickDelta).
 * On lit donc l'entité ciblée via le champ shadow {@code entity}, le tickDelta
 * via le {@link DeltaTracker}, et la vue (3e pers arrière vs avant vs 1re pers)
 * via {@code Options#getCameraType()}.
 *
 * @see fr.reborn.hud.camera.RebornCamera
 */
@Mixin(Camera.class)
public abstract class CameraThirdPersonMixin {

    /** Entité ciblée par la caméra (le joueur local en général). Champ vanilla. */
    @Shadow private Entity entity;

    /** Orientation de la caméra et ce que {@code setRotation} en dérive (champs vanilla, écrits pour la caméra de paroi). */
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;
    /** Drapeaux « matrice de vue » (1) et « vue × projection » (2) à refaire. */
    @Shadow private int matrixPropertiesDirty;

    @Shadow public abstract Vec3 position();
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    /**
     * La caméra de paroi a posé l'orientation de cette mise à jour. Les secousses ajoutées en fin de mise à jour (séisme
     * du staff, KO, faim) appellent {@code setRotation(yRot + a, xRot + b)}, qui reconstruirait une caméra sans roulis :
     * on relit alors leur écart comme un petit décalage local de l'orientation de paroi, roulis gardé.
     */
    @Unique private boolean reborn$paroi;

    @Inject(method = "update", at = @At("HEAD"))
    private void reborn$debutMiseAJour(DeltaTracker deltaTracker, CallbackInfo ci) {
        reborn$paroi = false;
        MurCamera.miseAJour();
    }

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void reborn$rotationParoi(float yaw, float pitch, CallbackInfo ci) {
        if (!reborn$paroi) return;
        this.rotation.set(MurCamera.ORIENTATION)
            .rotateY(-(yaw - MurCamera.lacet) * Mth.DEG_TO_RAD)
            .rotateX(-(pitch - MurCamera.tangage) * Mth.DEG_TO_RAD);
        this.rotation.transform(0f, 0f, -1f, this.forwards);
        this.rotation.transform(0f, 1f, 0f, this.up);
        this.rotation.transform(-1f, 0f, 0f, this.left);
    }

    // Injecter JUSTE APRÈS alignWithEntity (26.x) : vanilla vient de positionner la caméra
    // 3e-pers ; on la repositionne AVANT que update ne calcule la matrice de vue, le frustum
    // de culling ET le CameraRenderState (occlusion). Ainsi TOUT le pipeline de culling de MC
    // se construit sur NOTRE caméra → chunks rendus normalement (au TAIL c'était trop tard :
    // culling sur la caméra vanilla → trous en vue épaule orbitée).
    @Inject(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void reborn$shoulderCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // 3e personne ARRIÈRE uniquement (équiv. ancien !thirdPerson || inverseView :
        // THIRD_PERSON_FRONT et FIRST_PERSON sont exclus).
        if (mc.options == null || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        if (PhotoMode.INSTANCE.isActive()) return;
        // Le mode repositionnement prend le contrôle de la caméra (orbite dédiée).
        if (fr.reborn.hud.cosmetic.RepositionMode.INSTANCE.isActive()) return;
        if (fr.reborn.hud.menu.tirage.CameraTirage.INSTANCE.active()) return;   // test de la Feuille (CameraRepositionMixin)
        if (fr.reborn.hud.boutique.CameraEssayage.INSTANCE.active()) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        Entity focusedEntity = this.entity;
        if (!cam.isEnabled() || focusedEntity == null) return;

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        double recul = cam.distance(), epaule = cam.rightOffset(), hauteur = cam.upOffset() + cam.landImpact();

        // Caméra de paroi : même cadrage, dans le repère de la paroi. L'image peut terminer le retour (actif relu).
        if (MurCamera.actif && focusedEntity == mc.player) {
            MurCamera.image(focusedEntity, tickDelta);
            if (MurCamera.actif) {
                Vector3f v = MurCamera.VISEE, h = MurCamera.HAUT, g = MurCamera.GAUCHE;
                Vec3 pivot = new Vec3(MurCamera.pivotX, MurCamera.pivotY, MurCamera.pivotZ);
                Vec3 cible = new Vec3(
                    pivot.x - v.x * recul - g.x * epaule + h.x * hauteur,
                    pivot.y - v.y * recul - g.y * epaule + h.y * hauteur,
                    pivot.z - v.z * recul - g.z * epaule + h.z * hauteur);
                this.yRot = MurCamera.lacet;
                this.xRot = MurCamera.tangage;
                this.rotation.set(MurCamera.ORIENTATION);
                this.forwards.set(v);
                this.up.set(h);
                this.left.set(g);
                this.matrixPropertiesDirty |= 3;
                setPosition(reborn$clip(focusedEntity, pivot, cible));
                reborn$paroi = true;
                return;
            }
        }

        // Orientation caméra = orbite Reborn (découplée du regard du joueur).
        float cy = (float) cam.camYaw();
        float cp = (float) cam.camPitch();
        double yr = Math.toRadians(cy), pr = Math.toRadians(cp);
        // Direction de visée de la caméra (là où elle regarde).
        Vec3 viewDir = new Vec3(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
            Math.cos(yr) * Math.cos(pr));
        Vec3 backDir = viewDir.scale(-1.0);            // de l'oeil vers la caméra
        Vec3 right = viewDir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 haut = right.cross(viewDir).normalize();

        Vec3 eye = focusedEntity.getEyePosition(tickDelta);
        Vec3 target = eye
            .add(backDir.scale(recul))
            .add(right.scale(epaule))
            .add(haut.scale(hauteur));

        setRotation(cy, cp);
        setPosition(reborn$clip(focusedEntity, eye, target));
    }

    /** Réduit la distance si un mur est entre l'oeil et la caméra cible. */
    @Unique
    private Vec3 reborn$clip(Entity e, Vec3 from, Vec3 to) {
        HitResult hit = e.level().clip(new ClipContext(
            from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
        if (hit.getType() != HitResult.Type.BLOCK) return to;
        Vec3 hp = hit.getLocation();
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        // Recule de 0.2 bloc vers l'oeil pour ne pas coller/clipper dans le mur.
        return len < 1.0e-4 ? hp : hp.subtract(dir.scale(0.2 / len));
    }
}
