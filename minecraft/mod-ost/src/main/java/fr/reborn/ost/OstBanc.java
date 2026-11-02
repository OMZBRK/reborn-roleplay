package fr.reborn.ost;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstPlayback;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.config.OstConfig;
import fr.reborn.ost.ui.OstHudOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Banc de compatibilité du lecteur (0.2.6), <b>inactif en production</b> ({@code REBORN_OST_BANC=1}, monde solo de
 * dev avec au moins une piste dans {@code run/reborn/ost/<catégorie>/}) : joue la première piste de la bibliothèque,
 * capture lecture puis pause, saute à 3 s de la fin (lecture en flux : la fin du fichier arrête la piste), arrête et ferme le jeu → {@code run/screenshots/ostbanc_*.png}. Journal
 * {@code [ost-banc]} : qui dessine le lecteur (bandeau de secours de mod-ost, ou reborn-hud) et l'état publié dans
 * {@code reborn-ost:lecture} à chaque étape.
 */
final class OstBanc {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/banc");
    private static int t = -1;

    private OstBanc() {}

    static void init(OstLibrary library, OstAudioEngine engine, OstConfig config, OstHudOverlay overlay) {
        if (!"1".equals(System.getenv("REBORN_OST_BANC"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, library, engine, config, overlay));
    }

    private static void tick(Minecraft mc, OstLibrary library, OstAudioEngine engine, OstConfig config,
                             OstHudOverlay overlay) {
        if (mc.player == null || mc.level == null) { t = -1; return; }
        t++;
        switch (t) {
            case 40 -> {
                List<OstTrack> pistes = library.allTracks();
                LOGGER.info("[ost-banc] {} piste(s) ; lecteur dessiné par {}", pistes.size(),
                    overlay.secours() ? "reborn-ost (bandeau de secours)" : "reborn-hud");
                if (pistes.isEmpty()) { mc.stop(); return; }
                OstPlayback.INSTANCE.play(engine, config, pistes, 0);
            }
            case 80 -> { journal("lecture"); shot(mc, "ostbanc_01_lecture"); }
            case 84 -> engine.togglePause();
            case 110 -> { journal("pause"); shot(mc, "ostbanc_02_pause"); }
            case 114 -> engine.togglePause();
            case 140 -> { journal("reprise"); shot(mc, "ostbanc_03_reprise"); }
            // lecture en flux : saut à 3 s de la fin, puis fin naturelle (fin du fichier, pas une source à court de son)
            case 144 -> engine.seekMs(Math.max(0, engine.durationMs() - 3_000));
            case 150 -> journal("saut à 3 s de la fin");
            case 240 -> journal("après la fin");
            case 244 -> OstPlayback.INSTANCE.stop(engine);
            case 270 -> { journal("arrêt"); shot(mc, "ostbanc_04_arret"); }
            case 280 -> mc.stop();
            default -> { }
        }
    }

    private static void journal(String etape) {
        OstAudioEngine e = RebornOstClient.audioEngine();
        LOGGER.info("[ost-banc] {} : piste « {} », titre « {} », état {}, audible {}, position {} / {} ms", etape,
            OstLecture.piste(), OstLecture.titre(), OstLecture.etat(), OstLecture.audible(), e.elapsedMs(), e.durationMs());
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
    }
}
