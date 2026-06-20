package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.RebornChatRenderer;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin sur {@link ChatScreen} :
 * <ul>
 *   <li>{@code init} : positionne le {@code chatField} dans la zone
 *       d'input du panel custom Zenkai, en appliquant l'offset + scale +
 *       anchor du HUD-mover.</li>
 *   <li>{@code mouseClicked} : intercepte le click sur le pill /me en
 *       appliquant l'inverse-transform aux coords souris.</li>
 * </ul>
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$positionChatField(CallbackInfo ci) {
        if (chatField == null) return;
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            chatField.visible = false;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int[] vanillaRect = RebornChatRenderer.inputFieldRect(mc.textRenderer, screenW, screenH);

        // Applique l'anchor + scale + offset comme la matrix le fait pour le panel
        HudElementBounds anchor = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);
        float scale = state.scale();
        int scaledX = anchor.x() + Math.round((vanillaRect[0] - anchor.x()) * scale);
        int scaledY = anchor.y() + Math.round((vanillaRect[1] - anchor.y()) * scale);
        int scaledW = Math.round(vanillaRect[2] * scale);

        chatField.setX(scaledX + state.x());
        chatField.setY(scaledY + state.y());
        chatField.setWidth(Math.max(20, scaledW));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reborn$interceptClicks(double mouseX, double mouseY, int button,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        // Inverse-transform : on enleve offset + on dezoom autour de l'anchor.
        HudElementState state = readStateSafely();
        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        HudElementBounds anchor = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);

        double mx = mouseX - state.x();
        double my = mouseY - state.y();
        if (state.scale() != 1.0f) {
            mx = anchor.x() + (mx - anchor.x()) / state.scale();
            my = anchor.y() + (my - anchor.y()) / state.scale();
        }

        // Click sur pill /me → insère "/me " dans le chatField
        if (RebornChatRenderer.isCommandPillClick(mx, my)) {
            if (chatField != null) {
                String current = chatField.getText();
                if (current.isEmpty() || !current.startsWith("/")) {
                    chatField.setText("/me ");
                    chatField.setCursorToEnd(false);
                }
            }
            cir.setReturnValue(true);
        }
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
