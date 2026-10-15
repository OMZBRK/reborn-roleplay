package fr.reborn.hud.keybind;

import fr.reborn.hud.interaction.InteractionMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Les touches Reborn (carte réduite du 15/10, docs/technique/MOD_HUD.md §2) : K fiche, M carte, R interaction,
 * F ramasser, Z dégainer, B radio (maintenir = parler, appui bref = écran de l'escouade), Entrée / Retour, « . »
 * animations (démarches et émotes), Alt gauche regard libre, F1 cinéma, P photo (la galerie s'ouvre depuis le mode
 * photo), G dash, C parade ; F7 (staff) est dans {@code StaffClient}, N (OST) dans mod-ost. La <b>vue</b> est la
 * touche vanilla « Changer de vue » (F5) : en caméra Reborn elle bascule épaule ↔ 1ʳᵉ personne. Éditeur de HUD et
 * viseur : Échap → Interface ; caméra : Réglages → Caméra ; personnage : Échap (carte d'identité) ; course de chakra :
 * triple appui sur la touche de sprint ({@code allure/Allure}).
 *
 * <p>Catégorie « Reborn » ({@link RebornKeyCategory}) dans le menu Commandes vanilla ; tout reste réglable dans
 * Réglages → Contrôles ({@code ControlsTab}, qui itère {@link #REBORN_KEYS}). Les anciens défauts des joueurs existants
 * sont migrés une fois par {@link TouchesMigration}.
 */
public final class HudKeybinds {

    /** Exposée pour que l'écran Photo affiche/teste la touche de sortie. */
    public static KeyMapping PHOTO;

    /** Garde / parade timée (touche dédiée, MAINTENIR = garde, taper à l'impact =
     *  parade). Lue par {@link fr.reborn.hud.combat.CombatInput#tick} via isDown() —
     *  clic droit laissé libre pour les armes/kunai. Rebindable. */
    public static KeyMapping PARRY;
    /** Fiche shinobi (stats) — lue aussi par StatsScreen pour se refermer. */
    public static KeyMapping STATS;

    /**
     * Bascule des bandes cinéma / mode sans HUD. Défaut <b>F1</b> : ça remplace
     * le « masquer l'ATH » vanilla (redondant avec le mode CLEAN de Reborn) et
     * ça libère la touche K. Exposée pour {@code KeyboardCinemaMixin}, qui
     * neutralise le F1 vanilla UNIQUEMENT tant que ce bind est resté sur F1.
     */
    public static KeyMapping CINEMA;

    /** Carte du monde (lieux, et « Y aller » pour le staff). M, rebindable. */
    public static KeyMapping MAP;

    /** Duel consenti : accepter / refuser l'invitation affichée (sans effet sans invitation). Rebindables. */
    public static KeyMapping DUEL_ACCEPT, DUEL_REFUSE;

    /**
     * Ramasser l'objet au sol visé (inventaire du 13/10). Défaut <b>F</b> : interceptée par {@code sol/RamasserClient}
     * SEULEMENT quand un objet est visé et que le serveur gère le ramassage ; sinon F garde son rôle (techniques,
     * tyrolienne, grappin). Dégainer / rengainer l'arme de l'étui : <b>Z</b>. Rebindables.
     */
    public static KeyMapping PICKUP, DEGAINER;

    /**
     * Radio en escouades (docs/gameplay/RADIO.md) : <b>B</b> maintenu = parler à l'escouade (la touche est recopiée dans
     * l'activation « radio » de Plasmo Voice, cf. {@code voice/RadioVoix}) ; <b>appui bref</b> (moins de
     * {@link #RADIO_BREF_MS} ms) = écran de l'escouade. Rebindable.
     */
    public static KeyMapping RADIO_PARLER;

    /** Mode interaction (R) : lu par les invites (« [R] Tendre … », nourriture). */
    public static KeyMapping INTERACTION;

    /** Menu des animations (« . ») : démarches et émotes ; la même touche le referme. */
    public static KeyMapping ANIMATIONS;

    /** Dash de combat (G) et regard libre (Alt gauche, maintenu). */
    public static KeyMapping DASH, FREELOOK;

    /** Course de chakra en bascule (X) : un appui la lance, un autre l'arrête ; le triple appui sur le sprint reste. */
    public static KeyMapping COURSE_CHAKRA;

    /** Appui plus court que ça sur la touche radio (sans la tenir) = écran de l'escouade. */
    public static final long RADIO_BREF_MS = 250L;
    private static long radioDepuis = -1L;
    private static boolean radioRappel;

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

    /**
     * Nom de la touche tel que le clavier du joueur l'affiche (AZERTY : « M » de la carte = touche « ; » US), en
     * majuscules pour les aides en ArcadePix ; {@code def} si la liaison est absente ou vide. À utiliser dans toutes les
     * invites au lieu d'une lettre écrite en dur.
     */
    public static String nom(KeyMapping km, String def) {
        if (km == null || km.isUnbound()) return def;
        return km.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
    }

    /** Nom affiché d'une touche fixe ({@code GLFW_KEY_…}) sur le clavier du joueur. */
    public static String nom(int glfwKey) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString().toUpperCase(java.util.Locale.ROOT);
    }

    /** Copie d'une liste d'aides où l'entrée {@code lettre} est remplacée par le nom réel de la touche {@code km}. */
    public static String[][] aides(String[][] items, String lettre, KeyMapping km) {
        String n = nom(km, lettre);
        if (n.equals(lettre)) return items;
        String[][] out = new String[items.length][];
        for (int i = 0; i < items.length; i++) {
            out[i] = lettre.equals(items[i][0]) ? new String[]{n, items[i][1]} : items[i];
        }
        return out;
    }

    /** La caméra Reborn pilote-t-elle la touche « Changer de vue » (F5) ? Sinon F5 reste vanilla. */
    public static boolean vueReborn(Minecraft mc) {
        return mc.player != null && fr.reborn.hud.runtime.RebornSession.rpFeaturesEnabled()
            && !fr.reborn.hud.camera.RebornCamera.INSTANCE.isVanilla()
            && !fr.reborn.hud.immersion.PhotoMode.INSTANCE.isActive();
    }

    /** Menu personnage : sur serveur, ShinobiCore (r)ouvre la sélection ; en solo, l'écran de démonstration. */
    public static void ouvrirPersonnage(Minecraft mc) {
        if (mc.player == null) return;
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                fr.reborn.hud.menu.character.CharacterPayload.ID)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new fr.reborn.hud.menu.character.CharacterPayload("open"));
        } else {
            mc.setScreenAndShow(fr.reborn.hud.menu.character.CharacterScreens.select(mc));
        }
    }

    /**
     * Touche radio, lue chaque tick : tenue au-delà de {@link #RADIO_BREF_MS} = on veut parler (rappel discret si
     * l'on ne peut pas émettre) ; relâchée avant = écran de l'escouade. Plasmo Voice émet de son côté tant qu'elle est
     * tenue (activation « radio » en push-to-talk).
     */
    private static void tickRadio(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean libre = mc.gui.screen() == null && mc.player != null;
        int clics = 0;
        while (RADIO_PARLER.consumeClick()) clics++;
        boolean tenue = RADIO_PARLER.isDown();
        if (tenue) {
            if (radioDepuis < 0) {
                if (!libre) return;
                radioDepuis = now;
                radioRappel = false;
            } else if (!radioRappel && now - radioDepuis >= RADIO_BREF_MS) {
                radioRappel = true;
                fr.reborn.hud.radio.RadioClient.appuiB();
            }
            return;
        }
        boolean bref = radioDepuis >= 0 ? now - radioDepuis < RADIO_BREF_MS : clics > 0;
        radioDepuis = -1L;
        if (bref && libre) mc.setScreenAndShow(new fr.reborn.hud.radio.RadioScreen());
    }

    public static void registerClient() {
        // S'assure que les prefs (dont tablistHold) sont chargées avant lecture.
        fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.ensureLoaded();
        REBORN_KEYS.clear();

        // Ordre = ordre d'affichage dans Réglages → Contrôles (carte des touches du 15/10).
        STATS = bind("key.reborn-hud.stats", GLFW.GLFW_KEY_K);
        MAP = bind("key.reborn-hud.map", GLFW.GLFW_KEY_M);
        INTERACTION = bind("key.reborn-hud.open_interaction", GLFW.GLFW_KEY_R);
        // Ramasser (F, partagée avec « main gauche » : prise seulement quand un objet au sol est visé) et dégainer (Z).
        PICKUP = bind("key.reborn-hud.ramasser", GLFW.GLFW_KEY_F);
        DEGAINER = bind("key.reborn-hud.degainer", GLFW.GLFW_KEY_Z);
        RADIO_PARLER = bind("key.reborn-hud.radio_parler", GLFW.GLFW_KEY_B);
        // Entrée / Retour arrière, libres en jeu (le tchat a ses propres touches quand il est ouvert).
        DUEL_ACCEPT = bind("key.reborn-hud.duel_accept", GLFW.GLFW_KEY_ENTER);
        DUEL_REFUSE = bind("key.reborn-hud.duel_refuse", GLFW.GLFW_KEY_BACKSPACE);
        ANIMATIONS = bind("key.reborn-hud.walk_menu", GLFW.GLFW_KEY_PERIOD);
        // Regard libre (free-look) : MAINTENIR ALT gauche → la caméra orbite autour du perso sans le réorienter.
        FREELOOK = bind("key.reborn-hud.cam_freelook", GLFW.GLFW_KEY_LEFT_ALT);
        // F1 : remplace le « masquer l'ATH » vanilla (cf KeyboardCinemaMixin qui annule le F1 de Minecraft tant que
        // ce bind vaut F1).
        CINEMA = bind("key.reborn-hud.toggle_cinema", GLFW.GLFW_KEY_F1);
        PHOTO = bind("key.reborn-hud.toggle_photo", GLFW.GLFW_KEY_P);
        // Dash de combat 8-dir (formules Keriox : rapide + lift). G depuis le 15/10 (V = réglages de Plasmo Voice).
        DASH = bind("key.reborn-hud.dash", GLFW.GLFW_KEY_G);
        // Garde / parade (touche dédiée MAINTENUE). Lue par CombatInput.tick.
        PARRY = bind("key.reborn-hud.parry", GLFW.GLFW_KEY_C);
        // Course de chakra (X, 15/10 au soir, demande du user : une touche à part en plus du triple appui). Même
        // position en AZERTY et QWERTZ. Conflit voulu avec « charger la barre » du créatif (X + chiffre), comme C.
        COURSE_CHAKRA = bind("key.reborn-hud.course_chakra", GLFW.GLFW_KEY_X);

        TouchesMigration.init();
        TouchesDebug.init();

        // La touche INVENTAIRE vanilla (E par defaut) ouvre la SACOCHE au lieu de
        // l'inventaire Minecraft. On draine les clics en START_CLIENT_TICK (AVANT
        // handleKeybinds) pour que MC n'ouvre pas son inventaire ; la fermeture par
        // la meme touche est geree dans InventoryScreen.keyPressed. (Ancien raccourci
        // dedie C retire : l'inventaire est UNIQUEMENT sur la touche Inventaire.)
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.options == null) return;
            // Vue (touche vanilla « Changer de vue », F5 par défaut) : en caméra Reborn, bascule épaule ↔ 1ʳᵉ personne,
            // drainée AVANT handleKeybinds pour que Minecraft ne fasse pas défiler ses trois vues. Caméra vanilla,
            // serveur build, mode photo : F5 reste celle de Minecraft.
            if (vueReborn(client)) {
                while (client.options.keyTogglePerspective.consumeClick()) {
                    if (client.gui.screen() == null) fr.reborn.hud.camera.RebornCamera.INSTANCE.toggleFirstPerson(client);
                }
            }
            // On ne détourne la touche E vers la sacoche QUE si : (1) ShinobiCore est
            // présent (canal reborn:inventory enregistré → canSend), et (2) la pref
            // sacoche est active (basculable par /rpinv). Sinon on NE draine PAS le clic
            // → Minecraft ouvre son inventaire VANILLA. C'est ce qui rend l'inventaire
            // vanilla sur le build / les serveurs sans plugin, et permet le retour vanilla.
            boolean sacoche = fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.sacocheInventory
                && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                        fr.reborn.hud.menu.inventory.InventoryPayload.ID);
            if (!sacoche) return;
            while (client.options.keyInventory.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new fr.reborn.hud.menu.inventory.InventoryPayload("open"));
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed() drain le queue d'events — false sur les frames
            // sans transition pressed.
            while (INTERACTION.consumeClick()) {
                InteractionMode.INSTANCE.toggle();
            }
            while (CINEMA.consumeClick()) {
                fr.reborn.hud.immersion.CinemaBars.INSTANCE.toggle();
            }
            while (PHOTO.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null) {
                    mc.setScreenAndShow(new fr.reborn.hud.ui.PhotoModeScreen());
                }
            }
            while (DASH.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.gui.screen() == null) {
                    fr.reborn.hud.combat.CombatInput.INSTANCE.dash(mc);
                }
            }
            while (ANIMATIONS.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.setScreenAndShow(new fr.reborn.hud.animation.AnimationMenuScreen(null));
                }
            }
            while (STATS.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    // Sur serveur : ShinobiCore pousse la fiche (open=true) → l'écran s'ouvre
                    // à réception. Solo/dev : écran direct sur la fiche de démonstration.
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            fr.reborn.hud.menu.stats.StatsPayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new fr.reborn.hud.menu.stats.StatsPayload("open"));
                    } else {
                        mc.setScreenAndShow(new fr.reborn.hud.menu.fiche.FicheScreen());
                    }
                }
            }
            while (DUEL_ACCEPT.consumeClick()) {
                if (Minecraft.getInstance().gui.screen() != null) continue;
                // Entrée : l'invitation radio d'abord (10 s), sinon le duel proposé.
                if (!fr.reborn.hud.radio.RadioClient.accepter()) fr.reborn.hud.duel.DuelClient.accept();
            }
            while (DUEL_REFUSE.consumeClick()) {
                if (Minecraft.getInstance().gui.screen() != null) continue;
                // Retour : refuse l'invitation radio, sinon le duel proposé, sinon ce qu'on me tend (nourriture, 14/10).
                if (fr.reborn.hud.radio.RadioClient.refuser()) continue;
                if (fr.reborn.hud.duel.DuelClient.hasInvite()) fr.reborn.hud.duel.DuelClient.refuse();
                else fr.reborn.hud.nourriture.NourritureClient.refuser();
            }
            tickRadio(Minecraft.getInstance());
            while (MAP.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() == null && mc.player != null) {
                    // Sur serveur : ShinobiCore pousse la carte (open=true) → l'écran s'ouvre
                    // à réception. Solo/dev : écran direct sur l'aperçu hors ligne.
                    if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            fr.reborn.hud.map.MapPayload.ID)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new fr.reborn.hud.map.MapPayload("open"));
                    } else {
                        mc.setScreenAndShow(new fr.reborn.hud.map.WorldMapScreen());
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
                boolean fl = FREELOOK.isDown() && mc.gui.screen() == null && mc.player != null;
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
