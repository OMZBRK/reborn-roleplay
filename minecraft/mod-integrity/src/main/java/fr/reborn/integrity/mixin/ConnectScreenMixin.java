package fr.reborn.integrity.mixin;

import fr.reborn.integrity.ui.connect.ConnectingRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refonte du {@link ConnectScreen} (écran "Connexion en cours…" vanilla).
 *
 * <p>On annule entièrement le render vanilla (qui affiche un fond Dirt
 * et "Connecting to server..." au milieu) et on dessine notre propre
 * version Reborn via {@link ConnectingRenderer}. Le bouton Annuler est
 * re-rendu manuellement à partir de {@code this.children()} pour rester
 * cliquable.
 *
 * <p>Le {@code status} vanilla est lu via {@code @Shadow} et passé au
 * renderer pour afficher la phase de connexion en cours (handshake,
 * authentification, login, etc.).
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Shadow
    private Text status;

    protected ConnectScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$customRender(DrawContext ctx, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        // 1. Rendu Reborn (background, logo, spinner, status text).
        ConnectingRenderer.render(ctx, this.width, this.height, this.status);

        // 2. Re-render des widgets cliquables (bouton Annuler) par-dessus.
        for (Element e : this.children()) {
            if (e instanceof ClickableWidget cw) {
                cw.render(ctx, mouseX, mouseY, delta);
            }
        }

        ci.cancel();
    }
}
