package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.shop.ShopData;
import fr.reborn.hud.menu.shop.ShopPayload;
import fr.reborn.hud.skin.CharacterCatalog;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Boutique de TENUES (skins) — ouverte depuis la carte « Boutique » du menu ÉCHAP.
 * Liste les tenues du catalogue creator ; chaque tenue coûte des <b>ryo</b>
 * ({@link ShopData}). On sélectionne une tenue → aperçu live sur son propre corps
 * (caméra 3e pers. de face), on l'<b>achète</b> (si assez de ryo) puis on
 * l'<b>équipe</b>. Autoritaire côté serveur (canal {@code reborn:shop},
 * ShinobiCore) : le client ne fait qu'afficher + demander.
 */
public class ShopScreen extends Screen {

    private final Screen parent;

    // Entrée de liste : une tenue du catalogue (ou l'entrée « Aucune » = torse nu, id "").
    private record Entry(String id, String name) {}
    private final List<Entry> entries = new ArrayList<>();
    private int selected = 0;
    private int scroll = 0;

    private CameraType prevCamera;
    private boolean prevHudHidden;
    private boolean captured = false;
    private boolean syncedFromState = false; // aligne sélection/aperçu quand l'état serveur arrive

    // Géométrie du panneau liste (calculée dans render).
    private int listX, listY, listW, listH, rowH = 18;
    // Zones cliquables des boutons (x1,y1,x2,y2), posées en render, lues en mouseClicked.
    private int[] buyBtn, equipBtn, backBtn;

    public ShopScreen(Screen parent) {
        super(Component.literal("Boutique"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!captured && mc.options != null) {
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            prevCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); // on se voit de face
            captured = true;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.startPose(); // pose idle « présentation »

        // Construit la liste : « Aucune » + toutes les tenues du catalogue.
        entries.clear();
        entries.add(new Entry("", "Aucune (torse nu)"));
        for (CharacterCatalog.Asset a : CharacterCatalog.all("outfit")) {
            entries.add(new Entry(a.id, a.name != null && !a.name.isBlank() ? a.name : a.id));
        }

        // Demande l'état boutique au serveur (ryo, possédées, apparence).
        if (ClientPlayNetworking.canSend(ShopPayload.ID)) {
            ClientPlayNetworking.send(new ShopPayload("open"));
        }
        // Sélectionne la tenue actuellement portée (d'après l'apparence) puis aperçu.
        selectCurrent();
        preview();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (captured && mc.options != null) {
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            if (prevCamera != null) mc.options.setCameraType(prevCamera);
            captured = false;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.stopPose();
        // Restaure l'apparence réelle (au cas où on avait un aperçu non équipé).
        applyAppearance(ShopData.appearance());
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Pas de voile plein écran : on garde le monde/le corps visibles derrière. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // dégradés discrets haut/bas seulement (comme l'écran de sélection)
        ctx.fillGradient(0, 0, this.width, 60, 0xA0000000, 0x00000000);
        ctx.fillGradient(0, this.height - 70, this.width, this.height, 0x00000000, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        // Quand l'état serveur arrive (async après l'ouverture), on aligne une fois la
        // sélection sur la tenue portée + on rafraîchit l'aperçu.
        if (!syncedFromState && ShopData.received()) {
            syncedFromState = true;
            selectCurrent();
            preview();
        }
        Font f = this.font;

        // ── Titre + solde ryo ──
        ctx.text(f, RebornFont.bold("BOUTIQUE — TENUES"), 16, 14, 0xFFF2E9C0, true);
        String bal = ShopData.received() ? (ShopData.ryo() + " ryo") : "…";
        int balW = f.width(bal);
        ctx.text(f, RebornFont.bold(bal), this.width - 16 - balW, 14, 0xFFE8C34A, true);

        // ── Panneau liste (droite) ──
        listW = 240;
        listX = this.width - listW - 12;
        listY = 40;
        listH = this.height - listY - 56;
        DrawHelpers.roundedRectFull(ctx, listX - 6, listY - 6, listW + 12, listH + 12, 6, 0xC0140D0A);

        int visible = Math.max(1, listH / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        for (int row = 0; row < visible && (row + scroll) < entries.size(); row++) {
            int idx = row + scroll;
            Entry e = entries.get(idx);
            int ry = listY + row * rowH;
            boolean sel = idx == selected;
            boolean owned = e.id.isEmpty() || ShopData.owns(e.id);
            if (sel) DrawHelpers.roundedRectFull(ctx, listX, ry, listW, rowH - 2, 3, 0x804A6FE0);
            int nameCol = sel ? 0xFFFFFFFF : (owned ? 0xFFCFE8CF : 0xFFCFCFCF);
            ctx.text(f, Component.literal(trim(e.name, 22)), listX + 6, ry + 5, nameCol, false);
            // statut / prix à droite de la ligne
            String tag = e.id.isEmpty() ? "" : (ShopData.owns(e.id) ? "Possédée" : (ShopData.price() + "r"));
            int tagCol = e.id.isEmpty() ? 0 : (ShopData.owns(e.id) ? 0xFF7FD37F : 0xFFE8C34A);
            if (!tag.isEmpty()) {
                int tw = f.width(tag);
                ctx.text(f, Component.literal(tag), listX + listW - 6 - tw, ry + 5, tagCol, false);
            }
        }

        // ── Boutons bas ──
        int by = this.height - 42, bh = 26;
        Entry cur = current();
        boolean curOwned = cur != null && (cur.id.isEmpty() || ShopData.owns(cur.id));
        boolean canBuy = cur != null && !cur.id.isEmpty() && !ShopData.owns(cur.id)
                && ShopData.received() && ShopData.ryo() >= ShopData.price();

        backBtn = drawButton(ctx, f, 16, by, 110, bh, "< Retour", 0xFF3A3A3A, 0xFFE0E0E0);
        if (cur != null && !cur.id.isEmpty() && !ShopData.owns(cur.id)) {
            int col = canBuy ? 0xFF2E7D32 : 0xFF5A3A3A;
            buyBtn = drawButton(ctx, f, listX, by, 116, bh, "Acheter (" + ShopData.price() + "r)", col, canBuy ? 0xFFFFFFFF : 0xFF999999);
        } else buyBtn = null;
        equipBtn = drawButton(ctx, f, listX + 124, by, 116, bh,
                curOwned ? "Équiper" : "Non possédée", curOwned ? 0xFF2B5FA8 : 0xFF3A3A3A,
                curOwned ? 0xFFFFFFFF : 0xFF999999);

        // ── Toast serveur (achat/équipement) ~3 s ──
        String toast = ShopData.toast();
        if (toast != null && System.currentTimeMillis() - ShopData.toastAt() < 3000) {
            int tw = f.width(toast);
            ctx.text(f, Component.literal(toast), (this.width - tw) / 2, by - 16, 0xFFFFFFFF, true);
        }
    }

    private int[] drawButton(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h,
                             String label, int bg, int fg) {
        DrawHelpers.roundedRectFull(ctx, x, y, w, h, 4, bg);
        int tw = f.width(label);
        ctx.text(f, RebornFont.bold(label), x + (w - tw) / 2, y + (h - 8) / 2, fg, false);
        return new int[]{x, y, x + w, y + h};
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean dbl) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() == 0) {
            if (hit(backBtn, mx, my)) { onClose(); return true; }
            if (hit(buyBtn, mx, my)) { buy(); return true; }
            if (hit(equipBtn, mx, my)) { equip(); return true; }
            // clic sur une ligne de la liste
            if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
                int row = (my - listY) / rowH;
                int idx = row + scroll;
                if (idx >= 0 && idx < entries.size()) { selected = idx; preview(); return true; }
            }
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - Math.max(1, listH / rowH))));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_UP)   { selected = Math.max(0, selected - 1); ensureVisible(); preview(); return true; }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) { selected = Math.min(entries.size() - 1, selected + 1); ensureVisible(); preview(); return true; }
        return super.keyPressed(event);
    }

    private void ensureVisible() {
        int visible = Math.max(1, listH / rowH);
        if (selected < scroll) scroll = selected;
        else if (selected >= scroll + visible) scroll = selected - visible + 1;
    }

    private Entry current() {
        return (selected >= 0 && selected < entries.size()) ? entries.get(selected) : null;
    }

    private void buy() {
        Entry e = current();
        if (e == null || e.id.isEmpty() || ShopData.owns(e.id)) return;
        if (!ClientPlayNetworking.canSend(ShopPayload.ID)) return;
        ClientPlayNetworking.send(new ShopPayload("buy:" + e.id));
    }

    private void equip() {
        Entry e = current();
        if (e == null) return;
        if (!e.id.isEmpty() && !ShopData.owns(e.id)) return; // pas possédée
        if (!ClientPlayNetworking.canSend(ShopPayload.ID)) return;
        SkinSpec spec = SkinSpec.deserialize(ShopData.appearance());
        spec.outfitId = e.id;
        ClientPlayNetworking.send(new ShopPayload("equip:" + e.id + "\n" + spec.serialize()));
    }

    /** Applique l'aperçu de la tenue sélectionnée sur le corps du joueur (local). */
    private void preview() {
        Entry e = current();
        if (e == null) return;
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
        String cur = "";
        try { cur = SkinSpec.deserialize(ShopData.appearance()).outfitId; } catch (Exception ignored) {}
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(cur == null ? "" : cur)) { selected = i; break; }
        }
        ensureVisible();
    }

    private static boolean hit(int[] b, int mx, int my) {
        return b != null && mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3];
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
