package fr.reborn.hud.skin;

import com.mojang.blaze3d.platform.NativeImage;
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
     * Applique un message de diffusion {@code reborn:skins} = {@code <uuid>\n<queue
     * serialize()>}. Apparence vide → retrait de l'override (skin MC normal). Permet
     * à chaque client d'afficher le skin RP composé des AUTRES joueurs. À appeler
     * sur le thread client.
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

    /**
     * Applique une <b>composition de test</b> (base peau + marqueur torse) au
     * joueur — sert à valider le pipeline override en solo. À remplacer par
     * {@code compose(cosmeticIds)} une fois les assets/branchés.
     */
    public static void applyTest(UUID uuid) {
        NativeImage img = new NativeImage(64, 64, false);
        img.fillRect(0, 0, 64, 64, 0xFFD8A57D);  // teinte peau partout (ARGB)
        img.fillRect(20, 20, 8, 12, 0xFF3FE09A); // marqueur torse (teal)
        img.fillRect(8, 8, 8, 8, 0xFF784E34);    // "casquette" tête (test)
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
            img.fillRect(0, 0, 64, 64, SkinSpec.DEFAULT_SKIN);
        }

        // ⚠️ fillRect appelle setPixel = format ARGB (0xAARRGGBB). Les couleurs de la
        // spec SONT déjà en ARGB → on les passe telles quelles (surtout PAS d'abgr,
        // sinon R et B sont inversés : brun → bleu, etc.). La base peau vient de
        // NativeImage.read (format natif) et n'est pas touchée par ces overlays.
        int outfit = spec.outfitColor;
        int hair = spec.hairColor;
        int facial = spec.facialColor;

        // 1) Tenue (placeholder procédural) — pour l'instant SEUL le torse peut être
        //    habillé (débardeur/veste…). **Jambes ET bras laissés en PEAU** pour
        //    montrer le skin peint (pas encore d'assets de tenue/manches/pantalon).
        //    0=torse nu, 1=débardeur, 2=veste, 3=manteau, 4=kimono.
        int oStyle = clampIdx(spec.outfitStyle, SkinSpec.OUTFIT_STYLES.length);
        if (oStyle >= 1) {
            img.fillRect(16, 16, 24, 16, outfit); // torse
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

        // 3) Yeux — texture PNG (eyes/<style>.png) overlayée telle quelle : la
        //    sclérotique (pixels clairs) est conservée, l'IRIS (pixels rouges) est
        //    teinté par la couleur de l'œil en préservant ses 2 teintes (rouge foncé
        //    → couleur foncée, rouge clair → couleur claire). Œil gauche/droit =
        //    couleurs séparées (hétérochromie) : split sur x (nez = x11/12).
        overlayEyes(img, spec.eyeStyle, spec.eyeColor, spec.eyeColorRight);

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

    /**
     * Overlay des yeux depuis la texture {@code eyes/<style>.png}. Les pixels
     * <b>clairs</b> (sclérotique) sont recopiés tels quels ; les pixels <b>rouges</b>
     * (iris) sont teintés par la couleur de l'œil (gauche si x≤11, droit sinon) en
     * préservant leurs teintes (facteur = rouge_pixel / rouge_max). Repli procédural
     * si la texture manque.
     */
    private static void overlayEyes(NativeImage img, int style, int eyeLeft, int eyeRight) {
        EyeTex tex = loadEyeTex(style);
        if (tex == null) {
            drawEyeFallback(img, 9, 13, eyeLeft);
            drawEyeFallback(img, 13, 13, eyeRight);
            return;
        }
        for (int i = 0; i < tex.x.length; i++) {
            int x = tex.x[i], y = tex.y[i];
            int color;
            if (tex.iris[i]) {
                int base = x <= 11 ? eyeLeft : eyeRight;
                color = tintIris(base, (tex.argb[i] >> 16) & 0xFF, tex.refRed);
            } else {
                color = tex.argb[i]; // sclérotique conservée
            }
            img.setPixel(x, y, color);
        }
    }

    /** Teinte un iris : {@code couleur × (rouge_pixel / rouge_max)} (préserve les 2 teintes). */
    private static int tintIris(int argb, int pixelRed, int refRed) {
        float f = refRed <= 0 ? 1f : Math.min(1f, pixelRed / (float) refRed);
        int r = Math.round(((argb >> 16) & 0xFF) * f);
        int g = Math.round(((argb >> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Repli procédural (2×2, blanc externe + iris interne) si pas de texture d'yeux. */
    private static void drawEyeFallback(NativeImage img, int x, int y, int iris) {
        img.fillRect(x, y, 1, 2, 0xFFEEEEEE);
        img.fillRect(x + 1, y, 1, 2, iris);
    }

    /** Pixels d'une texture d'yeux décodée (parallèles) + rouge max de l'iris. */
    private record EyeTex(int[] x, int[] y, int[] argb, boolean[] iris, int refRed) {}

    private static final Map<Integer, EyeTex> eyeCache = new ConcurrentHashMap<>();

    /** Charge (cache) la texture d'yeux du style, avec repli sur {@code eyes/0.png}. */
    private static EyeTex loadEyeTex(int style) {
        int s = clampIdx(style, SkinSpec.EYE_STYLES.length);
        return eyeCache.computeIfAbsent(s, k -> {
            EyeTex t = decodeEyeTex(k);
            return t != null ? t : (k != 0 ? decodeEyeTex(0) : null);
        });
    }

    /** Décode {@code eyes/<style>.png} : liste des pixels opaques + détection iris. */
    private static EyeTex decodeEyeTex(int style) {
        String path = "/assets/reborn/textures/character/eyes/" + style + ".png";
        try (InputStream in = RebornSkins.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.warn("yeux introuvables (classpath) : {}", path);
                return null;
            }
            NativeImage ni = NativeImage.read(in);
            List<int[]> ps = new ArrayList<>();
            int refRed = 1;
            for (int y = 0; y < ni.getHeight(); y++) {
                for (int x = 0; x < ni.getWidth(); x++) {
                    int argb = ni.getPixel(x, y);
                    if (((argb >>> 24) & 0xFF) == 0) continue;
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    boolean iris = r > g * 1.5f && r > b * 1.5f && r > 50; // « rouge » = iris
                    ps.add(new int[] { x, y, argb, iris ? 1 : 0 });
                    if (iris && r > refRed) refRed = r;
                }
            }
            ni.close();
            int n = ps.size();
            int[] xs = new int[n], ys = new int[n], argbs = new int[n];
            boolean[] iris = new boolean[n];
            for (int i = 0; i < n; i++) {
                int[] p = ps.get(i);
                xs[i] = p[0]; ys[i] = p[1]; argbs[i] = p[2]; iris[i] = p[3] == 1;
            }
            LOGGER.info("yeux chargés style {} ({} px, iris rouge max={})", style, n, refRed);
            return new EyeTex(xs, ys, argbs, iris, refRed);
        } catch (Exception e) {
            LOGGER.warn("décodage yeux {} échec : {}", style, e.getMessage());
            return null;
        }
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

    /**
     * Lit les octets bruts du PNG de peau {@code key} (ex. {@code male/3}) depuis
     * le <b>classpath</b> du mod (jar), ou {@code null} si absent. On lit direct
     * dans le jar plutôt que via le {@code ResourceManager} MC : indépendant du
     * cycle de reload des packs et des quirks de mapping.
     */
    private static byte[] readSkinBytes(String key) {
        String path = "/assets/reborn/textures/character/skin/" + key + ".png";
        try (InputStream in = RebornSkins.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.warn("peau introuvable (classpath) : {}", path);
                return null;
            }
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
