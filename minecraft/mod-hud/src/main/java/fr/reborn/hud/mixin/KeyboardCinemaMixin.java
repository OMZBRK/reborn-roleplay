package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.CinemaBars;
import fr.reborn.hud.keybind.HudKeybinds;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>F1 = bandes cinéma Reborn</b> (au lieu de « masquer l'ATH » vanilla).
 *
 * <p>Le mode CLEAN de {@link CinemaBars} fait déjà mieux que le F1 de Minecraft
 * (écran propre + bandes en 2e cran), donc on remplace purement le
 * comportement vanilla. On intercepte {@code KeyboardHandler#keyPress} au HEAD
 * et on <b>annule</b> l'évènement pour F1 : ni le toggle {@code Hud.isHidden}
 * vanilla, ni {@code KeyMapping.click} ne s'exécutent — c'est nous qui appelons
 * {@link CinemaBars#toggle()}. Sans le cancel, les deux partiraient ensemble
 * (HUD masqué par MC <i>et</i> cycle cinéma), ce qui rendait la touche inutilisable.
 *
 * <p>Le remplacement est <b>conditionnel au bind</b> : si le joueur re-bind
 * « bandes cinéma » ailleurs dans les commandes, {@code CINEMA.matches(event)}
 * devient faux sur F1 → on ne cancelle rien et le F1 vanilla revient. Idem quand
 * un écran est ouvert (l'éditeur HUD utilise F1 pour son aide).
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardCinemaMixin {

    // 26.1 : KeyboardHandler#keyPress(long window, int action, KeyEvent event).
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void reborn$cinemaReplacesF1(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (event.key() != GLFW.GLFW_KEY_F1) return;
        if (HudKeybinds.CINEMA == null || !HudKeybinds.CINEMA.matches(event)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;   // écran ouvert → F1 vanilla

        if (action == GLFW.GLFW_PRESS) {
            CinemaBars.INSTANCE.toggle();
        }
        // On cancelle PRESS / REPEAT / RELEASE : vanilla ne voit jamais la touche,
        // donc l'état KeyMapping de F1 reste cohérent (jamais set, jamais unset).
        ci.cancel();
    }
}
