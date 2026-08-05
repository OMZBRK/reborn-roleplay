package fr.reborn.hud.menu.esc;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
        Identifier.fromNamespaceAndPath("reborn", "textures/gui/ninja_silhouette.png");
    private static final int NINJA_NATIVE_W = 200;
    private static final int NINJA_NATIVE_H = 280;

    private EscPanels() {}

    /** Profile panel — silhouette + currency + nom + role + stats. */
    public static void renderProfile(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        // Card BG.
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // Header.
        ctx.text(tr, RebornFont.bold("PROFIL"),
            x + 16, y + 12, Colors.FOREGROUND_SUBTLE, false);

        // Silhouette ninja à gauche (PNG).
        int artW = 90;
        int artH = (NINJA_NATIVE_H * artW) / NINJA_NATIVE_W;
        int artX = x + 16;
        int artY = y + 32;
        ctx.pose().pushMatrix();
        ctx.pose().translate(artX, artY);
        float artScale = (float) artW / NINJA_NATIVE_W;
        ctx.pose().scale(artScale, artScale);
        ;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, NINJA_SILHOUETTE, 0, 0, 0f, 0f,
            NINJA_NATIVE_W, NINJA_NATIVE_H, NINJA_NATIVE_W, NINJA_NATIVE_H);
        ctx.pose().popMatrix();

        // Meta à droite de la silhouette.
        int metaX = artX + artW + 16;
        int metaY = y + 36;

        // Currency.
        ctx.text(tr, RebornFont.bold("0 ZK"), metaX, metaY, Colors.WARNING, false);
        metaY += 16;

        // Nom RP (placeholder).
        // TODO: GET /v1/me/profile when API ready
        ctx.pose().pushMatrix();
        ctx.pose().translate(metaX, metaY);
        ctx.pose().scale(1.4f, 1.4f);
        ctx.text(tr, RebornFont.bold("Hikami Yorishiro"),
            0, 0, Colors.WHITE_PURE, false);
        ctx.pose().popMatrix();
        metaY += 22;

        // Handle.
        ctx.pose().pushMatrix();
        ctx.pose().translate(metaX, metaY);
        ctx.pose().scale(0.85f, 0.85f);
        ctx.text(tr, RebornFont.body("@hikami · #RBN-04217"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();
        metaY += 18;

        // Role badge.
        Component roleText = RebornFont.bold("WHITELISTED");
        int roleTextW = tr.width(roleText);
        int badgeW = roleTextW + 22;
        DrawHelpers.roundedOutlinedRect(ctx, metaX, metaY, badgeW, 16, 8,
            Colors.ACCENT_SOFT, Colors.withAlpha(Colors.ACCENT, 0.4f));
        DrawHelpers.disc(ctx, metaX + 10, metaY + 8, 3, Colors.ACCENT);
        ctx.pose().pushMatrix();
        ctx.pose().translate(metaX + 18, metaY + 4);
        ctx.pose().scale(0.85f, 0.85f);
        ctx.text(tr, roleText, 0, 0, Colors.ACCENT_HOVER, false);
        ctx.pose().popMatrix();
        metaY += 24;

        // Stats en bas.
        renderStat(ctx, tr, metaX, metaY, "Temps de jeu", "142 h");
        renderStat(ctx, tr, metaX + 100, metaY, "Dernière session", "Hier · 3h12");
        renderStat(ctx, tr, metaX + 220, metaY, "Faction", "Aucune");
    }

    private static void renderStat(GuiGraphicsExtractor ctx, Font tr, int x, int y,
                                   String label, String value) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(0.8f, 0.8f);
        ctx.text(tr, RebornFont.body(label), 0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();
        ctx.text(tr, RebornFont.bold(value), x, y + 10, Colors.WHITE_PURE, false);
    }

    /** Stream panel — placeholder LIVE 0. */
    public static void renderStream(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // Header avec icone Twitch.
        IconPack.twitch(ctx, x + 14, y + 12, 16, Colors.FOREGROUND_SUBTLE);
        ctx.text(tr, RebornFont.bold("STREAM REBORN"),
            x + 36, y + 14, Colors.FOREGROUND_SUBTLE, false);

        // Badge "LIVE 0" à droite.
        Component liveText = RebornFont.bold("LIVE 0");
        int liveW = tr.width(liveText) + 12;
        DrawHelpers.roundedOutlinedRect(ctx, x + w - liveW - 14, y + 12, liveW, 14, 6,
            Colors.DANGER_SOFT, Colors.withAlpha(Colors.DANGER, 0.4f));
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + w - liveW - 8, y + 16);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(tr, liveText, 0, 0, Colors.DANGER, false);
        ctx.pose().popMatrix();

        // Empty state au centre.
        String[] lines = {
            "Aucun stream en cours",
            "Reviens plus tard, ou lance le tien.",
        };
        int centerY = y + h / 2 - 16;
        Component line1 = RebornFont.bold(lines[0]);
        int line1W = tr.width(line1);
        ctx.text(tr, line1, x + (w - line1W) / 2, centerY,
            Colors.FOREGROUND_SUBTLE, false);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, centerY + 14);
        ctx.pose().scale(0.85f, 0.85f);
        Component line2 = RebornFont.body(lines[1]);
        int line2W = Math.round(tr.width(line2) * 0.85f);
        ctx.text(tr, line2, (w - line2W) / 2 / 0.85f != 0
            ? (int)((w - line2W) / 0.85f / 2) : 0, 0,
            Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();
    }

    /** Blog panel — placeholder dernier patch note. */
    public static void renderBlog(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        ctx.text(tr, RebornFont.bold("DEV · BLOG"),
            x + 16, y + 14, Colors.FOREGROUND_SUBTLE, false);

        // Thumb placeholder.
        int thumbH = 60;
        DrawHelpers.roundedRect(ctx, x + 16, y + 36, w - 32, thumbH, 6,
            Colors.ACCENT_SOFT);

        // TODO: GET /v1/patchnotes/latest
        int textY = y + 36 + thumbH + 10;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 16, textY);
        ctx.pose().scale(0.85f, 0.85f);
        ctx.text(tr, RebornFont.body("12 mai 2026"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();
        textY += 14;
        ctx.text(tr, RebornFont.bold("Patch 1.0.5 — Refonte des arts"),
            x + 16, textY, Colors.WHITE_PURE, false);
        textY += 16;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 16, textY);
        ctx.pose().scale(0.85f, 0.85f);
        ctx.text(tr, RebornFont.body(
            "Combats à mains nues réécrits, animations fluides."),
            0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.pose().popMatrix();
    }

    /** Rewards panel — placeholder 3 récompenses + timer. */
    public static void renderRewards(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        ctx.text(tr, RebornFont.arcade("RECOMPENSES"),
            x + 16, y + 14, Colors.FOREGROUND_SUBTLE, false);

        Component badgeText = RebornFont.arcade("3/5 ACTIVES");
        int badgeW = tr.width(badgeText) + 10;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + w - badgeW - 14, y + 16);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(tr, badgeText, 0, 0, Colors.WARNING, false);
        ctx.pose().popMatrix();

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
            ctx.text(tr, RebornFont.arcade(r[0]), x + 36, rowY + 4,
                Colors.WHITE_PURE, false);
            Component val = RebornFont.arcade(r[1]);
            ctx.text(tr, val, x + w - 16 - tr.width(val), rowY + 4,
                on ? Colors.SUCCESS : Colors.FOREGROUND_MUTED, false);
            rowY += 18;
        }

        // Timer en bas.
        ctx.text(tr, RebornFont.arcade("PROCHAINE 01:00:00"), x + 16, y + h - 18,
            Colors.FOREGROUND_MUTED, false);
    }

    /** Community bar floating en bas — quote + 4 social bubbles. */
    public static void renderCommunityBar(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);

        // Handle + compteur Discord à gauche (ArcadePix). Données live via
        // EscData (endpoint public /menu/panel) ; fallback si indisponible.
        ctx.text(tr, RebornFont.arcade("REBORN ROLEPLAY  @REBORNOFF"),
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
        ctx.text(tr, RebornFont.arcade(discordLine),
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
        void draw(GuiGraphicsExtractor ctx, int x, int y, int size, int color);
    }

    private static void renderBubble(GuiGraphicsExtractor ctx, int x, int y, int size, IconDraw icon) {
        DrawHelpers.disc(ctx, x + size / 2, y + size / 2, size / 2, Colors.SURFACE);
        DrawHelpers.ring(ctx, x + size / 2, y + size / 2, size / 2, 1, Colors.BORDER_STRONG);
        icon.draw(ctx, x + 7, y + 7, size - 14, Colors.FOREGROUND_SUBTLE);
    }
}
