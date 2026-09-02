package fr.reborn.hud.menu.widget;

import com.mojang.blaze3d.platform.InputConstants;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
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

        int bg = live ? Colors.ACCENT_SOFT : (hovered ? Colors.SURFACE_ELEVATED : Colors.SURFACE);
        int border = live ? Colors.ACCENT : (hovered ? Colors.ACCENT_HOVER : Colors.BORDER_STRONG);
        DrawHelpers.roundedOutlinedRect(ctx, x0, y0, w, h, 6, bg, border);

        Component label = live
            ? RebornFont.bold("> " + mapping.getTranslatedKeyMessage().getString() + " <")
            : RebornFont.bold(mapping.getTranslatedKeyMessage().getString());
        int color = live ? Colors.WHITE_PURE : (hovered ? Colors.WHITE_PURE : Colors.FOREGROUND);
        int tw = tr.width(label);
        ctx.text(tr, label, x0 + (w - tw) / 2, y0 + (h - tr.lineHeight) / 2 + 1, color, false);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
