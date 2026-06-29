package fr.reborn.hud.runtime;

import fr.reborn.hud.chat.ChatBlockList;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.MentionDetector;
import fr.reborn.hud.chat.MessageTimestamps;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;

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

    private ChatMessageRenderer() {}

    public static void renderMessages(DrawContext ctx, TextRenderer tr,
                                       List<ChatHudLine.Visible> visibleMessages,
                                       int scrolledLines,
                                       int currentTick, boolean focused,
                                       int screenW, int screenH,
                                       ChatSettings settings, String playerName) {
        if (visibleMessages == null || visibleMessages.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Géométrie vanilla : messages ancrés en bas, juste au-dessus de la
        // barre de saisie (à screenH-40 comme vanilla), à gauche. Le scroll
        // vanilla (scrolledLines) est respecté.
        int maxLines = focused ? 18 : 10;
        int bottomY = screenH - 40;
        int leftX = 4;
        int areaW = Math.min(Math.max(160, screenW - 8), 320);

        // Panneau sombre arrondi (façon Paladium) derrière les messages quand
        // le chat est ouvert. Dimensionné au nombre de lignes affichables.
        if (focused) {
            int avail = Math.max(1, Math.min(maxLines, visibleMessages.size() - scrolledLines));
            int panelTop = bottomY - avail * LINE_H - 4;
            int px = leftX - 4;
            int pw = areaW + 8;
            int pbottom = bottomY + 1;
            // Panneau carré sombre.
            ctx.fill(px, panelTop, px + pw, pbottom, 0xC00A0608);
            // Liseré accent épais en haut (3px, dégradé vers le bas).
            ctx.fill(px, panelTop, px + pw, panelTop + 1, Colors.ACCENT);
            ctx.fill(px, panelTop + 1, px + pw, panelTop + 2, Colors.withAlpha(Colors.ACCENT, 0.55f));
            ctx.fill(px, panelTop + 2, px + pw, panelTop + 3, Colors.withAlpha(Colors.ACCENT, 0.22f));
            // Liseré bas discret.
            ctx.fill(px, pbottom - 1, px + pw, pbottom, Colors.withAlpha(Colors.ACCENT, 0.3f));
        }

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

            OrderedText content = visible.content();
            String plain = orderedToPlainString(content);

            // Expéditeur (pour tête + blocage) : premier pseudo en ligne du texte.
            PlayerListEntry sender = findSender(mc, plain);
            if (sender != null && ChatBlockList.INSTANCE.isBlocked(sender.getProfile().getName())) {
                continue; // message masqué — ne consomme pas de ligne
            }

            int lineY = bottomY - (rendered + 1) * LINE_H;
            if (lineY < 4) break;

            int textX = leftX;

            // Tête du joueur (chat_heads).
            boolean drewHead = false;
            if (settings.chatHeads && sender != null) {
                Identifier skin = sender.getSkinTextures().texture();
                drawHead(ctx, skin, leftX, lineY - 1, alpha);
                textX += HEAD + HEAD_GAP;
                drewHead = true;
            }

            // Badge de rang : préfixe d'équipe scoreboard (rempli par le serveur
            // depuis LuckPerms). Rien à afficher si le serveur ne le fournit pas.
            if (settings.chatBadges && sender != null && mc != null && mc.world != null) {
                var sb = mc.world.getScoreboard();
                var team = sb != null ? sb.getScoreHolderTeam(sender.getProfile().getName()) : null;
                if (team != null) {
                    var prefix = team.getPrefix();
                    if (prefix != null && !prefix.getString().isEmpty()) {
                        ctx.drawText(tr, prefix, textX, lineY, (alpha << 24) | 0x00FFFFFF, true);
                        textX += tr.getWidth(prefix) + 2;
                    }
                }
            }

            boolean isMention = settings.highlightMentions && playerName != null
                && !playerName.isBlank()
                && MentionDetector.isMentioned(plain, playerName);
            if (isMention) {
                int bgAlpha = Math.max(40, (alpha * 60) / 255);
                int bgColor = (bgAlpha << 24) | (settings.highlightColor & 0x00FFFFFF);
                ctx.fill(textX - 2, lineY - 1, leftX + areaW + 2, lineY + LINE_H - 1, bgColor);
            }

            if (settings.showTimestamps) {
                String ts = "[" + MessageTimestamps.formattedFor(visible.addedTime()) + "] ";
                int tsColor = (alpha << 24) | (RebornColors.FOREGROUND_MUTED & 0x00FFFFFF);
                ctx.drawText(tr, ts, textX, lineY, tsColor, true);
                textX += tr.getWidth(ts);
            }

            // Typing : effet machine à écrire sur LE message le plus récent
            // (messageIdx 0) tant qu'il est frais.
            OrderedText drawContent = content;
            if (settings.chatTyping && messageIdx == 0 && !plain.isEmpty()) {
                int revealTicks = Math.min(40, Math.max(6, plain.length()));
                float prog = age / (float) revealTicks;
                if (prog < 1f) {
                    int n = Math.max(1, Math.round(prog * plain.length()));
                    drawContent = truncate(content, n);
                }
            }

            int textColor = (alpha << 24) | 0x00FFFFFF;
            ctx.drawText(tr, drawContent, textX, lineY, textColor, true);
            if (drewHead) { /* tête déjà dessinée, rien à finaliser */ }

            rendered++;
        }
    }

    /** Cherche le premier joueur en ligne dont le pseudo apparaît dans le texte. */
    private static PlayerListEntry findSender(MinecraftClient mc, String plain) {
        if (mc == null || mc.getNetworkHandler() == null) return null;
        PlayerListEntry best = null;
        int bestIdx = Integer.MAX_VALUE;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String name = e.getProfile() != null ? e.getProfile().getName() : null;
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
    private static void drawHead(DrawContext ctx, Identifier skin, int x, int y, int alpha) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
        ctx.drawTexture(skin, x, y, 8f, 8f, HEAD, HEAD, 64, 64);   // visage
        ctx.drawTexture(skin, x, y, 40f, 8f, HEAD, HEAD, 64, 64);  // calque chapeau
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** OrderedText tronqué aux {@code max} premiers code points (style préservé). */
    private static OrderedText truncate(OrderedText src, int max) {
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

    private static String orderedToPlainString(OrderedText ordered) {
        StringBuilder sb = new StringBuilder();
        ordered.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
