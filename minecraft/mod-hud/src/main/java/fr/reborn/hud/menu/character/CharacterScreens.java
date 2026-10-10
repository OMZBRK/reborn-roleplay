package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.character.scene.SceneJourneyScreen;
import fr.reborn.hud.menu.character.scene.SceneSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Point d'entrée des écrans du flux perso. Par défaut : sélection et création <b>en scène</b> ; l'ancienne version
 * reste disponible avec {@code -Dreborn.classicCharacterScreens=true} (repli en cas de souci).
 */
public final class CharacterScreens {

    private CharacterScreens() {}

    public static boolean classic() { return Boolean.getBoolean("reborn.classicCharacterScreens"); }

    public static Screen select(Minecraft mc) {
        if (classic() || mc.player == null || mc.level == null) return new CharacterSelectScreen();
        return SceneSelectScreen.fromRoster(mc);
    }

    public static Screen create(Minecraft mc) {
        if (classic() || mc.player == null || mc.level == null) return new CharacterCreateScreen();
        return SceneJourneyScreen.open(mc);
    }

    /** Vrai pour tout écran du flux perso (sélection / création / chargement, classiques ou en scène). */
    public static boolean isCharacterScreen(Screen s) {
        return s instanceof CharacterSelectScreen || s instanceof CharacterCreateScreen
            || s instanceof CharacterLoadingScreen || s instanceof SceneSelectScreen || s instanceof SceneJourneyScreen;
    }
}
