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
    public static final long DASH_CD_MS = 7000L;
    /** Durée du CD du double saut (doit matcher {@code KERIOX_DASH_COOLDOWN_MS} serveur). */
    public static final long DOUBLE_JUMP_CD_MS = 10000L;
    /** Durée du CD du saut de chakra (doit matcher le serveur) — long, pas de coût. */
    public static final long CHAKRA_JUMP_CD_MS = 35000L;

    /** Capacités affichées dans le HUD de cooldowns. {@code glyph} = placeholder
     *  tant qu'aucune icône {@code textures/gui/ability/<name>.png} n'est livrée. */
    public enum Ability {
        // alwaysShow=false → l'icône n'apparaît QUE pendant le cooldown, puis disparaît.
        // frames/frameMs : anim de l'icône (bande verticale reborn:textures/gui/ability/<name>.png,
        // frames empilées de haut en bas, chaque frame = côté de l'icône interne).
        DASH("Dash", 0xFF7FB4FF, ">>", false, 4, 100L),
        DOUBLE_JUMP("Double saut", 0xFF7FE0B4, "^^", false, 1, 0L),
        CHAKRA_JUMP("Saut de chakra", 0xFF9B7FE0, "^", false, 1, 0L),
        // Indicateurs d'ÉTAT (pas de cooldown) : visibles tant qu'actifs via setActive(...).
        // COMBAT = « en combat » (activité : coup porté/reçu récemment), COURSE_CHAKRA =
        // Naruto Run active. Icône = reborn:textures/gui/ability/{combat,course_chakra}.png
        // quand livrée (sinon glyphe placeholder). Textures à fournir par l'user.
        COMBAT("En combat", 0xFFFF6B5C, "!!", false, 1, 0L),
        COURSE_CHAKRA("Course de chakra", 0xFF62D0FF, "->", false, 1, 0L);

        public final String label;
        public final int color;
        public final String glyph;
        /** Si vrai, l'icône reste affichée même prête (sinon visible seulement en CD). */
        public final boolean alwaysShow;
        /** Nombre de frames de l'icône (1 = statique) et durée par frame (ms). */
        public final int frames;
        public final long frameMs;
        Ability(String label, int color, String glyph, boolean alwaysShow, int frames, long frameMs) {
            this.label = label; this.color = color; this.glyph = glyph; this.alwaysShow = alwaysShow;
            this.frames = frames; this.frameMs = frameMs;
        }
    }

    private static final class CD { long start; long duration; }
    private final Map<Ability, CD> map = new EnumMap<>(Ability.class);
    /** Modes actifs (combat, course de chakra) — affichés tant qu'actifs, sans cooldown. */
    private final java.util.Set<Ability> active = java.util.EnumSet.noneOf(Ability.class);

    private CooldownState() {}

    /** Active/désactive un indicateur de mode (icône visible tant qu'actif). */
    public void setActive(Ability a, boolean on) {
        if (on) active.add(a); else active.remove(a);
    }

    /** Un mode-indicateur est-il actif ? */
    public boolean isActive(Ability a) { return active.contains(a); }

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

    public void clear() { map.clear(); active.clear(); }
}
