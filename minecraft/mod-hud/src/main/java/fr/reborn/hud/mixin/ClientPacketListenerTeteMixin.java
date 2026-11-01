package fr.reborn.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lissage du lacet de tête des <b>autres joueurs</b> ({@code ClientboundRotateHeadPacket} → {@code Entity#lerpHeadTo(yaw,
 * pas)} dans {@code handleRotateMob}, seul appel du client) : le point de réglage unique, remis le 31/10 au soir aux
 * <b>3 ticks</b> de vanilla.
 *
 * <p>Le lot du 31/10 l'avait passé à 2 ticks (le serveur suit les joueurs tous les 2 ticks, {@code updateInterval(2)} :
 * la tête arrivait pile à la cible à l'arrivée du paquet suivant, environ 50 ms plus tôt). La relecture a montré que c'était
 * pire à voir : le lacet du joueur ({@code yRot}) et son tangage restent interpolés sur 3 ticks
 * ({@code InterpolationHandler}), et le corps est borné à 50° de {@code yRot}, pas de la tête — la tête devançait donc le
 * reste et le cou se tordait au-delà de 50° à chaque virage (jusqu'à 110° sur un demi-tour vif, contre 50° en vanilla) ;
 * le regard décrivait une courbe (lacet en 2, tangage en 3) ; et la tête arrivée trop tôt se figeait un tick à chaque
 * paquet en retard (gigue des connexions résidentielles), là où 3 ticks continuent l'approche. Le retard d'une tête
 * distante ne se perçoit pas (on ne voit pas la souris de l'autre) ; une saccade ou un cou tordu, si. À 3, tête, corps,
 * lacet, tangage et position restent synchrones, comme vanilla. Pour retenter 2 : borner aussi le corps sur la tête des
 * {@code RemotePlayer} ({@code tickHeadTurn}), et l'éprouver sur une connexion avec gigue.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerTeteMixin {

    /** Ticks de lissage du lacet de tête d'un autre joueur : 3, celui de vanilla (voir la classe avant de le changer). */
    @Unique
    private static final int REBORN_PAS_TETE_JOUEUR = 3;

    @WrapOperation(method = "handleRotateMob(Lnet/minecraft/network/protocol/game/ClientboundRotateHeadPacket;)V",
                   at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpHeadTo(FI)V"))
    private void reborn$teteJoueur(Entity entity, float yaw, int pas, Operation<Void> original) {
        original.call(entity, yaw, entity instanceof RemotePlayer ? REBORN_PAS_TETE_JOUEUR : pas);
    }
}
