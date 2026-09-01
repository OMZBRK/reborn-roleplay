package fr.reborn.hud.menu.inventory;

import fr.reborn.hud.menu.settings.RebornPrefs;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Commande client {@code /rpinv} — bascule entre l'inventaire <b>sacoche RP</b>
 * (touche E détournée par le mod) et l'<b>inventaire Minecraft vanilla</b>. La
 * préférence est persistée ({@link RebornPrefs#sacocheInventory}) et lue à chaque
 * appui sur E par {@code HudKeybinds}. {@code /rpinv} seul bascule ; {@code /rpinv
 * vanilla} force le vanilla ; {@code /rpinv custom} force la sacoche.
 *
 * <p>Indépendant de la présence du plugin : si ShinobiCore est absent (build), la
 * sacoche ne se déclenche de toute façon pas (gate {@code canSend} dans HudKeybinds),
 * donc l'inventaire reste vanilla quel que soit ce réglage.
 */
public final class InventoryCommands {

    private InventoryCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
            dispatcher.register(ClientCommands.literal("rpinv")
                .executes(ctx -> { apply(ctx.getSource(), !RebornPrefs.INSTANCE.sacocheInventory); return 1; })
                .then(ClientCommands.literal("vanilla").executes(ctx -> { apply(ctx.getSource(), false); return 1; }))
                .then(ClientCommands.literal("custom").executes(ctx -> { apply(ctx.getSource(), true); return 1; }))
                .then(ClientCommands.literal("sacoche").executes(ctx -> { apply(ctx.getSource(), true); return 1; }))));
    }

    private static void apply(FabricClientCommandSource src, boolean sacoche) {
        RebornPrefs.INSTANCE.ensureLoaded();
        RebornPrefs.INSTANCE.sacocheInventory = sacoche;
        RebornPrefs.INSTANCE.save();
        src.sendFeedback(Component.literal(sacoche
            ? "§a[Reborn] Inventaire : SACOCHE RP (touche E). Tape §f/rpinv vanilla§a pour l'inventaire Minecraft."
            : "§7[Reborn] Inventaire : VANILLA Minecraft (touche E). Tape §f/rpinv custom§7 pour la sacoche RP."));
    }
}
