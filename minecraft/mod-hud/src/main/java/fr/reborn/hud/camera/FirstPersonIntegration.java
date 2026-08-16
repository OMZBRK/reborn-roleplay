package fr.reborn.hud.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intégration du mod <b>First-Person Model</b> (tr7zw, {@code firstperson-fabric})
 * sous le contrôle de la caméra Reborn.
 *
 * <p>Le mod tr7zw rend uniquement le <i>corps en 1ère personne</i> (pas de vue 3e
 * personne concurrente : cf. sa description « Enables the third-person Model in
 * first-person »). Comme {@link RebornCamera#tickView} force déjà la perspective
 * {@code FIRST_PERSON} quand le joueur bascule en mode {@link RebornCamera.Mode#FIRST}
 * (touche Reborn {@code Y}), le corps s'affiche automatiquement — les deux systèmes
 * coopèrent sans code de rendu à réécrire.
 *
 * <p>Seul point de friction : le mod tr7zw expose <b>sa propre touche</b> de bascule
 * ({@code key.firstperson.toggle}, défaut {@code F6}) qui active/désactive son rendu.
 * Si le joueur la presse, la 1ère personne Reborn perdrait le corps. On la
 * <b>neutralise</b> (dé-bind) une fois au démarrage : ainsi <b>seule</b> la caméra
 * Reborn pilote l'expérience 1ère personne. No-op si le mod tr7zw est absent.
 */
public final class FirstPersonIntegration {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/firstperson");
    /** Clé de traduction du bind tr7zw (id mod {@code firstperson}). */
    private static final String TR7ZW_TOGGLE = "key.firstperson.toggle";

    private FirstPersonIntegration() {}

    public static void registerClient() {
        final boolean[] done = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done[0] || client.options == null) return;
            done[0] = true;
            neutralizeToggle(client);
        });
    }

    /** Dé-bind la touche de bascule de tr7zw si elle est encore assignée. */
    private static void neutralizeToggle(Minecraft client) {
        try {
            for (KeyMapping km : client.options.keyMappings) {
                if (!TR7ZW_TOGGLE.equals(km.getName())) continue;
                if (!km.isUnbound()) {
                    km.setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                    client.options.save();
                    LOG.info("First-Person Model (tr7zw) : bascule dédiée neutralisée "
                        + "→ la 1ère personne est pilotée uniquement par la caméra Reborn (touche Y).");
                }
                return;
            }
            // Mod absent du modpack : rien à faire.
        } catch (Throwable t) {
            LOG.warn("neutralisation bind tr7zw impossible ({})", t.toString());
        }
    }
}
