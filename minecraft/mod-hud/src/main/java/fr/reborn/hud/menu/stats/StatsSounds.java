package fr.reborn.hud.menu.stats;

import fr.reborn.hud.menu.RebornSounds;

/**
 * Sons de la fiche « lanternes célestes » ({@code assets/reborn/sounds/stats/*.ogg},
 * synthétisés par {@code tools/ui-art/gen_stats_sfx.py}) : gamme pentatonique japonaise
 * « in » sur ré — chaque point posé monte d'un degré.
 */
public final class StatsSounds {

    private StatsSounds() {}

    /** Demi-tons de la gamme « in » (ré, mi♭, sol, la, si♭…) pour les valeurs 0 → 10. */
    private static final int[] IN_SCALE = {0, 1, 5, 7, 8, 12, 13, 17, 19, 20, 24};

    private static long lastHover;

    public static void open() { RebornSounds.playReborn("stats.open", 1.0f, 0.55f); }

    /** Allumage d'une lanterne (ouverture), légère variation de hauteur par lanterne. */
    public static void light(int index) {
        RebornSounds.playReborn("stats.light", 0.9f + 0.05f * index, 0.35f);
    }

    /** Point ajouté : le carillon monte sur la gamme avec la valeur visée. */
    public static void rise(int newValue) {
        int v = Math.max(0, Math.min(IN_SCALE.length - 1, newValue));
        // rise.ogg est en la (= +7 demi-tons au-dessus de ré) ; on vise ré grave + degré.
        float pitch = (float) Math.pow(2, (IN_SCALE[v] - 12) / 12.0);
        RebornSounds.playReborn("stats.rise", Math.max(0.5f, Math.min(2.0f, pitch)), 0.6f);
    }

    public static void lower() { RebornSounds.playReborn("stats.lower", 1.0f, 0.5f); }

    public static void validate() { RebornSounds.playReborn("stats.validate", 1.0f, 0.75f); }

    public static void deny() { RebornSounds.playReborn("stats.deny", 1.0f, 0.6f); }

    public static void respec() { RebornSounds.playReborn("stats.respec", 1.0f, 0.7f); }

    /** Flamme-esprit qui part vers une lanterne (ou en revient). */
    public static void spirit(float pitch) { RebornSounds.playReborn("stats.spirit", pitch, 0.4f); }

    public static void tab() { RebornSounds.playReborn("stats.tab", 1.0f, 0.5f); }

    /** Survol : un tic très léger, limité pour ne pas crépiter en balayant la souris. */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < 70) return;
        lastHover = now;
        RebornSounds.playReborn("stats.hover", 1.0f, 0.18f);
    }

    /* ------------------------------------------------------------ ambiance */

    /** Grillons + vent en boucle tant que la fiche est ouverte (fondu d'entrée). */
    public static void startAmbience() { fr.reborn.hud.ui.UiAmbience.start("stats.ambience", 0.35f); }

    /** Fondu de sortie puis arrêt (non bloquant). */
    public static void stopAmbience() { fr.reborn.hud.ui.UiAmbience.stop(); }
}
