package fr.reborn.hud.combat;

import java.util.EnumMap;
import java.util.Map;

/**
 * État client des cooldowns de capacités (affiché par {@link CooldownHud}, façon
 * Zenkai). Purement visuel : le client déclenche le CD au moment où il envoie
 * l'action (dash…), la durée doit matcher le cooldown serveur. Extensible — il
 * suffit d'ajouter une entrée à {@link Ability} et de {@code trigger(...)} au bon
 * endroit (ex. double saut, saut de chakra à venir).
 */
public final class CooldownState {

    public static final CooldownState INSTANCE = new CooldownState();

    /** Durée du CD de dash (doit matcher {@code DASH_COOLDOWN_MS} serveur). */
    public static final long DASH_CD_MS = 1500L;

    /** Capacités affichées dans le HUD de cooldowns. {@code glyph} = placeholder
     *  tant qu'aucune icône {@code textures/gui/ability/<name>.png} n'est livrée. */
    public enum Ability {
        DASH("Dash", 0xFF7FB4FF, ">>");
        // À VENIR : DOUBLE_JUMP, CHAKRA_JUMP… (se branchent ici + trigger au bon endroit)

        public final String label;
        public final int color;
        public final String glyph;
        Ability(String label, int color, String glyph) {
            this.label = label; this.color = color; this.glyph = glyph;
        }
    }

    private static final class CD { long start; long duration; }
    private final Map<Ability, CD> map = new EnumMap<>(Ability.class);

    private CooldownState() {}

    /** Démarre (ou redémarre) le cooldown d'une capacité. */
    public void trigger(Ability a, long durationMs) {
        CD c = map.computeIfAbsent(a, k -> new CD());
        c.start = System.currentTimeMillis();
        c.duration = durationMs;
    }

    /** Fraction de cooldown RESTANTE [0..1] (0 = prêt, 1 = vient d'être lancé). */
    public float fraction(Ability a, long now) {
        CD c = map.get(a);
        if (c == null || c.duration <= 0) return 0f;
        long el = now - c.start;
        if (el >= c.duration) return 0f;
        return 1f - (el / (float) c.duration);
    }

    /** Millisecondes restantes avant que la capacité soit prête. */
    public long remainingMs(Ability a, long now) {
        CD c = map.get(a);
        if (c == null) return 0L;
        return Math.max(0L, c.duration - (now - c.start));
    }

    public void clear() { map.clear(); }
}
