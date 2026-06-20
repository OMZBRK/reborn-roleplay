package fr.reborn.hud.runtime;

import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.MentionDetector;
import fr.reborn.hud.chat.MessageTimestamps;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;

import java.util.List;

/**
 * Rendu custom des messages chat. Remplace ENTIÈREMENT le rendu vanilla
 * (qui ajoutait un fond noir par ligne). Style Zenkai : texte clair avec
 * ombre, pas de background opaque sauf sur mention.
 *
 * <p>Quand chat ouvert : messages rendus dans la zone du panel custom,
 * cappé à la hauteur du panel pour ne pas déborder.
 * Quand chat fermé : messages "flottants" avec fade vanilla normal,
 * sans panel ni fond.
 */
public final class ChatMessageRenderer {

    /** Height d'une ligne y compris spacing. */
    private static final int LINE_H = 10;
    private static final int FADE_TICK = 200;

    private ChatMessageRenderer() {}

    public static void renderMessages(DrawContext ctx, TextRenderer tr,
                                       List<ChatHudLine.Visible> visibleMessages,
                                       int scrolledLines,
                                       int currentTick, boolean focused,
                                       int screenW, int screenH,
                                       ChatSettings settings, String playerName) {
        if (visibleMessages == null || visibleMessages.isEmpty()) return;

        HudElementBounds chat = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);

        // Capping : chat ferme → 1 ligne max (le dernier message).
        // Chat ouvert → ce qui rentre dans la zone du panel.
        int maxLines = focused ? Math.max(1, chat.height() / LINE_H) : 1;
        int bottomY = chat.bottom() - 2;
        int leftX = chat.x();

        int rendered = 0;
        for (int i = 0; rendered < maxLines && i + scrolledLines < visibleMessages.size(); i++) {
            int messageIdx = i + scrolledLines;
            ChatHudLine.Visible visible = visibleMessages.get(messageIdx);
            if (visible == null) continue;

            int age = currentTick - visible.addedTime();
            if (age >= FADE_TICK && !focused) continue;

            double opacity = focused ? 1.0 : getMessageOpacity(age);
            int alpha = (int) (255.0 * opacity);
            if (alpha < 8) continue;

            int lineY = bottomY - (rendered + 1) * LINE_H;
            if (lineY < chat.y()) break; // hard stop au top du chat area

            OrderedText content = visible.content();
            String plain = orderedToPlainString(content);

            boolean isMention = settings.highlightMentions && playerName != null
                && !playerName.isBlank()
                && MentionDetector.isMentioned(plain, playerName);

            int textX = leftX;

            // Mention highlight bg (subtle, accent soft)
            if (isMention) {
                int bgAlpha = Math.max(40, (alpha * 60) / 255);
                int bgColor = (bgAlpha << 24) | (RebornColors.ACCENT & 0x00FFFFFF);
                ctx.fill(leftX - 2, lineY - 1, leftX + chat.width() + 2, lineY + LINE_H - 1, bgColor);
            }

            // Timestamp prefix
            if (settings.showTimestamps) {
                String ts = "[" + MessageTimestamps.formattedFor(visible.addedTime()) + "] ";
                int tsColor = (alpha << 24) | (RebornColors.FOREGROUND_MUTED & 0x00FFFFFF);
                ctx.drawText(tr, ts, textX, lineY, tsColor, true);
                textX += tr.getWidth(ts);
            }

            // Text avec shadow vanilla via drawText(shadow=true)
            int textColor = (alpha << 24) | 0x00FFFFFF;
            ctx.drawText(tr, content, textX, lineY, textColor, true);

            rendered++;
        }
    }

    /** Fade vanilla : age 0-179 → 1.0 ; 180-199 → fade linéaire ; 200+ → 0. */
    private static double getMessageOpacity(int age) {
        if (age < 180) return 1.0;
        if (age >= FADE_TICK) return 0.0;
        return 1.0 - (age - 180) / 20.0;
    }

    private static String orderedToPlainString(OrderedText ordered) {
        StringBuilder sb = new StringBuilder();
        ordered.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
