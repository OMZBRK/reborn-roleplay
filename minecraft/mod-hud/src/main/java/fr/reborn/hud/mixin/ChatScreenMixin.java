package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatPanel;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.EmojiPicker;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Branche l'écran de chat vanilla sur le {@link ChatPanel} : le champ de saisie est posé dans la barre du panneau
 * (son texte est redessiné par le panneau), la barre vanilla pleine largeur disparaît, et les clics, la molette et la
 * frappe passent d'abord par le panneau (onglets, recherche, poignée, mode /me, emoji, envoi, liens).
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$init(CallbackInfo ci) {
        if (input == null) return;
        if (!readStateSafely().visible()) input.visible = false;
        input.setTextColor(0x00000000); // texte redessiné par ChatPanel#drawInput
        ChatPanel.activeInput = input;
        EmojiPicker.onClose();
        reborn$placeInput();
    }

    /** Pose le champ vanilla sur la barre de saisie du panneau (coordonnées écran). */
    @Unique
    private void reborn$placeInput() {
        int[] r = ChatPanel.fieldRect();
        if (r[2] <= 0) {
            Minecraft mc = Minecraft.getInstance();
            var b = ChatPanel.bounds(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            r = new int[]{b.x() + 60, b.bottom() - 23, b.width() - 110, 16};
        }
        double[] p = HudTransform.toScreen(HudElement.CHAT, r[0], r[1]);
        input.setX((int) Math.round(p[0]));
        input.setY((int) Math.round(p[1]));
        input.setWidth(Math.max(10, r[2]));
        input.setHeight(r[3]);
    }

    /** La barre de saisie vanilla (fill pleine largeur en bas) est remplacée par celle du panneau. */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void reborn$dropInputBar(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (!(y2 >= sh - 2 && (y2 - y1) <= 16 && x2 >= sw - 4)) ctx.fill(x1, y1, x2, y2, color);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void reborn$beforeRender(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatPanel.tickDrag(mouseX, mouseY);
        if (input != null) reborn$placeInput();
    }

    /** Suggestions de commandes : remontées juste au-dessus de la barre du panneau. */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void reborn$liftSuggestions(CommandSuggestions suggestions, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int dy = input != null ? Math.min(0, input.getY() - 4 - (sh - 12)) : 0;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, dy);
        suggestions.extractRenderState(ctx, mouseX, mouseY - dy);
        ctx.pose().popMatrix();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$afterRender(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!readStateSafely().visible()) return;
        Minecraft mc = Minecraft.getInstance();
        ChatSettings settings;
        try {
            settings = RebornHudClient.config().getChatSettings();
        } catch (RuntimeException e) {
            settings = ChatSettings.defaults();
        }
        double[] loc = HudTransform.toLocal(HudElement.CHAT, mouseX, mouseY);
        HudTransform.apply(ctx, HudElement.CHAT);
        ChatPanel.drawInput(ctx, mc.font, input, settings);
        EmojiPicker.render(ctx, loc[0], loc[1]);
        reborn$hoverTooltip(ctx, mc, loc[0], loc[1]);
        HudTransform.revert(ctx);
    }

    /** Survol d'un lien tapé en clair : montre l'URL (affordance « cliquable »). */
    @Unique
    private void reborn$hoverTooltip(GuiGraphicsExtractor ctx, Minecraft mc, double lx, double ly) {
        String url = ChatPanel.urlAt(mc.font, lx, ly);
        if (url == null) return;
        String tip = "↳ " + url;
        int tw = mc.font.width(tip);
        int bx = (int) lx + 8, by = (int) ly - 14;
        ctx.fill(bx - 3, by - 2, bx + tw + 3, by + 10, 0xF014161B);
        ctx.fill(bx - 3, by - 2, bx + tw + 3, by - 1, 0xFFA0182B);
        ctx.text(mc.font, net.minecraft.network.chat.Component.literal(tip), bx, by, 0xFF7FB2FF, false);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reborn$click(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick,
                              CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double[] loc = HudTransform.toLocal(HudElement.CHAT, event.x(), event.y());
        double lx = loc[0], ly = loc[1];

        if (EmojiPicker.handleClick(lx, ly, s -> { if (input != null) input.insertText(s); })) {
            cir.setReturnValue(true);
            return;
        }
        var screen = (ChatScreen) (Object) this;
        boolean shift = (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        if (shift) {
            String line = ChatPanel.plainAt(ly);
            if (line != null && !line.isBlank()) {
                mc.keyboardHandler.setClipboard(line);
                if (mc.player != null) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[Reborn] Message copié dans le presse-papier."));
                }
                cir.setReturnValue(true);
                return;
            }
        } else {
            net.minecraft.network.chat.Style style = ChatPanel.styleAt(mc.font, lx, ly);
            if (style != null && style.getClickEvent() != null) {
                ScreenInvoker.reborn$defaultHandleGameClickEvent(style.getClickEvent(), mc, screen);
                cir.setReturnValue(true);
                return;
            }
            String url = ChatPanel.urlAt(mc.font, lx, ly);
            if (url != null) {
                try {
                    ScreenInvoker.reborn$clickUrlAction(mc, screen, new java.net.URI(url));
                    cir.setReturnValue(true);
                    return;
                } catch (java.net.URISyntaxException ignored) {
                    // URL malformée → on ignore.
                }
            }
        }
        if (ChatPanel.click(lx, ly, sw, sh)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void reborn$scroll(double mouseX, double mouseY, double scrollX, double scrollY,
                               CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        double[] loc = HudTransform.toLocal(HudElement.CHAT, mouseX, mouseY);
        if (ChatPanel.over(loc[0], loc[1], mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight())) {
            ChatPanel.scroll(scrollY);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void reborn$searchKeys(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
        if (ChatPanel.searchKey(event.key(), ctrl)) cir.setReturnValue(true);
    }

    /** Mode « /me » du panneau : préfixe les messages (pas les commandes). */
    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
    private String reborn$applyMode(String message) {
        return ChatPanel.applyMode(message);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void reborn$removed(CallbackInfo ci) {
        if (ChatPanel.activeInput == input) ChatPanel.activeInput = null;
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
