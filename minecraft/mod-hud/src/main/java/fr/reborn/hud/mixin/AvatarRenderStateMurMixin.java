package fr.reborn.hud.mixin;

import fr.reborn.hud.mur.MurFace;
import fr.reborn.hud.mur.MurRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Ajoute à l'état de rendu d'un avatar la face du mur sur lequel il marche, sa bascule et son cap (lot 5, essai C0). */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMurMixin implements MurRenderState {

    @Unique
    private MurFace reborn$murFace;
    @Unique
    private float reborn$murBascule;
    @Unique
    private float reborn$murCap;

    @Override
    public MurFace reborn$murFace() { return reborn$murFace; }

    @Override
    public float reborn$murBascule() { return reborn$murBascule; }

    @Override
    public float reborn$murCap() { return reborn$murCap; }

    @Override
    public void reborn$mur(MurFace face, float bascule, float cap) {
        reborn$murFace = face;
        reborn$murBascule = bascule;
        reborn$murCap = cap;
    }
}
