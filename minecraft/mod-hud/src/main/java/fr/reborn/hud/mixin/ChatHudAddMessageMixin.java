package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatBlockList;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.MentionDetector;
import fr.reborn.hud.chat.MessageTimestamps;
import fr.reborn.hud.chat.RebornChatRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@code ChatHud.addMessage(Text)} pour détecter les mentions
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
@Mixin(ChatHud.class)
public abstract class ChatHudAddMessageMixin {

    /**
     * Blocage : si le message provient d'un joueur de la {@link ChatBlockList}
     * (premier pseudo en ligne trouvé dans le texte), on annule l'ajout — le
     * message n'apparaît jamais. Additif : n'affecte pas le layout vanilla.
     */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void reborn$blockFilter(Text message, CallbackInfo ci) {
        try {
            if (reborn$isFromBlocked(message.getString())) {
                ci.cancel();
            }
        } catch (RuntimeException ignored) {}
    }

    private static boolean reborn$isFromBlocked(String plain) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return false;
        String earliest = null;
        int bestIdx = Integer.MAX_VALUE;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
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

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("TAIL"))
    private void reborn$detectMention(Text message, CallbackInfo ci) {
        try {
            // Record real-time timestamp pour le custom render
            int tick = MinecraftClient.getInstance().inGameHud.getTicks();
            MessageTimestamps.record(tick);

            RebornChatRenderer.onMessageReceived(message.getString());

            ChatSettings settings = RebornHudClient.config().getChatSettings();
            if (!settings.highlightMentions) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            if (player == null) return;
            String pseudo = player.getGameProfile().getName();

            if (MentionDetector.isMentioned(message.getString(), pseudo)) {
                RebornChatRenderer.triggerMentionFlash();
                if (settings.soundOnMention && mc.getSoundManager() != null) {
                    mc.getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.5f, 0.6f));
                }
            }
        } catch (RuntimeException ignored) {
            // Silently fail — pas critique, on prefere ne pas crasher le chat
        }
    }
}
