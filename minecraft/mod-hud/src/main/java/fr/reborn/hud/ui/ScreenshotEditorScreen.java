package fr.reborn.hud.ui;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Éditeur d'annotation de screenshot (crayon / formes / texte). <b>Stub</b> :
 * affiche l'image et la barre d'outils ; le dessin + l'enregistrement arrivent
 * à l'étape suivante.
 */
public class ScreenshotEditorScreen extends Screen {

    private final Screen parent;
    private final Entry entry;

    public ScreenshotEditorScreen(Screen parent, Entry entry) {
        super(Text.literal("Éditeur"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xE6000000);
        TextRenderer tr = this.textRenderer;
        ctx.drawText(tr, RebornFont.bold("ÉDITEUR — " + entry.name()), 12, 10, Colors.GOLD, false);

        int top = 28, bottom = this.height - 40;
        int availW = this.width - 24, availH = bottom - top;
        ScreenshotTextures.Tex t = ScreenshotTextures.get(entry.path());
        if (t != null && t.w() > 0) {
            float scale = Math.min(availW / (float) t.w(), availH / (float) t.h());
            int dw = Math.round(t.w() * scale), dh = Math.round(t.h() * scale);
            int ix = (this.width - dw) / 2, iy = top + (availH - dh) / 2;
            ctx.drawTexture(t.id(), ix, iy, dw, dh, 0f, 0f, t.w(), t.h(), t.w(), t.h());
        }

        // Barre d'outils (placeholder).
        String[] tools = { "Crayon", "Ligne", "Rectangle", "Texte", "Couleur", "Gomme" };
        int x = 12, y = this.height - 30;
        for (String tool : tools) {
            int w = tr.getWidth(tool) + 12;
            ctx.fill(x, y, x + w, y + 16, Colors.BACKDROP_85);
            ctx.drawText(tr, Text.literal(tool), x + 6, y + 4, Colors.FOREGROUND_MUTED, false);
            x += w + 4;
        }
        String note = "Éditeur en construction — dessin & enregistrement à venir";
        ctx.drawText(tr, Text.literal(note), this.width - tr.getWidth(note) - 12, this.height - 26, Colors.WARNING, false);
        // Retour.
        boolean bHov = in(mouseX, mouseY, this.width - 70, 8, 60, 16);
        ctx.fill(this.width - 70, 8, this.width - 10, 24, bHov ? Colors.ACCENT_HOVER : Colors.ACCENT);
        ctx.drawText(tr, Text.literal("Retour"), this.width - 56, 12, Colors.WHITE_PURE, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (in((int) mx, (int) my, this.width - 70, 8, 60, 16)) { close(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
