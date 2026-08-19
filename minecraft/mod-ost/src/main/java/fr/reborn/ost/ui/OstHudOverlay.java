package fr.reborn.ost.ui;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.audio.OstTrackMeta;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * HUD top-right « now-playing » (style Reborn) : petit disque + titre +
 * barre de progression. Auto-masquage après 5 s sans changement de piste.
 *
 * <p>26.1 : porté sur la nouvelle API HUD Fabric ({@link HudElementRegistry}
 * + rendu via {@link GuiGraphicsExtractor}). Le vinyl texturé tournant est
 * temporairement simplifié en pastille couleur (TODO polish : blit +
 * rotation Matrix3x2fStack sur la nouvelle pipeline de rendu).
 */
public final class OstHudOverlay {

    private static final long AUTO_HIDE_DELAY_MS = 5_000L;
    private static final int PAD = 6, RIGHT_MARGIN = 8, TOP_MARGIN = 8, VINYL_SZ = 22;

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("reborn-ost", "now-playing");
    private static final int BACKDROP = 0xCC0C0709;
    private static final int ACCENT   = 0xFFA0182B;
    private static final int GOLD     = 0xFFD9A95E;
    private static final int MUTED    = 0xFF9A8B78;

    private final OstAudioEngine engine;
    private String lastSeenTrackId = null;
    private long lastChangeAtMs = 0L;

    public OstHudOverlay(OstAudioEngine engine) {
        this.engine = engine;
    }

    public void registerClient() {
        HudElementRegistry.addLast(HUD_ID, this::render);
    }

    private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        Optional<OstTrack> currentOpt = engine.currentTrack();
        if (currentOpt.isEmpty()) { lastSeenTrackId = null; return; }
        OstTrack current = currentOpt.get();

        long now = System.currentTimeMillis();
        if (!current.trackId().equals(lastSeenTrackId)) {
            lastSeenTrackId = current.trackId();
            lastChangeAtMs = now;
        }
        if (client.gui.screen() != null) { lastChangeAtMs = now; return; }
        if (now - lastChangeAtMs > AUTO_HIDE_DELAY_MS) return;

        Font tr = client.font;
        String title = OstTrackMeta.title(current.trackId(), current.displayName());
        long el = engine.elapsedMs(), du = engine.durationMs();
        String time = mmss(el / 1000) + " / " + mmss(du / 1000);

        int textW = Math.max(tr.width("♪ " + title), tr.width(time));
        int w = PAD + VINYL_SZ + 8 + textW + PAD;
        int h = PAD + VINYL_SZ + PAD;
        int x = g.guiWidth() - w - RIGHT_MARGIN;
        int y = TOP_MARGIN;

        g.fill(x, y, x + w, y + h, BACKDROP);
        g.fill(x, y, x + w, y + 1, ACCENT);
        g.fill(x, y + h - 1, x + w, y + h, ACCENT);

        // Pastille categorie (placeholder du vinyl texture, TODO polish).
        int vx = x + PAD, vy = y + PAD;
        g.fill(vx, vy, vx + VINYL_SZ, vy + VINYL_SZ, categoryColor(current.category()));
        int cx = vx + VINYL_SZ / 2, cy = vy + VINYL_SZ / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x88000000);

        int tx = x + PAD + VINYL_SZ + 8;
        g.text(tr, Component.literal("♪ " + title), tx, y + PAD, GOLD, false);
        g.text(tr, Component.literal(time), tx, y + PAD + 11, MUTED, false);

        int bx = tx, bw = (x + w - PAD) - tx, by = y + h - PAD + 1;
        g.fill(bx, by, bx + bw, by + 2, 0x40FFFFFF);
        if (du > 0) {
            int fw = (int) (bw * Math.min(1.0, el / (double) du));
            g.fill(bx, by, bx + fw, by + 2, GOLD);
        }
    }

    private static int categoryColor(OstCategory cat) {
        return switch (cat) {
            case APAISANT -> 0xFF3FA89B;
            case COMBAT -> 0xFFB23A3A;
            case MISSION -> 0xFF3A6BB2;
            case MOTIVATION -> 0xFFD98E3A;
            case MYSTERE -> 0xFF7E3AB2;
            case TRISTE -> 0xFF5A6B7E;
            case FAVORIS -> 0xFFB23A6B;
        };
    }

    private static String mmss(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
