package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.ChargementScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran « Connexion en cours… » vanilla remplacé par la scène de chargement Reborn ({@link ChargementScene}), qui
 * continue ensuite sur l'application du pack serveur et le chargement du monde.
 *
 * <ul>
 *   <li>{@code startConnecting} : le serveur visé passe en « packs serveur : activés » — quel que soit le chemin
 *       (bouton JOUER, liste de serveurs, quickPlay du launcher), le pack Nexo est accepté sans écran de
 *       confirmation (voir aussi {@code ClientCommonPacketListenerPackMixin}).</li>
 *   <li>Aucun bouton à l'écran : le bouton Annuler vanilla est retiré, la touche Échap annule la connexion
 *       (aborted, disconnect, retour au parent, comme le bouton vanilla).</li>
 *   <li>Le fond (panorama flou) n'est pas calculé : la scène couvre tout l'écran.</li>
 * </ul>
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Shadow
    private Component status;

    @Shadow
    volatile boolean aborted;

    @Shadow
    volatile Connection connection;

    @Shadow
    @Final
    private Screen parent;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void reborn$packsServeurActives(Screen parent, Minecraft mc, ServerAddress address, ServerData data,
                                                   boolean quickPlay, TransferState transfer, CallbackInfo ci) {
        if (data != null && data.getResourcePackStatus() != ServerData.ServerPackStatus.ENABLED) {
            data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        }
    }

    /** Aucun bouton : le bouton Annuler vanilla est retiré, Échap annule (voir {@link #onClose}). */
    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$sansBouton(CallbackInfo ci) {
        ChargementScene.demarrer();
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (GuiEventListener e : this.children()) {
            if (e instanceof Button) toRemove.add(e);
        }
        for (GuiEventListener e : toRemove) this.removeWidget(e);
    }

    /** Vanilla refuse Échap sur cet écran (il y avait un bouton) : on l'accepte, il annule. */
    @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
    private void reborn$echapAnnule(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    /** Échap : annule la connexion comme le bouton vanilla (aborted, disconnect, retour à l'écran parent). */
    @Override
    public void onClose() {
        reborn$cancelConnect();
    }

    @Unique
    private void reborn$cancelConnect() {
        this.aborted = true;
        if (this.connection != null) {
            this.connection.disconnect(Component.translatable("connect.aborted"));
        }
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    /** Pas de panorama ni de flou derrière la scène (elle couvre tout l'écran). */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$customRender(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        ChargementScene.dessinerConnexion(ctx, this.status);
        ci.cancel();
    }
}
