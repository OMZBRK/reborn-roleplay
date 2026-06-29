package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link ChatHud#render}.
 *
 * <p>Le rendu chat custom (ancien panneau Zenkai + renderer léger avec offset)
 * a été ABANDONNÉ : il décollait les messages de la barre de saisie et cassait
 * le scroll. Le chat est désormais rendu <b>100% vanilla</b> (position en bas,
 * saisie attachée, scroll natif). On conserve uniquement le respect du toggle
 * de visibilité de l'élément CHAT (masquage via l'éditeur HUD).
 *
 * <p>Les features RP (blocage) sont greffées ailleurs de façon additive
 * (cf {@code ChatHudAddMessageMixin}). Têtes de joueurs + typing seront
 * ré-ajoutés en additif (mixin façon chat_heads) sans toucher au layout.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$chatVisibility(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                       boolean focused, CallbackInfo ci) {
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            ci.cancel(); // chat masqué via l'éditeur HUD
        }
        // Sinon : on laisse le rendu vanilla natif (aucun offset appliqué).
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
