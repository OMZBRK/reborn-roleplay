package fr.reborn.hud.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Lot d'aplats GUI soumis comme <b>un seul</b> élément de rendu (DA §6 — performance).
 *
 * <p>Depuis 26.x, chaque {@code ctx.fill} devient un élément du {@code GuiRenderState}, et chaque nouvel élément est
 * comparé (intersection de rectangles) à tous ceux de la strate courante pour trouver sa couche : le coût d'un écran
 * croît comme le <b>carré</b> du nombre d'éléments. Une courbe ou un disque tracés « au pixel réel » (des centaines de
 * {@code fill} d'1 px) coûtaient ainsi plusieurs millisecondes par image. Un lot rassemble ces aplats en un élément
 * dont les sommets sont exactement ceux des {@code fill} d'origine (même pipeline, mêmes couleurs, même ordre) :
 * rendu identique, coût linéaire.
 *
 * <p>Usage : {@code LotGui l = LotGui.ouvrir(ctx); l.fill(...); ... l.fermer();} — la pose et le ciseau sont ceux du
 * moment de l'ouverture (ne pas changer la pose entre l'ouverture et la fermeture).
 */
public final class LotGui {

    private final GuiGraphicsExtractor ctx;
    private final Matrix3x2f pose;
    private final ScreenRectangle ciseau;
    private int[] q = new int[6 * 32];
    private int n;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

    private LotGui(GuiGraphicsExtractor ctx) {
        this.ctx = ctx;
        this.pose = new Matrix3x2f(ctx.pose());
        this.ciseau = ctx.scissorStack.peek();
    }

    public static LotGui ouvrir(GuiGraphicsExtractor ctx) { return new LotGui(ctx); }

    /** Équivalent exact de {@code ctx.fill(x0, y0, x1, y1, col)}. */
    public LotGui fill(int x0, int y0, int x1, int y1, int col) { return degrade(x0, y0, x1, y1, col, col); }

    /** Équivalent exact de {@code ctx.fillGradient(x0, y0, x1, y1, haut, bas)}. */
    public LotGui degrade(int x0, int y0, int x1, int y1, int haut, int bas) {
        if (x0 < x1) { /* déjà ordonné */ } else { int t = x0; x0 = x1; x1 = t; }
        if (y0 < y1) { /* déjà ordonné */ } else { int t = y0; y0 = y1; y1 = t; }
        if (x0 == x1 || y0 == y1) return this;
        if (n * 6 + 6 > q.length) q = Arrays.copyOf(q, q.length * 2);
        int i = n * 6;
        q[i] = x0;
        q[i + 1] = y0;
        q[i + 2] = x1;
        q[i + 3] = y1;
        q[i + 4] = haut;
        q[i + 5] = bas;
        n++;
        minX = Math.min(minX, x0);
        minY = Math.min(minY, y0);
        maxX = Math.max(maxX, x1);
        maxY = Math.max(maxY, y1);
        return this;
    }

    public boolean vide() { return n == 0; }

    /** Lot de textes courts (les lettres espacées d'une étiquette…), voir {@link Textes}. */
    public static Textes textes(GuiGraphicsExtractor ctx) { return new Textes(ctx); }

    /**
     * Lot de textes soumis comme <b>un seul</b> élément de placement : chaque {@code ctx.text} est d'ordinaire un
     * élément que le jeu compare à tous ceux de la strate ; une étiquette aux lettres espacées (une lettre = un texte,
     * chacune calée sur la grille écran) en coûtait une vingtaine. Ici, chaque texte est préparé exactement comme le
     * fait le jeu (mêmes glyphes, même pose, même ciseau) ; le premier glyphe porte les bornes de l'ensemble pour le
     * placement, les autres suivent dans la même couche — comme les glyphes d'un texte ordinaire. Rendu identique.
     *
     * <p>Usage : {@code Textes t = LotGui.textes(ctx);} puis, sous la pose voulue, {@code t.texte(...)} à la place de
     * {@code ctx.text(...)} ; enfin {@code t.fermer()}. Ne rien dessiner d'autre qui chevauche entre les deux.
     */
    public static final class Textes {

        private final GuiGraphicsExtractor ctx;
        private final java.util.List<GlyphRenderState> glyphes = new java.util.ArrayList<>(32);
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        private Textes(GuiGraphicsExtractor ctx) { this.ctx = ctx; }

        public Textes texte(Font f, Component c, int x, int y, int col, boolean ombre) {
            return texte(f, c.getVisualOrderText(), x, y, col, ombre);
        }

        /** Équivalent exact de {@code ctx.text(f, s, x, y, col, ombre)} sous la pose et le ciseau courants. */
        public Textes texte(Font f, FormattedCharSequence s, int x, int y, int col, boolean ombre) {
            if (ARGB.alpha(col) == 0) return this;
            Matrix3x2f pose = new Matrix3x2f(ctx.pose());
            ScreenRectangle ciseau = ctx.scissorStack.peek();
            Font.PreparedText p = f.prepareText(s, x, y, col, ombre, false, 0);
            ScreenRectangle b = p.bounds();
            if (b == null) return this;
            b = b.transformMaxBounds(pose);
            if (ciseau != null) b = ciseau.intersection(b);
            if (b == null) return this;
            minX = Math.min(minX, b.left());
            minY = Math.min(minY, b.top());
            maxX = Math.max(maxX, b.right());
            maxY = Math.max(maxY, b.bottom());
            p.visit(new Font.GlyphVisitor() {
                @Override
                public void acceptRenderable(TextRenderable r) { glyphes.add(new GlyphRenderState(pose, r, ciseau)); }
            });
            return this;
        }

        public void fermer() {
            if (glyphes.isEmpty()) return;
            ScreenRectangle b = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY);
            ctx.guiRenderState.addGuiElement(new Porteur(glyphes.get(0), b));
            for (int i = 1; i < glyphes.size(); i++) ctx.guiRenderState.addGlyphToCurrentLayer(glyphes.get(i));
            glyphes.clear();
        }
    }

    /** Un glyphe qui porte les bornes de tout un lot de textes (placement), dessiné tel quel. */
    private record Porteur(GlyphRenderState g, ScreenRectangle bounds) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer vc) { g.buildVertices(vc); }

        @Override
        public RenderPipeline pipeline() { return g.pipeline(); }

        @Override
        public TextureSetup textureSetup() { return g.textureSetup(); }

        @Override
        public ScreenRectangle scissorArea() { return g.scissorArea(); }
    }

    /** Soumet le lot (un élément) au rendu GUI. */
    public void fermer() {
        if (n == 0) return;
        ScreenRectangle b = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
        if (ciseau != null) b = ciseau.intersection(b);
        if (b == null) return;
        ctx.guiRenderState.addGuiElement(new Element(pose, Arrays.copyOf(q, n * 6), n, ciseau, b));
        n = 0;
    }

    private record Element(Matrix3x2f pose, int[] q, int n, ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int k = 0; k < n; k++) {
                int i = k * 6;
                float x0 = q[i], y0 = q[i + 1], x1 = q[i + 2], y1 = q[i + 3];
                int c1 = q[i + 4], c2 = q[i + 5];
                vc.addVertexWith2DPose(pose, x0, y0).setColor(c1);
                vc.addVertexWith2DPose(pose, x0, y1).setColor(c2);
                vc.addVertexWith2DPose(pose, x1, y1).setColor(c2);
                vc.addVertexWith2DPose(pose, x1, y0).setColor(c1);
            }
        }

        @Override
        public RenderPipeline pipeline() { return RenderPipelines.GUI; }

        @Override
        public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    }
}
