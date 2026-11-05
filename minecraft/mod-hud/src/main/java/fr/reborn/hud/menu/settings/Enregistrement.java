package fr.reborn.hud.menu.settings;

import fr.reborn.hud.RebornHudClient;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enregistrements différés des Paramètres (revue du lot 3) : pendant qu'un curseur est tenu à la souris
 * ({@link SliderWidget#glisse()}), la valeur s'applique à chaque pas mais rien n'est écrit sur disque ; l'écriture part
 * une seule fois, au relâché ({@link SliderWidget}) ou à la fermeture des Paramètres. Hors glissé (interrupteur,
 * segments, liste), elle part tout de suite, comme avant.
 *
 * <p>Évite les centaines d'écritures d'{@code options.txt} (et de la config de Plasmo Voice) pendant un glissé, sur le
 * fil de rendu (cible 4 Go, disques lents).
 */
public final class Enregistrement {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/reglages");

    private static boolean options, plasmo, hud;

    private Enregistrement() {}

    /** Options de Minecraft ({@code options.txt}) à enregistrer. */
    public static void options() {
        options = true;
        siLibre();
    }

    /** Configuration de Plasmo Voice à enregistrer ({@link VoixPlasmo}). */
    public static void plasmo() {
        plasmo = true;
        siLibre();
    }

    /** Configuration du HUD (positions, tailles) à enregistrer. */
    public static void hud() {
        hud = true;
        siLibre();
    }

    private static void siLibre() {
        if (!SliderWidget.glisse()) vider();
    }

    /** Écrit ce qui attend (relâché d'un curseur, fermeture des Paramètres). */
    public static void vider() {
        if (options) {
            options = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) mc.options.save();
        }
        if (plasmo) {
            plasmo = false;
            VoixPlasmo.sauver();
        }
        if (hud) {
            hud = false;
            try {
                RebornHudClient.config().save();
            } catch (RuntimeException e) {
                LOG.warn("HUD : configuration non enregistrée ({})", e.toString());
            }
        }
    }
}
