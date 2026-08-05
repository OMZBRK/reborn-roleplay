package fr.reborn.hud.chat;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.ui.style.RebornColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Formatting;

/**
 * Chat custom Zenkai-like — version épurée carrée.
 *
 * <p>Formes 100% rectangulaires (zéro coin arrondi), bordures fines 1px,
 * fond solide légèrement transparent. Header avec onglets séparés par
 * lignes verticales (style terminal), footer compact avec pill mono pour
 * la commande active + hint inline.
 *
 * <p>Le panel est rendu UNIQUEMENT quand chat ouvert (T pressé). Quand
 * fermé, seuls les messages flottent (ChatMessageRenderer).
 *
 * <p>Tabs hit-test exposé via {@link #tabAtPos} pour le mixin ChatScreen.
 */
public final class RebornChatRenderer {

    public static final ChatTabState TAB_STATE = new ChatTabState();
    public static final MessageClassifier CLASSIFIER = new MessageClassifier();

    private static long mentionFlashStartedAtMs = 0L;
    private static final long MENTION_FLASH_DURATION_MS = 1500;

    public static void triggerMentionFlash() {
        mentionFlashStartedAtMs = System.currentTimeMillis();
    }

    private static float mentionFlashIntensity() {
        if (mentionFlashStartedAtMs == 0L) return 0f;
        long age = System.currentTimeMillis() - mentionFlashStartedAtMs;
        if (age > MENTION_FLASH_DURATION_MS) return 0f;
        float t = age / (float) MENTION_FLASH_DURATION_MS;
        return (float) Math.sin(t * Math.PI) * (1f - t);
    }

    // ─────────── Layout constants ───────────
    private static final int HEADER_HEIGHT = 18;
    private static final int INPUT_HEIGHT = 18;
    private static final int TAB_PAD_X = 8;
    private static final int TAB_GAP = 1; // separator vertical entre tabs

    /** Cache du dernier rect des tabs render pour hit-test depuis ChatScreenMixin. */
    private static int[][] lastTabRects = new int[ChatTab.values().length][];
    /** Cache du rect du pill /me pour click handling. */
    private static int[] lastCommandPillRect = null;

    private RebornChatRenderer() {}

    public static void render(GuiGraphicsExtractor ctx, Font tr, int screenW, int screenH) {
        render(ctx, tr, screenW, screenH, 92);
    }

    public static void render(GuiGraphicsExtractor ctx, Font tr, int screenW, int screenH,
                              int opacityPct) {
        HudElementBounds b = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);
        int x  = b.x()  - 2;
        int y  = b.y()  - HEADER_HEIGHT - 2;
        int x2 = b.right() + 2;
        int y2 = b.bottom() + INPUT_HEIGHT + 2;

        // BG solide avec opacite ajustee
        int bgAlpha = Math.max(40, Math.min(255, opacityPct * 255 / 100));
        int panelBg = (bgAlpha << 24) | (RebornColors.BG_PANEL & 0x00FFFFFF);
        ctx.fill(x, y, x2, y2, panelBg);

        // Border 1px tout autour (carré)
        ctx.fill(x,      y,      x2,     y + 1,  RebornColors.BORDER);
        ctx.fill(x,      y2 - 1, x2,     y2,     RebornColors.BORDER);
        ctx.fill(x,      y,      x + 1,  y2,     RebornColors.BORDER);
        ctx.fill(x2 - 1, y,      x2,     y2,     RebornColors.BORDER);

        // Mention flash : border accent + pulse subtil
        float flashIntensity = mentionFlashIntensity();
        if (flashIntensity > 0f) {
            int flashAlpha = (int) (flashIntensity * 200);
            int flashColor = (flashAlpha << 24) | (RebornColors.ACCENT & 0x00FFFFFF);
            ctx.fill(x,      y,      x2,     y + 1,  flashColor);
            ctx.fill(x,      y2 - 1, x2,     y2,     flashColor);
            ctx.fill(x,      y,      x + 1,  y2,     flashColor);
            ctx.fill(x2 - 1, y,      x2,     y2,     flashColor);
        }

        // Tabs supprimes (non fonctionnels) — header simple avec juste "Chat"
        renderHeaderSimple(ctx, tr, x + 1, y, x2 - x - 2);
        ctx.fill(x + 1, y + HEADER_HEIGHT, x2 - 1, y + HEADER_HEIGHT + 1, RebornColors.BORDER);

        // Divider messages / input
        int inputTop = y2 - INPUT_HEIGHT;
        ctx.fill(x + 1, inputTop, x2 - 1, inputTop + 1, RebornColors.BORDER);
        renderInputBar(ctx, tr, x + 1, inputTop, x2 - x - 2);
    }

    /** Header minimal sans tabs — juste le label "Chat" aligné gauche. */
    private static void renderHeaderSimple(GuiGraphicsExtractor ctx, Font tr, int hx, int hy, int hw) {
        int baseY = hy + (HEADER_HEIGHT - tr.lineHeight) / 2;
        ctx.text(tr, Component.literal("CHAT").formatted(Formatting.BOLD),
            hx + TAB_PAD_X, baseY, RebornColors.FOREGROUND, false);
        // Vider les hit-test caches : pas de tab cliquable
        lastTabRects = new int[ChatTab.values().length][];
    }

    private static void renderHeader(GuiGraphicsExtractor ctx, Font tr, int hx, int hy, int hw) {
        int xCursor = hx + TAB_PAD_X;
        int baseY = hy + (HEADER_HEIGHT - tr.lineHeight) / 2;

        ChatTab[] tabs = ChatTab.values();
        for (int i = 0; i < tabs.length; i++) {
            ChatTab tab = tabs[i];
            boolean active = TAB_STATE.activeTab() == tab;
            int unread = TAB_STATE.unreadOf(tab);

            int textColor = active ? RebornColors.FOREGROUND : RebornColors.FOREGROUND_MUTED;
            int labelW = tr.width(tab.displayName());

            // Petit dot rond accent à gauche (sauf GENERAL qui n'en a pas)
            int dotX = xCursor;
            int dotW = 0;
            if (tab.accentColor() != 0) {
                ctx.fill(dotX, baseY + 2, dotX + 4, baseY + 6, tab.accentColor());
                dotW = 7;
            }

            int labelX = xCursor + dotW;
            ctx.text(tr, Component.literal(tab.displayName()).formatted(Formatting.BOLD),
                labelX, baseY, textColor, false);
            int labelEnd = labelX + labelW;

            // Badge unread (carré minimal)
            int badgeEnd = labelEnd;
            if (unread > 0) {
                String s = unread > 99 ? "99+" : String.valueOf(unread);
                int badgeW = tr.width(s) + 4;
                int badgeH = 9;
                int badgeX = labelEnd + 4;
                int badgeY = baseY;
                int badgeBg = tab.accentColor() != 0 ? tab.accentColor() : RebornColors.ACCENT;
                ctx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, badgeBg);
                int sW = tr.width(s);
                ctx.text(tr, Component.literal(s),
                    badgeX + (badgeW - sW) / 2, badgeY + 1, 0xFF000000, false);
                badgeEnd = badgeX + badgeW;
            }

            // Underline 1px accent si actif
            if (active) {
                int underlineY = hy + HEADER_HEIGHT - 2;
                int accent = tab.accentColor() != 0 ? tab.accentColor() : RebornColors.ACCENT;
                ctx.fill(xCursor - 2, underlineY, badgeEnd + 2, underlineY + 1, accent);
            }

            // Cache rect pour hit-test
            int tabW = badgeEnd - xCursor + TAB_PAD_X;
            lastTabRects[i] = new int[]{xCursor - 3, hy, tabW + 6, HEADER_HEIGHT};

            xCursor = badgeEnd + TAB_PAD_X * 2;

            // Divider vertical entre tabs (sauf après le dernier)
            if (i < tabs.length - 1) {
                ctx.fill(xCursor - TAB_PAD_X - 1, hy + 4, xCursor - TAB_PAD_X,
                    hy + HEADER_HEIGHT - 4, RebornColors.BORDER);
            }
        }
    }

    private static void renderInputBar(GuiGraphicsExtractor ctx, Font tr, int ix, int iy, int iw) {
        String pillLabel = "/me ▾";
        int pillW = tr.width(pillLabel) + 10;
        int pillH = 12;
        int pillX = ix + 6;
        int pillY = iy + (INPUT_HEIGHT - pillH) / 2;

        // Pill carré minimal (pas de coins arrondis)
        ctx.fill(pillX, pillY, pillX + pillW, pillY + pillH, RebornColors.BG_INPUT);
        ctx.fill(pillX, pillY, pillX + pillW, pillY + 1, RebornColors.BORDER_STRONG);
        ctx.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, RebornColors.BORDER_STRONG);
        ctx.fill(pillX, pillY, pillX + 1, pillY + pillH, RebornColors.BORDER_STRONG);
        ctx.fill(pillX + pillW - 1, pillY, pillX + pillW, pillY + pillH, RebornColors.BORDER_STRONG);
        ctx.text(tr, Component.literal(pillLabel),
            pillX + 5, pillY + 2, RebornColors.ACCENT_HOVER, false);

        lastCommandPillRect = new int[]{pillX, pillY, pillW, pillH};

        // Plus de hint text — le chatField vanilla est positionne dans cette zone
        // via ChatScreenMixin et affiche son propre placeholder.
    }

    /**
     * Calcule la zone où le {@code chatField} de vanilla doit etre positionne
     * par {@code ChatScreenMixin}.
     * @return rect (x, y, w, h)
     */
    public static int[] inputFieldRect(Font tr, int screenW, int screenH) {
        HudElementBounds b = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);
        int inputTop = b.bottom() + 2; // 2px de padding sous chat history
        int pillW = tr.width("/me ▾") + 10;
        int fieldX = b.x() + 1 + 6 + pillW + 6;   // panel left + padding + pill + gap
        int fieldY = inputTop + (INPUT_HEIGHT - 8) / 2;  // centré dans input bar
        int fieldW = b.width() - (fieldX - b.x()) - 6;
        int fieldH = 8;
        return new int[]{fieldX, fieldY, fieldW, fieldH};
    }

    public static void onMessageReceived(String rawText) {
        ChatTab tab = CLASSIFIER.classify(rawText);
        TAB_STATE.onMessageReceived(tab);
    }

    /** Hit-test : renvoie le tab contenant la souris, ou null. */
    public static ChatTab tabAtPos(double mouseX, double mouseY) {
        if (lastTabRects == null) return null;
        ChatTab[] tabs = ChatTab.values();
        for (int i = 0; i < tabs.length; i++) {
            int[] r = lastTabRects[i];
            if (r != null && mouseX >= r[0] && mouseX < r[0] + r[2]
                          && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return tabs[i];
            }
        }
        return null;
    }

    /** Hit-test du pill /me. */
    public static boolean isCommandPillClick(double mouseX, double mouseY) {
        int[] r = lastCommandPillRect;
        if (r == null) return false;
        return mouseX >= r[0] && mouseX < r[0] + r[2]
            && mouseY >= r[1] && mouseY < r[1] + r[3];
    }
}
