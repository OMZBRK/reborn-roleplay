package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.EmojiPicker;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        EmojiPicker.close(); // picker fermé à chaque ouverture du chat
    }

    // Dessine le bouton emoji + le picker par-dessus l'écran de chat.
    @Inject(method = "render", at = @At("TAIL"))
    private void reborn$renderEmoji(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        EmojiPicker.render(ctx, mouseX, mouseY,
            mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }

    // Intercepte les clics sur le bouton/picker avant le reste de l'écran.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reborn$clickEmoji(double mx, double my, int button,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean handled = EmojiPicker.handleClick(mx, my,
            mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
            s -> { if (chatField != null) chatField.write(s); });
        if (handled) cir.setReturnValue(true);
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
