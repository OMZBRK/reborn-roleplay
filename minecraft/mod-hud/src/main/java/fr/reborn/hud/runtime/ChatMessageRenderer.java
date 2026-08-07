package fr.reborn.hud.runtime;

import fr.reborn.hud.chat.ChatBlockList;
import fr.reborn.hud.chat.ChatLayout;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.MentionDetector;
import fr.reborn.hud.chat.MessageTimestamps;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Rendu léger des messages chat (refonte RP). Remplace le rendu vanilla
 * (fond noir par ligne) par un rendu clair texte+ombre, et y greffe les
 * features RP :
 * <ul>
 *   <li><b>Têtes de joueurs</b> (façon chat_heads) : l'expéditeur est
 *       identifié en cherchant le premier pseudo en ligne présent dans le
 *       texte, et sa tête est dessinée devant le message.</li>
 *   <li><b>Blocage</b> : les messages d'un joueur de la {@link ChatBlockList}
 *       sont masqués.</li>
 *   <li><b>Typing</b> : effet machine à écrire sur le dernier message reçu.</li>
 * </ul>
 */
public final class ChatMessageRenderer {

    private static final int LINE_H = 10;
    private static final int FADE_TICK = 200;
    private static final int HEAD = 8;
    private static final int HEAD_GAP = 2;

    // ─── Animation d'arrivée (slide + fade, façon ChatAnimation) ───
    private static final int ANIM_MS = 220;
    private static long animStartMs = 0L;
    private static int lastTopAdded = Integer.MIN_VALUE;

    private ChatMessageRenderer() {}

    public static void renderMessages(GuiGraphicsExtractor ctx, Font tr,
                                       List<GuiMessage.Line> visibleMessages,
                                       int scrolledLines,
                                       int currentTick, boolean focused,
                                       int screenW, int screenH,
                                       ChatSettings settings, String playerName) {
        if (visibleMessages == null || visibleMessages.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();

        // Géométrie vanilla : messages ancrés en bas, juste au-dessus de la
        // barre de saisie (à screenH-40 comme vanilla), à gauche. Le scroll
        // vanilla (scrolledLines) est respecté.
        int maxLines = focused ? 18 : 10;
        int bottomY = screenH - 40;
        int leftX = ChatLayout.TEXT_X;
        int areaW = ChatLayout.areaW(screenW);
        int boxW = ChatLayout.boxW(screenW);

        // Animation d'arrivée : à chaque nouveau message (top line addedTime
        // change), les lignes glissent vers le haut (slide) et le plus récent
        // apparaît en fondu (fade). Basé sur l'horloge murale (rendu, pas tick).
        float slide = 0f;
        float newFade = 1f;
        if (settings.chatAnimation) {
            GuiMessage.Line top = visibleMessages.get(0);
            int topAdded = top != null ? top.addedTime() : lastTopAdded;
            if (topAdded != lastTopAdded) {
                lastTopAdded = topAdded;
                animStartMs = System.currentTimeMillis();
            }
            float t = Math.min(1f, Math.max(0f, (System.currentTimeMillis() - animStartMs) / (float) ANIM_MS));
            float ease = 1f - (1f - t) * (1f - t);   // ease-out quad
            slide = (1f - ease) * LINE_H;
            newFade = ease;
        }
        int slideY = Math.round(slide);

        // Clippe horizontalement à la largeur du panneau : vanilla wrappe les
        // lignes à SA largeur (souvent plus large) → sans ça, un long mot déborde
        // du cadre. (offset X du chat = 0 par défaut, donc le scissor est aligné.)
        ctx.enableScissor(ChatLayout.LEFT, 0, ChatLayout.LEFT + boxW, screenH);

        // Panneau sombre carré (façon Paladium) derrière les messages quand le
        // chat est ouvert. MÊME largeur que la barre de saisie (boxW) → aligné,
        // et assez large pour tête + ligne pleine (pas de débordement).
        if (focused) {
            int avail = Math.max(1, Math.min(maxLines, visibleMessages.size() - scrolledLines));
            int panelTop = bottomY - avail * LINE_H - 4;
            int px = ChatLayout.LEFT;
            int pbottom = bottomY + 1;
            ctx.fill(px, panelTop, px + boxW, pbottom, 0xC00A0608);
            ctx.fill(px, panelTop, px + boxW, panelTop + 1, Colors.ACCENT);
            ctx.fill(px, panelTop + 1, px + boxW, panelTop + 2, Colors.withAlpha(Colors.ACCENT, 0.55f));
            ctx.fill(px, panelTop + 2, px + boxW, panelTop + 3, Colors.withAlpha(Colors.ACCENT, 0.22f));
            ctx.fill(px, pbottom - 1, px + boxW, pbottom, Colors.withAlpha(Colors.ACCENT, 0.3f));
        }

        int rendered = 0;
        for (int i = 0; rendered < maxLines && i + scrolledLines < visibleMessages.size(); i++) {
            int messageIdx = i + scrolledLines;
            GuiMessage.Line visible = visibleMessages.get(messageIdx);
            if (visible == null) continue;

            int age = currentTick - visible.addedTime();
            if (age >= FADE_TICK && !focused) continue;

            double opacity = focused ? 1.0 : getMessageOpacity(age);
            int alpha = (int) (255.0 * opacity);
            boolean isNewest = messageIdx == 0;
            if (isNewest) alpha = Math.max(1, (int) (alpha * newFade)); // fondu du plus récent
            else if (alpha < 8) continue;

            FormattedCharSequence content = visible.content();
            String plain = orderedToPlainString(content);

            // Expéditeur (pour tête + blocage) : premier pseudo en ligne du texte.
            PlayerInfo sender = findSender(mc, plain);
            if (sender != null && ChatBlockList.INSTANCE.isBlocked(sender.getProfile().name())) {
                continue; // message masqué — ne consomme pas de ligne
            }

            int lineY = bottomY - (rendered + 1) * LINE_H + slideY;
            if (lineY < 4) break;

            int textX = leftX;

            // Tête du joueur (chat_heads).
            boolean drewHead = false;
            if (settings.chatHeads && sender != null) {
                Identifier skin = sender.getSkin().body().texturePath();
                drawHead(ctx, skin, leftX, lineY - 1, alpha);
                textX += HEAD + HEAD_GAP;
                drewHead = true;
            }

            // Badge de rang : préfixe d'équipe scoreboard (rempli par le serveur
            // depuis LuckPerms). Rien à afficher si le serveur ne le fournit pas.
            if (settings.chatBadges && sender != null && mc != null && mc.level != null) {
                var sb = mc.level.getScoreboard();
                var team = sb != null ? sb.getPlayersTeam(sender.getProfile().name()) : null;
                if (team != null) {
                    var prefix = team.getPlayerPrefix();
                    if (prefix != null && !prefix.getString().isEmpty()) {
                        ctx.text(tr, prefix, textX, lineY, (alpha << 24) | 0x00FFFFFF, true);
                        textX += tr.width(prefix) + 2;
                    }
                }
            }

            boolean isMention = settings.highlightMentions && playerName != null
                && !playerName.isBlank()
                && MentionDetector.isMentioned(plain, playerName);
            if (isMention) {
                int bgAlpha = Math.max(40, (alpha * 60) / 255);
                int bgColor = (bgAlpha << 24) | (settings.highlightColor & 0x00FFFFFF);
                ctx.fill(textX - 2, lineY - 1, ChatLayout.LEFT + boxW - 2, lineY + LINE_H - 1, bgColor);
            }

            if (settings.showTimestamps) {
                String ts = "[" + MessageTimestamps.formattedFor(visible.addedTime()) + "] ";
                int tsColor = (alpha << 24) | (RebornColors.FOREGROUND_MUTED & 0x00FFFFFF);
                ctx.text(tr, ts, textX, lineY, tsColor, true);
                textX += tr.width(ts);
            }

            // Typing : effet machine à écrire sur LE message le plus récent
            // (messageIdx 0) tant qu'il est frais.
            FormattedCharSequence drawContent = content;
            if (settings.chatTyping && messageIdx == 0 && !plain.isEmpty()) {
                int revealTicks = Math.min(40, Math.max(6, plain.length()));
                float prog = age / (float) revealTicks;
                if (prog < 1f) {
                    int n = Math.max(1, Math.round(prog * plain.length()));
                    drawContent = truncate(content, n);
                }
            }

            int textColor = (alpha << 24) | 0x00FFFFFF;
            ctx.text(tr, drawContent, textX, lineY, textColor, true);
            if (drewHead) { /* tête déjà dessinée, rien à finaliser */ }

            rendered++;
        }

        ctx.disableScissor();
    }

    /**
     * Style cliquable sous la souris, calculé avec NOTRE géométrie de rendu
     * (offset HUD + LINE_H + têtes/badges/timestamp) pour que les liens du chat
     * custom se cliquent là où ils sont réellement affichés. {@code null} si rien.
     */
    public static net.minecraft.network.chat.Style styleAt(Minecraft mc, Font tr,
            List<GuiMessage.Line> visibleMessages, int scrolledLines,
            int screenW, int screenH, ChatSettings settings,
            double mouseX, double mouseY, int offsetX, int offsetY) {
        if (visibleMessages == null || visibleMessages.isEmpty()) return null;
        int maxLines = 18; // chat ouvert
        int bottomY = screenH - 40;
        int leftX = ChatLayout.TEXT_X;
        double mx = mouseX - offsetX;
        double my = mouseY - offsetY;

        int rendered = 0;
        for (int i = 0; rendered < maxLines && i + scrolledLines < visibleMessages.size(); i++) {
            GuiMessage.Line visible = visibleMessages.get(i + scrolledLines);
            if (visible == null) continue;
            FormattedCharSequence content = visible.content();
            String plain = orderedToPlainString(content);
            PlayerInfo sender = findSender(mc, plain);
            if (sender != null && ChatBlockList.INSTANCE.isBlocked(sender.getProfile().name())) continue;

            int lineY = bottomY - (rendered + 1) * LINE_H;
            if (lineY < 4) break;
            int textX = leftX;
            if (settings.chatHeads && sender != null) textX += HEAD + HEAD_GAP;
            if (settings.chatBadges && sender != null && mc.level != null) {
                var sb = mc.level.getScoreboard();
                var team = sb != null ? sb.getPlayersTeam(sender.getProfile().name()) : null;
                if (team != null && team.getPlayerPrefix() != null && !team.getPlayerPrefix().getString().isEmpty()) {
                    textX += tr.width(team.getPlayerPrefix()) + 2;
                }
            }
            if (settings.showTimestamps) {
                textX += tr.width("[" + MessageTimestamps.formattedFor(visible.addedTime()) + "] ");
            }

            if (my >= lineY && my < lineY + LINE_H && mx >= textX) {
                return null;
            }
            rendered++;
        }
        return null;
    }

    /** Cherche le premier joueur en ligne dont le pseudo apparaît dans le texte. */
    private static PlayerInfo findSender(Minecraft mc, String plain) {
        if (mc == null || mc.getConnection() == null) return null;
        PlayerInfo best = null;
        int bestIdx = Integer.MAX_VALUE;
        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            String name = e.getProfile() != null ? e.getProfile().name() : null;
            if (name == null || name.isEmpty()) continue;
            int idx = plain.indexOf(name);
            if (idx >= 0 && idx < bestIdx) {
                bestIdx = idx;
                best = e;
            }
        }
        return best;
    }

    /** Dessine la tête (face + chapeau) 8×8 depuis la skin du joueur. */
    private static void drawHead(GuiGraphicsExtractor ctx, Identifier skin, int x, int y, int alpha) {
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, HEAD, HEAD, 64, 64);   // visage
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, HEAD, HEAD, 64, 64);  // calque chapeau
    }

    /** FormattedCharSequence tronqué aux {@code max} premiers code points (style préservé). */
    private static FormattedCharSequence truncate(FormattedCharSequence src, int max) {
        return visitor -> {
            int[] count = {0};
            src.accept((index, style, cp) -> {
                if (count[0] >= max) return false;
                count[0]++;
                return visitor.accept(index, style, cp);
            });
            return true;
        };
    }

    private static double getMessageOpacity(int age) {
        if (age < 180) return 1.0;
        if (age >= FADE_TICK) return 0.0;
        return 1.0 - (age - 180) / 20.0;
    }

    private static String orderedToPlainString(FormattedCharSequence ordered) {
        StringBuilder sb = new StringBuilder();
        ordered.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
