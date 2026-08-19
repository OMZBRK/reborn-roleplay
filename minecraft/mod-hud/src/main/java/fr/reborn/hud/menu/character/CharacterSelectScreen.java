package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

    // Masque le HUD vanilla pendant l'écran (rendu hybride GUI, monde masqué).
    private boolean prevHudHidden;
    private boolean perspectiveCaptured = false;

    public CharacterSelectScreen() {
        super(Component.literal("Sélection du personnage"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!perspectiveCaptured && mc.options != null) {
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor)(Object) mc.gui.hud).reborn$setHidden(true); // masque vie/faim/xp/armure/hotbar/crosshair
            perspectiveCaptured = true;
        }
        // Pose idle (émote assise) le temps de l'écran — présentation « plan idle ».
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.startPose();
        applyFocusPreview(); // affiche le skin RP composé du perso focalisé
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (perspectiveCaptured && mc.options != null) {
            ((fr.reborn.hud.mixin.HudAccessor)(Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            perspectiveCaptured = false;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.stopPose();
        super.removed();
    }

    /**
     * Applique le skin RP <b>composé du perso focalisé</b> sur le corps du joueur local
     * (rendu via {@code drawEntity}) → on voit le skin de CHAQUE perso en parcourant.
     * Le perso est gelé et les autres joueurs sont cachés pendant la sélection, donc ce
     * preview local est sans effet visible pour autrui. Tuile « créer » / sans apparence
     * → skin Minecraft normal.
     */
    private void applyFocusPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        java.util.UUID uuid = mc.player.getUUID();
        List<CharacterCard> list = cards();
        if (!isCreateTile(focused) && focused >= 0 && focused < list.size()
                && list.get(focused).hasAppearance()) {
            fr.reborn.hud.skin.RebornSkins.applySpec(
                uuid, fr.reborn.hud.skin.SkinSpec.deserialize(list.get(focused).appearance()));
        } else {
            fr.reborn.hud.skin.RebornSkins.clear(uuid);
        }
    }

    // ── Données / indices ─────────────────────────────────────────
    private List<CharacterCard> cards() { return CharacterData.characters(); }
    private boolean canCreate() { return cards().size() < CharacterData.slotLimit(); }
    private int tileCount() { return cards().size() + (canCreate() ? 1 : 0); }
    private boolean isCreateTile(int i) { return canCreate() && i == cards().size(); }

    private void moveFocus(int delta) {
        int n = Math.max(1, tileCount());
        focused = ((focused + delta) % n + n) % n;
        applyFocusPreview();
        // Son de changement de perso (passage d'un caractère à l'autre).
        fr.reborn.hud.menu.RebornSounds.charNav();
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
        // Fond stylisé PLEIN (opaque) — masque le monde IG (rendu hybride GUI).
        ctx.fillGradient(0, 0, this.width, this.height, 0xFF0B0709, 0xFF140A0D);
        // Bande sombre en bas où vivent le nom / village / bouton.
        ctx.fillGradient(0, this.height - 170, this.width, this.height,
            0x00000000, Colors.withAlpha(0xFF000000, 0.55f));
    }

    /** Rend le joueur local (skin composé du perso focalisé) centré, face caméra, statique. */
    private void drawAvatar(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int cx = this.width / 2;
        int size = (int) (this.height * 0.34f);
        int top = (int) (this.height * 0.12f);
        int bot = (int) (this.height * 0.86f);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(
            ctx, cx - size, top, cx + size, bot, size, 0f, cx, (top + bot) / 2f, mc.player);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        List<CharacterCard> list = cards();

        drawAvatar(ctx); // perso au centre (skin RP du perso focalisé)

        // Logo serveur (haut-droite).
        if (CreatorUi.logoExists()) {
            CreatorUi.blitLogo(ctx, this.width - 24 - 45, 14, 45, 30);
        } else {
            Component logo = Component.literal("REBORN");
            ctx.text(tr, logo, this.width - 24 - tr.width(logo), 22, Colors.GOLD, false);
        }

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
        // Applique + persiste le skin RP composé du perso choisi sur le joueur local
        // (l'override reste actif en jeu jusqu'à la déconnexion ; re-sélection au
        // prochain login le ré-applique). Pas d'apparence → skin Minecraft normal.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (c.hasAppearance()) {
                fr.reborn.hud.skin.RebornSkins.applySpec(
                    mc.player.getUUID(), fr.reborn.hud.skin.SkinSpec.deserialize(c.appearance()));
            } else {
                fr.reborn.hud.skin.RebornSkins.clear(mc.player.getUUID());
            }
        }
        sendAction("select:" + c.id());
        // Écran de chargement stylisé (~6 s) pendant que le serveur applique setActive
        // (téléport IG). Il se ferme seul → retour en jeu.
        Minecraft.getInstance().setScreenAndShow(new CharacterLoadingScreen(c.firstName(), c.clanColor()));
    }

    private void onCreate() {
        Minecraft.getInstance().setScreenAndShow(new CharacterCreateScreen());
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
