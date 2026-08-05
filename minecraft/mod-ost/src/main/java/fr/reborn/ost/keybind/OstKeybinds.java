package fr.reborn.ost.keybind;

import fr.reborn.ost.ui.OstScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds Reborn OST. Une seule entrée pour l'instant : ouvrir le menu.
 *
 * <p>Catégorie {@code key.categories.reborn-ost} → groupée dans les
 * options vanilla MC, l'utilisateur peut remap via Options → Commandes.
 */
public final class OstKeybinds {

    private static KeyMapping openMenu;

    private OstKeybinds() {}

    public static void registerClient() {
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-ost.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            // 26.1 : la categorie est une KeyMapping.Category (plus un String).
            KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                openOstScreen(client);
            }
        });
    }

    private static void openOstScreen(Minecraft client) {
        if (client == null) return;
        if (client.screen != null) {
            // Bloque l'ouverture si un autre screen est actif (chat, inv,
            // ESC menu) — évite des bugs d'input doublé.
            return;
        }
        client.setScreen(new OstScreen(null));
    }
}
