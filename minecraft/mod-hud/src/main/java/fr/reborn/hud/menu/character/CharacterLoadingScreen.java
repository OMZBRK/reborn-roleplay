package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Écran de chargement stylisé affiché ~7 s après le choix d'un personnage, pendant
 * que ShinobiCore applique {@code setActive} (téléport IG). Purement cosmétique : il
 * se ferme seul → retour en jeu. Fond opaque (masque la transition monde).
 */
public class CharacterLoadingScreen extends Screen {

    /** Durée en ticks (20/s) — ~7 s. */
    private static final int DURATION = 140;

    private final String name;
    private final int accent;
    private int ticks = 0;
    private boolean prevHudHidden;
    private boolean captured = false;

    public CharacterLoadingScreen(String name, int clanColor) {
        super(Component.literal("Chargement"));
        this.name = name == null ? "" : name;
        this.accent = clanColor != 0 ? clanColor : Colors.ACCENT;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!captured && mc.options != null) {
            prevHudHidden = mc.options.hideGui;
            mc.options.hideGui = true;
            captured = true;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (captured && mc.options != null) {
            mc.options.hideGui = prevHudHidden;
            captured = false;
        }
        super.removed();
    }

    @Override
    public void tick() {
        if (++ticks >= DURATION) {
            Minecraft.getInstance().setScreen(null); // retour en jeu
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, 0xFF0B0709, 0xFF140A0D);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        int cx = this.width / 2, cy = this.height / 2;

        // Titre REBORN.
        Component logo = Component.literal("REBORN");
        ctx.text(tr, logo, cx - tr.width(logo) / 2, cy - 48, Colors.GOLD, false);

        // « Chargement » + points animés.
        int dots = (ticks / 8) % 4;
        Component load = Component.literal("Chargement" + ".".repeat(dots));
        ctx.text(tr, load, cx - tr.width(load) / 2, cy - 24, Colors.WHITE_PURE, false);

        // Nom du perso qui rejoint le monde.
        if (!name.isBlank()) {
            Component sub = Component.literal(name + " entre en scène");
            ctx.text(tr, sub, cx - tr.width(sub) / 2, cy + 22, Colors.FOREGROUND_MUTED, false);
        }

        // Barre de progression.
        int bw = 260, bx = cx - bw / 2, by = cy + 2;
        float p = Math.min(1f, (ticks + delta) / (float) DURATION);
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, bw, 8, 3,
            Colors.withAlpha(0xFF000000, 0.5f), Colors.withAlpha(Colors.FOREGROUND, 0.35f));
        int fillW = Math.max(0, Math.round((bw - 2) * p));
        if (fillW > 0) DrawHelpers.roundedRect(ctx, bx + 1, by + 1, fillW, 6, 2, accent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // Écran non fermable manuellement (on attend la fin du chargement).
    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return true; }
}
