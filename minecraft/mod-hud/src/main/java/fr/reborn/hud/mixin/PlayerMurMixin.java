package fr.reborn.hud.mixin;

import fr.reborn.hud.mur.MurClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marche sur les murs (lot 5, essai C0), joueur local seulement, et seulement tant que son corps est au mur
 * ({@link MurClient#corpsPris}) :
 * <ul>
 *   <li><b>pose de nage tenue</b> : {@code Player.updatePlayerPose} remet la pose debout à chaque tick chez le joueur
 *       local, et le serveur ne renvoie pas une pose qui n'a pas changé chez lui. Les autres joueurs
 *       ({@code RemotePlayer}) gardent d'eux-mêmes la pose du serveur ;</li>
 *   <li><b>déplacement</b> : {@code Player.travel} est remplacé par {@link MurClient#voyage} (plan du mur, ni gravité ni
 *       frottement). On passe avant le vol créatif et avant {@code SautVolMixin} (saut chargé, sur
 *       {@code LivingEntity.travel}), qui restent tels quels : si un saut chargé part, le mur rend la main.</li>
 * </ul>
 * Ces méthodes existent aussi chez le {@code ServerPlayer} du serveur intégré : il n'est jamais {@code mc.player}.
 */
@Mixin(Player.class)
public abstract class PlayerMurMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void reborn$poseTenueAuMur(CallbackInfo ci) {
        if (!MurClient.corpsPris) return;
        if (!((Object) this instanceof LocalPlayer p) || p != Minecraft.getInstance().player) return;
        MurClient.tenirPose(p);
        ci.cancel();
    }

    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void reborn$marcheAuMur(Vec3 input, CallbackInfo ci) {
        if (!MurClient.corpsPris) return;
        if (!((Object) this instanceof LocalPlayer p) || p != Minecraft.getInstance().player) return;
        if (MurClient.voyage(p)) ci.cancel();
    }
}
