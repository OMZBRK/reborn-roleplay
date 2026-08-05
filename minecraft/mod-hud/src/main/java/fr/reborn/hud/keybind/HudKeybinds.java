package fr.reborn.hud.keybind;

import fr.reborn.hud.crosshair.CrosshairScreen;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.ui.HudEditScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Touche {@code H} ouvre {@link HudEditScreen} (édition des positions HUD).
 * Catégorie : "Reborn HUD" dans le menu Controls vanilla. Configurable par
 * l'utilisateur via le menu standard.
 */
public final class HudKeybinds {

    /** Exposée pour que l'écran Photo affiche/teste la touche de sortie. */
    public static KeyMapping PHOTO;

    private HudKeybinds() {}

    public static void registerClient() {
        // S'assure que les prefs (dont tablistHold) sont chargées avant lecture.
        fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.ensureLoaded();

        KeyMapping openEditScreen = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.reborn-hud"
        ));

        KeyMapping openCrosshair = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.open_crosshair",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.reborn-hud"
        ));

        KeyMapping openInteraction = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.open_interaction",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.reborn-hud"
        ));

        KeyMapping toggleCinema = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.toggle_cinema",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.reborn-hud"
        ));

        KeyMapping togglePhoto = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.toggle_photo",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.reborn-hud"
        ));
        PHOTO = togglePhoto;

        KeyMapping openGallery = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.open_gallery",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.reborn-hud"
        ));

        // Caméra épaule Reborn. Défauts sur des touches LIBRES (V=ReplayMod,
        // X=lâcher, B=émote Emotecraft, N=menu OST, M=PlasmoVoice sont pris).
        // Y = toggle, U = swap épaule, I = cycle preset. Rebindables.
        KeyMapping toggleShoulderCam = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.cam_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.reborn-hud"
        ));
        KeyMapping swapShoulder = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.cam_swap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.reborn-hud"
        ));
        KeyMapping cyclePreset = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.cam_preset",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.reborn-hud"
        ));
        KeyMapping openCamMenu = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.cam_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.reborn-hud"
        ));
        // TEST : bascule le naruto-run (en attendant le trigger plugin).
        KeyMapping narutoTest = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.naruto_test",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.reborn-hud"
        ));
        // Menu de sélection du style de marche (GTA-RP).
        KeyMapping walkMenu = KeyMappingHelper.registerKeyBinding(new KeyMapping(
            "key.reborn-hud.walk_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            "key.categories.reborn-hud"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed() drain le queue d'events — false sur les frames
            // sans transition pressed.
            while (openEditScreen.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new HudEditScreen(null));
                }
            }
            while (openCrosshair.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new CrosshairScreen(null));
                }
            }
            while (openInteraction.consumeClick()) {
                InteractionMode.INSTANCE.toggle();
            }
            while (toggleCinema.consumeClick()) {
                fr.reborn.hud.immersion.CinemaBars.INSTANCE.toggle();
            }
            while (togglePhoto.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new fr.reborn.hud.ui.PhotoModeScreen());
                }
            }
            while (openGallery.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null) {
                    mc.setScreen(new fr.reborn.hud.ui.GalleryScreen(null));
                }
            }
            while (toggleShoulderCam.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null && mc.player != null) {
                    fr.reborn.hud.camera.RebornCamera.INSTANCE.toggleFirstPerson(mc);
                }
            }
            while (swapShoulder.consumeClick()) {
                fr.reborn.hud.camera.RebornCamera.INSTANCE.swapShoulder();
            }
            while (cyclePreset.consumeClick()) {
                fr.reborn.hud.camera.RebornCamera.INSTANCE.cyclePreset();
            }
            while (openCamMenu.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null && mc.player != null) {
                    mc.setScreen(new fr.reborn.hud.camera.CameraScreen(null));
                }
            }
            while (narutoTest.consumeClick()) {
                fr.reborn.hud.animation.MovementAnimations.INSTANCE.toggleNarutoTest();
            }
            while (walkMenu.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.currentScreen == null && mc.player != null) {
                    mc.setScreen(new fr.reborn.hud.animation.AnimationMenuScreen(null));
                }
            }
            // Tablist Reborn (touche liste-joueurs = Tab). Mode Toggle (défaut) :
            // presser ouvre l'écran interactif. Mode Hold : on ne fait rien ici,
            // l'overlay lecture seule est dessiné par PlayerListHudMixin tant que
            // la touche est maintenue. On draine toujours la file d'events.
            if (client.options != null) {
                boolean hold = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.tablistHold;
                while (client.options.playerListKey.consumeClick()) {
                    Minecraft mc = Minecraft.getInstance();
                    if (!hold && mc.currentScreen == null && mc.player != null) {
                        mc.setScreen(new fr.reborn.hud.menu.tablist.TablistScreen());
                    }
                }
            }
            // Verrouille la vue selon le mode (épaule par défaut) + neutralise F5.
            fr.reborn.hud.camera.RebornCamera.INSTANCE.tickView(Minecraft.getInstance());
            // Anims de mouvement (marche/course/naruto-run) du joueur local.
            fr.reborn.hud.animation.MovementAnimations.INSTANCE.tick(Minecraft.getInstance());

            // Déplacement free-cam du mode photo (lecture clavier brute).
            fr.reborn.hud.immersion.PhotoMode.INSTANCE.tickMovement(Minecraft.getInstance());
        });
    }
}
