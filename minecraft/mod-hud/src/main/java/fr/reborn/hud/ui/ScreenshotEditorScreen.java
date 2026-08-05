package fr.reborn.hud.ui;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Éditeur d'annotation : outils crayon / ligne / rectangle / gomme, color picker
 * (carré SV + barre teinte + hex), épaisseur, undo/redo (Ctrl+Z / Ctrl+Y). Les
 * traits sont composés sur une copie de l'image et enregistrés en nouveau PNG.
 */
public class ScreenshotEditorScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/editor");

    private static final int TOOL_PENCIL = 0, TOOL_LINE = 1, TOOL_RECT = 2, TOOL_ERASER = 3;
    private static final int PANEL_W = 150, SV = 104, HUE_W = 12;

    private final Screen parent;
    private final Entry entry;

    private int tool = TOOL_PENCIL;
    private float hue = 0, sat = 1, val = 1;
    private int color = 0xFFEF4444;
    private int thickness = 4;
    private EditBox hexField;

    private List<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private final List<List<Stroke>> history = new ArrayList<>();
    private int histIdx = 0;
    private String status = "";

    private ScreenshotTextures.Tex tex;
    private int imgX, imgY, drawW, drawH;
    private int panelX;

    private record Stroke(int color, int thickness, int type, List<double[]> pts) {}

    public ScreenshotEditorScreen(Screen parent, Entry entry) {
        super(Component.literal("Éditeur"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        panelX = this.width - PANEL_W;
        float[] hsv = rgbToHsv(color);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        hexField = new EditBox(this.font, panelX + 34, 32, 100, 14, Component.literal("hex"));
        hexField.setMaxLength(7);
        hexField.setValue(hex(color));
        hexField.setResponder(this::onHex);
        this.addRenderableWidget(hexField);
        if (history.isEmpty()) { history.add(new ArrayList<>()); histIdx = 0; }
    }

    private void onHex(String s) {
        String t = s.startsWith("#") ? s.substring(1) : s;
        if (t.length() == 6) {
            try {
                int rgb = Integer.parseInt(t, 16);
                color = 0xFF000000 | rgb;
                float[] hsv = rgbToHsv(color);
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
            } catch (NumberFormatException ignored) {}
        }
    }

    private void setColorFromHsv() {
        color = hsvToArgb(hue, sat, val);
        if (hexField != null) hexField.setValue(hex(color));
    }

    private void computeFit() {
        tex = ScreenshotTextures.get(entry.path());
        if (tex == null || tex.w() <= 0) return;
        int top = 26, bottom = this.height - 12;
        int availW = panelX - 24, availH = bottom - top;
        float scale = Math.min(availW / (float) tex.w(), availH / (float) tex.h());
        drawW = Math.round(tex.w() * scale);
        drawH = Math.round(tex.h() * scale);
        imgX = 12 + (availW - drawW) / 2;
        imgY = top + (availH - drawH) / 2;
    }

    private double[] toImage(double sx, double sy) {
        double ix = (sx - imgX) * tex.w() / drawW;
        double iy = (sy - imgY) * tex.h() / drawH;
        return new double[]{ clamp(ix, 0, tex.w() - 1), clamp(iy, 0, tex.h() - 1) };
    }

    private boolean inImage(double sx, double sy) {
        return sx >= imgX && sx < imgX + drawW && sy >= imgY && sy < imgY + drawH;
    }

    // ─── Render ───

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xE6000000);
        Font tr = this.font;
        computeFit();

        ctx.text(tr, RebornFont.bold("ÉDITEUR"), 12, 8, Colors.GOLD, false);
        if (!status.isEmpty()) ctx.text(tr, Component.literal(status), 90, 9, Colors.SUCCESS, false);

        if (tex != null) {
            ctx.fill(imgX - 1, imgY - 1, imgX + drawW + 1, imgY + drawH + 1, Colors.BORDER_STRONG);
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex.id(), imgX, imgY, 0f, 0f, drawW, drawH, tex.w(), tex.h());
            float sc = drawW / (float) tex.w();
            for (Stroke s : strokes) drawStrokeScreen(ctx, s, sc);
            if (current != null) drawStrokeScreen(ctx, current, sc);
        }

        renderPanel(ctx, tr, mouseX, mouseY);
        if (hexField != null) hexField.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderPanel(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
        int x = panelX;
        ctx.fill(x, 0, this.width, this.height, 0xF20C0709);
        ctx.fill(x, 0, x + 1, this.height, Colors.BORDER_STRONG);

        // Aperçu couleur + hex.
        ctx.fill(x + 12, 30, x + 30, 46, color);
        ctx.fill(x + 11, 29, x + 31, 47, Colors.BORDER);
        ctx.fill(x + 12, 30, x + 30, 46, color);
        // (hexField rendu par super via addRenderableWidget)

        // Carré Saturation/Valeur.
        int svX = x + 12, svY = 54;
        for (int py = 0; py < SV; py += 3) {
            for (int px = 0; px < SV; px += 3) {
                ctx.fill(svX + px, svY + py, svX + px + 3, svY + py + 3,
                    hsvToArgb(hue, px / (float) SV, 1 - py / (float) SV));
            }
        }
        int mkx = svX + (int) (sat * SV), mky = svY + (int) ((1 - val) * SV);
        ctx.fill(mkx - 2, mky - 2, mkx + 2, mky + 2, 0xFFFFFFFF);
        ctx.fill(mkx - 1, mky - 1, mkx + 1, mky + 1, 0xFF000000);

        // Barre teinte.
        int hueX = svX + SV + 6;
        for (int py = 0; py < SV; py += 2) {
            ctx.fill(hueX, svY + py, hueX + HUE_W, svY + py + 2, hsvToArgb(py / (float) SV * 360f, 1, 1));
        }
        int hy = svY + (int) (hue / 360f * SV);
        ctx.fill(hueX - 1, hy - 1, hueX + HUE_W + 1, hy + 1, 0xFFFFFFFF);

        // Outils.
        int ty = svY + SV + 8;
        String[] tools = { "Crayon", "Ligne", "Rect", "Gomme" };
        for (int i = 0; i < tools.length; i++) {
            int bx = x + 12 + (i % 2) * 66, by = ty + (i / 2) * 18;
            boolean sel = tool == i;
            boolean hov = in(mouseX, mouseY, bx, by, 62, 16);
            ctx.fill(bx, by, bx + 62, by + 16, sel ? Colors.ACCENT : (hov ? 0x33FFFFFF : Colors.BACKDROP_85));
            ctx.text(tr, Component.literal(tools[i]), bx + 6, by + 4, sel ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
        }

        // Épaisseur.
        int ey = ty + 40;
        ctx.text(tr, Component.literal("Épaisseur"), x + 12, ey + 3, Colors.FOREGROUND_MUTED, false);
        sbtn(ctx, tr, "-", x + PANEL_W - 52, ey);
        ctx.text(tr, Component.literal(String.valueOf(thickness)), x + PANEL_W - 34, ey + 3, Colors.GOLD, false);
        sbtn(ctx, tr, "+", x + PANEL_W - 20, ey);

        // Actions.
        int ay = ey + 22;
        act(ctx, tr, "Annuler", x + 12, ay, 62, mouseX, mouseY, Colors.BACKDROP_85);
        act(ctx, tr, "Rétablir", x + 78, ay, 60, mouseX, mouseY, Colors.BACKDROP_85);
        act(ctx, tr, "Effacer tout", x + 12, ay + 20, 126, mouseX, mouseY, Colors.BACKDROP_85);
        act(ctx, tr, "Enregistrer", x + 12, ay + 40, 126, mouseX, mouseY, 0xFF3FA85B);
        act(ctx, tr, "Retour", x + 12, ay + 60, 126, mouseX, mouseY, Colors.ACCENT);
    }

    private void sbtn(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y) {
        ctx.fill(x, y, x + 14, y + 16, Colors.BACKDROP_85);
        ctx.text(tr, Component.literal(s), x + 5, y + 4, Colors.FOREGROUND_SUBTLE, false);
    }

    private void act(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y, int w, int mouseX, int mouseY, int bg) {
        boolean hov = in(mouseX, mouseY, x, y, w, 16);
        ctx.fill(x, y, x + w, y + 16, hov ? Colors.ACCENT_HOVER : bg);
        ctx.text(tr, Component.literal(s), x + (w - tr.width(s)) / 2, y + 4, Colors.WHITE_PURE, false);
    }

    private void drawStrokeScreen(GuiGraphicsExtractor ctx, Stroke s, float sc) {
        int st = Math.max(1, Math.round(s.thickness * sc));
        List<double[]> p = s.pts;
        if (s.type == TOOL_RECT && p.size() >= 2) {
            int x0 = sx(p.get(0)[0], sc), y0 = sy(p.get(0)[1], sc), x1 = sx(p.get(1)[0], sc), y1 = sy(p.get(1)[1], sc);
            lineScreen(ctx, x0, y0, x1, y0, st, s.color);
            lineScreen(ctx, x1, y0, x1, y1, st, s.color);
            lineScreen(ctx, x1, y1, x0, y1, st, s.color);
            lineScreen(ctx, x0, y1, x0, y0, st, s.color);
            return;
        }
        for (int i = 1; i < p.size(); i++) {
            lineScreen(ctx, sx(p.get(i - 1)[0], sc), sy(p.get(i - 1)[1], sc), sx(p.get(i)[0], sc), sy(p.get(i)[1], sc), st, s.color);
        }
        if (p.size() == 1) {
            int x = sx(p.get(0)[0], sc), y = sy(p.get(0)[1], sc);
            ctx.fill(x - st / 2, y - st / 2, x + st / 2 + 1, y + st / 2 + 1, s.color);
        }
    }

    private int sx(double ix, float sc) { return imgX + (int) (ix * sc); }
    private int sy(double iy, float sc) { return imgY + (int) (iy * sc); }

    private static void lineScreen(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int th, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        int r = Math.max(1, th) / 2;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x - r, y - r, x + r + 1, y + r + 1, color);
        }
    }

    // ─── Input ───

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (super.mouseClicked(event, doubleClick)) return true;
        int x = panelX;

        // SV.
        int svX = x + 12, svY = 54;
        if (in(mx, my, svX, svY, SV, SV)) {
            sat = (float) clamp((mx - svX) / SV, 0, 1);
            val = 1 - (float) clamp((my - svY) / SV, 0, 1);
            setColorFromHsv();
            return true;
        }
        int hueX = svX + SV + 6;
        if (in(mx, my, hueX, svY, HUE_W, SV)) {
            hue = (float) clamp((my - svY) / SV, 0, 1) * 360f;
            setColorFromHsv();
            return true;
        }
        // Outils.
        int ty = svY + SV + 8;
        for (int i = 0; i < 4; i++) {
            if (in(mx, my, x + 12 + (i % 2) * 66, ty + (i / 2) * 18, 62, 16)) { tool = i; return true; }
        }
        // Épaisseur.
        int ey = ty + 40;
        if (in(mx, my, x + PANEL_W - 52, ey, 14, 16)) { thickness = Math.max(1, thickness - 1); return true; }
        if (in(mx, my, x + PANEL_W - 20, ey, 14, 16)) { thickness = Math.min(40, thickness + 1); return true; }
        // Actions.
        int ay = ey + 22;
        if (in(mx, my, x + 12, ay, 62, 16)) { undo(); return true; }
        if (in(mx, my, x + 78, ay, 60, 16)) { redo(); return true; }
        if (in(mx, my, x + 12, ay + 20, 126, 16)) { strokes = new ArrayList<>(); pushHistory(); return true; }
        if (in(mx, my, x + 12, ay + 40, 126, 16)) { save(); return true; }
        if (in(mx, my, x + 12, ay + 60, 126, 16)) { onClose(); return true; }

        // Dessin.
        if (button == 0 && tex != null && inImage(mx, my)) {
            if (tool == TOOL_ERASER) { eraseAt(mx, my); return true; }
            int type = tool == TOOL_LINE ? TOOL_LINE : (tool == TOOL_RECT ? TOOL_RECT : TOOL_PENCIL);
            current = new Stroke(color, thickness, type, new ArrayList<>());
            double[] p = toImage(mx, my);
            current.pts().add(p);
            if (type != TOOL_PENCIL) current.pts().add(new double[]{ p[0], p[1] });
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (tool == TOOL_ERASER && tex != null && inImage(mx, my)) { eraseAt(mx, my); return true; }
        if (current != null && tex != null) {
            double[] p = toImage(mx, my);
            if (current.type == TOOL_PENCIL) current.pts().add(p);
            else current.pts().set(1, p); // ligne/rect : bouge le 2e point
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (current != null) { strokes.add(current); current = null; pushHistory(); return true; }
        return super.mouseReleased(event);
    }

    private void eraseAt(double mx, double my) {
        double[] ip = toImage(mx, my);
        double rad = Math.max(6, thickness * 1.5);
        boolean changed = strokes.removeIf(s -> s.pts.stream().anyMatch(pt ->
            Math.hypot(pt[0] - ip[0], pt[1] - ip[1]) <= rad));
        if (changed) pushHistory();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
        return super.keyPressed(event);
    }

    // ─── Historique ───

    private void pushHistory() {
        while (history.size() > histIdx + 1) history.remove(history.size() - 1);
        history.add(new ArrayList<>(strokes));
        histIdx = history.size() - 1;
    }

    private void undo() {
        if (histIdx > 0) { histIdx--; strokes = new ArrayList<>(history.get(histIdx)); }
    }

    private void redo() {
        if (histIdx < history.size() - 1) { histIdx++; strokes = new ArrayList<>(history.get(histIdx)); }
    }

    // ─── Sauvegarde ───

    private void save() {
        try (InputStream in = Files.newInputStream(entry.path())) {
            NativeImage img = NativeImage.read(in);
            for (Stroke s : strokes) rasterize(img, s);
            Path out = uniquePath(entry.path());
            img.writeToFile(out);
            img.close();
            status = "Enregistré : " + out.getFileName();
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

    private static void rasterize(NativeImage img, Stroke s) {
        int abgr = toAbgr(s.color);
        int r = Math.max(1, s.thickness) / 2;
        List<double[]> p = s.pts;
        if (s.type == TOOL_RECT && p.size() >= 2) {
            int x0 = (int) p.get(0)[0], y0 = (int) p.get(0)[1], x1 = (int) p.get(1)[0], y1 = (int) p.get(1)[1];
            seg(img, x0, y0, x1, y0, r, abgr); seg(img, x1, y0, x1, y1, r, abgr);
            seg(img, x1, y1, x0, y1, r, abgr); seg(img, x0, y1, x0, y0, r, abgr);
            return;
        }
        if (p.size() == 1) { disc(img, (int) p.get(0)[0], (int) p.get(0)[1], r, abgr); return; }
        for (int i = 1; i < p.size(); i++) {
            seg(img, (int) p.get(i - 1)[0], (int) p.get(i - 1)[1], (int) p.get(i)[0], (int) p.get(i)[1], r, abgr);
        }
    }

    private static void seg(NativeImage img, int x0, int y0, int x1, int y1, int r, int abgr) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        for (int k = 0; k <= steps; k++) {
            disc(img, x0 + (x1 - x0) * k / steps, y0 + (y1 - y0) * k / steps, r, abgr);
        }
    }

    private static void disc(NativeImage img, int cx, int cy, int r, int abgr) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setPixelABGR(x, y, abgr);
            }
        }
    }

    // ─── Couleur ───

    private static int hsvToArgb(float h, float s, float v) {
        h = ((h % 360) + 360) % 360;
        float c = v * s, xx = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = xx; b = 0; } else if (h < 120) { r = xx; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = xx; } else if (h < 240) { r = 0; g = xx; b = c; }
        else if (h < 300) { r = xx; g = 0; b = c; } else { r = c; g = 0; b = xx; }
        int R = Math.round((r + m) * 255), G = Math.round((g + m) * 255), B = Math.round((b + m) * 255);
        return 0xFF000000 | (R << 16) | (G << 8) | B;
    }

    private static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d != 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60; if (h < 0) h += 360;
        }
        return new float[]{ h, max == 0 ? 0 : d / max, max };
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF, red = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | red;
    }

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
