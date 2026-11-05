package fr.reborn.hud.menu.settings;

/**
 * Onglet des Paramètres découpé en <b>sous-catégories</b> (gabarit o2 du lot 3, format de Ketto : la sous-liste sur la
 * planche de gauche, les lignes de la sous-catégorie choisie sur la planche de droite, son titre en ArcadePix or avec
 * « ↺ Réinitialiser la section »).
 *
 * <p>C'est le <b>point d'accroche</b> entre l'écran ({@code menu/screens/ConfigShellScreen}) et le contenu d'un onglet :
 * un {@link SettingsTab} qui implémente aussi {@code SousListe} montre sa sous-liste ; sinon l'écran l'affiche comme une
 * seule sous-catégorie. L'onglet COMMANDES instancie {@code ControlsTab(Screen parent)} : pour avoir ses
 * sous-catégories (Déplacement, Combat, Monde…), il lui suffit d'implémenter cette interface. Après
 * {@link #choisirSous}, l'écran rappelle {@link SettingsTab#layout} (le contenu montré change) et remet le défilement en
 * haut.
 */
public interface SousListe {

    /** Nombre de sous-catégories (visibles ou non). */
    int nombreSous();

    /** Libellé de la sous-catégorie {@code i} dans la sous-liste (casse normale, accents permis). */
    String nomSous(int i);

    /** Sous-catégorie montrée dans la sous-liste (ex. « Staff » réservée au staff). */
    default boolean sousVisible(int i) { return true; }

    /** Sous-catégorie affichée. */
    int sousChoisie();

    /** Affiche la sous-catégorie {@code i} (l'écran refait la mise en page ensuite). */
    void choisirSous(int i);

    /** Titre de la planche de droite (mis en capitales en ArcadePix) ; par défaut, le nom de la sous-catégorie. */
    default String titreSous() { return nomSous(sousChoisie()); }

    /** « ↺ Réinitialiser la section » proposé pour la sous-catégorie affichée. */
    default boolean reinitialisable() { return false; }

    /** Remet la sous-catégorie affichée à ses défauts (après la confirmation de l'écran). */
    default void reinitialiserSous() {}
}
