package com.reborn.shinobicore.character.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Bridges Paper's {@link AsyncChatEvent} to the {@link ChatInputManager}.
 * When a player has a pending chat-input session, their next chat message is
 * cancelled (not broadcast) and handed to the manager instead.
 */
public class ChatInputListener implements Listener {

    private final ChatInputManager inputs;

    public ChatInputListener(ChatInputManager inputs) {
        this.inputs = inputs;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!inputs.hasPending(e.getPlayer().getUniqueId())) return;
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        boolean consumed = inputs.feedInput(e.getPlayer(), plain);
        if (consumed) e.setCancelled(true);
    }
}
