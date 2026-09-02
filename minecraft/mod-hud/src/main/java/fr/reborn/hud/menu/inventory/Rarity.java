package fr.reborn.hud.menu.inventory;

/**
 * Rareté d'un objet — palette « puissance » façon Zenkai / MMO
 * (gris / bleu / violet / or). Colore le liseré de la case et le nom.
 */
public enum Rarity {
    COMMUN("Commun", 0xFF9D9D9D),
    RARE("Rare", 0xFF3B82F6),
    EPIQUE("Épique", 0xFFA335EE),
    LEGENDAIRE("Légendaire", 0xFFFF8000);

    public final String label;
    public final int color;

    Rarity(String label, int color) {
        this.label = label;
        this.color = color;
    }

    /** Mappe un nom (insensible à la casse) ; défaut = COMMUN. */
    public static Rarity fromName(String s) {
        if (s == null) return COMMUN;
        try {
            return Rarity.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return COMMUN;
        }
    }
}
