package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.RebornBranding.ServerTarget;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Petit toggle segmenté {@code [BUILD | DEV]} affiché juste sous l'entrée
 * JOUER du menu principal — <b>visible uniquement pour les staffs</b> (le
 * launcher pousse {@code -Dreborn.staff=true}) quand un serveur de dev est
 * configuré. Un clic sur un segment sélectionne la cible de connexion
 * ({@link RebornBranding#setTarget}); JOUER connecte ensuite au serveur choisi.
 *
 * <p>Pensé pour de l'usage dev (période build/dev), il reste invisible pour
 * les joueurs normaux.
 */
public class ServerToggleChip extends ClickableWidget {

    public ServerToggleChip(int x, int y, int width, int height) {
        super(x, y, width, height, Text.literal("Serveur"));
    }

    private int midX() {
        return getX() + getWidth() / 2;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int mid = midX();
        ServerTarget t = RebornBranding.target();
        boolean buildActive = t == ServerTarget.BUILD;

        // Fond général + segment actif surligné (accent pour BUILD, teinte
        // rôle-staff pour DEV afin de bien le distinguer).
        ctx.fill(x, y, x + w, y + h, Colors.SURFACE);
        int activeBg = buildActive ? Colors.ACCENT_SOFT : Colors.withAlpha(Colors.WARNING, 0.30f);
        if (buildActive) {
            ctx.fill(x, y, mid, y + h, activeBg);
        } else {
            ctx.fill(mid, y, x + w, y + h, activeBg);
        }
        // Séparateur central + bordure.
        ctx.fill(mid, y, mid + 1, y + h, Colors.BORDER_STRONG);
        drawOutline(ctx, x, y, w, h, Colors.BORDER_STRONG);

        drawSegment(ctx, tr, "BUILD", x, mid, y, h, buildActive, mouseX, mouseY);
        drawSegment(ctx, tr, "DEV", mid, x + w, y, h, !buildActive, mouseX, mouseY);
    }

    private void drawSegment(DrawContext ctx, TextRenderer tr, String label,
                             int x0, int x1, int y, int h, boolean active, int mx, int my) {
        boolean hover = mx >= x0 && mx < x1 && my >= y && my < y + h;
        Text txt = RebornFont.arcade(label);
        int tw = tr.getWidth(txt);
        int tx = x0 + (x1 - x0 - tw) / 2;
        int ty = y + (h - 8) / 2;
        int color = active ? Colors.WHITE_PURE
            : (hover ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED);
        ctx.drawText(tr, txt, tx, ty, color, false);
    }

    private static void drawOutline(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c);
        ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c);
        ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && button == 0 && clicked(mouseX, mouseY)) {
            RebornBranding.setTarget(mouseX < midX() ? ServerTarget.BUILD : ServerTarget.DEV);
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, Text.literal("Choix du serveur : BUILD ou DEV"));
    }
}
