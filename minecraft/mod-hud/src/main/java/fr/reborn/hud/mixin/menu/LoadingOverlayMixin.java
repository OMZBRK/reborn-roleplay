package fr.reborn.hud.mixin.menu;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.reborn.hud.menu.connect.BootLoadingRenderer;
import fr.reborn.hud.menu.connect.ChargementScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skin Reborn de l'écran de rechargement des ressources ({@link LoadingOverlay}).
 *
 * <p>La logique vanilla n'est jamais annulée (fondus, fin du rechargement, retrait de l'écran : au premier
 * rechargement, au boot, tout court-circuit risquerait de bloquer le démarrage) ; on peint au TAIL.
 *
 * <ul>
 *   <li>Rechargement du <b>pack serveur</b> (trajet vers le serveur en cours, ou pack serveur en route ; décidé une
 *       fois pour toute la vie de l'écran) : rien de Mojang n'est dessiné (fond de marque, logo, barre) et la scène de
 *       chargement prend tout l'écran, dans la continuité de la connexion
 *       ({@link ChargementScene#dessinerRechargement}).</li>
 *   <li>Tout autre rechargement (boot, F3+T, packs du joueur) : {@link BootLoadingRenderer} par-dessus le vanilla.</li>
 * </ul>
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    /** Progression lissée du reload (0..1). Champ vanilla. */
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow @Final private boolean fadeIn;

    @Unique private Boolean reborn$scene;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void reborn$decider(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.reborn$scene == null) this.reborn$scene = this.fadeIn && ChargementScene.pourRechargement();
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private boolean reborn$sansFondMojang(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int col) {
        return !Boolean.TRUE.equals(this.reborn$scene);
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private boolean reborn$sansLogoMojang(GuiGraphicsExtractor ctx, RenderPipeline p, Identifier id, int x, int y,
                                          float u, float v, int w, int h, int rw, int rh, int tw, int th, int col) {
        return !Boolean.TRUE.equals(this.reborn$scene);
    }

    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private boolean reborn$sansBarreMojang(LoadingOverlay self, GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1,
                                           float a) {
        return !Boolean.TRUE.equals(this.reborn$scene);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$bootSkin(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                 float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        if (Boolean.TRUE.equals(this.reborn$scene)) {
            ChargementScene.dessinerRechargement(ctx, this, this.currentProgress, this.fadeOutStart);
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        BootLoadingRenderer.render(ctx, w, h, this.currentProgress);
    }
}
