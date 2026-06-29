package fr.reborn.hud.interaction;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Menu d'interaction contextuel (style GTA) : panneau vertical d'actions avec
 * sous-menus, ouvert sur la cible visée. Le curseur OS est visible (c'est un
 * Screen) et le monde reste actif derrière ({@link #shouldPause()} = false).
 * Les actions dispatchent des commandes via {@link InteractionMenus}.
 */
public class InteractionMenuScreen extends Screen {

    private static final int ROW_H = 16;
    private static final int HEADER_H = 22;
    private static final int PAD_X = 10;
    private static final int ARROW_W = 12;

    private final List<InteractionItem> items;

    private int panelX, panelY, panelW, panelH;
    private final int[] subW;

    private int hovered = -1;        // ligne du panneau principal
    private int submenuOwner = -1;   // ligne dont le sous-menu est ouvert
    private int subHovered = -1;     // ligne du sous-menu

    public InteractionMenuScreen(String title, List<InteractionItem> items) {
        super(Text.literal(title));
        this.items = items;
        this.subW = new int[items.size()];
    }

    @Override
    protected void init() {
        int maxW = this.textRenderer.getWidth(this.title);
        for (InteractionItem it : items) {
            maxW = Math.max(maxW, this.textRenderer.getWidth(it.label()));
        }
        panelW = Math.max(140, maxW + PAD_X * 2 + ARROW_W);
        panelH = HEADER_H + items.size() * ROW_H + 6;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            if (!it.hasChildren()) continue;
            int w = 0;
            for (InteractionItem c : it.children()) {
                w = Math.max(w, this.textRenderer.getWidth(c.label()));
            }
            subW[i] = Math.max(130, w + PAD_X * 2);
        }
    }

    private int rowY(int i) {
        return panelY + HEADER_H + i * ROW_H;
    }

    private int mainRowAt(double mx, double my) {
        if (mx < panelX || mx > panelX + panelW) return -1;
        for (int i = 0; i < items.size(); i++) {
            int y = rowY(i);
            if (my >= y && my < y + ROW_H) return i;
        }
        return -1;
    }

    private int subRowAt(int owner, double mx, double my) {
        if (owner < 0 || !items.get(owner).hasChildren()) return -1;
        int sx = panelX + panelW + 4;
        int sw = subW[owner];
        int sy = rowY(owner) - 4;
        if (mx < sx || mx > sx + sw) return -1;
        List<InteractionItem> children = items.get(owner).children();
        for (int j = 0; j < children.size(); j++) {
            int y = sy + 4 + j * ROW_H;
            if (my >= y && my < y + ROW_H) return j;
        }
        return -1;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        hovered = mainRowAt(mx, my);
        if (hovered >= 0 && items.get(hovered).hasChildren()) {
            submenuOwner = hovered;
        } else if (hovered >= 0) {
            submenuOwner = -1; // ligne feuille → ferme le sous-menu
        }
        subHovered = submenuOwner >= 0 ? subRowAt(submenuOwner, mx, my) : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Clic dans un sous-menu ouvert.
        if (submenuOwner >= 0) {
            int j = subRowAt(submenuOwner, mx, my);
            if (j >= 0) {
                run(items.get(submenuOwner).children().get(j));
                return true;
            }
        }
        int row = mainRowAt(mx, my);
        if (row >= 0) {
            InteractionItem it = items.get(row);
            if (it.hasChildren()) {
                submenuOwner = row; // ouvre le sous-menu
            } else {
                run(it);
            }
            return true;
        }
        // Clic en dehors → ferme.
        close();
        return true;
    }

    private void run(InteractionItem it) {
        if (it.action() != null) it.action().run();
        close();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Léger assombrissement pour garder le monde visible derrière.
        ctx.fill(0, 0, this.width, this.height, Colors.BACKDROP_60);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var tr = this.textRenderer;

        // Panneau principal.
        DrawHelpers.roundedOutlinedRect(ctx, panelX, panelY, panelW, panelH, 6,
            Colors.BACKDROP_85, Colors.BORDER_STRONG);
        // Titre.
        ctx.drawText(tr, RebornFont.bold(this.title.getString()),
            panelX + PAD_X, panelY + 7, Colors.GOLD, false);
        ctx.fill(panelX + 6, panelY + HEADER_H - 3, panelX + panelW - 6, panelY + HEADER_H - 2,
            Colors.BORDER);

        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            int y = rowY(i);
            boolean hot = (i == hovered) || (i == submenuOwner);
            if (hot) {
                DrawHelpers.roundedRect(ctx, panelX + 3, y, panelW - 6, ROW_H, 4, Colors.ACCENT_SOFT);
            }
            int col = hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE;
            ctx.drawText(tr, RebornFont.body(it.label()), panelX + PAD_X, y + 4, col, false);
            if (it.hasChildren()) {
                ctx.drawText(tr, RebornFont.body("›"), panelX + panelW - ARROW_W, y + 4,
                    hot ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);
            }
        }

        // Sous-menu.
        if (submenuOwner >= 0 && items.get(submenuOwner).hasChildren()) {
            List<InteractionItem> children = items.get(submenuOwner).children();
            int sx = panelX + panelW + 4;
            int sw = subW[submenuOwner];
            int sy = rowY(submenuOwner) - 4;
            int sh = children.size() * ROW_H + 8;
            DrawHelpers.roundedOutlinedRect(ctx, sx, sy, sw, sh, 6,
                Colors.BACKDROP_85, Colors.BORDER_STRONG);
            for (int j = 0; j < children.size(); j++) {
                int y = sy + 4 + j * ROW_H;
                boolean hot = j == subHovered;
                if (hot) {
                    DrawHelpers.roundedRect(ctx, sx + 3, y, sw - 6, ROW_H, 4, Colors.ACCENT_SOFT);
                }
                ctx.drawText(tr, RebornFont.body(children.get(j).label()),
                    sx + PAD_X, y + 4, hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
