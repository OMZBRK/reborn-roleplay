package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.tablist.TablistData;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Masque le <b>pseudo Minecraft</b> flottant au-dessus des JOUEURS : on annule
 * l'extraction du nametag ({@code EntityRenderer#extractNameTags}) pour les
 * {@link Player}, laissant {@code EntityRenderState#nameTag} à {@code null} → rien
 * n'est rendu. La plaque de nom RP ({@link fr.reborn.hud.nameplate.Nameplates})
 * prend le relais avec « Prénom [Clan] » ou « Inconnu », visible seulement de près.
 *
 * <p>Gardé sur {@link TablistData#hasData()} : uniquement quand on est sur un
 * serveur Reborn (données RP présentes). Sur un autre serveur / en solo, les
 * pseudos vanilla restent normaux. N'affecte QUE les joueurs — les mobs/armor
 * stands à nom custom (dummy, cinématique…) gardent leur nametag.
 *
 * <p><b>On cible la surcharge à 5 args {@code final}</b>
 * {@code extractNameTags(Entity, EntityRenderState, float, double, double)} — c'est
 * le vrai point d'écriture de {@code state.nameTag} (elle appelle {@code shouldShowName}
 * + {@code getNameTag} puis {@code putfield nameTag}). La surcharge à 3 args est
 * <b>redéfinie par {@code LivingEntityRenderer}</b> (dont hérite {@code PlayerRenderer})
 * → l'injecter ne s'appliquait PAS aux joueurs. La version 5 args est {@code final}
 * (non redéfinissable) et atteinte pour les joueurs via l'appel {@code super} de
 * LivingEntityRenderer → couvre tous les avatars, y compris les joueurs.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagHideMixin {

    @Inject(
        method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
        at = @At("HEAD"), cancellable = true)
    private void reborn$hidePlayerNameTag(Entity entity, EntityRenderState state, float partialTick,
                                          double x, double z, CallbackInfo ci) {
        if (entity instanceof Player && TablistData.hasData()) {
            ci.cancel();
        }
    }
}
