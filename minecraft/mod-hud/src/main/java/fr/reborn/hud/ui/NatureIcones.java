package fr.reborn.hud.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Les cinq <b>natures de chakra</b> et leurs <b>symboles canoniques</b> (décision du 14/10 : « fais comme pour les clans
 * ou les villages, prends les vraies SVG ») : les icônes de nature de Narutopedia (octogone au dégradé de la nature,
 * caractère de la nature ; ShounenSuki, CC BY-SA 3.0, {@code tools/natures_canon/README.md}), converties par
 * {@code tools/gen_natures_textures.py} avec le pipeline des blasons de clan, en couleurs canoniques. Ce sont des
 * symboles canoniques (comme les blasons), pas des kanji décoratifs : ils remplacent les sceaux kanji de nature (fiche
 * shinobi, lecture d'un rouleau) et sont la pièce maîtresse du test de la Feuille. Ce ne sont PAS des icônes de
 * recharge : chaque technique aura plus tard la sienne.
 *
 * <p>Textures {@code textures/gui/nature/<nature>[_16|_48|_64].png} (16, 32, 48 et 64 px, chacune rendue depuis le SVG).
 * {@link #dessiner} pose au pixel écran la plus grande taille entière qui tient (16 GUI : 32 px à l'échelle 2, 48 px à
 * l'échelle 3, 16 px à l'échelle 1) : jamais de réduction au plus proche.
 */
public final class NatureIcones {

    private NatureIcones() {}

    public enum Nature {
        // teintes : celles des dégradés canoniques, éclaircies pour le texte sur la nuit
        KATON("katon", "Katon", "Feu", 0xFFFF4A3A, 1),
        SUITON("suiton", "Suiton", "Eau", 0xFF51A8FF, 1),
        DOTON("doton", "Doton", "Terre", 0xFFD08C34, 1),
        FUTON("futon", "Fūton", "Vent", 0xFF00E090, 1),
        RAITON("raiton", "Raiton", "Foudre", 0xFFF4E41A, 1);

        public final String slug, nom, element;
        /** Teinte de la nature (texte, lueur). images : 1 (symboles fixes ; le code gère les bandes animées). */
        public final int couleur;
        final int images;
        final Identifier[] tex = new Identifier[4];   // 16, 32, 48, 64

        Nature(String slug, String nom, String element, int couleur, int images) {
            this.slug = slug;
            this.nom = nom;
            this.element = element;
            this.couleur = couleur;
            this.images = images;
            tex[0] = DaKit.tex("nature", slug + "_16");
            tex[1] = DaKit.tex("nature", slug);
            tex[2] = DaKit.tex("nature", slug + "_48");
            tex[3] = DaKit.tex("nature", slug + "_64");
        }
    }

    /** KATON / Katon / Fūton / Fûton / futon… ; null si ce n'est pas une nature. */
    public static Nature parse(String s) {
        if (s == null) return null;
        String k = DaKit.sansAccents(s).trim().toUpperCase(Locale.ROOT);
        for (Nature n : Nature.values()) if (n.name().equals(k)) return n;
        return null;
    }

    /** Icône de {@code tGui}×{@code tGui} GUI en (x, y), animée, nette (voir la classe). */
    public static void dessiner(GuiGraphicsExtractor ctx, Nature n, float x, float y, int tGui) {
        dessiner(ctx, n, x, y, tGui, 0xFFFFFFFF, System.currentTimeMillis());
    }

    public static void dessiner(GuiGraphicsExtractor ctx, Nature n, float x, float y, int tGui, int argb, long now) {
        if (n == null || (argb >>> 24) == 0) return;
        int gs = DaKit.echelleGui();
        int px = tGui * gs;
        // la plus grande taille entière (k × 64, 48, 32 ou 16 px) qui tient dans la case, centrée : jamais de facteur
        // non entier ; à taille égale, la texture la plus détaillée
        int cote = 16, d = 0;
        for (int c : new int[]{64, 48, 32, 16}) {
            int k = px / c;
            if (k >= 1 && k * c > d) { d = k * c; cote = c; }
        }
        if (d == 0) { d = 16; cote = 16; }
        int t = cote == 64 ? 3 : cote == 48 ? 2 : cote == 32 ? 1 : 0;
        int img = n.images <= 1 ? 0 : (int) ((now / 100L) % n.images);
        int sx = Math.round(x * gs) + (px - d) / 2, sy = Math.round(y * gs) + (px - d) / 2;
        DaKit.debutPx(ctx);
        ctx.blit(RenderPipelines.GUI_TEXTURED, n.tex[t], sx, sy, 0f, (float) (img * cote), d, d, cote, cote,
            cote, cote * n.images, argb);
        DaKit.finPx(ctx);
    }
}
