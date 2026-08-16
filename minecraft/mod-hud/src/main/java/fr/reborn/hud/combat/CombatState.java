package fr.reborn.hud.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * État client du combat taïjutsu, alimenté par le canal {@code reborn:combat}.
 * Lu par {@link CombatHud}. Purement visuel ; le serveur reste autoritaire.
 */
public final class CombatState {

    public static final CombatState INSTANCE = new CombatState();

    public static final long DMG_LIFE_MS = 1100L;
    public static final long COMBO_HOLD_MS = 2500L;
    public static final long COMBO_FADE_MS = 500L;
    /** Le réticule de combat reste visible ce délai après la dernière activité. */
    public static final long COMBAT_MODE_MS = 5000L;

    /** Nombre de dégâts flottant au-dessus d'une entité (avec dispersion). */
    public static final class DamageIndicator {
        public final int entityId;
        public final float amount;
        public final long spawnMs;
        public final float dx;   // dispersion horizontale (px écran)
        public final float dy;   // dispersion verticale de départ (px écran)
        DamageIndicator(int entityId, float amount, long spawnMs, float dx, float dy) {
            this.entityId = entityId; this.amount = amount; this.spawnMs = spawnMs;
            this.dx = dx; this.dy = dy;
        }
    }

    private final List<DamageIndicator> indicators = new ArrayList<>();

    private float staminaCurrent = 100f;
    private float staminaMax = 100f;
    private long lastStaminaMs = 0L;

    private double comboTotal = 0.0;
    private long lastHitMs = 0L;

    private CombatState() {}

    // ── mutations (thread client) ──
    public void onHit(int entityId, float amount, long now) {
        float dx = (float) (Math.random() * 26.0 - 13.0);
        float dy = (float) (Math.random() * 8.0 - 4.0);
        indicators.add(new DamageIndicator(entityId, amount, now, dx, dy));
        if (now - lastHitMs > COMBO_HOLD_MS + COMBO_FADE_MS) comboTotal = 0.0;
        comboTotal += amount;
        lastHitMs = now;
    }

    public void onStamina(float current, float max, long now) {
        this.staminaCurrent = current;
        this.staminaMax = max <= 0 ? 100f : max;
        this.lastStaminaMs = now;
    }

    public void clear() {
        indicators.clear();
        comboTotal = 0.0;
        lastHitMs = 0L;
        staminaCurrent = staminaMax;
        lastStaminaMs = 0L;
    }

    // ── lecture (rendu) ──
    public float staminaFraction() {
        return Math.max(0f, Math.min(1f, staminaCurrent / staminaMax));
    }

    /** Combat « actif » = activité récente (coup porté ou stamina non pleine). */
    public boolean inCombat(long now) {
        long last = Math.max(lastHitMs, lastStaminaMs);
        return last != 0L && (now - last) < COMBAT_MODE_MS;
    }

    /** Alpha du réticule de combat (1 puis fondu sur les 800 dernières ms). */
    public float combatModeAlpha(long now) {
        long last = Math.max(lastHitMs, lastStaminaMs);
        if (last == 0L) return 0f;
        long age = now - last;
        if (age >= COMBAT_MODE_MS) return 0f;
        long fadeStart = COMBAT_MODE_MS - 800L;
        return age <= fadeStart ? 1f : 1f - (age - fadeStart) / 800f;
    }

    public float comboAlpha(long now) {
        if (comboTotal <= 0) return 0f;
        long age = now - lastHitMs;
        if (age <= COMBO_HOLD_MS) return 1f;
        if (age <= COMBO_HOLD_MS + COMBO_FADE_MS) {
            return 1f - (age - COMBO_HOLD_MS) / (float) COMBO_FADE_MS;
        }
        return 0f;
    }

    public double comboTotal() { return comboTotal; }

    public List<DamageIndicator> liveIndicators(long now) {
        for (Iterator<DamageIndicator> it = indicators.iterator(); it.hasNext(); ) {
            if (now - it.next().spawnMs > DMG_LIFE_MS) it.remove();
        }
        return indicators;
    }
}
