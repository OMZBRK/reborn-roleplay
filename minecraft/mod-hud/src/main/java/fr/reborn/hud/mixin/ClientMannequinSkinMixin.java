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

/** Skin des acteurs de la scène de sélection, et des corps laissés au sol après une mort RP. */
@Mixin(ClientMannequin.class)
public abstract class ClientMannequinSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void reborn$sceneSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        ClientMannequin self = (ClientMannequin) (Object) this;
        if (cir.getReturnValue() == null) return;
        SceneActors.Skin s = SceneActors.skinOf(self.getId());
        Identifier id;
        boolean slim;
        if (s != null) { id = s.texture(); slim = s.slim(); }
        else {
            // corps laissé au sol après une mort RP : habillé de l'apparence Reborn du personnage
            fr.reborn.hud.ko.CorpsClient.Skin c = fr.reborn.hud.ko.CorpsClient.corpseSkin(self.getUUID());
            if (c == null) return;
            id = c.texture();
            slim = c.slim();
        }
        cir.setReturnValue(cir.getReturnValue().with(PlayerSkin.Patch.create(
                Optional.of(new ClientAsset.ResourceTexture(id, id)), Optional.empty(), Optional.empty(),
                Optional.of(slim ? PlayerModelType.SLIM : PlayerModelType.WIDE))));
    }
}
