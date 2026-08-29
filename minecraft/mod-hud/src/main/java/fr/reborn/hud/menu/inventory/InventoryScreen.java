package fr.reborn.hud.menu.inventory;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.style.IconTextures;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>Sacoche ninja</b> — inventaire RP fidèle à la maquette : <b>deux panneaux
 * translucides</b> (le monde reste visible). Panneau GAUCHE = <b>gros perso 3D
 * cadré</b> entouré de deux colonnes de cases cosmétiques + barre de poids ;
 * panneau DROITE = <b>rail de filtres vertical</b> + grille (base = hotbar
 * vanilla) + rangée « SUR SOI ». En-tête : titre, recherche, onglets, croix.
 * Clic gauche = déplacer, clic droit = panneau d'actions ancré sur l'objet.
 */
public class InventoryScreen extends Screen {

    private static final int F_ALL = 0, F_SAC = 1, F_DIVERS = 2;
    private static final String[] FILTER_NAME = {"Tout", "Sac", "Divers"};
    private static final String[] FILTER_ICON = {"cadre_all", "cadre_bag", "cadre_others"};
    private static final int TAB_EQUIP = 0, TAB_COSM = 1;

    // Accent unifié = crimson Reborn (raccord avec le reste du mod).
    private static final int ACC = Colors.ACCENT;
    private static final int ACC_HOVER = Colors.ACCENT_HOVER;

    private static final int PANEL_FILL = Colors.withAlpha(0xFF17110F, 0.40f);
    private static final int PANEL_BORDER = Colors.withAlpha(ACC, 0.34f);
    private static final int INK = Colors.withAlpha(0xFFFFFFFF, 0.86f);
    private static final int INK_DIM = Colors.withAlpha(0xFFFFFFFF, 0.42f);

    private static String lastSearch = "";
    private static int lastFilter = F_ALL;
    private static int lastTab = TAB_COSM;

    private InventoryData.Snapshot snap;
    private String tierDisplay;
    private int extraSlots;
    private int bagRows;

    private EditBox search;
    private int filter = lastFilter;
    private int tab = lastTab;

    private String carriedRef = null;
    private ItemStack carriedStack = null;

    private String selRef = null;
    private ItemStack selStack = null;
    private SlotItem selMeta = null;
    private int anchorX, anchorY;
    private final List<Btn> panelButtons = new ArrayList<>();

    private String hoverRef = null;
    private CosmeticSlot hoverCosm = null;
    private final long openedAt = System.currentTimeMillis();

    // Layout.
    private int contentX, contentW, top, bottom;
    private int leftX, leftW, rightX, rightW, panelsY;
    // Taille native des cadres dessinés-main : rendu 1:1 = net (pas d'étirement).
    private static final int CADRE_PX = 32;
    private int gridX, gridY, cell, gap = 4, gridCols = 9, hotbarY, dividerGap = 16;
    // Deux colonnes de cosmétiques (autour du perso).
    private int cosmLeftX, cosmRightX, cosmY0, cosmSize = CADRE_PX, cosmGap = 6;
    private static final int COSM_ROWS = 5; // lignes par colonne
    private int modelX1, modelY1, modelX2, modelY2;
    private int closeX, closeY, closeSize = 15, tabsY;
    private int weightX, weightY, weightW, weightH = 7;
    // Rail de filtres vertical (bord gauche du panneau droit).
    private static final int CHIP_PX = 22;
    private int chipRailX, chipY0, chipStep = CHIP_PX + 6;
    private final int[] chipY = new int[FILTER_ICON.length];
    private int tabEquipX0, tabEquipX1, tabCosmX0, tabCosmX1;

    public InventoryScreen() {
        super(Component.literal("Inventaire"));
        loadSnapshot();
    }

    /** (Re)lit l'état courant. Appelé au boot et à chaque push serveur (refresh en place). */
    private void loadSnapshot() {
        this.snap = InventoryData.get();
        this.tierDisplay = BagTiers.fromName(snap.bagTier()).displayName;
        this.extraSlots = Math.max(0, snap.extraSlots());
        this.bagRows = (extraSlots + gridCols - 1) / gridCols;
    }

    /**
     * Met à jour l'écran avec le nouvel état SANS le recréer — préserve le filtre,
     * l'onglet, la recherche et l'objet en main (évite le flash de catégorie au swap).
     */
    public void refresh() {
        loadSnapshot();
        // Un item peut avoir bougé/disparu : purge une sélection devenue invalide.
        if (selRef != null && stackAt(selRef) == null) closePanel();
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        }
        search = new EditBox(this.font, 0, 0, 120, 14, Component.literal("search"));
        search.setMaxLength(48);
        search.setHint(Component.literal("Rechercher…"));
        search.setBordered(false);
        search.setValue(lastSearch);
        search.setResponder(v -> lastSearch = v);
        this.addRenderableWidget(search);
        // Resync : demande un snapshot frais au serveur (cosmétiques équipés à jour).
        send("open");
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        }
        lastFilter = filter;
        lastTab = tab;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────── Layout (2 panneaux translucides, façon refinv) ───────────
    private void layout() {
        contentX = Math.max(10, (int) (this.width * 0.03f));
        contentW = this.width - contentX * 2;
        top = Math.max(12, (int) (this.height * 0.05f));
        bottom = this.height - Math.max(10, (int) (this.height * 0.045f));

        closeSize = 15;
        closeX = contentX + contentW - closeSize;
        closeY = top - 2;

        panelsY = top + 30;
        leftX = contentX;
        leftW = Math.max(160, (int) (contentW * 0.36f));
        rightX = leftX + leftW + 12;
        rightW = contentX + contentW - rightX;

        int pad = 10;

        // Panneau GAUCHE : onglets + 2 colonnes cosmétiques + perso au centre + poids.
        tabsY = panelsY + 8;
        weightX = leftX + pad;
        weightY = bottom - 16;
        weightW = leftW - pad * 2;

        cosmY0 = panelsY + 28;
        int availV = (weightY - 16) - cosmY0;
        cosmSize = clampI(availV / COSM_ROWS - cosmGap, 20, 30);
        cosmLeftX = leftX + pad;
        cosmRightX = leftX + leftW - pad - cosmSize;
        modelX1 = cosmLeftX + cosmSize + 8;
        modelX2 = cosmRightX - 8;
        modelY1 = panelsY + 24;
        modelY2 = weightY - 16;

        // Panneau DROITE : rail de filtres vertical + grille à droite.
        chipRailX = rightX + pad;
        chipY0 = panelsY + 12;
        chipStep = CHIP_PX + 6;

        int gridLeft = chipRailX + CHIP_PX + 12;
        gridY = panelsY + 16;
        gridCols = 9;
        int noticeRows = bagRows > 0 ? bagRows : 3;
        int rows = noticeRows + 1;
        int availW = (rightX + rightW - pad) - gridLeft;
        int availH = (bottom - 8) - gridY - dividerGap;
        int cellW = (availW - (gridCols - 1) * gap) / gridCols;
        int cellH = availH / rows - gap;
        // Vise CADRE_PX (32) pour un rendu net 1:1 ; ne réduit que si la place manque.
        cell = Math.max(18, Math.min(CADRE_PX, Math.min(cellW, cellH)));
        int gridW = gridCols * cell + (gridCols - 1) * gap;
        // Centré dans l'espace disponible à droite du rail.
        gridX = gridLeft + Math.max(0, (availW - gridW) / 2);
        hotbarY = gridY + noticeRows * (cell + gap) + dividerGap;

        // Recherche centrée en en-tête.
        int sw = Math.min(150, (int) (contentW * 0.18f));
        search.setX((this.width - sw) / 2);
        search.setY(top);
        search.setWidth(sw);
    }

    // ─────────── Accès items ───────────
    private ItemStack stackAt(String ref) {
        if (ref == null || ref.length() < 2) return null;
        // Cosmétique équipé (panneau détail ouvert via clic droit) : ref = "C<SLOT>".
        if (ref.charAt(0) == 'C') {
            CosmeticSlot cs = CosmeticSlot.fromName(ref.substring(1));
            if (cs == null) return null;
            SlotItem si = snap.equipped().get(cs);
            return si != null ? si.toStack() : null;
        }
        int i = idx(ref);
        if (i < 0) return null;
        if (ref.charAt(0) == 'H') {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || i >= 9) return null;
            ItemStack s = mc.player.getInventory().getItem(i);
            return (s == null || s.isEmpty()) ? null : s;
        }
        if (ref.charAt(0) == 'B') {
            if (i >= extraSlots || snap.bag() == null || i >= snap.bag().length) return null;
            SlotItem si = snap.bag()[i];
            return si != null ? si.toStack() : null;
        }
        return null;
    }

    private SlotItem metaAt(String ref) {
        if (ref == null || ref.charAt(0) != 'B') return null;
        int i = idx(ref);
        return (i >= 0 && snap.bag() != null && i < snap.bag().length) ? snap.bag()[i] : null;
    }

    private static int idx(String ref) {
        try { return Integer.parseInt(ref.substring(1)); } catch (Exception e) { return -1; }
    }

    private boolean isBagStack(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        Identifier id = s.get(DataComponents.ITEM_MODEL);
        return id != null && BagTiers.fromModel(id.toString()) != null;
    }

    private String hoverName(ItemStack s) {
        try { return s.getHoverName().getString(); } catch (Exception e) { return null; }
    }

    private String actionLabel(ItemStack st, SlotItem meta) {
        if (meta != null && meta.hasAction()) return meta.actionLabel;
        String n = st != null ? hoverName(st) : null;
        if (n != null && n.toLowerCase(Locale.ROOT).contains("feuille")) return "Faire le test";
        return null;
    }

    private int groupOf(ItemStack s) {
        if (isBagStack(s)) return F_SAC;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String p = id != null ? id.toString() : "";
        // « Sac » = ce qui se porte / s'équipe (armes, outils, armures) ; le reste → Divers.
        if (p.matches(".*(sword|_axe|bow|crossbow|trident|pickaxe|shovel|_hoe|mace|arrow|helmet|chestplate|leggings|boots|shield|elytra).*"))
            return F_SAC;
        return F_DIVERS;
    }

    private boolean dimmed(ItemStack st, SlotItem meta) {
        if (st == null) return false;
        if (filter != F_ALL && groupOf(st) != filter) return true;
        String q = search != null ? search.getValue().trim().toLowerCase(Locale.ROOT) : "";
        if (!q.isEmpty()) {
            String n = meta != null && meta.name != null ? meta.name : hoverName(st);
            if (n == null || !n.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    // ─────────── Rendu ───────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        ctx.fillGradient(0, 0, this.width, this.height,
            Colors.withAlpha(0xFF0A0709, 0.40f * appear + 0.04f),
            Colors.withAlpha(0xFF0A0709, 0.60f * appear + 0.04f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Refresh live : si le serveur a poussé un nouveau snapshot, se resynchro
        // (les cosmétiques équipés apparaissent sans rouvrir la Sacoche).
        if (InventoryData.fromServer()) {
            InventoryData.Snapshot cur = InventoryData.get();
            if (cur != snap) refresh();
        }
        layout();
        Font f = this.font;
        hoverRef = slotAt(mouseX, mouseY);
        hoverCosm = null;

        // En-tête : picto sac (net, vertical-centré) + titre.
        IconTextures.drawIcon(ctx, "slot_bag", contentX, top - 3, 16);
        ctx.text(f, RebornFont.arcade("INVENTAIRE"), contentX + 21, top, INK, false);
        search.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.fill(search.getX() - 2, top + 13, search.getX() + search.getWidth(), top + 14, Colors.withAlpha(0xFFFFFFFF, 0.18f));
        drawClose(ctx, f, mouseX, mouseY);

        // Panneaux translucides.
        panel(ctx, leftX, panelsY, leftW, bottom - panelsY);
        panel(ctx, rightX, panelsY, rightW, bottom - panelsY);

        // GAUCHE : onglets + cosmétiques + perso + poids.
        drawTabs(ctx, f, mouseX, mouseY);
        drawPreview(ctx, mouseX, mouseY);
        if (tab == TAB_COSM) drawCosmetics(ctx, f, mouseX, mouseY);
        else drawEquip(ctx, f, mouseX, mouseY);
        drawWeight(ctx, f);

        // DROITE : rail de filtres + grille.
        drawFilterRail(ctx, f, mouseX, mouseY);
        drawGrid(ctx, f, mouseX, mouseY);

        if (selRef != null && selStack != null) drawDetailPanel(ctx, f, mouseX, mouseY);

        if (carriedStack != null) {
            ctx.item(carriedStack, mouseX - 8, mouseY - 8);
            ctx.itemDecorations(f, carriedStack, mouseX - 8, mouseY - 8);
        }
        // Infobulle : nom du compartiment cosmétique survolé, sinon item survolé.
        if (carriedStack == null && selRef == null) {
            if (hoverCosm != null) {
                ctx.setTooltipForNextFrame(f, Component.literal(hoverCosm.label), mouseX, mouseY);
            } else if (hoverRef != null) {
                ItemStack tip = stackAt(hoverRef);
                if (tip != null) ctx.setTooltipForNextFrame(f, tip, mouseX, mouseY);
            }
        }

        Component hint = RebornFont.arcade(carriedStack != null
            ? "CLIC : poser   •   CLIC DEHORS : jeter   •   ECHAP : annuler"
            : "CLIC : prendre / poser   •   CLIC DROIT : actions   •   ECHAP / ✕ : fermer");
        // Hint discret : réduit + estompé.
        drawScaledCentered(ctx, f, hint, this.width / 2f, bottom + 6, INK_DIM, 0.72f);
    }

    private void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 7, PANEL_FILL, PANEL_BORDER);
        ctx.fill(x + 8, y + 1, x + w - 8, y + 2, Colors.withAlpha(ACC, 0.22f));
    }

    private void slot(GuiGraphicsExtractor ctx, int x, int y, int s, int state) {
        int fill = Colors.withAlpha(0xFF000000, 0.34f);
        int border = switch (state) {
            case 2 -> ACC;
            case 3 -> Colors.SUCCESS;
            case 1 -> Colors.withAlpha(ACC_HOVER, 0.75f);
            default -> Colors.withAlpha(0xFFFFFFFF, 0.14f);
        };
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, s, s, 4, fill, border);
    }

    /**
     * Dessine un cadre dessiné-main (bordure incrustée) comme fond de slot, puis
     * une surbrillance d'état par-dessus. Retombe sur {@link #slot} si la texture manque.
     * state : 0 neutre, 1 survol, 2 sélection/plein, 3 déposable.
     */
    private void cadre(GuiGraphicsExtractor ctx, String name, int x, int y, int s, int state) {
        if (!IconTextures.drawIcon(ctx, name, x, y, s)) { slot(ctx, x, y, s, state); return; }
        // Surbrillance façon cooldown / slot vanilla : voile translucide PAR-DESSUS le cadre
        // (pas de bordure ajoutée → le cadre garde sa taille nette).
        switch (state) {
            case 1 -> ctx.fill(x, y, x + s, y + s, 0x40FFFFFF);                              // survol : voile blanc
            case 2 -> ctx.fill(x, y, x + s, y + s, Colors.withAlpha(ACC_HOVER, 0.34f));      // sélection : voile crimson
            case 3 -> ctx.fill(x, y, x + s, y + s, Colors.withAlpha(Colors.SUCCESS, 0.30f)); // déposable : voile vert
            default -> { }
        }
    }

    private void drawPreview(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || modelX2 <= modelX1 || modelY2 <= modelY1) return;
        // Formule refinv : ~0.42 × hauteur de la boîte (perso cadré dans le panneau).
        int size = Math.max(40, Math.min(150, (int) ((modelY2 - modelY1) * 0.42f)));
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(ctx,
            modelX1, modelY1, modelX2, modelY2, size, 0.0f, mouseX, (modelY1 + modelY2) / 2f, mc.player);
    }

    /** Position (x,y) d'un slot cosmétique dans sa colonne. */
    private int[] cosmPos(CosmeticSlot cs) {
        int row = 0;
        for (CosmeticSlot o : CosmeticSlot.values()) {
            if (o == cs) break;
            if (o.side == cs.side) row++;
        }
        int x = cs.side == 0 ? cosmLeftX : cosmRightX;
        return new int[] { x, cosmY0 + row * (cosmSize + cosmGap) };
    }

    private void cosmoSlot(GuiGraphicsExtractor ctx, int x, int y, SlotItem it, String emptyCadre, int mx, int my, boolean droppable) {
        boolean hover = mx >= x && mx < x + cosmSize && my >= y && my < y + cosmSize;
        int state = droppable ? 3 : hover ? 1 : (it != null ? 2 : 0);
        cadre(ctx, it != null ? "cadre" : emptyCadre, x, y, cosmSize, state);
        if (it != null) ctx.item(it.toStack(), x + (cosmSize - 16) / 2, y + (cosmSize - 16) / 2);
    }

    private void drawCosmetics(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        for (CosmeticSlot cs : CosmeticSlot.values()) {
            int[] p = cosmPos(cs);
            cosmoSlot(ctx, p[0], p[1], snap.equipped().get(cs), cs.icon, mx, my, carriedStack != null);
            if (mx >= p[0] && mx < p[0] + cosmSize && my >= p[1] && my < p[1] + cosmSize) hoverCosm = cs;
        }
    }

    private void drawEquip(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        // Le sac s'équipe au centre (sous le perso), plus lisible que collé au bord.
        int x = cosmLeftX, y = cosmY0;
        cosmoSlot(ctx, x, y, snap.bagItem(), "cadre_bag", mx, my, carriedStack != null && isBagStack(carriedStack));
        ctx.text(f, RebornFont.arcade("SAC"), x, y + cosmSize + 3, INK_DIM, false);
    }

    private void drawWeight(GuiGraphicsExtractor ctx, Font f) {
        double cur = snap.curWeight(), max = snap.maxWeight();
        double ratio = max > 0 ? cur / max : 0;
        float r = (float) Math.min(1.0, ratio);
        int fillCol = ratio > 1.0 ? Colors.DANGER
            : r < 0.8f ? Colors.lerp(Colors.SUCCESS, Colors.GOLD, r / 0.8f)
            : Colors.lerp(Colors.GOLD, Colors.DANGER, (r - 0.8f) / 0.2f);
        ctx.text(f, RebornFont.arcade("POIDS"), weightX, weightY - 10, INK_DIM, false);
        Component amt = Component.literal(String.format(Locale.ROOT, "%.1f / %.1f kg", cur, max));
        ctx.text(f, amt, weightX + weightW - f.width(amt), weightY - 10, ratio > 1.0 ? Colors.DANGER : INK_DIM, false);
        DrawHelpers.roundedRectFull(ctx, weightX, weightY, weightW, weightH, 3, Colors.withAlpha(0xFF000000, 0.5f));
        int fw = (int) (weightW * r);
        if (fw > 2) DrawHelpers.roundedRectFull(ctx, weightX, weightY, fw, weightH, 3, Colors.withAlpha(fillCol, 0.85f));
    }

    /** Rail de filtres vertical (haut→bas), cadres 32×32 rendus petits + label au survol. */
    private void drawFilterRail(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        for (int i = 0; i < FILTER_ICON.length; i++) {
            int y = chipY0 + i * chipStep;
            chipY[i] = y;
            boolean active = filter == i;
            boolean hover = mx >= chipRailX && mx < chipRailX + CHIP_PX && my >= y && my < y + CHIP_PX;
            cadre(ctx, FILTER_ICON[i], chipRailX, y, CHIP_PX, active ? 2 : hover ? 1 : 0);
            if (!active) ctx.fill(chipRailX, y, chipRailX + CHIP_PX, y + CHIP_PX, Colors.withAlpha(0xFF000000, hover ? 0.12f : 0.40f));
            // Label du filtre actif ou survolé, à droite de la puce.
            if (active || hover) {
                ctx.text(f, Component.literal(FILTER_NAME[i]), chipRailX + CHIP_PX + 5,
                    y + (CHIP_PX - 8) / 2, hover ? INK : INK_DIM, false);
            }
        }
        if (extraSlots > 0) {
            Component cap = Component.literal(extraSlots + " (" + tierDisplay + ")");
            ctx.text(f, cap, rightX + rightW - 10 - f.width(cap), panelsY + 8, Colors.withAlpha(ACC_HOVER, 0.7f), false);
        }
    }

    private void drawGrid(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        if (extraSlots > 0) {
            for (int i = 0; i < extraSlots; i++) {
                ItemStack st = stackAt("B" + i);
                SlotItem meta = snap.bag() != null && i < snap.bag().length ? snap.bag()[i] : null;
                drawItemSlot(ctx, f, bagX(i), bagY(i), ("B" + i).equals(carriedRef) ? null : st, meta,
                    hoverRef != null && hoverRef.equals("B" + i), ("B" + i).equals(selRef));
            }
        } else {
            drawNoBagNotice(ctx, f);
        }
        ctx.text(f, RebornFont.arcade("SUR SOI  •  BARRE D'ACTION"), gridX, hotbarY - 10, INK_DIM, false);
        for (int i = 0; i < 9; i++) {
            ItemStack st = stackAt("H" + i);
            drawItemSlot(ctx, f, hotbarX(i), hotbarY, ("H" + i).equals(carriedRef) ? null : st, null,
                hoverRef != null && hoverRef.equals("H" + i), ("H" + i).equals(selRef));
        }
    }

    private void drawItemSlot(GuiGraphicsExtractor ctx, Font f, int x, int y, ItemStack st, SlotItem meta, boolean hover, boolean sel) {
        cadre(ctx, "cadre", x, y, cell, sel ? 2 : hover ? 1 : 0);
        if (st != null && !st.isEmpty()) {
            int ix = x + (cell - 16) / 2, iy = y + (cell - 16) / 2;
            ctx.item(st, ix, iy);
            ctx.itemDecorations(f, st, ix, iy);
            if (meta != null && meta.rarity != null)
                ctx.fill(x + 2, y + cell - 3, x + cell - 2, y + cell - 1, Colors.withAlpha(rarityColor(meta.rarity), 0.95f));
            if (dimmed(st, meta)) ctx.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, Colors.withAlpha(0xFF000000, 0.55f));
        }
    }

    private void drawNoBagNotice(GuiGraphicsExtractor ctx, Font f) {
        int gw = gridCols * cell + (gridCols - 1) * gap;
        int x = gridX, yTop = gridY, h = (hotbarY - dividerGap) - gridY;
        if (h < 56) return;
        // Carte translucide + fin liseré crimson en haut (raccord panneaux).
        DrawHelpers.roundedOutlinedRectFull(ctx, x, yTop, gw, h, 7,
            Colors.withAlpha(0xFF000000, 0.26f), Colors.withAlpha(ACC, 0.28f));
        ctx.fill(x + 10, yTop + 1, x + gw - 10, yTop + 2, Colors.withAlpha(ACC, 0.20f));

        int cx = x + gw / 2;
        int cy = yTop + h / 2;
        // Emblème sac : centré, comme un blason, bien au-dessus du texte.
        int emb = 34;
        int embY = cy - emb / 2 - 20;
        IconTextures.drawIcon(ctx, "slot_bag", cx - emb / 2, embY, emb);
        // Petit trait crimson sous l'emblème pour l'asseoir.
        ctx.fill(cx - 18, embY + emb + 6, cx + 18, embY + emb + 7, Colors.withAlpha(ACC_HOVER, 0.5f));

        // Bloc texte centré, SOUS l'emblème, bien détaché.
        int ty = embY + emb + 16;
        drawCentered(ctx, f, RebornFont.arcade("AUCUN SAC EQUIPE"), cx, ty, Colors.withAlpha(ACC_HOVER, 0.9f));
        ty += 15;
        drawCentered(ctx, f, Component.literal("Portez une sacoche, une bandoulière ou un sac"), cx, ty, INK_DIM);
        ty += 11;
        drawCentered(ctx, f, Component.literal("pour débloquer de l'espace de rangement."), cx, ty, Colors.withAlpha(0xFFFFFFFF, 0.32f));
    }

    private void drawTabs(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        Component cosm = Component.literal("Cosmétique");
        Component equip = Component.literal("Équipement");
        int cxE = leftX + 10;
        int wE = f.width(equip);
        int cxSep = cxE + wE + 6;
        int cxC = cxSep + 8;
        ctx.text(f, equip, cxE, tabsY, tab == TAB_EQUIP ? ACC_HOVER : INK_DIM, false);
        ctx.text(f, Component.literal("|"), cxSep, tabsY, Colors.withAlpha(0xFFFFFFFF, 0.25f), false);
        ctx.text(f, cosm, cxC, tabsY, tab == TAB_COSM ? ACC_HOVER : INK_DIM, false);
        tabEquipX0 = cxE; tabEquipX1 = cxE + wE;
        tabCosmX0 = cxC; tabCosmX1 = cxC + f.width(cosm);
    }

    /** Croix de fermeture : X épais tracé main, parfaitement centré dans le cadre. */
    private void drawClose(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        boolean h = mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize;
        slot(ctx, closeX, closeY, closeSize, h ? 1 : 0);
        int cx = closeX + closeSize / 2, cy = closeY + closeSize / 2, r = 4;
        int col = h ? Colors.WHITE_PURE : Colors.withAlpha(0xFFFFFFFF, 0.72f);
        DrawHelpers.thickLine(ctx, cx - r, cy - r, cx + r, cy + r, 2, col);
        DrawHelpers.thickLine(ctx, cx - r, cy + r, cx + r, cy - r, 2, col);
    }

    // ─────────── Panneau détail (ancré sur l'item) ───────────
    private static final class Btn {
        final String label, icon;
        final int accent;
        final Runnable act;
        int x, y, w, h;
        Btn(String label, String icon, int accent, Runnable act) {
            this.label = label; this.icon = icon; this.accent = accent; this.act = act;
        }
    }

    private void drawDetailPanel(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        String name = selMeta != null && selMeta.name != null ? selMeta.name : hoverName(selStack);
        if (name == null) name = "Objet";
        double weight = selMeta != null ? selMeta.weight : 0;
        String rarity = selMeta != null ? selMeta.rarity : null;
        String desc = selMeta != null ? selMeta.desc : null;

        int pad = 9, w = 200;
        List<String> descLines = new ArrayList<>();
        if (desc != null) for (String part : desc.split("\n")) wrap(f, part, w - pad * 2 - 20, descLines);

        buildPanelButtons();
        int headH = 13 + (rarity != null ? 11 : 0) + (descLines.isEmpty() ? 0 : (9 + 6 + descLines.size() * 9));
        int actH = 20;
        int h = pad + headH + 8 + actH + pad - 6;

        int px = anchorX + cell + 6;
        if (px + w > this.width - 4) px = anchorX - w - 6;
        if (px < 4) px = 4;
        int py = anchorY;
        if (py + h > bottom) py = bottom - h;
        if (py < top) py = top;

        DrawHelpers.roundedOutlinedRectFull(ctx, px, py, w, h, 6, Colors.withAlpha(0xFF0C0A0D, 0.96f), Colors.withAlpha(ACC, 0.60f));
        ctx.fill(px + 1, py + 1, px + w - 1, py + 2, Colors.withAlpha(ACC_HOVER, 0.55f));

        int tx = px + pad, ty = py + pad;
        if (selStack != null) ctx.item(selStack, px + w - pad - 18, py + pad);
        ctx.text(f, Component.literal(name), tx, ty, Colors.WHITE_PURE, false);
        int nw = f.width(name);
        if (weight > 0) ctx.text(f, Component.literal("  " + String.format(Locale.ROOT, "%.2f kg", weight)), tx + nw, ty, INK_DIM, false);
        ty += 12;
        if (rarity != null) { ctx.text(f, Component.literal(rarity), tx, ty, rarityColor(rarity), false); ty += 11; }
        if (!descLines.isEmpty()) {
            ty += 6;
            ctx.text(f, RebornFont.arcade("DESCRIPTION"), tx, ty, Colors.withAlpha(ACC_HOVER, 0.75f), false);
            ty += 9;
            for (String l : descLines) { ctx.text(f, Component.literal(l), tx, ty, INK_DIM, false); ty += 9; }
        }
        int sepY = py + h - pad - actH + 2;
        ctx.fill(tx, sepY, px + w - pad, sepY + 1, Colors.withAlpha(0xFFFFFFFF, 0.10f));

        int ax = tx, ay = sepY + 5;
        for (Btn b : panelButtons) {
            boolean iconOnly = b.label.isEmpty();
            int lblW = iconOnly ? 0 : f.width(b.label) + 4;
            int bw = 14 + lblW + 4;
            b.x = ax; b.y = ay - 3; b.w = bw; b.h = 16;
            boolean hov = mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
            if (hov) DrawHelpers.roundedRectFull(ctx, b.x, b.y, b.w, b.h, 3, Colors.withAlpha(b.accent, 0.20f));
            IconTextures.drawIcon(ctx, b.icon, ax + 2, ay - 1, 12);
            if (!iconOnly) ctx.text(f, Component.literal(b.label), ax + 18, ay, hov ? Colors.WHITE_PURE : INK, false);
            ax += bw + 6;
        }
    }

    private void buildPanelButtons() {
        panelButtons.clear();
        if (selRef == null) return;
        // Cosmétique équipé : repositionnement in-world + retrait.
        if (selRef.charAt(0) == 'C') {
            final CosmeticSlot cs = CosmeticSlot.fromName(selRef.substring(1));
            final SlotItem cosmItem = cs != null ? snap.equipped().get(cs) : null;
            panelButtons.add(new Btn("Repositionner", "act_equip", ACC_HOVER, () -> {
                Minecraft mc = Minecraft.getInstance();
                closePanel();
                if (cs == null) return;
                // Cible le cosmétique équipé précis : id (modèle Nexo) + ancrage du slot.
                String id = fr.reborn.hud.cosmetic.CosmeticFeatureRenderer.cosmeticId(cs, cosmItem);
                fr.reborn.hud.cosmetic.CosmeticTransform.Anchor anchor =
                    fr.reborn.hud.cosmetic.CosmeticFeatureRenderer.anchorForSlot(cs);
                mc.setScreenAndShow(new fr.reborn.hud.menu.cosmetic.RepositionScreen(id, cs.label, anchor));
            }));
            panelButtons.add(new Btn("Retirer", "act_trash", Colors.DANGER, () -> {
                if (cs != null) send("cos:unequip:" + cs.name());
                closePanel();
            }));
            return;
        }
        final String ref = selRef;
        String al = actionLabel(selStack, selMeta);
        if (al != null) panelButtons.add(new Btn(al, "act_leaf", ACC_HOVER, () -> { send("use:" + ref); closePanel(); }));
        if (isBagStack(selStack)) panelButtons.add(new Btn("Équiper", "act_equip", ACC, () -> { send("bag:equip:" + ref); closePanel(); }));
        panelButtons.add(new Btn("Drop", "act_drop", Colors.withAlpha(ACC, 0.85f), () -> { send("drop:" + ref); closePanel(); }));
        panelButtons.add(new Btn("", "act_trash", Colors.DANGER, () -> { send("del:" + ref); closePanel(); }));
    }

    private void closePanel() {
        selRef = null; selStack = null; selMeta = null;
        panelButtons.clear();
    }

    private static void wrap(Font f, String s, int maxW, List<String> out) {
        if (s.isEmpty()) return;
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (f.width(test) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0) out.add(line.toString());
    }

    private int rarityColor(String r) {
        return switch (r.toLowerCase(Locale.ROOT)) {
            case "rare" -> 0xFF4A9EE0;
            case "épique", "epique" -> 0xFFB061D9;
            case "légendaire", "legendaire" -> 0xFFE0A54A;
            default -> Colors.withAlpha(ACC_HOVER, 0.9f);
        };
    }

    private void drawCentered(GuiGraphicsExtractor ctx, Font f, Component c, int cx, int y, int color) {
        ctx.text(f, c, cx - f.width(c) / 2, y, color, false);
    }

    /** Texte mis à l'échelle, centré horizontalement autour de cx (hint discret). */
    private void drawScaledCentered(GuiGraphicsExtractor ctx, Font f, Component c, float cx, float y, int color, float scale) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - (f.width(c) * scale) / 2f, y);
        ctx.pose().scale(scale, scale);
        ctx.text(f, c, 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    // ─────────── Géométrie ───────────
    private int bagX(int i) { return gridX + (i % gridCols) * (cell + gap); }
    private int bagY(int i) { return gridY + (i / gridCols) * (cell + gap); }
    private int hotbarX(int i) { return gridX + i * (cell + gap); }

    private String slotAt(int mx, int my) {
        for (int i = 0; i < extraSlots; i++) {
            int x = bagX(i), y = bagY(i);
            if (mx >= x && mx < x + cell && my >= y && my < y + cell) return "B" + i;
        }
        for (int i = 0; i < 9; i++) {
            int x = hotbarX(i), y = hotbarY;
            if (mx >= x && mx < x + cell && my >= y && my < y + cell) return "H" + i;
        }
        return null;
    }

    private int slotRefX(String ref) { int i = idx(ref); return ref.charAt(0) == 'H' ? hotbarX(i) : bagX(i); }
    private int slotRefY(String ref) { int i = idx(ref); return ref.charAt(0) == 'H' ? hotbarY : bagY(i); }

    private CosmeticSlot cosmeticAt(int mx, int my) {
        if (tab != TAB_COSM) return null;
        for (CosmeticSlot cs : CosmeticSlot.values()) {
            int[] p = cosmPos(cs);
            if (mx >= p[0] && mx < p[0] + cosmSize && my >= p[1] && my < p[1] + cosmSize) return cs;
        }
        return null;
    }

    private boolean equipBagSlotAt(int mx, int my) {
        return tab == TAB_EQUIP && mx >= cosmLeftX && mx < cosmLeftX + cosmSize && my >= cosmY0 && my < cosmY0 + cosmSize;
    }

    private int chipAt(int mx, int my) {
        if (mx < chipRailX || mx >= chipRailX + CHIP_PX) return -1;
        for (int i = 0; i < FILTER_ICON.length; i++) if (my >= chipY[i] && my < chipY[i] + CHIP_PX) return i;
        return -1;
    }

    // ─────────── Interactions ───────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        layout();
        int mx = (int) e.x(), my = (int) e.y();

        if (selRef != null && e.button() == 0) {
            for (Btn b : panelButtons) {
                if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) { RebornSounds.uiClick(); b.act.run(); return true; }
            }
            closePanel();
        }

        if (e.button() == 0) {
            // Focus recherche : poser le focus au niveau de l'écran (sinon charTyped ne reçoit rien).
            if (mx >= search.getX() - 2 && mx < search.getX() + search.getWidth() + 2
                    && my >= top - 2 && my < top + 14) {
                search.mouseClicked(e, dbl);
                setFocused(search);
                search.setFocused(true);
                return true;
            }
            if (mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize) { RebornSounds.uiClick(); onClose(); return true; }
            if (my >= tabsY - 2 && my <= tabsY + 12) {
                if (mx >= tabEquipX0 && mx <= tabEquipX1) { tab = TAB_EQUIP; RebornSounds.charNav(); return true; }
                if (mx >= tabCosmX0 && mx <= tabCosmX1) { tab = TAB_COSM; RebornSounds.charNav(); return true; }
            }
            int c = chipAt(mx, my);
            if (c >= 0) { filter = c; RebornSounds.charNav(); return true; }
            if (equipBagSlotAt(mx, my)) {
                if (carriedStack != null && isBagStack(carriedStack)) { send("bag:equip:" + carriedRef); clearCarried(); RebornSounds.confirm(); }
                else RebornSounds.uiClick();
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null) {
                if (carriedStack != null) { send("cos:equip:" + carriedRef + ":" + cs.name()); clearCarried(); RebornSounds.confirm(); }
                else if (snap.equipped().get(cs) != null) { send("cos:unequip:" + cs.name()); RebornSounds.uiClick(); }
                return true;
            }
            String ref = slotAt(mx, my);
            if (ref != null) { handleSlotClick(ref); return true; }
            if (carriedStack != null) { send("drop:" + carriedRef); clearCarried(); RebornSounds.uiClick(); return true; }
            if (search.isFocused()) { search.setFocused(false); setFocused(null); }
            return true;
        }

        if (e.button() == 1) {
            String ref = slotAt(mx, my);
            if (ref != null && carriedStack == null) {
                ItemStack st = stackAt(ref);
                if (st != null && !st.isEmpty()) {
                    selRef = ref; selStack = st; selMeta = metaAt(ref);
                    anchorX = slotRefX(ref); anchorY = slotRefY(ref);
                    RebornSounds.uiClick();
                }
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null && snap.equipped().get(cs) != null) {
                // Ouvre le panneau détail du cosmétique équipé (bouton « Repositionner »).
                SlotItem it = snap.equipped().get(cs);
                int[] p = cosmPos(cs);
                selRef = "C" + cs.name(); selStack = it.toStack(); selMeta = it;
                anchorX = p[0]; anchorY = p[1];
                RebornSounds.uiClick();
                return true;
            }
            if (equipBagSlotAt(mx, my) && snap.bagItem() != null) { send("bag:unequip"); RebornSounds.uiClick(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    private void handleSlotClick(String ref) {
        closePanel();
        if (carriedStack == null) {
            ItemStack st = stackAt(ref);
            if (st != null && !st.isEmpty()) { carriedRef = ref; carriedStack = st.copy(); RebornSounds.uiClick(); }
        } else if (ref.equals(carriedRef)) {
            clearCarried(); RebornSounds.uiClick();
        } else {
            send("swap:" + carriedRef + ":" + ref); clearCarried(); RebornSounds.confirm();
        }
    }

    private void clearCarried() { carriedRef = null; carriedStack = null; }

    private void send(String cmd) {
        if (ClientPlayNetworking.canSend(InventoryPayload.ID)) ClientPlayNetworking.send(new InventoryPayload(cmd));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (search.isFocused()) { search.setFocused(false); return true; }
            if (selRef != null) { closePanel(); return true; }
            if (carriedStack != null) { clearCarried(); RebornSounds.uiClick(); return true; }
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (search.keyPressed(e)) return true;   // retour arrière, flèches…
            return super.keyPressed(e);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyInventory.matches(e)) {
            if (carriedStack != null) { clearCarried(); RebornSounds.uiClick(); return true; }
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        // Sans ce forward, on ne pouvait pas taper dans la barre de recherche.
        if (search != null && search.isFocused() && search.charTyped(event)) return true;
        return super.charTyped(event);
    }

    private static int clampI(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
