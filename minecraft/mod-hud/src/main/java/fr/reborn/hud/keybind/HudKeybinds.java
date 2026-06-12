package fr.reborn.hud.keybind;

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

    private HudKeybinds() {}

    public static void registerClient() {
        KeyBinding openEditScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-hud.open_editor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
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
        });
    }
}
