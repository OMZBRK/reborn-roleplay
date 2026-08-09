package fr.reborn.hud.skin;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Commandes client de test du pipeline skin (Phase 2) :
 * {@code /rebornskin test} applique une composition de test au joueur local
 * (validation solo de l'override de rendu), {@code /rebornskin reset} l'enlève.
 */
public final class SkinCommands {

    private SkinCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommands.literal("rebornskin")
                .then(ClientCommands.literal("test").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        RebornSkins.applyTest(mc.player.getUUID());
                        ctx.getSource().sendFeedback(Component.literal(
                            "§a[Reborn] Skin de test appliqué (override de composition)."));
                    }
                    return 1;
                }))
                .then(ClientCommands.literal("reset").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        RebornSkins.clear(mc.player.getUUID());
                        ctx.getSource().sendFeedback(Component.literal(
                            "§7[Reborn] Skin remis à la normale."));
                    }
                    return 1;
                }))));
    }
}
