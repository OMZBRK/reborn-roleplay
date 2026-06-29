package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link ChatScreen}.
 *
 * <p>On NE déplace plus la barre de saisie (elle reste à sa position vanilla
 * normale — la longue barre noire en bas). On masque seulement le champ si
 * l'élément CHAT est caché via l'éditeur HUD.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$chatFieldVisibility(CallbackInfo ci) {
        if (chatField == null) return;
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            chatField.visible = false;
        }
        // Sinon : on laisse la saisie à sa position vanilla (aucun déplacement).
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
