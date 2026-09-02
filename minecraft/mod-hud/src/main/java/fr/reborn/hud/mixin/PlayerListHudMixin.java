package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.tablist.TablistData;
import fr.reborn.hud.menu.tablist.TablistScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralise l'overlay tablist vanilla (hold-Tab) quand l'écran tablist Reborn
 * {@link TablistScreen} est ouvert — évite un double rendu si la touche reste
 * maintenue. Le reste du temps, les clients sans le mod gardent l'affichage
 * serveur normal.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {

    // 26.1 : PlayerListHud → PlayerTabOverlay, render → extractRenderState.
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$tablistRender(GuiGraphicsExtractor ctx, int scaledWindowWidth,
                                      Scoreboard scoreboard, Objective objective,
                                      CallbackInfo ci) {
        // On remplace le tablist vanilla par celui de Reborn UNIQUEMENT quand le
        // serveur pousse le feed reborn:tablist (plugin présent). Sans feed (ex.
        // modpack builder / serveur sans ShinobiCore), on NE neutralise PAS le
        // vanilla → le joueur garde l'ancien tablist. Sinon on neutralise dès que
        // l'écran interactif est ouvert OU la touche liste-joueurs est pressée
        // (mode hold + fenêtre toggle, pour éviter le « flash » vanilla).
        if (!TablistData.hasData()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TablistScreen
            || (mc.options != null && mc.options.keyPlayerList.isDown())) {
            ci.cancel();
        }
    }
}
