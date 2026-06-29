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
 * Menu d'interaction <b>live</b> (style GTA) : petit overlay HUD près de la
 * cible visée. Il N'EMPÊCHE PAS de jouer — déplacement ET vue restent libres.
 * On navigue à la <b>molette</b>, on choisit au <b>clic gauche</b> (sur l'item
 * sélectionné), et on ferme seulement par <b>Échap</b> ou en re-pressant la
 * touche bind (R). Pas d'écran, pas de curseur, pas de gel caméra.
 */
public final class InteractionMode {

    public static final InteractionMode INSTANCE = new InteractionMode();

    private static final int ROW_H = 12;
    private static final int HEADER_H = 14;
    private static final int PAD_X = 7;
    private static final int ARROW_W = 9;

    private boolean active = false;
    private String title = "";
    private List<InteractionItem> items = List.of();

    private int level = 0;       // 0 = menu principal, 1 = sous-menu
    private int selMain = 0;
    private int selSub = 0;

    private int panelX, panelY, panelW, panelH;
    private int[] subW = new int[0];

    private InteractionMode() {}

    public boolean isActive() {
        return active;
    }

    /** Ouvre le menu pour la cible visée, ou ferme s'il est déjà ouvert. */
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
        level = 0;
        selMain = 0;
        selSub = 0;
        layout(mc);
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    private void layout(MinecraftClient mc) {
        var tr = mc.textRenderer;
        int maxW = tr.getWidth(title);
        for (InteractionItem it : items) maxW = Math.max(maxW, tr.getWidth(it.label()));
        panelW = Math.max(96, maxW + PAD_X * 2 + ARROW_W);
        panelH = HEADER_H + items.size() * ROW_H + 4;

        // Près de la cible : la cible est sous le crosshair (centre écran), donc
        // on pose le panneau juste à droite du centre, centré verticalement
        // sur le crosshair.
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        panelX = cx + 14;
        panelY = cy - panelH / 2;
        // Garde-fous bords d'écran.
        panelX = Math.min(panelX, mc.getWindow().getScaledWidth() - panelW - 4);
        panelY = Math.max(4, Math.min(panelY, mc.getWindow().getScaledHeight() - panelH - 4));

        subW = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            if (!it.hasChildren()) continue;
            int w = 0;
            for (InteractionItem c : it.children()) w = Math.max(w, tr.getWidth(c.label()));
            subW[i] = Math.max(90, w + PAD_X * 2);
        }
    }

    /** Molette : navigue dans le niveau courant (dir = +1 bas / -1 haut). */
    public void scroll(int dir) {
        if (!active) return;
        if (level == 1 && selMain < items.size() && items.get(selMain).hasChildren()) {
            int n = items.get(selMain).children().size();
            selSub = wrap(selSub + dir, n);
        } else {
            selMain = wrap(selMain + dir, items.size());
            selSub = 0;
        }
    }

    /** Clic gauche : valide l'item sélectionné (entre dans un sous-menu ou exécute). */
    public void activateSelected() {
        if (!active || items.isEmpty()) return;
        if (level == 1 && selMain < items.size() && items.get(selMain).hasChildren()) {
            List<InteractionItem> ch = items.get(selMain).children();
            if (selSub < ch.size()) run(ch.get(selSub));
            return;
        }
        InteractionItem it = items.get(selMain);
        if (it.hasChildren()) {
            level = 1;
            selSub = 0;
        } else {
            run(it);
        }
    }

    private void run(InteractionItem it) {
        if (it.action() != null) it.action().run();
        deactivate();
    }

    private static int wrap(int i, int n) {
        if (n <= 0) return 0;
        return ((i % n) + n) % n;
    }

    private int rowY(int i) {
        return panelY + HEADER_H + i * ROW_H;
    }

    public void render(DrawContext ctx) {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return;
        var tr = mc.textRenderer;

        DrawHelpers.roundedOutlinedRect(ctx, panelX, panelY, panelW, panelH, 5,
            Colors.BACKDROP_85, Colors.BORDER_STRONG);
        ctx.drawText(tr, RebornFont.bold(title), panelX + PAD_X, panelY + 4, Colors.GOLD, false);

        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            int y = rowY(i);
            boolean sel = (i == selMain);
            if (sel) DrawHelpers.roundedRect(ctx, panelX + 2, y, panelW - 4, ROW_H, 3, Colors.ACCENT_SOFT);
            ctx.drawText(tr, RebornFont.body(it.label()), panelX + PAD_X, y + 2,
                sel ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
            if (it.hasChildren()) {
                ctx.drawText(tr, RebornFont.body("›"), panelX + panelW - ARROW_W, y + 2,
                    sel ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);
            }
        }

        // Sous-menu (niveau 1).
        if (level == 1 && selMain < items.size() && items.get(selMain).hasChildren()) {
            List<InteractionItem> ch = items.get(selMain).children();
            int sx = panelX + panelW + 3, sw = subW[selMain], sy = rowY(selMain) - 4;
            int sh = ch.size() * ROW_H + 6;
            sy = Math.max(4, Math.min(sy, mc.getWindow().getScaledHeight() - sh - 4));
            DrawHelpers.roundedOutlinedRect(ctx, sx, sy, sw, sh, 5, Colors.BACKDROP_85, Colors.BORDER_STRONG);
            for (int j = 0; j < ch.size(); j++) {
                int y = sy + 3 + j * ROW_H;
                boolean sel = j == selSub;
                if (sel) DrawHelpers.roundedRect(ctx, sx + 2, y, sw - 4, ROW_H, 3, Colors.ACCENT_SOFT);
                ctx.drawText(tr, RebornFont.body(ch.get(j).label()), sx + PAD_X, y + 2,
                    sel ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
            }
        }
    }
}
