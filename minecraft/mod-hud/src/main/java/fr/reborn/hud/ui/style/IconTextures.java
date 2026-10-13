package fr.reborn.hud.ui.style;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    /** Côté natif des PNG : icônes du HUD (blanches, 96) et de la Sacoche (pixel art coloré, 32). */
    private static final int TEX_HUD = 96, TEX_SACOCHE = 32;
    private static final java.util.Set<String> HUD = java.util.Set.of("close", "undo", "redo", "gear", "menu", "discord");

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

        // Icônes pixel-art de la Sacoche (32×32, style MC/pixel raccord police).
        for (String n : new String[]{
                "f_all", "f_weapons", "f_blocks", "f_misc",
                "slot_bandeau", "slot_masque", "slot_manteau", "slot_dos", "slot_bag",
                "act_leaf", "act_equip", "act_drop", "act_trash", "ic_weight",
                // Cadres 32×32 dessinés main (bordure + fond parchemin baked-in).
                "cadre", "cadre_reborn_none", "cadre_all", "cadre_bag", "cadre_others"}) {
            bundled.put(n, Identifier.fromNamespaceAndPath("reborn-hud", "textures/icons/" + n + ".png"));
        }

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
     * Render une icône entière à (x, y), réduite ou agrandie à {@code size} GUI, teintée par {@code colorArgb}
     * (les PNG du HUD sont blancs sur transparent).
     */
    public static void draw(GuiGraphicsExtractor ctx, String name, int x, int y, int size, int colorArgb) {
        Identifier id = bundled.get(name);
        int tex = TEX_HUD;
        if (id == null) {
            tex = LAZY_SIZE;
            id = lazyRegistered.get(name);
            if (id == null) {
                id = tryCreateLazy(name);
                if (id == null) return;
            }
        } else if (!HUD.contains(name)) {
            tex = TEX_SACOCHE;
        }
        blitNet(ctx, id, x, y, size, tex, colorArgb);
    }

    /**
     * Dessine une icône pixel-art de la Sacoche (texture native 32×32, colorée, sans teinte) <b>entière</b>, réduite
     * à {@code drawSize} GUI. Retourne false si absente.
     */
    public static boolean drawIcon(GuiGraphicsExtractor ctx, String name, int x, int y, int drawSize) {
        Identifier id = bundled.get(name);
        if (id == null) return false;
        blitNet(ctx, id, x, y, drawSize, HUD.contains(name) ? TEX_HUD : TEX_SACOCHE, 0xFFFFFFFF);
        return true;
    }

    /**
     * Pose la texture entière ({@code tex}×{@code tex}) dans la case {@code size}×{@code size} GUI en (x, y), au pixel
     * écran : la taille rendue est un multiple ou un diviseur entier de la texture (chaque texel garde la même
     * largeur, aucun trait ne saute) quand on n'y perd pas plus de 15 % ; sinon la taille exacte de la case. Centrée.
     */
    private static void blitNet(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int size, int tex, int argb) {
        if (size <= 0 || (argb >>> 24) == 0) return;
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        int t = size * gs;
        int p = 0;
        if (t >= tex) {
            p = t / tex * tex;
        } else {
            for (int d = 2; d <= tex; d++) {
                if (tex % d == 0 && tex / d <= t) { p = tex / d; break; }
            }
        }
        if (p < t * 0.85f) p = t;
        int off = (t - p) / 2;
        ctx.pose().pushMatrix();
        ctx.pose().scale(1f / gs, 1f / gs);
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x * gs + off, y * gs + off, 0f, 0f, p, p, tex, tex, tex, tex, argb);
        ctx.pose().popMatrix();
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
                    img.setPixelABGR(xx, yy, (c == '#') ? 0xFFFFFFFF : 0x00000000);
                }
            }
            DynamicTexture tex = new DynamicTexture(() -> "reborn-tex", img);
            Identifier id = Identifier.fromNamespaceAndPath("reborn-hud", "icons/lazy/" + name);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            lazyRegistered.put(name, id);
            return id;
        } catch (RuntimeException e) {
            LOGGER.warn("lazy icon '{}' creation failed : {}", name, e.getMessage());
            return null;
        }
    }
}
