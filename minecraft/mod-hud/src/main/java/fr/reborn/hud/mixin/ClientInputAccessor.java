package fr.reborn.hud.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accès en écriture au champ {@code moveVector} de {@link ClientInput}.
 *
 * <p>En 26.1 {@code moveVector} est déclaré sur {@code ClientInput} (superclasse
 * de {@code KeyboardInput}). Mixin ne résout pas les {@code @Shadow} de champs
 * hérités : on passe donc par un {@code @Accessor} sur la classe qui déclare le
 * champ, puis on caste l'instance {@code KeyboardInput} vers cette interface.
 * La lecture se fait via le getter public {@code getMoveVector()} — seul le
 * setter manque côté vanilla, d'où cet accessor.
 */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("moveVector")
    void reborn$setMoveVector(Vec2 moveVector);
}
