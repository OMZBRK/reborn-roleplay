package fr.reborn.hud.mixin;

import fr.reborn.hud.crosshair.CrosshairManager;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link Gui} : applique offset / scale / visibilité (configurés dans
 * l'éditeur HUD) aux éléments du HUD vanilla.
 *
 * <p><b>26.1 (mode extraction / retained)</b> — le HUD vanilla ne se dessine plus
 * en direct : chaque élément est « extrait » dans un render state via une méthode
 * {@code extract*} sur {@link Gui}, dessiné ensuite. On enrobe donc chaque
 * {@code extract*} d'un push/pop de {@code ctx.pose()} (via {@link HudTransform})
 * — la pose est bakée dans le render state à l'extraction, donc le translate/scale
 * décale bien l'élément. Un {@code @Inject} HEAD annulable masque l'élément
 * (visibilité), le {@code @Inject} RETURN dépile la transform.
 *
 * <p>Cibles 26.1 (javap sur {@code minecraft-merged-deobf-26.1.2}) :
 * <ul>
 *   <li>{@code extractItemHotbar} → {@link HudElement#HOTBAR}</li>
 *   <li>{@code extractPlayerHealth} → {@link HudElement#HEALTH}</li>
 *   <li>{@code extractArmor} (static) → {@link HudElement#ARMOR}</li>
 *   <li>{@code extractFood} → {@link HudElement#HUNGER}</li>
 *   <li>{@code extractAirBubbles} → {@link HudElement#AIR}</li>
 *   <li>{@code extractSelectedItemName} → {@link HudElement#ACTION_BAR}</li>
 *   <li>{@code extractScoreboardSidebar} → {@link HudElement#SCOREBOARD}</li>
 *   <li>{@code extractCrosshair} → viseur Reborn (override).</li>
 * </ul>
 * La barre d'XP est passée en {@code ContextualBarRenderer} (hors {@link Gui}) —
 * repositionnement traité séparément si besoin.
 */
@Mixin(Hud.class)
public abstract class InGameHudMixin {

    // ─────────── CROSSHAIR (viseur Reborn) ───────────
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void reborn$crosshair(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (CrosshairManager.tryRender(ctx)) ci.cancel();
    }

    // ─────────── HOTBAR ───────────
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void reborn$pushHotbar(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.HOTBAR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.HOTBAR);
    }

    @Inject(method = "extractItemHotbar", at = @At("RETURN"))
    private void reborn$popHotbar(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.HOTBAR)) return;
        HudTransform.revert(ctx);
    }

    // ─────────── HEALTH : masquée en permanence ───────────
    // La vie vanilla est remplacée par le VitalsHUD RP (tête + barre de vie).
    // On annule toujours son extraction — pas dans l'éditeur (cf HudElement.EDITABLE).
    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void reborn$hideHealth(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        // Sur un serveur build / non-Reborn, on laisse la vie vanilla (pas de HUD RP).
        if (fr.reborn.hud.runtime.RebornSession.rpFeaturesEnabled()) ci.cancel();
    }

    // ─────────── ARMOR (static) ───────────
    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
    private static void reborn$pushArmor(GuiGraphicsExtractor ctx, Player player, int a, int b, int c, int d, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.ARMOR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.ARMOR);
    }

    @Inject(method = "extractArmor", at = @At("RETURN"))
    private static void reborn$popArmor(GuiGraphicsExtractor ctx, Player player, int a, int b, int c, int d, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.ARMOR)) return;
        HudTransform.revert(ctx);
    }

    // ─────────── HUNGER (food) : masquée en permanence ───────────
    // La faim vanilla est remplacée par le VitalsHUD RP (barre stamina).
    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void reborn$hideFood(GuiGraphicsExtractor ctx, Player player, int a, int b, CallbackInfo ci) {
        // Sur un serveur build / non-Reborn, on laisse la faim vanilla.
        if (fr.reborn.hud.runtime.RebornSession.rpFeaturesEnabled()) ci.cancel();
    }

    // ─────────── AIR (bubbles) ───────────
    @Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
    private void reborn$pushAir(GuiGraphicsExtractor ctx, Player player, int a, int b, int c, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.AIR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.AIR);
    }

    @Inject(method = "extractAirBubbles", at = @At("RETURN"))
    private void reborn$popAir(GuiGraphicsExtractor ctx, Player player, int a, int b, int c, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.AIR)) return;
        HudTransform.revert(ctx);
    }

    // ─────────── ACTION BAR (nom d'item sélectionné) ───────────
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void reborn$pushActionBar(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.ACTION_BAR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.ACTION_BAR);
    }

    @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
    private void reborn$popActionBar(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.ACTION_BAR)) return;
        HudTransform.revert(ctx);
    }

    // ─────────── SCOREBOARD ───────────
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void reborn$pushScoreboard(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.SCOREBOARD)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.SCOREBOARD);
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("RETURN"))
    private void reborn$popScoreboard(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.SCOREBOARD)) return;
        HudTransform.revert(ctx);
    }

    // CHAT : géré par ChatHudMixin (sur ChatComponent#extractRenderState) pour
    // conserver la position que le chat soit ouvert (ChatScreen) ou fermé (HUD).

    // ─────────── BOSS BAR ───────────
    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void reborn$pushBossBar(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.BOSS_BAR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.BOSS_BAR);
    }

    @Inject(method = "extractBossOverlay", at = @At("RETURN"))
    private void reborn$popBossBar(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.BOSS_BAR)) return;
        HudTransform.revert(ctx);
    }
}
