package fr.reborn.hud.mixin;

import fr.reborn.hud.mur.MurFace;
import fr.reborn.hud.mur.MurRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Ajoute à l'état de rendu d'un avatar la face du mur sur lequel il marche, sa bascule, son cap (lot 5, essai C0) et le
 * lacet de la paroi, interpolé pendant un virage de coin, et le lacet debout visé par une sortie animée (étape C1a).
 */
@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMurMixin implements MurRenderState {

    @Unique
    private MurFace reborn$murFace;
    @Unique
    private float reborn$murBascule;
    @Unique
    private float reborn$murCap;
    @Unique
    private float reborn$murLacet;
    @Unique
    private float reborn$murDebout = Float.NaN;

    @Override
    public MurFace reborn$murFace() { return reborn$murFace; }

    @Override
    public float reborn$murBascule() { return reborn$murBascule; }

    @Override
    public float reborn$murCap() { return reborn$murCap; }

    @Override
    public float reborn$murLacet() { return reborn$murLacet; }

    @Override
    public float reborn$murDebout() { return reborn$murDebout; }

    @Override
    public void reborn$mur(MurFace face, float bascule, float cap, float lacet, float debout) {
        reborn$murFace = face;
        reborn$murBascule = bascule;
        reborn$murCap = cap;
        reborn$murLacet = lacet;
        reborn$murDebout = debout;
    }
}
