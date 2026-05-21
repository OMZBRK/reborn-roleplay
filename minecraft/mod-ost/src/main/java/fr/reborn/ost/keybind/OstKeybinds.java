package fr.reborn.ost.keybind;

import fr.reborn.ost.ui.OstScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds Reborn OST. Une seule entrée pour l'instant : ouvrir le menu.
 *
 * <p>Catégorie {@code key.categories.reborn-ost} → groupée dans les
 * options vanilla MC, l'utilisateur peut remap via Options → Commandes.
 */
public final class OstKeybinds {

    private static KeyBinding openMenu;

    private OstKeybinds() {}

    public static void registerClient() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reborn-ost.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.reborn-ost"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.wasPressed()) {
                openOstScreen(client);
            }
        });
    }

    private static void openOstScreen(MinecraftClient client) {
        if (client == null) return;
        if (client.currentScreen != null) {
            // Bloque l'ouverture si un autre screen est actif (chat, inv,
            // ESC menu) — évite des bugs d'input doublé.
            return;
        }
        client.setScreen(new OstScreen(null));
    }
}
