package fr.reborn.hud.mixin;

import fr.reborn.hud.combat.ChakraJump;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralise le saut vanilla du joueur local pendant qu'il CHARGE un saut de chakra
 * (sneak + espace maintenus) — cf {@link ChakraJump}. Sans ça, maintenir espace en
 * sneak déclencherait des sauts au lieu de charger.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerJumpMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void reborn$suppressJumpWhileCharging(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && ChakraJump.INSTANCE.suppressesJump()) {
            ci.cancel();
        }
    }
}
