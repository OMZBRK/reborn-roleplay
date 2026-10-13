package fr.reborn.hud.menu.fiche;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Le personnage de la fiche : le joueur LOCAL ({@code mc.player}, qui porte déjà son skin RP), jamais une entité
 * factice hors monde (gel au join). Son état vivant est neutralisé (marche, accroupi, objets, KO, invisibilité, flash
 * rouge, ombre, nom) ; la taille du rectangle reste FIXE (sinon la texture « image dans l'image » est réallouée).
 *
 * <p>6 px GUI par texel de skin (AvatarRenderer réduit le modèle à 0,9375 : 6·16/0,9375 = 102,4), 4 en compact.
 * Glisser fait tourner ; double clic = de face en 250 ms.
 */
final class FichePortrait {

    static final float ECHELLE = 6f * 16f / 0.9375f;            // 102,4
    static final float ECHELLE_COMPACTE = 4f * 16f / 0.9375f;   // 68,27
    /** Demi-rectangle fixe autour du milieu de la boîte englobante. */
    static final int DEMI_L = 80, DEMI_H = 112;
    /** Hauteur de la boîte d'un joueur debout (blocs). */
    static final float HAUTEUR = 1.8f;
    /** Signe du tangage : la caméra regarde un peu d'au-dessus (à caler sur capture). */
    static final float SIGNE_TANGAGE = -1f;

    private float lacet = 34f, cible = 34f;
    private float tangage = 8f;
    private boolean drag;
    private boolean bloque;
    private long faceDepuis = -1L;
    private float faceDepart;
    private long last = System.nanoTime();

    /** Pose de l'onglet (lacet de référence, tangage, rotation bloquée). Remet le personnage dans l'axe. */
    void poser(float lacetOnglet, float tangageOnglet, boolean bloquer) {
        this.cible = lacetOnglet;
        this.tangage = tangageOnglet;
        this.bloque = bloquer;
        this.faceDepuis = -1L;
        if (bloquer) this.lacet = lacetOnglet;
    }

    void forcer(float l) {
        lacet = cible = l;
        faceDepuis = -1L;
    }

    boolean bloque() { return bloque; }

    void commencerGlisser() { if (!bloque) drag = true; }

    void finGlisser() { drag = false; }

    boolean glisse() { return drag; }

    void glisser(double dx) {
        if (!drag || bloque) return;
        cible += (float) dx * 0.8f;
        faceDepuis = -1L;
    }

    void tourner(float deg) {
        if (bloque) return;
        cible += deg;
        faceDepuis = -1L;
    }

    /** Double clic : retour de face en 250 ms. */
    void deFace() {
        if (bloque) return;
        faceDepart = lacet;
        faceDepuis = System.currentTimeMillis();
        cible = 0f;
    }

    private void avancer() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (faceDepuis >= 0) {
            float t = Math.min(1f, (System.currentTimeMillis() - faceDepuis) / 250f);
            float e = 1f - (1f - t) * (1f - t);
            lacet = faceDepart + (0f - faceDepart) * e;
            if (t >= 1f) { faceDepuis = -1L; lacet = cible = 0f; }
            return;
        }
        lacet += (cible - lacet) * Math.min(1f, dt * 14f);
    }

    /** Zone de saisie du personnage (pieds en (cx, piedsY)). */
    static boolean dans(double mx, double my, int cx, int piedsY, float echelle) {
        float k = echelle / ECHELLE;
        return Math.abs(mx - cx) < 50 * k && my < piedsY + 4 && my > piedsY - 200 * k;
    }

    /** Pieds posés en (cx, piedsY), en px GUI. */
    void extract(GuiGraphicsExtractor ctx, int cx, int piedsY, float echelle) {
        avancer();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;                                       // monde en cours de chargement
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            EntityRenderer r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
            @SuppressWarnings("unchecked")
            EntityRenderState s = r.createRenderState(p, 1.0f);
            // Pose figée : ni démarche ni emote de PlayerAnimationLib, ni passe « première personne ».
            try { Pal.vuePleine(s); } catch (Throwable ignored) { }
            s.shadowPieces.clear();
            s.outlineColor = 0;
            s.nameTag = null;
            s.scoreText = null;
            s.isInvisible = false;                                  // vanish staff
            s.displayFireAnimation = false;
            if (s instanceof LivingEntityRenderState ls) {
                ls.bodyRot = 180f + lacet;                           // lacet > 0 : flanc droit de l'écran visible (maquettes)
                ls.yRot = 0f;
                ls.xRot = 0f;
                ls.walkAnimationPos = 0f;
                ls.walkAnimationSpeed = 0f;
                ls.pose = Pose.STANDING;
                ls.bedOrientation = null;                            // KO couché
                ls.hasRedOverlay = false;
                ls.deathTime = 0f;
                ls.isInvisibleToPlayer = false;
                ls.isUpsideDown = false;
                ls.isAutoSpinAttack = false;
                ls.boundingBoxWidth /= ls.scale;
                ls.boundingBoxHeight /= ls.scale;
                ls.scale = 1f;                                       // stature normalisée ; la taille RP est écrite
            }
            if (s instanceof HumanoidRenderState hs) {
                hs.isCrouching = false;
                hs.isVisuallySwimming = false;
                hs.swimAmount = 0f;
                hs.isFallFlying = false;
                hs.isPassenger = false;
                hs.isUsingItem = false;
                hs.speedValue = 1f;                                  // diviseur du balancement : jamais 0 (NaN)
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
            // Hauteur de joueur fixe : l'état de rendu peut porter une échelle RP qui fausserait la boîte.
            Vector3f tr = new Vector3f(0f, HAUTEUR / 2f, 0f);
            int cy = Math.round(piedsY - HAUTEUR / 2f * echelle);
            int dl = Math.round(DEMI_L * echelle / ECHELLE), dh = Math.round(DEMI_H * echelle / ECHELLE);
            ctx.entity(s, echelle, tr, pose, cam, cx - dl, cy - dh, cx + dl, cy + dh);
        } catch (Exception ignored) {
            // un rendu raté ne doit jamais casser l'écran
        }
    }

    /** Isolé : chargé seulement si PlayerAnimationLib est présent (sinon l'erreur de liaison est avalée). */
    private static final class Pal {
        static void vuePleine(EntityRenderState s) {
            if (s instanceof com.zigythebird.playeranim.accessors.IAvatarAnimationState a) {
                a.playerAnimLib$setFirstPersonPass(false);
                a.playerAnimLib$setAnimManager(null);       // pose figée : aucune animation de démarche ni emote
            }
        }
    }
}
