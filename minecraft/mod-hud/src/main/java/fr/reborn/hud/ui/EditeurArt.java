package fr.reborn.hud.ui;

import net.minecraft.resources.Identifier;

/**
 * Textures de l'éditeur de HUD ({@code textures/gui/editeur/*}, générées par {@code tools/gen_editeur_textures.py} ;
 * tailles et ancres = contrat en tête du générateur). Signature : l'équerre et le cordeau à encre du charpentier.
 */
public final class EditeurArt {

    private EditeurArt() {}

    private static Identifier tex(String n) { return DaKit.tex("editeur", n); }

    public static final Identifier PANNEAU = tex("panneau"), LANGUETTE = tex("languette"), PIECES = tex("pieces"),
        BOUTONS = tex("boutons"), LIGNES = tex("lignes"), CHAMP = tex("champ"), AIDE = tex("aide");
}
