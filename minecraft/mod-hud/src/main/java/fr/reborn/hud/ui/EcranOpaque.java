package fr.reborn.hud.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Écran Reborn dont le fond couvre tout l'écran de pixels opaques (Échap, fiche shinobi, échoppe, réglages, carte) :
 * tant qu'il le couvre, le monde n'est ni extrait ni rendu derrière lui (DA §6 — performance). Le monde reste
 * chargé et le jeu continue de tourner ; seule l'image cachée n'est plus calculée.
 *
 * <p>Branché par {@code mixin/menu/OpaqueMenuLevelMixin} sur {@code Minecraft.renderFrame} et
 * {@code OpaqueMenuHudMixin} sur {@code Hud.extractRenderState}. L'état est lu une fois
 * par image ({@link #debutImage}) pour que l'extraction et le rendu d'une même image fassent le même choix.
 */
public interface EcranOpaque {

    /** Vrai quand le fond de l'écran couvre, à cette image, chaque pixel de la fenêtre. */
    default boolean couvreEcran() { return true; }

    /** Décision de l'image en cours (true = le monde est caché). */
    final class Etat {
        static boolean cache;

        private Etat() {}
    }

    /** Appelé en tête de chaque image. */
    static void debutImage(Minecraft mc) {
        Screen s = mc.gui == null ? null : mc.gui.screen();
        boolean c = false;
        if (s instanceof EcranOpaque o) {
            try {
                c = o.couvreEcran();
            } catch (RuntimeException e) {
                c = false;
            }
        }
        Etat.cache = c;
    }

    /** Vrai si un menu opaque couvre l'écran à cette image (monde et HUD cachés). */
    static boolean mondeCache() { return Etat.cache; }

    /** Le monde doit-il être extrait / rendu à cette image ? */
    static boolean monde(boolean vanilla) { return vanilla && !Etat.cache; }
}
