package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.esc.EscTokonoma;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
}
