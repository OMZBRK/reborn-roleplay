package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatLayout;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.EmojiPicker;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin sur {@link ChatScreen}.
 *
 * <p>La barre de saisie reste en bas mais est rétrécie à la <b>largeur du chat</b>
 * (façon Paladium) au lieu de toute la page, avec le <b>bouton emoji</b> à sa
 * droite. On masque le champ si l'élément CHAT est caché via l'éditeur HUD.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$chatFieldVisibility(CallbackInfo ci) {
        if (input == null) return;
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            input.visible = false;
        }
        // Rétrécit la saisie à la largeur du chat (laisse la place au bouton emoji).
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        input.setX(ChatLayout.TEXT_X);
        input.setWidth(ChatLayout.inputW(sw));
        // Texte animé à la saisie : on masque le texte vanilla (alpha 0) et on le
        // redessine char-par-char animé au TAIL. Sinon couleur normale.
        try {
            input.setTextColor(RebornHudClient.config().getChatSettings().animatedTyping ? 0x00000000 : 0xFFE0E0E0);
        } catch (RuntimeException ignored) {}
        reborn$lastInput = "";
        EmojiPicker.onClose(); // picker fermé à chaque ouverture du chat
    }

    @org.spongepowered.asm.mixin.Unique private String reborn$lastInput = "";
    @org.spongepowered.asm.mixin.Unique private long reborn$lastCharMs = 0L;

    /**
     * La barre de saisie vanilla est un fill pleine largeur tout en bas. On le
     * remplace par une barre à la largeur du chat, stylée Reborn.
     */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void reborn$narrowInputBar(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (y2 >= sh - 2 && (y2 - y1) <= 16 && x2 >= sw - 4) {
            int bw = ChatLayout.boxW(sw);
            ctx.fill(ChatLayout.LEFT, y1, ChatLayout.LEFT + bw, y2, 0xC00A0608);
            ctx.fill(ChatLayout.LEFT, y1, ChatLayout.LEFT + bw, y1 + 1,
                Colors.withAlpha(Colors.ACCENT, 0.5f));
        } else {
            ctx.fill(x1, y1, x2, y2, color);
        }
    }

    // Dessine le bouton emoji + le picker par-dessus l'écran de chat.
    // 26.1 : ChatScreen#render → extractRenderState(GuiGraphicsExtractor, int, int, float).
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$renderEmoji(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        EmojiPicker.render(ctx, mouseX, mouseY,
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        reborn$animatedText(ctx, mc);
        reborn$hoverTooltip(ctx, mc, mouseX, mouseY);
    }

    /** Tooltip de survol : montre l'URL sous le curseur (affordance « cliquable »). */
    private void reborn$hoverTooltip(GuiGraphicsExtractor ctx, Minecraft mc, int mouseX, int mouseY) {
        try {
            ChatSettings settings = RebornHudClient.config().getChatSettings();
            var acc = (ChatComponentAccessor) (Object) mc.gui.hud.getChat();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            double[] loc = fr.reborn.hud.runtime.HudTransform.toLocal(HudElement.CHAT, mouseX, mouseY);
            String url = fr.reborn.hud.runtime.ChatMessageRenderer.urlAt(
                mc, mc.font, acc.reborn$trimmedMessages(), acc.reborn$chatScrollbarPos(),
                sw, sh, settings, loc[0], loc[1], 0, 0);
            if (url == null) return;
            var font = mc.font;
            String tip = "↳ " + url; // ↳ URL
            int tw = font.width(tip);
            int bx = Math.min(mouseX + 8, sw - tw - 8);
            int by = Math.max(2, mouseY - 14);
            ctx.fill(bx - 3, by - 2, bx + tw + 3, by + 10, 0xE00A0608);
            ctx.fill(bx - 3, by - 2, bx + tw + 3, by - 1, Colors.withAlpha(Colors.ACCENT, 0.7f));
            ctx.text(font, net.minecraft.network.chat.Component.literal(tip), bx, by, 0xFF7FB2FF, false);
        } catch (Throwable ignored) {
            // hit-testing indisponible → pas de tooltip.
        }
    }

    /**
     * Texte de saisie <b>animé caractère par caractère</b> (façon Animated Typing
     * de Pa-dej) : le texte vanilla est masqué (alpha 0 en init) et redessiné ici,
     * avec une animation d'entrée sur le <b>dernier caractère tapé</b>. Styles :
     * 0 = grossir, 1 = fondu, 2 = glisser. Approx : détecte l'ajout en fin de texte.
     */
    private void reborn$animatedText(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (input == null || !input.isFocused() || mc.font == null) return;
        ChatSettings s;
        try {
            s = RebornHudClient.config().getChatSettings();
        } catch (RuntimeException e) {
            return;
        }
        if (!s.animatedTyping) return;

        net.minecraft.client.gui.Font font = mc.font;
        String val = input.getValue();
        long now = System.currentTimeMillis();
        if (val.length() > reborn$lastInput.length() && val.startsWith(reborn$lastInput)) {
            reborn$lastCharMs = now; // un caractère vient d'être ajouté en fin
        }
        reborn$lastInput = val;
        if (val.isEmpty()) return;

        final int ANIM = 150;
        final int white = 0xFFE6E6E6;
        int ty = input.getY() + (input.getHeight() - 8) / 2;
        // Clip + défilement horizontal : garde le texte DANS la barre et le curseur
        // visible quand la saisie dépasse la largeur (sinon le texte débordait).
        int visLeft = input.getX() + 4;
        int visRight = input.getX() + input.getWidth() - 2;
        int visW = Math.max(1, visRight - visLeft);
        int caret = Math.max(0, Math.min(input.getCursorPosition(), val.length()));
        int wToCaret = font.width(val.substring(0, caret));
        int scroll = Math.max(0, wToCaret - visW + 6);
        ctx.enableScissor(visLeft, ty - 2, visRight, ty + 10);
        int tx = visLeft - scroll;

        for (int i = 0; i < val.length(); i++) {
            var ch = net.minecraft.network.chat.Component.literal(String.valueOf(val.charAt(i)));
            int cw = font.width(ch);
            long age = now - reborn$lastCharMs;
            if (i == val.length() - 1 && age < ANIM) {
                float t = age / (float) ANIM;
                float ease = 1f - (1f - t) * (1f - t);
                int a = Math.min(255, (int) (ease * 220) + 35);
                int col = (a << 24) | (white & 0x00FFFFFF);
                switch (s.typingCursorStyle) {
                    case 1 -> ctx.text(font, ch, tx, ty, col, false);                       // fondu
                    case 2 -> ctx.text(font, ch, tx, ty + Math.round((1f - ease) * 4), col, false); // glisser
                    default -> {                                                            // grossir
                        float sc = 0.35f + 0.65f * ease;
                        ctx.pose().pushMatrix();
                        ctx.pose().translate(tx + cw / 2f, ty + 4f);
                        ctx.pose().scale(sc, sc);
                        ctx.text(font, ch, -cw / 2, -4, col, false);
                        ctx.pose().popMatrix();
                    }
                }
            } else {
                ctx.text(font, ch, tx, ty, white, false);
            }
            tx += cw;
        }

        // Curseur vanilla (masqué par l'alpha 0 du texte) redessiné : blink ~500ms,
        // « _ » en fin de saisie, « | » au milieu — comme le chat normal.
        boolean blink = (now / 500) % 2 == 0;
        if (blink) {
            int cxCaret = visLeft - scroll + wToCaret;
            if (caret >= val.length()) {
                ctx.text(font, net.minecraft.network.chat.Component.literal("_"), cxCaret, ty, white, false);
            } else {
                ctx.fill(cxCaret, ty - 1, cxCaret + 1, ty + 9, 0xFFD0D0D0);
            }
        }
        ctx.disableScissor();
    }

    // Intercepte les clics sur le bouton/picker avant le reste de l'écran.
    // 26.1 : Screen#mouseClicked(double,double,int) -> mouseClicked(MouseButtonEvent, boolean).
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reborn$clickEmoji(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean handled = EmojiPicker.handleClick(event.x(), event.y(),
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
            s -> { if (input != null) input.insertText(s); });
        if (handled) { cir.setReturnValue(true); return; }

        // Interactivité chat : clic sur un composant (lien / copie / commande), ou
        // SHIFT-clic pour copier la ligne. Hit-testing sur NOTRE géométrie de rendu
        // (le rendu vanilla est annulé → getClickedComponentStyleAt ne marche plus).
        try {
            ChatSettings settings = RebornHudClient.config().getChatSettings();
            var acc = (ChatComponentAccessor) (Object) mc.gui.hud.getChat();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            // Coordonnées LOCALES du chat (inverse offset + scale) → hit-testing
            // aligné même si le chat est déplacé/redimensionné dans l'éditeur.
            double[] loc = fr.reborn.hud.runtime.HudTransform.toLocal(HudElement.CHAT, event.x(), event.y());
            double lx = loc[0], ly = loc[1];
            boolean shift = (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
            if (shift) {
                String line = fr.reborn.hud.runtime.ChatMessageRenderer.lineTextAt(
                    mc, mc.font, acc.reborn$trimmedMessages(), acc.reborn$chatScrollbarPos(),
                    sw, sh, settings, lx, ly, 0, 0);
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
                var screen = (net.minecraft.client.gui.screens.ChatScreen) (Object) this;
                // 1) Composant cliquable envoyé par le serveur (ClickEvent).
                net.minecraft.network.chat.Style style = fr.reborn.hud.runtime.ChatMessageRenderer.styleAt(
                    mc, mc.font, acc.reborn$trimmedMessages(), acc.reborn$chatScrollbarPos(),
                    sw, sh, settings, lx, ly, 0, 0);
                if (style != null && style.getClickEvent() != null) {
                    ScreenInvoker.reborn$defaultHandleGameClickEvent(style.getClickEvent(), mc, screen);
                    cir.setReturnValue(true);
                    return;
                }
                // 2) URL tapée en clair (pas de ClickEvent → auto-détection).
                String url = fr.reborn.hud.runtime.ChatMessageRenderer.urlAt(
                    mc, mc.font, acc.reborn$trimmedMessages(), acc.reborn$chatScrollbarPos(),
                    sw, sh, settings, lx, ly, 0, 0);
                if (url != null) {
                    try {
                        ScreenInvoker.reborn$clickUrlAction(mc, screen, new java.net.URI(url));
                        cir.setReturnValue(true);
                    } catch (java.net.URISyntaxException ignored) {
                        // URL malformée → on ignore.
                    }
                }
            }
        } catch (Throwable ignored) {
            // hit-testing indisponible → on laisse le clic suivre son cours normal.
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
