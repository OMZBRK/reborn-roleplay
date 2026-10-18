package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Textures de skin composées pour les écrans perso (même compositeur qu'en jeu), en cache par apparence ; variante
 * « mort » désaturée et refroidie comme la maquette. Garde aussi une copie CPU de chaque skin (64×64 ARGB) et le cache
 * des poupées pré-rendues ({@link PersoPoupee#rendre}) : une texture par (skin, angle, échelle), recalculée seulement
 * quand l'un d'eux change. Tout est libéré à la fermeture de l'écran.
 */
final class PersoSkins {

    private final Map<String, Identifier> cache = new LinkedHashMap<>();
    private final Map<Identifier, int[]> pixels = new HashMap<>();
    private final Map<String, PersoPoupee.Image> poupees = new LinkedHashMap<>(64, 0.75f, true);
    private int next, nextPoupee;
    private final String prefixe;
    /** Rendus de poupée faits depuis l'ouverture (banc de perf). */
    int rendus;

    PersoSkins(String prefixe) { this.prefixe = prefixe; }

    /** Texture pour {@code spec} ({@code null} = skin du joueur local). */
    Identifier get(SkinSpec spec, boolean mort) {
        if (spec == null || spec.useOwnSkin) return propre();
        return get(spec.serialize(), spec, mort);
    }

    /** Idem avec la clé {@code spec.serialize()} déjà calculée. */
    Identifier get(String cle, SkinSpec spec, boolean mort) {
        // génération du catalogue dans la clé : un skin composé avant l'arrivée des assets live est refait
        String key = (mort ? "m|" : "v|") + fr.reborn.hud.skin.CharacterCatalog.generation() + "|" + cle;
        Identifier id = cache.get(key);
        if (id != null) return id;
        NativeImage img;
        try { img = RebornSkins.compose(spec); } catch (RuntimeException e) { img = null; }
        if (img == null) return propre();
        if (mort) desaturer(img);
        id = Identifier.fromNamespaceAndPath("reborn", "perso/" + prefixe + (next++));
        pixels.put(id, img.getPixels());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "reborn-perso", img));
        cache.put(key, id);
        if (cache.size() > 120) {
            String first = cache.keySet().iterator().next();
            Identifier vieux = cache.remove(first);
            pixels.remove(vieux);
            Minecraft.getInstance().getTextureManager().release(vieux);
        }
        return id;
    }

    static Identifier propre() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        try { return mc.player.getSkin().body().texturePath(); }
        catch (RuntimeException e) { return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"); }
    }

    /** Pixels ARGB 64×64 d'une texture de skin (composée ici, dynamique, ou ressource du jeu), ou null. */
    int[] pixels(Identifier id) {
        int[] p = pixels.get(id);
        if (p != null) return p;
        Minecraft mc = Minecraft.getInstance();
        NativeImage img = null;
        boolean fermer = false;
        try {
            AbstractTexture t = mc.getTextureManager().getTexture(id);
            if (t instanceof DynamicTexture dt && dt.getPixels() != null) img = dt.getPixels();
        } catch (RuntimeException ignored) { }
        if (img == null) {
            try {
                var res = mc.getResourceManager().getResource(id);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) { img = NativeImage.read(in); fermer = true; }
                }
            } catch (Exception ignored) { }
        }
        if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
            if (fermer && img != null) img.close();
            return null;
        }
        int[] src = img.getPixels();
        if (img.getHeight() == 32) {                       // ancien format : on étend en 64×64 sans seconde couche
            int[] e = new int[64 * 64];
            System.arraycopy(src, 0, e, 0, src.length);
            src = e;
        }
        if (fermer) img.close();
        pixels.put(id, src);
        return src;
    }

    /** Poupée pré-rendue (cache LRU, ~80 images) pour ce skin, cet angle et {@code p} pixels par texel. */
    PersoPoupee.Image poupee(Identifier skin, boolean slim, float lacet, float tangage, float p) {
        String key = skin + "|" + slim + "|" + String.format(Locale.ROOT, "%.1f|%.1f|%.2f", lacet, tangage, p);
        PersoPoupee.Image im = poupees.get(key);
        if (im != null) return im;
        int[] px = pixels(skin);
        if (px == null) px = pixels(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"));
        if (px == null) return null;
        float[] ancre = new float[2];
        NativeImage img = PersoPoupee.rendre(px, slim, lacet, tangage, p, ancre);
        Identifier id = Identifier.fromNamespaceAndPath("reborn", "perso/" + prefixe + "p" + (nextPoupee++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "reborn-poupee", img));
        im = new PersoPoupee.Image(id, img.getWidth(), img.getHeight(), ancre[0], ancre[1], p);
        poupees.put(key, im);
        rendus++;
        if (poupees.size() > 80) {
            var it = poupees.entrySet().iterator();
            Identifier vieux = it.next().getValue().id();
            it.remove();
            Minecraft.getInstance().getTextureManager().release(vieux);
        }
        return im;
    }

    void liberer() {
        for (Identifier id : cache.values()) Minecraft.getInstance().getTextureManager().release(id);
        for (PersoPoupee.Image im : poupees.values()) Minecraft.getInstance().getTextureManager().release(im.id());
        cache.clear();
        poupees.clear();
        pixels.clear();
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
