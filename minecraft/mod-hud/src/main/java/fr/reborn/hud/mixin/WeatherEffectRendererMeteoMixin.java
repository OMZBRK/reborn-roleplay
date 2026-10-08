package fr.reborn.hud.mixin;

import fr.reborn.hud.meteo.MeteoClient;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Météo « pluie » : même dessin pixel art que la pluie du jeu, mais des gouttes blanc-bleu pâle et plus transparentes
 * ({@code textures/environment/pluie.png}) au lieu du bleu saturé. La pluie normale du jeu garde sa texture.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMeteoMixin {

    private static final Identifier REBORN$PLUIE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/environment/pluie.png");
    private static final Identifier REBORN$VANILLE = Identifier.withDefaultNamespace("textures/environment/rain.png");

    @Redirect(method = "render",
            at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
                    target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;RAIN_LOCATION:Lnet/minecraft/resources/Identifier;"))
    private Identifier reborn$texturePluie() {
        return MeteoClient.pluie3D() ? REBORN$PLUIE : REBORN$VANILLE;
    }
}
