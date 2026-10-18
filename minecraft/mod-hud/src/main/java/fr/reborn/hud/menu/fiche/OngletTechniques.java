package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.stats.StatsMath;
import fr.reborn.hud.menu.stats.StatsSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Onglet TECHNIQUES (maquette 04) : personnage au centre (lacet −28), planche DÉTAIL à gauche (titrée du nom de la
 * technique), LISTE défilante à droite (EN APPRENTISSAGE, NINJUTSU, TAIJUTSU, KENJUTSU, FONDAMENTAUX), même gabarit
 * pour chaque ligne (rang, nom, kanji de la stat principale, palier en mots). La couronne n'allume que les stats qui
 * nourrissent la technique choisie ; des fils de lumière les relient à la tête.
 */
final class OngletTechniques implements Onglet {

    private static String choixMemo = null;

    private enum Type { TITRE, APPR, TECH }

    /** Ligne de la liste ; y = haut de la ligne, relatif au haut du contenu. */
    private record Ligne(Type type, int y, int h, String id, Component titre, StatsData.Tech tech,
                         FicheData.Apprentissage appr, Component nom, Component mot, int maitrise, StatDef stat,
                         String lettre, Component sous, Component suite, boolean or) {}

    private final List<Ligne> lignes = new ArrayList<>();
    private int contenuH;
    private float defile, defileCible;
    private String choix;
    private int survolLigne = -1;
    private boolean glisseAscenseur;
    private FicheScene.Braises braises;
    private final FicheScreen.LanterneCouronne[] couronne = new FicheScreen.LanterneCouronne[6];
    private final boolean[] allumees = new boolean[6];
    private int hListe, hDetail;
    private final Component point = FicheArt.inter("·");

    // ---- détail (recalculé au choix)
    private Component dTitre, dMeta, dOrigine, dMaitrise, dSeances;
    private String dLettre;
    /** Nature de la technique affichée : son icône au coin du panneau (14/10) ; rien sans nature (18/10). */
    private fr.reborn.hud.ui.NatureIcones.Nature dNat;
    private int dMaitriseN = -1, dFaites, dTotal;
    /** Signes des mains nommés en français (« Tigre · Bélier · Rat ») : plus de plaquettes à kanji (18/10). */
    private final List<String> dMudras = new ArrayList<>();
    private FicheArt.Paragraphe dMudrasTxt = FicheArt.VIDE;
    private FicheArt.Paragraphe dDesc = FicheArt.VIDE, dCout = FicheArt.VIDE, dNourrie = FicheArt.VIDE;
    private FicheArt.Etiq eOrigine, eMaitrise, eMudras, eSeances;

    @Override
    public Identifier fond() { return FicheArt.FOND_TECHNIQUES; }

    @Override
    public float lacet() { return -28f; }

    @Override
    public int persoX(FicheScreen s) { return s.lay().px; }

    @Override
    public String[][] aides() { return AIDES; }

    private static final String[][] AIDES = {{"MOLETTE", "défiler"}, {"CLIC", "détails"}, {"K", "fermer"}};

    @Override
    public FicheScene.Braises braises() { return braises; }

    /* ================================================================ mise en page */

    /** Kanji et couleur de la stat principale d'une catégorie (apprentissage). */
    private static StatDef statCategorie(String cat) {
        String root = cat == null ? "" : cat.split("/")[0].toLowerCase(java.util.Locale.ROOT);
        return switch (root) {
            case "taijutsu" -> StatDef.TAIJUTSU;
            case "kenjutsu", "bukijutsu" -> StatDef.KENJUTSU;
            case "ninjutsu" -> StatDef.NINJUTSU;
            default -> StatDef.CONTROLE;
        };
    }

    private static String sousAppr(int done, int total) {
        if (total <= 0 || done <= 0) return "Premières bases";
        float r = done / (float) total;
        if (r < 0.45f) return "Premières bases";
        if (r < 0.8f) return "En bonne voie";
        return "Presque acquise";
    }

    @Override
    public void layout(FicheScreen s) {
        Font f = s.police();
        FicheLayout L = s.lay();
        StatsData.Snapshot sn = s.snap();
        FicheData.Donnees d = s.data();
        braises = new FicheScene.Braises(24, 0x5AA0E0, 21, 46, 9, 70);
        eOrigine = FicheArt.etiq(f, "ORIGINE");
        eMaitrise = FicheArt.etiq(f, "MAITRISE");
        eMudras = FicheArt.etiq(f, "MUDRAS");
        eSeances = FicheArt.etiq(f, "SEANCES");
        hListe = L.hauteurPanneau(FicheArt.P_TECHNIQUES.h(), true);
        hDetail = L.hauteurPanneau(FicheArt.P_DETAIL.h(), false);
        lignes.clear();
        int nomMax = 166 - 34 - 10;
        int yy = 0;
        String premier = null;
        if (!d.apprentissage().isEmpty()) {
            lignes.add(titre(yy, "EN APPRENTISSAGE"));
            yy += 14;
            for (FicheData.Apprentissage a : d.apprentissage()) {
                Component nom = FicheArt.inter(FicheArt.ellipse(f, a.name(), nomMax));
                boolean or = "MAITRE".equals(a.etat()) || "PRET".equals(a.etat());
                lignes.add(new Ligne(Type.APPR, yy, 38, "appr:" + a.id(), null, null, a, nom, null, 0,
                    statCategorie(a.cat()), a.letter(), FicheArt.inter(sousAppr(a.done(), a.total())),
                    FicheArt.inter(a.txt()), or));
                yy += 38;
            }
            yy += 4;
        }
        for (int g = 0; g < FicheTextes.GROUPES.length; g++) {
            String grp = FicheTextes.GROUPES[g];
            List<StatsData.Tech> ts = new ArrayList<>();
            for (StatsData.Tech t : sn.techniques()) {
                FicheData.TechPlus p = d.techs().get(t.id());
                String tg = p != null && p.groupe() != null ? p.groupe() : FicheTextes.groupe(t.category(), t.rank());
                if (grp.equals(tg)) ts.add(t);
            }
            if (ts.isEmpty()) continue;
            lignes.add(titre(yy, grp));
            yy += 14;
            for (StatsData.Tech t : ts) {
                FicheData.TechPlus p = d.techs().get(t.id());
                Component nom = FicheArt.inter(FicheArt.ellipse(f, t.name(), nomMax));
                int m = p != null && p.palier() != null ? Math.max(0, Math.min(2, p.palier())) : FicheTextes.palierMaitrise(t.mastery());
                String mot = p != null && p.palierTxt() != null ? p.palierTxt() : FicheTextes.PALIERS_MAITRISE[m];
                String lettre = p != null && p.letter() != null ? p.letter() : FicheTextes.lettre(t.rank());
                List<StatDef> tri = trier(t.scaling());
                StatDef st = tri.isEmpty() ? statCategorie(t.category()) : tri.get(0);
                lignes.add(new Ligne(Type.TECH, yy, 22, t.id(), null, t, null, nom, FicheArt.inter(mot), m, st, lettre,
                    null, null, false));
                if (premier == null) premier = t.id();
                yy += 22;
            }
            yy += 4;
        }
        contenuH = yy;
        if (choix == null || ligneDe(choix) < 0) choix = choixMemo != null && ligneDe(choixMemo) >= 0 ? choixMemo : premier;
        if (choix == null && !lignes.isEmpty()) {
            for (Ligne l : lignes) if (l.type() != Type.TITRE) { choix = l.id(); break; }
        }
        detail(s);
        defileCible = Math.max(0, Math.min(maxDefile(), defileCible));
        defile = defileCible;
    }

    private static Ligne titre(int y, String t) {
        return new Ligne(Type.TITRE, y, 14, null, FicheArt.arcade(t), null, null, null, null, 0, null, null, null, null,
            false);
    }

    private static List<StatDef> trier(Map<StatDef, String> sc) {
        List<Map.Entry<StatDef, String>> l = new ArrayList<>(sc.entrySet());
        l.sort((a, b) -> FicheTextes.poidsGrade(b.getValue()) - FicheTextes.poidsGrade(a.getValue()));
        List<StatDef> out = new ArrayList<>();
        for (int i = 0; i < Math.min(2, l.size()); i++) out.add(l.get(i).getKey());
        return out;
    }

    private int ligneDe(String id) {
        if (id == null) return -1;
        for (int i = 0; i < lignes.size(); i++) if (id.equals(lignes.get(i).id())) return i;
        return -1;
    }

    /** Choisit une technique (clic, ↑ ↓, banc). */
    void choisir(FicheScreen s, String id) {
        if (ligneDe(id) < 0) {
            if (ligneDe("appr:" + id) >= 0) id = "appr:" + id;
            else return;
        }
        choix = id;
        choixMemo = id;
        detail(s);
        visible(s);
    }

    private void detail(FicheScreen s) {
        Font f = s.police();
        int i = ligneDe(choix);
        dMudras.clear();
        dDesc = dCout = dNourrie = dMudrasTxt = FicheArt.VIDE;
        dOrigine = dMaitrise = dSeances = null;
        dMaitriseN = -1;
        int X1 = FicheArt.PW - 28;
        java.util.Arrays.fill(allumees, false);
        if (i < 0) {
            dTitre = FicheArt.arcade("TECHNIQUES");
            dMeta = FicheArt.inter("Aucune technique apprise.");
            dLettre = "";
            dNat = null;
            for (int k = 0; k < 6; k++) couronne[k] = lanterne(s, k, false);
            return;
        }
        Ligne l = lignes.get(i);
        String nom = l.tech() != null ? l.tech().name() : l.appr().name();
        String court = nom.contains("—") ? nom.substring(nom.indexOf('—') + 1).trim() : nom;
        String titre = FicheArt.sansAccents(court);
        while (f.width(FicheArt.arcade(titre)) > 180 && titre.length() > 4) titre = titre.substring(0, titre.length() - 1);
        dTitre = FicheArt.arcade(titre);
        dLettre = l.lettre();
        if (l.tech() != null) {
            StatsData.Tech t = l.tech();
            FicheData.TechPlus p = d(s).techs().get(t.id());
            String grp = p != null && p.groupe() != null ? p.groupe() : FicheTextes.groupe(t.category(), t.rank());
            int nat = FicheTextes.nature(t.nature());
            dNat = nat >= 0 ? FicheTextes.NATURES.get(nat).icone() : null;
            String meta = (FicheArt.rangDeBase(dLettre) ? "Fondamentale" : "Rang " + dLettre) + "  ·  "
                + FicheTextes.groupeNom(grp) + (nat >= 0 ? "  ·  " + FicheTextes.NATURES.get(nat).nom() : "");
            dMeta = FicheArt.inter(FicheArt.ellipse(f, meta, X1 - 24));
            String o = p != null ? FicheTextes.origine(p.origin()) : null;
            if (o != null) dOrigine = FicheArt.inter(o);
            dMaitriseN = l.maitrise();
            dMaitrise = l.mot();
            if (p != null && p.mudras() != null) {
                for (String m : p.mudras()) {
                    if (dMudras.size() >= 4) break;
                    dMudras.add(m);
                }
                if (!dMudras.isEmpty()) dMudrasTxt = FicheArt.paragraphe(f, String.join("  ·  ", dMudras), X1 - 70, false, 12, 2);
            }
            if (p != null && p.desc() != null) dDesc = FicheArt.paragraphe(f, p.desc(), X1, false, 13, 3);
            if (t.castable()) {
                int casts = StatsMath.casts(s.snap().levers(), s.rep().previewStats(), t);
                dCout = FicheArt.paragraphe(f, (t.stamina() ? "Se paie en endurance : " : "Se paie en chakra : ")
                    + FicheTextes.castWords(casts) + " à réserve pleine.", X1 - 12, true, 10, 2);
            }
            List<Map.Entry<StatDef, String>> tri = new ArrayList<>(t.scaling().entrySet());
            tri.sort((a, b) -> FicheTextes.poidsGrade(b.getValue()) - FicheTextes.poidsGrade(a.getValue()));
            String nour = FicheTextes.nourrie(tri);
            if (nour != null) dNourrie = FicheArt.paragraphe(f, nour, X1 - 10, true, 10, 2);
            for (int k = 0; k < 6; k++) {
                allumees[k] = t.scaling().containsKey(StatDef.values()[k]);
                couronne[k] = lanterne(s, k, allumees[k]);
            }
        } else {
            FicheData.Apprentissage a = l.appr();
            String grp = FicheTextes.groupe(a.cat(), a.letter());
            int nat = FicheTextes.nature(a.nature());
            dNat = nat >= 0 ? FicheTextes.NATURES.get(nat).icone() : null;
            dMeta = FicheArt.inter((dLettre.isEmpty() ? "" : "Rang " + dLettre + "  ·  ") + FicheTextes.groupeNom(grp)
                + (nat >= 0 ? "  ·  " + FicheTextes.NATURES.get(nat).nom() : ""));
            dSeances = FicheArt.inter(FicheTextes.avancement(a.done(), a.total()));
            dFaites = a.done();
            dTotal = a.total();
            dCout = FicheArt.paragraphe(f, a.txt(), X1 - 12, true, 10, 2);
            for (int k = 0; k < 6; k++) couronne[k] = lanterne(s, k, false);
        }
    }

    private static FicheData.Donnees d(FicheScreen s) { return s.data(); }

    private FicheScreen.LanterneCouronne lanterne(FicheScreen s, int k, boolean on) {
        int[][] pos = s.lay().arc();
        return new FicheScreen.LanterneCouronne(pos[k][0], pos[k][1] - (on ? 8 : 0), on ? 1f : 0.45f,
            on ? FicheScene.Flamme.GRANDE : FicheScene.Flamme.BRAISE, 1f, true);
    }

    private int vueH() { return hListe - 50; }

    private float maxDefile() { return Math.max(0, contenuH + 12 - vueH()); }

    private void visible(FicheScreen s) {
        int i = ligneDe(choix);
        if (i < 0) return;
        Ligne l = lignes.get(i);
        float top = l.y() - 14, bas = l.y() + l.h();
        if (top < defileCible) defileCible = top;
        if (bas > defileCible + vueH()) defileCible = bas - vueH();
        defileCible = Math.max(0, Math.min(maxDefile(), defileCible));
    }

    /* ================================================================ rendu */

    @Override
    public void scene(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        if (!s.fondJeu) {
            int c = FicheArt.desat(s.villageRgb(), 0.8f);
            FicheArt.lumiere(ctx, FicheArt.LUM_VILLAGE, L.cx, 336 + L.d, c, 0.20f);
            FicheArt.lumiere(ctx, FicheArt.LUM_COEUR, L.cx, 300 + L.d, FicheArt.desat(0xF6C27A, 0.8f), 0.06f);
            FicheArt.lumiere(ctx, FicheArt.LUM_FLAQUE_L, L.cx, 456 + L.d, c, 0.16f);
        }
        if (L.compact || couronne[0] == null) return;
        FicheScene.Rect[] r = s.couronne(ctx, couronne, -1);
        // fils de lumière des lanternes allumées vers la tête
        float k = L.echelle / FichePortrait.ECHELLE;
        float jx = s.lay().px, jy = L.piedsY - 197 * k;
        boolean un = false;
        StatDef[] st = StatDef.values();
        for (int i = 0; i < 6; i++) {
            if (!allumees[i] || r[i] == null) continue;
            un = true;
            float sx = r[i].x() + r[i].w() / 2f - 0.25f, sy = r[i].y() + r[i].h() - 1;
            float[][] pts = {{sx, sy}, {sx + (jx - sx) * 0.15f, sy + (jy - sy) * 0.45f},
                {jx + (sx - jx) * 0.18f, jy - 14}, {jx, jy}};
            FicheArt.courbe(ctx, pts, FicheArt.desat(st[i].color, 0.85f), 0.75f, 0.25f);
        }
        if (un) {
            FicheArt.halo(ctx, jx, jy, 7, 1f, 0xBFD8FF, 0.22f, 2);
            FicheArt.disque(ctx, jx, jy, 1.1f, 0xE8F2FF, 1f);
        }
    }

    @Override
    public void avant(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        defile += (defileCible - defile) * 0.35f;
        if (Math.abs(defileCible - defile) < 0.3f) defile = defileCible;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, s.dyPanneau(0));
        detailPanneau(s, ctx);
        ctx.pose().popMatrix();
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, s.dyPanneau(1));
        liste(s, ctx, mx, my - s.dyPanneau(1));
        ctx.pose().popMatrix();
    }

    /** Couleur de la pastille de stat (losange de la liste, du « nourrie par »). */
    private static int couleurStat(StatDef st) {
        return FicheArt.opaque(FicheArt.desat(FicheArt.mix(st.color, 0xF0E6D2, 0.15f), 0.8f));
    }

    private void detailPanneau(FicheScreen s, GuiGraphicsExtractor ctx) {
        FicheLayout L = s.lay();
        Font f = s.police();
        int x0 = L.panG, y0 = L.panY;
        FicheArt.panneau(ctx, FicheArt.P_DETAIL, x0, y0, hDetail);
        FicheArt.arcadeX1(ctx, f, dTitre, x0 + 14, y0 + 13, FicheArt.OR_CLAIR, true);
        if (dNat != null) fr.reborn.hud.ui.NatureIcones.dessiner(ctx, dNat, x0 + FicheArt.PW - 28, y0 + 8, 16);
        ctx.enableScissor(x0, y0 + 30, x0 + FicheArt.PW, y0 + hDetail - 4);
        int X = x0 + 14, X1 = x0 + FicheArt.PW - 14;
        int yy = y0 + 46;
        if (dLettre != null && (!dLettre.isEmpty() || dMaitriseN >= 0)) FicheArt.sceauRang(ctx, dLettre, X + 9, yy);
        FicheArt.inter9(ctx, f, dMeta, X + 24, yy, FicheArt.CREME, 0);
        yy += 15;
        ctx.fill(X, yy, X1, yy + 1, FicheArt.SEP);
        yy += 13;
        if (dOrigine != null) {
            ligne(ctx, f, X, X1, yy, eOrigine);
            FicheArt.inter9(ctx, f, dOrigine, X + 70, yy, FicheArt.CREME, 0);
            yy += 18;
        }
        if (dMaitriseN >= 0) {
            ligne(ctx, f, X, X1, yy, eMaitrise);
            FicheArt.tampon(ctx, X + 70, yy, dMaitriseN >= 1);
            FicheArt.inter9(ctx, f, dMaitrise, X + 78, yy, FicheArt.CREME, 0);
            yy += 24;
        } else if (dSeances != null) {
            ligne(ctx, f, X, X1, yy, eSeances);
            perles(ctx, X + 72, yy, dFaites, dTotal);
            yy += 24;
        }
        if (!dMudras.isEmpty()) {
            ligne(ctx, f, X, X1, yy + Math.max(0, dMudrasTxt.hauteur() - 12), eMudras);
            dMudrasTxt.dessiner(ctx, f, X + 70, yy - FicheArt.INTER9_MIL, FicheArt.CREME);
            yy += 12 + dMudrasTxt.hauteur();
        } else {
            yy += 4;
        }
        ctx.fill(X, yy, X1, yy + 1, FicheArt.R_OR[3]);
        ctx.fill(X, yy + 1, X1, yy + 2, FicheArt.R_OR[0]);
        yy += 10;
        if (!dDesc.lignes().isEmpty()) {
            dDesc.dessiner(ctx, f, X, yy - 1, FicheArt.CREME);
            yy += dDesc.hauteur() + 8;
        }
        if (dSeances != null) {
            FicheArt.ornementCoin(ctx, FicheArt.ORN_LOSANGE, X + 2, yy + 3);
            FicheArt.inter7(ctx, f, dSeances, X + 12, yy + 4, FicheArt.DOUX, 0);
            yy += 16;
        }
        if (!dCout.lignes().isEmpty()) {
            FicheArt.ornementCoin(ctx, FicheArt.ORN_LOSANGE, X + 2, yy + 3);
            dCout.dessiner(ctx, f, X + 12, yy, FicheArt.DOUX);
            yy += Math.max(16, dCout.hauteur()) + 6;
        }
        if (!dNourrie.lignes().isEmpty()) {
            FicheArt.ornementCoin(ctx, FicheArt.ORN_LOSANGE, X + 2, yy + 3);
            dNourrie.dessiner(ctx, f, X + 12, yy, FicheArt.DOUX);
        }
        ctx.disableScissor();
    }

    private static void ligne(GuiGraphicsExtractor ctx, Font f, int X, int X1, int yy, FicheArt.Etiq e) {
        FicheArt.etiq(ctx, f, e, X, yy, FicheArt.OR_ETIQ);
        ctx.fill(X, yy + 9, X1, yy + 10, FicheArt.SEP);
    }

    /** Losange plein 7×7 (demi-diagonale 3) à la couleur de la stat, bord sombre d'1 GUI, centré en (cx, cy). */
    private static void losange(GuiGraphicsExtractor ctx, int cx, int cy, int col) {
        for (int dy = -4; dy <= 4; dy++) {
            int hw = 4 - Math.abs(dy);
            ctx.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, 0xFF070405);
        }
        for (int dy = -3; dy <= 3; dy++) {
            int hw = 3 - Math.abs(dy);
            ctx.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, col);
        }
    }

    /** Perles de séances (10 au plus : au-delà, une perle vaut plusieurs séances). */
    private static void perles(GuiGraphicsExtractor ctx, int x, int y, int faites, int total) {
        int n = Math.min(10, Math.max(1, total));
        int pleines = total <= 10 ? faites : Math.round(faites * 10f / total);
        for (int k = 0; k < n; k++) {
            FicheArt.petit(ctx, k < pleines ? FicheArt.PETIT_PERLE : FicheArt.PETIT_PERLE_VIDE, x + k * 6, y);
        }
    }

    private void liste(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        Font f = s.police();
        int x0 = L.listeX, y0 = L.panY, w = FicheArt.PW;
        FicheArt.panneau(ctx, FicheArt.P_TECHNIQUES, x0, y0, hListe);
        int X0 = x0 + 12, X1 = x0 + w - 16;
        int cRang = X0 + 9, cNom = X0 + 22, cSt = x0 + 166, cMot = x0 + 182;
        int clipTop = y0 + 32, clipBas = y0 + hListe - 4;
        int oy = y0 + 40 - Math.round(defile);
        survolLigne = -1;
        if (lignes.isEmpty()) {
            FicheArt.inter9(ctx, f, FicheArt.inter("Aucune technique apprise."), x0 + w / 2f, y0 + 80, FicheArt.DOUX, 1);
            FicheArt.inter7(ctx, f, FicheArt.inter("Les parchemins et les maîtres t'attendent."), x0 + w / 2f, y0 + 96,
                FicheArt.DOUX, 1);
            return;
        }
        ctx.enableScissor(x0 + 1, clipTop, x0 + w - 1, clipBas);
        for (int i = 0; i < lignes.size(); i++) {
            Ligne l = lignes.get(i);
            int yt = oy + l.y();
            if (yt + l.h() < clipTop - 4 || yt > clipBas) continue;
            if (l.type() == Type.TITRE) {
                int yh = yt + 6;
                FicheArt.ornementCoin(ctx, FicheArt.ORN_LOSANGE, X0, yh - 1);
                float tw = FicheArt.arcFin(ctx, f, l.titre(), X0 + 7, yh - 1.75f, FicheArt.OR_MOYEN, 0, false);
                int fx = Math.round(X0 + 7 + tw + 5);
                ctx.fill(fx, yh, X1, yh + 1, FicheArt.R_OR[2]);
                continue;
            }
            int yc = yt + 11;
            boolean sel = l.id().equals(choix);
            boolean sv = mx >= x0 + 6 && mx < x0 + w - 16 && my >= yt && my < yt + l.h() && my >= clipTop && my < clipBas;
            if (sv) {
                survolLigne = i;
                s.survol("tech" + i);
            }
            if (sel) FicheArt.blit(ctx, FicheArt.LIGNE_CHOISIE, x0 - 3, yc - 11, 0, 0, 228, 24, 228, 24);
            else if (sv) ctx.fill(x0 + 6, yc - 11, x0 + w - 16, yc + 11, 0x14F2D08A);
            FicheArt.sceauRang(ctx, l.lettre(), cRang, yc);
            FicheArt.inter9(ctx, f, l.nom(), cNom, yc, FicheArt.CREME, 0);
            losange(ctx, cSt, yc, couleurStat(l.stat()));
            if (l.type() == Type.APPR) {
                int yp = yc + 16;
                FicheArt.tampon(ctx, cNom, yp, l.or());
                int we = FicheArt.inter9(ctx, f, l.sous(), cNom + 8, yp, FicheArt.CREME_VAL, 0);
                int ws = FicheArt.inter9(ctx, f, point, cNom + 8 + we + 4, yp, FicheArt.OR_ETEINT, 0);
                FicheArt.inter9(ctx, f, l.suite(), cNom + 8 + we + ws + 8, yp, l.or() ? FicheArt.OR_CLAIR : FicheArt.DOUX, 0);
            } else {
                FicheArt.tampon(ctx, cMot, yc, l.maitrise() >= 1);
                FicheArt.inter9(ctx, f, l.mot(), cMot + 8, yc, l.maitrise() == 2 ? FicheArt.OR_CLAIR : FicheArt.DOUX, 0);
            }
        }
        ctx.disableScissor();
        // fondu de défilement (au-dessus du texte), puis ascenseur
        FicheArt.blit(ctx, FicheArt.LISTE_BAS, x0 - 4, y0 + hListe - 22, 0, 0, 248, 28, 248, 28);
        float max = maxDefile();
        if (max > 0) {
            int railY = y0 + 36, railH = hListe - 48;
            ctx.fill(X1 + 8, railY, X1 + 9, railY + railH, FicheArt.R_OR[1]);
            int th = Math.max(12, Math.round(railH * vueH() / (float) (contenuH + 12)));
            int ty = railY + 4 + Math.round((railH - 8 - th) * (defile / max));
            ctx.fill(X1 + 7, ty, X1 + 10, ty + th, FicheArt.R_OR[3]);
            ctx.fill(X1 + 7, ty, X1 + 8, ty + th, FicheArt.R_OR[5]);
        }
    }

    /* ================================================================ entrées */

    @Override
    public boolean click(FicheScreen s, double mx, double my, int button, boolean shift) {
        if (button != 0) return false;
        FicheLayout L = s.lay();
        int X1 = L.listeX + FicheArt.PW - 16;
        if (maxDefile() > 0 && mx >= X1 + 4 && mx < X1 + 13 && my >= L.panY + 36 && my < L.panY + hListe - 12) {
            glisseAscenseur = true;
            glisse(s, mx, my);
            return true;
        }
        if (survolLigne >= 0) {
            Ligne l = lignes.get(survolLigne);
            if (!l.id().equals(choix)) {
                choix = l.id();
                choixMemo = choix;
                detail(s);
                StatsSounds.hover();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean glisse(FicheScreen s, double mx, double my) {
        if (!glisseAscenseur) return false;
        FicheLayout L = s.lay();
        float t = (float) ((my - (L.panY + 36)) / Math.max(1, hListe - 48));
        defileCible = Math.max(0, Math.min(maxDefile(), t * maxDefile()));
        defile = defileCible;
        return true;
    }

    @Override
    public void relache(FicheScreen s) { glisseAscenseur = false; }

    @Override
    public boolean scroll(FicheScreen s, double mx, double my, double dy) {
        defileCible = Math.max(0, Math.min(maxDefile(), defileCible - (float) dy * 22f));
        return true;
    }

    @Override
    public boolean key(FicheScreen s, int key, boolean shift) {
        if (key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) return false;
        int dir = key == GLFW.GLFW_KEY_UP ? -1 : 1;
        int i = ligneDe(choix);
        for (int j = i + dir; j >= 0 && j < lignes.size(); j += dir) {
            if (lignes.get(j).type() != Type.TITRE) {
                choisir(s, lignes.get(j).id());
                StatsSounds.hover();
                break;
            }
        }
        return true;
    }

    /** Pour le banc : stats de scaling de la technique choisie. */
    Map<StatDef, String> scalingChoisi() {
        int i = ligneDe(choix);
        return i >= 0 && lignes.get(i).tech() != null ? lignes.get(i).tech().scaling() : new EnumMap<>(StatDef.class);
    }
}
