package fr.reborn.integrity.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.integrity.ui.menu.IconButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Système d'icônes basé sur des textures PNG — fallback procédural si
 * le fichier n'est pas trouvé dans le resource pack du mod.
 *
 * <p>Permet d'utiliser de vraies icônes officielles (Lucide, Simple Icons,
 * Material Symbols, etc.) à la place des shapes procédurales d'{@link IconPack}.
 *
 * <h2>Pour activer les vraies icônes</h2>
 * Drop des PNGs blancs sur transparent (32×32 ou 64×64) dans
 * {@code src/main/resources/assets/reborn/textures/gui/icons/} avec les
 * noms suivants :
 *
 * <ul>
 *   <li>{@code settings.png} — Lucide "settings" (la roue dentée)</li>
 *   <li>{@code globe.png} — Lucide "globe"</li>
 *   <li>{@code discord.png} — Simple Icons "discord" (CC0)</li>
 *   <li>{@code play.png} — Lucide "play"</li>
 *   <li>{@code pause.png} — Lucide "pause"</li>
 *   <li>{@code volume.png} — Lucide "volume-2"</li>
 *   <li>{@code menu.png} — Lucide "list"</li>
 *   <li>{@code close.png} — Lucide "x"</li>
 *   <li>{@code prev.png} — Lucide "skip-back"</li>
 *   <li>{@code next.png} — Lucide "skip-forward"</li>
 * </ul>
 *
 * <p>Conseil : sur lucide.dev, export SVG → ouvre dans Inkscape → fond
 * transparent → fill blanc pur → export PNG 64×64. Pour Simple Icons,
 * c'est CC0 donc usage libre. Pour Discord en particulier, leur
 * branding guide autorise l'usage du logo pour pointer vers un serveur
 * Discord — voir discord.com/branding.
 *
 * <p>Sans PNG drop, le code utilise automatiquement le fallback
 * {@link IconPack} (procédural pixelisé).
 */
public final class IconTextures {

    private static final String BASE_PATH = "textures/gui/icons/";

    public static final Identifier SETTINGS = id("settings");
    public static final Identifier GLOBE    = id("globe");
    public static final Identifier DISCORD  = id("discord");
    public static final Identifier PLAY     = id("play");
    public static final Identifier PAUSE    = id("pause");
    public static final Identifier VOLUME   = id("volume");
    public static final Identifier MENU     = id("menu");
    public static final Identifier CLOSE    = id("close");
    public static final Identifier PREV     = id("prev");
    public static final Identifier NEXT     = id("next");

    /** Cache existence-check des PNGs — éviter la lookup au resource
     *  manager à chaque frame. Re-build au reload du resource pack. */
    private static final Map<Identifier, Boolean> EXISTS_CACHE = new ConcurrentHashMap<>();

    private IconTextures() {}

    private static Identifier id(String name) {
        return Identifier.of("reborn", BASE_PATH + name + ".png");
    }

    /**
     * Vérifie (avec cache) si la texture est dans le resource pack du mod.
     * Si oui, on draw via {@code ctx.drawTexture}. Sinon on tombe sur le
     * fallback procédural.
     */
    public static boolean exists(Identifier texId) {
        Boolean cached = EXISTS_CACHE.get(texId);
        if (cached != null) return cached;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;
        boolean present = mc.getResourceManager().getResource(texId).isPresent();
        EXISTS_CACHE.put(texId, present);
        return present;
    }

    /**
     * Vide le cache d'existence — appelé sur le reload du resource pack
     * via {@code SimpleSynchronousResourceReloadListener}. Pour l'instant
     * pas wired-up : le premier check au démarrage est suffisant pour
     * dev/prod (les PNGs ne bougent pas pendant qu'on joue).
     */
    public static void clearCache() {
        EXISTS_CACHE.clear();
    }

    /**
     * Construit un {@link IconButton.IconDrawer} qui tente d'abord la
     * texture PNG, et tombe sur le fallback procédural si le fichier
     * n'est pas dans le resource pack.
     *
     * <p>La couleur passée tinte la texture via {@code setShaderColor}
     * (multiplicative). Les PNGs doivent donc être blanc-sur-transparent
     * pour que tinting fonctionne.
     */
    public static IconButton.IconDrawer or(Identifier texId, IconButton.IconDrawer fallback) {
        return (ctx, x, y, size, color) -> {
            if (exists(texId)) {
                drawTinted(ctx, texId, x, y, size, color);
            } else {
                fallback.draw(ctx, x, y, size, color);
            }
        };
    }

    /**
     * Dessine une texture tintée par {@code color} (ARGB). Restaure
     * setShaderColor à blanc après pour ne pas polluer les rendus suivants.
     */
    public static void drawTinted(DrawContext ctx, Identifier texId,
                                  int x, int y, int size, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        ctx.drawTexture(texId, x, y, 0f, 0f, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
