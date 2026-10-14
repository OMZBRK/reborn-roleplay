package fr.reborn.hud.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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

    /* Géométrie en unités menu (DaKit.echelleMenu : 1 unité = la taille d'un GUI à l'échelle 2 en 1080p). */
    private static final int ROW_H = 14;
    private static final int HEADER_H = 24;
    private static final int SUB_PAD = 5;
    private static final int PAD_X = 12;
    private static final int ARROW_W = 12;
    private static final int MIN_W = 120, MAX_W = 200, MAX_SUB_W = 200;

    /* Textures : tools/gen_interaction_textures.py. */
    private static final Identifier PANNEAU = DaKit.tex("interaction", "panneau"),
        SOUS = DaKit.tex("interaction", "sous_panneau"),
        SURVOL = DaKit.tex("interaction", "survol"),
        PIECES = DaKit.tex("interaction", "pieces"),
        ETIQUETTE = DaKit.tex("interaction", "etiquette");
    /** Sceau du bandeau : 0 人 joueur, 1 屍 corps, 2 己 soi, 3 物 bloc ou chose. */
    private int sceau = 0;
    private Component titleC = Component.empty();

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
    /** Position calculée de chaque sous-menu (même valeur au rendu et au clic). */
    private int[] subX = new int[0], subY = new int[0];
    private int lastHot = -2;
    /** Menu à enchaîner après l'action choisie (confirmation), au même endroit. */
    private String chainTitle;
    private List<InteractionItem> chainItems;

    private InteractionMode() {}

    public boolean isActive() {
        return active;
    }

    /** Position du curseur (coordonnées GUI) — pour les plaques du duel cliquables en mode interaction. */
    public double cursorGuiX() { return cursorX; }

    public double cursorGuiY() { return cursorY; }

    /** Banc d'essai : mode curseur actif (sans menu), curseur posé en (x, y) GUI. */
    public void debugCursor(double x, double y) {
        active = true;
        menuOpen = false;
        cursorX = x;
        cursorY = y;
    }

    /**
     * Le mode capte la souris uniquement en jeu : si un écran s'ouvre (inventaire, chat…) on sort
     * du mode, sinon la souris restait bloquée dans cet écran.
     */
    public boolean isCapturing() {
        if (!active) return false;
        if (Minecraft.getInstance().gui.screen() != null) { deactivate(); return false; }
        return true;
    }

    /** Toggle : entre/sort du mode curseur. */
    public void toggle() {
        if (active) {
            deactivate();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gui.screen() != null) return;
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
            // Plaques du duel (invitation, abandon) : cliquables en mode curseur.
            if (fr.reborn.hud.duel.DuelHud.click(cursorX, cursorY)) { deactivate(); return; }
            // Mode curseur → raycast la cible ; rien sous le curseur = menu « sur soi ».
            HitResult hit = CursorRaycast.raycast(mc, cursorX, cursorY);
            if (hit != null) openMenuFor(mc, hit);
            else openSelfMenu(mc);
            return;
        }

        // Menu ouvert : choix d'un item.
        if (submenuOwner >= 0) {
            int j = subRowAt(submenuOwner, mx(), my());
            if (j >= 0) {
                run(items.get(submenuOwner).children().get(j));
                return;
            }
        }
        int row = mainRowAt(mx(), my());
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
        sceau = 2;
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
                title = pe.getGameProfile().name();
                var body = fr.reborn.hud.ko.CorpsClient.body(pe.getUUID());
                sceau = body != null && body.phase() != fr.reborn.hud.ko.CorpsClient.PHASE_DOULEUR ? 1 : 0;
                items = InteractionMenus.forPlayer(title, pe.getUUID());
            } else {
                title = e.getType().getDescription().getString();
                sceau = 3;
                items = InteractionMenus.forEntity(e);
            }
        } else if (hit instanceof BlockHitResult bhr) {
            title = "Bloc";
            sceau = 3;
            items = InteractionMenus.forBlock(bhr.getBlockPos());
        } else {
            return;
        }
        layoutAtCursor(mc);
        hovered = submenuOwner = subHovered = -1;
        menuOpen = true;
        updateHover();
    }

    /** Banc d'essai (FeaturePreview) : ouvre un menu donné au centre, survol sur la ligne {@code hover}. */
    public void debugOpen(String t, List<InteractionItem> it, int hover) {
        Minecraft mc = Minecraft.getInstance();
        active = true;
        title = t;
        items = it;
        sceau = t.equals("Moi") ? 2 : t.equals("Bloc") ? 3 : it.stream().anyMatch(x -> x.label().startsWith("Attraper")
            || x.label().startsWith("Constater")) ? 1 : 0;
        cursorX = mc.getWindow().getGuiScaledWidth() * 0.42;
        cursorY = mc.getWindow().getGuiScaledHeight() * 0.30;
        layoutAtCursor(mc);
        menuOpen = true;
        if (hover >= 0) {
            double k = 1 / guiParMenu();
            cursorX = (panelX + panelW / 2.0) * k;
            cursorY = (rowY(hover) + ROW_H / 2.0) * k;
        }
        updateHover();
    }

    /** Banc d'essai : referme tout. */
    public void debugClose() { if (active) deactivate(); }

    /** Position du curseur en unités menu. */
    private double mx() { return cursorX * guiParMenu(); }

    private double my() { return cursorY * guiParMenu(); }

    /** Unités menu par unité GUI (échelle GUI / échelle menu). */
    private static double guiParMenu() {
        return Minecraft.getInstance().getWindow().getGuiScale() / (double) DaKit.echelleMenu();
    }

    private void layoutAtCursor(Minecraft mc) {
        var tr = mc.font;
        int km = DaKit.echelleMenu();
        int sw = mc.getWindow().getWidth() / km, sh = mc.getWindow().getHeight() / km;
        titleC = DaKit.arcade(title);
        int maxW = tr.width(titleC) + 30 + 12;
        for (InteractionItem it : items) {
            maxW = Math.max(maxW, tr.width(DaKit.inter(it.label())) + PAD_X * 2 + (it.hasChildren() ? ARROW_W : 0)
                + raisonW(tr, it));
        }
        panelW = Math.max(MIN_W, Math.min(MAX_W, maxW));
        panelH = HEADER_H + items.size() * ROW_H + 6;
        // Au point du clic (curseur), clampé à l'écran.
        panelX = (int) Math.max(4, Math.min(mx(), sw - panelW - 4));
        panelY = (int) Math.max(4, Math.min(my() - 6, sh - panelH - 4));

        subW = new int[items.size()]; subX = new int[items.size()]; subY = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InteractionItem it = items.get(i);
            if (!it.hasChildren()) continue;
            int w = 0;
            for (InteractionItem c : it.children()) w = Math.max(w, tr.width(DaKit.inter(c.label())) + raisonW(tr, c));
            subW[i] = Math.max(MIN_W, Math.min(MAX_SUB_W, w + PAD_X * 2));
            int h = it.children().size() * ROW_H + SUB_PAD * 2;
            // à droite du menu, ou à gauche s'il n'y a pas la place
            subX[i] = panelX + panelW + subW[i] + 3 <= sw - 4 ? panelX + panelW + 3 : panelX - subW[i] - 3;
            subY[i] = Math.max(4, Math.min(rowY(i) - SUB_PAD, sh - h - 4));
        }
    }

    private void updateHover() {
        hovered = mainRowAt(mx(), my());
        if (hovered >= 0 && items.get(hovered).hasChildren()) submenuOwner = hovered;
        else if (hovered >= 0) submenuOwner = -1;
        subHovered = submenuOwner >= 0 ? subRowAt(submenuOwner, mx(), my()) : -1;
    }

    private void run(InteractionItem it) {
        // entrée grisée : rien n'est envoyé, le menu reste ouvert (la raison est déjà affichée)
        if (it.isDisabled()) {
            fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 0.6f, 0.2f);
            return;
        }
        chainItems = null;
        if (it.action() != null) it.action().run();
        if (chainItems != null) {
            Minecraft mc = Minecraft.getInstance();
            title = chainTitle;
            items = chainItems;
            chainItems = null;
            double k = 1 / guiParMenu();
            cursorX = panelX * k;
            cursorY = (panelY + 6) * k;
            layoutAtCursor(mc);
            hovered = submenuOwner = subHovered = -1;
            menuOpen = true;
            updateHover();
            return;
        }
        deactivate();
    }

    /**
     * Depuis l'action d'une entrée : ouvre {@code next} à la place du menu courant (confirmation d'une action
     * grave, par exemple une mise à mort RP).
     */
    public void chain(String nextTitle, List<InteractionItem> next) {
        chainTitle = nextTitle;
        chainItems = next;
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
        int sx = subX[owner], sw = subW[owner], sy = subY[owner];
        if (mx < sx || mx > sx + sw) return -1;
        var ch = items.get(owner).children();
        for (int j = 0; j < ch.size(); j++) {
            int y = sy + SUB_PAD + j * ROW_H;
            if (my >= y && my < y + ROW_H) return j;
        }
        return -1;
    }

    public void extractRenderState(GuiGraphicsExtractor ctx) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        var tr = mc.font;

        DaKit.unites(ctx, DaKit.echelleMenu());
        if (menuOpen) {
            planche(ctx, PANNEAU, panelX, panelY, panelW, panelH, 232);
            // bandeau : sceau à cheval sur le bord gauche, nom de la cible en ArcadePix
            DaKit.blit(ctx, PIECES, panelX + 5, panelY + 3, 12 + 20 * sceau, 0, 20, 20, 96, 34);
            DaKit.arcX1(ctx, tr, titleC, panelX + 30, panelY + 9, DaKit.CREME, 0, true);

            for (int i = 0; i < items.size(); i++) {
                boolean hot = (i == hovered) || (i == submenuOwner);
                ligne(ctx, tr, items.get(i), panelX, rowY(i), panelW, hot, i < items.size() - 1);
            }

            if (submenuOwner >= 0 && items.get(submenuOwner).hasChildren()) {
                var ch = items.get(submenuOwner).children();
                int sx = subX[submenuOwner], sw = subW[submenuOwner], sy = subY[submenuOwner];
                int sh = ch.size() * ROW_H + SUB_PAD * 2;
                planche(ctx, SOUS, sx, sy, sw, sh, 200);
                for (int j = 0; j < ch.size(); j++) {
                    ligne(ctx, tr, ch.get(j), sx, sy + SUB_PAD + j * ROW_H, sw, j == subHovered, j < ch.size() - 1);
                }
            }
            int hot = hovered * 100 + subHovered;
            if (hot != lastHot) {
                if (hovered >= 0) fr.reborn.hud.menu.RebornSounds.playReborn("esc.hover", 1.25f, 0.12f);
                lastHot = hot;
            }
        } else {
            // Mode curseur (pas encore de menu) : étiquette laquée près du curseur, touche en cabochon.
            Component clic = DaKit.arcade("Clic"), lib = DaKit.inter("interagir");
            int tw = (int) Math.ceil(tr.width(clic) * 0.5f) + 6;
            int w = 5 + tw + 4 + Math.round(DaKit.largeur8(tr, lib)) + 7;
            int x = (int) Math.round(mx()) + 14, y = (int) Math.round(my()) + 4;
            DaKit.coupeH(ctx, ETIQUETTE, x, y, w + 2, 0, 0, 132, 16, 8, 132, 16);
            float ym = y + 7;
            int cx = x + 5, cy = Math.round(ym - 5);
            DaKit.blit(ctx, PIECES, cx, cy, 0, 22, 4, 12, 96, 34);
            DaKit.blit(ctx, PIECES, cx + 4, cy, 12, 22, tw - 8, 12, 96, 34);
            DaKit.blit(ctx, PIECES, cx + tw - 4, cy, 36, 22, 6, 12, 96, 34);
            DaKit.arcFin(ctx, tr, clic, cx + tw / 2f, ym - 1.75f, DaKit.OR_CLAIR, 1, false);
            DaKit.inter8(ctx, tr, lib, cx + tw + 4, ym, DaKit.CREME_VAL, 0);
        }
        DaKit.finUnites(ctx);

        drawCursor(ctx, (int) cursorX, (int) cursorY);
    }

    /** Planche laquée à la taille voulue (coupe de la texture : région (w+8)×(h+8) posée en (x−2, y−2)). */
    private static void planche(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int texH) {
        DaKit.coupe(ctx, id, x - 2, y - 2, w + 8, h + 8, 0, 0, 208, texH, 16, 16, 208, texH);
    }

    /** Largeur réservée à la raison d'une entrée grisée (Inter 7, à droite), 0 sinon. */
    private static int raisonW(Font tr, InteractionItem it) {
        return it.isDisabled() ? Math.round(DaKit.largeur7(tr, DaKit.inter(it.reason()))) + 10 : 0;
    }

    /** Une ligne : survol en laque rouge à réglette d'or, libellé Inter 9, chevron des sous-menus, filet de séparation.
     *  Grisée : libellé éteint, pas de survol, raison en petit à droite. */
    private static void ligne(GuiGraphicsExtractor ctx, Font tr, InteractionItem it, int x, int y, int w, boolean hot,
                              boolean filet) {
        if (it.isDisabled()) {
            if (filet) ctx.fill(x + 10, y + ROW_H - 1, x + w - 10, y + ROW_H, 0xFF2D181C);
            DaKit.inter9(ctx, tr, DaKit.inter(it.label()), x + PAD_X, y + ROW_H / 2f, 0xFF7A6E60, 0);
            DaKit.inter7o(ctx, tr, DaKit.inter(it.reason()), x + w - PAD_X, y + ROW_H / 2f, 0xFF8E8172, 2);
            return;
        }
        if (hot) DaKit.coupeH(ctx, SURVOL, x + 4, y, w - 8, 0, 0, 196, 14, 8, 196, 14);
        else if (filet) ctx.fill(x + 10, y + ROW_H - 1, x + w - 10, y + ROW_H, 0xFF2D181C);
        // la mise à mort (Achever, exécutions, confirmation) en vermillon : l'action grave se distingue
        String l = it.label();
        boolean grave = l.startsWith("Achever") || l.startsWith("Confirmer la mise")
            || it.hasChildren() && l.startsWith("Exécut");
        DaKit.inter9(ctx, tr, DaKit.inter(l), x + PAD_X, y + ROW_H / 2f,
            grave ? (hot ? 0xFFF09A9E : 0xFFE26474) : hot ? DaKit.CREME : 0xFFD6CAB0, 0);
        if (it.hasChildren()) DaKit.blit(ctx, PIECES, x + w - 15, y + 4, hot ? 5 : 0, 0, 4, 7, 96, 34);
    }

    /** Curseur custom 16×16 (pointe en haut-gauche). Le numéro est choisi via
     *  le réglage {@code interactionCursor} → reborn:textures/gui/cursorN.png.
     *  0 ou texture absente = flèche procédurale. */
    private static final int CURSOR_SIZE = 16;
    private static final java.util.Map<net.minecraft.resources.Identifier, Boolean> CURSOR_EXISTS = new java.util.HashMap<>();

    private void drawCursor(GuiGraphicsExtractor ctx, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        int sel = 1;
        try { sel = fr.reborn.hud.RebornHudClient.config().getInteractionCursor(); }
        catch (RuntimeException ignored) {}
        if (sel >= 1) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn", "textures/gui/cursor" + sel + ".png");
            if (CURSOR_EXISTS.computeIfAbsent(id, k -> mc.getResourceManager().getResource(k).isPresent())) {
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
