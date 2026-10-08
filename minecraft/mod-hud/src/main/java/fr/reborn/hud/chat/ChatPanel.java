package fr.reborn.hud.chat;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chat Reborn : un panneau de laque noire tenu entre deux poutres de bois (onglets en haut, saisie en bas), filets
 * et ferrures d'or comme les autres menus. Motif propre au chat : des vagues seigaiha dorées (maki-e) dans le coin
 * de la zone des messages. Icônes : Lucide (ISC), textures/gui/chat, teintées au rendu.
 *
 * <p>Le panneau est dessiné dans l'espace local de l'élément HUD {@link HudElement#CHAT} : ses bornes par défaut
 * ({@link #bounds}) servent aussi à l'éditeur HUD, et la poignée de l'en-tête le déplace. Les messages sont
 * relus depuis {@code allMessages} et découpés en {@link ChatEntry} : en-tête (icône, genre, auteur, heure) puis
 * corps. Chat fermé, seuls les messages récents flottent à la même place.
 */
public final class ChatPanel {

    // ─── géométrie ───
    private static final int HDR = 19;
    private static final int BEAM = 26;
    private static final int ROW = 16;
    private static final int PAD = 8;
    private static final int HEAD_H = 12;
    private static final int LINE_H = 10;
    private static final int GAP = 6;

    // ─── palette Reborn (kit commun des menus) ───
    private static final int LACQUER = 0xFFA0182B, LACQUER_HI = 0xFFC01E35, LACQUER_LO = 0xFF7A1322;
    private static final int GOLD = 0xFFD9A95E, GOLD_SOFT = 0xFF3A2C14, GOLD_DARK = 0xFF5A4012;
    private static final int IVORY = 0xFFF5E9D0, IVORY_2 = 0xFFC2B59A, MUTED = 0xFF7A6E5C;
    private static final int WASHI = 0xFFE3D1A8, WASHI_2 = 0xFFD4BF91, INK = 0xFF2A1A10, INK_SOFT = 0xFF8A7250;
    private static final int WOOD_DARK = 0xFF2E1C11, NIGHT = 0x0A0608, NIGHT_HI = 0x1C0E12;

    private static final ChatTab[] TABS = {ChatTab.GENERAL, ChatTab.RP, ChatTab.GROUP};

    // ─── état de session ───
    private static ChatTab tab = ChatTab.GENERAL;
    private static final Map<ChatTab, Integer> seen = new EnumMap<>(ChatTab.class);
    private static boolean meMode = false;
    private static boolean searchOpen = false;
    private static boolean searchFocused = false;
    private static String query = "";
    private static int scrollPx = 0;
    private static int maxScroll = 0;

    private static boolean dragging = false;
    private static double dragMx, dragMy;
    private static int dragSx, dragSy;

    /** Souris en coordonnées écran, posée par l'écran de chat avant le rendu. */
    public static int mouseX = -1, mouseY = -1;
    /** Champ de saisie du chat ouvert (pour router la frappe vers la recherche). */
    public static EditBox activeInput;

    private static final Map<GuiMessage, ChatEntry> CACHE = new WeakHashMap<>();
    private static final List<Line> LINES = new ArrayList<>();
    private static final List<Btn> BTNS = new ArrayList<>();
    private static int[] fieldRect = {0, 0, 0, 0};
    private static int[] emojiRect = {0, 0, 0, 0};

    private static String lastInput = "";
    private static long lastCharMs = 0L;

    private record Line(int x, int y, FormattedCharSequence seq, ChatEntry entry) {}

    private record Btn(int x, int y, int w, int h, Runnable action, boolean quiet) {
        Btn(int x, int y, int w, int h, Runnable action) { this(x, y, w, h, action, false); }

        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private ChatPanel() {}

    // =============================================================== géométrie

    public static int width(int sw) { return clamp(Math.round(sw * 0.36f), 240, 330); }

    public static int height(int sh) { return clamp(Math.round(sh * 0.46f), 136, 220); }

    /** Bornes par défaut (bas-gauche), avant le décalage choisi par le joueur. */
    public static HudElementBounds bounds(int sw, int sh) {
        int w = Math.min(width(sw), sw - 12), h = height(sh);
        // écran étroit : le panneau toucherait la hotbar → il se pose au-dessus
        int margin = 6 + w > sw / 2 - 95 ? 28 : 6;
        return new HudElementBounds(6, sh - h - margin, w, h);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Rectangle du champ de saisie en coordonnées locales {x, y, w, h}. */
    public static int[] fieldRect() { return fieldRect; }

    /** Rectangle du bouton emoji en coordonnées locales {x, y, w, h}. */
    public static int[] emojiRect() { return emojiRect; }

    public static boolean searchFocused() { return searchOpen && searchFocused; }

    public static boolean isDragging() { return dragging; }

    // =============================================================== rendu

    public static void render(GuiGraphicsExtractor ctx, Font font, List<GuiMessage> all, int tick, boolean focused,
                              int sw, int sh, ChatSettings settings, String playerName, double lmx, double lmy) {
        HudElementBounds b = bounds(sw, sh);
        if (focused) renderOpen(ctx, font, all, tick, b, settings, playerName, lmx, lmy);
        else renderFloating(ctx, font, all, tick, b, settings, playerName);
    }

    private static List<ChatEntry> entries(List<GuiMessage> all, boolean filtered) {
        List<ChatEntry> out = new ArrayList<>();
        if (all == null) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        int n = Math.min(all.size(), 100);
        for (int i = 0; i < n; i++) {
            GuiMessage m = all.get(i);
            if (m == null) continue;
            ChatEntry e = CACHE.computeIfAbsent(m, k -> ChatEntry.parse(k.content(), k.addedTime()));
            if (filtered && (!e.visibleIn(tab) || (searchOpen && !e.matches(q)))) continue;
            out.add(e);
        }
        return out;
    }

    /** L'entrée plus ancienne {@code older} continue-t-elle le même auteur ({@code compactConsecutive}) ? */
    private static boolean continues(ChatEntry e, ChatEntry older, ChatSettings s) {
        return s.compactConsecutive && older != null && e.kind != ChatEntry.Kind.SYSTEM && older.kind == e.kind
                && older.name.getString().equals(e.name.getString()) && e.addedTime - older.addedTime < 1200;
    }

    private static int entryHeight(ChatEntry e, boolean header, Font font, int w) {
        return (header ? HEAD_H : 0) + e.lines(font, w).size() * LINE_H;
    }

    private static void renderOpen(GuiGraphicsExtractor ctx, Font font, List<GuiMessage> all, int tick, HudElementBounds b,
                                   ChatSettings s, String playerName, double mx, double my) {
        LINES.clear();
        BTNS.clear();
        int x = b.x(), y = b.y(), w = b.width(), h = b.height();
        int alpha = clamp(Math.round(s.opacity * 2.55f), 110, 248);
        int bt = y + h - BEAM;

        // ombre portée, cadre bois sombre, laque noire
        ctx.fill(x + 2, y + 3, x + w + 3, y + h + 3, 0x55000000);
        chamfer(ctx, x - 1, y - 1, w + 2, h + 2, WOOD_DARK);
        chamferGradient(ctx, x, y, w, h, (alpha << 24) | NIGHT_HI, (alpha << 24) | NIGHT);

        // ─── messages ───
        int ax0 = x + PAD + 1, ax1 = x + w - PAD - 4, ay0 = y + HDR + 5, ay1 = bt - 4;
        DrawHelpers.outlinedRect(ctx, x + 3, y + HDR + 2, w - 6, bt - y - HDR - 4, 0, 0x30D9A95E);
        seigaiha(ctx, x + w - 4 - 66, ay1 - 30, 66, 30);
        int tw = ax1 - ax0;
        List<ChatEntry> list = entries(all, true);
        int[] hs = new int[list.size()];
        boolean[] heads = new boolean[list.size()];
        int content = 0;
        for (int i = 0; i < list.size(); i++) {
            ChatEntry e = list.get(i);
            heads[i] = e.kind != ChatEntry.Kind.SYSTEM && !continues(e, i + 1 < list.size() ? list.get(i + 1) : null, s);
            hs[i] = entryHeight(e, heads[i], font, tw);
            content += hs[i] + (i > 0 ? (heads[i - 1] ? GAP : 2) : 0);
        }
        int areaH = ay1 - ay0;
        maxScroll = Math.max(0, content - areaH + 2);
        scrollPx = clamp(scrollPx, 0, maxScroll);

        if (list.isEmpty()) {
            String msg = searchOpen && !query.isBlank() ? "Aucun message ne correspond." : "Aucun message pour l'instant.";
            ctx.text(font, Component.literal(msg), x + w / 2 - font.width(msg) / 2, (ay0 + ay1) / 2 - 4, MUTED, false);
        }

        ctx.enableScissor(x + 3, ay0 - 1, x + w - 3, ay1);
        int bottom = ay1 + scrollPx;
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            ChatEntry e = list.get(i);
            int top = bottom - hs[i];
            if (top > ay1) { bottom = top - (heads[i] ? GAP : 2); continue; }
            if (bottom < ay0) break;
            int slide = 0;
            if (s.chatAnimation && i == 0 && scrollPx == 0) {
                float t = Math.min(1f, (now - e.seenAtMs) / 220f);
                slide = Math.round((1f - (1f - (1f - t) * (1f - t))) * 6);
            }
            drawEntry(ctx, font, e, heads[i], ax0, top + slide, tw, playerName, s, 255, false, true);
            bottom = top - (heads[i] ? GAP : 2);
        }
        ctx.disableScissor();

        // barre de défilement dorée + rappel « derniers messages »
        if (maxScroll > 0) {
            int thumb = Math.max(14, areaH * areaH / (content + 2));
            int ty = ay1 - thumb - Math.round((areaH - thumb) * (scrollPx / (float) maxScroll));
            ctx.fill(x + w - PAD + 1, ay0, x + w - PAD + 2, ay1, 0x26D9A95E);
            ctx.fill(x + w - PAD, ty, x + w - PAD + 3, ty + thumb, 0xB0D9A95E);
        }
        if (scrollPx > 0) {
            Component lbl = RebornFont.body("Derniers messages");
            int pw = font.width(lbl) + 24, px = x + w / 2 - pw / 2, py = ay1 - 16;
            boolean hov = in(mx, my, px, py, pw, 13);
            chamfer(ctx, px - 1, py - 1, pw + 2, 15, WOOD_DARK);
            chamferGradient(ctx, px, py, pw, 13, hov ? 0xFFD62440 : LACQUER_HI, LACQUER_LO);
            DrawHelpers.outlinedRect(ctx, px + 1, py + 1, pw - 2, 11, 0, 0x80D9A95E);
            icon(ctx, I_DOWN, px + 5, py + 2, 9, IVORY);
            ctx.text(font, lbl, px + 17, py + 3, IVORY, false);
            BTNS.add(new Btn(px, py, pw, 13, () -> scrollPx = 0));
        }

        header(ctx, font, all, tick, x, y, w, mx, my);
        inputBeam(ctx, font, x, bt, w, mx, my);

        if (!searchOpen || !searchFocused) {
            int at = seen.getOrDefault(tab, tick);
            seen.put(tab, Math.max(at, tick));
        }
    }

    /** Poutre de bois veinée (en-tête et saisie), biseau clair, filet d'or côté laque. */
    private static void beam(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean top) {
        if (top) chamferGradient(ctx, x, y, w, h, 0xFF744C2C, 0xFF4E321D);
        else chamferGradient(ctx, x, y, w, h, 0xFF5E3D23, 0xFF3A2414);
        long seed = top ? 0x2545F4914F6CDD1DL : 0x5DEECE66DL;
        for (int yy = y + 1; yy < y + h - 1; yy += 2) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int a = 0x14 + (int) ((seed >>> 59) & 0x0F);
            int sx = x + 2 + (int) ((seed >>> 33) % 40);
            ctx.fill(sx, yy, x + w - 2 - (int) ((seed >>> 45) % 40), yy + 1, (a << 24) | 0x1A0D06);
        }
        ctx.fill(x + 1, y, x + w - 1, y + 1, 0x40FFE0B0);
        if (top) {
            ctx.fill(x, y + h, x + w, y + h + 1, GOLD_SOFT);
            ctx.fill(x, y + h + 1, x + w, y + h + 3, 0x50000000);
        } else {
            ctx.fill(x, y - 1, x + w, y, GOLD_SOFT);
        }
    }

    /** Ferrure d'angle dorée miniature (L de 7 px, rivet). */
    private static void kanagu(GuiGraphicsExtractor ctx, int cx, int cy, int dx, int dy) {
        int L = 7, T = 2;
        int x0 = Math.min(cx, cx + dx * L), x1 = Math.max(cx, cx + dx * L);
        int y0 = Math.min(cy, cy + dy * L), y1 = Math.max(cy, cy + dy * L);
        int ty0 = dy > 0 ? cy : cy - T, ty1 = dy > 0 ? cy + T : cy;
        int tx0 = dx > 0 ? cx : cx - T, tx1 = dx > 0 ? cx + T : cx;
        ctx.fill(x0, ty0, x1, ty1, GOLD);
        ctx.fill(tx0, y0, tx1, y1, GOLD);
        ctx.fill(x0, ty0, x1, ty0 + 1, 0xFFF2D49A);
        int rx = cx + dx * 4 - (dx < 0 ? 1 : 0), ry = cy + dy * 4 - (dy < 0 ? 1 : 0);
        ctx.fill(rx, ry, rx + 1, ry + 1, GOLD);
    }

    /** Motif du chat : vagues seigaiha dorées très discrètes, comme un maki-e dans le coin de la laque. */
    private static void seigaiha(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.enableScissor(x, y, x + w, y + h);
        for (int r = 0; r < h / 5 + 2; r++) {
            int cy = y + r * 5 + 6;
            int off = (r % 2) * 6;
            for (int cx = x - 6 + off; cx < x + w + 12; cx += 12) {
                int fade = Math.max(0, Math.min(0x22, (cx - x) * 0x22 / Math.max(1, w) + (cy - y) * 0x0C / Math.max(1, h)));
                if (fade < 4) continue;
                DrawHelpers.ring(ctx, cx, cy, 6, 1, (fade << 24) | 0xD9A95E);
                DrawHelpers.ring(ctx, cx, cy, 3, 1, ((fade * 2 / 3) << 24) | 0xD9A95E);
            }
        }
        ctx.disableScissor();
    }

    private static void header(GuiGraphicsExtractor ctx, Font font, List<GuiMessage> all, int tick, int x, int y, int w,
                               double mx, double my) {
        beam(ctx, x, y, w, HDR, true);
        kanagu(ctx, x, y, 1, 1);
        kanagu(ctx, x + w, y, -1, 1);

        // poignée de déplacement
        boolean hovH = dragging || in(mx, my, x + 3, y + 3, 14, 13);
        if (hovH) chamfer(ctx, x + 3, y + 3, 14, 13, 0x40000000);
        icon(ctx, I_GRIP, x + 4, y + 4, 12, hovH ? GOLD : IVORY_2);
        BTNS.add(new Btn(x + 3, y + 3, 14, 13, ChatPanel::beginDrag, true));

        int gearX = x + w - 18, searchX = x + w - 33, iy = y + 3;
        int tx = x + 20;
        if (searchOpen) {
            int fw = searchX - 4 - tx;
            chamfer(ctx, tx - 1, y + 2, fw + 2, 15, WOOD_DARK);
            chamferGradient(ctx, tx, y + 3, fw, 13, WASHI, WASHI_2);
            if (searchFocused) ctx.fill(tx + 1, y + 14, tx + fw - 1, y + 15, LACQUER);
            boolean empty = query.isEmpty();
            String shown = empty ? "Rechercher dans le chat…" : query;
            while (!empty && font.width(shown) > fw - 12 && shown.length() > 1) shown = shown.substring(1);
            ctx.text(font, Component.literal(shown), tx + 5, y + 5, empty ? INK_SOFT : INK, false);
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + 5 + (empty ? 0 : font.width(shown));
                ctx.fill(cx, y + 4, cx + 1, y + 14, INK);
            }
            BTNS.add(new Btn(tx, y + 3, fw, 13, () -> searchFocused = true));
        } else {
            for (ChatTab t : TABS) {
                Component lbl = RebornFont.body(t.displayName());
                int pw = font.width(lbl) + 14;
                boolean sel = t == tab, hov = !sel && in(mx, my, tx, y + 3, pw, 13);
                if (sel) {
                    chamfer(ctx, tx - 1, y + 2, pw + 2, 15, WOOD_DARK);
                    chamferGradient(ctx, tx, y + 3, pw, 13, LACQUER_HI, LACQUER_LO);
                    ctx.fill(tx + 2, y + 14, tx + pw - 2, y + 15, GOLD);
                } else if (hov) {
                    chamfer(ctx, tx, y + 3, pw, 13, 0x40000000);
                }
                ctx.text(font, lbl, tx + 7, y + 5, sel ? IVORY : hov ? WASHI : IVORY_2, false);
                if (!sel && unread(all, t, tick) > 0) {
                    ctx.fill(tx + pw - 6, y + 2, tx + pw - 1, y + 7, GOLD);
                    ctx.fill(tx + pw - 5, y + 3, tx + pw - 2, y + 6, LACQUER_HI);
                }
                final ChatTab target = t;
                BTNS.add(new Btn(tx, y + 3, pw, 13, () -> setTab(target)));
                tx += pw + 2;
            }
        }

        // loupe
        boolean hovS = in(mx, my, searchX, iy, 14, 14);
        if (hovS || searchOpen) chamfer(ctx, searchX, iy, 14, 13, searchOpen ? 0x60000000 : 0x40000000);
        icon(ctx, I_SEARCH, searchX + 2, iy + 1, 11, hovS || searchOpen ? GOLD : IVORY_2);
        BTNS.add(new Btn(searchX, iy, 14, 14, ChatPanel::toggleSearch));

        // réglages
        boolean hovG = in(mx, my, gearX, iy, 14, 14);
        if (hovG) chamfer(ctx, gearX, iy, 14, 13, 0x40000000);
        icon(ctx, I_SETTINGS, gearX + 2, iy + 1, 11, hovG ? GOLD : IVORY_2);
        BTNS.add(new Btn(gearX, iy, 14, 14, ChatPanel::openSettings));
    }

    /** Poutre basse : sceau de mode, bande de washi pour écrire, emoji, envoi laqué. Embouts dorés. */
    private static void inputBeam(GuiGraphicsExtractor ctx, Font font, int x, int bt, int w, double mx, double my) {
        beam(ctx, x, bt, w, BEAM, false);
        kanagu(ctx, x, bt + BEAM, 1, -1);
        kanagu(ctx, x + w, bt + BEAM, -1, -1);
        int rowY = bt + (BEAM - ROW) / 2;

        // mode : dire / narrer (/me)
        Component chip = RebornFont.body(meMode ? "/me" : "Dire");
        int cw = Math.max(30, font.width(chip) + 14), cx = x + PAD - 1;
        boolean hovC = in(mx, my, cx, rowY, cw, ROW);
        chamfer(ctx, cx - 1, rowY - 1, cw + 2, ROW + 2, WOOD_DARK);
        if (meMode) chamferGradient(ctx, cx, rowY, cw, ROW, hovC ? 0xFFD62440 : LACQUER_HI, LACQUER_LO);
        else chamferGradient(ctx, cx, rowY, cw, ROW, hovC ? 0xFF3A2416 : 0xFF2A1A10, 0xFF1A0F09);
        DrawHelpers.outlinedRect(ctx, cx + 1, rowY + 1, cw - 2, ROW - 2, 0, meMode ? GOLD : 0x90D9A95E);
        ctx.text(font, chip, cx + cw / 2 - font.width(chip) / 2, rowY + 4, IVORY, false);
        BTNS.add(new Btn(cx, rowY, cw, ROW, ChatPanel::toggleMode));

        // envoyer
        int sx = x + w - PAD + 1 - ROW;
        boolean hovSend = in(mx, my, sx, rowY, ROW, ROW);
        chamfer(ctx, sx - 1, rowY - 1, ROW + 2, ROW + 2, WOOD_DARK);
        chamferGradient(ctx, sx, rowY, ROW, ROW, hovSend ? 0xFFD62440 : LACQUER_HI, LACQUER_LO);
        DrawHelpers.outlinedRect(ctx, sx + 1, rowY + 1, ROW - 2, ROW - 2, 0, GOLD);
        icon(ctx, I_SEND, sx + 3, rowY + 3, 10, IVORY);
        BTNS.add(new Btn(sx, rowY, ROW, ROW, ChatPanel::send));

        // emoji
        int ex = sx - 4 - ROW;
        boolean hovE = in(mx, my, ex, rowY, ROW, ROW) || EmojiPicker.isOpen();
        chamfer(ctx, ex - 1, rowY - 1, ROW + 2, ROW + 2, WOOD_DARK);
        chamferGradient(ctx, ex, rowY, ROW, ROW, hovE ? 0xFF3A2416 : 0xFF2A1A10, 0xFF1A0F09);
        DrawHelpers.outlinedRect(ctx, ex + 1, rowY + 1, ROW - 2, ROW - 2, 0, hovE ? GOLD : 0x90D9A95E);
        icon(ctx, I_EMOJI, ex + 3, rowY + 3, 10, hovE ? 0xFFF2D49A : GOLD);
        emojiRect = new int[]{ex, rowY, ROW, ROW};
        BTNS.add(new Btn(ex, rowY, ROW, ROW, EmojiPicker::toggle));

        // bande de washi
        int fx = cx + cw + 5, fw = ex - 5 - fx;
        chamfer(ctx, fx - 1, rowY - 1, fw + 2, ROW + 2, WOOD_DARK);
        chamferGradient(ctx, fx, rowY, fw, ROW, WASHI, WASHI_2);
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < fw / 6; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int fxx = fx + 2 + (int) ((seed >>> 33) % Math.max(1, fw - 8));
            int fyy = rowY + 2 + (int) ((seed >>> 17) % (ROW - 4));
            ctx.fill(fxx, fyy, fxx + 2 + (int) ((seed >>> 50) % 4), fyy + 1, 0x14462810);
        }
        if (!searchFocused()) ctx.fill(fx + 1, rowY + ROW - 2, fx + fw - 1, rowY + ROW - 1, 0x70A0182B);
        fieldRect = new int[]{fx + 5, rowY, fw - 10, ROW};
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("reborn-hud", "textures/gui/chat/" + name + ".png");
    }

    private static final Identifier I_GRIP = tex("chat_grip"), I_SEARCH = tex("chat_search"),
            I_SETTINGS = tex("chat_settings"), I_EMOJI = tex("chat_emoji"), I_SEND = tex("chat_send"),
            I_DOWN = tex("chat_down");

    /** Icône 64×64 blanche, réduite à {@code size} et teintée. */
    private static void icon(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int size, int color) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, 64, 64, 64, 64, color);
    }

    /** Rectangle aux coins coupés (1 px), comme les plaquettes des menus. */
    private static void chamfer(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void chamferGradient(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
        ctx.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        ctx.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
    }

    private static void drawEntry(GuiGraphicsExtractor ctx, Font font, ChatEntry e, boolean header, int x, int y, int w,
                                  String playerName, ChatSettings s, int a, boolean shadow, boolean record) {
        int eh = entryHeight(e, header, font, w);
        boolean mention = s.highlightMentions && e.kind != ChatEntry.Kind.SYSTEM && playerName != null
                && !playerName.isBlank() && MentionDetector.isMentioned(e.body.getString(), playerName);
        if (mention) {
            int hc = s.highlightColor & 0x00FFFFFF;
            chamfer(ctx, x - 4, y - 2, w + 6, eh + 3, (Math.round(0x38 * a / 255f) << 24) | hc);
            ctx.fill(x - 4, y - 1, x - 3, y + eh, (a << 24) | (GOLD & 0x00FFFFFF));
        }
        int ly = y;
        if (header) {
            int kc = e.kind.color & 0x00FFFFFF;
            icon(ctx, tex(e.kind.icon), x, ly + 1, 10, (a << 24) | kc);
            int hx = x + 14;
            Component lbl = Component.literal(e.kind.label).withStyle(ChatFormatting.BOLD);
            ctx.text(font, lbl, hx, ly + 2, (a << 24) | kc, shadow);
            hx += font.width(lbl);
            ctx.text(font, Component.literal(" · "), hx, ly + 2, (a << 24) | (MUTED & 0x00FFFFFF), shadow);
            hx += font.width(" · ");
            ctx.text(font, e.name, hx, ly + 2, (a << 24) | (WASHI & 0x00FFFFFF), shadow);
            hx += font.width(e.name) + 5;
            String time = MessageTimestamps.formattedFor(e.addedTime);
            ctx.pose().pushMatrix();
            ctx.pose().translate(hx, ly + 4);
            ctx.pose().scale(0.75f, 0.75f);
            ctx.text(font, Component.literal(time), 0, 0, (a << 24) | (MUTED & 0x00FFFFFF), shadow);
            ctx.pose().popMatrix();
            ly += HEAD_H + 1;
        }
        boolean sys = e.kind == ChatEntry.Kind.SYSTEM;
        int nLines = e.lines(font, w).size();
        if (sys) ctx.fill(x, ly, x + 1, ly + nLines * LINE_H - 1, (Math.round(0x90 * a / 255f) << 24) | 0xD9A95E);
        int bx = sys ? x + 5 : x + 1;
        int base = sys ? IVORY_2 & 0x00FFFFFF : IVORY & 0x00FFFFFF;
        for (FormattedCharSequence seq : e.lines(font, w)) {
            ctx.text(font, seq, bx, ly, (a << 24) | base, shadow);
            if (record) LINES.add(new Line(bx, ly, seq, e));
            ly += LINE_H;
        }
    }

    /** Chat fermé : les messages récents flottent sur une plaquette de laque, puis s'effacent. */
    private static void renderFloating(GuiGraphicsExtractor ctx, Font font, List<GuiMessage> all, int tick, HudElementBounds b,
                                       ChatSettings s, String playerName) {
        int x = b.x(), w = b.width();
        int ax0 = x + PAD + 1, tw = w - 2 * PAD - 5;
        int bottom = b.y() + b.height() - BEAM - 6;
        int top = b.y() + HDR + 4;
        List<ChatEntry> list = entries(all, false);
        int shown = 0;
        for (int i = 0; i < list.size() && shown < 6; i++) {
            ChatEntry e = list.get(i);
            int age = tick - e.addedTime;
            if (age >= 200) break;
            float op = age < 180 ? 1f : 1f - (age - 180) / 20f;
            int a = Math.round(255 * op);
            if (a < 8) continue;
            boolean header = e.kind != ChatEntry.Kind.SYSTEM && !continues(e, i + 1 < list.size() ? list.get(i + 1) : null, s);
            int eh = entryHeight(e, header, font, tw);
            int y = bottom - eh;
            if (y < top) break;
            int contentW = 0;
            for (FormattedCharSequence seq : e.lines(font, tw)) contentW = Math.max(contentW, font.width(seq) + 6);
            if (header) contentW = Math.max(contentW, 14 + font.width(Component.literal(e.kind.label).withStyle(ChatFormatting.BOLD))
                    + font.width(" · ") + font.width(e.name) + 30);
            int pw = Math.min(tw, contentW) + 10;
            int bgA = Math.round(Math.min(s.opacity, 85) * 2.55f * 0.8f * op);
            chamfer(ctx, ax0 - 5, y - 3, pw, eh + 5, (bgA << 24) | NIGHT);
            ctx.fill(ax0 - 5, y - 3, ax0 - 4, y + eh + 2, (Math.round(0xA0 * op) << 24) | 0xD9A95E);
            drawEntry(ctx, font, e, header, ax0, y, tw, playerName, s, a, false, false);
            bottom = y - 7;
            shown++;
        }
    }

    private static int unread(List<GuiMessage> all, ChatTab t, int tick) {
        Integer since = seen.get(t);
        if (since == null) { seen.put(t, tick); return 0; }
        int n = 0;
        for (ChatEntry e : entries(all, false)) {
            if (e.addedTime <= since) break;
            if (e.visibleIn(t)) n++;
        }
        return n;
    }

    // =============================================================== actions

    private static void setTab(ChatTab t) {
        tab = t;
        scrollPx = 0;
    }

    private static void toggleSearch() {
        searchOpen = !searchOpen;
        searchFocused = searchOpen;
        if (!searchOpen) query = "";
        scrollPx = 0;
    }

    private static void toggleMode() { meMode = !meMode; }

    private static void openSettings() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreenAndShow(new ChatSettingsScreen(mc.gui.screen()));
    }

    private static void send() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            mc.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        }
    }

    private static void beginDrag() {
        HudElementState st = state();
        dragging = true;
        dragMx = mouseX;
        dragMy = mouseY;
        dragSx = st.x();
        dragSy = st.y();
    }

    private static HudElementState state() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }

    /** Suit la souris tant que le bouton gauche reste enfoncé, puis enregistre la position. */
    public static void tickDrag(int mx, int my) {
        mouseX = mx;
        mouseY = my;
        if (!dragging) return;
        Minecraft mc = Minecraft.getInstance();
        boolean down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        HudElementBounds b = bounds(sw, sh);
        int nx = clamp(dragSx + (int) Math.round(mx - dragMx), -b.x(), sw - b.right());
        int ny = clamp(dragSy + (int) Math.round(my - dragMy), -b.y(), sh - b.bottom() - 2);
        try {
            var cfg = RebornHudClient.config();
            cfg.setState(HudElement.CHAT, cfg.stateOf(HudElement.CHAT).withPos(nx, ny));
            if (!down) {
                dragging = false;
                cfg.save();
            }
        } catch (IllegalStateException e) {
            dragging = false;
        }
    }

    /** Préfixe {@code /me} en mode narration (sauf commandes). */
    public static String applyMode(String msg) {
        if (!meMode || msg == null) return msg;
        String t = msg.trim();
        if (t.isEmpty() || t.startsWith("/")) return msg;
        return "/me " + t;
    }

    // =============================================================== entrées

    /** Clic en coordonnées locales. {@code true} = géré (ou tombé dans le panneau). */
    public static boolean click(double lx, double ly, int sw, int sh) {
        for (Btn btn : new ArrayList<>(BTNS)) {
            if (btn.hit(lx, ly)) {
                if (!btn.quiet) fr.reborn.hud.menu.RebornSounds.uiClick();
                btn.action.run();
                return true;
            }
        }
        if (in(lx, ly, fieldRect[0] - 6, fieldRect[1] - 1, fieldRect[2] + 12, fieldRect[3] + 2)) {
            searchFocused = false;
            return false;
        }
        HudElementBounds b = bounds(sw, sh);
        if (b.contains(lx, ly)) {
            if (searchOpen) searchFocused = false;
            return true;
        }
        return false;
    }

    public static boolean over(double lx, double ly, int sw, int sh) {
        return bounds(sw, sh).contains(lx, ly);
    }

    public static void scroll(double dy) {
        scrollPx = clamp(scrollPx + (int) Math.round(dy * LINE_H * 2), 0, maxScroll);
    }

    /** Style (clic, survol) sous un point local, ou {@code null}. */
    public static Style styleAt(Font font, double lx, double ly) {
        Line l = lineAt(ly);
        if (l == null || lx < l.x) return null;
        return styleAtPixel(font, l.seq, (int) Math.floor(lx - l.x));
    }

    /** Texte brut du message sous un point local (copie MAJ+clic), ou {@code null}. */
    public static String plainAt(double ly) {
        Line l = lineAt(ly);
        return l == null ? null : l.entry.plain;
    }

    /** URL tapée en clair sous un point local, ou {@code null}. */
    public static String urlAt(Font font, double lx, double ly) {
        Line l = lineAt(ly);
        if (l == null || lx < l.x) return null;
        StringBuilder sb = new StringBuilder();
        l.seq.accept((i, st, cp) -> { sb.appendCodePoint(cp); return true; });
        String plain = sb.toString();
        int idx = charIndexAtPixel(font, l.seq, (int) Math.floor(lx - l.x));
        if (idx < 0 || idx >= plain.length()) return null;
        int start = idx, end = idx;
        while (start > 0 && !Character.isWhitespace(plain.charAt(start - 1))) start--;
        while (end < plain.length() && !Character.isWhitespace(plain.charAt(end))) end++;
        String word = plain.substring(start, end);
        while (!word.isEmpty() && ".,!?)]}\"'".indexOf(word.charAt(word.length() - 1)) >= 0) {
            word = word.substring(0, word.length() - 1);
        }
        return (word.startsWith("http://") || word.startsWith("https://")) && word.length() > 10 ? word : null;
    }

    private static Line lineAt(double ly) {
        for (Line l : LINES) if (ly >= l.y - 1 && ly < l.y + LINE_H - 1) return l;
        return null;
    }

    private static Style styleAtPixel(Font font, FormattedCharSequence seq, int px) {
        if (px < 0) return null;
        Style[] found = {null};
        float[] acc = {0f};
        seq.accept((index, style, cp) -> {
            acc[0] += font.width(FormattedCharSequence.forward(new String(Character.toChars(cp)), style));
            if (acc[0] > px) { found[0] = style; return false; }
            return true;
        });
        return found[0];
    }

    private static int charIndexAtPixel(Font font, FormattedCharSequence seq, int px) {
        int[] idx = {0};
        float[] acc = {0f};
        int[] result = {-1};
        seq.accept((index, style, cp) -> {
            acc[0] += font.width(FormattedCharSequence.forward(new String(Character.toChars(cp)), style));
            if (acc[0] > px) { result[0] = idx[0]; return false; }
            idx[0] += Character.charCount(cp);
            return true;
        });
        return result[0];
    }

    /** Banc d'essai ({@link ChatDebug}) : onglet, mode et recherche imposés. */
    static void debugState(ChatTab t, boolean me, String q) {
        tab = t;
        meMode = me;
        searchOpen = q != null;
        searchFocused = false;
        query = q == null ? "" : q;
        scrollPx = 0;
    }

    // ─── recherche : frappe routée depuis l'écran de chat ───

    public static void searchType(String s) {
        if (query.length() < 48) query += s;
        scrollPx = 0;
    }

    /** @return {@code true} si la touche est consommée par la recherche. */
    public static boolean searchKey(int key, boolean ctrl) {
        if (!searchFocused()) return false;
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) query = ctrl ? "" : query.substring(0, query.length() - 1);
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> { searchOpen = false; searchFocused = false; query = ""; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_TAB -> searchFocused = false;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_V -> {
                if (ctrl) searchType(Minecraft.getInstance().keyboardHandler.getClipboard().replace('\n', ' '));
            }
            default -> { }
        }
        scrollPx = 0;
        return true;
    }

    // ─── texte de saisie : redessiné ici (animation lettre par lettre, défilement) ───

    public static void drawInput(GuiGraphicsExtractor ctx, Font font, EditBox input, ChatSettings s) {
        if (input == null) return;
        int[] r = fieldRect;
        int visLeft = r[0], visRight = r[0] + r[2], ty = r[1] + (r[3] - 8) / 2;
        String val = input.getValue();
        long now = System.currentTimeMillis();
        if (val.length() > lastInput.length() && val.startsWith(lastInput)) lastCharMs = now;
        lastInput = val;
        ctx.enableScissor(visLeft - 1, r[1], visRight + 1, r[1] + r[3]);
        if (val.isEmpty()) {
            String hint = meMode ? "Décris ton action…" : "Écris un message…";
            ctx.text(font, Component.literal(hint).withStyle(ChatFormatting.ITALIC), visLeft, ty, INK_SOFT, false);
        }
        int caret = clamp(input.getCursorPosition(), 0, val.length());
        int wToCaret = font.width(val.substring(0, caret));
        int scroll = Math.max(0, wToCaret - r[2] + 6);
        int tx = visLeft - scroll;
        for (int i = 0; i < val.length(); i++) {
            Component ch = Component.literal(String.valueOf(val.charAt(i)));
            int cw = font.width(ch);
            long age = now - lastCharMs;
            if (s.animatedTyping && i == val.length() - 1 && age < 150) {
                float t = age / 150f, ease = 1f - (1f - t) * (1f - t);
                int col = (Math.min(255, (int) (ease * 220) + 35) << 24) | (INK & 0x00FFFFFF);
                switch (s.typingCursorStyle) {
                    case 1 -> ctx.text(font, ch, tx, ty, col, false);
                    case 2 -> ctx.text(font, ch, tx, ty + Math.round((1f - ease) * 4), col, false);
                    default -> {
                        float sc = 0.35f + 0.65f * ease;
                        ctx.pose().pushMatrix();
                        ctx.pose().translate(tx + cw / 2f, ty + 4f);
                        ctx.pose().scale(sc, sc);
                        ctx.text(font, ch, -cw / 2, -4, col, false);
                        ctx.pose().popMatrix();
                    }
                }
            } else {
                ctx.text(font, ch, tx, ty, INK, false);
            }
            tx += cw;
        }
        if (!searchFocused() && (now / 500) % 2 == 0) {
            int cx = visLeft - scroll + wToCaret;
            ctx.fill(cx, ty - 1, cx + 1, ty + 9, LACQUER);
        }
        ctx.disableScissor();
    }
}
