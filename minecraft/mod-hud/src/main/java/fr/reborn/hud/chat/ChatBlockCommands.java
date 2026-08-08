package fr.reborn.hud.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import java.util.Set;

/**
 * Commandes client de blocage chat : {@code /rblock <pseudo>},
 * {@code /runblock <pseudo>}, {@code /rblocklist}. Purement côté client —
 * les messages des joueurs bloqués sont masqués au rendu
 * ({@link fr.reborn.hud.runtime.ChatMessageRenderer}) via {@link ChatBlockList}.
 */
public final class ChatBlockCommands {

    private ChatBlockCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("rblock")
                .then(ClientCommands.argument("pseudo", StringArgumentType.word())
                    .executes(ctx -> {
                        String p = StringArgumentType.getString(ctx, "pseudo");
                        boolean added = ChatBlockList.INSTANCE.block(p);
                        ctx.getSource().sendFeedback(Component.literal(added
                            ? "§c✖ " + p + " bloqué — ses messages sont masqués."
                            : "§7" + p + " est déjà bloqué."));
                        return 1;
                    })));

            dispatcher.register(ClientCommands.literal("runblock")
                .then(ClientCommands.argument("pseudo", StringArgumentType.word())
                    .executes(ctx -> {
                        String p = StringArgumentType.getString(ctx, "pseudo");
                        boolean removed = ChatBlockList.INSTANCE.unblock(p);
                        ctx.getSource().sendFeedback(Component.literal(removed
                            ? "§a✔ " + p + " débloqué."
                            : "§7" + p + " n'était pas bloqué."));
                        return 1;
                    })));

            dispatcher.register(ClientCommands.literal("rblocklist")
                .executes(ctx -> {
                    Set<String> names = ChatBlockList.INSTANCE.names();
                    ctx.getSource().sendFeedback(Component.literal(names.isEmpty()
                        ? "§7Aucun joueur bloqué."
                        : "§e● Bloqués (" + names.size() + ") : §f" + String.join(", ", names)));
                    return 1;
                }));
        });
    }
}
