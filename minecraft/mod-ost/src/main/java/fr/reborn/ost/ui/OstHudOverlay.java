package fr.reborn.ost.ui;

import fr.reborn.ost.OstLecture;
import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstTrack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bandeau « now-playing » de <b>secours</b>, en haut à droite : pastille de catégorie, titre, temps et barre de
 * progression, 5 s à chaque nouvelle piste.
 *
 * <p>Depuis 0.2.6 (lot 2), le lecteur est dessiné par {@code reborn-hud} (élément « Musique ») à partir de l'état publié
 * par {@link OstLecture}. Ce bandeau ne sert plus qu'avec un {@code reborn-hud} plus ancien : il n'est dessiné que si
 * {@code reborn-hud} n'a pas déposé {@value OstLecture#CLE_RENDU_HUD} dans l'{@code ObjectShare} au
 * {@code CLIENT_STARTED}. Corrigé au passage : il lit l'état publié (plus d'appel OpenAL ni de chaîne par image), se
 * tait hors de la zone d'une diffusion (piste inaudible), affiche le titre du HUD (la catégorie, jamais le nom de
 * fichier) et gèle son minuteur pendant qu'un écran est ouvert (il ne revenait 5 s après chaque fermeture d'écran,
 * tchat compris).
 */
public final class OstHudOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/hud");

    private static final long AUTO_HIDE_DELAY_MS = 5_000L;
    private static final int PAD = 6, RIGHT_MARGIN = 8, TOP_MARGIN = 8, VINYL_SZ = 22;

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("reborn-ost", "now-playing");
    private static final int BACKDROP = 0xCC0C0709;
    private static final int ACCENT   = 0xFFA0182B;
    private static final int GOLD     = 0xFFD9A95E;
    private static final int MUTED    = 0xFF9A8B78;

    private final OstAudioEngine engine;
    /** Faux dès que reborn-hud dessine le lecteur (décidé au CLIENT_STARTED). */
    private boolean secours = true;
    private String lastSeenTrackId = null;
    private Component titre = Component.empty();
    private int couleurCategorie = BACKDROP;
    private long montreMs, derniereImage;
    // Cache du libellé « m:ss / m:ss » : ne change qu'à la seconde près → on ne
    // reformate (String.format) que lorsque les secondes affichées changent.
    private long cachedElSec = -1L, cachedDuSec = -1L;
    private Component cachedTime = Component.empty();

    public OstHudOverlay(OstAudioEngine engine) {
        this.engine = engine;
    }

    public void registerClient() {
        HudElementRegistry.addLast(HUD_ID, this::render);
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            secours = FabricLoader.getInstance().getObjectShare().get(OstLecture.CLE_RENDU_HUD) == null;
            LOGGER.info(secours ? "lecteur OST : bandeau de secours de reborn-ost (reborn-hud ne le dessine pas)"
                : "lecteur OST : dessiné par reborn-hud (bandeau de secours coupé)");
        });
    }

    /** Vrai si ce bandeau est actif (reborn-hud ne dessine pas le lecteur). */
    public boolean secours() { return secours; }

    private void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!secours) return;
        Minecraft client = Minecraft.getInstance();
        long now = Util.getMillis();
        long dt = now - derniereImage;
        derniereImage = now;
        String id = OstLecture.piste();
        if (id.isEmpty() || !OstLecture.audible()) { lastSeenTrackId = null; return; }
        if (!id.equals(lastSeenTrackId)) {
            lastSeenTrackId = id;
            montreMs = 0L;
            dt = 0L;
            titre = Component.literal(OstLecture.titre());
            OstTrack t = engine.pisteOuNull();
            couleurCategorie = t != null ? categoryColor(t.category()) : BACKDROP;
            cachedElSec = -1L;
        }
        // minuteur gelé tant qu'un écran est ouvert (et pendant toute coupure du rendu)
        if (client.gui.screen() != null) return;
        if (dt > 0 && dt < 250) montreMs += dt;
        if (montreMs > AUTO_HIDE_DELAY_MS) return;

        Font tr = client.font;
        long el = engine.elapsedMs(), du = engine.durationMs();
        long elSec = el / 1000, duSec = du / 1000;
        if (elSec != cachedElSec || duSec != cachedDuSec) {
            cachedTime = Component.literal(mmss(elSec) + " / " + mmss(duSec));
            cachedElSec = elSec;
            cachedDuSec = duSec;
        }

        int textW = Math.max(tr.width(titre), tr.width(cachedTime));
        int w = PAD + VINYL_SZ + 8 + textW + PAD;
        int h = PAD + VINYL_SZ + PAD;
        int x = g.guiWidth() - w - RIGHT_MARGIN;
        int y = TOP_MARGIN;

        g.fill(x, y, x + w, y + h, BACKDROP);
        g.fill(x, y, x + w, y + 1, ACCENT);
        g.fill(x, y + h - 1, x + w, y + h, ACCENT);

        // Pastille de la catégorie.
        int vx = x + PAD, vy = y + PAD;
        g.fill(vx, vy, vx + VINYL_SZ, vy + VINYL_SZ, couleurCategorie);
        int cx = vx + VINYL_SZ / 2, cy = vy + VINYL_SZ / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x88000000);

        int tx = x + PAD + VINYL_SZ + 8;
        g.text(tr, titre, tx, y + PAD, GOLD, false);
        g.text(tr, cachedTime, tx, y + PAD + 11, MUTED, false);

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
