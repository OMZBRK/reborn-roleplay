package fr.reborn.hud.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Lot de blits d'<b>une même texture</b> soumis comme un seul élément GUI (voir {@link LotGui} : le coût de placement
 * des éléments GUI est quadratique). Sommets identiques à ceux de {@code ctx.blit(GUI_TEXTURED, id, x, y, u, v, w, h,
 * tw, th[, couleur])} : rendu identique. Pour les frises répétées (noren, rails, cordons…).
 */
public final class LotBlit {

    private final GuiGraphicsExtractor ctx;
    private final Matrix3x2f pose;
    private final ScreenRectangle ciseau;
    private final Identifier id;
    private final float tw, th;
    private int[] r = new int[5 * 16];
    private float[] uv = new float[4 * 16];
    private int n;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

    private LotBlit(GuiGraphicsExtractor ctx, Identifier id, int tw, int th) {
        this.ctx = ctx;
        this.pose = new Matrix3x2f(ctx.pose());
        this.ciseau = ctx.scissorStack.peek();
        this.id = id;
        this.tw = tw;
        this.th = th;
    }

    public static LotBlit ouvrir(GuiGraphicsExtractor ctx, Identifier id, int tw, int th) {
        return new LotBlit(ctx, id, tw, th);
    }

    public LotBlit blit(int x, int y, float u, float v, int w, int h) { return blit(x, y, u, v, w, h, -1); }

    /** Équivalent de {@code ctx.blit(GUI_TEXTURED, id, x, y, u, v, w, h, tw, th, argb)}. */
    public LotBlit blit(int x, int y, float u, float v, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return this;
        if (5 * n + 5 > r.length) {
            r = Arrays.copyOf(r, r.length * 2);
            uv = Arrays.copyOf(uv, uv.length * 2);
        }
        int i = 5 * n, j = 4 * n;
        r[i] = x;
        r[i + 1] = y;
        r[i + 2] = x + w;
        r[i + 3] = y + h;
        r[i + 4] = argb;
        uv[j] = u / tw;
        uv[j + 1] = (u + w) / tw;
        uv[j + 2] = v / th;
        uv[j + 3] = (v + h) / th;
        n++;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x + w);
        maxY = Math.max(maxY, y + h);
        return this;
    }

    /**
     * Région (u, v, rw, rh) étirée en w×h : équivalent de {@code ctx.blit(GUI_TEXTURED, id, x, y, u, v, w, h, rw, rh,
     * tw, th, argb)} (rw ou rh négatif = miroir).
     */
    public LotBlit etire(int x, int y, int w, int h, float u, float v, int rw, int rh, int argb) {
        if (w <= 0 || h <= 0) return this;
        blit(x, y, u, v, w, h, argb);
        int j = 4 * (n - 1);
        uv[j + 1] = (u + rw) / tw;
        uv[j + 3] = (v + rh) / th;
        return this;
    }

    public void fermer() {
        if (n == 0) return;
        ScreenRectangle b = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
        if (ciseau != null) b = ciseau.intersection(b);
        if (b == null) return;
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
        TextureSetup ts = TextureSetup.singleTexture(t.getTextureView(), t.getSampler());
        ctx.guiRenderState.addGuiElement(new Element(pose, ts, Arrays.copyOf(r, 5 * n), Arrays.copyOf(uv, 4 * n), n, ciseau, b));
        n = 0;
    }

    private record Element(Matrix3x2f pose, TextureSetup textureSetup, int[] r, float[] uv, int n,
                           ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int k = 0; k < n; k++) {
                int i = 5 * k, j = 4 * k;
                float x0 = r[i], y0 = r[i + 1], x1 = r[i + 2], y1 = r[i + 3];
                int c = r[i + 4];
                float u0 = uv[j], u1 = uv[j + 1], v0 = uv[j + 2], v1 = uv[j + 3];
                vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(c);
                vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(c);
                vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(c);
                vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(c);
            }
        }

        @Override
        public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }
    }
}
