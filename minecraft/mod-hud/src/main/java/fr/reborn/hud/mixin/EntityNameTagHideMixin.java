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
 */
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagHideMixin {

    @Inject(
        method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
        at = @At("HEAD"), cancellable = true)
    private void reborn$hidePlayerNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof Player && TablistData.hasData()) {
            ci.cancel();
        }
    }
}
