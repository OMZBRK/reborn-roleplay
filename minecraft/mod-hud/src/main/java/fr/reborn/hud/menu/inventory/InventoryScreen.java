package fr.reborn.hud.menu.inventory;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.ui.style.IconTextures;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
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
 * <b>Sacoche « inrō »</b> — l'inventaire RP dans la DA Reborn (carte, fiche, Échap) : nuit sur le
 * village, plaque laquée suspendue + onglets pendus, cadres laque/or, bande de parchemin.
 *
 * <p>Signature : un <b>inrō</b> (boîte laquée à étages portée à la ceinture). Chaque étage est un
 * filtre (Tout / Sac / Divers) ; l'étage actif glisse vers le <b>plateau</b> laqué rouge qui contient
 * le sac. Le cordon porte une perle (ojime) et un netsuke. La barre 1-9 est une <b>obi</b> de soie.
 * Gauche : personnage sur un socle laqué, cosmétiques / sac sur des plaques à coins dorés.
 *
 * <p>Logique inchangée : clic gauche = prendre / poser, clic droit = panneau d'actions,
 * clic hors cases avec un objet en main = jeter. Textures {@code textures/gui/sacoche/*}
 * ({@code tools/ui-art/gen_sacoche_art.py}), sons {@code sounds/sacoche/*}.
 */
public class InventoryScreen extends Screen {

    private static final int F_ALL = 0, F_SAC = 1, F_DIVERS = 2;
    private static final String[] FILTER_NAME = {"TOUT", "SAC", "DIVERS"};
    private static final int TAB_EQUIP = 0, TAB_COSM = 1;

    // Palette DA Reborn.
    private static final int GOLD = 0xFFF6CC78, GOLD_D = 0xFFAA8034, CREAM = 0xFFFAEED6, LACQ = 0xFF5C1418,
        BLACK_L = 0xFF0E0A0C, SHINE = 0xFF46383C, MUTED = 0xFFC8B4A0;

    private static Identifier tex(String p) { return Identifier.fromNamespaceAndPath("reborn", "textures/gui/" + p + ".png"); }
    private static final Identifier SKY = tex("stats/sky_night"), MOON = tex("stats/moon"), MOUNTAINS = tex("stats/mountains"),
        VILLAGE = tex("stats/village"), WINDOWS = tex("stats/windows"), GLOW = tex("stats/glow"),
        TIER_ON = tex("sacoche/tier_on"), TIER_OFF = tex("sacoche/tier_off"), NETSUKE = tex("sacoche/netsuke"),
        OJIME = tex("sacoche/ojime"), OBI = tex("sacoche/obi"), PEDESTAL = tex("sacoche/pedestal");

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

    private String hoverRef = null, lastHoverRef = null;
    private CosmeticSlot hoverCosm = null;
    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = openedAt;
    private boolean closing;
    private final float[] tierAnim = new float[FILTER_NAME.length];

    // Layout (coordonnées écran GUI).
    private int margin, top, bottom;
    private int leftX, leftW;
    private int inroX, inroW = 52, tierH = 38, tierGap = 6, tierY0;
    private int trayX, trayY, trayW, trayH;
    private int gridX, gridY, cell, gap = 4, gridCols = 9, hotbarY, obiX, obiY, obiW, obiH;
    private int cosmLeftX, cosmRightX, cosmY0, cosmSize = 22, cosmGap = 8;
    private static final int COSM_ROWS = 5;
    private int modelX1, modelY1, modelX2, modelY2;
    private int closeX, closeY, closeSize = 14;
    private int plateX, plateW = 168, plateH = 28, tabsY;
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

    /** Met à jour l'écran avec le nouvel état sans le recréer (filtre, onglet, recherche, objet en main conservés). */
    public void refresh() {
        loadSnapshot();
        if (selRef != null && stackAt(selRef) == null) closePanel();
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        }
        search = new EditBox(this.font, 0, 0, 120, 12, Component.literal("search"));
        search.setMaxLength(48);
        search.setHint(Component.literal("Chercher…"));
        search.setBordered(false);
        search.setTextColor(CREAM);
        search.setValue(lastSearch);
        search.setResponder(v -> lastSearch = v);
        this.addRenderableWidget(search);
        for (int i = 0; i < tierAnim.length; i++) tierAnim[i] = i == filter ? 1f : 0f;
        send("open");
        RebornSounds.playReborn("sacoche.open", 1.0f, 0.55f);
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
    public void onClose() {
        if (!closing) { closing = true; RebornSounds.playReborn("sacoche.close", 1.0f, 0.5f); }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────── Layout ───────────
    private void layout() {
        margin = Math.max(12, (int) (this.width * 0.03f));
        plateX = (this.width - plateW) / 2;
        tabsY = 8 + plateH + 6;
        top = tabsY + 22;
        bottom = this.height - 22;
        closeX = this.width - margin - closeSize;
        closeY = 10;

        // Bloc central compact : [personnage + plaques] [inrō] [plateau ajusté au sac], centré à l'écran.
        leftW = clampI((int) (this.width * 0.26f), 168, 230);
        int rows = Math.max(3, bagRows);
        int availTrayW = this.width - 2 * margin - leftW - 14 - inroW - 30;
        cell = clampI((availTrayW - 40 - (gridCols - 1) * gap) / gridCols, 18, 30);
        // Hauteur : le plateau + l'obi doivent tenir entre l'en-tête et le bas.
        int maxH = bottom - top;
        while (cell > 18 && (30 + rows * (cell + gap) - gap + 26) + 10 + (cell + 10) > maxH) cell--;
        int gridW = gridCols * cell + (gridCols - 1) * gap;
        trayW = gridW + 40;
        trayH = 30 + rows * (cell + gap) - gap + 26;
        obiH = cell + 10;
        cosmSize = clampI((maxH - 30) / COSM_ROWS - cosmGap, 18, 28);
        int colH = COSM_ROWS * (cosmSize + cosmGap) - cosmGap;
        int blockH = Math.max(trayH + 10 + obiH, Math.max(colH + 24, FILTER_NAME.length * (tierH + tierGap) + 40));
        int blockW = leftW + 14 + inroW + 30 + trayW;
        int blockTop = top + Math.max(0, (maxH - blockH) / 2);
        leftX = Math.max(margin, (this.width - blockW) / 2);
        inroX = leftX + leftW + 14;
        trayX = inroX + inroW + 30;
        trayY = blockTop;
        gridX = trayX + 20;
        gridY = trayY + 30;
        obiX = trayX - 20;
        obiW = trayW + 20;
        obiY = trayY + trayH + 10;
        hotbarY = obiY + 5;
        tierY0 = trayY + 4;

        // Gauche : deux colonnes de plaques autour du personnage, socle sous ses pieds.
        cosmY0 = blockTop + (blockH - colH) / 2;
        cosmLeftX = leftX + 4;
        cosmRightX = leftX + leftW - 4 - cosmSize;
        modelX1 = cosmLeftX + cosmSize + 10;
        modelX2 = cosmRightX - 10;
        modelY1 = blockTop;
        modelY2 = blockTop + blockH - 14;

        // Recherche : en tête du plateau.
        search.setX(trayX + 14);
        search.setY(trayY + 9);
        search.setWidth(Math.min(150, trayW / 2));
    }

    private int hotbarX(int i) {
        int w = 9 * cell + 8 * gap;
        return obiX + (obiW - w) / 2 + i * (cell + gap);
    }

    // ─────────── Accès items ───────────
    private ItemStack stackAt(String ref) {
        if (ref == null || ref.length() < 2) return null;
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

    private int itemCount() {
        int n = 0;
        if (snap.bag() != null) for (int i = 0; i < Math.min(extraSlots, snap.bag().length); i++) if (snap.bag()[i] != null) n++;
        return n;
    }

    // ─────────── Rendu ───────────
    /** Fond : la nuit sur le village (mêmes calques que la fiche shinobi), voilée pour la lisibilité. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int w = this.width, h = this.height;
        float t = (System.currentTimeMillis() - openedAt) / 1000f;
        ctx.blit(RenderPipelines.GUI_TEXTURED, SKY, 0, 0, 0f, 0f, w, h, 480, 270, 480, 270);
        int ms = 34, mx = (int) (w * 0.88f), my = (int) (h * 0.06f);
        glow(ctx, mx + ms / 2f, my + ms / 2f, ms * 3, Colors.withAlpha(0xFFFFECC8, 0.10f + 0.04f * (float) Math.sin(t * 0.8f)));
        ctx.blit(RenderPipelines.GUI_TEXTURED, MOON, mx, my, 0f, 0f, ms, ms, 40, 40, 40, 40, 0xFFF8EED2);
        int mh = (int) (h * 0.30f), mtop = h - (int) (h * 0.48f);
        ctx.blit(RenderPipelines.GUI_TEXTURED, MOUNTAINS, 0, mtop, 0f, 0f, w, mh, 480, 120, 480, 120, 0xFF1E162E);
        int vh = (int) (h * 0.20f), vy = h - vh;
        ctx.blit(RenderPipelines.GUI_TEXTURED, VILLAGE, 0, vy, 0f, 0f, w, vh, 480, 80, 480, 80, 0xFF120C18);
        float flick = 0.85f + 0.15f * (float) Math.sin(t * 4.3f);
        ctx.blit(RenderPipelines.GUI_TEXTURED, WINDOWS, 0, vy, 0f, 0f, w, vh, 480, 80, 480, 80, Colors.withAlpha(0xFFBE6E38, flick));
        ctx.fill(0, 0, w, h, 0x5A08040E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (InventoryData.fromServer()) {
            InventoryData.Snapshot cur = InventoryData.get();
            if (cur != snap) refresh();
        }
        layout();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float time = (now - openedAt) / 1000f;
        float appear = ease(Math.min(1f, (now - openedAt) / 260f));
        for (int i = 0; i < tierAnim.length; i++) tierAnim[i] += ((i == filter ? 1f : 0f) - tierAnim[i]) * Math.min(1f, dt * 12f);

        Font f = this.font;
        hoverRef = slotAt(mouseX, mouseY);
        hoverCosm = null;
        if (hoverRef != null && !hoverRef.equals(lastHoverRef)) RebornSounds.playReborn("sacoche.hover", 1.0f, 0.18f);
        lastHoverRef = hoverRef;

        drawHeader(ctx, f, mouseX, mouseY);

        // Gauche : socle + personnage + plaques.
        ctx.blit(RenderPipelines.GUI_TEXTURED, PEDESTAL, (modelX1 + modelX2) / 2 - 48, modelY2 - 4, 0f, 0f, 96, 16, 96, 16);
        drawPreview(ctx, mouseX, mouseY);
        if (tab == TAB_COSM) drawCosmetics(ctx, f, mouseX, mouseY);
        else drawEquip(ctx, f, mouseX, mouseY);

        // Inrō + plateau + obi : glissent légèrement à l'ouverture.
        int slideX = Math.round((1f - appear) * 24);
        ctx.pose().pushMatrix();
        ctx.pose().translate(slideX, 0);
        drawInro(ctx, f, time, mouseX - slideX, mouseY);
        drawTray(ctx, f, mouseX - slideX, mouseY);
        drawObi(ctx, f, mouseX - slideX, mouseY);
        ctx.pose().popMatrix();
        search.extractRenderState(ctx, mouseX, mouseY, delta);

        if (selRef != null && selStack != null) drawDetailPanel(ctx, f, mouseX, mouseY);

        if (carriedStack != null) {
            ctx.item(carriedStack, mouseX - 8, mouseY - 8);
            ctx.itemDecorations(f, carriedStack, mouseX - 8, mouseY - 8);
        }
        if (carriedStack == null && selRef == null) {
            if (hoverCosm != null) {
                ctx.setTooltipForNextFrame(f, Component.literal(hoverCosm.label), mouseX, mouseY);
            } else if (hoverRef != null) {
                ItemStack tip = stackAt(hoverRef);
                if (tip != null) ctx.setTooltipForNextFrame(f, tip, mouseX, mouseY);
            }
        }
        drawHint(ctx, f);
    }

    // ── en-tête : plaque laquée suspendue + onglets pendus + croix ──
    private void drawHeader(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        int cx = this.width / 2;
        line(ctx, cx - 64, 0, cx - 56, 8, 0xFFC8A05A);
        line(ctx, cx + 64, 0, cx + 56, 8, 0xFFC8A05A);
        frame(ctx, plateX, 8, plateW, plateH, LACQ, GOLD);
        outline(ctx, plateX + 2, 10, plateW - 4, plateH - 4, 0xFF963C32);
        scaledCentered(ctx, f, "SACOCHE", cx, 11, 1.25f, CREAM);
        scaledCentered(ctx, f, identity(), cx, 25, 0.75f, 0xFFE6B4A0);

        String[] labels = {"EQUIPEMENT", "COSMETIQUE"};
        int[] tabs = {TAB_EQUIP, TAB_COSM};
        int gapT = 6, total = 0;
        int[] w = new int[2];
        for (int i = 0; i < 2; i++) { w[i] = arcW(f, labels[i], 0.75f) + 14; total += w[i]; }
        total += gapT;
        int x = cx - total / 2;
        for (int i = 0; i < 2; i++) {
            boolean act = tab == tabs[i];
            boolean hov = mx >= x && mx < x + w[i] && my >= tabsY && my < tabsY + 12;
            ctx.fill(x + w[i] / 2, 8 + plateH, x + w[i] / 2 + 1, tabsY, 0xFFC8A05A);
            frame(ctx, x, tabsY, w[i], 12, act ? 0xFFAA1E22 : (hov ? 0xFF4A2830 : 0xFF3C1E24), act ? GOLD : 0xFF6E4646);
            scaledCentered(ctx, f, labels[i], x + w[i] / 2, tabsY + 3, 0.75f, act ? CREAM : MUTED);
            if (i == 0) { tabEquipX0 = x; tabEquipX1 = x + w[i]; } else { tabCosmX0 = x; tabCosmX1 = x + w[i]; }
            x += w[i] + gapT;
        }
        // Croix de fermeture : petite plaque laquée.
        boolean h = mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize;
        frame(ctx, closeX, closeY, closeSize, closeSize, h ? 0xFFAA1E22 : BLACK_L, h ? GOLD : GOLD_D);
        int c = h ? CREAM : MUTED, ccx = closeX + closeSize / 2, ccy = closeY + closeSize / 2;
        for (int k = -3; k <= 3; k++) { ctx.fill(ccx + k, ccy + k, ccx + k + 1, ccy + k + 1, c); ctx.fill(ccx + k, ccy - k, ccx + k + 1, ccy - k + 1, c); }
    }

    private static String identity() {
        StatsData.Snapshot s = StatsData.get();
        StringBuilder b = new StringBuilder();
        if (s != null && s.name() != null && !s.name().isBlank() && !"?".equals(s.name())) b.append(s.name());
        else {
            var p = Minecraft.getInstance().player;
            if (p != null) b.append(p.getName().getString());
        }
        if (s != null && s.rank() != null && !s.rank().isBlank() && !"?".equals(s.rank())) b.append("  -  ").append(s.rank());
        if (s != null && s.village() != null && !s.village().isBlank()) b.append("  -  ").append(s.village());
        return fr.reborn.hud.menu.esc.EscData.arcadeSafe(b.toString(), 48);
    }

    private void drawPreview(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || modelX2 <= modelX1 || modelY2 <= modelY1) return;
        int size = Math.max(40, Math.min(150, (int) ((modelY2 - modelY1) * 0.42f)));
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(ctx,
            modelX1, modelY1, modelX2, modelY2, size, 0.0f, mouseX, (modelY1 + modelY2) / 2f, mc.player);
    }

    /** Plaque laquée à coins dorés (kanagu). state : 0 neutre, 1 survol, 2 occupé, 3 déposable. */
    private void plate(GuiGraphicsExtractor ctx, int x, int y, int s, int state) {
        ctx.fill(x + 1, y + 2, x + s + 1, y + s + 2, 0x80000000);
        int border = switch (state) { case 1, 3 -> GOLD; case 2 -> 0xFFD2A050; default -> GOLD_D; };
        frame(ctx, x, y, s, s, state == 3 ? 0xFF1E2A14 : BLACK_L, border);
        ctx.fill(x + 3, y + 2, x + s / 2 + 2, y + 3, SHINE);
        int k = state == 1 || state == 3 ? CREAM : GOLD;
        ctx.fill(x, y, x + 4, y + 1, k); ctx.fill(x, y, x + 1, y + 4, k);
        ctx.fill(x + s - 4, y, x + s, y + 1, k); ctx.fill(x + s - 1, y, x + s, y + 4, k);
        ctx.fill(x, y + s - 1, x + 4, y + s, k); ctx.fill(x, y + s - 4, x + 1, y + s, k);
        ctx.fill(x + s - 4, y + s - 1, x + s, y + s, k); ctx.fill(x + s - 1, y + s - 4, x + s, y + s, k);
        if (state == 1) glow(ctx, x + s / 2f, y + s / 2f, s * 2, Colors.withAlpha(0xFFFFD28C, 0.18f));
    }

    private int[] cosmPos(CosmeticSlot cs) {
        int row = 0;
        for (CosmeticSlot o : CosmeticSlot.values()) {
            if (o == cs) break;
            if (o.side == cs.side) row++;
        }
        int x = cs.side == 0 ? cosmLeftX : cosmRightX;
        return new int[] { x, cosmY0 + row * (cosmSize + cosmGap) };
    }

    private void cosmoSlot(GuiGraphicsExtractor ctx, Font f, int x, int y, SlotItem it, String kanji, int mx, int my, boolean droppable) {
        boolean hover = mx >= x && mx < x + cosmSize && my >= y && my < y + cosmSize;
        plate(ctx, x, y, cosmSize, droppable ? 3 : hover ? 1 : (it != null ? 2 : 0));
        if (it != null) ctx.item(it.toStack(), x + (cosmSize - 16) / 2, y + (cosmSize - 16) / 2);
        else if (kanji != null) {
            Component k = Component.literal(kanji);
            ctx.text(f, k, x + (cosmSize - f.width(k)) / 2, y + (cosmSize - 8) / 2, 0x80C8A05A, false);
        }
    }

    private void drawCosmetics(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        for (CosmeticSlot cs : CosmeticSlot.values()) {
            int[] p = cosmPos(cs);
            cosmoSlot(ctx, f, p[0], p[1], snap.equipped().get(cs), cs.kanji, mx, my, carriedStack != null);
            if (mx >= p[0] && mx < p[0] + cosmSize && my >= p[1] && my < p[1] + cosmSize) hoverCosm = cs;
        }
    }

    private void drawEquip(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        int x = cosmLeftX, y = cosmY0;
        cosmoSlot(ctx, f, x, y, snap.bagItem(), "袋", mx, my, carriedStack != null && isBagStack(carriedStack));
        scaledText(ctx, f, "SAC", x, y + cosmSize + 4, 0.75f, MUTED);
    }

    // ── inrō : étages = filtres ──
    private void drawInro(GuiGraphicsExtractor ctx, Font f, float time, int mx, int my) {
        int cordX = inroX + inroW / 2;
        int endY = tierY0 + FILTER_NAME.length * (tierH + tierGap);
        // Cordon (himo) derrière les étages, perle en haut, netsuke en bas.
        for (int y = trayY - 14; y < endY + 8; y += 2) {
            ctx.fill(cordX, y, cordX + 1, y + 1, 0xFFC4322C);
            ctx.fill(cordX + 1, y + 1, cordX + 2, y + 2, 0xFF8C1E1E);
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.4f);
        glow(ctx, cordX + 1, trayY - 10, 18, Colors.withAlpha(0xFFFFDC8C, 0.25f + 0.25f * pulse));
        ctx.blit(RenderPipelines.GUI_TEXTURED, OJIME, cordX - 4, trayY - 15, 0f, 0f, 10, 10, 10, 10);
        for (int i = 0; i < FILTER_NAME.length; i++) {
            int y = tierY0 + i * (tierH + tierGap);
            int dx = Math.round(tierAnim[i] * 18);
            boolean act = filter == i;
            boolean hov = mx >= inroX && mx < inroX + inroW + 18 && my >= y && my < y + tierH;
            ctx.fill(inroX + dx + 1, y + 2, inroX + dx + inroW + 1, y + tierH + 2, 0x90000000);
            ctx.blit(RenderPipelines.GUI_TEXTURED, act ? TIER_ON : TIER_OFF, inroX + dx, y, 0f, 0f, inroW, tierH, 52, 38);
            scaledCentered(ctx, f, FILTER_NAME[i], inroX + dx + inroW / 2, y + 9, 0.75f, act ? GOLD : (hov ? CREAM : 0xFFC8B08C));
            if (act) {   // glissière dorée vers le plateau
                ctx.fill(inroX + dx + inroW, y + tierH / 2, trayX, y + tierH / 2 + 1, GOLD_D);
            }
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, NETSUKE, cordX - 10, endY + 6, 0f, 0f, 22, 22, 22, 22);
    }

    // ── plateau laqué : recherche, compteur, grille du sac, poids ──
    private void drawTray(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        ctx.fill(trayX + 2, trayY + 3, trayX + trayW + 2, trayY + trayH + 3, 0x90000000);
        frame(ctx, trayX, trayY, trayW, trayH, BLACK_L, GOLD);
        kanagu(ctx, trayX, trayY, trayW, trayH, 6, GOLD);
        int ix = trayX + 5, iy = trayY + 5, iw = trayW - 10, ih = trayH - 10;
        ctx.fillGradient(ix, iy, ix + iw, iy + ih, 0xFF961A1C, 0xFF600E12);
        ctx.fill(ix, iy, ix + iw, iy + 1, 0xFFBE3C38);
        // recherche : simple filet sous le champ
        ctx.fill(search.getX() - 2, trayY + 21, search.getX() + search.getWidth(), trayY + 22, 0xFFC86E5A);
        String count = extraSlots > 0 ? itemCount() + " / " + extraSlots + "  -  " + tierDisplay : "AUCUN SAC";
        String cap = fr.reborn.hud.menu.esc.EscData.arcadeSafe(count, 40);
        scaledText(ctx, f, cap, trayX + trayW - 12 - arcW(f, cap, 0.75f), trayY + 11, 0.75f, GOLD);

        if (extraSlots > 0) {
            for (int i = 0; i < extraSlots; i++) {
                ItemStack st = stackAt("B" + i);
                SlotItem meta = snap.bag() != null && i < snap.bag().length ? snap.bag()[i] : null;
                drawCell(ctx, f, bagX(i), bagY(i), ("B" + i).equals(carriedRef) ? null : st, meta,
                    hoverRef != null && hoverRef.equals("B" + i), ("B" + i).equals(selRef), false);
            }
        } else {
            drawNoBagNotice(ctx, f);
        }
        drawWeight(ctx, f);
    }

    /** Case du plateau : creux de laque rouge, liseré doré ; survol = halo. */
    private void drawCell(GuiGraphicsExtractor ctx, Font f, int x, int y, ItemStack st, SlotItem meta, boolean hover, boolean sel, boolean belt) {
        int fill = belt ? 0xFF101430 : (sel ? 0xFF7A1A1E : 0xFF460A0E);
        ctx.fill(x, y, x + cell, y + cell, fill);
        ctx.fill(x, y, x + cell, y + 1, belt ? 0xFF080A1C : 0xFF28060A);
        ctx.fill(x, y, x + 1, y + cell, belt ? 0xFF080A1C : 0xFF28060A);
        outline(ctx, x, y, cell, cell, hover ? 0xFFFFECBE : (belt ? 0xFFD2B064 : 0xFFB0803C));
        if (hover) glow(ctx, x + cell / 2f, y + cell / 2f, cell * 2, Colors.withAlpha(0xFFFFD28C, 0.22f));
        if (st != null && !st.isEmpty()) {
            int ix = x + (cell - 16) / 2, iy = y + (cell - 16) / 2;
            ctx.item(st, ix, iy);
            ctx.itemDecorations(f, st, ix, iy);
            if (meta != null && meta.rarity != null)
                ctx.fill(x + 2, y + cell - 3, x + cell - 2, y + cell - 2, Colors.withAlpha(rarityColor(meta.rarity), 0.95f));
            if (dimmed(st, meta)) ctx.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, 0x99200408);
        }
    }

    private void drawWeight(GuiGraphicsExtractor ctx, Font f) {
        double cur = snap.curWeight(), max = snap.maxWeight();
        double ratio = max > 0 ? cur / max : 0;
        float r = (float) Math.min(1.0, ratio);
        int y = trayY + trayH - 14, x0 = trayX + 14;
        scaledText(ctx, f, "POIDS", x0, y - 2, 0.75f, 0xFFF0C8AA);
        int lx = x0 + 34, lw = Math.max(40, Math.min(180, trayW / 2 - 40));
        ctx.fill(lx, y + 1, lx + lw, y + 2, 0xFF782824);
        int fw = Math.round(lw * r);
        int col = ratio > 1.0 ? 0xFFFF5A4A : GOLD;
        if (fw > 0) ctx.fill(lx, y, lx + fw, y + 2, col);
        String amt = String.format(Locale.ROOT, "%.1f / %.1f KG", cur, max);
        scaledText(ctx, f, amt, lx + lw + 8, y - 2, 0.75f, ratio > 1.0 ? 0xFFFF7A6A : 0xFFF0C8AA);
    }

    private void drawNoBagNotice(GuiGraphicsExtractor ctx, Font f) {
        int cx = trayX + trayW / 2, cy = trayY + trayH / 2 - 10;
        IconTextures.drawIcon(ctx, "slot_bag", cx - 14, cy - 30, 28);
        ctx.fill(cx - 18, cy + 2, cx + 18, cy + 3, GOLD_D);
        scaledCentered(ctx, f, "AUCUN SAC EQUIPE", cx, cy + 10, 1f, GOLD);
        Component l1 = Component.literal("Portez une sacoche, une bandoulière ou un sac");
        Component l2 = Component.literal("pour débloquer de l'espace de rangement.");
        ctx.text(f, l1, cx - f.width(l1) / 2, cy + 26, 0xFFE6C8B4, false);
        ctx.text(f, l2, cx - f.width(l2) / 2, cy + 37, 0xFFB49682, false);
    }

    // ── obi : ceinture de soie, barre 1-9 ──
    private void drawObi(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        ctx.fill(obiX + 2, obiY + 3, obiX + obiW + 2, obiY + obiH + 3, 0x90000000);
        for (int x = obiX; x < obiX + obiW; x += 48) {
            int w = Math.min(48, obiX + obiW - x);
            ctx.blit(RenderPipelines.GUI_TEXTURED, OBI, x, obiY, 0f, 0f, w, obiH, w, 30, 48, 30);
        }
        for (int i = 0; i < 9; i++) {
            ItemStack st = stackAt("H" + i);
            int x = hotbarX(i);
            drawCell(ctx, f, x, hotbarY, ("H" + i).equals(carriedRef) ? null : st, null,
                hoverRef != null && hoverRef.equals("H" + i), ("H" + i).equals(selRef), true);
            scaledText(ctx, f, String.valueOf(i + 1), x + 2, hotbarY + cell - 6, 0.6f, 0xC0E6DCC8);
        }
    }

    private void drawHint(GuiGraphicsExtractor ctx, Font f) {
        String s = carriedStack != null
            ? "CLIC : POSER     CLIC DEHORS : JETER     ECHAP : ANNULER"
            : "CLIC : PRENDRE / POSER     CLIC DROIT : ACTIONS     ETAGES : FILTRES     ECHAP : FERMER";
        int w = arcW(f, s, 0.75f) + 24, x = (this.width - w) / 2, y = this.height - 16;
        ctx.fill(x, y, x + w, y + 12, 0xF0EADCB6);
        outline(ctx, x, y, w, 12, 0xFF8C6E48);
        scaledCentered(ctx, f, s, this.width / 2, y + 3, 0.75f, 0xFF503C28);
    }

    // ─────────── Panneau détail (ancré sur l'objet) ───────────
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

        ctx.fill(px + 2, py + 3, px + w + 2, py + h + 3, 0x90000000);
        frame(ctx, px, py, w, h, 0xF60E0A0C, GOLD);
        kanagu(ctx, px, py, w, h, 5, GOLD);
        ctx.fill(px + 4, py + 2, px + w / 2, py + 3, SHINE);

        int tx = px + pad, ty = py + pad;
        if (selStack != null) ctx.item(selStack, px + w - pad - 18, py + pad);
        ctx.text(f, Component.literal(name), tx, ty, CREAM, false);
        int nw = f.width(name);
        if (weight > 0) ctx.text(f, Component.literal("  " + String.format(Locale.ROOT, "%.2f kg", weight)), tx + nw, ty, MUTED, false);
        ty += 12;
        if (rarity != null) { ctx.text(f, Component.literal(rarity), tx, ty, rarityColor(rarity), false); ty += 11; }
        if (!descLines.isEmpty()) {
            ty += 6;
            scaledText(ctx, f, "DESCRIPTION", tx, ty, 0.75f, GOLD);
            ty += 9;
            for (String l : descLines) { ctx.text(f, Component.literal(l), tx, ty, MUTED, false); ty += 9; }
        }
        int sepY = py + h - pad - actH + 2;
        ctx.fill(tx, sepY, px + w - pad, sepY + 1, GOLD_D);

        int ax = tx, ay = sepY + 5;
        for (Btn b : panelButtons) {
            boolean iconOnly = b.label.isEmpty();
            int lblW = iconOnly ? 0 : f.width(b.label) + 4;
            int bw = 14 + lblW + 4;
            b.x = ax; b.y = ay - 3; b.w = bw; b.h = 16;
            boolean hov = mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h;
            if (hov) frame(ctx, b.x, b.y, b.w, b.h, Colors.withAlpha(b.accent, 0.25f), GOLD_D);
            IconTextures.drawIcon(ctx, b.icon, ax + 2, ay - 1, 12);
            if (!iconOnly) ctx.text(f, Component.literal(b.label), ax + 18, ay, hov ? 0xFFFFFFFF : CREAM, false);
            ax += bw + 6;
        }
    }

    private void buildPanelButtons() {
        panelButtons.clear();
        if (selRef == null) return;
        if (selRef.charAt(0) == 'C') {
            final CosmeticSlot cs = CosmeticSlot.fromName(selRef.substring(1));
            final SlotItem cosmItem = cs != null ? snap.equipped().get(cs) : null;
            panelButtons.add(new Btn("Repositionner", "act_equip", GOLD, () -> {
                Minecraft mc = Minecraft.getInstance();
                closePanel();
                if (cs == null) return;
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
        if (al != null) panelButtons.add(new Btn(al, "act_leaf", GOLD, () -> { send("use:" + ref); closePanel(); }));
        if (isBagStack(selStack)) panelButtons.add(new Btn("Équiper", "act_equip", GOLD, () -> { send("bag:equip:" + ref); closePanel(); }));
        panelButtons.add(new Btn("Drop", "act_drop", 0xFFAA1E22, () -> { send("drop:" + ref); closePanel(); }));
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
            default -> 0xFFE6B4A0;
        };
    }

    // ─────────── petits outils de dessin ───────────
    private static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        outline(ctx, x, y, w, h, border);
    }

    private static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c); ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void kanagu(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int s, int c) {
        ctx.fill(x, y, x + s, y + 2, c); ctx.fill(x, y, x + 2, y + s, c);
        ctx.fill(x + w - s, y, x + w, y + 2, c); ctx.fill(x + w - 2, y, x + w, y + s, c);
        ctx.fill(x, y + h - 2, x + s, y + h, c); ctx.fill(x, y + h - s, x + 2, y + h, c);
        ctx.fill(x + w - s, y + h - 2, x + w, y + h, c); ctx.fill(x + w - 2, y + h - s, x + w, y + h, c);
    }

    private static void line(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int c) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, n), y = y0 + (y1 - y0) * i / Math.max(1, n);
            ctx.fill(x, y, x + 1, y + 1, c);
        }
    }

    private static void glow(GuiGraphicsExtractor ctx, float cx, float cy, int size, int argb) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - size / 2f), Math.round(cy - size / 2f), 0f, 0f,
            size, size, 64, 64, 64, 64, argb);
    }

    private static int arcW(Font f, String s, float sc) { return Math.round(f.width(RebornFont.arcade(s)) * sc); }

    private static void scaledText(GuiGraphicsExtractor ctx, Font f, String s, float x, float y, float sc, int c) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(sc, sc);
        ctx.text(f, RebornFont.arcade(s), 0, 0, c, false);
        ctx.pose().popMatrix();
    }

    private static void scaledCentered(GuiGraphicsExtractor ctx, Font f, String s, float cx, float y, float sc, int c) {
        scaledText(ctx, f, s, cx - f.width(RebornFont.arcade(s)) * sc / 2f, y, sc, c);
    }

    private static float ease(float t) { return 1 - (float) Math.pow(1 - t, 3); }

    // ─────────── Géométrie ───────────
    private int bagX(int i) { return gridX + (i % gridCols) * (cell + gap); }
    private int bagY(int i) { return gridY + (i / gridCols) * (cell + gap); }

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

    private int tierAt(int mx, int my) {
        if (mx < inroX || mx >= inroX + inroW + 18) return -1;
        for (int i = 0; i < FILTER_NAME.length; i++) {
            int y = tierY0 + i * (tierH + tierGap);
            if (my >= y && my < y + tierH) return i;
        }
        return -1;
    }

    // ─────────── Interactions ───────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        layout();
        int mx = (int) e.x(), my = (int) e.y();

        if (selRef != null && e.button() == 0) {
            for (Btn b : panelButtons) {
                if (mx >= b.x && mx < b.x + b.w && my >= b.y && my < b.y + b.h) {
                    RebornSounds.playReborn("sacoche.place", 1.0f, 0.5f);
                    b.act.run();
                    return true;
                }
            }
            closePanel();
        }

        if (e.button() == 0) {
            if (mx >= search.getX() - 2 && mx < search.getX() + search.getWidth() + 2
                    && my >= search.getY() - 2 && my < search.getY() + 14) {
                search.mouseClicked(e, dbl);
                setFocused(search);
                search.setFocused(true);
                return true;
            }
            if (mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize) { onClose(); return true; }
            if (my >= tabsY && my <= tabsY + 12) {
                if (mx >= tabEquipX0 && mx <= tabEquipX1) { tab = TAB_EQUIP; RebornSounds.playReborn("sacoche.pick", 0.9f, 0.4f); return true; }
                if (mx >= tabCosmX0 && mx <= tabCosmX1) { tab = TAB_COSM; RebornSounds.playReborn("sacoche.pick", 0.9f, 0.4f); return true; }
            }
            int t = tierAt(mx, my);
            if (t >= 0) {
                if (t != filter) { filter = t; RebornSounds.playReborn("sacoche.tier", 1.0f, 0.55f); }
                return true;
            }
            if (equipBagSlotAt(mx, my)) {
                if (carriedStack != null && isBagStack(carriedStack)) {
                    send("bag:equip:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                } else RebornSounds.playReborn("sacoche.pick", 1.0f, 0.4f);
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null) {
                if (carriedStack != null) {
                    send("cos:equip:" + carriedRef + ":" + cs.name()); clearCarried(); RebornSounds.playReborn("sacoche.place", 1.0f, 0.6f);
                } else if (snap.equipped().get(cs) != null) {
                    send("cos:unequip:" + cs.name()); RebornSounds.playReborn("sacoche.pick", 1.0f, 0.5f);
                }
                return true;
            }
            String ref = slotAt(mx, my);
            if (ref != null) { handleSlotClick(ref); return true; }
            if (carriedStack != null) { send("drop:" + carriedRef); clearCarried(); RebornSounds.playReborn("sacoche.deny", 1.2f, 0.45f); return true; }
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
                    RebornSounds.playReborn("sacoche.pick", 1.1f, 0.45f);
                }
                return true;
            }
            CosmeticSlot cs = cosmeticAt(mx, my);
            if (cs != null && snap.equipped().get(cs) != null) {
                SlotItem it = snap.equipped().get(cs);
                int[] p = cosmPos(cs);
                selRef = "C" + cs.name(); selStack = it.toStack(); selMeta = it;
                anchorX = p[0]; anchorY = p[1];
                RebornSounds.playReborn("sacoche.pick", 1.1f, 0.45f);
                return true;
            }
            if (equipBagSlotAt(mx, my) && snap.bagItem() != null) { send("bag:unequip"); RebornSounds.playReborn("sacoche.pick", 1.0f, 0.5f); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    private void handleSlotClick(String ref) {
        closePanel();
        boolean belt = ref.charAt(0) == 'H';
        if (carriedStack == null) {
            ItemStack st = stackAt(ref);
            if (st != null && !st.isEmpty()) { carriedRef = ref; carriedStack = st.copy(); RebornSounds.playReborn("sacoche.pick", 1.0f, 0.5f); }
        } else if (ref.equals(carriedRef)) {
            clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f);
        } else {
            send("swap:" + carriedRef + ":" + ref); clearCarried();
            RebornSounds.playReborn(belt ? "sacoche.belt" : "sacoche.place", 1.0f, 0.6f);
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
            if (carriedStack != null) { clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f); return true; }
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (search.keyPressed(e)) return true;
            return super.keyPressed(e);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyInventory.matches(e)) {
            if (carriedStack != null) { clearCarried(); RebornSounds.playReborn("sacoche.place", 1.1f, 0.4f); return true; }
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (search != null && search.isFocused() && search.charTyped(event)) return true;
        return super.charTyped(event);
    }

    private static int clampI(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
