package fr.reborn.hud.mixin.menu;

import com.mojang.blaze3d.platform.IconSet;
import fr.reborn.hud.menu.FenetreReborn;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

/**
 * Icône de la fenêtre (barre des tâches, Alt-Tab, coin de la barre de titre) : le logo Reborn du launcher au lieu de
 * l'icône Minecraft. {@code Window#setIcon} lit {@code IconSet#getStandardIcons} (Windows, Linux X11) puis la passe à
 * {@code glfwSetWindowIcon} : on rend les PNG Reborn ({@link FenetreReborn#icones}). Si un fichier manque, vanilla reste.
 * macOS ({@code getMacIcon}, .icns) et Wayland ne sont pas touchés : icône vanilla, rien qui plante.
 */
@Mixin(IconSet.class)
public abstract class FenetreIconeMixin {

    @Inject(method = "getStandardIcons", at = @At("HEAD"), cancellable = true)
    private void reborn$icones(PackResources pack, CallbackInfoReturnable<List<IoSupplier<InputStream>>> cir) {
        List<IoSupplier<InputStream>> l = FenetreReborn.icones();
        if (l != null) cir.setReturnValue(l);
    }
}
