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
        if ((Object) this != Minecraft.getInstance().player) return;
        // Saut neutralisé pendant la CHARGE d'un saut chakra (sneak+espace) OU
        // pendant la GARDE/PARADE (touche C) — sous parade on ne bouge ni ne saute.
        if (ChakraJump.INSTANCE.suppressesJump()
                || fr.reborn.hud.combat.CombatInput.INSTANCE.isBlocking()) {
            ci.cancel();
        }
    }
}
