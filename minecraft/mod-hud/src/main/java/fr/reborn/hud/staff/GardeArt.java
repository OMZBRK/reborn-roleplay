package fr.reborn.hud.staff;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kit du Poste de garde v3 (direction A, « le volet », 14/10) : palette nuit / laque / or allégée, <b>icônes au
 * trait</b> (atlas {@code garde/icones.png}, généré par {@code tools/gen_garde_icones.py} à chaque taille en pixels
 * écran : nettes à toutes les échelles, aucun kanji) et un <b>pinceau</b> qui regroupe le dessin d'une région en
 * quelques éléments GUI (DA §6, règle 7) : les aplats en un lot, les filets d'1 px écran en un lot, les visages, les
 * icônes en un lot, puis tous les textes en un lot. On peint dans n'importe quel ordre ; {@link Pinceau#vider}
 * soumet dans l'ordre fond → filets → visages → icônes → textes (à appeler avant chaque changement de ciseau).
 */
final class GardeArt {

    private GardeArt() {}

    /* ================================================================ palette (maquettes v3, _kit_v3.py) */

    static final int NUIT = 0xFF110B0D, NUIT_2 = 0xFF1B1215, NUIT_3 = 0xFF26191E, VOILE = 0xEC110B0D;
    static final int LAQUE = 0xFFA0182B, LAQUE_SOMBRE = 0xFF46121B, LAQUE_HAUT = 0xFFCC3A4C;
    static final int FILET = 0xFF3A282D, FILET_2 = 0xFF543C42, SEP = 0xFF1E1418;
    static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFE8C27A, OR_DOUX = 0xFF967648;
    static final int CREME = 0xFFEEE4D2, GRIS = 0xFFA0948A, GRIS_2 = 0xFF706462, GRIS_3 = 0xFF4E4444;
    static final int OK = 0xFF80B68C, TERRE = 0xFFE2A04A, ROUGE = 0xFFE46676, GLACE = 0xFF8CBADE, NOIR_V = 0xFFA692D2,
            STAFF = 0xFFC8AC70, ANIM = 0xFFB48CD8;

    /** Couleur d'une catégorie du journal (pastilles sobres). */
    static int categorie(String cat) {
        return switch (cat == null ? "" : cat) {
            case "KO" -> 0xFFC45464;
            case "COMBAT" -> 0xFFCE884C;
            case "STAFF" -> 0xFF8098B0;
            case "JOUEUR" -> 0xFF6EAAA0;
            case "INVENTAIRE" -> 0xFFA88460;
            case "OBJET" -> 0xFFC0A060;
            case "CHAT" -> 0xFF969696;
            case "INTERACTION" -> 0xFF96B06E;
            case "ECONOMIE" -> 0xFFD4B45A;
            case "COMMANDE" -> 0xFF78788C;
            case "DUEL" -> 0xFFAA78B4;
            case "BIBLIOTHEQUE" -> 0xFF7890C0;
            case "RADIO" -> 0xFF9FB0D0;
            case "FACTION" -> 0xFFC8A070;
            default -> 0xFF8C8478;
        };
    }

    /** Assombrit une couleur (bord des étiquettes). */
    static int sombre(int col, float k) {
        int r = Math.round(((col >> 16) & 0xFF) * k), g = Math.round(((col >> 8) & 0xFF) * k), b = Math.round((col & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /* ================================================================ icônes (contrat : tools/gen_garde_icones.py) */

    static final Identifier ICONES = DaKit.tex("garde", "icones");
    private static final String[] NOMS = {"chercher", "cloche", "joueurs", "journal", "outils", "croix", "gauche", "droite",
            "bas", "haut", "aller", "ramener", "revenir", "observer", "geler", "sac", "noir", "noter", "sanction", "radio",
            "plus", "reveiller", "annonce", "chat", "commandes", "infirmerie", "zones", "verrou", "filtre", "emote",
            "tenue", "seisme", "zero", "suppr", "lien", "horloge", "point", "epee", "alerte", "crane",
            "drapeau", "aide", "renvoyer", "hopital", "reglages", "masque", "bouclier", "copier", "tri", "coeur",
            "cercle", "micro", "micro_off", "externe", "lecture", "coche", "porte", "tirage", "barriere", "dossier"};
    private static final int[] TAILLES = {5, 6, 7, 8, 10, 12, 14, 15, 16, 18, 20, 21, 24, 28, 30, 32, 40};
    private static final Map<String, Integer> INDEX = new HashMap<>();
    private static final Map<Integer, Integer> RANGEE = new HashMap<>();
    static final int ATLAS_W, ATLAS_H;

    static {
        for (int i = 0; i < NOMS.length; i++) INDEX.put(NOMS[i], i);
        int y = 0, w = 0;
        for (int p : TAILLES) {
            RANGEE.put(p, y);
            y += p + 2;
            w = Math.max(w, NOMS.length * (p + 2));
        }
        ATLAS_W = w;
        ATLAS_H = y;
    }

    static int echelle() { return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }

    /* ================================================================ pinceau */

    /** Un texte en attente : séquence, coin haut gauche (GUI), couleur, échelle de la police. */
    private record Txt(FormattedCharSequence c, float x, float y, int col, float s) { }

    private record Vis(Identifier skin, int px, int py, int k) { }

    /**
     * Pinceau d'une région : collecte puis soumet en lots (voir la classe). Coordonnées en unités GUI ; les filets et
     * les icônes sont posés au pixel écran.
     */
    static final class Pinceau {
        private final List<int[]> fonds = new ArrayList<>(64);
        private final List<int[]> filets = new ArrayList<>(64);
        private final List<Vis> visages = new ArrayList<>(16);
        private final List<int[]> icones = new ArrayList<>(32);
        private final List<Txt> textes = new ArrayList<>(96);
        private Font font;

        void police(Font f) { this.font = f; }

        Font police() { return font; }

        /* ---- aplats (GUI) */
        void fond(int x0, int y0, int x1, int y1, int col) {
            if (x1 > x0 && y1 > y0 && (col >>> 24) != 0) fonds.add(new int[]{x0, y0, x1, y1, col});
        }

        /* ---- filets d'1 px écran (coordonnées GUI, éventuellement fractionnaires) */
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

        /** Rectangle au pixel écran (barre fine, pastille). */
        void px(float x0, float y0, float x1, float y1, int col) {
            int k = echelle();
            int X0 = Math.round(x0 * k), Y0 = Math.round(y0 * k), X1 = Math.round(x1 * k), Y1 = Math.round(y1 * k);
            if (X1 > X0 && Y1 > Y0) filets.add(new int[]{X0, Y0, X1, Y1, col});
        }

        /** Contour d'1 px écran autour de (x, y, w, h) GUI. */
        void cadre(float x, float y, float w, float h, int col) {
            int k = echelle();
            int X0 = Math.round(x * k), Y0 = Math.round(y * k), X1 = Math.round((x + w) * k), Y1 = Math.round((y + h) * k);
            filets.add(new int[]{X0, Y0, X1, Y0 + 1, col});
            filets.add(new int[]{X0, Y1 - 1, X1, Y1, col});
            filets.add(new int[]{X0, Y0 + 1, X0 + 1, Y1 - 1, col});
            filets.add(new int[]{X1 - 1, Y0 + 1, X1, Y1 - 1, col});
        }

        /* ---- icônes : taille en GUI (5, 6, 7, 8, 10), coin haut gauche en GUI */
        void icone(String nom, float x, float y, int taille, int col) {
            Integer i = INDEX.get(nom);
            if (i == null || (col >>> 24) == 0) return;
            int k = echelle();
            icones.add(new int[]{i, Math.round(x * k), Math.round(y * k), taille * k, col});
        }

        /** Pastille ronde de rayon r (GUI) centrée en (cx, cy). */
        void pastille(float cx, float cy, float r, int col) {
            int k = echelle();
            int d = Math.max(3, Math.round(2 * r * k));
            Integer i = INDEX.get("point");
            // l'icône « point » a un disque de rayon 6 sur 24 : on prend une icône 2× plus grande que le disque
            int p = d * 2;
            icones.add(new int[]{i, Math.round(cx * k) - p / 2, Math.round(cy * k) - p / 2, p, col});
        }

        /* ---- visage du joueur (skin du tchat), côté d'environ taille GUI, net (multiple de 8 px) */
        void visage(String uuid, float x, float y, int taille) {
            Identifier skin = null;
            try {
                var conn = Minecraft.getInstance().getConnection();
                PlayerInfo info = conn != null && uuid != null && !uuid.isEmpty() ? conn.getPlayerInfo(UUID.fromString(uuid)) : null;
                if (info != null) skin = info.getSkin().body().texturePath();
                // hors de la liste des joueurs (banc, joueur parti) : l'apparence RP connue, s'il y en a une
                if (skin == null && uuid != null && !uuid.isEmpty()) skin = fr.reborn.hud.skin.RebornSkins.overrideFor(UUID.fromString(uuid));
            } catch (RuntimeException ignored) { }
            int k = echelle();
            int kk = Math.max(1, Math.round(taille * k / 8f));
            int X = Math.round(x * k), Y = Math.round(y * k);
            int off = (taille * k - kk * 8) / 2;
            if (skin == null) {
                filets.add(new int[]{X + off, Y + off, X + off + kk * 8, Y + off + kk * 8, 0xFF3A2C30});
                return;
            }
            visages.add(new Vis(skin, X + off, Y + off, kk));
        }

        /* ---- textes */
        /** Texte Inter (taille en px de maquette à l'échelle 2 : 11 à 18), milieu de ligne en cy. Renvoie la largeur GUI. */
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
            String t = DaKit.sansAccents(s);
            float k = taillePx / 18f, xx = x;
            for (int i = 0; i < t.length(); i++) {
                String ch = String.valueOf(t.charAt(i));
                xx += texte(ch, xx, cy, taillePx, col, true) + 0.6f;
                if (ch.equals(" ")) xx += 1.2f * k * 2;
            }
            return xx - x;
        }

        float largeur(String s, int taillePx, boolean gras) {
            if (s == null || s.isEmpty()) return 0;
            return font.width(gras ? DaKit.interGras(s) : RebornFont.body(s)) * (taillePx / 18f);
        }

        boolean vide() { return fonds.isEmpty() && filets.isEmpty() && visages.isEmpty() && icones.isEmpty() && textes.isEmpty(); }

        /** Soumet tout ce qui a été peint, sous le ciseau courant. */
        void vider(GuiGraphicsExtractor ctx) {
            if (!fonds.isEmpty()) {
                LotGui l = LotGui.ouvrir(ctx);
                for (int[] f : fonds) l.fill(f[0], f[1], f[2], f[3], f[4]);
                l.fermer();
                fonds.clear();
            }
            if (!filets.isEmpty() || !visages.isEmpty() || !icones.isEmpty()) {
                DaKit.debutPx(ctx);
                if (!filets.isEmpty()) {
                    LotGui l = LotGui.ouvrir(ctx);
                    for (int[] f : filets) l.fill(f[0], f[1], f[2], f[3], f[4]);
                    l.fermer();
                    filets.clear();
                }
                for (Vis v : visages) {
                    int s = v.k() * 8;
                    ctx.blit(RenderPipelines.GUI_TEXTURED, v.skin(), v.px(), v.py(), 8f, 8f, s, s, 8, 8, 64, 64);
                    ctx.blit(RenderPipelines.GUI_TEXTURED, v.skin(), v.px(), v.py(), 40f, 8f, s, s, 8, 8, 64, 64);
                }
                visages.clear();
                if (!icones.isEmpty()) {
                    LotBlit l = LotBlit.ouvrir(ctx, ICONES, ATLAS_W, ATLAS_H);
                    for (int[] ic : icones) {
                        int p = ic[3];
                        Integer y = RANGEE.get(p);
                        if (y != null) l.blit(ic[1], ic[2], ic[0] * (p + 2), y, p, p, ic[4]);
                        else {
                            // taille absente de l'atlas (échelle > 4) : la plus grande, étirée
                            int q = TAILLES[TAILLES.length - 1];
                            for (int t : TAILLES) if (t >= p) { q = t; break; }
                            l.etire(ic[1], ic[2], p, p, ic[0] * (q + 2), RANGEE.get(q), q, q, ic[4]);
                        }
                    }
                    l.fermer();
                    icones.clear();
                }
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
    }
}
