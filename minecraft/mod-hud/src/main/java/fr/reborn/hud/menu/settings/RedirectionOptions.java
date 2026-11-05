package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.screens.ConfigShellScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.lang.reflect.Field;

/**
 * Les joueurs ne voient plus les écrans d'options de Minecraft (lot 3, CADRAGE §8.4) : tout {@link OptionsScreen}
 * ouvert (écran titre, autre mod) ouvre nos Paramètres, et ses sous-écrans renvoient à l'onglet qui les remplace
 * (Vidéo → Graphismes, Sons → Audio, Commandes et Souris → Commandes, Tchat → Interface › Chat). Appelé en tête de
 * {@code Gui#setScreen} ({@code mixin/menu/MenuScaleMixin}).
 *
 * <p><b>Porte de secours</b> : « Compte › Avancé › Réglages avancés de Minecraft » (et Langue, Accessibilité) passent
 * par {@link #ouvrirVanilla} : la redirection est suspendue jusqu'au retour à un écran Reborn, à l'Échap, à l'écran
 * titre ou au jeu (les sous-écrans de Minecraft et de Sodium s'enchaînent normalement entre-temps).
 */
public final class RedirectionOptions {

    private static boolean avance;
    private static Field dernierEcran;

    private RedirectionOptions() {}

    /** Ouvre un écran d'options de Minecraft tel quel (porte de secours des Paramètres). */
    public static void ouvrirVanilla(Screen s) {
        avance = true;
        Minecraft.getInstance().setScreenAndShow(s);
    }

    /** L'écran à ouvrir à la place de {@code s} (le même, hors écrans d'options de Minecraft). */
    public static Screen rediriger(Screen s) {
        if (s == null || s instanceof PauseScreen || s instanceof TitleScreen
            || s.getClass().getName().startsWith("fr.reborn.hud.")) {
            avance = false;
            return s;
        }
        if (avance) return s;
        try {
            if (s instanceof OptionsScreen os) {
                return new ConfigShellScreen(os.getLastScreen());
            }
            ConfigShellScreen.Onglet o;
            int sous = -1;
            if (s instanceof VideoSettingsScreen) o = ConfigShellScreen.Onglet.GRAPHISMES;
            else if (s instanceof SoundOptionsScreen) o = ConfigShellScreen.Onglet.AUDIO;
            else if (s instanceof ControlsScreen || s instanceof KeyBindsScreen || s instanceof MouseSettingsScreen)
                o = ConfigShellScreen.Onglet.COMMANDES;
            else if (s instanceof ChatOptionsScreen) {
                o = ConfigShellScreen.Onglet.INTERFACE;
                sous = InterfaceTab.CHAT;
            } else return s;
            Screen parent = parent((OptionsSubScreen) s);
            if (parent instanceof ConfigShellScreen) parent = ((ConfigShellScreen) parent).parent();
            return new ConfigShellScreen(parent, o, sous);
        } catch (RuntimeException e) {
            return s;
        }
    }

    /** {@code OptionsSubScreen#lastScreen} (protégé ; noms de Mojang, le client 26.x n'est pas obfusqué). */
    private static Screen parent(OptionsSubScreen s) {
        try {
            if (dernierEcran == null) {
                dernierEcran = OptionsSubScreen.class.getDeclaredField("lastScreen");
                dernierEcran.setAccessible(true);
            }
            return (Screen) dernierEcran.get(s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
