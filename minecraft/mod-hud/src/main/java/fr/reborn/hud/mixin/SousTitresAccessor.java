package fr.reborn.hud.mixin;

import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Sous-titres vanilla affichés (lot 2, colonne bas-droite) : {@code audibleSubtitles} est vidée puis remplie à chaque
 * image par {@link SubtitleOverlay#extractRenderState} (élément vanilla, dessiné avant les nôtres) ; sa taille donne le
 * nombre de cases, donc la hauteur de la pile (ligne i centrée à {@code guiHeight - 35 - 10 i}, case de ±5 unités).
 * Lue seulement si l'option est active (sinon la liste n'est plus tenue à jour).
 */
@Mixin(SubtitleOverlay.class)
public interface SousTitresAccessor {

    @Accessor("audibleSubtitles")
    List<?> reborn$audibles();
}
