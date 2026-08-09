package fr.reborn.hud.voice;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;

/**
 * Addon client PlasmoVoice minimal : sa seule utilité est de déclarer Reborn
 * comme addon PV (propreté) et de servir de <b>détection de présence</b> —
 * charger cet addon échoue (NoClassDefFoundError, capturé) si PlasmoVoice n'est
 * pas dans le modpack, ce qui gate l'enregistrement du renderer.
 *
 * <p>L'accès au client se fait dans {@link VoiceState} via l'instance statique
 * {@code ModVoiceClient.INSTANCE} (l'injection {@code @InjectPlasmoVoice} sur un
 * champ Java ne s'est pas révélée fiable).
 */
@Addon(id = "reborn-voice", name = "Reborn Voice", version = "1.0.0",
       authors = {"Reborn"}, scope = AddonLoaderScope.CLIENT)
public final class RebornVoiceAddon implements AddonInitializer {

    @Override
    public void onAddonInitialize() {
        // Rien : VoiceState lit ModVoiceClient.INSTANCE directement.
    }
}
