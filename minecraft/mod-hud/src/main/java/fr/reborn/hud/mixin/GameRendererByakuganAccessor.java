package fr.reborn.hud.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accès au filtre plein écran (post effect) du jeu, privé en 26.2 : sert au négatif du Byakugan. */
@Mixin(GameRenderer.class)
public interface GameRendererByakuganAccessor {

    @Invoker("setPostEffect")
    void reborn$setPostEffect(Identifier id);
}
