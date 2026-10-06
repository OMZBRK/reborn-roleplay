package fr.reborn.hud.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Météo : réglage du mouvement des particules de poussière (portées par le vent, sans gravité ni freinage). */
@Mixin(Particle.class)
public interface ParticleMeteoAccessor {

    @Accessor("gravity")
    void reborn$setGravity(float gravity);

    @Accessor("friction")
    void reborn$setFriction(float friction);

    @Accessor("hasPhysics")
    void reborn$setHasPhysics(boolean hasPhysics);
}
