package fr.reborn.hud.interaction;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.List;

/**
 * Menu d'interaction <b>live</b> (style GTA) : ce n'est PAS un écran modal —
 * c'est un overlay HUD. Tant qu'il est actif, le joueur garde le contrôle
 * (déplacement, etc.), seul le mouvement caméra est gelé (le curseur prend la
 * souris). On choisit une action au clic gauche ; clic droit ou R ferme.
 *
 * <p>Rendu via {@code HudRenderCallback}, entrées via {@code MouseInteractionMixin}.
 */
public final class InteractionMode {

    public static final InteractionMode INSTANCE = new InteractionMode();

    private static final int ROW_H = 16;
    private static final int HEADER_H = 22;
    private static final int PAD_X = 10;
    private static final int ARROW_W = 12;

    private boolean active = false;
    private String title = "";
    private List<InteractionItem> items = List.of();
    private double cursorX, cursorY;
    private int panelX, panelY, panelW, panelH;
    private int[] subW = new int[0];
    private int hovered = -1, submenuOwner = -1, subHovered = -1;

    private InteractionMode() {}

    public boolean isActive() {
        return active;
    }

    /** Toggle : ouvre le menu pour la cible visée, ou ferme s'il est déjà ouvert. */
    public void toggle() {
        if (active) {
            deactivate();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.currentScreen != null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e instanceof PlayerEntity pe) {
                title = pe.getGameProfile().getName();
                items = InteractionMenus.forPlayer(title);
            } else {
                title = e.getType().getName().getString();
                items = InteractionMenus.forEntity(e);
            }
        } else if (hit instanceof BlockHitResult bhr) {
            title = "Bloc";
            items = InteractionMenus.forBlock(bhr.getBlockPos());
        } else {
            title = "Interaction";
            items = InteractionMenus.generic();
        }
        layout(mc);
        cursorX = mc.getWindow().getScaledWidth() / 2.0;
        cursorY = mc.getWindow().getScaledHeight() / 2.0;
        hovered = submenuOwner = subHovered = -1;
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    private void layout(MinecraftClient mc) {
        var tr = mc.textRenderer;
        int maxW = tr.getWidth(title);
        for (InteractionItem it : items) maxW = Math.max(maxW, tr.getWidth(it.label()));
        panelW = Math.max(140, maxW + PAD_X * 2 + ARROW_W);
        panelH = HEADER_H + items.size() * ROW_H + 6;
        panelX = (mc.getWindow().getScaledWidth() - panelW) / 2;
        panelY = (mc.getWindow().getScaledHeight() - panelH) / 2;
        subW = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            if (!it.hasChildren()) continue;
            int w = 0;
            for (InteractionItem c : it.children()) w = Math.max(w, tr.getWidth(c.label()));
            subW[i] = Math.max(130, w + PAD_X * 2);
        }
    }

    /** Déplace le curseur d'un delta souris brut (px fenêtre), converti en coords GUI. */
    public void onMouseMove(double dxPx, double dyPx, double scaleFactor) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        double sf = scaleFactor <= 0 ? 1 : scaleFactor;
        cursorX = clamp(cursorX + dxPx / sf, 0, mc.getWindow().getScaledWidth());
        cursorY = clamp(cursorY + dyPx / sf, 0, mc.getWindow().getScaledHeight());
        updateHover();
    }

    public void onClick() {
        if (!active) return;
        if (submenuOwner >= 0) {
            int j = subRowAt(submenuOwner, cursorX, cursorY);
            if (j >= 0) {
                run(items.get(submenuOwner).children().get(j));
                return;
            }
        }
        int row = mainRowAt(cursorX, cursorY);
        if (row >= 0) {
            InteractionItem it = items.get(row);
            if (it.hasChildren()) submenuOwner = row;
            else run(it);
            return;
        }
        deactivate(); // clic en dehors
    }

    private void run(InteractionItem it) {
        if (it.action() != null) it.action().run();
        deactivate();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int rowY(int i) { return panelY + HEADER_H + i * ROW_H; }

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
        int sx = panelX + panelW + 4, sw = subW[owner], sy = rowY(owner) - 4;
        if (mx < sx || mx > sx + sw) return -1;
        var ch = items.get(owner).children();
        for (int j = 0; j < ch.size(); j++) {
            int y = sy + 4 + j * ROW_H;
            if (my >= y && my < y + ROW_H) return j;
        }
        return -1;
    }

    private void updateHover() {
        hovered = mainRowAt(cursorX, cursorY);
        if (hovered >= 0 && items.get(hovered).hasChildren()) submenuOwner = hovered;
        else if (hovered >= 0) submenuOwner = -1;
        subHovered = submenuOwner >= 0 ? subRowAt(submenuOwner, cursorX, cursorY) : -1;
    }

    public void render(DrawContext ctx) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return; // se ferme si un écran s'ouvre
        var tr = mc.textRenderer;

        DrawHelpers.roundedOutlinedRect(ctx, panelX, panelY, panelW, panelH, 6,
            Colors.BACKDROP_85, Colors.BORDER_STRONG);
        ctx.drawText(tr, RebornFont.bold(title), panelX + PAD_X, panelY + 7, Colors.GOLD, false);
        ctx.fill(panelX + 6, panelY + HEADER_H - 3, panelX + panelW - 6, panelY + HEADER_H - 2, Colors.BORDER);

        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            int y = rowY(i);
            boolean hot = (i == hovered) || (i == submenuOwner);
            if (hot) DrawHelpers.roundedRect(ctx, panelX + 3, y, panelW - 6, ROW_H, 4, Colors.ACCENT_SOFT);
            ctx.drawText(tr, RebornFont.body(it.label()), panelX + PAD_X, y + 4,
                hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
            if (it.hasChildren()) {
                ctx.drawText(tr, RebornFont.body("›"), panelX + panelW - ARROW_W, y + 4,
                    hot ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);
            }
        }

        if (submenuOwner >= 0 && items.get(submenuOwner).hasChildren()) {
            var ch = items.get(submenuOwner).children();
            int sx = panelX + panelW + 4, sw = subW[submenuOwner], sy = rowY(submenuOwner) - 4;
            int sh = ch.size() * ROW_H + 8;
            DrawHelpers.roundedOutlinedRect(ctx, sx, sy, sw, sh, 6, Colors.BACKDROP_85, Colors.BORDER_STRONG);
            for (int j = 0; j < ch.size(); j++) {
                int y = sy + 4 + j * ROW_H;
                boolean hot = j == subHovered;
                if (hot) DrawHelpers.roundedRect(ctx, sx + 3, y, sw - 6, ROW_H, 4, Colors.ACCENT_SOFT);
                ctx.drawText(tr, RebornFont.body(ch.get(j).label()), sx + PAD_X, y + 4,
                    hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
            }
        }

        // Hint + curseur.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(panelX, panelY + panelH + 4, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body("Clic gauche : choisir  ·  Clic droit / R : fermer"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();

        drawCursor(ctx, (int) cursorX, (int) cursorY);
    }

    /** Petit curseur flèche (blanc bordé noir). */
    private void drawCursor(DrawContext ctx, int x, int y) {
        for (int i = 0; i < 11; i++) {
            int w = i <= 7 ? i + 1 : (i == 8 ? 5 : (i == 9 ? 3 : 2));
            ctx.fill(x - 1, y + i, x + w + 1, y + i + 1, 0xFF000000); // contour
        }
        for (int i = 0; i < 10; i++) {
            int w = i <= 6 ? i : (i == 7 ? 5 : (i == 8 ? 3 : 1));
            ctx.fill(x, y + i, x + Math.max(1, w), y + i + 1, 0xFFFFFFFF); // blanc
        }
    }
}
