package fr.reborn.hud.mixin;

import net.minecraft.client.gui.Hud;
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
}
