package fr.reborn.hud.mixin;

import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Orientation du corps/tête en <b>post-tick</b> (après {@code aiStep}) pour la
 * caméra épaule.
 *
 * <p>Trois comportements, dans cet ordre de priorité :
 * <ul>
 *   <li><b>Free-look (ALT maintenu)</b> : le perso est <b>totalement figé</b> —
 *   corps, tête ET pitch. L'orientation est capturée au front montant puis
 *   ré-appliquée à chaque tick, y compris en course : sans ça, la logique
 *   vanilla ({@code aiStep} → {@code tickHeadTurn}) re-tournait le corps vers la
 *   direction de déplacement, d'où « ma tête bouge quand même / elle
 *   s'actualise quand je cours ».</li>
 *   <li><b>À l'arrêt</b> : la tête suit la caméra. Le corps ne bouge pas tant
 *   que le débattement reste sous {@link #REBORN_MAX_HEAD_YAW}° ; au-delà il est
 *   tiré, exactement comme le {@code tickHeadTurn} vanilla — c'est ce que les
 *   AUTRES joueurs voient (voir la note réseau ci-dessous), donc local et
 *   distant restent d'accord.</li>
 *   <li><b>En déplacement (mode base)</b> : le corps suit la caméra (souris).
 *   {@code KeyboardInputMixin} pose déjà yaw/bodyYaw vers la caméra, mais la
 *   logique vanilla de {@code LivingEntity#aiStep} re-tourne ensuite
 *   {@code yBodyRot} vers la direction de déplacement — d'où « le perso ne suit
 *   la caméra que quand je clique ». On ré-applique donc l'orientation au TAIL.</li>
 * </ul>
 *
 * <p><b>Note réseau (pourquoi les autres ne voyaient pas la tête tourner).</b>
 * Le client n'envoie qu'un couple {@code (yRot, xRot)} —
 * {@code ServerboundMovePlayerPacket}. Le serveur en dérive le yaw de tête ET
 * le yaw de corps ; {@code yHeadRot} posé côté client seul est donc purement
 * cosmétique et <i>invisible pour les autres</i>. C'était le bug : à l'arrêt on
 * ne touchait que {@code setYHeadRot} + {@code setXRot} → seul le haut/bas
 * partait sur le réseau. On pose maintenant aussi {@code yRot} (dans
 * {@code KeyboardInputMixin}, AVANT le {@code sendPosition} du tick, pour ne pas
 * perdre un tick), et les clients distants ré-appliquent leur propre
 * {@code tickHeadTurn} — corps qui traîne derrière la tête, interpolé, fluide.
 *
 * <p>Naruto run : on ne touche à rien — le corps fait face à la direction de
 * déplacement (Elden Ring), ce que fait déjà la logique vanilla.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBodyMixin {

    /** Débattement max de la tête par rapport au corps. Valeur <b>alignée sur le
     *  clamp vanilla</b> de {@code LivingEntity#tickHeadTurn} (75°), qui est ce
     *  que les clients distants appliquent sur notre avatar : toute autre valeur
     *  ferait décrocher le rendu local du rendu vu par les autres. */
    @Unique
    private static final float REBORN_MAX_HEAD_YAW = 75.0f;

    /** Free-look en cours au tick précédent (détection du front montant). */
    @Unique
    private boolean reborn$freeLookLatched = false;
    @Unique
    private float reborn$frozenYaw, reborn$frozenPitch, reborn$frozenBodyYaw, reborn$frozenHeadYaw;

    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$bodyFollowsCamera(CallbackInfo ci) {
        RebornCamera cam = RebornCamera.INSTANCE;
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (!cam.isEnabled()) { reborn$freeLookLatched = false; return; }   // vue épaule uniquement

        // ── FREE-LOOK : gel total (corps + tête + pitch), même en course ──
        if (cam.freeLook()) {
            if (!reborn$freeLookLatched) {
                reborn$freeLookLatched = true;
                reborn$frozenYaw = self.getYRot();
                reborn$frozenPitch = self.getXRot();
                reborn$frozenBodyYaw = self.yBodyRot;
                reborn$frozenHeadYaw = self.getYHeadRot();
            }
            self.setYRot(reborn$frozenYaw);
            self.setXRot(reborn$frozenPitch);
            self.setYBodyRot(reborn$frozenBodyYaw);
            self.setYHeadRot(reborn$frozenHeadYaw);
            return;
        }
        reborn$freeLookLatched = false;

        if (NarutoRun.INSTANCE.isActive()) return;          // naruto = corps vers déplacement

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        // Source de vérité = yRot, que KeyboardInputMixin a posé PLUS TÔT dans ce
        // même tick (et qui est parti sur le réseau). On ne le recalcule pas ici :
        // c'est ce qui garantit que notre rendu local et celui des autres joueurs
        // décrivent la même orientation, rattrapage post-free-look compris.
        float yaw = self.getYRot();

        // ── À l'arrêt : la TÊTE suit le regard, le corps traîne au-delà du clamp ──
        if (!moving) {
            float offset = Mth.wrapDegrees(yaw - self.yBodyRot);
            if (Math.abs(offset) > REBORN_MAX_HEAD_YAW) {
                // Le corps est tiré juste ce qu'il faut pour rester dans le débattement
                // (même règle que les clients distants → rendu identique partout).
                self.setYBodyRot(yaw - Mth.clamp(offset, -REBORN_MAX_HEAD_YAW, REBORN_MAX_HEAD_YAW));
            }
            self.setYHeadRot(yaw);
            return;
        }

        // ── En déplacement : le corps colle au regard (comme la pose de visée) ──
        self.setYBodyRot(yaw);
        self.setYHeadRot(yaw);
    }
}
