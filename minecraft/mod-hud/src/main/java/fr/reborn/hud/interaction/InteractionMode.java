package fr.reborn.hud.interaction;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

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
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.screen != null) return;
        active = true;
        menuOpen = false;
        cursorX = mc.getWindow().getGuiScaledWidth() / 2.0;
        cursorY = mc.getWindow().getGuiScaledHeight() / 2.0;
    }

    public void deactivate() {
        active = false;
        menuOpen = false;
    }

    /** Déplace le curseur d'un delta souris brut (px fenêtre → coords GUI). */
    public void onMouseMove(double dxPx, double dyPx, double scaleFactor) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        double sf = scaleFactor <= 0 ? 1 : scaleFactor;
        cursorX = clamp(cursorX + dxPx / sf, 0, mc.getWindow().getGuiScaledWidth());
        cursorY = clamp(cursorY + dyPx / sf, 0, mc.getWindow().getGuiScaledHeight());
        if (menuOpen) updateHover();
    }

    /** Clic gauche : ouvre le menu sur la cible (mode curseur) ou choisit un item. */
    public void onClick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();

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

    private void openSelfMenu(Minecraft mc) {
        title = "Moi";
        items = InteractionMenus.forSelf();
        layoutAtCursor(mc);
        hovered = submenuOwner = subHovered = -1;
        menuOpen = true;
        updateHover();
    }

    private void openMenuFor(Minecraft mc, HitResult hit) {
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e instanceof Player pe) {
                title = pe.getProfile().getName();
                items = InteractionMenus.forPlayer(title);
            } else {
                title = e.getType().getDescription().getString();
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

    private void layoutAtCursor(Minecraft mc) {
        var tr = mc.font;
        int maxW = tr.width(title);
        for (InteractionItem it : items) maxW = Math.max(maxW, tr.width(it.label()));
        panelW = Math.max(96, maxW + PAD_X * 2 + ARROW_W);
        panelH = HEADER_H + items.size() * ROW_H + 4;
        // Au point du clic (curseur), clampé à l'écran.
        panelX = (int) Math.min(cursorX, mc.getWindow().getGuiScaledWidth() - panelW - 4);
        panelY = (int) Math.max(4, Math.min(cursorY - 6, mc.getWindow().getGuiScaledHeight() - panelH - 4));

        subW = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            if (!it.hasChildren()) continue;
            int w = 0;
            for (InteractionItem c : it.children()) w = Math.max(w, tr.width(c.label()));
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

    public void extractRenderState(GuiGraphicsExtractor ctx) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        var tr = mc.font;

        if (menuOpen) {
            DrawHelpers.roundedOutlinedRect(ctx, panelX, panelY, panelW, panelH, 5,
                Colors.BACKDROP_85, Colors.BORDER_STRONG);
            ctx.text(tr, RebornFont.bold(title), panelX + PAD_X, panelY + 4, Colors.GOLD, false);

            for (int i = 0; i < items.size(); i++) {
                InteractionItem it = items.get(i);
                int y = rowY(i);
                boolean hot = (i == hovered) || (i == submenuOwner);
                if (hot) DrawHelpers.roundedRect(ctx, panelX + 2, y, panelW - 4, ROW_H, 3, Colors.ACCENT_SOFT);
                ctx.text(tr, RebornFont.body(it.label()), panelX + PAD_X, y + 2,
                    hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
                if (it.hasChildren()) {
                    ctx.text(tr, RebornFont.body("›"), panelX + panelW - ARROW_W, y + 2,
                        hot ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);
                }
            }

            if (submenuOwner >= 0 && items.get(submenuOwner).hasChildren()) {
                var ch = items.get(submenuOwner).children();
                int sx = panelX + panelW + 3, sw = subW[submenuOwner], sy = rowY(submenuOwner) - 4;
                int sh = ch.size() * ROW_H + 6;
                sy = Math.max(4, Math.min(sy, mc.getWindow().getGuiScaledHeight() - sh - 4));
                DrawHelpers.roundedOutlinedRect(ctx, sx, sy, sw, sh, 5, Colors.BACKDROP_85, Colors.BORDER_STRONG);
                for (int j = 0; j < ch.size(); j++) {
                    int y = sy + 3 + j * ROW_H;
                    boolean hot = j == subHovered;
                    if (hot) DrawHelpers.roundedRect(ctx, sx + 2, y, sw - 4, ROW_H, 3, Colors.ACCENT_SOFT);
                    ctx.text(tr, RebornFont.body(ch.get(j).label()), sx + PAD_X, y + 2,
                        hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
                }
            }
        } else {
            // Mode curseur (pas encore de menu) : petit indice.
            ctx.pose().pushMatrix();
            ctx.pose().translate(cursorX + 10, cursorY + 2);
            ctx.pose().scale(0.85f, 0.85f);
            ctx.text(tr, RebornFont.body("Clic : interagir"), 0, 0, Colors.FOREGROUND_MUTED, false);
            ctx.pose().popMatrix();
        }

        drawCursor(ctx, (int) cursorX, (int) cursorY);
    }

    /** Curseur custom 16×16 (pointe en haut-gauche). Le numéro est choisi via
     *  le réglage {@code interactionCursor} → reborn:textures/gui/cursorN.png.
     *  0 ou texture absente = flèche procédurale. */
    private static final int CURSOR_SIZE = 16;

    private void drawCursor(GuiGraphicsExtractor ctx, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        int sel = 1;
        try { sel = fr.reborn.hud.RebornHudClient.config().getInteractionCursor(); }
        catch (RuntimeException ignored) {}
        if (sel >= 1) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn", "textures/gui/cursor" + sel + ".png");
            if (mc.getResourceManager().getResource(id).isPresent()) {
                ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f,
                    CURSOR_SIZE, CURSOR_SIZE, CURSOR_SIZE, CURSOR_SIZE);
                return;
            }
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
