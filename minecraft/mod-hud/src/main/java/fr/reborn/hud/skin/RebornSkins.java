package fr.reborn.hud.skin;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.skin.CharacterCatalog.Asset;
import fr.reborn.hud.skin.CharacterCatalog.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins composés Reborn (Phase 2 création de perso). Compose une texture 64×64 à
 * partir de couches (base peau + cosmétiques du {@link CharacterCatalog}),
 * l'enregistre comme {@link DynamicTexture} et expose son identifiant pour que
 * {@code AbstractClientPlayerSkinMixin} remplace le {@code body()} du joueur → tout
 * le monde (avec le mod + le même catalogue bundlé) voit le skin composé.
 *
 * <h3>Pipeline des cosmétiques</h3>
 * Chaque asset est <b>peint en couleur pleine</b> (64×64, transparent hors zone) et
 * <b>blitté tel quel</b> à ses coordonnées. La recoloration est optionnelle :
 * <ul>
 *   <li><b>tint {@code all}</b> — toute la zone est reteintée par la couleur choisie
 *       (luminance de la texture préservée → l'ombrage reste). Utilisé pour cheveux/pilosité.</li>
 *   <li><b>tint {@code red}</b> — les pixels « rouges » (iris) sont teintés, le reste gardé,
 *       split gauche/droite pour l'hétérochromie. Utilisé pour les yeux.</li>
 *   <li><b>masque RGBA</b> — si {@code <id>_Mask.png} existe et que l'asset déclare des
 *       {@link Zone}s, chaque canal (R/G/B/A) devient une zone recolorée en HSL par sa
 *       couleur. Sinon (défaut tenues) la texture s'affiche telle que peinte.</li>
 * </ul>
 *
 * <h3>Ordre des calques</h3>
 * peau → tatouage → tenue (Complet/Haut/Bas) → yeux → pilosité → <b>cheveux</b>.
 * Les cheveux passent en dernier : ils recouvrent le col de la tenue (logique RP).
 */
public final class RebornSkins {

    private RebornSkins() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-skins");

    /** Couleur par défaut du sous-vêtement (tint=all) — gris-beige neutre proche du peint. */
    private static final int UNDERWEAR_DEFAULT = 0xFFB0A090;

    /** UUID joueur → identifiant de la texture composée enregistrée. */
    private static final Map<UUID, Identifier> overrides = new ConcurrentHashMap<>();

    /** UUID joueur → carrure du perso composé ({@code true} = Alex/slim). */
    private static final Map<UUID, Boolean> slimModel = new ConcurrentHashMap<>();

    /**
     * Cache des <b>octets PNG</b> des peaux de base (clé = {@code male/3}, …). On garde
     * les octets bruts pour redécoder une image <b>fraîche et mutable</b> à chaque
     * {@link #compose} sans muter une image partagée.
     */
    private static final Map<String, byte[]> skinBytes = new ConcurrentHashMap<>();

    /** Identifiant de la texture composée pour ce joueur, ou {@code null}. */
    public static Identifier overrideFor(UUID uuid) {
        return overrides.get(uuid);
    }

    public static boolean hasOverride(UUID uuid) {
        return overrides.containsKey(uuid);
    }

    /** Carrure du skin composé pour ce joueur : {@code true} = Alex (bras 3px). */
    public static boolean isSlim(UUID uuid) {
        return slimModel.getOrDefault(uuid, false);
    }

    /**
     * Applique un message de diffusion {@code reborn:skins} = {@code <uuid>\n<queue
     * serialize()>}. Apparence vide → retrait de l'override (skin MC normal). Permet à
     * chaque client d'afficher le skin RP composé des AUTRES joueurs. Thread client.
     */
    public static void applyBroadcast(String content) {
        if (content == null) return;
        int nl = content.indexOf('\n');
        String uuidStr = (nl < 0 ? content : content.substring(0, nl)).trim();
        String appearance = nl < 0 ? "" : content.substring(nl + 1);
        UUID uuid;
        try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { return; }
        if (appearance.isBlank()) {
            clear(uuid);
        } else {
            applySpec(uuid, SkinSpec.deserialize(appearance));
        }
    }

    /** Composition de test (base peau + marqueurs) — valide le pipeline override en solo. */
    public static void applyTest(UUID uuid) {
        NativeImage img = new NativeImage(64, 64, false);
        img.fillRect(0, 0, 64, 64, 0xFFD8A57D);
        img.fillRect(20, 20, 8, 12, 0xFF3FE09A);
        img.fillRect(8, 8, 8, 8, 0xFF784E34);
        register(uuid, img);
    }

    /**
     * Compose (ou retire) le skin du joueur à partir d'une {@link SkinSpec}. Si
     * {@link SkinSpec#useOwnSkin}, on retire l'override → skin Minecraft du joueur.
     */
    public static void applySpec(UUID uuid, SkinSpec spec) {
        if (spec == null || spec.useOwnSkin) {
            clear(uuid);
            return;
        }
        register(uuid, compose(spec));
        slimModel.put(uuid, spec.slim);
    }

    /**
     * Construit la texture de skin 64×64 : base peau (PNG livré) puis les cosmétiques
     * du catalogue, dans l'ordre des calques (cheveux en dernier). Chaque cosmétique
     * est résolu par son {@code id} et blitté/teinté selon son mode.
     */
    public static NativeImage compose(SkinSpec spec) {
        NativeImage img = composeSkinBase(spec);

        // 0) Sous-vêtement (selon le genre) = couche par défaut au-dessus de la peau.
        //    Couvert par une tenue/haut/bas quand le joueur en porte une ; sinon visible.
        //    tint=all → le PNG sert de masque : recolore tout le sous-vêtement par une
        //    couleur (ombrage du tissu préservé). Couleur par défaut neutre (le choix
        //    joueur viendra avec la boutique).
        overlayAsset(img, CharacterCatalog.byId("underwear", spec.female ? "Sous_Femme" : "Sous_Homme"),
            new int[] { UNDERWEAR_DEFAULT });
        // 1) Tenue (sous les cheveux). Complet couvre torse/bras/jambes ; Haut/Bas viendront.
        overlayAsset(img, CharacterCatalog.byId("outfit", spec.outfitId), spec.outfitZone);
        // 2) Yeux — iris (rouge) teinté 2 teintes, split gauche/droite (hétérochromie).
        overlayAsset(img, CharacterCatalog.byId("eyes", spec.eyeId),
            new int[] { spec.eyeColor, spec.eyeColorRight });
        // 3) Pilosité faciale.
        overlayAsset(img, CharacterCatalog.byId("facial", spec.facialId), new int[] { spec.facialColor });
        // 4) Cheveux — par-dessus le col de la tenue + la frange sur le front.
        overlayAsset(img, CharacterCatalog.byId("hair", spec.hairId), new int[] { spec.hairColor });
        // 5) Accessoire (bandeau…) — EN DERNIER, par-dessus les cheveux.
        overlayAsset(img, CharacterCatalog.byId("accessory", spec.accessoryId),
            new int[] { spec.accessoryColor });

        return img;
    }

    /**
     * Peau de base : recolore le <b>template de luminance</b> ({@code <genre>/N.png})
     * par {@link SkinSpec#skinColor} en préservant l'ombrage (facteur = luma /
     * luma_médiane), et recolore les <b>sourcils cuits</b> par {@link SkinSpec#browColor}.
     * L'image est redécodée à neuf (mutable) puis mutée en place (alpha préservé).
     */
    private static NativeImage composeSkinBase(SkinSpec spec) {
        SkinTemplate t = loadSkinTemplate(spec.female);
        if (t == null) {
            NativeImage fb = new NativeImage(64, 64, false);
            fb.fillRect(0, 0, 64, 64, spec.skinColor);
            return fb;
        }
        NativeImage img;
        try {
            img = NativeImage.read(new ByteArrayInputStream(t.bytes));
        } catch (Exception e) {
            NativeImage fb = new NativeImage(64, 64, false);
            fb.fillRect(0, 0, 64, 64, spec.skinColor);
            return fb;
        }
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getPixel(x, y);
                if (((argb >>> 24) & 0xFF) == 0) continue;
                int idx = y * 64 + x;
                if (idx < t.brow.length && t.brow[idx]) {
                    img.setPixel(x, y, spec.browColor);
                } else {
                    float lum = 0.299f * ((argb >> 16) & 0xFF)
                        + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF);
                    img.setPixel(x, y, tintSkin(spec.skinColor, lum, t.refLuma));
                }
            }
        }
        return img;
    }

    /** Couleur de peau modulée par l'ombrage : {@code skinColor × (luma / luma_médiane)}. */
    private static int tintSkin(int skinColor, float lum, float refLuma) {
        float f = refLuma <= 0f ? 1f : lum / refLuma;
        int r = clamp255(Math.round(((skinColor >> 16) & 0xFF) * f));
        int g = clamp255(Math.round(((skinColor >> 8) & 0xFF) * f));
        int b = clamp255(Math.round((skinColor & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp255(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }

    /**
     * Overlaye un asset du catalogue sur le skin. {@code colors} porte les couleurs de
     * recoloration : yeux = {@code {gauche, droite}} ; cheveux/pilosité = {@code {couleur}} ;
     * tenue = {@code outfitZone[0..3]} indexé par canal (R,G,B,A). Sans asset → no-op.
     */
    private static void overlayAsset(NativeImage img, Asset asset, int[] colors) {
        if (asset == null) return;
        Tex tex = loadTex(asset);
        if (tex == null) return;
        Mask mask = asset.zones.isEmpty() ? null : loadMask(asset);

        for (int i = 0; i < tex.xs.length; i++) {
            int x = tex.xs[i], y = tex.ys[i], base = tex.argb[i];
            int out;
            if (mask != null) {
                // Recoloration par zones de masque (chaque canal → une couleur).
                out = base;
                for (Zone z : asset.zones) {
                    float w = mask.value(x, y, z.channel()) / 255f;
                    if (w <= 0f) continue;
                    int col = colorForChannel(colors, z.channel(), z.defaultColor());
                    out = blend(out, tintLuma(base, col, tex.refLuma), w);
                }
            } else {
                out = switch (asset.tint) {
                    case ALL -> tintLuma(base, colors.length > 0 ? colors[0] : 0xFFFFFFFF, tex.refLuma);
                    case RED -> isRed(base)
                        ? tintShade(pickSplit(colors, x, asset.split), (base >> 16) & 0xFF, tex.refRed)
                        : base;
                    default -> base; // NONE : blit tel que peint
                };
            }
            img.setPixel(x, y, out);
        }
    }

    /** Couleur d'une zone selon son canal : R→0, G→1, B→2, A→3 dans {@code colors}. */
    private static int colorForChannel(int[] colors, char ch, int fallback) {
        int idx = switch (ch) { case 'G' -> 1; case 'B' -> 2; case 'A' -> 3; default -> 0; };
        return idx < colors.length ? colors[idx] : fallback;
    }

    private static int pickSplit(int[] colors, int x, int split) {
        int left = colors.length > 0 ? colors[0] : 0xFFFFFFFF;
        int right = colors.length > 1 ? colors[1] : left;
        return x <= split ? left : right;
    }

    /** « Rouge » = zone teintable (mode RED). */
    private static boolean isRed(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return r > g * 1.5f && r > b * 1.5f && r > 50;
    }

    /**
     * Reteinte préservant l'ombrage : couleur cible modulée par la luminance du pixel
     * de base (facteur = luma / luma_max). Donne teinte+saturation de la couleur, avec
     * la luminance (ombres/reflets) de la texture d'origine.
     */
    private static int tintLuma(int base, int target, float refLuma) {
        float lum = 0.299f * ((base >> 16) & 0xFF) + 0.587f * ((base >> 8) & 0xFF) + 0.114f * (base & 0xFF);
        float f = refLuma <= 0f ? 1f : Math.min(1f, lum / refLuma);
        int r = Math.round(((target >> 16) & 0xFF) * f);
        int g = Math.round(((target >> 8) & 0xFF) * f);
        int b = Math.round((target & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Teinte mode RED : {@code couleur × (rouge_pixel / rouge_max)} → préserve les nuances. */
    private static int tintShade(int argb, int pixelRed, int refRed) {
        float f = refRed <= 0 ? 1f : Math.min(1f, pixelRed / (float) refRed);
        int r = Math.round(((argb >> 16) & 0xFF) * f);
        int g = Math.round(((argb >> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Mélange {@code a} vers {@code b} par {@code w}∈[0,1] (canaux RGB, alpha opaque). */
    private static int blend(int a, int b, float w) {
        w = w < 0f ? 0f : w > 1f ? 1f : w;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * w);
        int g = Math.round(ag + (bg - ag) * w);
        int bl = Math.round(ab + (bb - ab) * w);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ── Décodage + cache des textures cosmétiques ─────────────────────

    /** Pixels opaques d'une texture cosmétique + repères de teinte (luma/rouge max). */
    private record Tex(int[] xs, int[] ys, int[] argb, float refLuma, int refRed) {}

    /** Masque RGBA (grille 64×64) : {@link #value} isole un canal en (x,y). */
    private record Mask(int[] grid, int w, int h) {
        int value(int x, int y, char ch) {
            if (x < 0 || y < 0 || x >= w || y >= h) return 0;
            int argb = grid[y * w + x];
            return switch (ch) {
                case 'G' -> (argb >> 8) & 0xFF;
                case 'B' -> argb & 0xFF;
                case 'A' -> (argb >>> 24) & 0xFF;
                default -> (argb >> 16) & 0xFF; // R
            };
        }
    }

    private static final Map<String, Tex> texCache = new ConcurrentHashMap<>();
    private static final Map<String, Mask> maskCache = new ConcurrentHashMap<>();
    /** Sentinelle « pas de masque » pour éviter de relire un fichier absent. */
    private static final Mask NO_MASK = new Mask(new int[0], 0, 0);

    private static Tex loadTex(Asset asset) {
        return texCache.computeIfAbsent(asset.folder + ":" + asset.id, k -> decodeTex(asset));
    }

    private static Tex decodeTex(Asset asset) {
        try (InputStream in = RebornSkins.class.getResourceAsStream(asset.texturePath())) {
            if (in == null) { LOGGER.warn("asset introuvable : {}", asset.texturePath()); return null; }
            NativeImage ni = NativeImage.read(in);
            List<int[]> ps = new ArrayList<>();
            float refLuma = 1f;
            int refRed = 1;
            for (int y = 0; y < ni.getHeight(); y++) {
                for (int x = 0; x < ni.getWidth(); x++) {
                    int argb = ni.getPixel(x, y);
                    if (((argb >>> 24) & 0xFF) == 0) continue;
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    float lum = 0.299f * r + 0.587f * g + 0.114f * b;
                    if (lum > refLuma) refLuma = lum;
                    if (r > g * 1.5f && r > b * 1.5f && r > 50 && r > refRed) refRed = r;
                    ps.add(new int[] { x, y, argb });
                }
            }
            ni.close();
            int n = ps.size();
            int[] xs = new int[n], ys = new int[n], argb = new int[n];
            for (int i = 0; i < n; i++) { int[] p = ps.get(i); xs[i] = p[0]; ys[i] = p[1]; argb[i] = p[2]; }
            LOGGER.info("asset {} chargé ({} px, luma max={}, rouge max={})",
                asset.id, n, Math.round(refLuma), refRed);
            return new Tex(xs, ys, argb, refLuma, refRed);
        } catch (Exception e) {
            LOGGER.warn("décodage {} échec : {}", asset.id, e.getMessage());
            return null;
        }
    }

    private static Mask loadMask(Asset asset) {
        Mask m = maskCache.computeIfAbsent(asset.folder + ":" + asset.id, k -> decodeMask(asset));
        return m == NO_MASK ? null : m;
    }

    private static Mask decodeMask(Asset asset) {
        try (InputStream in = RebornSkins.class.getResourceAsStream(asset.maskPath())) {
            if (in == null) return NO_MASK; // pas de masque → tenue affichée telle que peinte
            NativeImage ni = NativeImage.read(in);
            int w = ni.getWidth(), h = ni.getHeight();
            int[] grid = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) grid[y * w + x] = ni.getPixel(x, y);
            ni.close();
            LOGGER.info("masque {} chargé ({}×{})", asset.id, w, h);
            return new Mask(grid, w, h);
        } catch (Exception e) {
            LOGGER.warn("décodage masque {} échec : {}", asset.id, e.getMessage());
            return NO_MASK;
        }
    }

    // ── Template de peau (luminance + sourcils) ───────────────────────

    /** Template de peau décodé : octets PNG (re-décodés à chaud), luma médiane, sourcils. */
    private record SkinTemplate(byte[] bytes, float refLuma, boolean[] brow) {}

    private static final Map<String, SkinTemplate> skinTemplates = new ConcurrentHashMap<>();

    /** Charge (cache) le template de peau du genre : {@code <genre>/{SKIN_TEMPLATE}.png}. */
    private static SkinTemplate loadSkinTemplate(boolean female) {
        String key = (female ? "female/" : "male/") + SkinSpec.SKIN_TEMPLATE;
        return skinTemplates.computeIfAbsent(key, RebornSkins::decodeSkinTemplate);
    }

    private static SkinTemplate decodeSkinTemplate(String key) {
        byte[] bytes = skinBytes.computeIfAbsent(key, RebornSkins::readSkinBytes);
        if (bytes == null) return null;
        try {
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bytes));
            int w = ni.getWidth(), h = ni.getHeight();
            // Médiane de luminance des pixels opaques (robuste aux yeux blancs / sourcils sombres).
            List<Float> lumas = new ArrayList<>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = ni.getPixel(x, y);
                    if (((argb >>> 24) & 0xFF) == 0) continue;
                    lumas.add(0.299f * ((argb >> 16) & 0xFF)
                        + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF));
                }
            }
            float refLuma = 105f; // repli si template vide
            if (!lumas.isEmpty()) {
                lumas.sort(Float::compareTo);
                refLuma = lumas.get(lumas.size() / 2);
                if (refLuma <= 1f) refLuma = 105f;
            }
            // Sourcils = pixels sombres du rectangle visage (8..15, 8..15) du template.
            boolean[] brow = new boolean[64 * 64];
            float browThresh = 0.55f * refLuma;
            for (int y = 8; y < 16 && y < h; y++) {
                for (int x = 8; x < 16 && x < w; x++) {
                    int argb = ni.getPixel(x, y);
                    if (((argb >>> 24) & 0xFF) == 0) continue;
                    float lum = 0.299f * ((argb >> 16) & 0xFF)
                        + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF);
                    if (lum < browThresh) brow[y * 64 + x] = true;
                }
            }
            ni.close();
            LOGGER.info("template peau {} (luma médiane={})", key, Math.round(refLuma));
            return new SkinTemplate(bytes, refLuma, brow);
        } catch (Exception e) {
            LOGGER.warn("décodage template peau {} échec : {}", key, e.getMessage());
            return null;
        }
    }

    private static byte[] readSkinBytes(String key) {
        String path = "/assets/reborn/textures/character/skin/" + key + ".png";
        try (InputStream in = RebornSkins.class.getResourceAsStream(path)) {
            if (in == null) { LOGGER.warn("peau introuvable (classpath) : {}", path); return null; }
            byte[] bytes = in.readAllBytes();
            LOGGER.info("peau chargée {} ({} o)", key, bytes.length);
            return bytes;
        } catch (Exception e) {
            LOGGER.warn("lecture peau {} échec : {}", key, e.getMessage());
            return null;
        }
    }

    /** Enregistre l'image composée comme texture dynamique et mémorise l'override. */
    public static void register(UUID uuid, NativeImage composed) {
        Minecraft mc = Minecraft.getInstance();
        Identifier id = Identifier.fromNamespaceAndPath(
            "reborn", "skins/" + uuid.toString().replace("-", ""));
        mc.getTextureManager().register(id, new DynamicTexture(() -> "reborn-skin", composed));
        overrides.put(uuid, id);
    }

    public static void clear(UUID uuid) {
        overrides.remove(uuid);
        slimModel.remove(uuid);
    }

    public static void clearAll() {
        overrides.clear();
        slimModel.clear();
    }

    private static int clampIdx(int i, int len) {
        return i < 0 ? 0 : i >= len ? len - 1 : i;
    }
}
