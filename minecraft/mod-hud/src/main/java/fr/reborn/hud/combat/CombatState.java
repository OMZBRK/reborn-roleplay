package fr.reborn.hud.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * État client du combat taïjutsu, alimenté par le canal {@code reborn:combat}.
 * Lu par {@link CombatHud} au rendu. Purement visuel : le serveur reste
 * autoritaire (dégâts, stamina, hits).
 *
 * <ul>
 *   <li><b>Stamina</b> : dernière valeur poussée (anneau curseur). Masquée quand
 *       pleine et inactive depuis {@link #STAMINA_HIDE_MS}.</li>
 *   <li><b>Damage indicators</b> : nombres flottants au-dessus des cibles, montent
 *       + s'effacent sur {@link #DMG_LIFE_MS}.</li>
 *   <li><b>Combo</b> : cumul des dégâts de la session ; tenu {@link #COMBO_HOLD_MS}
 *       après le dernier coup, puis s'efface (affiche le total de session).</li>
 * </ul>
 */
public final class CombatState {

    public static final CombatState INSTANCE = new CombatState();

    public static final long DMG_LIFE_MS = 1400L;
    public static final long COMBO_HOLD_MS = 2500L;
    public static final long COMBO_FADE_MS = 500L;
    public static final long STAMINA_HIDE_MS = 1600L;

    /** Un nombre de dégâts flottant au-dessus d'une entité. */
    public static final class DamageIndicator {
        public final int entityId;
        public final float amount;
        public final long spawnMs;
        DamageIndicator(int entityId, float amount, long spawnMs) {
            this.entityId = entityId; this.amount = amount; this.spawnMs = spawnMs;
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
        indicators.add(new DamageIndicator(entityId, amount, now));
        // Nouveau combo si le précédent a expiré.
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

    public boolean staminaVisible(long now) {
        return staminaFraction() < 0.999f || (now - lastStaminaMs) < STAMINA_HIDE_MS;
    }

    /** Alpha du combo (1 pendant HOLD, fondu sur FADE, 0 ensuite). */
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

    /** Purge les indicateurs expirés et retourne la liste vivante. */
    public List<DamageIndicator> liveIndicators(long now) {
        for (Iterator<DamageIndicator> it = indicators.iterator(); it.hasNext(); ) {
            if (now - it.next().spawnMs > DMG_LIFE_MS) it.remove();
        }
        return indicators;
    }
}
