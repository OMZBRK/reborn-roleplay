package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.character.scene.SceneActors;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Acteurs de scène : éclairage « studio » plein, quelle que soit l'heure ou l'endroit. */
@Mixin(EntityRenderer.class)
public abstract class SceneLightMixin {

    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    private void reborn$studioLight(Entity e, float pt, CallbackInfoReturnable<Integer> cir) {
        if (SceneActors.isActor(e.getId())) cir.setReturnValue(0xF000F0);
    }
}
