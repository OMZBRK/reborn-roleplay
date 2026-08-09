package fr.reborn.hud.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.net.URI;

/**
 * Expose des méthodes protégées statiques de {@link Screen} pour l'interactivité
 * du chat custom : gestion d'un {@link ClickEvent} (run/suggest/copy) et
 * ouverture d'une URL (avec écran de confirmation vanilla).
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    @Invoker("defaultHandleGameClickEvent")
    static void reborn$defaultHandleGameClickEvent(ClickEvent event, Minecraft mc, Screen screen) {
        throw new AssertionError("mixin @Invoker non appliqué");
    }

    @Invoker("clickUrlAction")
    static boolean reborn$clickUrlAction(Minecraft mc, Screen screen, URI uri) {
        throw new AssertionError("mixin @Invoker non appliqué");
    }
}
