package fr.reborn.hud.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins composés Reborn (Phase 2 création de perso). Compose une texture 64×64
 * à partir de couches (base peau + overlays cheveux/yeux/tenues), l'enregistre
 * comme {@link DynamicTexture} dans le TextureManager, et expose son identifiant
 * pour que {@code AbstractClientPlayerSkinMixin} remplace le {@code body()} du
 * {@code PlayerSkin} du joueur → tout le monde (avec le mod) voit le skin composé.
 *
 * <p>Fondation : d'abord on valide l'<b>override du rendu</b> (une composition de
 * test appliquée au joueur local, testable solo). Les vrais assets + la synchro
 * par IDs cosmétiques (via {@code reborn:character}) viennent ensuite.
 */
public final class RebornSkins {

    private RebornSkins() {}

    /** UUID joueur → identifiant de la texture composée enregistrée. */
    private static final Map<UUID, Identifier> overrides = new ConcurrentHashMap<>();

    /** Identifiant de la texture composée pour ce joueur, ou {@code null}. */
    public static Identifier overrideFor(UUID uuid) {
        return overrides.get(uuid);
    }

    public static boolean hasOverride(UUID uuid) {
        return overrides.containsKey(uuid);
    }

    /**
     * Applique une <b>composition de test</b> (base peau + marqueur torse) au
     * joueur — sert à valider le pipeline override en solo. À remplacer par
     * {@code compose(cosmeticIds)} une fois les assets/branchés.
     */
    public static void applyTest(UUID uuid) {
        NativeImage img = new NativeImage(64, 64, false);
        img.fillRect(0, 0, 64, 64, abgr(216, 165, 125, 255));  // teinte peau partout
        img.fillRect(20, 20, 8, 12, abgr(63, 224, 154, 255));  // marqueur torse (teal)
        img.fillRect(8, 8, 8, 8, abgr(120, 78, 52, 255));      // "casquette" tête (test)
        register(uuid, img);
    }

    /**
     * Compose (ou retire) le skin du joueur à partir d'une {@link SkinSpec}.
     * Si {@link SkinSpec#useOwnSkin} est vrai, on retire l'override → le joueur
     * garde son skin Minecraft. Sinon on compose une texture 64×64 et on
     * l'enregistre.
     */
    public static void applySpec(UUID uuid, SkinSpec spec) {
        if (spec == null || spec.useOwnSkin) {
            clear(uuid);
            return;
        }
        register(uuid, compose(spec));
    }

    /**
     * Construit une texture de skin 64×64 <b>procédurale</b> à partir de la spec :
     * aplat de peau sur tout le corps, tenue sur torse/jambes, coiffure sur la
     * tête (frange selon le style), yeux sur le visage. Les vrais PNG (visages,
     * coiffures, tenues de clan) remplaceront ces aplats sans toucher au reste du
     * pipeline (override + synchro par IDs).
     */
    public static NativeImage compose(SkinSpec spec) {
        NativeImage img = new NativeImage(64, 64, false);

        int skin = argbToAbgr(lerp(SkinSpec.SKIN_DARK, SkinSpec.SKIN_LIGHT, spec.skinTone));
        int outfit = argbToAbgr(SkinSpec.OUTFIT_COLORS[clampIdx(spec.outfit, SkinSpec.OUTFIT_COLORS.length)]);
        int outfitDark = argbToAbgr(shade(SkinSpec.OUTFIT_COLORS[clampIdx(spec.outfit, SkinSpec.OUTFIT_COLORS.length)], 0.82f));
        int hair = argbToAbgr(SkinSpec.HAIR_COLORS[clampIdx(spec.hairColor, SkinSpec.HAIR_COLORS.length)]);
        int eye = argbToAbgr(SkinSpec.EYE_COLORS[clampIdx(spec.eyeColor, SkinSpec.EYE_COLORS.length)]);
        int white = abgr(238, 238, 238, 255);
        int transparent = abgr(0, 0, 0, 0);

        // 1) Peau sur tout le corps (couche de base).
        img.fillRect(0, 0, 64, 64, skin);

        // 2) On efface les couches « overlay » indésirables pour éviter un modèle
        //    gonflé : chapeau (32,0)-(64,16) + surcouches des bras.
        img.fillRect(32, 0, 32, 16, transparent); // hat
        img.fillRect(40, 32, 16, 16, transparent); // right-arm overlay
        img.fillRect(48, 48, 16, 16, transparent); // left-arm overlay

        // 3) Tenue : torse + jambes (couche base ET surcouche → look habillé).
        img.fillRect(16, 16, 24, 16, outfit); // torse base
        img.fillRect(16, 32, 24, 16, outfit); // torse overlay (veste)
        img.fillRect(0, 16, 16, 16, outfitDark); // jambe droite base
        img.fillRect(0, 32, 16, 16, outfitDark); // jambe droite overlay
        img.fillRect(16, 48, 16, 16, outfitDark); // jambe gauche base
        img.fillRect(0, 48, 16, 16, outfitDark); // jambe gauche overlay

        // 4) Coiffure : tout le bloc tête en cheveux, puis on restitue le visage.
        img.fillRect(0, 0, 32, 16, hair);   // top + faces de la tête en cheveux
        img.fillRect(16, 0, 8, 8, skin);    // dessous du menton (head-bottom) = peau
        img.fillRect(8, 8, 8, 8, skin);     // visage (front) = peau
        int fringe = SkinSpec.HAIR_FRINGE[clampIdx(spec.hairStyle, SkinSpec.HAIR_FRINGE.length)];
        if (fringe > 0) img.fillRect(8, 8, 8, Math.min(fringe, 8), hair); // frange sur le front

        // 5) Yeux sur le visage (blanc + iris).
        int eyeRow = 8 + Math.max(fringe, 2) + 1; // sous la frange
        if (eyeRow > 14) eyeRow = 14;
        img.fillRect(9, eyeRow, 2, 1, white);
        img.fillRect(13, eyeRow, 2, 1, white);
        img.fillRect(10, eyeRow, 1, 1, eye);
        img.fillRect(13, eyeRow, 1, 1, eye);

        return img;
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
    }

    public static void clearAll() {
        overrides.clear();
    }

    /** Couleur au format natif de NativeImage (RGBA → int ABGR). */
    private static int abgr(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** Convertit un ARGB (0xAARRGGBB) vers le format natif ABGR de NativeImage. */
    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF,
            g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        return abgr(r, g, b, a);
    }

    /** Interpolation linéaire entre deux ARGB. */
    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t), rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t), rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Assombrit un ARGB par un facteur (0..1). */
    private static int shade(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >>> 16) & 0xFF) * f);
        int g = Math.round(((argb >>> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampIdx(int i, int len) {
        return i < 0 ? 0 : i >= len ? len - 1 : i;
    }
}
