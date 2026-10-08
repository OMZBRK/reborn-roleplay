package fr.reborn.hud.mixin;

import fr.reborn.hud.chat.ChatPanel;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recherche du chat : tant que le champ de recherche du {@link ChatPanel} a le focus, les caractères tapés
 * vont dans la recherche au lieu du champ de saisie du chat.
 */
@Mixin(EditBox.class)
public abstract class EditBoxChatSearchMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void reborn$routeToSearch(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != ChatPanel.activeInput || !ChatPanel.searchFocused()) return;
        if (event.isAllowedChatCharacter()) ChatPanel.searchType(event.codepointAsString());
        cir.setReturnValue(true);
    }
}
