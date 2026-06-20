package fr.reborn.hud.menu.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.List;

/**
 * Interface commune pour les 5 tabs des paramètres Reborn.
 *
 * <p>Chaque tab :
 * <ul>
 *   <li>Crée ses widgets cliquables (sliders, toggles, etc.) à l'init
 *       et les expose via {@link #widgets()} pour que le parent
 *       Screen les addDrawableChild.</li>
 *   <li>Rend les éléments passifs (labels, hints, banners) via
 *       {@link #renderPassive(DrawContext, int, int, int)} — le parent
 *       gère le scroll et le clipping.</li>
 *   <li>Retourne la hauteur totale pour le scroll via {@link #height()}.</li>
 * </ul>
 */
public interface SettingsTab {

    /** Crée les widgets cliquables et les positionne relativement à (x, y). */
    void layout(int x, int y, int width);

    /** Widgets cliquables à addDrawableChild dans le parent Screen. */
    List<ClickableWidget> widgets();

    /** Hauteur totale du contenu de ce tab (pour scroll). */
    int height();

    /** Rend les éléments passifs (labels, hints, banners). */
    void renderPassive(DrawContext ctx, int scrollX, int scrollY, int contentWidth);
}
