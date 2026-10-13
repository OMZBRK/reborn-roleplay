package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.skin.RebornSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Le personnage principal en scène : le joueur LOCAL ({@code mc.player}), à qui l'on PRÊTE le temps de l'extraction
 * la texture composée du perso affiché (même méthode que l'ancien écran de sélection ; jamais d'entité factice hors
 * monde : gel au join). Pose figée et neutralisée comme dans la fiche ({@code FichePortrait}). Un seul rendu d'entité
 * par image.
 */
final class PersoFigure {

    private PersoFigure() {}

    /** Hauteur de la boîte d'un joueur debout (blocs). */
    private static final float HAUTEUR = 1.8f;
    private static final float SIGNE_TANGAGE = -1f;

    /**
     * Pieds en (cx, piedsY) GUI ; {@code k} GUI par texel de skin ; {@code demiL}/{@code demiH} : rectangle FIXE de
     * l'image dans l'image (sinon réallocation) ; lacet > 0 : flanc droit de l'écran visible.
     */
    static void dessiner(GuiGraphicsExtractor g, Identifier skin, boolean slim, int cx, int piedsY, float k,
                         float lacet, float tangage, int demiL, int demiH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        float echelle = k * 16f / 0.9375f;
        UUID self = p.getUUID();
        Identifier propre = RebornSkins.overrideFor(self);
        boolean propreSlim = RebornSkins.isSlim(self);
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            EntityRenderer r = mc.getEntityRenderDispatcher().getRenderer(p);
            EntityRenderState s;
            try {
                if (skin != null) RebornSkins.setOverride(self, skin, slim);
                @SuppressWarnings("unchecked")
                EntityRenderState s0 = r.createRenderState(p, 1.0f);
                s = s0;
            } finally {
                RebornSkins.setOverride(self, propre, propreSlim);
            }
            try { Pal.vuePleine(s); } catch (Throwable ignored) { }
            s.shadowPieces.clear();
            s.outlineColor = 0;
            s.nameTag = null;
            s.scoreText = null;
            s.isInvisible = false;
            s.displayFireAnimation = false;
            if (s instanceof LivingEntityRenderState ls) {
                ls.bodyRot = 180f + lacet;
                ls.yRot = 0f;
                ls.xRot = 0f;
                ls.walkAnimationPos = 0f;
                ls.walkAnimationSpeed = 0f;
                ls.pose = Pose.STANDING;
                ls.bedOrientation = null;
                ls.hasRedOverlay = false;
                ls.deathTime = 0f;
                ls.isInvisibleToPlayer = false;
                ls.isUpsideDown = false;
                ls.isAutoSpinAttack = false;
                ls.boundingBoxWidth /= ls.scale;
                ls.boundingBoxHeight /= ls.scale;
                ls.scale = 1f;
            }
            if (s instanceof HumanoidRenderState hs) {
                hs.isCrouching = false;
                hs.isVisuallySwimming = false;
                hs.swimAmount = 0f;
                hs.isFallFlying = false;
                hs.isPassenger = false;
                hs.isUsingItem = false;
                hs.speedValue = 1f;
            }
            if (s instanceof ArmedEntityRenderState as) {
                as.rightHandItemState.clear();
                as.leftHandItemState.clear();
                as.rightArmPose = HumanoidModel.ArmPose.EMPTY;
                as.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                as.attackTime = 0f;
            }
            Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(SIGNE_TANGAGE * tangage));
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(cam);
            Vector3f tr = new Vector3f(0f, HAUTEUR / 2f, 0f);
            int cy = Math.round(piedsY - HAUTEUR / 2f * echelle);
            g.entity(s, echelle, tr, pose, cam, cx - demiL, cy - demiH, cx + demiL, cy + demiH);
        } catch (Exception ignored) {
            // un rendu raté ne doit jamais casser l'écran
        }
    }

    /** Isolé : chargé seulement si PlayerAnimationLib est présent. */
    private static final class Pal {
        static void vuePleine(EntityRenderState s) {
            if (s instanceof com.zigythebird.playeranim.accessors.IAvatarAnimationState a) {
                a.playerAnimLib$setFirstPersonPass(false);
                a.playerAnimLib$setAnimManager(null);
            }
        }
    }
}
