package fr.reborn.hud.mixin;

import fr.reborn.hud.combat.ChakraJump;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vol du saut chargé (lot 5, étape A) : pour le joueur local seulement, pendant le bond, le tick de déplacement est joué par
 * {@link ChakraJump#volEtape} (frottement horizontal × 0,98, gravité vanilla, aucune poussée des touches) à la place de
 * {@code LivingEntity.travel}. Hors bond, rien ne change. {@code Player.travel} appelle {@code super.travel} hors vol
 * créatif et hors nage : c'est ici que l'on passe.
 */
@Mixin(LivingEntity.class)
public abstract class SautVolMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void reborn$volDuSautCharge(Vec3 input, CallbackInfo ci) {
        if (!ChakraJump.INSTANCE.enVol()) return;
        if (!((Object) this instanceof LocalPlayer p) || p != Minecraft.getInstance().player) return;
        if (ChakraJump.INSTANCE.volEtape(p)) ci.cancel();
    }
}
