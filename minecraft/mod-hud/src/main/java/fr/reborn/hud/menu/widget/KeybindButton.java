package fr.reborn.hud.menu.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Bouton de re-bind d'une {@link KeyMapping} (style Reborn), pour lister les
 * commandes Reborn directement dans l'onglet Contrôles des paramètres.
 *
 * <p>Comportement calqué sur l'écran vanilla « Commandes » :
 * <ul>
 *   <li>Clic → passe en écoute (affiche {@code > … <} en accent).</li>
 *   <li>La touche/bouton suivant est assigné ({@link KeyMapping#setKey}) puis
 *       {@link KeyMapping#resetMapping()} + {@code options.save()}.</li>
 *   <li>{@code Échap} annule (dé-bind → {@code UNKNOWN}).</li>
 * </ul>
 * Un seul bouton écoute à la fois (garde statique {@link #listening}).
 */
public final class KeybindButton extends Button {

    /** Bouton actuellement en écoute (un seul à la fois dans tout le menu). */
    private static KeybindButton listening;

    private final KeyMapping mapping;

    public KeybindButton(int x, int y, int w, int h, KeyMapping mapping) {
        super(x, y, w, h, Component.empty(), btn -> {}, Button.DEFAULT_NARRATION);
        this.mapping = mapping;
    }

    private boolean isListening() { return listening == this; }

    private void startListening() { listening = this; }

    private void stopListening() { if (listening == this) listening = null; }

    /** Assigne la touche puis persiste ; {@code null} = dé-bind ({@code UNKNOWN}). */
    private void assign(InputConstants.Key key) {
        mapping.setKey(key == null ? InputConstants.UNKNOWN : key);
        KeyMapping.resetMapping();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) mc.options.save();
        stopListening();
    }

    @Override
    public void onPress(InputWithModifiers input) {
        // Clic sur un bouton déjà en écoute = annule l'écoute (sans re-bind).
        if (isListening()) { stopListening(); return; }
        startListening();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isListening()) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            assign(null); // dé-bind
        } else {
            assign(InputConstants.getKey(event));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // En écoute : un clic assigne ce bouton de souris (au lieu de re-déclencher).
        if (isListening()) {
            assign(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        int x0 = getX(), y0 = getY(), w = getWidth(), h = getHeight();
        boolean live = isListening();
        boolean hovered = isHovered();

        // Touche laquée des Paramètres (plaque segmentée) : rouge en écoute, éclaircie au survol.
        // Lot 3 (planche t2) : plaque à la largeur du nom de la touche, calée à droite de la zone cliquable ; une touche
        // partagée avec une autre action (TouchesConflits) garde la plaque sombre, cerclée et lettrée de vermillon (t2-b2).
        boolean conflit = !live && fr.reborn.hud.keybind.TouchesConflits.enConflit(mapping);
        String key = mapping.isUnbound() ? "—" : mapping.getTranslatedKeyMessage().getString();
        String label = live ? "› " + key + " ‹" : key;
        Component texte = fr.reborn.hud.ui.DaKit.inter(fr.reborn.hud.ui.DaKit.ellipse(tr, label, w - 10));
        int pw = Math.min(w, Math.max(24, tr.width(texte) + 14));
        int xp = x0 + w - pw, py = y0 + (h - 16) / 2;
        fr.reborn.hud.menu.settings.ReglagesArt.segment(ctx, xp + 1, py, pw - 3, live ? 2 : hovered ? 1 : 0);
        if (conflit) {
            // filet vermillon d'1 px autour de la plaque sombre
            int r = fr.reborn.hud.ui.DaKit.ROUGE_CLAIR, xa = xp + 1, xb = xp + pw - 2;
            ctx.fill(xa, py, xb, py + 1, r);
            ctx.fill(xa, py + 15, xb, py + 16, r);
            ctx.fill(xa, py + 1, xa + 1, py + 15, r);
            ctx.fill(xb - 1, py + 1, xb, py + 15, r);
        }
        int color = conflit ? fr.reborn.hud.ui.DaKit.ROUGE_CLAIR
            : live || hovered ? fr.reborn.hud.ui.DaKit.CREME : fr.reborn.hud.ui.DaKit.OR_CLAIR;
        fr.reborn.hud.ui.DaKit.inter9(ctx, tr, texte, xp + pw / 2f - 1, y0 + h / 2f, color, 1);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
