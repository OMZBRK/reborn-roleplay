package fr.reborn.hud.mixin;

import fr.reborn.hud.monnaie.BourseClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Monnaie (15/10) : sur une bourse, le nombre de la pile est remplacé par le <b>montant abrégé en or</b> (750 · 1,2k ·
 * 12k · 1,2M), partout où Minecraft peint les décorations d'un objet (barre d'action, sacoche, coffres, guichet).
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemCountBourseMixin {

    @Inject(method = "itemCount", at = @At("HEAD"), cancellable = true)
    private void reborn$montantBourse(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        long n = BourseClient.montant(stack);
        if (n < 0) return;
        BourseClient.dessiner((GuiGraphicsExtractor) (Object) this, font, n, x, y);
        ci.cancel();
    }
}
