package fr.reborn.hud.ui.style;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Registre des icônes du HUD editor.
 *
 * <ul>
 *   <li><strong>PNG bundlés</strong> dans
 *       {@code assets/reborn-hud/textures/icons/} (close, undo, redo,
 *       gear, menu, discord) → chargés par ResourceManager.</li>
 *   <li><strong>Patterns lazy-générés</strong> via {@link NativeImage} pour
 *       les icônes qu'on n'a pas en PNG (eye_open, eye_closed, search).
 *       Création différée au premier appel à {@link #draw} pour éviter
 *       les crashs d'init quand le TextureManager n'est pas prêt.</li>
 * </ul>
 */
public final class IconTextures {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/icons");
    private static final int LAZY_SIZE = 16;

    /** PNG bundlés : id stable. */
    private static final Map<String, Identifier> bundled = new HashMap<>();
    /** Patterns lazy-init : créés au premier draw. */
    private static final Map<String, String[]> lazyPatterns = new HashMap<>();
    /** Marqueur des lazy déjà créés (évite re-création). */
    private static final Map<String, Identifier> lazyRegistered = new HashMap<>();

    static {
        bundled.put("close",   Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/close.png"));
        bundled.put("undo",    Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/undo.png"));
        bundled.put("redo",    Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/redo.png"));
        bundled.put("gear",    Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/gear.png"));
        bundled.put("menu",    Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/menu.png"));
        bundled.put("discord", Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/discord.png"));

        // Patterns 16×16 pour les icônes qu'on n'a pas en PNG
        lazyPatterns.put("eye_open", new String[]{
            "................",
            "................",
            "....########....",
            "..##........##..",
            ".##....##....##.",
            "##....####....##",
            "##....####....##",
            ".##....##....##.",
            "..##........##..",
            "....########....",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"
        });
        lazyPatterns.put("eye_closed", new String[]{
            "................",
            "................",
            "................",
            "................",
            "....########....",
            "..##........##..",
            ".##..........##.",
            "##............##",
            ".##..........##.",
            "..############..",
            "...##......##...",
            "....##....##....",
            "................",
            "................",
            "................",
            "................"
        });
        lazyPatterns.put("search", new String[]{
            "................",
            "....######......",
            "..##......##....",
            ".##........##...",
            ".##........##...",
            "##..........##..",
            "##..........##..",
            "##..........##..",
            ".##........##...",
            ".##........##...",
            "..##......##....",
            "....######.##...",
            ".........##.....",
            "..........##....",
            "...........##...",
            "............##.."
        });
    }

    private IconTextures() {}

    public static void registerAll() {
        // No-op : PNG bundlés chargés à la demande par MC, lazy patterns
        // créés au premier draw.
    }

    /**
     * Render une icône à (x, y) avec une taille donnée et tint color ARGB.
     */
    public static void draw(GuiGraphicsExtractor ctx, String name, int x, int y, int size, int colorArgb) {
        Identifier id = bundled.get(name);
        if (id == null) {
            id = lazyRegistered.get(name);
            if (id == null) {
                id = tryCreateLazy(name);
                if (id == null) return;
            }
        }

        float a = ((colorArgb >>> 24) & 0xFF) / 255f;
        float r = ((colorArgb >>> 16) & 0xFF) / 255f;
        float g = ((colorArgb >>>  8) & 0xFF) / 255f;
        float b = ( colorArgb         & 0xFF) / 255f;
        RenderSystem.setShaderColor(r, g, b, a);
        ctx.drawTexture(id, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Crée la texture lazy depuis le pattern, ou null si pas trouvé. */
    private static Identifier tryCreateLazy(String name) {
        String[] pattern = lazyPatterns.get(name);
        if (pattern == null) return null;
        try {
            NativeImage img = new NativeImage(LAZY_SIZE, LAZY_SIZE, false);
            for (int yy = 0; yy < LAZY_SIZE; yy++) {
                String row = yy < pattern.length ? pattern[yy] : "";
                for (int xx = 0; xx < LAZY_SIZE; xx++) {
                    char c = xx < row.length() ? row.charAt(xx) : '.';
                    img.setColor(xx, yy, (c == '#') ? 0xFFFFFFFF : 0x00000000);
                }
            }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = Identifier.fromNamespaceAndPath("reborn-hud", "icons/lazy/" + name);
            Minecraft.getInstance().getTextureManager().registerTexture(id, tex);
            lazyRegistered.put(name, id);
            return id;
        } catch (RuntimeException e) {
            LOGGER.warn("lazy icon '{}' creation failed : {}", name, e.getMessage());
            return null;
        }
    }
}
