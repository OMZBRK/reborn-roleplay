package fr.reborn.hud.mixin;

import fr.reborn.hud.crosshair.CrosshairManager;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.renderer.RenderTickCounter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link InGameHud} pour intercepter les render methods des
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
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    // ------------ CROSSHAIR (viseur Reborn) ------------
    // À HEAD : si le viseur Reborn est actif, on le dessine et on annule le
    // crosshair vanilla. Le gating vue-1ère-personne est dans CrosshairManager.
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void reborn$crosshair(GuiGraphicsExtractor ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrosshairManager.tryRender(ctx)) {
            ci.cancel();
        }
    }

    // ------------ HOTBAR ------------
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void reborn$pushHotbar(GuiGraphicsExtractor ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.HOTBAR)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.HOTBAR);
    }

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void reborn$popHotbar(GuiGraphicsExtractor ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.HOTBAR)) return;
        HudTransform.revert(ctx);
    }

    // ------------ STATUS BARS (split en 4 redirects) ------------

    // ARMOR (private static) : wrap l'invocation avec transform ARMOR, skip si invisible.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;renderArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/player/Player;IIII)V"))
    private void reborn$redirectArmor(GuiGraphicsExtractor ctx, Player player, int top, int rowOff, int armorIcon, int left) {
        if (!HudTransform.isVisible(HudElement.ARMOR)) return;
        HudTransform.apply(ctx, HudElement.ARMOR);
        InGameHudInvoker.reborn$invokeRenderArmor(ctx, player, top, rowOff, armorIcon, left);
        HudTransform.revert(ctx);
    }

    // HEALTH (private virtual) : wrap avec transform HEALTH, skip si invisible.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/player/Player;IIIIFIIIZ)V"))
    private void reborn$redirectHealth(InGameHud self, GuiGraphicsExtractor ctx, Player player, int x, int y, int lines, int regenOff, float maxHealth, int lastHealth, int health, int absorption, boolean blinking) {
        if (!HudTransform.isVisible(HudElement.HEALTH)) return;
        HudTransform.apply(ctx, HudElement.HEALTH);
        ((InGameHudInvoker) self).reborn$invokeRenderHealthBar(ctx, player, x, y, lines, regenOff, maxHealth, lastHealth, health, absorption, blinking);
        HudTransform.revert(ctx);
    }

    // HUNGER (private virtual) : wrap avec transform HUNGER, skip si invisible.
    @Redirect(method = "renderStatusBars",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;renderFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/entity/player/Player;II)V"))
    private void reborn$redirectFood(InGameHud self, GuiGraphicsExtractor ctx, Player player, int top, int right) {
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
        ctx.drawGuiTexture(id, x, y, width, height);
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
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void reborn$pushScoreboard(GuiGraphicsExtractor ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.SCOREBOARD)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.SCOREBOARD);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("RETURN"))
    private void reborn$popScoreboard(GuiGraphicsExtractor ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.SCOREBOARD)) return;
        HudTransform.revert(ctx);
    }
}
