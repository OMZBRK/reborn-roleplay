package fr.reborn.integrity.mixin;

import fr.reborn.integrity.ui.connect.ConnectingRenderer;
import fr.reborn.integrity.ui.menu.RebornButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
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
    private Text status;

    @Shadow
    volatile boolean connectingCancelled;

    @Shadow
    volatile ClientConnection connection;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Screen parent;

    protected ConnectScreenMixin(Text title) {
        super(title);
    }

    /**
     * Remplace le bouton Annuler vanilla par un RebornButton.ghost
     * positionné en bas de l'écran. L'action reproduit le comportement
     * vanilla (cancel + disconnect + setScreen(parent)) via {@code @Shadow}.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$replaceCancelButton(CallbackInfo ci) {
        // 1. Retire le ButtonWidget vanilla Cancel.
        List<Element> toRemove = new ArrayList<>();
        for (Element e : this.children()) {
            if (e instanceof ButtonWidget) toRemove.add(e);
        }
        for (Element e : toRemove) this.remove(e);

        // 2. Ajoute un RebornButton ghost à la place, en bas centré.
        int buttonW = 140;
        int buttonH = 30;
        int buttonX = (this.width - buttonW) / 2;
        int buttonY = this.height - 56;
        this.addDrawableChild(RebornButton.ghost(
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
            this.connection.disconnect(Text.translatable("connect.aborted"));
        }
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$customRender(DrawContext ctx, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        // 1. Rendu Reborn (background, spinner, status text, progress bar).
        ConnectingRenderer.render(ctx, this.width, this.height, this.status);

        // 2. Re-render des widgets cliquables (RebornButton Annuler) par-dessus.
        for (Element e : this.children()) {
            if (e instanceof ClickableWidget cw) {
                cw.render(ctx, mouseX, mouseY, delta);
            }
        }

        ci.cancel();
    }
}
