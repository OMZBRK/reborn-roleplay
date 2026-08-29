package fr.reborn.hud.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.reborn.hud.menu.inventory.CosmeticSlot;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.SlotItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature renderer (couche de rendu) qui affiche les <b>cosmétiques ÉQUIPÉS</b> du
 * personnage actif sur le corps du joueur, chacun avec son propre modèle d'item
 * Nexo réel et son placement (position / rotation / échelle / ancrage) éditable.
 *
 * <p><b>Pipeline 26.2 « submit ».</b> On surcharge
 * {@link #submit(PoseStack, SubmitNodeCollector, int, AvatarRenderState, float, float)}
 * (hérité de {@link RenderLayer}). Pour chaque cosmétique de
 * {@link InventoryData#get()}{@code .equipped()} : on reconstruit son
 * {@link ItemStack} ({@link SlotItem#toStack()}, qui pose déjà le
 * {@code DataComponents.ITEM_MODEL} Nexo), on résout son modèle monde via
 * {@link ItemModelResolver#updateForLiving} dans un {@link ItemStackRenderState}
 * réutilisable, on ancre le repère sur l'os de la partie du corps visée, on
 * applique le transform, puis on pousse l'item dans le
 * {@link SubmitNodeCollector} via {@link ItemStackRenderState#submit} — exactement
 * comme {@code CustomHeadLayer} rend l'item porté sur la tête.
 *
 * <p><b>Rendu local uniquement.</b> Les cosmétiques proviennent de l'inventaire du
 * personnage actif local ({@code InventoryData} = snapshot global) : on ne rend
 * donc que sur l'avatar du joueur local (comparaison {@code state.id}).
 *
 * <p><b>Transforms par-cosmétique, édition en direct.</b> Le placement de chaque
 * cosmétique est une instance mutable dédiée, indexée par son id
 * ({@link #cosmeticId}) dans {@link #LIVE}. L'éditeur ({@code RepositionScreen})
 * mute la MÊME instance → l'effet se voit immédiatement. Le placement persisté
 * (bouton « Appliquer ») est rechargé paresseusement depuis
 * {@link CosmeticPresets#loadApplied(String)} au premier rendu.
 */
public class CosmeticFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /**
     * Transforms live indexés par id de cosmétique — partagés par tous les renderers
     * (default + slim) ET par l'éditeur (source unique). Muter une instance =
     * appliquer en direct.
     */
    private static final Map<String, CosmeticTransform> LIVE = new ConcurrentHashMap<>();

    public CosmeticFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> parent,
                                   EntityModelSet models) {
        super(parent);
    }

    /**
     * Identifiant stable d'un cosmétique : son modèle Nexo (par-modèle, ex.
     * {@code reborn:masque_hannya}) si présent, sinon le nom du slot. Sert de clé
     * de transform commune au rendu et à l'éditeur.
     */
    public static String cosmeticId(CosmeticSlot slot, SlotItem item) {
        if (item != null && item.model != null && !item.model.isBlank()) return item.model;
        return slot != null ? slot.name().toLowerCase(Locale.ROOT) : "cosmetic";
    }

    /** Os d'ancrage par défaut d'un slot cosmétique (l'offset du transform couvre le reste). */
    public static CosmeticTransform.Anchor anchorForSlot(CosmeticSlot slot) {
        if (slot == null) return CosmeticTransform.Anchor.HEAD;
        return switch (slot) {
            case CHAPEAU, BANDEAU, MASQUE, BOUCLE -> CosmeticTransform.Anchor.HEAD;
            case HAUT, TENUE, MANTEAU, DOS -> CosmeticTransform.Anchor.TORSO;
            case CEINTURE, BAS -> CosmeticTransform.Anchor.PELVIS;
        };
    }

    /**
     * Transform live d'un cosmétique (instance partagée mutable). Chargé du placement
     * persisté à la première demande, sinon défaut raisonnable pour {@code defaultAnchor}.
     */
    public static CosmeticTransform live(String id, CosmeticTransform.Anchor defaultAnchor) {
        return LIVE.computeIfAbsent(id, k -> {
            CosmeticTransform applied = CosmeticPresets.loadApplied(k);
            return applied != null ? applied : CosmeticTransform.defaultFor(defaultAnchor);
        });
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isSpectator) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Entity ent = mc.level.getEntity(state.id);
        if (!(ent instanceof LivingEntity living)) return;

        ItemModelResolver resolver = mc.getItemModelResolver();
        PlayerModel model = this.getParentModel();

        // Joueur LOCAL avec sac déjà poussé : source autoritaire locale (InventoryData
        // + placement local via live() = aperçu direct de l'éditeur).
        boolean isLocal = state.id == mc.player.getId();
        if (isLocal && InventoryData.fromServer()) {
            Map<CosmeticSlot, SlotItem> equipped = InventoryData.get().equipped();
            if (equipped == null || equipped.isEmpty()) return;
            for (Map.Entry<CosmeticSlot, SlotItem> e : equipped.entrySet()) {
                renderCosmetic(poseStack, collector, light, state, model, resolver, living,
                    e.getKey(), e.getValue(), null);
            }
            return;
        }

        // Autres joueurs — ET joueur local tant que le sac n'a pas été poussé au spawn
        // (évite d'avoir à ouvrir l'inventaire pour voir ses propres cosmétiques). Le
        // store vient du broadcast serveur reborn:cosmetics ; le placement (transform)
        // reçu est appliqué tel quel → tout le monde voit le MÊME positionnement.
        Map<CosmeticSlot, RemoteCosmetics.Remote> remote = RemoteCosmetics.get(ent.getUUID());
        if (remote == null || remote.isEmpty()) return;
        for (Map.Entry<CosmeticSlot, RemoteCosmetics.Remote> e : remote.entrySet()) {
            RemoteCosmetics.Remote r = e.getValue();
            if (r == null) continue;
            renderCosmetic(poseStack, collector, light, state, model, resolver, living,
                e.getKey(), r.item(), r.transform());
        }
    }

    /**
     * Rend un cosmétique équipé sur l'avatar. {@code override} = placement reçu du
     * serveur (autres joueurs / repli local) ; {@code null} = placement local persisté
     * ({@link #live}, source de l'éditeur).
     */
    private void renderCosmetic(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                AvatarRenderState state, PlayerModel model, ItemModelResolver resolver,
                                LivingEntity living, CosmeticSlot slot, SlotItem item,
                                CosmeticTransform override) {
        if (slot == null || item == null) return;
        ItemStack stack = item.toStack();
        if (stack == null || stack.isEmpty()) return;

        CosmeticTransform t = override != null ? override : live(cosmeticId(slot, item), anchorForSlot(slot));
        ItemDisplayContext ctx = displayContextFor(t.anchor);

        // Instance par rendu (plus de champ partagé par slot) : plusieurs avatars
        // sont rendus dans la même frame et le collector « submit » peut différer
        // le rendu — réutiliser une même ItemStackRenderState corromprait l'affichage.
        ItemStackRenderState rs = new ItemStackRenderState();
        resolver.updateForLiving(rs, stack, ctx, living);
        if (rs.isEmpty()) return;

        poseStack.pushPose();
        anchorPose(model, state, t.anchor, poseStack);
        poseStack.translate(t.posX, t.posY, t.posZ);
        if (t.rotX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(t.rotX));
        if (t.rotY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(t.rotY));
        if (t.rotZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(t.rotZ));
        poseStack.scale(t.scale, t.scale, t.scale);

        rs.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    /** Contexte d'affichage adapté à l'os : {@code HEAD} pour la tête/cou, {@code FIXED} sinon. */
    private static ItemDisplayContext displayContextFor(CosmeticTransform.Anchor anchor) {
        return switch (anchor) {
            case HEAD, NECK -> ItemDisplayContext.HEAD;
            default -> ItemDisplayContext.FIXED;
        };
    }

    /**
     * Ancre le repère sur l'os choisi (main = translateToHand ; sinon le ModelPart
     * le plus proche via translateAndRotate — qui reproduit son pose animé — puis un
     * petit offset en BLOCS le long de l'os pour cibler la zone voulue ; Y est
     * orienté vers le BAS après translateAndRotate).
     */
    private static void anchorPose(PlayerModel model, AvatarRenderState state,
                                   CosmeticTransform.Anchor anchor, PoseStack poseStack) {
        switch (anchor) {
            case RIGHT_HAND -> model.translateToHand(state, HumanoidArm.RIGHT, poseStack);
            case LEFT_HAND  -> model.translateToHand(state, HumanoidArm.LEFT, poseStack);
            case HEAD       -> { model.head.translateAndRotate(poseStack); poseStack.translate(0f, -0.15f, 0f); }
            case NECK       -> model.head.translateAndRotate(poseStack); // pivot de la tête = base du cou
            case TORSO      -> { model.body.translateAndRotate(poseStack); poseStack.translate(0f, 0.35f, 0f); }
            case PELVIS     -> { model.body.translateAndRotate(poseStack); poseStack.translate(0f, 0.70f, 0f); }
            case RIGHT_ARM  -> model.rightArm.translateAndRotate(poseStack);
            case LEFT_ARM   -> model.leftArm.translateAndRotate(poseStack);
        }
    }
}
