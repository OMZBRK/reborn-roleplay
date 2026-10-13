package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.stats.StatsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Fiche shinobi « Le portrait en scène »</b> (v2, remplace {@code StatsScreen}) : le personnage réel au centre
 * d'une scène de nuit, des panneaux laqués suspendus, des lanternes en arc, quatre onglets pendus — Identité,
 * Attributs, Techniques, État.
 *
 * <p>Règles : ce qui ne bouge pas est cuit dans les textures {@code gui/fiche/*} ; le code ne dessine que ce qui bouge
 * et le texte ; mise en page recalculée seulement au redimensionnement ou à l'arrivée d'un paquet ; aucun chiffre
 * exact hormis le compteur de points ; taille « échelle 2 en 1080p » assurée par MenuScale.
 *
 * <p>Clavier : K / Échap ferment, Tab / Maj+Tab changent d'onglet, ← → tournent le personnage. Attributs : clic +1,
 * clic droit −1, Maj tout, 1-6, Entrée valide. Techniques : molette, ↑ ↓.
 */
public class FicheScreen extends Screen implements fr.reborn.hud.ui.EcranOpaque {

    public static final int IDENTITE = 0, ATTRIBUTS = 1, TECHNIQUES = 2, ETAT = 3;
    private static int dernierOnglet = IDENTITE;

    static final String[] TAB_LIB = {"IDENTITE", "ATTRIBUTS", "TECHNIQUES", "ETAT"};
    static final String[] TAB_KJ = {"名", "灯", "術", "身"};

    /** Variante « jeu voilé » (maquette 06) : le monde visible sous un voile, sans décor. */
    public boolean fondJeu = false;

    private final Onglet[] onglets = {new OngletIdentite(), new OngletAttributs(), new OngletTechniques(), new OngletEtat()};
    private int onglet;
    private FicheLayout lay;
    private final Repartition rep = new Repartition();
    private final FichePortrait portrait = new FichePortrait();
    private final FicheScene scene = new FicheScene();

    private boolean dejaOuvert;
    private boolean prevHidden;
    private long ouvertAt = System.currentTimeMillis();
    private long ongletDepuis = ouvertAt;
    private final boolean[] allumee = new boolean[6];

    private Integer dbgX, dbgY;
    private Object survolCle;
    private int villageRgb = 0xF2A548;
    private List<Component> aidesCache = List.of();

    public FicheScreen() {
        this(dernierOnglet);
    }

    public FicheScreen(int ongletInitial) {
        super(Component.literal("Fiche shinobi"));
        this.onglet = Math.max(0, Math.min(3, ongletInitial));
    }

    /* ================================================================ accès pour les onglets */

    FicheLayout lay() { return lay; }

    Font police() { return this.font; }

    Repartition rep() { return rep; }

    StatsData.Snapshot snap() { return rep.snap(); }

    FicheData.Donnees data() { return FicheData.get(); }

    FicheScene scene() { return scene; }

    FichePortrait portrait() { return portrait; }

    int villageRgb() { return villageRgb; }

    int ongletCourant() { return onglet; }

    boolean compact() { return lay.compact; }

    /* ================================================================ cycle de vie */

    @Override
    protected void init() {
        lay = new FicheLayout(this.width, this.height);
        scene.fondJeu = fondJeu;
        recalculer();
        if (!dejaOuvert) {
            dejaOuvert = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                prevHidden = mc.gui.hud.isHidden();
                ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            }
            ouvertAt = ongletDepuis = System.currentTimeMillis();
            Onglet o = onglets[onglet];
            portrait.poser(o.lacet(), o.tangage(), o.rotationBloquee());
            portrait.forcer(o.lacet());
            StatsSounds.open();
            StatsSounds.startAmbience();
            o.entrer(this);
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHidden);
        dernierOnglet = onglet;
        StatsSounds.stopAmbience();
        super.removed();
    }

    /** Le décor de la scène couvre l'écran (sauf variante « jeu voilé ») : le monde n'est pas rendu derrière. */
    @Override
    public boolean couvreEcran() { return !fondJeu && lay != null; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Nouveau paquet du serveur (écran ouvert) : resynchronise et recalcule, sans son. */
    public void refresh() {
        rep.resync();
        recalculer();
    }

    private void recalculer() {
        if (lay == null) return;
        rep.resync();
        villageRgb = FicheTextes.couleurVillage(snap().village());
        for (Onglet o : onglets) {
            try { o.layout(this); } catch (Exception ignored) { }
        }
        aidesCache = null;
    }

    /* ================================================================ animations d'ouverture */

    private long depuisOuverture() { return System.currentTimeMillis() - ouvertAt; }

    static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** Décalage vertical d'un panneau qui descend le long de ses cordes (260 ms, 60 ms entre deux). */
    int dyPanneau(int k) {
        float t = (System.currentTimeMillis() - ongletDepuis - 60L * k) / 260f;
        if (t >= 1f) return 0;
        if (t <= 0f) return -20;
        float e = easeOut(t);
        float rebond = t > 0.7f ? (float) Math.sin((t - 0.7f) / 0.3f * Math.PI) * 2f : 0f;
        return Math.round(-20 * (1 - e) + rebond);
    }

    /** Allumage d'une lanterne à l'ouverture (0 → 1), une toutes les 60 ms. */
    float allumage(int i) {
        float t = (depuisOuverture() - 100L - 60L * i) / 200f;
        if (t > 0f && !allumee[i]) {
            allumee[i] = true;
            StatsSounds.light(i);
        }
        return Math.max(0f, Math.min(1f, t));
    }

    private void passerIntro() {
        long now = System.currentTimeMillis();
        ouvertAt = now - 3000L;
        ongletDepuis = now - 3000L;
        java.util.Arrays.fill(allumee, true);
    }

    private boolean introEnCours() { return depuisOuverture() < 600L; }

    /* ================================================================ onglets */

    void changerOnglet(int i) {
        if (i == onglet || i < 0 || i > 3) return;
        onglet = i;
        ongletDepuis = System.currentTimeMillis();
        Onglet o = onglets[onglet];
        portrait.poser(o.lacet(), o.tangage(), o.rotationBloquee());
        aidesCache = null;
        StatsSounds.tab();
        o.entrer(this);
    }

    /* ================================================================ rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (lay == null) return;
        Onglet o = onglets[onglet];
        int w = this.width, h = this.height;
        if (fondJeu) {
            ctx.fill(0, 0, w, h, FicheArt.argb(0x0A0608, 0.68f));
            FicheArt.etire(ctx, FicheArt.VIGNETTE_JEU, 0, 0, w, h, 64, 36, FicheArt.argb(0xFFFFFF, 0.45f));
            return;
        }
        int x0 = lay.cx - FicheArt.FOND_W / 2, y0 = lay.d;
        if (y0 > 0) ctx.fill(0, 0, w, y0, o.cielHaut());
        if (x0 > 0) {
            ctx.fill(0, 0, x0, h, FicheArt.FOND);
            ctx.fill(x0 + FicheArt.FOND_W, 0, w, h, FicheArt.FOND);
        }
        FicheArt.blit(ctx, o.fond(), x0, y0, 0, 0, FicheArt.FOND_W, FicheArt.FOND_H, FicheArt.FOND_W, FicheArt.FOND_H);
        float f = depuisOuverture() / 150f;
        if (f < 1f) ctx.fill(0, 0, w, h, FicheArt.argb(0x0A0608, 1f - f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (lay == null) return;
        int mx = dbgX != null ? dbgX : mouseX, my = dbgY != null ? dbgY : mouseY;
        Onglet o = onglets[onglet];
        scene.fondJeu = fondJeu;
        int pxPieds = o.persoX(this);

        // ---- strate du décor : lumières, lanternes, objets, puis le personnage
        try { o.scene(this, ctx, mx, my); } catch (Exception ignored) { }
        FicheScene.Braises b = o.braises();
        scene.braises(ctx, b, pxPieds, lay.piedsY, false, lay.echelle);
        if (!fondJeu) scene.ombrePieds(ctx, pxPieds, lay.piedsY);
        portrait.extract(ctx, pxPieds, lay.piedsY, lay.echelle);
        ctx.nextStratum();

        // ---- devant le personnage : braises, objets, interface
        scene.braises(ctx, b, pxPieds, lay.piedsY, true, lay.echelle);
        if (o.perches()) FicheArt.perches(ctx, lay.panG + FicheArt.PW + 10, lay.panD - 10, lay.w);
        try { o.avant(this, ctx, mx, my); } catch (Exception ignored) { }
        entete(ctx, mx, my);
        cartouche(ctx);
        aides(ctx, o);
        ctx.nextStratum();

        // ---- infobulle
        try { o.infobulle(this, ctx, mx, my); } catch (Exception ignored) { }
    }

    /** Son de survol limité, joué quand l'objet survolé change. */
    void survol(Object cle) {
        if (cle != null && !cle.equals(survolCle)) StatsSounds.hover();
        survolCle = cle;
    }

    /* ---------------------------------------------------------------- en-tête */

    private void entete(GuiGraphicsExtractor ctx, int mx, int my) {
        Font f = this.font;
        float t = depuisOuverture() / 220f;
        int dy = t >= 1f ? 0 : Math.round(-36 * (1 - easeOut(t)));
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, dy);
        FicheArt.blit(ctx, FicheArt.ENTETE, lay.enteteX, 0, 0, 0, 320, 48, 320, 48);
        Object cle = null;
        for (int i = 0; i < 4; i++) {
            int tx = lay.tabX[i];
            boolean ac = i == onglet;
            boolean sv = !ac && dansOnglet(i, mx, my);
            if (sv) cle = "onglet" + i;
            int cell = ac ? 2 : sv ? 1 : 0;
            FicheArt.blit(ctx, FicheArt.ONGLETS, tx - 13, 36, cell * 26, 0, 26, 36, 78, 36);
            int ty = ac ? 46 : 44;
            FicheArt.kanji16(ctx, f, TAB_KJ[i], tx, ty + 3, ac ? FicheArt.OR_CLAIR : sv ? FicheArt.OR : FicheArt.R_OR[4],
                ac ? 0xFF1A0608 : 0);
            FicheArt.arcFin(ctx, f, tabLib[i], tx, ty + 22 + 4, ac ? FicheArt.CREME : sv ? FicheArt.CREME_VAL
                : FicheArt.ONGLET_REPOS, 1, ac);
            if (i == 1 && rep.remaining() > 0) FicheArt.ornementCoin(ctx, FicheArt.ORN_PASTILLE, tx + 7, ty - 2);
        }
        boolean svF = dansFermer(mx, my);
        if (svF) cle = "fermer";
        FicheArt.blit(ctx, FicheArt.FERMER, lay.fermerX - 13, 0, svF ? 26 : 0, 0, 26, 38, 52, 38);
        FicheArt.arcFin(ctx, f, toucheK, lay.fermerX, lay.fermerY + 22 + 4, FicheArt.ONGLET_REPOS, 1, false);
        ctx.pose().popMatrix();
        if (cle != null) survol(cle);
    }

    private final Component[] tabLib = {FicheArt.arcade(TAB_LIB[0]), FicheArt.arcade(TAB_LIB[1]),
        FicheArt.arcade(TAB_LIB[2]), FicheArt.arcade(TAB_LIB[3])};
    private final Component toucheK = FicheArt.arcade("K");

    private boolean dansOnglet(int i, double mx, double my) {
        int tx = lay.tabX[i];
        return mx >= tx - 22 && mx < tx + 22 && my >= 40 && my < 80;
    }

    private boolean dansFermer(double mx, double my) {
        return mx >= lay.fermerX - 12 && mx < lay.fermerX + 12 && my >= lay.fermerY - 2 && my < lay.fermerY + 30;
    }

    /* ---------------------------------------------------------------- cartouche, aides */

    private void cartouche(GuiGraphicsExtractor ctx) {
        Font f = this.font;
        StatsData.Snapshot s = snap();
        int x = lay.cartoucheX, y = lay.cartoucheY;
        FicheArt.embleme(ctx, FicheTextes.village(s.village()), x, y + 1, 12, FicheArt.CREME);
        FicheArt.arcadeX1(ctx, f, FicheArt.arcade(s.name()), x + 18, y + 2, FicheArt.CREME, true);
        Component g = FicheArt.inter(s.rank());
        int ya = lay.aidesY;
        int w1 = FicheArt.inter9(ctx, f, g, x + 18, ya, FicheArt.DOUX, 0);
        FicheArt.inter9(ctx, f, FicheArt.inter("·"), x + 18 + w1 + 4, ya, FicheArt.OR_ETEINT, 0);
        FicheArt.inter9(ctx, f, FicheArt.inter(s.village()), x + 18 + w1 + 10, ya, FicheArt.DOUX, 0);
        ctx.fill(x + 18, ya + 7, x + 18 + w1, ya + 8, FicheArt.ROUGE);
    }

    private String[][] aidesItems;
    private Component[] aidesT, aidesA;
    private float[] aidesAw;
    private int[] aidesTw;
    private float aidesTotal;

    private void aides(GuiGraphicsExtractor ctx, Onglet o) {
        Font f = this.font;
        String[][] items = lay.compact ? AIDES_COMPACT : o.aides();
        if (items != aidesItems) {
            aidesItems = items;
            int n = items.length;
            aidesT = new Component[n];
            aidesA = new Component[n];
            aidesAw = new float[n];
            aidesTw = new int[n];
            aidesTotal = 10f * (n - 1);
            for (int i = 0; i < n; i++) {
                aidesT[i] = FicheArt.arcade(items[i][0]);
                aidesTw[i] = FicheArt.largeurCabochon(f, aidesT[i]);
                aidesA[i] = items[i][1] == null ? null : FicheArt.inter(items[i][1]);
                aidesAw[i] = aidesA[i] == null ? 0 : f.width(aidesA[i]) * FicheArt.S8;
                aidesTotal += aidesTw[i] + (aidesA[i] != null ? 4 + aidesAw[i] : 0);
            }
        }
        float y = lay.aidesY;
        float x = lay.aidesDroite - aidesTotal;
        for (int i = 0; i < aidesT.length; i++) {
            FicheArt.cabochon(ctx, f, aidesT[i], Math.round(x), y);
            x += aidesTw[i];
            if (aidesA[i] != null) {
                FicheArt.inter8(ctx, f, aidesA[i], x + 4, y, FicheArt.argb(FicheArt.CREME, 0.6f));
                x += 4 + aidesAw[i];
            }
            if (i < aidesT.length - 1) {
                int X = Math.round(x + 5);
                ctx.fill(X, Math.round(y), X + 1, Math.round(y) + 1, FicheArt.OR_ETEINT);
                x += 10;
            }
        }
    }

    private static final String[][] AIDES_COMPACT = {{"K", "fermer"}};

    /* ---------------------------------------------------------------- couronne de lanternes */

    /** Paramètres d'une lanterne de couronne. */
    record LanterneCouronne(int x, int y, float l, FicheScene.Flamme flamme, float haloK, boolean sol) {}

    /**
     * Couronne de 6 lanternes S (Identité, Techniques, État) : coulées sur le plancher, halos, sprites. Renvoie les
     * rectangles (survol).
     */
    FicheScene.Rect[] couronne(GuiGraphicsExtractor ctx, LanterneCouronne[] ls, int survol) {
        FicheScene.Rect[] out = new FicheScene.Rect[6];
        StatDef[] st = StatDef.values();
        FicheScene.Taille T = FicheScene.Taille.S;
        for (int i = 0; i < 6; i++) {
            LanterneCouronne c = ls[i];
            float a = allumage(i);
            if (!c.sol() || a <= 0f) continue;
            scene.coulee(ctx, c.x(), lay.ySol, 20 + 20 * c.l(), st[i].color, (0.10f + 0.18f * c.l()) * a);
        }
        for (int i = 0; i < 6; i++) {
            LanterneCouronne c = ls[i];
            float a = allumage(i);
            if (a <= 0f) continue;
            int y = c.y() + scene.balance(i);
            float hk = c.haloK() * (survol == i ? 1.25f : 1f) * a;
            scene.halo(ctx, T, c.x() - T.w / 2, y - T.h / 2, st[i].color, c.l(), hk, c.flamme());
        }
        for (int i = 0; i < 6; i++) {
            LanterneCouronne c = ls[i];
            if (allumage(i) <= 0.2f) continue;
            int y = c.y() + scene.balance(i);
            out[i] = scene.lanterne(ctx, T, c.x(), y, st[i], c.l(), c.flamme(), survol == i, i);
        }
        for (int i = 0; i < 6; i++) if (out[i] == null) out[i] = new FicheScene.Rect(ls[i].x() - 15, ls[i].y() - 24, 30, 48);
        return out;
    }

    /* ---------------------------------------------------------------- infobulle d'une stat */

    /** Infobulle mise en page une fois (quand le survol ou l'attente change). */
    static final class Bulle {
        final StatDef stat;
        final String cle;
        final String[] palier;
        final FicheArt.Paragraphe devise;
        final List<FicheArt.Paragraphe> effets = new ArrayList<>();
        final int w = 218, h;

        Bulle(Font f, StatDef d, int v, int vise, int max, boolean complet) {
            this.stat = d;
            this.cle = d.name() + v + ":" + vise + complet;
            this.palier = FicheTextes.lignePalier(v, vise, max);
            this.devise = FicheArt.paragraphe(f, d.motto, w - 28, true, 10, 2);
            int hh = 14 + 22 + 13 + Math.max(0, devise.lignes().size() - 1) * 10;
            if (complet) {
                int hEff = 0;
                // Maquette : deux effets au plus, l'infobulle ne couvre ni lanterne ni ema.
                for (String e : d.effects.subList(0, Math.min(2, d.effects.size()))) {
                    FicheArt.Paragraphe p = FicheArt.paragraphe(f, e, w - 40, true, 10, 3);
                    effets.add(p);
                    hEff += p.lignes().size() * 10 + 4;
                }
                hh += 17 + hEff + 8;
            } else {
                hh += 12 + 8;
            }
            this.h = hh;
        }
    }

    /** Dessine une infobulle en (x, y) ; cordon d'or lissé le long de {@code cordon} (null : aucun). */
    void dessinerBulle(GuiGraphicsExtractor ctx, Bulle b, int x, int y, float[][] cordon) {
        Font f = this.font;
        if (cordon != null) FicheArt.courbe(ctx, cordon, FicheArt.OR & 0xFFFFFF, 0.9f, 0.9f);
        FicheArt.infobulle(ctx, x, y, b.h);
        if (cordon != null) {
            float[] fin = cordon[cordon.length - 1];
            FicheArt.ornementCentre(ctx, FicheArt.ORN_ANNEAU, Math.round(fin[0]), Math.round(fin[1]));
        }
        int X = x + 14;
        int yy = y + 14;
        FicheArt.hanko(ctx, b.stat.ordinal(), FicheArt.H_STATS, X + 9, yy + 8);
        FicheArt.arcadeX1(ctx, f, FicheArt.arcade(b.stat.arcadeLabel), X + 24, yy + 5, FicheArt.CREME, true);
        yy += 22;
        b.devise.dessiner(ctx, f, X, yy + 3 - FicheArt.INTER7_MIL, FicheArt.DOUX);
        yy += 13 + Math.max(0, b.devise.lignes().size() - 1) * 10;
        int w1 = FicheArt.inter9(ctx, f, FicheArt.inter(b.palier[0]), X, yy + 5, FicheArt.CREME, 0);
        if (b.palier[1] != null) {
            int w2 = FicheArt.inter9(ctx, f, FicheArt.interGras(b.palier[1]), X + w1, yy + 5, FicheArt.OR_CLAIR, 0);
            FicheArt.inter9(ctx, f, FicheArt.inter(b.palier[2]), X + w1 + w2, yy + 5, FicheArt.CREME, 0);
        }
        if (b.effets.isEmpty()) return;
        yy += 17;
        for (FicheArt.Paragraphe p : b.effets) {
            FicheArt.ornementCoin(ctx, FicheArt.ORN_LOSANGE, X + 1, yy + 3);
            p.dessiner(ctx, f, X + 9, yy, FicheArt.CREME);
            yy += p.lignes().size() * 10 + 4;
        }
    }

    /* ================================================================ entrées */

    private double lastMx, lastMy;

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (introEnCours()) passerIntro();
        if (e.button() == 0) {
            for (int i = 0; i < 4; i++) {
                if (dansOnglet(i, mx, my)) { changerOnglet(i); return true; }
            }
            if (dansFermer(mx, my)) { onClose(); return true; }
        }
        Onglet o = onglets[onglet];
        if (o.click(this, mx, my, e.button(), shift)) return true;
        if (e.button() == 0 && FichePortrait.dans(mx, my, o.persoX(this), lay.piedsY, lay.echelle)) {
            if (dbl) portrait.deFace();
            else portrait.commencerGlisser();
            lastMx = mx;
            lastMy = my;
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (portrait.glisse()) {
            portrait.glisser(dx);
            return true;
        }
        if (onglets[onglet].glisse(this, e.x(), e.y())) return true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        portrait.finGlisser();
        onglets[onglet].relache(this);
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (onglets[onglet].scroll(this, mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        boolean shift = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (k == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (fr.reborn.hud.keybind.HudKeybinds.STATS != null && fr.reborn.hud.keybind.HudKeybinds.STATS.matches(e)) {
            onClose();
            return true;
        }
        if (introEnCours()) passerIntro();
        if (k == GLFW.GLFW_KEY_TAB) {
            changerOnglet(Math.floorMod(onglet + (shift ? -1 : 1), 4));
            return true;
        }
        if (onglets[onglet].key(this, k, shift)) return true;
        if (k == GLFW.GLFW_KEY_LEFT) { portrait.tourner(-15f); return true; }
        if (k == GLFW.GLFW_KEY_RIGHT) { portrait.tourner(15f); return true; }
        return super.keyPressed(e);
    }

    /* ================================================================ banc REBORN_FICHE_DEBUG */

    public void debugOnglet(int i) {
        if (i == onglet) return;
        changerOnglet(i);
        passerIntro();
    }

    /** Remplace la position de la souris (les captures ne montrent pas le curseur). */
    public void debugSouris(int x, int y) {
        if (x < 0) { dbgX = dbgY = null; return; }
        dbgX = x;
        dbgY = y;
    }

    public void debugPasserIntro() { passerIntro(); }

    public void debugCas(String cas) {
        debugSouris(-1, -1);
        fondJeu = "identite_jeu".equals(cas);
        scene.fondJeu = fondJeu;
        switch (cas) {
            case "attributs" -> debugOnglet(ATTRIBUTS);
            case "attributs_repartition" -> {
                debugOnglet(ATTRIBUTS);
                if (rep.pending(StatDef.CONTROLE.ordinal()) == 0) rep.step(StatDef.CONTROLE.ordinal(), 1, false);
                int[] c = ((OngletAttributs) onglets[ATTRIBUTS]).centreLanterne(this, StatDef.CONTROLE.ordinal());
                debugSouris(c[0] + 2, c[1]);
            }
            case "techniques" -> {
                debugOnglet(TECHNIQUES);
                debugTechnique("suiton_mizurappa");
            }
            case "etat" -> {
                debugOnglet(ETAT);
                debugSouris(lay.panD + 11 + 170, lay.panY + 66);     // survol de la carte TORSE (maquette 05)
            }
            default -> debugOnglet(IDENTITE);
        }
        passerIntro();
    }

    public void debugTechnique(String id) {
        ((OngletTechniques) onglets[TECHNIQUES]).choisir(this, id);
    }
}
