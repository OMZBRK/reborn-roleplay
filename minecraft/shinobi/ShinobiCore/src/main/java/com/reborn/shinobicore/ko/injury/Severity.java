package com.reborn.shinobicore.ko.injury;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * How serious an injury is.
 *
 * <p>Severity drives the {@code /soigner} flow — both how a heal
 * downgrades the injury AND how long the medic must wait before
 * applying the next stage of treatment on the same wound:
 *
 * <table>
 *   <tr><th>Severity</th><th>Heal goes to</th><th>Cooldown after heal</th></tr>
 *   <tr><td>URGENT</td>   <td>MOYEN</td>  <td>3 h    </td></tr>
 *   <tr><td>IMPORTANT</td><td>MOYEN</td>  <td>1 h 30 </td></tr>
 *   <tr><td>MOYEN</td>    <td>FAIBLE</td> <td>30 min </td></tr>
 *   <tr><td>FAIBLE</td>   <td>cleared</td><td>—      </td></tr>
 * </table>
 *
 * <p>Note that URGENT and IMPORTANT both land on MOYEN — the medic
 * stabilises a critical wound in a single intervention, but the
 * patient still needs follow-up rounds to fully recover.
 *
 * <p>The colour is used by {@code EtatGui} / {@code SoignerGui} to
 * tint the body-part marker.
 */
public enum Severity {

    FAIBLE   ("Faible",    NamedTextColor.GREEN,  /*healCdMs=*/0L),
    MOYEN    ("Moyen",     NamedTextColor.YELLOW,                30L * 60L * 1000L),
    IMPORTANT("Important", NamedTextColor.GOLD,                  90L * 60L * 1000L),
    URGENT   ("Urgent",    NamedTextColor.RED,                  180L * 60L * 1000L);

    private final String    label;
    private final TextColor color;
    private final long      healCooldownMillis;

    Severity(String label, TextColor color, long healCooldownMillis) {
        this.label              = label;
        this.color              = color;
        this.healCooldownMillis = healCooldownMillis;
    }

    public String    label() { return label; }
    public TextColor color() { return color; }

    /** Wait, in millis, after a {@code /soigner} that downgraded FROM
     *  this severity, before the same injury can be treated again. */
    public long healCooldownMillis() { return healCooldownMillis; }

    /** Map a 0..1 health-fraction lost in the killing blow to a severity. */
    public static Severity fromDamageFraction(double f) {
        if (f >= 0.50) return URGENT;
        if (f >= 0.25) return IMPORTANT;
        if (f >= 0.10) return MOYEN;
        return FAIBLE;
    }

    /** Severity after a successful {@code /soigner} — URGENT and
     *  IMPORTANT both collapse to MOYEN; MOYEN becomes FAIBLE; FAIBLE
     *  resolves to {@code null} (the injury should be removed). */
    public Severity soignerDowngrade() {
        return switch (this) {
            case URGENT    -> MOYEN;
            case IMPORTANT -> MOYEN;
            case MOYEN     -> FAIBLE;
            case FAIBLE    -> null;
        };
    }

    /** One step UP — used when a fresh KO worsens every pre-existing
     *  injury. URGENT can't go higher, so it stays. */
    public Severity upgrade() {
        return switch (this) {
            case FAIBLE    -> MOYEN;
            case MOYEN     -> IMPORTANT;
            case IMPORTANT -> URGENT;
            case URGENT    -> URGENT;
        };
    }

    /** Legacy single-step downgrade (URGENT → IMPORTANT → MOYEN →
     *  FAIBLE → null). Kept only for callers that haven't been
     *  ported to {@link #soignerDowngrade}. New code should use
     *  the soigner variant. */
    @Deprecated
    public Severity downgrade() {
        return switch (this) {
            case URGENT    -> IMPORTANT;
            case IMPORTANT -> MOYEN;
            case MOYEN     -> FAIBLE;
            case FAIBLE    -> null;
        };
    }
}
