package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.shop.EchoppeArt;
import fr.reborn.hud.menu.shop.ShopData;
import fr.reborn.hud.menu.shop.ShopPayload;
import fr.reborn.hud.skin.CharacterCatalog;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.UiAmbience;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
 *
 * <p>Habillage au niveau de la fiche (DA §4.1, §6) : textures {@code textures/gui/echoppe/*}
 * ({@code tools/gen_echoppe_textures.py}, cf. {@link EchoppeArt}), 1 texel = 1 GUI, texte {@link DaKit}.
 */
public class ShopScreen extends Screen {

    private static final int TAB_OUTFITS = 0, TAB_MAPS = 1;

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
        EchoppeArt.mur(ctx, this.width, this.height);
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
        int W = this.width, H = this.height;

        // ── Géométrie ──
        norenH = compact ? 30 : 36;
        listW = compact ? 214 : Math.max(240, Math.min(290, Math.round(W * 0.28f)));
        listX = W - listW - (compact ? 18 : 30);
        int bourseY = norenH + (compact ? 4 : 6);
        tabsY = norenH + (compact ? 24 : 30);
        int panelY = tabsY + 24;
        rowH = compact ? 19 : 21;
        int btnH = 20;
        int footY = H - btnH - (compact ? 10 : 14);
        int detailH = compact ? 38 : 50;
        int detailY = footY - 8 - detailH;
        listY = panelY + 9;
        listH = detailY - 10 - listY - 9;
        listH = Math.max(rowH, listH - listH % rowH);

        // ── Scène : lumière, estrade, personnage ──
        int stageR = listX - 24;
        int cx = Math.max(110, stageR / 2 + 8);
        int pieds = H - EchoppeArt.SOL + 18;
        int etagereY = H - EchoppeArt.SOL - 46;
        if (cx - 104 >= 20 + 128) EchoppeArt.etagere(ctx, 20, etagereY);
        if (stageR - 12 - 128 >= cx + 104) EchoppeArt.etagere(ctx, stageR - 12 - 128, etagereY);
        EchoppeArt.halo(ctx, cx, pieds - H * 0.28f, Math.max(3, Math.round(H / 130f)), DaKit.argb(0xFFA850, 0.26f));
        EchoppeArt.halo(ctx, cx, pieds + 4, 3, DaKit.argb(0xFFC070, 0.30f));
        EchoppeArt.estrade(ctx, cx, pieds);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int size = (int) (H * 0.28f);
            // boîte centrée sur le milieu du corps (pieds − 0,9 × taille) : les pieds posent sur l'estrade
            int mid = pieds - Math.round(size * 0.9f);
            renderAvatar360(ctx, cx - size, mid - size, cx + size, mid + size, size, avatarSpin, mc.player);
        }

        // ── Chōchin, enseigne ──
        float flick = 0.85f + 0.15f * (float) Math.sin(time * 6.1f) * (float) Math.sin(time * 2.3f);
        float[] lx = W - listW < 420 ? new float[]{Math.min(stageR - 12, 196)}
            : new float[]{196, stageR - 12};
        for (float l : lx) EchoppeArt.halo(ctx, l, norenH + 24, 3, DaKit.argb(0xFF8A40, 0.34f * flick));
        DaKit.blit(ctx, EchoppeArt.ENSEIGNE, 12, norenH - 12, 0, 0, 156, 62, 156, 62);
        for (int i = 0; i < lx.length; i++) {
            float ang = (float) Math.sin(time * 1.1f + i * 2f) * 0.07f;
            EchoppeArt.fil(ctx, Math.round(lx[i]), norenH - 6, norenH + 2);
            EchoppeArt.chochin(ctx, lx[i], norenH + 2, ang, flick > 0.95f);
        }
        DaKit.arcX1(ctx, f, DaKit.arcade("Echoppe"), 90, norenH + 7, DaKit.CREME, 1, true);
        DaKit.inter7(ctx, f, DaKit.inter("Tenues et cartes"), 90, norenH + 21, DaKit.argb(0xE8DCC0, 0.75f), 1);

        // ── Bourse (ryo) ──
        String bal = ShopData.received() ? fmtRyo(ShopData.ryo()) + " ryo" : "…";
        int bw = Math.round(DaKit.largeur8(f, DaKit.inter(bal))) + 26;
        int bx = listX + listW - bw;
        EchoppeArt.fil(ctx, bx + 8, norenH - 4, bourseY + 1);
        EchoppeArt.fil(ctx, bx + bw - 9, norenH - 4, bourseY + 1);
        EchoppeArt.bourse(ctx, bx, bourseY, bw);
        EchoppeArt.ryo(ctx, bx + 6, bourseY + 3);
        DaKit.inter8(ctx, f, DaKit.inter(bal), bx + 20, bourseY + 8, DaKit.OR_CLAIR, 0);

        // ── Onglets pendus ──
        int nOut = Math.max(0, outfits.size() - 1), nMaps = ShopData.maps().size();
        tabOutfitsBtn = tabTag(ctx, f, "Tenues", nOut, listX, tabsY, tab == TAB_OUTFITS, mouseX, mouseY);
        tabMapsBtn = tabTag(ctx, f, "Cartes", nMaps, tabOutfitsBtn[2] + 8, tabsY, tab == TAB_MAPS, mouseX, mouseY);

        // ── Tableau d'étiquettes ──
        EchoppeArt.planche(ctx, listX - 10, panelY, listW + 20, listH + 18);
        List<Entry> all = list();
        int visible = Math.max(1, listH / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visible)));
        hoveredRow = -1;
        String worn = wornOutfit();
        if (all.isEmpty()) {
            DaKit.inter8(ctx, f, DaKit.inter(tab == TAB_MAPS ? "Aucune carte en vente." : "Aucune tenue au catalogue."),
                listX + listW / 2f, listY + listH / 2f, DaKit.NEUTRE_TXT, 1);
        }
        int tagW = listW - 10;
        for (int row = 0; row < visible && (row + scroll) < all.size(); row++) {
            int idx = row + scroll;
            Entry e = all.get(idx);
            int ry = listY + row * rowH;
            boolean sel = idx == selected;
            boolean hov = mouseX >= listX && mouseX < listX + tagW + 4 && mouseY >= ry && mouseY < ry + rowH - 2;
            if (hov) hoveredRow = idx;
            int ox = sel ? 4 : hov ? 2 : 0;
            int tx0 = listX + ox, th = 18, ty = ry + (rowH - th) / 2;
            EchoppeArt.etiquette(ctx, tx0, ty, tagW - ox, sel ? (hov ? 3 : 2) : (hov ? 1 : 0));
            EchoppeArt.icone14(ctx, e.map ? EchoppeArt.IC_CARTE : EchoppeArt.IC_ROBE, tx0 + 14, ty + 2);
            int right = tx0 + tagW - ox - 8;
            boolean owned = !e.map && (e.id.isEmpty() || ShopData.owns(e.id));
            boolean wornIt = !e.map && e.id.equals(worn == null ? "" : worn);
            int nameMax = right - (tx0 + 32) - 56;
            DaKit.inter9(ctx, f, DaKit.inter(DaKit.ellipse(f, e.name, nameMax)), tx0 + 32, ty + 9, DaKit.ENCRE, 0);
            if (wornIt) {
                EchoppeArt.icone14(ctx, EchoppeArt.IC_SCEAU_PORTE, right - 14, ty + 2);
                DaKit.inter7(ctx, f, DaKit.inter("portée"), right - 18, ty + 9, 0xFF2E6A4A, 2);
            } else if (owned && !e.id.isEmpty()) {
                EchoppeArt.icone14(ctx, EchoppeArt.IC_SCEAU_POSSEDE, right - 14, ty + 2);
            } else if (!e.id.isEmpty() || e.map) {
                boolean can = ShopData.received() && ShopData.ryo() >= priceOf(e);
                Component pc = DaKit.inter(fmtRyo(priceOf(e)));
                float pw = DaKit.largeur8(f, pc);
                DaKit.inter8(ctx, f, pc, right, ty + 9, can ? 0xFF6E4A14 : 0xFF9A3A30, 2);
                EchoppeArt.ryo(ctx, Math.round(right - pw - 13), ty + 4);
            }
        }
        if (all.size() > visible) {
            int rx = listX + listW + 1, trackH = listH;
            EchoppeArt.rail(ctx, rx, listY, trackH);
            int thumbH = Math.max(14, trackH * visible / all.size());
            int thumbY = listY + (trackH - thumbH) * scroll / Math.max(1, all.size() - visible);
            EchoppeArt.curseur(ctx, rx - 1, thumbY, thumbH);
        }
        if (hoveredRow >= 0 && hoveredRow != lastHoveredRow) RebornSounds.playReborn("stats.hover", 1.2f, 0.15f);
        lastHoveredRow = hoveredRow;

        // ── Détail de l'article sélectionné ──
        Entry cur = current();
        EchoppeArt.planche(ctx, listX - 10, detailY, listW + 20, detailH);
        if (cur != null) {
            DaKit.arcX1(ctx, f, DaKit.arcade(DaKit.ellipse(f, cur.name, listW - 8)), listX, detailY + 9, DaKit.CREME, 0, true);
            String desc = cur.map ? "Objet : tiens-la en main (clic droit ou M) pour ouvrir la carte. Rachetable si elle est perdue."
                : cur.id.isEmpty() ? "Retirer sa tenue." : "Tenue : aperçu en direct sur ton personnage.";
            DaKit.Paragraphe p = DaKit.paragraphe(f, desc, listW - 4, DaKit.S8, 9, compact ? 1 : 2);
            p.dessiner(ctx, f, listX, detailY + 21, DaKit.DOUX);
        }

        // ── Boutons ──
        boolean curOwned = cur != null && !cur.map && (cur.id.isEmpty() || ShopData.owns(cur.id));
        boolean wornNow = cur != null && !cur.map && cur.id.equals(worn == null ? "" : worn);
        backBtn = plaque(ctx, f, 18, footY, 96, btnH, "Retour", 0, true, mouseX, mouseY);
        buyBtn = null;
        equipBtn = null;
        if (cur != null && (cur.map || (!cur.id.isEmpty() && !ShopData.owns(cur.id)))) {
            long price = priceOf(cur);
            boolean can = ShopData.received() && ShopData.ryo() >= price;
            String label = (cur.map ? "Acheter la carte  " : "Acheter  ") + fmtRyo(price);
            buyBtn = plaque(ctx, f, listX - 10, footY, listW + 20, btnH, label, 2, can, mouseX, mouseY);
            float lw = f.width(DaKit.arcade(label));
            EchoppeArt.ryo(ctx, Math.round(listX + listW / 2f + lw / 2f + 5), footY + 5);
        } else if (cur != null && !cur.map) {
            equipBtn = plaque(ctx, f, listX - 10, footY, listW + 20, btnH, wornNow ? "Deja portee" : "Equiper", 2,
                curOwned && !wornNow, mouseX, mouseY);
        }

        // ── Aides ──
        EchoppeArt.aides(ctx, f, compact
                ? new String[][]{{"Clic droit", "tourner"}, {"Tab", "onglet"}}
                : new String[][]{{"Clic droit", "tourner"}, {"Haut Bas", "parcourir"}, {"Tab", "onglet"}},
            stageR, footY + btnH / 2f);

        // ── Message du marchand (papier qui glisse sous le noren) ──
        String toast = ShopData.toast();
        long age = System.currentTimeMillis() - ShopData.toastAt();
        if (toast != null && age < 3200) {
            float in = Math.min(1f, age / 180f), out = Math.max(0f, (age - 2800) / 400f);
            boolean ok = toast.startsWith("§a"), err = toast.startsWith("§c");
            String txt = toast.replaceAll("§.", "");
            Component tc = DaKit.inter(txt);
            int tw = Math.round(DaKit.largeur8(f, tc)) + 28, th = 16;
            int tx = W / 2 - tw / 2;
            int ty = Math.round(norenH - th + (th + 12) * (in - out));
            EchoppeArt.toast(ctx, tx, ty, tw);
            DaKit.inter8(ctx, f, tc, W / 2f, ty + 9, ok ? 0xFF2E6A4A : err ? DaKit.ENCRE_ROUGE : DaKit.ENCRE, 1);
        }
        EchoppeArt.noren(ctx, W, norenH, time);
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

    /** Onglet pendu : libellé ArcadePix ×0,5, compte en or ; fil d'or depuis le noren. */
    private int[] tabTag(GuiGraphicsExtractor ctx, Font f, String label, int count, int x, int y, boolean active, int mx,
                         int my) {
        Component c = DaKit.arcade(label), n = DaKit.arcade(String.valueOf(count));
        float lw = DaKit.largeurFin(f, c), nw = DaKit.largeurFin(f, n);
        int w = Math.round(lw + nw) + 26, h = 16;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        int dy = active ? 2 : 0;
        EchoppeArt.fil(ctx, x + w / 2, norenH - 4, y + dy);
        EchoppeArt.onglet(ctx, x, y + dy, w, active ? 2 : hov ? 1 : 0);
        float ty = y + dy + h / 2f - 1.75f;
        DaKit.arcFin(ctx, f, c, x + 9, ty, active || hov ? DaKit.CREME : 0xFFD8CCB0, 0, active);
        DaKit.arcFin(ctx, f, n, x + 9 + lw + 8, ty, active ? DaKit.OR_CLAIR : DaKit.OR, 0, false);
        return new int[]{x, y, x + w, y + h + 2};
    }

    /** Bouton-plaque : ton 0 = laque noire, 2 = laque rouge ; libellé ArcadePix ×1. */
    private int[] plaque(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h, String label, int ton,
                         boolean enabled, int mx, int my) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int etat = !enabled ? 4 : ton + (hov ? 1 : 0);
        EchoppeArt.bouton(ctx, x, y, w, etat);
        DaKit.arcX1(ctx, f, DaKit.arcade(label), x + w / 2f, y + (h - 7) / 2f, enabled ? DaKit.CREME : DaKit.INACTIF, 1, enabled);
        return new int[]{x, y, x + w, y + h};
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
