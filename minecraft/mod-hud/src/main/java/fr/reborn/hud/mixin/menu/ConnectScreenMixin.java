package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.ConnectingRenderer;
import fr.reborn.hud.menu.widget.RebornButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Refonte du {@link ConnectScreen} (écran "Connexion en cours…" vanilla).
 *
 * <p>On annule entièrement le render vanilla (qui affiche un fond Dirt
 * et "Connecting to server..." au milieu) et on dessine notre propre
 * version Reborn via {@link ConnectingRenderer}.
 *
 * <p>Le bouton Annuler vanilla (pixelisé gris) est <strong>remplacé</strong>
 * par un {@link RebornButton#ghost} en bas de l'écran. L'action de
 * fallback reproduit le comportement vanilla : passe {@code
 * connectingCancelled} à true, disconnect la {@link ClientConnection}
 * si déjà ouverte, et revient à l'écran parent — tous accédés via
 * {@code @Shadow}.
 *
 * <p>Le {@code status} vanilla est lu via {@code @Shadow} et passé au
 * renderer pour afficher la phase de connexion en cours.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Shadow
    private Component status;

    @Shadow
    volatile boolean connectingCancelled;

    @Shadow
    volatile ClientConnection connection;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Screen parent;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    /**
     * Remplace le bouton Annuler vanilla par un RebornButton.ghost
     * positionné en bas de l'écran. L'action reproduit le comportement
     * vanilla (cancel + disconnect + setScreen(parent)) via {@code @Shadow}.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$replaceCancelButton(CallbackInfo ci) {
        // 1. Retire le Button vanilla Cancel.
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (GuiEventListener e : this.children()) {
            if (e instanceof Button) toRemove.add(e);
        }
        for (GuiEventListener e : toRemove) this.removeWidget(e);

        // 2. Ajoute un RebornButton ghost à la place, en bas centré.
        int buttonW = 140;
        int buttonH = 30;
        int buttonX = (this.width - buttonW) / 2;
        int buttonY = this.height - 56;
        this.addRenderableWidget(RebornButton.ghost(
            buttonX, buttonY, buttonW, buttonH,
            "Annuler",
            b -> reborn$cancelConnect()
        ));
    }

    /**
     * Reproduit l'action vanilla du bouton Cancel : marque la connexion
     * comme cancellée, disconnect la {@link ClientConnection} si
     * ouverte, retourne au screen parent.
     */
    @org.spongepowered.asm.mixin.Unique
    private void reborn$cancelConnect() {
        this.connectingCancelled = true;
        if (this.connection != null) {
            this.connection.disconnect(Component.translatable("connect.aborted"));
        }
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$customRender(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        // 1. Rendu Reborn (background, spinner, status text, progress bar).
        ConnectingRenderer.render(ctx, this.width, this.height, this.status);

        // 2. Re-render des widgets cliquables (RebornButton Annuler) par-dessus.
        for (GuiEventListener e : this.children()) {
            if (e instanceof AbstractWidget cw) {
                cw.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }

        ci.cancel();
    }
}
