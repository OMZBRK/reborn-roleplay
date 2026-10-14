package fr.reborn.hud.mixin;

import fr.reborn.hud.allure.Allure;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Le champ de vision vanilla suit l'attribut de vitesse : la marche Reborn (× 0,75) rétrécirait la vue en permanence
 * et chaque changement d'allure ferait « zoomer » l'écran. On retire le modificateur {@code reborn:allure} du calcul :
 * la vue reste celle de Minecraft (marche normale, léger élargissement en trot et en sprint).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerFovMixin {

    @Redirect(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double reborn$vitesseSansAllure(AbstractClientPlayer self, Holder<Attribute> attr) {
        double v = self.getAttributeValue(attr);
        if (attr != Attributes.MOVEMENT_SPEED) return v;
        AttributeInstance a = self.getAttribute(attr);
        AttributeModifier m = a == null ? null : a.getModifier(Allure.CLE);
        if (m == null || m.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL || m.amount() <= -0.95) return v;
        return v / (1.0 + m.amount());
    }
}
