package fr.reborn.ost.keybind;

import fr.reborn.ost.ui.OstScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds Reborn OST. Une seule entrée pour l'instant : ouvrir le menu.
 *
 * <p>Catégorie « Reborn » partagée ({@code reborn:controls}) → même section que
 * les binds de {@code mod-hud} dans Options → Commandes, dissociée de « Divers ».
 * Les deux mods enregistrent le même id ; le premier initialisé l'enregistre,
 * l'autre réutilise une instance {@code equals} (cf. {@link #rebornCategory()}).
 */
public final class OstKeybinds {

    private static KeyMapping openMenu;

    private OstKeybinds() {}

    /** Catégorie partagée « Reborn » (dupliquée : pas de dépendance vers mod-hud). */
    private static KeyMapping.Category rebornCategory() {
        Identifier id = Identifier.fromNamespaceAndPath("reborn", "controls");
        try {
            return KeyMapping.Category.register(id);
        } catch (IllegalArgumentException alreadyRegistered) {
            return new KeyMapping.Category(id);
        }
    }

    public static void registerClient() {
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-ost.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            rebornCategory()
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
