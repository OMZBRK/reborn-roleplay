package fr.reborn.hud.menu.fiche;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Un onglet de la fiche. {@link #layout} est appelé une seule fois par mise en page (init, redimensionnement,
 * nouveau paquet) ; les méthodes de dessin ne dessinent que ce qui bouge, plus le texte.
 */
interface Onglet {

    /** Positions, textes découpés, listes triées : calculés ici seulement. */
    void layout(FicheScreen s);

    /** Fond cuit de l'onglet (1280×540). */
    Identifier fond();

    /** Couleur de la rangée 0 du ciel (bandeau du haut si l'écran dépasse 540 GUI). */
    default int cielHaut() { return FicheArt.CIEL_HAUT; }

    /** Strate du décor, derrière le personnage : lumières, coulées, lanternes, objets. */
    void scene(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my);

    /** Devant le personnage : hitodamas, sceaux, panneaux et texte. */
    void avant(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my);

    /** Strate du dessus : infobulle. */
    default void infobulle(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {}

    /** Clic (bouton 0 gauche, 1 droit). Renvoie vrai s'il est consommé. */
    boolean click(FicheScreen s, double mx, double my, int button, boolean shift);

    default boolean scroll(FicheScreen s, double mx, double my, double dy) { return false; }

    default boolean key(FicheScreen s, int key, boolean shift) { return false; }

    default void relache(FicheScreen s) {}

    default boolean glisse(FicheScreen s, double mx, double my) { return false; }

    /** En entrant dans l'onglet (après le changement). */
    default void entrer(FicheScreen s) {}

    /** x des pieds du personnage. */
    default int persoX(FicheScreen s) { return s.lay().cx; }

    float lacet();

    default float tangage() { return 8f; }

    default boolean rotationBloquee() { return false; }

    /** Aides en bas à droite : paires {touche, action} (action en minuscules, Inter 8). */
    String[][] aides();

    /** Planches pendues aux perches de bambou (Identité, Techniques, État). */
    default boolean perches() { return true; }

    /** Braises aux pieds (null : aucune). */
    default FicheScene.Braises braises() { return null; }
}
