package fr.reborn.hud.faction;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kit du volet Faction (touche J) : le kit du Poste de garde v3 (palette nuit / laque / or, icônes au trait de
 * {@code garde/icones.png}, pinceau qui peint par lots — DA §6, règle 7) et, en plus, l'atlas des <b>insignes</b>
 * ({@code faction/insignes.png}, {@code tools/gen_faction_textures.py} : insignes de section au trait et emblèmes réels
 * des villages, à chaque taille en pixels écran), les têtes RP ({@link RebornSkins}), la tête d'un <b>inconnu</b>
 * (capuche sombre, aucun trait) et la vignette d'une tenue (vue de face du skin composé).
 */
final class FactionArt {

    private FactionArt() {}

    /* ================================================================ palette (maquettes v3, _kit_v3.py) */

    static final int NUIT = 0xFF110B0D, NUIT_2 = 0xFF1B1215, NUIT_3 = 0xFF26191E, VOILE = 0xEC110B0D;
    static final int LAQUE = 0xFFA0182B, LAQUE_SOMBRE = 0xFF46121B, LAQUE_HAUT = 0xFFCC3A4C;
    static final int FILET = 0xFF3A282D, FILET_2 = 0xFF543C42, SEP = 0xFF1E1418;
    static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFE8C27A, OR_DOUX = 0xFF967648;
    static final int CREME = 0xFFEEE4D2, GRIS = 0xFFA0948A, GRIS_2 = 0xFF706462, GRIS_3 = 0xFF4E4444;
    static final int OK = 0xFF80B68C, TERRE = 0xFFE2A04A, ROUGE = 0xFFE46676;

    /* ================================================================ atlas (contrats des générateurs) */

    private static final Identifier ICONES = DaKit.tex("garde", "icones");
    private static final String[] NOMS_IC = {"chercher", "cloche", "joueurs", "journal", "outils", "croix", "gauche", "droite",
            "bas", "haut", "aller", "ramener", "revenir", "observer", "geler", "sac", "noir", "noter", "sanction", "radio",
            "plus", "reveiller", "annonce", "chat", "commandes", "infirmerie", "zones", "verrou", "filtre", "emote",
            "tenue", "seisme", "zero", "suppr", "lien", "horloge", "point", "epee", "alerte", "crane",
            "drapeau", "aide", "renvoyer", "hopital", "reglages", "masque", "bouclier", "copier", "tri", "coeur",
            "cercle"};
    private static final int[] TAILLES_IC = {5, 6, 7, 8, 10, 12, 14, 15, 16, 18, 20, 21, 24, 28, 30, 32, 40};

    private static final Identifier INSIGNES = DaKit.tex("faction", "insignes");
    private static final String[] NOMS_INS = {"anbu", "police", "medical", "academie", "missions", "garde", "conseil",
            "bandeau", "lune", "konoha", "suna", "kiri", "kumo", "iwa", "ame"};
    private static final int[] TAILLES_INS = {8, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 30, 32, 36, 40, 44, 48, 54, 60,
            64, 72, 80, 96};

    /** Un atlas « une rangée par taille en pixels écran ». */
    private static final class Atlas {
        final Identifier tex;
        final int[] tailles;
        final Map<String, Integer> index = new HashMap<>();
        final Map<Integer, Integer> rangee = new HashMap<>();
        final int w, h;

        Atlas(Identifier tex, String[] noms, int[] tailles) {
            this.tex = tex;
            this.tailles = tailles;
            for (int i = 0; i < noms.length; i++) index.put(noms[i], i);
            int y = 0, ww = 0;
            for (int p : tailles) {
                rangee.put(p, y);
                y += p + 2;
                ww = Math.max(ww, noms.length * (p + 2));
            }
            w = ww;
            h = y;
        }
    }

    private static final Atlas A_IC = new Atlas(ICONES, NOMS_IC, TAILLES_IC);
    private static final Atlas A_INS = new Atlas(INSIGNES, NOMS_INS, TAILLES_INS);

    static boolean insigneConnu(String n) { return n != null && A_INS.index.containsKey(n); }

    static int echelle() { return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }

    /* ================================================================ têtes et tenues */

    /** Skin d'un joueur : skin RP prêté, sinon celui du serveur, sinon par défaut. */
    static Identifier skin(UUID id) {
        if (id == null) return DefaultPlayerSkin.getDefaultTexture();
        Identifier rp = RebornSkins.overrideFor(id);
        if (rp != null) return rp;
        try {
            var conn = Minecraft.getInstance().getConnection();
            PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);
            if (info != null) return info.getSkin().body().texturePath();
        } catch (RuntimeException ignored) { }
        return DefaultPlayerSkin.get(id).body().texturePath();
    }

    private static final Map<String, Identifier> TENUES = new HashMap<>();

    /** Skin composé pour la vignette d'une tenue (un corps neutre qui la porte) ; null si la tenue est inconnue. */
    static Identifier tenue(String id) {
        if (id == null || id.isEmpty()) return null;
        if (TENUES.containsKey(id)) return TENUES.get(id);
        Identifier r = null;
        try {
            SkinSpec s = new SkinSpec();
            s.skinColor = SkinSpec.skinRamp(0.3f);
            s.hairId = "Cheveux_Base";
            s.hairColor = 0xFF1E1818;
            s.eyeColor = s.eyeColorRight = 0xFF2A2420;
            s.outfitId = id;
            NativeImage img = RebornSkins.compose(s);
            if (img != null) {
                r = Identifier.fromNamespaceAndPath("reborn", "faction/tenue_" + id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
                Minecraft.getInstance().getTextureManager().register(r, new DynamicTexture(() -> "reborn-faction-tenue", img));
            }
        } catch (RuntimeException e) {
            r = null;
        }
        TENUES.put(id, r);
        return r;
    }

    /* ================================================================ pinceau */

    private record Txt(FormattedCharSequence c, float x, float y, int col, float s) { }

    /** Tête : skin, coin au pixel écran, côté d'une case (px), teinte. */
    private record Vis(Identifier skin, int px, int py, int k, int col) { }

    /** Une partie de skin vue de face : u, v (texels), w, h (texels), x, y (px écran), taille d'un texel (px). */
    private record Part(Identifier skin, int u, int v, int w, int h, int x, int y, int k) { }

    /**
     * Pinceau d'une région : collecte puis soumet en lots dans l'ordre fond → filets → têtes → icônes → insignes →
     * textes. Coordonnées en unités GUI ; filets, icônes et insignes posés au pixel écran.
     */
    static final class Pinceau {
        private final List<int[]> fonds = new ArrayList<>(64);
        private final List<int[]> filets = new ArrayList<>(96);
        private final List<Vis> visages = new ArrayList<>(24);
        private final List<Part> parts = new ArrayList<>(16);
        private final List<int[]> icones = new ArrayList<>(48);
        private final List<int[]> insignes = new ArrayList<>(16);
        private final List<Txt> textes = new ArrayList<>(160);
        private Font font;

        void police(Font f) { this.font = f; }

        void fond(int x0, int y0, int x1, int y1, int col) {
            if (x1 > x0 && y1 > y0 && (col >>> 24) != 0) fonds.add(new int[]{x0, y0, x1, y1, col});
        }

        void filetH(float x0, float x1, float y, int col) {
            int k = echelle();
            int X0 = Math.round(x0 * k), X1 = Math.round(x1 * k), Y = Math.round(y * k);
            if (X1 > X0) filets.add(new int[]{X0, Y, X1, Y + 1, col});
        }

        void filetV(float x, float y0, float y1, int col) {
            int k = echelle();
            int X = Math.round(x * k), Y0 = Math.round(y0 * k), Y1 = Math.round(y1 * k);
            if (Y1 > Y0) filets.add(new int[]{X, Y0, X + 1, Y1, col});
        }

        void px(float x0, float y0, float x1, float y1, int col) {
            int k = echelle();
            int X0 = Math.round(x0 * k), Y0 = Math.round(y0 * k), X1 = Math.round(x1 * k), Y1 = Math.round(y1 * k);
            if (X1 > X0 && Y1 > Y0) filets.add(new int[]{X0, Y0, X1, Y1, col});
        }

        void cadre(float x, float y, float w, float h, int col) {
            int k = echelle();
            int X0 = Math.round(x * k), Y0 = Math.round(y * k), X1 = Math.round((x + w) * k), Y1 = Math.round((y + h) * k);
            filets.add(new int[]{X0, Y0, X1, Y0 + 1, col});
            filets.add(new int[]{X0, Y1 - 1, X1, Y1, col});
            filets.add(new int[]{X0, Y0 + 1, X0 + 1, Y1 - 1, col});
            filets.add(new int[]{X1 - 1, Y0 + 1, X1, Y1 - 1, col});
        }

        /** Icône au trait du Poste de garde, taille GUI, coin haut gauche. */
        void icone(String nom, float x, float y, int taille, int col) { poser(icones, A_IC, nom, x, y, taille, col); }

        /** Insigne de section ou emblème de village, taille GUI, coin haut gauche. */
        void insigne(String nom, float x, float y, int taille, int col) {
            poser(insignes, A_INS, insigneConnu(nom) ? nom : "missions", x, y, taille, col);
        }

        private void poser(List<int[]> l, Atlas a, String nom, float x, float y, int taille, int col) {
            Integer i = a.index.get(nom);
            if (i == null || (col >>> 24) == 0) return;
            int k = echelle();
            l.add(new int[]{i, Math.round(x * k), Math.round(y * k), Math.round(taille * k), col});
        }

        /** Pastille ronde de rayon r (GUI) centrée en (cx, cy). */
        void pastille(float cx, float cy, float r, int col) {
            int k = echelle();
            int d = Math.max(3, Math.round(2 * r * k));
            Integer i = A_IC.index.get("point");
            int p = d * 2;
            icones.add(new int[]{i, Math.round(cx * k) - p / 2, Math.round(cy * k) - p / 2, p, col});
        }

        /** Tête du skin (et son calque) : côté ≈ taille GUI, nette (multiple de 8 px) ; {@code terne} = absent. */
        void visage(UUID id, float x, float y, int taille, boolean terne) {
            int k = echelle();
            int kk = Math.max(1, Math.round(taille * k / 8f));
            int X = Math.round(x * k), Y = Math.round(y * k);
            int off = (Math.round(taille * k) - kk * 8) / 2;
            visages.add(new Vis(skin(id), X + off, Y + off, kk, terne ? 0xFF8C8286 : 0xFFFFFFFF));
        }

        /** Tête d'un inconnu : capuche sombre, visage dans l'ombre, aucun trait (maquettes v3). */
        void inconnu(float x, float y, int taille) {
            int k = echelle();
            int kk = Math.max(1, Math.round(taille * k / 8f));
            int X = Math.round(x * k) + (Math.round(taille * k) - kk * 8) / 2, Y = Math.round(y * k) + (Math.round(taille * k) - kk * 8) / 2;
            String[] motif = {"aaaaaaaa", "abbbbbba", "abccccba", "abccccba", "abccccba", "abbccbba", "aabbbbaa", "aaaaaaaa"};
            for (int j = 0; j < 8; j++) {
                for (int i = 0; i < 8; i++) {
                    char c = motif[j].charAt(i);
                    int col = c == 'a' ? 0xFF3A3236 : c == 'b' ? 0xFF282226 : 0xFF181417;
                    filets.add(new int[]{X + i * kk, Y + j * kk, X + (i + 1) * kk, Y + (j + 1) * kk, col});
                }
            }
        }

        /** Vignette de face d'un skin 64×64 (16 × 32 texels), coin (x, y) GUI, un texel = {@code t} GUI. */
        void poupee(Identifier skin, float x, float y, float t) {
            if (skin == null) return;
            int k = echelle();
            int kk = Math.max(1, Math.round(t * k));
            int X = Math.round(x * k), Y = Math.round(y * k);
            int[][] p = {
                    {8, 8, 8, 8, 4, 0}, {20, 20, 8, 12, 4, 8}, {44, 20, 4, 12, 0, 8}, {36, 52, 4, 12, 12, 8},
                    {4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20},
                    {40, 8, 8, 8, 4, 0}, {20, 36, 8, 12, 4, 8}, {44, 36, 4, 12, 0, 8}, {52, 52, 4, 12, 12, 8},
                    {4, 36, 4, 12, 4, 20}, {4, 52, 4, 12, 8, 20}};
            for (int[] q : p) parts.add(new Part(skin, q[0], q[1], q[2], q[3], X + q[4] * kk, Y + q[5] * kk, kk));
        }

        /* ---- textes */
        float texte(String s, float x, float cy, int taillePx, int col, boolean gras) {
            if (s == null || s.isEmpty()) return 0;
            Component c = gras ? DaKit.interGras(s) : RebornFont.body(s);
            float k = taillePx / 18f;
            textes.add(new Txt(c.getVisualOrderText(), x, cy - 4f * k, col, k));
            return font.width(c) * k;
        }

        float texteD(String s, float xd, float cy, int taillePx, int col, boolean gras) {
            float w = largeur(s, taillePx, gras);
            texte(s, xd - w, cy, taillePx, col, gras);
            return w;
        }

        float texteC(String s, float xc, float cy, int taillePx, int col, boolean gras) {
            float w = largeur(s, taillePx, gras);
            texte(s, xc - w / 2f, cy, taillePx, col, gras);
            return w;
        }

        /** ArcadePix ×1 (capitales de 7 GUI), haut des capitales en y. */
        float arcade(String s, float x, float y, int col) {
            Component c = DaKit.arcade(s);
            textes.add(new Txt(c.getVisualOrderText(), x, y, col, 1f));
            return font.width(c) - 1;
        }

        /** Petites capitales espacées (en-têtes de section), Inter gras. */
        float capitales(String s, float x, float cy, int taillePx, int col) {
            String t = DaKit.sansAccents(s).toUpperCase(java.util.Locale.ROOT);
            float k = taillePx / 18f, xx = x;
            for (int i = 0; i < t.length(); i++) {
                String ch = String.valueOf(t.charAt(i));
                xx += texte(ch, xx, cy, taillePx, col, true) + 0.6f;
                if (ch.equals(" ")) xx += 1.2f * k * 2;
            }
            return xx - x;
        }

        float largeurCapitales(String s, int taillePx) {
            String t = DaKit.sansAccents(s).toUpperCase(java.util.Locale.ROOT);
            float k = taillePx / 18f, w = 0;
            for (int i = 0; i < t.length(); i++) {
                String ch = String.valueOf(t.charAt(i));
                w += largeur(ch, taillePx, true) + 0.6f;
                if (ch.equals(" ")) w += 1.2f * k * 2;
            }
            return w;
        }

        float largeur(String s, int taillePx, boolean gras) {
            if (s == null || s.isEmpty()) return 0;
            return font.width(gras ? DaKit.interGras(s) : RebornFont.body(s)) * (taillePx / 18f);
        }

        /** Soumet tout ce qui a été peint, sous le ciseau courant. */
        void vider(GuiGraphicsExtractor ctx) {
            if (!fonds.isEmpty()) {
                LotGui l = LotGui.ouvrir(ctx);
                for (int[] f : fonds) l.fill(f[0], f[1], f[2], f[3], f[4]);
                l.fermer();
                fonds.clear();
            }
            if (!filets.isEmpty() || !visages.isEmpty() || !parts.isEmpty() || !icones.isEmpty() || !insignes.isEmpty()) {
                DaKit.debutPx(ctx);
                if (!filets.isEmpty()) {
                    LotGui l = LotGui.ouvrir(ctx);
                    for (int[] f : filets) l.fill(f[0], f[1], f[2], f[3], f[4]);
                    l.fermer();
                    filets.clear();
                }
                for (Vis v : visages) {
                    int s = v.k() * 8;
                    ctx.blit(RenderPipelines.GUI_TEXTURED, v.skin(), v.px(), v.py(), 8f, 8f, s, s, 8, 8, 64, 64, v.col());
                    ctx.blit(RenderPipelines.GUI_TEXTURED, v.skin(), v.px(), v.py(), 40f, 8f, s, s, 8, 8, 64, 64, v.col());
                }
                visages.clear();
                for (Part p : parts) {
                    ctx.blit(RenderPipelines.GUI_TEXTURED, p.skin(), p.x(), p.y(), p.u(), p.v(), p.w() * p.k(), p.h() * p.k(),
                            p.w(), p.h(), 64, 64);
                }
                parts.clear();
                lot(ctx, A_IC, icones);
                lot(ctx, A_INS, insignes);
                DaKit.finPx(ctx);
            }
            if (!textes.isEmpty()) {
                LotGui.Textes t = LotGui.textes(ctx);
                for (Txt x : textes) {
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(DaKit.cale(x.x()), DaKit.cale(x.y()));
                    if (x.s() != 1f) ctx.pose().scale(x.s(), x.s());
                    t.texte(font, x.c(), 0, 0, x.col(), false);
                    ctx.pose().popMatrix();
                }
                t.fermer();
                textes.clear();
            }
        }

        private static void lot(GuiGraphicsExtractor ctx, Atlas a, List<int[]> l) {
            if (l.isEmpty()) return;
            LotBlit b = LotBlit.ouvrir(ctx, a.tex, a.w, a.h);
            for (int[] ic : l) {
                int p = ic[3];
                Integer y = a.rangee.get(p);
                if (y != null) b.blit(ic[1], ic[2], ic[0] * (p + 2), y, p, p, ic[4]);
                else {
                    int q = a.tailles[a.tailles.length - 1];
                    for (int t : a.tailles) if (t >= p) { q = t; break; }
                    b.etire(ic[1], ic[2], p, p, ic[0] * (q + 2), a.rangee.get(q), q, q, ic[4]);
                }
            }
            b.fermer();
            l.clear();
        }
    }
}
