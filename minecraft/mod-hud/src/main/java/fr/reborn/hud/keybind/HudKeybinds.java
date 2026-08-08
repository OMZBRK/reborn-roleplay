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

        KeyMapping openEditScreen = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        KeyMapping openCrosshair = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.open_crosshair",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        KeyMapping openInteraction = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.open_interaction",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        KeyMapping toggleCinema = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.toggle_cinema",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        KeyMapping togglePhoto = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.toggle_photo",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        PHOTO = togglePhoto;

        KeyMapping openGallery = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.open_gallery",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        // Caméra épaule Reborn. Défauts sur des touches LIBRES (V=ReplayMod,
        // X=lâcher, B=émote Emotecraft, N=menu OST, M=PlasmoVoice sont pris).
        // Y = toggle, U = swap épaule, I = cycle preset. Rebindables.
        KeyMapping toggleShoulderCam = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.cam_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        KeyMapping swapShoulder = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.cam_swap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        KeyMapping cyclePreset = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.cam_preset",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        KeyMapping openCamMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.cam_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        // Course chakraïque (« Naruto run ») : touche dédiée, bascule le
        // mouvement libre client + notifie le plugin serveur (canal reborn:naruto).
        KeyMapping narutoTest = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.naruto_test",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        // Menu de sélection du style de marche (GTA-RP).
        KeyMapping walkMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.walk_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        // Regard libre (free-look) : MAINTENIR ALT gauche → la caméra orbite
        // autour du perso sans le réorienter. Relâcher = la caméra reste où elle
        // est (pas de snap brutal). Touche de maintien : lue par isDown().
        KeyMapping freeLook = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.cam_freelook",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            net.minecraft.client.KeyMapping.Category.MISC
        ));
        // Menu de sélection de personnage : sur serveur → demande au plugin de
        // (r)ouvrir la sélection (C2S "open") ; en solo/dev → ouvre l'écran mock.
        KeyMapping charMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.reborn-hud.char_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            net.minecraft.client.KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed() drain le queue d'events — false sur les frames
            // sans transition pressed.
            while (openEditScreen.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new HudEditScreen(null));
                }
            }
            while (openCrosshair.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
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
                if (mc.screen == null) {
                    mc.setScreen(new fr.reborn.hud.ui.PhotoModeScreen());
                }
            }
            while (openGallery.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new fr.reborn.hud.ui.GalleryScreen(null));
                }
            }
            while (toggleShoulderCam.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null && mc.player != null) {
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
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new fr.reborn.hud.camera.CameraScreen(null));
                }
            }
            while (narutoTest.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    // Bascule la course chakraïque : active le mouvement libre
                    // client + informe le plugin serveur (canal reborn:naruto).
                    fr.reborn.hud.animation.NarutoRun.INSTANCE.toggle();
                }
            }
            while (walkMenu.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null && mc.player != null) {
                    mc.setScreen(new fr.reborn.hud.animation.AnimationMenuScreen(null));
                }
            }
            while (charMenu.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null && mc.player != null) {
                    // Sur serveur : demande à ShinobiCore de (r)ouvrir la sélection.
                    // Solo/dev : ouvre directement l'écran (données mock).
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            fr.reborn.hud.menu.character.CharacterPayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new fr.reborn.hud.menu.character.CharacterPayload("open"));
                    } else {
                        mc.setScreen(new fr.reborn.hud.menu.character.CharacterSelectScreen());
                    }
                }
            }
            // Tablist Reborn (touche liste-joueurs = Tab). Mode Toggle (défaut) :
            // presser ouvre l'écran interactif. Mode Hold : on ne fait rien ici,
            // l'overlay lecture seule est dessiné par PlayerListHudMixin tant que
            // la touche est maintenue. On draine toujours la file d'events.
            if (client.options != null) {
                boolean hold = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.tablistHold;
                while (client.options.keyPlayerList.consumeClick()) {
                    Minecraft mc = Minecraft.getInstance();
                    if (!hold && mc.screen == null && mc.player != null) {
                        mc.setScreen(new fr.reborn.hud.menu.tablist.TablistScreen());
                    }
                }
            }
            // Regard libre : actif tant que ALT est maintenu (hors écran/ingame).
            {
                Minecraft mc = Minecraft.getInstance();
                boolean fl = freeLook.isDown() && mc.screen == null && mc.player != null;
                fr.reborn.hud.camera.RebornCamera.INSTANCE.setFreeLook(fl);
                // Amortissement d'atterrissage (no-op si désactivé dans le menu caméra).
                fr.reborn.hud.camera.RebornCamera.INSTANCE.tickImpact(mc.player);
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
