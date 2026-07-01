package fr.reborn.hud.ui;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Éditeur d'annotation : crayon (freehand) + palette + épaisseur. Les traits
 * sont stockés en coordonnées IMAGE (robuste), affichés en overlay, et
 * <b>composés sur l'image + enregistrés</b> en un nouveau PNG à l'export.
 */
public class ScreenshotEditorScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/editor");
    private static final int[] PALETTE = {
        0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF3B82F6, 0xFFA855F7, 0xFFFFFFFF, 0xFF000000
    };

    private final Screen parent;
    private final Entry entry;

    private int color = PALETTE[0];
    private int thickness = 4; // en pixels image
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private String status = "";

    // Transform image → écran (calculé en render).
    private ScreenshotTextures.Tex tex;
    private int imgX, imgY, drawW, drawH;

    private record Stroke(int color, int thickness, List<double[]> pts) {}

    public ScreenshotEditorScreen(Screen parent, Entry entry) {
        super(Text.literal("Éditeur"));
        this.parent = parent;
        this.entry = entry;
    }

    private void computeFit() {
        tex = ScreenshotTextures.get(entry.path());
        if (tex == null || tex.w() <= 0) return;
        int top = 26, bottom = this.height - 34;
        int availW = this.width - 24, availH = bottom - top;
        float scale = Math.min(availW / (float) tex.w(), availH / (float) tex.h());
        drawW = Math.round(tex.w() * scale);
        drawH = Math.round(tex.h() * scale);
        imgX = (this.width - drawW) / 2;
        imgY = top + (availH - drawH) / 2;
    }

    private double[] toImage(double sx, double sy) {
        double ix = (sx - imgX) * tex.w() / drawW;
        double iy = (sy - imgY) * tex.h() / drawH;
        return new double[]{ Math.max(0, Math.min(tex.w() - 1, ix)), Math.max(0, Math.min(tex.h() - 1, iy)) };
    }

    private boolean inImage(double sx, double sy) {
        return sx >= imgX && sx < imgX + drawW && sy >= imgY && sy < imgY + drawH;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xE6000000);
        TextRenderer tr = this.textRenderer;
        computeFit();

        ctx.drawText(tr, RebornFont.bold("ÉDITEUR"), 12, 8, Colors.GOLD, false);
        if (!status.isEmpty()) ctx.drawText(tr, Text.literal(status), 90, 9, Colors.SUCCESS, false);

        if (tex != null) {
            ctx.fill(imgX - 1, imgY - 1, imgX + drawW + 1, imgY + drawH + 1, Colors.BORDER_STRONG);
            ctx.drawTexture(tex.id(), imgX, imgY, drawW, drawH, 0f, 0f, tex.w(), tex.h(), tex.w(), tex.h());
            float sc = drawW / (float) tex.w();
            for (Stroke s : strokes) drawStrokeScreen(ctx, s, sc);
            if (current != null) drawStrokeScreen(ctx, current, sc);
        }

        renderToolbar(ctx, tr, mouseX, mouseY);
    }

    private void drawStrokeScreen(DrawContext ctx, Stroke s, float sc) {
        int st = Math.max(1, Math.round(s.thickness * sc));
        List<double[]> p = s.pts;
        for (int i = 1; i < p.size(); i++) {
            int x0 = imgX + (int) (p.get(i - 1)[0] * sc), y0 = imgY + (int) (p.get(i - 1)[1] * sc);
            int x1 = imgX + (int) (p.get(i)[0] * sc), y1 = imgY + (int) (p.get(i)[1] * sc);
            lineScreen(ctx, x0, y0, x1, y1, st, s.color);
        }
        if (p.size() == 1) {
            int x = imgX + (int) (p.get(0)[0] * sc), y = imgY + (int) (p.get(0)[1] * sc);
            ctx.fill(x - st / 2, y - st / 2, x + st / 2 + 1, y + st / 2 + 1, s.color);
        }
    }

    private static void lineScreen(DrawContext ctx, int x0, int y0, int x1, int y1, int th, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        int r = Math.max(1, th) / 2;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x - r, y - r, x + r + 1, y + r + 1, color);
        }
    }

    // ─── Barre d'outils ───

    private void renderToolbar(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int y = this.height - 26;
        // Palette.
        int x = 12;
        for (int c : PALETTE) {
            ctx.fill(x, y, x + 16, y + 16, c);
            if (c == color) ctx.fill(x - 1, y - 1, x + 17, y + 17, Colors.GOLD);
            ctx.fill(x, y, x + 16, y + 16, c);
            x += 20;
        }
        // Épaisseur.
        x += 6;
        ctx.drawText(tr, Text.literal("Ep."), x, y + 4, Colors.FOREGROUND_MUTED, false);
        x += 22;
        tbtn(ctx, tr, "-", x, y); x += 16;
        ctx.drawText(tr, Text.literal(String.valueOf(thickness)), x + 3, y + 4, Colors.GOLD, false);
        x += 18;
        tbtn(ctx, tr, "+", x, y); x += 24;
        // Actions à droite.
        String[] acts = { "Annuler", "Effacer", "Enregistrer", "Retour" };
        int bx = this.width - 12;
        for (int i = acts.length - 1; i >= 0; i--) {
            int w = tr.getWidth(acts[i]) + 12;
            bx -= w;
            boolean hov = in(mouseX, mouseY, bx, y, w, 16);
            int bg = i == 2 ? (hov ? 0xFF4ECE6F : 0xFF3FA85B) : (hov ? Colors.ACCENT_HOVER : Colors.BACKDROP_85);
            ctx.fill(bx, y, bx + w, y + 16, bg);
            ctx.drawText(tr, Text.literal(acts[i]), bx + 6, y + 4, Colors.WHITE_PURE, false);
            bx -= 4;
        }
    }

    private void tbtn(DrawContext ctx, TextRenderer tr, String s, int x, int y) {
        ctx.fill(x, y, x + 14, y + 16, Colors.BACKDROP_85);
        ctx.drawText(tr, Text.literal(s), x + 5, y + 4, Colors.FOREGROUND_SUBTLE, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = this.height - 26;
        // Palette.
        int x = 12;
        for (int c : PALETTE) {
            if (in(mx, my, x, y, 16, 16)) { color = c; return true; }
            x += 20;
        }
        x += 6 + 22;
        if (in(mx, my, x, y, 14, 16)) { thickness = Math.max(1, thickness - 1); return true; }
        x += 16 + 18;
        if (in(mx, my, x, y, 14, 16)) { thickness = Math.min(40, thickness + 1); return true; }
        // Actions.
        int bx = this.width - 12;
        String[] acts = { "Annuler", "Effacer", "Enregistrer", "Retour" };
        for (int i = acts.length - 1; i >= 0; i--) {
            int w = this.textRenderer.getWidth(acts[i]) + 12;
            bx -= w;
            if (in(mx, my, bx, y, w, 16)) {
                switch (i) {
                    case 0 -> { if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1); }
                    case 1 -> strokes.clear();
                    case 2 -> save();
                    case 3 -> close();
                }
                return true;
            }
            bx -= 4;
        }
        // Dessin.
        if (button == 0 && tex != null && inImage(mx, my)) {
            current = new Stroke(color, thickness, new ArrayList<>());
            current.pts().add(toImage(mx, my));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (current != null && tex != null) {
            current.pts().add(toImage(mx, my));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (current != null) { strokes.add(current); current = null; return true; }
        return super.mouseReleased(mx, my, button);
    }

    private void save() {
        try (InputStream in = Files.newInputStream(entry.path())) {
            NativeImage img = NativeImage.read(in);
            for (Stroke s : strokes) rasterize(img, s);
            Path out = uniquePath(entry.path());
            img.writeTo(out);
            img.close();
            status = "Enregistré : " + out.getFileName();
            strokes.clear();
            LOGGER.info("screenshot édité → {}", out);
        } catch (Exception e) {
            status = "Échec de l'enregistrement";
            LOGGER.warn("save édition échec : {}", e.getMessage());
        }
    }

    private static Path uniquePath(Path src) {
        String name = src.getFileName().toString();
        String base = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        Path dir = src.getParent();
        for (int i = 1; ; i++) {
            Path p = dir.resolve(base + "_edit" + (i == 1 ? "" : i) + ".png");
            if (!Files.exists(p)) return p;
        }
    }

    /** Compose un trait sur la NativeImage (lignes épaisses). */
    private static void rasterize(NativeImage img, Stroke s) {
        int abgr = toAbgr(s.color);
        int r = Math.max(1, s.thickness) / 2;
        List<double[]> p = s.pts;
        if (p.size() == 1) { disc(img, (int) p.get(0)[0], (int) p.get(0)[1], r, abgr); return; }
        for (int i = 1; i < p.size(); i++) {
            int x0 = (int) p.get(i - 1)[0], y0 = (int) p.get(i - 1)[1];
            int x1 = (int) p.get(i)[0], y1 = (int) p.get(i)[1];
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
            for (int k = 0; k <= steps; k++) {
                disc(img, x0 + (x1 - x0) * k / steps, y0 + (y1 - y0) * k / steps, r, abgr);
            }
        }
    }

    private static void disc(NativeImage img, int cx, int cy, int r, int abgr) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
                    img.setColor(x, y, abgr);
                }
            }
        }
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF, red = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | red;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
