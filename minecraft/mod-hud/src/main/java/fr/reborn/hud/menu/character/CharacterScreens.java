package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.character.perso.PersoCreationScreen;
import fr.reborn.hud.menu.character.perso.PersoSelectionScreen;
import fr.reborn.hud.menu.character.scene.SceneJourneyScreen;
import fr.reborn.hud.menu.character.scene.SceneSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Point d'entrée des écrans du flux perso. Par défaut : sélection et création « au bord de l'étang »
 * ({@code perso/}, maquettes 2026-10-13) ; repli possible sur la version en scène 3D
 * ({@code -Dreborn.sceneCharacterScreens=true}) ou sur la toute première version
 * ({@code -Dreborn.classicCharacterScreens=true}).
 */
public final class CharacterScreens {

    private CharacterScreens() {}

    public static boolean classic() { return Boolean.getBoolean("reborn.classicCharacterScreens"); }

    public static Screen select(Minecraft mc) {
        if (classic() || mc.player == null || mc.level == null) return new CharacterSelectScreen();
        if (Boolean.getBoolean("reborn.sceneCharacterScreens")) return SceneSelectScreen.fromRoster(mc);
        return PersoSelectionScreen.fromRoster(mc);
    }

    public static Screen create(Minecraft mc) {
        if (classic() || mc.player == null || mc.level == null) return new CharacterCreateScreen();
        if (Boolean.getBoolean("reborn.sceneCharacterScreens")) return SceneJourneyScreen.open(mc);
        return new PersoCreationScreen();
    }

    /** Vrai pour tout écran du flux perso (sélection / création / chargement, classiques ou en scène). */
    public static boolean isCharacterScreen(Screen s) {
        return s instanceof CharacterSelectScreen || s instanceof CharacterCreateScreen
            || s instanceof CharacterLoadingScreen || s instanceof SceneSelectScreen || s instanceof SceneJourneyScreen
            || s instanceof PersoSelectionScreen || s instanceof PersoCreationScreen;
    }
}
