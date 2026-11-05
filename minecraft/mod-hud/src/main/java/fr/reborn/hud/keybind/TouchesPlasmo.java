package fr.reborn.hud.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

import java.util.function.BiConsumer;

/**
 * Touches de Plasmo Voice vues depuis le paquet des touches, <b>sans aucune classe de Plasmo</b> : ce paquet se charge
 * aussi quand Plasmo Voice n'est pas dans le modpack. Le travail (fusion des raccourcis, CADRAGE §10) est fait par
 * {@code voice/PlasmoTouches}, chargée par l'addon Plasmo seulement, qui se branche ici par {@link #poser(Pont)}.
 *
 * <p>Fusion du lot 3 (05/11) : une seule liaison par action. Nos {@link KeyMapping} « Couper le micro »
 * ({@link HudKeybinds#MICRO}), « Couper le son » ({@link HudKeybinds#SON}), « Appuyer pour parler »
 * ({@link HudKeybinds#PARLER}) et « Parler à la radio » ({@link HudKeybinds#RADIO_PARLER}) sont recopiées dans les
 * raccourcis de Plasmo, et un changement fait dans le menu de Plasmo (V › Raccourcis) revient dans les nôtres.
 * « Réglages de la voix » est la touche Minecraft de Plasmo ({@code key.plasmovoice.settings}) : rien à recopier.
 */
public final class TouchesPlasmo {

    /** Identifiant Fabric de Plasmo Voice. */
    public static final String MOD_ID = "plasmovoice";
    /** Touche Minecraft du menu de Plasmo Voice (V). */
    public static final String REGLAGES = "key.plasmovoice.settings";

    private static Boolean present;
    private static volatile Pont pont;

    private TouchesPlasmo() {}

    /** Ce que la colle de Plasmo ({@code voice/PlasmoTouches}) fournit une fois Plasmo prêt. */
    public interface Pont {
        /**
         * Raccourcis de Plasmo <b>non fusionnés</b> avec une touche Reborn et liés à une touche (distance, occlusion,
         * activations d'addons…), pour signaler les conflits : (nom lisible, touche principale).
         */
        void autres(BiConsumer<String, InputConstants.Key> sortie);

        /** Banc : l'état de chaque lien (touche Reborn ↔ raccourci de Plasmo). */
        String etat();
    }

    static void init() {
        present();
    }

    /** Plasmo Voice est-il dans le modpack ? (sinon les touches de la voix restent sans effet et sont masquées) */
    public static boolean present() {
        if (present == null) {
            boolean p;
            try {
                p = FabricLoader.getInstance().isModLoaded(MOD_ID);
            } catch (RuntimeException e) {
                p = false;
            }
            present = p;
        }
        return present;
    }

    /** Branché par {@code voice/PlasmoTouches} quand Plasmo Voice est prêt. */
    public static void poser(Pont p) {
        pont = p;
    }

    /** La touche est-elle recopiée dans un raccourci de Plasmo Voice ? (étiquette « Plasmo » des Paramètres) */
    public static boolean fusionnee(KeyMapping km) {
        return km != null && present() && (km == HudKeybinds.MICRO || km == HudKeybinds.SON || km == HudKeybinds.PARLER
            || km == HudKeybinds.RADIO_PARLER);
    }

    /** Touche Minecraft « Réglages de la voix » (V) de Plasmo, ou null sans Plasmo. */
    public static KeyMapping reglages() {
        return present() ? KeyMapping.get(REGLAGES) : null;
    }

    /** Raccourcis de Plasmo non fusionnés (rien sans Plasmo, ou avant qu'il soit prêt). */
    public static void autres(BiConsumer<String, InputConstants.Key> sortie) {
        Pont p = pont;
        if (p == null) return;
        try {
            p.autres(sortie);
        } catch (Throwable ignored) { }
    }

    /** Banc des touches : état des liens, ou pourquoi il n'y en a pas. */
    public static String etat() {
        if (!present()) return "Plasmo Voice absent";
        Pont p = pont;
        if (p == null) return "Plasmo Voice pas encore prêt";
        try {
            return p.etat();
        } catch (Throwable t) {
            return "Plasmo Voice : " + t;
        }
    }
}
