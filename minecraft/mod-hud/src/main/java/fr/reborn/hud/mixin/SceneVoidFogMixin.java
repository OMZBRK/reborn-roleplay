package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.character.scene.SceneCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scènes de sélection / création : le monde disparaît dans un noir profond à quelques blocs de la caméra — seuls les
 * persos (à 2–5 blocs) restent visibles, sur fond sombre, où que le joueur se trouve sur la carte.
 */
@Mixin(FogRenderer.class)
public abstract class SceneVoidFogMixin {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"))
    private void reborn$sceneVoid(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                  CallbackInfoReturnable<FogData> cir) {
        if (!SceneCamera.active()) return;
        FogData fog = cir.getReturnValue();
        if (fog == null) return;
        fog.environmentalStart = 7f;
        fog.environmentalEnd = 14f;
        fog.renderDistanceStart = 7f;
        fog.renderDistanceEnd = 14f;
        fog.skyEnd = 0f;
        fog.cloudEnd = 0f;
        if (fog.color != null) fog.color.set(0.016f, 0.012f, 0.018f, 1f);
    }
}
