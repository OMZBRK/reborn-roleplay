package fr.reborn.hud.voice;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;

/**
 * Addon client PlasmoVoice minimal : capte l'instance {@link PlasmoVoiceClient}
 * (injectée par {@link InjectPlasmoVoice}) pour que {@link VoiceState} puisse
 * interroger qui parle et alimenter la <b>bulle de parole</b> + les icônes voix.
 *
 * <p>Enregistré via {@code PlasmoVoiceClient.getAddonsLoader().load(...)} au boot
 * du mod. Se désactive proprement si PlasmoVoice est absent (try/catch au load).
 */
@Addon(id = "reborn-voice", name = "Reborn Voice", version = "1.0.0",
       authors = {"Reborn"}, scope = AddonLoaderScope.CLIENT)
public final class RebornVoiceAddon implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    @Override
    public void onAddonInitialize() {
        VoiceState.setClient(voiceClient);
    }

    @Override
    public void onAddonShutdown() {
        VoiceState.setClient(null);
    }
}
