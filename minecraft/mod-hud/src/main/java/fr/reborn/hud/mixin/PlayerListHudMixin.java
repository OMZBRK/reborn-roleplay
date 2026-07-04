package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.tablist.TablistScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
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
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$tablistRender(DrawContext ctx, int scaledWindowWidth,
                                      Scoreboard scoreboard, ScoreboardObjective objective,
                                      CallbackInfo ci) {
        // On neutralise l'overlay vanilla dans deux cas : l'écran interactif
        // Reborn est ouvert, ou le mode Hold est actif (le panneau Reborn est
        // alors dessiné par le HudRenderCallback dans RebornHudClient). On ne
        // dessine PAS ici : en solo ce render n'est même pas appelé.
        if (MinecraftClient.getInstance().currentScreen instanceof TablistScreen
            || RebornPrefs.INSTANCE.tablistHold) {
            ci.cancel();
        }
    }
}
