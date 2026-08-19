package com.reborn.shinobicore.ko.injury;

/**
 * The 16 body locations the KO {@code /état} system tracks.
 *
 * <p>Each constant carries a French display label (used in
 * {@code EtatGui} item names) and the chest-GUI slot it occupies in
 * the silhouette layout. The slot constants are kept here so the
 * silhouette is described in one place.
 *
 * <h2>Layout (6-row chest)</h2>
 * <pre>
 * row 0 |       [tG][tD]                          cols 3, 4   (Tête L / R)
 * row 1 |       [cG][cD]                          cols 3, 4   (Cou  L / R)
 * row 2 |    [eG][bsG][bsD][eD]                   cols 2..5   (Épaule + Buste L/R)
 * row 3 |    [brG][vG][vD][brD]                   cols 2..5   (Bras + Ventre L/R)
 * row 4 |       [jG][jD]                          cols 3, 4   (legs adjacent)
 * row 5 |       [pG][pD]                  [×]     cols 3, 4   + close at col 8
 * </pre>
 */
public enum BodyPart {

    TETE_GAUCHE    ("Tête gauche",     3),
    TETE_DROIT     ("Tête droite",     4),
    COU_GAUCHE     ("Cou gauche",     12),
    COU_DROIT      ("Cou droit",      13),
    EPAULE_GAUCHE  ("Épaule gauche",  20),
    BUSTE_GAUCHE   ("Buste gauche",   21),
    BUSTE_DROIT    ("Buste droit",    22),
    EPAULE_DROITE  ("Épaule droite",  23),
    BRAS_GAUCHE    ("Bras gauche",    29),
    VENTRE_GAUCHE  ("Ventre gauche",  30),
    VENTRE_DROIT   ("Ventre droit",   31),
    BRAS_DROIT     ("Bras droit",     32),
    JAMBE_GAUCHE   ("Jambe gauche",   39),
    JAMBE_DROITE   ("Jambe droite",   40),
    PIED_GAUCHE    ("Pied gauche",    48),
    PIED_DROIT     ("Pied droit",     49);

    private final String label;
    private final int    slot;

    BodyPart(String label, int slot) {
        this.label = label;
        this.slot  = slot;
    }

    public String label() { return label; }
    public int    slot()  { return slot;  }

    /** Match the {@code BodyPart} that owns the given chest slot, or
     *  {@code null} when the slot is decoration. */
    public static BodyPart bySlot(int slot) {
        for (BodyPart p : values()) if (p.slot == slot) return p;
        return null;
    }

    /** Lenient enum lookup that maps legacy values from older saves
     *  onto the current split-pair set:
     *  <ul>
     *    <li>{@code TETE}   → {@link #TETE_GAUCHE}</li>
     *    <li>{@code BUSTE}  → {@link #BUSTE_GAUCHE}</li>
     *    <li>{@code VENTRE} → {@link #VENTRE_GAUCHE}</li>
     *  </ul>
     *  Returns {@code null} for genuinely unknown names so callers
     *  can drop malformed rows. */
    public static BodyPart parseLenient(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        try { return BodyPart.valueOf(s); }
        catch (IllegalArgumentException ignored) { /* fall through */ }
        return switch (s) {
            case "TETE"   -> TETE_GAUCHE;
            case "BUSTE"  -> BUSTE_GAUCHE;
            case "VENTRE" -> VENTRE_GAUCHE;
            default       -> null;
        };
    }
}
