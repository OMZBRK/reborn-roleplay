package fr.reborn.hud.mudra;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.keybind.RappelsTouches;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.TexteCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * La <b>barre de mudras</b>, dans la direction artistique des menus Reborn (maquette R2 retenue le 16/11 : « trop
 * ressemblante avec celle de Ketto », « dans le cadre, comme les autres menus Reborn », « R2 avec les signes comme
 * avant, dessins lisses, et pour les touches fais comme avec les trucs concentration »).
 *
 * <p>Douze <b>cases du kit</b> (laque en creux, liseré d'or biseauté : les cases de la sacoche agrandies, texture
 * {@code gui/mudras/cases.png}, pixel art au texel), sans plaque derrière. Dans chaque case le <b>vrai dessin du
 * signe</b> ({@code gui/mudras/mains.png}, Schlum, Wikimedia Commons, CC BY-SA 4.0, lissée). Au-dessus le nom en
 * ArcadePix, dessous la <b>jauge du temps</b> (celle du poids de la sacoche : piste sombre cernée d'or sombre, plein or à
 * crête claire), puis la touche dans la <b>case des rappels de touches</b> ({@link RappelsTouches#caseTouche}, Inter
 * gras). Le signe qui vient d'être composé, et en mode guidé le signe attendu, passent en <b>laque rouge</b> à équerres
 * d'angle, leur nom en or ; en mode guidé les autres pâlissent et le nom de la technique s'écrit au-dessus avec
 * l'avancement. Rien d'autre : aucune trace de la suite composée (il faut la connaître).
 *
 * <p>Gabarit au <b>texel</b> : un texel = {@code z} pixels écran, entier ({@code z} = hauteur / 540 arrondi : 2 en
 * 1080p, 1 en 768p, 3 en 1440p ; borné pour que la rangée tienne dans 94 % de la largeur, le pas resserré en dessous). Case 34 texels, 58 d'axe en
 * axe : 672 texels en tout, soit 70 % de la largeur en 1080p. Les douze cases partent en un lot, les douze mains en un
 * autre, les douze cases de touche en un troisième ; les {@link Component} des noms sont gardés.
 *
 * <p>Animations : entrée en cascade (0,4 s), signe composé = la case se soulève 0,4 s, sortie en fondu 0,3 s (en
 * descendant si la suite est bonne).
 */
public final class MudraHud {

    private static final Identifier CASES = DaKit.tex("mudras", "cases"), MAINS = DaKit.tex("mudras", "mains"),
        MAINS_PETIT = DaKit.tex("mudras", "mains_petit");
    /** Atlas des cases : cellules de 38 (case de 34 en (2, 1), ombre autour) ; atlas des mains : 96 par main. */
    private static final int CELLULE = 38, CASES_L = 2 * CELLULE, T = 96, T_PETIT = 48;
    /** Gabarit en texels : case, pas, main dans la case, épaisseur de la jauge. */
    private static final int COTE = 34, PAS = 58, MAIN = 30, TOTAL = 11 * PAS + COTE;
    /** Rappels de touches : case de 22 px et capitales de 10 px en 1080p. */
    private static final int CASE_TOUCHE = 22, CAP_TOUCHE = 10;
    private static final int CREME = 0xF4E8D2, OR_CLAIR = 0xF2D59A, VERM = 0xE0563C, OMBRE = 0x0A0406;
    private static final int J_BORD = 0x6A4E24, J_PISTE = 0x0F090B, J_PLEIN = 0xD9A95E, J_CRETE = 0xF2D59A, J_BOUT = 0xFFF2C8;
    private static final long ENTREE_MS = 160L, CASCADE_MS = 22L, SORTIE_MS = 300L, APPUI_MS = 400L;

    private static final Component[] NOMS = new Component[MudraClient.N];
    private static final TexteCache.Texte[] CLES = new TexteCache.Texte[MudraClient.N];
    private static final TexteCache.Texte TITRE = new TexteCache.Texte();
    private static final String[] CLE_TXT = new String[MudraClient.N];
    private static final boolean[] CLE_PONCT = new boolean[MudraClient.N];
    private static long clesPour = Long.MIN_VALUE;

    private static Component messageComp = Component.empty();
    private static String titreTxt = "", titreSrc, messageSrc;
    private static int titreFait = -1, titreTotal = -1;
    /** Images pendant lesquelles les textes des touches sont demandés à l'avance, barre fermée. */
    private static int aPreparer;

    static {
        for (int i = 0; i < MudraClient.N; i++) {
            NOMS[i] = DaKit.arcade(MudraClient.NOMS[i]);
            CLES[i] = new TexteCache.Texte();
        }
    }

    private MudraHud() {}

    /** Le système vient d'être annoncé actif : les textes des touches seront rendus avant la première ouverture. */
    static void preparer() { aPreparer = 30; }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        long now = MudraClient.horloge.getAsLong();
        boolean ouverte = MudraClient.ouverte();
        long finA = MudraClient.finA();
        float sortie = ouverte || finA == Long.MIN_VALUE ? 0f : Math.min(1f, (now - finA) / (float) SORTIE_MS);
        boolean barre = ouverte || (finA != Long.MIN_VALUE && sortie < 1f);
        boolean dit = MudraClient.messageA() != Long.MIN_VALUE && now - MudraClient.messageA() < MudraClient.MESSAGE_MS
            && !MudraClient.message().isEmpty();
        if (!barre && !dit && aPreparer <= 0) return;

        Font f = mc.font;
        int ug = DaKit.echelleGui();
        int w = mc.getWindow().getGuiScaledWidth() * ug, h = mc.getWindow().getGuiScaledHeight() * ug;
        // un texel = z pixels écran, entier (pixel art net)
        int z = Math.max(1, Math.min(Math.round(h / 540f), (int) (w * 0.94 / TOTAL)));
        // touches et titre : gabarit des rappels de touches (px du 1080p × k), jamais sous 0,7
        double k = Math.max(0.7, z / 2.0);
        int cote = COTE * z, pas = PAS * z;
        // fenêtre plus étroite que la rangée au plus petit texel : le pas se resserre, les cases ne sortent pas de l'écran
        if (11 * pas + cote > w * 0.94) pas = Math.max(cote + 2 * z, (int) ((w * 0.94 - cote) / 11));
        int total = 11 * pas + cote, x0 = (w - total) / 2;
        int touche = (int) Math.round(CASE_TOUCHE * k);
        int base = h - Math.round(h * 0.135f);
        int yCle = base - touche, yJauge = yCle - 4 * z - 4 * z, yCase = yJauge - 5 * z - cote;
        int yNom = yCase - 4 * z - 7 * z, yTitre = yNom - 6 * z - 7 * z;

        if (!barre && aPreparer > 0) {
            aPreparer--;
            if (CLE_TXT[0] == null) nommerTouches();
            for (int i = 0; i < MudraClient.N; i++) cle(i, k, x0 + i * pas + cote / 2.0, yCle + touche / 2.0);
        }
        DaKit.debutPx(g);
        if (barre) {
            if (clesPour != MudraClient.ouverteA()) nommerTouches();
            float fin = 1f - sortie;
            int dyFin = MudraClient.finOk() ? Math.round(sortie * 5 * z) : 0;
            boolean guide = MudraClient.guide();
            int attendu = MudraClient.attendu();
            long t0 = MudraClient.ouverteA();

            // 1) cases du kit, en un lot (la cellule porte l'ombre : case en (2, 1))
            LotBlit l = LotBlit.ouvrir(g, CASES, CASES_L, CELLULE);
            for (int i = 0; i < MudraClient.N; i++) {
                float e = entree(now, t0, i);
                float a = e * fin;
                if (a <= 0.01f) continue;
                boolean choisie = i == attendu || bosse(now, i) > 0.01f;
                l.etire(x0 + i * pas - 2 * z, yCase(yCase, dyFin, e, now, i, z) - z, CELLULE * z, CELLULE * z,
                    choisie ? CELLULE : 0, 0, CELLULE, CELLULE, DaKit.blanc(a));
            }
            l.fermer();
            // 2) mains des signes, en un lot
            // sous 2 texels par pixel la main est prise dans l'atlas réduit (96 → 30 px au filtrage linéaire crénelait) ;
            // lue un demi-texel en retrait : la main voisine ne bave pas quand la texture est agrandie (4K)
            int main = MAIN * z, marge = (cote - main) / 2, t = main <= T_PETIT ? T_PETIT : T;
            l = LotBlit.ouvrir(g, t == T ? MAINS : MAINS_PETIT, t * MudraClient.N, t);
            for (int i = 0; i < MudraClient.N; i++) {
                float e = entree(now, t0, i);
                float a = e * fin;
                if (a <= 0.01f) continue;
                int teinte = guide && i != attendu && bosse(now, i) <= 0.01f ? 0x8C8C8C : 0xFFFFFF;
                l.etire(x0 + i * pas + marge, yCase(yCase, dyFin, e, now, i, z) + marge, main, main, i * t + 0.5f, 0, t - 1, t,
                    DaKit.argb(teinte, a));
            }
            l.fermer();
            // 3) jauge du temps
            float aj = entree(now, t0, 0) * fin;
            if (aj > 0.01f) {
                int yj = yJauge + dyFin;
                g.fill(x0 - z, yj - z, x0 + total + z, yj + 4 * z, DaKit.argb(J_BORD, aj));
                g.fill(x0, yj, x0 + total, yj + 3 * z, DaKit.argb(J_PISTE, aj));
                int plein = Math.round(total * MudraClient.reste(now));
                if (plein > 0) {
                    g.fill(x0, yj, x0 + plein, yj + 3 * z, DaKit.argb(J_PLEIN, aj));
                    g.fill(x0, yj, x0 + plein, yj + z, DaKit.argb(J_CRETE, aj));
                    g.fill(x0 + plein - z, yj, x0 + plein, yj + 3 * z, DaKit.argb(J_BOUT, aj));
                }
            }
            // 4) cases des touches (celles des rappels), en un lot
            l = RappelsTouches.lotCases(g);
            for (int i = 0; i < MudraClient.N; i++) {
                float a = entree(now, t0, i) * fin;
                if (a <= 0.01f) continue;
                RappelsTouches.caseTouche(l, x0 + i * pas + (cote - touche) / 2, yCle + dyFin, touche, touche, DaKit.blanc(a));
            }
            l.fermer();
            // 5) noms et touches
            for (int i = 0; i < MudraClient.N; i++) {
                float e = entree(now, t0, i);
                float a = e * fin;
                if (a <= 0.01f) continue;
                float cx = x0 + i * pas + cote / 2f;
                boolean choisie = i == attendu || bosse(now, i) > 0.01f;
                float an = guide && !choisie ? 0.55f * a : a;
                arc(g, f, NOMS[i], cx, yNom + (yCase(yCase, dyFin, e, now, i, z) - yCase), z, choisie ? OR_CLAIR : CREME, an);
                cle(i, k, cx, yCle + dyFin + touche / 2.0).dessinerPx(g, an);
            }
            if (guide && ouverte) {
                TITRE.ligne(titre(), TexteCache.Style.TOUCHE, 13 * k, k, 0, w / 2.0, yTitre + 3.5 * z).dessinerPx(g, fin);
            }
        }
        DaKit.finPx(g);
        if (dit) {
            long age = now - MudraClient.messageA();
            float a = Math.min(1f, (MudraClient.MESSAGE_MS - age) / 300f);
            String m = MudraClient.message();
            if (!m.equals(messageSrc)) { messageSrc = m; messageComp = DaKit.inter(m); }
            int col = MudraClient.messageRefus() ? DaKit.argb(VERM, a) : DaKit.argb(CREME, 0.85f * a);
            DaKit.inter7o(g, f, messageComp, mc.getWindow().getGuiScaledWidth() / 2f, (yTitre + 3.5f * z) / ug, col, 1);
        }
    }

    /** Haut de la case i : sa place, la montée de l'entrée, le sursaut du signe composé, la descente de la sortie. */
    private static int yCase(int yCase, int dyFin, float entree, long now, int i, int z) {
        return yCase + dyFin + Math.round((1f - entree) * 4 * z) - Math.round(2 * z * bosse(now, i));
    }

    /** Le texte de la touche i, dans sa case (Inter gras des rappels de touches). */
    private static TexteCache.Texte cle(int i, double k, double cx, double yCentre) {
        return CLES[i].ligne(CLE_TXT[i], CLE_PONCT[i] ? TexteCache.Style.TOUCHE_PONCT : TexteCache.Style.TOUCHE,
            CAP_TOUCHE * k, k, 0, cx, yCentre);
    }

    /**
     * Texte en ArcadePix, centré sur {@code xCentre}, haut des capitales en {@code yHaut} (pixels écran), au facteur entier
     * {@code s}, avec son ombre d'un pixel de police.
     */
    private static void arc(GuiGraphicsExtractor g, Font f, Component c, float xCentre, int yHaut, int s, int rgb, float a) {
        if (a <= 0.02f) return;
        int larg = (f.width(c) - 1) * s;
        g.pose().pushMatrix();
        g.pose().translate(Math.round(xCentre - larg / 2f), yHaut);
        g.pose().scale(s, s);
        g.text(f, c, 1, 1, DaKit.argb(OMBRE, 0.85f * a), false);
        g.text(f, c, 0, 0, DaKit.argb(rgb, a), false);
        g.pose().popMatrix();
    }

    /** Apparition de la case i (0 à 1), en cascade de gauche à droite. */
    private static float entree(long now, long t0, int i) {
        float t = (now - t0 - i * CASCADE_MS) / (float) ENTREE_MS;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return 1f - (1f - t) * (1f - t);
    }

    /** Sursaut de la case i après un signe (0 à 1 à 0 sur {@value #APPUI_MS} ms). */
    private static float bosse(long now, int i) {
        long age = now - MudraClient.appui(i);
        if (age < 0 || age >= APPUI_MS) return 0f;
        return (float) Math.sin(Math.PI * age / (double) APPUI_MS);
    }

    /**
     * Ce qui s'écrit sous chaque case : les chiffres 1 à 9 puis 0 pour la rangée des chiffres (sur un clavier AZERTY la
     * touche porte « &amp; », « é »… mais chacun l'appelle par son chiffre), et le vrai nom des deux touches à droite du
     * zéro. Relu à chaque ouverture (le joueur a pu changer de clavier).
     */
    private static void nommerTouches() {
        clesPour = MudraClient.ouverteA();
        for (int i = 0; i < MudraClient.N; i++) {
            String t = i < 9 ? String.valueOf(i + 1) : i == 9 ? "0" : HudKeybinds.nom(MudraClient.TOUCHES[i]);
            if (t == null || t.isEmpty() || t.length() > 2) t = i == 10 ? "-" : "=";
            CLE_TXT[i] = t;
            CLE_PONCT[i] = i >= 10 && !Character.isLetterOrDigit(t.charAt(0));
        }
    }

    private static String titre() {
        String src = MudraClient.titre();
        int fait = MudraClient.fait(), n = MudraClient.suite().length;
        if (fait != titreFait || n != titreTotal || !src.equals(titreSrc)) {
            titreFait = fait;
            titreTotal = n;
            titreSrc = src;
            String rang = Math.min(n, fait + 1) + " / " + n;
            titreTxt = src.isEmpty() ? rang : src + "  ·  " + rang;
        }
        return titreTxt;
    }
}
