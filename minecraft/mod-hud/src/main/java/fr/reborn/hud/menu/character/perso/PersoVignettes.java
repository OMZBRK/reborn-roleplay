package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Accès public aux <b>poupées</b> ({@link PersoPoupee}) pour les écrans hors de la création : le personnage rendu une
 * fois en logiciel dans une texture puis affiché en un blit (cache par skin, angle, échelle). Sert aux vignettes du
 * tailleur (boutiques, 15/10) : chaque pièce portée sur <b>son propre corps</b>. Jamais d'entité factice.
 * {@link #liberer()} à la fermeture de l'écran.
 */
public final class PersoVignettes {

    private final PersoSkins skins;

    public PersoVignettes(String prefixe) { this.skins = new PersoSkins(prefixe); }

    /** Texture composée pour cette apparence ({@code null} : skin du joueur local). */
    public Identifier skin(SkinSpec spec) { return skins.get(spec, false); }

    /**
     * Dessine la poupée, pieds en (cx, piedsY) GUI, {@code s} GUI par texel ; lacet > 0 : flanc droit visible ;
     * {@code argb} : teinte (alpha = opacité, gris pour une pièce verrouillée).
     */
    public void dessiner(GuiGraphicsExtractor g, Identifier skin, boolean slim, float cx, float piedsY, float s, float lacet,
                         float tangage, int argb) {
        PersoPoupee.dessiner(g, skins, skin, slim, cx, piedsY, s, lacet, tangage, argb);
    }

    public void liberer() { skins.liberer(); }
}
