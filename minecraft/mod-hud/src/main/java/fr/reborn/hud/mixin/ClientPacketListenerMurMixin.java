package fr.reborn.hud.mixin;

import fr.reborn.hud.mur.MurClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compteur des <b>corrections de position</b> reçues du serveur pendant une marche sur un mur (lot 5, essai C0) : c'est la
 * mesure des essais 1 et 2 (le serveur a-t-il refusé un déplacement du marcheur ?), envoyée dans {@code lache}.
 *
 * <p>{@code handleMovePlayer} reçoit aussi les téléportations ordinaires : on ne compte que le corps au mur. Le jeu appelle
 * la méthode une première fois sur le fil du réseau, qui la renvoie au fil client : seul ce second passage compte.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMurMixin {

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
            at = @At("HEAD"))
    private void reborn$correctionAuMur(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (MurClient.corpsPris && Minecraft.getInstance().isSameThread()) MurClient.correction();
    }
}
