package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Base immédiat-mode pour les onglets de paramètres Reborn.
 *
 * <p>Chaque sous-classe décrit son contenu <b>une seule fois</b> dans
 * {@link #build()} à l'aide du petit DSL ({@link #section}, {@link #row},
 * {@link #bascule}, {@link #curseur}, {@link #choix}, {@link #liste}, {@link #action}, {@link #valueRow}…).
 * {@code build()} est rejoué en deux passes — une pour créer/positionner les widgets ({@code layout}), une
 * pour dessiner les labels passifs ({@code renderPassive}) — et comme les deux
 * passes avancent le curseur Y de façon strictement identique, les contrôles
 * et leurs libellés ne peuvent jamais se désaligner (y compris au scroll).
 *
 * <p>DA (gabarit o2 du lot 3, format de Ketto avec l'allure de nos Paramètres) : lignes de {@value #ROW_H} GUI,
 * libellé Inter 9 crème à gauche (milieu à 11 GUI du haut avec une aide, 16 sans), aide Inter 7 dessous (milieu à 23),
 * réglage calé à droite et centré sur la ligne, filet sombre au pied ; la ligne sous la souris est voilée de laque
 * (une seule chose survolée à la fois). Le titre de la sous-catégorie est posé par l'écran ; {@link #section} reste
 * pour un intertitre dans une page. Contrôles laqués de {@link ReglagesArt}.
 */
public abstract class SectionedTab implements SettingsTab {

    protected static final int ROW_H = 34;
    protected static final int SECTION_GAP = 26;   // hauteur réservée à un intertitre
    protected static final int BUTTON_H = 20;
    protected static final int BUTTON_GAP = 8;
    protected static final int CONTROL_W = 200;
    /** Hauteur des widgets posés par {@link #row} (curseur, segments, touche), centrés sur la ligne. */
    protected static final int WIDGET_H = 24;

    private static final int AIDE = DaKit.argb(0xC2B59A, 0.85f);
    private static final int VOILE_SURVOL = DaKit.argb(0x2A161A, 0.55f);

    // ─── Survol et demandes à l'écran (statiques : un seul écran de Paramètres à la fois) ───
    private static int sourisX = Integer.MIN_VALUE, sourisY = Integer.MIN_VALUE, bandeHaut, bandeBas;
    private static boolean aRafraichir, aRedimensionner;

    /**
     * Posé par l'écran avant chaque {@link #renderPassive} : position de la souris et bande visible de la planche
     * (hors d'elle, aucune ligne n'est survolée). {@code mx = Integer.MIN_VALUE} : rien n'est survolé.
     */
    public static void survol(int mx, int my, int haut, int bas) {
        sourisX = mx;
        sourisY = my;
        bandeHaut = haut;
        bandeBas = bas;
    }

    /** Une valeur a changé d'autres réglages (préréglage graphique, remise aux défauts) : refaire la mise en page. */
    public static void demanderRafraichir() { aRafraichir = true; }

    /** L'échelle des menus a changé (« Taille des menus Reborn ») : l'écran rappelle {@code resizeGui} au prochain tick. */
    public static void demanderRedimension() { aRedimensionner = true; }

    /** Lu (et remis à faux) par l'écran à chaque tick. */
    public static boolean consommerRafraichir() {
        boolean r = aRafraichir;
        aRafraichir = false;
        return r;
    }

    /** Lu (et remis à faux) par l'écran à chaque tick. */
    public static boolean consommerRedimension() {
        boolean r = aRedimensionner;
        aRedimensionner = false;
        return r;
    }

    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;
    /** Bord gauche du réglage de chaque ligne (passe LAYOUT), pour couper libellé et aide avant lui (passe RENDER). */
    private int[] reglageX = new int[16];
    private int ligne;

    private enum Pass { LAYOUT, RENDER }

    // État de passe (réinitialisé à chaque layout/renderPassive).
    private Pass pass;
    private GuiGraphicsExtractor ctx;
    private Font tr;
    private int originX;
    private int width;
    private int cursorY;
    private int controlX;
    private int controlW;

    /** Décrit le contenu de l'onglet via le DSL. Rejoué en 2 passes. */
    protected abstract void build();

    // ─── SettingsTab ────────────────────────────────────
    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        this.pass = Pass.LAYOUT;
        this.tr = Minecraft.getInstance().font;
        this.originX = x;
        this.width = width;
        this.controlW = Math.max(150, Math.min(CONTROL_W, width - 130));
        this.controlX = x + width - controlW;
        this.cursorY = y;
        this.ligne = 0;
        build();
        this.contentHeight = cursorY - y;
        this.tr = null;
    }

    @Override
    public void renderPassive(GuiGraphicsExtractor ctx, int x, int y, int width) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        this.pass = Pass.RENDER;
        this.ctx = ctx;
        this.tr = mc.font;
        this.originX = x;
        this.width = width;
        this.controlW = Math.max(150, Math.min(CONTROL_W, width - 130));
        this.controlX = x + width - controlW;
        this.cursorY = y;
        this.ligne = 0;
        build();
        this.ctx = null;
        this.tr = null;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    @Override
    public int height() {
        return contentHeight;
    }

    // ─── DSL ────────────────────────────────────────────
    /** X gauche de la colonne (labels). */
    protected int contentX() { return originX; }
    /** Largeur totale de la colonne. */
    protected int contentWidth() { return width; }
    /** X du contrôle (aligné à droite de la colonne). */
    protected int controlX() { return controlX; }
    /** Largeur du contrôle. */
    protected int controlW() { return controlW; }

    /** Intertitre dans une page : ArcadePix ×1 or (accents dessinés), filet à losanges jusqu'au bord droit. */
    protected void section(String title) {
        if (pass == Pass.RENDER) {
            int w = ReglagesArt.titre(ctx, tr, title, originX, cursorY + 8, DaKit.OR, 0, true);
            ReglagesArt.filet(ctx, originX + w + 8, originX + width, cursorY + 11);
        }
        cursorY += SECTION_GAP;
    }

    private void noterReglage(int x) {
        if (ligne >= reglageX.length) reglageX = Arrays.copyOf(reglageX, reglageX.length * 2);
        reglageX[ligne] = x;
    }

    /** Bord gauche du réglage de la ligne courante (passe RENDER) ; le bord droit de la colonne s'il n'y en a pas. */
    private int reglageCourant() {
        return ligne < reglageX.length && reglageX[ligne] > originX ? reglageX[ligne] : originX + width;
    }

    /** Aide de la prochaine ligne en vermillon (ex. touche déjà prise, planche t2-b2) ; remis à faux après la ligne. */
    protected boolean aideAlerte;

    /**
     * Étiquette « NOUVEAU » à droite du libellé de la prochaine ligne (un réglage arrivé avec la version, à retirer à la
     * suivante) ; remis à faux après la ligne.
     */
    protected boolean nouveau;
    private static DaKit.Etiq etiqNouveau;

    /** Voile de survol, libellé (Inter 9 crème) et aide (Inter 7), puis le filet de séparation. */
    private void libelle(String label, String hint) {
        int y = cursorY;
        if (sourisY >= y && sourisY < y + ROW_H - 2 && sourisY >= bandeHaut && sourisY < bandeBas
            && sourisX >= originX - 8 && sourisX < originX + width + 4) {
            ctx.fill(originX - 8, y, originX + width + 4, y + ROW_H - 2, VOILE_SURVOL);
        }
        int place = Math.max(40, reglageCourant() - originX - 12);
        float ym = hint != null ? y + 11 : y + 16;
        int lw = DaKit.inter9(ctx, tr, DaKit.inter(DaKit.ellipse(tr, label, place)), originX, ym, DaKit.CREME, 0);
        if (nouveau) etiquetteNouveau(originX + lw + 6, ym);
        if (hint != null) {
            String h = DaKit.ellipse(tr, hint, Math.round(place / DaKit.S7));
            DaKit.inter7(ctx, tr, DaKit.inter(h), originX, y + 23, aideAlerte ? DaKit.ROUGE_CLAIR : AIDE, 0);
        }
        aideAlerte = false;
        ctx.fill(originX, y + ROW_H - 2, originX + width + 4, y + ROW_H - 1, DaKit.SEP);
    }

    /** « NOUVEAU » : petites capitales Inter gras 7 or, dans un cadre d'or d'1 px écran sur la laque sombre. */
    private void etiquetteNouveau(float x, float ymil) {
        if (etiqNouveau == null) etiqNouveau = DaKit.etiq(tr, "NOUVEAU");
        int k = DaKit.echelleGui();
        int x0 = Math.round(x * k), y0 = Math.round((ymil - 4.5f) * k);
        int x1 = Math.round((x + etiqNouveau.w() + 6) * k), y1 = Math.round((ymil + 4.5f) * k);
        DaKit.debutPx(ctx);
        ctx.fill(x0, y0, x1, y1, DaKit.OR_MOYEN);
        ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF1E1410);
        DaKit.finPx(ctx);
        DaKit.etiq(ctx, tr, etiqNouveau, x + 3, ymil, DaKit.OR);
    }

    /**
     * Ligne label (+ hint) à gauche, contrôle à droite. Le {@code factory} n'est
     * invoqué qu'en passe LAYOUT ; il reçoit (controlX, controlY, controlW) et
     * doit retourner un widget déjà positionné ({@code controlY} : haut d'un widget de {@value #WIDGET_H} centré).
     */
    protected void row(String label, String hint, WidgetFactory factory) {
        if (pass == Pass.RENDER) {
            libelle(label, hint);
        } else {
            AbstractWidget w = factory.create(controlX, cursorY + (ROW_H - WIDGET_H) / 2, controlW);
            widgets.add(w);
            noterReglage(w.getX());
        }
        aideAlerte = false;
        nouveau = false;
        ligne++;
        cursorY += ROW_H;
    }

    /** Ligne label (+ hint) seule, sans contrôle (le contrôle suit, ex. bouton). */
    protected void labelRow(String label, String hint) {
        if (pass == Pass.RENDER) {
            float ym = hint != null ? cursorY + 11 : cursorY + 16;
            DaKit.inter9(ctx, tr, DaKit.inter(label), originX, ym, DaKit.CREME, 0);
            if (hint != null) {
                DaKit.inter7(ctx, tr, DaKit.inter(hint), originX, cursorY + 23, AIDE, 0);
            }
        } else {
            noterReglage(0);
        }
        ligne++;
        cursorY += ROW_H;
    }

    /** Ligne lecture seule : label (+ hint) à gauche, valeur à droite. */
    protected void valueRow(String label, String hint, String value, int valueColor) {
        if (pass == Pass.RENDER) {
            int col = valueColor == Colors.WHITE_PURE ? DaKit.OR_CLAIR
                : valueColor == Colors.FOREGROUND_MUTED ? DaKit.NEUTRE_TXT : valueColor;
            String v = value != null ? DaKit.ellipse(tr, value, Math.max(40, width / 2 - 8)) : null;
            if (v != null) {
                // le libellé s'arrête avant la valeur : on note son bord, comme pour un widget
                noterReglage(originX + width - tr.width(DaKit.inter(v)));
            }
            libelle(label, hint);
            if (v != null) {
                DaKit.inter9(ctx, tr, DaKit.inter(v), originX + width, cursorY + 16, col, 2);
            }
        } else {
            noterReglage(0);
        }
        ligne++;
        cursorY += ROW_H;
    }

    /** Bouton pleine largeur (action) : bouton-plaque laqué, flèche d'or. */
    protected void actionButton(String label, Runnable action) {
        if (pass == Pass.LAYOUT) {
            widgets.add(new PlaqueBouton(originX, cursorY, width, BUTTON_H, label, action));
        }
        cursorY += BUTTON_H + BUTTON_GAP;
    }

    /** Espace vertical libre. */
    protected void spacer(int px) {
        cursorY += px;
    }

    // ─── Lignes du gabarit o2 ───────────────────────────

    /** Interrupteur laqué calé à droite. */
    protected void bascule(String label, String hint, boolean valeur, Consumer<Boolean> onChange) {
        row(label, hint, (cx, cy, cw) -> new ToggleBig(cx + cw - ToggleBig.DEFAULT_WIDTH + 1, cy + 1, valeur, onChange));
    }

    /** Réglette d'or et valeur à droite (entiers de {@code min} à {@code max}, suffixe accolé : « % », « dB »). */
    protected void curseur(String label, String hint, int valeur, int min, int max, String suffixe, IntConsumer onChange) {
        row(label, hint, (cx, cy, cw) -> new SliderWidget(cx, cy, cw, WIDGET_H, valeur, min, max, suffixe, onChange));
    }

    /** Segments (« Automatique | Petite », « Voix | Appuyer ») à la largeur de leurs libellés, calés à droite. */
    protected void choix(String label, String hint, SegmentedControl.Option[] options, String valeur,
                         Consumer<String> onChange) {
        row(label, hint, (cx, cy, cw) -> {
            int seg = 30;
            for (SegmentedControl.Option o : options) {
                seg = Math.max(seg, Math.round(tr.width(DaKit.inter(o.label())) * DaKit.S8) + 18);
            }
            int w = Math.min(Math.max(cw, width / 2), seg * options.length);
            return new SegmentedControl(cx + cw - w, cy, w, WIDGET_H, options, valeur, onChange);
        });
    }

    /** Liste à valeurs (bouton laqué + chevron) : un clic passe à la suivante. */
    protected void liste(String label, String hint, String[] libelles, int index, IntConsumer onChange) {
        row(label, hint, (cx, cy, cw) -> new ChoixCyclique(cx + cw - ChoixCyclique.LARGEUR,
            cy + (WIDGET_H - ChoixCyclique.HAUTEUR) / 2, ChoixCyclique.LARGEUR, ChoixCyclique.HAUTEUR, libelles, index,
            onChange));
    }

    /** Bouton d'action (« Déplacer › », « Ouvrir › ») calé à droite. */
    protected void action(String label, String hint, String bouton, Runnable action) {
        row(label, hint, (cx, cy, cw) -> {
            int w = BoutonAction.largeur(bouton);
            return new BoutonAction(cx + cw - w, cy + (WIDGET_H - BoutonAction.HAUTEUR) / 2, w, bouton, action);
        });
    }

    /** Valeur en lecture seule, or clair, à droite. */
    protected void valeur(String label, String hint, String valeur) {
        valueRow(label, hint, valeur, DaKit.OR_CLAIR);
    }

    @FunctionalInterface
    protected interface WidgetFactory {
        AbstractWidget create(int controlX, int controlY, int controlW);
    }
}
