package fr.reborn.hud.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-skins");

    /** UUID joueur → identifiant de la texture composée enregistrée. */
    private static final Map<UUID, Identifier> overrides = new ConcurrentHashMap<>();

    /** UUID joueur → carrure du perso composé ({@code true} = Alex/slim). */
    private static final Map<UUID, Boolean> slimModel = new ConcurrentHashMap<>();

    /**
     * Cache des <b>octets PNG</b> des peaux de base (clé = {@code male/3}, …). On
     * garde les octets bruts (et non un {@link NativeImage}) pour redécoder une
     * image <b>fraîche et mutable</b> à chaque {@link #compose} sans jamais muter
     * une image partagée. Les PNG sont statiques (bundlés) → lecture disque une
     * seule fois par teinte.
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
        slimModel.put(uuid, spec.slim);
    }

    /**
     * Construit une texture de skin 64×64 à partir de la spec. La <b>peau de base</b>
     * vient d'un vrai PNG livré ({@code character/skin/<genre>/<teinte>.png}, corps
     * entier peint) ; par-dessus on ajoute encore <b>procéduralement</b> la tenue,
     * la coiffure, la pilosité et les yeux (leurs vrais PNG viendront ensuite et
     * remplaceront ces aplats sans toucher au reste du pipeline).
     *
     * <p>La base peau est redécodée à neuf à chaque appel (image mutable) ; les
     * overlays sont peints dessus <b>sans détruire le visage</b> (les cheveux ne
     * couvrent que crâne/tempes/nuque + frange).
     */
    public static NativeImage compose(SkinSpec spec) {
        NativeImage img = loadSkinBase(spec.female, spec.skinStyle);
        if (img == null) {
            // Repli : aplat de peau si le PNG est illisible.
            img = new NativeImage(64, 64, false);
            img.fillRect(0, 0, 64, 64, argbToAbgr(SkinSpec.DEFAULT_SKIN));
        }

        int outfit = argbToAbgr(spec.outfitColor);
        int outfitDark = argbToAbgr(shade(spec.outfitColor, 0.82f));
        int hair = argbToAbgr(spec.hairColor);
        int eye = argbToAbgr(spec.eyeColor);
        int facial = argbToAbgr(spec.facialColor);
        int white = abgr(238, 238, 238, 255);

        // 1) Tenue (placeholder procédural) — dessinée sur les faces « base » du corps.
        //    0=torse nu (juste short), 1=débardeur, 2=veste, 3=manteau, 4=kimono.
        int oStyle = clampIdx(spec.outfitStyle, SkinSpec.OUTFIT_STYLES.length);
        img.fillRect(0, 16, 16, 16, outfitDark);  // jambe droite (pantalon)
        img.fillRect(16, 48, 16, 16, outfitDark); // jambe gauche (pantalon)
        if (oStyle >= 1) {
            img.fillRect(16, 16, 24, 16, outfit);     // torse
            if (oStyle >= 2) {
                img.fillRect(40, 16, 16, 16, outfit); // bras droit (manche)
                img.fillRect(32, 48, 16, 16, outfit); // bras gauche (manche)
            }
        }

        // 2) Coiffure (placeholder) : crâne + nuque + tempes + frange, SANS toucher
        //    au visage (les rangées du bas de la face restent en peau).
        int hStyle = clampIdx(spec.hairStyle, SkinSpec.HAIR_STYLES.length);
        int fringe = SkinSpec.HAIR_FRINGE[hStyle];
        if (hStyle > 0) { // "Chauve" = pas de cheveux
            int back = Math.min(fringe + 3, 8);       // hauteur de la nuque selon le style
            img.fillRect(8, 0, 8, 8, hair);           // dessus du crâne (face top)
            img.fillRect(24, 8, 8, back, hair);        // nuque (face arrière)
            if (fringe > 0) {
                img.fillRect(0, 8, 8, fringe, hair);   // tempe droite (face droite)
                img.fillRect(16, 8, 8, fringe, hair);  // tempe gauche (face gauche)
                img.fillRect(8, 8, 8, fringe, hair);   // frange (haut du visage)
            }
        }

        // 3) Yeux — un œil = 2×2 : colonne gauche BLANCHE, colonne droite = IRIS.
        //    (disposition demandée par le user ; teinte d'iris = eyeColor.)
        int eStyle = clampIdx(spec.eyeStyle, SkinSpec.EYE_STYLES.length);
        int eyeRow = clamp(8 + Math.max(fringe, 1), 9, 12); // sous la frange
        int eh = eStyle == 1 ? 1 : 2;             // "Fendus" = fente 1px ; sinon 2px
        drawEye(img, 9, eyeRow, eh, white, eye);  // œil gauche
        drawEye(img, 13, eyeRow, eh, white, eye); // œil droit

        // 4) Pilosité faciale (moustache/bouc/barbe) sur le bas du visage.
        int fStyle = clampIdx(spec.facialStyle, SkinSpec.FACIAL_STYLES.length);
        switch (fStyle) {
            case 1 -> img.fillRect(10, 13, 4, 1, facial);              // moustache
            case 2 -> img.fillRect(11, 13, 2, 3, facial);              // bouc
            case 3 -> { img.fillRect(9, 13, 6, 3, facial); img.fillRect(8, 12, 1, 3, facial); img.fillRect(15, 12, 1, 3, facial); } // barbe
            default -> { }
        }

        return img;
    }

    /** Un œil : colonne gauche blanche + colonne droite iris, sur {@code h} pixels. */
    private static void drawEye(NativeImage img, int x, int y, int h, int white, int iris) {
        img.fillRect(x, y, 1, h, white);
        img.fillRect(x + 1, y, 1, h, iris);
    }

    /**
     * Décode une image de peau <b>fraîche</b> (64×64) pour {@code (genre, teinte)}
     * depuis les resources bundlées, ou {@code null} si illisible. Les octets PNG
     * sont mis en cache ; l'image est ré-décodée à chaque appel (mutable, propre).
     */
    private static NativeImage loadSkinBase(boolean female, int style) {
        int s = clampIdx(style, SkinSpec.SKIN_TONES);
        String key = (female ? "female/" : "male/") + s;
        byte[] bytes = skinBytes.computeIfAbsent(key, RebornSkins::readSkinBytes);
        if (bytes == null) return null;
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            LOGGER.warn("décodage peau {} échec : {}", key, e.getMessage());
            return null;
        }
    }

    /** Lit les octets bruts du PNG de peau {@code key} (ex. {@code male/3}), ou null. */
    private static byte[] readSkinBytes(String key) {
        Identifier id = Identifier.fromNamespaceAndPath(
            "reborn", "textures/character/skin/" + key + ".png");
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) {
                LOGGER.warn("peau introuvable : {}", id);
                return null;
            }
            try (InputStream in = res.get().open()) {
                return in.readAllBytes();
            }
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

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
