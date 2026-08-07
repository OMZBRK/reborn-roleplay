package fr.reborn.hud.menu.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Background du main menu Reborn : remplace le trio gradient bleu + Chakra
 * + Sakura par un browser MCEF plein écran qui charge
 * {@code bbmodel_viewer.html} (asset Dynamic Animated Player). Affiche le
 * skin du joueur courant en 3D animé avec HDRI lighting.
 *
 * <p>Port 26.1 sur le fork <b>net.dimaskama:mcef-modern</b> (l'API cinemamod
 * d'origine + le rendu GL immédiat sont morts en 26.x) :
 * <ul>
 *   <li>{@link #init()} — {@link MCEFApi#initialize()} démarre le download/extract
 *       async des natives CEF (~150 Mo, une fois par machine), puis on crée le
 *       browser quand {@link MCEFApi#getInstanceFuture()} complète.</li>
 *   <li>{@link #render} — resize le browser à la taille réelle (pixels) puis
 *       blit sa {@link GpuTextureView} plein écran via la pipeline
 *       {@code GUI_TEXTURED} (helper natif {@code GuiGraphicsExtractor.blit}).</li>
 *   <li>Le browser singleton persiste entre les visites du TitleScreen :
 *       on évite ainsi un cold-start CEF (~500ms) à chaque retour menu.</li>
 * </ul>
 *
 * <p><b>Runtime</b> : mcef-modern doit être présent dans le modpack pour que le
 * CEF natif se charge. S'il est absent (dev sans le mod, plateforme non
 * supportée, natives KO), toutes les entrées MCEF lèvent (Throwable capturé) et
 * on retombe sur le solid color {@code 0xFF040611} — le menu reste utilisable.
 */
public final class DynamicPlayerBackground {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/dyn-bg");

    /** Couleur de fallback si MCEF KO (idem OUTER de l'ancien BackgroundRenderer). */
    private static final int FALLBACK_COLOR = 0xFF040611;

    private static volatile MCEFBrowser browser = null;
    private static volatile boolean schedulingFailed = false;
    private static volatile String viewerUrl = null;
    private static int lastResizeW = -1;
    private static int lastResizeH = -1;

    private DynamicPlayerBackground() {}

    /** À appeler une fois depuis {@code RebornIntegrityClient.onInitializeClient}. */
    public static void init() {
        Path html = DynamicPlayerAssets.extractIfNeeded();
        if (html == null) {
            LOG.warn("assets dynamic-player indisponibles -> fallback solid color");
            schedulingFailed = true;
            return;
        }
        viewerUrl = buildViewerUrl(html);
        LOG.info("MCEF: init CEF (download/extract natives async) pour {}", viewerUrl);
        try {
            // Démarre le pipeline d'init CEF (download -> extract -> install -> init).
            // Idempotent : un seul appel côté client-init. Retourne immédiatement.
            MCEFApi.initialize();
        } catch (Throwable t) {
            // mcef-modern absent du modpack (NoClassDefFoundError) ou natives KO.
            LOG.warn("MCEFApi.initialize() indisponible -> fallback solid color ({})",
                    t.toString());
            schedulingFailed = true;
            return;
        }
        MCEFApi.getInstanceFuture().thenAccept(api -> {
            try {
                browser = api.createBrowser(viewerUrl, /* transparent */ true);
                LOG.info("MCEF browser cree (transparent=true).");
            } catch (Throwable t) {
                LOG.error("creation du browser MCEF a echoue", t);
                schedulingFailed = true;
            }
        }).exceptionally(t -> {
            LOG.warn("init CEF a echoue -> fallback solid color", t);
            schedulingFailed = true;
            return null;
        });
    }

    /**
     * Construit l'URL {@code file://} + query string pour passer l'UUID
     * du joueur local au viewer (qui l'utilise pour charger le bon skin).
     */
    private static String buildViewerUrl(Path html) {
        String absolute = html.toAbsolutePath().toString().replace('\\', '/');
        if (!absolute.startsWith("/")) absolute = "/" + absolute;
        var uuid = Minecraft.getInstance().getUser().getProfileId();
        String uuidStr = uuid != null ? uuid.toString() : "";
        return "file://" + absolute + (uuidStr.isEmpty() ? "" : "?uuid=" + uuidStr);
    }

    /**
     * Render le browser plein écran. Si le browser n'est pas prêt
     * (CEF still booting, ~1-2s after launch), on dessine le fallback
     * solid color pour ne pas laisser un trou noir transparent.
     */
    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        MCEFBrowser b = browser;
        if (schedulingFailed || b == null) {
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
            return;
        }
        // MCEF travaille en pixels réels (le framebuffer offscreen est créé
        // à la taille demandée). On scale par window.getGuiScale() pour
        // ne pas avoir un rendu basse-res quand le GUI scale est > 1.
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int realW = (int) Math.max(1, Math.round(screenW * scale));
        int realH = (int) Math.max(1, Math.round(screenH * scale));
        if (realW != lastResizeW || realH != lastResizeH) {
            try {
                b.resize(realW, realH);
                lastResizeW = realW;
                lastResizeH = realH;
            } catch (Throwable t) {
                LOG.warn("browser.resize({},{}) a echoue", realW, realH, t);
            }
        }

        GpuTextureView view;
        try {
            view = b.getTextureView();
        } catch (Throwable t) {
            LOG.warn("getTextureView() a echoue", t);
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
            return;
        }
        if (view == null) {
            // Texture pas encore allouee (premiere frame post-resize) -> fallback.
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
            return;
        }

        try {
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            // blit(view, sampler, x0, x1, y0, y1, u0, u1, v0, v1) — ordre innerBlit
            // (x-pair puis y-pair). CEF rend en origine haut-gauche comme le GUI MC
            // -> pas de flip V (u0=0,u1=1 / v0=0,v1=1). Respecte la matrix du pose
            // stack (le TitleScreenMixin pousse un translate Z pour passer au-dessus
            // du panorama vanilla).
            ctx.blit(view, sampler, 0, screenW, 0, screenH, 0f, 1f, 0f, 1f);
        } catch (Throwable t) {
            LOG.warn("blit plein ecran du browser a echoue, fallback solid color", t);
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
        }
    }

    /** À appeler si le mod se décharge (rare, mais propre). */
    public static void dispose() {
        MCEFBrowser b = browser;
        if (b != null) {
            try {
                b.close();
            } catch (Throwable ignored) {
                // best-effort
            }
            browser = null;
        }
    }
}
