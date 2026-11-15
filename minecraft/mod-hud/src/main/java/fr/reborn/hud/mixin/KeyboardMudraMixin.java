package fr.reborn.hud.mixin;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.mudra.MudraClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>Barre de mudras : la rangée des chiffres compose les signes</b> (14/11). Tant que la barre est ouverte et qu'aucun
 * écran ne l'est ({@link MudraClient#capte()}), les douze touches 1 à 9, 0 et les deux suivantes sont prises au HEAD de
 * {@code KeyboardHandler#keyPress} : le jeu ne les voit pas, l'objet tenu ne change pas. Échap referme la barre au lieu
 * d'ouvrir le menu.
 *
 * <p>Une touche dont l'appui a été pris voit aussi sa répétition et sa relâche prises (l'état des liaisons du jeu reste
 * cohérent) ; une touche déjà tenue à l'ouverture de la barre garde sa relâche (sinon elle resterait « enfoncée » pour
 * le jeu). Barre fermée, écran ouvert : rien n'est touché.
 *
 * <p><b>Touches en bascule</b> (Mudras, Byakugan) : la répétition du clavier est prise. En 26.2 le jeu compte un clic de
 * liaison à chaque répétition ; tenir la touche refermait donc la barre (ou la vision) puis la redemandait trente fois par
 * seconde (relecture du 15/11). L'appui et la relâche passent toujours.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardMudraMixin {

    /** Touches dont l'appui a été pris (12 signes + Échap). */
    @Unique private static final boolean[] reborn$prises = new boolean[MudraClient.N + 1];

    // 26.x : KeyboardHandler#keyPress(long window, int action, KeyEvent event).
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void reborn$mudras(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_REPEAT && Minecraft.getInstance().gui.screen() == null
            && (reborn$bascule(HudKeybinds.MUDRAS, event) || reborn$bascule(HudKeybinds.BYAKUGAN, event))) {
            ci.cancel();
            return;
        }
        int k = event.key();
        int i = k == GLFW.GLFW_KEY_ESCAPE ? MudraClient.N : MudraClient.indexTouche(k);
        if (i < 0) return;
        if (action == GLFW.GLFW_PRESS) {
            if (!MudraClient.capte()) return;
            reborn$prises[i] = true;
            if (i == MudraClient.N) MudraClient.fermer();
            else MudraClient.signe(i);
            ci.cancel();
        } else if (reborn$prises[i]) {
            // répétition, puis relâche de l'appui pris
            if (action == GLFW.GLFW_RELEASE) reborn$prises[i] = false;
            ci.cancel();
        }
    }

    @Unique
    private static boolean reborn$bascule(KeyMapping km, KeyEvent event) {
        return km != null && !km.isUnbound() && km.matches(event);
    }
}
