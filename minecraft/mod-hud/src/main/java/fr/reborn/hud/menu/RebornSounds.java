package fr.reborn.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sons d'interface Reborn (menus perso). Joués via {@code SoundManager} en
 * {@code forUI} (2D, non spatialisés).
 *
 * <p>Par défaut on utilise des effets vanilla bien choisis (fiables, aucun
 * asset requis). Pour brancher des sons <b>custom</b> (ex. Pixabay) : déposer
 * un OGG Vorbis dans {@code assets/reborn/sounds/ui/<nom>.ogg}, l'ajouter à
 * {@code assets/reborn/sounds.json} sous la clé {@code "ui.<nom>"}, puis
 * remplacer l'appel vanilla par {@link #playReborn(String, float, float)}.
 * (MC n'accepte QUE l'OGG Vorbis — un MP3 Pixabay doit être converti avant.)
 */
public final class RebornSounds {

    private RebornSounds() {}

    private static void play(SoundEvent event, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || event == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    /** Joue un son custom Reborn (namespace {@code reborn}, clé {@code sounds.json}). */
    public static void playReborn(String key, float pitch, float volume) {
        play(SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath("reborn", key)), pitch, volume);
    }

    /** Changement de perso dans la sélection / de page (son « clickmenu » Pixabay). */
    public static void charNav() {
        playReborn("ui.menu_nav", 1.0f, 0.7f);
    }

    /** Clic dans la création de perso (onglets, cycleurs, boutons de nav). */
    public static void uiClick() {
        playReborn("ui.menu_nav", 1.0f, 0.6f);
    }

    /** Confirmation / validation (entrer en jeu, valider le perso — « clickmenuconf »). */
    public static void confirm() {
        playReborn("ui.menu_confirm", 1.0f, 0.8f);
    }
}
