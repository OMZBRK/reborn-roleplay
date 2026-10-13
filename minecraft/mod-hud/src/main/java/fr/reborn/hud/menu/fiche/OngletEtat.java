package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.combat.CombatState;
import fr.reborn.hud.menu.stats.StatsPayload;
import fr.reborn.hud.runtime.VitalsFeed;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Onglet ÉTAT (maquette 05) : lumière froide, personnage DE FACE (rotation bloquée), AUCUNE couleur peinte sur le skin.
 * Planche CORPS (sceau du titre, mèches VIE / CHAKRA / ENDURANCE, VOLONTÉ, SOINS), planche CE QUE TU RESSENS (une
 * carte laquée par blessure, en sensations, sans la blessure cachée), sceaux posés sur le vide près du membre touché,
 * reliés au bord de la silhouette par un fil kumihimo. Vie, chakra et endurance suivent les vitals en direct.
 */
final class OngletEtat implements Onglet {

    private static long dernierRefresh;

    /** Ancrage d'une zone (personnage de face, 6 GUI/texel) : {côté, x du bord de la silhouette, y} par rapport aux pieds. */
    private static int[] ancre(String zone) {
        return switch (zone == null ? "" : zone) {
            case "TETE" -> new int[]{-1, -27, -168};
            case "COU" -> new int[]{-1, -27, -146};
            case "VENTRE" -> new int[]{1, 52, -100};
            case "BRAS_G" -> new int[]{1, 52, -86};
            case "BRAS_D" -> new int[]{-1, -52, -86};
            case "JAMBE_G" -> new int[]{1, 27, -40};
            case "JAMBE_D" -> new int[]{-1, -27, -40};
            default -> new int[]{-1, -52, -110};                 // TORSE
        };
    }

    private record Carte(FicheArt.Etiq zone, Component txt, int col, String zoneId) {}

    private record Jauge(FicheArt.Etiq etiq, String cle, int rang, List<FicheTextes.Cran> echelle) {}

    private FicheScene.Braises braises;
    private final FicheScreen.LanterneCouronne[] couronne = new FicheScreen.LanterneCouronne[6];
    private String titre;
    private Component titreTxt;
    private FicheArt.Paragraphe sousTxt = FicheArt.VIDE, chapeau = FicheArt.VIDE;
    private final List<Jauge> jauges = new ArrayList<>();
    private final Component[] jaugeTxt = new Component[4];
    private final int[] jaugeLvl = new int[4];
    private final String[] jaugeCle = new String[4];
    private final List<Carte> cartes = new ArrayList<>();
    private Component vide, autres, soins;
    private FicheArt.Etiq eSoins;
    private boolean grand;
    private int hCorps, hRessens, chapeauY;
    private int survolCarte = -1;
    /** Par carte : {sx (centre du sceau), y, ex (bord de la silhouette), côté}. */
    private final int[][] sceaux = new int[4][4];

    @Override
    public Identifier fond() { return FicheArt.FOND_ETAT; }

    @Override
    public float lacet() { return 0f; }

    @Override
    public float tangage() { return 6f; }

    @Override
    public boolean rotationBloquee() { return true; }

    @Override
    public String[][] aides() { return AIDES; }

    private static final String[][] AIDES = {{"SURVOL", "montrer la zone"}, {"TAB", "onglet"}, {"K", "fermer"}};

    @Override
    public FicheScene.Braises braises() { return braises; }

    @Override
    public void entrer(FicheScreen s) {
        // Données fraîches : « refresh » seulement si le serveur connaît le format v2 (au plus 1 par seconde).
        long now = System.currentTimeMillis();
        if (s.data().v() >= 2 && now - dernierRefresh > 1000L && ClientPlayNetworking.canSend(StatsPayload.ID)) {
            dernierRefresh = now;
            ClientPlayNetworking.send(new StatsPayload("refresh"));
        }
    }

    /* ================================================================ mise en page */

    @Override
    public void layout(FicheScreen s) {
        Font f = s.police();
        FicheLayout L = s.lay();
        FicheData.Etat e = s.data().etat();
        braises = new FicheScene.Braises(10, s.villageRgb(), 33, 46, 9, 24);
        int[][] pos = L.arc();
        for (int i = 0; i < 6; i++) {
            couronne[i] = new FicheScreen.LanterneCouronne(pos[i][0], pos[i][1], 0.45f, FicheScene.Flamme.BRAISE, 0.6f, true);
        }
        titre = e != null ? e.titre() : titreRepli();
        titreTxt = FicheArt.arcade(e != null && e.titreTxt() != null ? e.titreTxt() : FicheTextes.titreTxt(titre));
        sousTxt = FicheArt.paragraphe(f, e != null && e.sousTxt() != null ? e.sousTxt() : FicheTextes.sousTitre(titre),
            FicheArt.PW - 28, true, 10, 1);

        jauges.clear();
        jauges.add(new Jauge(FicheArt.etiq(f, "VIE"), "vie", 0, echelle(e, "vie", FicheTextes.VIE)));
        jauges.add(new Jauge(FicheArt.etiq(f, "CHAKRA"), "chakra", 1, echelle(e, "chakra", FicheTextes.CHAKRA)));
        if (e == null || e.endurance() != null) {
            jauges.add(new Jauge(FicheArt.etiq(f, "ENDURANCE"), "endurance", 2, echelle(e, "endurance", FicheTextes.ENDURANCE)));
        }
        if (e != null && e.volonte() != null) jauges.add(new Jauge(FicheArt.etiq(f, "VOLONTE"), "volonte", 3, List.of()));
        eSoins = FicheArt.etiq(f, "SOINS");
        java.util.Arrays.fill(jaugeCle, null);
        majJauges(s);

        hCorps = L.hauteurPanneau(FicheArt.P_CORPS.h(), false);
        cartes.clear();
        vide = null;
        autres = null;
        soins = null;
        if (e == null) {
            vide = FicheArt.inter(FicheTextes.SANS_SENSATION);
        } else {
            List<FicheData.Blessure> bl = new ArrayList<>(e.blessures());
            bl.sort((a, b) -> Integer.compare(ancre(a.zone())[2], ancre(b.zone())[2]));
            for (FicheData.Blessure b : bl) {
                if (cartes.size() >= 4) { autres = FicheArt.inter(FicheTextes.AUTRES_DOULEURS); break; }
                String lab = b.label() != null && !b.label().isBlank() ? b.label() : b.zone().replace('_', ' ');
                cartes.add(new Carte(FicheArt.etiq(f, lab), FicheArt.inter(FicheArt.ellipse(f, b.txt(), 160)),
                    FicheTextes.colBlessure(b.type()), b.zone()));
            }
            if (cartes.isEmpty()) vide = FicheArt.inter(FicheTextes.SANS_DOULEUR);
            String st = e.ataTxt() != null ? e.ataTxt() : e.soins() != null ? e.soins().txt() : null;
            if (st != null && !st.isBlank()) soins = FicheArt.inter(FicheArt.ellipse(f, st, FicheArt.PW - 28 - 82));
        }
        grand = cartes.size() >= 3;
        hRessens = L.hauteurPanneau(grand ? FicheArt.P_RESSENS_GRAND.h() : FicheArt.P_RESSENS.h(), true);
        chapeauY = cartes.isEmpty() ? L.panY + 60 : L.panY + 64 + 44 * (cartes.size() - 1) + 32;
        chapeau = FicheArt.paragraphe(f, FicheTextes.CHAPEAU_RESSENS, FicheArt.PW - 28, true, 10, 2);
        // sceaux sur le vide, près du membre touché ; 20 GUI au moins entre deux du même côté
        float k = L.echelle / FichePortrait.ECHELLE;
        int[] prev = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < cartes.size(); i++) {
            int[] a = ancre(cartes.get(i).zoneId());
            int cote = a[0];
            int side = cote < 0 ? 0 : 1;
            int y = L.piedsY + Math.round(a[2] * k);
            if (y < prev[side] + 20) y = prev[side] + 20;
            prev[side] = y;
            int ex = L.cx + Math.round(a[1] * k);
            sceaux[i][0] = ex + cote * 44;
            sceaux[i][1] = y;
            sceaux[i][2] = ex;
            sceaux[i][3] = cote;
        }
    }

    private static List<FicheTextes.Cran> echelle(FicheData.Etat e, String cle, List<FicheTextes.Cran> def) {
        if (e != null && e.echelles().containsKey(cle)) return e.echelles().get(cle);
        return def;
    }

    private static String titreRepli() {
        VitalsFeed.Vitals v = VitalsFeed.get();
        if (v == null) return "INDEMNE";
        float r = v.hp() / (float) v.maxHp();
        // Mêmes seuils que le serveur (FichePhrases.titreGraveSous / titreBlesseSous).
        return r < 0.3f ? "GRAVE" : r >= 0.7f ? "INDEMNE" : "BLESSE";
    }

    /** Met à jour les phrases des jauges en direct (vitals), seulement quand le cran change. */
    private void majJauges(FicheScreen s) {
        FicheData.Etat e = s.data().etat();
        VitalsFeed.Vitals v = VitalsFeed.get();
        for (int i = 0; i < jauges.size(); i++) {
            Jauge j = jauges.get(i);
            String txt;
            int lvl;
            FicheData.Jauge srv = e == null ? null : switch (j.cle()) {
                case "vie" -> e.vie();
                case "chakra" -> e.chakra();
                case "endurance" -> e.endurance();
                default -> e.volonte();
            };
            Double ratio = null;
            if (v != null && j.rang() == 0) ratio = v.hp() / (double) v.maxHp();
            if (v != null && j.rang() == 1) ratio = v.chakra() / (double) v.maxChakra();
            if (j.rang() == 2 && (srv == null || v != null)) ratio = (double) CombatState.INSTANCE.staminaFraction();
            if (ratio != null && !j.echelle().isEmpty()) {
                FicheTextes.Cran c = FicheTextes.cran(j.echelle(), ratio);
                txt = c.txt();
                lvl = c.lvl();
            } else if (srv != null) {
                txt = srv.txt();
                lvl = srv.lvl();
            } else {
                FicheTextes.Cran c = FicheTextes.cran(j.echelle(), 1.0);
                txt = c.txt();
                lvl = c.lvl();
            }
            String cle = lvl + txt;
            if (!cle.equals(jaugeCle[i])) {
                jaugeCle[i] = cle;
                jaugeTxt[i] = FicheArt.inter(txt);
                jaugeLvl[i] = Math.max(0, Math.min(2, lvl));
            }
        }
    }

    /* ================================================================ rendu */

    @Override
    public void scene(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        if (!s.fondJeu) {
            FicheArt.lumiere(ctx, FicheArt.LUM_NUIT, L.cx, 330 + L.d, 0x2A3558, 0.30f);
            FicheArt.lumiere(ctx, FicheArt.LUM_FLAQUE_ETAT, L.cx, 456 + L.d, 0x3A4A80, 0.14f);
        }
        s.couronne(ctx, couronne, -1);
    }

    @Override
    public void avant(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        Font f = s.police();
        majJauges(s);
        int x1 = L.panD;
        int cx0 = x1 + 11, cw = FicheArt.PW - 22;

        // ---- survol carte ↔ sceau
        survolCarte = -1;
        for (int i = 0; i < cartes.size(); i++) {
            int yc = L.panY + 64 + 44 * i;
            if (mx >= cx0 && mx < cx0 + cw && my >= yc - 19 && my < yc + 19) survolCarte = i;
            if (mx >= sceaux[i][0] - 10 && mx < sceaux[i][0] + 10 && my >= sceaux[i][1] - 10 && my < sceaux[i][1] + 10) {
                survolCarte = i;
            }
        }
        if (survolCarte >= 0) s.survol("carte" + survolCarte);

        // ---- sceaux sur le vide, fil kumihimo jusqu'au bord de la silhouette, petit nœud
        for (int i = 0; i < cartes.size(); i++) {
            int sx = sceaux[i][0], sy = sceaux[i][1], ex = sceaux[i][2], cote = sceaux[i][3];
            int a = sx - cote * 10, b = ex - cote * 2;
            FicheArt.repeteH(ctx, FicheArt.KUMIHIMO, Math.min(a, b), Math.max(a, b) + 1, sy, 0, 0, 32, 2, 4, 2);
            FicheArt.ornementCoin(ctx, FicheArt.ORN_NOEUD, ex - cote - 1, sy - 1);
            if (survolCarte == i) {
                float p = 0.5f + 0.5f * (float) Math.sin(s.scene().ms() / 1000.0 * 6.0);
                FicheArt.halo(ctx, sx, sy, 14f, 1f, 0xF2D08A, 0.16f + 0.10f * p, 2);
            }
            FicheArt.hanko(ctx, cartes.get(i).col(), FicheArt.H_BLESSURES, sx, sy);
        }

        // ---- CORPS
        int x0 = L.panG;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, s.dyPanneau(0));
        FicheArt.panneau(ctx, FicheArt.P_CORPS, x0, L.panY, hCorps);
        FicheArt.kanjiTitre(ctx, f, "身", x0, L.panY);
        ctx.enableScissor(x0, L.panY + 30, x0 + FicheArt.PW, L.panY + hCorps - 4);
        int X = x0 + 14, WL = FicheArt.PW - 28, CV = 82;
        int yy = L.panY + 48;
        FicheArt.hanko(ctx, FicheTextes.colTitre(titre), FicheArt.H_NATURES, X + 9, yy);
        FicheArt.arcadeX1(ctx, f, titreTxt, X + 24, yy - 3, FicheArt.CREME, true);
        sousTxt.dessiner(ctx, f, X, yy + 17 - FicheArt.INTER7_MIL, FicheArt.DOUX);
        ctx.fill(X, yy + 26, X + WL, yy + 27, FicheArt.SEP);
        yy += 42;
        for (int i = 0; i < jauges.size(); i++) {
            Jauge j = jauges.get(i);
            FicheArt.etiq(ctx, f, j.etiq(), X, yy, FicheArt.OR_ETIQ);
            ctx.fill(X, yy + 9, X + WL, yy + 10, FicheArt.SEP);
            if (j.rang() < 3) {
                FicheArt.blit(ctx, FicheArt.MECHES, X + CV, yy - 4, jaugeLvl[i] * 6, 0, 6, 8, 18, 8);
                if (jaugeTxt[i] != null) FicheArt.inter9(ctx, f, jaugeTxt[i], X + CV + 11, yy, FicheArt.CREME, 0);
            } else if (jaugeTxt[i] != null) {
                FicheArt.inter9(ctx, f, jaugeTxt[i], X + CV, yy, FicheArt.CREME, 0);
            }
            yy += 18;
        }
        if (soins != null) {
            FicheArt.etiq(ctx, f, eSoins, X, yy, FicheArt.OR_ETIQ);
            FicheArt.inter9(ctx, f, soins, X + CV, yy, FicheArt.DOUX, 0);
        }
        ctx.disableScissor();
        ctx.pose().popMatrix();

        // ---- CE QUE TU RESSENS
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, s.dyPanneau(1));
        FicheArt.panneau(ctx, grand ? FicheArt.P_RESSENS_GRAND : FicheArt.P_RESSENS, x1, L.panY, hRessens);
        FicheArt.kanjiTitre(ctx, f, "感", x1, L.panY);
        ctx.enableScissor(x1 - 2, L.panY + 30, x1 + FicheArt.PW + 2, L.panY + hRessens - 4);
        for (int i = 0; i < cartes.size(); i++) {
            Carte c = cartes.get(i);
            int yc = L.panY + 64 + 44 * i;
            boolean sv = survolCarte == i;
            FicheArt.blit(ctx, FicheArt.CARTE_BLESSURE, cx0 - 1, yc - 19 - 1, sv ? 224 : 0, 0, 224, 44, 448, 44);
            FicheArt.hanko(ctx, c.col(), FicheArt.H_BLESSURES, cx0 + cw - 18, yc);
            FicheArt.etiq(ctx, f, c.zone(), cx0 + 12, yc - 8, sv ? FicheArt.OR : FicheArt.OR_MOYEN);
            FicheArt.inter9(ctx, f, c.txt(), cx0 + 12, yc + 6, FicheArt.CREME, 0);
        }
        int yl = chapeauY;
        if (vide != null) {
            FicheArt.inter7(ctx, f, vide, x1 + 14, L.panY + 48, FicheArt.DOUX, 0);
        }
        if (autres != null) {
            FicheArt.inter7(ctx, f, autres, x1 + 14, yl - 8, FicheArt.DOUX, 0);
            yl += 6;
        }
        chapeau.dessiner(ctx, f, x1 + 14, yl, FicheArt.DOUX);
        ctx.disableScissor();
        ctx.pose().popMatrix();
    }

    @Override
    public boolean click(FicheScreen s, double mx, double my, int button, boolean shift) {
        return false;
    }
}
