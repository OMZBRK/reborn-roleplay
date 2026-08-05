package fr.reborn.hud.mixin;

import fr.reborn.hud.crosshair.CrosshairManager;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link Gui} pour intercepter les render methods des
 * elements HUD vanilla et leur appliquer l'offset/scale/visibilite
 * configures.
 *
 * <p>Couvre :
 * <ul>
 *   <li>{@code renderHotbar} -> {@link HudElement#HOTBAR}</li>
 *   <li>{@code renderStatusBars} : split en 4 redirects independants sur
 *       renderArmor / renderHealthBar / renderFood / drawGuiTexture (air)
 *       -> {@link HudElement#ARMOR} / {@link HudElement#HEALTH} /
 *       {@link HudElement#HUNGER} / {@link HudElement#AIR}</li>
 *   <li>{@code renderExperienceBar} -> {@link HudElement#EXPERIENCE_BAR}</li>
 *   <li>{@code renderHeldItemTooltip} -> {@link HudElement#ACTION_BAR}</li>
 *   <li>{@code renderScoreboardSidebar} -> {@link HudElement#SCOREBOARD}</li>
 * </ul>
 *
 * <p><b>FIXME 26.1 (phase 2)</b> — le HUD vanilla est passé en <i>mode extraction</i> :
 * les méthodes ciblées ci-dessous ont été renommées / scindées / supprimées sur
 * {@code Gui}, donc ces {@code @Inject}/{@code @Redirect} ne s'appliqueront pas au
 * runtime tels quels (le code compile, mais Mixin échouera à résoudre les cibles).
 * Correspondances relevées par javap sur 26.1.2 :
 * <ul>
 *   <li>{@code renderCrosshair} → {@code extractCrosshair(GuiGraphicsExtractor, DeltaTracker)}</li>
 *   <li>{@code renderHotbar} → {@code extractHotbarAndDecorations} / {@code extractItemHotbar}</li>
 *   <li>{@code renderStatusBars} → scindé en {@code extractPlayerHealth(GuiGraphicsExtractor)},
 *       {@code extractArmor(GuiGraphicsExtractor, Player, int, int, int, int)} (static),
 *       {@code extractFood(GuiGraphicsExtractor, Player, int, int)},
 *       {@code extractVehicleHealth(GuiGraphicsExtractor)} — la variante par-cœur
 *       {@code renderHealthBar(...)} n'existe plus</li>
 *   <li>{@code renderExperienceBar} / {@code renderHeldItemTooltip} → supprimés
 *       (déplacés vers les {@code ContextualBarRenderer})</li>
 *   <li>{@code renderScoreboardSidebar} → {@code extractScoreboardSidebar(GuiGraphicsExtractor,
 *       DeltaTracker)} + {@code displayScoreboardSidebar(GuiGraphicsExtractor, Objective)}</li>
 *   <li>descripteurs à remapper : {@code net/minecraft/util/Identifier} →
 *       {@code net/minecraft/resources/Identifier}, {@code .../gui/hud/Gui} → {@code .../gui/Gui}</li>
 * </ul>
 * La stratégie « push/pop transform autour de chaque render d'élément » doit être
 * repensée pour le mode extraction (retained state) avant réactivation.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {

    // ------------ CROSSHAIR (viseur Reborn) ------------
    // À HEAD : si le viseur Reborn est actif, on le dessine et on annule le
    // crosshair vanilla. Le gating vue-1ère-personne est dans CrosshairManager.
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void reborn$crosshair(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrosshairManager.tryRender(ctx)) {
            ci.cancel();
        }
    }

    // ------------ HOTBAR ------------
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void reborn$pushHotbar(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.HOTBAR)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.HOTBAR);
    }

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void reborn$popHotbar(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.HOTBAR)) return;
        HudTransform.revert(ctx);
    }

    // ------------ STATUS BARS (split en 4 redirects) ------------

    // ARMOR (private static) : wrap l'invocation avec transform ARMOR, skip si invisible.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/Gui;renderArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/player/Player;IIII)V"))
    private void reborn$redirectArmor(GuiGraphicsExtractor ctx, Player player, int top, int rowOff, int armorIcon, int left) {
        if (!HudTransform.isVisible(HudElement.ARMOR)) return;
        HudTransform.apply(ctx, HudElement.ARMOR);
        InGameHudInvoker.reborn$invokeRenderArmor(ctx, player, top, rowOff, armorIcon, left);
        HudTransform.revert(ctx);
    }

    // HEALTH (private virtual) : wrap avec transform HEALTH, skip si invisible.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/Gui;renderHealthBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/player/Player;IIIIFIIIZ)V"))
    private void reborn$redirectHealth(Gui self, GuiGraphicsExtractor ctx, Player player, int x, int y, int lines, int regenOff, float maxHealth, int lastHealth, int health, int absorption, boolean blinking) {
        if (!HudTransform.isVisible(HudElement.HEALTH)) return;
        HudTransform.apply(ctx, HudElement.HEALTH);
        ((InGameHudInvoker) self).reborn$invokeRenderHealthBar(ctx, player, x, y, lines, regenOff, maxHealth, lastHealth, health, absorption, blinking);
        HudTransform.revert(ctx);
    }

    // HUNGER (private virtual) : wrap avec transform HUNGER, skip si invisible.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/Gui;renderFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/player/Player;II)V"))
    private void reborn$redirectFood(Gui self, GuiGraphicsExtractor ctx, Player player, int top, int right) {
        if (!HudTransform.isVisible(HudElement.HUNGER)) return;
        HudTransform.apply(ctx, HudElement.HUNGER);
        ((InGameHudInvoker) self).reborn$invokeRenderFood(ctx, player, top, right);
        HudTransform.revert(ctx);
    }

    // AIR (drawn inline via drawGuiTexture inside renderStatusBars) : redirect chaque
    // appel direct, push/pop autour. Les seuls drawGuiTexture (Identifier, int*4) direct
    // dans renderStatusBars sont les 2 sprites de bulles (full + popping) sous l'eau.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
    private void reborn$redirectAirDraw(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int width, int height) {
        if (!HudTransform.isVisible(HudElement.AIR)) return;
        HudTransform.apply(ctx, HudElement.AIR);
        // 26.1 : drawGuiTexture(Identifier, x, y, w, h) → blitSprite(pipeline, Identifier, x, y, w, h).
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, width, height);
        HudTransform.revert(ctx);
    }

    // ------------ EXPERIENCE BAR ------------
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void reborn$pushXpBar(GuiGraphicsExtractor ctx, int x, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.EXPERIENCE_BAR)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.EXPERIENCE_BAR);
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void reborn$popXpBar(GuiGraphicsExtractor ctx, int x, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.EXPERIENCE_BAR)) return;
        HudTransform.revert(ctx);
    }

    // ------------ ACTION BAR (held item tooltip) ------------
    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void reborn$pushActionBar(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.ACTION_BAR)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.ACTION_BAR);
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("RETURN"))
    private void reborn$popActionBar(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.ACTION_BAR)) return;
        HudTransform.revert(ctx);
    }

    // ------------ SCOREBOARD ------------
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/scoreboard/Objective;)V",
            at = @At("HEAD"), cancellable = true)
    private void reborn$pushScoreboard(GuiGraphicsExtractor ctx, Objective objective, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.SCOREBOARD)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.SCOREBOARD);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/scoreboard/Objective;)V",
            at = @At("RETURN"))
    private void reborn$popScoreboard(GuiGraphicsExtractor ctx, Objective objective, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.SCOREBOARD)) return;
        HudTransform.revert(ctx);
    }
}
