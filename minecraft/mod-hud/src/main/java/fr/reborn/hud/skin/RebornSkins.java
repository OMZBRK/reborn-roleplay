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

        int skin = argbToAbgr(spec.skinColor);
        int outfit = argbToAbgr(spec.outfitColor);
        int outfitDark = argbToAbgr(shade(spec.outfitColor, 0.82f));
        int hair = argbToAbgr(spec.hairColor);
        int eye = argbToAbgr(spec.eyeColor);
        int facial = argbToAbgr(spec.facialColor);
        int white = abgr(238, 238, 238, 255);
        int transparent = abgr(0, 0, 0, 0);

        // 1) Peau sur tout le corps (couche de base).
        img.fillRect(0, 0, 64, 64, skin);

        // 2) Efface les couches « overlay » indésirables (évite un modèle gonflé) :
        //    chapeau (32,0)-(64,16) + surcouches des bras.
        img.fillRect(32, 0, 32, 16, transparent); // hat
        img.fillRect(40, 32, 16, 16, transparent); // right-arm overlay
        img.fillRect(48, 48, 16, 16, transparent); // left-arm overlay

        // 3) Tenue : couverture selon le style.
        //    0=torse nu (juste short), 1=débardeur, 2=veste, 3=manteau, 4=kimono.
        int oStyle = clampIdx(spec.outfitStyle, SkinSpec.OUTFIT_STYLES.length);
        // Short/pantalon (toujours présent) sur les jambes.
        img.fillRect(0, 16, 16, 16, outfitDark);  // jambe droite base
        img.fillRect(0, 32, 16, 16, outfitDark);  // jambe droite overlay
        img.fillRect(16, 48, 16, 16, outfitDark); // jambe gauche base
        img.fillRect(0, 48, 16, 16, outfitDark);  // jambe gauche overlay
        if (oStyle >= 1) {
            // Torse habillé.
            img.fillRect(16, 16, 24, 16, outfit);
            img.fillRect(16, 32, 24, 16, outfit); // veste (overlay torse)
            if (oStyle >= 2) {
                // Manches (bras) pour veste/manteau/kimono.
                img.fillRect(40, 16, 16, 16, outfit); // bras droit base
                img.fillRect(32, 48, 16, 16, outfit); // bras gauche base
            }
        }

        // 4) Coiffure : bloc tête en cheveux, puis on restitue le visage.
        int hStyle = clampIdx(spec.hairStyle, SkinSpec.HAIR_STYLES.length);
        int fringe = SkinSpec.HAIR_FRINGE[hStyle];
        if (hStyle > 0) { // "Chauve" = pas de cheveux
            img.fillRect(0, 0, 32, 16, hair); // top + faces de la tête
            img.fillRect(16, 0, 8, 8, skin);  // dessous menton = peau
            img.fillRect(8, 8, 8, 8, skin);   // visage = peau
            if (fringe > 0) img.fillRect(8, 8, 8, Math.min(fringe, 6), hair); // frange
        }

        // 5) Pilosité faciale (moustache/bouc/barbe) sur le bas du visage.
        int fStyle = clampIdx(spec.facialStyle, SkinSpec.FACIAL_STYLES.length);
        switch (fStyle) {
            case 1 -> img.fillRect(10, 13, 4, 1, facial);              // moustache
            case 2 -> { img.fillRect(11, 13, 2, 3, facial); }          // bouc
            case 3 -> { img.fillRect(9, 13, 6, 3, facial); img.fillRect(8, 12, 1, 3, facial); img.fillRect(15, 12, 1, 3, facial); } // barbe
            default -> { }
        }

        // 6) Yeux (blanc + iris), forme selon le style.
        int eStyle = clampIdx(spec.eyeStyle, SkinSpec.EYE_STYLES.length);
        int eyeRow = 8 + Math.max(fringe, 2) + 1;
        if (eyeRow > 12) eyeRow = 12;
        int ew = eStyle == 2 ? 2 : 1; // "Ronds" = iris plus large
        img.fillRect(9, eyeRow, 2, 1, white);
        img.fillRect(13, eyeRow, 2, 1, white);
        img.fillRect(10, eyeRow, ew, 1, eye);
        img.fillRect(13, eyeRow, ew, 1, eye);
        if (eStyle == 1) { // "Fendus" : trait plus fin/allongé
            img.fillRect(9, eyeRow, 3, 1, eye);
            img.fillRect(13, eyeRow, 3, 1, eye);
        }

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
