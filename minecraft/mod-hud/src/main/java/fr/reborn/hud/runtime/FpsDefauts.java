package fr.reborn.hud.runtime;

import fr.reborn.hud.menu.settings.RebornPrefs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Réglages d'images par seconde (banc du 24/10, {@code audit/FpsDebug}) : les défauts de Minecraft brident le jeu à
 * <b>120 images par seconde avec la VSync</b>, et à <b>30 après une minute sans toucher au clavier</b> (« AFK ») — ce
 * qui arrive tout le temps en RP vocal. Une seule fois (drapeau {@link RebornPrefs#fpsVersion}), on lève ces trois
 * défauts : sans limite, sans VSync, ralenti seulement quand la fenêtre est réduite. Un réglage déjà personnalisé par le
 * joueur (autre limite, VSync remise exprès) n'est jamais touché ; tout se rechange dans les options vidéo.
 */
public final class FpsDefauts {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/fps");
    static final int VERSION = 1;
    private static final int ILLIMITE = 260, DEFAUT_VANILLA = 120;

    private FpsDefauts() {}

    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(FpsDefauts::migrer);
    }

    private static void migrer(Minecraft mc) {
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        prefs.ensureLoaded();
        if (prefs.fpsVersion >= VERSION || mc.options == null) return;
        var o = mc.options;
        boolean change = false;
        if (o.framerateLimit().get() == DEFAUT_VANILLA) { o.framerateLimit().set(ILLIMITE); change = true; }
        if (o.enableVsync().get()) { o.enableVsync().set(false); change = true; }
        if (o.inactivityFpsLimit().get() == InactivityFpsLimit.AFK) { o.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED); change = true; }
        if (change) {
            o.save();
            LOG.info("images par seconde : limite levée, VSync coupée, ralenti seulement fenêtre réduite");
        }
        prefs.fpsVersion = VERSION;
        prefs.save();
    }
}
