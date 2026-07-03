package fr.reborn.hud.menu.esc;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Rendu des 4 panels du ESC menu Reborn (Profile / Stream / Blog / Rewards)
 * + community bar. Référence : {@code esc-menu.jsx}.
 *
 * <p>Tout en static — les panels sont passifs (juste des renders, pas
 * de widgets cliquables custom à ce stade).
 *
 * <p>Données : placeholders hardcodés. Sera connecté aux endpoints API
 * en PR ulterieure (TODOs commentés).
 */
public final class EscPanels {

    private static final Identifier NINJA_SILHOUETTE =
        Identifier.of("reborn", "textures/gui/ninja_silhouette.png");
    private static final int NINJA_NATIVE_W = 200;
    private static final int NINJA_NATIVE_H = 280;

    private EscPanels() {}

    /** Profile panel — silhouette + currency + nom + role + stats. */
    public static void renderProfile(DrawContext ctx, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        // Card BG.
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // Header.
        ctx.drawText(tr, RebornFont.bold("PROFIL"),
            x + 16, y + 12, Colors.FOREGROUND_SUBTLE, false);

        // Silhouette ninja à gauche (PNG).
        int artW = 90;
        int artH = (NINJA_NATIVE_H * artW) / NINJA_NATIVE_W;
        int artX = x + 16;
        int artY = y + 32;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(artX, artY, 0);
        float artScale = (float) artW / NINJA_NATIVE_W;
        ctx.getMatrices().scale(artScale, artScale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(NINJA_SILHOUETTE, 0, 0, 0f, 0f,
            NINJA_NATIVE_W, NINJA_NATIVE_H, NINJA_NATIVE_W, NINJA_NATIVE_H);
        ctx.getMatrices().pop();

        // Meta à droite de la silhouette.
        int metaX = artX + artW + 16;
        int metaY = y + 36;

        // Currency.
        ctx.drawText(tr, RebornFont.bold("0 ZK"), metaX, metaY, Colors.WARNING, false);
        metaY += 16;

        // Nom RP (placeholder).
        // TODO: GET /v1/me/profile when API ready
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX, metaY, 0);
        ctx.getMatrices().scale(1.4f, 1.4f, 1f);
        ctx.drawText(tr, RebornFont.bold("Hikami Yorishiro"),
            0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();
        metaY += 22;

        // Handle.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX, metaY, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body("@hikami · #RBN-04217"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
        metaY += 18;

        // Role badge.
        Text roleText = RebornFont.bold("WHITELISTED");
        int roleTextW = tr.getWidth(roleText);
        int badgeW = roleTextW + 22;
        DrawHelpers.roundedOutlinedRect(ctx, metaX, metaY, badgeW, 16, 8,
            Colors.ACCENT_SOFT, Colors.withAlpha(Colors.ACCENT, 0.4f));
        DrawHelpers.disc(ctx, metaX + 10, metaY + 8, 3, Colors.ACCENT);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(metaX + 18, metaY + 4, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, roleText, 0, 0, Colors.ACCENT_HOVER, false);
        ctx.getMatrices().pop();
        metaY += 24;

        // Stats en bas.
        renderStat(ctx, tr, metaX, metaY, "Temps de jeu", "142 h");
        renderStat(ctx, tr, metaX + 100, metaY, "Dernière session", "Hier · 3h12");
        renderStat(ctx, tr, metaX + 220, metaY, "Faction", "Aucune");
    }

    private static void renderStat(DrawContext ctx, TextRenderer tr, int x, int y,
                                   String label, String value) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.8f, 0.8f, 1f);
        ctx.drawText(tr, RebornFont.body(label), 0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
        ctx.drawText(tr, RebornFont.bold(value), x, y + 10, Colors.WHITE_PURE, false);
    }

    /** Stream panel — placeholder LIVE 0. */
    public static void renderStream(DrawContext ctx, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // Header avec icone Twitch.
        IconPack.twitch(ctx, x + 14, y + 12, 16, Colors.FOREGROUND_SUBTLE);
        ctx.drawText(tr, RebornFont.bold("STREAM REBORN"),
            x + 36, y + 14, Colors.FOREGROUND_SUBTLE, false);

        // Badge "LIVE 0" à droite.
        Text liveText = RebornFont.bold("LIVE 0");
        int liveW = tr.getWidth(liveText) + 12;
        DrawHelpers.roundedOutlinedRect(ctx, x + w - liveW - 14, y + 12, liveW, 14, 6,
            Colors.DANGER_SOFT, Colors.withAlpha(Colors.DANGER, 0.4f));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w - liveW - 8, y + 16, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawText(tr, liveText, 0, 0, Colors.DANGER, false);
        ctx.getMatrices().pop();

        // Empty state au centre.
        String[] lines = {
            "Aucun stream en cours",
            "Reviens plus tard, ou lance le tien.",
        };
        int centerY = y + h / 2 - 16;
        Text line1 = RebornFont.bold(lines[0]);
        int line1W = tr.getWidth(line1);
        ctx.drawText(tr, line1, x + (w - line1W) / 2, centerY,
            Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, centerY + 14, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        Text line2 = RebornFont.body(lines[1]);
        int line2W = Math.round(tr.getWidth(line2) * 0.85f);
        ctx.drawText(tr, line2, (w - line2W) / 2 / 0.85f != 0
            ? (int)((w - line2W) / 0.85f / 2) : 0, 0,
            Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
    }

    /** Blog panel — placeholder dernier patch note. */
    public static void renderBlog(DrawContext ctx, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        ctx.drawText(tr, RebornFont.bold("DEV · BLOG"),
            x + 16, y + 14, Colors.FOREGROUND_SUBTLE, false);

        // Thumb placeholder.
        int thumbH = 60;
        DrawHelpers.roundedRect(ctx, x + 16, y + 36, w - 32, thumbH, 6,
            Colors.ACCENT_SOFT);

        // TODO: GET /v1/patchnotes/latest
        int textY = y + 36 + thumbH + 10;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 16, textY, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body("12 mai 2026"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
        textY += 14;
        ctx.drawText(tr, RebornFont.bold("Patch 1.0.5 — Refonte des arts"),
            x + 16, textY, Colors.WHITE_PURE, false);
        textY += 16;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 16, textY, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body(
            "Combats à mains nues réécrits, animations fluides."),
            0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
    }

    /** Rewards panel — placeholder 3 récompenses + timer. */
    public static void renderRewards(DrawContext ctx, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        ctx.drawText(tr, RebornFont.arcade("RECOMPENSES"),
            x + 16, y + 14, Colors.FOREGROUND_SUBTLE, false);

        Text badgeText = RebornFont.arcade("3/5 ACTIVES");
        int badgeW = tr.getWidth(badgeText) + 10;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w - badgeW - 14, y + 16, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawText(tr, badgeText, 0, 0, Colors.WARNING, false);
        ctx.getMatrices().pop();

        // TODO: GET /v1/me/rewards
        String[][] rewards = {
            {"PREMIUM", "+5 ZK/H", "on"},
            {"BOOSTER", "+5 ZK/H", "on"},
            {"TAG", "+5 ZK/H", "off"},
            {"BIO", "+5 ZK/H", "on"},
            {"STREAM", "+5 ZK/H", "off"},
        };

        int rowY = y + 36;
        for (String[] r : rewards) {
            boolean on = "on".equals(r[2]);
            // Dot status.
            DrawHelpers.disc(ctx, x + 22, rowY + 8, 4,
                on ? Colors.SUCCESS : Colors.MUTED);
            ctx.drawText(tr, RebornFont.arcade(r[0]), x + 36, rowY + 4,
                Colors.WHITE_PURE, false);
            Text val = RebornFont.arcade(r[1]);
            ctx.drawText(tr, val, x + w - 16 - tr.getWidth(val), rowY + 4,
                on ? Colors.SUCCESS : Colors.FOREGROUND_MUTED, false);
            rowY += 18;
        }

        // Timer en bas.
        ctx.drawText(tr, RebornFont.arcade("PROCHAINE 01:00:00"), x + 16, y + h - 18,
            Colors.FOREGROUND_MUTED, false);
    }

    /** Community bar floating en bas — quote + 4 social bubbles. */
    public static void renderCommunityBar(DrawContext ctx, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);

        // Handle + compteur Discord à gauche (ArcadePix). Données live via
        // EscData (endpoint public /menu/panel) ; fallback si indisponible.
        ctx.drawText(tr, RebornFont.arcade("REBORN ROLEPLAY  @REBORNOFF"),
            x + 16, y + 13, Colors.WHITE_PURE, false);
        EscData.Snapshot snap = EscData.get();
        String discordLine;
        if (snap != null && snap.discordMembers() >= 0) {
            discordLine = "DISCORD  " + snap.discordMembers() + " MEMBRES";
            if (snap.discordOnline() >= 0) {
                discordLine += "  " + snap.discordOnline() + " EN LIGNE";
            }
        } else {
            discordLine = "DISCORD  REJOINS LA COMMUNAUTE";
        }
        ctx.drawText(tr, RebornFont.arcade(discordLine),
            x + 16, y + 28, Colors.FOREGROUND_SUBTLE, false);

        // 4 social bubbles à droite (positions calculées).
        int bubbleSize = 28;
        int bubbleGap = 6;
        int totalBubbleW = 4 * bubbleSize + 3 * bubbleGap;
        int bubbleStartX = x + w - totalBubbleW - 16;
        int bubbleY = y + (h - bubbleSize) / 2;

        renderBubble(ctx, bubbleStartX, bubbleY, bubbleSize, IconPack::discord);
        renderBubble(ctx, bubbleStartX + (bubbleSize + bubbleGap), bubbleY, bubbleSize, IconPack::xLogo);
        renderBubble(ctx, bubbleStartX + 2 * (bubbleSize + bubbleGap), bubbleY, bubbleSize, IconPack::globe);
        renderBubble(ctx, bubbleStartX + 3 * (bubbleSize + bubbleGap), bubbleY, bubbleSize, IconPack::twitch);
    }

    @FunctionalInterface
    private interface IconDraw {
        void draw(DrawContext ctx, int x, int y, int size, int color);
    }

    private static void renderBubble(DrawContext ctx, int x, int y, int size, IconDraw icon) {
        DrawHelpers.disc(ctx, x + size / 2, y + size / 2, size / 2, Colors.SURFACE);
        DrawHelpers.ring(ctx, x + size / 2, y + size / 2, size / 2, 1, Colors.BORDER_STRONG);
        icon.draw(ctx, x + 7, y + 7, size - 14, Colors.FOREGROUND_SUBTLE);
    }
}
