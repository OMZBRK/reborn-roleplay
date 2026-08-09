package fr.reborn.hud.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Expose la méthode protégée statique {@code Screen.defaultHandleGameClickEvent}
 * (gestion vanilla d'un {@link ClickEvent} : ouverture de lien, copie
 * presse-papier, run/suggest command) pour la réutiliser dans le hit-testing du
 * chat custom ({@code ChatScreenMixin}).
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    @Invoker("defaultHandleGameClickEvent")
    static void reborn$defaultHandleGameClickEvent(ClickEvent event, Minecraft mc, Screen screen) {
        throw new AssertionError("mixin @Invoker non appliqué");
    }
}
