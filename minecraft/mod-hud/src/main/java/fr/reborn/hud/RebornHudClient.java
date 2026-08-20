package fr.reborn.hud;

import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.widget.DynamicPlayerBackground;
import fr.reborn.hud.ui.style.IconTextures;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entree client du mod Reborn HUD.
 *
 * <p>Init :
 * <ol>
 *   <li>Charge {@link HudConfig} (positions des elements HUD vanilla,
 *       presets, prefs chat) depuis {@code config/reborn-hud.json}.</li>
 *   <li>Enregistre les textures du pack {@code reborn-hud} (icones de
 *       l'editeur, ressources UI).</li>
 *   <li>Enregistre les keybindings (touche H = editeur HUD par defaut).</li>
 *   <li>Schedule la creation du browser MCEF qui rendra le background
 *       dynamique (skin 3D du joueur) du main menu. Le browser est cree
 *       une fois CEF initialise (~1-2s apres le boot du client).</li>
 * </ol>
 *
 * <p>Le singleton static {@link #config()} est expose parce que les
 * Mixins ont besoin de lire les offsets au moment du render, depuis
 * du code qui ne peut pas passer par DI.
 */
public final class RebornHudClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud");

    private static HudConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = HudConfig.load();
        LOGGER.info("config loaded");

        IconTextures.registerAll();
        HudKeybinds.registerClient();
        fr.reborn.hud.camera.RebornCamera.INSTANCE.loadFromPrefs();
        // First-Person Model (tr7zw) piloté par la caméra Reborn : neutralise sa
        // touche de bascule dédiée pour que seule la caméra Reborn (Y) contrôle
        // la 1ère personne avec corps. No-op si le mod est absent.
        fr.reborn.hud.camera.FirstPersonIntegration.registerClient();
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.register();
        fr.reborn.hud.combat.CombatAnimations.INSTANCE.register();
        fr.reborn.hud.chat.ChatBlockCommands.register();
        fr.reborn.hud.skin.SkinCommands.register();

        // Overlay du menu d'interaction live (rendu HUD, pas un écran).
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "interaction"),
            (ctx, tickCounter) -> fr.reborn.hud.interaction.InteractionMode.INSTANCE.extractRenderState(ctx));

        // Panneau RP vitals (tête + vie + chakra + faim) — repositionnable/scalable
        // via l'élément HudElement.VITALS (comme les autres, dans l'éditeur).
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "vitals"),
            (ctx, tickCounter) -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
                fr.reborn.hud.element.HudElementState st;
                try { st = config().stateOf(fr.reborn.hud.element.HudElement.VITALS); }
                catch (RuntimeException e) { return; }
                if (!st.visible()) return;
                int w = mc.getWindow().getGuiScaledWidth();
                int h = mc.getWindow().getGuiScaledHeight();
                fr.reborn.hud.element.HudElementBounds b = fr.reborn.hud.element.HudElementBounds.currentFor(
                    fr.reborn.hud.element.HudElement.VITALS, st, w, h);
                fr.reborn.hud.runtime.VitalsHud.render(ctx, mc, b.x(), b.y(), st.scale());
            });

        // Overlay tablist en mode Hold (maintenir la touche liste-joueurs).
        // Rendu HUD fiable — même en solo où Minecraft ne rend PAS le
        // PlayerListHud vanilla (1 seul joueur, pas d'objectif scoreboard).
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "tablist-hold"),
            (ctx, tickCounter) -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.gui.screen() != null || mc.options == null) return;
            if (!fr.reborn.hud.menu.settings.RebornPrefs.INSTANCE.tablistHold) return;
            if (!mc.options.keyPlayerList.isDown()) return;
            fr.reborn.hud.menu.tablist.TablistScreen.renderHoldOverlay(ctx);
        });

        // Réception du tablist serveur (canal reborn:tablist depuis ShinobiCore).
        // Le mod ne fait que rendre ; les données sont server-authoritative.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
            fr.reborn.hud.menu.tablist.TablistPayload.ID,
            fr.reborn.hud.menu.tablist.TablistPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            fr.reborn.hud.menu.tablist.TablistPayload.ID,
            (payload, context) -> context.client().execute(
                () -> fr.reborn.hud.menu.tablist.TablistData.update(payload.json())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.menu.tablist.TablistData.clear());

        // Course chakraïque (« Naruto run ») : canal C2S reborn:naruto — le client
        // informe le plugin (ShinobiCore) quand le joueur (dés)active sa course
        // pour l'activer IG. Le mouvement client, lui, marche sans le serveur.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
            fr.reborn.hud.animation.NarutoRunPayload.ID,
            fr.reborn.hud.animation.NarutoRunPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.animation.NarutoRun.INSTANCE.reset());

        // Synchro des démarches ENTRE JOUEURS (canal reborn:anim, bidirectionnel avec
        // ShinobiAbilities). C2S : {uuid,state,walk} quand notre état change (+ heartbeat).
        // S2C : le relais rediffuse l'état des joueurs proches → on anime leurs avatars.
        // Les états observables (marche/course/saut) jouent même sans le plugin.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
            fr.reborn.hud.animation.AnimSyncPayload.ID, fr.reborn.hud.animation.AnimSyncPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
            fr.reborn.hud.animation.AnimSyncPayload.ID, fr.reborn.hud.animation.AnimSyncPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            fr.reborn.hud.animation.AnimSyncPayload.ID,
            (payload, context) -> context.client().execute(() ->
                fr.reborn.hud.animation.MovementAnimations.INSTANCE.onRemoteState(
                    payload.uuid(), payload.state(), payload.walk())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.animation.MovementAnimations.INSTANCE.clearRemote());

        // Sélection / création de personnage (canal reborn:character, bidirectionnel
        // avec ShinobiCore). S2C : roster du joueur ({slotLimit, characters[],
        // candidature}) → ouvre l'écran de sélection Zenkai. C2S : select:<id> /
        // create / create\n<...> / open (envoyés par les écrans). Le mod ne fait
        // que l'UI ; ShinobiCore est autoritaire (setActive, création, limites).
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
            fr.reborn.hud.menu.character.CharacterPayload.ID,
            fr.reborn.hud.menu.character.CharacterPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
            fr.reborn.hud.menu.character.CharacterPayload.ID,
            fr.reborn.hud.menu.character.CharacterPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            fr.reborn.hud.menu.character.CharacterPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                fr.reborn.hud.menu.character.CharacterData.update(payload.content());
                net.minecraft.client.Minecraft mc = context.client();
                if (mc.gui.screen() == null && mc.player != null) {
                    mc.setScreenAndShow(new fr.reborn.hud.menu.character.CharacterSelectScreen());
                }
            }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.menu.character.CharacterData.clear());

        // Diffusion des apparences RP (canal reborn:skins, S2C) : ShinobiCore pousse
        // <uuid>\n<serialize()> pour chaque joueur actif → chacun voit le skin composé
        // des autres. Apparence vide = retrait de l'override.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
            fr.reborn.hud.skin.SkinPayload.ID, fr.reborn.hud.skin.SkinPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            fr.reborn.hud.skin.SkinPayload.ID,
            (payload, context) -> context.client().execute(() ->
                fr.reborn.hud.skin.RebornSkins.applyBroadcast(payload.content())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.skin.RebornSkins.clearAll());

        // Voix Reborn (PlasmoVoice) : addon client qui capte l'instance PV pour
        // savoir qui parle → bulle de parole au-dessus des têtes. Se désactive
        // proprement si PlasmoVoice n'est pas dans le modpack.
        try {
            su.plo.voice.api.client.PlasmoVoiceClient.getAddonsLoader()
                .load(new fr.reborn.hud.voice.RebornVoiceAddon());
            LOGGER.info("addon voix Reborn enregistré (PlasmoVoice)");
        } catch (Throwable t) {
            LOGGER.warn("PlasmoVoice absent — indicateur voix désactivé ({})", t.toString());
        }

        // Indicateur de frappe (canal reborn:typing, bidirectionnel avec ShinobiCore).
        // C2S : "1"/"0" quand le joueur ouvre/ferme le chat. S2C : liste des joueurs
        // proches en train d'écrire → 3 points au-dessus de leur tête.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
            fr.reborn.hud.chat.TypingPayload.ID, fr.reborn.hud.chat.TypingPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
            fr.reborn.hud.chat.TypingPayload.ID, fr.reborn.hud.chat.TypingPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            fr.reborn.hud.chat.TypingPayload.ID,
            (payload, context) -> context.client().execute(
                () -> fr.reborn.hud.chat.TypingState.update(payload.content())));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.chat.TypingState.clear());

        // Bulles au-dessus des têtes : voix (1 point, si PlasmoVoice présent) +
        // frappe chat (3 points). Toujours enregistré ; la partie voix no-op sans PV.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "head-bubbles"),
            (ctx, tickCounter) -> fr.reborn.hud.voice.SpeechBubbles.render(ctx));

        // Envoie l'état de frappe au serveur quand on ouvre/ferme le chat (C2S).
        final boolean[] wasChatOpen = {false};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean open = client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
            if (open != wasChatOpen[0]) {
                wasChatOpen[0] = open;
                fr.reborn.hud.chat.TypingState.sendTyping(open);
            }
        });

        // Bandes noires cinéma (immersion) — toggle touche K.
        fr.reborn.hud.immersion.CinemaBars.INSTANCE.registerClient();

        // Preview auto après capture d'écran.
        fr.reborn.hud.screenshot.CapturePreview.INSTANCE.registerClient();

        // Désactive le narrateur Minecraft (demande user) — une fois, quand les
        // options sont prêtes.
        final boolean[] narratorDone = {false};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (narratorDone[0] || client.options == null) return;
            narratorDone[0] = true;
            if (client.options.narrator().get() != net.minecraft.client.NarratorStatus.OFF) {
                client.options.narrator().set(net.minecraft.client.NarratorStatus.OFF);
                client.options.save();
                LOGGER.info("narrateur Minecraft désactivé");
            }
        });

        // SPLASH → MENU : SEULEMENT une touche clavier sur le TitleScreen fait
        // passer l'ecran d'accroche au menu (pas la souris — demande user : le
        // clic ne doit PAS fermer le splash). On passe par le Screen API Fabric
        // parce qu'un Mixin sur TitleScreen ne peut pas hooker keyPressed (herite
        // de Screen, non redeclare par TitleScreen).
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
            (client, screen, scaledW, scaledH) -> {
                if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
                net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
                    .afterKeyPress(screen)
                    .register((scr, keyEvent) ->
                        fr.reborn.hud.menu.MainMenuFlow.advanceFromSplash());
            });

        // Combat taïjutsu (canal reborn:combat, S2C depuis ShinobiCombat).
        // HIT → damage indicator + cumul combo ; STAMINA → anneau curseur.
        // Purement visuel ; le serveur reste autoritaire.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
            fr.reborn.hud.combat.CombatPayload.ID, fr.reborn.hud.combat.CombatPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            fr.reborn.hud.combat.CombatPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                long now = System.currentTimeMillis();
                if (payload.msgType() == fr.reborn.hud.combat.CombatPayload.TYPE_HIT) {
                    fr.reborn.hud.combat.CombatState.INSTANCE.onHit(
                        payload.victimEntityId(), payload.damage(), now);
                } else if (payload.msgType() == fr.reborn.hud.combat.CombatPayload.TYPE_STAMINA) {
                    fr.reborn.hud.combat.CombatState.INSTANCE.onStamina(
                        payload.staminaCurrent(), payload.staminaMax(), now);
                } else if (payload.msgType() == fr.reborn.hud.combat.CombatPayload.TYPE_ANIM) {
                    int eid = payload.victimEntityId();     // = attackerEntityId pour TYPE_ANIM
                    int animId = payload.animId();
                    // Garde (5/6) du joueur LOCAL : déjà jouée par CombatInput → on ignore
                    // le broadcast pour soi (anti-double). Les coups (1-4) sur soi, eux, viennent
                    // du serveur (pas de trigger local M1).
                    net.minecraft.client.player.LocalPlayer lp = context.client().player;
                    boolean selfBlock = lp != null && eid == lp.getId() && animId >= 5;
                    if (!selfBlock) fr.reborn.hud.combat.CombatAnimations.INSTANCE.playByEntityId(eid, animId);
                }
            }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.combat.CombatState.INSTANCE.clear());
        // Input de garde M2 (C2S reborn:combatin) — ShinobiCombat applique la réduction
        // + rediffuse l'anim. Inerte via canSend si le plugin est absent.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
            fr.reborn.hud.combat.CombatInputPayload.ID, fr.reborn.hud.combat.CombatInputPayload.CODEC);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> fr.reborn.hud.combat.CombatInput.INSTANCE.reset());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "combat"),
            (ctx, tickCounter) -> fr.reborn.hud.combat.CombatHud.render(ctx));

        // Extrait les assets dynamic-player + schedule la creation du
        // browser MCEF pour le main menu background.
        DynamicPlayerBackground.init();

        LOGGER.info("Reborn HUD mod ready.");
    }

    public static HudConfig config() {
        if (CONFIG == null) {
            throw new IllegalStateException("HudConfig not initialized yet");
        }
        return CONFIG;
    }
}
