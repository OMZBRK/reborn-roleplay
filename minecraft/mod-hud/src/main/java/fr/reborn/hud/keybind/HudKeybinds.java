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

import java.util.ArrayList;
import java.util.List;

/**
 * Touche {@code H} ouvre {@link HudEditScreen} (édition des positions HUD).
 * Catégorie : « Reborn » ({@link RebornKeyCategory}) dans le menu Commandes
 * vanilla — les binds Reborn sont dissociés de « Divers » (MISC) et regroupés.
 * Configurable par l'utilisateur via le menu standard <b>ou</b> l'onglet
 * Contrôles des paramètres Reborn (qui itère {@link #REBORN_KEYS}).
 */
public final class HudKeybinds {

    /** Exposée pour que l'écran Photo affiche/teste la touche de sortie. */
    public static KeyMapping PHOTO;

    /** Garde / parade timée (touche dédiée, MAINTENIR = garde, taper à l'impact =
     *  parade). Lue par {@link fr.reborn.hud.combat.CombatInput#tick} via isDown() —
     *  clic droit laissé libre pour les armes/kunai. Rebindable. */
    public static KeyMapping PARRY;

    /**
     * Tous les binds Reborn de ce mod, dans l'ordre d'affichage — l'onglet
     * Contrôles ({@code ControlsTab}) les liste pour rebind inline. Rempli à
     * {@link #registerClient()}.
     */
    public static final List<KeyMapping> REBORN_KEYS = new ArrayList<>();

    private HudKeybinds() {}

    /** Crée + enregistre un bind Reborn (catégorie « Reborn ») et le référence. */
    private static KeyMapping bind(String translationKey, int glfwKey) {
        KeyMapping km = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            translationKey, InputConstants.Type.KEYSYM, glfwKey, RebornKeyCategory.REBORN));
        REBORN_KEYS.add(km);
        return km;
    }

    public static void registerClient() {
        // S'assure que les prefs (dont tablistHold) sont chargées avant lecture.
        fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.ensureLoaded();
        REBORN_KEYS.clear();

        KeyMapping openEditScreen = bind("key.reborn-hud.open_editor", GLFW.GLFW_KEY_H);
        KeyMapping openCrosshair = bind("key.reborn-hud.open_crosshair", GLFW.GLFW_KEY_J);
        KeyMapping openInteraction = bind("key.reborn-hud.open_interaction", GLFW.GLFW_KEY_R);
        KeyMapping toggleCinema = bind("key.reborn-hud.toggle_cinema", GLFW.GLFW_KEY_K);
        KeyMapping togglePhoto = bind("key.reborn-hud.toggle_photo", GLFW.GLFW_KEY_P);
        PHOTO = togglePhoto;

        KeyMapping openGallery = bind("key.reborn-hud.open_gallery", GLFW.GLFW_KEY_G);

        // Caméra épaule Reborn. Défauts sur des touches LIBRES (V=ReplayMod,
        // X=lâcher, B=émote Emotecraft, N=menu OST, M=PlasmoVoice sont pris).
        // Y = toggle, U = swap épaule, I = cycle preset. Rebindables.
        KeyMapping toggleShoulderCam = bind("key.reborn-hud.cam_toggle", GLFW.GLFW_KEY_Y);
        KeyMapping swapShoulder = bind("key.reborn-hud.cam_swap", GLFW.GLFW_KEY_U);
        KeyMapping cyclePreset = bind("key.reborn-hud.cam_preset", GLFW.GLFW_KEY_I);
        KeyMapping openCamMenu = bind("key.reborn-hud.cam_menu", GLFW.GLFW_KEY_O);
        // Bascule caméra Reborn (épaule) ↔ caméra Minecraft VANILLA (F5/double-F5 libre).
        // Utile en cas de souci de rendu (chunks) avec la caméra Reborn, et pour un test
        // A/B. Défaut touche « ' » (apostrophe), rebindable.
        KeyMapping camVanilla = bind("key.reborn-hud.cam_vanilla", GLFW.GLFW_KEY_APOSTROPHE);
        // Course chakraïque (« Naruto run ») : touche dédiée, bascule le
        // mouvement libre client + notifie le plugin serveur (canal reborn:naruto).
        KeyMapping narutoTest = bind("key.reborn-hud.naruto_test", GLFW.GLFW_KEY_L);
        // Menu de sélection du style de marche (GTA-RP).
        KeyMapping walkMenu = bind("key.reborn-hud.walk_menu", GLFW.GLFW_KEY_PERIOD);
        // Regard libre (free-look) : MAINTENIR ALT gauche → la caméra orbite
        // autour du perso sans le réorienter. Relâcher = la caméra reste où elle
        // est (pas de snap brutal). Touche de maintien : lue par isDown().
        KeyMapping freeLook = bind("key.reborn-hud.cam_freelook", GLFW.GLFW_KEY_LEFT_ALT);
        // Menu de sélection de personnage : sur serveur → demande au plugin de
        // (r)ouvrir la sélection (C2S "open") ; en solo/dev → ouvre l'écran mock.
        KeyMapping charMenu = bind("key.reborn-hud.char_menu", GLFW.GLFW_KEY_SEMICOLON);
        // Dash de combat 8-dir (formules Keriox : rapide + lift). Touche V, rebindable.
        KeyMapping dash = bind("key.reborn-hud.dash", GLFW.GLFW_KEY_V);
        // Garde / parade (touche dédiée MAINTENUE). Défaut C (ancien raccourci inventaire
        // libéré ; ⚠️ Zoomify utilise parfois C → rebindable). Lue par CombatInput.tick.
        PARRY = bind("key.reborn-hud.parry", GLFW.GLFW_KEY_C);
        // Test de la feuille (tirage de nature de chakra, prototype de FEEL). Touche F
        // (= Feuille), rebindable. Solo/dev : ouvre directement l'écran gacha.
        KeyMapping tirageFeuille = bind("key.reborn-hud.tirage_feuille", GLFW.GLFW_KEY_F);
        // (Le repositionnement cosmétique n'a PLUS de raccourci : il s'ouvre depuis
        // l'inventaire — clic droit sur un cosmétique équipé → « Repositionner ».)

        // La touche INVENTAIRE vanilla (E par defaut) ouvre la SACOCHE au lieu de
        // l'inventaire Minecraft. On draine les clics en START_CLIENT_TICK (AVANT
        // handleKeybinds) pour que MC n'ouvre pas son inventaire ; la fermeture par
        // la meme touche est geree dans InventoryScreen.keyPressed. (Ancien raccourci
        // dedie C retire : l'inventaire est UNIQUEMENT sur la touche Inventaire.)
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.options == null) return;
            while (client.options.keyInventory.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            fr.reborn.hud.menu.inventory.InventoryPayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new fr.reborn.hud.menu.inventory.InventoryPayload("open"));
                    } else {
                        mc.setScreenAndShow(new fr.reborn.hud.menu.inventory.InventoryScreen());
                    }
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed() drain le queue d'events — false sur les frames
            // sans transition pressed.
            while (openEditScreen.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) {
                    mc.setScreenAndShow(new HudEditScreen(null));
                }
            }
            while (openCrosshair.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) {
                    mc.setScreenAndShow(new CrosshairScreen(null));
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
                if (mc.gui.screen() == null) {
                    mc.setScreenAndShow(new fr.reborn.hud.ui.PhotoModeScreen());
                }
            }
            while (openGallery.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) {
                    mc.setScreenAndShow(new fr.reborn.hud.ui.GalleryScreen(null));
                }
            }
            while (toggleShoulderCam.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
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
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.setScreenAndShow(new fr.reborn.hud.camera.CameraScreen(null));
                }
            }
            while (camVanilla.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    fr.reborn.hud.camera.RebornCamera.INSTANCE.toggleVanilla(mc);
                    String state = fr.reborn.hud.camera.RebornCamera.INSTANCE.isVanilla()
                        ? "§eCaméra Minecraft vanilla (F5 libre)"
                        : "§aCaméra Reborn (épaule)";
                    mc.gui.hud.getChat().addClientSystemMessage(
                        net.minecraft.network.chat.Component.literal("§6[Reborn] §f" + state));
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
            while (dash.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.gui.screen() == null) {
                    fr.reborn.hud.combat.CombatInput.INSTANCE.dash(mc);
                }
            }
            while (tirageFeuille.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.setScreenAndShow(new fr.reborn.hud.menu.tirage.TirageScreen());
                }
            }
            while (walkMenu.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.setScreenAndShow(new fr.reborn.hud.animation.AnimationMenuScreen(null));
                }
            }
            while (charMenu.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    // Sur serveur : demande à ShinobiCore de (r)ouvrir la sélection.
                    // Solo/dev : ouvre directement l'écran (données mock).
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            fr.reborn.hud.menu.character.CharacterPayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new fr.reborn.hud.menu.character.CharacterPayload("open"));
                    } else {
                        mc.setScreenAndShow(new fr.reborn.hud.menu.character.CharacterSelectScreen());
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
                    // N'ouvre l'écran Reborn QUE si le serveur pousse le feed
                    // (plugin présent) ; sinon on laisse le tablist vanilla.
                    if (!hold && fr.reborn.hud.menu.tablist.TablistData.hasData()
                            && mc.gui.screen() == null && mc.player != null) {
                        mc.setScreenAndShow(new fr.reborn.hud.menu.tablist.TablistScreen());
                    }
                }
            }
            // Regard libre : actif tant que ALT est maintenu (hors écran/ingame).
            {
                Minecraft mc = Minecraft.getInstance();
                boolean fl = freeLook.isDown() && mc.gui.screen() == null && mc.player != null;
                fr.reborn.hud.camera.RebornCamera.INSTANCE.setFreeLook(fl);
                // Amortissement d'atterrissage (no-op si désactivé dans le menu caméra).
                fr.reborn.hud.camera.RebornCamera.INSTANCE.tickImpact(mc.player);
            }
            // Verrouille la vue selon le mode (épaule par défaut) + neutralise F5.
            fr.reborn.hud.camera.RebornCamera.INSTANCE.tickView(Minecraft.getInstance());
            // Anims de mouvement (marche/course/naruto-run) du joueur local.
            fr.reborn.hud.animation.MovementAnimations.INSTANCE.tick(Minecraft.getInstance());
            // Garde M2 (clic droit maintenu à mains nues) → anim + C2S.
            fr.reborn.hud.combat.CombatInput.INSTANCE.tick(Minecraft.getInstance());
            // Saut de chakra (sneak + espace maintenus → charge + saut).
            fr.reborn.hud.combat.ChakraJump.INSTANCE.tick(Minecraft.getInstance());
            // Double saut : double-tap barre espace (le 2e appui en l'air).
            fr.reborn.hud.combat.MobilityInput.INSTANCE.tick(Minecraft.getInstance());

            // Déplacement free-cam du mode photo (lecture clavier brute).
            fr.reborn.hud.immersion.PhotoMode.INSTANCE.tickMovement(Minecraft.getInstance());
        });
    }
}
