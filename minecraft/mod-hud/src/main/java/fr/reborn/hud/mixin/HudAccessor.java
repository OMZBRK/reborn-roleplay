package fr.reborn.hud.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Écriture du flag privé {@code isHidden} de {@link Hud} (26.2 a sorti le HUD
 * in-game de {@code Gui} vers {@code Hud}, et {@code Options.hideGui} a disparu).
 *
 * <p>Remplace l'ancien {@code mc.options.hideGui = true} : les écrans perso
 * (sélection / création / loading) masquent vie/faim/xp/armure/hotbar/crosshair
 * en posant ce flag à l'entrée et le restaurant à la sortie. Lecture = getter
 * public {@code Hud#isHidden()}.
 */
@Mixin(Hud.class)
public interface HudAccessor {

    @Accessor("isHidden")
    void reborn$setHidden(boolean hidden);

    /**
     * Sous-titres vanilla (option d'accessibilité) : la colonne bas-droite du lot 2 remonte au-dessus de leurs cases
     * ({@code notif/BasDroite}, avec {@link SousTitresAccessor}).
     */
    @Accessor("subtitleOverlay")
    SubtitleOverlay reborn$sousTitres();

    /**
     * Minuteur du titre vanilla (ticks restants) : pendant un texte au centre ({@code recit/RecitHud}), le titre est
     * masqué et son minuteur attend — un titre du serveur (« Épuisement », « Bienvenue »…) passe après, en entier.
     */
    @Accessor("titleTime")
    int reborn$titreTemps();

    @Accessor("titleTime")
    void reborn$titreTemps(int ticks);
}
