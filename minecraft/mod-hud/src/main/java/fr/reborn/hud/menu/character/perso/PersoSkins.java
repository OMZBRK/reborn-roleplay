package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textures de skin composées pour les écrans perso (même compositeur qu'en jeu), en cache par apparence ; variante
 * « mort » désaturée et refroidie comme la maquette. Libérées à la fermeture de l'écran.
 */
final class PersoSkins {

    private final Map<String, Identifier> cache = new LinkedHashMap<>();
    private int next;
    private final String prefixe;

    PersoSkins(String prefixe) { this.prefixe = prefixe; }

    /** Texture pour {@code spec} ({@code null} = skin du joueur local). */
    Identifier get(SkinSpec spec, boolean mort) {
        if (spec == null || spec.useOwnSkin) return propre();
        String key = (mort ? "m|" : "v|") + spec.serialize();
        Identifier id = cache.get(key);
        if (id != null) return id;
        NativeImage img;
        try { img = RebornSkins.compose(spec); } catch (RuntimeException e) { img = null; }
        if (img == null) return propre();
        if (mort) desaturer(img);
        id = Identifier.fromNamespaceAndPath("reborn", "perso/" + prefixe + (next++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "reborn-perso", img));
        cache.put(key, id);
        if (cache.size() > 120) {
            String first = cache.keySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(cache.remove(first));
        }
        return id;
    }

    static Identifier propre() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        try { return mc.player.getSkin().body().texturePath(); }
        catch (RuntimeException e) { return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"); }
    }

    void liberer() {
        for (Identifier id : cache.values()) Minecraft.getInstance().getTextureManager().release(id);
        cache.clear();
    }

    private static void desaturer(NativeImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getPixel(x, y);
                int a = c >>> 24;
                if (a == 0) continue;
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                float m = (r + g + b) / 3f;
                int nr = Math.round((r * 0.12f + m * 0.88f) * 0.72f + 22 * 0.28f);
                int ng = Math.round((g * 0.12f + m * 0.88f) * 0.72f + 26 * 0.28f);
                int nb = Math.round((b * 0.12f + m * 0.88f) * 0.72f + 40 * 0.28f);
                img.setPixel(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
    }
}
