package fr.reborn.hud.menu.character.scene;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vignettes d'apparence pour la création : chaque coiffure / tenue est composée sur le perso en cours (même
 * compositeur qu'en jeu) puis affichée à plat — la tête de face pour les coiffures, le corps entier de face pour les
 * tenues. Cache par clé (asset + couleurs), libéré à la fermeture de l'écran.
 */
public final class SkinThumbs {

    private final Map<String, Identifier> cache = new LinkedHashMap<>();
    private int next;

    /** Texture composée pour {@code spec} (clé = ce qui la rend unique). */
    public Identifier get(String key, SkinSpec spec) {
        Identifier id = cache.get(key);
        if (id != null) return id;
        NativeImage img;
        try { img = RebornSkins.compose(spec); } catch (RuntimeException e) { img = null; }
        if (img == null) return null;
        id = Identifier.fromNamespaceAndPath("reborn", "thumbs/t" + (next++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "reborn-thumb", img));
        cache.put(key, id);
        if (cache.size() > 96) {                                    // garde-fou mémoire
            String first = cache.keySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(cache.remove(first));
        }
        return id;
    }

    public void releaseAll() {
        for (Identifier id : cache.values()) Minecraft.getInstance().getTextureManager().release(id);
        cache.clear();
    }

    private static void part(GuiGraphicsExtractor g, Identifier id, int x, int y, int u, int v, int pw, int ph, int s) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, pw * s, ph * s, pw, ph, 64, 64, 0xFFFFFFFF);
    }

    /** Tête de face (couche + chapeau), {@code s} pixels écran par pixel de skin. */
    public static void head(GuiGraphicsExtractor g, Identifier id, int x, int y, int s) {
        part(g, id, x, y, 8, 8, 8, 8, s);
        part(g, id, x, y, 40, 8, 8, 8, s);
    }

    /** Corps entier de face (tête, torse, bras, jambes + leurs couches de vêtement). */
    public static void body(GuiGraphicsExtractor g, Identifier id, int x, int y, int s, boolean slim) {
        int aw = slim ? 3 : 4;
        head(g, id, x + 4 * s, y, s);
        part(g, id, x + 4 * s, y + 8 * s, 20, 20, 8, 12, s);          // torse
        part(g, id, x + 4 * s, y + 8 * s, 20, 36, 8, 12, s);          // veste
        part(g, id, x + (4 - aw) * s, y + 8 * s, 44, 20, aw, 12, s);  // bras droit (à gauche à l'écran)
        part(g, id, x + (4 - aw) * s, y + 8 * s, 44, 36, aw, 12, s);
        part(g, id, x + 12 * s, y + 8 * s, 36, 52, aw, 12, s);        // bras gauche
        part(g, id, x + 12 * s, y + 8 * s, 52, 52, aw, 12, s);
        part(g, id, x + 4 * s, y + 20 * s, 4, 20, 4, 12, s);          // jambe droite
        part(g, id, x + 4 * s, y + 20 * s, 4, 36, 4, 12, s);
        part(g, id, x + 8 * s, y + 20 * s, 20, 52, 4, 12, s);         // jambe gauche
        part(g, id, x + 8 * s, y + 20 * s, 4, 52, 4, 12, s);
    }
}
