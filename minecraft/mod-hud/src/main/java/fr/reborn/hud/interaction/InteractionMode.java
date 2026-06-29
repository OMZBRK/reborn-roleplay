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
 * Mode d'interaction <b>live</b> (style GTA). Deux temps :
 * <ol>
 *   <li>Touche bind (R) → un <b>curseur</b> apparaît, la caméra est figée, le
 *       déplacement reste libre. Pas encore de menu.</li>
 *   <li><b>Clic gauche</b> sur un bloc / une entité / un joueur (raycast depuis
 *       le curseur, cf {@link CursorRaycast}) → ouvre le menu contextuel
 *       correspondant, à l'emplacement du clic.</li>
 * </ol>
 * Dans le menu : survol + clic gauche pour choisir. Clic en dehors → referme le
 * menu (retour au curseur). Échap ou re-press R → sort complètement.
 */
public final class InteractionMode {

    public static final InteractionMode INSTANCE = new InteractionMode();

    private static final int ROW_H = 12;
    private static final int HEADER_H = 14;
    private static final int PAD_X = 7;
    private static final int ARROW_W = 9;

    private boolean active = false;
    private boolean menuOpen = false;

    private String title = "";
    private List<InteractionItem> items = List.of();

    private double cursorX, cursorY;
    private int hovered = -1;
    private int submenuOwner = -1;
    private int subHovered = -1;

    private int panelX, panelY, panelW, panelH;
    private int[] subW = new int[0];

    private InteractionMode() {}

    public boolean isActive() {
        return active;
    }

    /** Toggle : entre/sort du mode curseur. */
    public void toggle() {
        if (active) {
            deactivate();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.currentScreen != null) return;
        active = true;
        menuOpen = false;
        cursorX = mc.getWindow().getScaledWidth() / 2.0;
        cursorY = mc.getWindow().getScaledHeight() / 2.0;
    }

    public void deactivate() {
        active = false;
        menuOpen = false;
    }

    /** Déplace le curseur d'un delta souris brut (px fenêtre → coords GUI). */
    public void onMouseMove(double dxPx, double dyPx, double scaleFactor) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        double sf = scaleFactor <= 0 ? 1 : scaleFactor;
        cursorX = clamp(cursorX + dxPx / sf, 0, mc.getWindow().getScaledWidth());
        cursorY = clamp(cursorY + dyPx / sf, 0, mc.getWindow().getScaledHeight());
        if (menuOpen) updateHover();
    }

    /** Clic gauche : ouvre le menu sur la cible (mode curseur) ou choisit un item. */
    public void onClick() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!menuOpen) {
            // Mode curseur → raycast la cible ; rien sous le curseur = menu « sur soi ».
            HitResult hit = CursorRaycast.raycast(mc, cursorX, cursorY);
            if (hit != null) openMenuFor(mc, hit);
            else openSelfMenu(mc);
            return;
        }

        // Menu ouvert : choix d'un item.
        if (submenuOwner >= 0) {
            int j = subRowAt(submenuOwner, cursorX, cursorY);
            if (j >= 0) {
                run(items.get(submenuOwner).children().get(j));
                return;
            }
        }
        int row = mainRowAt(cursorX, cursorY);
        if (row >= 0) {
            if (!items.get(row).hasChildren()) run(items.get(row));
            // parent : le sous-menu s'ouvre au survol, le clic ne fait rien
            return;
        }
        // Clic en dehors du menu → referme le menu, retour au curseur.
        menuOpen = false;
    }

    private void openSelfMenu(MinecraftClient mc) {
        title = "Moi";
        items = InteractionMenus.forSelf();
        layoutAtCursor(mc);
        hovered = submenuOwner = subHovered = -1;
        menuOpen = true;
        updateHover();
    }

    private void openMenuFor(MinecraftClient mc, HitResult hit) {
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
            return;
        }
        layoutAtCursor(mc);
        hovered = submenuOwner = subHovered = -1;
        menuOpen = true;
        updateHover();
    }

    private void layoutAtCursor(MinecraftClient mc) {
        var tr = mc.textRenderer;
        int maxW = tr.getWidth(title);
        for (InteractionItem it : items) maxW = Math.max(maxW, tr.getWidth(it.label()));
        panelW = Math.max(96, maxW + PAD_X * 2 + ARROW_W);
        panelH = HEADER_H + items.size() * ROW_H + 4;
        // Au point du clic (curseur), clampé à l'écran.
        panelX = (int) Math.min(cursorX, mc.getWindow().getScaledWidth() - panelW - 4);
        panelY = (int) Math.max(4, Math.min(cursorY - 6, mc.getWindow().getScaledHeight() - panelH - 4));

        subW = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            if (!it.hasChildren()) continue;
            int w = 0;
            for (InteractionItem c : it.children()) w = Math.max(w, tr.getWidth(c.label()));
            subW[i] = Math.max(90, w + PAD_X * 2);
        }
    }

    private void updateHover() {
        hovered = mainRowAt(cursorX, cursorY);
        if (hovered >= 0 && items.get(hovered).hasChildren()) submenuOwner = hovered;
        else if (hovered >= 0) submenuOwner = -1;
        subHovered = submenuOwner >= 0 ? subRowAt(submenuOwner, cursorX, cursorY) : -1;
    }

    private void run(InteractionItem it) {
        if (it.action() != null) it.action().run();
        deactivate();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
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
        int sx = panelX + panelW + 3, sw = subW[owner], sy = rowY(owner) - 4;
        if (mx < sx || mx > sx + sw) return -1;
        var ch = items.get(owner).children();
        for (int j = 0; j < ch.size(); j++) {
            int y = sy + 3 + j * ROW_H;
            if (my >= y && my < y + ROW_H) return j;
        }
        return -1;
    }

    public void render(DrawContext ctx) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return;
        var tr = mc.textRenderer;

        if (menuOpen) {
            DrawHelpers.roundedOutlinedRect(ctx, panelX, panelY, panelW, panelH, 5,
                Colors.BACKDROP_85, Colors.BORDER_STRONG);
            ctx.drawText(tr, RebornFont.bold(title), panelX + PAD_X, panelY + 4, Colors.GOLD, false);

            for (int i = 0; i < items.size(); i++) {
                InteractionItem it = items.get(i);
                int y = rowY(i);
                boolean hot = (i == hovered) || (i == submenuOwner);
                if (hot) DrawHelpers.roundedRect(ctx, panelX + 2, y, panelW - 4, ROW_H, 3, Colors.ACCENT_SOFT);
                ctx.drawText(tr, RebornFont.body(it.label()), panelX + PAD_X, y + 2,
                    hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
                if (it.hasChildren()) {
                    ctx.drawText(tr, RebornFont.body("›"), panelX + panelW - ARROW_W, y + 2,
                        hot ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);
                }
            }

            if (submenuOwner >= 0 && items.get(submenuOwner).hasChildren()) {
                var ch = items.get(submenuOwner).children();
                int sx = panelX + panelW + 3, sw = subW[submenuOwner], sy = rowY(submenuOwner) - 4;
                int sh = ch.size() * ROW_H + 6;
                sy = Math.max(4, Math.min(sy, mc.getWindow().getScaledHeight() - sh - 4));
                DrawHelpers.roundedOutlinedRect(ctx, sx, sy, sw, sh, 5, Colors.BACKDROP_85, Colors.BORDER_STRONG);
                for (int j = 0; j < ch.size(); j++) {
                    int y = sy + 3 + j * ROW_H;
                    boolean hot = j == subHovered;
                    if (hot) DrawHelpers.roundedRect(ctx, sx + 2, y, sw - 4, ROW_H, 3, Colors.ACCENT_SOFT);
                    ctx.drawText(tr, RebornFont.body(ch.get(j).label()), sx + PAD_X, y + 2,
                        hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
                }
            }
        } else {
            // Mode curseur (pas encore de menu) : petit indice.
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cursorX + 10, cursorY + 2, 0);
            ctx.getMatrices().scale(0.85f, 0.85f, 1f);
            ctx.drawText(tr, RebornFont.body("Clic : interagir"), 0, 0, Colors.FOREGROUND_MUTED, false);
            ctx.getMatrices().pop();
        }

        drawCursor(ctx, (int) cursorX, (int) cursorY);
    }

    /** Curseur custom 16×16 (pointe en haut-gauche) si présent dans les assets,
     *  sinon flèche procédurale. */
    private static final net.minecraft.util.Identifier CURSOR_TEX =
        net.minecraft.util.Identifier.of("reborn", "textures/gui/cursor.png");
    private static final int CURSOR_SIZE = 16;

    private void drawCursor(DrawContext ctx, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getResourceManager().getResource(CURSOR_TEX).isPresent()) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            ctx.drawTexture(CURSOR_TEX, x, y, 0f, 0f,
                CURSOR_SIZE, CURSOR_SIZE, CURSOR_SIZE, CURSOR_SIZE);
            return;
        }
        for (int i = 0; i < 10; i++) {
            int w = i <= 6 ? i + 1 : (i == 7 ? 6 : (i == 8 ? 4 : 3));
            ctx.fill(x - 1, y + i, x + w + 1, y + i + 1, 0xFF000000);
        }
        for (int i = 0; i < 9; i++) {
            int w = i <= 6 ? i : (i == 7 ? 5 : 2);
            ctx.fill(x, y + i, x + Math.max(1, w), y + i + 1, 0xFFFFFFFF);
        }
    }
}
