package fr.reborn.hud.menu.widget;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.BufferRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexFormat;
import net.minecraft.client.renderer.VertexFormats;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Background du main menu Reborn : remplace le trio gradient bleu + Chakra
 * + Sakura par un browser MCEF plein écran qui charge
 * {@code bbmodel_viewer.html} (asset Dynamic Animated Player). Affiche le
 * skin du joueur courant en 3D animé avec HDRI lighting.
 *
 * <p>Lifecycle :
 * <ul>
 *   <li>{@link #init()} appelé une fois depuis {@code RebornIntegrityClient}.
 *       Schedule la création du browser via {@link MCEF#scheduleForInit}.</li>
 *   <li>{@link #render} appelé chaque frame par {@code MainMenuRenderer}.
 *       Resize le browser quand la fenêtre change, le dessine plein écran
 *       en blittant la texture OpenGL offscreen du renderer CEF.</li>
 *   <li>Le browser singleton persiste entre les visites du TitleScreen :
 *       on évite ainsi un cold-start CEF (~500ms) à chaque retour menu.</li>
 * </ul>
 *
 * <p>Fallback : si l'extraction des assets a échoué ou si MCEF n'a pas
 * réussi à init (natives manquantes, plateforme non supportée), on
 * dessine un solid color sombre {@code 0xFF040611} pour conserver un
 * fond neutre et ne pas bloquer le menu.
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
        LOG.info("scheduling MCEF browser init pour {}", viewerUrl);
        MCEF.scheduleForInit(success -> {
            if (!success) {
                LOG.warn("MCEF init a echoue (success=false), fallback solid color.");
                schedulingFailed = true;
                return;
            }
            try {
                browser = MCEF.createBrowser(viewerUrl, true);
                LOG.info("MCEF browser cree (transparent=true).");
            } catch (Throwable t) {
                LOG.error("creation du browser MCEF a echoue", t);
                schedulingFailed = true;
            }
        });
    }

    /**
     * Construit l'URL {@code file://} + query string pour passer l'UUID
     * du joueur local au viewer (qui l'utilise pour charger le bon skin).
     */
    private static String buildViewerUrl(Path html) {
        String absolute = html.toAbsolutePath().toString().replace('\\', '/');
        if (!absolute.startsWith("/")) absolute = "/" + absolute;
        var uuid = Minecraft.getInstance().getSession().getUuidOrNull();
        String uuidStr = uuid != null ? uuid.toString() : "";
        return "file://" + absolute + (uuidStr.isEmpty() ? "" : "?uuid=" + uuidStr);
    }

    /**
     * Render le browser plein écran. Si le browser n'est pas prêt
     * (CEF still booting, ~1-2s after launch), on dessine le fallback
     * solid color pour ne pas laisser un trou noir transparent.
     */
    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        if (schedulingFailed || browser == null) {
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
            return;
        }
        // MCEF travaille en pixels réels (le framebuffer offscreen est créé
        // à la taille demandée). On scale par window.getScaleFactor() pour
        // ne pas avoir un rendu basse-res quand le GUI scale est > 1.
        double scale = Minecraft.getInstance().getWindow().getScaleFactor();
        int realW = (int) Math.max(1, Math.round(screenW * scale));
        int realH = (int) Math.max(1, Math.round(screenH * scale));
        if (realW != lastResizeW || realH != lastResizeH) {
            try {
                browser.resize(realW, realH);
                lastResizeW = realW;
                lastResizeH = realH;
            } catch (Throwable t) {
                LOG.warn("browser.resize({},{}) a echoue", realW, realH, t);
            }
        }

        int textureId;
        try {
            textureId = browser.getRenderer().getTextureID();
        } catch (Throwable t) {
            LOG.warn("getRenderer().getTextureID() a echoue", t);
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
            return;
        }
        if (textureId == 0) {
            // Texture pas encore allouee (premiere frame post-resize) -> fallback.
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
            return;
        }

        try {
            renderTextureFullscreen(ctx, textureId, screenW, screenH);
        } catch (Throwable t) {
            LOG.warn("rendu plein ecran browser a echoue, fallback solid color", t);
            ctx.fill(0, 0, screenW, screenH, FALLBACK_COLOR);
        }
    }

    /**
     * Blit la texture OpenGL du browser CEF sur un quad plein écran. On
     * passe par RenderSystem + Tessellator + BufferRenderer plutôt que
     * par GuiGraphicsExtractor.drawTexture parce que la texture est un ID GL brut
     * (pas un Identifier MC).
     *
     * <p>Pattern repris de {@code com.cinemamod.mcef.example.ExampleScreen}
     * mais adapté en plein écran et en respectant la matrix transform du
     * GuiGraphicsExtractor (le TitleScreenMixin pousse un translate Z=400 pour
     * passer au-dessus du panorama vanilla).
     */
    private static void renderTextureFullscreen(GuiGraphicsExtractor ctx, int textureId, int w, int h) {
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, textureId);

        Matrix4f matrix = ctx.pose().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.begin(
            VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        // Winding identique à ExampleScreen : BL -> BR -> TR -> TL.
        // UV (0,1)/(1,1)/(1,0)/(0,0) — Y-flip implicite (CEF rend top-down,
        // GL texture coordinate origin bottom-left).
        buf.vertex(matrix, 0, h, 0).texture(0, 1).color(255, 255, 255, 255);
        buf.vertex(matrix, w, h, 0).texture(1, 1).color(255, 255, 255, 255);
        buf.vertex(matrix, w, 0, 0).texture(1, 0).color(255, 255, 255, 255);
        buf.vertex(matrix, 0, 0, 0).texture(0, 0).color(255, 255, 255, 255);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
    }

    /** À appeler si le mod se décharge (rare, mais propre). */
    public static void dispose() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Throwable ignored) {
                // best-effort
            }
            browser = null;
        }
    }
}
