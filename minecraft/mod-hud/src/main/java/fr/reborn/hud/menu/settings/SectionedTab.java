package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Base immédiat-mode pour les onglets de paramètres Reborn.
 *
 * <p>Chaque sous-classe décrit son contenu <b>une seule fois</b> dans
 * {@link #build()} à l'aide du petit DSL ({@link #section}, {@link #row},
 * {@link #valueRow}, {@link #actionButton}). {@code build()} est rejoué en
 * deux passes — une pour créer/positionner les widgets ({@code layout}), une
 * pour dessiner les labels passifs ({@code renderPassive}) — et comme les deux
 * passes avancent le curseur Y de façon strictement identique, les contrôles
 * et leurs libellés ne peuvent jamais se désaligner (y compris au scroll).
 *
 * <p>DA (niveau de la fiche, DA §4.1) : titre de section en ArcadePix ×1 or suivi d'un filet
 * à losanges, libellé Inter 9 crème à gauche / contrôle à droite, aide Inter 7 sous le libellé,
 * filet sombre entre les lignes ; contrôles laqués de {@link ReglagesArt}.
 */
public abstract class SectionedTab implements SettingsTab {

    protected static final int ROW_H = 40;
    protected static final int SECTION_GAP = 30;   // hauteur réservée à un en-tête de section
    protected static final int BUTTON_H = 20;
    protected static final int BUTTON_GAP = 10;
    protected static final int CONTROL_W = 240;

    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

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
        this.originX = x;
        this.width = width;
        this.controlW = Math.max(150, Math.min(CONTROL_W, width - 130));
        this.controlX = x + width - controlW;
        this.cursorY = y;
        build();
        this.contentHeight = cursorY - y;
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

    /** En-tête de section : ArcadePix ×1 or, filet à losanges jusqu'au bord droit. */
    protected void section(String title) {
        if (pass == Pass.RENDER) {
            int w = DaKit.arcX1(ctx, tr, DaKit.arcade(title), originX, cursorY + 10, DaKit.OR_CLAIR, 0, true);
            ReglagesArt.filet(ctx, originX + w + 8, originX + width, cursorY + 13);
        }
        cursorY += SECTION_GAP;
    }

    /** Libellé (Inter 9 crème) et aide (Inter 7) d'une ligne, puis le filet de séparation. */
    private void libelle(String label, String hint) {
        float ym = hint != null ? cursorY + 15 : cursorY + 18;
        DaKit.inter9(ctx, tr, DaKit.inter(label), originX, ym, DaKit.CREME, 0);
        if (hint != null) {
            DaKit.inter7(ctx, tr, DaKit.inter(hint), originX, cursorY + 27, DaKit.argb(0xC2B59A, 0.85f), 0);
        }
        ctx.fill(originX, cursorY + ROW_H - 2, originX + width, cursorY + ROW_H - 1, DaKit.SEP);
    }

    /**
     * Ligne label (+ hint) à gauche, contrôle à droite. Le {@code factory} n'est
     * invoqué qu'en passe LAYOUT ; il reçoit (controlX, controlY, controlW) et
     * doit retourner un widget déjà positionné.
     */
    protected void row(String label, String hint, WidgetFactory factory) {
        if (pass == Pass.RENDER) {
            libelle(label, hint);
        } else {
            widgets.add(factory.create(controlX, cursorY + 6, controlW));
        }
        cursorY += ROW_H;
    }

    /** Ligne label (+ hint) seule, sans contrôle (le contrôle suit, ex. bouton). */
    protected void labelRow(String label, String hint) {
        if (pass == Pass.RENDER) {
            float ym = hint != null ? cursorY + 15 : cursorY + 18;
            DaKit.inter9(ctx, tr, DaKit.inter(label), originX, ym, DaKit.CREME, 0);
            if (hint != null) {
                DaKit.inter7(ctx, tr, DaKit.inter(hint), originX, cursorY + 27, DaKit.argb(0xC2B59A, 0.85f), 0);
            }
        }
        cursorY += ROW_H;
    }

    /** Ligne lecture seule : label (+ hint) à gauche, valeur à droite. */
    protected void valueRow(String label, String hint, String value, int valueColor) {
        if (pass == Pass.RENDER) {
            libelle(label, hint);
            if (value != null) {
                int col = valueColor == Colors.WHITE_PURE ? DaKit.OR_CLAIR
                    : valueColor == Colors.FOREGROUND_MUTED ? DaKit.NEUTRE_TXT : valueColor;
                String v = DaKit.ellipse(tr, value, Math.max(40, width - 150));
                DaKit.inter9(ctx, tr, DaKit.inter(v), originX + width, hint != null ? cursorY + 15 : cursorY + 18, col, 2);
            }
        }
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

    @FunctionalInterface
    protected interface WidgetFactory {
        AbstractWidget create(int controlX, int controlY, int controlW);
    }
}
