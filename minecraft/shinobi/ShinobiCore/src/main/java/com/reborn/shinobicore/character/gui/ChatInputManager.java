package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks in-flight "close GUI → ask in chat → reopen GUI" flows.
 *
 * Each entry is one pending input: the next message the player types (or the
 * keyword {@code cancel}) is consumed by our async-chat listener, cancels the
 * chat event so nothing leaks into public chat, and calls the stored handler
 * on the main thread.
 *
 * Sessions auto-expire after {@link #TIMEOUT_TICKS} ticks so a player who
 * walks away doesn't get trapped in a silent input state.
 */
public class ChatInputManager {

    /** 20 ticks/sec × 60 = 60s. */
    public static final long TIMEOUT_TICKS = 20L * 60L;

    private final ShinobiCore plugin;
    private final Map<UUID, Session> pending = new ConcurrentHashMap<>();

    public ChatInputManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a chat-input flow for a player. Closes their inventory, shows a
     * prompt, and arms the chat listener to consume their next message.
     *
     * @param player    the player being prompted
     * @param prompt    the question to show in chat
     * @param onInput   main-thread handler; receives the raw string the
     *                  player typed. Handler is expected to re-open the GUI.
     * @param onCancel  optional handler fired if the player types "cancel" or
     *                  the 60-second timeout elapses. May be {@code null}.
     */
    public void prompt(Player player, String prompt,
                       Consumer<String> onInput, Runnable onCancel) {
        UUID id = player.getUniqueId();
        cancel(id); // drop any previous pending session
        player.closeInventory();
        player.sendMessage(Component.text(prompt, NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                "Type the value in chat, or 'cancel' to abort. (60s timeout)",
                NamedTextColor.GRAY));

        Session s = new Session(onInput, onCancel);
        pending.put(id, s);

        s.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Session active = pending.remove(id);
            if (active == null || active != s) return; // already consumed
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(Component.text(
                        "Chat-input timed out.", NamedTextColor.RED));
            }
            if (active.onCancel != null) active.onCancel.run();
        }, TIMEOUT_TICKS).getTaskId();
    }

    /**
     * Called by {@link ChatInputListener} when a pending player sends chat.
     *
     * @return true if we consumed this message and the caller should cancel
     *         the chat event; false if the player has no pending session.
     */
    public boolean feedInput(Player player, String message) {
        UUID id = player.getUniqueId();
        Session s = pending.remove(id);
        if (s == null) return false;
        plugin.getServer().getScheduler().cancelTask(s.timeoutTask);

        // Back to main thread — Bukkit state is unsafe off-thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if ("cancel".equalsIgnoreCase(message.trim())) {
                player.sendMessage(Component.text("Annulé.", NamedTextColor.YELLOW));
                if (s.onCancel != null) s.onCancel.run();
                return;
            }
            try {
                s.onInput.accept(message);
            } catch (Exception ex) {
                player.sendMessage(Component.text(
                        "Error handling input: " + ex.getMessage(),
                        NamedTextColor.RED));
                plugin.getLogger().warning("ChatInput handler threw: " + ex);
            }
        });
        return true;
    }

    public boolean hasPending(UUID id) {
        return pending.containsKey(id);
    }

    public void cancel(UUID id) {
        Session s = pending.remove(id);
        if (s == null) return;
        plugin.getServer().getScheduler().cancelTask(s.timeoutTask);
        if (s.onCancel != null) {
            plugin.getServer().getScheduler().runTask(plugin, s.onCancel);
        }
    }

    /* -------------------------------------------------------- inner types */

    private static final class Session {
        final Consumer<String> onInput;
        final Runnable onCancel;
        int timeoutTask = -1;

        Session(Consumer<String> onInput, Runnable onCancel) {
            this.onInput = onInput;
            this.onCancel = onCancel;
        }
    }
}
