package fr.reborn.hud.mudra;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.TexteCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * La <b>barre de mudras</b> (14/11) : douze médaillons en ligne en bas de l'écran, le nom du signe au-dessus, la touche
 * dessous, un filet doré qui raccourcit avec le temps. Rien d'autre : ni plaque, ni fond, ni trace de la suite composée
 * (il faut la connaître). En mode guidé (apprentissage), le signe attendu reste clair, les autres pâlissent, et le nom de
 * la technique s'écrit au-dessus avec l'avancement.
 *
 * <p>Gabarit en pixels écran entiers ({@link DaKit#debutPx}), agrandi le 14/11 à la demande du propriétaire (« la taille
 * est un peu trop petite », la référence remplit ~91 % de la largeur) : unité {@code u} = hauteur de fenêtre / 360 (3 en
 * 1080p, 2 en 768p, 4 en 1440p), bornée pour que la rangée tienne dans 94 % de la largeur. Médaillon 32 u (96 px en
 * 1080p), 50 u d'axe en axe, soit 91 % de la largeur en 1080p. Texture {@code gui/mudras/medaillons.png} : douze
 * médaillons de 96 × 96 tirés des vrais dessins des signes (15/11, Schlum, Wikimedia Commons, CC BY-SA 4.0 ; générateur
 * {@code tools/gen_mudras_textures.py}), lissée ({@code .mcmeta} « blur »), à 1:1 en 1080p. Les médaillons partent en un
 * seul lot.
 *
 * <p><b>Noms des signes en ArcadePix</b>, la police du menu principal (demande du 14/11, précisée le 15/11 : « que pour
 * celle des noms des mudras ») : capitales sans accents, à un facteur entier (capitales de 14 px en 1080p), ombre d'un
 * pixel de police, {@link Component} gardés. <b>Touches, titre et messages</b> : comme les rappels de touches du bas à
 * droite (Inter gras de {@link TexteCache}, case sombre).
 *
 * <p>Animations : entrée en cascade (0,4 s), signe composé = le médaillon monte et grossit 0,4 s, sortie en fondu 0,3 s
 * (en descendant si la suite est bonne).
 */
public final class MudraHud {

    private static final Identifier TEX = DaKit.tex("mudras", "medaillons");
    /** Côté d'un médaillon : dans la texture (pixels), et à l'écran (unités u). */
    private static final int T = 96, TW = T * MudraClient.N, D = 32;
    private static final int PAS = 50, COTE = 11;
    private static final int OR = 0xE8C27A, VERM = 0xE0563C, CASE = 0x14110D, CREME = 0xF4E8D2, OMBRE = 0x0A0406;
    private static final long ENTREE_MS = 160L, CASCADE_MS = 22L, SORTIE_MS = 300L, APPUI_MS = 400L;

    private static final Component[] NOMS = new Component[MudraClient.N];
    private static final TexteCache.Texte[] CLES = new TexteCache.Texte[MudraClient.N];
    private static final TexteCache.Texte TITRE = new TexteCache.Texte();
    private static final String[] CLE_TXT = new String[MudraClient.N];
    private static final boolean[] CLE_PONCT = new boolean[MudraClient.N];
    private static long clesPour = Long.MIN_VALUE;

    private static Component messageComp = Component.empty();
    private static String titreTxt = "", titreSrc, messageSrc;
    /** Images pendant lesquelles les textes des touches sont demandés à l'avance, barre fermée. */
    private static int aPreparer;
    private static int titreFait = -1, titreTotal = -1;

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
        int u = Math.max(1, Math.min(Math.round(h / 360f), (int) (w * 0.94 / (11 * PAS + D))));
        // touches, titre : même gabarit que les rappels de touches (px du 1080p × k), jamais sous 0,7
        double k = Math.max(0.7, u / 3.0);
        // facteur entier de la police (capitales de 7 px × s) : 2 en 1080p, 1 en 768p, 3 en 1440p
        int s = Math.max(1, Math.round(u * 2 / 3f));
        int d = D * u, pas = PAS * u, total = 11 * pas + d, x0 = (w - total) / 2;
        // la case suit son texte quand il est à sa taille plancher (très petite fenêtre, u = 1)
        int ep = Math.max(2, u), cote = Math.max(COTE * u, (int) Math.ceil(33 * k));
        int base = h - Math.round(h * 0.135f);
        int yCase = base - ep - 4 * u - cote, yMed = yCase - 3 * u - d;
        int yNom = yMed - 3 * u - 7 * s, yTitre = yNom - 6 * u - 7 * s;

        if (!barre && aPreparer > 0) {
            aPreparer--;
            if (CLE_TXT[0] == null) nommerTouches();
            for (int i = 0; i < MudraClient.N; i++) cle(i, k, x0 + i * pas + d / 2.0, yCase + cote / 2.0);
        }
        DaKit.debutPx(g);
        if (barre) {
            if (clesPour != MudraClient.ouverteA()) nommerTouches();
            float fin = 1f - sortie;
            int dyFin = MudraClient.finOk() ? Math.round(sortie * 10 * u) : 0;
            boolean guide = MudraClient.guide();
            int attendu = MudraClient.attendu();
            long t0 = MudraClient.ouverteA();

            // 1) médaillons, en un lot
            LotBlit l = LotBlit.ouvrir(g, TEX, TW, T);
            for (int i = 0; i < MudraClient.N; i++) {
                float e = entree(now, t0, i);
                float a = e * fin;
                if (a <= 0.01f) continue;
                float b = bosse(now, i);
                int taille = d + Math.round(d * 0.15f * b);
                int x = x0 + i * pas - (taille - d) / 2;
                int y = yMed + dyFin + Math.round((1f - e) * 6 * u) - Math.round(5 * u * b) - (taille - d) / 2;
                int teinte = guide && i != attendu && b <= 0.01f ? 0x8C8C8C : 0xFFFFFF;
                l.etire(x, y, taille, taille, i * T, 0, T, T, DaKit.argb(teinte, a));
            }
            l.fermer();
            // 2) cases des touches, repère du signe attendu, filet du temps
            for (int i = 0; i < MudraClient.N; i++) {
                float a = entree(now, t0, i) * fin;
                if (a <= 0.01f) continue;
                int x = x0 + i * pas + (d - cote) / 2, y = yCase + dyFin;
                int c = DaKit.argb(CASE, 0.62f * a);
                g.fill(x + u, y, x + cote - u, y + cote, c);
                g.fill(x, y + u, x + u, y + cote - u, c);
                g.fill(x + cote - u, y + u, x + cote, y + cote - u, c);
                if (i == attendu) {
                    int xr = x0 + i * pas + d / 2 - 8 * u;
                    g.fill(xr, yCase - 2 * u + dyFin, xr + 16 * u, yCase - u + dyFin, DaKit.argb(OR, 0.95f * a));
                }
            }
            if (ouverte) {
                int plein = Math.round(total * MudraClient.reste(now));
                if (plein > 0) g.fill(x0, base - ep, x0 + plein, base, DaKit.argb(OR, 0.9f * entree(now, t0, 0)));
            }
            // 3) noms et touches
            for (int i = 0; i < MudraClient.N; i++) {
                float a = entree(now, t0, i) * fin;
                if (a <= 0.01f) continue;
                float cx = x0 + i * pas + d / 2f;
                float an = guide && i != attendu ? 0.55f * a : a;
                arc(g, f, NOMS[i], cx, yNom + dyFin - Math.round(5 * u * bosse(now, i)), s, CREME, an);
                cle(i, k, cx, yCase + dyFin + cote / 2.0).dessinerPx(g, an);
            }
            if (guide && ouverte) {
                TITRE.ligne(titre(), TexteCache.Style.TOUCHE, 20 * k, k, 0, w / 2.0, yTitre + 3.5 * s).dessinerPx(g, fin);
            }
        }
        DaKit.finPx(g);
        if (dit) {
            long age = now - MudraClient.messageA();
            float a = Math.min(1f, (MudraClient.MESSAGE_MS - age) / 300f);
            String m = MudraClient.message();
            if (!m.equals(messageSrc)) { messageSrc = m; messageComp = DaKit.inter(m); }
            int col = MudraClient.messageRefus() ? DaKit.argb(VERM, a) : DaKit.argb(CREME, 0.85f * a);
            DaKit.inter7o(g, f, messageComp, mc.getWindow().getGuiScaledWidth() / 2f, (yTitre + 3.5f * s) / ug, col, 1);
        }
    }

    /** Le texte de la touche i, dans sa case (Inter gras des rappels de touches). */
    private static TexteCache.Texte cle(int i, double k, double cx, double yCentre) {
        return CLES[i].ligne(CLE_TXT[i], CLE_PONCT[i] ? TexteCache.Style.TOUCHE_PONCT : TexteCache.Style.TOUCHE,
            15 * k, k, 0, cx, yCentre);
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

    /** Apparition du médaillon i (0 à 1), en cascade de gauche à droite. */
    private static float entree(long now, long t0, int i) {
        float t = (now - t0 - i * CASCADE_MS) / (float) ENTREE_MS;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return 1f - (1f - t) * (1f - t);
    }

    /** Sursaut du médaillon i après un signe (0 à 1 à 0 sur {@value #APPUI_MS} ms). */
    private static float bosse(long now, int i) {
        long age = now - MudraClient.appui(i);
        if (age < 0 || age >= APPUI_MS) return 0f;
        return (float) Math.sin(Math.PI * age / (double) APPUI_MS);
    }

    /**
     * Ce qui s'écrit sous chaque médaillon : les chiffres 1 à 9 puis 0 pour la rangée des chiffres (sur un clavier AZERTY
     * la touche porte « &amp; », « é »… mais chacun l'appelle par son chiffre), et le vrai nom des deux touches à droite du
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
