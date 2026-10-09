package fr.reborn.hud.chat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Banc d'essai visuel du chat, <b>inactif en production</b> : ne fait rien sauf si {@code REBORN_CHAT_DEBUG=1}
 * (client de dev). Injecte des messages aux formats de ShinobiCore, capture le chat fermé puis ouvert (onglets,
 * recherche, mode /me + emojis) dans run/screenshots/, puis ferme le jeu.
 */
public final class ChatDebug {

    private static int ticks = -1;

    private ChatDebug() {}

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_CHAT_DEBUG"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(ChatDebug::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) { ticks = -1; return; }
        ticks++;
        var chat = mc.gui.hud.getChat();
        String me = mc.player.getGameProfile().name();
        switch (ticks) {
            case 30 -> { mc.player.connection.sendCommand("time set 1000"); chat.clearMessages(false); }
            case 40 -> chat.addClientSystemMessage(Component.literal("Le vent se lève sur les toits de Konoha.").withStyle(ChatFormatting.GRAY));
            case 44 -> chat.addClientSystemMessage(me("Kazuki Uchiha", ChatFormatting.RED, "serre le poing et fixe l'horizon, la mâchoire crispée."));
            case 48 -> chat.addClientSystemMessage(say("Ren Hyūga", ChatFormatting.AQUA, "Tu comptes rester planté là toute la nuit ?"));
            case 52 -> chat.addClientSystemMessage(me("????", ChatFormatting.GRAY, "observe la scène depuis l'ombre d'une ruelle, sans un bruit."));
            case 56 -> chat.addClientSystemMessage(say("Kazuki Uchiha", ChatFormatting.RED, "Je l'attends. Il a promis de venir, " + me + "."));
            case 60 -> chat.addClientSystemMessage(Component.literal("[Staff] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Modo Raiden_FR : ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("Petit rappel : les actions se font en /me.").withStyle(ChatFormatting.WHITE)));
            case 64 -> chat.addClientSystemMessage(Component.literal("Mei Akimichi vous chuchote : rendez-vous au stand d'Ichiraku.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            case 68 -> chat.addClientSystemMessage(me("Ren Hyūga", ChatFormatting.AQUA, "active son Byakugan, les veines autour de ses yeux se gonflent."));
            case 90 -> shot(mc);                                                            // chat fermé
            case 95 -> mc.setScreenAndShow(new ChatScreen("", false));
            case 115 -> shot(mc);                                                           // ouvert, Général
            case 120 -> ChatPanel.debugState(ChatTab.RP, false, null);
            case 135 -> shot(mc);                                                           // onglet RP
            case 140 -> ChatPanel.debugState(ChatTab.GENERAL, false, "konoha");
            case 155 -> shot(mc);                                                           // recherche
            case 160 -> {
                ChatPanel.debugState(ChatTab.GENERAL, true, null);
                if (ChatPanel.activeInput != null) ChatPanel.activeInput.setValue("serre le poing");
                EmojiPicker.toggle();
            }
            case 180 -> shot(mc);                                                           // mode /me + emojis
            case 190 -> mc.stop();
            default -> { }
        }
    }

    /** Audit responsive : remplit le chat avec les messages de démo. */
    public static void fillDemo(Minecraft mc) {
        var chat = mc.gui.hud.getChat();
        String me = mc.player.getGameProfile().name();
        chat.clearMessages(false);
        chat.addClientSystemMessage(Component.literal("Le vent se lève sur les toits de Konoha.").withStyle(ChatFormatting.GRAY));
        chat.addClientSystemMessage(me("Kazuki Uchiha", ChatFormatting.RED, "serre le poing et fixe l'horizon, la mâchoire crispée."));
        chat.addClientSystemMessage(say("Ren Hyūga", ChatFormatting.AQUA, "Tu comptes rester planté là toute la nuit ?"));
        chat.addClientSystemMessage(say("Kazuki Uchiha", ChatFormatting.RED, "Je l'attends. Il a promis de venir, " + me + "."));
        chat.addClientSystemMessage(Component.literal("[Staff] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Modo Raiden_FR : ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Petit rappel : les actions se font en /me.").withStyle(ChatFormatting.WHITE)));
        chat.addClientSystemMessage(me("Ren Hyūga", ChatFormatting.AQUA, "active son Byakugan, les veines autour de ses yeux se gonflent."));
    }

    private static MutableComponent me(String name, ChatFormatting tone, String action) {
        return Component.empty()
                .append(Component.literal("* ").withStyle(tone, ChatFormatting.BOLD))
                .append(Component.literal(name).withStyle(tone, ChatFormatting.BOLD))
                .append(Component.literal(" " + action).withStyle(tone, ChatFormatting.ITALIC));
    }

    private static MutableComponent say(String name, ChatFormatting tone, String text) {
        return Component.empty()
                .append(Component.literal(name).withStyle(tone, ChatFormatting.BOLD))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(text));
    }

    private static void shot(Minecraft mc) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), msg -> { });
    }
}
