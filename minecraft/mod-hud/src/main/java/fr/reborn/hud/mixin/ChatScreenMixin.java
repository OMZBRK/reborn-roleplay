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
 * Mixin sur {@link ChatScreen#init} : décale le champ d'input à la même
 * position que le chat history (qui est lui-même décalé via
 * {@link ChatHudMixin}). Sans ça, l'input box restait à sa position
 * vanilla en bas pendant que le chat se déplaçait.
 *
 * <p>On modifie x/y du widget directement (vs un matrix push/pop dans
 * render) pour que le hitbox du clic suive aussi le visuel — sinon le
 * curseur "rate" la barre quand elle est repositionnée.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$shiftChatField(CallbackInfo ci) {
        if (chatField == null) return;
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            // Si l'utilisateur a cache le chat, on cache aussi l'input.
            // Comportement coherent : chat invisible = pas de chat IG.
            chatField.visible = false;
            return;
        }
        if (state.x() == 0 && state.y() == 0) return;
        chatField.setX(chatField.getX() + state.x());
        chatField.setY(chatField.getY() + state.y());
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
