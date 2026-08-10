package fr.reborn.hud.mixin;

import fr.reborn.hud.skin.RebornSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Override du skin rendu : si {@link RebornSkins} a une texture composée pour ce
 * joueur, on remplace le {@code body()} du {@link PlayerSkin} par notre texture
 * (via {@link PlayerSkin#with}) et on force le {@code model()} selon la carrure
 * choisie ({@link RebornSkins#isSlim} → Alex/classique). La cape et l'elytra sont
 * conservées. Comme tous les joueurs ont le mod, chacun compose la même texture
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
        PlayerModelType model = RebornSkins.isSlim(self.getUUID())
            ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        // ⚠️ Constructeur 2-arg (id, texturePath) : le 1-arg dérive
        // texturePath = "textures/<path>.png", or notre DynamicTexture est
        // enregistrée sous l'id BRUT → mismatch = texture manquante (magenta).
        // On force donc texturePath = id (l'identifiant exact de la texture).
        PlayerSkin patched = original.with(PlayerSkin.Patch.create(
            Optional.of(new ClientAsset.ResourceTexture(id, id)),
            Optional.empty(),
            Optional.empty(),
            Optional.of(model)));
        cir.setReturnValue(patched);
    }
}
