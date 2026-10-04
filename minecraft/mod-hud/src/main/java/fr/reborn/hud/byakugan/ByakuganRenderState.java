package fr.reborn.hud.byakugan;

/** Ajouté à {@code LivingEntityRenderState} par mixin : l'entité est-elle vue au Byakugan pour ce rendu ? */
public interface ByakuganRenderState {
    boolean reborn$byakugan();

    void reborn$setByakugan(boolean value);
}
