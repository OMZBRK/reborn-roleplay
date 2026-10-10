package fr.reborn.hud.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequins de la scène de sélection : sans la mention « PNJ » sous le nom, immobiles. */
@Mixin(Mannequin.class)
public interface MannequinAccessor {

    @Invoker("setHideDescription")
    void reborn$setHideDescription(boolean hide);

    @Invoker("setImmovable")
    void reborn$setImmovable(boolean immovable);
}
