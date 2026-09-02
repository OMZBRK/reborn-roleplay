package fr.reborn.hud.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coupe la musique vanilla de Minecraft (menu + en jeu). Reborn a son
 * propre OST in-game (mod-ost, lecture OpenAL indépendante), on ne veut
 * donc jamais entendre la musique de base par-dessus.
 *
 * <p>Stratégie : on annule {@code MusicManager#tick()} en HEAD. C'est la
 * seule méthode qui déclenche {@code play()} d'une nouvelle piste ; comme
 * le mod est initialisé avant le premier client tick, aucune musique
 * vanilla ne démarre jamais. L'OST Reborn (mod-ost) passe par OpenAL,
 * indépendant du MusicManager — il n'est pas affecté.
 *
 * <p>26.1 : {@code MusicTracker} (net.minecraft.client.resources.sounds) →
 * {@code MusicManager} (net.minecraft.client.sounds).
 */
@Mixin(MusicManager.class)
public class MusicTrackerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void reborn$suppressVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }
}
