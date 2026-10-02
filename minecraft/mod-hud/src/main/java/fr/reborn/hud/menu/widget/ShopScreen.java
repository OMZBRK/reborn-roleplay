package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.shop.ShopData;
import fr.reborn.hud.menu.shop.ShopPayload;
import fr.reborn.hud.skin.CharacterCatalog;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.UiAmbience;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>Échoppe</b> (Boutique du menu ÉCHAP) — tenues et cartes, dans une échoppe de nuit :
 * noren indigo au blason 商, chōchin rouges qui se balancent, mur de planches. Le
 * personnage est présenté sur une estrade éclairée (rotation libre au clic droit maintenu),
 * le catalogue est un tableau d'étiquettes de papier en deux onglets : TENUES / CARTES.
 *
 * <p>Tenue : sélection → aperçu live sur son propre corps, ACHETER (si assez de ryo) puis
 * ÉQUIPER. Carte (objet « Carte — région », {@link ShopData#maps()}) : ACHETER donne l'objet
 * (C2S {@code buymap:<id>}), rachetable. Autoritaire côté serveur (canal
 * {@code reborn:shop}, ShinobiCore) : le client ne fait qu'afficher + demander.
 *
 * <p>Sons {@code reborn:shop.*} ({@code tools/ui-art/gen_shop_sfx.py}) : fūrin à l'entrée,
 * pièces au paiement, tissu à l'équipement, perle de boulier à la sélection, ambiance.
 */
public class ShopScreen extends Screen {

    private static final int TAB_OUTFITS = 0, TAB_MAPS = 1;

    private static final int CREAM = 0xFFFAEED6;
    private static final int GOLD = 0xFFF6CC78;
    private static final int LACQ = 0xFF5C1418;
    private static final int ROPE = 0xFFC8A05A;
    private static final int PAPER = 0xFFEADCB4;
    private static final int PAPER_SEL = 0xFFF6EAC6;
    private static final int PAPER_INK = 0xFF3C2A1C;
    private static final int PAPER_DIM = 0xFF8C7458;
    private static final int OWNED = 0xFF2E7D4A;

    private static final Identifier WALL = tex("wall"), NOREN = tex("noren"), CREST = tex("crest"),
        CHOCHIN = tex("chochin"), COIN = tex("coin"), STAMP = tex("stamp"), SCROLL = tex("scroll"), ROBE = tex("robe");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("reborn", "textures/gui/stats/glow.png");

    private static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/shop/" + n + ".png");
    }

    private final Screen parent;

    // Entrée de liste : une tenue du catalogue (ou l'entrée « Aucune » = torse nu, id ""),
    // ou une carte en vente (map = true, prix propre).
    private record Entry(String id, String name, boolean map, long price) {
        Entry(String id, String name) { this(id, name, false, 0L); }
    }
    private final List<Entry> outfits = new ArrayList<>();
    private int tab = TAB_OUTFITS;
    private int selected = 0;
    private int scroll = 0;
    private int hoveredRow = -1, lastHoveredRow = -1;

    private boolean prevHudHidden;
    private boolean captured = false;
    private boolean syncedFromState = false; // aligne sélection/aperçu quand l'état serveur arrive
    private float avatarSpin = 180f;          // rotation horizontale du modèle (clic droit maintenu)
    private final long openedAt = System.currentTimeMillis();
    private long seenToastAt;
    private String lastAction = "";

    // Géométrie (calculée dans le rendu).
    private int listX, listY, listW, listH, rowH = 20, tabsY, norenH;
    // Zones cliquables (x1,y1,x2,y2), posées au rendu, lues en mouseClicked.
    private int[] buyBtn, equipBtn, backBtn, tabOutfitsBtn, tabMapsBtn;

    public ShopScreen(Screen parent) {
        super(Component.literal("Boutique"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!captured && mc.gui != null) {
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            captured = true;
        }
        // « Aucune » + toutes les tenues du catalogue.
        outfits.clear();
        outfits.add(new Entry("", "Aucune (torse nu)"));
        for (CharacterCatalog.Asset a : CharacterCatalog.all("outfit")) {
            outfits.add(new Entry(a.id, a.name != null && !a.name.isBlank() ? a.name : a.id));
        }
        if (ClientPlayNetworking.canSend(ShopPayload.ID)) {
            ClientPlayNetworking.send(new ShopPayload("open"));
        }
        seenToastAt = ShopData.toastAt();
        if (System.currentTimeMillis() - openedAt < 200) {
            RebornSounds.playReborn("shop.open", 1.0f, 0.55f);
            UiAmbience.start("shop.ambience", 0.3f);
        }
        selectCurrent();
        preview();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (captured && mc.gui != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            captured = false;
        }
        UiAmbience.stop();
        // Restaure l'apparence réelle (au cas où on avait un aperçu non équipé).
        applyAppearance(ShopData.appearance());
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /* ------------------------------------------------------------ données */

    private List<Entry> list() {
        if (tab == TAB_OUTFITS) return outfits;
        List<Entry> maps = new ArrayList<>();
        for (ShopData.MapOffer m : ShopData.maps()) maps.add(new Entry(m.id(), m.name(), true, m.price()));
        return maps;
    }

    private Entry current() {
        List<Entry> all = list();
        return (selected >= 0 && selected < all.size()) ? all.get(selected) : null;
    }

    private String wornOutfit() {
        try { return SkinSpec.deserialize(ShopData.appearance()).outfitId; } catch (Exception e) { return ""; }
    }

    private long priceOf(Entry e) { return e.map ? e.price : ShopData.price(); }

    /* ---------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        ctx.blit(RenderPipelines.GUI_TEXTURED, WALL, 0, 0, 0f, 0f, this.width, this.height, 480, 270, 480, 270);
        // Noren : pans indigo répétés + blason au centre, léger mouvement d'air.
        norenH = this.height < 400 ? 28 : 40;
        for (int x = 0, k = 0; x < this.width; x += 64, k++) {
            int sway = Math.round((float) Math.sin(time * 1.4f + k * 0.8f) * 1.2f);
            ctx.blit(RenderPipelines.GUI_TEXTURED, NOREN, x, sway - (40 - norenH), 0f, 0f, 64, 40, 64, 40);
        }
        int cs = norenH < 40 ? 20 : 28;
        ctx.blit(RenderPipelines.GUI_TEXTURED, CREST, this.width / 2 - cs / 2, (norenH - cs) / 2, 0f, 0f, cs, cs, 28, 28, 28, 28);
        // Chōchin qui se balancent de part et d'autre.
        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? this.width * 0.045f + 12 : this.width * 0.955f - 12;
            float ang = (float) Math.sin(time * 1.1f + side * 2f) * 0.08f;
            int top = norenH + 2;
            blitGlow(ctx, cx, top + 26, 70, Colors.withAlpha(0xFFFF8A50, 0.35f + 0.05f * (float) Math.sin(time * 7f + side)));
            ctx.fill(Math.round(cx), norenH - 2, Math.round(cx) + 1, top + 4, 0xFF2A1E18);
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx, top + 4);
            ctx.pose().rotate(ang);
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHOCHIN, -11, 0, 0f, 0f, 22, 32, 22, 32);
            ctx.pose().popMatrix();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (!syncedFromState && ShopData.received()) {
            syncedFromState = true;
            selectCurrent();
            preview();
        }
        reactToServer();
        Font f = this.font;
        boolean compact = this.height < 400;
        float time = (System.currentTimeMillis() - openedAt) / 1000f;

        // ── Géométrie ──
        listW = compact ? 200 : 250;
        listX = this.width - listW - (compact ? 14 : 28);
        tabsY = norenH + (compact ? 8 : 14);
        listY = tabsY + 18;
        int footY = this.height - (compact ? 22 : 30);
        int detailH = compact ? 30 : 40;
        listH = footY - 8 - detailH - 8 - listY;
        rowH = compact ? 17 : 20;

        // ── Estrade + personnage (zone gauche) ──
        int stageW = listX - 20;
        int cx = stageW / 2 + 10;
        int floorY = (int) (this.height * (compact ? 0.86f : 0.84f));
        blitGlow(ctx, cx, floorY - this.height * 0.28f, this.height * 0.75f, 0x40FFC878);
        ctx.fillGradient(cx - 80, floorY - 4, cx + 80, floorY + 10, 0xFF5A3A24, 0xFF2A1A12);
        for (int k = 0; k < 3; k++) ctx.fill(cx - 80 + k * 2, floorY - 4 - k, cx + 80 - k * 2, floorY - 3 - k, 0xFF7A5232);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int size = (int) (this.height * 0.30f);
            int y0 = (int) (this.height * 0.16f);
            renderAvatar360(ctx, cx - size, y0, cx + size, floorY, size, avatarSpin, mc.player);
            Component hint = RebornFont.arcade("CLIC DROIT MAINTENU : TOURNER");
            drawScaled(ctx, f, hint, cx - f.width(hint) * 0.35f, floorY + 14, 0.7f, 0x99FAEED6);
        }

        // ── Enseigne (titre) + bourse ──
        int py = norenH + 6;
        Component title = RebornFont.arcade("ECHOPPE");
        Component sub = RebornFont.arcade("TENUES & CARTES");
        int pw = Math.max(f.width(title) * 13 / 10, Math.round(f.width(sub) * 0.7f)) + 26;
        int px = 14;
        ctx.fill(px + 2, py + 3, px + pw + 2, py + 33, 0x55000000);
        ctx.fill(px, py, px + pw, py + 30, LACQ);
        frame(ctx, px, py, pw, 30, GOLD);
        drawScaled(ctx, f, title, px + 13, py + 6, 1.3f, CREAM);
        drawScaled(ctx, f, sub, px + 13, py + 19, 0.7f, 0xFFE6B4A0);

        String bal = ShopData.received() ? fmtRyo(ShopData.ryo()) : "…";
        Component balC = RebornFont.arcade(bal + " RYO");
        int bw = f.width(balC) + 26, bx = listX + listW - bw;
        ctx.fill(bx, py - 2, bx + bw, py + 12, 0xD0281810);
        frame(ctx, bx, py - 2, bw, 14, 0xFFA07840);
        ctx.blit(RenderPipelines.GUI_TEXTURED, COIN, bx + 5, py + 0, 0f, 0f, 10, 10, 10, 10);
        ctx.text(f, balC, bx + 19, py + 1, GOLD, false);

        // ── Onglets suspendus ──
        int nOut = Math.max(0, outfits.size() - 1), nMaps = ShopData.maps().size();
        tabOutfitsBtn = tabTag(ctx, f, "TENUES (" + nOut + ")", listX, tabsY, tab == TAB_OUTFITS, mouseX, mouseY);
        tabMapsBtn = tabTag(ctx, f, "CARTES (" + nMaps + ")", tabOutfitsBtn[2] + 6, tabsY, tab == TAB_MAPS, mouseX, mouseY);

        // ── Tableau d'étiquettes ──
        ctx.fill(listX - 6, listY - 6, listX + listW + 6, listY + listH + 6, 0xE0201410);
        frame(ctx, listX - 6, listY - 6, listW + 12, listH + 12, 0xFF6E4A2E);
        List<Entry> all = list();
        int visible = Math.max(1, listH / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visible)));
        hoveredRow = -1;
        String worn = wornOutfit();
        if (all.isEmpty()) {
            Component e1 = Component.literal(tab == TAB_MAPS ? "Aucune carte en vente." : "Aucune tenue au catalogue.");
            ctx.text(f, e1, listX + (listW - f.width(e1)) / 2, listY + listH / 2 - 4, 0xFFB09070, false);
        }
        for (int row = 0; row < visible && (row + scroll) < all.size(); row++) {
            int idx = row + scroll;
            Entry e = all.get(idx);
            int ry = listY + row * rowH;
            boolean sel = idx == selected;
            boolean hov = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + rowH - 2;
            if (hov) hoveredRow = idx;
            int ox = sel ? 4 : hov ? 2 : 0;
            int tx0 = listX + ox, tx1 = listX + listW - 4 + ox;
            ctx.fill(tx0 + 1, ry + 2, tx1 + 1, ry + rowH - 1, 0x60000000);
            ctx.fill(tx0, ry + 1, tx1, ry + rowH - 2, sel ? PAPER_SEL : PAPER);
            frame(ctx, tx0, ry + 1, tx1 - tx0, rowH - 3, sel ? GOLD : 0xFF9C7E58);
            ctx.fill(tx0 + 3, ry + rowH / 2 - 1, tx0 + 5, ry + rowH / 2 + 1, 0xFF6E4A2E); // œillet
            ctx.blit(RenderPipelines.GUI_TEXTURED, e.map ? SCROLL : ROBE, tx0 + 8, ry + (rowH - 14) / 2, 0f, 0f, 14, 14, 14, 14);
            ctx.text(f, Component.literal(trim(f, e.name, listW - 110)), tx0 + 26, ry + (rowH - 8) / 2, PAPER_INK, false);
            // statut / prix
            boolean owned = !e.map && (e.id.isEmpty() || ShopData.owns(e.id));
            if (!e.map && e.id.equals(worn == null ? "" : worn)) {
                Component tg = RebornFont.arcade("PORTEE");
                drawScaled(ctx, f, tg, tx1 - 6 - f.width(tg) * 0.75f, ry + (rowH - 6) / 2f, 0.75f, OWNED);
            } else if (owned && !e.id.isEmpty()) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, STAMP, tx1 - 22, ry + (rowH - 18) / 2, 0f, 0f, 18, 18, 22, 22, 22, 22);
            } else if (!e.id.isEmpty() || e.map) {
                Component pc = Component.literal(fmtRyo(priceOf(e)));
                int pxr = tx1 - 6 - f.width(pc);
                ctx.text(f, pc, pxr, ry + (rowH - 8) / 2, 0xFF8C5A14, false);
                ctx.blit(RenderPipelines.GUI_TEXTURED, COIN, pxr - 13, ry + (rowH - 10) / 2, 0f, 0f, 10, 10, 10, 10);
            }
        }
        if (all.size() > visible) {
            int trackH = listH;
            int thumbH = Math.max(12, trackH * visible / all.size());
            int thumbY = listY + (trackH - thumbH) * scroll / Math.max(1, all.size() - visible);
            ctx.fill(listX + listW + 2, listY, listX + listW + 3, listY + trackH, 0x40FFFFFF);
            ctx.fill(listX + listW + 1, thumbY, listX + listW + 4, thumbY + thumbH, GOLD);
        }
        if (hoveredRow >= 0 && hoveredRow != lastHoveredRow) RebornSounds.playReborn("stats.hover", 1.2f, 0.15f);
        lastHoveredRow = hoveredRow;

        // ── Détail de l'article sélectionné ──
        Entry cur = current();
        int dy = listY + listH + 8;
        ctx.fill(listX - 6, dy, listX + listW + 6, dy + detailH, 0xE0201410);
        frame(ctx, listX - 6, dy, listW + 12, detailH, 0xFF6E4A2E);
        if (cur != null) {
            ctx.text(f, Component.literal(trim(f, cur.name, listW - 10)), listX, dy + 5, CREAM, false);
            String desc = cur.map ? "Objet : tiens-la en main (clic droit ou M) pour ouvrir la carte."
                : cur.id.isEmpty() ? "Retirer sa tenue." : "Tenue : apercu en direct sur ton personnage.";
            if (!compact) drawScaled(ctx, f, Component.literal(desc), listX, dy + 18, 0.75f, 0xFFB4A08A);
            if (!compact && cur.map) drawScaled(ctx, f, Component.literal("Rachetable si elle est perdue."), listX, dy + 27, 0.75f, 0xFF8C7A66);
        }

        // ── Boutons ──
        int bh = compact ? 15 : 18;
        boolean curOwned = cur != null && !cur.map && (cur.id.isEmpty() || ShopData.owns(cur.id));
        boolean wornNow = cur != null && !cur.map && cur.id.equals(worn == null ? "" : worn);
        backBtn = lacquerButton(ctx, f, 14, footY, 90, bh, "< RETOUR", true, false, mouseX, mouseY);
        buyBtn = null;
        equipBtn = null;
        if (cur != null && (cur.map || (!cur.id.isEmpty() && !ShopData.owns(cur.id)))) {
            long price = priceOf(cur);
            boolean can = ShopData.received() && ShopData.ryo() >= price;
            String label = (cur.map ? "ACHETER LA CARTE  " : "ACHETER  ") + fmtRyo(price);
            buyBtn = lacquerButton(ctx, f, listX - 6, footY, listW + 12, bh, label, can, true, mouseX, mouseY);
            ctx.blit(RenderPipelines.GUI_TEXTURED, COIN, buyBtn[2] - 16, footY + (bh - 10) / 2, 0f, 0f, 10, 10, 10, 10);
        } else if (cur != null && !cur.map) {
            equipBtn = lacquerButton(ctx, f, listX - 6, footY, listW + 12, bh, wornNow ? "DEJA PORTEE" : "EQUIPER",
                curOwned && !wornNow, true, mouseX, mouseY);
        }

        // ── Message du marchand (papier qui glisse) ──
        String toast = ShopData.toast();
        long age = System.currentTimeMillis() - ShopData.toastAt();
        if (toast != null && age < 3200) {
            float in = Math.min(1f, age / 180f), out = Math.max(0f, (age - 2800) / 400f);
            Component tc = Component.literal(toast);
            int tw = f.width(tc) + 24, th = 16;
            int tx = this.width / 2 - tw / 2;
            int ty = Math.round(norenH - th + (th + 10) * (in - out));
            ctx.fill(tx + 2, ty + 2, tx + tw + 2, ty + th + 2, 0x55000000);
            ctx.fill(tx, ty, tx + tw, ty + th, PAPER);
            frame(ctx, tx, ty, tw, th, 0xFF9C7E58);
            ctx.text(f, tc, tx + 12, ty + 4, PAPER_INK, false);
        }
    }

    /** Réagit aux réponses du serveur (nouveau message) par le bon son. */
    private void reactToServer() {
        long at = ShopData.toastAt();
        if (at == seenToastAt) return;
        seenToastAt = at;
        String t = ShopData.toast();
        if (t == null) return;
        if (t.startsWith("§a")) {
            RebornSounds.playReborn(lastAction.equals("equip") ? "shop.equip" : "shop.buy", 1.0f, 0.7f);
        } else if (t.startsWith("§c")) {
            RebornSounds.playReborn("stats.deny", 1.0f, 0.6f);
        }
    }

    private int[] tabTag(GuiGraphicsExtractor ctx, Font f, String label, int x, int y, boolean active, int mx, int my) {
        Component c = RebornFont.arcade(label);
        int w = f.width(c) + 14, h = 13;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.fill(x + w / 2, norenH, x + w / 2 + 1, y, ROPE);
        ctx.fill(x, y, x + w, y + h, active ? 0xFFAA1E22 : (hov ? 0xFF4A242A : 0xFF3C1E24));
        frame(ctx, x, y, w, h, active ? GOLD : 0xFF6E4646);
        ctx.text(f, c, x + 7, y + 3, active ? CREAM : 0xFFC8A0A0, false);
        return new int[]{x, y, x + w, y + h};
    }

    private int[] lacquerButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h, String label,
                                boolean enabled, boolean primary, int mx, int my) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = !enabled ? 0xFF2C1812 : primary ? (hov ? 0xFFC02A30 : 0xFFAA1E22) : (hov ? 0xFF7A2A30 : LACQ);
        if (enabled && primary && hov) blitGlow(ctx, x + w / 2f, y + h / 2f, w + 30, 0x55FFBE6E);
        ctx.fill(x, y, x + w, y + h, fill);
        frame(ctx, x, y, w, h, enabled ? (primary ? GOLD : 0xFFA06E50) : 0xFF4A2E22);
        Component c = RebornFont.arcade(label);
        ctx.text(f, c, x + (w - f.width(c)) / 2, y + (h - 7) / 2, enabled ? CREAM : 0xFF7A5E4A, false);
        return new int[]{x, y, x + w, y + h};
    }

    private void blitGlow(GuiGraphicsExtractor ctx, float cx, float cy, float size, int argb) {
        int s = Math.round(size);
        if (s < 2) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - s / 2f), Math.round(cy - s / 2f),
            0f, 0f, s, s, 64, 64, 64, 64, argb);
    }

    private static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        DrawHelpers.outlinedRect(ctx, x, y, w, h, 0, color);
    }

    private static void drawScaled(GuiGraphicsExtractor ctx, Font f, Component c, float x, float y, float s, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(s, s);
        ctx.text(f, c, 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    private static String fmtRyo(long v) {
        return String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ');
    }

    /* ------------------------------------------------------------ entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() == 0) {
            if (hit(backBtn, mx, my)) { RebornSounds.uiClick(); onClose(); return true; }
            if (hit(buyBtn, mx, my)) { buy(); return true; }
            if (hit(equipBtn, mx, my)) { equip(); return true; }
            if (hit(tabOutfitsBtn, mx, my)) { switchTab(TAB_OUTFITS); return true; }
            if (hit(tabMapsBtn, mx, my)) { switchTab(TAB_MAPS); return true; }
            if (hoveredRow >= 0 && hoveredRow < list().size()) {
                if (selected != hoveredRow) {
                    selected = hoveredRow;
                    RebornSounds.playReborn("shop.select", 1.0f, 0.6f);
                    preview();
                }
                return true;
            }
        }
        return super.mouseClicked(event, dbl);
    }

    private void switchTab(int t) {
        if (tab == t) return;
        tab = t;
        selected = 0;
        scroll = 0;
        RebornSounds.playReborn("stats.tab", 1.0f, 0.5f);
        if (t == TAB_OUTFITS) selectCurrent();
        preview();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list().size() - Math.max(1, listH / rowH))));
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dX, double dY) {
        // Clic droit maintenu = rotation horizontale libre du modèle (comme le créateur).
        if (event.button() == 1) { avatarSpin += (float) dX; return true; }
        return super.mouseDragged(event, dX, dY);
    }

    /**
     * Rend le perso avec rotation horizontale LIBRE (360°, dos visible) — copie du
     * créateur : on reconstruit l'{@link net.minecraft.client.renderer.entity.state.EntityRenderState}
     * et on impose {@code bodyRot = 180 − spin} (la méthode vanilla borne à ±31°).
     */
    private static void renderAvatar360(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1,
                                        int scale, float spinDeg, net.minecraft.client.player.LocalPlayer player) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        net.minecraft.client.renderer.entity.EntityRenderer renderer =
            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        net.minecraft.client.renderer.entity.state.EntityRenderState state =
            renderer.createRenderState(player, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState ls) {
            ls.bodyRot = 180.0f - spinDeg;
            ls.yRot = 0.0f;
            ls.xRot = 0.0f;
            ls.boundingBoxWidth = ls.boundingBoxWidth / ls.scale;
            ls.boundingBoxHeight = ls.boundingBoxHeight / ls.scale;
            ls.scale = 1.0f;
        }
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        org.joml.Quaternionf camOrient = new org.joml.Quaternionf();
        pose.mul(camOrient);
        org.joml.Vector3f translate = new org.joml.Vector3f(0.0f, state.boundingBoxHeight / 2.0f, 0.0f);
        ctx.entity(state, (float) scale, translate, pose, camOrient, x0, y0, x1, y1);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        int n = list().size();
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_UP && n > 0) { selected = Math.max(0, selected - 1); ensureVisible(); preview(); RebornSounds.playReborn("shop.select", 1.0f, 0.5f); return true; }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN && n > 0) { selected = Math.min(n - 1, selected + 1); ensureVisible(); preview(); RebornSounds.playReborn("shop.select", 1.0f, 0.5f); return true; }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) { switchTab(tab == TAB_OUTFITS ? TAB_MAPS : TAB_OUTFITS); return true; }
        return super.keyPressed(event);
    }

    private void ensureVisible() {
        int visible = Math.max(1, listH / rowH);
        if (selected < scroll) scroll = selected;
        else if (selected >= scroll + visible) scroll = selected - visible + 1;
    }

    private void buy() {
        Entry e = current();
        if (e == null || !ClientPlayNetworking.canSend(ShopPayload.ID)) { RebornSounds.playReborn("stats.deny", 1f, 0.6f); return; }
        if (!ShopData.received() || ShopData.ryo() < priceOf(e)) { RebornSounds.playReborn("stats.deny", 1f, 0.6f); return; }
        if (e.map) {
            lastAction = "buymap";
            ClientPlayNetworking.send(new ShopPayload("buymap:" + e.id));
            return;
        }
        if (e.id.isEmpty() || ShopData.owns(e.id)) return;
        lastAction = "buy";
        ClientPlayNetworking.send(new ShopPayload("buy:" + e.id));
    }

    private void equip() {
        Entry e = current();
        if (e == null || e.map) return;
        if (!e.id.isEmpty() && !ShopData.owns(e.id)) return; // pas possédée
        if (!ClientPlayNetworking.canSend(ShopPayload.ID)) return;
        SkinSpec spec = SkinSpec.deserialize(ShopData.appearance());
        spec.outfitId = e.id;
        lastAction = "equip";
        ClientPlayNetworking.send(new ShopPayload("equip:" + e.id + "\n" + spec.serialize()));
    }

    /** Applique l'aperçu de la tenue sélectionnée sur le corps du joueur (local). */
    private void preview() {
        Entry e = current();
        if (e == null || e.map) { applyAppearance(ShopData.appearance()); return; }
        SkinSpec spec = SkinSpec.deserialize(ShopData.appearance());
        spec.outfitId = e.id;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) RebornSkins.applySpec(mc.player.getUUID(), spec);
    }

    private void applyAppearance(String blob) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (blob == null || blob.isBlank()) RebornSkins.clear(mc.player.getUUID());
        else RebornSkins.applySpec(mc.player.getUUID(), SkinSpec.deserialize(blob));
    }

    /** Sélectionne l'entrée correspondant à la tenue actuellement portée. */
    private void selectCurrent() {
        if (tab != TAB_OUTFITS) return;
        String cur = wornOutfit();
        for (int i = 0; i < outfits.size(); i++) {
            if (outfits.get(i).id.equals(cur == null ? "" : cur)) { selected = i; break; }
        }
        ensureVisible();
    }

    private static boolean hit(int[] b, int mx, int my) {
        return b != null && mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3];
    }

    private static String trim(Font f, String s, int maxW) {
        if (s == null) return "";
        if (f.width(s) <= maxW) return s;
        String e = s;
        while (e.length() > 1 && f.width(e + "…") > maxW) e = e.substring(0, e.length() - 1);
        return e + "…";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
