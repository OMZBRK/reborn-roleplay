package fr.reborn.hud.menu.inventory;

import fr.reborn.hud.menu.Colors;

/**
 * Catégories d'objets du sac ninja. Chaque catégorie a un onglet-filtre (label
 * ASCII pour la police pixel {@code arcade}), un libellé FR accentué (police
 * vanilla, tooltips) et une couleur d'accent (plus de kanji-repère depuis le 18/10, DA §3).
 */
public enum ItemCategory {
    ARMES("ARMES", "Armes", 0xFFE0574A),
    CONSOMMABLES("CONSO", "Consommables", 0xFF7FD18B),
    PARCHEMINS("PARCHEMINS", "Parchemins", 0xFFD9A95E),
    MATERIAUX("MATERIAUX", "Matériaux", 0xFF9BB0C4),
    COSMETIQUES("COSMETIQUES", "Cosmétiques", 0xFFB98AD9),
    SACS("SACS", "Sacs", 0xFFC89B6A),
    QUETE("QUETE", "Quête", 0xFF5EC2D9);

    /** Label court ASCII (police pixel, onglets). */
    public final String tab;
    /** Libellé FR accentué (police vanilla, tooltip). */
    public final String label;
    /** Couleur d'accent de la catégorie (ARGB). */
    public final int color;

    ItemCategory(String tab, String label, int color) {
        this.tab = tab;
        this.label = label;
        this.color = color;
    }

    public int softColor() {
        return Colors.withAlpha(color, 0.16f);
    }
}
