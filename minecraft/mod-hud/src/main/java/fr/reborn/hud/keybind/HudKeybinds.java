package fr.reborn.hud.keybind;

import fr.reborn.hud.crosshair.CrosshairScreen;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.ui.HudEditScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Touche {@code H} ouvre {@link HudEditScreen} (édition des positions HUD).
 * Catégorie : "Reborn HUD" dans le menu Controls vanilla. Configurable par
 * l'utilisateur via le menu standard.
 */
public final class HudKeybinds {

    /** Exposée pour que l'écran Photo affiche/teste la touche de sortie. */
    public static KeyBinding PHOTO;

    private HudKeybinds() {}

    public static void registerClient() {
        KeyBinding openEditScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.open_editor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.reborn-hud"
        ));

        KeyBinding openCrosshair = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.open_crosshair",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.reborn-hud"
        ));

        KeyBinding openInteraction = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.open_interaction",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.reborn-hud"
        ));

        KeyBinding toggleCinema = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.toggle_cinema",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.reborn-hud"
        ));

        KeyBinding togglePhoto = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.toggle_photo",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.reborn-hud"
        ));
        PHOTO = togglePhoto;

        KeyBinding openGallery = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.open_gallery",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.reborn-hud"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed() drain le queue d'events — false sur les frames
            // sans transition pressed.
            while (openEditScreen.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new HudEditScreen(null));
                }
            }
            while (openCrosshair.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new CrosshairScreen(null));
                }
            }
            while (openInteraction.wasPressed()) {
                InteractionMode.INSTANCE.toggle();
            }
            while (toggleCinema.wasPressed()) {
                fr.reborn.hud.immersion.CinemaBars.INSTANCE.toggle();
            }
            while (togglePhoto.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new fr.reborn.hud.ui.PhotoModeScreen());
                }
            }
            while (openGallery.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new fr.reborn.hud.ui.GalleryScreen(null));
                }
            }
            // Déplacement free-cam du mode photo (lecture clavier brute).
            fr.reborn.hud.immersion.PhotoMode.INSTANCE.tickMovement(MinecraftClient.getInstance());
        });
    }
}
