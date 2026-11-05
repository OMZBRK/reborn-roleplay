package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.esc.EscTokonoma;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu pause (Échap) Reborn : tout le rendu et les clics sont délégués à {@link EscTokonoma}.
 *
 * <p>Les widgets vanilla (dont le titre « Game Menu ») sont ajoutés dans {@code init()} :
 * on les retire tous au RETURN, puis on annule le render vanilla.
 *
 * <p>Depuis l'Échap e5 (lot 3, 05/11), le jeu reste visible derrière : flou des menus de Minecraft (une passe), puis
 * le voile en dégradé de la piste A ; l'écran n'est donc plus « opaque » (le monde est rendu). Échap ferme avec la
 * même animation que CONTINUER (180 ms).
 */
@Mixin(PauseScreen.class) // 26.1 : GameMenuScreen → PauseScreen
public abstract class GameMenuScreenMixin extends Screen {

    @Unique private EscTokonoma reborn$ui;

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void reborn$rebuildEscMenu(CallbackInfo ci) {
        List<GuiEventListener> toRemove = new ArrayList<>(this.children());
        for (GuiEventListener e : toRemove) this.removeWidget(e);
        if (reborn$ui == null) reborn$ui = new EscTokonoma(this);
        reborn$ui.init();
    }

    /** Flou des menus de Minecraft, voile de l'Échap, sous-titres ; pas le fond sombre vanilla. */
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void reborn$fond(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (reborn$ui == null) return;
        this.extractBlurredBackground(ctx);
        reborn$ui.fond(ctx);
        net.minecraft.client.Minecraft.getInstance().gui.hud.extractDeferredSubtitles();
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (reborn$ui == null) return;
        reborn$ui.render(ctx, this.width, this.height, mouseX, mouseY);
        ci.cancel();
    }

    // PauseScreen ne déclare pas mouseClicked (hérité de Screen) : on l'override.
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (reborn$ui != null && reborn$ui.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    // Échap : fermeture animée, comme CONTINUER (PauseScreen ne déclare pas keyPressed non plus).
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (reborn$ui != null && event.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc() && reborn$ui.continuer()) return true;
        return super.keyPressed(event);
    }
}
