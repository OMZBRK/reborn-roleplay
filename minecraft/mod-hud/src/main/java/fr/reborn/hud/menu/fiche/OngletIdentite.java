package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;
import fr.reborn.hud.menu.stats.StatsData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Onglet IDENTITÉ (maquettes 01 et 06) : planche REGISTRE à gauche, LIGNÉE ET SERVICE à droite (pendues aux perches),
 * couronne des six lanternes au-dessus du personnage. Les lignes absentes (ancien serveur) sont masquées et les
 * suivantes remontent.
 */
final class OngletIdentite implements Onglet {

    /** Une ligne du Registre, déjà mise en page. */
    private record Ligne(FicheArt.Etiq etiq, int yy, Component valeur, int sepY) {}

    private record Nat(int idx, Component nom, int x) {}

    private record Entree(int col, String kj, String rang, Component txt, Component date, int cy) {}

    private static final String SERVICE_KJ = "登昇降任功記伝";

    private final List<Ligne> registre = new ArrayList<>();
    private int clanCol, villageIdx, clanY = -1, villageY = -1, gradeY = -1;
    private Component clanNom, villageNom, gradeNom;
    private String gradeKj = "";
    private int naturesY = -1;
    private final List<Nat> natures = new ArrayList<>();
    private Component naturesVide;
    private FicheArt.Paragraphe naturesNote = FicheArt.VIDE, citation = FicheArt.VIDE;
    private int citationY;

    // Lignée
    private Component dojutsuNom;
    private FicheArt.Paragraphe dojutsuTxt = FicheArt.VIDE, meriteTxt = FicheArt.VIDE;
    private int dojutsuY = -1, dojutsuSep, meriteY, serviceY;
    private final List<Entree> service = new ArrayList<>();
    private Component histoire;
    private int filTop, filH;
    private FicheArt.Etiq eDojutsu, eMerite;
    private final Component titreService = FicheArt.arcade("ETATS DE SERVICE");

    private int hReg, hLig;
    private final FicheScreen.LanterneCouronne[] couronne = new FicheScreen.LanterneCouronne[6];
    private FicheScene.Rect[] rects = new FicheScene.Rect[0];
    private int survol = -1;
    private FicheScreen.Bulle bulle;
    private FicheScene.Braises braises;

    @Override
    public Identifier fond() { return FicheArt.FOND_IDENTITE; }

    @Override
    public float lacet() { return 28f; }

    @Override
    public String[][] aides() { return AIDES; }

    private static final String[][] AIDES = {{"GLISSER", "tourner"}, {"DOUBLE CLIC", "de face"}, {"K", "fermer"}};

    @Override
    public FicheScene.Braises braises() { return braises; }

    /* ================================================================ mise en page */

    @Override
    public void layout(FicheScreen s) {
        Font f = s.police();
        FicheLayout L = s.lay();
        StatsData.Snapshot sn = s.snap();
        FicheData.Donnees d = s.data();
        braises = new FicheScene.Braises(30, s.villageRgb(), 3, 46, 9, 70);
        eDojutsu = FicheArt.etiq(f, "DOJUTSU");
        eMerite = FicheArt.etiq(f, "MERITE");

        // ---- REGISTRE (maquette : X = x0+14, CV 76, pas 20)
        registre.clear();
        natures.clear();
        hReg = L.hauteurPanneau(FicheArt.P_REGISTRE.h(), false);
        int X = L.panG + 14, CV = 76, WL = FicheArt.PW - 28, P = 20;
        int yy = L.panY + 46;
        clanY = yy;
        clanCol = FicheTextes.colClan(sn.clan());
        clanNom = FicheArt.inter(sn.clan().isBlank() ? "Sans clan" : sn.clan());
        registre.add(new Ligne(FicheArt.etiq(f, "CLAN"), yy, null, yy + P / 2));
        yy += P;
        villageY = yy;
        villageIdx = FicheTextes.village(sn.village());
        villageNom = FicheArt.inter(sn.village());
        registre.add(new Ligne(FicheArt.etiq(f, "VILLAGE"), yy, null, yy + P / 2));
        yy += P;
        gradeY = yy;
        gradeNom = FicheArt.inter(sn.rank());
        gradeKj = FicheTextes.kanjiGrade(d.rankId(), sn.rank());
        registre.add(new Ligne(FicheArt.etiq(f, "GRADE"), yy, null, yy + P / 2));
        yy += P;
        FicheData.Identite id = d.identite();
        if (id != null) {
            if (id.age() != null && id.age() > 0) {
                registre.add(new Ligne(FicheArt.etiq(f, "AGE"), yy, FicheArt.inter(id.age() + " ans"), yy + P / 2));
                yy += P;
            }
            if (id.taille() != null && !id.taille().isBlank()) {
                registre.add(new Ligne(FicheArt.etiq(f, "TAILLE"), yy, FicheArt.inter(id.taille()), yy + P / 2));
                yy += P;
            }
            if (id.sexe() != null && !id.sexe().isBlank()) {
                registre.add(new Ligne(FicheArt.etiq(f, "SEXE"), yy, FicheArt.inter(id.sexe()), yy + P / 2));
                yy += P;
            }
        }
        yy += 2;
        naturesY = yy;
        int nx = X + CV;
        for (String n : sn.natures()) {
            int idx = FicheTextes.nature(n);
            if (idx < 0) continue;
            Component c = FicheArt.inter(FicheTextes.NATURES.get(idx).nom());
            natures.add(new Nat(idx, c, nx));
            nx += 22 + f.width(c) + 8;
            if (nx > X + WL - 30) break;
        }
        naturesVide = natures.isEmpty() ? FicheArt.inter("Aucune pour l'instant") : null;
        String note = id != null ? id.naturesNote() : null;
        naturesNote = FicheArt.paragraphe(f, note, WL - CV, true, 10, 1);
        boolean aNote = !naturesNote.lignes().isEmpty();
        registre.add(new Ligne(FicheArt.etiq(f, "NATURES"), yy, null, aNote ? yy + 23 : yy + P / 2));
        yy += aNote ? 36 : P + 2;
        citation = FicheArt.VIDE;
        boolean aTraits = id != null && id.traits() != null && !id.traits().isEmpty();
        boolean aCitation = id != null && id.citation() != null && !id.citation().isBlank();
        if (aTraits || aCitation) {
            registre.add(new Ligne(FicheArt.etiq(f, "CARACTERE"), yy,
                aTraits ? FicheArt.inter(String.join(" · ", id.traits())) : null, -1));
            if (aCitation) {
                citation = FicheArt.paragraphe(f, "« " + id.citation() + " »", WL - CV, true, 10, 2);
                citationY = yy + 10;
            }
        }

        // ---- LIGNÉE ET SERVICE (maquette : CV 66)
        hLig = L.hauteurPanneau(FicheArt.P_LIGNEE.h(), true);
        int CVL = 66;
        yy = L.panY + 46;
        FicheData.Lignee li = d.lignee();
        dojutsuY = -1;
        if (li != null && li.dojutsu() != null) {
            dojutsuY = yy;
            dojutsuNom = FicheArt.interGras(li.dojutsu().nom());
            dojutsuTxt = FicheArt.paragraphe(f, li.dojutsu().txt(), WL - CVL, true, 10, 2);
            int hp = dojutsuTxt.hauteur();
            dojutsuSep = yy + 10 + hp;
            yy += 22 + hp;
        }
        meriteY = yy;
        int palier = li != null && li.meritePalier() != null ? li.meritePalier()
            : d.merit() != null ? d.merit() / 3 : 0;
        String mt = li != null && li.meriteTxt() != null ? li.meriteTxt() : FicheTextes.merite(palier);
        meriteTxt = FicheArt.paragraphe(f, mt, WL - CVL - 26, 1f, 12, 2, false);
        yy += 30;
        serviceY = yy;
        service.clear();
        histoire = null;
        if (li == null || li.service().isEmpty()) {
            histoire = FicheArt.inter(FicheTextes.HISTOIRE);
        } else {
            int yj = yy + 22, pas = 26;
            int bas = L.panY + hLig - 10;
            int place = Math.max(1, (bas - 8 - yj) / pas + 1);
            List<FicheData.Service> sv = li.service();
            List<FicheData.Service> vis = sv.subList(Math.max(0, sv.size() - place), sv.size());
            for (int k = 0; k < vis.size(); k++) {
                FicheData.Service e = vis.get(k);
                String kj = e.kj() != null && !e.kj().isBlank() ? e.kj() : null;
                // Mission avec rang : le sceau de rang (D…S) remplace le hanko générique.
                String rang = kj == null && "MISSION".equals(e.type()) && !FicheArt.rangDeBase(e.rang()) ? e.rang() : null;
                int col = FicheTextes.colService(e.type());
                if (kj != null) {
                    int ix = SERVICE_KJ.indexOf(kj);
                    if (ix >= 0 && kj.length() == 1) { col = ix; kj = null; } else col = FicheArt.H_SERVICE_VIERGE;
                }
                service.add(new Entree(col, kj, rang, FicheArt.inter(FicheArt.ellipse(f, e.txt(), WL - 26)),
                    FicheArt.inter(e.date() == null ? "" : e.date()), yj + k * pas));
            }
            filTop = yj;
            filH = (vis.size() - 1) * pas;
        }

        // ---- couronne : intensité 0,62 + 0,38·(0,45 + 0,11·palier) ; les paliers bas pendent plus bas
        int[][] pos = L.arc();
        for (int i = 0; i < 6; i++) {
            int v = s.rep().valide(i);
            int p = FicheTextes.palierIdx(v, sn.max());
            float l = 0.62f + 0.38f * (0.45f + 0.11f * p);
            couronne[i] = new FicheScreen.LanterneCouronne(pos[i][0], pos[i][1] + 6 * Math.max(0, 2 - p), l,
                FicheScene.Flamme.GRANDE, 1f, true);
        }
        bulle = null;
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
        survol = -1;
        for (int i = 0; i < rects.length; i++) {
            FicheScene.Rect r = rects[i];
            if (r != null && mx >= r.x() - 3 && mx < r.x() + r.w() + 3 && my >= r.y() - 4 && my < r.y() + r.h() + 2) survol = i;
        }
        rects = s.couronne(ctx, couronne, survol);
        if (survol >= 0) s.survol("couronne" + survol);
    }

    @Override
    public void avant(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        Font f = s.police();
        int WL = FicheArt.PW - 28;
        // ---- REGISTRE
        int x0 = L.panG;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, s.dyPanneau(0));
        FicheArt.panneau(ctx, FicheArt.P_REGISTRE, x0, L.panY, hReg);
        FicheArt.kanjiTitre(ctx, f, "籍", x0, L.panY);
        ctx.enableScissor(x0, L.panY + 30, x0 + FicheArt.PW, L.panY + hReg - 4);
        int X = x0 + 14, CV = 76;
        for (Ligne l : registre) {
            FicheArt.etiq(ctx, f, l.etiq(), X, l.yy(), FicheArt.OR_ETIQ);
            if (l.valeur() != null) FicheArt.inter9(ctx, f, l.valeur(), X + CV, l.yy(), FicheArt.CREME, 0);
            if (l.sepY() >= 0) ctx.fill(X, l.sepY(), X + WL, l.sepY() + 1, FicheArt.SEP);
        }
        FicheArt.hanko(ctx, clanCol, FicheArt.H_CLANS, X + CV + 9, clanY);
        FicheArt.inter9(ctx, f, clanNom, X + CV + 22, clanY, FicheArt.CREME, 0);
        if (villageIdx >= 0) FicheArt.embleme(ctx, villageIdx, X + CV + 1, villageY - 7, 16, FicheArt.CREME);
        FicheArt.inter9(ctx, f, villageNom, X + CV + (villageIdx >= 0 ? 22 : 0), villageY, FicheArt.CREME, 0);
        int wg = FicheArt.inter9(ctx, f, gradeNom, X + CV, gradeY, FicheArt.CREME, 0);
        if (!gradeKj.isEmpty()) FicheArt.kanji16g(ctx, f, gradeKj, X + CV + wg + 6, gradeY - 8, FicheArt.R_OR[3], 0);
        for (Nat n : natures) {
            // icône peinte de la nature (décision du 14/10), plus de sceau kanji
            fr.reborn.hud.ui.NatureIcones.dessiner(ctx, FicheTextes.NATURES.get(n.idx()).icone(), n.x() + 1, naturesY - 8, 16);
            FicheArt.inter9(ctx, f, n.nom(), n.x() + 22, naturesY, FicheArt.CREME, 0);
        }
        if (naturesVide != null) FicheArt.inter9(ctx, f, naturesVide, X + CV, naturesY, FicheArt.DOUX, 0);
        naturesNote.dessiner(ctx, f, X + CV, naturesY + 15 - FicheArt.INTER7_MIL, FicheArt.DOUX);
        citation.dessiner(ctx, f, X + CV, citationY, FicheArt.DOUX);
        ctx.disableScissor();
        ctx.pose().popMatrix();

        // ---- LIGNÉE ET SERVICE
        int x1 = L.panD;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, s.dyPanneau(1));
        FicheArt.panneau(ctx, FicheArt.P_LIGNEE, x1, L.panY, hLig);
        FicheArt.kanjiTitre(ctx, f, "系", x1, L.panY);
        ctx.enableScissor(x1, L.panY + 30, x1 + FicheArt.PW, L.panY + hLig - 4);
        int XL = x1 + 14, CVL = 66;
        if (dojutsuY >= 0) {
            FicheArt.etiq(ctx, f, eDojutsu, XL, dojutsuY, FicheArt.OR_ETIQ);
            FicheArt.inter9(ctx, f, dojutsuNom, XL + CVL, dojutsuY, FicheArt.CREME, 0);
            dojutsuTxt.dessiner(ctx, f, XL + CVL, dojutsuY + 8, FicheArt.DOUX);
            ctx.fill(XL, dojutsuSep, XL + WL, dojutsuSep + 1, FicheArt.SEP);
        }
        FicheArt.etiq(ctx, f, eMerite, XL, meriteY, FicheArt.OR_ETIQ);
        FicheArt.plaqueMerite(ctx, XL + CVL, meriteY - 10);
        FicheArt.kanji16(ctx, f, "功", XL + CVL + 10, meriteY - 8, FicheArt.OR_CLAIR, 0xFF3A0A12);
        meriteTxt.dessiner(ctx, f, XL + CVL + 26, meriteY - 7, FicheArt.CREME);
        ctx.fill(XL, meriteY + 15, XL + WL, meriteY + 16, FicheArt.SEP);
        FicheArt.arcadeX1(ctx, f, titreService, XL, serviceY - 3, FicheArt.OR_MOYEN, false);
        if (histoire != null) {
            FicheArt.inter7(ctx, f, histoire, XL, serviceY + 18, FicheArt.DOUX, 0);
        } else {
            if (filH > 0) {
                ctx.fill(XL + 9, filTop, XL + 10, filTop + filH, FicheArt.R_OR[2]);
                ctx.fill(XL + 10, filTop, XL + 11, filTop + filH, FicheArt.R_OR[0]);
            }
            for (Entree e : service) {
                if (e.rang() != null) {
                    FicheArt.sceauRang(ctx, e.rang(), XL + 10, e.cy());
                } else {
                    FicheArt.hanko(ctx, e.col(), FicheArt.H_SERVICE, XL + 10, e.cy());
                    if (e.kj() != null) FicheArt.kanji16(ctx, f, e.kj(), XL + 10, e.cy() - 8, FicheArt.CREME_KJ, 0xFF4E0C0B);
                }
                FicheArt.inter9(ctx, f, e.txt(), XL + 24, e.cy() - 4, FicheArt.CREME, 0);
                FicheArt.inter7(ctx, f, e.date(), XL + 24, e.cy() + 7, FicheArt.DOUX, 0);
            }
        }
        ctx.disableScissor();
        ctx.pose().popMatrix();
    }

    @Override
    public void infobulle(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        if (survol < 0 || survol >= rects.length || rects[survol] == null) return;
        StatDef d = StatDef.values()[survol];
        int v = s.rep().valide(survol);
        String cle = d.name() + v + ":" + v + false;
        if (bulle == null || !bulle.cle.equals(cle)) {
            bulle = new FicheScreen.Bulle(s.police(), d, v, v, s.snap().max(), false);
        }
        FicheScene.Rect r = rects[survol];
        int ax = r.x() + r.w() / 2;
        int y = r.y() + r.h() + 16;
        int x = Math.max(8, Math.min(s.lay().w - 8 - bulle.w, ax - bulle.w / 2));
        s.dessinerBulle(ctx, bulle, x, y, new float[][]{{ax, r.y() + r.h()}, {ax, y + 1}});
    }

    @Override
    public boolean click(FicheScreen s, double mx, double my, int button, boolean shift) {
        if (button == 0 && survol >= 0) {
            s.changerOnglet(FicheScreen.ATTRIBUTS);
            return true;
        }
        return false;
    }
}
