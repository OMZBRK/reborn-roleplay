package fr.reborn.hud.config;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers pour produire les snapshots des presets par défaut décrits dans
 * le brief :
 * <ul>
 *   <li><b>Default</b> — tout vanilla (zero offset/scale, visible).</li>
 *   <li><b>Streamer</b> — chat en haut, hotbar centrée bas, scoreboard
 *       caché, boss bar décalée.</li>
 *   <li><b>RP</b> — chat agrandi 1.2x + à gauche, scoreboard agrandi à droite.</li>
 *   <li><b>Compact</b> — tout réduit à 0.85x, action bar masquée.</li>
 * </ul>
 *
 * <p>Renvoie un {@code Map<id, HudElementState>} prêt à être inséré dans
 * la map presets de {@link HudConfig}.
 */
public final class HudPresets {

    public static final String DEFAULT = "default";
    public static final String STREAMER = "streamer";
    public static final String RP       = "rp";
    public static final String COMPACT  = "compact";

    private HudPresets() {}

    /**
     * Construit la map preset-id → {@code Map<element-id, state>} pour les
     * 4 presets prédéfinis. Idempotent.
     */
    public static Map<String, Map<String, HudElementState>> defaults() {
        Map<String, Map<String, HudElementState>> presets = new LinkedHashMap<>();
        presets.put(DEFAULT,  buildDefault());
        presets.put(STREAMER, buildStreamer());
        presets.put(RP,       buildRP());
        presets.put(COMPACT,  buildCompact());
        return presets;
    }

    private static Map<String, HudElementState> buildDefault() {
        Map<String, HudElementState> m = new LinkedHashMap<>();
        for (HudElement e : HudElement.values()) {
            m.put(e.id(), HudElementState.DEFAULT);
        }
        // Chat pile au-dessus de la barre de saisie (offset +23 en Y).
        m.put(HudElement.CHAT.id(), new HudElementState(0, 23, 1.0f, true, null));
        return m;
    }

    private static Map<String, HudElementState> buildStreamer() {
        Map<String, HudElementState> m = buildDefault();
        // Chat shifté vers le haut
        m.put(HudElement.CHAT.id(),       new HudElementState(0, -200, 1.0f, true, null));
        // Hotbar centrée bas — defaultAnchor = BOTTOM_CENTER, déjà OK vanilla
        // Scoreboard caché
        m.put(HudElement.SCOREBOARD.id(), new HudElementState(0, 0, 1.0f, false, null));
        // Boss bar décalée
        m.put(HudElement.BOSS_BAR.id(),   new HudElementState(0, 40, 1.0f, true, null));
        return m;
    }

    private static Map<String, HudElementState> buildRP() {
        Map<String, HudElementState> m = buildDefault();
        // Chat agrandi 1.2x + déplacé à gauche
        m.put(HudElement.CHAT.id(),       new HudElementState(-50, 0, 1.2f, true, null));
        // Scoreboard agrandi à droite (default anchor CENTER_RIGHT)
        m.put(HudElement.SCOREBOARD.id(), new HudElementState(0, 0, 1.2f, true, null));
        return m;
    }

    private static Map<String, HudElementState> buildCompact() {
        Map<String, HudElementState> m = buildDefault();
        for (HudElement e : HudElement.values()) {
            HudElementState s = m.get(e.id());
            m.put(e.id(), s.withScale(0.85f));
        }
        // Action bar masquée
        m.put(HudElement.ACTION_BAR.id(),
            m.get(HudElement.ACTION_BAR.id()).withVisible(false));
        return m;
    }

    /** Nom UI lisible d'un preset par son id. */
    public static String displayName(String id) {
        return switch (id) {
            case DEFAULT  -> "Default";
            case STREAMER -> "Streamer";
            case RP       -> "RP";
            case COMPACT  -> "Compact";
            default       -> id;
        };
    }

    /** Description courte UI d'un preset par son id. */
    public static String description(String id) {
        return switch (id) {
            case DEFAULT  -> "Disposition vanilla";
            case STREAMER -> "Minimal pour le stream";
            case RP       -> "Focus chat + scoreboard";
            case COMPACT  -> "Tout réduit en bas";
            default       -> "Preset personnalisé";
        };
    }
}
