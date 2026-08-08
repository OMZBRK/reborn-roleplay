package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.widget.RebornButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * <p>DA : titre de section en {@link RebornFont#bold} majuscule discret, label
 * à gauche / contrôle à droite, hint gris sous le label. Aligné sur le menu
 * ÉCHAP et la maquette Zenkai.
 */
public abstract class SectionedTab implements SettingsTab {

    protected static final int ROW_H = 40;
    protected static final int SECTION_GAP = 30;   // hauteur réservée à un en-tête de section
    protected static final int BUTTON_H = 22;
    protected static final int BUTTON_GAP = 12;
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

    /** En-tête de section (majuscule discret). */
    protected void section(String title) {
        if (pass == Pass.RENDER) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(originX, cursorY + 8);
            ctx.pose().scale(1.1f, 1.1f);
            ctx.text(tr, RebornFont.bold(title.toUpperCase(Locale.ROOT)),
                0, 0, Colors.FOREGROUND_SUBTLE, false);
            ctx.pose().popMatrix();
        }
        cursorY += SECTION_GAP;
    }

    /**
     * Ligne label (+ hint) à gauche, contrôle à droite. Le {@code factory} n'est
     * invoqué qu'en passe LAYOUT ; il reçoit (controlX, controlY, controlW) et
     * doit retourner un widget déjà positionné.
     */
    protected void row(String label, String hint, WidgetFactory factory) {
        if (pass == Pass.RENDER) {
            ctx.text(tr, RebornFont.bold(label), originX, cursorY + 10, Colors.WHITE_PURE, false);
            if (hint != null) {
                ctx.pose().pushMatrix();
                ctx.pose().translate(originX, cursorY + 23);
                ctx.pose().scale(0.85f, 0.85f);
                ctx.text(tr, RebornFont.body(hint), 0, 0, Colors.FOREGROUND_MUTED, false);
                ctx.pose().popMatrix();
            }
        } else {
            widgets.add(factory.create(controlX, cursorY + 6, controlW));
        }
        cursorY += ROW_H;
    }

    /** Ligne label (+ hint) seule, sans contrôle (le contrôle suit, ex. bouton). */
    protected void labelRow(String label, String hint) {
        if (pass == Pass.RENDER) {
            ctx.text(tr, RebornFont.bold(label), originX, cursorY + 10, Colors.WHITE_PURE, false);
            if (hint != null) {
                ctx.pose().pushMatrix();
                ctx.pose().translate(originX, cursorY + 23);
                ctx.pose().scale(0.85f, 0.85f);
                ctx.text(tr, RebornFont.body(hint), 0, 0, Colors.FOREGROUND_MUTED, false);
                ctx.pose().popMatrix();
            }
        }
        cursorY += ROW_H;
    }

    /** Ligne lecture seule : label (+ hint) à gauche, valeur à droite. */
    protected void valueRow(String label, String hint, String value, int valueColor) {
        if (pass == Pass.RENDER) {
            ctx.text(tr, RebornFont.bold(label), originX, cursorY + 10, Colors.WHITE_PURE, false);
            if (hint != null) {
                ctx.pose().pushMatrix();
                ctx.pose().translate(originX, cursorY + 23);
                ctx.pose().scale(0.85f, 0.85f);
                ctx.text(tr, RebornFont.body(hint), 0, 0, Colors.FOREGROUND_MUTED, false);
                ctx.pose().popMatrix();
            }
            if (value != null) {
                int vw = tr.width(RebornFont.bold(value));
                ctx.text(tr, RebornFont.bold(value),
                    originX + width - vw, cursorY + 10, valueColor, false);
            }
        }
        cursorY += ROW_H;
    }

    /** Bouton pleine largeur (action), style Reborn ghost. */
    protected void actionButton(String label, Runnable action) {
        if (pass == Pass.LAYOUT) {
            widgets.add(RebornButton.ghost(originX, cursorY, width, BUTTON_H,
                label, btn -> action.run()));
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
