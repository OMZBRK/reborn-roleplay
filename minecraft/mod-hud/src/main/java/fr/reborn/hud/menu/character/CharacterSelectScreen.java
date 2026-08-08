package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Écran de sélection de personnage <b>épuré façon Zenkai</b> (affiché au join).
 *
 * <p>Le décor du jeu reste visible derrière (le serveur téléporte le joueur au
 * build de spawn et fige la caméra). Un seul personnage à la fois, <b>centré,
 * face caméra, statique</b>. Dessous : nom, ligne « clan · village », et un
 * bouton <b>Sélectionner</b>. On change de personnage avec les flèches ‹ › aux
 * bords. Si le personnage est mort (RPK), le bouton devient « RPK » non cliquable.
 *
 * <p>Le « perso dans le monde » = le vrai joueur local en 3e personne de face
 * pendant que l'écran est ouvert (skins par perso = Phase 2). Branché serveur via
 * le canal {@code reborn:character}.
 */
public class CharacterSelectScreen extends Screen {

    private static final int BTN_W = 190;
    private static final int BTN_H = 36;

    private int focused = 0;

    // Sauvegarde/restaure la vue + masque le HUD vanilla pendant l'écran.
    private CameraType prevPerspective;
    private boolean prevHudHidden;
    private boolean perspectiveCaptured = false;

    public CharacterSelectScreen() {
        super(Component.literal("Sélection du personnage"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!perspectiveCaptured && mc.options != null) {
            prevPerspective = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            prevHudHidden = mc.options.hideGui;
            mc.options.hideGui = true; // masque vie/faim/xp/armure/hotbar/crosshair
            perspectiveCaptured = true;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (perspectiveCaptured && mc.options != null) {
            mc.options.setCameraType(prevPerspective);
            mc.options.hideGui = prevHudHidden;
            perspectiveCaptured = false;
        }
        super.removed();
    }

    // ── Données / indices ─────────────────────────────────────────
    private List<CharacterCard> cards() { return CharacterData.characters(); }
    private boolean canCreate() { return cards().size() < CharacterData.slotLimit(); }
    private int tileCount() { return cards().size() + (canCreate() ? 1 : 0); }
    private boolean isCreateTile(int i) { return canCreate() && i == cards().size(); }

    private void moveFocus(int delta) {
        int n = Math.max(1, tileCount());
        focused = ((focused + delta) % n + n) % n;
    }

    // ── Géométrie ─────────────────────────────────────────────────
    private int modelCenterY() { return (int) (this.height * 0.44f); }

    private int nameY() { return (int) (this.height * 0.76f); }
    private int villageY() { return nameY() + 14; }
    private int btnX() { return (this.width - BTN_W) / 2; }
    private int btnY() { return villageY() + 16; }

    private int arrowY() { return modelCenterY() - 6; }
    private int leftArrowX() { return 44; }
    private int rightArrowX() { return this.width - 64; }

    // ── Rendu ─────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // PAS de voile plein écran : le décor du jeu reste visible (effet Zenkai).
        // Bande sombre en bas où vivent le nom / village / bouton.
        ctx.fillGradient(0, this.height - 170, this.width, this.height,
            0x00000000, Colors.withAlpha(0xFF000000, 0.80f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        List<CharacterCard> list = cards();

        if (isCreateTile(focused)) {
            drawCreate(ctx, tr, mouseX, mouseY);
        } else if (!list.isEmpty()) {
            drawCharacter(ctx, tr, list.get(Math.min(focused, list.size() - 1)), mouseX, mouseY);
        }

        // Flèches ‹ › (si plus d'une tuile).
        if (tileCount() > 1) {
            boolean lh = overArrow(mouseX, mouseY, leftArrowX());
            boolean rh = overArrow(mouseX, mouseY, rightArrowX());
            ctx.text(tr, Component.literal("<"), leftArrowX(), arrowY(),
                lh ? Colors.WHITE_PURE : Colors.FOREGROUND_MUTED, false);
            ctx.text(tr, Component.literal(">"), rightArrowX(), arrowY(),
                rh ? Colors.WHITE_PURE : Colors.FOREGROUND_MUTED, false);
        }
    }

    private void drawCharacter(GuiGraphicsExtractor ctx, Font tr, CharacterCard c, int mouseX, int mouseY) {
        int clanCol = c.clanColor() != 0 ? c.clanColor() : Colors.ACCENT;
        int nameCol = c.dead() ? Colors.FOREGROUND_MUTED : Colors.WHITE_PURE;

        // Ligne 1 : nom + clan côte à côte, centrés en groupe.
        Component name = Component.literal(c.firstName());
        Component clan = c.hasClan() ? Component.literal(c.clan()) : null;
        int gap = 12;
        int nameW = tr.width(name);
        int clanW = clan != null ? tr.width(clan) : 0;
        int totalW = nameW + (clan != null ? gap + clanW : 0);
        int lx = (this.width - totalW) / 2;
        ctx.text(tr, name, lx, nameY(), nameCol, false);
        if (clan != null) {
            ctx.text(tr, clan, lx + nameW + gap, nameY(),
                c.dead() ? Colors.FOREGROUND_MUTED : clanCol, false);
        }

        // Ligne 2 : village.
        if (c.hasVillage()) {
            Component vil = Component.literal(c.village());
            ctx.text(tr, vil, (this.width - tr.width(vil)) / 2, villageY(),
                c.dead() ? Colors.FOREGROUND_MUTED : Colors.FOREGROUND_SUBTLE, false);
        }

        // Bouton « Sélectionner » OU pastille « RPK » (non cliquable).
        if (c.dead()) {
            DrawHelpers.roundedOutlinedRect(ctx, btnX(), btnY(), BTN_W, BTN_H, 8,
                Colors.withAlpha(Colors.DANGER, 0.35f), Colors.DANGER);
            Component rpk = Component.literal("RPK");
            ctx.text(tr, rpk, (this.width - tr.width(rpk)) / 2, btnY() + (BTN_H - 8) / 2,
                Colors.WHITE_PURE, false);
        } else {
            drawButton(ctx, tr, "Sélectionner", mouseX, mouseY, clanCol);
        }
    }

    private void drawCreate(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
        Component name = Component.literal("Nouveau personnage");
        ctx.text(tr, name, (this.width - tr.width(name)) / 2, nameY(), Colors.WHITE_PURE, false);
        Component m = Component.literal("Crée ton shinobi");
        ctx.text(tr, m, (this.width - tr.width(m)) / 2, villageY(), Colors.FOREGROUND_MUTED, false);
        drawButton(ctx, tr, "Créer", mouseX, mouseY, Colors.ACCENT);
    }

    private void drawButton(GuiGraphicsExtractor ctx, Font tr, String label, int mouseX, int mouseY, int accent) {
        boolean hover = overButton(mouseX, mouseY);
        int fill = hover ? Colors.withAlpha(accent, 0.35f) : Colors.withAlpha(0xFF000000, 0.45f);
        int border = hover ? accent : Colors.withAlpha(Colors.FOREGROUND, 0.35f);
        DrawHelpers.roundedOutlinedRect(ctx, btnX(), btnY(), BTN_W, BTN_H, 8, fill, border);
        Component t = Component.literal(label);
        ctx.text(tr, t, (this.width - tr.width(t)) / 2, btnY() + (BTN_H - 8) / 2,
            Colors.WHITE_PURE, false);
    }

    // ── Hit-tests ─────────────────────────────────────────────────
    private boolean overButton(int mx, int my) {
        return mx >= btnX() && mx < btnX() + BTN_W && my >= btnY() && my < btnY() + BTN_H;
    }

    private boolean overArrow(int mx, int my, int ax) {
        return mx >= ax - 6 && mx <= ax + 18 && Math.abs(my - arrowY()) < 18;
    }

    // ── Interactions ──────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (v != 0) { moveFocus(v > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> { moveFocus(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { moveFocus(1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { confirmFocused(); return true; }
            default -> { return super.keyPressed(event); }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mx = (int) event.x(), my = (int) event.y();
            if (tileCount() > 1) {
                if (overArrow(mx, my, leftArrowX())) { moveFocus(-1); return true; }
                if (overArrow(mx, my, rightArrowX())) { moveFocus(1); return true; }
            }
            if (overButton(mx, my)) {
                if (isCreateTile(focused)) { onCreate(); return true; }
                List<CharacterCard> list = cards();
                if (focused >= 0 && focused < list.size() && !list.get(focused).dead()) {
                    onSelect(list.get(focused));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void confirmFocused() {
        if (isCreateTile(focused)) { onCreate(); return; }
        List<CharacterCard> list = cards();
        if (focused < 0 || focused >= list.size()) return;
        CharacterCard c = list.get(focused);
        if (!c.dead()) onSelect(c);
    }

    private void onSelect(CharacterCard c) {
        sendAction("select:" + c.id());
        this.onClose(); // le serveur téléporte le joueur en jeu (setActive)
    }

    private void onCreate() {
        Minecraft.getInstance().setScreen(new CharacterCreateScreen());
    }

    /** Envoie une commande C2S sur reborn:character ; feedback local si hors serveur. */
    private void sendAction(String cmd) {
        if (ClientPlayNetworking.canSend(CharacterPayload.ID)) {
            ClientPlayNetworking.send(new CharacterPayload(cmd));
        } else {
            note(cmd.startsWith("select:") ? "Sélection (hors serveur)" : "Création (hors serveur)");
        }
    }

    private void note(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Reborn] " + msg));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
