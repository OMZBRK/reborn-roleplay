package com.reborn.shinobicore.ko.injury;

/**
 * The five injury kinds tracked per body part.
 *
 * <p>Picked by {@code KoListener.diagnose()} from the
 * {@link DamageOrigin} of the hit that caused the wound:
 * <ul>
 *   <li>Taijutsu / Kenjutsu strikes that kill produce
 *       {@code HEMATOME} or {@code PLAIE} depending on weapon.</li>
 *   <li>{@code KATON} and fire/lava ticks → {@code BRULURE}.</li>
 *   <li>{@code DOTON}, {@code RAITON}, falls → {@code OS_CASSE}.</li>
 *   <li>{@code INFECTION} is generated only by the healing decay
 *       ticker for very-old untreated wounds.</li>
 * </ul>
 */
public enum InjuryType {

    HEMATOME ("Hématome"),
    BRULURE  ("Brûlure"),
    OS_CASSE ("Os cassé"),
    INFECTION("Infection"),
    PLAIE    ("Plaie");

    private final String label;
    InjuryType(String label) { this.label = label; }
    public String label() { return label; }
}
