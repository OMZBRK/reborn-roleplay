package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.ChargementScene;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackLoadFeedback;
import net.minecraft.network.Connection;
import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Suivi du pack serveur pour la scène de chargement ({@link ChargementScene}) :
 * <ul>
 *   <li>progression du téléchargement (octets reçus / taille annoncée) → « Téléchargement des ressources 42 % ». Le
 *       toast vanilla « Téléchargement du pack » n'est jamais affiché ;</li>
 *   <li>résultat final de chaque pack (appliqué, refusé, échec) → le pack n'est plus « en route ».</li>
 * </ul>
 * Le retour au serveur (écouteur de résultats) est enveloppé, pas remplacé : le serveur reçoit toujours ses réponses.
 */
@Mixin(DownloadedPackSource.class)
public abstract class DownloadedPackSourceMixin {

    @Inject(method = "createDownloadNotifier", at = @At("RETURN"), cancellable = true)
    private void reborn$suivreTelechargement(int totalCount, CallbackInfoReturnable<HttpUtil.DownloadProgressListener> cir) {
        // l'écouteur vanilla ne sert qu'au toast : il n'est plus appelé (la scène affiche la progression)
        cir.setReturnValue(new HttpUtil.DownloadProgressListener() {
            @Override
            public void requestStart() {
                ChargementScene.dlDebut();
            }

            @Override
            public void downloadStart(OptionalLong total) {
                ChargementScene.dlTaille(total.orElse(-1L));
            }

            @Override
            public void downloadedBytes(long bytes) {
                ChargementScene.dlRecu(bytes);
            }

            @Override
            public void requestFinished(boolean success) {
                ChargementScene.dlFin(success);
            }
        });
    }

    @Inject(method = "createPackResponseSender", at = @At("RETURN"), cancellable = true)
    private static void reborn$suivreResultat(Connection connection, CallbackInfoReturnable<PackLoadFeedback> cir) {
        PackLoadFeedback vanilla = cir.getReturnValue();
        cir.setReturnValue(new PackLoadFeedback() {
            @Override
            public void reportUpdate(UUID id, PackLoadFeedback.Update update) {
                vanilla.reportUpdate(id, update);
            }

            @Override
            public void reportFinalResult(UUID id, PackLoadFeedback.FinalResult result) {
                ChargementScene.packFini(id, result);
                vanilla.reportFinalResult(id, result);
            }
        });
    }
}
