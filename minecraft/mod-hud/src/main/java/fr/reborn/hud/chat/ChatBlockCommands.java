package fr.reborn.hud.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * Commandes client de blocage chat : {@code /rblock <pseudo>},
 * {@code /runblock <pseudo>}, {@code /rblocklist}. Purement côté client —
 * les messages des joueurs bloqués sont masqués au rendu
 * ({@link fr.reborn.hud.runtime.ChatMessageRenderer}).
 */
public final class ChatBlockCommands {

    private ChatBlockCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommandManager.literal("rblock")
                .then(ClientCommandManager.argument("pseudo", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "pseudo");
                        boolean ok = ChatBlockList.INSTANCE.block(name);
                        ctx.getSource().sendFeedback(Text.literal(
                            ok ? "§cJoueur bloqué : §f" + name
                               : "§7Déjà bloqué : §f" + name));
                        return 1;
                    })));

            dispatcher.register(ClientCommandManager.literal("runblock")
                .then(ClientCommandManager.argument("pseudo", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "pseudo");
                        boolean ok = ChatBlockList.INSTANCE.unblock(name);
                        ctx.getSource().sendFeedback(Text.literal(
                            ok ? "§aJoueur débloqué : §f" + name
                               : "§7Ce joueur n'était pas bloqué : §f" + name));
                        return 1;
                    })));

            dispatcher.register(ClientCommandManager.literal("rblocklist")
                .executes(ctx -> {
                    var names = ChatBlockList.INSTANCE.names();
                    ctx.getSource().sendFeedback(Text.literal(
                        names.isEmpty() ? "§7Aucun joueur bloqué."
                                        : "§cBloqués (§f" + names.size() + "§c) : §f"
                                          + String.join(", ", names)));
                    return 1;
                }));
        });
    }
}
