package fr.reborn.hud.config;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Snapshot immutable d'un {@link HudConfig} pour l'undo/redo.
 *
 * <p>Capture l'état des éléments + le preset actif. Les presets définis
 * (la collection elle-même) ne sont pas snapshotés — créer/supprimer un
 * preset n'est pas undoable, c'est une action "structurelle".
 */
public record HudConfigSnapshot(Map<HudElement, HudElementState> states, String activePreset) {

    public static HudConfigSnapshot capture(HudConfig config) {
        EnumMap<HudElement, HudElementState> copy = new EnumMap<>(HudElement.class);
        for (HudElement e : HudElement.values()) {
            copy.put(e, config.stateOf(e));
        }
        return new HudConfigSnapshot(copy, config.getActivePreset());
    }

    /** Applique ce snapshot au {@code config} cible (mutation + save). */
    public void applyTo(HudConfig config) {
        for (HudElement e : HudElement.values()) {
            HudElementState s = states.getOrDefault(e, HudElementState.DEFAULT);
            config.setState(e, s);
        }
        config.setActivePreset(activePreset);
        config.save();
    }
}
