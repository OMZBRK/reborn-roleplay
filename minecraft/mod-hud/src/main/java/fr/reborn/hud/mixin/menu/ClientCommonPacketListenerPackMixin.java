package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.ChargementScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;

/**
 * Pack de ressources du serveur (Nexo) <b>accepté automatiquement et silencieusement</b> : jamais d'écran « Ce serveur
 * recommande / exige un pack de ressources ». Couvre la phase de configuration et le jeu, quel que soit le chemin de
 * connexion ({@code serverData} absent ou réglé sur « Demander » / « Désactivé »).
 *
 * <p>Vanilla ({@code handleResourcePackPush}) : statut = {@code serverData} ou « Demander » ; « Demander », ou
 * « Désactivé » + pack exigé → écran de confirmation ; sinon {@code DownloadedPackSource.pushPack}. Ici, juste après le
 * passage sur le fil principal : URL valide → les packs serveur sont autorisés et le pack est poussé directement (le
 * cache vanilla par empreinte SHA-1 évite de retélécharger un pack inchangé). URL invalide → vanilla (refus signalé).
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerPackMixin {

    @Shadow @Final protected Minecraft minecraft;
    @Shadow @Final protected ServerData serverData;

    @Shadow
    private static URL parseResourcePackUrl(String url) {
        throw new AssertionError();
    }

    @Inject(method = "handleResourcePackPush", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
    private void reborn$accepterPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        URL url = parseResourcePackUrl(packet.url());
        if (url == null) return;
        if (this.serverData != null) this.serverData.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ChargementScene.packPousse(packet.id());
        DownloadedPackSource source = this.minecraft.getDownloadedPackSource();
        source.allowServerPacks();
        source.pushPack(packet.id(), url, packet.hash());
        ci.cancel();
    }
}
