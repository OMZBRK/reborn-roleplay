package fr.reborn.hud.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accès en lecture aux champs privés de {@link ChatComponent} (lignes affichées
 * + position de scroll) pour le hit-testing du clic sur les composants du chat
 * custom (liens / copie), calculé sur NOTRE géométrie de rendu.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> reborn$trimmedMessages();

    @Accessor("chatScrollbarPos")
    int reborn$chatScrollbarPos();
}
