package fr.reborn.hud.menu.settings;

import java.util.function.Consumer;

/**
 * Page de réglages décrite par une fonction (le DSL de {@link SectionedTab} : {@code bascule}, {@code curseur},
 * {@code choix}, {@code liste}, {@code action}…), rejouée à chaque passe comme un onglet ordinaire. Sert aux
 * sous-catégories courtes des onglets du gabarit o2 (Graphismes, Interface, Audio, Compte).
 */
public final class PageReglages extends SectionedTab {

    private final Consumer<PageReglages> contenu;

    public PageReglages(Consumer<PageReglages> contenu) {
        this.contenu = contenu;
    }

    @Override
    protected void build() {
        contenu.accept(this);
    }
}
