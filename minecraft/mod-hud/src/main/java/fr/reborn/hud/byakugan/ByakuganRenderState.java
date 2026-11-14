package fr.reborn.hud.byakugan;

/**
 * Ajouté à {@code LivingEntityRenderState} par mixin : l'entité est-elle vue au Byakugan pour ce rendu (corps et réseau
 * en couleurs-clés) ? Porte-t-elle elle-même le Byakugan, vue de près par un autre (yeux pâles) ?
 */
public interface ByakuganRenderState {
    boolean reborn$byakugan();

    void reborn$setByakugan(boolean value);

    boolean reborn$signe();

    void reborn$setSigne(boolean value);
}
