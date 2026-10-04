package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.ByakuganRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Ajoute le drapeau « vu au Byakugan » à l'état de rendu des entités vivantes. */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateByakuganMixin implements ByakuganRenderState {

    @Unique
    private boolean reborn$byakugan;

    @Override
    public boolean reborn$byakugan() { return reborn$byakugan; }

    @Override
    public void reborn$setByakugan(boolean value) { reborn$byakugan = value; }
}
