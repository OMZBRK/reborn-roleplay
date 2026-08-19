package com.reborn.shinobicore.ko.injury;

/**
 * What kind of attack produced an injury.
 *
 * <p>Used by the {@code EtatGui} to flavour each entry's lore line
 * ("Origine : Katon — Brûlure — Important") and by the future
 * Iryō healing techniques when calculating recovery time.
 *
 * <p>Mapping is auto-detected by {@code KoListener.diagnose()}:
 * <ul>
 *   <li>Sword/axe → {@link #KENJUTSU}</li>
 *   <li>Fist / unarmed melee → {@link #TAIJUTSU}</li>
 *   <li>{@code TechniquesService} ability marker → matching element</li>
 *   <li>Fire / lava / hot floor → {@link #FEU}</li>
 *   <li>Fall damage → {@link #CHUTE}</li>
 *   <li>Anything else → {@link #AUTRE}</li>
 * </ul>
 */
public enum DamageOrigin {

    TAIJUTSU ("Taijutsu"),
    KENJUTSU ("Kenjutsu"),
    KATON    ("Katon"),
    DOTON    ("Doton"),
    SUITON   ("Suiton"),
    FUUTON   ("Fūton"),
    RAITON   ("Raiton"),
    FEU      ("Feu"),
    CHUTE    ("Chute"),
    POISON   ("Poison"),
    AUTRE    ("Autre");

    private final String label;
    DamageOrigin(String label) { this.label = label; }
    public String label() { return label; }
}
