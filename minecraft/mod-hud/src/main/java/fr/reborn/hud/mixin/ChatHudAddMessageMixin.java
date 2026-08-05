package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatBlockList;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.MentionDetector;
import fr.reborn.hud.chat.MessageTimestamps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@code ChatComponent.addMessage(Component)} pour détecter les mentions
 * du joueur local et déclencher :
 * <ul>
 *   <li>Un son de notification discret (cloche).</li>
 *   <li>Un flash visuel du panel chat (pulse 1.5s) — géré dans
 *       {@link RebornChatRenderer#triggerMentionFlash}.</li>
 *   <li>L'incrément du compteur unread du tab approprié via le classifier.</li>
 * </ul>
 *
 * <p>Le toggle {@code chatSettings.highlightMentions} active/désactive
 * tout. Le toggle {@code chatSettings.soundOnMention} contrôle séparément
 * le son (parfois on veut le flash sans bip).
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudAddMessageMixin {

    /**
     * Blocage : si le message provient d'un joueur de la {@link ChatBlockList}
     * (premier pseudo en ligne trouvé dans le texte), on annule l'ajout — le
     * message n'apparaît jamais. Additif : n'affecte pas le layout vanilla.
     */
    @Inject(method = "addMessage(Lnet/minecraft/text/Component;)V", at = @At("HEAD"), cancellable = true)
    private void reborn$blockFilter(Component message, CallbackInfo ci) {
        try {
            if (reborn$isFromBlocked(message.getString())) {
                ci.cancel();
            }
        } catch (RuntimeException ignored) {}
    }

    private static boolean reborn$isFromBlocked(String plain) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        String earliest = null;
        int bestIdx = Integer.MAX_VALUE;
        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            String name = e.getProfile() != null ? e.getProfile().getName() : null;
            if (name == null || name.isEmpty()) continue;
            int idx = plain.indexOf(name);
            if (idx >= 0 && idx < bestIdx) {
                bestIdx = idx;
                earliest = name;
            }
        }
        return earliest != null && ChatBlockList.INSTANCE.isBlocked(earliest);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Component;)V", at = @At("TAIL"))
    private void reborn$detectMention(Component message, CallbackInfo ci) {
        try {
            // Record real-time timestamp pour le custom render
            int tick = Minecraft.getInstance().gui.getTicks();
            MessageTimestamps.record(tick);

            ChatSettings settings = RebornHudClient.config().getChatSettings();
            if (!settings.highlightMentions) return;

            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;
            String pseudo = player.getProfile().getName();

            if (MentionDetector.isMentioned(message.getString(), pseudo)) {
                if (settings.soundOnMention && mc.getSoundManager() != null) {
                    mc.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.master(
                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.5f, 0.6f));
                }
            }
        } catch (RuntimeException ignored) {
            // Silently fail — pas critique, on prefere ne pas crasher le chat
        }
    }
}
