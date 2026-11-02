package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.tablist.TablistData;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Masque le <b>pseudo Minecraft</b> flottant au-dessus des JOUEURS quand les nametags Reborn le remplacent
 * ({@link fr.reborn.hud.nametag.Nametags} : « Prénom Clan » ou « ??? », nametags de la douane). Gardé sur
 * {@link TablistData#hasData()} (serveur Reborn) ou {@code DouaneClient.nametagsActifs()} ; ailleurs, en solo, pseudos
 * vanilla normaux. Seuls les {@link Player} : mannequins et entités à nom personnalisé gardent leur nom.
 *
 * <p><b>Point visé (31/10)</b> : {@code AvatarRenderer#shouldShowName(Avatar, double)}, qui porte la décision pour
 * tout avatar en 26.2 (ses ponts {@code shouldShowName(LivingEntity, double)} et {@code (Entity, double)} y renvoient).
 * Il couvre les deux chemins qui écrivent un nom :
 * <ul>
 *   <li>vanilla : {@code EntityRenderer#extractNameTags(…, double, double)} (finale) n'écrit {@code nameTag} et son
 *   point d'attache que si {@code shouldShowName} ; sans point d'attache, la ligne de score sous le nom n'est pas
 *   dessinée non plus ({@code submitNameTag} sort sur une attache nulle) ;</li>
 *   <li>EntityCulling (« noms à travers les murs », actif dans le modpack) : pour un joueur caché, il écrit lui-même
 *   {@code nameTag} hors de {@code extractNameTags}, mais seulement si son invoker
 *   {@code LivingEntityRenderer#shouldShowName} (qui aboutit ici) répond vrai.</li>
 * </ul>
 * L'ancienne version annulait {@code extractNameTags} en HEAD : elle sautait aussi
 * {@code distanceToCameraSq = …} (première écriture de la méthode), d'où des ombres de joueurs jamais atténuées et
 * calculées au-delà de 16 blocs, et l'icône de Plasmo Voice sans sa coupure à 64 blocs. La distance se calcule de
 * nouveau. Emotecraft et Sodium Extra visent {@code LivingEntityRenderer#shouldShowName} (HEAD, réponse fausse
 * uniquement) : appelé ensuite par {@code super}, sans conflit.
 */
@Mixin(AvatarRenderer.class)
public abstract class EntityNameTagHideMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void reborn$hidePlayerNameTag(Avatar entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && (TablistData.hasData() || fr.reborn.hud.douane.DouaneClient.nametagsActifs())) {
            cir.setReturnValue(false);
        }
    }
}
