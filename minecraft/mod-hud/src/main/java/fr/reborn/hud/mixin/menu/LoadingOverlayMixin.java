package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.BootLoadingRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skin Reborn de l'écran de chargement au <b>boot</b> (reload de ressources).
 *
 * <p>Mode <b>OVERLAY</b> volontairement peu invasif : on {@code @Inject} au
 * TAIL de {@code extractRenderState} pour peindre notre fond noir + logo + barre
 * PAR-DESSUS le rendu Mojang, <b>sans annuler</b> la logique vanilla (fade,
 * complétion du reload, transition vers le title screen). Ce hook s'exécutant au
 * tout premier reload de ressources, tout court-circuit du vanilla risquerait de
 * bloquer le démarrage — d'où le choix de superposer sans cancel.
 *
 * @see fr.reborn.hud.menu.connect.BootLoadingRenderer
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    /** Progression lissée du reload (0..1). Champ vanilla. */
    @Shadow private float currentProgress;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$bootSkin(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                 float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        BootLoadingRenderer.render(ctx, w, h, this.currentProgress);
    }
}
