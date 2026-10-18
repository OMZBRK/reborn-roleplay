package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emblèmes des écrans perso, construits AU LANCEMENT depuis les vraies textures du mod — villages
 * {@code gui/esc/village_<v>.png}, clans {@code gui/clans/clan_<id>.png} (blasons canon, cf. tools/gen_clan_emblemes.py)
 * — au lieu d'être cuits dans les textures générées (qui ont déjà affiché d'anciens logos après une mise à jour des
 * blasons). Un blason ajouté ou redessiné dans gui/clans/ apparaît donc tel quel, sans régénération.
 *
 * <p>Atlas des emblèmes (même contrat que l'ancien emblemes.png) : colonnes de 64 texels, villages puis clans
 * (ordre alphabétique des fichiers) ; rangée y 0 en 64×64, y 64 en 32×32, y 96 en 24×24, y 120 en 56×56 ; blancs,
 * luminance gardée (55 % à 100 %), alpha net. Atlas « kamon » (emblèmes couchés gravés sur le dais) : anneau 116×30
 * en (0, 0) (repris de kamon_sol.png), emblèmes 50×15 dans des cellules 52×16 en (52·(i % 8), 30 + 16·(i / 8)).
 */
final class PersoEmblemes {

    private PersoEmblemes() {}

    static final String[] VILLAGES = PersoArt.VILLAGES;
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("reborn", "perso/emblemes"),
        KAMON = Identifier.fromNamespaceAndPath("reborn", "perso/kamon");
    private static List<String> clans;
    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static int atlasW = 64, kamonH = 46;
    private static boolean pret;

    static Identifier atlas() { pret(); return ATLAS; }

    static int atlasW() { pret(); return atlasW; }

    static Identifier kamon() { pret(); return KAMON; }

    static int kamonH() { pret(); return kamonH; }

    /** Index du clan dans la liste (repli : « autre »). */
    static int clan(String slug) {
        pret();
        Integer i = INDEX.get(slug == null ? "" : slug);
        if (i == null) i = INDEX.get("autre");
        return i == null ? 0 : i;
    }

    static int nbClans() { pret(); return clans.size(); }

    /** Force la reconstruction (rechargement des ressources). */
    static void invalider() { pret = false; sceauxPret = false; }

    /* ------------------------------------------------------------- sceau du registre (récapitulatif) */

    private static final Identifier SCEAUX = Identifier.fromNamespaceAndPath("reborn", "perso/sceaux_registre");
    /** Cellule d'un sceau : 80 texels = 40 GUI (le sceau est composé à la demi-texel GUI). */
    static final int SCEAU_TEX = 80, SCEAU_GUI = 40;
    /** Pente du tampon (degrés, sens trigonométrique à l'écran : le coin haut droit remonte). */
    private static final double PENTE = -5;
    private static int sceauxW = SCEAU_TEX;
    private static boolean sceauxPret;
    /** Marque du sceau : « village » (défaut), « clan » ou « kanji » (l'ancien 認) ; -Dreborn.sceauRegistre, banc. */
    static String sceauMode = System.getProperty("reborn.sceauRegistre", "village");

    static Identifier sceaux() { sceauxPret(); return SCEAUX; }

    static int sceauxW() { sceauxPret(); return sceauxW; }

    /** Cellule du sceau : index d'emblème (villages puis clans, comme l'atlas) ; {@link #sceauKanji()} pour 認. */
    static int sceauKanji() { pret(); return VILLAGES.length + clans.size(); }

    private static String chemin(int k) {
        return k < VILLAGES.length ? "textures/gui/esc/village_" + VILLAGES[k] + ".png"
            : "textures/gui/clans/clan_" + clans.get(k - VILLAGES.length) + ".png";
    }

    /**
     * Sceaux du registre, construits au lancement comme les emblèmes : tampon vermillon de
     * {@code personnage/sceau_registre.png} (cellule 0 sans marque, 1 avec l'ancien 認, 2 masque d'usure ; 32×32,
     * sceau 30×30 non tourné, tools/gen_personnage_textures.py) doublé, emblème du village ou du clan réservé couleur
     * papier (40 texels = 20 GUI, échantillonné 4×4 dans le repère penché : traits nets), ombre de la réserve, manques
     * d'encre, pente de 5°, ombre portée. Une cellule 80×80 par emblème, puis la variante 認.
     */
    private static void sceauxPret() {
        if (sceauxPret) return;
        sceauxPret = true;
        pret();
        Minecraft mc = Minecraft.getInstance();
        ResourceManager rm = mc.getResourceManager();
        int n = VILLAGES.length + clans.size();
        sceauxW = SCEAU_TEX * (n + 1);
        NativeImage im = new NativeImage(sceauxW, SCEAU_TEX, true);
        vider(im);
        float[][] tex = lireBrut(rm, PersoArt.tex("sceau_registre"));
        if (tex != null && Math.round(tex[0][0]) >= 96 && Math.round(tex[0][1]) >= 32) {
            for (int k = 0; k <= n; k++) {
                float[][] emb = k < n ? lire(rm, Identifier.fromNamespaceAndPath("reborn", chemin(k))) : null;
                sceau(im, k * SCEAU_TEX, tex, k < n ? 0 : 32, emb);
            }
        }
        mc.getTextureManager().register(SCEAUX, new DynamicTexture(() -> "reborn-sceaux-registre", im));
    }

    private static void sceau(NativeImage im, int x0, float[][] tex, int cellU, float[][] emb) {
        int tw = Math.round(tex[0][0]), D = SCEAU_TEX;
        double th = Math.toRadians(PENTE), ca = Math.cos(th), sa = Math.sin(th), cs = 32, cd = D / 2.0;
        // champ (dans le sceau, hors bord biseauté) sur la grille 32×32 de la cellule
        boolean[] dedans = new boolean[32 * 32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) dedans[y * 32 + x] = tex[1 + y * tw + cellU + x][3] > 0;
        int[] col = new int[D * D];
        boolean[] champ = new boolean[D * D], usure = new boolean[D * D], encre = new boolean[D * D];
        int ew = emb == null ? 0 : Math.round(emb[0][0]), eh = emb == null ? 0 : Math.round(emb[0][1]);
        double e = 40, o = 32 - e / 2;
        for (int y = 0; y < D; y++) {
            for (int x = 0; x < D; x++) {
                double dx = x + 0.5 - cd, dy = y + 0.5 - cd;
                int ix = (int) Math.floor(ca * dx + sa * dy + cs), iy = (int) Math.floor(-sa * dx + ca * dy + cs);
                int i = y * D + x;
                if (ix >= 0 && ix < 64 && iy >= 0 && iy < 64) {
                    int tx = ix >> 1, ty = iy >> 1;
                    float[] p = tex[1 + ty * tw + cellU + tx];
                    if (p[3] > 0) col[i] = argb(p);
                    champ[i] = dedans[ty * 32 + tx] && tx > 0 && ty > 0 && tx < 31 && ty < 31
                        && dedans[ty * 32 + tx - 1] && dedans[ty * 32 + tx + 1]
                        && dedans[(ty - 1) * 32 + tx] && dedans[(ty + 1) * 32 + tx];
                    usure[i] = tex[1 + ty * tw + 64 + tx][3] > 0;
                }
                if (emb == null) continue;
                float cov = 0;
                for (int j = 0; j < 4; j++) {
                    for (int k = 0; k < 4; k++) {
                        double qx = x + (k + 0.5) / 4 - cd, qy = y + (j + 0.5) / 4 - cd;
                        double sx = ca * qx + sa * qy + cs, sy = -sa * qx + ca * qy + cs;
                        int ex = (int) Math.floor((sx - o) / e * ew), ey = (int) Math.floor((sy - o) / e * eh);
                        if (ex >= 0 && ex < ew && ey >= 0 && ey < eh) cov += emb[1 + ey * ew + ex][3];
                    }
                }
                encre[i] = cov / 16f >= 0.45f;
            }
        }
        for (int i = 0; i < D * D; i++) encre[i] &= champ[i];
        for (int y = 0; y < D; y++) {
            for (int x = 0; x < D; x++) {
                int i = y * D + x;
                if (encre[i]) { if (!usure[i]) col[i] = 0xFFF2E6CC; continue; }
                boolean ombre = x > 0 && y > 0 && encre[i - D - 1] || x > 1 && y > 1 && encre[i - 2 * D - 2];
                if (ombre && champ[i]) col[i] = 0xFF4E0C0B;
            }
        }
        for (int y = 0; y < D; y++) {
            for (int x = 0; x < D; x++) {
                int c = col[y * D + x];
                if ((c >>> 24) == 0 && x > 1 && y > 1 && (col[(y - 2) * D + x - 2] >>> 24) == 0xFF) c = 0x8C040203;
                if (c != 0) im.setPixel(x0 + x, y, c);
            }
        }
    }

    private static void pret() {
        if (pret) return;
        pret = true;
        Minecraft mc = Minecraft.getInstance();
        ResourceManager rm = mc.getResourceManager();
        clans = new ArrayList<>();
        try {
            for (Identifier id : rm.listResources("textures/gui/clans", i -> i.getPath().endsWith(".png")).keySet()) {
                if (!"reborn".equals(id.getNamespace())) continue;
                String n = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
                if (n.startsWith("clan_")) clans.add(n.substring(5, n.length() - 4));
            }
        } catch (RuntimeException ignored) { }
        clans.sort(String::compareTo);
        INDEX.clear();
        for (int i = 0; i < clans.size(); i++) INDEX.put(clans.get(i), i);

        int n = VILLAGES.length + clans.size();
        atlasW = 64 * Math.max(1, n);
        NativeImage at = new NativeImage(atlasW, 176, true);
        vider(at);
        int[][] rangees = {{64, 0}, {32, 64}, {24, 96}, {56, 120}};
        for (int k = 0; k < n; k++) {
            String chemin = k < VILLAGES.length ? "textures/gui/esc/village_" + VILLAGES[k] + ".png"
                : "textures/gui/clans/clan_" + clans.get(k - VILLAGES.length) + ".png";
            float[][] src = lire(rm, Identifier.fromNamespaceAndPath("reborn", chemin));
            if (src == null) continue;
            for (int[] r : rangees) {
                float[][] e = reechantillonner(src, r[0], r[0]);
                for (int y = 0; y < r[0]; y++) {
                    for (int x = 0; x < r[0]; x++) {
                        float[] p = e[y * r[0] + x];
                        if (p[3] * 255 <= 110) continue;
                        float lum = (p[0] + p[1] + p[2]) / 3f;
                        int gr = Math.max(0, Math.min(255, Math.round(255 * (0.55f + 0.45f * lum))));
                        at.setPixel(k * 64 + x, r[1] + y, 0xFF000000 | gr << 16 | gr << 8 | gr);
                    }
                }
            }
        }
        mc.getTextureManager().register(ATLAS, new DynamicTexture(() -> "reborn-emblemes", at));

        kamonH = 30 + 16 * ((clans.size() + 7) / 8);
        NativeImage km = new NativeImage(416, kamonH, true);
        vider(km);
        float[][] anneau = lireBrut(rm, PersoArt.tex("kamon_sol"));
        if (anneau != null) {
            int aw = Math.round(anneau[0][0]), ah = Math.round(anneau[0][1]);
            for (int y = 0; y < Math.min(30, ah); y++) {
                for (int x = 0; x < Math.min(116, aw); x++) {
                    float[] p = anneau[1 + y * aw + x];
                    if (p[3] > 0) km.setPixel(x, y, argb(p));
                }
            }
        }
        for (int i = 0; i < clans.size(); i++) {
            float[][] src = lire(rm, Identifier.fromNamespaceAndPath("reborn", "textures/gui/clans/clan_" + clans.get(i) + ".png"));
            if (src == null) continue;
            float[][] e = reechantillonner(src, 50, 15);
            int x0 = (i % 8) * 52 + 1, y0 = 30 + (i / 8) * 16;
            for (int y = 0; y < 15; y++) {
                for (int x = 0; x < 50; x++) {
                    float a = e[y * 50 + x][3] * 255;
                    if (a <= 70) continue;
                    int al = Math.round(255 * (0.45f + 0.40f * Math.min(1f, a / 160f)));
                    km.setPixel(x0 + x, y0 + y, al << 24 | 0xFFFFFF);
                }
            }
        }
        mc.getTextureManager().register(KAMON, new DynamicTexture(() -> "reborn-kamon", km));
    }

    private static void vider(NativeImage im) {
        for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) im.setPixel(x, y, 0);
    }

    private static int argb(float[] p) {
        return Math.round(p[3] * 255) << 24 | Math.round(p[0] * 255) << 16 | Math.round(p[1] * 255) << 8 | Math.round(p[2] * 255);
    }

    /** Image en flottants RGBA (0-1), ligne par ligne ; [0] = {largeur, hauteur} puis les pixels. */
    private static float[][] lireBrut(ResourceManager rm, Identifier id) {
        try {
            var res = rm.getResource(id);
            if (res.isEmpty()) return null;
            Resource r = res.get();
            try (InputStream in = r.open(); NativeImage im = NativeImage.read(in)) {
                int w = im.getWidth(), h = im.getHeight();
                float[][] out = new float[1 + w * h][];
                out[0] = new float[]{w, h, 0, 0};
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int c = im.getPixel(x, y);
                        out[1 + y * w + x] = new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f,
                            (c & 0xFF) / 255f, (c >>> 24) / 255f};
                    }
                }
                return out;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static float[][] lire(ResourceManager rm, Identifier id) { return lireBrut(rm, id); }

    /**
     * Rééchantillonnage « boîte » à couverture exacte (moyenne pondérée par l'alpha pour la couleur) : réduit sans
     * perdre les traits fins, agrandit sans flou excessif.
     */
    private static float[][] reechantillonner(float[][] src, int dw, int dh) {
        int sw = Math.round(src[0][0]), sh = Math.round(src[0][1]);
        float fx = sw / (float) dw, fy = sh / (float) dh;
        float[][] out = new float[dw * dh][];
        for (int y = 0; y < dh; y++) {
            float y0 = y * fy, y1 = (y + 1) * fy;
            for (int x = 0; x < dw; x++) {
                float x0 = x * fx, x1 = (x + 1) * fx;
                float r = 0, g = 0, b = 0, a = 0, aire = 0;
                for (int sy = (int) Math.floor(y0); sy < Math.ceil(y1) && sy < sh; sy++) {
                    float cy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    if (cy <= 0) continue;
                    for (int sx = (int) Math.floor(x0); sx < Math.ceil(x1) && sx < sw; sx++) {
                        float cx = Math.min(x1, sx + 1) - Math.max(x0, sx);
                        if (cx <= 0) continue;
                        float[] p = src[1 + sy * sw + sx];
                        float k = cx * cy;
                        r += p[0] * p[3] * k;
                        g += p[1] * p[3] * k;
                        b += p[2] * p[3] * k;
                        a += p[3] * k;
                        aire += k;
                    }
                }
                out[y * dw + x] = a > 0 ? new float[]{r / a, g / a, b / a, a / aire} : new float[]{0, 0, 0, 0};
            }
        }
        return out;
    }
}
