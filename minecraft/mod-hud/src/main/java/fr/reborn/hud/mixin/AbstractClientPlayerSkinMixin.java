package fr.reborn.hud.mixin;

import fr.reborn.hud.skin.RebornSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Override du skin rendu : si {@link RebornSkins} a une texture composée pour ce
 * joueur, on remplace le {@code body()} du {@link PlayerSkin} par notre texture
 * (via {@link PlayerSkin#with}). Le modèle (wide/slim), la cape et l'elytra sont
 * conservés. Comme tous les joueurs ont le mod, chacun compose la même texture
 * depuis les IDs cosmétiques → tout le monde voit le même skin.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void reborn$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Identifier id = RebornSkins.overrideFor(self.getUUID());
        if (id == null) return;
        PlayerSkin original = cir.getReturnValue();
        if (original == null) return;
        PlayerSkin patched = original.with(PlayerSkin.Patch.create(
            Optional.of(new ClientAsset.ResourceTexture(id)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()));
        cir.setReturnValue(patched);
    }
}
