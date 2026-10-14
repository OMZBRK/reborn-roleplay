package fr.reborn.hud.voice;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;

/**
 * Addon client PlasmoVoice de Reborn : déclare Reborn comme addon PV, sert de <b>détection de présence</b> — charger
 * cet addon échoue (NoClassDefFoundError, capturé) si PlasmoVoice n'est pas dans le modpack, ce qui gate
 * l'enregistrement du renderer — et branche la <b>radio</b> ({@link RadioVoix} : son radio sur la ligne « radio »,
 * icône d'activation masquée, touche B).
 *
 * <p>L'accès au client se fait via l'instance statique {@code ModVoiceClient.INSTANCE} (l'injection
 * {@code @InjectPlasmoVoice} sur un champ Java ne s'est pas révélée fiable côté client).
 */
@Addon(id = "reborn-voice", name = "Reborn Voice", version = "1.1.0",
       authors = {"Reborn"}, scope = AddonLoaderScope.CLIENT)
public final class RebornVoiceAddon implements AddonInitializer {

    private static RadioVoix radio;

    @Override
    public void onAddonInitialize() {
        PlasmoVoiceClient client;
        try {
            client = su.plo.voice.client.ModVoiceClient.INSTANCE;
        } catch (Throwable t) {
            return;
        }
        if (client == null || radio != null) return;
        radio = new RadioVoix(client);
        client.getEventBus().register(this, radio);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { radio.tick(); } catch (RuntimeException ignored) { }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (h, mc) -> radio.reinitialiser());
    }

    /** J'émets à la radio en ce moment (touche B tenue et activation active) ? Faux sans Plasmo Voice. */
    public static boolean radioEmet() {
        try {
            return radio != null && radio.emet();
        } catch (Throwable t) {
            return false;
        }
    }
}
