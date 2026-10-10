package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.character.scene.SceneActors;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Skin des acteurs de la scène de sélection (mannequins côté client, sans profil Mojang). */
@Mixin(ClientMannequin.class)
public abstract class ClientMannequinSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void reborn$sceneSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        ClientMannequin self = (ClientMannequin) (Object) this;
        SceneActors.Skin s = SceneActors.skinOf(self.getId());
        if (s == null || cir.getReturnValue() == null) return;
        Identifier id = s.texture();
        cir.setReturnValue(cir.getReturnValue().with(PlayerSkin.Patch.create(
                Optional.of(new ClientAsset.ResourceTexture(id, id)), Optional.empty(), Optional.empty(),
                Optional.of(s.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE))));
    }
}
