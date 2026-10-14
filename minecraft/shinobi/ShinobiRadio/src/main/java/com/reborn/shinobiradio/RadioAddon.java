package com.reborn.shinobiradio;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * Addon serveur Plasmo Voice de la radio : chargé dans {@code onLoad} du plugin
 * ({@code PlasmoVoiceServer.getAddonsLoader().load(addon)}, plasmovoice.com/docs/api). Plasmo Voice injecte
 * l'instance serveur puis appelle {@link #onAddonInitialize()} : on enregistre alors l'activation « radio » et la
 * ligne de sources « radio » ({@link VoixRadio}). Plasmo Voice recharge ses addons à chaque rechargement de sa config :
 * l'activation est réenregistrée à chaque fois.
 */
@Addon(id = "reborn-radio", name = "Reborn Radio", version = "1.0.0", authors = {"Reborn"}, scope = AddonLoaderScope.SERVER)
public final class RadioAddon implements AddonInitializer {

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private final ShinobiRadio plugin;
    private VoixRadio voix;

    RadioAddon(ShinobiRadio plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onAddonInitialize() {
        if (voiceServer == null) {
            plugin.getLogger().severe("[radio] Plasmo Voice n'a pas injecté son serveur : pas de voix radio.");
            return;
        }
        if (voix == null) voix = new VoixRadio(plugin, this, voiceServer);
        voix.enregistrer();
        plugin.voixPrete(voix);
    }

    @Override
    public void onAddonShutdown() {
        if (voix != null) voix.retirer();
    }
}
