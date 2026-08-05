package fr.reborn.hud.chat;

/**
 * Commandes client de blocage chat : {@code /rblock <pseudo>},
 * {@code /runblock <pseudo>}, {@code /rblocklist}. Purement côté client —
 * les messages des joueurs bloqués sont masqués au rendu
 * ({@link fr.reborn.hud.runtime.ChatMessageRenderer}).
 */
public final class ChatBlockCommands {

    private ChatBlockCommands() {}

    public static void register() {
        // ⚠️ STUB 26.1 (phase 2) — ClientCommandManager (fabric command-api-v2)
        // pas sur le classpath de compile ici. Commandes /rblock desactivees.
    }
}
